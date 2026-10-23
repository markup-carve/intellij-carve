#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ] || [[ ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || \
   { [ "$#" -eq 2 ] && [[ ! "$2" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; }; then
  echo "Usage: tools/bump-lsp-bundle.sh <carve-lsp-version> [<carve-js-version>]" >&2
  exit 64
fi
here="$(cd "$(dirname "$0")/.." && pwd)"
node - "$here/tools/lsp-bundle/package.json" "$@" <<'NODE'
const fs = require('node:fs');
const [file, lsp, carve] = process.argv.slice(2);
const pkg = JSON.parse(fs.readFileSync(file, 'utf8'));
pkg.dependencies['@markup-carve/carve-lsp'] = lsp;
if (carve) pkg.overrides['@markup-carve/carve'] = carve;
fs.writeFileSync(file, JSON.stringify(pkg, null, 2) + '\n');
NODE
npm install --prefix "$here/tools/lsp-bundle" --no-audit --no-fund --silent
"$here/tools/build-lsp-bundle.sh"
node "$here/tools/lsp-smoke.mjs" "$here/src/main/resources/lsp/server.js"
