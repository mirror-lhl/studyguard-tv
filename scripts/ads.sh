#!/usr/bin/env bash
# 开/关 小米电视开机广告（改的是桌面 com.mitv.tvhome 读取的系统设置项 boot_ad_switch）
#   ./scripts/ads.sh status   查看当前值
#   ./scripts/ads.sh off      关闭开机广告
#   ./scripts/ads.sh on       恢复（删掉该键，回到系统默认）
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

$ADB connect "$D" >/dev/null 2>&1 || true

status() {
  printf 'boot_ad_switch      = %s\n' "$($ADB -s "$D" shell settings get system boot_ad_switch | tr -d '\r')"
  printf 'boot_ad             = %s\n' "$($ADB -s "$D" shell settings get system boot_ad | tr -d '\r')"
  printf 'boot_ad_switch_show = %s\n' "$($ADB -s "$D" shell settings get system boot_ad_switch_show | tr -d '\r')"
}

case "${1:-status}" in
  status)
    echo "==== 电视 $D ===="
    status
    ;;
  off)
    orig="$($ADB -s "$D" shell settings get system boot_ad_switch | tr -d '\r')"
    mkdir -p out
    echo "$orig" > out/ad-backup.txt
    echo "原始值已备份到 out/ad-backup.txt（值：$orig）"
    $ADB -s "$D" shell settings put system boot_ad_switch 0
    echo "已关闭。当前："
    status
    echo
    echo "提示：下次电视【真正关机再开机】时生效，若仍是广告请跑 ./scripts/ads.sh status 反馈给我。"
    ;;
  on)
    orig="$(cat out/ad-backup.txt 2>/dev/null || echo null)"
    if [ "$orig" = "null" ] || [ -z "$orig" ]; then
      $ADB -s "$D" shell settings delete system boot_ad_switch
    else
      $ADB -s "$D" shell settings put system boot_ad_switch "$orig"
    fi
    echo "已恢复。当前："
    status
    ;;
  *)
    echo "用法: $0 {status|off|on}" >&2
    exit 1
    ;;
esac
