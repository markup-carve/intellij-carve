# Changelog

All notable changes to the Carve plugin for JetBrains IDEs are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **Block quotes retain the scopes of the constructs inside them** (#98). Headings, thematic breaks, tables, captions, code fences and inline constructs behind a quote marker are now tokenized as their own constructs inside the quote instead of flattening to quote text.
- **All construct-ledger payload leaks and attribution gaps are closed** (#97). Fenced and inline code plus both braced comment families keep their payload inert across the generated sweep; reference images, footnote definitions and symbols carry their own identities; and the shared ledger now records the grammar's intentional grouped rules.

## [0.1.6] - 2026-08-27

### Fixed

- **The preview registers on a module that exists** (#104). The split editor and the tool window were registered behind `<depends optional="true">com.intellij.modules.jcef</depends>`, and no IDE declares that module - JCEF is platform code in `lib/app-client.jar`, not a plugin - so neither extension point was ever registered anywhere and `Toggle Carve Preview` looked up a tool window that did not exist and returned silently. Both register unconditionally now and ask `JBCefApp.isSupported()` directly, so a runtime without a browser opens `.crv` in the plain editor and offers no dead control.
- **A bare boolean attribute does not start with an underscore** (#90, #87). `{_x}` scoped as an attribute block where the engine renders it as text, and `{_x_}` collided with a forced underline; `{_k=1}`, `{#_id}` and `{._c}` keep their leading underscore. All seven sites spelling the item alternation take the narrowing, `CarveMarkerScanner` included.
- **A table cell's marker run ends at a space** (#91, #86). The header rule was a bare `|=`, so it took the marker wherever the two characters stood and `|=hot= is the reading |` came back a header cell holding `hot=`. The alignment run is scoped for the first time, and only where the engine reads one.
- **A hyphen run before a word is a flag, and an empty brace pair is text** (#92, #85). `git log --oneline` colored as an en dash; the doubled arrows are scoped and `=>` is no longer one; `{//}` through `{##}` render literally, while `{--}` is the braced en dash rather than a deletion.
- **The preview pane and the language server run the same engine** (#94, #93). The plugin ships carve-js twice and the two copies were 42 commits apart, so the two halves of one editor could disagree about one document. Both bundles are built from one revision by one script that takes it as an input, and `CarveBundleProvenanceTest` compares the two headers instead of reading one file at a time, so they cannot drift apart again.
- **A colon fence's marker separator is a run of spaces** (#102). Every slot in the opener used `\s*`, so `:::note`, `:::<TAB>note`, `:::|`, `:::<TAB>[l]` and `::: note<TAB>"T"` all colored as containers where the engines read an ordinary paragraph, and so did `::: {.sidebar}`, where an attribute block cannot ride the opener at all. A tab belongs at the start of a line and nowhere else on one. The bare label keeps its glued form - `:::[l]` does open a div, measured - and the rule is anchored to its own line now, so a container scope no longer runs past the end of it.
- **A caption marker followed by only whitespace is not a caption** (#99). The rule was ` +(.+)$`, so the separator could give a space back to let the content group match, and a marker with nothing after it colored as a caption. The headings rule has carried that guard all along and both sibling ports already had it; this grammar had drifted alone.

### Changed

- **The preview, HTML export and language server run on a released engine, against a released spec** (#99). Both engine bundles are rebuilt from carve-js `37ed8904` - the published 0.1.5 - and the spec pin moves to carve `375e1f37`, the published 0.1.4. The two now agree exactly: 0 of 1538 corpus documents render differently, where the engine this release started from missed 87 of them. carve-lsp stays at `ef1ca246`; only the engine inside it moves.
- **The published vendor address is the project's noreply one** (#95). JetBrains renders the vendor email on the public plugin page, so it is published metadata rather than repository bookkeeping, and it carried a personal address. The marketplace only refreshes on a new version, so the change becomes visible with this release.
- **The minimum IDE is 2025.1** (#106). The plugin required 2024.3 and built on a toolchain that warned on every run it could not build for 2024.2 and later; it now builds on the IntelliJ Platform Gradle Plugin 2.x against 2025.1, so `since-build` is `251`. Anyone still on a 2023 or 2024 IDE keeps 0.1.5 and stops receiving updates. There is still no `until-build`: the plugin claims every train from 2025.1 onward, and the Plugin Verifier runs against 2025.1, 2025.2 and 2025.3 to back that claim.

## [0.1.5] - 2026-08-21

### Added

- **The delimited inline comment `{% ... %}` is highlighted** (#84, markup-carve/carve#1239). It had no rule at all, so the payload read as ordinary text and the markers inside it stayed live. Scoped whole, in paragraphs and in table cells.
- **A bare `::: figure` is a composite figure, not a generic container** (#68, markup-carve/carve#1215). `::: figure "A title"`, `::: figure [g]` and `:::<TAB>figure` are other productions and keep every scope they had. Groups do not nest. The caption after the closing fence stays an over-approximation shared with every Carve TextMate grammar - it sits one line past the closer, outside any begin/end span.
- **The language attribute `{:fr}` is scoped** (#67, markup-carve/carve#1114). An unrecognized attribute item does not degrade gracefully: one bad item left the whole block unscoped, so a span carrying a language lost its attribute coloring entirely. All six sites that spell the item alternation take it, including both glued marker rules.

### Fixed

- **A block opener on a list item's marker line opens the block there.** Seven shapes took no scope, or the wrong one: `- > q` (#75), `- %%%` (#76), `- # h`, `- ---`, `- ::: d`, `- :: term` (#79), and `` - ```js `` with `- |= a |` (#83). The comment fence was the damaging one - it opened no block, left the hidden body live, and ran to the end of the document, graying out every block after the item. All seven now share one marker prefix, so the family accepts and rejects the same marker forms, and each leaves the marker its own list scopes.
- **Opening a `.crv` file no longer fails when JCEF is not on the plugin's class path** (#88). JCEF is its own plugin - `com.intellij.modules.jcef` - rather than part of `com.intellij.modules.platform`, and this plugin declared no dependency on it, so `JBCefBrowser` was missing and `CarvePreviewEditorProvider` threw `NoClassDefFoundError` on PhpStorm 2026.2. The provider hides the default editor, so the file did not open at all. The two preview extension points moved into an optional `carve-jcef.xml` that loads only where JCEF does: without it the text editor opens the file and highlighting, export, templates and the language server are unaffected.
- **The preview, HTML export and language server run on a current engine again** (#70, #72). The vendored bundle rendered 23 of 1317 corpus documents differently where a bundle built from carve-js `main` renders them correctly. Rebuilt from carve-js `5695480e` and carve-lsp `ef1ca246`, with the spec pin moved to carve `b78950f` - the revision carve-js itself pins, so the engine and the corpus agree rather than the engine running ahead of the goldens: 0 of 1330 documents render differently now.
- **An unclosed fence is pinned per fence** (#82). `%%%` degrades to a line comment and hides nothing, while ` ``` `, `~~~` and `:::` run to the end of the document - so bounding the code fence by analogy would be the regression. The comment fence's own case is not fixable in a TextMate grammar: whether the opener is a fence depends on whether a closer appears later, and a begin pattern sees one line.

### Changed

- **The spec corpus is a declared test input** (#71). The shared-corpus tests reach `spec/tests/corpus` by file path, so moving the submodule changed nothing Gradle tracked: a 108-document pin bump left `./gradlew test` `UP-TO-DATE` in 9 seconds having run no test, while `--rerun-tasks` on the same tree failed twice. CI never saw it - a fresh checkout has no build cache - so the one place it bit was a human reviewing a bump locally.
- **The spec pin is current** (#70, #72, #78): 1131 -> 1330 corpus documents. Twenty-five new categories are classified, all SKIP - block context a line-based grammar cannot see, or render-time behavior with no token. Four of them record a MEASURED FALSE POSITIVE instead of a reason to skip, so a golden would pin the wrong answer: empty brace pairs, `{--}` and flag-shaped hyphen runs (#85), the table header marker claiming plain cells (#86), and a boolean attribute starting with an underscore (#87). Two goldens changed, both following upstream edits to the corpus input rather than a grammar regression. Two population guards that could not fail are replaced - the bundle corpus test asserted `>= 500` against 1131, and the corpus script rejected only an entirely absent corpus - and `spec-drift.yml` now gates the pin's AGE at ten days, since distance would be red every morning.

## [0.1.4] - 2026-08-11

### Changed

- **The preview and HTML export render on a current engine again** (#62). The vendored `carve.iife.js` was 363 commits behind and rendered 140 of 690 corpus documents differently - none of them threw, so the preview showed wrong HTML with nothing to indicate it. Rebuilt from carve-js `3d95e948`: 0 of 690. The language server moved to carve-lsp `14320242`, taking its engine to the published 0.1.2 (91 of 690); carve-lsp pins the published package, so the rest needs a carve-js release (markup-carve/carve#608). Two documents still differ, both recorded: one whose golden changed upstream after the spec pin, and one with 100 nested containers that overflows the GraalJS host stack.
- **A bullet glued to an attribute block is a marker, and both marker rules validate the payload** (markup-carve/carve-grammars#126). `-{#x} item` and `-{title="a}b"} item` went uncolored on lines that ARE list items, and three goldens pinned it. The guard requires valid attribute syntax rather than any brace, so `-{+a+} text` stays the paragraph it renders as; identifiers are strict (PART 9 §14). Known limitation: the checkbox after a glued block is not scoped.
- **A mixed-case roman run is not an ordered marker** (markup-carve/carve-grammars#118). One class matched any mixture of cases, so `Vim.`, `Mix.` and `Ix.` colored as lists - the shape of a word starting a sentence. Not a length rule: `mix.`, `ivx.` and `IVX.` do open lists, so the fix is two classes.
- **The annotator highlights a marker glued to an attribute block, the bare dot, and roman runs** (#55). `CarveMarkerScanner` demanded a space straight after the marker and carried neither the bare dot (markup-carve/carve#472) nor roman runs, so the two highlighting paths disagreed on `1.{#x} item`, `. first` and `iv. fourth`.
- **An ordered marker glued to an attribute block needs content after it** (markup-carve/carve-grammars#85). `1.{#x}` renders as a paragraph and `1.{#x} item` as a list item; the old guard was satisfied by any brace, so MARKER REQUIRES CONTENT never reached past the block.
- **A run of spaces is not heading content** (#47). `#` followed by two spaces scoped as a heading, where the engine renders `<p>#</p>`. Found by a shared block battery, now vendored here.
- **A marker alone on its line is prose** (#44, markup-carve/carve#513). The rule was written `\s+` and `\s` matches the line's own newline, so `-`, `1.`, `::` and their spaced forms all scoped as markers.
- **A blockquote marker takes a space** (#42, markup-carve/carve#525). `>no space`, `>>x` and `>\tx` render as paragraphs, and `>>` is not a nested marker. Both the grammar and `CarveMarkerScanner` carried the old rule.
- **The spec submodule is current again** (#38): 392 -> 529 corpus documents. Fifty-five new categories classified, `symbols` and `inline-literal` COVERED and the other 47 SKIP with a reason. Three entries were upstream renames rather than removals.

### Added

- **Syntax highlighting for the inline literal.** A `!` before a verbatim backtick span (`` !`/kaet/` ``) renders its content as prose, so notation that collides with the bare emphasis delimiters needs no per-character escaping. A trailing `{...}` stays a separate attribute block.

### Fixed

- **An orphaned golden can no longer rot unnoticed.** The snapshot test only fails on goldens it looks for, so a renamed category left its `.tokens` file with nothing reading it - three had accumulated. The coverage-matrix test now fails on any golden with no corpus file behind it.
- **Inline footnote content is parsed as inline markup.** `^[...]` was a flat match, so nested emphasis and code did not highlight and `^[a \] b]` terminated the note early. It is a line-bounded `begin`/`end` now, so an unclosed `^[` still cannot leak.
- **An escaped `\^[` is no longer a footnote**, including in a table cell, where the top-level escape rule is not in scope.
- **Table cells highlight footnotes, citations, mentions, tags and symbols**, all of which the row pattern list omitted.
- **A div fence opening on a list marker is highlighted again.** `- ::: note` is corpus-pinned but the rule was anchored past the bullet, so the whole opener fell through to plain text.
- **A definition-list term no longer swallows the rest of its line.** Only the marker is scoped, mirroring the single-colon rule.
- **An unquoted attribute value containing dots keeps its value scope.** In `{k=v.w}` the `.w` scoped as a class; unquoted values are consumed whole, which also fixes `{lang=en-US}`.
- **Unclosed inline literal and math openers no longer leak.** Both were open-ended begin/end pairs, so an unclosed `` !` `` or `$` highlighted every following paragraph. Both are closed-span match rules now, like inline code.
- Table alignment colons in a GFM delimiter row get their own scope, and table cells highlight raw inline, inline literals and math.
- **`checkGrammarDrift` reports far less noise.** It compared prose comments and normalized only one scope-name convention; divergent shared rules dropped from 13 to 7, and the 7 are real. It also stopped reporting two phantom missing features, where upstream splits into rules this grammar folds together - each declared delta must be backed by a fixture, so the declaration cannot hide a genuine gap.
- **The `downloadGrammar` task no longer destroys the committed grammar.** It streamed vscode-carve's grammar straight over the committed copy, rewriting 111 scope names, deleting the three plugin-only rules and clobbering 13 of the 28 shared ones. It is a read-only `checkGrammarDrift` now, failing only on the actionable category.

## [0.1.3] - 2026-07-14

### Added

- **Distinct, customizable colours for structural markers, with a Color Scheme page.** TextMate
  gave every marker (`#`, `-`, `+`, `:::`, `|`, `>`, code fences) the same keyword colour. A new
  annotator now colours each one differently - each default drawn from a semantic scheme colour so
  it matches the active theme (a fence marker takes the code/string colour, and so on) - and every
  colour is editable in Settings | Editor | Color Scheme | Carve. Code, strings, comments, emphasis
  and links are left to TextMate, so files still look familiar.

### Added

- **The preview scrolls in step with the editor.** carve-js stamps each top-level block with
  `data-source-line`, and the preview jumps to the nearest preceding anchor as the editor's
  visible area moves. One-way (editor to preview) - syncing back would need a browser scroll
  listener and risks a feedback loop. Anchors are preview-only, so HTML export stays clean;
  with the carve-php renderer (which cannot emit them) the preview simply does not scroll.

### Fixed

- **Structural markers are highlighted instead of looking like literal text.** The `+` list
  continuation, `::` / `:` definition lists, `:::` divs, list bullets, table pipes, fences and
  every other delimiter were tokenized correctly but scoped under `markup.list.*` and
  `punctuation.definition.*` - roots that IntelliJ's TextMate engine does not map to any
  color, so they rendered as plain text. Re-scoped the markers onto roots IntelliJ actually
  maps (`keyword.operator.*`, `entity.name.*`, `variable.parameter.*`), which takes the
  grammar from 38 colored scopes to 103. A new test pins the rule so a new scope cannot
  silently become invisible again.

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

[Unreleased]: https://github.com/markup-carve/intellij-carve/compare/0.1.6...HEAD
[0.1.6]: https://github.com/markup-carve/intellij-carve/compare/0.1.5...0.1.6
[0.1.5]: https://github.com/markup-carve/intellij-carve/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/markup-carve/intellij-carve/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/markup-carve/intellij-carve/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/markup-carve/intellij-carve/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/markup-carve/intellij-carve/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/markup-carve/intellij-carve/releases/tag/0.1.0
