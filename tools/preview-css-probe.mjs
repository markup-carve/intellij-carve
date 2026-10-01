#!/usr/bin/env node
/*
 * Proves the preview's stylesheet gaps are closed in the RENDER, not in its text.
 *
 * The preview is a JCEF browser, and JCEF is Chromium, so a computed style read
 * out of headless Chromium is the closest a check outside the IDE gets to what
 * a user sees. The reason to bother: #224's defect class is a rule that looks
 * right in the source and never applies. `.gallery :is(img, video)` reads as a
 * tile rule and reaches an image nested arbitrarily deep; `img { max-width }`
 * reads as image styling and leaves a promoted block image inline. A grep for
 * either selector passes in both the broken and the fixed tree.
 *
 * Usage:
 *   ./gradlew test --tests '*CarveCssProbePageTest*'   # writes the page
 *   node tools/preview-css-probe.mjs
 *
 * Needs Playwright's Chromium (`npx playwright install chromium`). Not wired
 * into `check` or CI for the same reason preview-offline-probe.mjs is not: a
 * browser download in front of every build.
 */
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const pageFile = path.resolve(process.argv[2] ?? 'build/preview-css-probe/index.html');

if (!existsSync(pageFile)) {
    console.error(
        `${pageFile} is not there.\n` +
        `Generate it first:  ./gradlew test --tests '*CarveCssProbePageTest*'`,
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
const page = await browser.newPage();
await page.goto(pathToFileURL(pageFile).href, { waitUntil: 'load' });

const seen = await page.evaluate(() => {
    const style = sel => getComputedStyle(document.querySelector(sel));
    const box = sel => document.querySelector(sel).getBoundingClientRect();
    return {
        blockImageDisplay: style('.carve > img:nth-of-type(1)').display,
        blockImageMargin: style('.carve > img:nth-of-type(1)').marginBottom,
        // The reading that no CSS assertion can give: two promoted images are
        // on separate lines, which is what the blank line between them meant.
        blockImagesStacked: box('.carve > img:nth-of-type(2)').top
            >= box('.carve > img:nth-of-type(1)').bottom,
        inlineImageDisplay: style('#inline-image img').display,
        tileFit: style('#tiles > figure > img').objectFit,
        nestedFit: style('#tiles .deep img').objectFit,
        galleryMinSize: style('#tiles').getPropertyValue('--carve-gallery-min-size').trim(),
        galleryColumns: style('#tiles').gridTemplateColumns,
        markBackground: style('#highlight mark').backgroundColor,
        markLinkColor: style('#highlight mark a').color,
    };
});

await browser.close();

const checks = [
    // The separate finding in #224.
    ['a promoted block image is a block', seen.blockImageDisplay === 'block', seen.blockImageDisplay],
    ['two promoted images stack', seen.blockImagesStacked, `margin-bottom ${seen.blockImageMargin}`],
    ['an image inside a paragraph stays inline', seen.inlineImageDisplay === 'inline', seen.inlineImageDisplay],
    // Gap 1: the tile rule reaches a direct figure child and nothing deeper.
    ['a gallery tile is cropped', seen.tileFit === 'cover', seen.tileFit],
    ['an image nested inside a tile is not', seen.nestedFit !== 'cover', seen.nestedFit],
    // Gap 2: the token the refreshed recipes read actually resolves.
    ['the gallery min size is a token', seen.galleryMinSize === '12rem', seen.galleryMinSize || '(unset)'],
    ['the gallery grid resolved it', /px/.test(seen.galleryColumns), seen.galleryColumns],
    // What this plugin does better than the published package, kept measured so
    // a later refresh cannot quietly take it away.
    ['a highlight has its own background', seen.markBackground === 'rgb(245, 223, 138)', seen.markBackground],
    ['a link inside a highlight keeps the highlight ink', seen.markLinkColor === 'rgb(31, 31, 31)', seen.markLinkColor],
];

let failed = 0;
for (const [name, ok, detail] of checks) {
    if (!ok) failed++;
    console.log(`${ok ? 'ok  ' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
}
process.exit(failed === 0 ? 0 : 1);
