#!/usr/bin/env node
/*
 * Proves the live preview renders with no network at all.
 *
 * The JVM tests can check that the page references no remote URL and that every
 * local URL resolves to a real file. What they cannot check is the thing the
 * change is actually for: that the page, loaded with the network taken away,
 * still colours code, draws a chart, typesets math and renders a diagram. Those
 * four are exactly what silently disappeared when the assets came from a CDN,
 * and a preview that merely looks plainer is not a failure any assertion on the
 * HTML would catch.
 *
 * So this loads the REAL page - the bytes CarvePreviewHtml produced, written to
 * build/preview-probe/index.html by CarvePreviewHtmlTest - in Chromium, aborts
 * every request that is not file://, and asserts on the resulting DOM.
 *
 * Usage:
 *   ./gradlew test --tests '*CarvePreviewHtmlTest*'   # writes the page
 *   node tools/preview-offline-probe.mjs
 *
 * Needs Playwright's Chromium (`npx playwright install chromium`). Deliberately
 * NOT wired into `check` or CI: it would put a browser download in front of
 * every build for a check that belongs to one file.
 */
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const pageFile = path.resolve(process.argv[2] ?? 'build/preview-probe/index.html');

if (!existsSync(pageFile)) {
    console.error(
        `${pageFile} is not there.\n` +
        `Generate it first:  ./gradlew test --tests '*CarvePreviewHtmlTest*'`,
    );
    process.exit(2);
}

