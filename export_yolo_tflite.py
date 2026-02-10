from ultralytics import YOLO
import os

# Path to the model
model_path = "/Users/vincentchoi/development/SportsCam/SportsCamAndroid/app/src/main/assets/yolov8n.pt"

print(f"Loading model from: {model_path}")
model = YOLO(model_path)

# Export the model to TFLite format
# int8=True: for NPU speed (Pixel 10)
# nms=True: so the TFLite file handles the box filtering
# data="coco8.yaml": representative dataset for quantization
print("Starting export to TFLite with int8 quantization...")
model.export(format="tflite", int8=True, nms=True, data="coco8.yaml")

print("Export complete.")
