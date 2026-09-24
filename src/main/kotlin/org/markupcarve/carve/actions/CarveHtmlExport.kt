package org.markupcarve.carve.actions

import org.markupcarve.carve.preview.CarveCodeHighlight
import org.markupcarve.carve.preview.CarvePreviewAssets

/**
 * The standalone page written by [ExportHtmlAction].
 *
 * highlight.js, the Carve grammar and the light theme are inlined rather than
 * linked: the file has to work wherever it is copied, and the plugin's asset
 * extraction directory is neither portable nor stable across plugin updates.
 */
object CarveHtmlExport {

    fun page(title: String, content: String): String {
        return """<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <style>
${inlineStyle(CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS))}
    </style>
    <style>
${inlineStyle(CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_TABLE_CSS))}
    </style>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            max-width: 820px;
            margin: 0 auto;
            padding: 20px;
            color: #333;
        }
        h1 { border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        h2 { border-bottom: 1px solid #ddd; padding-bottom: 5px; }
        code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px;
            font-family: 'JetBrains Mono', Consolas, monospace; }
        pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
        pre code, pre code.hljs { background: none; padding: 0; }
        blockquote { border-left: 4px solid #3498db; margin: 1em 0; padding: 0.5em 0 0.5em 20px; color: #666; }
        table { border-collapse: collapse; width: 100%; margin: 1em 0; }
        th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
        th { background: #f8f9fa; }
        table caption { caption-side: bottom; font-size: 0.9em; color: #666; padding-top: 0.5em; }
        mark { background: #fff3cd; }
        del { color: #dc3545; }
        ins { color: #28a745; text-decoration: none; border-bottom: 1px solid #28a745; }
        img { max-width: 100%; height: auto; }
        figure { margin: 1em 0; text-align: center; }
        figcaption { font-size: 0.9em; color: #666; margin-top: 0.5em; }
        dt { font-weight: bold; margin-top: 0.75em; }
        dd { margin: 0 0 0 2em; }
        abbr[title] { text-decoration: underline dotted; cursor: help; }
        .mention, .tag { display: inline-block; padding: 0 4px; border-radius: 4px; font-size: 0.95em; }
        .mention { background: #e7f0fb; color: #1c5fb4; }
        .tag { background: #eef3e7; color: #4a7a18; }
        [role="doc-endnotes"] { margin-top: 2em; font-size: 0.9em; color: #555; }
        [role="doc-noteref"], [role="doc-backlink"] { text-decoration: none; }
        [role="doc-backlink"] { margin-left: 0.4em; }
        .admonition { margin: 1em 0; padding: 0.75em 1em; border-left: 4px solid #3498db;
            border-radius: 4px; background: #f4f8fd; }
        .admonition > :first-child { margin-top: 0; }
        .admonition > :last-child { margin-bottom: 0; }
        .admonition-title { font-weight: 700; margin: 0 0 0.4em; }
        .admonition.tip, .admonition.success { border-color: #2ecc71; background: #f2fbf5; }
        .admonition.warning { border-color: #f39c12; background: #fef8ee; }
        .admonition.danger { border-color: #e74c3c; background: #fdf3f2; }
        .admonition.example { border-color: #9b59b6; background: #f9f4fb; }
        .admonition.quote { border-color: #95a5a6; background: #f7f9f9; }
        .math.display { display: block; text-align: center; margin: 1em 0; }
        .featured { background: linear-gradient(90deg, #eaf4ff, transparent);
            border-left: 4px solid #3498db; padding: 0.3em 0.6em; border-radius: 4px; }
        .error { color: #c0392b; font-weight: 600; }
        .success { color: #27ae60; font-weight: 600; }
        .warn { color: #b9770e; font-weight: 600; }
        li:has(> .error), li:has(> input + .error) { background: #fdf3f2; border-radius: 4px; }
        li:has(> .success), li:has(> input + .success) { background: #f2fbf5; border-radius: 4px; }
${CarveCodeHighlight.DIFF_CSS}
    </style>
    <script>
        window.MathJax = {
            tex: { inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']] },
            options: { skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'] }
        };
    </script>
    <script async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
</head>
<body>
    $content
    <script>
${inlineScript(CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_JS))}
    </script>
    <script>
${inlineScript(CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_CARVE_JS))}
    </script>
    <script>
${CarveCodeHighlight.SCRIPT}
        carveHighlightCode(document);
    </script>
</body>
</html>
"""
    }

    /** An end tag inside the payload would close the element early. */
    fun inlineScript(js: String): String = js.replace(Regex("</(script)", RegexOption.IGNORE_CASE), "<\\\\/$1")

    fun inlineStyle(css: String): String = css.replace(Regex("</(style)", RegexOption.IGNORE_CASE), "<\\\\/$1")
}
