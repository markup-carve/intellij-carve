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

        // The 120 categories that arrived with the spec bump to carve bbd7d8e. Every one of
        // them was read against its corpus documents and against the grammar before landing
        // here; the ones below carry the rule in a token, the rest are in SKIP with the reason.
        // The indented `%%%` fence still opens - the begin rule is column-agnostic - so the definition
        // inside it carries the comment scope instead of a definition scope, the same token-level shape
        // as `a-definition-inside-a-comment-registers-nothing`.
        "a-comment-fence-at-an-item-s-content-column-registers-nothing-either",
        // The generic div is a per-line match rather than a begin/end, so the comment fence still claims
        // its body inside the container.
        "a-comment-fence-inside-a-colon-container-registers-nothing",
        // The same rule two indents in: the fence begin/end is what decides it, and the body scopes as
        // comment.
        "a-comment-fence-one-item-deeper-registers-nothing-either",
        // The end rule backreferences the opener width, so a four-percent fence closes on its own width
        // and its body stays comment.
        "a-wider-comment-fence-inside-an-item-hides-its-body-the-same-way",
        // The footnote definition inside the fence carries comment.block instead of the footnote scopes.
        "a-footnote-definition-inside-an-item-s-comment-registers-nothing",
        // The same for the abbreviation definition form.
        "an-abbreviation-inside-a-comment-defines-nothing",
        // The term slot is exactly one ASCII alphanumeric run, so the valid terms take the abbreviation
        // scopes and the rejected ones scope as prose - the rule, on the line, both ways.
        "an-abbreviation-term-is-one-ascii-alphanumeric-word",
        // A colon in a NAME makes the whole block fail to match and stay literal, while a colon in an
        // unquoted VALUE keeps it an attribute block. Both sides are in the documents.
        "an-attribute-name-admits-no-colon",
        // Glued items leave the block literal and a separated pair scopes as attributes - the strict
        // identifier family `attribute-edge-cases` already pins the shape.
        "two-attributes-need-a-separator-between-them",
        // `{:fr}` has its own attribute-name scope (added for markup-carve/carve#1114) and no other
        // golden reaches it.
        "a-language-attribute-is-exact-sugar-for-lang",
        // The word-boundary lookbehind decides it on the line: the first name takes the tag or mention
        // scope and the glued second one stays bare, which is what the fixture renders too.
        "a-marker-glued-to-a-name-opens-nothing",
        // The continuation rule requires the line to end at a pipe, so a row with trailing text loses the
        // continuation scope entirely.
        "a-continuation-row-carries-no-trailing-text",
        // A dashless delimiter cell fails the separator-row lookahead, so the line falls to the ordinary
        // row scope instead of the separator-row scope.
        "a-table-delimiter-cell-needs-at-least-one-dash",
        // A dedicated bare-opener begin/end with its own type scope (markup-carve/carve-grammars#222); a
        // titled or labeled opener deliberately falls through to the generic div.
        "composite-figures",
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

        // The spec bump to carve bbd7d8e. Each of these is a parser- or renderer-level rule
        // with no token that changes when the rule goes the other way, or a negative case
        // whose positive form another category already pins. Three of them record a MEASURED
        // FALSE POSITIVE instead: the grammar answers them wrongly today, so a golden would
        // pin the wrong answer rather than the rule.
        "a-below-column-marker-after-a-comment-where-no-paragraph-is-open" to
            "Whether a marker below the content column opens a sibling or folds in is block context; the fence and list tokens are pinned by `nested-comment-fences` and `lists`.",
        "a-blank-line-holds-spaces-and-tabs-and-nothing-else" to
            "What counts as a blank line is block bookkeeping; an invisible character carries no scope of its own and the surrounding text is plain either way.",
        "a-block-attached-after-an-invisible-line-leaves-the-item-tight" to
            "Looseness is a render-time decision about paragraph wrapping; the attribute, comment and list tokens are pinned by `list-item-attributes`, `comments` and `lists`.",
        "a-block-image-is-separated-from-the-block-after-it-on-every-target" to
            "Block separation per render target is an output concern with no token; the image tokens are pinned by `images`.",
        "a-boolean-and-a-key-value-of-the-same-name-are-one-attribute" to
            "Merging two spellings of one name happens when attributes are applied at render time; both items scope as attributes.",
        "a-boolean-lang-is-the-third-spelling-of-the-same-key" to
            "The same merging question for the language key, and a bare boolean carries no name scope of its own, so the stream is one attribute block either way.",
        "a-boundary-line-inside-an-open-fence-does-not-end-the-container" to
            "Where the container ends is block extent; the fenced lines already scope as raw through the fence begin/end that `fenced-code` pins.",
        "a-caption-attaches-across-one-blank-line" to
            "Whether the caption still attaches is block context; the caption line takes the same scopes with or without the blank line, as `image-with-caption` pins.",
        "a-captioned-quote-holds-more-than-one-block" to
            "How many blocks the captioned quote holds is block structure; the quote and caption tokens are pinned by `blockquote-with-attribution`.",
        "a-caret-line-does-not-end-a-paragraph-it-cannot-caption" to
            "Negative case: a caret line under a paragraph captions nothing. The grammar scopes every caret line as a caption deliberately, so a golden would pin that over-approximation rather than the rule.",
        "a-code-fence-opener-takes-exactly-one-space" to
            "Separator strictness is parsing, and the info-string slot admits any run of spaces or tabs, so both spellings give the fence tokens `fenced-code` pins.",
        "a-collapsed-reference-reaches-a-heading-by-the-heading-s-rendered-text" to
            "Reaching a heading by its rendered text is resolution; the heading and reference tokens are pinned by `headings` and `collapsed-reference-link`.",
        "a-column-0-line-after-a-container-s-last-block-when-that-block-left-no-paragraph-open" to
            "Whether the flush-left line rejoins the container is block context, invisible to a line-based grammar.",
        "a-column-zero-definition-ends-an-open-list-item" to
            "Ending the item is block context; the definition and list tokens are pinned by `reference-link`, `footnotes` and `lists`.",
        "a-comment-fence-opened-on-an-item-s-marker-line-hides-its-body-too" to
            "MEASURED FALSE POSITIVE: the comment fence rule is anchored to the start of the line, so a fence opened after a bullet does not open at all and the definition inside keeps its reference-definition scopes, the opposite of what the category asserts.",
        "a-container-a-lazy-line-folded-into-is-still-open" to
            "Lazy continuation and container extent are block context; the div, quote and list tokens are pinned by `admonitions`, `blockquote-with-attribution` and `lists`.",
        "a-continuation-marker-after-a-blank-line-in-a-loose-item" to
            "Whether the marker still attaches across a blank line is block context; the marker token is pinned by `list-continuation-marker`.",
        "a-continuation-marker-after-a-blank-line-in-the-item" to
            "The same attachment question one blank line earlier; no token distinguishes the two.",
        "a-continuation-marker-attaches-one-block-and-the-boundary-is-that-block-s-extent" to
            "How far the attached run reaches is block extent; the marker token is pinned by `list-continuation-marker` and each attached block by its own category.",
        "a-continuation-row-s-open-run-and-an-escaped-closing-pipe" to
            "Where a verbatim run ends inside a row is parser tokenization; the row and code tokens are pinned by `tables` and `inline-code`.",
        "a-definition-attached-by-a-continuation-marker-is-collected-and-the-item-keeps-no-trace" to
            "Collection is a document pass with no token of its own; the marker and definition tokens are pinned by `list-continuation-marker` and `reference-link`.",
        "a-definition-body-continuation-indented-past-its-column-is-lazy-text" to
            "Column arithmetic on a continuation line; the term and description tokens are pinned by `definition-lists`.",
        "a-definition-inside-a-definition-list-dd-is-collected-and-the-entry-keeps-no-trace" to
            "Collection out of a description is a document pass; the tokens are the ones `definition-lists`, `reference-link` and `footnotes` pin.",
        "a-definition-marker-s-separator-is-a-space-and-it-is-a-run" to
            "Separator strictness is parsing, and the definition rules accept a tab there; the well-formed definitions are pinned by `abbreviations` and `footnotes`.",
        "a-derived-title-yields-to-an-authored-one" to
            "Which title wins is render-time attribute application; both keys scope as attributes, pinned by `attributes`.",
        "a-fence-keeps-the-blank-line-at-the-end-of-its-content" to
            "Content preservation inside a fence changes the text, not the scopes, which `fenced-code` pins.",
        "a-fence-opened-on-a-list-marker-line-body-below-the-content-column" to
            "The content column is block context, and the grammar's code fence only opens at column zero; the list and fence tokens are pinned by `lists` and `fenced-code`.",
        "a-floating-attribute-is-scoped-to-the-container-that-holds-it" to
            "Which container an attribute line belongs to is block context, the same limit `a-floating-attribute-stops-at-the-item-boundary` records.",
        "a-footnote-body-s-last-block-when-it-is-not-a-paragraph-gets-a-synthesized-paragraph-for-the-backlink" to
            "The synthesized paragraph is render-time output; the footnote tokens are pinned by `footnotes`.",
        "a-footnote-in-an-unresolved-reference-is-not-a-reference" to
            "Whether the reference resolves is a later pass; both forms scope identically, as `unresolved-reference-link` pins.",
        "a-footnote-in-link-text-nests-the-anchors" to
            "Anchor nesting happens at render time; the link and footnote tokens are pinned by `links` and `footnotes`.",
        "a-footnote-in-reference-link-text-nests-the-anchors-too" to
            "The same render-time nesting from the reference side; tokens pinned by `reference-link` and `footnotes`.",
        "a-format-character-before-a-scheme-is-not-stripped-and-is-inert" to
            "Scheme probing and sanitization are render-time security behavior; the link tokens are the same either way.",
        "a-frontmatter-opener-takes-exactly-one-space" to
            "Separator strictness is parsing, and the frontmatter opener accepts any whitespace run; the well-formed opener is pinned by `frontmatter`.",
        "a-label-beginning-with-an-at-sign-is-not-a-reference-label" to
            "The label charset is a resolution rule the grammar does not encode: it scopes the label the same either way, so a golden would bless the wrong answer. The reference tokens are pinned by `reference-link`.",
        "a-language-attribute-and-lang-are-one-key" to
            "Collapsing the two spellings into one key is render-time attribute application; both items scope as attributes.",
        "a-line-at-a-footnote-definition-s-own-column-followed-by-non-blank-text-forms-its-own-tight-block" to
            "Column arithmetic inside an item; the footnote and list tokens are pinned by `footnotes` and `lists`.",
        "a-link-definition-written-before-a-footnote-stays-before-it" to
            "Ordering of collected definitions is a document pass; the definition tokens are pinned by `reference-link` and `footnotes`.",
        "a-link-title-takes-exactly-one-space" to
            "Separator strictness is parsing, and the grammar accepts any whitespace before the title; the well-formed link is pinned by `links`.",
        "a-list-marker-at-the-content-column-inside-an-open-fence" to
            "Whether the marker sits inside the fence is block context; the fence and marker tokens are pinned by `fenced-code` and `lists`.",
        "a-malformed-language-tag-leaves-the-whole-block-literal" to
            "Negative case: a malformed tag leaves the block literal, and the absence of the attribute scope is the point. The positive form is pinned by `a-language-attribute-is-exact-sugar-for-lang`.",
        "a-math-span-s-base-class-keeps-the-class-slot-in-place" to
            "Where the base class lands is render-time attribute application; the math and attribute tokens are pinned by `math` and `attributes`.",
        "a-multi-letter-ordered-marker-opens-no-list" to
            "Negative case: a multi-letter marker opens nothing and the grammar emits no list scope for it. The valid marker spellings are pinned by `ordered-list-dialects`.",
        "a-multi-line-raw-block-is-placed-at-its-opening-and-verbatim-after-it" to
            "Placement of the raw block is render-time; the raw fence tokens are pinned by `raw-blocks`.",
        "a-note-body-s-own-references-resolve" to
            "Resolution inside a note body is a document pass; the note and reference tokens are pinned by `footnotes` and `reference-link`.",
        "a-note-s-content-recognizes-no-note" to
            "Whether the inner brackets open a second note is inline parsing the grammar does not nest; the note tokens are pinned by `inline-footnotes`.",
        "a-quoted-attribute-value-stops-at-the-newline" to
            "Negative case: a value broken over two lines is literal, which a line-bounded grammar produces for free rather than by encoding the rule. The valid block is pinned by `attributes`.",
        "a-ragged-table-keeps-each-row-s-cell-count" to
            "Cell counts are table structure; every row scopes as a row, pinned by `tables`.",
        "a-real-div-in-a-container-and-the-flush-left-line-after-it" to
            "Whether the flush-left line stays in the div is block context; the div and list tokens are pinned by `admonitions` and `lists`.",
        "a-reference-definition-is-anchored-at-end-of-line" to
            "Anchoring is parsing, and the grammar's definition rule matches a prefix and ignores whatever follows it; the well-formed definition is pinned by `reference-link`.",
        "a-reference-definition-s-metadata-slots-take-exactly-one-space" to
            "Separator strictness is parsing; the definition tokens are pinned by `reference-link` and `trailing-attributes-on-a-link-reference-definition`.",
        "a-reference-link-s-text-survives-its-own-frame" to
            "Bracket balance inside the text is parsing; the tokens are pinned by `reference-link`, with the same limit the `nested-brackets-in-link-text` golden shows.",
        "a-semantic-name-renames-the-span-and-the-leftovers-ride-the-element" to
            "Renaming the element is render-time, and a bare name carries no scope of its own, so the tokens are the ones `inline-span` and `attributes` pin.",
        "a-single-percent-is-not-a-comment" to
            "Negative case: one percent sign opens nothing, and the absence of a comment scope is the point. The comment forms are pinned by `comments`.",
        "a-structural-attribute-leads-the-author-s-own" to
            "Attribute ordering on the rendered element has no token of its own; the block attribute tokens are pinned by `block-attribute-lines`.",
        "a-tab-after-a-fence-or-a-frontmatter-opener-depends-on-where-it-sits" to
            "Separator strictness is parsing, and both openers admit a tab in the grammar; the well-formed forms are pinned by `fenced-code` and `frontmatter`.",
        "a-tab-after-a-heading-quote-or-caption-marker-leaves-the-line-as-prose" to
            "Strictness is parsing: the disqualified line is prose end to end, so the document asserts only the absence of the heading, quote and caption scopes those categories pin.",
        "a-tab-as-the-first-character-of-a-definition-term" to
            "The separator is still a space, so the line is a term either way; the leading tab is stripped from the text, which is not a scope. Pinned by `definition-lists`.",
        "a-tab-continues-a-list-item-just-as-two-spaces-do" to
            "Tab-to-column arithmetic is block context; both spellings give the marker tokens `lists` pins.",
        "a-tab-indent-is-the-column-it-reaches-whatever-the-line-holds" to
            "The same column arithmetic for an indented block opener; the marker and quote tokens are pinned by `lists`.",
        "a-tab-reaches-a-footnote-body-s-column-just-as-two-spaces-do" to
            "The same arithmetic against a footnote body column; the footnote tokens are pinned by `footnotes`.",
        "a-tab-separates-two-attributes-and-pads-a-block-as-a-space-does" to
            "The grammar spells every attribute separator as generic whitespace, so a tab and a space give identical scopes, pinned by `attributes`.",
        "a-zero-width-character-in-a-reference-definition-destination" to
            "Invisible characters in a destination are render-time sanitization; the definition tokens are pinned by `reference-link`.",
        "adjacent-block-openers-in-an-attached-run-stay-separate" to
            "Whether two openers stay separate blocks is block structure; the marker and table tokens are pinned by `list-continuation-marker` and `tables`.",
        "adjacent-sibling-lists-survive-the-round-trip" to
            "Sibling-list identity is block structure exercised through the canonical writer; every marker scopes as a list marker, pinned by `lists`.",
        "an-abbreviation-definition-in-an-item-body-is-paragraph-text" to
            "Document-level-only recognition is block context, and the grammar scopes the indented definition as an abbreviation definition anyway - the same false positive `a-div-does-not-define-an-abbreviation-either` records.",
        "an-abbreviation-expands-inside-an-inline-container" to
            "Expansion is render-time; the span, extension and definition tokens are pinned by `inline-span`, `inline-extensions` and `abbreviations`.",
        "an-absorbed-colon-fence-leaves-a-block-quote-s-paragraph-open" to
            "Whether the paragraph stays open is block context; the quote and div tokens are pinned by `blockquote-with-attribution` and `admonitions`.",
        "an-angle-bracket-is-escaped-only-where-it-opens-markup" to
            "Which angle brackets get escaped is render-time output escaping; the prose carries no scope either way.",
        "an-at-sign-is-a-reference-label-character-everywhere-but-the-first-position" to
            "The label charset is resolution; the definition and reference tokens are pinned by `reference-link`, and the name-boundary rule is pinned by `mention-and-tag-name-boundaries`.",
        "an-attribute-block-reaches-the-nested-list-it-precedes" to
            "What a floating attribute line attaches to is block context; the attribute and list tokens are pinned by `list-item-attributes` and `lists`.",
        "an-attribute-line-after-a-continuation-marker-attributes-the-attached-block" to
            "The same attachment question after a continuation marker; tokens pinned by `block-attribute-lines` and `list-continuation-marker`.",
        "an-autolink-body-admits-non-ascii-and-excludes-format-characters" to
            "MEASURED FALSE POSITIVE: the grammar's autolink body is any run of non-space characters, so an invisible format character inside the destination still scopes as an autolink where the spec rejects it. The valid forms are pinned by `autolinks`.",
        "an-editorial-comment-s-bracket-is-content-not-the-close" to
            "Which bracket closes the surrounding link is parsing, and the grammar's link text stops at the first one; the editorial tokens are pinned by `editorial-markup`.",
        "an-empty-abbreviation-term-is-not-a-definition" to
            "Negative case: an empty term defines nothing. The term charset rule is pinned by `an-abbreviation-term-is-one-ascii-alphanumeric-word`.",
        "an-empty-footnote-body-is-written-with-the-empty-sentinel" to
            "The empty sentinel is a canonical-writer spelling rather than a parse; the grammar reads it as an ordinary attribute block, pinned by `attributes`.",
        "an-empty-inline-note-is-literal" to
            "Negative case, and a false positive: the grammar opens an inline note on the delimiter alone, so an empty one still takes note scopes. The valid form is pinned by `inline-footnotes`.",
        "an-image-s-alt-text-closes-where-a-link-s-text-closes" to
            "Bracket balance is parsing; the grammar's alt text stops at the first closing bracket, the same limit the `nested-brackets-in-link-text` golden shows.",
        "an-inline-attribute-block-does-not-span-lines-but-an-attribute-line-does" to
            "Half the rule is a multi-line attribute line, which a line-based grammar cannot carry; the single-line forms are pinned by `attributes` and `block-attribute-lines`.",
        "an-inline-note-s-content-resolves-after-the-note" to
            "Resolution order is a document pass; the note, reference and crossref tokens are pinned by `inline-footnotes`, `reference-link` and `cross-reference`.",
        "an-unclosed-inline-run-in-a-line-block-reaches-the-end-of-the-block" to
            "Line blocks carry no dedicated scope and an inline run crossing a newline is beyond a line-based grammar, as `line-blocks` already records.",
        "an-unclosed-verbatim-run-in-a-row-stops-at-the-closing-pipe" to
            "Where an unclosed run stops is parser tokenization; the row and code tokens are pinned by `tables` and `inline-code`.",
        "an-uppercase-roman-numeral-is-a-list-marker" to
            "Which dialect a marker belongs to is not a token distinction: every ordered marker takes the one numbered-list scope `ordered-list-dialects` pins.",
        "cell-attributes-bind-after-the-kind-and-alignment-markers" to
            "Where in the cell the braces may sit is row parsing, and the grammar matches an attribute block anywhere in a row; the tokens are pinned by `table-cell-attributes` and `attributes`.",
        "code-fence-metadata-slots-must-be-a-space-too" to
            "Separator strictness is parsing, and the info-string slots admit tabs; the well-formed fence is pinned by `fenced-code` and `fenced-code-language-with-punctuation`.",
        "colon-fence-metadata-slots-must-be-a-space-too" to
            "The same strictness on the colon fence, with the same tab-tolerant slots; the well-formed opener is pinned by `admonitions`.",
        "colon-fence-separator-must-be-a-space" to
            "Separator strictness is parsing: the generic div rule accepts a tab after the fence, so both spellings scope alike, pinned by `admonitions` and `generic-divs`.",
        "delimited-comments" to
            "The grammar carries no rule for the delimited comment form, so the run scopes as ordinary text and a golden would pin only its absence.",
        "heading-index-plain-text-covers-visible-leaves-and-rejects-an-empty-key" to
            "Building the heading index from rendered text is a document pass; the heading and reference tokens are pinned by `headings` and `collapsed-reference-link`.",
        "line-endings-and-a-byte-order-mark" to
            "Line endings and a byte order mark are input normalization; the tokenizer works line by line, so neither reaches a scope.",
        "link-and-image-title-slots-must-be-a-space" to
            "Separator strictness is parsing, and the grammar accepts a tab before the title; the well-formed forms are pinned by `links` and `images`.",
        "sibling-markers-that-reach-one-column-are-one-list" to
            "Which list a marker joins is column arithmetic; every marker scopes as a list marker, pinned by `lists` and `marker-line-nested-lists`.",
        "table-cell-padding-must-be-a-space" to
            "Separator strictness is parsing, and the grammar pads cells with generic whitespace; the rows, separator rows and span markers are pinned by `tables`.",
        "the-canonical-writer-glues-a-code-fence-to-its-info-string" to
            "Canonical spacing is a writer decision; both spellings give the same fence and language tokens, pinned by `fenced-code`.",
        "the-continuation-marker-at-an-item-s-own-column-and-what-follows-it" to
            "The item's own column is block context; the marker token is pinned by `list-continuation-marker`.",
        "the-flush-left-line-after-a-container-a-quoted-line-opened" to
            "Whether the flush-left line rejoins the container is block context; the quote and div tokens are pinned by `blockquote-with-attribution` and `admonitions`.",
        "the-inline-attribute-interior-is-space-only-the-attribute-line-is-not" to
            "The grammar spells attribute padding as generic whitespace, so the tab and space interiors scope identically; the valid blocks are pinned by `attributes`.",
        "the-language-sigil-takes-no-padding" to
            "Negative case, and a false positive: the grammar reads the padded form as an empty language item plus a boolean and still scopes the block. The unpadded form is pinned by `a-language-attribute-is-exact-sugar-for-lang`.",
        "the-same-column-written-with-four-spaces" to
            "Column arithmetic with no token of its own; the marker and quote tokens are pinned by `lists`.",
        "the-semantic-registry-holds-no-element-carve-already-spells" to
            "Whether a name is in the semantic registry is render-time; the grammar has no registry and scopes the extension, span and verbatim forms exactly as `inline-extensions`, `inline-span`, `inline-code` and `inline-literal` pin them.",
        "trailing-whitespace-after-a-block-marker" to
            "The grammar allows trailing whitespace on every block marker it matches, so the scopes are identical; the markers are pinned by `thematic-breaks`, `fenced-code`, `admonitions` and `list-continuation-marker`.",
        "trailing-whitespace-on-a-content-line-is-dropped" to
            "Trailing-whitespace stripping is render-time normalization.",
        "two-backticks-are-not-a-code-fence-opening-or-closing" to
            "In the language the two-backtick run becomes a multi-line inline code span, which a line-based grammar cannot carry; the fence widths are pinned by `fenced-code` and `fenced-code-shorter-inner-fence`.",
        "two-blank-lines-detach-a-caption" to
            "Detaching is block context; the caption line scopes as a caption in both documents, as `image-with-caption` and `blockquote-caption-after-a-blank-line` pin.",
        "two-dashes-are-not-a-thematic-break" to
            "Negative case: two dashes are not a break, and the grammar scopes them as a typographic entity instead, which adds no rule-specific token. The valid form is pinned by `thematic-breaks`.",
        "which-inline-content-a-heading-id-is-derived-from" to
            "Id derivation is render-time; the heading and inline tokens are pinned by `headings` and `heading-ids`.",
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
