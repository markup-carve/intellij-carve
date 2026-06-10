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
  wrapped in the plugin's exact preview CSS (extracted from
  `CarvePreviewPanel.kt`) inside an iframe.
- **Source highlighting** = the plugin's TextMate grammar
  (`src/main/resources/textmate/carve.tmLanguage.json`) via Shiki.

An optional `settings.png` (Settings → Carve panel) can be added with a manual
capture from a running IDE - it is the one shot that needs the live UI.
