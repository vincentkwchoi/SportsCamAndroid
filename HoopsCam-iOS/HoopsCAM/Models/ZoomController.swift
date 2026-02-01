import Combine
import Foundation
import CoreGraphics

class ZoomController: ObservableObject {
    @Published var currentZoomFactor: CGFloat = 1.0
    
    @Published var isAutoZoomEnabled = true
    
    // Configuration
    private let minZoom: CGFloat = 0.5 // Updated to support Ultra Wide (0.5x)
    private let maxZoom: CGFloat = 5.0 // Increased to 5.0x per updated CameraManager logic
    private let margin: CGFloat = 0.2 // 20% margin around players
    private let smoothingFactor: CGFloat = 0.05 // Lower = smoother/slower
    
    // Kalman Filter (Balanced Tuning)
    // Q=0.001 (Process Noise), R=0.05 (Measurement Noise)
    private let kalmanFilter = KalmanFilter(initialValue: 1.0, processNoise: 0.001, measurementNoise: 0.05)
    
    // Deadband State
    private var lastAcceptedTargetZoom: CGFloat = 1.0
    
    func manualZoomIn() {
        isAutoZoomEnabled = false
        let newZoom = min(currentZoomFactor + 0.1, maxZoom)
        currentZoomFactor = newZoom
        kalmanFilter.reset(to: Double(newZoom)) // Sync filter
    }
    
    func manualZoomOut() {
        isAutoZoomEnabled = false
        let newZoom = max(currentZoomFactor - 0.1, minZoom)
        currentZoomFactor = newZoom
        kalmanFilter.reset(to: Double(newZoom)) // Sync filter
    }
    
    func toggleAutoZoom() {
        isAutoZoomEnabled.toggle()
        if isAutoZoomEnabled {
            kalmanFilter.reset(to: Double(currentZoomFactor)) // Smooth start from current
        }
    }
    
    func update(detections: [Detection], videoAspectRatio: CGFloat = 9.0/16.0) {
        guard isAutoZoomEnabled else { return }
        
        // 1. Filter for ACTIVE players only (classId == 1)
        // In our pipeline: 0 = Person (Raw), 1 = Active Player (Filtered)
        let activePlayers = detections.filter { $0.classId == 1 }
        
        // 2. Edge Case: No Active Players
        // "Stay Put" logic: If lost tracking, do NOT change zoom.
        if activePlayers.isEmpty {
            return
        }
        
        // 3. Calculate Average Height
        // BBox height is normalized [0.0 - 1.0]
        let totalHeight = activePlayers.reduce(0.0) { $0 + $1.bbox.height }
        let averageHeight = totalHeight / CGFloat(activePlayers.count)
        
        // 4. Calculate Target Zoom
        // Goal: Player should take up 1/3 (0.33) of the screen height.
        // Formula: CurrentHeight * Zoom = TargetHeight
        //          Zoom = TargetHeight / CurrentHeight
        // Safety: Avoid divide by zero or extremely small heights (false positives)
        let safeHeight = max(averageHeight, 0.05) // Min 5% height to prevent infinite zoom
        let targetSizeRatio: CGFloat = 0.33 // 1/3 of screen
        
        var targetZoom = targetSizeRatio / safeHeight
        
        // 5. Deadband (Hysteresis)
        // Prevent "hunting" by ignoring small changes (< 5%)
        // If the new target is very close to the last one we accepted, stick to the old one.
        // This makes the zoom "lock" until a significant move happens.
        if abs(targetZoom - lastAcceptedTargetZoom) / lastAcceptedTargetZoom > 0.05 {
            lastAcceptedTargetZoom = targetZoom
        } else {
            targetZoom = lastAcceptedTargetZoom
        }
        
        // 6. Clamp
        targetZoom = min(max(targetZoom, minZoom), maxZoom)
        
        // 7. Kalman Filter Update
        let filteredZoom = kalmanFilter.update(measurement: Double(targetZoom))
        
        // 8. Apply
        currentZoomFactor = CGFloat(filteredZoom)
    }
    
    // Removed old smoothZoom helper
}
