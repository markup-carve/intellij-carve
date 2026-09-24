#!/usr/bin/env node
// Writes the per-language fenced code rules into src/main/resources/textmate/carve.tmLanguage.json.
//
//   node tools/generate-fence-languages.mjs          rewrite the generated entries
//   node tools/generate-fence-languages.mjs --check  exit 1 when they are stale
//
// Each generated rule is a copy of a hand-written generic fence rule with its
// language capture narrowed to one language, so the fence, title, label and
// closer handling cannot drift from the generic rule.
//
// Ported from vscode-carve's tools/generate-fence-languages.mjs. Three deltas,
// all forced by the IDE rather than chosen:
//
//  * The three generic rules have this grammar's names, and the two container
//    variants are separate rules here where upstream keeps both in one.
//  * Kotlin is included under BOTH `source.kotlin` and `source.Kotlin`: the IDE
//    bundles a legacy-format Kotlin grammar whose scope carries the capital K,
//    while upstream's VS Code grammar uses the lowercase one.
//  * No `embeddedLanguages` map. That is a VS Code manifest contribution, and
//    the IDE's TextMate bridge has no equivalent, so comment toggling and
//    brackets inside a fence stay Carve's.
//
// The file is spliced, never re-serialized: the rest of it keeps its own layout.

import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const grammarPath = resolve(root, 'src/main/resources/textmate/carve.tmLanguage.json')

// [language id, info-string words, grammar scopes to include]
//
// `source.toml` has no bundle in a stock IDE, so a `toml` fence falls back to a
// flat body. The rule is kept for the IDE that has one installed: an include
// that resolves to nothing is skipped, which is the same fallback every other
// language takes.
export const LANGUAGES = [
  ['carve', ['carve', 'crv'], ['text.carve']],
  ['javascript', ['js', 'javascript', 'mjs', 'cjs'], ['source.js']],
  ['javascriptreact', ['jsx'], ['source.js.jsx']],
  ['typescript', ['ts', 'typescript', 'mts', 'cts'], ['source.ts']],
  ['typescriptreact', ['tsx'], ['source.tsx']],
  ['json', ['json', 'json5'], ['source.json']],
  ['jsonc', ['jsonc'], ['source.json.comments']],
  ['yaml', ['yaml', 'yml'], ['source.yaml']],
  ['toml', ['toml'], ['source.toml']],
  ['html', ['html', 'htm', 'xhtml'], ['text.html.basic']],
  ['xml', ['xml', 'svg', 'xsd'], ['text.xml']],
  ['css', ['css'], ['source.css']],
  ['scss', ['scss'], ['source.css.scss']],
  ['less', ['less'], ['source.css.less']],
  ['php', ['php'], ['text.html.basic', 'source.php']],
  ['python', ['python', 'py', 'py3'], ['source.python']],
  ['ruby', ['ruby', 'rb'], ['source.ruby']],
  ['rust', ['rust', 'rs'], ['source.rust']],
  ['go', ['go', 'golang'], ['source.go']],
  ['java', ['java'], ['source.java']],
  ['kotlin', ['kotlin', 'kt', 'kts'], ['source.kotlin', 'source.Kotlin']],
  ['swift', ['swift'], ['source.swift']],
  ['dart', ['dart'], ['source.dart']],
  ['c', ['c', 'h'], ['source.c']],
  ['cpp', ['cpp', 'c\\+\\+', 'cxx', 'cc', 'hpp'], ['source.cpp']],
  ['csharp', ['cs', 'csharp', 'c#'], ['source.cs']],
  ['shellscript', ['sh', 'bash', 'shell', 'zsh'], ['source.shell']],
  ['powershell', ['powershell', 'ps1', 'pwsh'], ['source.powershell']],
  ['bat', ['bat', 'batch', 'cmd'], ['source.batchfile']],
  ['sql', ['sql'], ['source.sql']],
  ['lua', ['lua'], ['source.lua']],
  ['perl', ['perl', 'pl'], ['source.perl']],
  ['r', ['r'], ['source.r']],
  ['markdown', ['markdown', 'md'], ['text.html.markdown']],
  ['diff', ['diff', 'patch'], ['source.diff']],
  ['dockerfile', ['dockerfile', 'docker'], ['source.dockerfile']],
  ['makefile', ['makefile', 'make'], ['source.makefile']],
  ['ini', ['ini', 'cfg'], ['source.ini']],
]

// The optional info-string capture, in the two spellings the generic rules use:
// the marker-line rule escapes the quote inside the character class, the other
// two do not. Both parse to the same class.
const LANGUAGE_CAPTURES = ['([^`~\\s\\["]+)?', '([^`~\\s\\[\\"]+)?']
const BARE_FENCE = '[ \\t]*[`~]{3,}[ \\t]*$'

