#!/usr/bin/env bash
#
# Regenerates src/main/resources/js/carve.iife.js from @markup-carve/carve.
#
# Carve-js is not published to npm yet, so this bundles from a local checkout of
# the carve-js repo (a sibling of this repo by default). It produces a single
# IIFE that exposes a global `carve` with `carveToHtml(source)`, which the plugin
# runs on GraalJS for preview and HTML export.
#
# Usage:
#   tools/build-carve-bundle.sh [path-to-carve-js]
#
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
carve_js="${1:-$here/../carve-js}"

if [ ! -f "$carve_js/dist/index.js" ]; then
  echo "carve-js dist not found at $carve_js/dist/index.js" >&2
  echo "Pass the carve-js checkout path, and run 'npm ci && npm run build' there first." >&2
  exit 1
fi

out="$here/src/main/resources/js/carve.iife.js"
npx --yes esbuild "$carve_js/dist/index.js" \
  --bundle --format=iife --global-name=carve --platform=neutral \
  --legal-comments=none --outfile="$out"

echo "Wrote $out"
