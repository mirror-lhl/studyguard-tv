#!/usr/bin/env bash
# 安装 + 授权 + 启动 学习守卫
set -euo pipefail
# adb 需要可写的 $HOME/.android（沙箱内），统一指向工程目录
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
$ADB -s "$D" shell echo ok >/dev/null

echo "» 安装 APK"
$ADB -s "$D" install -r -t out/StudyGuard.apk

echo "» 授予悬浮窗权限（appops）"
$ADB -s "$D" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>&1 || true
$ADB -s "$D" shell cmd appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>&1 || true

echo "» 关闭电池优化限制"
$ADB -s "$D" shell dumpsys deviceidle whitelist +"$PKG" 2>&1 || true
$ADB -s "$D" shell cmd deviceidle whitelist +"$PKG" 2>&1 || true

echo "» 允许后台弹出界面（部分 MIUI 版本需要）"
$ADB -s "$D" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow 2>&1 || true

echo "» 激活设备管理器（防卸载第一层）"
$ADB -s "$D" shell dpm set-active-admin "$PKG/.GuardAdmin" 2>&1 | tail -1 || true

echo "» 开启无障碍拦截（卸载/停用要密码的关键）"
$ADB -s "$D" shell settings put secure enabled_accessibility_services "$PKG/.GuardAccessibilityService" 2>&1 || true
$ADB -s "$D" shell settings put secure accessibility_enabled 1 2>&1 || true

echo "» 启动 App"
$ADB -s "$D" shell am start -n "$PKG/.MainActivity" 2>&1 || true

sleep 2
echo "» 当前前台："
$ADB -s "$D" shell dumpsys activity activities 2>&1 | tr -d '\r' | grep -m1 mResumedActivity || true
echo "✓ 完成。电视上应该能看到“学习守卫”设置页。"
