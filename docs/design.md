# ApexCore — Design System

> *ApexCore is the calm before the match.* Every visual decision should feel like the silence inside a high-end gaming headset: matte, focused, premium. The interface is dark, breathable, and quietly confident — it disappears when you're playing and glows only when it matters.

The design language is called **"Summit"** — a precision aesthetic built for performance tools. Cold cyan light on obsidian surfaces. Typography that feels machined. Motion that obeys physics, not theatrics.

---

## 1. Brand Identity

### Logo
**Mark + wordmark, left-aligned.**

- **The Apex Mark** — a 30×30 dp circular dot in pure cyan (`#00E5FF`) with a 1 dp white inner-rim at 20% opacity. Represents the "apex" point of performance: a single, sharp pulse of energy.
- **The Wordmark** — `APEX` in 14 sp, weight 700, letter-spacing `0.3` (loose, technical), color `#FFFFFF`. The "CORE" suffix is implied; we never say it out loud.

**Variants**
| Variant | Use |
|---|---|
| Mark + Wordmark | Top bar, app launcher icon, store listing |
| Mark only | Status bar, notification icon (monochrome white) |
| Wordmark only | Splash screen, loading screens |

### Tagline
> *One tap to reclaim memory & focus CPU*

Used as subtitle on home, never as button text. Italicized in marketing, upright in product.

---

## 2. Color Palette

### Primary
| Token | Hex | Role |
|---|---|---|
| `--summit-cyan` | `#00E5FF` | Action, focus, primary accent |
| `--summit-sky` | `#0EA5E9` | Gradient stop, secondary action |
| `--obsidian` | `#070A12` | App background |
| `--obsidian-2` | `#0F1623` | Card / panel surface |
| `--obsidian-3` | `#1F2937` | Borders, dividers, track rings |

### Semantic
| Token | Hex | Use |
|---|---|---|
| `--ready` | `#10B981` | Idle / success / "nothing to do" |
| `--boost` | `#00E5FF` | Active state, in-progress |
| `--warn` | `#F59E0B` | Reserved (not used in v0.1) |
| `--danger` | `#EF4444` | Reserved (not used in v0.1) |

### Text
| Token | Hex | Use |
|---|---|---|
| `--ink-100` | `#FFFFFF` | Headlines, big numbers |
| `--ink-70` | `#9CA3AF` | Subdued labels |
| `--ink-50` | `#6B7280` | Hints, monospace metadata |
| `--ink-on-cyan` | `#070A12` | Text on cyan buttons |

**Rule:** Cyan is *currency*. Spend it only on (a) the BOOST button, (b) result numbers, (c) the apex mark. Everything else is grayscale-on-obsidian. The page should be 85% black, 10% white, 5% cyan.

---

## 3. Typography

Two font families, never more.

### `Inter` (Display & Body)
- **Hero Title** — 56 sp / 700 / letter-spacing `-0.02` (tight). Two lines, stacked. First line: white. Second line: cyan. *("Game" / "Performance")*
- **Subtitle** — 13 sp / 400 / color `--ink-70`
- **Stat values** — 16 sp / 700 / white
- **Freed MB hero number** — 64 sp / 700 / letter-spacing `-0.04` / cyan

### `JetBrains Mono` (Telemetry & Metadata)
- **Status dot text** — 12 sp / regular / `"● Optimizing…"`
- **Section labels** (`PROCESSES`, `MEM FREE`, `LOAD AVG`) — 9 sp / letter-spacing `0.15`
- **Result header** (`BOOST COMPLETE`) — 10 sp / letter-spacing `0.2`
- **Version chip** — 11 sp / `--ink-50`

**Rule:** If a number is *moving* (a stat, a result, a counter), it lives in Inter Bold. If a number is *fixed* (a label, a version), it lives in Mono.

---

## 4. Spacing & Layout

- Base unit: **6 dp**. All paddings/margins are multiples of 6.
- Page horizontal padding: **72 dp** on a 1080-wide canvas.
- Vertical breathing room: **192 dp** above the hero title, **144 dp** between the button and the result panel.
- The BOOST button is **840×840 dp** total footprint (ring + button). The button itself is **660×660 dp** (10× base unit).

### The Three Zones
1. **Top bar** — logo, version chip. 30 dp height. Always pinned.
2. **Center stage** — title, subtitle, status, BOOST button. Centered.
3. **Result panel** — appears below the button after a boost. Hidden in idle.

---

## 5. The BOOST Button — Primary Action

The single most important element in the app.

- **Shape:** Perfect circle, 660 dp diameter.
- **Fill:** Linear gradient `TL→BR`, `--summit-cyan` (`#00E5FF`) → `--summit-sky` (`#0EA5E9`).
- **Shadow:** Inner top highlight (white at 12% opacity, 2 dp from edge). Outer ambient shadow cyan at 30% opacity, blur 24 dp, y-offset 8 dp.
- **Label:** `BOOST` in Inter Bold, 26 sp, letter-spacing `0.15`, color `--ink-on-cyan`.
- **Ripple:** Cyan-white at 25% alpha, 6 dp radius.
- **Press:** Scale to `0.95`, alpha to `0.6`, instant. Release springs back.
- **Idle pulse:** Continuous breath — `scale 1.0 → 1.04 → 1.0` over 2000 ms, infinite, ease-in-out. The button is *alive*.
- **Result state:** Label flips to `AGAIN`. Same button, same press behavior.

---

## 6. The Rings

Two concentric systems frame the button. They are not decoration — they are *feedback*.

