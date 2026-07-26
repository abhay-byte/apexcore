# ApexCore — Design & Architecture Document

## 1. Design Philosophy: "Precision Instrument"

Standard "game booster" apps look like cheap RGB peripherals from 2012—aggressive neon greens, dragons, robotic fonts, and fake temperature gauges. ApexCore rejects this entirely. 

ApexCore is designed as a **Precision Instrument**. Think aerospace engineering meets high-end audio mixing software. It caters not just to gamers, but to power users, developers, and creatives who need to dedicate maximum system resources to a single heavy task (e.g., rendering a video, running a local LLM, or playing a demanding game). 

The UI is dark, utilitarian, and data-rich. It utilizes **Cryo-Blue** (signifying cooling/resource freeing) and **Cadmium-Orange** (signifying active processes/heat) against a deep matte titanium charcoal. 

---

## 2. Design Language & Tokens

### Typography
- **Display/Headers:** `Space Grotesk` (Geometric, modern, engineering feel)
- **Data/Metrics:** `JetBrains Mono` (Monospaced, highly legible for rapid-changing numbers)
- **Body:** `Inter` (Clean, neutral)

### Color Palette
- **Base Canvas:** `#0A0B0D` (Matte Carbon)
- **Surface Elevation:** `#14171C` (Titanium)
- **Primary Accent (Cryo):** `#00E5FF` (Neon Cyan - represents freed resources)
- **Warning/Active (Heat):** `#FF6B00` (Muted Orange - represents background bloat)
- **Text Primary:** `#EAEAEA`
- **Text Secondary:** `#6B7280`

### Iconography
No standard outline icons. We use **Isometric Glyphs** and topographic line patterns.

---

## 3. App Icon Design: "The Monolith"

The icon is a 3D isometric representation of focus and core power.
- **Shape:** A perfect, dark titanium cube (`#14171C`), viewed slightly from the top-right.
- **Carving:** A precise, laser-cut circular hole is bored through the center of the cube.
- **Core:** Inside the hole, a glowing `Cryo-Blue` (`#00E5FF`) vertical light beam shoots upward, fading out at the top.
- **Background:** A subtle, dark topographic grid pattern etched into the sides of the cube.
- **Meaning:** The cube represents the rigid Android system; the glowing core represents ApexCore cutting through the bloat to channel power to one specific point.

---

## 4. UI/UX Flow: The 3-Page Architecture

### Page 1: The Core (Optimize Dashboard)
**Purpose:** A system monitor that visualizes current memory bloat and acts as the master switch to free resources.

- **Layout:** 
  - Top: "SYSTEM MASS" in `Space Grotesk`. Below it, a monospaced readout of current used RAM vs. Total RAM.
  - Center: A massive, custom-drawn **Circular Sonogram**. Instead of a boring pie chart, this is a ring made of 64 individual vertical bars. 
    - Bars glowing `#FF6B00` (Heat) = Active background apps eating RAM.
    - Bars glowing `#00E5FF` (Cryo) = Already freed/available RAM.
  - Bottom: The "PURGE" button. A wide, pill-shaped button with a brushed metal texture. 

- **Interaction (The Purge):**
  - When the user taps "PURGE", the app executes the freeze backend. 
  - The orange bars on the Sonogram physically "shatter" into tiny particles, fall down the screen, and fade out. 
  - The freed amount of RAM (e.g., `+1.4 GB`) shoots up the screen in massive `JetBrains Mono` typography, freezing in the center for 1.5 seconds before settling back into the UI.

### Page 2: The Launch Matrix (App Selection)
**Purpose:** To select the heavy application (Game or Productivity app) and prepare the environment for launch. 

- **Layout:**
  - Top: Minimalist search bar. Toggle between "GAMES" and "ALL APPS" (catering to non-gamers).
  - Center: A 3D "Carousel" or "Treadmill" of app cards. Only 3 are visible at once (Previous, Active, Next).
  - Active Card: Shows the app icon (auto-themed background color extraction), App Name, and a "Resource Demand" meter (Low, Medium, High) based on historical usage.
  - Bottom: The "ALLOCATE & LAUNCH" trigger. 

- **Interaction (The Allocation):**
  - As the user swipes through the carousel, the device gives a subtle haptic tick on every card change.
  - When "ALLOCATE & LAUNCH" is tapped, the screen dims. A topographic grid sweeps across the screen. 
  - The chosen app's icon scales up, merges into the grid, and the target app launches. ApexCore pushes an ADB/Tasker broadcast `FREEZE_ALL` milliseconds before the target app hits the foreground.

### Page 3: The Phantom HUD (Overlay)
**Purpose:** A non-intrusive, real-time performance overlay that sits on top of the target app.

