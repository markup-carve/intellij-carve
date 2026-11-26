// Compare the vendored Carve 0.2 preview bundle with the option-free render
// examples in the spec's conformance JSON. A reference bundle isolates editor
// bundle drift from upstream engine debt. The caller decides its tolerance.
//
// Usage: node tools/corpus-through-bundle.mjs <bundle.iife.js> <examples.json>
//        [--list] [--reference <bundle.iife.js>]

import { readFileSync } from 'node:fs'
import { basename } from 'node:path'
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
const [bundlePath, conformancePath] = positional

if (!bundlePath || !conformancePath) {
  console.error(
    'usage: node tools/corpus-through-bundle.mjs <bundle.iife.js> <conformance-json> [--list] [--reference <bundle.iife.js>]',
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
  const carveToHtml = vm.runInContext('carve.render', context)
  if (typeof carveToHtml !== 'function') {
    console.error(`${path} exposes no carve.render`)
    process.exit(2)
  }
  return carveToHtml
}

// Only option-free render examples have the same call contract as the plugin's
// bundled default renderer. The pinned 0.2 conformance source is the population
// guard: an absent or empty file is a failed measurement.
let examples
try {
  examples = JSON.parse(readFileSync(conformancePath, 'utf8')).examples
} catch (error) {
  console.error(`Cannot read 0.2 conformance at ${conformancePath}: ${error.message}`)
  process.exit(2)
}
if (!Array.isArray(examples)) {
  console.error(`${conformancePath} has no examples list`)
  process.exit(2)
}
const pairs = examples.filter((e) => e.kind === 'render' &&
  Object.keys(e.options ?? {}).length === 0 && typeof e.expected?.html === 'string')
  .map((e) => ({ name: e.id, source: e.input, expected: e.expected.html }))
if (pairs.length === 0 || new Set(pairs.map((p) => p.name)).size !== pairs.length) {
  console.error(`${conformancePath} has no applicable render examples or duplicate IDs`)
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
