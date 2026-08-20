#!/usr/bin/env python3
"""ApexCore Play Store listing — Zen Organic Dark Deluxe.

Modern premium 1080x1920 portrait frames (1_hero … 8_cta) and a 1024x500
feature graphic. Visual system:

- Multi-layer radial chromatic atmosphere (teal spotlight, warm amber corner,
  lavender side glow) over deep obsidian slate.
- Ultra-fine dot matrix + soft film grain + smooth vignette.
- Flagship titanium bezel with 2.5D corners, inner metallic stroke, top-left
  specular sheen, top pill cutout, and dual-stage drop shadows (contact +
  ambient diffuse).
- Pill kicker badges with gradient border and micro-dot, crisp Plus Jakarta
  Sans headlines (58-64pt) and clear benefit subtitles.

The 8 frames are synced to fastlane metadata
(fastlane/metadata/android/en-US/images/…) and docs/storelisting/phoneScreenshots/.
"""
from __future__ import annotations

import math
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path("/home/abhaybyte/repos/apexcore")
SHOTS_PRIMARY = ROOT / "docs" / "storelisting" / "organic_capture"
SHOTS_FALLBACK = ROOT / "docs" / "screenshots" / "v1"
ICON = ROOT / "docs" / "brand" / "icon.png"
OUT = ROOT / "docs" / "storelisting"
PHONE_OUT_DIRS = [
    OUT / "phoneScreenshots",
    ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots",
]
FONT_DIR = Path("/tmp/storelisting-fonts")
FONT_DIR.mkdir(parents=True, exist_ok=True)
W, H = 1080, 1920
FEATURE_W, FEATURE_H = 1024, 500
SHOT_ASPECT = 1264 / 2780  # 0.4547 — organic captures

_FONT_URLS = {
    "bold": (
        "PlusJakartaSans-Bold.ttf",
        "https://github.com/tokotype/PlusJakartaSans/raw/master/fonts/ttf/PlusJakartaSans-Bold.ttf",
    ),
    "med": (
        "PlusJakartaSans-Medium.ttf",
        "https://github.com/tokotype/PlusJakartaSans/raw/master/fonts/ttf/PlusJakartaSans-Medium.ttf",
    ),
    "reg": (
        "PlusJakartaSans-Regular.ttf",
        "https://github.com/tokotype/PlusJakartaSans/raw/master/fonts/ttf/PlusJakartaSans-Regular.ttf",
    ),
}
for _name, _url in _FONT_URLS.values():
    p = FONT_DIR / _name
    if not p.exists():
        try:
            urllib.request.urlretrieve(_url, p)
        except Exception as e:
            print(f"[warn] font download {_name}: {e}")

# ── Zen Organic Dark Deluxe palette ─────────────────────────────────
BG_OBSIDIAN = (7, 14, 18, 255)          # #070E12
BG_SLATE = (10, 19, 23, 255)            # #0A1317
CYAN = (0x4E, 0xE4, 0xD0)               # #4EE4D0
GOLD = (0xF4, 0xC9, 0x6B)               # #F4C96B
LAVENDER = (0x7B, 0x61, 0xFF)           # #7B61FF
TEXT = (0xF2, 0xFA, 0xFC)               # #F2FAFC
TEXT_DIM = (0x9A, 0xB3, 0xBE)           # #9AB3BE
MINT = (0x4E, 0xF5, 0xC6)               # #4EF5C6 badge
BEZEL = (0x0C, 0x14, 0x18)              # titanium dark matte
BEZEL_EDGE = (0x3A, 0x55, 0x60)         # inner metallic stroke
SPECULAR = (0x6F, 0xD8, 0xC8)           # #6FD8C8 top-left sheen
NOISE_SEED = 42


def font(weight: str, size: int) -> ImageFont.ImageFont:
    name = {
        "bold": "PlusJakartaSans-Bold.ttf",
        "med": "PlusJakartaSans-Medium.ttf",
        "reg": "PlusJakartaSans-Regular.ttf",
    }[weight]
    try:
        return ImageFont.truetype(str(FONT_DIR / name), size)
    except Exception:
        return ImageFont.load_default()


