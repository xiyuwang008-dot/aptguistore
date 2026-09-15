#!/usr/bin/env bash
# 构建脚本：编译并打包为可执行 JAR（需要 JDK 25）
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 查找 javac
if command -v javac &>/dev/null; then
    JAVAC=javac
    JAR=jar
else
    echo "错误：未找到 javac，请安装 JDK 25+。"
    echo "  Ubuntu/Debian: sudo apt install openjdk-25-jdk"
    exit 1
fi

echo "==> 编译源码..."
rm -rf out
mkdir -p out
"$JAVAC" --release 25 -encoding UTF-8 -d out src/com/aptgui/*.java

echo "==> 打包 JAR..."
"$JAR" cfm debguistore.jar MANIFEST.MF -C out .

echo "==> 构建完成: debguistore.jar"
ls -lh debguistore.jar
