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
    lsp/                     # bundled carve-js language server (generated)
    kotlin/org/markupcarve/carve/lsp/  # lsp4ij factory + connection provider
    textmate/                # TextMate bundle (package.json, grammar, language config)
    js/carve.iife.js         # bundled carve-js renderer (generated)
    icons/, liveTemplates/
tools/build-carve-bundle.sh   # regenerates js/carve.iife.js
tools/build-lsp-bundle.sh     # regenerates lsp/server.js
tools/build-engine-bundles.sh # regenerates BOTH, from one carve-js revision
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

A second argument writes the bundle somewhere else instead of over the vendored
one. `engine-drift.yml` uses it to build a throwaway REFERENCE bundle from
carve-js `main` through this same recipe, so it can report how much of the
measured drift a rebuild here would actually fix:

```bash
tools/build-carve-bundle.sh ../carve-js /tmp/reference.iife.js
node tools/corpus-through-bundle.mjs src/main/resources/js/carve.iife.js \
  ../carve/tests/corpus --list --reference /tmp/reference.iife.js
```

`wrong=` is the vendored bundle against carve `main`; `reference_wrong=` is
carve-js `main` against the same corpus, which is upstream engine debt this repo
cannot close; `attributable=` is the difference, and the only one gated.

The `gradle test` task exercises this bundle under GraalJS, so a broken or
incompatible bundle fails CI.

## Bundled language server (lsp/server.js)

LSP features (diagnostics, completion, folding, document symbols, hover, code
actions, rename, formatting, semantic tokens, code lenses) come from
[carve-lsp](https://github.com/markup-carve/carve-lsp), a Node stdio language
server, wired up through
[LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij). The compiled server
is bundled into the plugin as a single self-contained file
(`src/main/resources/lsp/server.js`, all dependencies inlined by esbuild) and
launched as `node lsp/server.js --stdio`. Only a `node` binary is needed at
runtime - no `node_modules` are shipped.

carve-lsp is not published to npm yet, so the bundle is generated from a local
checkout and committed. To refresh it:

```bash
# carve-lsp checked out as a sibling; the script runs npm install + build for you
tools/build-lsp-bundle.sh ../carve-lsp
```

The script records the carve-lsp commit in `src/main/resources/lsp/VERSION` and
in a header comment in `server.js`, so a stale bundle is detectable (compare
against carve-lsp HEAD). It also runs `node --check` on the result.

The `carve-js dependency` line in that header is read from the INSTALLED tree
(`node_modules/.package-lock.json`), not from carve-lsp's own `package-lock.json`.
The two differ whenever anything is installed outside the declaration - including
`--carve-js` below - and only the installed tree describes what esbuild actually
bundled.

## Regenerating both bundles together

The plugin ships the engine twice: `js/carve.iife.js` for the preview pane and
HTML export, and a second copy inlined into `lsp/server.js`. They are one engine
as far as a user is concerned, so they have to be one revision - and the two
scripts above cannot get there on their own, because they choose an engine two
different ways. `build-carve-bundle.sh` uses the carve-js checkout it is handed;
`build-lsp-bundle.sh` gets whatever carve-lsp pins, which is an exact commit in
carve-lsp's own `package.json`. Run independently they shipped 42 commits apart
(#93).

So regenerate the pair with one command, which takes the revision once:

```bash
# carve-js parked on the revision you want and already built (npm ci && npm run build)
tools/build-engine-bundles.sh ../carve-js ../carve-lsp
```

It stamps both bundles with `../carve-js`'s `HEAD`, links the language server
against that same commit (with `npm install --no-save`, so carve-lsp's own pin
and lockfile are left untouched), and refuses to finish if the two headers come
out naming different commits. `CarveBundleProvenanceTest` asserts the same
equality offline on every pull request, so a pair that drifted apart cannot be
committed quietly.

Use the individual scripts when only one bundle needs to move - a carve-lsp
change with no engine change, say. Pass the engine explicitly so the result
still matches its neighbour:

```bash
# the commit js/carve.iife.js already names, so the pair stays on one engine
tools/build-lsp-bundle.sh ../carve-lsp --carve-js 5695480e97e228821590fc7180485aecdb2a8839
```

Without `--carve-js` the server is linked against whatever carve-lsp pins, and
the script warns (naming both revisions) when that would disagree with the
preview bundle.

The language server itself is not exercised headlessly by `gradle test`; verify
it manually with `./gradlew runIde` (see the checklist in the PR / below).

## TextMate grammar

Editor syntax highlighting uses the TextMate bundle in
`src/main/resources/textmate/`. The grammar (`carve.tmLanguage.json`) is
committed here and is *related to* - but deliberately not identical to -
[vscode-carve](https://github.com/markup-carve/vscode-carve)'s copy (scope
`text.carve`).

Two differences are by design:

1. **Scope names.** This plugin uses `keyword.control.*` where vscode-carve uses
   `punctuation.definition.*`, because the IDE's TextMate bridge colours
   `keyword.control.*` out of the box. Suffixes are kept identical.
2. **Plugin-only rules.** `cross-reference`, `hard-break` and `thematic-break`
   are highlighted here and not upstream.

Because of that, the grammar must **never** be overwritten with the upstream
file - doing so would rewrite every scope name and delete the plugin-only rules.
Port upstream changes by hand. To see what currently differs:

```bash
gradle checkGrammarDrift   # read-only; never edits the grammar
```

It reports three categories - plugin-only rules (expected), structurally
diverged shared rules (human judgement), and upstream-only rules (features this
plugin is missing) - and fails only on the last, actionable category.

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
