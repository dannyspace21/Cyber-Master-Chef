#!/usr/bin/env bash
# Rebuilds the CyberChef engine that the app runs (app/src/main/assets/cyberchef).
#
# You only need this to update CyberChef to a newer version. The built files are
# already committed, so building the Android app does not need Node or npm.
#
# Usage:  engine/build.sh [cyberchef-version]     (needs Node 22+ and npm)
set -euo pipefail

VERSION="${1:-11.6.0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(dirname "$HERE")"
OUT="$REPO/app/src/main/assets/cyberchef"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Downloading CyberChef $VERSION…"
cd "$WORK"
npm pack "cyberchef@$VERSION" --silent >/dev/null
tar xzf "cyberchef-$VERSION.tgz"
cd package

echo "Installing build tools (this takes a minute)…"
npm install --ignore-scripts --no-audit --no-fund --loglevel=error
npx grunt exec:fixCryptoApiImports exec:generateConfig >/dev/null

cp "$HERE/engine.webpack.js" "$HERE/run-webpack.js" .
cp "$HERE/worker-entry.js" cmc-worker-entry.js

echo "Building…"
rm -rf "$OUT"
ENGINE_OUT="$OUT" NODE_OPTIONS=--max_old_space_size=6000 node run-webpack.js

# Operation list and categories for the app's screen, plus the licence.
node -e '
const fs=require("fs"),out=process.argv[1];
fs.writeFileSync(out+"/operations.json",JSON.stringify({
  version:require("./package.json").version,
  categories:require("./src/core/config/Categories.json"),
  operations:require("./src/core/config/OperationConfig.json")}));
' "$OUT"
cp LICENSE "$OUT/LICENSE.txt"
# jsQR powers the live QR scanner on the main screen.
cp node_modules/jsqr/dist/jsQR.js "$OUT/jsQR.js"
# Some results (Entropy, Frequency distribution…) draw charts with CyberChef's
# CanvasComponents; they run inside the output view, so ship it as a plain script.
{ sed 's/^export function/function/' src/core/lib/CanvasComponents.mjs
  echo 'window.CanvasComponents = { drawLine, drawBarChart, drawScaleBar };'; } > "$OUT/CanvasComponents.js"
echo "Done: $OUT ($(du -sh "$OUT" | cut -f1))"
