#!/usr/bin/env bash
# 家长要彻底卸载学习守卫时用这个（会把设备管理器、无障碍一起清掉）
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
$ADB -s "$D" shell echo ok >/dev/null 2>&1 || { echo "✗ 连不上电视"; exit 1; }

echo "» 停止服务"
$ADB -s "$D" shell am force-stop "$PKG" 2>&1 || true

echo "» 取消设备管理器"
$ADB -s "$D" shell dpm remove-active-admin "$PKG/.GuardAdmin" 2>&1 | tail -1 || true

echo "» 关闭无障碍"
$ADB -s "$D" shell settings delete secure enabled_accessibility_services 2>&1 || true
$ADB -s "$D" shell settings put secure accessibility_enabled 0 2>&1 || true

echo "» 卸载 App"
$ADB -s "$D" uninstall "$PKG" 2>&1 | tail -1

echo "✓ 已清理。若还想恢复开机广告：./scripts/ads.sh on"
