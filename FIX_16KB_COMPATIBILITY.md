# Summary: 16 KB Page Size Compatibility Fixes

This document summarizes the issues identified and the changes made to ensure the SportsCam app is compatible with devices using a 16 KB memory page size (specifically tested on Pixel 10).

## 1. Identified Issues

Devices running with a 16 KB kernel (like some Android 15 configurations) require all native libraries (`.so` files) to have their ELF `LOAD` segments aligned to 16 KB boundaries. If any library fails this check, the OS will block the app from launching.

### Culprit Libraries Identified:
*   **`libimage_processing_util_jni.so`**: Found in CameraX version 1.3.1. This version was compiled with 4 KB alignment.
*   **`libmediapipe_tasks_vision_jni.so`**: Found in MediaPipe Task Vision 0.10.20. Despite being a modern library, this specific artifact still lacked the required 16 KB alignment.
*   **`libLiteRt.so`**: (Initial check) LiteRT 2.1.1 was initially suspected but confirmed clean after a full project rebuild and dependency synchronization.

## 2. Changes Applied

### A. Dependency Upgrades
We updated core libraries to versions that are explicitly recompiled by Google for 16 KB alignment:
*   **CameraX**: Upgraded from `1.3.1` to **`1.5.3`**. This resolved the alignment issue for camera utilities.
*   **LiteRT (Core & Support)**: Confirmed use of **`2.1.1`** (Core) and **`1.4.1`** (Support).
*   **Compose BOM**: Upgraded to **`2024.12.01`**. This was required to fix a runtime `IllegalArgumentException` (ripple crash) that appears on Android 15+.
*   **MediaPipe**: **Removed** from the current build. Version `0.10.20` is currently incompatible with 16 KB mode on this hardware.

### B. Build Configuration (`app/build.gradle.kts`)
The following settings were mandated to ensure the build tools preserve alignment:
*   **NDK Version**: Set to **`28.0.12433566`**. NDK 28+ defaults to 16 KB alignment for all native code.
*   **Packaging Options**: Set `useLegacyPackaging = false` in the `jniLibs` block. This ensures native libraries are stored **uncompressed** and **aligned** in the APK, allowing the OS to memory-map them directly.
*   **Asset Compression**: Added `noCompress += "tflite"` to ensure ML models are also memory-mapped efficiently.

### C. Manifest Changes (`AndroidManifest.xml`)
*   Added **`android:extractNativeLibs="false"`** to the `<application>` tag. This is the explicit signal to the OS to use the libraries directly from the APK without extraction, which is mandatory for 16 KB compatibility.

## 3. Current State
*   **16 KB Compatibility**: **PASSED**. No ELF alignment warnings appear on launch.
*   **App Stability**: **SUCCESS**. The app launches and runs the camera preview loop successfully on Pixel 10.
*   **Inference Engine**: The app is currently using **LiteRT 2.1.1** with the **EfficientDet-Lite0 INT8** model, achieving ~30ms latency.

## 4. Maintenance Note
If re-adding a library causes the `ELF alignment check failed` error to return:
1.  Verify the library version is the absolute latest.
2.  If it still fails, the library author has not yet recompiled it for 16 KB alignment. Use `zipalign -p -c 16` on the final APK to check for manual fixes or wait for an updated artifact.
