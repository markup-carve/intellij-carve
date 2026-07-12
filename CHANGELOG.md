# Changelog

All notable changes to the Carve plugin for JetBrains IDEs are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- TextMate grammar: `#critic-markup` and `#emphasis` now run before `#attributes`,
  which was consuming `{+ins+}` / `{-del-}` / `{~old~>new~}` / `{#comment#}` and
  every brace-emphasis form
- TextMate grammar: brace forms `{=highlight=}`, `{,sub,}`, `{^sup^}` tokenize
- TextMate grammar: cross-references (`</#id>`) get their own link scope instead
  of leaking into the `#tag` rule; inline footnotes (`^[...]`), hard breaks
  (trailing backslash) and `:: term` definition lines tokenize
- TextMate grammar: extended task states `[-] [_] [>] [?]`; a lone `+`
  list-attach marker is no longer stolen by the table-continuation rule
- TextMate grammar: word-boundary guards on bare `*bold*`, `~strike~`, `^sup^`
  so intraword delimiters stay literal per spec (fixture corpus asserted the
  old bold behavior as a golden - the corpus text itself says "stay literal")

## [0.1.1]

### Added

- LSP support via LSP4IJ, backed by the bundled carve-lsp server: diagnostics,
  completion, code folding, structure/outline and breadcrumbs, hover, quick
  fixes/intentions (Djot/Markdown to Carve migrations), rename, formatting,
  semantic highlighting, and code lenses. Requires Node.js on the PATH (or set
  in Settings | Tools | Carve).
- Preview rendering for the extension set: code groups/tabs, inline and block
  spoilers, Mermaid diagrams, and Chart.js charts.
- Code-block language badges and filename header bars in the preview.

### Changed

- Refreshed the bundled carve-js renderer (list tables, math blocks,
  details/disclosure, and the latest core fixes).
- Highlighting updated for the latest syntax: block headers (`"..."`) and
  grouping labels (`[...]`) on code fences and `:::` divs, and GFM `|---|`
  delimiter rows.
- Preview re-renders all extensions live as you type (single hydration pass on
  load and after every edit).
- Minimum supported IDE raised to 2024.3 (build 243). 2024.1 and 2024.2 are no
  longer supported.

### Fixed

- The `chl` and `csub` live templates inserted doubled delimiters (`==`/`,,`);
  corrected to the canonical single `=`/`,`.
- The preview no longer raises a read-access threading error when editing or
  pasting.
- Modernized deprecated platform API usage (file-chooser fields, JBCef browser,
  action update thread).

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

[Unreleased]: https://github.com/markup-carve/intellij-carve/compare/0.1.1...HEAD
[0.1.1]: https://github.com/markup-carve/intellij-carve/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/markup-carve/intellij-carve/releases/tag/0.1.0
