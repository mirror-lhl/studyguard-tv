#!/usr/bin/env bash
# 重启电视，抓开机过程中的前台 Activity + 录屏，用来定位开机广告的宿主。
set -uo pipefail
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
OUT="out"; mkdir -p "$OUT"; LOG="$OUT/boot-trace.log"; : > "$LOG"

say() { echo "$@" | tee -a "$LOG"; }

$ADB -s "$D" logcat -c >/dev/null 2>&1
$ADB -s "$D" shell rm -f /sdcard/boot.mp4 >/dev/null 2>&1

say "== reboot $(date '+%H:%M:%S') =="
$ADB -s "$D" reboot

sleep 8
up=0
for i in $(seq 1 150); do
  $ADB connect "$D" >/dev/null 2>&1
  if $ADB -s "$D" shell echo ok 2>/dev/null | grep -q ok; then
    say "== adb back at +${i}s  $(date '+%H:%M:%S') =="
    up=1; break
  fi
  sleep 1
done
[ "$up" = 1 ] || { say "!! adb 没回来"; exit 1; }

# 边录边轮询前台
( $ADB -s "$D" shell screenrecord --time-limit 30 --bit-rate 4000000 /sdcard/boot.mp4 ) >/dev/null 2>&1 &

for i in $(seq 1 45); do
  ts="+${i}s"
  act=$($ADB -s "$D" shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -m1 'mResumedActivity' | sed 's/.*u0 //;s/ t[0-9].*//')
  foc=$($ADB -s "$D" shell dumpsys window 2>/dev/null | tr -d '\r' | grep -m1 'mCurrentFocus' | sed 's/.*mCurrentFocus=//')
  say "$ts  act=${act:-?}  focus=${foc:-?}"
  sleep 1
done

wait
$ADB -s "$D" pull /sdcard/boot.mp4 "$OUT/boot.mp4" >>"$LOG" 2>&1
$ADB -s "$D" logcat -d > "$OUT/boot-logcat.txt" 2>/dev/null
say "== done =="
