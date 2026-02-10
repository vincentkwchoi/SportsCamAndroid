# SportsCam Android Auto-Zoom - Development Blueprint

## Overview
This blueprint provides a detailed, step-by-step plan for building the SportsCam Android auto-zoom app. The project is broken down into small, iterative chunks that build on each other, with strong testing at each stage.

> **Note**: This project supports multiple sport modes (Basketball, Ski, Hockey) with sport-specific tracking parameters. The architecture follows MVVM with Clean Architecture using Kotlin and Jetpack Compose.

---

## Project Structure

```
SportsCam-Android/
├── app/src/main/java/com/sportscam/
│   ├── data/
│   │   ├── models/
│   │   │   ├── Detection.kt
│   │   │   ├── Track.kt
│   │   │   ├── ZoomState.kt
│   │   │   └── SportMode.kt
│   ├── domain/
│   │   ├── YOLODetector.kt           # TFLite wrapper
│   │   ├── BBoxActivityDetector.kt   # Activity filtering
│   │   ├── ByteTrackTracker.kt       # Multi-object tracking
│   │   ├── AutoZoomService.kt        # Main orchestrator
│   │   ├── FrameThrottler.kt         # Frame validation
│   │   ├── TargetZoomCalc.kt         # Error calculation
│   │   ├── PIDController.kt          # Smooth zoom control
│   │   ├── ZoomLogScaling.kt         # Physics scaling
│   │   ├── ZoomConstraint.kt         # Safety limits
│   │   ├── HysteresisGate.kt         # Jitter prevention
│   │   └── PanSmoother.kt            # Digital stabilizer
│   ├── presentation/
│   │   ├── viewmodels/
│   │   │   └── CameraViewModel.kt
│   │   ├── ui/
│   │   │   ├── CameraScreen.kt       # Main camera UI
│   │   │   ├── StatusBar.kt
│   │   │   ├── SportModeSelector.kt  # Sport mode dropdown
│   │   │   └── DebugOverlay.kt       # Bounding boxes
│   │   └── camera/
│   │       ├── CameraManager.kt      # CameraX lifecycle
│   │       └── ZoomHardwareActuator.kt # Camera control wrapper
│   └── utils/
│       └── VolumeButtonHandler.kt
└── app/src/main/assets/
    └── yolo.tflite                    # TensorFlow Lite model
```

---

## Development Phases

### Phase 1: Project Setup & Model Conversion (Week 1)
**Goal**: Set up Android project and convert YOLO model to TensorFlow Lite

#### Step 1.1: Create Android Project
**Prompt for LLM:**
```
Create a new Android project named "SportsCam" with the following setup:
- Kotlin + Jetpack Compose
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Enable camera permissions in AndroidManifest.xml
- Add CameraX dependencies
- Create folder structure: data/models/, domain/, presentation/viewmodels/, presentation/ui/, presentation/camera/, utils/
- Set up basic Compose UI with camera preview placeholder
- Add Material3 theme

Test: App launches and shows placeholder UI
```

#### Step 1.2: Export YOLO to TensorFlow Lite (NPU Optimized)
**Prompt for LLM:**
```
Convert the YOLO model to TensorFlow Lite optimized for NPU:
1. Use Python script to export YOLO model:
   - Input: yolov8n.pt (PyTorch)
   - Output: yolov8n_int8.tflite (TensorFlow Lite)
   - Include NMS in model (`nms=True`)
   - Use INT8 quantization (`int8=True`) for NPU speed
   - Input size: 640x640
   - Representative dataset: `coco8.yaml`
2. Verify model exports successfully
3. Check model size (should be <6MB)
4. Test inference on sample image

Command: /opt/homebrew/bin/python3.12 export_yolo_tflite.py

Test: yolov8n_int8.tflite file created and verified.
```

#### Step 1.3: Add TFLite Model to Project
**Prompt for LLM:**
```
Add the exported YOLO TensorFlow Lite model to the Android project:
1. Add yolo.tflite to app/src/main/assets/
2. Add TensorFlow Lite dependencies to build.gradle:
   - org.tensorflow:tensorflow-lite:2.14.0
   - org.tensorflow:tensorflow-lite-gpu:2.14.0
   - org.tensorflow:tensorflow-lite-support:0.4.4
3. Create YOLODetector.kt wrapper class with:
   - init() to load model with GPU delegate
   - detect(bitmap: Bitmap): List<Detection> method
   - Detection data class with: bbox, confidence, classId, trackId
   - Preprocessing (resize to 640x640, normalize)
   - Postprocessing (NMS, confidence filtering, Class 0/Person only)
   - Robust Parsing: Automatic layout detection ([features, anchors] vs [anchors, features])
   - Coordinate Detection: Autodetect LTRB vs CXCYWH formats
4. Add basic error handling for model loading

Test: Model loads successfully, no crashes
```

