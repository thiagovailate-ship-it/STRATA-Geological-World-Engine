#!/usr/bin/env bash
# Type-check the Bedrock JavaScript against the official stable server API declarations.
set -euo pipefail
cd "$(dirname "$0")/.."

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
mkdir -p "$tmpdir/scripts"
cp bedrock/STRATA_BehaviorPack/scripts/*.js "$tmpdir/scripts/"
npm install --prefix "$tmpdir" --ignore-scripts --no-audit --no-fund \
  typescript@5.8.3 @minecraft/server@2.9.0
"$tmpdir/node_modules/.bin/tsc" \
  --allowJs --checkJs --noEmit \
  --module NodeNext --moduleResolution NodeNext \
  --target ES2022 --skipLibCheck --strict false \
  "$tmpdir/scripts/main.js" "$tmpdir/scripts/geology_model.js"
