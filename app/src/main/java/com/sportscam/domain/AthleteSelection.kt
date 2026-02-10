package com.sportscam.domain

import com.sportscam.data.models.Track

class AthleteSelection {
    private var lockedTrackId: Int? = null
    
    fun selectTarget(tracks: List<Track>): Track? {
        if (tracks.isEmpty()) return null
        
        // Find track closest to center (0.5, 0.5)
        val bestMatch = tracks.minByOrNull { track ->
            val dx = track.bbox.centerX() - 0.5f
            val dy = track.bbox.centerY() - 0.5f
            dx * dx + dy * dy
        }
        
        if (bestMatch != null) {
            lockedTrackId = bestMatch.id
        }
        
        return bestMatch
    }
    
    fun reset() {
        lockedTrackId = null
    }

    fun setLockedId(id: Int?) {
        this.lockedTrackId = id
    }
}
