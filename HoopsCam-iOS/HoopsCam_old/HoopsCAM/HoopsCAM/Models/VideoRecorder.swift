import AVFoundation
import UIKit

class VideoRecorder: NSObject {
    private var assetWriter: AVAssetWriter?
    private var videoInput: AVAssetWriterInput?
    private var audioInput: AVAssetWriterInput?
    
    private var isRecording = false
    private var startTime: CMTime = .zero
    private var outputURL: URL?
    private var sessionStarted = false
    
    // Config
    private var videoSize = CGSize(width: 1080, height: 1920) // Default Portrait
    
    func startRecording(size: CGSize) {
        self.videoSize = size
        
        let tempDir = FileManager.default.temporaryDirectory
        let fileName = "recording-\(Date().timeIntervalSince1970).mov"
        outputURL = tempDir.appendingPathComponent(fileName)
        
        guard let url = outputURL else { return }
        
        // Remove existing
        try? FileManager.default.removeItem(at: url)
        
        do {
            assetWriter = try AVAssetWriter(outputURL: url, fileType: .mov)
        } catch {
            print("Failed to create AssetWriter: \(error)")
            return
        }
        
        // Video Input
        let videoSettings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: videoSize.width,
            AVVideoHeightKey: videoSize.height
        ]
        
        videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        videoInput?.expectsMediaDataInRealTime = true
        if let videoInput = videoInput, assetWriter?.canAdd(videoInput) == true {
            assetWriter?.add(videoInput)
        }
        
        // Audio Input
        let audioSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVNumberOfChannelsKey: 1,
            AVSampleRateKey: 44100,
            AVEncoderBitRateKey: 64000
        ]
        
        audioInput = AVAssetWriterInput(mediaType: .audio, outputSettings: audioSettings)
        audioInput?.expectsMediaDataInRealTime = true
        if let audioInput = audioInput, assetWriter?.canAdd(audioInput) == true {
            assetWriter?.add(audioInput)
        }
        
        if assetWriter?.startWriting() == true {
            isRecording = true
            sessionStarted = false
            print("🎙️ Start Recording (Waiting for first buffer)")
        } else {
             print("❌ Failed to start writing: \(assetWriter?.error?.localizedDescription ?? "Unknown")")
        }
    }
    
    func stopRecording(completion: @escaping (URL) -> Void) {
        guard isRecording, let assetWriter = assetWriter else { return }
        
        isRecording = false
        sessionStarted = false
        videoInput?.markAsFinished()
        audioInput?.markAsFinished()
        
        assetWriter.finishWriting { [weak self] in
            guard let self = self, let url = self.outputURL else { return }
            print("💾 Recording Finished: \(url)")
            DispatchQueue.main.async {
                completion(url)
            }
        }
    }
    
    func appendVideoBuffer(_ sampleBuffer: CMSampleBuffer) {
        guard isRecording, let writer = assetWriter, let input = videoInput, input.isReadyForMoreMediaData else { return }
        
        if !sessionStarted {
            let timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            writer.startSession(atSourceTime: timestamp)
            sessionStarted = true
            // Also ensure we set startTime for session if needed (but AVAssetWriter handles relative time from startSession)
        }
        
        if writer.status == .writing {
            input.append(sampleBuffer)
        }
    }
    
    func appendAudioBuffer(_ sampleBuffer: CMSampleBuffer) {
        guard isRecording, let writer = assetWriter, let input = audioInput, input.isReadyForMoreMediaData else { return }
        
        if !sessionStarted {
            // Usually we wait for video to start session to avoid black frames or sync issues?
            // Or we start session with Audio?
            // If we start with Audio, timestamp might be slightly before Video.
            // Safe approach: Initialize session on FIRST buffer (Audio OR Video).
            let timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            writer.startSession(atSourceTime: timestamp)
            sessionStarted = true
        }
        
        if writer.status == .writing {
            input.append(sampleBuffer)
        }
    }
}
