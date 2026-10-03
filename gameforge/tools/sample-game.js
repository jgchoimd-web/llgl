// A sample game section in the shape the model is asked to produce. Used by engine-smoke.mjs.
const CONFIG = { title: '공 잡기', hint: '튀어 다니는 공을 탭하세요. 세 번 놓치면 끝!' };

let ball = { x: 0, y: 0, r: 28, vx: 0, vy: 0 };
let misses = 0;
let flash = 0;

function reset() {
  ball.x = W / 2;
  ball.y = H / 2;
  ball.vx = rand(120, 220) * (Math.random() < 0.5 ? -1 : 1);
  ball.vy = rand(120, 220);
  misses = 0;
  flash = 0;
}

function update(dt) {
  ball.x += ball.vx * dt;
  ball.y += ball.vy * dt;
  if (ball.x < ball.r || ball.x > W - ball.r) { ball.vx = -ball.vx; ball.x = clamp(ball.x, ball.r, W - ball.r); }
  if (ball.y < ball.r + 40 || ball.y > H - ball.r) { ball.vy = -ball.vy; ball.y = clamp(ball.y, ball.r + 40, H - ball.r); }
  flash = Math.max(0, flash - dt * 3);
}

function draw() {
  for (let i = 0; i < 3; i++) {
    circle(W - 24 - i * 28, H - 30, 8, i < misses ? '#ff5a5a' : '#2a3140');
  }
  circle(ball.x, ball.y, ball.r + flash * 10, flash > 0 ? '#ffe97a' : '#6fe3c4');
  text('공을 탭!', W / 2, H - 30, 14, '#9aa3b2');
}

function onTap(x, y) {
  if (dist(x, y, ball.x, ball.y) < ball.r + 14) {
    addScore(1);
    flash = 1;
    ball.vx *= 1.08;
    ball.vy *= 1.08;
    vibrate(20);
  } else {
    misses++;
    if (misses >= 3) gameOver();
  }
}
