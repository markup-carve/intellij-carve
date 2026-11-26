// Entry of src/main/resources/js/carve.iife.js, bundled by tools/build-carve-bundle.sh
// from inside a carve-js checkout, so `./dist` is that checkout's build. The global
// `carve` holds the 0.2 root API and the standard extensions, the Markdown, plain
// text and ANSI exports and the importers under their own names.
export * from './dist/index.js'
export * as extensions from './dist/extensions.js'
export * as exporters from './dist/export.js'
export * as importers from './dist/import.js'
import { renderWithDocumentTransforms } from './dist/index.js'

// The preview render: the document transforms of the extension set (C2-X-053) run first.
export function renderPreview(document, options) {
  const { transforms = [], ...rest } = options
  return renderWithDocumentTransforms(document, transforms, rest).html
}

const BARE_URL = /https?:\/\/[^\s<>]+/g
const TRAILING_PUNCTUATION = /[.,;:!?'")\]]+$/
const OFF_SITE = /^https?:\/\//i
const NO_AUTOLINK = new Set(['link', 'image', 'code', 'code_block', 'raw_block', 'raw_inline', 'math'])

function autolinked(node) {
  const value = node.value || ''
  const pieces = []
  let at = 0
  for (const match of value.matchAll(BARE_URL)) {
    const url = match[0].replace(TRAILING_PUNCTUATION, '')
    if (!/^https?:\/\/./.test(url)) continue
    if (match.index > at) pieces.push({ type: 'text', value: value.slice(at, match.index) })
    pieces.push({ type: 'link', href: url, children: [{ type: 'text', value: url }] })
    at = match.index + url.length
  }
  if (pieces.length === 0) return [node]
  if (at < value.length) pieces.push({ type: 'text', value: value.slice(at) })
  return pieces
}

// The 0.1 autolink and externalLinks extensions, rebuilt as a transform of the
// parsed tree: a bare http(s) URL in prose becomes a link, and an off-site link
// opens in a new browsing context with rel="noopener noreferrer".
export function decorateLinks(document) {
  const walk = (node, linkable) => {
    if (node.type === 'link' && node.href && OFF_SITE.test(node.href)) {
      const pairs = (node.attrs && node.attrs.keyValues) || []
      const has = (key) => pairs.some((pair) => pair[0] === key)
      const added = []
      if (!has('target')) added.push(['target', '_blank'])
      if (!has('rel')) added.push(['rel', 'noopener noreferrer'])
      if (added.length) node.attrs = Object.assign({}, node.attrs, { keyValues: pairs.concat(added) })
    }
    const childLinkable = linkable && !NO_AUTOLINK.has(node.type)
    for (const key of Object.keys(node)) {
      const value = node[key]
      if (key === 'pos' || key === 'attrs' || !Array.isArray(value)) continue
      if (!value.some((child) => child && typeof child === 'object' && typeof child.type === 'string')) continue
      if (childLinkable) node[key] = value.flatMap((child) => (child.type === 'text' ? autolinked(child) : [child]))
      for (const child of node[key]) walk(child, childLinkable)
    }
  }
  walk(document, true)
  return document
}
