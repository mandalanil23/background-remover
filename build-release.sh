#!/usr/bin/env bash
#
# Builds the release AAB (for Google Play) and APK (for direct install), runs the unit
# tests first, and prints where the artifacts landed.
#
# Signing comes from keystore.properties in the project root, or from the environment:
#   KEYSTORE_FILE  KEYSTORE_PASSWORD  KEY_ALIAS  KEY_PASSWORD
#
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Unit tests"
./gradlew --no-daemon testDebugUnitTest

echo "==> Clean"
./gradlew --no-daemon clean

echo "==> Android App Bundle (Play Store)"
./gradlew --no-daemon bundleRelease

echo "==> APK (testing / sideload)"
./gradlew --no-daemon assembleRelease

AAB=$(find app/build/outputs/bundle/release -name '*.aab' | head -n 1 || true)
APK=$(find app/build/outputs/apk/release -name '*.apk' | head -n 1 || true)

echo
echo "──────────────────────────────────────────────"
[ -n "$AAB" ] && echo "AAB : $AAB  ($(du -h "$AAB" | cut -f1))"
[ -n "$APK" ] && echo "APK : $APK  ($(du -h "$APK" | cut -f1))"
echo "──────────────────────────────────────────────"

if [ ! -f keystore.properties ] && [ -z "${KEYSTORE_FILE:-}" ]; then
  echo
  echo "WARNING: no signing configuration was found, so these artifacts are UNSIGNED."
  echo "         Google Play will reject an unsigned bundle. See README section 2."
fi
