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
  let declared = 0
  let marker = null
  let fence = null
  for (const line of lines) {
    if (fence !== null) {
      if (line.startsWith(fence) && line.slice(fence.length).trim() === '') fence = null
      continue
    }
    const ticks = /^`{3,}/.exec(line)
    if (ticks !== null) {
      fence = ticks[0]
      if (marker !== null && line.slice(fence.length).trim() === 'carve') declared++
      continue
    }
    const trimmed = line.trim()
    const colons = /^:{3,}/.exec(trimmed)
    if (colons === null) continue
    if (marker === null) {
      if (/^[ \t]+compare(?:[ \t]|$)/.test(trimmed.slice(colons[0].length))) marker = colons[0]
    } else if (trimmed === marker) {
      marker = null
    }
  }
  return declared
}
