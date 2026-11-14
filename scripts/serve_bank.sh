#!/usr/bin/env bash
# 在本机临时起一个题库服务，给电视做"在线题库"测试用。
# 用法：./scripts/serve_bank.sh [端口]
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-8000}"
IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 127.0.0.1)"

cat <<EOF
题库目录: $(pwd)/app/assets
电脑地址: http://$IP:$PORT/questions.txt
在电视设置页"在线题库地址"里填上面这个地址，点"同步题库"。
（Ctrl+C 结束）
EOF

python3 -m http.server "$PORT" --bind 0.0.0.0 --directory ./app/assets
