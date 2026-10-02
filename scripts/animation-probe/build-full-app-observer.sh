#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/../../build/full-app-observer}"
JDK_DIR="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
mkdir -p "$OUTPUT_DIR/classes"
javac -d "$OUTPUT_DIR/classes" "$SCRIPT_DIR/FullAppObserver.java"
jar cf "$OUTPUT_DIR/observer.jar" -C "$OUTPUT_DIR/classes" .
g++ -std=c++17 -shared -fPIC -I "$JDK_DIR/include" -I "$JDK_DIR/include/linux" \
    "$SCRIPT_DIR/full_app_observer.cpp" -o "$OUTPUT_DIR/libobserver.so"
printf 'Observer built: %s\n' "$OUTPUT_DIR"
