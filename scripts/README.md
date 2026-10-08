# Icon Generator Script

Python script to generate the official FPS Meter application icon and all Android launcher icon assets.

The icon is drawn programmatically using pure geometric math and Pillow drawing functions. It was never made with AI image generation.

## Script

* `gen_icon.py`: Creates `icon/icon.png` (512x512 high-resolution icon) and all Android mipmap launcher icons (square, round, and adaptive foregrounds).

## Requirements

Install Pillow:

```bash
pip install pillow
```

## How to Run

From the project root:

```bash
python scripts/gen_icon.py
```

Or from the `scripts` folder:

```bash
cd scripts
python gen_icon.py
```

## Generated Outputs

1. **Repository Icon**:
   * `icon/icon.png`: 512x512 high-resolution icon for stores, showcase pages, and GitHub.

2. **Android Mipmap Launcher Assets**:
   * `app/src/main/res/mipmap-mdpi/`: 48x48 icons and 108x108 adaptive foreground
   * `app/src/main/res/mipmap-hdpi/`: 72x72 icons and 162x162 adaptive foreground
   * `app/src/main/res/mipmap-xhdpi/`: 96x96 icons and 216x216 adaptive foreground
   * `app/src/main/res/mipmap-xxhdpi/`: 144x144 icons and 324x324 adaptive foreground
   * `app/src/main/res/mipmap-xxxhdpi/`: 192x192 icons and 432x432 adaptive foreground
