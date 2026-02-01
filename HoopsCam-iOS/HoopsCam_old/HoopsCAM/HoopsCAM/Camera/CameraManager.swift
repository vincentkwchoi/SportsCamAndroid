import AVFoundation
import UIKit
import Combine
import Photos

class CameraManager: NSObject, ObservableObject {
    let captureSession = AVCaptureSession()
    private var videoOutput = AVCaptureVideoDataOutput()
    
    // Publisher for video frames (CVPixelBuffer)
    let framePublisher = PassthroughSubject<CVPixelBuffer, Never>()
    
    @Published var isAuthorized = false
    @Published var isRunning = false
    
    // -- Phase 5: Recording --
    private let videoRecorder = VideoRecorder()
    private let audioOutput = AVCaptureAudioDataOutput()
    private let audioQueue = DispatchQueue(label: "audio.queue")
    @Published var isRecording = false
    
    override init() {
        super.init()
        checkPermissions()
    }
    
    private func checkPermissions() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            isAuthorized = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { self.isAuthorized = granted }
            }
        default:
            isAuthorized = false
        }
        
        // Audio Permission
        AVCaptureDevice.requestAccess(for: .audio) { _ in }
    }
    
    func setupCamera() { // Renamed from setupSession
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .hd1920x1080 // 1080p // Moved from original setupSession
        
        // Input Discovery Priority:
        // 1. Triple Camera (Pro Models: 0.5x, 1x, 3x/5x) - Best zoom range
        // 2. Dual Wide Camera (Base Models: 0.5x, 1x)
        // 3. Wide Angle Camera (Old Models: 1x only)
        
        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInTripleCamera, .builtInDualWideCamera, .builtInWideAngleCamera],
            mediaType: .video,
            position: .back
        )
        
        guard let videoDevice = discoverySession.devices.first,
              let videoInput = try? AVCaptureDeviceInput(device: videoDevice),
              let audioDevice = AVCaptureDevice.default(for: .audio),
              let audioInput = try? AVCaptureDeviceInput(device: audioDevice)
        else { return }
        
        if captureSession.canAddInput(videoInput) { captureSession.addInput(videoInput) }
        if captureSession.canAddInput(audioInput) { captureSession.addInput(audioInput) }
        
        // Video Output
        videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)]
        videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "camera.queue"))
        
        if captureSession.canAddOutput(videoOutput) {
            captureSession.addOutput(videoOutput)
            // Set initial orientation
            if let connection = videoOutput.connection(with: .video) {
                if #available(iOS 17.0, *) {
                    if connection.isVideoRotationAngleSupported(90) {
                        connection.videoRotationAngle = 90 // Default Portrait
                    }
                } else {
                    if connection.isVideoOrientationSupported {
                        connection.videoOrientation = .portrait
                    }
                }
            }
        }
        
        // Audio Output
        audioOutput.setSampleBufferDelegate(self, queue: audioQueue)
        if captureSession.canAddOutput(audioOutput) {
            captureSession.addOutput(audioOutput)
        }
        
        captureSession.commitConfiguration()
        // start() // Removed from here, now called from start() method
    }
    
    // -- Phase 4: Zoom Control --
    func setZoom(_ factor: CGFloat) {
        // Must discover ANY active back camera input to control zoom
        guard let videoInput = captureSession.inputs.first(where: { ($0 as? AVCaptureDeviceInput)?.device.position == .back }) as? AVCaptureDeviceInput else { return }
        let device = videoInput.device
        
        do {
            try device.lockForConfiguration()
            // Some devices report huge max zoom (e.g. 15x digital). Clamp to 5.0x for quality.
            let maxZoom = min(device.activeFormat.videoMaxZoomFactor, 5.0)
            let minZoom = device.minAvailableVideoZoomFactor // usually 1.0 or 0.5 depending on device
            
            let clampedFactor = max(minZoom, min(factor, maxZoom))
            device.videoZoomFactor = clampedFactor
            device.unlockForConfiguration()
        } catch {
            print("Zoom error: \(error)")
        }
    }
    
    func start() {
        // Start Orientation Observer
        NotificationCenter.default.addObserver(self, selector: #selector(orientationChanged), name: UIDevice.orientationDidChangeNotification, object: nil)
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            if let self_ = self, !self_.captureSession.isRunning {
                self_.setupCamera() // Ensure setup is called only once or properly managed?
                // Actually setupCamera initializes inputs again if called repeatedly? 
                // Better to move input setup to separate method or guard it.
                // But for now, let's just start running.
                self_.captureSession.startRunning()
                DispatchQueue.main.async { self_.isRunning = true }
                
                // Set initial orientation
                self_.updateOrientation()
            }
        }
    }
    
    func stop() {
        NotificationCenter.default.removeObserver(self)
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            if let self_ = self, self_.captureSession.isRunning {
                self_.captureSession.stopRunning()
                DispatchQueue.main.async { self_.isRunning = false }
            }
        }
    }
    
    @objc private func orientationChanged() {
        updateOrientation()
    }
    
    private func updateOrientation() {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let deviceOrientation = UIDevice.current.orientation
            
            guard let connection = self.videoOutput.connection(with: .video) else { return }

            if #available(iOS 17.0, *) {
                // iOS 17+: Use Rotation Angle
                let angle = self.getRotationAngle(for: deviceOrientation)
                if connection.isVideoRotationAngleSupported(angle) {
                    if connection.videoRotationAngle != angle {
                        connection.videoRotationAngle = angle
                        print("🔄 Camera Rotation Updated: \(angle) degrees")
                    }
                }
            } else {
                // iOS 16 legacy
                if connection.isVideoOrientationSupported {
                    let videoOrientation: AVCaptureVideoOrientation?
                    switch deviceOrientation {
                    case .portrait: videoOrientation = .portrait
                    case .portraitUpsideDown: videoOrientation = .portraitUpsideDown
                    case .landscapeLeft: videoOrientation = .landscapeRight // Camera is opposite
                    case .landscapeRight: videoOrientation = .landscapeLeft
                    default: videoOrientation = nil
                    }
                    
                    if let orientation = videoOrientation {
                        connection.videoOrientation = orientation
                        print("🔄 Camera Orientation Updated: \(orientation.rawValue)")
                    }
                }
            }
        }
    }
    
    private func getRotationAngle(for orientation: UIDeviceOrientation) -> CGFloat {
        switch orientation {
        case .portrait: return 90
        case .portraitUpsideDown: return 270
        case .landscapeLeft: return 180 // Home button right -> Camera rotated 180? Wait.
        // Standard mapping:
        // Portrait (Home Bottom) -> 90
        // Landscape Left (Home Right) -> 180
        // Landscape Right (Home Left) -> 0
        // Upside Down -> 270
        case .landscapeRight: return 0
        default: return 90
        }
    }
    
    // -- Recording Control --
    func toggleRecording() {
        if isRecording {
            isRecording = false
            videoRecorder.stopRecording { [weak self] url in
                self?.saveToPhotoLibrary(url: url)
            }
        } else {
            isRecording = true
            
            // Determine video size based on current interface orientation
            let orientation = UIDevice.current.orientation
            let isLandscape = orientation.isLandscape
            let size = isLandscape ? CGSize(width: 1920, height: 1080) : CGSize(width: 1080, height: 1920)
            
            videoRecorder.startRecording(size: size)
        }
    }
    
    @Published var lastSavedAssetId: String? = nil
    
    private func saveToPhotoLibrary(url: URL) {
        PHPhotoLibrary.requestAuthorization { status in
            guard status == .authorized else { return }
            
            var placeholder: PHObjectPlaceholder?
            
            PHPhotoLibrary.shared().performChanges({
                let request = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
                placeholder = request?.placeholderForCreatedAsset
            }) { success, error in
                if success, let id = placeholder?.localIdentifier {
                    print("✅ Video saved to Photos. ID: \(id)")
                    DispatchQueue.main.async {
                        self.lastSavedAssetId = id
                    }
                } else {
                    print("❌ Failed to save video: \(error?.localizedDescription ?? "Unknown error")")
                }
            }
        }
    }
    
    func deleteLastVideo() {
        guard let id = lastSavedAssetId else { return }
        
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
        guard let asset = assets.firstObject else { return }
        
        PHPhotoLibrary.shared().performChanges({
            PHAssetChangeRequest.deleteAssets([asset] as NSFastEnumeration)
        }) { success, error in
            if success {
                print("🗑️ Deleted last video.")
                DispatchQueue.main.async {
                    self.lastSavedAssetId = nil // Clear after delete
                }
            } else {
                print("❌ Delete failed: \(error?.localizedDescription ?? "Unknown")")
            }
        }
    }
    
}

extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        
        if output == videoOutput {
             guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
             framePublisher.send(pixelBuffer) // Kept framePublisher as per original declaration
             
             // Record Video Frame
             if isRecording {
                 videoRecorder.appendVideoBuffer(sampleBuffer)
             }
        } else if output == audioOutput {
             // Record Audio Frame
             if isRecording {
                 videoRecorder.appendAudioBuffer(sampleBuffer)
             }
        }
    }
}
