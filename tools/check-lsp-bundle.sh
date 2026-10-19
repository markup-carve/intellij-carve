#!/usr/bin/env bash
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$here/tools/build-lsp-bundle.sh" --out "$tmp/server.js"
if ! cmp -s "$tmp/server.js" "$here/src/main/resources/lsp/server.js" || \
   ! diff -q "$tmp/VERSION" "$here/src/main/resources/lsp/VERSION" >/dev/null; then
  echo "Pinned tools/lsp-bundle build differs from committed LSP bundle. Run tools/build-lsp-bundle.sh" >&2
  exit 1
fi
node "$here/tools/lsp-smoke.mjs" "$here/src/main/resources/lsp/server.js"
