package org.markupcarve.carve.corpus

/**
 * The shared-corpus coverage matrix for the TextMate highlighter.
 *
 * Every category in the markup-carve/carve shared corpus (the slug on each
 * `spec/tests/corpus/NN-name.crv` file) is classified here as either
 * [COVERED] - the grammar carries scopes that highlight the construct, and we
 * snapshot its token stream as a golden - or [SKIP] - a construct the grammar
 * intentionally does not give distinct highlighting (it is a pure parser /
 * rendering behavior with no token-level signal), recorded with the reason.
 *
 * [CarveCorpusCoverageMatrixTest] fails if the live corpus contains a category
 * absent from both maps, so a newly added spec category forces a deliberate
 * COVERED-or-SKIP decision rather than silently slipping through.
 *
 * Keys are the canonical category slugs - the corpus file name without its
 * LEADING NUMBER, without the trailing `-N` variant index, and without the
 * `.crv` extension, e.g. `01-emphasis-3.crv` -> `emphasis`. The number is
 * the spec's ordering, not an identity.
 */
object CarveCorpusCategories {

    /**
     * Categories whose constructs the grammar highlights. Each gets golden
     * token-stream snapshots generated from every matching corpus `.crv` file.
     */
    val COVERED: Set<String> = linkedSetOf(
        "emphasis",
        "headings",
        "links",
        "images",
        "lists",
        "task-lists",
        "blockquote-with-attribution",
        "image-with-caption",
        "tables",
        "tables-with-rowspan-and-colspan",
        "fenced-code",
        "inline-code",
        "admonitions",
        "abbreviations",
        "mentions-and-tags",
        "inline-extensions",
        "attributes",
        "frontmatter",
        "heading-ids",
        "fenced-code-shorter-inner-fence",
        "blockquote-caption-after-a-blank-line",
        "abbreviation-matches-on-word-boundaries-only",
        "mention-ignores-email-addresses",
        "tag-requires-a-word-boundary",
        "reference-link",
        "collapsed-reference-link",
        "unresolved-reference-link",
        "smart-typography-dashes-and-quotes",
        "smart-typography-arrows-and-symbols",
        "smart-typography-escapes-and-code",
        "math",
        "footnotes",
        "generic-divs",
        "definition-lists",
        "comments",
        "raw-blocks",
        "raw-inline",
        "emoji",
        "ordered-list-start-and-delimiter",
        "ordered-list-dialects",
        "ordered-marker-vs-prose",
        "editorial-markup",
        "thematic-breaks",
        "cross-reference",
        "autolinks",
        "escapes",
        "bare-urls-stay-literal",
        "attribute-edge-cases",
        "escape-coverage",
        "inline-span",
        "superscript-and-subscript",
        "parenthesized-ordered-marker",
        "emphasis-edge-cases",
        "doubled-emphasis-delimiters",
        "nested-brackets-in-link-text",
        "two-char-delimiter-runs",
        "trailing-attribute-block-edge-cases",
        "fenced-code-language-with-punctuation",
        "block-attribute-lines",
        "numbered-cross-references",
        "inline-footnotes",
        "list-item-attributes",
        "mention-and-tag-name-boundaries",
        "superscript-in-a-table-cell",
        "nested-comment-fences",
        "strong-emphasis-starting-with-a-link",
        "literal-less-than-in-prose",
        "boolean-attributes",
        "table-span-marker-in-first-column",
        "table-cell-attributes",
        "table-row-attributes",
        "table-header-cell-rowspan",
        "block-quote-continuation-marker",
        "heading-marker-column-zero",
        "list-continuation-marker",
        "marker-line-nested-lists",
        "link-destination-stops-at-the-first-parenthesis",
        "empty-link-and-image-titles-are-preserved",
        "unquoted-attribute-values-may-contain-dots-and-colons",
        "adjacent-attribute-blocks-on-one-line-merge",
        "footnotes-placement",
        "code-span-and-image-trailing-attributes-are-strict",
        "a-bare-attribute-block-on-its-own-line-is-literal",
        "a-backslash-in-a-link-destination-is-a-literal-character",
        "autolink-display-keeps-the-raw-content",
        "editorial-markup-takes-a-trailing-attribute",
    )

