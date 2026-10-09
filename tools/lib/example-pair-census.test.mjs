import { test } from 'node:test'
import assert from 'node:assert/strict'

import { countDeclaredPairs } from './example-pair-census.mjs'

test('every carve fence in a compare block is one declared pair', () => {
  const page = [
    '::: compare',
    '```carve', 'one', '```',
    '```html', '<p>one</p>', '```',
    '````carve', '```carve', 'nested, not a pair', '```', '````',
    '```html', '<pre>two</pre>', '```',
    '```carve', 'three', '```',
    '```html', '<p>three</p>', '```',
    ':::',
    '```carve', 'outside any block', '```',
  ]
  const got = countDeclaredPairs(page)
  assert.equal(got, 3, `got ${got} pairs, want 3`)
})
