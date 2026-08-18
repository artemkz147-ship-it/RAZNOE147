import { chromium } from 'playwright';
import fs from 'node:fs/promises';

await fs.mkdir('qa', { recursive: true });

const browser = await chromium.launch({
  headless: true,
  args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist']
});
const page = await browser.newPage({ viewport: { width: 1440, height: 820 }, deviceScaleFactor: 1 });

const errors = [];
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`);
});

await page.goto('http://127.0.0.1:4173', { waitUntil: 'networkidle', timeout: 60_000 });
await page.waitForFunction(
  () => document.querySelectorAll('.level-card').length > 0,
  undefined,
  { timeout: 60_000 }
);
await page.locator('.level-card').first().click();

await page.waitForFunction(
  () => {
    const hud = document.querySelector('#hud');
    const state = document.querySelector('#state')?.textContent ?? '';
    const target = document.querySelector('#target')?.textContent ?? '';
    const drop = Number.parseFloat(document.querySelector('#drop')?.textContent ?? '0');
    const indicator = document.querySelector('#targetIndicator');
    const indicatorBox = indicator?.getBoundingClientRect();
    return hud?.classList.contains('visible')
      && state.includes('ГОТОВ К ПРЫЖКУ')
      && target !== 'ЦЕЛЬ'
      && target.length > 2
      && Number.isFinite(drop)
      && drop > 0.5
      && indicator?.classList.contains('visible')
      && indicatorBox
      && indicatorBox.width > 40
      && indicatorBox.left >= 0
      && indicatorBox.right <= innerWidth
      && indicatorBox.top >= 0
      && indicatorBox.bottom <= innerHeight;
  },
  undefined,
  { timeout: 90_000 }
);
await page.waitForTimeout(1200);
await page.screenshot({ path: 'qa/drop-flow-level-01-ready.png' });

await page.keyboard.press('Space');
await page.waitForFunction(
  () => (document.querySelector('#state')?.textContent ?? '').includes('В ПОЛЁТЕ')
    && document.querySelector('#targetIndicator')?.classList.contains('visible'),
  undefined,
  { timeout: 10_000 }
);
await page.keyboard.press('Digit1');
await page.waitForTimeout(340);
await page.screenshot({ path: 'qa/drop-flow-level-01-air.png' });

await fs.writeFile('qa/browser-errors.txt', errors.join('\n'), 'utf8');
if (errors.some((line) => /pageerror/i.test(line))) {
  console.error(errors.join('\n'));
  process.exitCode = 2;
}
await browser.close();