let chromium;
try {
    ({ chromium } = require('playwright'));
} catch {
    console.error('playwright is not installed. Try:  npx playwright install chromium');
    process.exit(2);
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();

// The whole point. Anything that is not a local file never leaves the process,
// and is recorded so the report can name it rather than just failing.
const external = [];
await context.route('**/*', route => {
    const url = route.request().url();
    if (url.startsWith('file://')) return route.continue();
    external.push(url);
    return route.abort();
});

const page = await context.newPage();
const consoleErrors = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', e => consoleErrors.push(`pageerror: ${e.message}`));

await page.goto(pathToFileURL(pageFile).href, { waitUntil: 'load' });
// Mermaid and MathJax both finish asynchronously.
await page.waitForFunction(
    () => document.querySelector('.mermaid-rendered svg') && document.querySelector('mjx-container'),
    null,
    { timeout: 15000 },
).catch(() => {});
await page.evaluate(() => document.fonts.ready);

const seen = await page.evaluate(() => {
    const copy = document.querySelector('.carve-copy');
    return {
        highlightedTokens: document.querySelectorAll('code.hljs .hljs-keyword, code.hljs .hljs-string, code.hljs .hljs-comment').length,
        mermaidSvgs: document.querySelectorAll('.mermaid-rendered svg').length,
        mermaidNodes: document.querySelectorAll('.mermaid-rendered svg .node, .mermaid-rendered svg text').length,
        chartCanvases: Array.from(document.querySelectorAll('div.chart canvas'))
            .filter(c => c.width > 0 && c.height > 0).length,
        mathContainers: document.querySelectorAll('mjx-container').length,
        mathGlyphs: document.querySelectorAll('mjx-container mjx-mi, mjx-container mjx-mn, mjx-container mjx-c').length,
        mathFontsLoaded: Array.from(document.fonts).filter(f => f.status === 'loaded').map(f => f.family),
        mathFontsFailed: Array.from(document.fonts).filter(f => f.status === 'error').map(f => f.family),
        copyButtons: document.querySelectorAll('.carve-copy').length,
        copyIcons: document.querySelectorAll('.carve-copy > svg').length,
        copyRestOpacity: getComputedStyle(document.querySelector('.carve-copy')).opacity,
        permalinks: document.querySelectorAll('h1 > .permalink, h2 > .permalink').length,
        permalinkRestOpacity: document.querySelector('.permalink')
            ? getComputedStyle(document.querySelector('.permalink')).opacity : null,
        codeWrappers: document.querySelectorAll('.carve-code').length,
        codeHeader: document.querySelector('.carve-code > .code-header')?.textContent ?? null,
        langBadge: document.querySelector('.carve-code[data-lang]')?.dataset.lang ?? null,
        // The heaviness that was reported: a <pre> and its <code> painting two
        // different surfaces, the inner one inset by hljs's own 1em of padding.
        preBackground: getComputedStyle(document.querySelector('.carve-code > pre')).backgroundColor,
        codeBackground: getComputedStyle(document.querySelector('.carve-code > pre > code')).backgroundColor,
        codePadding: getComputedStyle(document.querySelector('.carve-code > pre > code')).paddingTop,
        preBorderWidth: getComputedStyle(document.querySelector('.carve-code > pre')).borderTopWidth,
        isSecureContext: window.isSecureContext,
        hasAsyncClipboard: !!(navigator.clipboard && navigator.clipboard.writeText),
    };
});

// Copy button: exercise both fallbacks the page ships. The IDE bridge is not
// present here (this page has none), so path 2 and path 3 are what is left, and
// path 3 is what a CEF permission refusal would fall through to.
const clipboard = await page.evaluate(async () => {
    const button = document.querySelector('.carve-copy');
    const out = {};
    button.click();
    await new Promise(r => setTimeout(r, 300));
    out.asyncState = button.dataset.state ?? null;
    out.asyncLabel = button.title;

    // Now with the async API taken away, which is what a refused
    // clipboard-write permission looks like from the page's side.
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    const second = document.querySelectorAll('.carve-copy')[0];
    second.dataset.state = '';
    second.click();
    await new Promise(r => setTimeout(r, 300));
    out.legacyState = second.dataset.state ?? null;
    out.legacyLabel = second.title;
    return out;
});

await browser.close();

const checks = [
    ['no request left the page', external.length === 0, external.join(', ')],
    ['no console error', consoleErrors.length === 0, consoleErrors.join(' | ')],
    ['code is highlighted', seen.highlightedTokens > 0, `${seen.highlightedTokens} tokens`],
    ['a diagram rendered', seen.mermaidSvgs > 0 && seen.mermaidNodes > 0, `${seen.mermaidSvgs} svg / ${seen.mermaidNodes} parts`],
    ['a chart rendered', seen.chartCanvases > 0, `${seen.chartCanvases} canvas`],
    ['math typeset', seen.mathContainers > 0 && seen.mathGlyphs > 0, `${seen.mathContainers} containers / ${seen.mathGlyphs} glyphs`],
    ['MathJax webfonts loaded', seen.mathFontsLoaded.length > 0 && seen.mathFontsFailed.length === 0,
        `loaded ${seen.mathFontsLoaded.join(',')} failed ${seen.mathFontsFailed.join(',') || 'none'}`],
    ['copy buttons present', seen.copyButtons > 0 && seen.copyIcons === seen.copyButtons,
        `${seen.copyButtons} on ${seen.codeWrappers} blocks, ${seen.copyIcons} icons`],
    ['copy button is visible at rest', Number(seen.copyRestOpacity) > 0.2, `opacity ${seen.copyRestOpacity}`],
    ['heading permalinks are hidden until hover', seen.permalinks > 0 && seen.permalinkRestOpacity === '0',
        `${seen.permalinks} permalinks, resting opacity ${seen.permalinkRestOpacity}`],
    ['copy reports success (async clipboard)', clipboard.asyncState === 'done', `${clipboard.asyncState} / ${clipboard.asyncLabel}`],
    ['copy reports success (execCommand fallback)', clipboard.legacyState === 'done', `${clipboard.legacyState} / ${clipboard.legacyLabel}`],
    ['code block is one flat surface', seen.codeBackground === 'rgba(0, 0, 0, 0)' && seen.codePadding === '0px',
        `pre ${seen.preBackground}, code ${seen.codeBackground}, code padding ${seen.codePadding}`],
    ['code block has no outline', seen.preBorderWidth === '0px', seen.preBorderWidth],
];

let failed = 0;
for (const [name, ok, detail] of checks) {
    if (!ok) failed++;
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
}
console.log(`\nsecure context: ${seen.isSecureContext}, async clipboard available: ${seen.hasAsyncClipboard}`);
console.log(`code header: ${seen.codeHeader}, language badge: ${seen.langBadge}`);
process.exit(failed === 0 ? 0 : 1);
