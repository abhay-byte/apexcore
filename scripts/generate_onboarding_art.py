#!/usr/bin/env python3
"""
Generate 4 high-resolution Zen Organic onboarding illustrations for ApexCore.
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

W, H = 720, 720

# Color Palette (Zen Organic)
BG_DARK = (0x0D, 0x18, 0x1D, 255)
SURFACE = (0x16, 0x27, 0x2E, 255)
SURFACE_LIGHT = (0x1F, 0x35, 0x3E, 255)
TEAL_PRIMARY = (0x6F, 0xD8, 0xC8, 255)
TEAL_BRIGHT = (0x8C, 0xF5, 0xE4, 255)
GOLD_ACCENT = (0xE7, 0xC2, 0x68, 255)
CORAL_ACCENT = (0xF0, 0x8C, 0x7E, 255)
TEXT_WHITE = (0xEE, 0xF7, 0xFA, 255)
TEXT_MUTED = (0x92, 0xB0, 0xBA, 255)
BORDER_COLOR = (0x3A, 0x54, 0x5E, 200)

def radial_glow(w, h, cx, cy, radius, color, peak_alpha=120):
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

def rounded_glass_card(w, h, fill=SURFACE, border=BORDER_COLOR, radius=32):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=fill, outline=border, width=2)
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
    cx, cy = W // 2, H // 2

    # Glow background
    im.alpha_composite(radial_glow(W, H, cx, cy, 300, TEAL_PRIMARY, 90))
    im.alpha_composite(radial_glow(W, H, cx, cy - 20, 160, TEAL_BRIGHT, 140))

    d = ImageDraw.Draw(im)

    # Concentric orbital rings
    for r, width, alpha in [(230, 2, 40), (180, 2, 80), (130, 3, 140), (85, 4, 200)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(*TEAL_PRIMARY[:3], alpha), width=width)

    # Orbital nodes
    for angle, r, size, col in [
        (0.6, 180, 10, GOLD_ACCENT),
        (2.1, 180, 8, TEAL_BRIGHT),
        (3.8, 230, 12, TEAL_BRIGHT),
        (5.2, 130, 14, TEAL_PRIMARY),
        (1.2, 130, 9, GOLD_ACCENT),
    ]:
        nx = cx + int(math.cos(angle) * r)
        ny = cy + int(math.sin(angle) * r)
        im.alpha_composite(radial_glow(W, H, nx, ny, 40, col, 160))
        d.ellipse([nx - size, ny - size, nx + size, ny + size], fill=col)

    # Central Core Shield / Lotus Orb
    core_r = 58
    d.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=SURFACE_LIGHT, outline=TEAL_BRIGHT, width=3)
    
    # Core inner glow
    d.ellipse([cx - 36, cy - 36, cx + 36, cy + 36], fill=TEAL_PRIMARY)
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=TEAL_BRIGHT)

    # Free Memory Badge
    badge_w, badge_h = 240, 56
    bx, by = cx - badge_w // 2, cy + 180
    badge = rounded_glass_card(badge_w, badge_h, fill=(*SURFACE_LIGHT[:3], 240), border=TEAL_PRIMARY, radius=28)
    im.alpha_composite(badge, (bx, by))
    
    bd = ImageDraw.Draw(im)
    f_bold = get_font(20, bold=True)
    f_small = get_font(13, bold=False)
    draw_text_center(bd, "4.2 GB FREED", cx, by + 20, f_bold, TEAL_BRIGHT)
    draw_text_center(bd, "SYSTEM OPTIMIZED", cx, by + 40, f_small, TEXT_MUTED)

    return im

# ── 2. PERFORMANCE HUD ─────────────────────────────────────────────────────
def generate_hud_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2

    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 280, TEAL_PRIMARY, 75))
    im.alpha_composite(radial_glow(W, H, cx + 120, cy + 100, 200, GOLD_ACCENT, 60))

    # Main Floating HUD Glass Card
    card_w, card_h = 480, 320
    card_x, card_y = cx - card_w // 2, cy - card_h // 2 - 20
    card = rounded_glass_card(card_w, card_h, fill=(0x11, 0x20, 0x27, 245), border=TEAL_PRIMARY, radius=36)
    im.alpha_composite(card, (card_x, card_y))

    d = ImageDraw.Draw(im)

    # Top Header Pill in HUD
    pill_w, pill_h = 130, 36
    px, py = card_x + 30, card_y + 28
    d.rounded_rectangle([px, py, px + pill_w, py + pill_h], radius=18, fill=(*TEAL_PRIMARY[:3], 40), outline=TEAL_PRIMARY, width=1)
    d.ellipse([px + 12, py + 14, px + 20, py + 22], fill=TEAL_BRIGHT)
    f_chip = get_font(14, bold=True)
    d.text((px + 28, py + 9), "LIVE HUD", font=f_chip, fill=TEAL_BRIGHT)

    # FPS Large Metric
    f_fps_num = get_font(64, bold=True)
    f_fps_unit = get_font(22, bold=True)
    d.text((card_x + 30, card_y + 82), "120", font=f_fps_num, fill=TEXT_WHITE)
    d.text((card_x + 160, card_y + 116), "FPS", font=f_fps_unit, fill=TEAL_PRIMARY)

    # Live telemetry waveform
    wave_pts = []
    start_x = card_x + 230
    end_x = card_x + card_w - 30
    base_y = card_y + 115
    for i in range(16):
        x = start_x + (end_x - start_x) * (i / 15.0)
        y = base_y - math.sin(i * 0.6) * 18 - (math.cos(i * 1.2) * 8)
        wave_pts.append((x, y))
    for i in range(len(wave_pts) - 1):
        d.line([wave_pts[i], wave_pts[i+1]], fill=TEAL_BRIGHT, width=3)

    # Divider line
    d.line([(card_x + 30, card_y + 175), (card_x + card_w - 30, card_y + 175)], fill=BORDER_COLOR, width=1)

    # Metrics row (RAM & CPU)
    # RAM Meter
    f_lbl = get_font(13, bold=True)
    f_val = get_font(20, bold=True)
    d.text((card_x + 30, card_y + 195), "RAM PRESSURE", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 30, card_y + 222), "38% Normal", font=f_val, fill=TEAL_BRIGHT)
    # Bar
    bar_w = 170
    d.rounded_rectangle([card_x + 30, card_y + 260, card_x + 30 + bar_w, card_y + 270], radius=5, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 30, card_y + 260, card_x + 30 + int(bar_w * 0.38), card_y + 270], radius=5, fill=TEAL_PRIMARY)

    # CPU Load
    d.text((card_x + 260, card_y + 195), "CPU FREQ / LOAD", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 260, card_y + 222), "2.8 GHz · 24%", font=f_val, fill=GOLD_ACCENT)
    # Bar
    d.rounded_rectangle([card_x + 260, card_y + 260, card_x + 260 + bar_w, card_y + 270], radius=5, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 260, card_y + 260, card_x + 260 + int(bar_w * 0.24), card_y + 270], radius=5, fill=GOLD_ACCENT)

    # Small floating satellite pill
    sat_w, sat_h = 220, 50
    sx, sy = cx - sat_w // 2, card_y + card_h + 30
    sat = rounded_glass_card(sat_w, sat_h, fill=(*SURFACE_LIGHT[:3], 230), border=GOLD_ACCENT, radius=25)
    im.alpha_composite(sat, (sx, sy))
    sd = ImageDraw.Draw(im)
    draw_text_center(sd, "Zero Background Lag", cx, sy + 25, get_font(14, bold=True), TEXT_WHITE)

    return im

# ── 3. GAMES LIBRARY & RAM RECLAIM ─────────────────────────────────────────
def generate_library_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2

    im.alpha_composite(radial_glow(W, H, cx, cy - 20, 260, TEAL_PRIMARY, 70))
    im.alpha_composite(radial_glow(W, H, cx - 100, cy + 120, 180, GOLD_ACCENT, 60))

    d = ImageDraw.Draw(im)

    # Two staggered game cards
    card_w, card_h = 210, 260
    
    # Card 1 (Left, slightly back)
    c1_x, c1_y = cx - card_w - 15, cy - card_h // 2 - 30
    c1 = rounded_glass_card(card_w, card_h, fill=(0x13, 0x22, 0x2A, 240), border=BORDER_COLOR, radius=28)
    im.alpha_composite(c1, (c1_x, c1_y))
    
    # Game icon placeholder 1
    d.rounded_rectangle([c1_x + 25, c1_y + 25, c1_x + 95, c1_y + 95], radius=20, fill=(*GOLD_ACCENT[:3], 60), outline=GOLD_ACCENT, width=2)
    f_icon = get_font(26, bold=True)
    draw_text_center(d, "🎮", c1_x + 60, c1_y + 60, f_icon)
    draw_text_center(d, "Cyber RPG", c1_x + card_w // 2, c1_y + 130, get_font(18, bold=True), TEXT_WHITE)
    draw_text_center(d, "Pinned · Safe", c1_x + card_w // 2, c1_y + 160, get_font(13), TEAL_PRIMARY)
    
    # Card 2 (Right, front focus)
    c2_x, c2_y = cx + 15, cy - card_h // 2 + 10
    c2 = rounded_glass_card(card_w, card_h, fill=(0x18, 0x2C, 0x36, 250), border=TEAL_PRIMARY, radius=28)
    im.alpha_composite(c2, (c2_x, c2_y))
    
    d.rounded_rectangle([c2_x + 25, c2_y + 25, c2_x + 95, c2_y + 95], radius=20, fill=(*TEAL_PRIMARY[:3], 60), outline=TEAL_BRIGHT, width=2)
    draw_text_center(d, "⚡", c2_x + 60, c2_y + 60, f_icon)
    draw_text_center(d, "Speed Racer", c2_x + card_w // 2, c2_y + 130, get_font(18, bold=True), TEXT_WHITE)
    draw_text_center(d, "Boost Ready", c2_x + card_w // 2, c2_y + 160, get_font(13), TEAL_BRIGHT)

    # Pin Badge on top of Card 2
    pin_size = 38
    px, py = c2_x + card_w - 45, c2_y + 15
    d.ellipse([px, py, px + pin_size, py + pin_size], fill=GOLD_ACCENT)
    draw_text_center(d, "📌", px + pin_size // 2, py + pin_size // 2, get_font(16))

    # Bottom Reclaim status card
    rc_w, rc_h = 420, 68
    rx, ry = cx - rc_w // 2, cy + 150
    rc = rounded_glass_card(rc_w, rc_h, fill=(0x11, 0x20, 0x27, 245), border=TEAL_PRIMARY, radius=30)
    im.alpha_composite(rc, (rx, ry))
    
    rd = ImageDraw.Draw(im)
    draw_text_center(rd, "SAFE RAM RECLAIM (90% CAP)", cx, ry + 24, get_font(14, bold=True), TEAL_PRIMARY)
    draw_text_center(rd, "Reclaims inactive memory without killing apps", cx, ry + 46, get_font(12), TEXT_MUTED)

    return im

# ── 4. SYSTEM ACCESS & ELEVATION ──────────────────────────────────────────
def generate_access_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2

    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 280, TEAL_PRIMARY, 80))
    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 140, GOLD_ACCENT, 90))

    d = ImageDraw.Draw(im)

    # Central Shield shape
    sw, sh = 200, 240
    sx, sy = cx - sw // 2, cy - sh // 2 - 30
    
    # Outer shield border
    d.polygon([
        (cx, sy),
        (cx + sw // 2, sy + 40),
        (cx + sw // 2, sy + 150),
        (cx, sy + sh),
        (cx - sw // 2, sy + 150),
        (cx - sw // 2, sy + 40),
    ], fill=(0x14, 0x26, 0x2F, 240), outline=TEAL_BRIGHT, width=3)

    # Inner shield accent
    d.polygon([
        (cx, sy + 25),
        (cx + sw // 2 - 25, sy + 55),
        (cx + sw // 2 - 25, sy + 135),
        (cx, sy + sh - 25),
        (cx - sw // 2 + 25, sy + 135),
        (cx - sw // 2 + 25, sy + 55),
    ], fill=(0x1A, 0x30, 0x3A, 250), outline=TEAL_PRIMARY, width=2)

    # Shield Keyhole / Emblem
    d.ellipse([cx - 24, sy + 70, cx + 24, sy + 118], fill=TEAL_BRIGHT)
    d.polygon([(cx - 16, sy + 105), (cx + 16, sy + 105), (cx + 10, sy + 145), (cx - 10, sy + 145)], fill=TEAL_BRIGHT)

    # Dual elevation chips below
    chip_w, chip_h = 190, 52
    
    # Shizuku chip
    c1_x, c1_y = cx - chip_w - 10, cy + 130
    c1 = rounded_glass_card(chip_w, chip_h, fill=(0x12, 0x23, 0x2B, 240), border=TEAL_PRIMARY, radius=26)
    im.alpha_composite(c1, (c1_x, c1_y))
    d.text((c1_x + 22, c1_y + 16), "⚡ Shizuku API", font=get_font(15, bold=True), fill=TEAL_BRIGHT)
    
    # Root chip
    c2_x, c2_y = cx + 10, cy + 130
    c2 = rounded_glass_card(chip_w, chip_h, fill=(0x12, 0x23, 0x2B, 240), border=BORDER_COLOR, radius=26)
    im.alpha_composite(c2, (c2_x, c2_y))
    d.text((c2_x + 30, c2_y + 16), "# Root su", font=get_font(15, bold=True), fill=GOLD_ACCENT)

    # Footer note
    f_sub = get_font(13)
    draw_text_center(d, "On-device security · No internet required", cx, cy + 220, f_sub, TEXT_MUTED)

    return im

def main():
    print("Generating Onboarding illustration assets...")
    
    purge_img = generate_purge_art()
    purge_path = RES_DRAWABLE / "ic_onboard_purge.png"
    purge_img.save(purge_path, "PNG", optimize=True)
    print(f"  ✓ {purge_path.name}")

    hud_img = generate_hud_art()
    hud_path = RES_DRAWABLE / "ic_onboard_hud.png"
    hud_img.save(hud_path, "PNG", optimize=True)
    print(f"  ✓ {hud_path.name}")

    library_img = generate_library_art()
    library_path = RES_DRAWABLE / "ic_onboard_library.png"
    library_img.save(library_path, "PNG", optimize=True)
    print(f"  ✓ {library_path.name}")

    access_img = generate_access_art()
    access_path = RES_DRAWABLE / "ic_onboard_access.png"
    access_img.save(access_path, "PNG", optimize=True)
    print(f"  ✓ {access_path.name}")

    print("All onboarding art generated successfully!")

if __name__ == "__main__":
    main()
