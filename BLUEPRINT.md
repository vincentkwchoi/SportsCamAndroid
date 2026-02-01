# HoopsCam iOS Auto-Zoom - Development Blueprint

## Overview
This blueprint provides a detailed, step-by-step plan for building the HoopsCam iOS auto-zoom app. The project is broken down into small, iterative chunks that build on each other, with strong testing at each stage.

> **Note**: This project follows the architecture, UI/UX patterns, and control schemes of the existing **SkiCameraIOS** project. While the auto-zoom algorithm is different (Basketball/YOLO vs Skiing), the application structure should mirror SkiCameraIOS.

---

## Project Structure

```
HoopsCam-iOS/  # Structure mirrors SkiCameraIOS
├── HoopsCam/
│   ├── Models/
│   │   ├── YOLODetector.swift          # YOLO11x CoreML wrapper
│   │   ├── BBoxActivityDetector.swift  # Activity filtering
│   │   └── PlayerTracker.swift         # ByteTrack implementation
│   ├── Camera/
│   │   ├── CameraManager.swift         # AVFoundation camera control
│   │   ├── ZoomController.swift        # Auto-zoom logic
│   │   └── VideoRecorder.swift         # Recording management
│   ├── Views/
│   │   ├── CameraView.swift            # Main camera UI
│   │   ├── BoundingBoxOverlay.swift    # Player boxes overlay
│   │   └── ControlsView.swift          # Recording controls
│   └── Utils/
│       ├── GeometryUtils.swift         # Bbox calculations
│       └── SmoothingFilter.swift       # Zoom smoothing
└── Resources/
    └── yolo11x.mlmodel                 # CoreML model
```

---

## Development Phases

### Phase 1: Project Setup & Model Conversion (Week 1)
**Goal**: Set up iOS project and convert YOLO model to CoreML

#### Step 1.1: Create iOS Project
**Prompt for LLM:**
```
Create a new iOS project named "HoopsCam" with the following setup:
- SwiftUI app for iOS 16+
- Target: iPhone only (no iPad)
- Enable camera permissions in Info.plist
- Add AVFoundation framework
- Create basic folder structure: Models/, Camera/, Views/, Utils/ (mirror SkiCameraIOS)
- Set up a simple SwiftUI view with camera preview placeholder
- Reference SkiCameraIOS for project settings and capabilities

Test: App launches and shows placeholder UI
```

#### Step 1.2: Export YOLO11x to CoreML
**Prompt for LLM:**
```
Using the existing YOLO11x PyTorch model from the Python pipeline:
1. Install coremltools: pip install coremltools
2. Create a Python script to export YOLO11x to CoreML format:
   - Load yolo11x.pt model
   - Export to CoreML with NMS (Non-Maximum Suppression)
   - Optimize for iPhone Neural Engine
   - Target input size: 640x640
   - Output: yolo11x.mlmodel file
3. Verify the model exports successfully
4. Check model size (should be <100MB)

Test: yolo11x.mlmodel file created and can be inspected with Xcode
```

#### Step 1.3: Add CoreML Model to Project
**Prompt for LLM:**
```
Add the exported YOLO11x CoreML model to the iOS project:
1. Drag yolo11x.mlmodel into Xcode Resources folder
2. Verify Xcode auto-generates Swift model class
3. Create YOLODetector.swift wrapper class with:
   - init() to load model
   - detect(image: CVPixelBuffer) -> [Detection] method
   - Detection struct with: bbox, confidence, classId
4. Add basic error handling for model loading

Test: Model loads successfully in simulator, no crashes
```

---

### Phase 2: Basic Camera Integration (Week 1-2)
**Goal**: Get camera preview working with basic detection

#### Step 2.1: Implement Camera Preview
**Prompt for LLM:**
```
Create CameraManager.swift to handle AVFoundation camera:
1. Set up AVCaptureSession with video output
2. Configure for 1080p @ 30fps (balance quality/performance)
3. Request camera permissions
4. Provide CVPixelBuffer output via Combine publisher
5. Add start/stop session methods
6. Handle camera errors gracefully

Create CameraView.swift SwiftUI view:
1. Display camera preview using AVCaptureVideoPreviewLayer
2. Show "Camera Permission Required" if denied
3. Add simple start/stop button for testing

Test: Camera preview shows live feed, no lag
```

#### Step 2.2: Run YOLO Detection on Camera Frames
**Prompt for LLM:**
```
Integrate YOLODetector with CameraManager:
1. In CameraManager, add detection pipeline:
   - Receive CVPixelBuffer from camera
   - Resize to 640x640 for YOLO input
   - Run YOLODetector.detect()
   - Publish detections via Combine
2. Throttle detection to 15 FPS (every 2nd frame)
3. Run detection on background queue
4. Measure and log inference time

Test: Detections logged to console, inference <100ms
```

