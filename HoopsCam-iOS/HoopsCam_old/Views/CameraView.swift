import SwiftUI
import AVFoundation

struct CameraView: View {
    @StateObject private var cameraManager = CameraManager()
    @StateObject private var detector = YOLODetector()
    
    var body: some View {
        ZStack {
            // Camera Preview
            if cameraManager.isAuthorized {
                CameraPreviewLayer(session: cameraManager.captureSession)
                    .ignoresSafeArea()
            } else {
                Text("Camera Permission Required")
            }
            
            // Bounding Boxes
            BoundingBoxOverlay(detections: detector.detections)
            
            // Status and Controls
            VStack {
                Text("HoopsCam Auto-Zoom")
                    .font(.headline)
                    .padding()
                    .background(.ultraThinMaterial)
                    .cornerRadius(8)
                
                Spacer()
                
                // Debug control
                Text(cameraManager.isRunning ? "Running" : "Stopped")
                    .foregroundColor(cameraManager.isRunning ? .green : .red)
            }
        }
        .onAppear {
            cameraManager.start()
            
            // Subscribe to detections
            cameraManager.framePublisher
                .receive(on: DispatchQueue.global(qos: .userInteractive))
                .sink { pixelBuffer in
                    detector.detect(pixelBuffer: pixelBuffer)
                }
                .store(in: &cancellables)
        }
        .onDisappear {
            cameraManager.stop()
        }
    }
    
    @State private var cancellables = Set<AnyCancellable>()
}

// Map AVCaptureSession to UIKit UIView
struct CameraPreviewLayer: UIViewRepresentable {
    let session: AVCaptureSession
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        // Store layer to resize later
        context.coordinator.previewLayer = previewLayer
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        if let layer = context.coordinator.previewLayer {
            layer.frame = uiView.bounds
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator {
        var previewLayer: AVCaptureVideoPreviewLayer?
    }
}
