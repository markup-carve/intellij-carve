# Screenshots

Listing screenshots, referenced from the root `README.md` and reused on the
JetBrains Marketplace listing.

| File | Content |
|------|---------|
| `live-preview.png` | Split view (light): highlighted Carve source next to the rendered HTML preview |
| `theme-dark.png` | Same split in dark theme (preview follows IDE theme) |
| `highlighting.png` | Full `examples/sample.crv` with Carve syntax highlighting |

All three show `examples/sample.crv` in a running sandbox IDE (`./gradlew
runIde`, then open the file), so they stay faithful to the plugin. Size the
window and grab it 1:1 - never scale a capture down, the text goes soft. Grab
the window itself (`xwd -id <window>`), not a screen region: a region grab picks
up whatever happens to be on top.

- **The two split shots** are 1450x940 and sit at the same document position;
  only the IDE theme differs (Settings → Appearance → Theme). The preview
  follows that theme by itself - `CarvePreviewPanel` listens on
  `EditorColorsManager.TOPIC` and moves `data-theme` on the preview's root
  element - so the theme is the only thing to change between the two. Give it a
  second or two to repaint before grabbing.
- **`highlighting.png`** is the whole file in one image, so it outgrows the
  screen. Collapse the preview by dragging the editor splitter fully right, then
  grab the window in four passes, scrolling exactly 48 lines between them (16
  wheel notches at 3 lines each), and stack the passes. Align them on their
  overlap rather than trusting the scroll to be exact; done that way the joins
  do not show. The editor scrollbar column is painted out across the stacked
  region - four scroll positions would otherwise leave four thumbs stacked down
  the right edge.

`CarvePreviewHtml.create(...)` builds the preview page, and `CarvePreviewHtmlTest`
writes a real one to `build/preview-probe/index.html`. That is handy for checking
the preview's own styling, but a preview-only page is not a substitute for the
split shots: it has neither the source pane nor the IDE chrome.

An optional `settings.png` (Settings → Carve panel) can be added with a manual
capture from a running IDE.
