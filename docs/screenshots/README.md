# Screenshots

Listing screenshots, referenced from the root `README.md` and reused on the
JetBrains Marketplace listing.

| File | Content |
|------|---------|
| `live-preview.png` | Split view (light): highlighted Carve source next to the rendered HTML preview |
| `theme-dark.png` | Same split in dark theme (preview follows IDE theme) |
| `highlighting.png` | Full `examples/sample.crv` with Carve syntax highlighting |

All three show `examples/sample.crv`, so they stay faithful to the plugin.

- **The two split shots** are captured from a running sandbox IDE
  (`./gradlew runIde`, then open `examples/sample.crv`), with the window sized
  to 1450x940 and grabbed 1:1 so nothing is scaled. Both sit at the same
  document position; only the IDE theme differs (Settings → Appearance →
  Theme). The preview follows that theme by itself - `CarvePreviewPanel`
  listens on `EditorColorsManager.TOPIC` and moves `data-theme` on the preview's
  root element - so the theme is the only thing to change between the two.
- **`highlighting.png`** comes from the plugin's TextMate grammar
  (`src/main/resources/textmate/carve.tmLanguage.json`) via Shiki.

`CarvePreviewHtml.create(...)` builds the preview page, and `CarvePreviewHtmlTest`
writes a real one to `build/preview-probe/index.html`. That is handy for checking
the preview's own styling, but a preview-only page is not a substitute for the
split shots: it has neither the source pane nor the IDE chrome.

An optional `settings.png` (Settings → Carve panel) can be added with a manual
capture from a running IDE.
