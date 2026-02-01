# HoopsCam Performance Optimization TODO

## Neural Engine (NPU)
- [ ] **A17 Pro Optimization**: Re-export YOLO11x CoreML model specifically targeting A17 Pro architecture.
- [ ] **Quantization**: Experiment with Int8 or Int16 quantization to reduce model size and heat without sacrificing too much accuracy.
- [ ] **Compute Units**: Force `CPUAndGPU` vs `All` vs `CPUOnly` benchmarks to find the most efficient execution provider.

## Battery & Thermal
- [ ] **Energy Impact Profiling**: Run Xcode Instruments "Energy Log" during a 60-minute recording session.
- [ ] **Thermal State Handling**: Implement `ProcessInfo.processInfo.thermalState` monitoring to degrade performance (e.g., lower FPS, disable detection) if device gets too hot.
- [ ] **Frame Rate Throttling**: Test if running detection at 15 FPS (instead of 30/60) suffices for auto-zoom while saving battery.

## Memory & CPU
- [ ] **Pixel Buffer Copies**: Ensure `CameraManager` -> `YOLODetector` pixel buffer passing is zero-copy or minimal copy.
- [ ] **Background Tasks**: Verify no unnecessary background threads are spinning when recording is paused.
