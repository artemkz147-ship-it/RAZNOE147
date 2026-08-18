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
await page.waitForFunction(() => document.querySelectorAll('.level-card').length > 0, { timeout: 60_000 });
await page.locator('.level-card').first().click();
await page.waitForSelector('#hud.visible', { timeout: 60_000 });
await page.waitForTimeout(4500);
await page.screenshot({ path: 'qa/drop-flow-level-01-ready.png' });

await page.keyboard.press('Space');
await page.waitForTimeout(520);
await page.keyboard.press('Digit1');
await page.waitForTimeout(420);
await page.screenshot({ path: 'qa/drop-flow-level-01-air.png' });

await fs.writeFile('qa/browser-errors.txt', errors.join('\n'), 'utf8');
if (errors.some((line) => /pageerror/i.test(line))) {
  console.error(errors.join('\n'));
  process.exitCode = 2;
}
await browser.close();
