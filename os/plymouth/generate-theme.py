#!/usr/bin/env python3
"""Generate EduPulse Plymouth theme assets (background, progress bar, etc.)"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

THEME_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'edupulse')
os.makedirs(THEME_DIR, exist_ok=True)

W, H = 1920, 1080
CYAN = (0, 255, 255)
DARK = (10, 10, 11)
GRAY = (148, 163, 184)

def get_font(size, bold=False):
    paths = [
        os.path.expanduser("~/.fonts/Orbitron-Variable.ttf"),
        "/usr/share/fonts/truetype/orbitron/Orbitron-Variable.ttf",
    ]
    for p in paths:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def draw_background():
    img = Image.new('RGB', (W, H), DARK)
    draw = ImageDraw.Draw(img)

    # Grid dots
    for x in range(0, W, 40):
        for y in range(0, H, 40):
            draw.ellipse([x-1, y-1, x+1, y+1], fill=(0, 255, 255, 15) if False else (0, 40, 50))

    # Glow behind text
    cx, cy = W//2, H//2 - 60
    for r in range(300, 0, -20):
        alpha = max(0, 30 - (300-r)//10)
        draw.ellipse([cx-r, cy-r, cx+r, cy+r], outline=(0, 255, 255, alpha) if False else None)

    # "EduPulse" text
    font_large = get_font(80, bold=True)
    _, _, tw, th = draw.textbbox((0, 0), "EduPulse", font=font_large)
    tx, ty = (W - tw)//2, H//2 - th - 20
    draw.text((tx, ty), "EduPulse", fill=CYAN + (200,), font=font_large)

    # Tagline
    font_small = get_font(20)
    _, _, tw2, th2 = draw.textbbox((0, 0), "ATTENDANCE SYSTEM", font=font_small)
    draw.text(((W - tw2)//2, ty + th + 10), "ATTENDANCE SYSTEM", fill=GRAY + (180,), font=font_small)

    # Corner brackets
    bracket = 40
    m = 30
    for (x1,y1,x2,y2) in [
        (m, m, m+bracket, m), (m, m, m, m+bracket),
        (W-m-bracket, m, W-m, m), (W-m, m, W-m, m+bracket),
        (m, H-m, m+bracket, H-m), (m, H-m, m, H-m-bracket),
        (W-m-bracket, H-m, W-m, H-m), (W-m, H-m, W-m, H-m-bracket),
    ]:
        draw.line([x1, y1, x2, y2], fill=CYAN + (120,), width=3)

    # Version
    draw.text((W//2 - 50, H - 50), "v1.0.0  ·  CYBERPUNK EDITION", fill=GRAY + (80,), font=get_font(12))

    img.save(os.path.join(THEME_DIR, 'background.png'))
    print(f"[Plymouth] background.png saved")

def draw_progress_bar():
    img = Image.new('RGBA', (400, 8), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background track
    draw.rounded_rectangle([0, 0, 400, 8], radius=4, fill=(0, 255, 255, 20))

    # Fill (empty — will be animated by Plymouth)
    draw.rounded_rectangle([2, 1, 398, 7], radius=3, fill=(0, 255, 255, 40))

    img.save(os.path.join(THEME_DIR, 'progress-bar.png'))
    print(f"[Plymouth] progress-bar.png saved")

def draw_bullet():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([2, 2, 14, 14], fill=(0, 255, 255, 180))
    img.save(os.path.join(THEME_DIR, 'bullet.png'))
    print(f"[Plymouth] bullet.png saved")

def draw_logo():
    """Small logo for grub and boot menu."""
    img = Image.new('RGBA', (256, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = get_font(40, bold=True)
    draw.text((10, 10), "EduPulse", fill=(0, 255, 255, 220), font=font)
    img.save(os.path.join(THEME_DIR, 'logo.png'))
    print(f"[Plymouth] logo.png saved")

if __name__ == '__main__':
    print("[Plymouth] Generating EduPulse theme assets...")
    draw_background()
    draw_progress_bar()
    draw_bullet()
    draw_logo()
    print("[Plymouth] Done!")
