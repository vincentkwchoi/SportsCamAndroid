# YOLO Model Export for Android

## Quick Start

The app requires a `yolo.tflite` model file in the assets directory. Follow these steps:

### 1. Install Ultralytics (if not already installed)

```bash
pip install ultralytics
```

### 2. Export the Model

```bash
cd /Users/vincentchoi/development/SportsCam/SportsCamAndroid
python3 -c "from ultralytics import YOLO; model = YOLO('yolo26n.pt'); model.export(format='tflite', imgsz=640, nms=True)"
```

This will create `yolo26n_float32.tflite` and `yolo26n_float16.tflite`.

### 3. Model Precision: Float32 vs Float16

| Feature | Float32 | Float16 (Recommended) |
| :--- | :--- | :--- |
| **Size** | ~12MB | **~6MB (50% smaller)** |
| **Speed (GPU)** | Normal | **Significantly Faster** |
| **Accuracy** | 100% | ~99.9% (Negligible loss) |

> [!TIP]
> Always use **Float16** for the mobile app. The GPU delegate is optimized for 16-bit precision, and the speed boost is critical for sports tracking.

### 4. Rename and Move to Assets

```bash
mv yolo26n_float16.tflite app/src/main/assets/yolo.tflite
```

### 5. Rebuild and Run

```bash
./gradlew assembleDebug
```

Then install and run the app from Android Studio.

---

## Troubleshooting

**If export fails:**
- Make sure you're using Python 3.12 (not 3.14): `/opt/homebrew/bin/python3.12`
- Try a smaller model: `yolo11n.pt` (nano) instead of `yolo11s.pt` (small)

**If the model is too slow:**
- The app uses GPU acceleration via TensorFlow Lite GPU delegate
- Check Logcat for "libEGL" errors if GPU isn't working
