package org.markupcarve.carve.preview

/**
 * Code highlighting shared by the live preview and the HTML export, so the two
 * cannot drift apart on how a fence or a `{.diff}` fence is presented.
 *
 * [SCRIPT] expects the global `hljs` (and, for Carve fences, the vendored Carve
 * grammar) to be loaded first; it is a no-op without it.
 */
object CarveCodeHighlight {

    /** Styles for the `{.diff}` presentation built by `carveHighlightCode`. */
    val DIFF_CSS: String = """
        pre.diff.has-diff .line { display: inline-block; width: 100%; }
        pre.diff.has-diff .line.diff.add { background: rgb(46 160 67 / 15%); }
        pre.diff.has-diff .line.diff.remove { background: rgb(248 81 73 / 15%); }
        pre.diff.has-diff .diff-marker { display: inline-block; width: 1ch; font-weight: 700; }
        pre.diff.has-diff .line.diff.add .diff-marker { color: #1a7f37; }
        pre.diff.has-diff .line.diff.remove .diff-marker { color: #cf222e; }
    """.trimIndent()

    /** Defines `renderLanguageDiff(code)` and `carveHighlightCode(scope)`. */
    val SCRIPT: String = """
        function escapeDiffHtml(s) {
            return s.replace(/[&<>]/g, function (c) { return c === '&' ? '&amp;' : c === '<' ? '&lt;' : '&gt;'; });
        }
        // The {.diff} presentation on a language fence: core emits a pre.diff
        // with a code.language-x whose lines keep their leading +/-/space. Keep
        // the highlight.js highlighting and present the markers, the way
        // carve-grammars' shared helper does (same classes). hljs has no line
        // model, so tokenize each line after stripping its marker.
        function renderLanguageDiff(code) {
            var cls = Array.prototype.find.call(code.classList, function (c) { return c.indexOf('language-') === 0; });
            var lang = cls ? cls.slice('language-'.length) : '';
            var canHighlight = lang && hljs.getLanguage && hljs.getLanguage(lang);
            var text = code.textContent || '';
            if (text.slice(-1) === '\n') text = text.slice(0, -1);
            code.innerHTML = text.split('\n').map(function (line) {
                var marker = /^[+\- ]/.test(line) ? line[0] : '';
                var body = marker ? line.slice(1) : line;
                var inner;
                try { inner = canHighlight ? hljs.highlight(body, { language: lang }).value : escapeDiffHtml(body); }
                catch (e) { inner = escapeDiffHtml(body); }
                var lineClass = marker === '+' ? 'line diff add' : marker === '-' ? 'line diff remove' : 'line';
                var markerSpan = marker ? '<span class="diff-marker">' + escapeDiffHtml(marker) + '</span>' : '';
                return '<span class="' + lineClass + '">' + markerSpan + inner + '</span>';
            }).join('\n');
            if (code.parentElement) code.parentElement.classList.add('has-diff');
        }
        function carveHighlightCode(scope) {
            if (typeof hljs === 'undefined') return;
            scope.querySelectorAll('pre > code[class*="language-"]').forEach(function (block) {
                if (block.classList.contains('language-mermaid')) return;
                var pre = block.parentElement;
                if (pre && pre.classList.contains('diff')) { try { renderLanguageDiff(block); } catch (e) {} return; }
                hljs.highlightElement(block);
            });
        }
    """.trimIndent()
}
