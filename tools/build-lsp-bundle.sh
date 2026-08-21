#!/usr/bin/env bash
#
# Regenerates src/main/resources/lsp/server.js from markup-carve/carve-lsp.
#
# carve-lsp is a Node language server (stdio transport). It is not published to
# npm, so this bundles from a local checkout (a sibling of this repo by
# default). esbuild produces a single self-contained CommonJS file with every
# runtime dependency (carve-js, vscode-languageserver, ...) inlined, so the
# plugin only needs a `node` binary on the host - no node_modules to ship.
#
# The plugin launches it as `node <bundledServerDir>/server.js --stdio` through
# lsp4ij.
#
# Usage:
#   tools/build-lsp-bundle.sh [path-to-carve-lsp] [--carve-js <git-ref>]
#
# --carve-js links the server against an explicit carve-js revision instead of
# whatever carve-lsp itself pins. The plugin ships TWO engine bundles and they
# have to be the same engine: the preview pane runs carve.iife.js on GraalJS,
# this server runs its own inlined copy, and a user who sees them disagree about
# one document has no way to tell which is right. carve-lsp pins its engine
# exactly, so without this flag the two bundles inherit whatever two revisions
# their two upstreams happened to be at - which is how they came to ship 42
# commits apart (#93). tools/build-engine-bundles.sh passes it.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
carve_lsp="$here/../carve-lsp"
carve_js_override=""
saw_path=0

while [ $# -gt 0 ]; do
  case "$1" in
    --carve-js)
      if [ $# -lt 2 ]; then
        echo "--carve-js needs a carve-js git ref" >&2
        exit 64
      fi
      carve_js_override="$2"
      shift 2
      ;;
    -*)
      echo "unknown option: $1" >&2
      echo "Usage: tools/build-lsp-bundle.sh [path-to-carve-lsp] [--carve-js <git-ref>]" >&2
      exit 64
      ;;
    *)
      if [ "$saw_path" -eq 1 ]; then
        echo "unexpected argument: $1" >&2
        exit 64
      fi
      carve_lsp="$1"
      saw_path=1
      shift
      ;;
  esac
done

if [ ! -d "$carve_lsp" ]; then
  echo "carve-lsp checkout not found at $carve_lsp" >&2
  echo "Pass the carve-lsp checkout path explicitly." >&2
  exit 1
fi

# Always rebuild the server from source so the vendored bundle matches the
# checked-out commit. carve-lsp pulls carve-js from git; `npm install` (not
# `npm ci`) is used deliberately so a drifted lockfile still resolves a
# carve-js that exports everything carve-lsp's source imports.
echo "Building carve-lsp in $carve_lsp ..."
(cd "$carve_lsp" && npm install)

if [ -n "$carve_js_override" ]; then
  # --no-save on purpose: the caller's carve-lsp checkout is a sibling working
  # copy, and this override belongs to THIS build, not to carve-lsp's own pin.
  # Its tracked package.json and package-lock.json come out untouched, which is
  # exactly why the provenance probe further down reads the INSTALLED tree
  # rather than the declared lockfile - see the comment there.
  echo "Linking carve-lsp against carve-js ${carve_js_override} ..."
  (cd "$carve_lsp" && npm install --no-save "github:markup-carve/carve-js#${carve_js_override}")
fi

(cd "$carve_lsp" && npm run build)

if [ ! -f "$carve_lsp/dist/server.js" ]; then
  echo "carve-lsp dist not found at $carve_lsp/dist/server.js after build" >&2
  exit 1
fi

out_dir="$here/src/main/resources/lsp"
out="$out_dir/server.js"
mkdir -p "$out_dir"

# Run esbuild from inside the carve-lsp checkout so the module path comments
# and __commonJS keys it emits are checkout-relative (dist/..., node_modules/...)
# instead of embedding machine-local paths from wherever the script happens to
# be invoked.
(cd "$carve_lsp" && npx --yes esbuild dist/server.js \
  --bundle --platform=node --format=cjs --target=node18 \
  --legal-comments=none --outfile="$out")