#### Step 2.3: Display Bounding Boxes
**Prompt for LLM:**
```
Create BoundingBoxOverlay.swift SwiftUI view:
1. Receive array of Detection objects
2. Convert YOLO bbox coordinates to screen coordinates
3. Draw rectangles over camera preview
4. Add confidence score labels
5. Use different colors for different confidence levels
6. Optimize drawing for 30 FPS

Update CameraView to include overlay:
1. Layer BoundingBoxOverlay on top of camera preview
2. Wire up detections from CameraManager

Test: Bounding boxes appear on detected people in real-time
```

---

### Phase 3: Activity Filtering (Week 3)
**Goal**: Port bbox activity detector to filter static people

#### Step 3.1: Implement ByteTrack for iOS
**Prompt for LLM:**
```
Create PlayerTracker.swift (simplified ByteTrack):
1. Track detected players across frames using IOU matching
2. Assign consistent tracker IDs
3. Maintain tracker history (last 30 frames)
4. Handle tracker creation and deletion
5. Return tracked detections with IDs

Update detection pipeline:
1. Pass YOLO detections through PlayerTracker
2. Add tracker IDs to Detection struct
3. Display tracker IDs in bounding box labels

Test: Players maintain same ID across frames, IDs don't flicker
```

#### Step 3.2: Port BBox Activity Detector
**Prompt for LLM:**
```
Create BBoxActivityDetector.swift (port from Python):
1. Track bbox features per tracker ID:
   - Width, height, area, aspect ratio
2. Calculate shape variance over last 10 frames
3. Classify as active (variance > 0.15) or static
4. Implement filter method to remove static detections
5. Match Python implementation logic exactly

Update detection pipeline:
1. Pass tracked detections through activity detector
2. Filter out static people (refs, audience)
3. Only show active players in overlay

Test: Static refs on sideline filtered out, active players shown
```

#### Step 3.3: Test Activity Detection
**Prompt for LLM:**
```
Create test scenarios for activity detection:
1. Record test video with camera panning
2. Verify bench players filtered out
3. Verify active players on court shown
4. Tune threshold if needed (currently 0.15)
5. Add debug mode to show all detections vs filtered

Add UI toggle for debug mode:
1. Button to show all detections (red) vs active only (green)
2. Display detection count and filter ratio

Test: Activity filtering works correctly with camera pan
```

---

### Phase 4: Auto-Zoom Logic (Week 4)
**Goal**: Calculate and apply optimal zoom level

#### Step 4.1: Calculate Optimal Zoom
**Prompt for LLM:**
```
Create ZoomController.swift with zoom calculation:
1. Input: Array of active player bboxes
2. Calculate bounding box containing 80% of players:
   - Sort players by distance from center
   - Take closest 80% (round up)
   - Calculate min/max x/y to contain them
3. Add 10% padding around bbox
4. Convert to zoom factor (1.0 = no zoom, 2.0 = 2x)
5. Clamp zoom to camera limits (1.0 - 5.0)

Add GeometryUtils.swift helper:
1. calculateBoundingBox(points: [CGPoint]) -> CGRect
2. calculateZoomFactor(bbox: CGRect, viewSize: CGSize) -> Float
3. Unit tests for geometry calculations

Test: Zoom factor calculated correctly for various player positions
```

#### Step 4.2: Implement Smooth Zoom Transitions
**Prompt for LLM:**
```
Add smooth zoom to ZoomController:
1. Create SmoothingFilter.swift:
   - Exponential moving average for zoom values
   - Configurable smoothing factor (0.2 = smooth, 0.8 = responsive)
   - Prevent jitter with deadband (±0.05 zoom)
2. Apply smoothing to calculated zoom factor
3. Limit zoom change rate (max 0.1 per frame)

Update ZoomController:
1. Smooth zoom transitions over time
2. Add min/max zoom constraints
3. Handle edge cases (no players detected)

Test: Zoom transitions are smooth and cinematic, not jarring
```

#### Step 4.3: Apply Zoom to Camera
**Prompt for LLM:**
```
Integrate ZoomController with CameraManager:
1. Add setZoom(factor: Float) method to CameraManager
2. Use AVCaptureDevice.videoZoomFactor
3. Apply zoom on main thread (camera requirement)
4. Handle zoom limits from device capabilities

Wire up auto-zoom pipeline:
1. Active detections → ZoomController → Camera zoom
2. Run at 30 FPS
3. Add auto-zoom enable/disable flag

Test: Camera zooms automatically to keep players in frame
```

---

### Phase 5: Recording & Controls (Week 5)
**Goal**: Implement video recording with manual controls

#### Step 5.1: Implement Video Recording
**Prompt for LLM:**
```
Create VideoRecorder.swift:
1. Use AVAssetWriter for video recording
2. Configure H.265 encoding (efficient compression)
3. Record at 1080p @ 30fps
4. Save to temporary directory during recording
5. Implement start/stop recording methods
6. Add recording state (idle, recording, saving)

Update CameraManager:
1. Pipe camera frames to VideoRecorder when recording
2. Apply zoom to recorded video (not just preview)
3. Handle recording errors gracefully

Test: Can record 30 second video, file size reasonable (<50MB)
```

