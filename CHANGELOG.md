# Changelog

All notable changes to the Carve plugin for JetBrains IDEs are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0]

Initial release.

### Added

- Syntax highlighting via the shared TextMate grammar (lockstep with vscode-carve).
- Live preview panel in a split editor view, updating as you type.
- IDE theme sync - the preview follows the IDE dark/light mode.
- Code highlighting in preview code blocks (highlight.js).
- Export to HTML from the editor context menu.
- Live templates for Carve's visual mnemonics (type `c` + `Tab`).
- File type recognition for `.crv` and `.carve`.
- Two preview renderers: bundled carve-js (GraalJS, no dependencies) and
  carve-php (PHP CLI via markup-carve/carve-php).
- Custom preview CSS, layered from file-, project-, and settings-level sources.

[Unreleased]: https://github.com/markup-carve/intellij-carve/compare/0.1.0...HEAD
[0.1.0]: https://github.com/markup-carve/intellij-carve/releases/tag/0.1.0