# Record the carve-lsp commit this bundle was built from, so staleness is
# detectable later (compare against carve-lsp HEAD). Written as a sibling
# VERSION file and prepended as a JS comment in the bundle itself.
carve_lsp_sha="$(git -C "$carve_lsp" rev-parse HEAD 2>/dev/null || echo unknown)"
# The carve-js dependency this bundle actually CARRIES, read from the installed
# tree. Two shapes, because carve-lsp has used both: a git tarball URL carrying
# the commit after a `#`, and (as of carve-lsp 465a0d5, "pin the engine
# exactly") a plain registry tarball for a published version. Emitted as
# `commit <40-hex>` or `npm <version>`.
#
# It reads node_modules/.package-lock.json, which npm writes to describe what is
# on disk, and NOT the checkout's own package-lock.json, which records what is
# declared. The two differ whenever anything installs outside the declaration -
# most obviously --carve-js above, which leaves the tracked lockfile alone by
# design. esbuild bundled the installed tree, so the installed tree is the only
# honest answer to "which engine is in this file"; stamping the declaration
# would put a commit in the header that is not in the payload, and a stamp that
# lies is worse than no stamp (#93).
#
# This used to read only the `#` fragment and fall back to the literal string
# `unknown`. When carve-lsp switched to the registry the field degraded to
# `unknown` in the committed bundle and nobody noticed, because nothing read the
# header back - the same shape as the drift this file's provenance exists to
# make visible (#62). `unknown` is now a hard failure here, and
# CarveBundleProvenanceTest rejects it on the way in as well.
carve_js_ref="$(node -e '
  const fs = require("fs");
  const installed = process.argv[1] + "/node_modules/.package-lock.json";
  if (!fs.existsSync(installed)) {
    throw new Error("no installed tree at " + installed + " - npm install did not run");
  }
  const lock = JSON.parse(fs.readFileSync(installed, "utf8"));
  const pkg = lock.packages["node_modules/@markup-carve/carve"];
  if (!pkg) throw new Error("carve-lsp installed tree has no @markup-carve/carve entry");
  const resolved = pkg.resolved || "";
  const frag = resolved.split("#")[1];
  if (frag && /^[0-9a-f]{40}$/.test(frag)) console.log("commit " + frag);
  else if (pkg.version) console.log("npm " + pkg.version);
  else throw new Error("cannot determine the carve-js reference from " + resolved);
' "$carve_lsp")"

# The stamp has to describe the payload, so an override that did not take is a
# hard failure rather than a bundle quietly built from something else.
if [ -n "$carve_js_override" ] && [ "$carve_js_ref" != "commit $carve_js_override" ]; then
  echo "asked to link carve-js ${carve_js_override}, but the installed tree resolves to '${carve_js_ref}'" >&2
  echo "Pass a full 40-character carve-js commit; a branch or tag name cannot be stamped." >&2
  exit 1
fi

# Standalone run, no override: say so now if this bundle is about to disagree
# with the preview bundle sitting next to it. CarveBundleProvenanceTest fails on
# exactly this, and finding out at build time - with both revisions named - beats
# finding out from a test three commands later.
if [ -z "$carve_js_override" ]; then
  preview_ref="$(sed -n 's|^// Bundled from markup-carve/carve-js commit \([0-9a-f]\{40\}\)$|commit \1|p' \
    "$here/src/main/resources/js/carve.iife.js" 2>/dev/null | head -n 1)"
  if [ -n "$preview_ref" ] && [ "$preview_ref" != "$carve_js_ref" ]; then
    echo >&2
    echo "WARNING: this bundle carries carve-js '${carve_js_ref}', but the preview bundle" >&2
    echo "         beside it carries '${preview_ref}'. The plugin would ship two different" >&2
    echo "         engines and CarveBundleProvenanceTest will reject the pair." >&2
    echo "         Rebuild both:   tools/build-engine-bundles.sh ../carve-js ../carve-lsp" >&2
    echo "         Or link this one against the preview's engine:" >&2
    echo "         tools/build-lsp-bundle.sh ${carve_lsp} --carve-js ${preview_ref#commit }" >&2
    echo >&2
  fi
fi
built_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

header="// Generated by tools/build-lsp-bundle.sh - do not edit by hand.
// Bundled from markup-carve/carve-lsp commit ${carve_lsp_sha}
// carve-js dependency ${carve_js_ref}
// Built (UTC): ${built_at}
"
# esbuild preserves the source shebang (#!/usr/bin/env node) at the top of the
# bundle. The plugin launches the file as an argument to `node`, never directly,
# so the shebang is dead weight - and a shebang anywhere but the very first line
# is a syntax error. Drop any leading shebang, then prepend the header.
body="$(sed '1{/^#!/d;}' "$out")"
printf '%s\n%s' "$header" "$body" > "$out"

printf 'carve-lsp %s\ncarve-js %s\nbuilt %s\n' "$carve_lsp_sha" "$carve_js_ref" "$built_at" > "$out_dir/VERSION"

# Sanity check: the bundle must be syntactically valid for `node`.
node --check "$out"

echo "Wrote $out (carve-lsp ${carve_lsp_sha})"
