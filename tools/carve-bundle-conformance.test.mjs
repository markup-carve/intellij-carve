import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'

const bundle = readFileSync(process.env.CARVE_BUNDLE_PATH ??
  new URL('../src/main/resources/js/carve.iife.js', import.meta.url), 'utf8')
const cases = JSON.parse(readFileSync(new URL('../src/test/resources/conformance-0.2.json', import.meta.url))).examples
const context = vm.createContext({ TextEncoder, TextDecoder, console })
vm.runInContext(bundle, context)
const render = vm.runInContext('carve.render', context)

test('the bundled renderer follows the pinned option-free render corpus', () => {
  for (const { id, input, html } of cases) {
    assert.equal(String(render(input)).trim(), html.trim(), id)
  }
})
