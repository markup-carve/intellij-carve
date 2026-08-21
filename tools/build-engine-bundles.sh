#!/usr/bin/env bash
#
# Regenerates BOTH vendored engine bundles from ONE carve-js revision.
#
# The plugin ships two independent copies of the engine: src/main/resources/js/
# carve.iife.js, which the preview pane and HTML export run on GraalJS, and the
# copy inlined into src/main/resources/lsp/server.js, which the language server
# uses for diagnostics and structure. A user editing one document sees both, so
# they have to be the same engine - and when they are not, nothing about the
# disagreement tells them which half is right.
#
# They drifted 42 commits apart because they were built by two scripts that pick
# an engine two different ways, 21 seconds apart (#93):
#
#   * build-carve-bundle.sh bundles whatever carve-js checkout it is handed, so
#     its revision is the caller's;
#   * build-lsp-bundle.sh bundles carve-lsp, whose package.json pins carve-js
#     exactly, so its revision is carve-lsp's - and re-running it can never
#     converge on the other one.
#
# Hence this script: the revision is an INPUT, taken once, and both bundles are
# built from it in one invocation. Running the two scripts by hand still works
# and is still correct for a single bundle; it is just no longer the way to
# regenerate the pair.
#
# Usage:
#   tools/build-engine-bundles.sh [path-to-carve-js] [path-to-carve-lsp]
#
# The carve-js checkout's HEAD is the revision both bundles are built from, so
# check out the revision you want before running this. Build carve-js first
# (npm ci && npm run build); build-carve-bundle.sh needs its dist/.
#
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
carve_js="${1:-$here/../carve-js}"
carve_lsp="${2:-$here/../carve-lsp}"

# `git rev-parse`, not a test for a `.git` DIRECTORY: in a worktree `.git` is a
# file pointing at the real gitdir, and this script's whole job is to be run
# against a checkout parked on a particular revision - which is exactly what a
# worktree is for.
if ! git -C "$carve_js" rev-parse --git-dir >/dev/null 2>&1; then
  echo "carve-js checkout not found at $carve_js" >&2
  echo "Usage: tools/build-engine-bundles.sh [path-to-carve-js] [path-to-carve-lsp]" >&2
  exit 1
fi

rev="$(git -C "$carve_js" rev-parse HEAD)"
case "$rev" in
  [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]) : ;;
  *)
    echo "carve-js HEAD is '$rev', which is not a 40-character commit" >&2
    exit 1
    ;;
esac

# The language server needs the revision to be fetchable from GitHub, because it
# links it through npm rather than from the checkout. A commit that only exists
# locally would install as something else entirely, so refuse it here rather
# than stamping a header nobody can reproduce.
if ! git -C "$carve_js" merge-base --is-ancestor "$rev" origin/main 2>/dev/null; then
  echo "carve-js $rev is not an ancestor of its origin/main." >&2
  echo "Both bundles are stamped with it and the language server installs it from GitHub," >&2
  echo "so it has to be a commit that is actually published. Fetch, or check out a merged commit." >&2
  exit 1
fi

echo "Building both engine bundles from carve-js $rev"
"$here/tools/build-carve-bundle.sh" "$carve_js"
"$here/tools/build-lsp-bundle.sh" "$carve_lsp" --carve-js "$rev"

# Read the two headers back. The build scripts each write their own, from their
# own view of what they linked, so comparing them here is a real check and not a
# restatement of the argument above: it is what catches a build script whose
# probe stopped describing its payload.
preview_rev="$(sed -n 's|^// Bundled from markup-carve/carve-js commit \([0-9a-f]\{40\}\)$|\1|p' \
  "$here/src/main/resources/js/carve.iife.js" | head -n 1)"
server_rev="$(sed -n 's|^// carve-js dependency commit \([0-9a-f]\{40\}\)$|\1|p' \
  "$here/src/main/resources/lsp/server.js" | head -n 1)"

fail=0
if [ "$preview_rev" != "$rev" ]; then
  echo "carve.iife.js came out stamped '${preview_rev:-MISSING}', not $rev" >&2
  fail=1
fi
if [ "$server_rev" != "$rev" ]; then
  echo "lsp/server.js came out carrying '${server_rev:-MISSING}', not $rev" >&2
  fail=1
fi
if [ "$fail" -ne 0 ]; then
  echo "The two bundles did not come out on the same carve-js commit - do not commit this pair." >&2
  exit 1
fi

echo "Both bundles now carry carve-js $rev"
