#!/usr/bin/env python3
"""
Generate Android launcher icons from a source PNG.
Creates:
  - Standard mipmap PNGs (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi)
  - Adaptive icon foreground/background layers (API 26+)
"""
import os
import shutil
from PIL import Image, ImageDraw

PROJECT_DIR = "/Users/guohaiqiu/.openclaw/workspace/projects/keysjoy"
RES_DIR = os.path.join(PROJECT_DIR, "app/src/main/res")
SOURCE_PNG = "/Users/guohaiqiu/.openclaw/workspace/downloads/19f55b00-8992-8c4f-8000-000030d4a6c5_1000030469.png"
BACKUP_DIR = os.path.join(PROJECT_DIR, "icon_backup")

# Density configs: (folder_suffix, standard_size, adaptive_canvas, adaptive_safe_zone)
DENSITIES = {
    "mdpi":    (48, 108, 72),
    "hdpi":    (72, 162, 108),
    "xhdpi":   (96, 216, 144),
    "xxhdpi":  (144, 324, 216),
    "xxxhdpi": (192, 432, 288),
}

# Background gradient colors (matching original app theme)
BG_START = (0x33, 0x78, 0xFF)   # #3378FF
BG_END = (0x00, 0x00, 0x00)     # #000000

# Rounded corner radius as fraction of icon size
CORNER_RADIUS_FRAC = 0.18  # ~18% of size for nice rounded corners


def backup_original_files():
    """Back up existing icon-related files."""
    os.makedirs(BACKUP_DIR, exist_ok=True)

    files_to_backup = [
        "mipmap-anydpi-v26/ic_launcher.xml",
        "mipmap-anydpi-v26/ic_launcher_ble.xml",
        "mipmap-anydpi-v26/ic_launcher_ble_round.xml",
        "drawable/ic_launcher_background.xml",
        "drawable/ic_launcher_background_ble.xml",
        "drawable/ic_launcher_foreground_ble.xml",
        "drawable-v24/ic_launcher_foreground.xml",
    ]

    for f in files_to_backup:
        src = os.path.join(RES_DIR, f)
        dst = os.path.join(BACKUP_DIR, f)
        if os.path.exists(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            print(f"  Backed up: {f}")

    print("Backup complete.")


def make_square(img):
    """Center-crop an image to a square."""
    w, h = img.size
    if w == h:
        return img
    size = min(w, h)
    left = (w - size) // 2
    top = (h - size) // 2
    return img.crop((left, top, left + size, top + size))


def add_rounded_corners(img, radius_px):
    """Apply rounded corners to an image using an alpha mask."""
    img = img.convert("RGBA")
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (w - 1, h - 1)], radius=radius_px, fill=255)
    result = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    result.paste(img, (0, 0), mask)
    return result


def create_radial_gradient(size):
    """Create a radial gradient background image."""
    img = Image.new("RGBA", (size, size))
    cx, cy = size / 2, size / 2
    max_dist = size * 0.707  # corner distance from center

    for y in range(size):
        for x in range(size):
            dx = x - cx
            dy = y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            t = min(dist / max_dist, 1.0)
            # Ease-in curve for nicer gradient
            t = t ** 0.7
            r = int(BG_START[0] + (BG_END[0] - BG_START[0]) * t)
            g = int(BG_START[1] + (BG_END[1] - BG_START[1]) * t)
            b = int(BG_START[2] + (BG_END[2] - BG_START[2]) * t)
            img.putpixel((x, y), (r, g, b, 255))

    return img


def generate_standard_icons(source_img):
    """Generate standard mipmap icons at all densities."""
    square = make_square(source_img)
    created = []

    for density, (size, _, _) in DENSITIES.items():
        icon = square.resize((size, size), Image.LANCZOS)
        radius = int(size * CORNER_RADIUS_FRAC)
        icon = add_rounded_corners(icon, radius)

        out_dir = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "ic_launcher.png")
        icon.save(out_path, "PNG")
        created.append(out_path)
        print(f"  Created: mipmap-{density}/ic_launcher.png ({size}x{size})")

    return created


