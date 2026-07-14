# Changelog

All notable changes to the Carve plugin for JetBrains IDEs are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.3] - 2026-07-14

### Fixed

- **Live preview no longer fails to render on current IDE builds.** The bundled GraalJS
  (`org.graalvm.js:js:23.0.2`) shipped a Truffle runtime that calls
  `sun.misc.Unsafe.ensureClassInitialized`, a JDK-internal method removed in recent JDKs.
  On a current JBR this threw `NoSuchMethodError` while building the polyglot context, so
  opening a `.crv` file produced no preview. Moved to the modern polyglot coordinates
  (`org.graalvm.polyglot:polyglot` + `js-community` 24.2.1).
- **Marketplace "What's new" no longer drifts from the release.** `<change-notes>` is now
  generated from `CHANGELOG.md` at build time instead of being hand-maintained in
  `plugin.xml` - 0.1.2 shipped carrying 0.1.1's notes because the block was never updated.

### Changed

- **Superscript and subscript are braced-only (`{^sup^}` / `{,sub,}`); bare
  `^sup^` and `,sub,` no longer exist** (upstream spec change, markup-carve/carve#259).
  A bare `^` or `,` is literal text, so the bare emphasis delimiter set is now
  `/ * _ ~ =`. The TextMate grammar no longer highlights the bare forms, and the
  `csup` / `csub` live templates now insert the braced forms. Line-start `^ `
  captions, table header rowspan `^` cells and `^[...]` inline footnotes are
  unaffected.
- **LSP4IJ is now an optional dependency.** The plugin loads with only syntax
  highlighting, live preview, HTML export and live templates when LSP4IJ is not
  installed; the language-server features (diagnostics, completion, outline,
  hover, code actions, rename, formatting, semantic tokens) activate when the
  [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin is present.
  Previously LSP4IJ was a hard dependency, so the IDE refused to load the plugin
  until it was installed. The LSP4IJ extension points moved to an optional
  `carve-lsp.xml` config file.

## [0.1.2] - 2026-07-12

### Changed

- **The `.carve` file extension is no longer registered; `.crv` is the only
  Carve extension.** Rename any `.carve` files to `.crv`.

### Added

- Syntax highlighting for citations (`[@key]` groups: keys, integral `+`,
  per-item modifiers, separators) and code callouts (`<N>` markers on
  annotation lines and at end of line), with a hand-authored fixture snapshot
  test class alongside the corpus goldens

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
- Vendored JS bundles no longer leak machine-local build paths in module
  comments; both bundles rebuilt from their pinned source commits

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