// [generated entry, hand-written entry, how the body is embedded]
//
// A `while` GUARD CANNOT SEE THE BEGIN RULE'S CAPTURES in the IDE's engine.
// Measured on a probe grammar: `^(?=\\1[ \\t])` in a `while` never matches, and the
// same backref in a trailing lookahead matches EMPTY, so the guard is either always
// true or always false - neither of which is the container boundary. The `end`
// patterns do resolve `\\1`, which is where both container rules already carry it.
//
// So only the column-0 family gets a `while` region: its boundary is a bare fence
// line, which needs no capture. Inside a container the body is included directly and
// the generic rule's own `end` closes the block - the same boundary it has today,
// including `- outer` / `  - ```js` / `    code` / `  - sibling`, where a `while`
// region swallowed the sibling item (CarveMarkerLineMultilineOpenerTest).
//
// The cost of the direct include is what `while` was there to prevent: an
// unterminated string or block comment in the embedded language can run past the
// fence's closer inside a list item. It cannot escape the item, because the generic
// rule's second `end` alternative is the container boundary.
const VARIANTS = [
  ['fenced-code-languages', 'code-blocks', `(^|\\G)(?!${BARE_FENCE})`],
  ['fenced-code-languages-on-a-marker-line', 'code-fence-on-marker-line', null],
  ['fenced-code-languages-at-a-body-column', 'code-fence-at-body-column', null],
]

function narrowLanguage(begin, words) {
  const capture = LANGUAGE_CAPTURES.find((c) => begin.split(c).length === 2)
  if (!capture) {
    throw new Error(`generic fence rule no longer has exactly one language capture: ${begin}`)
  }
  return begin.replace(capture, `((?i:${words.join('|')}))`)
}

function languageRule(generic, [id, words, scopes], whileGuard) {
  const { comment, ...rule } = generic
  rule.begin = narrowLanguage(generic.begin, words)
  const include = scopes.map((scope) => ({ include: scope }))
  const embedded = `meta.embedded.block.${id}`
  // A Carve body may hold fences of its own, so it always takes the outer rule's
  // exact-width closer rather than a guard that stops at the first bare fence.
  if (!whileGuard || id === 'carve') {
    // A scope name may carry several scopes, which is how the body keeps the raw
    // scope the generic rule gives it AND gains the embedded one.
    const contentName = rule.contentName ? `${rule.contentName} ${embedded}` : embedded
    return { ...rule, contentName, patterns: include }
  }
  // Measured: the IDE's TextMate engine honors `while` and resolves an include into
  // another registered grammar in the same syntax table (CarveFenceEmbedTest).
  return {
    ...rule,
    patterns: [{ begin: '\\G', while: whileGuard, contentName: embedded, patterns: include }],
  }
}

export function generate(grammar) {
  const entries = {}
  for (const [key, source, whileGuard] of VARIANTS) {
    const generic = grammar.repository[source].patterns.filter((rule) => rule.begin)[0]
    entries[key] = { patterns: LANGUAGES.map((language) => languageRule(generic, language, whileGuard)) }
  }
  return entries
}

// Returns [start, end) of the JSON value that follows `"key":` at `depth` 2
// (a repository entry), skipping over strings so braces inside regexes do not count.
function valueSpan(text, key) {
  const needle = `"${key}": `
  let depth = 0
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (ch === '"') {
      if (depth === 2 && text.startsWith(needle, i)) {
        const start = i + needle.length
        return [start, skipValue(text, start)]
      }
      i = skipString(text, i)
    } else if (ch === '{' || ch === '[') depth++
    else if (ch === '}' || ch === ']') depth--
  }
  return null
}

function skipString(text, i) {
  for (i++; text[i] !== '"'; i++) if (text[i] === '\\') i++
  return i
}

function skipValue(text, i) {
  let depth = 0
  for (; i < text.length; i++) {
    const ch = text[i]
    if (ch === '"') i = skipString(text, i)
    else if (ch === '{' || ch === '[') depth++
    else if (ch === '}' || ch === ']') {
      depth--
      if (depth === 0) return i + 1
    }
  }
  throw new Error('unterminated JSON value')
}

// One rule per line keeps a language's three variants reviewable as one-line diffs.
const indentValue = (value) =>
  `{\n      "patterns": [\n${value.patterns.map((rule) => `        ${JSON.stringify(rule)}`).join(',\n')}\n      ]\n    }`

function splice(text, entries) {
  for (const [key, value] of Object.entries(entries)) {
    const span = valueSpan(text, key)
    if (span) {
      text = text.slice(0, span[0]) + indentValue(value) + text.slice(span[1])
    } else {
      const anchor = valueSpan(text, 'code-fence-at-body-column')
      text = `${text.slice(0, anchor[1])},\n    "${key}": ${indentValue(value)}${text.slice(anchor[1])}`
    }
  }
  return text
}

function isMain() {
  return process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)
}

if (isMain()) {
  const check = process.argv.includes('--check')
  const text = readFileSync(grammarPath, 'utf8')
  const grammar = JSON.parse(text)
  const next = splice(text, generate(grammar))
  JSON.parse(next)
  if (check) {
    if (next !== text) {
      console.error(
        'src/main/resources/textmate/carve.tmLanguage.json: generated fence rules are stale; ' +
          'run node tools/generate-fence-languages.mjs',
      )
      process.exit(1)
    }
    process.exit(0)
  }
  writeFileSync(grammarPath, next)
}
