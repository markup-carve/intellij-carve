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
        // The golden pins line ONE: `::<TAB>term` carries no marker scope, which
        // is the rule this category exists for and what #50 fixed.
        //
        // It does NOT bless line two. `:  d` is scoped as a definition
        // description there, and the engines render the whole document as one
        // paragraph - with no term above it, the `:` line is a lazy
        // continuation. A line-oriented grammar cannot see that the term line
        // was disqualified by its tab, and vscode-carve's snapshot has the
        // identical false positive. Tracked in markup-carve/carve-grammars#91.
        //
        // COVERED rather than SKIP, unlike the other line-based false
        // positives below (markup-carve/carve-grammars#71): in those the whole
        // document is a negative case with nothing to assert, and here line one
        // carries the rule. Skipping would drop the only corpus-level
        // assertion this repo has for it.
        "a-marker-separator-is-a-space-never-a-tab",
        "a-flush-left-line-needs-an-open-paragraph-to-fold-into",
        "a-repeated-definition-which-one-wins",
        "two-abbreviation-definitions",
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
        "inline-literal",
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
        "symbols",
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
        "link-destination-parentheses-balance",
        "empty-link-and-image-titles-are-preserved",
        "unquoted-attribute-values-may-contain-dots-and-colons",
        "adjacent-attribute-blocks-on-one-line-merge",
        "footnotes-placement",
        "code-span-and-image-trailing-attributes-are-strict",
        "a-bare-attribute-block-on-its-own-line-is-literal",
        "a-backslash-in-a-link-destination-is-a-literal-character",
        "autolink-display-keeps-the-raw-content",
        "editorial-markup-takes-a-trailing-attribute",
        "a-caret-is-a-reference-label-not-an-empty-footnote",
        "a-collapsed-image-reference-uses-its-alt-text-as-the-label",
        "a-comment-fence-is-a-comment-at-any-column-too",
        "a-comment-is-recognized-at-any-column",
        "a-definition-inside-a-comment-registers-nothing",
        "a-marker-attribute-may-hold-a-quoted-brace",
        "a-quote-marker-is-plus-a-space-and-a-lazy-line-keeps-its-own-text",
        "a-reference-image-takes-a-caption",
        "an-image-takes-a-reference-the-way-a-link-does",
        "trailing-attributes-on-a-link-reference-definition",
        // The comment scope has to survive a non-zero column: ` %% c` under a list item is
        // still comment.line.percent, which is the rule and is decided on the line.
        "a-comment-is-recognized-at-any-column",
        // Same rule for the fence form: comment.block regardless of the column it opens at.
        "a-comment-fence-is-a-comment-at-any-column-too",
        // The definition text carries the comment scope rather than a definition scope, which
        // is exactly what "registers nothing" looks like at token level.
        "a-definition-inside-a-comment-registers-nothing",
        // `1.{title='a}b'} item` keeps the `}` inside a quoted string and still opens a
        // numbered list - a lexical decision, and one tree-sitter-carve#81 gets wrong.
        "a-marker-attribute-may-hold-a-quoted-brace",
        // Whether `[^]` scopes as a reference label or a footnote marker is decided by the
        // characters on the line.
        "a-caret-is-a-reference-label-not-an-empty-footnote",
        // The collapsed form scopes its own text as the reference label, which is a lexical
        // property of the run.
        "a-collapsed-image-reference-uses-its-alt-text-as-the-label",
        // An image reference gets the reference scopes a link reference gets; a grammar that
        // handled only links would show the difference here.
        "an-image-takes-a-reference-the-way-a-link-does",
        // The caption line after a reference image takes the caption scopes - a line-level
        // decision that depends on what the line above was.
        "a-reference-image-takes-a-caption",
        // The attribute run at the end of a definition line scopes as attributes rather than
        // as part of the destination.
        "trailing-attributes-on-a-link-reference-definition",
        // The marker-plus-space requirement is lexical; the lazy line's own scopes are what
        // the golden pins.
        "a-quote-marker-is-plus-a-space-and-a-lazy-line-keeps-its-own-text",
    )

    /**
     * Categories the grammar intentionally does not highlight distinctly. These
     * are parser- or renderer-level behaviors (block boundaries, looseness,
     * alignment math, lazy continuation) that produce no dedicated token scope:
     * a TextMate grammar is line/regex based and cannot model them, so there is
     * nothing meaningful to snapshot. Each entry records why it is skipped.
     */
    val SKIP: Map<String, String> = linkedMapOf(
        "abbreviation-definition-separator-must-be-a-space" to
            "Separator strictness is parsing: the definition either matches or stays prose. The matching case is pinned by `abbreviations`; the other has no token by definition.",
        "abbreviation-title-escapes-its-markup-characters" to
            "Escaping changes the rendered title text, not the scopes - one unquoted string token either way, pinned by `abbreviations`.",
        "adjacent-slash-and-underscore-emphasis-nest" to
            "Nesting order between two emphasis kinds; both scopes are pinned by `emphasis`, and there is no token for which delimiter won.",
        "bold-italic-delimiter-needs-content" to
            "Negative case: an empty bold-italic run stays literal. The absence of a scope is the point; the positive form is pinned by `emphasis`.",
        "emphasis-opener-slash-adjacency" to
            "Flanking decides whether a delimiter opens; the resulting tokens are the ones `emphasis` pins.",
        "emphasis-span-closes-before-a-following-delimiter" to
            "Which delimiter closes a run is parsing; the span tokens are pinned by `emphasis`.",
        "all-space-verbatim-content" to
            "Whitespace-only verbatim content is still one raw-inline run, pinned by `inline-code` and `inline-literal`.",
        "trailing-whitespace-boundaries" to
            "Where a verbatim run's content starts and ends. Both run kinds it exercises are pinned by `inline-code` and `inline-literal`.",
        "widened-verbatim-fences" to
            "A wider fence changes the delimiter length, not the scope; the run kinds are pinned by `inline-code`, `inline-literal` and `math`.",
        "fence-folds-as-lazy-inline-code-above-the-content-column" to
            "Whether a fence opens a block or folds into a paragraph is block context, which a line-based grammar does not have; the list tokens are pinned by `lists`.",
        "comment-fence-with-trailing-text" to
            "The trailing text is inside the comment either way - one comment token, pinned by `comments`.",
        "unterminated-comment-fence" to
            "An unterminated fence runs to end of file, which the `comments` snapshot already shows; there is no separate scope for \"never closed\".",
        "unclaimed-openers-stay-literal" to
            "Negative case: a colon no symbol or extension claims stays prose. The claimed forms are pinned by `symbols`.",
        "attribute-block-after-a-mention-stays-literal" to
            "Whether the braces bind to the mention is parsing; the mention and attribute tokens are pinned by `mentions-and-tags` and `attributes`.",
        "attribute-braces-on-a-list-item-marker-line" to
            "A marker line with attributes produces the marker tokens and the attribute tokens, both pinned by `lists` and `attributes`.",
        "attribute-order-on-an-unwrapped-heading" to
            "Which attribute lands on which element is render-time; the tokens are pinned by `attribute-edge-cases`.",
        "only-the-id-hoists-to-the-section-wrapper" to
            "Hoisting is a render-time placement decision with no token of its own; the id and class scopes are pinned by `attributes`.",
        "indented-attribute-line-stays-literal" to
            "Negative case: an indented attribute line is prose. A line-based grammar cannot tell that from a valid attribute line inside a list item (markup-carve/carve-grammars#71), so it scopes as an attribute block here deliberately.",
        "leading-attribute-brace-before-an-inline-span-stays-literal" to
            "Negative case for brace binding; the attribute tokens are pinned by `attributes`.",
        "image-trailing-attribute-is-strict-about-the-glue" to
            "Glue strictness decides whether the braces bind; the image and attribute tokens are pinned by `image-with-caption` and `attributes`.",
        "unresolved-footnote-reference-with-a-trailing-attribute-stays-literal" to
            "Resolution is a later pass and carries no scope; the reference and attribute tokens are pinned by `footnotes` and `attributes`.",
        "footnote-definition-requires-an-inline-body" to
            "A body-less definition stays prose; the well-formed case is pinned by `footnotes`.",
        "footnote-definition-separator-must-be-a-space" to
            "Separator strictness is parsing; the definition tokens are pinned by `footnotes`.",
        "link-reference-definition-separator-must-be-a-space" to
            "Separator strictness is parsing; the definition tokens are pinned by `reference-link`.",
        "indented-reference-and-footnote-definitions-stay-literal" to
            "Negative case: indented definitions are prose. Same line-based limitation as the other indented categories; the tokens are pinned by `reference-link` and `footnotes`.",
        "implicit-heading-references-with-no-definition" to
            "Whether a reference resolves to a heading is a later pass; the heading and reference tokens are pinned by `headings` and `reference-link`.",
        "headings-inside-containers-are-not-wrapped" to
            "Section wrapping is render-time structure; inside a container the heading produces the tokens `headings` pins.",
        "indented-colon-fence-blocks-stay-literal" to
            "Negative case: an indented colon fence opens nothing, but the line-based grammar scopes it as an admonition deliberately (markup-carve/carve-grammars#71).",
        "below-content-column-div-body-in-a-list-item-stays-literal" to
            "Whether the body is inside the div depends on the content column, which needs block context; the div and list tokens are pinned by `admonitions` and `lists`.",
        "colon-fence-as-a-block-opener-in-a-list-item" to
            "The opener produces the same tokens wherever it sits, pinned by `admonitions`.",
        "opaque-spans-inside-a-container" to
            "Opacity is about what the renderer descends into; the fence and div tokens are pinned by `fenced-code` and `admonitions`.",
        "blocks-that-render-to-nothing" to
            "Rendering to nothing is a render-time outcome; the blocks still scope, and those scopes are pinned by `comments`, `definition-lists` and `abbreviations`.",
        "indented-image-and-caption-stay-literal" to
            "Negative case for indented blocks; the image and caption tokens are pinned by `image-with-caption`.",
        "bare-dot-ordered-markers" to
            "The bare `.` is an ordered marker and scopes as one - the same numbered-list token `lists` pins. Which value it starts at is not a highlighting distinction.",
        "definition-list-as-a-first-class-block-opener" to
            "Opening a list with no preceding paragraph is block structure; the term and definition tokens are pinned by `definition-lists`.",
        "under-indented-definition-attaches-over-indented-definition-folds" to
            "Attachment depends on the content column, which needs block context; the tokens are pinned by `definition-lists`.",
        "wrapped-definition-term-continuation-below-the-content-column-strips-leading-whitespace" to
            "Whitespace stripping on a continuation line changes the text, not the scopes, which `definition-lists` pins.",
        "nested-item-looseness-does-not-propagate-to-the-outer-item" to
            "Looseness decides whether the renderer wraps item content in a paragraph; no token, and the list tokens are pinned by `lists`.",
        "outer-item-with-an-internal-blank-before-an-attached-block-is-loose" to
            "The same looseness question from the blank-line side; no token either way.",
        "post-blank-list-continuation-content-column-model" to
            "The content column is block context; the marker tokens are pinned by `list-continuation-marker`.",
        "tight-list-item-keeps-trailing-text-after-a-block-bare" to
            "Where trailing text attaches is block structure; the list and div tokens are pinned by `lists` and `admonitions`.",
        "sublist-marker-interrupts-a-continuation-paragraph" to
            "Whether a marker opens a sublist or folds into the paragraph is block context; both marker kinds are pinned by `lists`.",
        "indented-ordered-marker-content-column-includes-the-marker-indent" to
            "A content-column rule with no token of its own; the marker tokens are pinned by `lists` and `list-continuation-marker`.",
        "thematic-break-requires-contiguous-markers" to
            "Negative case: a non-contiguous run is not a break. The grammar reads the valid form as a break and the invalid one as list markers or prose; neither adds a scope.",
        "table-as-a-block-opener-in-a-list-item" to
            "A table opens with the same row tokens wherever it sits, pinned by `tables`.",
        "table-row-closing-pipe" to
            "Whether the closing pipe is required is row parsing; the separator token is pinned by `tables`.",
        "quote-flanking-after-an-escaped-character" to
            "Flanking decides which smart quote is produced; the escape and quote tokens are pinned by `escape-coverage` and `smart-typography-dashes-and-quotes`.",
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
        "single-line-headings" to
            "A heading produces the same marker and title tokens the covered heading categories already pin. Renamed upstream from multi-line-headings when carve#451 made headings end at the newline: the category now asserts the opposite rule, and still has no token-level signal.",
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
        "a-blank-after-a-comment-still-ends-the-item" to
            "Whether the item ends is block context; the comment and list tokens are pinned by `comments` and `lists`.",
        "a-comment-ends-the-paragraph-it-sits-under" to
            "Paragraph termination is block context; the tokens are the ones `comments` pins.",
        "a-comment-fence-at-column-0-ends-the-item-a-line-does-not" to
            "Which form ends the item is block context; both comment forms are pinned by `comments` and `nested-comment-fences`.",
        "a-comment-fence-under-a-nested-item-does-not-close-it-either" to
            "Container nesting is block context; the fence tokens are pinned by `nested-comment-fences`.",
        "a-comment-under-a-nested-item-does-not-close-it" to
            "Same block-context rule for the line form; tokens pinned by `comments`.",
        "an-invisible-line-does-not-cancel-a-blank-line-separation" to
            "Blank-line bookkeeping across a comment is block context, with no token of its own.",
        "a-block-attribute-line-inside-a-quote-ends-the-paragraph-above-it" to
            "Paragraph termination inside a container is block context; the quote and attribute tokens are pinned by `block-attribute-lines` and `quotes`.",
        "a-floating-attribute-stops-at-the-item-boundary" to
            "Where an attribute line stops applying is block context; the attribute tokens are pinned by `list-item-attributes`.",
        "openers-past-the-nesting-cap-are-one-paragraph" to
            "A depth limit is a parser resource bound; the div tokens are pinned by `generic-divs`.",
        "a-definition-below-every-content-column-folds-as-text" to
            "Which column a definition must start at is block context; the reference tokens are pinned by `reference-links`.",
        "a-definition-inside-a-container-is-collected-at-that-container-s-content-column" to
            "Content-column arithmetic inside a container, invisible to a line-based grammar.",
        "a-definition-below-a-footnote-body-s-column-is-the-document-s-own-text" to
            "Footnote-body column arithmetic; the definition and footnote tokens are pinned by `footnotes` and `reference-links`.",
        "a-definition-on-a-footnote-body-s-continuation-line-is-collected" to
            "Same arithmetic, positive case; no token distinguishes where the definition was collected.",
        "a-definition-past-a-footnote-body-s-column-is-the-body-s-own-text" to
            "Same arithmetic, third column; tokens identical either way.",
        "a-footnote-body-s-own-column-is-two-and-a-third-column-is-its-text" to
            "The body's own column is block context; the footnote tokens are pinned by `footnotes`.",
        "a-flush-left-line-after-a-footnote-definition-belongs-to-the-document" to
            "Whether a flush-left line continues the body or leaves it is block context.",
        "a-footnote-body-holds-blocks-and-they-render-where-they-were-written" to
            "Where the body's blocks render is a rendering property; each block's tokens are pinned by its own category.",
        "a-nested-list-in-a-footnote-body-stays-nested" to
            "Nesting inside a body is block context; the list tokens are pinned by `lists`.",
        "a-heading-in-a-footnote-body-takes-an-id-but-no-section-wrapper" to
            "The section wrapper is a rendering decision; the heading tokens are pinned by `headings`.",
        "an-attribute-line-inside-a-footnote-body-attaches-inside-it" to
            "What the attribute line attaches to is block context; the tokens are pinned by `block-attribute-lines`.",
        "an-abbreviation-at-a-list-item-s-content-column-is-still-not-a-definition" to
            "Document-level-only recognition is block context; the abbreviation tokens are pinned by `abbreviations`.",
        "an-abbreviation-definition-is-recognized-only-at-document-level" to
            "Negative case inside a quote: no abbreviation scope appears, and the absence is the point.",
        "a-list-item-does-not-define-an-abbreviation-either" to
            "Same negative case under a list item; the grammar emits no abbreviation scope.",
        "a-collapsed-reference-is-matched-by-the-label-the-author-wrote" to
            "Label matching is resolution; the reference tokens are pinned by `reference-links`.",
        "an-unresolved-image-reference-stays-literal" to
            "Whether a reference resolves is not a token property; both forms scope identically.",
        "an-unresolved-reference-image-takes-no-caption" to
            "The caption is dropped during resolution, so the tokens are the same as the resolved case pinned by `a-reference-image-takes-a-caption`.",
        "one-definition-serves-a-link-and-an-image" to
            "One definition serving two references is resolution; the tokens are two ordinary reference runs.",
        "a-heading-id-keeps-a-non-ascii-space" to
            "Generated ids are not tokens; the heading tokens are pinned by `headings`.",
        "a-heading-reference-folds-unicode-normalization-but-not-compatibility" to
            "Normalization happens in the reference index, with no token-level signal.",
        "a-combined-bold-italic-span-may-cross-a-line" to
            "The document tokenizes as plain text end to end: a line-based grammar cannot carry an emphasis run across a newline, so there is no scope to assert.",
        "a-description-line-needs-a-term-above-it" to
            "The grammar scopes `:  [r]: /u` as a definition-list marker although no term precedes it, so the golden would bless a false positive. Same line-based limit as markup-carve/carve-grammars#91, which the tab case records.",
        "a-div-does-not-define-an-abbreviation-either" to
            "MEASURED FALSE POSITIVE: the grammar scopes `*[HTML]: Hyper Text` inside a div as meta.abbreviation.definition, where the spec says a div defines nothing. Tracked in markup-carve/carve-grammars#125; a golden here would pin the wrong answer.",
        "a-tag-inside-a-literal-brace-run-is-still-a-tag" to
            "MEASURED FALSE NEGATIVE: `# H {#id .cls}` scopes entirely as heading text, so the tag inside the literal brace run gets no tag scope. Tracked in markup-carve/carve-grammars#125.",
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
