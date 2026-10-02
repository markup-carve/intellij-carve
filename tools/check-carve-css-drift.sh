#!/usr/bin/env bash
# The vendored carve-css layers are a copy. Prove the stamp is honest, and
# prove the copy is of the newest published release.
#
# src/main/resources/css/{tokens,recipes,contrast}.css are three of carve-css's
# layers, vendored because the plugin ships its resources in a jar and has no
# npm step to resolve the package at build time. A copy nothing compares is a
# copy only until upstream moves: these sat at 0.1.0 while 0.1.1 was published,
# so a gallery tile styled media it should not reach and seven public control
# tokens the recipes read were simply absent (#224).
#
# contrast.css joined at 0.1.2. It was held back from the 0.1.1 refresh because
# that release's forced-colors block remapped --carve-accent to LinkText and
# left --carve-ink-inverse a hex, which put a code-callout badge near 1.50:1 -
# worse than vendoring no layer at all (#226).
#
# Two questions, kept apart because they have different remedies:
#
#   1. Does the recorded version still match what npm publishes? A newer
#      release means re-vendor.
#   2. Do the vendored bytes still match the version they CLAIM to be? A
#      mismatch means someone edited a vendored layer in place, which the
#      header forbids.
#
# Needs network. Run out of the `test` task for that reason, the same way
# check-battery-drift.sh is.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
css="$root/src/main/resources/css"
record="$css/UPSTREAM"
package="@markup-carve/carve-css"

recorded_version="$(awk '$1 == "version" { print $2 }' "$record")"
if [[ -z "$recorded_version" ]]; then
  echo "No version line in $record" >&2
  exit 1
fi

published="$(npm view "$package" version)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
(cd "$work" && npm pack --silent "$package@$recorded_version" >/dev/null)
tarball="$(find "$work" -maxdepth 1 -name '*.tgz' -print -quit)"
tar xzf "$tarball" -C "$work"

failed=0

# Question 2 first: a lying stamp makes question 1's answer meaningless.
for name in tokens recipes contrast extensions; do
  local_file="$css/$name.css"
  remote_file="$work/package/src/$name.css"
  if [[ ! -f "$remote_file" ]]; then
    echo "$package@$recorded_version does not ship src/$name.css" >&2
    failed=1
    continue
  fi
  # The vendored file is the upstream text with a provenance header prepended.
  # Drop everything up to and including that header's closing delimiter.
  sed '1,/^ \*\//d' "$local_file" > "$work/$name.stripped"
  if ! diff -q "$remote_file" "$work/$name.stripped" >/dev/null; then
    echo "$name.css does not match $package@$recorded_version:"
    # `diff` exits 1 when there IS a difference, which under `pipefail` would
    # abort here and swallow the remediation line below.
    diff "$remote_file" "$work/$name.stripped" | head -40 || true
    echo
    echo "Do not edit a vendored layer. Change it in carve-css, release, re-vendor."
    failed=1
  fi
done

if [[ "$published" != "$recorded_version" ]]; then
  echo "carve-css has moved: vendored $recorded_version, npm publishes $published."
  echo
  echo "Re-vendor all four layers from the published tarball and update $record:"
  echo "  npm pack $package@$published"
  echo "Keep the provenance header, set its version and commit to the new release's tag,"
  echo "and refresh the SHA-256 lines. Then read what the new text changes - a layer that"
  echo "is only half correct for the preview is a review, not a copy."
  failed=1
fi

if (( failed )); then
  exit 1
fi

printf 'check-carve-css-drift: all four layers match %s@%s, the newest published release.\n' \
  "$package" "$recorded_version"
