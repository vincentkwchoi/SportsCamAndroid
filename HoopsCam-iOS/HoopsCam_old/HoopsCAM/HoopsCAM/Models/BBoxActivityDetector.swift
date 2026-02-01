import Foundation
import CoreGraphics

class BBoxActivityDetector {
    // Configuration
    private let historySize = 60 // 2 seconds at 30fps
    private let shapeThreshold: Double = 0.002 // Hand-tuned threshold from Python
    
    // State: Map of TrackerID -> [BBoxFeatures]
    private var history: [UUID: [BBoxFeatures]] = [:]
    
    struct BBoxFeatures {
        let width: Double
        let height: Double
        let area: Double
        let aspectRatio: Double
    }
    
    func isActive(trackerId: UUID, bbox: CGRect) -> Bool {
        let features = extractFeatures(bbox)
        
        // Update history
        if history[trackerId] == nil {
            history[trackerId] = []
        }
        history[trackerId]?.append(features)
        
        // Maintain buffer size
        if let count = history[trackerId]?.count, count > historySize {
            history[trackerId]?.removeFirst(count - historySize)
        }
        
        // Check activity
        let variance = calculateShapeVariance(trackerId: trackerId)
        return variance > shapeThreshold
    }
    
    private func extractFeatures(_ bbox: CGRect) -> BBoxFeatures {
        // Assume bbox is normalized [0-1]
        let w = Double(bbox.width)
        let h = Double(bbox.height)
        return BBoxFeatures(
            width: w,
            height: h,
            area: w * h,
            aspectRatio: h > 0 ? w / h : 0
        )
    }
    
    private func calculateShapeVariance(trackerId: UUID) -> Double {
        guard let trackHistory = history[trackerId], trackHistory.count > 10 else {
            return 1.0 // Assume active if not enough history
        }
        
        // Calculate variance of Aspect Ratio and Area
        let aspectRatios = trackHistory.map { $0.aspectRatio }
        let areas = trackHistory.map { $0.area }
        
        let aspectVar = variance(aspectRatios)
        let areaVar = variance(areas)
        
        // Combine them (simple average or weighted)
        // Python: (aspect_std + area_std) / 2 ... wait, std or var?
        // Python code said: (std(aspect) + std(area)) / 2
        // Let's implement Standard Deviation
        
        return (sqrt(aspectVar) + sqrt(areaVar)) / 2.0
    }
    
    private func variance(_ numbers: [Double]) -> Double {
        let count = Double(numbers.count)
        if count == 0 { return 0 }
        
        let mean = numbers.reduce(0, +) / count
        let sumSquaredDiffs = numbers.reduce(0) { sum, val in
            sum + pow(val - mean, 2)
        }
        return sumSquaredDiffs / count
    }
    
    func clear() {
        history.removeAll()
    }
}
