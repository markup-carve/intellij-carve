# Vendored preview assets

Everything the live preview loads in the browser lives here. Nothing in this
directory is fetched at run time, and the preview references no remote URL at
all - opening a `.crv` file makes **no outbound request** from the IDE.

Before 0.1.6 these six files came from `cdn.jsdelivr.net` on every render. That
meant the preview degraded silently offline (unstyled code, no charts, no math,
no diagrams), and it meant every preview announced itself to a third party from
inside the user's editor. Neither was anything the user opted into.

Do not hand-edit anything here. `tools/vendor-preview-assets.sh` produces this
tree, pins the versions, and regenerates `INDEX`; `CarvePreviewAssetsTest`
checks the tree against `INDEX` by SHA-256, so an edited file fails the build
rather than shipping.

## What is here

| Package | Version | Files | Bytes | Licence |
| --- | --- | --- | --- | --- |
| [highlight.js](https://github.com/highlightjs/highlight.js) | 11.9.0 | `highlight/highlight.min.js`, `highlight/github.min.css`, `highlight/github-dark.min.css` | 124,351 | BSD-3-Clause |
| [Chart.js](https://github.com/chartjs/Chart.js) | 4.5.1 | `chart/chart.umd.min.js` | 208,522 | MIT |
| [MathJax](https://github.com/mathjax/MathJax) | 3.2.2 | `mathjax/tex-mml-chtml.js` + 23 WOFF faces | 1,520,979 | Apache-2.0 |
| [Mermaid](https://github.com/mermaid-js/mermaid) | 11.17.2 | `mermaid/mermaid.min.js` | 3,572,661 | MIT |

Total 5,445,554 bytes across 33 files, licence texts included. All four
licences permit redistribution in binary/compiled form; each package's own
licence text is kept next to its files (`highlight/LICENSE`,
`chart/LICENSE.md`, `mathjax/LICENSE`, `mermaid/LICENSE`) and is shipped with
the plugin, which is what BSD-3-Clause and Apache-2.0 require.

These are the same versions the CDN URLs resolved to when they were replaced -
`highlight.js@11.9.0` was already exact, and `chart.js@4`, `mathjax@3` and
`mermaid@11` resolved to 4.5.1, 3.2.2 and 11.17.2 on 2026-08-27. Moving them
here pinned those numbers; it did not upgrade anything.

## Two details that are load-bearing

**MathJax's font directory.** `output/chtml/fonts/woff-v2/` is not decoration.
The CHTML output resolves its webfonts relative to the URL the bundle itself
was loaded from, so the fonts have to sit under the script in exactly this
layout. Flatten the directory and every glyph silently falls back to a serif
face with the wrong metrics.

**Mermaid's UMD bundle.** `dist/mermaid.min.js` is self-contained; the ESM
builds next to it split diagram types into separately-imported chunks, which
would reintroduce a run-time fetch for anything but a flowchart. The vendoring
script fails if a `import(` ever appears in the bundle it downloaded.
