# Development

## Building

No Gradle wrapper is committed; use a local Gradle 8.5+ (CI installs it):

```bash
gradle buildPlugin     # -> build/distributions/intellij-carve-*.zip
gradle test            # run unit tests (incl. the GraalJS render test)
gradle verifyPlugin    # plugin structure verification
gradle runIde          # launch a sandbox IDE with the plugin
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
