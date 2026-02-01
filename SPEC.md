# HoopsCam iOS Auto-Zoom Specification

## Overview
iOS camera application that automatically zooms to keep 80% of active basketball players in frame during recording.

---

## Questions to Define Specification

**Q1: What is the primary use case for this app?**
✅ **Answer: Recording youth basketball games**

**Q2: Who will be operating the camera during the game?**
✅ **Answer: Parent sitting in the stands, handheld camera. App helps parents record their kids playing. Coaches will be present on sidelines.**

**Q3: How should the auto-zoom behave?**
✅ **Answer (MVP): Keep minimum 80% of active players in frame**
- Simple automatic zoom to capture the action
- No player selection needed (future feature)
- Focus on getting the core auto-zoom working first

**Q4: How does the parent select their kid at the start?**
✅ **Answer: Not needed for MVP - just record all active players**
(Future feature: tap to select, jersey number, etc.)

**Q5: How should the zoom transitions behave?**
✅ **Answer: Smooth gradual zoom (cinematic)**
- Pleasant viewing experience
- Avoids jarring camera movements
- Professional-looking output

**Q6: What iOS devices should this support?**
✅ **Answer: iPhone 13 and newer**
- Broad compatibility (3+ years of devices)
- No specific camera requirements (works with standard wide lens)
- Good balance of market coverage and performance

**Q7: What recording features are needed?**
✅ **Answer: Simple recording with manual override**
- Video recording with auto-zoom
- Manual zoom override using press-hold volume buttons
- Auto-save to Photos app
- One-click delete last video

**Q8: What performance is expected for real-time detection?**
✅ **Answer: Real-time with acceptable delay**
- Auto-zoom should work in real-time during recording
- 1 second delay is acceptable
- Must work on battery for full game (60+ minutes)
- Heating not a concern

**Q9: What should the UI show during recording?**
✅ **Answer: Informative recording UI**
- Camera preview with auto-zoom
- Show detected player bounding boxes
- Show recording time
- Start/Stop recording button
- Auto-zoom toggle button (re-enable after manual override)
- Auto-zoom disabled when volume buttons used for manual zoom

**Q10: How should the app detect players for auto-zoom?**
✅ **Answer: Use proven Python pipeline approach**
- YOLO11x for player detection
- BBox activity detection to filter out refs/audience
- Port Python logic to iOS (CoreML or similar)
- Same accuracy and filtering as desktop version

**Q11: How should the LiDAR distance reading translate to zoom level?**
✅ **Answer: Constant Subject Size ("Virtual Dolly")**
- Assume player height = 6 feet (1.83m)
- Target size = 1/3 of screen height
- Adjust zoom dynamically based on distance to maintain this ratio

**Q12: Where should we measure the distance from?**
✅ **Answer: Center of Frame**
- Use the LiDAR depth reading at the center point of the screen
- Simpler implementation than tracking-based depth
- Requires user to keep subject centered (which they naturally do)

**Q13: Feasibility Check - LiDAR Range (~5m limit)**
✅ **Answer: Pivot to Vision-Based "Virtual Dolly"**
- Since LiDAR range is too short for court-side/stands recording, we will **NOT** use LiDAR.
- **New Logic**: Calculate the average bounding box height of active players.
- Adjust zoom so that this average height equals **1/3 of the screen height**.
- This achieves the "Constant Subject Size" effect using computer vision instead of distance sensors.

**Q14: Interaction - Zoom Modes**
✅ **Answer: Replace Old Logic (Option B)**
- The "Constant Subject Size" logic will **replace** the previous "Group Action" logic.
- We will no longer try to fit *all* players in the frame.
- Instead, we focus on maintaining the **average active player height at 33% (1/3) of screen height**.
- This creates a consistent viewing experience, treating the players as the fixed subject size.

---

## Complete Specification

### User Story
As a **parent** recording my child's basketball game, I want an **iOS app that automatically zooms to keep active players in frame** so that **I can enjoy watching the game while getting professional-looking footage without manual camera adjustments**.

### Core Features (MVP)

#### 1. Auto-Zoom System
- **Player Detection**: YOLO11x model running on-device
- **Activity Filtering**: BBox activity detector to filter static people (refs, audience, coaches)
- **Zoom Logic**: Keep minimum 80% of active players in frame
- **Smooth Transitions**: Cinematic gradual zoom (not jarring)
- **Real-time Processing**: <1 second delay acceptable

#### 2. Recording Controls
#### 1. Auto-Zoom "Virtual Dolly"
- **Logic**: Calculate the **average height** of all "Active" player bounding boxes.
- **Target**: Adjust zoom so this average height occupies **33% (1/3) of the vertical screen height**.
- **Edge Case**: If no players are detected, **maintain (lock)** the current zoom level.
- **Smoothing**: Cinematic damping to avoid jitter.

#### 2. Detection & Filtering
- **Model**: YOLO11x (CoreML).
- **Filtering**: `BBoxActivityDetector` to exclude static people (refs, bench).
- **Tracking**: `SimpleTracker` to stabilize IDs.

