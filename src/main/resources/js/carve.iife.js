"use strict";
var carve = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // ../../media/mark/data/work/git/carve-js/dist/index.js
  var index_exports = {};
  __export(index_exports, {
    applyMigrationFixes: () => applyMigrationFixes,
    carveToHtml: () => carveToHtml,
    djotMigrationWarnings: () => djotMigrationWarnings,
    formatLintWarnings: () => formatLintWarnings,
    formatMigrationWarnings: () => formatMigrationWarnings,
    lintCarve: () => lintCarve,
    markdownToCarve: () => markdownToCarve,
    parse: () => parse2,
    renderHtml: () => renderHtml2,
    resolve: () => resolve,
    tabNormalize: () => tabNormalize
  });

  // ../../media/mark/data/work/git/carve-js/dist/parse.js
  var RE_HEADING = /^(#{1,6})\s+(.+?)(?:\s+\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?\s*$/;
  var RE_HR = /^(?:-{3,}|\*{3,}|_{3,})\s*$/;
  var RE_FENCE = /^(\s*)(`{3,}|~{3,})\s*([a-zA-Z0-9_+#.-]*)\s*(\[[^\]]*\])?\s*$/;
  var RE_UNORDERED = /^(\s*)[-*]\s+(\S.*)$/;
  var RE_ORDERED = /^(\s*)([0-9]+|[ivxlcdm]+|[IVXLCDM]+|[a-z]|[A-Z])([.)])\s+(\S.*)$/;
  var RE_TASK = /^(\s*)[-*]\s+\[([ xX\-_>?])\]\s+(\S.*)$/;
  var RE_BLOCKQUOTE = /^>\s?(.*)$/;
  var RE_ADMONITION_OPEN = /^(:{3,})\s*([a-zA-Z][\w-]*)\s*(.*)$/;
  var RE_ADMONITION_CLOSE = /^(:{3,})\s*$/;
  var RE_DIV_OPEN = /^(:{3,})\s*(?:\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?\s*$/;
  var RE_DEFLIST_TERM = /^::(?!:)\s+(.+)$/;
  var RE_DEFLIST_DEF = /^: {2,}(.+)$/;
  var RE_ABBR_DEF = /^\*\[([A-Z][A-Z0-9]*)\]:\s+(.+)$/;
  var RE_LINK_DEF = /^\s*\[([^\]]+)\]:\s+(\S+)(?:\s+(?:"([^"]*)"|'([^']*)'))?\s*$/;
  var RE_FOOTNOTE_DEF = /^\[\^([^\]]+)\]:\s+(.+)$/;
  var RE_CAPTION = /^\^\s+(.+)$/;
  var RE_TABLE_ROW = /^\|/;
  var RE_TABLE_CONT = /^\+.*\|\s*$/;
  var RE_BARE_IMAGE = /^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"([^"]*)"|\s+'([^']*)')?\)\s*(?:\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?\s*$/;
  var RE_FRONTMATTER_OPEN = /^---[ \t]*(\w*)\s*$/;
  var RE_FRONTMATTER_CLOSE = /^---\s*$/;
  var RE_RAW_FENCE = /^(`{3,}|~{3,})\s*raw\s+([a-zA-Z][\w-]*)\s*$/;
  var RE_COMMENT_BLOCK = /^%{3,}\s*$/;
  var RE_COMMENT_LINE = /^%%/;
  var RE_FENCE_CLOSER = /^\s{0,3}(`{3,}|~{3,})\s*$/;
  var MAX_NESTING_DEPTH = 200;
  var Lexer = class {
    lines;
    lineOffsets;
    pos = 0;
    // Block-container nesting depth of this (sub-)lexer; 0 at the document top.
    depth = 0;
    frontmatter;
    /** Format applied to a bare `---` fence; set from ParseOptions. */
    defaultFrontmatterFormat = "yaml";
    abbrDefs = /* @__PURE__ */ new Map();
    linkDefs = /* @__PURE__ */ new Map();
    // Footnote definitions keyed by raw label; value is the parsed note
    // body (def line + indented continuation), set by parseFootnoteDef.
    footnoteDefs = /* @__PURE__ */ new Map();
    // True for sub-lexers over already-nested block content (list item /
    // blockquote / admonition bodies). Informational only: under the §10
    // Markdown-like rule a visible block interrupts a paragraph at EVERY level
    // (top and nested) — startsInterruptingBlock no longer branches on this —
    // but sub-lexers still set it to mark their context.
    nested = false;
    // Negative cache for divHasCloser: the smallest line index from which
    // NO bare colon-fence closer of ANY length exists onward. Once a scan
    // proves that, every later bare opener (pos only advances) is O(1),
    // keeping pathological "many unclosed `:::`" input linear.
    divNoCloserFrom = Infinity;
    // Negative cache for fenceHasCloser (paragraph-interruption closer
    // lookahead): the smallest line index from which NO bare fence-closer
    // line exists onward. Once proven, every later fence opener (pos only
    // advances) short-circuits, keeping "many unclosed fences" input linear.
    noFenceCloserFrom = Infinity;
    constructor(source, opts = {}) {
      this.defaultFrontmatterFormat = opts.defaultFrontmatterFormat ?? "yaml";
      this.lines = source.replace(/\r\n?/g, "\n").split("\n");
      if (this.lines.length && this.lines[this.lines.length - 1] === "") {
        this.lines.pop();
      }
      this.lineOffsets = [];
      let offset = 0;
      for (const line of this.lines) {
        this.lineOffsets.push(offset);
        offset += line.length + 1;
      }
    }
    consumeFrontmatter() {
      if (this.lines.length < 2)
        return;
      const open = RE_FRONTMATTER_OPEN.exec(this.lines[0]);
      if (!open)
        return;
      for (let i = 1; i < this.lines.length; i++) {
        if (RE_FRONTMATTER_CLOSE.test(this.lines[i])) {
          const content = this.lines.slice(1, i).join("\n");
          const format = open[1] !== "" ? open[1] : this.defaultFrontmatterFormat;
          this.frontmatter = { format, content };
          this.pos = i + 1;
          return;
        }
      }
    }
    peek(offset = 0) {
      return this.lines[this.pos + offset];
    }
    consume() {
      return this.lines[this.pos++];
    }
    eof() {
      return this.pos >= this.lines.length;
    }
    lineOffset(lineIndex) {
      return this.lineOffsets[lineIndex] ?? 0;
    }
  };
  function parse(source, opts = {}) {
    newlineIndexCache.clear();
    const lexer = new Lexer(source, opts);
    lexer.consumeFrontmatter();
    collectAbbrDefs(lexer);
    collectLinkDefs(lexer);
    const children = parseBlocks(lexer, 0);
    const doc = { type: "document", children };
    if (lexer.frontmatter)
      doc.frontmatter = lexer.frontmatter;
    if (lexer.footnoteDefs.size)
      doc.footnoteDefs = Object.fromEntries(lexer.footnoteDefs);
    return doc;
  }
  function collectAbbrDefs(lexer) {
    for (let idx = 0; idx < lexer.lines.length; idx++) {
      if (idx < lexer.pos)
        continue;
      const m = RE_ABBR_DEF.exec(lexer.lines[idx]);
      if (m)
        lexer.abbrDefs.set(m[1], m[2]);
    }
  }
  function normalizeRefLabel(label) {
    return label.trim().replace(/\s+/g, " ");
  }
  function stripContainerPrefixes(raw) {
    let line = raw;
    let prev;
    do {
      prev = line;
      line = line.replace(/^\s*>\s?/, "").replace(/^\s*(?:[-*]|\d+[.)])\s+(?:\[[ xX\-_>?]\]\s+)?/, "");
    } while (line !== prev);
    return line.replace(/^\s+/, "");
  }
  function collectLinkDefs(lexer) {
    let fence = null;
    for (let idx = 0; idx < lexer.lines.length; idx++) {
      if (idx < lexer.pos)
        continue;
      const raw = lexer.lines[idx];
      const line = stripContainerPrefixes(raw);
      if (fence) {
        const close = line.match(/^ {0,3}([`~]{3,})\s*$/);
        if (close && close[1][0] === fence.ch && close[1].length >= fence.len)
          fence = null;
        continue;
      }
      const open = RE_FENCE.exec(line);
      if (open) {
        fence = { ch: open[2][0], len: open[2].length };
        continue;
      }
      if (RE_ABBR_DEF.test(line))
        continue;
      if (RE_FOOTNOTE_DEF.test(line))
        continue;
      const m = RE_LINK_DEF.exec(line);
      if (m) {
        const def = { href: m[2] };
        const title = m[3] ?? m[4];
        if (title !== void 0)
          def.title = title;
        lexer.linkDefs.set(normalizeRefLabel(m[1]), def);
        continue;
      }
    }
  }
  function parseBlocks(lexer, baseIndent) {
    const out = [];
    let pending = null;
    while (!lexer.eof()) {
      const line = lexer.peek();
      if (line.trim() === "") {
        lexer.consume();
        continue;
      }
      const indent2 = leadingWhitespace(line);
      if (indent2 < baseIndent)
        break;
      const ba = tryCollectBlockAttributes(lexer);
      if (ba) {
        pending = pending ? mergeAttrs(pending, ba) : ba;
        continue;
      }
      const node = parseBlock(lexer);
      if (node) {
        if (pending) {
          node.attrs = mergeAttrs(pending, node.attrs ?? {});
        }
        out.push(node);
      }
      pending = null;
    }
    return out;
  }
  function tryCollectBlockAttributes(lexer) {
    if (!/^\s*\{/.test(lexer.peek()))
      return null;
    let collected = "";
    let n = 0;
    let closed = false;
    for (; ; ) {
      const ln = lexer.peek(n);
      if (ln === void 0)
        break;
      if (n > 0 && ln.trim() === "")
        break;
      collected += (n === 0 ? "" : "\n") + ln;
      n++;
      if (ln.includes("}")) {
        closed = true;
        break;
      }
    }
    if (!closed)
      return null;
    const m = /^\s*\{([\s\S]*)\}\s*$/.exec(collected);
    if (!m)
      return null;
    if (!isValidAttrPayload(m[1]))
      return null;
    const attrs = parseAttrs(m[1]);
    if (isEmptyAttrs(attrs))
      return null;
    for (let k = 0; k < n; k++)
      lexer.consume();
    return attrs;
  }
  function parseBlock(lexer) {
    const startLine = lexer.pos;
    const node = parseBlockInner(lexer);
    if (node)
      attachBlockPos(lexer, node, startLine, lexer.pos);
    return node;
  }
  function parseBlockInner(lexer) {
    const line = lexer.peek();
    if (lexer.depth >= MAX_NESTING_DEPTH)
      return parseParagraph(lexer);
    if (RE_RAW_FENCE.test(line))
      return parseRawBlock(lexer);
    if (RE_FENCE.test(line))
      return parseFence(lexer);
    if (RE_COMMENT_BLOCK.test(line))
      return parseCommentBlock(lexer);
    if (RE_COMMENT_LINE.test(line)) {
      const l = lexer.consume();
      return { type: "comment", block: false, content: l.slice(2).replace(/^\s/, "") };
    }
    if (RE_ADMONITION_OPEN.test(line) && !RE_ADMONITION_CLOSE.test(line))
      return parseAdmonition(lexer);
    if (RE_DIV_OPEN.test(line) && divHasCloser(lexer))
      return parseDiv(lexer);
    if (RE_ABBR_DEF.test(line)) {
      return parseAbbrDef(lexer);
    }
    if (RE_FOOTNOTE_DEF.test(line))
      return parseFootnoteDef(lexer);
    if (RE_LINK_DEF.test(line)) {
      lexer.consume();
      return null;
    }
    if (RE_HR.test(line.trim())) {
      lexer.consume();
      return { type: "thematic-break" };
    }
    if (RE_HEADING.test(line))
      return parseHeading(lexer);
    if (RE_DEFLIST_TERM.test(line))
      return parseDefinitionList(lexer);
    if (RE_BLOCKQUOTE.test(line))
      return parseBlockQuote(lexer);
    if (RE_TASK.test(line) || RE_UNORDERED.test(line) || RE_ORDERED.test(line))
      return parseList(lexer);
    if (RE_TABLE_ROW.test(line))
      return parseTable(lexer);
    if (isBlockImageLine(line))
      return parseBlockImage(lexer);
    return parseParagraph(lexer);
  }
  function attachBlockPos(lexer, node, startLineIndex, endLineIndexExclusive) {
    const endLineIndex = Math.max(startLineIndex, endLineIndexExclusive - 1);
    const endLine = lexer.lines[endLineIndex] ?? "";
    node.pos = {
      startLine: startLineIndex + 1,
      endLine: endLineIndex + 1,
      startColumn: 1,
      endColumn: endLine.length + 1,
      startOffset: lexer.lineOffset(startLineIndex),
      endOffset: lexer.lineOffset(endLineIndex) + endLine.length
    };
  }
  var RE_HEADING_TRAIL_ATTR = /^([\s\S]*?)[ \t]*\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\}$/;
  function parseHeading(lexer) {
    const lineIndex = lexer.pos;
    const line = lexer.consume();
    const m = RE_HEADING.exec(line);
    const level = m[1].length;
    let text = line.replace(/^#{1,6}[ \t]+/, "");
    const sameOrLower = new RegExp(`^#{1,${level}}[ \\t]+(.+)$`);
    while (!lexer.eof()) {
      const next = lexer.peek();
      if (next.trim() === "")
        break;
      const cont = sameOrLower.exec(next);
      if (cont) {
        text += "\n" + cont[1];
        lexer.consume();
        continue;
      }
      if (/^#{1,6}([ \t]|$)/.test(next) || RE_CAPTION.test(next) || RE_COMMENT_BLOCK.test(next)) {
        break;
      }
      text += "\n" + next;
      lexer.consume();
    }
    const node = { type: "heading", level, children: [] };
    const am = RE_HEADING_TRAIL_ATTR.exec(text);
    if (am) {
      const attrs = parseAttrs(am[2]);
      if (!isEmptyAttrs(attrs)) {
        node.attrs = attrs;
        text = am[1].replace(/[ \t]+$/, "");
      }
    }
    const textColumn = line.length - line.replace(/^#{1,6}[ \t]+/, "").length + 1;
    node.children = parseInline(text, lexer.abbrDefs, lexer.linkDefs, {
      baseOffset: lexer.lineOffset(lineIndex) + textColumn - 1,
      startLine: lineIndex + 1,
      startColumn: textColumn
    });
    return node;
  }
  function parseFence(lexer) {
    const open = lexer.consume();
    const m = RE_FENCE.exec(open);
    const indent2 = m[1].length;
    const marker = m[2];
    const lang = m[3] || void 0;
    const label = m[4] ? m[4].slice(1, -1) : void 0;
    const closeRe = new RegExp(`^\\s{0,3}${marker[0]}{${marker.length},}\\s*$`);
    const lines = [];
    while (!lexer.eof()) {
      const ln = lexer.peek();
      if (closeRe.test(ln) && ln.length - ln.trimStart().length <= 3) {
        lexer.consume();
        break;
      }
      lexer.consume();
      lines.push(ln.slice(Math.min(indent2, leadingWhitespace(ln))));
    }
    const cb = { type: "code-block", content: lines.join("\n") };
    if (lang)
      cb.lang = lang;
    if (label !== void 0)
      cb.label = label;
    return cb;
  }
  function parseRawBlock(lexer) {
    const m = RE_RAW_FENCE.exec(lexer.consume());
    const marker = m[1];
    const format = m[2];
    const closeRe = new RegExp(`^\\s{0,3}${marker[0]}{${marker.length},}\\s*$`);
    const lines = [];
    while (!lexer.eof()) {
      const ln = lexer.peek();
      if (closeRe.test(ln)) {
        lexer.consume();
        break;
      }
      lexer.consume();
      lines.push(ln);
    }
    return { type: "raw-block", format, content: lines.join("\n") };
  }
  function parseCommentBlock(lexer) {
    const open = lexer.consume().trim();
    const lines = [];
    while (!lexer.eof()) {
      const ln = lexer.peek();
      if (ln.trim() === open) {
        lexer.consume();
        break;
      }
      lexer.consume();
      lines.push(ln);
    }
    return { type: "comment", block: true, content: lines.join("\n") };
  }
  function parseFootnoteDef(lexer) {
    const m = RE_FOOTNOTE_DEF.exec(lexer.consume());
    const label = m[1].trim();
    const bodyLines = [m[2]];
    let pendingBlanks = 0;
    let contentCol = -1;
    while (!lexer.eof()) {
      const ln = lexer.peek();
      if (ln.trim() === "") {
        pendingBlanks++;
        lexer.consume();
        continue;
      }
      const ws = leadingWhitespace(ln);
      if (ws >= 2) {
        if (contentCol === -1)
          contentCol = ws;
        for (let k = 0; k < pendingBlanks; k++)
          bodyLines.push("");
        pendingBlanks = 0;
        bodyLines.push(ln.slice(Math.min(contentCol, ws)));
        lexer.consume();
      } else {
        break;
      }
    }
    if (!lexer.footnoteDefs.has(label)) {
      const sub = new Lexer(bodyLines.join("\n"));
      sub.abbrDefs = lexer.abbrDefs;
      sub.linkDefs = lexer.linkDefs;
      sub.footnoteDefs = lexer.footnoteDefs;
      sub.nested = true;
      sub.depth = lexer.depth + 1;
      lexer.footnoteDefs.set(label, parseBlocks(sub, 0));
    }
    return null;
  }
  function parseAdmonition(lexer) {
    const open = lexer.consume();
    const m = RE_ADMONITION_OPEN.exec(open);
    const fence = m[1].length;
    const kind = m[2];
    const tail = m[3]?.trim() ?? "";
    const quoted = /^"([^"]*)"\s*(?:\{[^}]*\})?$/.exec(tail);
    const titleText = quoted ? quoted[1] : void 0;
    const inner = [];
    while (!lexer.eof()) {
      const ln = lexer.peek();
      const c = RE_ADMONITION_CLOSE.exec(ln);
      if (c && c[1].length >= fence) {
        lexer.consume();
        break;
      }
      lexer.consume();
      inner.push(ln);
    }
    const subLexer = new Lexer(inner.join("\n"));
    subLexer.abbrDefs = lexer.abbrDefs;
    subLexer.linkDefs = lexer.linkDefs;
    subLexer.footnoteDefs = lexer.footnoteDefs;
    subLexer.nested = true;
    subLexer.depth = lexer.depth + 1;
    const children = parseBlocks(subLexer, 0);
    const node = { type: "admonition", kind, children };
    if (titleText !== void 0) {
      node.title = parseInline(titleText, lexer.abbrDefs, lexer.linkDefs);
    }
    return node;
  }
  function divHasCloser(lexer) {
    const start = lexer.pos + 1;
    if (start >= lexer.divNoCloserFrom)
      return false;
    const fence = /^(:{3,})/.exec(lexer.peek())[1].length;
    let sawAnyCloser = false;
    for (let i = start; i < lexer.lines.length; i++) {
      const c = RE_ADMONITION_CLOSE.exec(lexer.lines[i]);
      if (c) {
        sawAnyCloser = true;
        if (c[1].length >= fence)
          return true;
      }
    }
    if (!sawAnyCloser)
      lexer.divNoCloserFrom = start;
    return false;
  }
  function parseDiv(lexer) {
    const m = RE_DIV_OPEN.exec(lexer.consume());
    const fence = m[1].length;
    const attrSrc = m[2];
    const inner = [];
    while (!lexer.eof()) {
      const ln = lexer.peek();
      const c = RE_ADMONITION_CLOSE.exec(ln);
      if (c && c[1].length >= fence) {
        lexer.consume();
        break;
      }
      lexer.consume();
      inner.push(ln);
    }
    const subLexer = new Lexer(inner.join("\n"));
    subLexer.abbrDefs = lexer.abbrDefs;
    subLexer.linkDefs = lexer.linkDefs;
    subLexer.footnoteDefs = lexer.footnoteDefs;
    subLexer.nested = true;
    subLexer.depth = lexer.depth + 1;
    const node = { type: "div", children: parseBlocks(subLexer, 0) };
    if (attrSrc)
      node.attrs = parseAttrs(attrSrc);
    return node;
  }
  function parseDefinitionList(lexer) {
    const items = [];
    const parseDefBody = (first) => {
      const bodyLines = [first];
      while (!lexer.eof()) {
        const ln = lexer.peek();
        if (ln.trim() !== "" && leadingWhitespace(ln) >= 3) {
          bodyLines.push(ln.replace(/^\s+/, ""));
          lexer.consume();
        } else
          break;
      }
      const sub = new Lexer(bodyLines.join("\n"));
      sub.abbrDefs = lexer.abbrDefs;
      sub.linkDefs = lexer.linkDefs;
      sub.footnoteDefs = lexer.footnoteDefs;
      sub.nested = true;
      sub.depth = lexer.depth + 1;
      return parseBlocks(sub, 0);
    };
    while (!lexer.eof() && RE_DEFLIST_TERM.test(lexer.peek())) {
      const terms = [];
      const definitions = [];
      while (!lexer.eof()) {
        const t = RE_DEFLIST_TERM.exec(lexer.peek());
        if (!t)
          break;
        lexer.consume();
        terms.push(parseInline(t[1], lexer.abbrDefs, lexer.linkDefs));
      }
      while (!lexer.eof()) {
        const d = RE_DEFLIST_DEF.exec(lexer.peek());
        if (!d)
          break;
        lexer.consume();
        definitions.push(parseDefBody(d[1]));
      }
      items.push({ terms, definitions });
      if (!lexer.eof() && lexer.peek().trim() === "") {
        let look = 1;
        while (lexer.peek(look)?.trim() === "")
          look++;
        const next = lexer.peek(look);
        if (next && RE_DEFLIST_TERM.test(next))
          for (let k = 0; k < look; k++)
            lexer.consume();
        else
          break;
      }
    }
    return { type: "definition-list", items };
  }
  function parseAbbrDef(lexer) {
    const line = lexer.consume();
    const m = RE_ABBR_DEF.exec(line);
    return { type: "abbreviation-def", abbr: m[1], expansion: m[2] };
  }
  function trackBlockQuoteLazyState(content, state) {
    if (state.inComment) {
      const c = /^(%{3,})\s*$/.exec(content);
      if (c && c[1].length >= state.commentLen)
        state.inComment = false;
      state.paragraphOpen = false;
      return;
    }
    if (state.inFence) {
      if (state.fenceClose.test(content))
        state.inFence = false;
      state.paragraphOpen = false;
      return;
    }
    if (content.trim() === "") {
      state.paragraphOpen = false;
      return;
    }
    if (!state.paragraphOpen) {
      const fence = RE_FENCE.exec(content);
      if (fence) {
        const marker = fence[2];
        state.inFence = true;
        state.fenceClose = new RegExp(`^\\s{0,3}${marker[0]}{${marker.length},}\\s*$`);
        state.paragraphOpen = false;
        return;
      }
      const comment = /^(%{3,})\s*$/.exec(content);
      if (comment) {
        state.inComment = true;
        state.commentLen = comment[1].length;
        state.paragraphOpen = false;
        return;
      }
      if (RE_DIV_OPEN.test(content) || RE_ADMONITION_OPEN.test(content)) {
        state.paragraphOpen = false;
        return;
      }
    }
    state.paragraphOpen = true;
  }
  function parseBlockQuote(lexer) {
    const inner = [];
    const state = {
      inFence: false,
      fenceClose: null,
      inComment: false,
      commentLen: 0,
      paragraphOpen: false
    };
    while (!lexer.eof()) {
      const ln = lexer.peek();
      const m = RE_BLOCKQUOTE.exec(ln);
      if (m) {
        lexer.consume();
        const content = m[1] ?? "";
        inner.push(content);
        trackBlockQuoteLazyState(content, state);
        continue;
      }
      if (ln.trim() === "" || RE_LINK_DEF.test(ln) || RE_FOOTNOTE_DEF.test(ln) || RE_ABBR_DEF.test(ln) || RE_COMMENT_LINE.test(ln) || RE_COMMENT_BLOCK.test(ln) || RE_CAPTION.test(ln)) {
        break;
      }
      if (!state.paragraphOpen)
        break;
      lexer.consume();
      inner.push(ln);
      trackBlockQuoteLazyState(ln, state);
    }
    const subLexer = new Lexer(inner.join("\n"));
    subLexer.abbrDefs = lexer.abbrDefs;
    subLexer.linkDefs = lexer.linkDefs;
    subLexer.footnoteDefs = lexer.footnoteDefs;
    subLexer.nested = true;
    subLexer.depth = lexer.depth + 1;
    const children = parseBlocks(subLexer, 0);
    const bq = { type: "blockquote", children };
    let lookahead = 0;
    while (!lexer.eof() && lexer.peek(lookahead)?.trim() === "")
      lookahead++;
    const next = lexer.peek(lookahead);
    if (next) {
      const cap = RE_CAPTION.exec(next);
      if (cap && lookahead <= 1) {
        for (let i = 0; i <= lookahead; i++)
          lexer.consume();
        return {
          type: "figure",
          target: bq,
          caption: parseInline(cap[1], lexer.abbrDefs, lexer.linkDefs)
        };
      }
    }
    return bq;
  }
  function isBlockImageLine(line) {
    const m = RE_BARE_IMAGE.exec(line);
    return m !== null && (m[5] === void 0 || !isEmptyAttrs(parseAttrs(m[5])));
  }
  function parseBlockImage(lexer) {
    const line = lexer.consume();
    const m = RE_BARE_IMAGE.exec(line);
    const img = { type: "image", src: m[2], alt: m[1] };
    const title = m[3] ?? m[4];
    if (title)
      img.title = title;
    if (m[5])
      img.attrs = parseAttrs(m[5]);
    let lookahead = 0;
    while (!lexer.eof() && lexer.peek(lookahead)?.trim() === "")
      lookahead++;
    const next = lexer.peek(lookahead);
    if (next) {
      const cap = RE_CAPTION.exec(next);
      if (cap && lookahead <= 1) {
        for (let i = 0; i <= lookahead; i++)
          lexer.consume();
        return {
          type: "figure",
          target: img,
          caption: parseInline(cap[1], lexer.abbrDefs, lexer.linkDefs)
        };
      }
    }
    return img;
  }
  function unorderedMarkerChar(line) {
    return line.replace(/^\s*/, "").charAt(0);
  }
  function matchListMarker(line, isTask, isOrdered) {
    if (isTask)
      return RE_TASK.exec(line);
    if (isOrdered) {
      if (RE_TASK.test(line))
        return null;
      return RE_ORDERED.exec(line);
    }
    if (RE_TASK.test(line) || RE_ORDERED.test(line))
      return null;
    return RE_UNORDERED.exec(line);
  }
  function romanToInt(s) {
    const map = { i: 1, v: 5, x: 10, l: 50, c: 100, d: 500, m: 1e3 };
    const t = s.toLowerCase();
    let total = 0;
    for (let k = 0; k < t.length; k++) {
      const cur = map[t[k]];
      const nxt = map[t[k + 1]] ?? 0;
      total += cur < nxt ? -cur : cur;
    }
    return total;
  }
  function olKindMatches(marker, kind) {
    switch (kind) {
      case "dec":
        return /^[0-9]+$/.test(marker);
      case "alo":
        return /^[a-z]$/.test(marker);
      case "aup":
        return /^[A-Z]$/.test(marker);
      case "rlo":
        return /^[ivxlcdm]+$/.test(marker);
      case "rup":
        return /^[IVXLCDM]+$/.test(marker);
    }
  }
  function olKindOf(marker, nextMarker) {
    if (/^[0-9]+$/.test(marker))
      return "dec";
    const upper = marker === marker.toUpperCase();
    const romanChars = /^[ivxlcdm]+$/i.test(marker);
    if (romanChars && marker.length > 1)
      return upper ? "rup" : "rlo";
    if (romanChars) {
      if (nextMarker !== null && nextMarker === nextMarker.toUpperCase() === upper) {
        if (/^[ivxlcdm]+$/i.test(nextMarker) && romanToInt(nextMarker) === romanToInt(marker) + 1) {
          return upper ? "rup" : "rlo";
        }
        if (/^[a-z]$/i.test(nextMarker) && nextMarker.toLowerCase().charCodeAt(0) === marker.toLowerCase().charCodeAt(0) + 1) {
          return upper ? "aup" : "alo";
        }
      }
      if (marker.toLowerCase() === "i")
        return upper ? "rup" : "rlo";
    }
    return upper ? "aup" : "alo";
  }
  function olStartOf(marker, kind) {
    if (kind === "dec")
      return parseInt(marker, 10);
    if (kind === "rlo" || kind === "rup")
      return romanToInt(marker);
    return marker.toLowerCase().charCodeAt(0) - 96;
  }
  function olTypeOf(kind) {
    return kind === "dec" ? "" : kind === "alo" ? "a" : kind === "aup" ? "A" : kind === "rlo" ? "i" : "I";
  }
  function orderedContinues(line, kind, delim) {
    const o = RE_ORDERED.exec(line);
    return o !== null && o[3] === delim && olKindMatches(o[2], kind);
  }
  function lineOpensBlock(line) {
    return RE_RAW_FENCE.test(line) || RE_FENCE.test(line) || RE_COMMENT_BLOCK.test(line) || RE_ABBR_DEF.test(line) || RE_FOOTNOTE_DEF.test(line) || RE_LINK_DEF.test(line) || RE_HR.test(line.trim()) || RE_HEADING.test(line) || RE_DEFLIST_TERM.test(line) || RE_BLOCKQUOTE.test(line) || RE_TASK.test(line) || RE_UNORDERED.test(line) || RE_ORDERED.test(line) || RE_TABLE_ROW.test(line) || RE_ADMONITION_OPEN.test(line) && !RE_ADMONITION_CLOSE.test(line) || RE_DIV_OPEN.test(line);
  }
  function lazyContinuationEndsList(line, lexer) {
    return RE_RAW_FENCE.test(line) || RE_FENCE.test(line) || RE_COMMENT_BLOCK.test(line) || RE_ADMONITION_OPEN.test(line) && !RE_ADMONITION_CLOSE.test(line) || RE_DIV_OPEN.test(line) && divHasCloser(lexer) || RE_ABBR_DEF.test(line) || RE_FOOTNOTE_DEF.test(line) || RE_LINK_DEF.test(line) || RE_HR.test(line.trim()) || RE_HEADING.test(line) || RE_DEFLIST_TERM.test(line) || RE_BLOCKQUOTE.test(line) || RE_TASK.test(line) || RE_UNORDERED.test(line) || RE_ORDERED.test(line) || RE_TABLE_ROW.test(line) || isBlockImageLine(line);
  }
  function parseList(lexer) {
    const first = lexer.peek();
    const baseIndent = leadingWhitespace(first);
    const isTask = RE_TASK.test(first);
    const isOrdered = !isTask && RE_ORDERED.test(first);
    const firstMarkerChar = isOrdered ? "" : unorderedMarkerChar(first);
    const firstOrdered = isOrdered ? RE_ORDERED.exec(first) : null;
    const orderedDelim = firstOrdered ? firstOrdered[3] : "";
    let orderedKind = "dec";
    let orderedStart = 1;
    if (firstOrdered) {
      let k = 1;
      for (; lexer.peek(k) !== void 0; k++) {
        const ln = lexer.peek(k);
        if (ln.trim() !== "" && leadingWhitespace(ln) <= baseIndent)
          break;
      }
      const nextLine = lexer.peek(k);
      const nm = nextLine !== void 0 && leadingWhitespace(nextLine) === baseIndent ? RE_ORDERED.exec(nextLine) : null;
      orderedKind = olKindOf(firstOrdered[2], nm ? nm[2] : null);
      orderedStart = olStartOf(firstOrdered[2], orderedKind);
    }
    const items = [];
    let loose = false;
    while (!lexer.eof()) {
      const line = lexer.peek();
      if (line.trim() === "") {
        break;
      }
      if (leadingWhitespace(line) !== baseIndent)
        break;
      const m = matchListMarker(line, isTask, isOrdered);
      if (!m)
        break;
      if (!isOrdered && unorderedMarkerChar(line) !== firstMarkerChar)
        break;
      if (isOrdered && !orderedContinues(line, orderedKind, orderedDelim))
        break;
      let content;
      let checked;
      if (isTask) {
        checked = m[2].toLowerCase() === "x";
        content = m[3];
      } else if (isOrdered) {
        content = m[4];
      } else {
        content = m[2];
      }
      const contentCol = m[0].length - content.length;
      lexer.consume();
      if (content.trim() === "+") {
        const attached = [];
        while (!lexer.eof()) {
          const a = lexer.peek();
          if (a.trim() === "")
            break;
          const ind = leadingWhitespace(a);
          if (ind < baseIndent)
            break;
          if (ind === baseIndent) {
            const am = matchListMarker(a, isTask, isOrdered);
            const sibling = am && (isOrdered ? orderedContinues(a, orderedKind, orderedDelim) : unorderedMarkerChar(a) === firstMarkerChar);
            if (sibling || a.trim() === "+")
              break;
          }
          attached.push(a.slice(baseIndent));
          lexer.consume();
        }
        const sub2 = new Lexer(attached.join("\n"));
        sub2.abbrDefs = lexer.abbrDefs;
        sub2.linkDefs = lexer.linkDefs;
        sub2.footnoteDefs = lexer.footnoteDefs;
        sub2.nested = true;
        sub2.depth = lexer.depth + 1;
        const fbChildren = parseBlocks(sub2, 0);
        const fbItem = { type: "list-item", children: fbChildren };
        if (checked !== void 0)
          fbItem.checked = checked;
        items.push(fbItem);
        continue;
      }
      const nested = [];
      let pendingBlanks = 0;
      while (!lexer.eof()) {
        const l = lexer.peek();
        if (l.trim() === "") {
          pendingBlanks++;
          lexer.consume();
          continue;
        }
        if (leadingWhitespace(l) === baseIndent && l.trim() === "+") {
          lexer.consume();
          pendingBlanks = 0;
          nested.push("");
          while (!lexer.eof()) {
            const a = lexer.peek();
            if (a.trim() === "")
              break;
            const ind = leadingWhitespace(a);
            if (ind < baseIndent)
              break;
            if (ind === baseIndent) {
              const am = matchListMarker(a, isTask, isOrdered);
              const sibling = am && (isOrdered ? orderedContinues(a, orderedKind, orderedDelim) : unorderedMarkerChar(a) === firstMarkerChar);
              if (sibling || a.trim() === "+")
                break;
            }
            nested.push(a.slice(baseIndent));
            lexer.consume();
          }
          continue;
        }
        if (leadingWhitespace(l) >= contentCol) {
          for (let k = 0; k < pendingBlanks; k++)
            nested.push("");
          pendingBlanks = 0;
          nested.push(l.slice(contentCol));
          lexer.consume();
        } else if (pendingBlanks === 0 && !lazyContinuationEndsList(l, lexer)) {
          nested.push(l);
          lexer.consume();
        } else {
          break;
        }
      }
      if (pendingBlanks > 0 && !lexer.eof()) {
        const nextLine = lexer.peek();
        if (leadingWhitespace(nextLine) === baseIndent && matchListMarker(nextLine, isTask, isOrdered) && (isOrdered ? orderedContinues(nextLine, orderedKind, orderedDelim) : unorderedMarkerChar(nextLine) === firstMarkerChar)) {
          loose = true;
        }
      }
      for (let k = 0; k < nested.length; k++) {
        if (nested[k] !== "")
          continue;
        let j = k + 1;
        while (j < nested.length && nested[j] === "")
          j++;
        if (j < nested.length && !lineOpensBlock(nested[j])) {
          loose = true;
          break;
        }
      }
      const sub = new Lexer([content, ...nested].join("\n"));
      sub.abbrDefs = lexer.abbrDefs;
      sub.linkDefs = lexer.linkDefs;
      sub.footnoteDefs = lexer.footnoteDefs;
      sub.nested = true;
      sub.depth = lexer.depth + 1;
      const children = parseBlocks(sub, 0);
      const item = { type: "list-item", children };
      if (checked !== void 0)
        item.checked = checked;
      items.push(item);
    }
    const list = { type: "list", ordered: isOrdered, tight: !loose, items };
    if (isOrdered) {
      if (orderedStart !== 1)
        list.start = orderedStart;
      const t = olTypeOf(orderedKind);
      if (t)
        list.olType = t;
    }
    return list;
  }
  function parseCellMarkers(src) {
    let i = 0;
    let header = false;
    if (src[i] === "=") {
      header = true;
      i++;
    }
    let align;
    const a = src[i];
    if (a === ">") {
      align = "right";
      i++;
    } else if (a === "<") {
      align = "left";
      i++;
    } else if (a === "~") {
      align = "center";
      i++;
    }
    if (i > 0) {
      const content = src.slice(i).trim();
      return align ? { header, align, content } : { header, content };
    }
    const trimmed = src.trim();
    if (trimmed === "^")
      return { header: false, span: "rowspan", content: "" };
    if (trimmed === "<")
      return { header: false, span: "colspan", content: "" };
    return { header: false, content: trimmed };
  }
  function parseTable(lexer) {
    const rawRows = [];
    let lastRaw = null;
    while (!lexer.eof() && (RE_TABLE_ROW.test(lexer.peek()) || RE_TABLE_CONT.test(lexer.peek()))) {
      const line = lexer.peek();
      if (RE_TABLE_CONT.test(line)) {
        if (!lastRaw)
          break;
        lexer.consume();
        splitTableRow(line).forEach((src, idx) => {
          const frag = src.trim();
          const target = lastRaw[idx];
          if (!frag || !target || target.span)
            return;
          target.raw = target.raw ? `${target.raw} ${frag}` : frag;
        });
        continue;
      }
      lexer.consume();
      const raw = splitTableRow(line).map((src) => {
        const { header, span, align, content } = parseCellMarkers(src);
        const c = { header, raw: content };
        if (span)
          c.span = span;
        if (align)
          c.align = align;
        return c;
      });
      rawRows.push(raw);
      lastRaw = raw;
    }
    const rows = rawRows.map((rc) => ({
      type: "table-row",
      cells: rc.map((c) => {
        const cell = {
          type: "table-cell",
          header: c.header,
          children: c.span ? [] : parseInline(c.raw, lexer.abbrDefs, lexer.linkDefs)
        };
        if (c.span)
          cell.span = c.span;
        if (c.align)
          cell.align = c.align;
        return cell;
      })
    }));
    const table = { type: "table", rows };
    let lookahead = 0;
    while (!lexer.eof() && lexer.peek(lookahead)?.trim() === "")
      lookahead++;
    const next = lexer.peek(lookahead);
    if (next) {
      const cap = RE_CAPTION.exec(next);
      if (cap && lookahead <= 1) {
        for (let i = 0; i <= lookahead; i++)
          lexer.consume();
        table.caption = parseInline(cap[1], lexer.abbrDefs, lexer.linkDefs);
      }
    }
    return table;
  }
  function splitTableRow(line) {
    const cells = [];
    let buf = "";
    let inCode = false;
    let i = 0;
    if (line[0] === "|" || line[0] === "+")
      i = 1;
    for (; i < line.length; i++) {
      const ch = line[i];
      if (ch === "`")
        inCode = !inCode;
      if (ch === "\\" && line[i + 1] === "|") {
        buf += "|";
        i++;
        continue;
      }
      if (ch === "|" && !inCode) {
        cells.push(buf);
        buf = "";
        continue;
      }
      buf += ch;
    }
    if (buf.trim() !== "")
      cells.push(buf);
    return cells;
  }
  function fenceHasCloser(lexer, marker) {
    const start = lexer.pos + 1;
    if (start >= lexer.noFenceCloserFrom)
      return false;
    const closeRe = new RegExp(`^\\s{0,3}${marker[0]}{${marker.length},}\\s*$`);
    let sawAnyCloser = false;
    for (let i = start; i < lexer.lines.length; i++) {
      const l = lexer.lines[i];
      if (closeRe.test(l))
        return true;
      if (RE_FENCE_CLOSER.test(l))
        sawAnyCloser = true;
    }
    if (!sawAnyCloser)
      lexer.noFenceCloserFrom = start;
    return false;
  }
  function startsInterruptingBlock(lexer) {
    const ln = lexer.peek();
    if (ln === void 0)
      return false;
    let i = 0;
    while (i < ln.length && (ln.charCodeAt(i) === 32 || ln.charCodeAt(i) === 9))
      i++;
    switch (ln[i]) {
      case "#":
        return RE_HEADING.test(ln);
      case ">":
        return RE_BLOCKQUOTE.test(ln);
      case "|":
        return RE_TABLE_ROW.test(ln) && /\|\s*$/.test(ln);
      case "`":
      case "~":
        if (RE_RAW_FENCE.test(ln))
          return fenceHasCloser(lexer, RE_RAW_FENCE.exec(ln)[1]);
        if (RE_FENCE.test(ln))
          return fenceHasCloser(lexer, RE_FENCE.exec(ln)[2]);
        return false;
      case "-":
        return RE_HR.test(ln.trim()) || RE_TASK.test(ln) || RE_UNORDERED.test(ln);
      case "+":
        return RE_TASK.test(ln) || RE_UNORDERED.test(ln);
      case "*":
        return RE_ABBR_DEF.test(ln) || RE_HR.test(ln.trim()) || RE_TASK.test(ln) || RE_UNORDERED.test(ln);
      case "_":
        return RE_HR.test(ln.trim());
      case ":":
        if (RE_DEFLIST_TERM.test(ln))
          return true;
        if (RE_ADMONITION_OPEN.test(ln) && !RE_ADMONITION_CLOSE.test(ln) || RE_DIV_OPEN.test(ln))
          return divHasCloser(lexer);
        return false;
      case "[":
        return RE_LINK_DEF.test(ln) || RE_FOOTNOTE_DEF.test(ln);
      case "%":
        return RE_COMMENT_LINE.test(ln) || RE_COMMENT_BLOCK.test(ln);
      default:
        return false;
    }
  }
  function parseParagraph(lexer) {
    const lines = [];
    const startLineIndex = lexer.pos;
    while (!lexer.eof()) {
      const ln = lexer.peek();
      if (ln.trim() === "")
        break;
      if (lines.length > 0 && startsInterruptingBlock(lexer))
        break;
      lexer.consume();
      lines.push(ln);
    }
    const text = lines.map((ln, idx) => idx === 0 ? ln : ln.replace(/^[ \t]+/, "")).join("\n");
    return {
      type: "paragraph",
      children: parseInline(text, lexer.abbrDefs, lexer.linkDefs, {
        baseOffset: lexer.lineOffset(startLineIndex),
        startLine: startLineIndex + 1,
        startColumn: 1
      })
    };
  }
  function leadingWhitespace(line) {
    let n = 0;
    while (n < line.length && (line[n] === " " || line[n] === "	"))
      n++;
    return n;
  }
  var RE_FOOTNOTE_REF = /^\[\^([^\]]+)\]/;
  var RE_EXTENSION = /^:([a-zA-Z][\w-]*)\[([^\]]*)\](?:\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?/;
  var RE_RAW_INLINE = /^\{=([a-zA-Z][\w-]*)\}/;
  var RE_EMOJI = /^:([a-zA-Z0-9][\w+-]*):/;
  var RE_AUTOLINK = /^<([a-zA-Z][a-zA-Z0-9+.\-]*:[^>\s]+|[^\s>@]+@[^\s>]+)>/;
  var RE_CROSSREF = /^<\/#([^>\s]+)>/;
  var RE_INLINE_ATTR = /^\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\}/;
  var RE_LINK_TAIL = /^\(([^)\s]*)(?:\s+"([^"]*)"|\s+'([^']*)')?\)(?:\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?/;
  var RE_REF_TAIL = /^\[([^\]]*)\](?:\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')+)\})?/;
  var RE_SPAN_TAIL = /^\{((?:[^}"'\n]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')*)\}/;
  function verbatimSpanEnd(text, i) {
    let openLen = 1;
    while (text[i + openLen] === "`")
      openLen++;
    let k = i + openLen;
    while (k < text.length) {
      if (text[k] === "`") {
        let m = 1;
        while (text[k + m] === "`")
          m++;
        if (m === openLen)
          return { end: k + openLen, closed: true, openLen };
        k += m;
      } else {
        k++;
      }
    }
    return { end: text.length, closed: false, openLen };
  }
  function buildBracketMap(s) {
    const map = {};
    const stack = [];
    for (let j = 0; j < s.length; j++) {
      const ch = s[j];
      if (ch === "\\") {
        j++;
        continue;
      }
      if (ch === "`") {
        j = verbatimSpanEnd(s, j).end - 1;
        continue;
      }
      if (ch === "[") {
        stack.push(j);
      } else if (ch === "]") {
        const open = stack.pop();
        if (open !== void 0)
          map[open] = j;
      }
    }
    return map;
  }
  var RE_CRITIC_INS = /^\{\+([^}]*)\+\}/;
  var RE_CRITIC_DEL = /^\{-([^}]*)-\}/;
  var RE_CRITIC_SUB = /^\{~([^}]*)~>([^}]*)~\}/;
  var RE_CRITIC_CMT = /^\{#([^}]*)#\}/;
  var RE_MENTION = /^@([a-zA-Z][\w-]*(?:\.\w+)*)/;
  var RE_TAG = /^#([a-zA-Z][\w-]*(?:\.\w+)*)/;
  var SMART_TOKENS = [
    ["<->", "\u2194"],
    ["(tm)", "\u2122"],
    ["...", "\u2026"],
    ["->", "\u2192"],
    ["<-", "\u2190"],
    ["=>", "\u21D2"],
    ["<=", "\u2264"],
    [">=", "\u2265"],
    ["!=", "\u2260"],
    ["+-", "\xB1"],
    ["(c)", "\xA9"],
    ["(r)", "\xAE"]
  ];
  function allocateDashes(n) {
    if (n % 3 === 0)
      return "\u2014".repeat(n / 3);
    if (n % 2 === 0)
      return "\u2013".repeat(n / 2);
    let em = Math.floor(n / 3);
    let en;
    if (n % 3 === 1) {
      em -= 1;
      en = 2;
    } else {
      en = 1;
    }
    return "\u2014".repeat(em) + "\u2013".repeat(en);
  }
  var isAlnum = (ch) => /[A-Za-z0-9]/.test(ch);
  var isQuoteOpenContext = (prev) => prev === "" || /[\s([{\-–—/]/.test(prev) || prev === "\u201C" || prev === "\u2018";
  function smartToken(text, i, prev) {
    for (const [tok, out] of SMART_TOKENS) {
      if (text.startsWith(tok, i))
        return { out, len: tok.length };
    }
    if (text[i] === "-" && text[i + 1] === "-") {
      let n = 0;
      while (text[i + n] === "-")
        n++;
      return { out: allocateDashes(n), len: n };
    }
    const c = text[i];
    if (c === '"') {
      return { out: isQuoteOpenContext(prev) ? "\u201C" : "\u201D", len: 1 };
    }
    if (c === "'") {
      const next = text[i + 1] ?? "";
      const apostrophe = isAlnum(prev) || /[0-9]/.test(next) || !isQuoteOpenContext(prev);
      return { out: apostrophe ? "\u2019" : "\u2018", len: 1 };
    }
    return null;
  }
  function parseInline(text, abbrDefs, linkDefs = /* @__PURE__ */ new Map(), source = inlineSource()) {
    const nodes = applyAbbreviations(scanInline(text, source), abbrDefs);
    return applyLinkDefs(nodes, linkDefs);
  }
  function inlineSource(overrides = {}) {
    return {
      baseOffset: overrides.baseOffset ?? 0,
      startLine: overrides.startLine ?? 1,
      startColumn: overrides.startColumn ?? 1
    };
  }
  function scanInline(text, source = inlineSource()) {
    const out = [];
    let i = 0;
    let buf = "";
    let bufStart = 0;
    const bracketClose = text.includes("[") ? buildBracketMap(text) : {};
    const flush = () => {
      if (buf) {
        out.push(withPos({ type: "text", value: buf }, source, text, bufStart, i));
        buf = "";
      }
    };
    const append = (value) => {
      if (!buf)
        bufStart = i;
      buf += value;
    };
    while (i < text.length) {
      const c = text[i];
      const rest = text.slice(i);
      if (c === "\\" && text[i + 1] === "\n") {
        flush();
        out.push(withPos({ type: "hard-break" }, source, text, i, i + 2));
        i += 2;
        continue;
      }
      if (c === "\\" && text[i + 1] === " ") {
        append("\xA0");
        i += 2;
        continue;
      }
      if (c === "\\" && i + 1 < text.length) {
        const nxt = text[i + 1];
        if (/[\\`*_{}\[\]()#+\-.!~^/<>@%|=,"'$&:;?]/.test(nxt)) {
          append(nxt);
          i += 2;
          continue;
        }
      }
      {
        const prevForQuote = buf.length ? buf[buf.length - 1] : out.length ? "x" : "";
        const st = smartToken(text, i, prevForQuote);
        if (st) {
          append(st.out);
          i += st.len;
          continue;
        }
      }
      if (c === "%" && text[i + 1] === "%" && (i === 0 || /[ \t]/.test(text[i - 1]))) {
        const trimmed = buf.replace(/[ \t]+$/, "");
        const commentStart = i - (buf.length - trimmed.length);
        if (trimmed) {
          out.push(withPos({ type: "text", value: trimmed }, source, text, bufStart, commentStart));
        }
        buf = "";
        const nl = text.indexOf("\n", i);
        const end = nl === -1 ? text.length : nl;
        const content = text.slice(i + 2, end).replace(/^[ \t]/, "");
        out.push(withPos({ type: "comment", block: false, content }, source, text, commentStart, end));
        i = end;
        continue;
      }
      if (c === "`") {
        const { end, closed, openLen } = verbatimSpanEnd(text, i);
        flush();
        if (!closed) {
          const value = text.slice(i + openLen).replace(/\s+$/, "");
          out.push(withPos({ type: "code", value }, source, text, i, text.length));
          i = text.length;
          continue;
        }
        const inner = text.slice(i + openLen, end - openLen).replace(/^ (.*) $/, "$1");
        const raw = RE_RAW_INLINE.exec(text.slice(end));
        if (raw) {
          const len = end - i + raw[0].length;
          out.push(withPos({ type: "raw-inline", format: raw[1], content: inner }, source, text, i, i + len));
          i += len;
        } else {
          out.push(withPos({ type: "code", value: inner }, source, text, i, end));
          i = end;
        }
        continue;
      }
      if (c === "$") {
        const display = text[i + 1] === "$";
        const dollarLen = display ? 2 : 1;
        if (text[i + dollarLen] === "`") {
          const mm = /^(`+)([\s\S]*?[^`])(\1)(?!`)/.exec(text.slice(i + dollarLen));
          if (mm) {
            flush();
            const content = mm[2].replace(/^ (.*) $/, "$1");
            const len = dollarLen + mm[0].length;
            out.push(withPos({ type: "math", display, content }, source, text, i, i + len));
            i += len;
            continue;
          }
        }
      }
      if (c === "!" && text[i + 1] === "[") {
        const closeAbs = bracketClose[i + 1];
        const close = closeAbs === void 0 ? -1 : closeAbs - i;
        if (close > 1) {
          const ml = RE_LINK_TAIL.exec(rest.slice(close + 1));
          if (ml) {
            flush();
            const img = { type: "image", src: ml[1], alt: rest.slice(2, close) };
            const title = ml[2] ?? ml[3];
            if (title)
              img.title = title;
            let len = close + 1 + ml[0].length;
            if (ml[4]) {
              const a = parseAttrs(ml[4]);
              if (isEmptyAttrs(a))
                len -= ml[4].length + 2;
              else
                img.attrs = a;
            }
            out.push(withPos(img, source, text, i, i + len));
            i += len;
            continue;
          }
        }
      }
      if (c === "[") {
        const closeAbs = bracketClose[i];
        const close = closeAbs === void 0 ? -1 : closeAbs - i;
        if (close > 0) {
          const innerText = rest.slice(1, close);
          const tail = rest.slice(close + 1);
          const ml = RE_LINK_TAIL.exec(tail);
          if (ml) {
            flush();
            const link = {
              type: "link",
              href: ml[1],
              children: scanInline(innerText, shiftSource(source, text, i + 1))
            };
            const title = ml[2] ?? ml[3];
            if (title)
              link.title = title;
            let len = close + 1 + ml[0].length;
            if (ml[4]) {
              const a = parseAttrs(ml[4]);
              if (isEmptyAttrs(a))
                len -= ml[4].length + 2;
              else
                link.attrs = a;
            }
            out.push(withPos(link, source, text, i, i + len));
            i += len;
            continue;
          }
          const mref = RE_REF_TAIL.exec(tail);
          if (mref && innerText !== "") {
            flush();
            let len = close + 1 + mref[0].length;
            let attrs;
            if (mref[2]) {
              const a = parseAttrs(mref[2]);
              if (isEmptyAttrs(a))
                len -= mref[2].length + 2;
              else
                attrs = a;
            }
            const refLink = {
              type: "link",
              href: "",
              children: scanInline(innerText, shiftSource(source, text, i + 1)),
              ref: mref[1] !== "" ? mref[1] : innerText,
              // rawRef includes any consumed trailing {attrs} so the literal
              // fallback for an unresolved ref preserves the full source.
              rawRef: rest.slice(0, len)
            };
            if (attrs)
              refLink.attrs = attrs;
            out.push(withPos(refLink, source, text, i, i + len));
            i += len;
            continue;
          }
        }
        const mfn = RE_FOOTNOTE_REF.exec(rest);
        if (mfn) {
          flush();
          out.push(withPos({ type: "footnote", id: mfn[1].trim() }, source, text, i, i + mfn[0].length));
          i += mfn[0].length;
          continue;
        }
        if (close > 0) {
          const innerText = rest.slice(1, close);
          const ms = RE_SPAN_TAIL.exec(rest.slice(close + 1));
          if (ms && isValidAttrPayload(ms[1])) {
            flush();
            out.push({
              type: "span",
              children: scanInline(innerText, shiftSource(source, text, i + 1)),
              attrs: parseAttrs(ms[1]),
              pos: sourcePos(source, text, i, i + close + 1 + ms[0].length)
            });
            i += close + 1 + ms[0].length;
            continue;
          }
        }
      }
      if (c === ":") {
        const m = RE_EXTENSION.exec(rest);
        if (m) {
          flush();
          const ext = {
            type: "extension",
            name: m[1],
            content: scanInline(m[2], shiftSource(source, text, i + m[0].indexOf("[") + 1))
          };
          if (m[3])
            ext.attrs = parseAttrs(m[3]);
          out.push(withPos(ext, source, text, i, i + m[0].length));
          i += m[0].length;
          continue;
        }
        const em2 = RE_EMOJI.exec(rest);
        if (em2) {
          flush();
          out.push(withPos({ type: "emoji", name: em2[1] }, source, text, i, i + em2[0].length));
          i += em2[0].length;
          continue;
        }
      }
      if (c === "<") {
        const cr = RE_CROSSREF.exec(rest);
        if (cr) {
          flush();
          const cref = { type: "crossref", target: cr[1] };
          cref.pos = sourcePos(source, text, i, i + cr[0].length);
          out.push(cref);
          i += cr[0].length;
          continue;
        }
        const m = RE_AUTOLINK.exec(rest);
        if (m) {
          flush();
          const href = m[1];
          const auto = {
            type: "autolink",
            href: href.includes("@") && !href.includes(":") ? `mailto:${href}` : href
          };
          let consumed = m[0].length;
          const am = /^\{([^}\n]+)\}/.exec(text.slice(i + consumed));
          if (am) {
            const attrs = parseAttrs(am[1]);
            if (!isEmptyAttrs(attrs)) {
              if (attrs.keyValues?.href !== void 0) {
                delete attrs.keyValues.href;
                if (attrs.order)
                  attrs.order = attrs.order.filter((s) => s !== "href");
              }
              if (!isEmptyAttrs(attrs))
                auto.attrs = attrs;
              consumed += am[0].length;
            }
          }
          out.push(withPos(auto, source, text, i, i + consumed));
          i += consumed;
          continue;
        }
      }
      if (c === "{") {
        const sub = RE_CRITIC_SUB.exec(rest);
        if (sub) {
          flush();
          out.push({
            type: "critic-substitute",
            oldText: sub[1],
            newText: sub[2],
            pos: sourcePos(source, text, i, i + sub[0].length)
          });
          i += sub[0].length;
          continue;
        }
        const ins = RE_CRITIC_INS.exec(rest);
        if (ins) {
          flush();
          out.push(withPos({ type: "critic-insert", children: scanInline(ins[1], shiftSource(source, text, i + 2)) }, source, text, i, i + ins[0].length));
          i += ins[0].length;
          continue;
        }
        const del = RE_CRITIC_DEL.exec(rest);
        if (del) {
          flush();
          out.push(withPos({ type: "critic-delete", children: scanInline(del[1], shiftSource(source, text, i + 2)) }, source, text, i, i + del[0].length));
          i += del[0].length;
          continue;
        }
        const cmt = RE_CRITIC_CMT.exec(rest);
        if (cmt) {
          flush();
          out.push(withPos({ type: "critic-comment", text: cmt[1] }, source, text, i, i + cmt[0].length));
          i += cmt[0].length;
          continue;
        }
        const attr = RE_INLINE_ATTR.exec(rest);
        if (attr && out.length) {
          const prev = out[out.length - 1];
          const parsed = parseAttrs(attr[1]);
          if (prev.type !== "text" && !isEmptyAttrs(parsed)) {
            ;
            prev.attrs = mergeAttrs(prev.attrs, parsed);
            i += attr[0].length;
            continue;
          }
        }
      }
      if (c === "@" && (i === 0 || !/[A-Za-z0-9_]/.test(text[i - 1]))) {
        const m = RE_MENTION.exec(rest);
        if (m) {
          flush();
          out.push(withPos({ type: "mention", user: m[1] }, source, text, i, i + m[0].length));
          i += m[0].length;
          continue;
        }
      }
      if (c === "#" && (i === 0 || !/[A-Za-z0-9_]/.test(text[i - 1]))) {
        const m = RE_TAG.exec(rest);
        if (m) {
          flush();
          out.push(withPos({ type: "tag", name: m[1] }, source, text, i, i + m[0].length));
          i += m[0].length;
          continue;
        }
      }
      const em = matchEmphasis(text, i, source);
      if (em) {
        flush();
        out.push(withPos(em.node, source, text, i, em.end));
        i = em.end;
        continue;
      }
      if (c === "\n") {
        flush();
        out.push(withPos({ type: "soft-break" }, source, text, i, i + 1));
        i++;
        continue;
      }
      append(c);
      i++;
    }
    flush();
    return out;
  }
  function matchEmphasis(text, i, source) {
    const c = text[i];
    if (c === "/" && text[i + 1] === "*") {
      const close = findClose(text, i + 2, "*/");
      if (close !== -1) {
        const inner = text.slice(i + 2, close);
        return {
          node: { type: "bold-italic", children: scanInline(inner, shiftSource(source, text, i + 2)) },
          end: close + 2
        };
      }
    }
    if (c === "," && text[i + 1] === "," && text[i - 1] !== "," && text[i + 2] !== ",") {
      const close = findClose(text, i + 2, ",,");
      if (close !== -1 && close > i + 2) {
        const inner = text.slice(i + 2, close);
        if (inner.trim() && !inner.startsWith(" ") && !inner.endsWith(" ")) {
          return {
            node: { type: "sub", children: scanInline(inner, shiftSource(source, text, i + 2)) },
            end: close + 2
          };
        }
      }
    }
    if (c === "=" && text[i + 1] === "=" && text[i - 1] !== "=" && text[i + 2] !== "=") {
      const close = findClose(text, i + 2, "==");
      if (close !== -1 && close > i + 2) {
        const inner = text.slice(i + 2, close);
        if (inner.trim() && !inner.startsWith(" ") && !inner.endsWith(" ")) {
          return {
            node: { type: "highlight", children: scanInline(inner, shiftSource(source, text, i + 2)) },
            end: close + 2
          };
        }
      }
    }
    const pairs = [
      ["/", "italic"],
      ["*", "strong"],
      ["_", "underline"],
      ["~", "strike"],
      ["^", "super"]
    ];
    for (const [delim, type] of pairs) {
      if (c === delim) {
        const after = text[i + 1];
        const before = text[i - 1];
        if (!after || after === " " || after === "\n")
          continue;
        if (after === delim || before === delim)
          continue;
        if ((delim === "/" || delim === "_") && before && /[A-Za-z0-9_/]/.test(before))
          continue;
        const close = findEmphasisClose(text, i + 1, delim);
        if (close !== -1) {
          const inner = text.slice(i + 1, close);
          return {
            node: { type, children: scanInline(inner, shiftSource(source, text, i + 1)) },
            end: close + 1
          };
        }
      }
    }
    return null;
  }
  function findClose(text, from, marker) {
    return text.indexOf(marker, from);
  }
  function withPos(node, source, text, start, end) {
    node.pos = sourcePos(source, text, start, end);
    return node;
  }
  function sourcePos(source, text, start, end) {
    const startPoint = pointAt(source, text, start);
    const endPoint = pointAt(source, text, end);
    return {
      startLine: startPoint.line,
      endLine: endPoint.line,
      startColumn: startPoint.column,
      endColumn: endPoint.column,
      startOffset: source.baseOffset + start,
      endOffset: source.baseOffset + end
    };
  }
  function shiftSource(source, text, by) {
    const point = pointAt(source, text, by);
    return {
      baseOffset: source.baseOffset + by,
      startLine: point.line,
      startColumn: point.column
    };
  }
  var newlineIndexCache = /* @__PURE__ */ new Map();
  function newlineIndices(text) {
    let indices = newlineIndexCache.get(text);
    if (indices === void 0) {
      indices = [];
      for (let i = 0; i < text.length; i++) {
        if (text[i] === "\n")
          indices.push(i);
      }
      newlineIndexCache.set(text, indices);
    }
    return indices;
  }
  function pointAt(source, text, offset) {
    const indices = newlineIndices(text);
    let lo = 0;
    let hi = indices.length;
    while (lo < hi) {
      const mid = lo + hi >> 1;
      if (indices[mid] < offset) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    const newlinesBefore = lo;
    const line = source.startLine + newlinesBefore;
    const column = newlinesBefore === 0 ? source.startColumn + offset : offset - indices[newlinesBefore - 1];
    return { line, column };
  }
  function findEmphasisClose(text, from, delim) {
    let depth = 0;
    for (let j = from; j < text.length; j++) {
      const ch = text[j];
      if (ch === "\\" && j + 1 < text.length) {
        j++;
        continue;
      }
      if (ch === "`") {
        const span = verbatimSpanEnd(text, j);
        if (!span.closed)
          return -1;
        j = span.end - 1;
        continue;
      }
      if (ch === delim) {
        const prev = text[j - 1];
        if (prev === " " || prev === "\n" || prev === void 0)
          continue;
        const next = text[j + 1];
        if ((delim === "/" || delim === "_") && next && /[A-Za-z0-9]/.test(next))
          continue;
        if (depth === 0)
          return j;
        depth--;
      }
    }
    return -1;
  }
  function applyAbbreviations(nodes, defs) {
    if (defs.size === 0)
      return nodes;
    const out = [];
    const abbrRe = new RegExp(`\\b(${[...defs.keys()].join("|")})\\b`, "g");
    for (const node of nodes) {
      if (node.type !== "text") {
        const anyChildren = node.children;
        if (Array.isArray(anyChildren)) {
          ;
          node.children = applyAbbreviations(anyChildren, defs);
        }
        out.push(node);
        continue;
      }
      const value = node.value;
      let last = 0;
      abbrRe.lastIndex = 0;
      let m;
      while (m = abbrRe.exec(value)) {
        if (m.index > last) {
          out.push({ type: "text", value: value.slice(last, m.index) });
        }
        const abbr = m[1];
        out.push({
          type: "abbreviation",
          abbr,
          expansion: defs.get(abbr)
        });
        last = m.index + abbr.length;
      }
      if (last < value.length) {
        out.push({ type: "text", value: value.slice(last) });
      } else if (last === 0) {
        out.push(node);
      }
    }
    return out;
  }
  function applyLinkDefs(nodes, defs) {
    const out = [];
    for (const node of nodes) {
      const anyChildren = node.children;
      if (Array.isArray(anyChildren)) {
        ;
        node.children = applyLinkDefs(anyChildren, defs);
      }
      if (node.type === "link" && node.ref !== void 0) {
        const def = defs.get(normalizeRefLabel(node.ref));
        if (def) {
          node.href = def.href;
          if (def.title !== void 0)
            node.title = def.title;
          delete node.ref;
          delete node.rawRef;
        }
        out.push(node);
        continue;
      }
      out.push(node);
    }
    return out;
  }
  function isValidAttrPayload(inner) {
    const stripped = inner.replace(/(?:#[\w-]+)|(?:\.[\w-]+)|(?:[\w-]+=(?:"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\S+))|\s+/g, "");
    return stripped === "";
  }
  function isEmptyAttrs(attrs) {
    return attrs.id === void 0 && (attrs.classes === void 0 || attrs.classes.length === 0) && (attrs.keyValues === void 0 || Object.keys(attrs.keyValues).length === 0);
  }
  function unescapeAttrValue(v) {
    return v.replace(/\\(.)/g, (whole, c) => /[\\`*_{}\[\]()#+\-.!~^/<>@%|=,"'$&:;?]/.test(c) ? c : whole);
  }
  function parseAttrs(src) {
    const attrs = {};
    const order = [];
    const note = (slot) => {
      if (!order.includes(slot))
        order.push(slot);
    };
    const re = /(?:#([\w-]+))|(?:\.([\w-]+))|(?:([\w-]+)=(?:"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)'|(\S+)))/g;
    let m;
    while (m = re.exec(src)) {
      if (m[1]) {
        attrs.id = m[1];
        note("#id");
      } else if (m[2]) {
        attrs.classes = [...attrs.classes ?? [], m[2]];
        note(".class");
      } else if (m[3]) {
        const val = m[4] !== void 0 ? unescapeAttrValue(m[4]) : m[5] !== void 0 ? unescapeAttrValue(m[5]) : m[6] ?? "";
        attrs.keyValues = { ...attrs.keyValues ?? {}, [m[3]]: val };
        note(m[3]);
      }
    }
    if (order.length)
      attrs.order = order;
    return attrs;
  }
  function mergeAttrs(a, b) {
    if (!a)
      return b;
    const out = { ...a };
    if (b.id)
      out.id = b.id;
    if (b.classes)
      out.classes = [...out.classes ?? [], ...b.classes];
    if (b.keyValues)
      out.keyValues = { ...out.keyValues ?? {}, ...b.keyValues };
    const order = [...attrOrder(a)];
    for (const slot of attrOrder(b))
      if (!order.includes(slot))
        order.push(slot);
    if (order.length)
      out.order = order;
    return out;
  }
  function attrOrder(a) {
    if (a.order)
      return a.order;
    const o = [];
    if (a.classes?.length)
      o.push(".class");
    if (a.id !== void 0)
      o.push("#id");
    if (a.keyValues)
      for (const k of Object.keys(a.keyValues))
        o.push(k);
    return o;
  }

  // ../../media/mark/data/work/git/carve-js/dist/translit-map.js
  var TRANSLIT_MAP = {
    "\xA0": " ",
    "\xA1": "!",
    "\xA9": "(C)",
    "\xAB": "<<",
    "\xAD": "-",
    "\xAE": "(R)",
    "\xB1": "+/-",
    "\xBB": ">>",
    "\xBC": " 1/4",
    "\xBD": " 1/2",
    "\xBE": " 3/4",
    "\xBF": "?",
    "\xC0": "A",
    "\xC1": "A",
    "\xC2": "A",
    "\xC3": "A",
    "\xC4": "A",
    "\xC5": "A",
    "\xC6": "AE",
    "\xC7": "C",
    "\xC8": "E",
    "\xC9": "E",
    "\xCA": "E",
    "\xCB": "E",
    "\xCC": "I",
    "\xCD": "I",
    "\xCE": "I",
    "\xCF": "I",
    "\xD0": "D",
    "\xD1": "N",
    "\xD2": "O",
    "\xD3": "O",
    "\xD4": "O",
    "\xD5": "O",
    "\xD6": "O",
    "\xD7": "*",
    "\xD8": "O",
    "\xD9": "U",
    "\xDA": "U",
    "\xDB": "U",
    "\xDC": "U",
    "\xDD": "Y",
    "\xDE": "TH",
    "\xDF": "ss",
    "\xE0": "a",
    "\xE1": "a",
    "\xE2": "a",
    "\xE3": "a",
    "\xE4": "a",
    "\xE5": "a",
    "\xE6": "ae",
    "\xE7": "c",
    "\xE8": "e",
    "\xE9": "e",
    "\xEA": "e",
    "\xEB": "e",
    "\xEC": "i",
    "\xED": "i",
    "\xEE": "i",
    "\xEF": "i",
    "\xF0": "d",
    "\xF1": "n",
    "\xF2": "o",
    "\xF3": "o",
    "\xF4": "o",
    "\xF5": "o",
    "\xF6": "o",
    "\xF7": "/",
    "\xF8": "o",
    "\xF9": "u",
    "\xFA": "u",
    "\xFB": "u",
    "\xFC": "u",
    "\xFD": "y",
    "\xFE": "th",
    "\xFF": "y",
    "\u0100": "A",
    "\u0101": "a",
    "\u0102": "A",
    "\u0103": "a",
    "\u0104": "A",
    "\u0105": "a",
    "\u0106": "C",
    "\u0107": "c",
    "\u0108": "C",
    "\u0109": "c",
    "\u010A": "C",
    "\u010B": "c",
    "\u010C": "C",
    "\u010D": "c",
    "\u010E": "D",
    "\u010F": "d",
    "\u0110": "D",
    "\u0111": "d",
    "\u0112": "E",
    "\u0113": "e",
    "\u0114": "E",
    "\u0115": "e",
    "\u0116": "E",
    "\u0117": "e",
    "\u0118": "E",
    "\u0119": "e",
    "\u011A": "E",
    "\u011B": "e",
    "\u011C": "G",
    "\u011D": "g",
    "\u011E": "G",
    "\u011F": "g",
    "\u0120": "G",
    "\u0121": "g",
    "\u0122": "G",
    "\u0123": "g",
    "\u0124": "H",
    "\u0125": "h",
    "\u0126": "H",
    "\u0127": "h",
    "\u0128": "I",
    "\u0129": "i",
    "\u012A": "I",
    "\u012B": "i",
    "\u012C": "I",
    "\u012D": "i",
    "\u012E": "I",
    "\u012F": "i",
    "\u0130": "I",
    "\u0131": "i",
    "\u0132": "IJ",
    "\u0133": "ij",
    "\u0134": "J",
    "\u0135": "j",
    "\u0136": "K",
    "\u0137": "k",
    "\u0138": "q",
    "\u0139": "L",
    "\u013A": "l",
    "\u013B": "L",
    "\u013C": "l",
    "\u013D": "L",
    "\u013E": "l",
    "\u013F": "L",
    "\u0140": "l",
    "\u0141": "L",
    "\u0142": "l",
    "\u0143": "N",
    "\u0144": "n",
    "\u0145": "N",
    "\u0146": "n",
    "\u0147": "N",
    "\u0148": "n",
    "\u0149": "'n",
    "\u014A": "N",
    "\u014B": "n",
    "\u014C": "O",
    "\u014D": "o",
    "\u014E": "O",
    "\u014F": "o",
    "\u0150": "O",
    "\u0151": "o",
    "\u0152": "OE",
    "\u0153": "oe",
    "\u0154": "R",
    "\u0155": "r",
    "\u0156": "R",
    "\u0157": "r",
    "\u0158": "R",
    "\u0159": "r",
    "\u015A": "S",
    "\u015B": "s",
    "\u015C": "S",
    "\u015D": "s",
    "\u015E": "S",
    "\u015F": "s",
    "\u0160": "S",
    "\u0161": "s",
    "\u0162": "T",
    "\u0163": "t",
    "\u0164": "T",
    "\u0165": "t",
    "\u0166": "T",
    "\u0167": "t",
    "\u0168": "U",
    "\u0169": "u",
    "\u016A": "U",
    "\u016B": "u",
    "\u016C": "U",
    "\u016D": "u",
    "\u016E": "U",
    "\u016F": "u",
    "\u0170": "U",
    "\u0171": "u",
    "\u0172": "U",
    "\u0173": "u",
    "\u0174": "W",
    "\u0175": "w",
    "\u0176": "Y",
    "\u0177": "y",
    "\u0178": "Y",
    "\u0179": "Z",
    "\u017A": "z",
    "\u017B": "Z",
    "\u017C": "z",
    "\u017D": "Z",
    "\u017E": "z",
    "\u017F": "s",
    "\u0180": "b",
    "\u0181": "B",
    "\u0182": "B",
    "\u0183": "b",
    "\u0187": "C",
    "\u0188": "c",
    "\u0189": "D",
    "\u018A": "D",
    "\u018B": "D",
    "\u018C": "d",
    "\u0190": "E",
    "\u0191": "F",
    "\u0192": "f",
    "\u0193": "G",
    "\u0195": "hv",
    "\u0196": "I",
    "\u0197": "I",
    "\u0198": "K",
    "\u0199": "k",
    "\u019A": "l",
    "\u019D": "N",
    "\u019E": "n",
    "\u01A0": "O",
    "\u01A1": "o",
    "\u01A2": "OI",
    "\u01A3": "oi",
    "\u01A4": "P",
    "\u01A5": "p",
    "\u01AB": "t",
    "\u01AC": "T",
    "\u01AD": "t",
    "\u01AE": "T",
    "\u01AF": "U",
    "\u01B0": "u",
    "\u01B2": "V",
    "\u01B3": "Y",
    "\u01B4": "y",
    "\u01B5": "Z",
    "\u01B6": "z",
    "\u01C4": "DZ",
    "\u01C5": "Dz",
    "\u01C6": "dz",
    "\u01C7": "LJ",
    "\u01C8": "Lj",
    "\u01C9": "lj",
    "\u01CA": "NJ",
    "\u01CB": "Nj",
    "\u01CC": "nj",
    "\u01CD": "A",
    "\u01CE": "a",
    "\u01CF": "I",
    "\u01D0": "i",
    "\u01D1": "O",
    "\u01D2": "o",
    "\u01D3": "U",
    "\u01D4": "u",
    "\u01D5": "U",
    "\u01D6": "u",
    "\u01D7": "U",
    "\u01D8": "u",
    "\u01D9": "U",
    "\u01DA": "u",
    "\u01DB": "U",
    "\u01DC": "u",
    "\u01DE": "A",
    "\u01DF": "a",
    "\u01E0": "A",
    "\u01E1": "a",
    "\u01E2": "AE",
    "\u01E3": "ae",
    "\u01E4": "G",
    "\u01E5": "g",
    "\u01E6": "G",
    "\u01E7": "g",
    "\u01E8": "K",
    "\u01E9": "k",
    "\u01EA": "O",
    "\u01EB": "o",
    "\u01EC": "O",
    "\u01ED": "o",
    "\u01F0": "j",
    "\u01F1": "DZ",
    "\u01F2": "Dz",
    "\u01F3": "dz",
    "\u01F4": "G",
    "\u01F5": "g",
    "\u01F8": "N",
    "\u01F9": "n",
    "\u01FA": "A",
    "\u01FB": "a",
    "\u01FC": "AE",
    "\u01FD": "ae",
    "\u01FE": "O",
    "\u01FF": "o",
    "\u0200": "A",
    "\u0201": "a",
    "\u0202": "A",
    "\u0203": "a",
    "\u0204": "E",
    "\u0205": "e",
    "\u0206": "E",
    "\u0207": "e",
    "\u0208": "I",
    "\u0209": "i",
    "\u020A": "I",
    "\u020B": "i",
    "\u020C": "O",
    "\u020D": "o",
    "\u020E": "O",
    "\u020F": "o",
    "\u0210": "R",
    "\u0211": "r",
    "\u0212": "R",
    "\u0213": "r",
    "\u0214": "U",
    "\u0215": "u",
    "\u0216": "U",
    "\u0217": "u",
    "\u0218": "S",
    "\u0219": "s",
    "\u021A": "T",
    "\u021B": "t",
    "\u021E": "H",
    "\u021F": "h",
    "\u0221": "d",
    "\u0224": "Z",
    "\u0225": "z",
    "\u0226": "A",
    "\u0227": "a",
    "\u0228": "E",
    "\u0229": "e",
    "\u022A": "O",
    "\u022B": "o",
    "\u022C": "O",
    "\u022D": "o",
    "\u022E": "O",
    "\u022F": "o",
    "\u0230": "O",
    "\u0231": "o",
    "\u0232": "Y",
    "\u0233": "y",
    "\u0234": "l",
    "\u0235": "n",
    "\u0236": "t",
    "\u0237": "j",
    "\u0238": "db",
    "\u0239": "qp",
    "\u023A": "A",
    "\u023B": "C",
    "\u023C": "c",
    "\u023D": "L",
    "\u023E": "T",
    "\u023F": "s",
    "\u0240": "z",
    "\u0243": "B",
    "\u0244": "U",
    "\u0246": "E",
    "\u0247": "e",
    "\u0248": "J",
    "\u0249": "j",
    "\u024C": "R",
    "\u024D": "r",
    "\u024E": "Y",
    "\u024F": "y",
    "\u0253": "b",
    "\u0255": "c",
    "\u0256": "d",
    "\u0257": "d",
    "\u025B": "e",
    "\u025F": "j",
    "\u0260": "g",
    "\u0261": "g",
    "\u0262": "G",
    "\u0266": "h",
    "\u0267": "h",
    "\u0268": "i",
    "\u026A": "I",
    "\u026B": "l",
    "\u026C": "l",
    "\u026D": "l",
    "\u0271": "m",
    "\u0272": "n",
    "\u0273": "n",
    "\u0274": "N",
    "\u0276": "OE",
    "\u027C": "r",
    "\u027D": "r",
    "\u027E": "r",
    "\u0280": "R",
    "\u0282": "s",
    "\u0288": "t",
    "\u0289": "u",
    "\u028B": "v",
    "\u028F": "Y",
    "\u0290": "z",
    "\u0291": "z",
    "\u0299": "B",
    "\u029B": "G",
    "\u029C": "H",
    "\u029D": "j",
    "\u029F": "L",
    "\u02A0": "q",
    "\u02A3": "dz",
    "\u02A5": "dz",
    "\u02A6": "ts",
    "\u02AA": "ls",
    "\u02AB": "lz",
    "\u0400": "E",
    "\u0401": "E",
    "\u0402": "D",
    "\u0403": "G",
    "\u0404": "E",
    "\u0405": "Z",
    "\u0406": "I",
    "\u0407": "I",
    "\u0408": "J",
    "\u0409": "L",
    "\u040A": "N",
    "\u040B": "C",
    "\u040C": "K",
    "\u040D": "I",
    "\u040E": "U",
    "\u040F": "D",
    "\u0410": "A",
    "\u0411": "B",
    "\u0412": "V",
    "\u0413": "G",
    "\u0414": "D",
    "\u0415": "E",
    "\u0416": "Z",
    "\u0417": "Z",
    "\u0418": "I",
    "\u0419": "J",
    "\u041A": "K",
    "\u041B": "L",
    "\u041C": "M",
    "\u041D": "N",
    "\u041E": "O",
    "\u041F": "P",
    "\u0420": "R",
    "\u0421": "S",
    "\u0422": "T",
    "\u0423": "U",
    "\u0424": "F",
    "\u0425": "H",
    "\u0426": "C",
    "\u0427": "C",
    "\u0428": "S",
    "\u0429": "S",
    "\u042B": "Y",
    "\u042D": "E",
    "\u042E": "U",
    "\u042F": "A",
    "\u0430": "a",
    "\u0431": "b",
    "\u0432": "v",
    "\u0433": "g",
    "\u0434": "d",
    "\u0435": "e",
    "\u0436": "z",
    "\u0437": "z",
    "\u0438": "i",
    "\u0439": "j",
    "\u043A": "k",
    "\u043B": "l",
    "\u043C": "m",
    "\u043D": "n",
    "\u043E": "o",
    "\u043F": "p",
    "\u0440": "r",
    "\u0441": "s",
    "\u0442": "t",
    "\u0443": "u",
    "\u0444": "f",
    "\u0445": "h",
    "\u0446": "c",
    "\u0447": "c",
    "\u0448": "s",
    "\u0449": "s",
    "\u044A": '"',
    "\u044B": "y",
    "\u044C": "'",
    "\u044D": "e",
    "\u044E": "u",
    "\u044F": "a",
    "\u0450": "e",
    "\u0451": "e",
    "\u0452": "d",
    "\u0453": "g",
    "\u0454": "e",
    "\u0455": "z",
    "\u0456": "i",
    "\u0457": "i",
    "\u0458": "j",
    "\u0459": "l",
    "\u045A": "n",
    "\u045B": "c",
    "\u045C": "k",
    "\u045D": "i",
    "\u045E": "u",
    "\u045F": "d",
    "\u0490": "G",
    "\u0491": "g",
    "\u0492": "G",
    "\u0493": "g",
    "\u0494": "G",
    "\u0495": "g",
    "\u0498": "Z",
    "\u0499": "z",
    "\u04A2": "N",
    "\u04A3": "n",
    "\u04AE": "U",
    "\u04AF": "u",
    "\u04B0": "U",
    "\u04B1": "u",
    "\u04BA": "H",
    "\u04BB": "h",
    "\u04C1": "Z",
    "\u04C2": "z",
    "\u04D0": "A",
    "\u04D1": "a",
    "\u04D2": "A",
    "\u04D3": "a",
    "\u04D4": "AE",
    "\u04D5": "ae",
    "\u04D6": "E",
    "\u04D7": "e",
    "\u04DC": "Z",
    "\u04DD": "z",
    "\u04DE": "Z",
    "\u04DF": "z",
    "\u04E2": "I",
    "\u04E3": "i",
    "\u04E4": "I",
    "\u04E5": "i",
    "\u04E6": "O",
    "\u04E7": "o",
    "\u04E8": "O",
    "\u04E9": "o",
    "\u04EC": "E",
    "\u04ED": "e",
    "\u04EE": "U",
    "\u04EF": "u",
    "\u04F0": "U",
    "\u04F1": "u",
    "\u04F2": "U",
    "\u04F3": "u",
    "\u04F4": "C",
    "\u04F5": "c",
    "\u04F8": "Y",
    "\u04F9": "y",
    "\u1E00": "A",
    "\u1E01": "a",
    "\u1E02": "B",
    "\u1E03": "b",
    "\u1E04": "B",
    "\u1E05": "b",
    "\u1E06": "B",
    "\u1E07": "b",
    "\u1E08": "C",
    "\u1E09": "c",
    "\u1E0A": "D",
    "\u1E0B": "d",
    "\u1E0C": "D",
    "\u1E0D": "d",
    "\u1E0E": "D",
    "\u1E0F": "d",
    "\u1E10": "D",
    "\u1E11": "d",
    "\u1E12": "D",
    "\u1E13": "d",
    "\u1E14": "E",
    "\u1E15": "e",
    "\u1E16": "E",
    "\u1E17": "e",
    "\u1E18": "E",
    "\u1E19": "e",
    "\u1E1A": "E",
    "\u1E1B": "e",
    "\u1E1C": "E",
    "\u1E1D": "e",
    "\u1E1E": "F",
    "\u1E1F": "f",
    "\u1E20": "G",
    "\u1E21": "g",
    "\u1E22": "H",
    "\u1E23": "h",
    "\u1E24": "H",
    "\u1E25": "h",
    "\u1E26": "H",
    "\u1E27": "h",
    "\u1E28": "H",
    "\u1E29": "h",
    "\u1E2A": "H",
    "\u1E2B": "h",
    "\u1E2C": "I",
    "\u1E2D": "i",
    "\u1E2E": "I",
    "\u1E2F": "i",
    "\u1E30": "K",
    "\u1E31": "k",
    "\u1E32": "K",
    "\u1E33": "k",
    "\u1E34": "K",
    "\u1E35": "k",
    "\u1E36": "L",
    "\u1E37": "l",
    "\u1E38": "L",
    "\u1E39": "l",
    "\u1E3A": "L",
    "\u1E3B": "l",
    "\u1E3C": "L",
    "\u1E3D": "l",
    "\u1E3E": "M",
    "\u1E3F": "m",
    "\u1E40": "M",
    "\u1E41": "m",
    "\u1E42": "M",
    "\u1E43": "m",
    "\u1E44": "N",
    "\u1E45": "n",
    "\u1E46": "N",
    "\u1E47": "n",
    "\u1E48": "N",
    "\u1E49": "n",
    "\u1E4A": "N",
    "\u1E4B": "n",
    "\u1E4C": "O",
    "\u1E4D": "o",
    "\u1E4E": "O",
    "\u1E4F": "o",
    "\u1E50": "O",
    "\u1E51": "o",
    "\u1E52": "O",
    "\u1E53": "o",
    "\u1E54": "P",
    "\u1E55": "p",
    "\u1E56": "P",
    "\u1E57": "p",
    "\u1E58": "R",
    "\u1E59": "r",
    "\u1E5A": "R",
    "\u1E5B": "r",
    "\u1E5C": "R",
    "\u1E5D": "r",
    "\u1E5E": "R",
    "\u1E5F": "r",
    "\u1E60": "S",
    "\u1E61": "s",
    "\u1E62": "S",
    "\u1E63": "s",
    "\u1E64": "S",
    "\u1E65": "s",
    "\u1E66": "S",
    "\u1E67": "s",
    "\u1E68": "S",
    "\u1E69": "s",
    "\u1E6A": "T",
    "\u1E6B": "t",
    "\u1E6C": "T",
    "\u1E6D": "t",
    "\u1E6E": "T",
    "\u1E6F": "t",
    "\u1E70": "T",
    "\u1E71": "t",
    "\u1E72": "U",
    "\u1E73": "u",
    "\u1E74": "U",
    "\u1E75": "u",
    "\u1E76": "U",
    "\u1E77": "u",
    "\u1E78": "U",
    "\u1E79": "u",
    "\u1E7A": "U",
    "\u1E7B": "u",
    "\u1E7C": "V",
    "\u1E7D": "v",
    "\u1E7E": "V",
    "\u1E7F": "v",
    "\u1E80": "W",
    "\u1E81": "w",
    "\u1E82": "W",
    "\u1E83": "w",
    "\u1E84": "W",
    "\u1E85": "w",
    "\u1E86": "W",
    "\u1E87": "w",
    "\u1E88": "W",
    "\u1E89": "w",
    "\u1E8A": "X",
    "\u1E8B": "x",
    "\u1E8C": "X",
    "\u1E8D": "x",
    "\u1E8E": "Y",
    "\u1E8F": "y",
    "\u1E90": "Z",
    "\u1E91": "z",
    "\u1E92": "Z",
    "\u1E93": "z",
    "\u1E94": "Z",
    "\u1E95": "z",
    "\u1E96": "h",
    "\u1E97": "t",
    "\u1E98": "w",
    "\u1E99": "y",
    "\u1E9A": "a",
    "\u1E9B": "s",
    "\u1E9C": "s",
    "\u1E9D": "s",
    "\u1E9E": "SS",
    "\u1EA0": "A",
    "\u1EA1": "a",
    "\u1EA2": "A",
    "\u1EA3": "a",
    "\u1EA4": "A",
    "\u1EA5": "a",
    "\u1EA6": "A",
    "\u1EA7": "a",
    "\u1EA8": "A",
    "\u1EA9": "a",
    "\u1EAA": "A",
    "\u1EAB": "a",
    "\u1EAC": "A",
    "\u1EAD": "a",
    "\u1EAE": "A",
    "\u1EAF": "a",
    "\u1EB0": "A",
    "\u1EB1": "a",
    "\u1EB2": "A",
    "\u1EB3": "a",
    "\u1EB4": "A",
    "\u1EB5": "a",
    "\u1EB6": "A",
    "\u1EB7": "a",
    "\u1EB8": "E",
    "\u1EB9": "e",
    "\u1EBA": "E",
    "\u1EBB": "e",
    "\u1EBC": "E",
    "\u1EBD": "e",
    "\u1EBE": "E",
    "\u1EBF": "e",
    "\u1EC0": "E",
    "\u1EC1": "e",
    "\u1EC2": "E",
    "\u1EC3": "e",
    "\u1EC4": "E",
    "\u1EC5": "e",
    "\u1EC6": "E",
    "\u1EC7": "e",
    "\u1EC8": "I",
    "\u1EC9": "i",
    "\u1ECA": "I",
    "\u1ECB": "i",
    "\u1ECC": "O",
    "\u1ECD": "o",
    "\u1ECE": "O",
    "\u1ECF": "o",
    "\u1ED0": "O",
    "\u1ED1": "o",
    "\u1ED2": "O",
    "\u1ED3": "o",
    "\u1ED4": "O",
    "\u1ED5": "o",
    "\u1ED6": "O",
    "\u1ED7": "o",
    "\u1ED8": "O",
    "\u1ED9": "o",
    "\u1EDA": "O",
    "\u1EDB": "o",
    "\u1EDC": "O",
    "\u1EDD": "o",
    "\u1EDE": "O",
    "\u1EDF": "o",
    "\u1EE0": "O",
    "\u1EE1": "o",
    "\u1EE2": "O",
    "\u1EE3": "o",
    "\u1EE4": "U",
    "\u1EE5": "u",
    "\u1EE6": "U",
    "\u1EE7": "u",
    "\u1EE8": "U",
    "\u1EE9": "u",
    "\u1EEA": "U",
    "\u1EEB": "u",
    "\u1EEC": "U",
    "\u1EED": "u",
    "\u1EEE": "U",
    "\u1EEF": "u",
    "\u1EF0": "U",
    "\u1EF1": "u",
    "\u1EF2": "Y",
    "\u1EF3": "y",
    "\u1EF4": "Y",
    "\u1EF5": "y",
    "\u1EF6": "Y",
    "\u1EF7": "y",
    "\u1EF8": "Y",
    "\u1EF9": "y",
    "\u1EFA": "LL",
    "\u1EFB": "ll",
    "\u1EFC": "V",
    "\u1EFD": "v",
    "\u1EFE": "Y",
    "\u1EFF": "y",
    "\u2000": " ",
    "\u2001": " ",
    "\u2002": " ",
    "\u2003": " ",
    "\u2004": " ",
    "\u2005": " ",
    "\u2006": " ",
    "\u2007": " ",
    "\u2008": " ",
    "\u2009": " ",
    "\u200A": " ",
    "\u2010": "-",
    "\u2011": "-",
    "\u2012": "-",
    "\u2013": "-",
    "\u2014": "-",
    "\u2015": "-",
    "\u2016": "||",
    "\u2018": "'",
    "\u2019": "'",
    "\u201A": ",",
    "\u201B": "'",
    "\u201C": '"',
    "\u201D": '"',
    "\u201E": ",,",
    "\u201F": '"',
    "\u2024": ".",
    "\u2025": "..",
    "\u2026": "...",
    "\u2032": "'",
    "\u2033": '"',
    "\u2039": "<",
    "\u203A": ">",
    "\u203C": "!!",
    "\u2044": "/",
    "\u2045": "[",
    "\u2046": "]",
    "\u2047": "??",
    "\u2048": "?!",
    "\u2049": "!?",
    "\u204E": "*",
    "\u205F": " ",
    "\u20A0": "CE",
    "\u20A2": "Cr",
    "\u20A3": "Fr.",
    "\u20A4": "L.",
    "\u20A7": "Pts",
    "\u20B9": "Rs",
    "\u20BA": "TL",
    "\u2100": "a/c",
    "\u2101": "a/s",
    "\u2102": "C",
    "\u2105": "c/o",
    "\u2106": "c/u",
    "\u210A": "g",
    "\u210B": "H",
    "\u210C": "x",
    "\u210D": "H",
    "\u210E": "h",
    "\u2110": "I",
    "\u2111": "I",
    "\u2112": "L",
    "\u2113": "l",
    "\u2115": "N",
    "\u2116": "No",
    "\u2117": "(P)",
    "\u2118": "P",
    "\u2119": "P",
    "\u211A": "Q",
    "\u211B": "R",
    "\u211C": "R",
    "\u211D": "R",
    "\u211E": "Rx",
    "\u2121": "TEL",
    "\u2124": "Z",
    "\u2126": "O",
    "\u2128": "Z",
    "\u212A": "K",
    "\u212B": "A",
    "\u212C": "B",
    "\u212D": "C",
    "\u212F": "e",
    "\u2130": "E",
    "\u2131": "F",
    "\u2133": "M",
    "\u2134": "o",
    "\u2139": "i",
    "\u213B": "FAX",
    "\u2145": "D",
    "\u2146": "d",
    "\u2147": "e",
    "\u2148": "i",
    "\u2149": "j"
  };

  // ../../media/mark/data/work/git/carve-js/dist/heading-ids.js
  function normalizeHeadingRefLabel(label) {
    return normalizeRefLabel(label).toLowerCase();
  }
  function transliterate(s) {
    let out = "";
    for (const ch of s)
      out += TRANSLIT_MAP[ch] ?? ch;
    return out;
  }
  function slugRun(s) {
    return s.replace(/[^0-9A-Za-z\u{80}-\u{10FFFF}]+/gu, "-").replace(/^-+|-+$/gu, "");
  }
  function slugify(plainText, asciiFold = false) {
    let s = slugRun(plainText.normalize("NFC"));
    if (asciiFold) {
      s = slugRun(transliterate(s));
    }
    s = s.toLowerCase();
    if (/^\p{N}/u.test(s))
      s = `s-${s}`;
    if (s === "")
      s = "s";
    return s;
  }
  function inlineText(nodes) {
    let out = "";
    for (const n of nodes) {
      switch (n.type) {
        case "text":
        case "code":
          out += n.value;
          break;
        case "math":
          out += n.content;
          break;
        case "italic":
        case "strong":
        case "underline":
        case "strike":
        case "super":
        case "sub":
        case "highlight":
        case "bold-italic":
        case "link":
        case "span":
        case "critic-insert":
        case "critic-delete":
          out += inlineText(n.children);
          break;
        case "extension":
          out += inlineText(n.content);
          break;
        case "critic-substitute":
          out += n.newText;
          break;
        case "abbreviation":
          out += n.abbr;
          break;
        case "mention":
          out += n.user;
          break;
        case "tag":
          out += n.name;
          break;
        case "soft-break":
        case "hard-break":
          out += " ";
          break;
        // image, autolink, footnote, crossref, critic-comment: no slug text
        default:
          break;
      }
    }
    return out;
  }
  function resolveHeadingIds(doc, asciiFold = false) {
    const used = /* @__PURE__ */ new Set();
    const targets = /* @__PURE__ */ new Map();
    const headingRefs = /* @__PURE__ */ new Map();
    for (const block of doc.children) {
      if (block.type !== "heading")
        continue;
      let id;
      if (block.attrs?.id) {
        id = block.attrs.id;
        used.add(id);
      } else {
        const base = slugify(inlineText(block.children), asciiFold);
        if (!used.has(base)) {
          id = base;
        } else {
          let n = 2;
          while (used.has(`${base}-${n}`))
            n++;
          id = `${base}-${n}`;
        }
        used.add(id);
        block.attrs = { ...block.attrs, id };
      }
      if (!targets.has(id))
        targets.set(id, block.children);
      const plain = inlineText(block.children);
      const key = normalizeHeadingRefLabel(plain);
      if (key && !headingRefs.has(key))
        headingRefs.set(key, id);
    }
    const resolveRefs = (nodes) => {
      for (let i = 0; i < nodes.length; i++) {
        const n = nodes[i];
        if (n.type === "link" && n.ref !== void 0) {
          const id = headingRefs.get(normalizeHeadingRefLabel(n.ref));
          if (id) {
            n.href = `#${id}`;
            delete n.ref;
            delete n.rawRef;
          } else {
            nodes[i] = { type: "text", value: n.rawRef ?? "" };
            continue;
          }
        }
        switch (n.type) {
          case "italic":
          case "strong":
          case "underline":
          case "strike":
          case "super":
          case "sub":
          case "highlight":
          case "bold-italic":
          case "link":
          case "span":
          case "critic-insert":
          case "critic-delete":
            resolveRefs(n.children);
            break;
          case "extension":
            resolveRefs(n.content);
            break;
          default:
            break;
        }
      }
    };
    const resolveCrossrefs = (nodes) => {
      for (let i = 0; i < nodes.length; i++) {
        const n = nodes[i];
        if (n.type === "crossref") {
          const tgt = targets.get(n.target);
          if (tgt) {
            const link = {
              type: "link",
              href: `#${n.target}`,
              // structuredClone would need DOM/Node lib typings absent from this
              // tsconfig; InlineNode is plain JSON-serializable data so a
              // stringify/parse round-trip is a safe deep clone here.
              children: JSON.parse(JSON.stringify(tgt))
            };
            nodes[i] = link;
          } else {
            const txt = { type: "text", value: `</#${n.target}>` };
            nodes[i] = txt;
          }
          continue;
        }
        switch (n.type) {
          case "italic":
          case "strong":
          case "underline":
          case "strike":
          case "super":
          case "sub":
          case "highlight":
          case "bold-italic":
          case "link":
          case "span":
          case "critic-insert":
          case "critic-delete":
            resolveCrossrefs(n.children);
            break;
          case "extension":
            resolveCrossrefs(n.content);
            break;
          default:
            break;
        }
      }
    };
    const walkBlock = (b, fn) => {
      switch (b.type) {
        case "heading":
        case "paragraph":
          fn(b.children);
          break;
        case "blockquote":
          if (b.attribution)
            fn(b.attribution);
          b.children.forEach((c) => walkBlock(c, fn));
          break;
        case "list":
          for (const item of b.items)
            item.children.forEach((c) => walkBlock(c, fn));
          break;
        case "admonition":
          if (b.title)
            fn(b.title);
          b.children.forEach((c) => walkBlock(c, fn));
          break;
        case "div":
          b.children.forEach((c) => walkBlock(c, fn));
          break;
        case "definition-list":
          for (const it of b.items) {
            for (const t of it.terms)
              fn(t);
            for (const d of it.definitions)
              d.forEach((c) => walkBlock(c, fn));
          }
          break;
        case "table":
          if (b.caption)
            fn(b.caption);
          for (const row of b.rows)
            for (const cell of row.cells)
              fn(cell.children);
          break;
        case "figure":
          fn(b.caption);
          if (b.target.type === "blockquote" || b.target.type === "table")
            walkBlock(b.target, fn);
          break;
        default:
          break;
      }
    };
    const footnoteBodies = doc.footnoteDefs ? Object.values(doc.footnoteDefs) : [];
    for (const block of doc.children)
      walkBlock(block, resolveRefs);
    for (const body of footnoteBodies)
      for (const b of body)
        walkBlock(b, resolveRefs);
    for (const block of doc.children)
      walkBlock(block, resolveCrossrefs);
    for (const body of footnoteBodies)
      for (const b of body)
        walkBlock(b, resolveCrossrefs);
    return doc;
  }

  // ../../media/mark/data/work/git/carve-js/dist/render-html.js
  var DEFAULT_URL_SCHEMES = ["http", "https", "mailto"];
  function sanitizeUrl(url, opts) {
    if (opts.sanitizeUrls === false)
      return url;
    const probe = url.replace(/^[\u0000-\u0020]+/, "").replace(/[\t\n\r]/g, "");
    const scheme = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(probe);
    if (!scheme)
      return url;
    const allowed = opts.allowedUrlSchemes ?? DEFAULT_URL_SCHEMES;
    return allowed.some((s) => s.toLowerCase() === scheme[1].toLowerCase()) ? url : "";
  }
  function withSourceLine(html, line) {
    if (line === void 0)
      return html;
    return html.replace(/^(\s*<[A-Za-z][A-Za-z0-9]*)/, `$1 data-source-line="${line}"`);
  }
  function renderHtml(ast, opts = {}) {
    const out = [];
    const sectionStack = [];
    const closeTo = (level) => {
      while (sectionStack.length && sectionStack[sectionStack.length - 1] >= level) {
        sectionStack.pop();
        out.push(`${indent(sectionStack.length)}</section>`);
      }
    };
    const footnotes = collectFootnotes(ast);
    for (const node of ast.children) {
      if (node.type === "abbreviation-def")
        continue;
      if (node.type === "heading") {
        closeTo(node.level);
        const depth = sectionStack.length;
        const id = node.attrs?.id;
        const sectionId = id ? ` id="${escapeAttr(id)}"` : "";
        out.push(`${indent(depth)}<section${sectionId}>`);
        sectionStack.push(node.level);
        const headingAttrs = stripId(node.attrs);
        const inner = renderInlines(node.children, opts);
        const slAttr = opts.sourceLine && node.pos ? ` data-source-line="${node.pos.startLine}"` : "";
        out.push(`${indent(depth + 1)}<h${node.level}${slAttr}${renderAttrs(headingAttrs)}>${inner}</h${node.level}>`);
        continue;
      }
      let rendered = renderBlock(node, opts, sectionStack.length);
      if (opts.sourceLine && node.type !== "raw-block") {
        rendered = withSourceLine(rendered, node.pos?.startLine);
      }
      if (rendered !== "")
        out.push(rendered);
    }
    closeTo(1);
    if (footnotes.order.length)
      out.push(renderFootnoteSection(ast, footnotes, opts));
    return out.join("\n");
  }
  function walkBlockInlines(node, visit) {
    switch (node.type) {
      case "heading":
      case "paragraph":
        visit(node.children);
        break;
      case "blockquote":
        if (node.attribution)
          visit(node.attribution);
        node.children.forEach((c) => walkBlockInlines(c, visit));
        break;
      case "list":
        for (const it of node.items)
          it.children.forEach((c) => walkBlockInlines(c, visit));
        break;
      case "admonition":
        if (node.title)
          visit(node.title);
        node.children.forEach((c) => walkBlockInlines(c, visit));
        break;
      case "div":
        node.children.forEach((c) => walkBlockInlines(c, visit));
        break;
      case "definition-list":
        for (const it of node.items) {
          for (const t of it.terms)
            visit(t);
          for (const d of it.definitions)
            for (const b of d)
              walkBlockInlines(b, visit);
        }
        break;
      case "table":
        if (node.caption)
          visit(node.caption);
        for (const row of node.rows)
          for (const cell of row.cells)
            visit(cell.children);
        break;
      case "figure":
        visit(node.caption);
        if (node.target.type === "blockquote" || node.target.type === "table")
          walkBlockInlines(node.target, visit);
        break;
      default:
        break;
    }
  }
  function visitInlineTree(nodes, fn) {
    for (const n of nodes) {
      fn(n);
      const kids = n.children ?? n.content;
      if (Array.isArray(kids))
        visitInlineTree(kids, fn);
    }
  }
  function collectFootnotes(ast) {
    const defs = ast.footnoteDefs ?? {};
    const order = [];
    const backrefs = {};
    const seen = {};
    const onNode = (n) => {
      if (n.type !== "footnote" || !n.id || !defs[n.id])
        return;
      let idx = order.indexOf(n.id);
      if (idx === -1) {
        order.push(n.id);
        idx = order.length - 1;
        backrefs[n.id] = [];
      }
      const number = idx + 1;
      const occ = seen[n.id] = (seen[n.id] ?? 0) + 1;
      const refId = occ === 1 ? `fnref${number}` : `fnref${number}-${occ}`;
      n.number = number;
      n.refId = refId;
      backrefs[n.id].push(refId);
    };
    for (const b of ast.children)
      walkBlockInlines(b, (xs) => visitInlineTree(xs, onNode));
    for (let k = 0; k < order.length; k++) {
      for (const b of defs[order[k]] ?? [])
        walkBlockInlines(b, (xs) => visitInlineTree(xs, onNode));
    }
    return { order, backrefs };
  }
  function renderFootnoteSection(ast, st, opts) {
    const defs = ast.footnoteDefs ?? {};
    const lines = ['<section role="doc-endnotes">', `${indent(1)}<hr>`, `${indent(1)}<ol>`];
    st.order.forEach((label, idx) => {
      const number = idx + 1;
      const body = (defs[label] ?? []).map((b) => renderBlock(b, opts, 3));
      const blink = (st.backrefs[label] ?? []).map((rid) => `<a href="#${rid}" role="doc-backlink">\u21A9</a>`).join("");
      const last = body.length - 1;
      if (last >= 0 && /<\/p>\s*$/.test(body[last])) {
        body[last] = body[last].replace(/<\/p>(\s*)$/, `${blink}</p>$1`);
      } else {
        body.push(`${indent(3)}<p>${blink}</p>`);
      }
      lines.push(`${indent(2)}<li id="fn${number}">`, ...body, `${indent(2)}</li>`);
    });
    lines.push(`${indent(1)}</ol>`, "</section>");
    return lines.join("\n");
  }
  function stripId(attrs) {
    if (!attrs)
      return void 0;
    if (attrs.id === void 0)
      return attrs;
    const { id: _omit, ...rest } = attrs;
    return rest;
  }
  function stripKeyValue(attrs, key) {
    if (!attrs?.keyValues)
      return attrs;
    const lower = key.toLowerCase();
    const matches = (k) => k.toLowerCase() === lower;
    if (!Object.keys(attrs.keyValues).some(matches))
      return attrs;
    const kv = {};
    for (const [k, v] of Object.entries(attrs.keyValues))
      if (!matches(k))
        kv[k] = v;
    const result = { ...attrs, keyValues: kv };
    if (attrs.order)
      result.order = attrs.order.filter((s) => !matches(s));
    return result;
  }
  function indent(level) {
    return "  ".repeat(level);
  }
  function renderAttrs(attrs) {
    if (!attrs)
      return "";
    const parts = [];
    const classAttr = () => attrs.classes && attrs.classes.length ? `class="${attrs.classes.join(" ")}"` : "";
    const idAttr = () => attrs.id ? `id="${attrs.id}"` : "";
    const kvAttr = (k) => {
      const v = attrs.keyValues?.[k];
      return v !== void 0 ? `${k}="${escapeAttr(v)}"` : "";
    };
    const seen = new Set(attrs.order ?? []);
    if (attrs.order) {
      for (const slot of attrs.order) {
        const p = slot === ".class" ? classAttr() : slot === "#id" ? idAttr() : kvAttr(slot);
        if (p)
          parts.push(p);
      }
    }
    if (!seen.has(".class")) {
      const c = classAttr();
      if (c)
        parts.push(c);
    }
    if (!seen.has("#id")) {
      const i = idAttr();
      if (i)
        parts.push(i);
    }
    if (attrs.keyValues) {
      for (const k of Object.keys(attrs.keyValues)) {
        if (!seen.has(k)) {
          const p = kvAttr(k);
          if (p)
            parts.push(p);
        }
      }
    }
    return parts.length ? " " + parts.join(" ") : "";
  }
  function renderAttrs2(attrs, opts = {}) {
    if (!attrs && !opts.baseClass)
      return "";
    const a = attrs ? { ...attrs } : {};
    if (opts.baseClass) {
      a.classes = [opts.baseClass, ...a.classes ?? []];
      if (a.order && !a.order.includes(".class"))
        a.order = [".class", ...a.order];
    }
    if (opts.dropId) {
      delete a.id;
      if (a.order)
        a.order = a.order.filter((s) => s !== "#id");
    }
    return renderAttrs(a);
  }
  function renderBlock(node, opts, level) {
    const pad = indent(level);
    switch (node.type) {
      case "heading": {
        const inner = renderInlines(node.children, opts);
        return `${pad}<h${node.level}${renderAttrs(node.attrs)}>${inner}</h${node.level}>`;
      }
      case "paragraph": {
        const inner = renderInlines(node.children, opts);
        return `${pad}<p${renderAttrs(node.attrs)}>${inner}</p>`;
      }
      case "thematic-break":
        return `${pad}<hr${renderAttrs(node.attrs)}>`;
      case "code-block": {
        const langAttr = node.lang ? ` class="language-${node.lang}"` : "";
        const escaped = escapeHtml(node.content);
        return `${pad}<pre${renderAttrs(node.attrs)}><code${langAttr}>${escaped}
</code></pre>`;
      }
      case "blockquote":
        return renderBlockQuote(node, opts, level);
      case "list":
        return renderList(node, opts, level);
      case "image":
        return `${pad}${renderImage(node, opts)}`;
      case "table":
        return renderTable(node, opts, level);
      case "admonition":
        return renderAdmonition(node, opts, level);
      case "div": {
        const open = `${pad}<div${renderAttrs(node.attrs)}>`;
        if (node.children.length === 0)
          return `${open}
${pad}</div>`;
        const body = node.children.map((c) => renderBlock(c, opts, level + 1)).join("\n");
        return `${open}
${body}
${pad}</div>`;
      }
      case "definition-list": {
        const lines = [`${pad}<dl>`];
        for (const it of node.items) {
          for (const t of it.terms)
            lines.push(`${pad}  <dt>${renderInlines(t, opts)}</dt>`);
          for (const d of it.definitions) {
            if (d.length === 1 && d[0].type === "paragraph") {
              lines.push(`${pad}  <dd>${renderInlines(d[0].children, opts)}</dd>`);
            } else {
              const body = d.map((b) => renderBlock(b, opts, level + 2)).join("\n");
              lines.push(`${pad}  <dd>
${body}
${pad}  </dd>`);
            }
          }
        }
        lines.push(`${pad}</dl>`);
        return lines.join("\n");
      }
      case "figure":
        return renderFigure(node, opts, level);
      case "abbreviation-def":
        return "";
      case "raw-block":
        return node.format === "html" ? node.content : "";
      case "comment":
        return "";
      default: {
        const t = node;
        throw new Error(`renderHtml: unknown block ${t.type}`);
      }
    }
  }
  function renderBlockQuote(node, opts, level) {
    const pad = indent(level);
    const attrs = renderAttrs(node.attrs);
    if (node.children.length === 1 && node.children[0].type === "paragraph") {
      const para = node.children[0];
      const inner2 = renderInlines(para.children, opts);
      return `${pad}<blockquote${attrs}><p${renderAttrs(para.attrs)}>${inner2}</p></blockquote>`;
    }
    const inner = node.children.map((c) => renderBlock(c, opts, level + 1)).join("\n");
    return `${pad}<blockquote${attrs}>
${inner}
${pad}</blockquote>`;
  }
  function renderList(node, opts, level) {
    const pad = indent(level);
    const tag = node.ordered ? "ol" : "ul";
    const typeAttr = node.ordered && node.olType ? ` type="${node.olType}"` : "";
    const startAttr = node.ordered && node.start !== void 0 && node.start !== 1 ? ` start="${node.start}"` : "";
    const items = node.items.map((it) => renderListItem(it, opts, level + 1, node.tight)).join("\n");
    return `${pad}<${tag}${typeAttr}${startAttr}${renderAttrs(node.attrs)}>
${items}
${pad}</${tag}>`;
  }
  function renderListItem(item, opts, level, tight) {
    const pad = indent(level);
    const checkbox = item.checked === void 0 ? "" : item.checked ? '<input type="checkbox" checked disabled> ' : '<input type="checkbox" disabled> ';
    const wrapPara = (p) => {
      const inner = renderInlines(p.children, opts);
      if (tight && !p.attrs)
        return inner;
      return `<p${renderAttrs(p.attrs)}>${inner}</p>`;
    };
    if (item.children.length === 1 && item.children[0].type === "paragraph") {
      return `${pad}<li>${checkbox}${wrapPara(item.children[0])}</li>`;
    }
    let head = `${pad}<li>${checkbox}`;
    const body = [];
    item.children.forEach((child, i) => {
      if (child.type === "paragraph") {
        const rendered = wrapPara(child);
        if (i === 0)
          head += rendered;
        else
          body.push(`${indent(level + 1)}${rendered}`);
      } else {
        body.push(renderBlock(child, opts, level + 1));
      }
    });
    if (body.length === 0)
      return `${head}</li>`;
    return `${head}
${body.join("\n")}
${pad}</li>`;
  }
  function renderTable(node, opts, level) {
    const pad = indent(level);
    const lines = [`${pad}<table${renderAttrs(node.attrs)}>`];
    if (node.caption) {
      lines.push(`${pad}  <caption>${renderInlines(node.caption, opts)}</caption>`);
    }
    const grid = [];
    for (let r = 0; r < node.rows.length; r++) {
      const row = node.rows[r];
      const gridRow = [];
      for (let c = 0; c < row.cells.length; c++) {
        const cell = row.cells[c];
        gridRow.push({ row, cell, rowspan: 1, colspan: 1, skip: false });
      }
      grid.push(gridRow);
    }
    for (let r = 0; r < grid.length; r++) {
      for (let c = 0; c < grid[r].length; c++) {
        const entry = grid[r][c];
        if (entry.skip)
          continue;
        if (entry.cell.span === "rowspan" && r > 0) {
          let up = r - 1;
          while (up >= 0 && grid[up][c] && grid[up][c].skip)
            up--;
          const src = grid[up]?.[c];
          if (src) {
            src.rowspan++;
            entry.skip = true;
          }
        } else if (entry.cell.span === "colspan" && c > 0) {
          let left = c - 1;
          while (left >= 0 && grid[r][left].skip)
            left--;
          const src = grid[r][left];
          if (src) {
            src.colspan++;
            entry.skip = true;
          }
        }
      }
    }
    let headerEnd = 0;
    while (headerEnd < grid.length && grid[headerEnd].every((e) => e.cell.header || e.skip)) {
      headerEnd++;
    }
    const columnAlign = [];
    for (let r = 0; r < headerEnd; r++) {
      const hr = grid[r];
      for (let c = 0; c < hr.length; c++) {
        const entry = hr[c];
        if (entry.skip || !entry.cell.align)
          continue;
        for (let k = c; k < c + entry.colspan; k++)
          columnAlign[k] = entry.cell.align;
      }
    }
    for (let r = 0; r < grid.length; r++) {
      for (let c = 0; c < grid[r].length; c++) {
        const a = grid[r][c].cell.align ?? columnAlign[c];
        if (a)
          grid[r][c].align = a;
      }
    }
    if (headerEnd > 0) {
      const rows = grid.slice(0, headerEnd).map((r) => renderTableRowFlat(r, opts));
      lines.push(`${pad}  <thead>${rows.join("")}</thead>`);
    }
    if (headerEnd < grid.length) {
      lines.push(`${pad}  <tbody>`);
      for (let r = headerEnd; r < grid.length; r++) {
        lines.push(`${pad}    ${renderTableRowFlat(grid[r], opts)}`);
      }
      lines.push(`${pad}  </tbody>`);
    }
    lines.push(`${pad}</table>`);
    return lines.join("\n");
  }
  function renderTableRowFlat(cells, opts) {
    const parts = ["<tr>"];
    for (const entry of cells) {
      if (entry.skip)
        continue;
      const tag = entry.cell.header ? "th" : "td";
      const attrs = [];
      if (entry.rowspan > 1)
        attrs.push(`rowspan="${entry.rowspan}"`);
      if (entry.colspan > 1)
        attrs.push(`colspan="${entry.colspan}"`);
      if (entry.align)
        attrs.push(`style="text-align: ${entry.align};"`);
      const attrStr = attrs.length ? " " + attrs.join(" ") : "";
      parts.push(`<${tag}${attrStr}>${renderInlines(entry.cell.children, opts)}</${tag}>`);
    }
    parts.push("</tr>");
    return parts.join("");
  }
  var CANONICAL_ADMONITIONS = /* @__PURE__ */ new Set([
    "note",
    "tip",
    "warning",
    "danger",
    "info",
    "success",
    "example",
    "quote"
  ]);
  function renderAdmonition(node, opts, level) {
    const pad = indent(level);
    const titleLine = node.title !== void 0 ? `${pad}  <p class="admonition-title">${renderInlines(node.title, opts)}</p>
` : "";
    const body = node.children.map((c) => renderBlock(c, opts, level + 1)).join("\n");
    const canonical = CANONICAL_ADMONITIONS.has(node.kind);
    const baseClass = canonical ? `admonition ${node.kind}` : node.kind;
    const extraClasses = node.attrs?.classes?.length ? " " + node.attrs.classes.join(" ") : "";
    const restAttrs = {};
    if (node.attrs?.id !== void 0)
      restAttrs.id = node.attrs.id;
    if (node.attrs?.keyValues)
      restAttrs.keyValues = node.attrs.keyValues;
    if (node.attrs?.order)
      restAttrs.order = node.attrs.order.filter((s) => s !== ".class");
    const rest = renderAttrs(restAttrs);
    const tag = canonical ? "aside" : "div";
    return `${pad}<${tag} class="${baseClass}${extraClasses}"${rest}>
${titleLine}${body}
${pad}</${tag}>`;
  }
  function renderFigure(node, opts, level) {
    const pad = indent(level);
    let inner;
    if (node.target.type === "image") {
      inner = `${pad}  ${renderImage(node.target, opts)}`;
    } else if (node.target.type === "blockquote") {
      const bq = renderBlockQuote(node.target, opts, level + 1);
      inner = bq;
    } else {
      inner = renderTable(node.target, opts, level + 1);
    }
    return `${pad}<figure${renderAttrs(node.attrs)}>
${inner}
${pad}  <figcaption>${renderInlines(node.caption, opts)}</figcaption>
${pad}</figure>`;
  }
  function renderImage(img, opts) {
    const titleAttr = img.title ? ` title="${escapeAttr(img.title)}"` : "";
    const src = escapeAttr(sanitizeUrl(img.src, opts));
    return `<img src="${src}" alt="${escapeAttr(img.alt)}"${titleAttr}${renderAttrs(stripKeyValue(img.attrs, "src"))}>`;
  }
  function renderInlines(nodes, opts) {
    return nodes.map((n) => renderInline(n, opts)).join("");
  }
  function renderInline(node, opts) {
    switch (node.type) {
      case "text":
        return escapeHtml(node.value);
      case "italic":
        return `<em${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</em>`;
      case "strong":
        return `<strong${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</strong>`;
      case "underline":
        return `<u${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</u>`;
      case "strike":
        return `<s${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</s>`;
      case "super":
        return `<sup${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</sup>`;
      case "sub":
        return `<sub${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</sub>`;
      case "highlight":
        return `<mark${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</mark>`;
      case "bold-italic":
        return `<strong${renderAttrs(node.attrs)}><em>${renderInlines(node.children, opts)}</em></strong>`;
      case "code":
        return `<code>${escapeHtml(node.value)}</code>`;
      case "link": {
        const titleAttr = node.title ? ` title="${escapeAttr(node.title)}"` : "";
        const href = escapeAttr(sanitizeUrl(node.href, opts));
        return `<a href="${href}"${titleAttr}${renderAttrs(stripKeyValue(node.attrs, "href"))}>${renderInlines(node.children, opts)}</a>`;
      }
      case "image":
        return renderImage(node, opts);
      case "span":
        return `<span${renderAttrs(node.attrs)}>${renderInlines(node.children, opts)}</span>`;
      case "math": {
        const base = node.display ? "math display" : "math inline";
        const body = node.display ? `\\[${escapeHtml(node.content)}\\]` : `\\(${escapeHtml(node.content)}\\)`;
        return `<span${renderAttrs2(node.attrs, { baseClass: base })}>${body}</span>`;
      }
      case "raw-inline":
        return node.format === "html" ? node.content : "";
      case "emoji":
        return opts.emoji?.[node.name] ?? escapeHtml(`:${node.name}:`);
      case "autolink": {
        const display = node.href.startsWith("mailto:") ? node.href.slice(7) : node.href;
        const href = escapeAttr(sanitizeUrl(node.href, opts));
        return `<a href="${href}"${renderAttrs(stripKeyValue(node.attrs, "href"))}>${escapeHtml(display)}</a>`;
      }
      case "mention": {
        const text = `@${escapeHtml(node.user)}`;
        if (!opts.mentionUrl)
          return `<span class="mention"><strong>${text}</strong></span>`;
        const enc = encodeURIComponent(node.user);
        const href = opts.mentionUrl.replaceAll("{name}", enc).replaceAll("{user}", enc);
        return `<a class="mention" href="${escapeAttr(href)}">${text}</a>`;
      }
      case "tag": {
        const text = `#${escapeHtml(node.name)}`;
        if (!opts.tagUrl)
          return `<span class="tag"><strong>${text}</strong></span>`;
        const href = opts.tagUrl.replaceAll("{name}", encodeURIComponent(node.name));
        return `<a class="tag" href="${escapeAttr(href)}">${text}</a>`;
      }
      case "extension": {
        const renderer = opts.extensions?.flatMap((e) => e.renderers ? [e.renderers] : []).map((r) => r[node.name]).find((fn) => fn !== void 0);
        if (renderer) {
          const ctx = {
            renderInlines: (nodes) => renderInlines(nodes, opts),
            escapeHtml,
            escapeAttr,
            renderAttrs
          };
          return renderer(node, ctx);
        }
        return renderExtension(node.name, node.content, node.attrs, opts);
      }
      case "abbreviation":
        return `<abbr title="${escapeAttr(node.expansion)}">${escapeHtml(node.abbr)}</abbr>`;
      case "footnote":
        return node.number === void 0 ? escapeHtml(`[^${node.id ?? ""}]`) : `<a id="${node.refId}" href="#fn${node.number}" role="doc-noteref"${renderAttrs2(node.attrs, { dropId: true })}><sup>${node.number}</sup></a>`;
      case "soft-break":
        return "\n";
      case "hard-break":
        return "<br>\n";
      case "critic-insert":
        return `<ins>${renderInlines(node.children, opts)}</ins>`;
      case "critic-delete":
        return `<del>${renderInlines(node.children, opts)}</del>`;
      case "critic-substitute":
        return `<del>${escapeHtml(node.oldText)}</del><ins>${escapeHtml(node.newText)}</ins>`;
      case "critic-comment":
        return `<span class="critic-comment">${escapeHtml(node.text)}</span>`;
      case "crossref":
        return `&lt;/#${escapeHtml(node.target)}&gt;`;
      case "comment":
        return "";
      default: {
        const t = node;
        throw new Error(`renderHtml: unknown inline ${t.type}`);
      }
    }
  }
  function renderExtension(name, content, attrs, opts) {
    const inner = renderInlines(content, opts);
    const semanticTags = /* @__PURE__ */ new Set(["kbd", "dfn", "abbr", "cite", "samp", "var", "code", "mark", "time"]);
    if (semanticTags.has(name)) {
      return `<${name}${renderAttrs2(attrs)}>${inner}</${name}>`;
    }
    return `<span${renderAttrs2(attrs, { baseClass: `ext-${name}` })}>${inner}</span>`;
  }
  var HTML_ESCAPE = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\xA0": "&nbsp;"
  };
  function escapeHtml(s) {
    return s.replace(/[&<>\u00a0]/g, (c) => HTML_ESCAPE[c]);
  }
  function escapeAttr(s) {
    return s.replace(/[&<>"']/g, (c) => c === '"' ? "&quot;" : c === "'" ? "&apos;" : HTML_ESCAPE[c]);
  }

  // ../../media/mark/data/work/git/carve-js/dist/djot-migrate.js
  var RULES = [
    // `C(x)` = a content run that may cross soft line breaks (Carve's
    // parseInline parses emphasis across them) but never a blank line,
    // and never the delimiter char `x`.
    {
      id: "markdown-strong-double-star",
      family: "*",
      pattern: /\*\*(?!\s)((?:(?!\n[ \t]*\n)[^*])+?)(?<!\s)\*\*/gd,
      message: () => "Djot/Markdown `**strong**` is not Carve bold \u2014 Carve bold is a single `*`, so this renders with literal asterisks.",
      suggestion: (m) => `*${m[1]}*`
    },
    {
      id: "markdown-strikethrough-double-tilde",
      family: "~",
      pattern: /~~(?!\s)((?:(?!\n[ \t]*\n)[^~])+?)(?<!\s)~~/gd,
      message: () => "Markdown `~~strikethrough~~` is not Carve \u2014 Carve strikethrough is a single `~`.",
      suggestion: (m) => `~${m[1]}~`
    },
    {
      id: "djot-subscript-tilde",
      family: "~",
      pattern: /~(?!\s)((?:(?!\n[ \t]*\n)[^~])+?)(?<!\s)~/gd,
      message: () => "Djot subscript `~x~` renders as *strikethrough* in Carve.",
      suggestion: (m) => `,,${m[1]},,`
    },
    {
      id: "djot-emphasis-underscore",
      family: "_",
      pattern: /(?<![A-Za-z0-9_])_(?!\s)((?:(?!\n[ \t]*\n)[^_])+?)(?<!\s)_(?![A-Za-z0-9_])/gd,
      message: () => "Djot emphasis `_x_` renders as *underline* in Carve.",
      suggestion: (m) => `/${m[1]}/`
    },
    {
      id: "djot-highlight-braces",
      family: "{",
      pattern: /\{=(?!\s)((?:(?!\n[ \t]*\n)[\s\S])+?)(?<!\s)=\}/gd,
      message: () => "Djot highlight `{=x=}` is written `==x==` in Carve.",
      suggestion: (m) => `==${m[1]}==`
    },
    // Block-level (line-anchored): a leading `+ content` is a bullet in
    // Djot/Markdown but NOT in Carve — `+` is the list-continuation marker, so
    // the line renders as a paragraph. A lone `+` (no content) is excluded: that
    // IS the Carve continuation marker and is intentional.
    {
      id: "djot-plus-bullet",
      family: "plus-bullet",
      pattern: /(?<=^[ \t]*)(\+)(?=[ \t]+\S)/gmd,
      message: () => "Djot/Markdown `+` bullet is not a Carve bullet (`+` is the list-continuation marker) \u2014 this line renders as a paragraph.",
      suggestion: () => "-"
    }
    // NOTE: full Djot reference links `[text][ref]` are NOT flagged — Carve
    // resolves them identically against a `[ref]: url` definition (corpus
    // 34-reference-link), so there is no silent mis-render. Math (`$`x``)
    // and editorial `{+ +}`/`{- -}` are likewise identical and unflagged.
  ];
  var blanks = (s) => s.replace(/[^\n]/g, " ");
  function maskCode(src) {
    const lines = src.split("\n");
    let fence = null;
    const staged = lines.map((line) => {
      if (fence) {
        const close = line.match(/^ {0,3}([`~]{3,})[ \t]*$/);
        if (close && close[1][0] === fence.ch && close[1].length >= fence.len) {
          fence = null;
        }
        return blanks(line);
      }
      const open = line.match(/^(\s*)(`{3,}|~{3,})\s*([a-zA-Z0-9_+#.-]*)\s*$/);
      if (open) {
        fence = { ch: open[2][0], len: open[2].length };
        return blanks(line);
      }
      return line;
    });
    const s = staged.join("\n");
    const out = s.split("");
    const runLen = (i2) => {
      let n = 0;
      while (s[i2 + n] === "`")
        n++;
      return n;
    };
    let i = 0;
    while (i < s.length) {
      if (s[i] !== "`") {
        i++;
        continue;
      }
      const len = runLen(i);
      let j = i + len;
      let closed = -1;
      while (j < s.length) {
        if (s[j] === "`" && runLen(j) === len) {
          closed = j;
          break;
        }
        j++;
      }
      if (closed === -1) {
        i += len;
        continue;
      }
      for (let k = i; k < closed + len; k++)
        if (out[k] !== "\n")
          out[k] = " ";
      i = closed + len;
    }
    let masked = out.join("");
    masked = masked.replace(/(?<=\])\([^()\n]*\)/g, (g) => blanks(g));
    return masked;
  }
  function djotMigrationWarnings(source) {
    const out = [];
    const norm = source.replace(/\r\n?/g, "\n");
    const masked = maskCode(norm);
    const nlAt = [];
    for (let k = 0; k < masked.length; k++)
      if (masked[k] === "\n")
        nlAt.push(k);
    const posOf = (idx) => {
      let lo = 0;
      let hi = nlAt.length;
      while (lo < hi) {
        const mid = lo + hi >> 1;
        if (nlAt[mid] < idx)
          lo = mid + 1;
        else
          hi = mid;
      }
      const lineStart = lo === 0 ? 0 : nlAt[lo - 1] + 1;
      return { line: lo + 1, column: idx - lineStart + 1 };
    };
    const taken = [];
    const sameFamilyOverlap = (s, e, fam) => taken.some(([ts, te, tf]) => tf === fam && s < te && ts < e);
    for (const rule of RULES) {
      rule.pattern.lastIndex = 0;
      let m;
      while (m = rule.pattern.exec(masked)) {
        const start = m.index;
        const end = m.index + m[0].length;
        let bs = 0;
        for (let k = start - 1; k >= 0 && masked[k] === "\\"; k--)
          bs++;
        if (bs % 2 === 1)
          continue;
        if (sameFamilyOverlap(start, end, rule.family))
          continue;
        taken.push([start, end, rule.family]);
        const { line, column } = posOf(start);
        const span = m.indices?.[1];
        const orig = span ? norm.slice(span[0], span[1]) : m[1];
        const origM = m.slice();
        origM[1] = orig;
        out.push({
          line,
          column,
          rule: rule.id,
          message: rule.message(m),
          suggestion: rule.suggestion(origM),
          start,
          end
        });
      }
    }
    out.sort((a, b) => a.line - b.line || a.column - b.column);
    return out;
  }
  function applyMigrationFixes(source) {
    const warnings = djotMigrationWarnings(source);
    const overlaps = (a, b) => a.start < b.end && b.start < a.end;
    const applied = [];
    const skipped = [];
    for (const w of warnings) {
      if (warnings.some((o) => o !== w && overlaps(w, o)))
        skipped.push(w);
      else
        applied.push(w);
    }
    let output = source.replace(/\r\n?/g, "\n");
    for (let i = applied.length - 1; i >= 0; i--) {
      const w = applied[i];
      output = output.slice(0, w.start) + w.suggestion + output.slice(w.end);
    }
    return { output, applied, skipped };
  }
  function formatMigrationWarnings(warnings, file = "<stdin>") {
    return warnings.map((w) => `${file}:${w.line}:${w.column} ${w.rule} \u2014 ${w.message} (use: ${w.suggestion})`).join("\n");
  }

  // ../../media/mark/data/work/git/carve-js/dist/markdown-migrate.js
  var HTML_TAG_RULES = [
    [/<mark>([^<]+)<\/mark>/gi, "==$1=="],
    [/<ins>([^<]+)<\/ins>/gi, "{+$1+}"],
    [/<del>([^<]+)<\/del>/gi, "~$1~"],
    [/<s>([^<]+)<\/s>/gi, "~$1~"],
    [/<sup>([^<]+)<\/sup>/gi, "^$1^"],
    [/<sub>([^<]+)<\/sub>/gi, ",,$1,,"],
    [/<strong>([^<]+)<\/strong>/gi, "*$1*"],
    [/<b>([^<]+)<\/b>/gi, "*$1*"],
    [/<em>([^<]+)<\/em>/gi, "/$1/"],
    [/<i>([^<]+)<\/i>/gi, "/$1/"]
  ];
  function protectCodeSpans(s, repl) {
    const runLen = (idx) => {
      let n = 0;
      while (s[idx + n] === "`")
        n++;
      return n;
    };
    let out = "";
    let i = 0;
    while (i < s.length) {
      if (s[i] !== "`") {
        out += s[i];
        i++;
        continue;
      }
      const len = runLen(i);
      let j = i + len;
      let closed = -1;
      while (j < s.length) {
        if (s[j] === "`" && s[j - 1] !== "`" && runLen(j) === len) {
          closed = j;
          break;
        }
        j++;
      }
      if (closed === -1) {
        out += s.slice(i, i + len);
        i += len;
        continue;
      }
      out += repl(s.slice(i, closed + len));
      i = closed + len;
    }
    return out;
  }
  function convertInline(input) {
    const protectedSpans = [];
    const protect = (s) => {
      protectedSpans.push(s);
      return `\0P${protectedSpans.length - 1}\0`;
    };
    let line = protectCodeSpans(input, protect);
    line = line.replace(/\\[^A-Za-z0-9\s]/g, protect);
    line = line.replace(/<code>([^<]+)<\/code>/gi, (_m, inner) => protect(`\`${inner}\``));
    const encodeDest = (paren) => {
      const inner = paren.slice(1, -1);
      const m = inner.match(/^(\S+)([\s\S]*)$/);
      const url = m ? m[1] : inner;
      const rest = m ? m[2] : "";
      const enc = url.replace(/[()]/g, (c) => c === "(" ? "%28" : "%29");
      return `(${enc}${rest})`;
    };
    line = line.replace(/(!\[(?:[^[\]]|\[[^\]]*\])*\])(\((?:[^()\n]|\([^()\n]*\))*\))/g, (_m, alt, dest) => protect(alt + encodeDest(dest)));
    line = line.replace(/(?<=\])(\((?:[^()\n]|\([^()\n]*\))*\))/g, (_m, dest) => protect(encodeDest(dest)));
    line = line.replace(/(?<=\])\[[^\]]*\]/g, protect);
    line = line.replace(/<[A-Za-z][A-Za-z0-9+.-]*:[^>\s]+>/g, protect);
    line = line.replace(/<[^>\s@]+@[^>\s]+>/g, protect);
    line = line.replace(/\bhttps?:\/\/[^\s<>`]+/g, protect);
    line = line.replace(/^\s*\[[^^\]][^\]]*\]:\s*\S.*$/, (m) => protect(m));
    line = line.replace(/\$\$([^$]+)\$\$/g, (_m, inner) => protect(`$$\`${inner}\``));
    line = line.replace(/\$([^$\s][^$]*[^$\s]|\S)\$(?!\d)/g, (m, inner) => /^[\d.,]+$/.test(inner) ? m : protect(`$\`${inner}\``));
    const stash = [];
    const hold = (s) => {
      stash.push(s);
      return `\0S${stash.length - 1}\0`;
    };
    const convertNestedEm = (inner) => inner.replace(/(?<![A-Za-z0-9*])\*(?!\s)([^*]+?)(?<!\s)\*(?![A-Za-z0-9*])/g, "/$1/").replace(/(?<![A-Za-z0-9_])_(?!\s)([^_]+?)(?<!\s)_(?![A-Za-z0-9_])/g, "/$1/");
    line = line.replace(/\*{3}(?!\s)(.+?)(?<!\s)\*{3}/g, (_m, inner) => hold(`/*${convertNestedEm(inner)}*/`));
    line = line.replace(/(?<![A-Za-z0-9])___(?!\s)(.+?)(?<!\s)___(?![A-Za-z0-9])/g, (_m, inner) => hold(`/*${convertNestedEm(inner)}*/`));
    line = line.replace(/\*\*(?!\s)(.+?)(?<!\s)\*\*/g, (_m, inner) => hold(`*${convertNestedEm(inner)}*`));
    line = line.replace(/(?<![A-Za-z0-9])__(?!\s)(.+?)(?<!\s)__(?![A-Za-z0-9])/g, (_m, inner) => hold(`*${convertNestedEm(inner)}*`));
    line = line.replace(/(?<![A-Za-z0-9*])\*(?!\s)([^*]+?)(?<!\s)\*(?![A-Za-z0-9*])/g, "/$1/");
    line = line.replace(/(?<![A-Za-z0-9_])_(?!\s)([^_]+?)(?<!\s)_(?![A-Za-z0-9_])/g, "/$1/");
    line = line.replace(/~~([^~]+)~~/g, "~$1~");
    for (const [re, repl] of HTML_TAG_RULES) {
      line = line.replace(re, repl);
    }
    let prev;
    do {
      prev = line;
      line = line.replace(/\x00S(\d+)\x00/g, (_m, i) => stash[Number(i)]).replace(/\x00P(\d+)\x00/g, (_m, i) => protectedSpans[Number(i)]);
    } while (line !== prev);
    return line;
  }
  function markdownToCarve(markdown) {
    const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
    const out = [];
    let inCode = false;
    let fenceChar = "";
    let fenceLen = 0;
    let prevType = "blank";
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const trimmed = line.trim();
      const open = !inCode ? line.match(/^(\s{0,3})(`{3,}|~{3,})(.*)$/) : null;
      if (open) {
        if (prevType !== "blank" && out.length > 0)
          out.push("");
        inCode = true;
        fenceChar = open[2][0];
        fenceLen = open[2].length;
        const info = open[3].match(/[A-Za-z0-9_+#.-]+/)?.[0] ?? "";
        out.push(open[1] + open[2] + info);
        prevType = "code_fence";
        continue;
      }
      if (inCode) {
        if (new RegExp(`^\\s{0,3}(${fenceChar}{${fenceLen},})\\s*$`).test(line)) {
          inCode = false;
          fenceChar = "";
          fenceLen = 0;
          out.push(line);
          if (i + 1 < lines.length && lines[i + 1].trim() !== "")
            out.push("");
          prevType = "code_fence";
        } else {
          out.push(line);
          prevType = "code";
        }
        continue;
      }
      const isBlank = trimmed === "";
      const isHeading = /^#{1,6}\s/.test(trimmed);
      const indent2 = line.length - line.replace(/^\s+/, "").length;
      const isBlockquote = trimmed.startsWith(">");
      const ordered = trimmed.match(/^(\d+)[.)]\s/);
      const isList = (/^[-*+]\s/.test(trimmed) || ordered !== null) && !(prevType === "text" && ordered !== null && Number(ordered[1]) !== 1);
      if (isBlank) {
        out.push(line);
        prevType = "blank";
        continue;
      }
      if (prevType === "list" && indent2 >= 1) {
        out.push(convertInline(line));
        prevType = "list";
        continue;
      }
      const underline = i + 1 < lines.length ? lines[i + 1].trim() : "";
      if (!isHeading && !isBlockquote && !isList && (/^=+$/.test(underline) || /^-+$/.test(underline))) {
        if (prevType !== "blank" && prevType !== "heading")
          out.push("");
        out.push(convertInline(`${underline[0] === "=" ? "#" : "##"} ${trimmed}`));
        i++;
        if (i + 1 < lines.length && lines[i + 1].trim() !== "")
          out.push("");
        prevType = "heading";
        continue;
      }
      if (isHeading && prevType !== "blank" && prevType !== "heading")
        out.push("");
      if (isBlockquote && prevType !== "blank" && prevType !== "blockquote")
        out.push("");
      if (!isBlockquote && !isHeading && !isList && prevType === "blockquote")
        out.push("");
      const isTopLevelList = isList && prevType !== "list";
      if (isTopLevelList && prevType !== "blank")
        out.push("");
      const dedent = indent2 >= 1 && indent2 <= 3 && (isHeading || isBlockquote);
      let body = dedent ? line.slice(indent2) : line;
      if (isHeading)
        body = body.replace(/[ \t]+#+[ \t]*$/, "");
      if (isList)
        body = body.replace(/^(\s*)\+(\s)/, "$1-$2");
      out.push(convertInline(body));
      if (isHeading && i + 1 < lines.length) {
        const next = lines[i + 1].trim();
        if (next !== "" && !/^#{1,6}\s/.test(next))
          out.push("");
      }
      if (isHeading)
        prevType = "heading";
      else if (isList)
        prevType = "list";
      else if (isBlockquote)
        prevType = "blockquote";
      else
        prevType = "text";
    }
    return out.join("\n").replace(/\n{3,}/g, "\n\n");
  }

  // ../../media/mark/data/work/git/carve-js/dist/lint.js
  function locate(node) {
    const p = node.pos;
    return {
      line: p?.startLine ?? 1,
      column: p?.startColumn ?? 1,
      start: p?.startOffset ?? 0,
      end: p?.endOffset ?? p?.startOffset ?? 0
    };
  }
  function collectCrossrefs(doc) {
    const found = [];
    const visit = (value) => {
      if (Array.isArray(value)) {
        for (const item of value)
          visit(item);
        return;
      }
      if (value && typeof value === "object") {
        const node = value;
        if (node.type === "crossref" && typeof node.target === "string") {
          found.push({ target: node.target, node });
        }
        for (const key of Object.keys(node)) {
          if (key !== "pos" && key !== "attrs")
            visit(node[key]);
        }
      }
    };
    visit(doc.children);
    return found;
  }
  function lintCarve(source, opts = {}) {
    const doc = parse(source, { positions: true });
    const asciiFold = opts.asciiHeadingIds ?? false;
    const out = [];
    const used = /* @__PURE__ */ new Set();
    for (const block of doc.children) {
      if (block.type !== "heading")
        continue;
      const heading = block;
      const explicit = heading.attrs?.id;
      if (explicit) {
        if (used.has(explicit)) {
          out.push({
            ...locate(heading),
            rule: "duplicate-heading-id",
            message: `Duplicate heading id "${explicit}": the repeated HTML id is invalid, and cross-references to it resolve to the first occurrence.`
          });
        }
        used.add(explicit);
        continue;
      }
      const base = slugify(inlineText(heading.children), asciiFold);
      if (!base)
        continue;
      if (used.has(base)) {
        let n = 2;
        while (used.has(`${base}-${n}`))
          n++;
        const id = `${base}-${n}`;
        out.push({
          ...locate(heading),
          rule: "duplicate-heading-id",
          message: `Heading slug "${base}" collides with an earlier heading; its auto id becomes "${id}", and ambiguous references to "${base}" resolve to the first occurrence.`
        });
        used.add(id);
      } else {
        used.add(base);
      }
    }
    for (const { target, node } of collectCrossrefs(doc)) {
      if (used.has(target))
        continue;
      out.push({
        ...locate(node),
        rule: "broken-crossref",
        message: `Cross-reference </#${target}> has no matching heading id; it renders as the literal text "</#${target}>".`
      });
    }
    out.sort((a, b) => a.start - b.start || a.line - b.line || a.column - b.column);
    return out;
  }
  function formatLintWarnings(warnings, file = "<stdin>") {
    return warnings.map((w) => `${file}:${w.line}:${w.column} ${w.rule} \u2014 ${w.message}`).join("\n");
  }

  // ../../media/mark/data/work/git/carve-js/dist/tab-normalize.js
  function tabNormalize(width = 2) {
    const spaces = " ".repeat(Math.max(0, width));
    const expand = (s) => s.replace(/\t/g, spaces);
    const visit = (node) => {
      if (Array.isArray(node)) {
        for (const child of node)
          visit(child);
        return;
      }
      if (node === null || typeof node !== "object")
        return;
      const n = node;
      if (n.type === "code-block" && typeof n.content === "string") {
        n.content = expand(n.content);
      } else if (n.type === "code" && typeof n.value === "string") {
        n.value = expand(n.value);
      }
      for (const key in n) {
        if (key === "pos" || key === "attrs")
          continue;
        visit(n[key]);
      }
    };
    return {
      name: "tab-normalize",
      beforeRender(doc) {
        visit(doc);
        return doc;
      }
    };
  }

  // ../../media/mark/data/work/git/carve-js/dist/index.js
  function parse2(source, opts = {}) {
    return parse(source, opts);
  }
  function renderHtml2(ast, opts = {}) {
    return renderHtml(ast, opts);
  }
  function resolve(doc, opts = {}) {
    return resolveHeadingIds(doc, opts.asciiHeadingIds ?? false);
  }
  function carveToHtml(source, opts = {}) {
    const parseOpts = opts.sourceLine ? { ...opts, positions: true } : opts;
    let doc = resolve(parse2(source, parseOpts), { asciiHeadingIds: opts.asciiHeadingIds ?? false });
    const exts = opts.extensions ?? [];
    for (const ext of exts)
      if (ext.afterParse)
        doc = ext.afterParse(doc);
    for (const ext of exts)
      if (ext.beforeRender)
        doc = ext.beforeRender(doc);
    return renderHtml2(doc, opts);
  }
  return __toCommonJS(index_exports);
})();
