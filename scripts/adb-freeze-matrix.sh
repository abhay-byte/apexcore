#!/usr/bin/env bash
# adb-freeze-matrix.sh — T10a device validation
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
echo "=== Standard (standard) ==="
echo "1. Open app, ensure Shizuku is NOT granted, pick Standard in dropdown"
echo "2. Tap BOOST"
echo "3. Press Enter when done"
read -r
echo "--- Standard logs ---"
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
echo "=== RAM Free accuracy ==="
echo "1. Go to RAM FREE screen, tap FREE RAM"
echo "2. Press Enter when done"
read -r
echo "--- meminfo before ---"
adb shell 'grep -E "MemAvailable|MemTotal|SwapFree|SwapTotal" /proc/meminfo'

echo ""
echo "=== meminfo after ==="
adb shell 'grep -E "MemAvailable|SwapFree" /proc/meminfo'

echo ""
echo "=== Pass criteria ==="
cat <<'EOF'
| Mode          | Expect                                                   |
|---------------|----------------------------------------------------------|
| Standard      | backend=standard; limited-mode UI; no fake multi-GB  |
| Shizuku       | freezeAll via Shizuku; killed>0 when targets exist        |
| Root          | freezeAll via Root; same                                  |
| Dropdown      | re-detect; no crash                                       |
| Home BOOST    | MemAvailable Δ only; 0 → Already optimized                |
| RAM Free      | UI Δ matches /proc/meminfo within ~±5% noise             |
EOF
