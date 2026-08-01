# ApexCore Redesign Plan

## 1. Style Direction and Reasoning
**Direction:** Glassmorphism + Bento Grid (Premium Technical)
**Reasoning:** The existing "Summit/Titanium" design is functional but feels somewhat dated and heavy. We are transitioning to a modern Glassmorphism + Bento Grid aesthetic. This retains the technical "power user" feel while introducing a highly premium, modern, and fluid experience. The bento grid perfectly organizes the complex stats returned by the freeze operation, and the glassmorphic surfaces give a sense of depth and lightness. 

## 2. Design System

### Color Palette (Slate & Emerald)
- **Background:** `#0F172A` (Slate 950) - Deep, immersive base.
- **Surface/Card:** `#1E293B` (Slate 800) - Used with slight transparency for bento cards.
- **Accent (Boost):** `#22C55E` (Emerald 500) - Conveys positive performance, speed, and safety.
- **Text Primary:** `#F8FAFC` (Slate 50)
- **Text Secondary:** `#94A3B8` (Slate 400)
- **Border/Outline:** `#334155` (Slate 700)

### Typography Scale
- **Display (Huge numbers):** System Sans, 64sp, Bold, tight tracking (-0.04em)
- **Heading (App Title/Section):** System Sans, 28sp to 56sp, Bold
- **Body:** System Sans, 14sp to 16sp, Normal
- **Mono/Technical (Status, Labels, Tags):** System Monospace, 10sp to 12sp, Medium, wide tracking (0.1em)

### Spacing System (4pt grid)
- Base increments of 4dp: 4, 8, 12, 16, 24, 32, 48, 72.
- Bento cards use 12dp internal padding and 8dp gaps.

## 3. Key UX Changes
- **Motion:** Smooth spring animations for button scaling (`animateFloatAsState`). 
- **Transitions:** Result panel will use `AnimatedVisibility` with slide and fade in, staggered from the boost action.
- **Micro-interactions:** Tappable items (games, options) will use Ripple effects naturally provided by Compose.
- **Feedback:** The Boost sweep ring will be a smooth, infinitely rotating gradient arc.

## 4. Screen / Component Mapping

| Before (Views) | After (Compose) |
| --- | --- |
| `MainActivity.kt` (FrameLayout, LinearLayout) | `MainActivity.kt` using `setContent { ApexCoreTheme { HomeScreen() } }` |
| `TopBar` | `TopBar` composable |
| `GlowRingView` / `BoostRingView` | `GlowRing` and `BoostRing` composables with infinite transitions |
| `BoostButton` | `BoostButton` composable (Box + Text with spring scale animation) |
| `ResultPanel` (Bento Cards) | `ResultPanel` composable (LazyVerticalGrid or Column/Row bento layout) |
| `SetupDialog.kt` (Dialog + Views) | `SetupDialog` composable launched via state or `Dialog` window |
| `GameListDialog.kt` (Dialog + Views) | `GameListDialog` composable |
