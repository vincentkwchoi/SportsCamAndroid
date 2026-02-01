import Combine
import SwiftUI
import AVFoundation
import MediaPlayer

// Volume Button Listener
class VolumeHandler: ObservableObject {
    @Published var volumeChange: CGFloat = 0.0
    private var audioSession = AVAudioSession.sharedInstance()
    private var observer: NSKeyValueObservation?
    private var lastVolume: Float = 0.0
    
    init() {
        startObserving()
    }
    
    func startObserving() {
        do {
            try audioSession.setActive(true)
            lastVolume = audioSession.outputVolume
            observer = audioSession.observe(\.outputVolume) { [weak self] session, _ in
                guard let self = self else { return }
                let currentVolume = session.outputVolume
                if currentVolume > self.lastVolume {
                    self.volumeChange = 1.0 // Up
                } else if currentVolume < self.lastVolume {
                    self.volumeChange = -1.0 // Down
                }
                self.lastVolume = currentVolume
            }
        } catch {
            print("Audio Session Error: \(error)")
        }
    }
}

struct CameraView: View {
    @StateObject private var cameraManager = CameraManager()
    @StateObject private var detector = YOLODetector()
    @StateObject private var zoomController = ZoomController()
    @StateObject private var volumeHandler = VolumeHandler()
    
    // Recording Timer State
    @State private var recordingTime: TimeInterval = 0
    @State private var timer: Timer.TimerPublisher = Timer.publish(every: 1, on: .main, in: .common)
    @State private var timerCancellable: AnyCancellable?
    
    // Debug Mode
    @State private var isDebugMode = false
    
    @State private var cancellables = Set<AnyCancellable>()
    
