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
// ## Why a reference bundle
//
// Counting how many of carve `main`'s documents the vendored bundle renders
// differently measures TWO lags added together:
//
//   1. the vendored bundle behind carve-js `main` - this repo's to fix, by
//      rerunning tools/build-carve-bundle.sh and committing the result;
//   2. carve-js `main` behind carve `main` - upstream's own conformance debt,
//      which no amount of rebuilding here can close.
//
// Gating on the sum makes the job fail for (2) with a remediation ("rebuild the
// bundle") that cannot work, and the only way out is to raise the tolerance -
// which is how a gate stops being read. So when `--reference` is given, the
// same corpus is driven through a bundle built from carve-js `main` and the
// gate number becomes the DIFFERENCE: documents the vendored bundle gets wrong
// that a freshly built one gets right. That is exactly the part this repo can
// act on, and it was 140 in the state nobody noticed.
//
// Usage:
//   node tools/corpus-through-bundle.mjs <bundle.iife.js> <corpus-dir> [--list]
//                                        [--reference <bundle.iife.js>]
//
// Exits 0 always; the caller decides what count is tolerable.

import { readFileSync, readdirSync } from 'node:fs'
import { resolve, basename } from 'node:path'
import vm from 'node:vm'

const argv = process.argv.slice(2)
const list = argv.includes('--list')

const referenceAt = argv.indexOf('--reference')
const referencePath = referenceAt === -1 ? null : argv[referenceAt + 1]
if (referenceAt !== -1 && !referencePath) {
  console.error('--reference needs a bundle path')
  process.exit(2)
}

// `referenceAt + 1` is the flag's VALUE, not a positional. Guard on the flag
// actually being present: when it is absent `referenceAt` is -1, and an
// unguarded `i !== referenceAt + 1` silently swallows argv[0] - the bundle
// path - leaving the no-reference invocation to die on a usage message.
const valueAt = referenceAt === -1 ? -1 : referenceAt + 1
const positional = argv.filter((a, i) => !a.startsWith('--') && i !== valueAt)
const [bundlePath, corpusDir] = positional

if (!bundlePath || !corpusDir) {
  console.error(
    'usage: node tools/corpus-through-bundle.mjs <bundle.iife.js> <corpus-dir> [--list] [--reference <bundle.iife.js>]',
  )
  process.exit(2)
}

// The bundle is an IIFE that assigns a `carve` global. Evaluate it in its own
// context with nothing but the globals the GraalJS banner also provides, so a
// bundle that reaches for something else fails here rather than silently
// picking up a Node built-in the plugin's host would not have.
function load(path) {
  const context = vm.createContext({ console, TextEncoder, TextDecoder })
  vm.runInContext(readFileSync(path, 'utf8'), context, { filename: basename(path) })
  const carveToHtml = vm.runInContext('carve.carveToHtml', context)
  if (typeof carveToHtml !== 'function') {
    console.error(`${path} exposes no carve.carveToHtml`)
    process.exit(2)
  }
  return carveToHtml
}

const names = readdirSync(corpusDir)
  .filter((f) => f.endsWith('.crv'))
  .map((f) => basename(f, '.crv'))
  .sort()

// Read every pair once; both bundles are measured against the same inputs.
const pairs = []
for (const name of names) {
  let expected
  try {
    expected = readFileSync(resolve(corpusDir, `${name}.html`), 'utf8')
  } catch {
    continue // a .crv with no .html pair is not a rendering assertion
  }
  pairs.push({ name, expected, source: readFileSync(resolve(corpusDir, `${name}.crv`), 'utf8') })
}

if (pairs.length === 0) {
  console.error(`No corpus pairs found under ${corpusDir}`)
  process.exit(2)
}

/** @returns {{wrong: Map<string, string>, threw: number}} */
function measure(carveToHtml) {
  const wrong = new Map()
  let threw = 0
  for (const { name, expected, source } of pairs) {
    try {
      // Bare `carveToHtml(source)` with no options: the corpus contract every
      // Carve engine is measured against. The plugin's preview layers a
      // showcase extension set on top, which changes the output by design.
      if (String(carveToHtml(source)).trim() !== expected.trim()) {
        wrong.set(name, 'differs')
      }
    } catch (e) {
      threw++
      wrong.set(name, `threw: ${e.message}`)
    }
  }
  return { wrong, threw }
}

const vendored = measure(load(bundlePath))
const reference = referencePath ? measure(load(referencePath)) : null

// Documents the vendored bundle gets wrong that a bundle built from carve-js
// `main` gets right. Without a reference this degrades to the whole count,
// which is the pre-existing behaviour and still the right number when the two
// lags cannot be told apart.
const attributable = [...vendored.wrong.keys()].filter(
  (name) => !reference || !reference.wrong.has(name),
)

if (list) {
  for (const [name, why] of vendored.wrong) {
    const upstream = reference && reference.wrong.has(name) ? ' [also wrong in carve-js main]' : ''
    console.log(why === 'differs' ? `${name}${upstream}` : `${name} (${why})${upstream}`)
  }
}

console.log(`documents=${pairs.length}`)
console.log(`wrong=${vendored.wrong.size}`)
console.log(`threw=${vendored.threw}`)
if (reference) {
  console.log(`reference_wrong=${reference.wrong.size}`)
  console.log(`reference_threw=${reference.threw}`)
}
console.log(`attributable=${attributable.length}`)
