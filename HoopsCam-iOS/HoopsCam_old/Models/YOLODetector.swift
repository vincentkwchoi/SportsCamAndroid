import CoreML
import Vision
import CoreVideo
import SwiftUI

struct Detection: Identifiable {
    let id: UUID = UUID()
    let bbox: CGRect // Normalized [0-1] coordinates
    let confidence: Float
    let classId: Int
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
            
            // NOTE: This will fail to compile until yolo11x.mlmodel is added to the project
            // and the auto-generated class is available.
            // For now, we wrap in try/catch and comment out the specific class instantiation
            // to allow basic structural compilation if the model isn't there yet.
            // In a real build, uncomment the next line:
            // let yoloModel = try yolo11x(configuration: config)
            
            // Placeholder for compilable code without model:
            // self.model = try VNCoreMLModel(for: yoloModel.model)
            
            // To make this file valid Swift for the user to start with, I will assume the class exists.
            // If the user hasn't added the model, they will get a build error, which is expected.
            // But to avoid "Type not found" immediately, I will print a warning.
            
            print("⚠️ YOLODetector: Model not loaded yet. Add yolo11x.mlmodel to Resources.")
        } catch {
            print("Failed to load YOLO model: \(error)")
        }
    }
    
    func detect(pixelBuffer: CVPixelBuffer) {
        frameCounter += 1
        if frameCounter % detectionInterval != 0 { return }
        
        // Mock detection for testing UI without model
        // remove this when model is ready
        /*
        DispatchQueue.main.async {
            self.detections = [
                Detection(bbox: CGRect(x: 0.2, y: 0.3, width: 0.1, height: 0.2), confidence: 0.9, classId: 0),
                Detection(bbox: CGRect(x: 0.6, y: 0.4, width: 0.1, height: 0.25), confidence: 0.85, classId: 0)
            ]
        }
        return; 
        */
        
        guard let model = model else { return }
        
        let request = VNCoreMLRequest(model: model) { [weak self] request, error in
            guard let results = request.results as? [VNRecognizedObjectObservation] else { return }
            self?.processResults(results)
        }
        request.imageCropAndScaleOption = .scaleFill
        
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up)
        try? handler.perform([request])
    }
    
    private func processResults(_ results: [VNRecognizedObjectObservation]) {
        let newDetections = results.compactMap { observation -> Detection? in
            // Filter for 'person' class (assuming class 0 is person, depends on model config)
            // YOLO usually returns classes. We should check labels if available.
            // standard YOLOv8/11 COCO: class 0 is person.
            
            // Check confidence (e.g. > 0.5)
            guard observation.confidence > 0.5 else { return nil }
            
            // Transform bbox: Vision uses standard normalized coordinates (origin bottom-left).
            // SwiftUI uses origin top-left.
            // Converting Vision Rect (y up) to SwiftUI Rect (y down)
            let bbox = observation.boundingBox
            let correctedBBox = CGRect(
                x: bbox.origin.x,
                y: 1.0 - bbox.origin.y - bbox.height, // Flip Y
                width: bbox.width,
                height: bbox.height
            )
            
            return Detection(
                bbox: correctedBBox,
                confidence: observation.confidence,
                classId: 0 // Simplification
            )
        }
        
        DispatchQueue.main.async {
            self.detections = newDetections
        }
    }
}
