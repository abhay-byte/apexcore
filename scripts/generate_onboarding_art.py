#!/usr/bin/env python3
"""
Generate 4 high-resolution, high-DPI Zen Organic onboarding illustrations for ApexCore.
Canvas size: 1080x1080 (50% increase in scale, large legible typography and UI elements).
Assets are saved to app/src/main/res/drawable/
"""
from __future__ import annotations
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path("/home/abhaybyte/repos/apexcore")
RES_DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"
RES_DRAWABLE.mkdir(parents=True, exist_ok=True)

W, H = 1080, 1080

# Color Palette (Zen Organic)
BG_DARK = (0x0D, 0x18, 0x1D, 255)
SURFACE = (0x15, 0x26, 0x2E, 255)
SURFACE_LIGHT = (0x1F, 0x36, 0x40, 255)
TEAL_PRIMARY = (0x6F, 0xD8, 0xC8, 255)
TEAL_BRIGHT = (0x8C, 0xF5, 0xE4, 255)
GOLD_ACCENT = (0xE7, 0xC2, 0x68, 255)
CORAL_ACCENT = (0xF0, 0x8C, 0x7E, 255)
TEXT_WHITE = (0xEE, 0xF7, 0xFA, 255)
TEXT_MUTED = (0x9D, 0xB8, 0xC2, 255)
BORDER_COLOR = (0x44, 0x64, 0x70, 220)

def radial_glow(w, h, cx, cy, radius, color, peak_alpha=130):
    scale = 2
    ys = np.arange(h // scale, dtype=np.float32)
    xs = np.arange(w // scale, dtype=np.float32)
    xx, yy = np.meshgrid(xs, ys)
    dist = np.sqrt(((xx - cx / scale) / (radius / scale)) ** 2 + ((yy - cy / scale) / (radius / scale)) ** 2)
    a = (np.clip(1.0 - dist, 0, 1) ** 2 * peak_alpha).astype(np.uint8)
    r, g, b = color[:3]
    arr = np.zeros((h // scale, w // scale, 4), dtype=np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3] = r, g, b, a
    return Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)

def rounded_glass_card(w, h, fill=SURFACE, border=BORDER_COLOR, radius=44, border_width=3):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=fill, outline=border, width=border_width)
    return im

def draw_text_center(d, text, cx, cy, font, fill=TEXT_WHITE):
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text((cx - tw / 2, cy - th / 2), text, font=font, fill=fill)

def get_font(size: int, bold: bool = False):
    font_path = Path("/tmp/storelisting-fonts") / ("PlusJakartaSans-Bold.ttf" if bold else "PlusJakartaSans-Medium.ttf")
    if font_path.exists():
        try:
            return ImageFont.truetype(str(font_path), size)
        except Exception:
            pass
    return ImageFont.load_default()

# ── 1. PURGE ENGINE ────────────────────────────────────────────────────────
def generate_purge_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 30

    # Large ambient glow
    im.alpha_composite(radial_glow(W, H, cx, cy, 450, TEAL_PRIMARY, 100))
    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 240, TEAL_BRIGHT, 150))

    d = ImageDraw.Draw(im)

    # Concentric orbital rings (Thick, high-contrast)
    for r, width, alpha in [(360, 3, 50), (280, 4, 90), (200, 5, 160), (130, 6, 220)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(*TEAL_PRIMARY[:3], alpha), width=width)

    # Orbital nodes with glowing halos
    for angle, r, size, col in [
        (0.5, 280, 16, GOLD_ACCENT),
        (2.0, 280, 14, TEAL_BRIGHT),
        (3.7, 360, 18, TEAL_BRIGHT),
        (5.1, 200, 20, TEAL_PRIMARY),
        (1.1, 200, 15, GOLD_ACCENT),
        (4.4, 130, 14, TEAL_BRIGHT)
    ]:
        nx = cx + int(math.cos(angle) * r)
        ny = cy + int(math.sin(angle) * r)
        im.alpha_composite(radial_glow(W, H, nx, ny, 60, col, 180))
        d.ellipse([nx - size, ny - size, nx + size, ny + size], fill=col)

    # Central Core Orb
    core_r = 90
    d.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=SURFACE_LIGHT, outline=TEAL_BRIGHT, width=5)
    
    # Core inner glowing layers
    d.ellipse([cx - 58, cy - 58, cx + 58, cy + 58], fill=TEAL_PRIMARY)
    d.ellipse([cx - 32, cy - 32, cx + 32, cy + 32], fill=TEAL_BRIGHT)

    # Large Free Memory Badge at bottom
    badge_w, badge_h = 420, 92
    bx, by = cx - badge_w // 2, cy + 270
    badge = rounded_glass_card(badge_w, badge_h, fill=(*SURFACE_LIGHT[:3], 245), border=TEAL_PRIMARY, radius=46, border_width=3)
    im.alpha_composite(badge, (bx, by))
    
    bd = ImageDraw.Draw(im)
    f_bold = get_font(32, bold=True)
    f_small = get_font(20, bold=False)
    draw_text_center(bd, "4.2 GB RAM FREED", cx, by + 34, f_bold, TEAL_BRIGHT)
    draw_text_center(bd, "SYSTEM FULLY OPTIMIZED", cx, by + 66, f_small, TEXT_MUTED)

    return im

