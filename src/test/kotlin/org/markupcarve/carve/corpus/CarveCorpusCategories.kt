package org.markupcarve.carve.corpus

/**
 * The shared-corpus coverage matrix for the TextMate highlighter.
 *
 * Every category in the markup-carve/carve shared corpus (the numeric prefix on
 * each `spec/tests/corpus/NN-name.crv` file) is classified here as either
 * [COVERED] - the grammar carries scopes that highlight the construct, and we
 * snapshot its token stream as a golden - or [SKIP] - a construct the grammar
 * intentionally does not give distinct highlighting (it is a pure parser /
 * rendering behavior with no token-level signal), recorded with the reason.
 *
 * [CarveCorpusCoverageMatrixTest] fails if the live corpus contains a category
 * absent from both maps, so a newly added spec category forces a deliberate
 * COVERED-or-SKIP decision rather than silently slipping through.
 *
 * Keys are the canonical category slugs - the corpus file name without the
 * trailing `-N` variant index and without the `.crv` extension, e.g.
 * `01-emphasis-3.crv` -> `01-emphasis`.
 */
object CarveCorpusCategories {

    /**
     * Categories whose constructs the grammar highlights. Each gets golden
     * token-stream snapshots generated from every matching corpus `.crv` file.
     */
    val COVERED: Set<String> = linkedSetOf(
        "01-emphasis",
        "02-headings",
        "03-links",
        "04-images",
        "05-lists",
        "06-task-lists",
        "07-blockquote-with-attribution",
        "08-image-with-caption",
        "09-tables",
        "10-tables-with-rowspan-and-colspan",
        "11-fenced-code",
        "12-inline-code",
        "13-admonitions",
        "14-abbreviations",
        "15-mentions-and-tags",
        "16-inline-extensions",
        "17-attributes",
        "18-frontmatter",
        "19-heading-ids",
        "26-fenced-code-shorter-inner-fence",
        "27-blockquote-caption-after-a-blank-line",
        "30-abbreviation-matches-on-word-boundaries-only",
        "31-mention-ignores-email-addresses",
        "32-tag-requires-a-word-boundary",
        "34-reference-link",
        "35-collapsed-reference-link",
        "36-unresolved-reference-link",
        "37-smart-typography-dashes-and-quotes",
        "38-smart-typography-arrows-and-symbols",
        "39-smart-typography-escapes-and-code",
        "42-math",
        "43-footnotes",
        "44-generic-divs",
        "45-definition-lists",
        "46-comments",
        "47-raw-blocks",
        "50-raw-inline",
        "51-emoji",
        "52-ordered-list-start-and-delimiter",
        "53-ordered-list-dialects",
        "54-ordered-marker-vs-prose",
        "56-editorial-markup",
        "58-cross-reference",
        "59-autolinks",
        "60-escapes",
        "62-bare-urls-stay-literal",
        "64-attribute-edge-cases",
        "65-escape-coverage",
        "66-inline-span",
        "67-superscript-and-subscript",
        "68-parenthesized-ordered-marker",
        "69-emphasis-edge-cases",
        "71-doubled-emphasis-delimiters",
        "72-nested-brackets-in-link-text",
        "74-two-char-delimiter-runs",
        "75-trailing-attribute-block-edge-cases",
        "78-fenced-code-language-with-punctuation",
        "84-block-attribute-lines",
        "85-numbered-cross-references",
        "86-inline-footnotes",
        "87-list-item-attributes",
        "89-mention-and-tag-name-boundaries",
        "90-superscript-in-a-table-cell",
        "91-nested-comment-fences",
        "92-strong-emphasis-starting-with-a-link",
        "94-literal-less-than-in-prose",
        "95-boolean-attributes",
        "96-table-span-marker-in-first-column",
        "97-table-cell-attributes",
        "98-table-row-attributes",
        "99-table-header-cell-rowspan",
        "100-block-quote-continuation-marker",
        "101-heading-marker-column-zero",
        "83-list-continuation-marker",
    )

