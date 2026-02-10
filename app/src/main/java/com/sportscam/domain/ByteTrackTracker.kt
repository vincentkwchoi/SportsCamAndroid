package com.sportscam.domain

import android.util.Log
import android.graphics.RectF
import com.sportscam.data.models.Detection
import com.sportscam.data.models.Track
import com.sportscam.data.models.TrackState

class ByteTrackTracker(
    var trackThresh: Float = 0.5f,
    var trackBuffer: Int = 30, // frames to keep lost tracks
    var matchThresh: Float = 0.3f
) {
    private var tracks = ArrayList<Track>()
    private var lostTracks = ArrayList<Track>() // Removed tracks, kept in buffer
    private var removedTracks = ArrayList<Track>()
    
    private var frameCount = 0
    private var nextId = 1

    fun update(detections: List<Detection>): List<Track> {
        frameCount++

        // 1. Prediction: Predict next state for all tracks
        val pool = ArrayList<Track>()
        tracks.forEach { t ->
            val predictedBbox = t.kf?.predict() ?: t.bbox
            pool.add(t.copy(bbox = predictedBbox))
        }

        // 2. Separate detections into high/low score
        val highDets = detections.filter { it.confidence >= trackThresh }
        val lowDets = detections.filter { it.confidence < trackThresh && it.confidence > 0.1f }
        
        if (detections.isNotEmpty() || tracks.isNotEmpty()) {
            Log.d("AutoZoom", "ByteTrack - Detections: High=${highDets.size}, Low=${lowDets.size}, Pool=${pool.size}")
        }

        // 3. First Association: High confidence detections with Pool (Tracked + Lost)
        // iOS use minIoU = 0.2 (greedy match)
        val minIoU = 0.2f
        val (matches1, unmatchedTracks1, unmatchedDets1) = associate(pool, highDets, minIoU)

        // 4. Second Association: Low confidence detections with remaining Tracked tracks
        // iOS logic: only match against previously TRACKED tracks, not previously LOST
        val candidates2 = unmatchedTracks1.filter { it.state != TrackState.LOST }
        val (matches2, unmatchedTracks2, _) = associate(candidates2, lowDets, 0.5f)

        // 5. Handle unmatched and matched logic to re-build track list
        val nextTracks = ArrayList<Track>()
        val processedIds = HashSet<Int>()

        // Update matched tracks (Stage 1 & 2)
        (matches1 + matches2).forEach { (track, det) ->
            val updatedTrack = updateTrackWithMeasurement(track, det)
            nextTracks.add(updatedTrack)
            processedIds.add(track.id)
        }

        // Handle unmatched previously tracked/lost tracks
        val remainingTracks = pool.filter { !processedIds.contains(it.id) }
        for (track in remainingTracks) {
            // Logic: only keep tracks in LOST pool if they were previously CONFIRMED and active
            if (track.state == TrackState.CONFIRMED || track.state == TrackState.LOST) {
                val newMisses = track.misses + 1
                if (newMisses <= trackBuffer) {
                    nextTracks.add(track.copy(
                        state = TrackState.LOST,
                        misses = newMisses
                    ))
                } else {
                    Log.d("AutoZoom", "ByteTrack - Track ${track.id} removed after $newMisses missed frames")
                }
            } else {
                // TENTATIVE track unmatched -> Delete immediately (prevents noisy clutter)
            }
        }

        // 6. Initialize New Tracks from unmatched High Detections
        for (det in unmatchedDets1) {
            val newTrack = Track(
                id = nextId++,
                bbox = det.bbox,
                confidence = det.confidence,
                state = TrackState.TENTATIVE, // Start as tentative (Standard ByteTrack)
                hits = 1,
                misses = 0,
                age = 1,
                kf = KalmanFilter(det.bbox)
            )
            nextTracks.add(newTrack)
        }
        
        tracks = nextTracks
        
        // Return only confirmed tracks that are currently visible
        return tracks.filter { it.state == TrackState.CONFIRMED && it.misses == 0 }
    }
    
    private fun updateTrackWithMeasurement(track: Track, det: Detection): Track {
        track.kf?.update(det.bbox)
        val updatedBbox = track.kf?.currentStateRect() ?: det.bbox
        
        // Move to CONFIRMED if hits >= 2 (Standard ByteTrack logic)
        val newState = if (track.hits + 1 >= 2) TrackState.CONFIRMED else TrackState.TENTATIVE
        
        return track.copy(
            bbox = updatedBbox,
            confidence = det.confidence,
            hits = track.hits + 1,
            misses = 0,
            age = track.age + 1,
            state = newState
        )
    }
    
    // Returns: Matches<Track, Det>, UnmatchedTracks, UnmatchedDets
    private fun associate(
        tracks: List<Track>, 
        detections: List<Detection>,
        iouThresh: Float
    ): Triple<List<Pair<Track, Detection>>, List<Track>, List<Detection>> {
        if (tracks.isEmpty() || detections.isEmpty()) {
            return Triple(emptyList(), tracks, detections)
        }

        val matches = ArrayList<Pair<Track, Detection>>()
        
        // Match Global Greedy (Matching iOS logic)
        data class Cost(val iou: Float, val tIdx: Int, val dIdx: Int)
        val costs = ArrayList<Cost>()
        
        for (tIdx in tracks.indices) {
            for (dIdx in detections.indices) {
                val iou = calculateIoU(tracks[tIdx].bbox, detections[dIdx].bbox)
                if (iou >= iouThresh) {
                    costs.add(Cost(iou, tIdx, dIdx))
                }
            }
        }
        
        // Sort by IoU descending (Best match first)
        costs.sortByDescending { it.iou }
        
        val assignedTracks = HashSet<Int>()
        val assignedDets = HashSet<Int>()
        
        for (cost in costs) {
            if (!assignedTracks.contains(cost.tIdx) && !assignedDets.contains(cost.dIdx)) {
                val track = tracks[cost.tIdx]
                val det = detections[cost.dIdx]
                val rect = det.bbox
                Log.d("AutoZoom", "ByteTrack - Match: Track ID=${track.id} with Detection [cx=${"%.3f".format(rect.centerX())}, cy=${"%.3f".format(rect.centerY())}, conf=${"%.2f".format(det.confidence)}] (IoU=${"%.3f".format(cost.iou)})")
                matches.add(Pair(track, det))
                assignedTracks.add(cost.tIdx)
                assignedDets.add(cost.dIdx)
            }
        }
        
        val unmatchedTracks = tracks.filterIndexed { index, _ -> !assignedTracks.contains(index) }
        val unmatchedDets = detections.filterIndexed { index, _ -> !assignedDets.contains(index) }
        
        return Triple(matches, unmatchedTracks, unmatchedDets)
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val intersectLeft = maxOf(a.left, b.left)
        val intersectTop = maxOf(a.top, b.top)
        val intersectRight = minOf(a.right, b.right)
        val intersectBottom = minOf(a.bottom, b.bottom)
        
        if (intersectRight < intersectLeft || intersectBottom < intersectTop) return 0f
        
        val intersection = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        
        return intersection / (areaA + areaB - intersection)
    }
    
    fun updateConfig(trackBuffer: Int, minConfidence: Float) {
        this.trackBuffer = trackBuffer
        this.trackThresh = minConfidence
    }

    fun reset() {
        tracks.clear()
        lostTracks.clear()
        frameCount = 0
        nextId = 1
    }
}
