import Foundation

/// A 1D Kalman Filter for smoothing scalar values (like zoom factor).
class KalmanFilter {
    // State
    private var x: Double // Estimated value (zoom)
    private var p: Double // Estimation error covariance
    
    // Tuning Parameters
    private let q: Double // Process noise covariance (Responsiveness: higher = faster)
    private let r: Double // Measurement noise covariance (Smoothness: higher = smoother)
    
    // Initial guess
    init(initialValue: Double, processNoise: Double = 0.001, measurementNoise: Double = 0.05) {
        self.x = initialValue
        self.p = 1.0 // Initial high uncertainty
        self.q = processNoise
        self.r = measurementNoise
    }
    
    /// Predicts and updates the state with a new measurement
    /// - Parameter measurement: The raw target value (new zoom calculation)
    /// - Returns: The filtered/smoothed state
    func update(measurement: Double) -> Double {
        // 1. Prediction (Time Update)
        // Assume constant state model: x_k = x_{k-1}
        // p_k = p_{k-1} + q
        let pPred = p + q
        
        // 2. Update (Measurement Update)
        // Kalman Gain: k = pPred / (pPred + r)
        let k = pPred / (pPred + r)
        
        // Update estimate: x = xPred + k * (measurement - xPred)
        x = x + k * (measurement - x)
        
        // Update covariance: p = (1 - k) * pPred
        p = (1.0 - k) * pPred
        
        return x
    }
    
    /// Resets the filter to a hard value (e.g. on manual override)
    func reset(to value: Double) {
        self.x = value
        self.p = 1.0
    }
}
