# Roboflow Model Download Investigation

## Issue
Attempted to download Roboflow RF-DETR model weights for offline use but encountered authorization error.

## Findings

### Model Download Restrictions
- **Free Tier**: API inference only (rate limited)
- **Basic/Growth Plans** ($49+/month): Can download model weights
- The basketball-player-detection-3-ycjdo model requires paid plan to download

### Alternative: Local Inference with Caching

The `inference` package provides a middle ground:

1. **First Run**: Uses API to download and cache model
2. **Subsequent Runs**: Uses cached model offline
3. **Benefits**:
   - Basketball-specific detections (jump shots, layups, etc.)
   - Faster than API calls
   - Works offline after initial cache
   - No rate limits on cached inference

### Three Options

#### Option 1: Roboflow Local Inference (Recommended)
- Install: `inference` package (✅ already installed)
- Cache model on first run (~500MB download)
- Run offline with basketball-specific classes
- **Pros**: Best of both worlds - basketball-specific + local
- **Cons**: Large initial download, requires API key for first run

#### Option 2: Continue with YOLO11x (Current)
- Already working
- Fast, offline
- **Pros**: Simple, no dependencies
- **Cons**: Generic "person" detection only, no basketball actions

#### Option 3: Upgrade Roboflow Plan
- Cost: ~$49/month (Basic plan)
- Download actual .pt weights
- **Pros**: Full model ownership
- **Cons**: Monthly cost

## Recommendation

**Implement Option 1** - Add "roboflow_local" detection mode that:
1. Uses `inference.get_model()` for local inference
2. Caches model after first run
3. Provides basketball-specific detections
4. Runs without API rate limits

This matches the article's algorithm while avoiding API limitations.
