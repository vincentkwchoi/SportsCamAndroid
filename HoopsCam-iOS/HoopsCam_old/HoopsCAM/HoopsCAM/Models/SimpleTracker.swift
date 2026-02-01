import Foundation
import CoreGraphics

struct TrackedObject {
    let id: UUID
    var bbox: CGRect
    var confidence: Float
    var lastSeenFrame: Int
}

class SimpleTracker {
    private var tracks: [TrackedObject] = []
    private var frameCount: Int = 0
    private let iouThreshold: CGFloat = 0.3
    private let maxLostFrames: Int = 15 // Keep ID for ~1 second if lost (at 15fps processing)
    
    func track(detections: [Detection]) -> [TrackedObject] {
        frameCount += 1
        var activeTracks: [TrackedObject] = []
        
        // Convert detections to temporary TrackedObjects (no ID yet)
        var unmatchedDetections = detections
        
        // 1. Match existing tracks to new detections using IOU
        for i in 0..<tracks.count {
            var bestIOU: CGFloat = 0
            var bestMatchIndex: Int = -1
            
            // Predict next position? (Simple assumption: same position)
            // Ideally we use Kalman filter, but for MVP simple overlap is enough at high FPS
            
            for (j, detection) in unmatchedDetections.enumerated() {
                let iou = calculateIOU(tracks[i].bbox, detection.bbox)
                if iou > bestIOU {
                    bestIOU = iou
                    bestMatchIndex = j
                }
            }
            
            if bestIOU > iouThreshold && bestMatchIndex != -1 {
                // Update track
                var updatedTrack = tracks[i]
                updatedTrack.bbox = unmatchedDetections[bestMatchIndex].bbox
                updatedTrack.confidence = unmatchedDetections[bestMatchIndex].confidence
                updatedTrack.lastSeenFrame = frameCount
                activeTracks.append(updatedTrack)
                
                // Remove matched detection
                unmatchedDetections.remove(at: bestMatchIndex)
            } else {
                // Track lost this frame
                // Keep it if not too old
                if frameCount - tracks[i].lastSeenFrame < maxLostFrames {
                    activeTracks.append(tracks[i])
                }
            }
        }
        
        // 2. Create new tracks for unmatched detections
        for detection in unmatchedDetections {
            let newTrack = TrackedObject(
                id: UUID(),
                bbox: detection.bbox,
                confidence: detection.confidence,
                lastSeenFrame: frameCount
            )
            activeTracks.append(newTrack)
        }
        
        self.tracks = activeTracks
        
        // Return only tracks seen in THIS frame (or very recently?)
        // For drawing, we want current positions.
        // If we return "lost" tracks, they will appear frozen.
        // Let's return only tracks updated this frame for "Detection" purpose,
        // or return all if we want to show "ghosts" (not recommended for auto zoom).
        // Returning tracks updated this frame is safer for UI.
        
        return activeTracks.filter { $0.lastSeenFrame == frameCount }
    }
    
    private func calculateIOU(_ rect1: CGRect, _ rect2: CGRect) -> CGFloat {
        let intersection = rect1.intersection(rect2)
        if intersection.isNull { return 0 }
        
        let intersectArea = intersection.width * intersection.height
        let area1 = rect1.width * rect1.height
        let area2 = rect2.width * rect2.height
        let unionArea = area1 + area2 - intersectArea
        
        if unionArea <= 0 { return 0 }
        return intersectArea / unionArea
    }
}