### Boost Ring (sweep)
- Surrounds the button at 8 dp outside its edge.
- **Track:** `--obsidian-3`, 8 dp stroke, 96% alpha.
- **Arc:** `--summit-cyan`, 8 dp stroke, rounded caps, 240/255 alpha.
- **Behavior:** During `BOOSTING`, rotates 0°→360° over 1200 ms, infinite, linear. Sweeps the full circle like a radar ping.
- **Hidden** in idle and result states.

### Glow Ring (ambient)
- A field of three concentric circles centered on the button, behind everything.
- Stroke 3 dp, color `--summit-cyan`, alpha modulated by intensity.
- **Intensity states:**
  - Idle: `0.4` (subtle breathing)
  - Boosting: `1.0` (full glow, alive)
  - Result: `0.3` (settles)
- **Animation:** Each ring oscillates with a 12 dp radius offset, phase-shifted by `i * π/2`. 3000 ms loop, infinite, ease-in-out.
- **Purpose:** Subliminally tells the user the system is *watching*, even when idle.

---

## 7. Icons

The app uses **no raster icons** in the body. Every glyph is a vector primitive or monospace character. This is intentional — it keeps the app at 1.2 MB and prevents icon-style drift.

| Glyph | Where | How |
|---|---|---|
| `●` | Status indicator | Cyan or green, 12 sp mono |
| `APEX` | Logo wordmark | Inter Bold, 14 sp, white |
| `v0.1.0` | Version chip | JetBrains Mono, 11 sp, `--ink-50` |
| `BOOST` | Primary button | Inter Bold, 26 sp, on-cyan |
| `AGAIN` | Post-boost CTA | Same |
| Section labels | Result panel | Mono 9 sp, letter-spacing 0.15 |

**Launcher icon** is the only raster asset: a 1024×1024 adaptive icon. The foreground is the Apex Mark (`#00E5FF`) on a radial dark gradient (`#0F1623` → `#070A12`). No text. No ring. Just the dot, large and centered.

---

## 8. Result Panel

A card that slides up from below the button after a successful boost.

- **Surface:** `--obsidian-2`, 20 dp corner radius, 3 dp border in `--obsidian-3`.
- **Padding:** 72 dp horizontal, 72 dp vertical.
- **Entry:** `alpha 0→1, translationY 60→0` over 500 ms, ease-in-out.
- **Layout (top to bottom):**
  1. `BOOST COMPLETE` — mono 10 sp, `--ink-50`, letter-spacing 0.2
  2. **Freed MB number** — 64 sp Inter Bold cyan, 24 dp top padding
  3. `MB reclaimed` — 13 sp, `--ink-70`, 12 dp top padding, 60 dp bottom
  4. Divider — 3 dp tall, `--obsidian-3`
  5. **Stats row** — three columns, 48 dp top padding
     - `PROCESSES` / killed count
     - `MEM FREE` / `before→after` MB
     - `LOAD AVG` / `before→after`

### Number animation
The freed MB number counts up from 0 to the result over 1000 ms, ease-in-out. Every digit lands clean — no overshoot, no bounce.

---

## 9. Motion Principles

Motion is *correction*, not *decoration*. Every animation has a job.

| Trigger | Animation | Duration | Easing |
|---|---|---|---|
| Idle (always) | Glow ring breathing | 3000 ms loop | ease-in-out |
| Idle (always) | Button breath | 2000 ms loop | ease-in-out |
| Tap BOOST | Button scale → 0.95, alpha → 0.6 | 0 ms | instant |
| Start boosting | Button scale → 0.95, ring sweep starts | 0 ms | instant |
| Boosting | Ring sweep 0°→360° | 1200 ms loop | linear |
| Boost complete | Panel slide-up + fade | 500 ms | ease-in-out |
| Boost complete | Count-up | 1000 ms | ease-in-out |
| Tap AGAIN | Panel fade-out, state reset | 300 ms | ease-in-out |

**Forbidden:** bounce, overshoot, rotation on tap, parallax, springs > 1.0.

---

## 10. Accessibility

- All text on `--obsidian` meets WCAG AA against its background.
- Cyan (`#00E5FF`) on obsidian (`#070A12`) = **14.2:1 contrast ratio**. AAA.
- White on obsidian = **19.8:1**. AAA.
- `--ink-50` (`#6B7280`) is reserved for *non-essential* hints and is never the sole carrier of meaning.
- All interactive elements have a minimum 48 dp touch target.
- The button press state is conveyed by both scale *and* alpha — never scale alone (color-blind users).

---

## 11. Don'ts

- **No green-blue gradients.** Cyan stops at `#0EA5E9`. Past that it gets cute.
- **No drop shadows on text.** The dark canvas is the shadow.
- **No icons inside the BOOST button.** The word is the icon.
- **No more than 3 weights of Inter in any one screen.** Pick bold, regular, done.
- **No white text smaller than 11 sp.** Use mono, use cyan, or don't.
- **No animations longer than 1 second on user-triggered actions.** Loops are fine; waits aren't.

---

## 12. Voice & Tone

- **Status messages** are mono, always start with a `●`, always present-tense.
  - `● Ready to boost`
  - `● Optimizing…`
  - `● Available: 1842 MB`
  - `● Nothing to clean`
- **Section labels** are uppercase, letter-spaced, mono. They are *tags*, not sentences.
- **The app never apologizes.** If a boost finds nothing, it says `Already optimized` and shows `0`. It does not say "Sorry, we couldn't find anything to clean."

---

*Summit is the aesthetic of someone who knows the difference between loud and clear. ApexCore should always be the latter.*
