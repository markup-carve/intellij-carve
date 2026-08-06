// Render a corpus through a built Carve bundle and report how many documents
// come out differently from their goldens.
//
// The point is the ARTIFACT. Comparing the commit recorded in a bundle's
// provenance header against upstream tells you which commit was bundled; it
// never tells you what the bundled code now does. The preview bundle here sat
// 363 commits behind for three weeks rendering 140 of 690 documents wrongly,
// none of them throwing, so nothing surfaced as an error anywhere (#62).
//
// This is the networked half, used by .github/workflows/engine-drift.yml
// against markup-carve/carve `main`. The offline half is
// CarveBundleCorpusTest, which asks a narrower question - does the vendored
// bundle still agree with the corpus this repo PINS - on GraalJS, the host the
// plugin actually renders with. Both are needed: this one notices the pin going
// stale, that one notices the bundle going wrong on the plugin's own runtime.
//
// Usage:
//   node tools/corpus-through-bundle.mjs <bundle.iife.js> <corpus-dir> [--list]
//
// Exits 0 always; the caller decides what count is tolerable.

import { readFileSync, readdirSync } from 'node:fs'
import { resolve, basename } from 'node:path'
import vm from 'node:vm'

const [bundlePath, corpusDir] = process.argv.slice(2)
const list = process.argv.includes('--list')

if (!bundlePath || !corpusDir) {
  console.error('usage: node tools/corpus-through-bundle.mjs <bundle.iife.js> <corpus-dir> [--list]')
  process.exit(2)
}

// The bundle is an IIFE that assigns a `carve` global. Evaluate it in its own
// context with nothing but the globals the GraalJS banner also provides, so a
// bundle that reaches for something else fails here rather than silently
// picking up a Node built-in the plugin's host would not have.
const context = vm.createContext({ console, TextEncoder, TextDecoder })
vm.runInContext(readFileSync(bundlePath, 'utf8'), context, { filename: basename(bundlePath) })
const carveToHtml = vm.runInContext('carve.carveToHtml', context)
if (typeof carveToHtml !== 'function') {
  console.error(`${bundlePath} exposes no carve.carveToHtml`)
  process.exit(2)
}

const names = readdirSync(corpusDir)
  .filter((f) => f.endsWith('.crv'))
  .map((f) => basename(f, '.crv'))
  .sort()

let documents = 0
let threw = 0
const wrong = []

for (const name of names) {
  let expected
  try {
    expected = readFileSync(resolve(corpusDir, `${name}.html`), 'utf8')
  } catch {
    continue // a .crv with no .html pair is not a rendering assertion
  }
  documents++
  const source = readFileSync(resolve(corpusDir, `${name}.crv`), 'utf8')
  try {
    // Bare `carveToHtml(source)` with no options: the corpus contract every
    // Carve engine is measured against. The plugin's preview layers a showcase
    // extension set on top, which changes the output by design.
    if (String(carveToHtml(source)).trim() !== expected.trim()) wrong.push(name)
  } catch (e) {
    threw++
    wrong.push(`${name} (threw: ${e.message})`)
  }
}

if (documents === 0) {
  console.error(`No corpus pairs found under ${corpusDir}`)
  process.exit(2)
}

if (list) for (const w of wrong) console.log(w)
console.log(`documents=${documents}`)
console.log(`wrong=${wrong.length}`)
console.log(`threw=${threw}`)