    /**
     * Categories the grammar intentionally does not highlight distinctly. These
     * are parser- or renderer-level behaviors (block boundaries, looseness,
     * alignment math, lazy continuation) that produce no dedicated token scope:
     * a TextMate grammar is line/regex based and cannot model them, so there is
     * nothing meaningful to snapshot. Each entry records why it is skipped.
     */
    val SKIP: Map<String, String> = linkedMapOf(
        "table-column-alignment" to
            "Column alignment is a render-time table attribute; the grammar marks `|=` header rows " +
            "and pipes but does not derive per-column alignment, so there is no distinct token to snapshot.",
        "table-per-cell-alignment-override" to
            "Per-cell alignment override is a render-time attribute with no dedicated highlighting scope.",
        "headerless-table-alignment" to
            "Alignment inference for headerless tables is render-time only; no token-level signal.",
        "table-without-alignment" to
            "Absence of alignment markers is a render-time default; highlighting is identical to a plain table row.",
        "table-alignment-with-colspan" to
            "Alignment-plus-colspan interaction is render-time; the colspan `<` marker is already exercised by category 10.",
        "table-doubled-alignment-marker" to
            "Doubled alignment marker is a render-time parsing nuance with no separate highlighting scope.",
        "table-cell-escaped-pipe" to
            "Escaped-pipe cell splitting is a parser tokenization concern; the grammar highlights the escape via smart-typography, not a table-specific scope.",
        "table-cell-pipe-inside-code-span" to
            "Pipe-inside-code-span is a parser boundary rule; inline-code highlighting is already covered by category 12.",
        "table-stacked-rowspan" to
            "Stacked rowspan is a render-time cell-merging behavior; the `^` rowspan marker scope is already covered by category 10.",
        "table-multi-line-cell-continuation" to
            "Multi-line cell continuation (`+` row glue) is a render-time block behavior; the continuation marker scope is covered by categories 83/100.",
        "table-rowspan-with-multi-line-content" to
            "Combines rowspan and continuation, both render-time; constituent marker scopes already covered.",
        "hard-line-breaks" to
            "A hard line break is a render-time decision about trailing whitespace / backslash; no dedicated highlighting scope.",
        "non-breaking-space" to
            "Non-breaking space is a render-time character substitution with no token-level highlight.",
        "footnote-with-multiple-blocks" to
            "Multi-block footnote bodies are a block-structure / render behavior; the footnote reference scope is covered by category 43.",
        "empty-delimiters" to
            "Empty delimiters (e.g. `**`) are intentionally left as literal text; the grammar requires non-empty content, so there is no emphasis token to assert.",
        "nested-containers" to
            "Container nesting depth is a block-parser behavior; the div / blockquote opener scopes are already covered by categories 44 and 07.",
        "list-nesting-and-looseness" to
            "List nesting depth and loose-vs-tight looseness are render-time block behaviors; the list marker scopes are covered by categories 05/52/53.",
        "reference-labels-are-case-sensitive" to
            "Label case-sensitivity is a reference-resolution behavior; reference link highlighting is covered by categories 34-36.",
        "paragraph-interruption" to
            "Whether a block opener interrupts an open paragraph is a parser block-boundary behavior; the opener scopes themselves are covered by their own categories.",
        "blockquote-lazy-continuation" to
            "Lazy continuation folds a non-`>` line into the quote at parse time; the grammar scopes only the `>` marker line (category 07).",
        "multi-line-headings" to
            "Multi-line heading bodies are a parser behavior; the grammar's heading rule is single-line by design (column-0 `#` only, see category 101).",
        "blockquote-lazy-continuation-stops-at-a-fenced-block" to
            "A parser block-boundary edge of lazy continuation; no distinct highlighting scope beyond the quote and code-fence scopes already covered.",
        "list-lazy-continuation" to
            "Lazy continuation of list items is a parser behavior; list marker scopes are covered by categories 05/52/53.",
        "compact-list-blocks" to
            "Compact (tight) list rendering is a render-time looseness behavior; marker highlighting is unchanged.",
        "line-blocks" to
            "Line blocks are a block-structure / render behavior; the grammar carries no dedicated line-block scope.",
        "abbreviation-definition-interrupts-a-paragraph" to
            "Paragraph-interruption by an abbreviation definition is a parser block-boundary behavior; the abbreviation-definition scope is covered by category 14.",
        "paragraph-trailing-whitespace" to
            "Trailing-whitespace stripping is a render-time normalization with no token-level highlight; the text carries only the root scope.",
        "blocked-span-marker-renders-as-empty-cell" to
            "Whether a blocked `^`/`<` span marker collapses to an empty cell is render-time cell merging; both marker scopes are covered by category 10.",
        "colspan-marker-scans-left-past-a-consumed-cell" to
            "Colspan target resolution across consumed cells is render-time cell merging; the `<` marker scope is covered by category 10.",
        "security-hardening" to
            "URL-scheme and attribute sanitization is a render-time security behavior; the grammar has no notion of a blocked scheme and highlights link, autolink and attribute syntax identically either way (categories 03/17/59).",
        "cross-references-resolve-inside-footnote-bodies" to
            "Cross-reference resolution inside a footnote body is a document-level link-resolution behavior; the crossref and footnote scopes are covered by categories 58 and 43.",
        "a-pipe-pair-with-no-cell-is-not-a-table" to
            "Rejecting a lone `||` as a table needs the block parser's delimiter-row lookahead; a line/regex grammar cannot make that decision, so there is no meaningful token stream to pin.",
        "a-continuation-row-needs-a-body-row" to
            "Requiring a body row before a `+` continuation row is a block-parser precondition; the continuation marker scope is covered by categories 83/100.",
        "fence-opener-with-a-nested-list-body-inside-a-list-item" to
            "Container-in-list-item nesting and fence-close matching are block-parser behaviors; the div opener/closer and list marker scopes are covered by categories 44 and 05.",
        "footnote-definition-inside-a-container-is-collected" to
            "Hoisting a footnote definition out of a container is a document-collection behavior; the footnote definition scope is covered by category 43.",
        "cyclic-cross-reference-resolves-to-one-level" to
            "Cycle breaking in cross-reference resolution is a render-time guard; the crossref scope is covered by category 58.",
        "trojan-source-heading-ids-are-nfc-normalized-and-strip-invisible-controls" to
            "Heading-id derivation (NFC normalization, invisible-control stripping) is render-time; the heading scopes are covered by categories 02/19.",
        "trojan-source-rendered-text-and-code-strip-bidi-override-controls" to
            "Bidi-override stripping happens on rendered output; the invisible controls carry no token scope of their own and inline code is covered by category 12.",
        "scheme-probe-strips-unicode-whitespace" to
            "Unicode-whitespace stripping before the scheme probe is render-time sanitization; the reference definition and reference link scopes are covered by categories 34-36.",
        "classes-are-deduplicated" to
            "Class deduplication happens when attributes are applied at render time; the attribute scopes are covered by categories 17/64.",
    )

    /**
     * Maps a corpus file name (e.g. `01-emphasis-3.crv`) to its canonical
     * category slug (`01-emphasis`) by stripping the `.crv` extension and any
     * trailing `-<digits>` variant index. Category slugs that themselves end in
     * a number (e.g. `100-block-quote-continuation-marker`) are preserved
     * because the trailing token is non-numeric.
     */
    fun categoryOf(fileName: String): String = slugOf(fileName).replace(Regex("-\\d+$"), "")

    /**
     * A corpus file name without its leading number and `.crv` extension.
     *
     * `01-emphasis-3.crv` -> `emphasis-3`. The number is the spec's ORDERING,
     * not an identity: keying on it meant an upstream renumbering invalidated
     * every entry in this matrix at once, none of which had changed, and
     * renamed all 286 golden files with it. Golden artifacts are named by slug
     * for the same reason.
     */
    fun slugOf(fileName: String): String =
        fileName.removeSuffix(".crv").replace(Regex("^\\d+-"), "")
}