    var body: some View {
        ZStack {
            // Camera Preview
             CameraPreviewLayer(session: cameraManager.captureSession)
                .ignoresSafeArea()
            
            // Hidden System Volume View to disable Volume HUD
            VolumeView()
                .frame(width: 0, height: 0)
                .opacity(0.01)

            // Bounding Boxes (Debug Only)
            if isDebugMode {
                BoundingBoxOverlay(detections: detector.detections)
            }
            
            // Status and Controls
            VStack {
                // Top Status Bar: Zoom Mode & Recording Timer
                HStack {
                    // Zoom Status Indicator
                    HStack(spacing: 4) {
                        Image(systemName: zoomController.isAutoZoomEnabled ? "bolt.fill" : "hand.raised.fill")
                            .font(.caption)
                        Text(zoomController.isAutoZoomEnabled ? "AUTO: Virtual Dolly" : "MANUAL")
                            .font(.caption)
                            .fontWeight(.bold)
                    }
                    .foregroundColor(zoomController.isAutoZoomEnabled ? .yellow : .white)
                    .padding(6)
                    .background(.ultraThinMaterial)
                    .cornerRadius(6)
                    .onTapGesture {
                        withAnimation { zoomController.toggleAutoZoom() }
                    }
                    
                    Spacer()
                    
                    // Recording Timer
                    if cameraManager.isRecording {
                        Text(timeString(from: recordingTime))
                            .font(.system(.body, design: .monospaced))
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(6)
                            .background(Color.red.opacity(0.8))
                            .cornerRadius(6)
                            .transition(.opacity)
                    }
                    
                    Spacer()
                    
                    // Debug Toggle
                    Button(action: {
                        withAnimation { isDebugMode.toggle() }
                    }) {
                        Image(systemName: isDebugMode ? "ladybug.fill" : "ladybug")
                            .font(.body)
                            .foregroundColor(isDebugMode ? .green : .white)
                            .padding(8)
                            .background(.ultraThinMaterial)
                            .clipShape(Circle())
                    }
                }
                .padding(.top, 50) // Safe Area
                .padding(.horizontal)

                // Debug Zoom Value (Only in Debug Mode)
                if isDebugMode {
                    Text("Zoom: \(String(format: "%.1fx", zoomController.currentZoomFactor))")
                        .font(.caption2)
                        .padding(4)
                        .background(.ultraThinMaterial)
                        .cornerRadius(4)
                        .padding(.top, 4)
                }
                
                Spacer()
                
                // Controls Row
                HStack {
                    // Manual Zoom Toggle / Auto Toggle
                    Button(action: {
                         withAnimation { zoomController.toggleAutoZoom() }
                    }) {
                        Image(systemName: zoomController.isAutoZoomEnabled ? "bolt.circle.fill" : "hand.raised.circle.fill")
                            .font(.largeTitle)
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(zoomController.isAutoZoomEnabled ? .yellow : .white)
                            .background(.ultraThinMaterial)
                            .clipShape(Circle())
                    }
                    .padding(.leading)
                    
                    Spacer()
                    
                    // Record Button
                    Button(action: {
                        if cameraManager.isRecording {
                            stopTimer()
                        } else {
                            startTimer()
                        }
                        cameraManager.toggleRecording()
                    }) {
                        ZStack {
                            Circle()
                                .stroke(Color.white, lineWidth: 4)
                                .frame(width: 70, height: 70)
                            
                            RoundedRectangle(cornerRadius: cameraManager.isRecording ? 8 : 35)
                                .fill(Color.red)
                                .frame(width: cameraManager.isRecording ? 30 : 60, height: cameraManager.isRecording ? 30 : 60)
                                .animation(.spring(), value: cameraManager.isRecording)
                        }
                    }
                    
                    Spacer()
                    
                    // Delete Last Video Button
                    if let _ = cameraManager.lastSavedAssetId, !cameraManager.isRecording {
                        Button(action: {
                            cameraManager.deleteLastVideo()
                        }) {
                            Image(systemName: "trash")
                                .font(.title)
                                .foregroundColor(.red)
                                .padding()
                                .background(.ultraThinMaterial)
                                .clipShape(Circle())
                        }
                        .padding(.trailing)
                        .transition(.scale)
                    } else {
                        // Invisible right placeholder
                        Image(systemName: "trash")
                            .font(.title)
                            .padding()
                            .opacity(0)
                            .padding(.trailing)
                    }
                }
                .padding(.bottom, 30)
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
            
            // Auto-Zoom Loop (Observe detections)
            detector.$detections
                .receive(on: DispatchQueue.main)
                .sink { detections in
                    zoomController.update(detections: detections)
                    cameraManager.setZoom(zoomController.currentZoomFactor)
                }
                .store(in: &cancellables)
            
            // Volume Button Handling
            volumeHandler.$volumeChange
                .dropFirst() // Ignore initial
                .sink { change in
                    if change > 0 {
                        zoomController.manualZoomIn()
                    } else if change < 0 {
                        zoomController.manualZoomOut()
                    }
                    // Apply immediate zoom update
                    cameraManager.setZoom(zoomController.currentZoomFactor)
                }
                .store(in: &cancellables)
        }
        .onDisappear {
            cameraManager.stop()
            stopTimer()
        }
    }
    
    // Timer Logic
    func startTimer() {
        recordingTime = 0
        timer = Timer.publish(every: 1, on: .main, in: .common)
        timerCancellable = timer.autoconnect().sink { _ in
            recordingTime += 1
        }
    }
    
    func stopTimer() {
        timerCancellable?.cancel()
        timerCancellable = nil
    }
    
    func timeString(from interval: TimeInterval) -> String {
        let minutes = Int(interval) / 60
        let seconds = Int(interval) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

// Hidden Volume View to suppress system HUD
struct VolumeView: UIViewRepresentable {
    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: .zero)
        view.alpha = 0.001
        return view
    }
    func updateUIView(_ uiView: MPVolumeView, context: Context) {}
}

// Robust Camera Preview using UIView subclass
struct CameraPreviewLayer: UIViewRepresentable {
    let session: AVCaptureSession
    
    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }
    
    func updateUIView(_ uiView: PreviewView, context: Context) {
        if uiView.videoPreviewLayer.session != session {
            uiView.videoPreviewLayer.session = session
        }
        
        // Update Orientation
        DispatchQueue.main.async {
            guard let connection = uiView.videoPreviewLayer.connection else { return }
            let deviceOrientation = UIDevice.current.orientation
            
            if #available(iOS 17.0, *) {
                let angle: CGFloat
                switch deviceOrientation {
                case .portrait: angle = 90
                case .portraitUpsideDown: angle = 270
                case .landscapeLeft: angle = 180
                case .landscapeRight: angle = 0
                default: angle = 90
                }
                
                if connection.isVideoRotationAngleSupported(angle) {
                    connection.videoRotationAngle = angle
                }
            } else {
                if connection.isVideoOrientationSupported {
                    switch deviceOrientation {
                    case .portrait: connection.videoOrientation = .portrait
                    case .portraitUpsideDown: connection.videoOrientation = .portraitUpsideDown
                    case .landscapeLeft: connection.videoOrientation = .landscapeRight
                    case .landscapeRight: connection.videoOrientation = .landscapeLeft
                    default: break 
                    }
                }
            }
        }
    }
}

class PreviewView: UIView {
    override class var layerClass: AnyClass {
        return AVCaptureVideoPreviewLayer.self
    }
    
    var videoPreviewLayer: AVCaptureVideoPreviewLayer {
        return layer as! AVCaptureVideoPreviewLayer
    }
}
