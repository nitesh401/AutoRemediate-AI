#!/usr/bin/env bash
# Copies demo/sample-app into a fresh git repository and prints its path.
set -euo pipefail
SRC="$(cd "$(dirname "$0")/../demo/sample-app" && pwd)"
DEST="${1:-/tmp/autoremediate-demo-repo}"
rm -rf "$DEST"
cp -r "$SRC" "$DEST"
cd "$DEST"
git init -q -b main
git add -A
git -c user.name=demo -c user.email=demo@example.com commit -q -m "Initial sample app (3 known Sonar findings, 1 Snyk finding)"
echo "$DEST"
