// The same census as the spec's scripts/lib/example-pair-census.mjs: one pair
// per `carve` fence inside a `::: compare` block, and nothing inside a fence is
// markup, so a ```` example holding a ``` carve line declares no pair of its
// own. It lives here rather than inline in a tool so a test can call it with a
// synthetic page instead of only through a whole corpus run.

/**
 * @param {string[]} lines source lines of an example page
 * @returns {number} pairs the page declares
 */
export function countDeclaredPairs(lines) {
  return declaredInputs(lines).length
}

/**
 * One key per declared pair: the block's `::: compare` modifiers plus the
 * `carve` fence's content. The spec writes one corpus fixture per distinct
 * input, so the corpus size is the number of distinct keys across all pages.
 *
 * @param {string[]} lines source lines of an example page
 * @returns {string[]} one key per pair the page declares
 */
export function declaredInputs(lines) {
  const inputs = []
  let marker = null
  let modifiers = ''
  let fence = null
  let body = null
  for (const line of lines) {
    if (fence !== null) {
      if (line.startsWith(fence) && line.slice(fence.length).trim() === '') {
        if (body !== null) inputs.push(`${modifiers}\u0000${body.join('\n')}`)
        fence = null
        body = null
      } else if (body !== null) {
        body.push(line)
      }
      continue
    }
    const ticks = /^`{3,}/.exec(line)
    if (ticks !== null) {
      fence = ticks[0]
      if (marker !== null && line.slice(fence.length).trim() === 'carve') body = []
      continue
    }
    const trimmed = line.trim()
    const colons = /^:{3,}/.exec(trimmed)
    if (colons === null) continue
    if (marker === null) {
      const rest = trimmed.slice(colons[0].length)
      if (/^[ \t]+compare(?:[ \t]|$)/.test(rest)) {
        marker = colons[0]
        modifiers = rest.trim().split(/\s+/).slice(1).sort().join(' ')
      }
    } else if (trimmed === marker) {
      marker = null
    }
  }
  return inputs
}
