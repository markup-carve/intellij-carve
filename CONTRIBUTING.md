# Contributing to intellij-carve

Thanks for your interest in contributing!

## Setup

```bash
git clone https://github.com/markup-carve/intellij-carve.git
cd intellij-carve
./gradlew runIde      # launches a sandbox IDE with the plugin
```

See [docs/development.md](docs/development.md) for the project structure, the
bundled renderer, grammar updates, and the release process.

## Making changes

- **Editor highlighting** comes from this plugin's own TextMate grammar, which is
  related to [vscode-carve](https://github.com/markup-carve/vscode-carve)'s but
  **intentionally diverges** from it (different scope-name convention, plus rules
  only this plugin has). Never overwrite it with the upstream file. Fix shared
  grammar issues upstream first, then port the change here by hand, keeping the
  `keyword.control.*` scope names. Run `./gradlew checkGrammarDrift` to see what
  differs - it is read-only and never edits the grammar.
- **Preview/export rendering** uses the bundled `carve.iife.js`
  (`@markup-carve/carve` on GraalJS) or the carve-php CLI. Regenerate the bundle
  with `tools/build-carve-bundle.sh` when carve-js changes.

## Testing

```bash
./gradlew test            # unit tests, including the GraalJS render test
./gradlew verifyPlugin    # plugin structure verification
```

## Pull requests

1. Create a feature branch from `main`.
2. Make your change and run `./gradlew test` + `./gradlew runIde`.
3. Open a PR against `main`.

## Code style

- Kotlin conventions, 4-space indentation.
- Keep the plugin in lockstep with the Carve language: do not invent syntax not
  in the [spec](https://github.com/markup-carve/carve).

## Reporting issues

Use the [issue tracker](https://github.com/markup-carve/intellij-carve/issues).
Include your IDE version, plugin version, steps to reproduce, and expected vs
actual behavior.