#### Step 1.4: Create Data Models
**Prompt for LLM:**
```
Create core data models in data/models/:
1. Detection.kt - bbox (RectF), confidence, classId, trackId
2. Track.kt - id, bbox, confidence, age, hits, misses, isActive, state
3. ZoomState.kt - currentZoom, targetZoom, isAutoZoom, mode
4. CameraState.kt - isRecording, recordingDuration, zoomState, detections, activePlayers, fps, showDebug, sportMode
5. SportMode.kt - enum with BASKETBALL, SKI, HOCKEY
   - Each with: displayName, trackBuffer, minConfidence, targetFrameRatio, movementThreshold

Test: All models compile without errors
```

---

### Phase 2: Camera Integration with CameraX (Week 1-2)
**Goal**: Get camera preview working with basic detection

#### Step 2.1: Implement Camera Preview
**Prompt for LLM:**
```
Create CameraManager.kt to handle CameraX:
1. Set up CameraX with Preview + ImageAnalysis use cases
2. Configure for 1080p @ 30fps
3. Request camera permissions (runtime permissions)
4. Provide ImageProxy output via Kotlin Flow
5. Add start/stop camera methods
6. Enable video stabilization
7. Handle camera errors gracefully

Create CameraScreen.kt Compose UI:
1. Display camera preview using PreviewView
2. Show "Camera Permission Required" if denied
3. Add simple start/stop button for testing
4. Use AndroidView to integrate PreviewView

Test: Camera preview shows live feed, no lag
```

#### Step 2.2: Run YOLO Detection on Camera Frames
**Prompt for LLM:**
```
Integrate YOLODetector with CameraManager:
1. In CameraManager, add ImageAnalysis pipeline:
   - Receive ImageProxy from camera
   - Convert to Bitmap and resize to 640x640
   - Run YOLODetector.detect()
   - Emit detections via Flow
2. Throttle detection to 10 FPS (process every 3rd frame)
3. Run detection on background coroutine
4. Measure and log inference time

Test: Detections logged to Logcat, inference <100ms
```

#### Step 2.3: Display Bounding Boxes
**Prompt for LLM:**
```
Create DebugOverlay.kt Compose component:
1. Receive list of Detection objects
2. Convert YOLO bbox coordinates to screen coordinates
3. Draw rectangles using Canvas in Compose
4. Add confidence score labels
5. Use different colors for different confidence levels
6. Optimize drawing for 30 FPS

Update CameraScreen to include overlay:
1. Layer DebugOverlay on top of camera preview using Box
2. Wire up detections from CameraViewModel

Test: Bounding boxes appear on detected people in real-time
```

---

### Phase 3: Activity Filtering & Tracking (Week 3)
**Goal**: Implement ByteTrack and activity filtering

#### Step 3.1: Implement ByteTrack for Android
**Prompt for LLM:**
```
Create ByteTrackTracker.kt:
1. Track detected people across frames using IoU matching
2. Assign consistent tracker IDs
3. Maintain tracker history (configurable buffer: 30-150 frames)
4. Handle tracker creation and deletion
5. Return tracked detections with IDs
6. Support sport-specific track buffer configuration

Update detection pipeline:
1. Pass YOLO detections through ByteTrackTracker
2. Add tracker IDs to Detection objects
3. Display tracker IDs in bounding box labels

Test: People maintain same ID across frames, IDs don't flicker
```

#### Step 3.2: Implement BBox Activity Detector
**Prompt for LLM:**
```
Create BBoxActivityDetector.kt (port from Python):
1. Track bbox movement per tracker ID over last N frames
2. Calculate movement as sum of bbox center displacement
3. Classify as active (movement > threshold) or static
4. Support sport-specific movement thresholds:
   - Basketball: 0.05 (moderate)
   - Ski: 0.08 (higher, background motion)
   - Hockey: 0.06 (fast movement)
5. Implement filter method to remove static detections

Update detection pipeline:
1. Pass tracked detections through activity detector
2. Filter out static people (refs, audience, spectators)
3. Only show active athletes in overlay

Test: Static refs on sideline filtered out, active athletes shown
```

#### Step 3.3: Test Activity Detection
**Prompt for LLM:**
```
Create test scenarios for activity detection:
1. Record test video with camera panning
2. Verify bench players filtered out
3. Verify active athletes shown
4. Test with different sport modes
5. Add debug mode to show all detections vs filtered

Add UI toggle for debug mode:
1. Button to show all detections (red) vs active only (green)
2. Display detection count and filter ratio

Test: Activity filtering works correctly across all sport modes
```

