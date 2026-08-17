#!/usr/bin/env python3
"""
Generate 4 high-resolution, high-DPI Zen Organic onboarding illustrations for ApexCore.
Canvas size: 1080x1080 with crisp, large TrueType typography and vector glyphs.
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

BOLD_FONT_PATHS = [
    "/usr/share/fonts/noto/NotoSans-Bold.ttf",
    "/usr/share/fonts/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/ubuntu/Ubuntu-Bold.ttf"
]

REGULAR_FONT_PATHS = [
    "/usr/share/fonts/noto/NotoSans-Medium.ttf",
    "/usr/share/fonts/noto/NotoSans-Regular.ttf",
    "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/TTF/DejaVuSans.ttf",
    "/usr/share/fonts/ubuntu/Ubuntu-Regular.ttf"
]

def get_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    paths = BOLD_FONT_PATHS if bold else REGULAR_FONT_PATHS
    for p in paths:
        if Path(p).exists():
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    # Fallback to default truetype
    return ImageFont.truetype("/usr/share/fonts/TTF/DejaVuSans.ttf", size)

def radial_glow(w, h, cx, cy, radius, color, peak_alpha=140):
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

# Vector glyph helpers
def draw_gamepad(d, cx, cy, size=32, color=TEXT_WHITE):
    w, h = size * 1.5, size * 0.9
    x0, y0 = cx - w / 2, cy - h / 2
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=int(h * 0.45), fill=color)
    # D-pad cross on left
    dpad_x, dpad_y = cx - size * 0.4, cy
    d.line([(dpad_x - 7, dpad_y), (dpad_x + 7, dpad_y)], fill=SURFACE, width=3)
    d.line([(dpad_x, dpad_y - 7), (dpad_x, dpad_y + 7)], fill=SURFACE, width=3)
    # Action buttons on right
    btn_x, btn_y = cx + size * 0.4, cy
    d.ellipse([btn_x + 3, btn_y - 4, btn_x + 9, btn_y + 2], fill=SURFACE)
    d.ellipse([btn_x - 7, btn_y + 1, btn_x - 1, btn_y + 7], fill=SURFACE)

def draw_lightning(d, cx, cy, size=34, color=TEAL_BRIGHT):
    pts = [
        (cx + size * 0.1, cy - size * 0.6),
        (cx - size * 0.45, cy + size * 0.05),
        (cx - size * 0.05, cy + size * 0.05),
        (cx - size * 0.2, cy + size * 0.6),
        (cx + size * 0.45, cy - size * 0.05),
        (cx + size * 0.05, cy - size * 0.05),
    ]
    d.polygon(pts, fill=color)

# ── 1. PURGE ENGINE ────────────────────────────────────────────────────────
def generate_purge_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 40

    # Ambient glow
    im.alpha_composite(radial_glow(W, H, cx, cy, 460, TEAL_PRIMARY, 110))
    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 260, TEAL_BRIGHT, 160))

    d = ImageDraw.Draw(im)

    # Concentric orbital rings (high-contrast)
    for r, width, alpha in [(360, 4, 60), (280, 5, 110), (200, 6, 180), (130, 7, 240)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(*TEAL_PRIMARY[:3], alpha), width=width)

    # Orbital nodes
    for angle, r, size, col in [
        (0.5, 280, 18, GOLD_ACCENT),
        (2.0, 280, 16, TEAL_BRIGHT),
        (3.7, 360, 20, TEAL_BRIGHT),
        (5.1, 200, 22, TEAL_PRIMARY),
        (1.1, 200, 17, GOLD_ACCENT),
        (4.4, 130, 16, TEAL_BRIGHT)
    ]:
        nx = cx + int(math.cos(angle) * r)
        ny = cy + int(math.sin(angle) * r)
        im.alpha_composite(radial_glow(W, H, nx, ny, 70, col, 200))
        d.ellipse([nx - size, ny - size, nx + size, ny + size], fill=col)

    # Central Core Orb
    core_r = 96
    d.ellipse([cx - core_r, cy - core_r, cx + core_r, cy + core_r], fill=SURFACE_LIGHT, outline=TEAL_BRIGHT, width=6)
    
    # Core inner glow layers
    d.ellipse([cx - 62, cy - 62, cx + 62, cy + 62], fill=TEAL_PRIMARY)
    d.ellipse([cx - 36, cy - 36, cx + 36, cy + 36], fill=TEAL_BRIGHT)

    # Large Free Memory Badge at bottom
    badge_w, badge_h = 560, 108
    bx, by = cx - badge_w // 2, cy + 280
    badge = rounded_glass_card(badge_w, badge_h, fill=(0x11, 0x22, 0x2B, 250), border=TEAL_PRIMARY, radius=54, border_width=4)
    im.alpha_composite(badge, (bx, by))
    
    bd = ImageDraw.Draw(im)
    f_bold = get_font(38, bold=True)
    f_small = get_font(24, bold=False)
    draw_text_center(bd, "4.2 GB RAM FREED", cx, by + 40, f_bold, TEAL_BRIGHT)
    draw_text_center(bd, "SYSTEM FULLY OPTIMIZED", cx, by + 78, f_small, TEXT_MUTED)

    return im

# ── 2. PERFORMANCE HUD ─────────────────────────────────────────────────────
def generate_hud_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2

    im.alpha_composite(radial_glow(W, H, cx, cy - 50, 460, TEAL_PRIMARY, 95))
    im.alpha_composite(radial_glow(W, H, cx + 200, cy + 160, 320, GOLD_ACCENT, 80))

    # Large Main Floating HUD Glass Card
    card_w, card_h = 820, 540
    card_x, card_y = cx - card_w // 2, cy - card_h // 2 - 40
    card = rounded_glass_card(card_w, card_h, fill=(0x10, 0x1E, 0x25, 252), border=TEAL_PRIMARY, radius=56, border_width=4)
    im.alpha_composite(card, (card_x, card_y))

    d = ImageDraw.Draw(im)

    # Top Header Pill in HUD
    pill_w, pill_h = 220, 60
    px, py = card_x + 50, card_y + 44
    d.rounded_rectangle([px, py, px + pill_w, py + pill_h], radius=30, fill=(*TEAL_PRIMARY[:3], 50), outline=TEAL_PRIMARY, width=2)
    d.ellipse([px + 20, py + 22, px + 36, py + 38], fill=TEAL_BRIGHT)
    f_chip = get_font(26, bold=True)
    d.text((px + 48, py + 16), "LIVE HUD", font=f_chip, fill=TEAL_BRIGHT)

    # FPS Large Metric
    f_fps_num = get_font(110, bold=True)
    f_fps_unit = get_font(38, bold=True)
    d.text((card_x + 50, card_y + 130), "120", font=f_fps_num, fill=TEXT_WHITE)
    d.text((card_x + 280, card_y + 192), "FPS", font=f_fps_unit, fill=TEAL_PRIMARY)

    # Live telemetry waveform
    wave_pts = []
    start_x = card_x + 400
    end_x = card_x + card_w - 50
    base_y = card_y + 190
    for i in range(20):
        x = start_x + (end_x - start_x) * (i / 19.0)
        y = base_y - math.sin(i * 0.55) * 32 - (math.cos(i * 1.1) * 14)
        wave_pts.append((x, y))
    for i in range(len(wave_pts) - 1):
        d.line([wave_pts[i], wave_pts[i+1]], fill=TEAL_BRIGHT, width=6)

    # Divider line
    d.line([(card_x + 50, card_y + 290), (card_x + card_w - 50, card_y + 290)], fill=BORDER_COLOR, width=2)

    # Metrics row (RAM & CPU)
    f_lbl = get_font(24, bold=True)
    f_val = get_font(34, bold=True)
    
    # RAM Meter
    d.text((card_x + 50, card_y + 320), "RAM PRESSURE", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 50, card_y + 365), "38% Normal", font=f_val, fill=TEAL_BRIGHT)
    
    bar_w = 310
    bar_h = 18
    d.rounded_rectangle([card_x + 50, card_y + 435, card_x + 50 + bar_w, card_y + 435 + bar_h], radius=9, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 50, card_y + 435, card_x + 50 + int(bar_w * 0.38), card_y + 435 + bar_h], radius=9, fill=TEAL_PRIMARY)

    # CPU Load
    d.text((card_x + 440, card_y + 320), "CPU FREQ / LOAD", font=f_lbl, fill=TEXT_MUTED)
    d.text((card_x + 440, card_y + 365), "2.8 GHz · 24%", font=f_val, fill=GOLD_ACCENT)
    
    d.rounded_rectangle([card_x + 440, card_y + 435, card_x + 440 + bar_w, card_y + 435 + bar_h], radius=9, fill=SURFACE_LIGHT)
    d.rounded_rectangle([card_x + 440, card_y + 435, card_x + 440 + int(bar_w * 0.24), card_y + 435 + bar_h], radius=9, fill=GOLD_ACCENT)

    # Bottom floating satellite badge
    sat_w, sat_h = 420, 80
    sx, sy = cx - sat_w // 2, card_y + card_h + 35
    sat = rounded_glass_card(sat_w, sat_h, fill=(0x15, 0x2A, 0x33, 245), border=GOLD_ACCENT, radius=40, border_width=3)
    im.alpha_composite(sat, (sx, sy))
    sd = ImageDraw.Draw(im)
    draw_text_center(sd, "Zero Background Lag", cx, sy + 40, get_font(26, bold=True), TEXT_WHITE)

    return im

# ── 3. GAMES LIBRARY & RAM RECLAIM ─────────────────────────────────────────
def generate_library_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 20

    im.alpha_composite(radial_glow(W, H, cx, cy - 30, 440, TEAL_PRIMARY, 90))
    im.alpha_composite(radial_glow(W, H, cx - 160, cy + 190, 300, GOLD_ACCENT, 80))

    d = ImageDraw.Draw(im)

    # Two large staggered game cards
    card_w, card_h = 360, 430
    
    # Card 1 (Left, slightly back)
    c1_x, c1_y = cx - card_w - 24, cy - card_h // 2 - 40
    c1 = rounded_glass_card(card_w, card_h, fill=(0x11, 0x21, 0x29, 245), border=BORDER_COLOR, radius=46, border_width=3)
    im.alpha_composite(c1, (c1_x, c1_y))
    
    # Game icon placeholder 1
    d.rounded_rectangle([c1_x + 45, c1_y + 45, c1_x + 165, c1_y + 165], radius=32, fill=(*GOLD_ACCENT[:3], 65), outline=GOLD_ACCENT, width=3)
    draw_gamepad(d, c1_x + 105, c1_y + 105, size=46, color=TEXT_WHITE)
    draw_text_center(d, "Cyber RPG", c1_x + card_w // 2, c1_y + 225, get_font(32, bold=True), TEXT_WHITE)
    draw_text_center(d, "Pinned · Safe", c1_x + card_w // 2, c1_y + 275, get_font(24, bold=True), TEAL_PRIMARY)
    
    # Card 2 (Right, front focus)
    c2_x, c2_y = cx + 24, cy - card_h // 2 + 20
    c2 = rounded_glass_card(card_w, card_h, fill=(0x16, 0x2B, 0x35, 252), border=TEAL_PRIMARY, radius=46, border_width=4)
    im.alpha_composite(c2, (c2_x, c2_y))
    
    d.rounded_rectangle([c2_x + 45, c2_y + 45, c2_x + 165, c2_y + 165], radius=32, fill=(*TEAL_PRIMARY[:3], 65), outline=TEAL_BRIGHT, width=3)
    draw_lightning(d, c2_x + 105, c2_y + 105, size=48, color=TEAL_BRIGHT)
    draw_text_center(d, "Speed Racer", c2_x + card_w // 2, c2_y + 225, get_font(32, bold=True), TEXT_WHITE)
    draw_text_center(d, "Boost Ready", c2_x + card_w // 2, c2_y + 275, get_font(24, bold=True), TEAL_BRIGHT)

    # Pin Badge on top of Card 2
    pin_size = 64
    px, py = c2_x + card_w - 76, c2_y + 24
    d.ellipse([px, py, px + pin_size, py + pin_size], fill=GOLD_ACCENT)
    d.ellipse([px + 18, py + 18, px + 46, py + 46], fill=(0x3D, 0x2E, 0x00, 255))
    d.ellipse([px + 26, py + 26, px + 38, py + 38], fill=GOLD_ACCENT)

    # Bottom Reclaim status card
    rc_w, rc_h = 760, 114
    rx, ry = cx - rc_w // 2, cy + 260
    rc = rounded_glass_card(rc_w, rc_h, fill=(0x10, 0x1E, 0x25, 250), border=TEAL_PRIMARY, radius=50, border_width=3)
    im.alpha_composite(rc, (rx, ry))
    
    rd = ImageDraw.Draw(im)
    draw_text_center(rd, "SAFE RAM RECLAIM (90% CAP)", cx, ry + 42, get_font(26, bold=True), TEAL_PRIMARY)
    draw_text_center(rd, "Reclaims inactive memory without killing apps", cx, ry + 78, get_font(20), TEXT_MUTED)

    return im

# ── 4. SYSTEM ACCESS & ELEVATION ──────────────────────────────────────────
def generate_access_art() -> Image.Image:
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cx, cy = W // 2, H // 2 - 20

    im.alpha_composite(radial_glow(W, H, cx, cy - 40, 460, TEAL_PRIMARY, 100))
    im.alpha_composite(radial_glow(W, H, cx, cy - 40, 240, GOLD_ACCENT, 110))

    d = ImageDraw.Draw(im)

    # Large Central Shield shape
    sw, sh = 340, 390
    sx, sy = cx - sw // 2, cy - sh // 2 - 40
    
    # Outer shield border
    d.polygon([
        (cx, sy),
        (cx + sw // 2, sy + 65),
        (cx + sw // 2, sy + 250),
        (cx, sy + sh),
        (cx - sw // 2, sy + 250),
        (cx - sw // 2, sy + 65),
    ], fill=(0x13, 0x24, 0x2D, 248), outline=TEAL_BRIGHT, width=6)

    # Inner shield accent
    d.polygon([
        (cx, sy + 42),
        (cx + sw // 2 - 42, sy + 90),
        (cx + sw // 2 - 42, sy + 230),
        (cx, sy + sh - 42),
        (cx - sw // 2 + 42, sy + 230),
        (cx - sw // 2 + 42, sy + 90),
    ], fill=(0x1A, 0x31, 0x3B, 252), outline=TEAL_PRIMARY, width=4)

    # Shield Keyhole / Emblem
    d.ellipse([cx - 40, sy + 115, cx + 40, sy + 195], fill=TEAL_BRIGHT)
    d.polygon([(cx - 28, sy + 170), (cx + 28, sy + 170), (cx + 18, sy + 245), (cx - 18, sy + 245)], fill=TEAL_BRIGHT)

    # Dual elevation chips below
    chip_w, chip_h = 350, 88
    
    # Shizuku chip
    c1_x, c1_y = cx - chip_w - 20, cy + 220
    c1 = rounded_glass_card(chip_w, chip_h, fill=(0x11, 0x22, 0x2A, 250), border=TEAL_PRIMARY, radius=44, border_width=3)
    im.alpha_composite(c1, (c1_x, c1_y))
    cd1 = ImageDraw.Draw(im)
    draw_lightning(cd1, c1_x + 50, c1_y + 44, size=30, color=TEAL_BRIGHT)
    cd1.text((c1_x + 80, c1_y + 26), "Shizuku API", font=get_font(28, bold=True), fill=TEAL_BRIGHT)
    
    # Root chip
    c2_x, c2_y = cx + 20, cy + 220
    c2 = rounded_glass_card(chip_w, chip_h, fill=(0x11, 0x22, 0x2A, 250), border=BORDER_COLOR, radius=44, border_width=3)
    im.alpha_composite(c2, (c2_x, c2_y))
    cd2 = ImageDraw.Draw(im)
    cd2.text((c2_x + 40, c2_y + 26), "# Root su", font=get_font(28, bold=True), fill=GOLD_ACCENT)

    # Footer note
    f_sub = get_font(22, bold=False)
    draw_text_center(d, "On-device security · Zero telemetry", cx, cy + 345, f_sub, TEXT_MUTED)

    return im

def main():
    print("Generating high-visibility TrueType Onboarding illustration assets (1080x1080)...")
    
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

    print("All TrueType high-DPI onboarding art generated successfully!")

if __name__ == "__main__":
    main()
