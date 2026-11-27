import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'

const bin = mkdtempSync(join(tmpdir(), 'carve-css-drift-'))
const head = '4d2a0985a7d3c4f034f84545810f799a03b85e75'
const root = new URL('../src/main/resources/css/', import.meta.url).pathname
const checker = process.env.CARVE_CSS_CHECKER ?? new URL('./check-carve-css-drift.sh', import.meta.url).pathname

writeFileSync(join(bin, 'git'), '#!/bin/sh\nprintf "%s\\trefs/heads/main\\n" "$CSS_TEST_HEAD"\n')
writeFileSync(join(bin, 'curl'), `#!/bin/sh
name="\${2##*/}"
if [ "$CSS_TEST_MISMATCH" = "$name" ]; then
  printf 'different\\n'
else
  sed '1,/^ \\*\\//d' '${root}'"$name"
fi
`)
chmodSync(join(bin, 'git'), 0o755)
chmodSync(join(bin, 'curl'), 0o755)

function check(mismatch = '') {
  return spawnSync(checker, [], {
    env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, CSS_TEST_HEAD: head, CSS_TEST_MISMATCH: mismatch },
    encoding: 'utf8',
  })
}

test('an upstream commit changing other CSS layers leaves the four vendored layers current', () => {
  const result = check()
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stdout, /all four vendored layers match carve-css main/)
})

test('a changed vendored layer still fails the drift gate', () => {
  const result = check('recipes.css')
  assert.equal(result.status, 1)
  assert.match(result.stderr, /recipes.css differs/)
})