#### Step 5.2: Save to Photos App
**Prompt for LLM:**
```
Add Photos integration to VideoRecorder:
1. Request Photos library permission
2. Use PHPhotoLibrary to save video
3. Move from temp directory to Photos
4. Clean up temp files after save
5. Handle save errors (storage full, permission denied)
6. Show save confirmation to user

Test: Recorded video appears in Photos app immediately
```

#### Step 5.3: Implement Manual Zoom Override
**Prompt for LLM:**
```
Add manual zoom control to CameraManager:
1. Listen for volume button presses:
   - Volume up = zoom in
   - Volume down = zoom out
2. When volume buttons pressed:
   - Disable auto-zoom
   - Apply manual zoom (0.1 per press)
   - Set manual mode flag
3. Add method to re-enable auto-zoom

Create ControlsView.swift:
1. Start/Stop recording button
2. Auto-zoom toggle button (re-enable after manual)
3. Recording time display
4. Auto-zoom status indicator (Auto/Manual)

*Reference SkiCameraIOS for control layout and styling.*

Test: Volume buttons control zoom, auto-zoom can be re-enabled
```

---

### Phase 6: UI Polish & Testing (Week 6)
**Goal**: Refine UI and optimize performance

#### Step 6.1: Implement Delete Last Video
**Prompt for LLM:**
```
Add delete functionality:
1. Track last saved video asset ID
2. Add delete button to ControlsView
3. Use PHPhotoLibrary to delete asset
4. Show confirmation alert before deleting
5. Handle delete errors gracefully
6. Disable button if no video to delete

Test: Can delete last recorded video from Photos
```

#### Step 6.2: Optimize Battery Performance
**Prompt for LLM:**
```
Add battery optimizations:
1. Reduce detection FPS when battery <20% (15 → 10 FPS)
2. Use lower resolution for detection (640x640 → 416x416)
3. Disable bounding box overlay when battery <10%
4. Monitor battery level and adjust automatically
5. Add battery level indicator to UI

Test: Record for 60 minutes, measure battery drain (<50%)
```

#### Step 6.3: UI Refinements
**Prompt for LLM:**
```
Polish the UI:
1. Add app icon and launch screen
2. Improve button styling (larger, clearer)
3. Add haptic feedback for button presses
4. Show toast notifications (recording started, saved, etc.)
5. Add dark mode support
6. Improve bounding box visibility (thicker lines, better colors)
7. Add recording indicator (red dot)

*Ensure UI look-and-feel matches SkiCameraIOS.*

Test: UI feels polished and professional
```

#### Step 6.4: Real-World Testing
**Prompt for LLM:**
```
Conduct real-world testing:
1. Record actual youth basketball game
2. Test in different lighting conditions
3. Test with different camera angles (stands, sideline)
4. Verify auto-zoom keeps players in frame
5. Check video quality and smoothness
6. Measure actual battery life
7. Collect feedback from parent users

Document issues and create bug fix list

Test: App works reliably in real game scenarios
```

---

## Iteration Strategy

After each step:
1. **Implement** the feature
2. **Test** thoroughly (unit tests + manual testing)
3. **Verify** it works as expected
4. **Commit** to version control
5. **Move to next step**

If a step fails:
1. Debug and fix issues
2. Re-test
3. Don't proceed until working

---

## Testing Strategy

### Unit Tests
- Geometry calculations (bbox, zoom factor)
- Smoothing filter
- Activity detection logic
- Tracker ID assignment

### Integration Tests
- Camera → Detection pipeline
- Detection → Activity filtering
- Activity filtering → Zoom calculation
- Zoom → Camera application

### Manual Tests
- Record 5-minute video
- Test manual override
- Test auto-zoom re-enable
- Test delete functionality
- Test battery performance

---

## Success Metrics

After each phase, verify:

**Phase 1**: ✅ YOLO model runs on device
**Phase 2**: ✅ Camera shows detections in real-time
**Phase 3**: ✅ Static people filtered out correctly
**Phase 4**: ✅ Auto-zoom keeps 80% players in frame
**Phase 5**: ✅ Can record and save videos
**Phase 6**: ✅ App ready for real-world use

---

## Risk Mitigation

### Performance Risks
- **Risk**: YOLO too slow on device
- **Mitigation**: Use smaller model (YOLO11n) or lower resolution

### Battery Risks
- **Risk**: Battery drains too fast
- **Mitigation**: Reduce FPS, optimize detection pipeline

### Accuracy Risks
- **Risk**: Activity detection filters out active players
- **Mitigation**: Tune threshold, add manual override

---

## Final Deliverable

A production-ready iOS app that:
1. ✅ Detects active basketball players in real-time
2. ✅ Automatically zooms to keep 80% in frame
3. ✅ Filters out refs and audience
4. ✅ Records smooth, professional-looking videos
5. ✅ Allows manual zoom override
6. ✅ Saves to Photos app automatically
7. ✅ Works for 60+ minutes on battery

---

## Next Steps

1. Review this blueprint
2. Set up development environment
3. Start with Phase 1, Step 1.1
4. Follow prompts sequentially
5. Test thoroughly at each step
6. Iterate until complete

**Ready to build!** 🚀
