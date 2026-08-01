#!/usr/bin/env bash
# adb-freeze-matrix.sh — T10a device validation (Decision E: Shizuku/Root only)
# Usage: ./scripts/adb-freeze-matrix.sh
# Prerequisites: debug APK built, device connected

set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.ivarna.apexcore"

if [ ! -f "$APK" ]; then
    echo "APK not found. Build first:"
    echo "  ./gradlew :app:assembleDebug"
    exit 1
fi

echo "=== Installing APK ==="
adb install -r "$APK"

echo ""
echo "=== Clearing logcat ==="
adb logcat -c

echo ""
echo "=== No elevation (freeze blocked) ==="
echo "1. Open app with Shizuku NOT granted and no root; dropdown must show Shizuku/Root only"
echo "2. Tap BOOST — expect NO freezeAll run; setup CTA / elevation banner shown"
echo "3. Press Enter when done"
read -r
echo "--- No-elevation logs (expect NO 'freezeAll via' line) ---"
adb logcat -d -s "ApexCore.Freeze:*" | tail -40

echo ""
echo "=== Shizuku ==="
echo "1. Grant Shizuku to ApexCore, pick Shizuku in dropdown"
echo "2. Tap BOOST"
echo "3. Press Enter when done"
read -r
echo "--- Shizuku logs ---"
adb logcat -d -s "ApexCore.Freeze:*" | tail -40

echo ""
echo "=== Root (if available) ==="
echo "1. Pick Root in dropdown"
echo "2. Tap BOOST"
echo "3. Press Enter when done"
read -r
echo "--- Root logs ---"
adb logcat -d -s "ApexCore.Freeze:*" | tail -40

echo ""
echo "=== RAM Free accuracy (no elevation OK) ==="
echo "--- meminfo BEFORE ---"
adb shell 'grep -E "MemAvailable|MemTotal|SwapFree|SwapTotal" /proc/meminfo'
echo ""
echo "1. Go to RAM FREE screen, tap FREE RAM"
echo "2. Press Enter when done"
read -r
echo "--- meminfo AFTER ---"
adb shell 'grep -E "MemAvailable|SwapFree" /proc/meminfo'

echo ""
echo "=== Pass criteria ==="
cat <<'EOF'
| Mode          | Expect                                                   |
|---------------|----------------------------------------------------------|
| No elevation  | freeze gated; setup CTA; NO 'freezeAll via' log line     |
| Shizuku       | freezeAll via Shizuku; killed>0 when targets exist       |
| Root          | freezeAll via Root; no pending→Success inflation         |
| Dropdown      | Shizuku/Root only; re-detect; no crash                   |
| Home BOOST    | MemAvailable Δ only; 0 → Already optimized               |
| RAM Free      | UI Δ matches true before/after MemAvailable (~±5%)       |
EOF