# ── 2. PERFORMANCE HUD ─────────────────────────────────────────────────────
def generate_hud_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2

    im.alpha_composite(radial_glow(W, H, cx, cy - 50, 440, TEAL_PRIMARY, 85))
    im.alpha_composite(radial_glow(W, H, cx + 180, cy + 150, 300, GOLD_ACCENT, 70))

    # Large Main Floating HUD Glass Card
    card_w, card_h = 760, 500
    card_x, card_y = cx - card_w // 2, cy - card_h // 2 - 30
    card = rounded_glass_card(card_w, card_h, fill=(0x10, 0x1F, 0x26, 248), border=TEAL_PRIMARY, radius=52, border_width=4)
    im.alpha_composite(card, (card_x, card_y))

    d = ImageDraw.Draw(im)

    # Top Header Pill in HUD
    pill_w, pill_h = 200, 54
    px, py = card_x + 46, card_y + 44
    d.rounded_rectangle([px, py, px + pill_w, py + pill_h], radius=27, fill=(*TEAL_PRIMARY[:3], 45), outline=TEAL_PRIMARY, width=2)
    d.ellipse([px + 18, py + 21, px + 30, py + 33], fill=TEAL_BRIGHT)
    f_chip = get_font(22, bold=True)
    d.text((px + 44, py + 14), "LIVE HUD", font=f_chip, fill=TEAL_BRIGHT)

    # FPS Large Metric
    f_fps_num = get_font(100, bold=True)
    f_fps_unit = get_font(34, bold=True)
    d.text((card_x + 46, card_y + 128), "120", font=f_fps_num, fill=TEXT_WHITE)
    d.text((card_x + 250, card_y + 182), "FPS", font=f_fps_unit, fill=TEAL_PRIMARY)

    # Live telemetry waveform
    wave_pts = []
    start_x = card_x + 360
    end_x = card_x + card_w - 46
    base_y = card_y + 180
    for i in range(20):
        x = start_x + (end_x - start_x) * (i / 19.0)
        y = base_y - math.sin(i * 0.55) * 28 - (math.cos(i * 1.1) * 12)
        wave_pts.append((x, y))
    for i in range(len(wave_pts) - 1):
        d.line([wave_pts[i], wave_pts[i+1]], fill=TEAL_BRIGHT, width=5)

    # Divider line
    d.line([(card_x + 46, card_y + 270), (card_x + card_w - 46, card_y + 270)], fill=BORDER_COLOR, width=2)

    # Metrics row (RAM & CPU)
    f_lbl = get_font(20, bold=True)
    f_val = get_font(30, bold=True)
    
    # RAM Meter
    d.text((card_x + 46, card_y + 300), "RAM PRESSURE", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 46, card_y + 340), "38% Normal", font=f_val, fill=TEAL_BRIGHT)
    
    bar_w = 280
    bar_h = 16
    d.rounded_rectangle([card_x + 46, card_y + 400, card_x + 46 + bar_w, card_y + 400 + bar_h], radius=8, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 46, card_y + 400, card_x + 46 + int(bar_w * 0.38), card_y + 400 + bar_h], radius=8, fill=TEAL_PRIMARY)

    # CPU Load
    d.text((card_x + 410, card_y + 300), "CPU FREQ / LOAD", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 410, card_y + 340), "2.8 GHz · 24%", font=f_val, fill=GOLD_ACCENT)
    
    d.rounded_rectangle([card_x + 410, card_y + 400, card_x + 410 + bar_w, card_y + 400 + bar_h], radius=8, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 410, card_y + 400, card_x + 410 + int(bar_w * 0.24), card_y + 400 + bar_h], radius=8, fill=GOLD_ACCENT)

    # Bottom floating satellite badge
    sat_w, sat_h = 360, 72
    sx, sy = cx - sat_w // 2, card_y + card_h + 40
    sat = rounded_glass_card(sat_w, sat_h, fill=(*SURFACE_LIGHT[:3], 240), border=GOLD_ACCENT, radius=36, border_width=2)
    im.alpha_composite(sat, (sx, sy))
    sd = ImageDraw.Draw(im)
    draw_text_center(sd, "Zero Background Lag", cx, sy + 36, get_font(22, bold=True), TEXT_WHITE)

    return im

