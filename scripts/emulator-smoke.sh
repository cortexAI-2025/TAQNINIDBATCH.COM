#!/usr/bin/env bash
# Vérification sur émulateur : installation de l'APK, ouverture, écran de
# connexion affiché, aucune exception fatale, puis ouverture à froid après
# arrêt forcé. La connexion elle-même demande un projet Firebase de recette.
set -euo pipefail
APK_DIR="${1:-apk}"
OUT=emulator-out
PKG=com.taqnid.batch.debug
mkdir -p "$OUT"
APK=$(ls "$APK_DIR"/*.apk | head -1)
echo "APK : $APK"

adb wait-for-device
adb logcat -c
adb install -r "$APK"
adb shell pm list packages | grep -q "$PKG" && echo "✓ installée"

check_launch() {
  local label="$1"
  adb shell am start -W -n "$PKG/com.taqnid.batch.ui.auth.LoginActivity" | tee "$OUT/start-$label.txt"
  sleep 8
  adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png "$OUT/ecran-$label.png" > /dev/null
  adb shell pidof "$PKG" > /dev/null && echo "✓ processus vivant ($label)"
  adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | tee "$OUT/activite-$label.txt"
  grep -q "LoginActivity" "$OUT/activite-$label.txt" && echo "✓ écran de connexion affiché ($label)"
}

check_launch premiere-ouverture
adb shell am force-stop "$PKG"
check_launch reouverture

adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG" "$OUT/logcat.txt"; then
  echo "✗ exception fatale dans le journal"
  exit 1
fi
echo "✓ aucune exception fatale"
