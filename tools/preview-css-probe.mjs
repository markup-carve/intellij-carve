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
        // The inline stylesheet's own spoiler design, which the vendored
        // extensions layer must not repaint. `filter` is the mechanism; a
        // border would turn a blurred word into a box.
        inlineSpoilerFilter: style('#inline-spoiler .spoiler').filter,
        inlineSpoilerBorder: style('#inline-spoiler .spoiler').borderTopWidth,
        inlineSpoilerPadding: style('#inline-spoiler .spoiler').paddingTop,
        // The block form keeps the vendored box on purpose; what must survive
        // is the local disclosure affordance, which upstream has no rule for.
        blockSpoilerAffordance: getComputedStyle(
            document.querySelector('#block-spoiler > summary'), '::after',
        ).content,
    };
});

/*
 * The radio-driven panel ladder, measured by driving it.
 *
 * carve-css 0.1.2 pairs a radio with its panel BY POSITION across the sibling
 * combinator, because the engine emits every radio and label first and the
 * panels after - the interleaved `:checked + .tabs-label + .tabs-panel` form
 * that preceded it matched nothing, so every panel was `display: none` in the
 * default render. A selector that hides all content fails silently, which is
 * why this is driven rather than read.
 *
 * `visible` returns which panels of a set are not `display: none`, with radio
 * N checked. Checking the radio directly rather than clicking the label keeps
 * the measurement about CSS and not about the flex `order` that moves labels.
 */
const visible = (setId, n) => page.evaluate(({ setId, n }) => {
    const set = document.getElementById(setId);
    const radios = [...set.children].filter(e => e.classList.contains('tabs-radio'));
    radios.forEach((r, i) => { r.checked = i === n - 1; });
    const panels = [...set.children].filter(e => e.classList.contains('tabs-panel'));
    return panels
        .map((p, i) => (getComputedStyle(p).display !== 'none' ? i + 1 : null))
        .filter(v => v !== null);
}, { setId, n });

const labelInk = (setId, n) => page.evaluate(({ setId, n }) => {
    const set = document.getElementById(setId);
    const radios = [...set.children].filter(e => e.classList.contains('tabs-radio'));
    radios.forEach((r, i) => { r.checked = i === n - 1; });
    const labels = [...set.children].filter(e => e.classList.contains('tabs-label'));
    return labels.map(l => {
        const c = getComputedStyle(l);
        return `${c.color}|${c.backgroundColor}|${c.borderBottomColor}`;
    });
}, { setId, n });

// Every N in the three-panel set selects exactly its own panel.
const threeExact = [];
for (let n = 1; n <= 3; n++) {
    const v = await visible('t3', n);
    threeExact.push(v.length === 1 && v[0] === n ? null : `N=${n} showed ${JSON.stringify(v)}`);
}
// The same across the whole documented bound, which is where an off-by-one in a
// hand-written ladder lives.
const boundExact = [];
for (let n = 1; n <= 12; n++) {
    const v = await visible('t13', n);
    boundExact.push(v.length === 1 && v[0] === n ? null : `N=${n} showed ${JSON.stringify(v)}`);
}
// Past the bound: the catch-all has to show everything rather than nothing.
// Blanking a 13th panel is content loss, and it is the failure the catch-all
// exists for.
const past = await visible('t13', 13);

// The selected control has to be tellable from the unselected ones.
const inks = await labelInk('t3', 2);
const selectedDistinct = inks[1] !== inks[0] && inks[1] !== inks[2];

// A nested set must not reveal a panel of the set around it. The descendant
// form of this rule did exactly that.
const nestedInner = await visible('tin', 2);
const nestedOuter = await page.evaluate(() => {
    const out = document.getElementById('tout');
    const panels = [...out.children].filter(e => e.classList.contains('tabs-panel'));
    return panels
        .map((p, i) => (getComputedStyle(p).display !== 'none' ? i + 1 : null))
        .filter(v => v !== null);
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
    // Gap 3's layer and the panel ladder it arrived with.
    ['each of 3 radios shows only its own panel', threeExact.every(x => x === null), threeExact.filter(Boolean).join('; ') || 'exact for 1..3'],
    ['the ladder is exact across its 12-panel bound', boundExact.every(x => x === null), boundExact.filter(Boolean).join('; ') || 'exact for 1..12'],
    ['a 13th panel is not blanked past the bound', past.length === 13, `${past.length} of 13 visible`],
    ['the selected control is distinguishable', selectedDistinct, inks.join('  vs  ')],
    ['a nested set shows only its own panel', nestedInner.length === 1 && nestedInner[0] === 2, JSON.stringify(nestedInner)],
    ['a nested set does not reveal its parent set\'s other panel', nestedOuter.length === 1 && nestedOuter[0] === 1, JSON.stringify(nestedOuter)],
    // The local rules the vendored layers must not repaint.
    ['an inline spoiler is still blurred', seen.inlineSpoilerFilter.includes('blur'), seen.inlineSpoilerFilter],
    ['an inline spoiler did not become a box', seen.inlineSpoilerBorder === '0px', seen.inlineSpoilerBorder],
    ['an inline spoiler kept its own padding', seen.inlineSpoilerPadding === '0px', seen.inlineSpoilerPadding],
    ['the block spoiler kept its reveal affordance', seen.blockSpoilerAffordance.includes('click to reveal'), seen.blockSpoilerAffordance],
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
