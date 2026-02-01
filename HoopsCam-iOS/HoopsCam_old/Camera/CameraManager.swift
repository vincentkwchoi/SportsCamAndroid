import AVFoundation
import Combine
import UIKit

class CameraManager: NSObject, ObservableObject {
    private let captureSession = AVCaptureSession()
    private var videoOutput = AVCaptureVideoDataOutput()
    
    // Publisher for video frames (CVPixelBuffer)
    let framePublisher = PassthroughSubject<CVPixelBuffer, Never>()
    
    @Published var isAuthorized = false
    @Published var isRunning = false
    
    override init() {
        super.init()
        checkPermissions()
    }
    
    private func checkPermissions() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            self.isAuthorized = true
            setupSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    self?.isAuthorized = granted
                    if granted {
                        self?.setupSession()
                    }
                }
            }
        default:
            self.isAuthorized = false
        }
    }
    
    private func setupSession() {
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .hd1920x1080 // 1080p
        
        do {
            // Setup input
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else { return }
            let input = try AVCaptureDeviceInput(device: device)
            
            if captureSession.canAddInput(input) {
                captureSession.addInput(input)
            }
            
            // Setup output
            videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)]
            videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "camera.queue"))
            
            if captureSession.canAddOutput(videoOutput) {
                captureSession.addOutput(videoOutput)
                // Fix orientation for portrait mode app
                videoOutput.connection(with: .video)?.videoOrientation = .portrait
            }
            
        } catch {
            print("Failed to setup camera: \(error.localizedDescription)")
        }
        
        captureSession.commitConfiguration()
        start()
    }
    
    func start() {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            if let self_ = self, !self_.captureSession.isRunning {
                self_.captureSession.startRunning()
                DispatchQueue.main.async { self_.isRunning = true }
            }
        }
    }
    
    func stop() {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            if let self_ = self, self_.captureSession.isRunning {
                self_.captureSession.stopRunning()
                DispatchQueue.main.async { self_.isRunning = false }
            }
        }
    }
}

extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        framePublisher.send(pixelBuffer)
    }
}
