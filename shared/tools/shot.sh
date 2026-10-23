#!/bin/zsh
# 用法: shot.sh <pkg-with-debug> <out.png> [extra am args...]
PKG=$1; OUT=$2; shift 2
adb -s 3919a665 shell am force-stop "$PKG"
adb -s 3919a665 shell am start -W -n "$PKG/com.lemon.mingzhu.MainActivity" "$@" >/dev/null
sleep 2.5
adb -s 3919a665 exec-out screencap -p > "$OUT"