#### 3. Recording Controls
- **Record**: One-tap recording to Photos.
- **Manual Override**: Volume buttons (+/-) allow temporary manual control.
- **Auto-Zoom Toggle**: Button to re-engage auto-mode after manual override.
- **Delete Last**: Trash button to quick-delete standard bad takes.

### UI/UX Design

#### Main Screen
```
┌─────────────────────────────────┐
│  [AUTO: Virtual Dolly]     12:45│ <- Status / Time
│                                 │
│          ┌──────┐               │
│          │Player│               │
│          │      │               │ <- Player kept at 
│          │      │               │    ~1/3 Height
│          └──────┘               │
│                                 │
│  [Rec]   [Manual]    [Trash]    │
└─────────────────────────────────┘
```

**Q16: Zoom Smoothing - Kalman Filter Tuning**
✅ **Answer: Option B (Balanced)**
- Use a 1D Kalman Filter for zoom smoothing.
- Tune process noise (Q) and measurement noise (R) for a balance between stability and responsiveness.
- Eliminate "jitter" from frame-by-frame detection noise.

**Q17: Device Orientation Support**
✅ **Answer: Portrait & Landscape**
- The app must support recording in both **Portrait** (vertical) and **Landscape** (horizontal) modes.
- Camera preview, UI layout, and video recording output should adapt to the device orientation.
- **Auto-Zoom Logic**: Should work consistently in both orientations (using height relative to the shorter or longer dimension as appropriate).

**Q18: Zoom Oscillation Prevention**
✅ **Answer: Deadband / Hysteresis**
- Prevent "hunting" or "breathing" where zoom constantly adjusts for micro-movements.
- **Logic**: Only apply a new zoom target if it differs from the current zoom by **> 5%**.
- This ensures the camera stays locked until a meaningful change in subject distance occurs.

### Technical Implementation

#### Auto-Zoom Algorithm
```python
1. Detect all people (YOLO)
2. Filter for "Active" (Movement/Pose)
3. If active_players > 0:
    avg_height_normalized = average(bbox.height for p in active_players)
    raw_target_zoom = 0.33 / avg_height_normalized
    
    # Kalman Filter Step
    filtered_zoom = kalman.predict_and_update(raw_target_zoom)
    
    set_zoom(filtered_zoom)
4. Else:
    maintain_current_zoom()
```

### Constraints
- **Max Zoom**: Cap at 5.0x (digital).
- **Min Zoom**: 1.0x (wide).

---

## Development Approach

### Phase 1: Core Detection (Week 1-2)
- Port YOLO11x to CoreML
- Implement real-time inference
- Test detection accuracy on iPhone

### Phase 2: Activity Filtering (Week 3)
- Port bbox_activity_detector to Swift
- Integrate with detection pipeline
- Verify filtering works correctly

### Phase 3: Auto-Zoom Logic (Week 4)
- Calculate optimal zoom from player positions
- Implement smooth zoom transitions
- Test zoom behavior

### Phase 4: Recording UI (Week 5)
- Build camera preview with AVFoundation
- Add recording controls
- Implement manual override

### Phase 5: Polish & Testing (Week 6)
- Battery optimization
- UI refinements
- Real-world game testing
- Bug fixes

---

## Success Criteria

✅ **Functional**
- Auto-zoom keeps 80%+ players in frame
- Filters out static refs/audience
- Smooth zoom transitions
- Manual override works
- Videos save to Photos

✅ **Performance**
- 15+ FPS detection speed
- 60+ minutes battery life
- No overheating
- <1 second zoom delay

✅ **User Experience**
- Simple one-tap recording
- Clear visual feedback
- Intuitive controls
- Professional-looking output

---

## Questions Answered

1. ✅ Primary use case: Recording youth basketball games
2. ✅ Operator: Parent in stands, handheld
3. ✅ Zoom behavior: 80% player coverage (MVP)
4. ✅ Player selection: Not needed for MVP
5. ✅ Zoom transitions: Smooth gradual (cinematic)
6. ✅ Device support: iPhone 13+
7. ✅ Recording features: Auto-zoom, manual override, auto-save, quick delete
8. ✅ Performance: Real-time, 1s delay OK, 60+ min battery
9. ✅ UI: Bounding boxes, time, controls, auto-zoom toggle
10. ✅ Detection: YOLO + bbox activity (same as Python)

---

## Ready for Development!

This specification provides a complete blueprint for building the HoopsCam iOS auto-zoom app. All key decisions have been made, and the scope is well-defined for an MVP that delivers real value to parents recording youth basketball games.

---

## Specification Details (In Progress)

### User Story
As a **parent/guardian** of a youth basketball player, I want to **record the entire game with automatic zoom** so that **I can focus on watching the game while the app keeps the active players in frame**, without needing to manually adjust the camera.

### Key Features
[Pending Q2+]

### Technical Requirements
[Pending]

### UI/UX Design
[Pending]

### Performance Requirements
[Pending]

### Platform Requirements
[Pending]

---

## Next Steps
Answer Q2 above to continue building the specification.
