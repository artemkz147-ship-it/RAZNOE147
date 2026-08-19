import { chromium } from 'playwright';
import fs from 'node:fs/promises';

const ASSET_ROOT = '/assets3d_df6_20260818/';
const BUILD_ID = 'DF6-20260818-1903';

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
  (buildId) => document.querySelectorAll('.level-card').length > 0
    && document.querySelector('#buildStamp')?.getAttribute('data-build') === buildId,
  BUILD_ID,
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
await page.waitForTimeout(900);

const resources = await page.evaluate(() => performance.getEntriesByType('resource').map((entry) => entry.name));
await fs.writeFile('qa/resource-urls.txt', resources.join('\n'), 'utf8');

const staleAssets = resources.filter((url) => {
  try {
    return new URL(url).pathname.includes('/assets3d/');
  } catch {
    return false;
  }
});
if (staleAssets.length) throw new Error(`Stale unversioned assets were requested:\n${staleAssets.join('\n')}`);

const versionedResources = resources.filter((url) => {
  try {
    return new URL(url).pathname.includes(ASSET_ROOT);
  } catch {
    return false;
  }
});
const required = [
  'parkour_performer.glb',
  'parkour_locomotion.glb',
  'parkour_tricks.glb',
  'rooftop_sunset_1k.hdr',
  'landing-target.svg',
  'kaykit_city_',
  'citybits_texture.png',
  'kaykit_road_'
];
for (const token of required) {
  if (!versionedResources.some((url) => url.includes(token))) throw new Error(`Expected DF6 asset was not requested: ${token}`);
}

await page.screenshot({ path: 'qa/drop-flow-df6-level-01-ready.png' });

const canvas = page.locator('#game canvas');
const box = await canvas.boundingBox();
if (!box) throw new Error('Gameplay canvas not found');
const startX = box.x + box.width * 0.62;
const startY = box.y + box.height * 0.46;
await page.mouse.move(startX, startY);
await page.mouse.down({ button: 'left' });
await page.mouse.move(startX - 170, startY + 62, { steps: 12 });
await page.mouse.up({ button: 'left' });
await page.mouse.wheel(0, -220);

await page.keyboard.down('KeyW');
await page.waitForTimeout(800);
await page.keyboard.up('KeyW');
await page.waitForTimeout(300);
await page.screenshot({ path: 'qa/drop-flow-df6-level-01-grounded-walk.png' });
if (!(await page.locator('#state').textContent())?.includes('ГОТОВ К ПРЫЖКУ')) {
  throw new Error('Grounded walking left the roof or changed gameplay state before jump');
}

await page.keyboard.down('KeyS');
await page.waitForTimeout(800);
await page.keyboard.up('KeyS');
await page.waitForTimeout(350);
if (!(await page.locator('#state').textContent())?.includes('ГОТОВ К ПРЫЖКУ')) {
  throw new Error('Player did not remain grounded after walking back from the edge');
}

await page.keyboard.press('Space');
await page.waitForFunction(
  () => (document.querySelector('#state')?.textContent ?? '').includes('В ПОЛЁТЕ')
    && document.querySelector('#targetIndicator')?.classList.contains('visible'),
  undefined,
  { timeout: 10_000 }
);
await page.keyboard.press('Digit1');
await page.waitForFunction(
  () => /NinjaJump|flip|somersault/i.test(document.documentElement.dataset.dropTrickClip ?? ''),
  undefined,
  { timeout: 4_000 }
);
await page.waitForTimeout(330);
await page.screenshot({ path: 'qa/drop-flow-df6-level-01-air.png' });

const trajectory = [];
let landed = false;
for (let i = 0; i < 75; i += 1) {
  const sample = await page.evaluate(() => {
    let physics = null;
    try {
      physics = JSON.parse(document.documentElement.dataset.dropPhysics ?? 'null');
    } catch {
      physics = null;
    }
    return {
      time: performance.now(),
      state: document.querySelector('#state')?.textContent ?? '',
      score: Number.parseInt(document.querySelector('#score')?.textContent ?? '0', 10),
      drop: Number.parseFloat(document.querySelector('#drop')?.textContent ?? '0'),
      trickClip: document.documentElement.dataset.dropTrickClip ?? '',
      physics
    };
  });
  trajectory.push(sample);
  if (sample.score > 0) {
    landed = true;
    break;
  }
  await page.waitForTimeout(80);
}
await fs.writeFile('qa/physics-trajectory.json', JSON.stringify(trajectory, null, 2), 'utf8');

if (!landed) {
  await page.screenshot({ path: 'qa/drop-flow-df6-level-01-no-contact.png' });
  const last = trajectory.at(-1);
  throw new Error(`Physical landing did not score. Last sample: ${JSON.stringify(last)}`);
}

await page.screenshot({ path: 'qa/drop-flow-df6-level-01-contact.png' });
await page.waitForFunction(
  () => document.querySelector('#finish')?.classList.contains('visible'),
  undefined,
  { timeout: 5_000 }
);

await fs.writeFile('qa/browser-errors.txt', errors.join('\n'), 'utf8');
if (errors.some((line) => /pageerror/i.test(line))) {
  console.error(errors.join('\n'));
  process.exitCode = 2;
}
await browser.close();
