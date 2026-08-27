package org.markupcarve.carve.preview

/**
 * Builds the preview page.
 *
 * A pure function of its arguments, deliberately: the scaffold is the part of
 * the preview that can be checked without an IDE, and the two things most worth
 * checking about it - that it references no remote URL, and that every local
 * URL it does reference resolves to a file that exists - are invisible from
 * inside [CarvePreviewPanel].
 *
 * ### Layers, in the order the browser sees them
 *
 *  1. the highlight.js theme (light or dark, the other one `disabled`)
 *  2. this file's base styles, written in terms of the `--carve-*` tokens
 *  3. `tokens.css` + `recipes.css`, the vendored carve-css layers
 *  4. the user's own CSS
 *
 * The highlight.js theme comes FIRST on purpose. It used to come after, so its
 * `pre code.hljs { background: #fff; padding: 1em }` painted a second, differently
 * shaded rectangle inside the `<pre>`'s own background - the "quite the border"
 * the code blocks were reported for was that inset frame, not a border property.
 * With the theme underneath, the one rule below that zeroes the padding and
 * background wins on document order, and a code block is one flat surface.
 *
 * ### Colour
 *
 * Every colour here resolves through a token from `tokens.css`. There used to be
 * a second, hardcoded palette (`#2c3e50`, `#3498db`, `#f4f4f4`, `#bdc3c7`) plus a
 * parallel `body.dark` block that restated it, and the tokens were injected but
 * never referenced - so the document carried two palettes that disagreed, and
 * the dark one had to be maintained by hand. Dark mode now falls out of
 * `data-theme` on the root element, which is the mechanism `tokens.css` already
 * defines.
 */
object CarvePreviewHtml {

