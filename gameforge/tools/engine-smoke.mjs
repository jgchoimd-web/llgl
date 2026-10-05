// Runs the engine template in headless Chromium and checks the things the app relies on:
// a game plays through title → play → over → play, a syntax error in the game script is isolated
// and shown, a hook that throws is reported exactly once. Needs Node 18+ and the `playwright`
// package with Chromium installed (npx playwright install chromium).
//
//   node gameforge/tools/engine-smoke.mjs
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// require() honours NODE_PATH, so a globally installed playwright works too.
const { chromium } = createRequire(import.meta.url)('playwright');
const here = dirname(fileURLToPath(import.meta.url));
const shell = readFileSync(join(here, '../src/main/resources/gameforge/shell.html'), 'utf8');
const sample = readFileSync(join(here, 'sample-game.js'), 'utf8');
const dir = mkdtempSync(join(tmpdir(), 'gameforge-'));

function assemble(gameJs, title = '테스트') {
  return shell.replace('__TITLE__', title).replace('/*__GAME__*/', gameJs);
}

let failures = 0;
function check(name, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
  if (!ok) failures++;
}

async function open(browser, html, name) {
  const file = join(dir, name + '.html');
  writeFileSync(file, html);
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  await page.goto('file://' + file);
  await page.waitForTimeout(250);
  return { page, errors };
}

const browser = await chromium.launch();
try {
  // 1. A healthy game.
  {
    const { page, errors } = await open(browser, assemble(sample, '공 잡기'), 'ok');
    check('boots without console errors', errors.length === 0, errors.join(' | '));
    check('starts on the title screen', (await page.evaluate(() => G.state)) === 'title');
    check('title comes from CONFIG', (await page.title()) === '공 잡기');
    check('reset ran before the title (state exists)', (await page.evaluate(() => typeof ball.vx === 'number' && ball.vx !== 0)));
    await page.tap('#game');
    await page.waitForTimeout(400);
    check('tap on title starts a round', (await page.evaluate(() => G.state)) === 'play');
    check('update advances time', (await page.evaluate(() => G.time)) > 0.2);
    const before = await page.evaluate(() => G.score);
    await page.evaluate(() => { ball.x = W / 2; ball.y = H / 2; ball.vx = 0; ball.vy = 0; });
    await page.tap('#game', { position: { x: 195, y: 422 } });
    await page.waitForTimeout(100);
    check('tapping the ball scores', (await page.evaluate(() => G.score)) === before + 1);
    await page.evaluate(() => gameOver());
    check('gameOver switches to over', (await page.evaluate(() => G.state)) === 'over');
    await page.tap('#game');
    check('an immediate tap after game over is ignored', (await page.evaluate(() => G.state)) === 'over');
    await page.waitForTimeout(700);
    await page.tap('#game');
    check('tap after the cooldown restarts', (await page.evaluate(() => G.state === 'play' && G.score === 0)));
    check('best score was kept', (await page.evaluate(() => G.best)) >= 1);
    // swipe
    await page.evaluate(() => { window.__swipes = []; window.onSwipe = (d) => __swipes.push(d); G.hooks.onSwipe = window.onSwipe; });
    await page.touchscreen.tap(50, 400); // tap first (no swipe)
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: 60, y: 400 }] });
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 200, y: 405 }] });
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await page.waitForTimeout(100);
    check('a quick horizontal drag is a right swipe', JSON.stringify(await page.evaluate(() => __swipes)) === '["right"]', JSON.stringify(await page.evaluate(() => __swipes)));
    check('still no console errors after playing', errors.length === 0, errors.join(' | '));
    await page.close();
  }

  // 2. A game script with a syntax error: the engine must survive and say so.
  {
    const broken = sample.replace('function update(dt) {', 'function update(dt) {{');
    const { page, errors } = await open(browser, assemble(broken), 'syntax');
    // Playwright hands uncaught errors over as pageerror (without the "SyntaxError:" prefix); the WebView logs them to the console.
    check('syntax error is reported', errors.some((e) => /SyntaxError|Unexpected end of input/.test(e)), errors.join(' | '));
    check('engine still booted', (await page.evaluate(() => typeof G === 'object' && G.state === 'title')));
    const err = await page.evaluate(() => document.getElementById('err').style.display + '|' + document.getElementById('err').textContent);
    check('error banner is shown with a line number', err.startsWith('block|') && /줄 \d+/.test(err), err);
    await page.close();
  }

  // 3. A hook that throws: reported once, game frozen, no flood.
  {
    const throwing = sample.replace('function update(dt) {', 'function update(dt) {\n  ball.nope.x = 1;');
    const { page, errors } = await open(browser, assemble(throwing), 'throws');
    await page.tap('#game');
    await page.waitForTimeout(500);
    check('hook error is logged exactly once', errors.filter((e) => e.includes('[게임 오류] update()')).length === 1, errors.join(' | '));
    check('hook error is shown on screen', (await page.evaluate(() => document.getElementById('err').style.display)) === 'block');
    await page.close();
  }

  // 4. No game code at all still gives a working title screen.
  {
    const { page, errors } = await open(browser, assemble(''), 'empty');
    check('empty game boots', errors.length === 0 && (await page.evaluate(() => G.state)) === 'title', errors.join(' | '));
    await page.tap('#game');
    check('empty game can start a round', (await page.evaluate(() => G.state)) === 'play');
    await page.close();
  }
} finally {
  await browser.close();
}
console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILED`);
process.exit(failures === 0 ? 0 : 1);
