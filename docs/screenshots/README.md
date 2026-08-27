# Screenshots

Listing screenshots, referenced from the root `README.md` and reused on the
JetBrains Marketplace listing.

| File | Content |
|------|---------|
| `live-preview.png` | Split view (light): highlighted Carve source next to the rendered HTML preview |
| `theme-dark.png` | Same split in dark theme (preview follows IDE theme) |
| `highlighting.png` | Full `examples/sample.crv` with Carve syntax highlighting |

These are generated from the project's own assets, so they stay faithful to the
plugin and are reproducible:

- **Preview** = `examples/sample.crv` rendered by the bundled carve-js renderer,
  wrapped in the plugin's exact preview page. That page is built by
  `CarvePreviewHtml.create(...)` (it moved out of `CarvePreviewPanel.kt` in
  0.1.6), and `CarvePreviewHtmlTest` already writes a real one to
  `build/preview-probe/index.html` - swapping the document body into that
  file is the shortest path to a faithful shot.
- **Source highlighting** = the plugin's TextMate grammar
  (`src/main/resources/textmate/carve.tmLanguage.json`) via Shiki.

An optional `settings.png` (Settings → Carve panel) can be added with a manual
capture from a running IDE - it is the one shot that needs the live UI.

**These two are stale as of 0.1.6.** The preview moved to the Carve design
tokens, dropped the blue heading and quote rules, flattened the code blocks and
gained a copy button, so `live-preview.png` and `theme-dark.png` still show the
older look. Both are split-editor shots, which need the live UI to retake.