    /**
     * @param initialHtml rendered document body
     * @param isDark      which highlight.js theme to enable, from the editor colour scheme
     * @param assetBase   `file://` URL of the unpacked [CarvePreviewAssets] root, trailing slash
     * @param carveCss    the vendored carve-css layers
     * @param userCss     the user's own CSS, injected last so equal-specificity rules win
     * @param copyBridge  body of `window.carveCopyText(text, onDone)`, or empty when the page
     *                    has no IDE bridge (the button then falls back to the browser clipboard).
     *                    `onDone(ok)` must be called once per request - it is a parameter rather
     *                    than a global so two quick clicks cannot answer each other's callback.
     */
    fun create(
        initialHtml: String,
        isDark: Boolean,
        assetBase: String,
        carveCss: String = "",
        userCss: String = "",
        copyBridge: String = "",
    ): String {
        val themeClass = if (isDark) "dark" else "light"
        val userStyle = if (userCss.isBlank()) "" else "<style id=\"carve-user-css\">\n$userCss\n</style>"
        val bridgeScript = if (copyBridge.isBlank()) {
            ""
        } else {
            "<script>\n        window.carveCopyText = function (text, onDone) {\n            $copyBridge\n        };\n    </script>"
        }
        return """
<!DOCTYPE html>
<html data-theme="$themeClass">
<head>
    <meta charset="UTF-8">
    <link id="hljs-light" rel="stylesheet"
        href="${assetBase}${CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS}"${if (isDark) " disabled" else ""}>
    <link id="hljs-dark" rel="stylesheet"
        href="${assetBase}${CarvePreviewAssets.HIGHLIGHT_DARK_CSS}"${if (!isDark) " disabled" else ""}>
    <style>
        * { box-sizing: border-box; }
        /* --carve-font-body is `inherit` by default, so the stack lives on the
           root and a consumer overriding the token still wins. */
        html {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
        }
        body {
            font-family: var(--carve-font-body);
            line-height: var(--carve-line-height);
            padding: 20px;
            max-width: 820px;
            margin: 0 auto;
            color: var(--carve-ink);
            background: var(--carve-surface);
        }

        /* Headings carry their hierarchy in size and weight. The old scaffold
           underlined h1 with 2px of accent and h2 with 1px of grey, which reads
           as a 2015 web page rather than as an IDE panel. */
        h1, h2, h3, h4, h5, h6 {
            font-family: var(--carve-font-heading);
            color: var(--carve-ink);
            line-height: 1.25;
        }
        h1 {
            margin-top: 0;
            padding-bottom: var(--carve-space-2);
            border-bottom: var(--carve-border-width) solid var(--carve-rule);
        }
        h2 { margin-top: var(--carve-space-6); }
        h4, h5, h6 { color: var(--carve-ink-soft); }

        code { font-family: var(--carve-font-mono); font-size: 0.9em; }
        :not(pre) > code {
            background: var(--carve-sunk);
            padding: 0.1em 0.35em;
            border-radius: var(--carve-radius);
        }
        pre {
            background: var(--carve-sunk);
            border: 0;
            border-radius: var(--carve-radius);
            padding: var(--carve-space-3) var(--carve-space-4);
            overflow-x: auto;
            font-size: 0.9em;
            line-height: 1.5;
            margin: var(--carve-space-4) 0;
        }
        /* The one rule that keeps a code block a single flat surface: the
           highlight.js theme paints its own background and 1em of padding on
           `pre code.hljs`, and two stacked surfaces of slightly different shades
           is what read as a heavy frame. Colours still come from the theme. */
        pre code, pre code.hljs {
            background: none;
            padding: 0;
            font-size: inherit;
            display: block;
        }

        blockquote {
            border-left: var(--carve-accent-width) solid var(--carve-rule);
            margin: var(--carve-space-4) 0;
            padding: 0 0 0 var(--carve-space-4);
            color: var(--carve-ink-soft);
        }
        blockquote p { margin: 0; }
        table { border-collapse: collapse; width: 100%; margin: var(--carve-space-4) 0; }
        th, td {
            border: var(--carve-border-width) solid var(--carve-rule);
            padding: var(--carve-space-2) var(--carve-space-3);
            text-align: left;
        }
        th { background: var(--carve-sunk); font-weight: 600; }
        mark {
            background: var(--carve-warn-wash);
            color: inherit;
            padding: 0.1em 0.25em;
            border-radius: var(--carve-radius);
        }
        del { color: var(--carve-danger); text-decoration: line-through; }
        ins {
            color: var(--carve-success);
            text-decoration: none;
            border-bottom: var(--carve-border-width) solid var(--carve-success);
        }
        a { color: var(--carve-accent); text-decoration: none; }
        a:hover { text-decoration: underline; }
        img { max-width: 100%; height: auto; }
        hr {
            border: 0;
            border-top: var(--carve-border-width) solid var(--carve-rule);
            margin: var(--carve-space-6) 0;
        }
        ul, ol { padding-left: 2em; }
        li { margin: var(--carve-space-1) 0; }
        li:has(> input[type="checkbox"]) { list-style: none; margin-left: -1.5em; }
        li > input[type="checkbox"] { margin-right: 0.5em; width: 1em; height: 1em; vertical-align: middle; }
        sup, sub { font-size: 0.75em; }
        dl { margin: var(--carve-space-4) 0; }
        dt { font-weight: bold; margin-top: var(--carve-space-3); }
        dd { margin: 0 0 0 2em; }
        figure { margin: var(--carve-space-4) 0; text-align: center; }
        figure img { display: block; margin: 0 auto; }
        figcaption, table caption {
            font-size: var(--carve-font-size-small);
            color: var(--carve-ink-soft);
        }
        figcaption { margin-top: var(--carve-space-2); }
        table caption { caption-side: bottom; padding-top: var(--carve-space-2); }
        abbr[title] { text-decoration: underline dotted; cursor: help; }
        .mention strong, .tag strong { font-weight: 600; }
        .mention, .tag {
            display: inline-block;
            padding: 0 4px;
            border-radius: var(--carve-radius);
            font-size: 0.95em;
        }
        .mention { background: var(--carve-accent-soft); color: var(--carve-accent-ink); }
        .tag { background: var(--carve-success-wash); color: var(--carve-success); }
        /* Footnotes (djot doc-* roles) */
        [role="doc-endnotes"] {
            margin-top: var(--carve-space-6);
            font-size: var(--carve-font-size-small);
            color: var(--carve-ink-soft);
        }
        [role="doc-endnotes"] hr { margin-bottom: var(--carve-space-4); }
        [role="doc-noteref"] { text-decoration: none; }
        [role="doc-backlink"] { text-decoration: none; margin-left: 0.4em; }
        /* Admonitions: aside.admonition.{type}; generic custom types render as div */
        .admonition {
            margin: var(--carve-space-4) 0;
            padding: var(--carve-space-3) var(--carve-space-4);
            border-left: var(--carve-accent-width) solid var(--carve-info);
            border-radius: var(--carve-radius);
            background: var(--carve-info-wash);
        }
        .admonition > :first-child { margin-top: 0; }
        .admonition > :last-child { margin-bottom: 0; }
        .admonition-title { font-weight: 700; margin: 0 0 0.4em; }
        .admonition.note,    .admonition.info    { border-color: var(--carve-info);    background: var(--carve-info-wash); }
        .admonition.tip,     .admonition.success { border-color: var(--carve-success); background: var(--carve-success-wash); }
        .admonition.warning                      { border-color: var(--carve-warn);    background: var(--carve-warn-wash); }
        .admonition.danger                       { border-color: var(--carve-danger);  background: var(--carve-danger-wash); }
        .admonition.example                      { border-color: var(--carve-accent);  background: var(--carve-accent-soft); }
        .admonition.quote                        { border-color: var(--carve-neutral); background: var(--carve-neutral-wash); }
        /* Math spans rendered by MathJax */
        .math.display { display: block; text-align: center; margin: var(--carve-space-4) 0; }
        /* Featured: emphasized block (e.g. a heading carrying {.featured}) */
        .featured {
            background: linear-gradient(90deg, var(--carve-accent-soft), transparent);
            border-left: var(--carve-accent-width) solid var(--carve-accent);
            padding: 0.3em 0.6em;
            border-radius: var(--carve-radius);
        }
        /* Status classes (carve [text]{.class} inline spans) */
        .error { color: var(--carve-danger); font-weight: 600; }
        .success { color: var(--carve-success); font-weight: 600; }
        .warn { color: var(--carve-warn); font-weight: 600; }
        li:has(> .error), li:has(> input + .error) {
            background: var(--carve-danger-wash);
            border-radius: var(--carve-radius);
        }
        li:has(> .success), li:has(> input + .success) {
            background: var(--carve-success-wash);
            border-radius: var(--carve-radius);
        }

        /* ---- Code-block chrome ----
           The hydrate JS wraps every `pre > code` in `.carve-code` and hangs a
           small tool strip off it: the language name and the copy button. They
           belong to the WRAPPER, not the `<pre>` - the `<pre>` is the scroll
           box, and anything positioned inside it slides out of view with a long
           line. When the block has a #201 header bar, the strip moves into the
           bar instead of floating over the code. */
        .carve-code { position: relative; margin: var(--carve-space-4) 0; }
        .carve-code > pre { margin: 0; }
        .carve-code > .carve-code-tools {
            position: absolute;
            top: var(--carve-space-1);
            right: var(--carve-space-1);
            z-index: 1;
            /* The strip floats over the first line of code, so it carries the
               code block's own surface - otherwise a long first line runs under
               the icon. Inside a header bar it needs none of this, which is why
               the rule is scoped to the floating case. */
            background: var(--carve-sunk);
            border-radius: var(--carve-radius);
            padding: 0 var(--carve-space-1) 0 var(--carve-space-2);
        }
        .carve-code-tools {
            display: flex;
            align-items: center;
            gap: var(--carve-space-2);
        }
        .carve-lang {
            font-family: var(--carve-font-mono);
            font-size: 0.7em;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.03em;
            color: var(--carve-ink-soft);
            opacity: 0.7;
            pointer-events: none;
        }

        /* Visible at rest, not hover-only: a copy button nobody can find is
           the same as no copy button. Faint enough not to compete with the
           code, solid the moment the pointer is anywhere near it. */
        .carve-copy {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 3px;
            color: var(--carve-ink-soft);
            background: transparent;
            border: 0;
            border-radius: var(--carve-radius);
            cursor: pointer;
            opacity: 0.45;
            transition: opacity 0.12s ease, color 0.12s ease, background-color 0.12s ease;
        }
        .carve-copy > svg { display: block; width: 14px; height: 14px; }
        .carve-code:hover > .carve-code-tools > .carve-copy,
        .code-header:hover .carve-copy,
        .carve-copy:focus-visible { opacity: 1; }
        .carve-copy:hover { color: var(--carve-ink); background: var(--carve-surface); opacity: 1; }
        .carve-copy[data-state="done"] { opacity: 1; color: var(--carve-success); }
        .carve-copy[data-state="failed"] { opacity: 1; color: var(--carve-danger); }

        /* #201 header caption bar (a code-block filename/title). A rule under
           the bar rather than a box around the whole block. */
        .carve-code > .code-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--carve-space-3);
            font-family: var(--carve-font-mono);
            font-size: 0.8em;
            font-weight: 600;
            color: var(--carve-ink-soft);
            background: var(--carve-sunk);
            border-bottom: var(--carve-border-width) solid var(--carve-rule);
            border-radius: var(--carve-radius) var(--carve-radius) 0 0;
            padding: var(--carve-space-1) var(--carve-space-2) var(--carve-space-1) var(--carve-space-4);
        }
        .carve-code:has(> .code-header) > pre { border-radius: 0 0 var(--carve-radius) var(--carve-radius); }

        /* Heading permalinks (the headingPermalinks extension emits
           `<a class="permalink">`). One pilcrow per heading, always on, reads as
           noise on a document that is mostly headings - so it appears when the
           heading is under the pointer or the link itself is focused. */
        .permalink {
            margin-left: 0.35em;
            font-weight: 400;
            color: var(--carve-ink-soft);
            text-decoration: none;
            opacity: 0;
            transition: opacity 0.12s ease;
        }
        :is(h1, h2, h3, h4, h5, h6):hover > .permalink,
        .permalink:focus-visible { opacity: 1; }

        /* ---- Code group / tabs (codeGroup + tabs extensions) ----
           Both emit CSS-only radio-tab widgets: a run of
           `input.{code-group,tabs}-radio` + `label.*-label`, then the panels.
           `:checked ~ nth-of-type` wires each radio to its label + panel, so tab
           switching needs no JS. Mirrors the docs custom.css. */
        .code-group, .tabs { margin: var(--carve-space-3) 0; position: relative; }
        .code-group-radio, .tabs-radio {
            position: absolute; top: 0; left: 0; width: 1px; height: 1px;
            opacity: 0; pointer-events: none;
        }
        .code-group-label, .tabs-label {
            display: inline-block; cursor: pointer; padding: 4px 12px; font-size: 0.85em;
            color: var(--carve-ink-soft);
            border: var(--carve-border-width) solid transparent; border-bottom: none;
            border-radius: var(--carve-radius) var(--carve-radius) 0 0;
        }
        .code-group-panel, .tabs-panel {
            display: none;
            border: var(--carve-border-width) solid var(--carve-rule);
            border-radius: 0 var(--carve-radius) var(--carve-radius) var(--carve-radius);
            padding: 0 12px;
        }
        .code-group-panel > .carve-code, .tabs-panel > .carve-code { margin: 0.6em 0; }
        /* Up to 8 tabs per widget. */
        .code-group-radio:nth-of-type(1):checked ~ .code-group-label:nth-of-type(1),
        .tabs-radio:nth-of-type(1):checked ~ .tabs-label:nth-of-type(1) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(1):checked ~ .code-group-panel:nth-of-type(1),
        .tabs-radio:nth-of-type(1):checked ~ .tabs-panel:nth-of-type(1) { display: block; }
        .code-group-radio:nth-of-type(2):checked ~ .code-group-label:nth-of-type(2),
        .tabs-radio:nth-of-type(2):checked ~ .tabs-label:nth-of-type(2) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(2):checked ~ .code-group-panel:nth-of-type(2),
        .tabs-radio:nth-of-type(2):checked ~ .tabs-panel:nth-of-type(2) { display: block; }
        .code-group-radio:nth-of-type(3):checked ~ .code-group-label:nth-of-type(3),
        .tabs-radio:nth-of-type(3):checked ~ .tabs-label:nth-of-type(3) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(3):checked ~ .code-group-panel:nth-of-type(3),
        .tabs-radio:nth-of-type(3):checked ~ .tabs-panel:nth-of-type(3) { display: block; }
        .code-group-radio:nth-of-type(4):checked ~ .code-group-label:nth-of-type(4),
        .tabs-radio:nth-of-type(4):checked ~ .tabs-label:nth-of-type(4) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(4):checked ~ .code-group-panel:nth-of-type(4),
        .tabs-radio:nth-of-type(4):checked ~ .tabs-panel:nth-of-type(4) { display: block; }
        .code-group-radio:nth-of-type(5):checked ~ .code-group-label:nth-of-type(5),
        .tabs-radio:nth-of-type(5):checked ~ .tabs-label:nth-of-type(5) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(5):checked ~ .code-group-panel:nth-of-type(5),
        .tabs-radio:nth-of-type(5):checked ~ .tabs-panel:nth-of-type(5) { display: block; }
        .code-group-radio:nth-of-type(6):checked ~ .code-group-label:nth-of-type(6),
        .tabs-radio:nth-of-type(6):checked ~ .tabs-label:nth-of-type(6) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(6):checked ~ .code-group-panel:nth-of-type(6),
        .tabs-radio:nth-of-type(6):checked ~ .tabs-panel:nth-of-type(6) { display: block; }
        .code-group-radio:nth-of-type(7):checked ~ .code-group-label:nth-of-type(7),
        .tabs-radio:nth-of-type(7):checked ~ .tabs-label:nth-of-type(7) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(7):checked ~ .code-group-panel:nth-of-type(7),
        .tabs-radio:nth-of-type(7):checked ~ .tabs-panel:nth-of-type(7) { display: block; }
        .code-group-radio:nth-of-type(8):checked ~ .code-group-label:nth-of-type(8),
        .tabs-radio:nth-of-type(8):checked ~ .tabs-label:nth-of-type(8) { color: var(--carve-ink); background: var(--carve-surface); border-color: var(--carve-rule); }
        .code-group-radio:nth-of-type(8):checked ~ .code-group-panel:nth-of-type(8),
        .tabs-radio:nth-of-type(8):checked ~ .tabs-panel:nth-of-type(8) { display: block; }

        /* ---- Details / spoiler (details + spoiler extensions) ----
           details: native `<details><summary>`. spoiler: inline
           `<span class="spoiler">` (blur, JS toggle) and block
           `<details class="spoiler">` (native). Mirrors the docs custom.css. */
        details {
            margin: var(--carve-space-4) 0;
            border: var(--carve-border-width) solid var(--carve-rule);
            border-radius: var(--carve-radius);
            padding: 2px 14px;
        }
        details > summary { cursor: pointer; font-weight: 600; padding: 6px 0; }
        details[open] > summary { margin-bottom: 4px; }
        details > :last-child { margin-bottom: 8px; }

        span.spoiler {
            filter: blur(0.3em); cursor: pointer; border-radius: var(--carve-radius); padding: 0 0.15em;
            background: rgba(127, 127, 127, 0.14);
            -webkit-user-select: none; user-select: none; transition: filter 0.2s;
        }
        span.spoiler.revealed { filter: none; background: transparent; user-select: text; }
        details.spoiler { border-left: var(--carve-accent-width) solid var(--carve-warn); }
        details.spoiler > summary { color: var(--carve-warn); list-style: none; }
        details.spoiler > summary::-webkit-details-marker { display: none; }
        details.spoiler > summary::before { content: '\01F441 '; }
        details.spoiler > summary::after {
            content: ' (click to reveal)';
            color: var(--carve-ink-soft);
            font-weight: 400;
            font-size: 0.85em;
        }
        details.spoiler[open] > summary::after { content: ''; }

        /* ---- Mermaid: emitted as `<pre class="mermaid">DEF</pre>`; the hydrate
           JS replaces it with the rendered SVG inside `.mermaid-rendered`. ---- */
        pre.mermaid { background: none; padding: 0; text-align: center; }
        .mermaid-rendered { margin: var(--carve-space-4) 0; text-align: center; }
        .mermaid-rendered svg { max-width: 100%; height: auto; }

        /* ---- Chart: emitted as `<div class="chart"><script type=application/json>`;
           the hydrate JS swaps in a <canvas>. ---- */
        div.chart { max-width: 560px; margin: var(--carve-space-4) 0; }
        div.chart > script { display: none; }

        #content { min-height: 100px; }
    </style>
    <script src="${assetBase}${CarvePreviewAssets.HIGHLIGHT_JS}"></script>
    <script>
        window.MathJax = {
            tex: { inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']] },
            options: { skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'] }
        };
    </script>
    <script async src="${assetBase}${CarvePreviewAssets.MATHJAX_JS}"></script>
    <script src="${assetBase}${CarvePreviewAssets.MERMAID_JS}"></script>
    <script>
        // Mermaid auto-runs on window load and rewrites `pre.mermaid` in place.
        // Left alone it races hydrate(): whichever loses re-renders the other's
        // output and throws UnknownDiagramError on it. Turning startOnLoad off
        // the moment the bundle is parsed leaves exactly one renderer.
        if (typeof mermaid !== 'undefined') { mermaid.initialize({ startOnLoad: false }); }
    </script>
    <script src="${assetBase}${CarvePreviewAssets.CHART_JS}"></script>
    <style id="carve-css">
$carveCss
    </style>
    $userStyle
    $bridgeScript
</head>
<body class="$themeClass">
    <div id="content" class="carve">$initialHtml</div>
    <script>
        var chartInstances = [];

        // A single hydrate() pass, run BOTH on initial load AND after every
        // updateContentHtml() innerHTML swap. Anything done only once at page
        // load would not apply to edited content, so every extension's client
        // runtime is re-run here against the freshly-swapped markup.
        function updateContentHtml(html) {
            document.getElementById('content').innerHTML = html;
            hydrate();
        }

        function root() { return document.getElementById('content'); }

        function isDark() { return document.documentElement.getAttribute('data-theme') === 'dark'; }

        function readToken(name) {
            return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        }

        // Scroll sync: carve-js stamps every top-level block with data-source-line (1-based),
        // so we scroll to the last block that starts at or before the editor's top visible
        // line. Called from the editor's VisibleAreaListener. A no-op when the markup has no
        // anchors (e.g. the carve-php renderer, which cannot emit them).
        window.carveScrollToLine = function (line) {
            var nodes = root().querySelectorAll('[data-source-line]');
            if (!nodes.length) return;
            var target = null;
            for (var i = 0; i < nodes.length; i++) {
                if (parseInt(nodes[i].getAttribute('data-source-line'), 10) <= line) {
                    target = nodes[i];
                } else {
                    break;
                }
            }
            if (target) {
                target.scrollIntoView({ block: 'start' });
            } else {
                window.scrollTo(0, 0);
            }
        };

        function hydrate() {
            wrapCodeBlocks();
            wireSpoilers();
            highlightCode();
            renderMermaid();
            renderCharts();
            typesetMath();
        }

        // Every `pre > code` gets a `.carve-code` wrapper carrying the language
        // badge, the optional #201 header bar and the copy button. They hang off
        // the wrapper rather than the <pre> because the <pre> is the scroll box:
        // anything positioned inside it slides out of view with a long line.
        function wrapCodeBlocks() {
            root().querySelectorAll('pre > code').forEach(function (code) {
                var pre = code.parentElement;
                if (!pre || !pre.parentNode) return;
                if (pre.parentElement && pre.parentElement.classList.contains('carve-code')) return;

                var wrap = document.createElement('div');
                wrap.className = 'carve-code';

                var cls = Array.prototype.find.call(code.classList, function (c) {
                    return c.indexOf('language-') === 0;
                });
                var lang = cls ? cls.slice('language-'.length) : '';
                if (lang) wrap.dataset.lang = lang;

                pre.parentNode.insertBefore(wrap, pre);

                // #201 quoted header: carve-js puts it on `pre[title]`. Kept as
                // `.code-with-header` as well, because user CSS targets it.
                var title = pre.getAttribute('title');
                var header = null;
                if (title) {
                    wrap.classList.add('code-with-header');
                    header = document.createElement('div');
                    header.className = 'code-header';
                    var name = document.createElement('span');
                    name.textContent = title;
                    header.appendChild(name);
                    wrap.appendChild(header);
                }

                if (lang !== 'mermaid') {
                    var tools = document.createElement('div');
                    tools.className = 'carve-code-tools';
                    if (lang) {
                        var badge = document.createElement('span');
                        badge.className = 'carve-lang';
                        badge.textContent = lang;
                        tools.appendChild(badge);
                    }
                    tools.appendChild(copyButton(code));
                    // With a header bar there is a strip of chrome already; the
                    // tools belong in it rather than floating over the first
                    // line of code.
                    (header || wrap).appendChild(tools);
                }
                wrap.appendChild(pre);
            });
        }

        var ICONS = {
            copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
                'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                '<rect x="9" y="9" width="13" height="13" rx="2"></rect>' +
                '<path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>',
            done: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" ' +
                'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                '<polyline points="20 6 9 17 4 12"></polyline></svg>',
            failed: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" ' +
                'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                '<line x1="18" y1="6" x2="6" y2="18"></line>' +
                '<line x1="6" y1="6" x2="18" y2="18"></line></svg>'
        };

        function copyButton(code) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'carve-copy';
            button.innerHTML = ICONS.copy;
            button.title = 'Copy code';
            button.setAttribute('aria-label', 'Copy code to the clipboard');
            button.addEventListener('click', function () {
                // The <code> text, never the wrapper's: the wrapper also holds
                // the header bar and this button, and both would be copied.
                copyText(code.textContent || '').then(function (how) {
                    flash(button, how ? 'done' : 'failed', how ? 'Copied' : 'Copy failed');
                });
            });
            return button;
        }

        var flashTimers = new WeakMap();
        function flash(button, state, label) {
            button.dataset.state = state;
            button.innerHTML = ICONS[state];
            button.title = label;
            clearTimeout(flashTimers.get(button));
            flashTimers.set(button, setTimeout(function () {
                delete button.dataset.state;
                button.innerHTML = ICONS.copy;
                button.title = 'Copy code';
            }, 1400));
        }

        // Three paths, most reliable first.
        //
        //  1. carveCopyText - a JBCefJSQuery bridge into the IDE, which sets the
        //     real IDE clipboard through CopyPasteManager. No permission is
        //     involved, and the text lands where a JetBrains user expects it.
        //  2. navigator.clipboard - present under JCEF (a file:// page IS a
        //     secure context in Chromium) but gated on a permission that CEF's
        //     default handler can refuse, so its rejection has to be caught.
        //  3. document.execCommand('copy') - the pre-permission path, still
        //     available on a user gesture, which a button click is.
        //
        // Returns the name of whichever one worked, or '' if none did. The empty
        // string is what turns the button red: a copy button that quietly does
        // nothing is worse than no copy button.
        function copyText(text) {
            if (typeof window.carveCopyText === 'function') {
                return new Promise(function (resolve) {
                    // settled guards the fallback as much as the promise: resolve()
                    // is idempotent, but legacyCopy() is not - it would write the
                    // clipboard a second time a second after the bridge succeeded.
                    var settled = false;
                    function finish(how) {
                        if (settled) return;
                        settled = true;
                        resolve(how);
                    }
                    try {
                        // The callback is passed IN, not parked on window: two
                        // buttons clicked before the first round trip returns
                        // would otherwise share one handler, and the first
                        // answer would light up the second button.
                        window.carveCopyText(text, function (ok) {
                            finish(ok ? 'bridge' : legacyCopy(text));
                        });
                    } catch (e) {
                        finish(legacyCopy(text));
                    }
                    // The bridge is a round trip through the IDE. If it never
                    // answers, fall back rather than leaving the button blank.
                    setTimeout(function () { if (!settled) finish(legacyCopy(text)); }, 1000);
                });
            }
            if (navigator.clipboard && navigator.clipboard.writeText) {
                return navigator.clipboard.writeText(text)
                    .then(function () { return 'clipboard'; })
                    .catch(function () { return legacyCopy(text); });
            }
            return Promise.resolve(legacyCopy(text));
        }

        function legacyCopy(text) {
            var area = document.createElement('textarea');
            area.value = text;
            area.setAttribute('readonly', '');
            area.style.position = 'fixed';
            area.style.top = '-1000px';
            document.body.appendChild(area);
            var ok = false;
            try {
                area.select();
                ok = document.execCommand('copy');
            } catch (e) {
                ok = false;
            }
            document.body.removeChild(area);
            return ok ? 'execCommand' : '';
        }

        // Inline `<span class="spoiler">` reveals on click/keyboard. The block
        // form `<details class="spoiler">` is native and needs no JS.
        function wireSpoilers() {
            root().querySelectorAll('span.spoiler').forEach(function (el) {
                if (el.dataset.spoilerWired) return;
                el.dataset.spoilerWired = '1';
                el.tabIndex = 0;
                el.setAttribute('role', 'button');
                el.title = 'Click to reveal';
                function toggle() { el.classList.toggle('revealed'); }
                el.addEventListener('click', toggle);
                el.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
                });
            });
        }

        function highlightCode() {
            if (typeof hljs === 'undefined') return;
            root().querySelectorAll('pre > code[class*="language-"]').forEach(function (block) {
                if (block.classList.contains('language-mermaid')) return;
                hljs.highlightElement(block);
            });
        }

        // Mermaid: `<pre class="mermaid">DEF</pre>` -> rendered SVG. Theme-aware.
        var mermaidSeq = 0;
        function renderMermaid() {
            if (typeof mermaid === 'undefined') return;
            var blocks = root().querySelectorAll('pre.mermaid, pre > code.language-mermaid');
            if (!blocks.length) return;
            mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: isDark() ? 'dark' : 'default' });
            Array.prototype.forEach.call(blocks, function (el) {
                var pre = el.tagName === 'PRE' ? el : el.parentElement;
                if (!pre) return;
                var host = pre.parentElement && pre.parentElement.classList.contains('carve-code')
                    ? pre.parentElement
                    : pre;
                var def = el.textContent || '';
                try {
                    mermaid.render('carve-mermaid-' + (mermaidSeq++), def).then(function (res) {
                        var fig = document.createElement('div');
                        fig.className = 'mermaid-rendered';
                        fig.innerHTML = res.svg;
                        if (host.parentNode) host.replaceWith(fig);
                    }).catch(function () {});
                } catch (e) { /* leave the raw block on a parse error */ }
            });
        }

        // Chart.js: `<div class="chart"><script type=application/json>CONFIG</` +
        // `script></div>` -> a <canvas>. Old instances are destroyed first to
        // avoid leaks / duplicate canvases on re-hydrate.
        var chartSeq = 0;
        function renderCharts() {
            if (typeof Chart === 'undefined') return;
            // Chart.js draws its labels, ticks and grid in a fixed near-black.
            // On a dark IDE theme that is text on its own colour. Now that the
            // page has tokens, hand it the same ink and rules everything else
            // uses; it has to be re-read per hydrate because the theme moves.
            //
            // Only the text and the grid. Chart.js 4 picks the series colours
            // with its built-in `colors` plugin, and that plugin stands down as
            // soon as it sees a colour already defined - setting
            // Chart.defaults.borderColor turned every bar into the default
            // 10%-black wash, which on a dark ground is an invisible chart.
            var ink = readToken('--carve-ink');
            var rule = readToken('--carve-rule');
            if (ink) { Chart.defaults.color = ink; }
            if (rule && Chart.defaults.scale && Chart.defaults.scale.grid) {
                Chart.defaults.scale.grid.color = rule;
            }
            while (chartInstances.length) {
                var inst = chartInstances.pop();
                try { inst.destroy(); } catch (e) {}
            }
            root().querySelectorAll('div.chart').forEach(function (el) {
                var script = el.querySelector('script[type="application/json"]');
                if (!script) return;
                var config;
                try { config = JSON.parse(script.textContent || ''); } catch (e) { return; }
                var canvas = document.createElement('canvas');
                canvas.id = 'carve-chart-' + (chartSeq++);
                el.replaceChildren(canvas);
                try { chartInstances.push(new Chart(canvas, config)); } catch (e) {}
            });
        }

        function typesetMath() {
            if (window.MathJax && MathJax.typesetPromise) {
                if (MathJax.typesetClear) { MathJax.typesetClear(); }
                MathJax.typesetPromise([root()]);
            }
        }

        document.addEventListener('DOMContentLoaded', hydrate);
        if (document.readyState !== 'loading') { hydrate(); }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
