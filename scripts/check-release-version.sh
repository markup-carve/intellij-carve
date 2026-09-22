#!/usr/bin/env bash
set -euo pipefail

tag="${1:?release tag required}"
gradle_file="${2:-build.gradle.kts}"
tag="${tag#v}"
declared="$(sed -nE 's/^version[[:space:]]*=[[:space:]]*"([^"]+)"/\1/p' "$gradle_file" | head -n1)"

if [ -z "$declared" ]; then
  echo "::error::could not read the Gradle version from $gradle_file"
  exit 1
fi

echo "tag: $tag, build.gradle.kts: $declared"
if [ "$tag" != "$declared" ]; then
  echo "::error::tag $tag does not match build.gradle.kts ($declared)"
  exit 1
fi