---

### Phase 4: Auto-Zoom Logic (Week 4)
**Goal**: Implement the 10-step "Virtual Dolly" execution pipeline

#### Step 4.1: Implement Control Helpers
**Prompt for LLM:**
```
Create helper components for the auto-zoom pipeline:

1. **FrameThrottler**:
   - `shouldProcess(frameNum): Boolean`
   - Configurable interval (default 1)

2. **TargetZoomCalc**:
   - Calculate error: `targetRatio - (avgSubjectHeight / frameHeight)`
   - Input: List of active tracks

3. **ZoomLogScaling**:
   - Converts linear velocity to log scale
   - `velocity * kZoom * currentZoom`
   - Configurable `kZoom` (default 15.0)

4. **ZoomConstraint**:
   - Clamps zoom level (min 1.0, max 3.0/5.0/20.0 based on sport)
   - Limits max velocity (ramp rate)

5. **ZoomHardwareActuator**:
   - Wrapper for `CameraControl.setLinearZoom`
   - Hides API complexity

Test: Unit tests for each helper class
```

#### Step 4.2: Implement PID Controller
**Prompt for LLM:**
```
Create `PIDController.kt` for smooth zoom control:
1. **Critically Damped Logic**:
   - `velocity = Kp * error + Kd * derivative`
   - `Kd = 2 * sqrt(Kp)` (optional auto-calc or manual tune)
2. **State**:
   - Track `lastError` and `lastTime`
   - Calculate `dt` (delta time) in seconds
3. **Reset**:
   - Clear history on track loss or mode switch

Test: Verify controller output for step input (zero overshoot)
```

#### Step 4.3: Implement Hysteresis & Smoothing
**Prompt for LLM:**
```
Create filtering components:

1. **HysteresisGate**:
   - Deadband logic to prevent jitter
   - `shouldUpdate(error)`
   - High threshold to START (e.g. 0.10)
   - Low threshold to STOP (e.g. 0.05)

2. **PanSmoother** (Optional/Digital):
   - EMA filter for center X/Y
   - `alpha` parameter (0.2 - 0.4)
   - Note: Hardware zoom is center-locked, but good for digital crop backup

Test: Verify gate prevents micro-oscillations
```

#### Step 4.4: Create AutoZoomService
**Prompt for LLM:**
```
Create `AutoZoomService.kt` orchestrator:
1. **Dependencies**: Inject all components (Detector, Tracker, PID, Helpers...)
2. **Pipeline Implementation**:
   - Step 1: Throttle check
   - Step 2: Detection (YOLO)
   - Step 3: Tracking (ByteTrack)
   - Step 4: Selection (Filter active)
   - Step 5: Activity Check
   - Step 6: Error Calc
   - Step 7: Hysteresis Check
   - Step 8: PID Calculation
   - Step 9: Log Scaling
   - Step 10: Constraints
   - Step 11: Hardware Apply
3. **Sport Mode Configuration**:
   - `setSportMode(mode)` updates all parameters (Kp, Kd, thresholds, etc.)

Test: Run full pipeline with mock inputs, verify zoom commands
```

---

### Phase 5: Recording, Controls & Sport Modes (Week 5)
**Goal**: Implement video recording with manual controls and sport selection

#### Step 5.1: Implement Video Recording
**Prompt for LLM:**
```
Update CameraManager for video recording:
1. Add VideoCapture use case to CameraX
2. Configure H.265 encoding
3. Record at 1080p @ 30fps
4. Save to MediaStore (Videos directory)
5. Implement start/stop recording methods
6. Add recording state to CameraState

Test: Can record 30 second video, file size reasonable (<50MB)
```

#### Step 5.2: Implement Sport Mode Selector
**Prompt for LLM:**
```
Create SportModeSelector.kt Compose component:
1. Dropdown menu with Basketball, Ski, Hockey options
2. Show current mode with icon
3. Disable during recording
4. Call viewModel.setSportMode(mode) on selection

Update CameraViewModel:
1. Add setSportMode(mode: SportMode) method
2. Update tracker buffer size
3. Update activity detector threshold
4. Update zoom target ratio
5. Emit state change

Test: Sport mode switches correctly, tracking parameters update
```

