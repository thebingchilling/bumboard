#!/usr/bin/env node
// Draws the preview strip Lychee shows for a font before it is downloaded (Settings > Fonts):
// a few sample characters in the font, on a transparent background, as a PNG. Uses Chromium
// (Playwright), which draws every kind of colour emoji font.
//
//   node render_preview.js OUT.png FONT.ttf [emoji|text]
//
// Previews live in latinime/lychee/assets/lychee/previews/<pack id>.png.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const SAMPLES = {
  // faces, an animal, food, a party, a heart and a newer emoji
  emoji: ['😀', '🥰', '🐱', '🍜', '🎉', '❤️', '🫠'],
  text: ['永', '你好', 'かな', '한글', 'Aa'],
};

(async () => {
  const [out, font, kind = 'emoji'] = process.argv.slice(2);
  const sample = SAMPLES[kind];
  const data = fs.readFileSync(font).toString('base64');
  const cell = 48;
  const html = `<!doctype html><style>
    @font-face { font-family: preview; src: url(data:font/ttf;base64,${data}); }
    html, body { margin: 0; background: transparent; }
    #strip { display: inline-flex; padding: 0 4px; }
    #strip span { font-family: preview; font-size: ${cell * 0.75}px; line-height: ${cell}px;
      width: ${cell * 1.2}px; height: ${cell}px; display: flex; justify-content: center; }
  </style><div id="strip">${sample.map((s) => `<span>${s}</span>`).join('')}</div>`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  await page.setContent(html);
  await page.evaluate(() => document.fonts.ready);
  await page.locator('#strip').screenshot({ path: path.resolve(out), omitBackground: true });
  await browser.close();
})();
