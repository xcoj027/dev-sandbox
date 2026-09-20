#!/usr/bin/env bash
# Copyright (c) 2026 TNQ MEDIA
# Author: TNQ MEDIA
# Git: github.com/xcoj027

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sandbox_root="$(cd "$script_dir/.." && pwd)"

target_project="${1:-}"

echo "=========================================="
echo "Launching Dev Sandbox GUI..."
echo "=========================================="

cd "$sandbox_root"

if [[ -n "$target_project" ]]; then
	./gradlew run --args="$target_project" --no-daemon --no-watch-fs
else
	./gradlew run --no-daemon --no-watch-fs
fi
