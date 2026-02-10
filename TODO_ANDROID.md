# SportsCam Android - Development Checklist

## Phase 1: Project Setup & Core Models (Week 1)
- [x] **1.1 Create Android Project**
  - [x] Initialize "SportsCam" Kotlin project (Min SDK 24, Target SDK 34)
  - [x] Configure `build.gradle` (CameraX, TFLite, Compose, Material3)
  - [x] Set up folder structure (`domain`, `data`, `presentation`)
  - [x] Add Permissions (`CAMERA`, `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE`)

- [x] **1.2 Export Model**
  - [x] Export YOLOv8n to TFLite (`yolov8n_int8.tflite`)
  - [x] Verify model inputs (640x640) and outputs
  - [x] Add model to `assets/` folder

- [x] **1.3 Implement Data Models**
  - [x] Create `Detection.kt` (bbox, conf, classId)
  - [x] Create `Track.kt` (id, state, history)
  - [x] Create `SportMode.kt` Enum (Basketball, Ski, Hockey params)
  - [x] Create `ZoomState.kt` & `CameraState.kt`

## Phase 2: CameraX Integration (Week 1-2)
- [x] **2.1 Camera Infrastructure**
  - [x] Create `CameraManager.kt`
  - [x] Implement `startCamera()` with Preview Use Case
  - [x] Implement `ImageAnalysis` Use Case (for YOLO)
  - [ ] Enable Video Stabilization (SPEC Q19 - accounting for stabilized FOV)

- [x] **2.2 Detection Pipeline**
  - [x] Create `YOLODetector.kt` Wrapper (TFLite Interpreter)
  - [x] Implement `detect()` with GPU Delegate
  - [x] Wire Camera Frame → YOLODetector
  - [x] Throttle analysis (e.g., every 3rd frame)
  - [x] Robust TFLite Parsing (Layout-agnostic, multi-class support)
  - [x] Coordinate Format Autodetection (Corner vs Center)

- [x] **2.3 Basic UI**
  - [x] Create `CameraScreen.kt` with `PreviewView`
  - [x] Implement `DebugOverlay.kt` (Draw Bounding Boxes)
  - [x] Wiring: View → ViewModel → CameraManager
  - [x] **Verify**: Real-time bounding boxes visible on screen

## Phase 3: Tracking & Filtering (Week 3)
- [ ] **3.1 Multi-Object Tracking**
  - [x] Implement `ByteTrackTracker.kt` (IoU Logic)
  - [ ] Implement `KalmanFilter1D.kt` for zoom and track smoothing (MISSING FROM SPEC)
  - [x] Integrate Tracker into `AutoZoomService` loop (Integrated into VM loop)

- [x] **3.2 Activity Filtering**
  - [x] Create `BBoxActivityDetector.kt`
  - [x] Implement Movement Calculation (Center displacement)
  - [x] Tune Thresholds for Sport Modes
  - [ ] **Verify**: Static objects (Refs, Audience) are ignored

## Phase 4: Auto-Zoom Logic (Week 4) - *Critical*
- [x] **4.1 Helper Components**
  - [x] `TargetZoomCalc`: Calculate error from target ratio (0.33)
  - [x] `ZoomLogScaling`: Implement physics scaling formula
  - [x] `ZoomConstraint`: Implement Min/Max clamping
  - [x] `FrameThrottler`: Implement logic (Basic impl provided)

- [x] **4.2 PID Controller**
  - [x] Create `PIDController.kt`
  - [x] Implement Critically Damped logic (`Kp`, `Kd`)
  - [x] Unit Test: Verify smooth convergence without overshoot (Logic verified vs Spec)

- [x] **4.3 Hysteresis & Service**
  - [x] Create `HysteresisGate.kt` (Deadband 5%-10%)
  - [x] Implement `AutoZoomService.kt` (The 10-step pipeline)
  - [x] Integrate `ZoomHardwareActuator` with CameraX

## Phase 5: Controls & Recording (Week 5)
- [x] **5.1 Video Recording**
  - [x] Add `VideoCapture` Use Case to `CameraManager`
  - [x] Implement Start/Stop Recording (MediaStore - Movies/SportsCam/)
  - [x] Ensure Audio is captured (Enabled in builder)

- [x] **5.2 Sport Mode UI**
  - [x] Create `SportModeSelector.kt` (Dropdown)
  - [x] Wire to ViewModel to update Algorithm params

- [x] **5.3 Manual Overrides**
  - [ ] Implement `VolumeButtonHandler.kt` (MISSING FROM SPEC)
  - [x] UI button used for temporary override
  - [ ] Volume Up/Down → Manual Zoom & Disable Auto (Hardware integration)
  - [ ] Double-tap Volume → Start/Stop Recording (Hardware integration)

## Phase 6: Polish & Release (Week 6)
- [ ] **6.1 Battery Optimization** (SPEC 5.3)
  - [ ] Reduced FPS mode when battery low
  - [ ] Profile memory usage (< 50MB budget per SPEC 5.2)
  - [ ] Release resources when app backgrounded

- [ ] **6.2 UI Experience & Rotation** (SPEC Q17, 3.3.3)
  - [ ] Full Landscape/Portrait Rotation support for UI & Video Metadata
  - [ ] Implement Recording Red Dot & Timer (SPEC Q9)
  - [ ] Implement "Delete Last Video" Trash feature (SPEC Q7)
  - [ ] Implement Configuration Dialog (SPEC 3.3.2 - modal for PID tuning)

- [ ] **6.3 Comprehensive Testing** (SPEC 6.0)
  - [ ] Unit Tests (90% coverage for Zoom/Tracking)
  - [ ] Integration Tests (Camera → Detection → Zoom pipeline)
  - [ ] UI Tests (Recording toggle, manual zoom controls)
  - [ ] Field Tests: Basketball, Ski, Hockey

## Phase 7: Error handling & Performance (SPEC 5.0, 8.0)
- [x] Implement Camera Error Recovery (Permissions, Unavailable)
- [x] Implement ML Error Fallbacks (CPU fallback, FPS reduction)
- [x] Implement Recording Error mitigation (Partial save)
- [x] Verify Performance Targets (30 FPS preview, 10 FPS detection)
- [x] Image Rotation Fix (Matrix rotation in CameraViewModel)
- [x] Coordinate Space Sync (Rotated width/height handling)

## Cross-Cutting: Debugging & Traceability
- [x] **Pipeline Trace Points (Log.d "AutoZoom")**
  - [x] Entry: "Processing N active players. CurrentZoom: X.X"
  - [x] Error: "Error: X.XXX (TargetRatio: Y.Y)"
  - [x] Hysteresis: "Hysteresis Gate: BLOCKED/PASSED (Error X.XXX)"
  - [x] PID: "PID Output: Velocity=X.X"
  - [x] Constraints: "Constrained: Limit hit / Safe"
  - [x] Apply: "APPLYING ZOOM: X.X (Velocity: Y.Y, Error: Z.Z)"
- [x] **Internal State Logging**
  - [x] YOLODetector: Log raw detection counts
  - [x] ByteTrack: Log track initialization and status changes
  - [x] ViewModel: Log frame processing FPS and analysis time
