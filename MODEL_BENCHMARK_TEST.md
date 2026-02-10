# Model Benchmark Results (Pixel 10 - 16 KB Page Size Mode)

This document summarizes the performance and accuracy of different person detection models tested on the Pixel 10 (Tensor G5). Tests were performed using the `ModelBenchmarkTest.kt` instrumentation suite.

## Summary Table

| Model | Framework / API | Target Hardware | Detected? | Confidence | Inference Time (Avg) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **EfficientDet-Lite0 (INT8)** | **LiteRT 2.1.1** | GPU / CPU | **YES** | **0.86** | **~30.4 ms** |
| **EfficientDet-Lite0 (INT8)** | **MediaPipe Task API** | CPU | **YES** | **0.86** | **~96.6 ms** |
| **YOLOv26n (FP16)** | **LiteRT 2.1.1** | CPU (Fallback) | **YES** | **0.88** | **~180.6 ms** |

## Detailed Observations

### 1. EfficientDet-Lite0 INT8 (via LiteRT API)
*   **Performance Winner**: This is the fastest configuration currently running on the device.
*   **Hardware Usage**: Although the NPU explicitly requested failed to initialize (`kLiteRtStatusErrorInvalidArgument`), this model successfully utilized the GPU and optimized XNNPACK CPU delegates via the modern LiteRT API.
*   **Result**: 30.4ms latency allows for a stable 30 FPS processing loop.

### 2. MediaPipe EfficientDet-Lite0 INT8 (via Task API)
*   **Accuracy**: Matches the LiteRT version exactly.
*   **Performance**: Significantly slower (~96ms) than the direct LiteRT implementation. This is due to the overhead of the high-level MediaPipe Task wrapper and its default CPU delegation on this system configuration.

### 3. YOLOv26n FP16 (Custom Model)
*   **Accuracy**: Highest confidence detected (0.88).
*   **Performance**: Slowest model (~180ms). The model architecture is complex, and being FP16 rather than INT8, it forces a heavy fallback to the CPU. It is currently not viable for real-time 30 FPS tracking on this hardware.

## Test Artifacts
*   **Test Image 1**: `one_basketball_player.jpg` (Athlete detected with 0.86-0.88 confidence)
*   **Test Image 2**: `raw_capture.jpg` (Athlete detected with 0.69-0.74 confidence)

## Conclusion for SportsCam AutoZoom
The **EfficientDet-Lite0 INT8 running via LiteRT API** is the recommended engine for the production app. It provides the necessary 16 KB page size compatibility and the lowest latency for real-time athlete tracking.