# ── 3. GAMES LIBRARY & RAM RECLAIM ─────────────────────────────────────────
def generate_library_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 20

    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 420, TEAL_PRIMARY, 80))
    im.alpha_composite(radial_glow(W, H, cx - 150, cy + 180, 280, GOLD_ACCENT, 70))

    d = ImageDraw.Draw(im)

    # Two large staggered game cards
    card_w, card_h = 330, 400
    
    # Card 1 (Left, slightly back)
    c1_x, c1_y = cx - card_w - 24, cy - card_h // 2 - 40
    c1 = rounded_glass_card(card_w, card_h, fill=(0x12, 0x22, 0x2A, 240), border=BORDER_COLOR, radius=42, border_width=3)
    im.alpha_composite(c1, (c1_x, c1_y))
    
    # Game icon placeholder 1
    d.rounded_rectangle([c1_x + 40, c1_y + 40, c1_x + 150, c1_y + 150], radius=30, fill=(*GOLD_ACCENT[:3], 60), outline=GOLD_ACCENT, width=3)
    f_icon = get_font(42, bold=True)
    draw_text_center(d, "🎮", c1_x + 95, c1_y + 95, f_icon)
    draw_text_center(d, "Cyber RPG", c1_x + card_w // 2, c1_y + 205, get_font(28, bold=True), TEXT_WHITE)
    draw_text_center(d, "Pinned · Safe", c1_x + card_w // 2, c1_y + 250, get_font(20), TEAL_PRIMARY)
    
    # Card 2 (Right, front focus)
    c2_x, c2_y = cx + 24, cy - card_h // 2 + 20
    c2 = rounded_glass_card(card_w, card_h, fill=(0x17, 0x2C, 0x36, 252), border=TEAL_PRIMARY, radius=42, border_width=4)
    im.alpha_composite(c2, (c2_x, c2_y))
    
    d.rounded_rectangle([c2_x + 40, c2_y + 40, c2_x + 150, c2_y + 150], radius=30, fill=(*TEAL_PRIMARY[:3], 60), outline=TEAL_BRIGHT, width=3)
    draw_text_center(d, "⚡", c2_x + 95, c2_y + 95, f_icon)
    draw_text_center(d, "Speed Racer", c2_x + card_w // 2, c2_y + 205, get_font(28, bold=True), TEXT_WHITE)
    draw_text_center(d, "Boost Ready", c2_x + card_w // 2, c2_y + 250, get_font(20), TEAL_BRIGHT)

    # Pin Badge on top of Card 2
    pin_size = 58
    px, py = c2_x + card_w - 70, c2_y + 24
    d.ellipse([px, py, px + pin_size, py + pin_size], fill=GOLD_ACCENT)
    draw_text_center(d, "📌", px + pin_size // 2, py + pin_size // 2, get_font(26))

    # Bottom Reclaim status card
    rc_w, rc_h = 660, 100
    rx, ry = cx - rc_w // 2, cy + 240
    rc = rounded_glass_card(rc_w, rc_h, fill=(0x10, 0x1F, 0x26, 248), border=TEAL_PRIMARY, radius=46, border_width=3)
    im.alpha_composite(rc, (rx, ry))
    
    rd = ImageDraw.Draw(im)
    draw_text_center(rd, "SAFE RAM RECLAIM (90% CAP)", cx, ry + 36, get_font(22, bold=True), TEAL_PRIMARY)
    draw_text_center(rd, "Reclaims inactive memory without force-stopping apps", cx, ry + 68, get_font(17), TEXT_MUTED)

    return im

# ── 4. SYSTEM ACCESS & ELEVATION ──────────────────────────────────────────
def generate_access_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 20

    im.alpha_composite(radial_glow(W, H, cx, cy - 40, 440, TEAL_PRIMARY, 90))
    im.alpha_composite(radial_glow(W, H, cx, cy - 40, 220, GOLD_ACCENT, 100))

    d = ImageDraw.Draw(im)

    # Large Central Shield shape
    sw, sh = 310, 360
    sx, sy = cx - sw // 2, cy - sh // 2 - 40
    
    # Outer shield border
    d.polygon([
        (cx, sy),
        (cx + sw // 2, sy + 60),
        (cx + sw // 2, sy + 230),
        (cx, sy + sh),
        (cx - sw // 2, sy + 230),
        (cx - sw // 2, sy + 60),
    ], fill=(0x14, 0x26, 0x2F, 245), outline=TEAL_BRIGHT, width=5)

    # Inner shield accent
    d.polygon([
        (cx, sy + 38),
        (cx + sw // 2 - 38, sy + 82),
        (cx + sw // 2 - 38, sy + 210),
        (cx, sy + sh - 38),
        (cx - sw // 2 + 38, sy + 210),
        (cx - sw // 2 + 38, sy + 82),
    ], fill=(0x1B, 0x32, 0x3D, 252), outline=TEAL_PRIMARY, width=3)

    # Shield Keyhole / Emblem
    d.ellipse([cx - 36, sy + 105, cx + 36, sy + 175], fill=TEAL_BRIGHT)
    d.polygon([(cx - 24, sy + 155), (cx + 24, sy + 155), (cx + 15, sy + 220), (cx - 15, sy + 220)], fill=TEAL_BRIGHT)

    # Dual elevation chips below
    chip_w, chip_h = 300, 78
    
    # Shizuku chip
    c1_x, c1_y = cx - chip_w - 16, cy + 200
    c1 = rounded_glass_card(chip_w, chip_h, fill=(0x11, 0x22, 0x2A, 245), border=TEAL_PRIMARY, radius=38, border_width=3)
    im.alpha_composite(c1, (c1_x, c1_y))
    d.text((c1_x + 36, c1_y + 24), "⚡ Shizuku API", font=get_font(24, bold=True), fill=TEAL_BRIGHT)
    
    # Root chip
    c2_x, c2_y = cx + 16, cy + 200
    c2 = rounded_glass_card(chip_w, chip_h, fill=(0x11, 0x22, 0x2A, 245), border=BORDER_COLOR, radius=38, border_width=3)
    im.alpha_composite(c2, (c2_x, c2_y))
    d.text((c2_x + 48, c2_y + 24), "# Root su", font=get_font(24, bold=True), fill=GOLD_ACCENT)

    # Footer note
    f_sub = get_font(20)
    draw_text_center(d, "On-device security · Zero telemetry", cx, cy + 320, f_sub, TEXT_MUTED)

    return im

def main():
    print("Generating high-DPI Onboarding illustration assets (1080x1080)...")
    
    purge_img = generate_purge_art()
    purge_path = RES_DRAWABLE / "ic_onboard_purge.png"
    purge_img.save(purge_path, "PNG", optimize=True)
    print(f"  ✓ {purge_path.name} ({purge_img.size})")

    hud_img = generate_hud_art()
    hud_path = RES_DRAWABLE / "ic_onboard_hud.png"
    hud_img.save(hud_path, "PNG", optimize=True)
    print(f"  ✓ {hud_path.name} ({hud_img.size})")

    library_img = generate_library_art()
    library_path = RES_DRAWABLE / "ic_onboard_library.png"
    library_img.save(library_path, "PNG", optimize=True)
    print(f"  ✓ {library_path.name} ({library_img.size})")

    access_img = generate_access_art()
    access_path = RES_DRAWABLE / "ic_onboard_access.png"
    access_img.save(access_path, "PNG", optimize=True)
    print(f"  ✓ {access_path.name} ({access_img.size})")

    print("High-DPI onboarding art generated successfully!")

if __name__ == "__main__":
    main()
