# Store listing assets

## App icon (official)

Graphite (dark plate) and Vellum (light plate) masters live beside the Play sizes:

| File | Role |
|------|------|
| `icon_graphite.png` | 1024 Graphite master |
| `icon_vellum.png` | 1024 Vellum master |
| `icon.png` / `app_icon_512.png` / `icon_1024.png` | Play default = **Graphite** |
| `icon_48`…`icon_256.png` | Density ladder from Graphite |

In-app / launcher: Graphite vs Vellum switches with the selected Iron theme (`ThemeBrand`).

## Generated Play phone set (current)

Script: `generate_playstore_images.py`

**Layout:** text zone top · one large phone bottom (no overlap, no multi-phone clutter)  
**Visual:** Zen Organic **Dark** (`ZenColors.Dark`) — bg `#0A1317`, primary `#6FD8C8`, onSurface `#E0F2F8`, Plus Jakarta Sans, dark surface bezel  
**Sources:** `docs/screenshots/v1/`  
**Feature graphic:** dark twin of brand layout (same copy/structure)

| # | File | Claim | Shot |
|---|------|-------|------|
| 1 | `1_hero.png` | Apex Core · Free resources for focus | home (+ logo) |
| 2 | `2_purge.png` | Purge Engine · Deep freeze · Shizuku or Root | home |
| 3 | `3_ram_free.png` | RAM Free · Force system reclaim | free_ram |
| 4 | `4_overlay.png` | Performance HUD · Live FPS · RAM · CPU | overlay |
| 5 | `5_pin_apps.png` | Pin Apps · Protect what you need | pin_apps |
| 6 | `6_library.png` | Your Library · Add games and apps | add_apps |
| 7 | `7_settings.png` | Honest Elevation · Shizuku · Root · Privacy | settings |
| 8 | `8_cta.png` | Play Local · No ads · No accounts · On-device | launcher |

```bash
python3 docs/storelisting/generate_playstore_images.py
```

All phone frames: **1080×1920**. Feature graphic: **1024×500**.
