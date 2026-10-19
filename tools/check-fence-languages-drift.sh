#!/usr/bin/env bash
# The vendored fence-language table is a copy. Prove it still is.
#
# tools/fence-languages.json is carve-grammars' table, vendored so this grammar
# embeds the same fence languages as every other Carve editor grammar.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
local_file="$here/fence-languages.json"
upstream="${CARVE_GRAMMARS_DIR:-}"

if [[ -z "$upstream" ]]; then
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  git clone --quiet --depth 1 https://github.com/markup-carve/carve-grammars "$work/cg"
  upstream="$work/cg"
fi

remote_file="$upstream/fence-languages/fence-languages.json"
if [[ ! -f "$remote_file" ]]; then
  echo "No fence-languages/fence-languages.json in $upstream" >&2
  exit 1
fi

if ! diff -q "$local_file" "$remote_file" >/dev/null; then
  echo "The vendored fence-language table has drifted from carve-grammars:"
  # `diff` exits 1 on a difference, which `pipefail` would turn into an abort
  # before the remediation line.
  diff "$local_file" "$remote_file" | head -40 || true
  echo
  echo "Re-copy tools/fence-languages.json, then run node tools/generate-fence-languages.mjs."
  exit 1
fi
for language in csv tsv; do
  local_grammar="$here/../src/main/resources/textmate/$language.tmLanguage.json"
  remote_grammar="$upstream/textmate/$language.tmLanguage.json"
  if [[ ! -f "$remote_grammar" ]] || ! diff -q "$local_grammar" "$remote_grammar" >/dev/null; then
    echo "The vendored $language grammar differs from carve-grammars: $remote_grammar" >&2
    exit 1
  fi
done
printf 'check-fence-languages-drift: %s row(s) match carve-grammars.\n' \
  "$(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1], encoding='utf-8'))['languages']))" "$local_file")"
