#!/usr/bin/env python3
"""ApexCore Play Store listing frames — simple.

Portrait 1080x1920, Zen Organic dark. One claim per page:
title + tagline up top, then a single large phone (or two
phones stacked vertically) at the bottom. No side-by-side rows.
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
FONT_DIR = Path("/tmp/storelisting-fonts")
FONT_DIR.mkdir(parents=True, exist_ok=True)
W, H = 1080, 1920
FEATURE_W, FEATURE_H = 1024, 500

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

BG = (0x0A, 0x13, 0x17, 255)
SURFACE_BRIGHT = (0x1F, 0x32, 0x39, 255)
OUTLINE_VAR = (0x3A, 0x4F, 0x56, 255)
PRIMARY = (0x6F, 0xD8, 0xC8, 255)
PRIMARY_SOFT = (0x8C, 0xF5, 0xE4, 255)
GOLD = (0xE7, 0xC2, 0x68, 255)
TEXT = (0xE0, 0xF2, 0xF8, 255)
TEXT_DIM = (0xB0, 0xC4, 0xCA, 255)
ON_PRIMARY = (0x0A, 0x13, 0x17, 255)
NOISE_SEED = 42

SHOT_MAP = {
    "home": ["01_home.png", "01_home_store.png", "home.png"],
    "home_scrolled": ["01b_home_scrolled.png", "01_home.png", "home.png"],
    "games": ["02_games.png", "08_games_all_apps.png"],
    "overlay": ["03_overlay.png", "overlay.png"],
    "overlay_hud": ["03b_overlay_hud_active.png", "03_overlay.png", "overlay.png"],
    "overlay_v1": ["overlay.png"],
    "optimisations": ["01c_home_game_opt.png", "optimisations.png"],
    "ram_free": ["04_ram_free.png", "free_ram.png"],
    "pin": ["05_pin_apps.png", "pin_apps.png"],
    "settings": ["06_settings.png", "settings.png"],
    "privacy": ["06c_settings_privacy.png", "06_settings.png", "settings.png"],
    "add_games": ["07_add_games.png", "add_apps.png"],
    "games_all": ["08_games_all_apps.png", "02_games.png"],
    "launcher": ["launcher.png", "01_home.png", "home.png"],
}


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


def resolve_shot(key: str) -> Path:
    for name in SHOT_MAP.get(key, [key]):
        for base in (SHOTS_PRIMARY, SHOTS_FALLBACK):
            p = base / name
            if p.is_file():
                return p
    raise FileNotFoundError(f"No screenshot for '{key}'")


def load_shot(key: str) -> Image.Image:
    return Image.open(resolve_shot(key)).convert("RGBA")


def load_logo(size: int = 280) -> Image.Image:
    im = Image.open(ICON).convert("RGBA")
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    return im


def text_size(d: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont):
    b = d.textbbox((0, 0), text, font=fnt)
    return b[2] - b[0], b[3] - b[1]


def wrap_lines(d, text: str, fnt, max_w: int) -> list[str]:
    lines, cur = [], ""
    for w in text.split():
        trial = (cur + " " + w).strip()
        if text_size(d, trial, fnt)[0] <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


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


def noise_layer(w, h, alpha=14, seed=NOISE_SEED):
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


def grid_layer(w, h, step=36):
    g = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    for x in range(0, w, step):
        d.line([(x, 0), (x, h)], fill=(*OUTLINE_VAR[:3], 26), width=1)
    for y in range(0, h, step):
        d.line([(0, y), (w, y)], fill=(*OUTLINE_VAR[:3], 26), width=1)
    for x in range(0, w, step * 4):
        d.line([(x, 0), (x, h)], fill=(*PRIMARY[:3], 14), width=1)
    for y in range(0, h, step * 4):
        d.line([(0, y), (w, y)], fill=(*PRIMARY[:3], 14), width=1)
    return g


def scanlines(w, h, gap=5, a=8):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(0, h, gap):
        d.line([(0, y), (w, y)], fill=(0, 0, 0, a), width=1)
    return layer


def vignette(im: Image.Image) -> Image.Image:
    scale = 4
    xx, yy = np.meshgrid(
        np.linspace(-1, 1, W // scale, dtype=np.float32),
        np.linspace(-1, 1, H // scale, dtype=np.float32),
    )
    edge = np.maximum(np.abs(xx), np.abs(yy))
    a = np.where(edge > 0.75, ((edge - 0.75) / 0.25) ** 2 * 90, 0).astype(np.uint8)
    arr = np.zeros((H // scale, W // scale, 4), dtype=np.uint8)
    arr[..., 3] = a
    vig = Image.fromarray(arr, "RGBA").resize((W, H), Image.Resampling.BILINEAR)
    return Image.alpha_composite(im, vig)


def make_base(variant: int = 0) -> Image.Image:
    im = Image.new("RGBA", (W, H), BG)
    depth = np.zeros((H, W, 4), dtype=np.uint8)
    depth[..., 3] = np.linspace(0, 36, H, dtype=np.uint8)[:, None]
    im = Image.alpha_composite(im, Image.fromarray(depth, "RGBA"))

    for i, (cx, cy, rad, peak, col) in enumerate(
        [
            (W * 0.5, H * 0.16, 520, 55, PRIMARY),
            (W * 0.15, H * 0.80, 420, 32, PRIMARY),
            (W * 0.88, H * 0.55, 390, 26, GOLD),
        ]
    ):
        shift = (variant * 37 + i * 19) % 80
        im = Image.alpha_composite(im, radial_glow(W, H, cx + shift, cy - shift // 2, rad, col, peak))

    im = Image.alpha_composite(im, grid_layer(W, H))
    im = Image.alpha_composite(im, noise_layer(W, H, 14, NOISE_SEED + variant))
    im = Image.alpha_composite(im, scanlines(W, H))

    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 5], fill=PRIMARY)
    d.rectangle([0, H - 5, W, H], fill=PRIMARY)
    return im


# ── phone mock ──────────────────────────────────────────────────────

def cover_crop(im: Image.Image, tw: int, th: int) -> Image.Image:
    iw, ih = im.size
    scale = max(tw / iw, th / ih)
    im = im.resize((max(1, int(iw * scale)), max(1, int(ih * scale))), Image.Resampling.LANCZOS)
    left = (im.size[0] - tw) // 2
    top = max(0, (im.size[1] - th) // 8)
    return im.crop((left, top, left + tw, top + th))


def phone_frame(shot: Image.Image, pw: int, ph: int, bezel: int = 10, top_bar: int = 18) -> Image.Image:
    out = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=28, fill=(0x0E, 0x14, 0x16, 255))
    d.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=28, outline=PRIMARY, width=3)

    sx0, sy0 = bezel, bezel + top_bar // 2
    sx1, sy1 = pw - bezel, ph - bezel
    sw, sh = sx1 - sx0, sy1 - sy0
    screen = cover_crop(shot, sw, sh)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], radius=18, fill=255)
    rounded = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    rounded.paste(screen, (0, 0), mask)
    out.paste(rounded, (sx0, sy0), rounded)

    bar_w = pw // 4
    d.rounded_rectangle([(pw - bar_w) // 2, 10, (pw + bar_w) // 2, 16], radius=3, fill=SURFACE_BRIGHT)
    return out


def paste_phone(canvas: Image.Image, phone: Image.Image, x: int, y: int, shadow: int = 10):
    pw, ph = phone.size
    d = ImageDraw.Draw(canvas)
    # soft drop shadow below only — no right-side shadow bar
    d.rectangle([x + shadow, y + ph, x + pw, y + ph + shadow], fill=SURFACE_BRIGHT)
    canvas.paste(phone, (x, y), phone)


# ── page layout: kicker · title · tagline · phones (vertical stack) ─

def render_page(
    title: str,
    tagline: str,
    shots: list[tuple[str, str]],
    kicker: str = "APEX CORE  ·  PLAY LISTING",
    variant: int = 0,
    show_logo: bool = False,
    duo_side: bool = False,
    card_hero: str | None = None,
) -> Image.Image:
    im = make_base(variant)
    d = ImageDraw.Draw(im)
    y = 84

    kick = font("med", 18)
    tw, th = text_size(d, kicker, kick)
    d.text(((W - tw) // 2, y), kicker, font=kick, fill=PRIMARY_SOFT)
    y += th + 30

    title_f = font("bold", 64)
    lines = wrap_lines(d, title, title_f, W - 100)
    while len(lines) > 2 and title_f.size > 44:
        title_f = font("bold", title_f.size - 4)
        lines = wrap_lines(d, title, title_f, W - 100)
    for line in lines[:2]:
        tw, th = text_size(d, line, title_f)
        d.text(((W - tw) // 2, y), line, font=title_f, fill=TEXT)
        y += th + 10
    y += 12

    tag_f = font("reg", 26)
    tag_lines = wrap_lines(d, tagline, tag_f, W - 130)[:2]
    for line in tag_lines:
        tw, th = text_size(d, line, tag_f)
        d.text(((W - tw) // 2, y), line, font=tag_f, fill=TEXT_DIM)
        y += th + 8
    y += 26

    d.line([(80, y), (W - 80, y)], fill=(*PRIMARY[:3], 90), width=2)
    y += 34

    if show_logo:
        logo = load_logo(150)
        im.alpha_composite(radial_glow(W, H, W / 2, y + 75, 220, PRIMARY, 80))
        im.alpha_composite(logo, ((W - logo.size[0]) // 2, y))
        y += logo.size[1] + 30

    # phone zone: one large phone, two stacked, duo side-by-side, or card
    avail = H - y - 64
    if card_hero:
        # standalone screenshot shown as a large rounded card (no phone frame)
        shot = load_shot(card_hero)
        iw, ih = shot.size
        c_h = int(avail * 0.9)
        c_w = int(iw * c_h / ih)
        if c_w > W - 100:
            c_w = W - 100
            c_h = int(ih * c_w / iw)
        shot = shot.resize((c_w, c_h), Image.Resampling.LANCZOS)
        radius = 56
        mask = Image.new("L", (c_w, c_h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, c_w - 1, c_h - 1], radius=radius, fill=255)
        card = Image.new("RGBA", (c_w, c_h), (0, 0, 0, 0))
        card.paste(shot, (0, 0), mask)
        cx = (W - c_w) // 2
        cy = y + (avail - c_h) // 2
        paste_phone(im, card, cx, cy, 14)
    elif duo_side:
        # two phones side by side, one slightly up / one slightly down,
        # together covering ~50% of the page (no labels)
        per = (W * H * 0.5) / 2
        ph_h = int(math.sqrt(per * 19.5 / 9))
        ph_w = int(ph_h * 9 / 19.5)
        gap = 36
        x0 = (W - (ph_w * 2 + gap)) // 2
        cy = y + avail // 2
        stagger = 46
        for i, (key, _) in enumerate(shots[:2]):
            px = x0 + i * (ph_w + gap)
            py = cy - ph_h // 2 + stagger * (1 if i == 0 else -1)
            paste_phone(im, phone_frame(load_shot(key), ph_w, ph_h), px, py, 10)
    elif len(shots) == 1:
        ph_h = int(avail * 0.92)
        ph_w = int(ph_h * 9 / 19.5)
        if ph_w > W - 72:
            ph_w = W - 72
            ph_h = int(ph_w * 19.5 / 9)
        x = (W - ph_w) // 2
        py = y + (avail - ph_h) // 2
        phone = phone_frame(load_shot(shots[0][0]), ph_w, ph_h)
        paste_phone(im, phone, x, py, 12)
        if shots[0][1]:
            lf = font("med", 16)
            tw, th = text_size(d, shots[0][1], lf)
            d.text(((W - tw) // 2, py + ph_h + 14), shots[0][1], font=lf, fill=PRIMARY_SOFT)
    else:
        ph_h = int((avail - 56) / 2)
        ph_w = int(ph_h * 9 / 19.5)
        if ph_w > W - 96:
            ph_w = W - 96
            ph_h = int(ph_w * 19.5 / 9)
        x = (W - ph_w) // 2
        py = y + (avail - (ph_h * 2 + 44)) // 2
        lf = font("med", 16)
        for key, label in shots[:2]:
            phone = phone_frame(load_shot(key), ph_w, ph_h)
            paste_phone(im, phone, x, py, 10)
            if label:
                tw, th = text_size(d, label, lf)
                d.text(((W - tw) // 2, py + ph_h + 10), label, font=lf, fill=PRIMARY_SOFT)
            py += ph_h + 44

    return vignette(im).convert("RGB")


FRAMES = [
    ("1_hero", "APEX CORE", "More resources for focus", "HOME", True),
    ("2_purge", "PURGE ENGINE", "Optimise game performance", "PURGE", False),
    ("3_ram_free", "RAM FREE", "Force system reclaim, on device", "RAM FREE", False),
    ("4_overlay", "LIVE PERFORMANCE HUD", "FPS · RAM · CPU while you play", "HUD", False),
    ("5_pin_apps", "PIN WHAT MUST STAY AWAKE", "Whitelist apps purge never freezes", "PIN APPS", False),
    ("6_library", "GAMES LIBRARY", "Browse · pin · allocate · launch", "LIBRARY", False),
    ("7_settings", "HONEST ELEVATION", "Shizuku · Root · privacy first", "SETTINGS", False),
    ("8_cta", "PLAY LOCAL", "No ads · No accounts · On-device", "ON-DEVICE", False),
]

SHOTS = {
    "1_hero": [("home", "")],
    "2_purge": [("home", "HOME")],
    "3_ram_free": [("ram_free", "MEMORY TOOLKIT")],
    "4_overlay": [("overlay_v1", "LIVE HUD")],
    "5_pin_apps": [("pin", "PIN APPS")],
    "6_library": [("games", "GAMES"), ("add_games", "ADD APPS")],
    "7_settings": [("settings", "SETTINGS")],
    "8_cta": [("home", "HOME"), ("overlay", "HUD")],
}


def generate_feature_graphic():
    im = Image.new("RGBA", (FEATURE_W, FEATURE_H), BG)
    depth = np.zeros((FEATURE_H, FEATURE_W, 4), dtype=np.uint8)
    depth[..., 3] = np.linspace(0, 28, FEATURE_H, dtype=np.uint8)[:, None]
    im = Image.alpha_composite(im, Image.fromarray(depth, "RGBA"))
    im = Image.alpha_composite(im, radial_glow(FEATURE_W, FEATURE_H, FEATURE_W * 0.55, 250, 420, PRIMARY, 55))
    im = Image.alpha_composite(im, grid_layer(FEATURE_W, FEATURE_H, 34))
    im = Image.alpha_composite(im, noise_layer(FEATURE_W, FEATURE_H, 12, NOISE_SEED + 7))
    d = ImageDraw.Draw(im)

    logo = load_logo(92)
    im.alpha_composite(logo, (40, 44))

    d.text((40, 168), "Apex Core", font=font("bold", 46), fill=TEXT)
    d.text((40, 234), "More resources for focus", font=font("bold", 21), fill=PRIMARY)
    d.text((40, 272), "Purge · RAM Free · Live HUD · On-device", font=font("reg", 15), fill=TEXT_DIM)

    chip = "NO ADS · NO ACCOUNTS"
    chip_f = font("med", 17)
    tw, th = text_size(d, chip, chip_f)
    cw, ch = tw + 44, 46
    d.rounded_rectangle([40, 330, 40 + cw, 330 + ch], radius=ch // 2, fill=PRIMARY)
    d.text((40 + 22, 330 + (ch - th) // 2 - 1), chip, font=chip_f, fill=ON_PRIMARY)

    pw, ph = 168, 380
    px = FEATURE_W - pw - 52
    py = (FEATURE_H - ph) // 2
    paste_phone(im, phone_frame(load_shot("home"), pw, ph, bezel=6, top_bar=10), px, py, 6)
    return im.convert("RGB")


# ── hero page: big logo + title + tagline only (no divider, no phones) ─

def render_hero(variant: int = 0) -> Image.Image:
    im = make_base(variant)
    d = ImageDraw.Draw(im)

    kicker = "APEX CORE  ·  PLAY LISTING"
    kick = font("med", 20)
    _, kh = text_size(d, kicker, kick)

    logo = load_logo(420)
    title_f = font("bold", 76)
    title = "APEX CORE"
    _, th = text_size(d, title, title_f)
    tag_f = font("reg", 30)
    tag = "More resources for focus"
    _, tgh = text_size(d, tag, tag_f)

    gap1, gap2, gap3, gap4 = 52, 58, 26, 26
    block_h = kh + gap1 + logo.size[1] + gap2 + th + gap3 + tgh + gap4
    y = (H - block_h) // 2

    tw, _ = text_size(d, kicker, kick)
    d.text(((W - tw) // 2, y), kicker, font=kick, fill=PRIMARY_SOFT)
    y += kh + gap1

    im.alpha_composite(radial_glow(W, H, W / 2, y + logo.size[1] / 2, 360, PRIMARY, 85))
    im.alpha_composite(logo, ((W - logo.size[0]) // 2, y))
    y += logo.size[1] + gap2

    tw, _ = text_size(d, title, title_f)
    d.text(((W - tw) // 2, y), title, font=title_f, fill=TEXT)
    y += th + gap3

    tw, _ = text_size(d, tag, tag_f)
    d.text(((W - tw) // 2, y), tag, font=tag_f, fill=TEXT_DIM)

    return vignette(im).convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print("Generating simple ApexCore listing…")
    for stem, title, tagline, kicker, logo in FRAMES:
        print(f"  {stem} …")
        if stem == "1_hero":
            img = render_hero(variant=FRAMES.index((stem, title, tagline, kicker, logo)))
        else:
            img = render_page(
                title,
                tagline,
                SHOTS[stem],
                kicker=f"// {kicker}",
                variant=FRAMES.index((stem, title, tagline, kicker, logo)),
                duo_side=(stem in ("6_library", "8_cta")),
                card_hero=("optimisations" if stem == "2_purge" else None),
            )
        path = OUT / f"{stem}.png"
        img.save(path, "PNG", optimize=True)
        print(f"    → {path.name} {img.size}")

    fg = OUT / "feature_graphic.png"
    generate_feature_graphic().save(fg, "PNG", optimize=True)
    print(f"  feature_graphic.png: 1024x500")

    fl = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "featureGraphic.png"
    if fl.parent.is_dir():
        fl.write_bytes(fg.read_bytes())
        print(f"  synced → {fl}")
    print("Done →", OUT)


if __name__ == "__main__":
    main()
