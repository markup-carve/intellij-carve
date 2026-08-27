#!/usr/bin/env bash
#
# Re-vendor the preview's third-party browser assets.
#
# The live preview runs entirely offline: highlight.js, Chart.js, MathJax and
# Mermaid are copied into src/main/resources/preview-assets/ and referenced from
# there, so opening a .crv file makes no outbound request from the user's IDE.
# This script is how that copy is produced, and it is the only supported way to
# change it - hand-editing a vendored file would leave INDEX (and therefore
# CarvePreviewAssetsTest) disagreeing with the tree.
#
# Versions are PINNED here, one variable per package. Bumping one is a
# deliberate edit to this file followed by a re-run; nothing resolves "latest"
# at build time, and nothing resolves anything at all at run time.
#
# Usage:  tools/vendor-preview-assets.sh
#
set -euo pipefail

HLJS_VERSION="11.9.0"
CHARTJS_VERSION="4.5.1"
MATHJAX_VERSION="3.2.2"
MERMAID_VERSION="11.17.2"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$repo_root/src/main/resources/preview-assets"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

say() { printf '  %s\n' "$*"; }

fetch() {
    # fetch <url> <path> - fails loudly. A vendoring script that silently wrote a
    # 404 page into the plugin would ship a preview that renders nothing.
    local url="$1" out="$2"
    curl -fsSL --retry 2 -o "$out" "$url"
    [ -s "$out" ] || { echo "empty download: $url" >&2; exit 1; }
}

untar() {
    # untar <tgz> <member...> - extracts npm package members into $work/pkg.
    local tgz="$1"; shift
    rm -rf "$work/pkg"
    mkdir -p "$work/pkg"
    tar xzf "$tgz" -C "$work/pkg" "$@"
}

# Everything except VENDOR.md, which is hand-written prose this script must not
# eat. A blanket rm -rf on $dest did exactly that.
rm -rf "$dest"/{highlight,chart,mathjax,mermaid} "$dest/INDEX"
mkdir -p "$dest"/{highlight,chart,mathjax,mermaid}

echo "highlight.js $HLJS_VERSION"
hljs_base="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@$HLJS_VERSION/build"
fetch "$hljs_base/highlight.min.js" "$dest/highlight/highlight.min.js"
fetch "$hljs_base/styles/github.min.css" "$dest/highlight/github.min.css"
fetch "$hljs_base/styles/github-dark.min.css" "$dest/highlight/github-dark.min.css"
fetch "https://raw.githubusercontent.com/highlightjs/highlight.js/$HLJS_VERSION/LICENSE" "$dest/highlight/LICENSE"
say "$(du -sh "$dest/highlight" | cut -f1)"

echo "Chart.js $CHARTJS_VERSION"
fetch "https://cdn.jsdelivr.net/npm/chart.js@$CHARTJS_VERSION/dist/chart.umd.min.js" "$dest/chart/chart.umd.min.js"
fetch "https://registry.npmjs.org/chart.js/-/chart.js-$CHARTJS_VERSION.tgz" "$work/chart.tgz"
untar "$work/chart.tgz" package/LICENSE.md
cp "$work/pkg/package/LICENSE.md" "$dest/chart/LICENSE.md"
say "$(du -sh "$dest/chart" | cut -f1)"

echo "MathJax $MATHJAX_VERSION"
# The CHTML output bundle plus its webfont directory. MathJax resolves the fonts
# RELATIVE TO ITS OWN script URL, so the `output/chtml/fonts/woff-v2` layout
# under the bundle is not decoration - flatten it and every glyph falls back.
fetch "https://registry.npmjs.org/mathjax/-/mathjax-$MATHJAX_VERSION.tgz" "$work/mathjax.tgz"
untar "$work/mathjax.tgz" \
    package/es5/tex-mml-chtml.js \
    package/es5/output/chtml/fonts/woff-v2 \
    package/LICENSE
cp "$work/pkg/package/es5/tex-mml-chtml.js" "$dest/mathjax/tex-mml-chtml.js"
mkdir -p "$dest/mathjax/output/chtml/fonts/woff-v2"
cp "$work/pkg/package/es5/output/chtml/fonts/woff-v2/"*.woff "$dest/mathjax/output/chtml/fonts/woff-v2/"
cp "$work/pkg/package/LICENSE" "$dest/mathjax/LICENSE"
say "$(du -sh "$dest/mathjax" | cut -f1)"

echo "Mermaid $MERMAID_VERSION"
fetch "https://registry.npmjs.org/mermaid/-/mermaid-$MERMAID_VERSION.tgz" "$work/mermaid.tgz"
untar "$work/mermaid.tgz" package/dist/mermaid.min.js package/LICENSE
cp "$work/pkg/package/dist/mermaid.min.js" "$dest/mermaid/mermaid.min.js"
cp "$work/pkg/package/LICENSE" "$dest/mermaid/LICENSE"
say "$(du -sh "$dest/mermaid" | cut -f1)"

# The UMD bundle must be self-contained: a dynamic import() would resolve at run
# time against a chunk directory that is not vendored, i.e. the exact silent
# network dependency this whole change removes.
if grep -q 'import(' "$dest/mermaid/mermaid.min.js"; then
    echo "mermaid.min.js contains a dynamic import() - its chunks would have to be vendored too" >&2
    exit 1
fi

# INDEX is what the runtime extractor reads (it cannot list a directory inside a
# jar) and what CarvePreviewAssetsTest checks the tree against. Sorted, so the
# file is stable across machines. VENDOR.md is prose for humans and is neither
# extracted nor hashed, so it is the one file the index leaves out.
index="$dest/INDEX"
: > "$index"
(
    cd "$dest"
    find . -type f ! -name INDEX ! -name VENDOR.md -printf '%P\n' | LC_ALL=C sort | while read -r rel; do
        printf '%s %s %s\n' "$(sha256sum "$rel" | cut -d' ' -f1)" "$(stat -c%s "$rel")" "$rel"
    done
) >> "$index"

echo
echo "vendored $(wc -l < "$index") files, $(du -sh "$dest" | cut -f1) total"
echo "VENDOR.md was left untouched - update its version table by hand."
