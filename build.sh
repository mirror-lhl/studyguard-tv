#!/usr/bin/env bash
# 不需要 Gradle / Android Studio，只用 SDK 自带的 aapt2 + javac + d8 + apksigner 打包。
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

SDK="${ANDROID_SDK:-$HOME/Library/Android/sdk}"
if [ ! -d "$SDK" ]; then
  echo "✗ 找不到 Android SDK: $SDK（可以用 ANDROID_SDK=... 指定）" >&2
  exit 1
fi

pick() { ls -d "$@" 2>/dev/null | sort -V | tail -1; }
BT="$(pick "$SDK"/build-tools/*)"
PLATFORM="$(pick "$SDK"/platforms/*/android.jar)"
[ -x "$BT/aapt2" ] || { echo "✗ 缺少 aapt2（build-tools）" >&2; exit 1; }
[ -f "$PLATFORM" ] || { echo "✗ 缺少 android.jar（platforms）" >&2; exit 1; }

OUT="$ROOT/out"
BUILD="$ROOT/build"
APP="$ROOT/app"
NAME="StudyGuard"
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/base.apk" "$BUILD/unsigned.apk" "$BUILD/aligned.apk" "$BUILD/res.zip"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$OUT"

echo "» SDK      : $SDK"
echo "» build-tools: $BT"
echo "» android.jar: $PLATFORM"

echo "» 1/6 编译 Java"
# shellcheck disable=SC2046
javac -encoding UTF-8 -source 8 -target 8 \
      -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
      -d "$BUILD/classes" \
      $(find "$APP/src" -name '*.java')

echo "» 2/6 转 dex"
# shellcheck disable=SC2046
"$BT/d8" --release --min-api 21 --lib "$PLATFORM" \
      --output "$BUILD/dex" \
      $(find "$BUILD/classes" -name '*.class')

echo "» 3/6 编译资源"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "» 3/6 打包资源与清单"
"$BT/aapt2" link \
      -o "$BUILD/base.apk" \
      -I "$PLATFORM" \
      --manifest "$APP/AndroidManifest.xml" \
      -A "$APP/assets" \
      --min-sdk-version 21 \
      --target-sdk-version 28 \
      --version-code 1 \
      --version-name 1.0 \
      "$BUILD/res.zip"

echo "» 4/6 塞入 classes.dex"
python3 - "$BUILD/base.apk" "$BUILD/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("   classes.dex 已写入")
PY

echo "» 5/6 对齐 + 签名"
"$BT/zipalign" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

KS="$BUILD/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
          -storepass android -keypass android \
          -keyalg RSA -keysize 2048 -validity 10000 \
          -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$BT/apksigner" sign \
      --ks "$KS" --ks-pass pass:android --key-pass pass:android \
      --ks-key-alias androiddebugkey \
      --v1-signing-enabled true --v2-signing-enabled true \
      --out "$OUT/$NAME.apk" "$BUILD/aligned.apk"

"$BT/apksigner" verify --print-certs "$OUT/$NAME.apk" | head -3

echo
ls -lh "$OUT/$NAME.apk"
echo "✓ 打包完成: $OUT/$NAME.apk"