#### Step 5.3: Implement Manual Zoom Override
**Prompt for LLM:**
```
Create VolumeButtonHandler.kt:
1. Listen for volume button key events
2. Volume up = zoom in (hold)
3. Volume down = zoom out (hold)
4. When volume buttons pressed:
   - Disable auto-zoom
   - Apply manual zoom
   - Set manual mode flag
5. Add method to re-enable auto-zoom

Update CameraScreen:
1. Start/Stop recording button
2. Auto-zoom toggle button
3. Recording time display
4. Auto-zoom status indicator (Auto/Manual/Locked)
5. Sport mode selector (top-left)
6. Delete last button

Test: Volume buttons control zoom, auto-zoom can be re-enabled
```

---

### Phase 6: UI Polish & Testing (Week 6)
**Goal**: Refine UI and optimize performance

#### Step 6.1: Implement Delete Last Video
**Prompt for LLM:**
```
Add delete functionality:
1. Track last saved video URI
2. Add delete button to CameraScreen
3. Use MediaStore to delete video
4. Show confirmation dialog before deleting
5. Handle delete errors gracefully
6. Disable button if no video to delete

Test: Can delete last recorded video from gallery
```

#### Step 6.2: Optimize Battery Performance
**Prompt for LLM:**
```
Add battery optimizations:
1. Reduce detection FPS when battery <20% (10 → 5 FPS)
2. Disable bounding box overlay when battery <10%
3. Monitor battery level via BatteryManager
4. Add battery level indicator to UI

Test: Record for 60 minutes, measure battery drain (<50%)
```

#### Step 6.3: UI Refinements
**Prompt for LLM:**
```
Polish the UI:
1. Add app icon and splash screen
2. Improve button styling (Material3 design)
3. Add haptic feedback for button presses
4. Show snackbar notifications (recording started, saved, etc.)
5. Improve bounding box visibility (thicker lines, better colors)
6. Add recording indicator (red dot pulsing)
7. Support portrait and landscape orientations
8. Add dark theme support

Test: UI feels polished and professional
```

#### Step 6.4: Real-World Testing
**Prompt for LLM:**
```
Conduct real-world testing:
1. Record actual youth sports events (basketball, ski, hockey)
2. Test in different lighting conditions
3. Test with different camera angles
4. Verify auto-zoom keeps athletes in frame
5. Test sport mode switching
6. Check video quality and smoothness
7. Measure actual battery life
8. Collect feedback from parent users

Document issues and create bug fix list

Test: App works reliably in real game scenarios across all sports
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
- Kalman filter
- Hysteresis gate
- Activity detection logic
- Tracker ID assignment
- Sport mode parameter switching

### Integration Tests
- Camera → Detection pipeline
- Detection → Tracking
- Tracking → Activity filtering
- Activity filtering → Zoom calculation
- Zoom → Camera application

### Manual Tests
- Record 5-minute video in each sport mode
- Test manual override
- Test auto-zoom re-enable
- Test delete functionality
- Test battery performance
- Test sport mode switching

---

## Success Metrics

After each phase, verify:

**Phase 1**: ✅ YOLO model runs on device with TFLite
**Phase 2**: ✅ Camera shows detections in real-time
**Phase 3**: ✅ Static people filtered out correctly
**Phase 4**: ✅ Auto-zoom keeps athletes in frame
**Phase 5**: ✅ Can record and save videos, sport modes work
**Phase 6**: ✅ App ready for real-world use

---

## Sport Mode Configurations

| Sport | Track Buffer | Movement Threshold | Use Case |
|-------|-------------|-------------------|----------|
| Basketball | 150 frames | 0.05 | Team play, long occlusions |
| Ski | 30 frames | 0.08 | Individual runs, background motion |
| Hockey | 100 frames | 0.06 | Fast movement, medium occlusions |

---

## Risk Mitigation

### Performance Risks
- **Risk**: YOLO too slow on device
- **Mitigation**: Use GPU delegate, reduce input size, throttle FPS

### Battery Risks
- **Risk**: Battery drains too fast
- **Mitigation**: Reduce FPS, optimize detection pipeline, adaptive quality

### Accuracy Risks
- **Risk**: Activity detection filters out active athletes
- **Mitigation**: Tune threshold per sport, add manual override

---

## Final Deliverable

A production-ready Android app that:
1. ✅ Detects active athletes in real-time
2. ✅ Automatically zooms to keep athletes in frame
3. ✅ Supports Basketball, Ski, and Hockey modes
4. ✅ Filters out refs and audience
5. ✅ Records smooth, professional-looking videos
6. ✅ Allows manual zoom override
7. ✅ Saves to MediaStore automatically
8. ✅ Works for 60+ minutes on battery

---

## Next Steps

1. Review this blueprint
2. Set up development environment (Android Studio)
3. Start with Phase 1, Step 1.1
4. Follow prompts sequentially
5. Test thoroughly at each step
6. Iterate until complete

**Ready to build!** 🚀
