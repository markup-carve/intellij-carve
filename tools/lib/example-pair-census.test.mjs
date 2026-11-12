import { test } from 'node:test'
import assert from 'node:assert/strict'

import { countDeclaredPairs, declaredInputs } from './example-pair-census.mjs'

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

test('a repeated input is one corpus fixture unless its modifiers differ', () => {
  const page = [
    '::: compare',
    '```carve', 'same', '```',
    '```html', '<p>same</p>', '```',
    '```carve', 'same', '```',
    '```html', '<p>same</p>', '```',
    ':::',
    '::: compare no-final-newline',
    '```carve', 'same', '```',
    '```html', '<p>same</p>', '```',
    ':::',
  ]
  assert.equal(declaredInputs(page).length, 3)
  assert.equal(new Set(declaredInputs(page)).size, 2)
})

test('a modifier that leaves the bytes alone does not make a second fixture', () => {
  const page = [
    '::: compare',
    '```carve', 'same', '```',
    '```html', '<p>same</p>', '```',
    ':::',
    '::: compare no-render',
    '```carve', 'same', '```',
    '```html', '<p>same</p>', '```',
    ':::',
  ]
  assert.equal(new Set(declaredInputs(page)).size, 1)
})
