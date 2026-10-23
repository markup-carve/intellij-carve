#!/usr/bin/env bash
# Rebuild both bundles and verify that they carry the same carve-js commit.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
if [ "$#" -gt 1 ]; then
  echo "Usage: tools/build-engine-bundles.sh [path-to-carve-js]" >&2
  exit 64
fi
carve_js="${1:-$here/../carve-js}"
"$here/tools/build-carve-bundle.sh" "$carve_js"
"$here/tools/build-lsp-bundle.sh"
preview_rev="$(sed -n 's|^// Bundled from markup-carve/carve-js commit \([0-9a-f]\{40\}\)$|\1|p' "$here/src/main/resources/js/carve.iife.js" | head -n 1)"
server_rev="$(sed -n 's|^// carve-js dependency commit \([0-9a-f]\{40\}\)$|\1|p' "$here/src/main/resources/lsp/server.js" | head -n 1)"
if [ -z "$preview_rev" ] || [ "$preview_rev" != "$server_rev" ]; then
  echo "Engine commits differ. Set the carve-js override with tools/bump-lsp-bundle.sh <carve-lsp-version> <carve-js-version>." >&2
  exit 1
fi
echo "Both bundles carry carve-js $preview_rev"
