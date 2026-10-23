#!/bin/zsh
# 用法: ./build.sh v1-paper [assembleDebug|assembleRelease]
set -e
V=${1:?version dir}
T=${2:-assembleDebug}
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
"$V/app/gradlew" -p "$V/app" "$T" --console=plain "${@:3}" -Pkotlin.compiler.execution.strategy=in-process 2>&1 | grep -E "^e:|error:|BUILD |What went wrong" -A3 | head -40
