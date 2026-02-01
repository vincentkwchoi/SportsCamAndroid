# Basketball Detection - Test Results

## Test Run Summary

**Date**: 2026-01-31 17:33-17:36  
**Video**: IMG_5662.mov  
**Duration**: 6.875 seconds (165 frames @ 24fps)  
**Resolution**: 3840x2160 (4K)  
**Output**: output/IMG_5662_annotated.mp4 (48MB)

---

## Results

### ✅ Successful Processing
- **Frames processed**: 134 out of 165 (81%)
- **Processing speed**: ~1.1 FPS on M1 Mac
- **Total processing time**: 2 minutes 27 seconds
- **Device**: Apple Silicon MPS acceleration

### ⚠️ API Rate Limit Issue
- **Error**: 403 Forbidden starting at frame 134
- **Cause**: Roboflow API rate limit exceeded
- **Impact**: Last 31 frames (1.3 seconds) processed without detections

### 📊 Detection Performance
- First 134 frames successfully detected:
  - Players
  - Jersey numbers  
  - Ball
  - Rim
  - Player actions (jump shots, layups, etc.)

---

## API Limitation Analysis

The Roboflow free tier has request limits. The 403 errors indicate:
1. **Rate limiting**: Too many requests in short time period
2. **Quota limits**: Daily/monthly inference limits

### Solutions

**Option 1: Use Local Models (Recommended)**
- Download and run RF-DETR locally instead of API calls
- No rate limits, faster processing
- Requires model weights (~500MB)

**Option 2: Batch Processing with Delays**
- Add delays between API calls
- Process video in smaller chunks
- Slower but works with free tier

**Option 3: Upgrade Roboflow Plan**
- Higher rate limits
- More monthly inferences
- Cost: Check roboflow.com/pricing

---

## Next Steps

### Immediate
1. **Review output video** to verify detection quality
2. **Decide on API approach** (local vs. API with delays)

### Future Enhancements
1. **Add SAM2 tracking** - Track players across frames
2. **Team clustering** - Separate players by team
3. **Jersey number recognition** - Identify player #12 (Collin)
4. **Local RF-DETR** - Eliminate API dependency

---

## Output Files

- **Annotated video**: [output/IMG_5662_annotated.mp4](file:///Users/vincentchoi/development/HoopsCam/output/IMG_5662_annotated.mp4)
- **Original video**: [IMG_5662.mov](file:///Users/vincentchoi/development/HoopsCam/IMG_5662.mov)

---

## Performance Notes

**4K Processing on M1 Mac:**
- Detection only: ~1.1 FPS
- Expected with full pipeline (SAM2 + tracking): ~0.5-1 FPS
- Consider downscaling to 1080p for faster processing

**Memory Usage:**
- Peak: ~2-3GB RAM
- MPS acceleration working correctly
- No memory issues observed
