#!/usr/bin/env bash
# 运行脚本：启动 APT 图形管理器
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/apt-gui.jar"

if [ ! -f "$JAR" ]; then
    echo "未找到 apt-gui.jar，正在先构建..."
    bash "$SCRIPT_DIR/build.sh"
fi

# 查找 java
if command -v java &>/dev/null; then
    JAVA=java
else
    echo "错误：未找到 java 运行时，请安装 JRE 11+。"
    echo "  Ubuntu/Debian: sudo apt install default-jre"
    exit 1
fi

exec "$JAVA" -jar "$JAR" "$@"