- **Layout:**
  - No bulky, square "game booster" widgets. 
  - ApexCore uses a **Magnetic Edge Rail**. It appears as a 2px thick vertical line on the left edge of the screen.
  - When tapped, it expands into a slim, vertical data column:
    - **FPS:** Large mono number (updates every 500ms).
    - **RAM:** Mini sparkline showing the memory curve over the last 60 seconds.
    - **CPU:** A tiny 8-segment equalizer representing core usage.
  - **The "Unfreeze" Node:** At the bottom of the rail is a small snowflake icon. Tapping it unfreezes background apps (useful if the user needs to quickly reply to a message and switch apps).

- **Interaction:**
  - Draggable vertically along the left edge to avoid covering UI elements in different games/apps.
  - Auto-minimizes back to the 2px line after 5 seconds of inactivity.
  - The line subtly pulses `#00E5FF` if FPS is stable, and shifts to `#FF6B00` if thermal throttling or massive frame drops are detected.

---

## 5. Detailed Animation Instructions

All animations must follow the **"Physical Mass"** principle. Nothing snaps. Everything has weight, friction, and restitution.

### Animation 1: The Purge Sequence (Page 1)
**Trigger:** Tapping the PURGE button.
**Duration:** 1200ms total
**Easing:** `EaseOutExpo` for expansion, `EaseInQuart` for falling particles.
- **0ms - 150ms:** The PURGE button depresses (scale Y to 0.95). The Circular Sonogram ring contracts inward by 10%.
- **150ms - 400ms:** The orange bars (representing bloat) rapidly extend upwards, as if pressurized. 
- **400ms - 700ms:** A shockwave ring (white, 50% opacity) expands from the center of the sonogram outward. 
- **400ms - 1000ms:** The orange bars "shatter" into 4-5 smaller rectangles. These pieces are governed by a physics simulation (gravity = 9.8, bounce = 0.2). They fall down the screen and fade out.
- **400ms - 1200ms:** Simultaneously, the `#00E5FF` (Cryo) bars expand from the bottom of the ring to replace the shattered orange bars, filling the circle.
- **600ms - 1200ms:** The Freed RAM number (e.g., `+1.4 GB`) scales from 0.0 to 1.0 at the center of the screen, overshoots slightly (1.05), and settles at 1.0.

### Animation 2: Launch Matrix Carousel (Page 2)
**Trigger:** Swiping left/right on the app carousel.
**Duration:** 400ms
**Easing:** Custom Spring (`dampingRatio = 0.7f`, `stiffness = 400f`)
- **Active Card:** Scales to 1.0f, opacity 1.0f, positioned at Z-axis 0.
- **Adjacent Cards:** Scale to 0.8f, opacity 0.4f, positioned at Z-axis -50f (creating a depth of field blur effect using RenderEffect if API > 31).
- **Transition:** As the user swipes, the cards don't just slide horizontally; they move along a subtle parabolic arc (dipping down slightly in the middle) to emphasize the 3D space.
- **Haptics:** `HapticFeedbackConstants.CLOCK_TICK` triggered on every 50% swipe progress.

### Animation 3: The Phantom HUD Expansion (Page 3)
**Trigger:** Tapping the 2px edge rail.
**Duration:** 350ms
**Easing:** `FastOutSlowInInterpolator`
- **0ms - 100ms:** The 2px line thickens to 16dp width instantly.
- **0ms - 350ms:** The background of the HUD (blurred glass, `RenderEffect BlurEffect(16f, 16f)`) slides in from the left edge. 
- **100ms - 200ms:** The FPS number, RAM sparkline, and CPU equalizer fade in (opacity 0 to 1) with a staggered delay of 50ms between each element.
- **Auto-minimize (5000ms idle):** Reverses the animation. The UI elements fade out (100ms), the width shrinks to 2px (150ms).
- **Thermal Warning State:** If CPU temp > 45°C, the 2px rail pulses continuously. It oscillates between `#FF6B00` and `#66000000` over an 800ms loop using `Transition` with `REVERSE` repeat mode.

---

## 6. Implementation Notes for Engineers

- **Rendering:** Page 1's Sonogram and Page 3's HUD must be drawn using Android's `Canvas` API for 60fps rendering. Do not use standard Android Views for the graphing elements.
- **Blur:** Use `RenderEffect.createBlurEffect` for the overlay HUD. If running below API 31, fall back to a semi-transparent solid color (`#CC0A0B0D`) to maintain performance.
- **Physics:** For the "shatter" animation on Page 1, use a simple custom particle system. Do not pull in a heavy physics engine like Box2D; a basic implementation of gravity and velocity in a custom `Drawable` is sufficient and keeps the APK under 1.2MB.
- **Haptics:** Limit haptic feedback strictly to Page 2 (Carousel) and the moment "Purge" completes. Overusing haptics drains battery and feels cheap.