def text_size(d: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont):
    b = d.textbbox((0, 0), text, font=fnt)
    return b[2] - b[0], b[3] - b[1]


def resolve_shot(*names: str) -> Path:
    for base in (SHOTS_PRIMARY, SHOTS_FALLBACK):
        for n in names:
            p = base / n
            if p.is_file():
                return p
    raise FileNotFoundError(f"No screenshot for {names}")


def load_shot(*names: str) -> Image.Image:
    return Image.open(resolve_shot(*names)).convert("RGBA")


def load_logo(size: int = 280) -> Image.Image:
    im = Image.open(ICON).convert("RGBA")
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    return im


# ── background layers ───────────────────────────────────────────────

def radial_glow(w, h, cx, cy, radius, color, peak_a=70):
    scale = 4
    ys = np.arange(max(1, h // scale), dtype=np.float32)
    xs = np.arange(max(1, w // scale), dtype=np.float32)
    xx, yy = np.meshgrid(xs, ys)
    dist = np.sqrt(((xx - cx / scale) / (radius / scale)) ** 2 + ((yy - cy / scale) / (radius / scale)) ** 2)
    a = (np.clip(1.0 - dist, 0, 1) ** 2 * peak_a).astype(np.uint8)
    r, g, b = color[:3]
    arr = np.zeros((max(1, h // scale), max(1, w // scale), 4), dtype=np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3] = r, g, b, a
    return Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)


def dot_matrix(w, h, step=42, seed=NOISE_SEED + 3):
    """Ultra-fine dot grid, ~8-10% alpha with a faint teal super-grid."""
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    rng = np.random.default_rng(seed)
    for y in range(step, h, step):
        for x in range(step, w, step):
            if rng.random() < 0.85:
                d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(*TEXT[:3], 20))
    for x in range(step * 4, w, step * 4):
        for y in range(step * 4, h, step * 4):
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(*CYAN, 26))
    return layer


def noise_layer(w, h, alpha=20, seed=NOISE_SEED):
    rng = np.random.default_rng(seed)
    hw, hh = max(1, w // 2), max(1, h // 2)
    v = rng.integers(0, 256, size=(hh, hw), dtype=np.uint8)
    a = rng.integers(0, alpha + 1, size=(hh, hw), dtype=np.uint8)
    arr = np.zeros((hh, hw, 4), dtype=np.uint8)
    arr[..., 0] = arr[..., 1] = arr[..., 2] = v
    arr[..., 3] = a
    return (
        Image.fromarray(arr, "RGBA")
        .resize((w, h), Image.Resampling.BILINEAR)
        .filter(ImageFilter.GaussianBlur(0.8))
    )


def vignette(im: Image.Image, w=W, h=H) -> Image.Image:
    scale = 4
    xx, yy = np.meshgrid(
        np.linspace(-1, 1, w // scale, dtype=np.float32),
        np.linspace(-1, 1, h // scale, dtype=np.float32),
    )
    edge = np.maximum(np.abs(xx), np.abs(yy))
    a = np.where(edge > 0.72, ((edge - 0.72) / 0.28) ** 2 * 88, 0).astype(np.uint8)
    arr = np.zeros((h // scale, w // scale, 4), dtype=np.uint8)
    arr[..., 3] = a
    vig = Image.fromarray(arr, "RGBA").resize((w, h), Image.Resampling.BILINEAR)
    return Image.alpha_composite(im, vig)


def make_base(variant: int = 0, w=W, h=H) -> Image.Image:
    im = Image.new("RGBA", (w, h), BG_SLATE)
    # subtle vertical depth
    depth = np.zeros((h, w, 4), dtype=np.uint8)
    depth[..., 3] = np.linspace(0, 38, h, dtype=np.uint8)[:, None]
    im = Image.alpha_composite(im, Image.fromarray(depth, "RGBA"))
    shift = (variant * 31 + 11) % 60
    glows = [
        (w * 0.5, h * 0.14, w * 0.5, CYAN, 62),       # primary teal spotlight
        (w * 0.90, h * 0.78, w * 0.42, GOLD, 34),     # warm amber corner
        (w * 0.08, h * 0.50, w * 0.34, LAVENDER, 20), # soft lavender side
    ]
    for i, (cx, cy, rad, col, peak) in enumerate(glows):
        im = Image.alpha_composite(
            im, radial_glow(w, h, cx + (shift if i == 0 else 0), cy - (shift // 2 if i == 0 else 0), rad, col, peak)
        )
    if w >= 800:  # dot matrix only for full canvas (skip feature small strip)
        im = Image.alpha_composite(im, dot_matrix(w, h))
    im = Image.alpha_composite(im, noise_layer(w, h, 20, NOISE_SEED + variant))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, 5], fill=CYAN)
    d.rectangle([0, h - 5, w, h], fill=CYAN)
    return im


# ── typography helpers ──────────────────────────────────────────────

def draw_centered(d: ImageDraw.ImageDraw, cx: int, y: int, text: str, fnt, fill, shadow=True):
    tw, th = text_size(d, text, fnt)
    if shadow:
        d.text((cx - tw // 2 + 2, y + 3), text, font=fnt, fill=(0, 0, 0, 110))
    d.text((cx - tw // 2, y), text, font=fnt, fill=fill)
    return th


def kicker_pill(im: Image.Image, text: str, y: int, cx: int, fnt) -> int:
    """Pill badge: gradient border, translucent fill, micro-dot icon."""
    d = ImageDraw.Draw(im)
    dot = "\u25c6 "  # ◆ micro-dot
    full = dot + text
    tw, th = text_size(d, full, fnt)
    pad_x, pad_y = 26, 14
    x0, x1 = cx - (tw + pad_x * 2) // 2, cx + (tw + pad_x * 2) // 2
    y0, y1 = y, y + th + pad_y * 2
    radius = (y1 - y0) // 2

    # gradient border (cyan → gold) + inner fill
    border = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
    bd = ImageDraw.Draw(border)
    bd.rounded_rectangle([0, 0, border.size[0] - 1, border.size[1] - 1], radius=radius, fill=BG_OBSIDIAN)
    grad = np.zeros((border.size[1], border.size[0], 4), dtype=np.uint8)
    for yy in range(border.size[1]):
        t = yy / max(1, border.size[1] - 1)
        col = tuple(int(a * (1 - t) + b * t) for a, b in zip(CYAN, GOLD))
        grad[yy, :, :3] = col
        grad[yy, :, 3] = 200
    ring = Image.fromarray(grad, "RGBA")
    mask = Image.new("L", border.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, border.size[0] - 1, border.size[1] - 1], radius=radius, fill=255)
    outer = Image.composite(ring, border, mask)
    inner_mask = Image.new("L", border.size, 0)
    ImageDraw.Draw(inner_mask).rounded_rectangle([2, 2, border.size[0] - 3, border.size[1] - 3], radius=max(2, radius - 2), fill=255)
    inner = Image.new("RGBA", border.size, (0, 0, 0, 0))
    ImageDraw.Draw(inner).rounded_rectangle([2, 2, border.size[0] - 3, border.size[1] - 3], radius=max(2, radius - 2), fill=(*BG_SLATE[:3], 200))
    pill = Image.composite(inner, outer, inner_mask)

    # glow behind pill
    im.alpha_composite(radial_glow(im.size[0], im.size[1], cx, y + (y1 - y0) // 2, 240, CYAN, 30))
    im.alpha_composite(pill, (x0, y0))

    # text: mint tint + dot in mint
    d2 = ImageDraw.Draw(im)
    dot_fnt = fnt
    d2.text((x0 + pad_x, y0 + pad_y), full, font=dot_fnt, fill=TEXT)
    tw_dot, _ = text_size(d2, dot, dot_fnt)
    d2.text((x0 + pad_x, y0 + pad_y), dot, font=dot_fnt, fill=MINT)
    return y1 + 30


def gradient_divider(im: Image.Image, y: int, cx: int, span: int):
    step = 4
    d = ImageDraw.Draw(im)
    for x in range(0, span // 2, step):
        t = x / max(1, span // 2)
        a = int(120 * (1 - t))
        col = tuple(int(c * (1 - t) + g * t) for c, g in zip(CYAN, GOLD))
        d.line([(cx - x, y), (cx - x + step, y)], fill=(*col, a), width=2)
        d.line([(cx + x, y), (cx + x + step, y)], fill=(*col, a), width=2)


# ── phone mock ──────────────────────────────────────────────────────

def cover_crop(im: Image.Image, tw: int, th: int, focus_top: bool = True) -> Image.Image:
    iw, ih = im.size
    scale = max(tw / iw, th / ih)
    im = im.resize((max(1, int(iw * scale)), max(1, int(ih * scale))), Image.Resampling.LANCZOS)
    left = (im.size[0] - tw) // 2
    top = max(0, (im.size[1] - th) // 8) if focus_top else (im.size[1] - th) // 2
    return im.crop((left, top, left + tw, top + th))


def phone_frame(shot: Image.Image, pw: int, ph: int, radius: int = 34, bezel: int = 12, top_bar: int = 20) -> Image.Image:
    out = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    # outer titanium body
    d.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=radius, fill=BEZEL)
    # inner metallic stroke
    d.rounded_rectangle([1, 1, pw - 2, ph - 2], radius=max(2, radius - 1), outline=BEZEL_EDGE, width=2)

    # screen
    sx0, sy0 = bezel, bezel + top_bar // 2
    sx1, sy1 = pw - bezel, ph - bezel
    sw, sh = sx1 - sx0, sy1 - sy0
    screen = cover_crop(shot, sw, sh)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], radius=max(2, radius - 4), fill=255)
    rounded = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    rounded.paste(screen, (0, 0), mask)
    out.paste(rounded, (sx0, sy0), rounded)

    # top pill cutout (speaker + camera island)
    pill_w = pw // 3
    d.rounded_rectangle([(pw - pill_w) // 2, bezel - 2, (pw + pill_w) // 2, bezel + 4], radius=5, fill=(0x16, 0x20, 0x26, 255))
    d.ellipse([(pw + pill_w) // 2 - 6, bezel - 1, (pw + pill_w) // 2 + 4, bezel + 9], fill=(0x1B, 0x27, 0x2D, 255))

    # top-left specular sheen on the bezel ring
    ring = Image.new("L", (pw, ph), 0)
    rd = ImageDraw.Draw(ring)
    rd.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=radius, fill=255)
    rd.rounded_rectangle([sx0, sy0, sx1 - 1, sy1 - 1], radius=max(2, radius - 4), fill=0)
    sheen = np.zeros((ph, pw, 4), dtype=np.uint8)
    ys, xs = np.mgrid[0:ph, 0:pw]
    t = np.clip(1 - (xs + ys) / (pw + ph) * 1.9, 0, 1)
    a = (t ** 2 * 70).astype(np.uint8)
    sheen[..., 0], sheen[..., 1], sheen[..., 2] = SPECULAR
    sheen[..., 3] = a
    sheen_im = Image.fromarray(sheen, "RGBA")
    sheen_masked = Image.composite(sheen_im, Image.new("RGBA", (pw, ph), (0, 0, 0, 0)), ring)
    out = Image.alpha_composite(out, sheen_masked)
    return out


def dual_shadow(pw: int, ph: int, radius: int = 34, dx: int = 8, dy: int = 12):
    """Contact (tight, 8px blur) + ambient diffuse (40px blur, 20%)."""
    pad = 110
    cw, ch = pw + pad * 2, ph + pad * 2
    ambient = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    ImageDraw.Draw(ambient).rounded_rectangle(
        [pad + dx - 8, pad + dy - 6, pad + dx + pw + 8, pad + dy + ph + 6],
        radius=radius + 8, fill=(0, 0, 0, 51),
    )
    ambient = ambient.filter(ImageFilter.GaussianBlur(40))
    contact = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    ImageDraw.Draw(contact).rounded_rectangle(
        [pad + dx, pad + dy, pad + dx + pw, pad + dy + ph],
        radius=radius, fill=(0, 0, 0, 150),
    )
    contact = contact.filter(ImageFilter.GaussianBlur(8))
    return Image.alpha_composite(ambient, contact), pad


def paste_phone(canvas: Image.Image, phone: Image.Image, x: int, y: int):
    shadow, pad = dual_shadow(*phone.size)
    canvas.alpha_composite(shadow, (x - pad, y - pad))
    canvas.alpha_composite(phone, (x, y))


def aura(canvas: Image.Image, x: int, y: int, w: int, h: int, color=CYAN, peak=70):
    canvas.alpha_composite(radial_glow(canvas.size[0], canvas.size[1], x + w // 2, y + h // 2, max(w, h) * 0.62, color, peak))


# ── frame rendering ─────────────────────────────────────────────────

def render_page(
    kicker: str,
    headline: str,
    subtitle: str,
    shots: list[str],
    variant: int = 0,
    duo: bool = False,
    aura_peak: int = 70,
) -> Image.Image:
    """Single or staggered-duo flagship phone layout."""
    im = make_base(variant)
    d = ImageDraw.Draw(im)
    cx = W // 2
    y = kicker_pill(im, kicker, 84, cx, font("med", 20))

    head_f = font("bold", 62)
    for line in headline.split("\n"):
        while text_size(d, line, head_f)[0] > W - 120 and head_f.size > 44:
            head_f = font("bold", head_f.size - 4)
    lines = headline.split("\n")[:2]
    for line in lines:
        th = draw_centered(d, cx, y, line, head_f, TEXT)
        y += th + 12
    y += 8

    sub_f = font("reg", 25)
    for line in subtitle.split("\n")[:2]:
        th = draw_centered(d, cx, y, line, sub_f, TEXT_DIM)
        y += th + 8
    y += 22
    gradient_divider(im, y, cx, W - 200)
    y += 44

    avail = H - y - 60
    if duo:
        # staggered duo phones covering ~55% of the page
        per = (W * H * 0.55) / 2
        ph_h = int(math.sqrt(per / SHOT_ASPECT))
        ph_w = int(ph_h * SHOT_ASPECT)
        gap = 34
        x0 = (W - (ph_w * 2 + gap)) // 2
        cy = y + avail // 2
        stagger = 42
        for i, key in enumerate(shots[:2]):
            px = x0 + i * (ph_w + gap)
            py = cy - ph_h // 2 + stagger * (1 if i == 0 else -1)
            aura(im, px - 30, py - 30, ph_w + 60, ph_h + 60, CYAN if i == 0 else GOLD, 46)
            paste_phone(im, phone_frame(load_shot(key), ph_w, ph_h), px, py)
    else:
        ph_h = int(avail * 0.92)
        ph_w = int(ph_h * SHOT_ASPECT)
        if ph_w > W - 80:
            ph_w = W - 80
            ph_h = int(ph_w / SHOT_ASPECT)
        x = (W - ph_w) // 2
        py = y + (avail - ph_h) // 2
        aura(im, x, py, ph_w, ph_h, CYAN, aura_peak)
        paste_phone(im, phone_frame(load_shot(shots[0]), ph_w, ph_h), x, py)
    return vignette(im).convert("RGB")


def render_hero(variant: int = 0) -> Image.Image:
    """Hero: glowing center logo + headline + large aura phone."""
    im = make_base(variant)
    d = ImageDraw.Draw(im)
    cx = W // 2
    y = kicker_pill(im, "ZEN PERFORMANCE", 70, cx, font("med", 20))

    logo = load_logo(170)
    aura(im, cx - 120, y - 20, 240, 240, CYAN, 80)
    im.alpha_composite(logo, (cx - logo.size[0] // 2, y))
    y += logo.size[1] + 26

    head_f = font("bold", 66)
    for line in ["ApexCore", "Pure Game Focus"]:
        while text_size(d, line, head_f)[0] > W - 110 and head_f.size > 46:
            head_f = font("bold", head_f.size - 4)
        th = draw_centered(d, cx, y, line, head_f, TEXT)
        y += th + 10
    y += 6

    sub_f = font("reg", 25)
    th = draw_centered(d, cx, y, "Deep freeze bloat & unlock peak hardware performance", sub_f, TEXT_DIM)
    y += th + 20
    gradient_divider(im, y, cx, W - 220)
    y += 40

    avail = H - y - 60
    ph_h = int(avail * 0.92)
    ph_w = int(ph_h * SHOT_ASPECT)
    if ph_w > W - 80:
        ph_w = W - 80
        ph_h = int(ph_w / SHOT_ASPECT)
    x = (W - ph_w) // 2
    py = y + (avail - ph_h) // 2
    aura(im, x, py, ph_w, ph_h, CYAN, 90)
    aura(im, x + 40, py + 40, ph_w, ph_h, GOLD, 34)
    paste_phone(im, phone_frame(load_shot("01_home_store.png", "01_home.png"), ph_w, ph_h), x, py)
    return vignette(im).convert("RGB")


# ── feature graphic (1024x500) ──────────────────────────────────────

def angled_phone(shot: Image.Image, pw: int, ph: int, angle_deg: float = -6.0) -> Image.Image:
    """Phone frame rotated slightly for a floating perspective."""
    phone = phone_frame(shot, pw, ph, radius=28, bezel=10, top_bar=16)
    ang = math.radians(angle_deg)
    cos, sin = math.cos(ang), math.sin(ang)
    w, h = phone.size
    xform = (cos, sin, 0, -sin, cos, 0, 0, 0)
    # transform expands bounds; rotate around center
    rotated = phone.rotate(angle_deg, resample=Image.Resampling.BICUBIC, expand=True)
    return rotated


def generate_feature_graphic() -> Image.Image:
    im = make_base(1, FEATURE_W, FEATURE_H)
    d = ImageDraw.Draw(im)

    logo = load_logo(96)
    im.alpha_composite(radial_glow(FEATURE_W, FEATURE_H, 88, 96, 150, CYAN, 85))
    im.alpha_composite(logo, (40, 42))

    d.text((44, 156), "ApexCore", font=font("bold", 52), fill=TEXT)
    d.text((44, 220), "Zen Performance Engine", font=font("bold", 23), fill=MINT)
    d.text((44, 258), "Deep freeze bloat · Live HUD · Real kernel tune", font=font("reg", 16), fill=TEXT_DIM)

    chips = ["NO ADS · NO ACCOUNTS", "REAL KERNEL TUNE", "100% ON-DEVICE"]
    cy = 310
    for chip in chips:
        chip_f = font("med", 17)
        tw, th = text_size(d, chip, chip_f)
        cw, ch = tw + 40, 40
        d.rounded_rectangle([44, cy, 44 + cw, cy + ch], radius=ch // 2, fill=(*CYAN, 40), outline=(*CYAN, 200), width=2)
        d.text((44 + 20, cy + (ch - th) // 2 - 1), chip, font=chip_f, fill=TEXT)
        cy += ch + 10

    # angled floating phone with cyan backlight
    pw, ph = 148, 340
    rotated = angled_phone(load_shot("03b_overlay_hud_active.png", "03_overlay.png"), pw, ph, -7)
    rw, rh = rotated.size
    px = FEATURE_W - rw - 46
    py = (FEATURE_H - rh) // 2 + 6
    glow_cx, glow_cy = px + rw // 2, py + rh // 2
    im.alpha_composite(radial_glow(FEATURE_W, FEATURE_H, glow_cx, glow_cy, 260, CYAN, 72))
    shadow, pad = dual_shadow(pw, ph)
    im.alpha_composite(shadow, (px + rw // 2 - pw // 2 - pad, py + rh // 2 - ph // 2 - pad))
    im.alpha_composite(rotated, (px, py))

    return vignette(im, FEATURE_W, FEATURE_H).convert("RGB")


# ── storyboard (plan T12-onboarding-storelisting §3.3) ──────────────

FRAMES = [
    # 1_hero rendered separately
    {
        "stem": "2_purge", "kicker": "01 · PURGE ENGINE",
        "headline": "Deep Freeze Bloat\nZero Background Lag",
        "subtitle": "Reclaim CPU cycles and memory before launching games",
        "shots": ["01_home.png"],
    },
    {
        "stem": "3_ram_free", "kicker": "02 · MEMORY TOOLKIT",
        "headline": "Force RAM Reclaim\nInstant System Clean",
        "subtitle": "Safely clear cached memory with hardware-safe 90% cap",
        "shots": ["04_ram_free.png"],
    },
    {
        "stem": "4_overlay", "kicker": "03 · PERFORMANCE HUD",
        "headline": "Live On-Screen HUD\nFPS · RAM · CPU",
        "subtitle": "Real-time telemetry overlay rendered while you play",
        "shots": ["03b_overlay_hud_active.png", "03_overlay.png"],
    },
    {
        "stem": "5_pin_apps", "kicker": "04 · APP WHITELIST",
        "headline": "Pin Essential Apps\nStay Always Awake",
        "subtitle": "Protect messaging, audio, and tools from being frozen",
        "shots": ["05_pin_apps.png"],
    },
    {
        "stem": "6_library", "kicker": "05 · GAME LIBRARY",
        "headline": "Your Game Hub\nAllocate & Launch",
        "subtitle": "Automatic game detection with one-tap boost launch",
        "shots": ["02_games.png", "07_add_games.png"],
        "duo": True,
    },
    {
        "stem": "7_settings", "kicker": "06 · REAL KERNEL TUNE",
        "headline": "Real Kernel Tuning\n36 Advanced Knobs",
        "subtitle": "Frequency floors, GPU keep-awake & input boost",
        "shots": ["01c_home_game_opt.png", "01_home.png"],
    },
    {
        "stem": "8_cta", "kicker": "07 · PRIVATE & LOCAL",
        "headline": "100% On-Device\nNo Ads · No Tracking",
        "subtitle": "Shizuku & Root privileged speed without cloud dependencies",
        "shots": ["06c_settings_privacy.png", "10_backend_dropdown.png"],
        "duo": True,
    },
]


def sync_copies():
    """Copy frames + feature graphic into phoneScreenshots dirs and fastlane."""
    stems = ["1_hero.png"] + [f["stem"] + ".png" for f in FRAMES]
    for dest in PHONE_OUT_DIRS:
        dest.mkdir(parents=True, exist_ok=True)
        for stem in stems:
            src = OUT / stem
            if src.is_file():
                dest.joinpath(stem).write_bytes(src.read_bytes())
                print(f"    → {dest.name}/{stem}")

    fl_feature = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "featureGraphic.png"
    if fl_feature.parent.is_dir():
        fl_feature.write_bytes((OUT / "feature_graphic.png").read_bytes())
        print(f"    → fastlane featureGraphic.png")
    doc_feature = OUT / "featureGraphic.png"
    doc_feature.write_bytes((OUT / "feature_graphic.png").read_bytes())
    print(f"    → {doc_feature.name}")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print("Generating Zen Organic Dark Deluxe ApexCore listing…")

    hero = OUT / "1_hero.png"
    hero_img = render_hero(variant=0)
    hero_img.save(hero, "PNG", optimize=True)
    print(f"  1_hero.png {hero_img.size}")

    for idx, f in enumerate(FRAMES, start=2):
        img = render_page(
            kicker=f['kicker'],
            headline=f["headline"],
            subtitle=f["subtitle"],
            shots=f["shots"],
            variant=idx,
            duo=f.get("duo", False),
            aura_peak=66,
        )
        path = OUT / f"{f['stem']}.png"
        img.save(path, "PNG", optimize=True)
        print(f"  {f['stem']}.png {img.size}")

    fg = OUT / "feature_graphic.png"
    fg_img = generate_feature_graphic()
    fg_img.save(fg, "PNG", optimize=True)
    print(f"  feature_graphic.png {fg_img.size}")

    sync_copies()
    print("Done →", OUT)


if __name__ == "__main__":
    main()
