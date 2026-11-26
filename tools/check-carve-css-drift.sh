#!/usr/bin/env bash
# Verify the vendored preview layers against the pinned Carve 0.2 source.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
css="$root/src/main/resources/css"
record="$css/UPSTREAM"
commit="$(awk '$1 == "commit" { print $2 }' "$record")"
if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo "css/UPSTREAM needs a full commit" >&2
  exit 1
fi

head="$(git ls-remote https://github.com/markup-carve/carve-css.git refs/heads/next2 | cut -f1)"
if [[ "$head" != "$commit" ]]; then
  echo "carve-css next2 moved: vendored $commit, current $head" >&2
  exit 1
fi

for name in tokens recipes contrast extensions; do
  local_file="$css/$name.css"
  source_url="https://raw.githubusercontent.com/markup-carve/carve-css/$commit/src/$name.css"
  if ! diff -q <(curl -fsSL "$source_url") <(sed '1,/^ \*\//d' "$local_file") >/dev/null; then
    echo "$name.css differs from carve-css $commit" >&2
    exit 1
  fi
done
printf 'check-carve-css-drift: all four layers match carve-css next2 %s.\n' "$commit"