def generate_adaptive_icons(source_img):
    """Generate adaptive icon background and foreground layers."""
    square = make_square(source_img)
    created = []

    for density, (_, canvas_size, safe_size) in DENSITIES.items():
        # Background: radial gradient
        bg = create_radial_gradient(canvas_size)
        bg_dir = os.path.join(RES_DIR, f"drawable-{density}")
        os.makedirs(bg_dir, exist_ok=True)
        bg_path = os.path.join(bg_dir, "ic_launcher_background.png")
        bg.save(bg_path, "PNG")
        created.append(bg_path)

        # Foreground: keyboard icon centered in safe zone
        fg = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        icon_scaled = square.resize((safe_size, safe_size), Image.LANCZOS)
        # Apply rounded corners to foreground icon
        fg_radius = int(safe_size * 0.15)
        icon_scaled = add_rounded_corners(icon_scaled, fg_radius)

        offset_x = (canvas_size - safe_size) // 2
        offset_y = (canvas_size - safe_size) // 2
        fg.paste(icon_scaled, (offset_x, offset_y), icon_scaled)

        fg_path = os.path.join(bg_dir, "ic_launcher_foreground.png")
        fg.save(fg_path, "PNG")
        created.append(fg_path)
        print(f"  Created: drawable-{density}/ic_launcher_background.png ({canvas_size}x{canvas_size})")
        print(f"  Created: drawable-{density}/ic_launcher_foreground.png ({canvas_size}x{canvas_size})")

    return created


def update_adaptive_xml():
    """Update adaptive icon XMLs to reference new PNG drawables."""
    mipmap_dir = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    os.makedirs(mipmap_dir, exist_ok=True)

    # Main ic_launcher.xml
    launcher_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>'''

    launcher_round_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>'''

    with open(os.path.join(mipmap_dir, "ic_launcher.xml"), "w") as f:
        f.write(launcher_xml)
    print("  Created: mipmap-anydpi-v26/ic_launcher.xml")

    with open(os.path.join(mipmap_dir, "ic_launcher_round.xml"), "w") as f:
        f.write(launcher_round_xml)
    print("  Created: mipmap-anydpi-v26/ic_launcher_round.xml")


def clean_old_files():
    """Remove old BLE icon files that are no longer needed."""
    old_files = [
        "mipmap-anydpi-v26/ic_launcher_ble.xml",
        "mipmap-anydpi-v26/ic_launcher_ble_round.xml",
        "drawable/ic_launcher_background_ble.xml",
        "drawable/ic_launcher_foreground_ble.xml",
        "drawable/ic_launcher_background.xml",       # old grid background
        "drawable-v24/ic_launcher_foreground.xml",     # old android robot foreground
    ]
    for f in old_files:
        path = os.path.join(RES_DIR, f)
        if os.path.exists(path):
            os.remove(path)
            print(f"  Removed: {f}")


def main():
    print("=" * 60)
    print("KeysJoy Launcher Icon Generator")
    print("=" * 60)

    # Step 1: Backup
    print("\n[1/5] Backing up original icon files...")
    backup_original_files()

    # Step 2: Load source
    print("\n[2/5] Loading source image...")
    source = Image.open(SOURCE_PNG).convert("RGBA")
    print(f"  Source: {source.size[0]}x{source.size[1]} pixels")

    # Step 3: Generate standard icons
    print("\n[3/5] Generating standard mipmap icons...")
    generate_standard_icons(source)

    # Step 4: Generate adaptive icon layers
    print("\n[4/5] Generating adaptive icon layers...")
    generate_adaptive_icons(source)
    update_adaptive_xml()

    # Step 5: Clean old files
    print("\n[5/5] Cleaning up old icon files...")
    clean_old_files()

    print("\n" + "=" * 60)
    print("Icon generation complete!")
    print("=" * 60)


if __name__ == "__main__":
    main()
