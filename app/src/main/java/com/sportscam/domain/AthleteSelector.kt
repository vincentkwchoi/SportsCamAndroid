package com.sportscam.domain

import android.graphics.RectF
import com.sportscam.data.models.Track

interface AthleteSelector {
    fun selectTarget(tracks: List<Track>): Track?
    fun reset()
    fun setLockedId(id: Int?)
}

class SingleSubjectSelector : AthleteSelector {
    private var lockedTrackId: Int? = null

    override fun selectTarget(tracks: List<Track>): Track? {
        if (tracks.isEmpty()) return null

        // 1. Try to find the locked subject first
        lockedTrackId?.let { id ->
            val lockedMatch = tracks.find { it.id == id }
            if (lockedMatch != null) return lockedMatch
        }

        // 2. Fallback: Find track closest to center (0.5, 0.5)
        val bestMatch = tracks.minByOrNull { track ->
            val dx = track.bbox.centerX() - 0.5f
            val dy = track.bbox.centerY() - 0.5f
            dx * dx + dy * dy
        }

        lockedTrackId = bestMatch?.id
        return bestMatch
    }

    override fun reset() {
        lockedTrackId = null
    }

    override fun setLockedId(id: Int?) {
        lockedTrackId = id
    }
}

class GroupSelector : AthleteSelector {
    override fun selectTarget(tracks: List<Track>): Track? {
        if (tracks.isEmpty()) return null
        if (tracks.size == 1) return tracks.first()

        // Calculate a bounding box that contains all tracks
        val groupBbox = RectF(
            tracks.minOf { it.bbox.left },
            tracks.minOf { it.bbox.top },
            tracks.maxOf { it.bbox.right },
            tracks.maxOf { it.bbox.bottom }
        )

        // Return a virtual track representing the whole group
        return Track(
            id = -100, // Special ID for group tracking
            bbox = groupBbox,
            confidence = tracks.map { it.confidence }.average().toFloat()
        )
    }

    override fun reset() {}
    override fun setLockedId(id: Int?) {}
}
