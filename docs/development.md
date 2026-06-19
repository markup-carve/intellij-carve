# Development

## Building

Use the committed Gradle wrapper (`./gradlew`); it pins the Gradle version:

```bash
./gradlew buildPlugin     # -> build/distributions/intellij-carve-*.zip
./gradlew test            # run unit tests (incl. the GraalJS render test)
./gradlew verifyPlugin    # plugin structure verification
./gradlew runIde          # launch a sandbox IDE with the plugin
```

Install the built zip via **Settings → Plugins → Install Plugin from Disk**.

## Project structure

```
src/main/
  kotlin/org/markupcarve/carve/
    CarveLanguage.kt, CarveFileType.kt, CarveIcons.kt
    CarveConverter.kt        # GraalJS (carve-js) + PHP routing
    CarvePhpConverter.kt     # carve-php CLI
    preview/                 # split editor, preview panel, tool window
    settings/                # renderer settings + UI
    actions/                 # export-to-HTML, toggle-preview
  resources/
    META-INF/plugin.xml
    textmate/                # TextMate bundle (package.json, grammar, language config)
    js/carve.iife.js         # bundled carve-js renderer (generated)
    icons/, liveTemplates/
tools/build-carve-bundle.sh  # regenerates js/carve.iife.js
```

## Bundled renderer (carve.iife.js)

The default preview/export engine runs the bundled `carve.iife.js` on GraalJS.
It is an IIFE build of [`@markup-carve/carve`](https://github.com/markup-carve/carve-js)
exposing a global `carve` with `carveToHtml(source)`.

Carve-js is not published to npm yet, so the bundle is generated from a local
checkout and committed. To refresh it:

```bash
# carve-js checked out as a sibling, already built (npm ci && npm run build)
tools/build-carve-bundle.sh ../carve-js
```

The `gradle test` task exercises this bundle under GraalJS, so a broken or
incompatible bundle fails CI.

## TextMate grammar

Editor syntax highlighting uses the TextMate bundle in
`src/main/resources/textmate/`. The grammar (`carve.tmLanguage.json`) is
committed and shared with [vscode-carve](https://github.com/markup-carve/vscode-carve)
(scope `text.carve`). To refresh it to the latest upstream version:

```bash
gradle downloadGrammar   # overwrites the committed grammar; commit the result
```

## Shared-corpus highlighter conformance

The highlighter is tested against the shared Carve corpus from
[markup-carve/carve](https://github.com/markup-carve/carve), pinned as the `spec`
git submodule (inputs under `spec/tests/corpus`). Clone with submodules, or
initialize them after the fact:

```bash
git clone --recurse-submodules https://github.com/markup-carve/intellij-carve
# or, in an existing checkout:
git submodule update --init spec
```

Two test layers live in `src/test/kotlin/org/markupcarve/carve/corpus/`:

- **Snapshot tests** (`CarveCorpusSnapshotTest`) run every covered `.crv` through
  the IDE's own TextMate engine over the committed grammar - the same code path
  editor highlighting uses - and compare the token-scope stream against golden
  resources in `src/test/resources/corpus-tokens/`. Each golden is one line per
  token (`escaped-text -> full.scope.chain`) and is meant to be reviewed by hand.

  Regenerate goldens after a deliberate grammar change, then review the diff:

  ```bash
  ./gradlew test --tests "*CarveCorpusSnapshotTest" -Dcarve.updateGoldens=true
  ```

- **Coverage matrix** (`CarveCorpusCoverageMatrixTest` + `CarveCorpusCategories`)
  classifies every corpus category as `COVERED` (the grammar highlights it, so it
  is snapshotted) or `SKIP` (a parser/render behavior with no token-level signal,
  recorded with a reason). The test fails if the live corpus contains a category
  absent from both lists, so a new spec category - pulled in by bumping the
  submodule - forces a deliberate COVERED-or-SKIP decision instead of silently
  drifting behind the spec.

To bump the corpus to a newer spec commit:

```bash
git -C spec fetch origin && git -C spec checkout origin/main
git add spec && ./gradlew test    # resolve any new-category coverage failure
```

## Publishing

Releases are automated via GitHub Actions. Pushing a `X.Y.Z` tag builds the
plugin, publishes it to the JetBrains Marketplace, and creates a GitHub release.

### Release checklist

1. Bump `version` in `build.gradle.kts`, commit, and push.
2. Tag and push:
   ```bash
   git tag X.Y.Z
   git push origin X.Y.Z
   ```
3. GitHub Actions builds, publishes, and creates the release.

The `PUBLISH_TOKEN` secret (a JetBrains Marketplace token from
https://plugins.jetbrains.com/author/me/tokens) must be configured in the
repo settings.
