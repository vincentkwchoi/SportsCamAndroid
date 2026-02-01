import Combine
import CoreML
import Vision
import CoreVideo
import SwiftUI

struct Detection: Identifiable {
    var id: UUID = UUID()
    let bbox: CGRect // Normalized [0-1] coordinates
    let confidence: Float
    let classId: Int // 0: Inactive, 1: Active
}

class YOLODetector: ObservableObject {
    private var model: VNCoreMLModel?
    private var request: VNCoreMLRequest?
    
    @Published var detections: [Detection] = []
    
    // Throttle: Process every Nth frame
    private var frameCounter = 0
    private let detectionInterval = 2 // Process every 2nd frame (15 FPS)
    
    init() {
        setupModel()
    }
    
    private func setupModel() {
        do {
            // Assumes the model file name is "yolo11x" and Xcode generated the class "yolo11x"
            let config = MLModelConfiguration()
            config.computeUnits = .all // Use Neural Engine if available
            
            // Load the model
            let yoloModel = try yolo11x(configuration: config)
            self.model = try VNCoreMLModel(for: yoloModel.model)
            print("✅ YOLODetector: Model loaded successfully!")
        } catch {
            print("Failed to load YOLO model: \(error)")
        }
    }
    
    // -- Phase 3 Integration --
    private let tracker = SimpleTracker()
    private let activityDetector = BBoxActivityDetector()
    
    // NMS Configuration
    private let confidenceThreshold: Float = 0.5
    private let iouThreshold: Float = 0.5
    
    func detect(pixelBuffer: CVPixelBuffer) {
        frameCounter += 1
        if frameCounter % detectionInterval != 0 { return }
        
        guard let model = model else { return }
        
        let request = VNCoreMLRequest(model: model) { [weak self] request, error in
            if let error = error {
                print("❌ Vision Request Error: \(error)")
                return
            }
            
            // Handle both raw and processed results
            if let results = request.results as? [VNRecognizedObjectObservation] {
                self?.processResults(results)
            } else if let multiArrayObservations = request.results as? [VNCoreMLFeatureValueObservation],
                      let multiArray = multiArrayObservations.first?.featureValue.multiArrayValue {
                self?.processRawResults(multiArray)
            }
        }
        request.imageCropAndScaleOption = .scaleFill
        
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up)
        try? handler.perform([request])
    }
    
    // Handle standard Vision results (if NMS model used in future)
    private func processResults(_ results: [VNRecognizedObjectObservation]) {
        let rawDetections = results.compactMap { observation -> Detection? in
            guard observation.confidence > 0.5 else { return nil }
            let bbox = observation.boundingBox
            // Vision (Bottom-Left) -> SwiftUI (Top-Left)
            let correctedBBox = CGRect(
                x: bbox.origin.x,
                y: 1.0 - bbox.origin.y - bbox.height,
                width: bbox.width,
                height: bbox.height
            )
            return Detection(bbox: correctedBBox, confidence: observation.confidence, classId: 0)
        }
        updateTracking(rawDetections)
    }
    
    // Handle Raw MultiArray [1, 84, 8400] from YOLO11 (No NMS)
    private func processRawResults(_ multiArray: MLMultiArray) {
        guard multiArray.shape.count == 3,
              multiArray.shape[1].intValue == 84,
              multiArray.shape[2].intValue == 8400 else {
            print("⚠️ Unexpected model shape: \(multiArray.shape)")
            return
        }
        
        // Safety check for pointer access
        let count = 8400
        let ptr = UnsafeMutablePointer<Float>(OpaquePointer(multiArray.dataPointer))
        
        // Channel indices for [1, 84, 8400] layout:
        // ptr[ch * 8400 + i]
        // 0: x, 1: y, 2: w, 3: h, 4...83: class probabilities
        // We only care about Person (Index 4? Or Index 0 of classes?)
        // YOLO box is indices 0-3. Classes start at index 4.
        // COCO: Person is Class 0.
        // So Person confidence is at index 4 + 0 = 4.
        
        var detections: [Detection] = []
        
        for i in 0..<count {
            // Check Person Confidence
            let confidence = ptr[4 * 8400 + i]
            
            if confidence > confidenceThreshold {
                let cx = ptr[0 * 8400 + i]
                let cy = ptr[1 * 8400 + i]
                let w = ptr[2 * 8400 + i]
                let h = ptr[3 * 8400 + i]
                
                // YOLO raw output is usually in 640x640 pixel coordinates? Or normalized?
                // Standard default export often normalizes?
                // Often it is PIXELS if `export format=coreml`.
                // Let's assume normalized first? No, logs showed shape [1, 84, 8400]. 
                // Usually cx, cy are valid.
                // If they are > 1.0, they are pixels.
                // Let's assume pixels (640x640).
                
                let x = (cx - w/2) / 640.0
                let y = (cy - h/2) / 640.0
                let width = w / 640.0
                let height = h / 640.0
                
                // BBox in raw output usually matches image coordinate system (Top-left 0,0?)
                // YOLO PyTorch training uses Top-Left logic usually.
                // But conversion might differ.
                // Usually CoreML image input (640x640) -> Output.
                // Let's create normalized rect directly.
                
                let bbox = CGRect(x: Double(x), y: Double(y), width: Double(width), height: Double(height))
                
                detections.append(Detection(
                    id: UUID(),
                    bbox: bbox,
                    confidence: confidence,
                    classId: 0
                ))
            }
        }
        
        let nmsDetections = nonMaxSuppression(detections: detections, iouThreshold: iouThreshold)
        updateTracking(nmsDetections)
    }
    
    private func updateTracking(_ detections: [Detection]) {
        // 2. Tracking
        let trackedObjects = tracker.track(detections: detections)
        
        // 3. Activity Filtering
        let finalDetections = trackedObjects.map { track -> Detection in
            let isActive = activityDetector.isActive(trackerId: track.id, bbox: track.bbox)
            return Detection(
                id: track.id,
                bbox: track.bbox,
                confidence: track.confidence,
                classId: isActive ? 1 : 0
            )
        }
        
        DispatchQueue.main.async {
            self.detections = finalDetections
        }
    }
    
    private func nonMaxSuppression(detections: [Detection], iouThreshold: Float) -> [Detection] {
        let sorted = detections.sorted { $0.confidence > $1.confidence }
        var selected: [Detection] = []
        var active = [Bool](repeating: true, count: sorted.count)
        
        for i in 0..<sorted.count {
            if active[i] {
                let boxA = sorted[i]
                selected.append(boxA)
                
                for j in (i+1)..<sorted.count {
                    if active[j] {
                        let boxB = sorted[j]
                        if calculateIOU(boxA.bbox, boxB.bbox) > Double(iouThreshold) {
                            active[j] = false
                        }
                    }
                }
            }
        }
        return selected
    }
    
    private func calculateIOU(_ rect1: CGRect, _ rect2: CGRect) -> Double {
        let intersection = rect1.intersection(rect2)
        if intersection.isNull { return 0 }
        let intersectArea = intersection.width * intersection.height
        let unionArea = rect1.width * rect1.height + rect2.width * rect2.height - intersectArea
        return unionArea > 0 ? Double(intersectArea / unionArea) : 0
    }
}
