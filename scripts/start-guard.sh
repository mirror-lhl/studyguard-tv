#!/usr/bin/env bash
# 电视彻底断电/重启后，守卫不会自动起来（MIUI 会停掉第三方 App）。
# 在电脑上跑这个脚本就能把它拉起来，不用去电视上找图标。
set -euo pipefail
export HOME="${ADB_HOME:-$(cd "$(dirname "$0")/.." && pwd)/.adbhome}"
mkdir -p "$HOME"
cd "$(dirname "$0")/.."
ADB="${ADB:-/Users/lvhonglei/Library/Android/sdk/platform-tools/adb}"
D="${DEVICE:-$(cat "$(dirname "$0")/.tv-ip" 2>/dev/null || true)}"
if [ -z "$D" ]; then
  echo "请先指定电视地址：DEVICE=192.168.1.50 $0" >&2
  echo "（也可以把 IP 写进 scripts/.tv-ip，这个文件不会被提交）" >&2
  exit 1
fi
PKG="com.family.studyguard"

$ADB connect "$D" >/dev/null 2>&1 || true
$ADB -s "$D" shell echo ok >/dev/null 2>&1 || {
  echo "✗ 连不上电视 $D，确认电视开着、网络调试开着、IP 没变"
  exit 1
}

echo "» 拉起学习守卫"
$ADB -s "$D" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 1
$ADB -s "$D" shell am start -n "$PKG/.MainActivity" -e cmd start >/dev/null 2>&1
sleep 2

n=$($ADB -s "$D" shell dumpsys activity services "$PKG" 2>/dev/null | tr -d '\r' | grep -c ServiceRecord || true)
txt=$($ADB -s "$D" shell dumpsys notification --noredact 2>/dev/null | tr -d '\r' | grep -m1 'android.text=String (每' | sed 's/.*String (//;s/).*//' || true)

if [ "${n:-0}" -ge 1 ]; then
  echo "✓ 守卫已运行：${txt:-已启动}"
  $ADB -s "$D" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
else
  echo "✗ 启动失败，服务没有起来"
  exit 1
fi