    /**
     * Categories the grammar intentionally does not highlight distinctly. These
     * are parser- or renderer-level behaviors (block boundaries, looseness,
     * alignment math, lazy continuation) that produce no dedicated token scope:
     * a TextMate grammar is line/regex based and cannot model them, so there is
     * nothing meaningful to snapshot. Each entry records why it is skipped.
     */
    val SKIP: Map<String, String> = linkedMapOf(
        "20-table-column-alignment" to
            "Column alignment is a render-time table attribute; the grammar marks `|=` header rows " +
            "and pipes but does not derive per-column alignment, so there is no distinct token to snapshot.",
        "21-table-per-cell-alignment-override" to
            "Per-cell alignment override is a render-time attribute with no dedicated highlighting scope.",
        "22-headerless-table-alignment" to
            "Alignment inference for headerless tables is render-time only; no token-level signal.",
        "23-table-without-alignment" to
            "Absence of alignment markers is a render-time default; highlighting is identical to a plain table row.",
        "24-table-alignment-with-colspan" to
            "Alignment-plus-colspan interaction is render-time; the colspan `<` marker is already exercised by category 10.",
        "25-table-doubled-alignment-marker" to
            "Doubled alignment marker is a render-time parsing nuance with no separate highlighting scope.",
        "28-table-cell-escaped-pipe" to
            "Escaped-pipe cell splitting is a parser tokenization concern; the grammar highlights the escape via smart-typography, not a table-specific scope.",
        "29-table-cell-pipe-inside-code-span" to
            "Pipe-inside-code-span is a parser boundary rule; inline-code highlighting is already covered by category 12.",
        "33-table-stacked-rowspan" to
            "Stacked rowspan is a render-time cell-merging behavior; the `^` rowspan marker scope is already covered by category 10.",
        "40-table-multi-line-cell-continuation" to
            "Multi-line cell continuation (`+` row glue) is a render-time block behavior; the continuation marker scope is covered by categories 83/100.",
        "41-table-rowspan-with-multi-line-content" to
            "Combines rowspan and continuation, both render-time; constituent marker scopes already covered.",
        "48-hard-line-breaks" to
            "A hard line break is a render-time decision about trailing whitespace / backslash; no dedicated highlighting scope.",
        "49-non-breaking-space" to
            "Non-breaking space is a render-time character substitution with no token-level highlight.",
        "55-footnote-with-multiple-blocks" to
            "Multi-block footnote bodies are a block-structure / render behavior; the footnote reference scope is covered by category 43.",
        "61-empty-delimiters" to
            "Empty delimiters (e.g. `**`) are intentionally left as literal text; the grammar requires non-empty content, so there is no emphasis token to assert.",
        "63-nested-containers" to
            "Container nesting depth is a block-parser behavior; the div / blockquote opener scopes are already covered by categories 44 and 07.",
        "70-list-nesting-and-looseness" to
            "List nesting depth and loose-vs-tight looseness are render-time block behaviors; the list marker scopes are covered by categories 05/52/53.",
        "73-reference-labels-are-case-sensitive" to
            "Label case-sensitivity is a reference-resolution behavior; reference link highlighting is covered by categories 34-36.",
        "76-paragraph-interruption" to
            "Whether a block opener interrupts an open paragraph is a parser block-boundary behavior; the opener scopes themselves are covered by their own categories.",
        "77-blockquote-lazy-continuation" to
            "Lazy continuation folds a non-`>` line into the quote at parse time; the grammar scopes only the `>` marker line (category 07).",
        "79-multi-line-headings" to
            "Multi-line heading bodies are a parser behavior; the grammar's heading rule is single-line by design (column-0 `#` only, see category 101).",
        "80-blockquote-lazy-continuation-stops-at-a-fenced-block" to
            "A parser block-boundary edge of lazy continuation; no distinct highlighting scope beyond the quote and code-fence scopes already covered.",
        "81-list-lazy-continuation" to
            "Lazy continuation of list items is a parser behavior; list marker scopes are covered by categories 05/52/53.",
        "82-compact-list-blocks" to
            "Compact (tight) list rendering is a render-time looseness behavior; marker highlighting is unchanged.",
        "88-line-blocks" to
            "Line blocks are a block-structure / render behavior; the grammar carries no dedicated line-block scope.",
        "93-abbreviation-definition-interrupts-a-paragraph" to
            "Paragraph-interruption by an abbreviation definition is a parser block-boundary behavior; the abbreviation-definition scope is covered by category 14.",
        "102-paragraph-trailing-whitespace" to
            "Trailing-whitespace stripping is a render-time normalization with no token-level highlight; the text carries only the root scope.",
        "57-thematic-breaks" to
            "The grammar has no thematic-break rule, so `***` / `___` / `---` breaks are not highlighted as such. " +
            "Note: a mid-document `---` is additionally mis-detected as a frontmatter start because the IDE TextMate " +
            "engine treats the frontmatter `begin` anchor `\\A` like line-start `^` rather than document-start; this is " +
            "a grammar/highlighter bug tracked separately, not something to snapshot as correct behavior here.",
    )

    /**
     * Maps a corpus file name (e.g. `01-emphasis-3.crv`) to its canonical
     * category slug (`01-emphasis`) by stripping the `.crv` extension and any
     * trailing `-<digits>` variant index. Category slugs that themselves end in
     * a number (e.g. `100-block-quote-continuation-marker`) are preserved
     * because the trailing token is non-numeric.
     */
    fun categoryOf(fileName: String): String {
        val base = fileName.removeSuffix(".crv")
        return base.replace(Regex("-\\d+$"), "")
    }
}
