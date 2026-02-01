# Basketball Player Detection - Quick Start Guide

## Setup

1. **Activate the virtual environment:**
   ```bash
   source venv/bin/activate
   ```

2. **Set up your Roboflow API key:**
   ```bash
   # Copy the template
   cp .env.template .env
   
   # Edit .env and add your API key
   # Get your key from: https://app.roboflow.com/settings/api
   nano .env
   ```

3. **Load environment variables:**
   ```bash
   export $(cat .env | xargs)
   ```

## Running the Detection

Process your video:
```bash
python process_video.py
```

The annotated video will be saved to `output/IMG_5662_annotated.mp4`

## Current Features

- ✅ Player detection using RF-DETR
- ✅ Jersey number detection
- ✅ Ball and rim detection
- ✅ Basic annotation with bounding boxes and labels
- ⏳ Player tracking with SAM2 (coming next)
- ⏳ Team clustering (coming next)
- ⏳ Player identification (coming next)

## Troubleshooting

**"ROBOFLOW_API_KEY not set" error:**
- Make sure you've created the `.env` file with your API key
- Run `export $(cat .env | xargs)` to load the variables

**Video not found:**
- Make sure `IMG_5662.mov` is in the project directory

**Slow processing:**
- This is normal on first run as models are downloaded
- M1 Mac should process at ~5-10 FPS with MPS acceleration

## Next Steps

1. Get your Roboflow API key from https://app.roboflow.com/settings/api
2. Update the `.env` file with your key
3. Run the script!
