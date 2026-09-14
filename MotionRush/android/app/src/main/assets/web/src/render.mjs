export function entityScale(z) {
  const d = Math.max(0.5, z);
  return 1 / (1 + d * 0.085);
}

export function projectPoint(lane, z, width, height) {
  const t = entityScale(z);
  const horizon = height * 0.29;
  const bottom = height * 0.93;
  const roadHalf = width * (0.07 + 0.40 * t);
  return {
    x: width / 2 + lane * roadHalf * 0.57,
    y: horizon + (bottom - horizon) * t,
    scale: t,
    roadHalf,
  };
}

function roundedRect(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, rr);
}

function drawCity(ctx, w, h, time) {
  const sky = ctx.createLinearGradient(0, 0, 0, h * 0.68);
  sky.addColorStop(0, '#071229');
  sky.addColorStop(.45, '#163b70');
  sky.addColorStop(1, '#ff8b6b');
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  const sunY = h * .20;
  const glow = ctx.createRadialGradient(w * .72, sunY, 0, w * .72, sunY, w * .23);
  glow.addColorStop(0, 'rgba(255,235,158,.95)');
  glow.addColorStop(.25, 'rgba(255,144,111,.32)');
  glow.addColorStop(1, 'rgba(255,144,111,0)');
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, w, h * .55);

  const baseY = h * .33;
  for (let i = 0; i < 18; i++) {
    const bw = w * (.04 + ((i * 37) % 8) / 150);
    const bh = h * (.06 + ((i * 53) % 16) / 100);
    const x = (i / 17) * w - bw / 2;
    ctx.fillStyle = i % 3 === 0 ? '#0f203d' : '#10294c';
    ctx.fillRect(x, baseY - bh, bw, bh);
    ctx.fillStyle = 'rgba(255,211,96,.55)';
    const rows = Math.max(2, Math.floor(bh / 28));
    for (let r = 1; r < rows; r++) {
      const yy = baseY - bh + r * 22;
      if ((i + r + Math.floor(time)) % 3 !== 0) ctx.fillRect(x + bw * .22, yy, 3, 5);
      if ((i * r) % 2 === 0) ctx.fillRect(x + bw * .62, yy, 3, 5);
    }
  }
}

function drawRoad(ctx, w, h, distance) {
  const horizonY = h * .29;
  const bottomY = h * .98;
  const nearHalf = w * .47;
  const farHalf = w * .07;

  ctx.beginPath();
  ctx.moveTo(w / 2 - farHalf, horizonY);
  ctx.lineTo(w / 2 + farHalf, horizonY);
  ctx.lineTo(w / 2 + nearHalf, bottomY);
  ctx.lineTo(w / 2 - nearHalf, bottomY);
  ctx.closePath();
  const road = ctx.createLinearGradient(0, horizonY, 0, bottomY);
  road.addColorStop(0, '#202a42');
  road.addColorStop(1, '#0d1423');
  ctx.fillStyle = road;
  ctx.fill();

  ctx.strokeStyle = 'rgba(48,232,255,.75)';
  ctx.lineWidth = Math.max(2, w * .006);
  ctx.beginPath();
  ctx.moveTo(w / 2 - farHalf, horizonY);
  ctx.lineTo(w / 2 - nearHalf, bottomY);
  ctx.moveTo(w / 2 + farHalf, horizonY);
  ctx.lineTo(w / 2 + nearHalf, bottomY);
  ctx.stroke();

  for (const boundary of [-.5, .5]) {
    for (let i = 0; i < 13; i++) {
      const z = ((i * 4 + distance * .55) % 52) + 1.5;
      const p = projectPoint(boundary, z, w, h);
      const scale = p.scale;
      ctx.strokeStyle = `rgba(255,255,255,${.18 + .48 * scale})`;
      ctx.lineWidth = Math.max(1, w * .009 * scale);
      ctx.beginPath();
      ctx.moveTo(p.x, p.y - h * .017 * scale);
      ctx.lineTo(p.x, p.y + h * .017 * scale);
      ctx.stroke();
    }
  }
}

function drawShadow(ctx, x, y, rx, ry, alpha = .3) {
  ctx.save();
  ctx.fillStyle = `rgba(0,0,0,${alpha})`;
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

function drawEntity(ctx, entity, w, h, time) {
  const p = projectPoint(entity.lane, entity.z, w, h);
  const s = p.scale;
  if (s < .07 || entity.z < 0) return;
  const base = Math.max(12, w * .09 * s);

  if (entity.type === 'obstacle') {
    drawShadow(ctx, p.x, p.y + base * .22, base * .55, base * .15, .25);
    if (entity.obstacle === 'high') {
      const width = base * 1.5;
      const barY = p.y - base * .85;
      ctx.fillStyle = '#ff3b5c';
      roundedRect(ctx, p.x - width / 2, barY, width, base * .25, base * .08);
      ctx.fill();
      ctx.fillStyle = '#ffe04b';
      for (let i = 0; i < 4; i++) ctx.fillRect(p.x - width / 2 + i * width / 4, barY, width / 8, base * .25);
      ctx.fillStyle = '#6c7a95';
      ctx.fillRect(p.x - width * .45, barY + base * .2, base * .12, base * .9);
      ctx.fillRect(p.x + width * .33, barY + base * .2, base * .12, base * .9);
    } else {
      ctx.fillStyle = entity.obstacle === 'solid' ? '#6f7b91' : '#ff8d26';
      roundedRect(ctx, p.x - base * .62, p.y - base * .72, base * 1.24, base * .72, base * .12);
      ctx.fill();
      ctx.fillStyle = '#ffe247';
      ctx.fillRect(p.x - base * .5, p.y - base * .54, base, base * .12);
    }
    return;
  }

  if (entity.type === 'enemy') {
    drawShadow(ctx, p.x, p.y + base * .2, base * .42, base * .13, .28);
    ctx.save();
    ctx.translate(p.x, p.y - base * .42);
    ctx.rotate(Math.sin(time * 5 + entity.z) * .08);
    ctx.fillStyle = '#702cff';
    roundedRect(ctx, -base * .36, -base * .52, base * .72, base * .86, base * .2);
    ctx.fill();
    ctx.fillStyle = '#f7fbff';
    ctx.fillRect(-base * .18, -base * .28, base * .12, base * .12);
    ctx.fillRect(base * .06, -base * .28, base * .12, base * .12);
    ctx.restore();
    return;
  }

  const air = entity.type === 'airPickup';
  const yy = p.y - (air ? base * 1.7 : base * .48) + Math.sin(time * 5 + entity.z) * base * .08;
  const radius = base * (air ? .36 : .28);
  const glow = ctx.createRadialGradient(p.x, yy, 0, p.x, yy, radius * 2.1);
  glow.addColorStop(0, 'rgba(98,245,255,.95)');
  glow.addColorStop(.45, 'rgba(76,134,255,.35)');
  glow.addColorStop(1, 'rgba(76,134,255,0)');
  ctx.fillStyle = glow;
  ctx.beginPath();
  ctx.arc(p.x, yy, radius * 2.1, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = entity.pickup === 'shield' ? '#54f0ff' : entity.pickup === 'boost' ? '#ffca3a' : entity.pickup === 'magnet' ? '#ff5ad7' : '#ffd64a';
  ctx.beginPath();
  ctx.arc(p.x, yy, radius, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#091122';
  ctx.font = `700 ${Math.max(9, radius * .9)}px system-ui`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const icon = entity.pickup === 'shield' ? 'S' : entity.pickup === 'boost' ? '⚡' : entity.pickup === 'magnet' ? 'M' : '●';
  ctx.fillText(icon, p.x, yy + 1);
  if (air) {
    ctx.font = `700 ${Math.max(8, radius * .48)}px system-ui`;
    ctx.fillStyle = 'rgba(255,255,255,.9)';
    ctx.fillText(entity.hand === 'both' ? '2 HANDS' : entity.hand?.toUpperCase() || 'HAND', p.x, yy + radius * 1.7);
  }
}

function drawPlayer(ctx, state, w, h, time) {
  const p = state.player;
  const screen = projectPoint(p.lane, .35, w, h);
  const jumpPx = p.y * h * .085;
  const crouching = p.crouchRemaining > 0;
  const baseY = h * .89 - jumpPx;
  const size = w * .15;
  drawShadow(ctx, screen.x, h * .91, size * .42 * (1 - Math.min(.5, p.y * .12)), size * .12, .38);

  ctx.save();
  ctx.translate(screen.x, baseY);
  const bob = p.y === 0 ? Math.sin(time * 12) * size * .025 : 0;
  ctx.translate(0, bob);
  if (crouching) ctx.scale(1.08, .64);

  ctx.strokeStyle = '#182342';
  ctx.lineWidth = size * .14;
  ctx.lineCap = 'round';
  const stride = p.y === 0 ? Math.sin(time * 13) * size * .12 : 0;
  ctx.beginPath();
  ctx.moveTo(-size * .14, -size * .08);
  ctx.lineTo(-size * .22 + stride, size * .42);
  ctx.moveTo(size * .14, -size * .08);
  ctx.lineTo(size * .22 - stride, size * .42);
  ctx.stroke();

  const torso = ctx.createLinearGradient(-size * .4, 0, size * .4, 0);
  torso.addColorStop(0, '#00c7ff');
  torso.addColorStop(1, '#275dff');
  ctx.fillStyle = torso;
  roundedRect(ctx, -size * .32, -size * .66, size * .64, size * .7, size * .20);
  ctx.fill();
  ctx.fillStyle = '#d7f9ff';
  ctx.fillRect(-size * .08, -size * .6, size * .16, size * .52);

  ctx.strokeStyle = '#ffbf97';
  ctx.lineWidth = size * .12;
  const leftRaised = p.leftHandRemaining > 0;
  const rightRaised = p.rightHandRemaining > 0;
  const leftPunch = p.punchRemaining > 0 && p.punchSide === 'left';
  const rightPunch = p.punchRemaining > 0 && p.punchSide === 'right';
  ctx.beginPath();
  ctx.moveTo(-size * .26, -size * .5);
  ctx.lineTo(leftRaised ? -size * .38 : leftPunch ? -size * .78 : -size * .48, leftRaised ? -size * 1.1 : leftPunch ? -size * .48 : -size * .12);
  ctx.moveTo(size * .26, -size * .5);
  ctx.lineTo(rightRaised ? size * .38 : rightPunch ? size * .78 : size * .48, rightRaised ? -size * 1.1 : rightPunch ? -size * .48 : -size * .12);
  ctx.stroke();

  ctx.fillStyle = '#ffbf97';
  ctx.beginPath();
  ctx.arc(0, -size * .92, size * .23, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#171c35';
  ctx.beginPath();
  ctx.arc(-size * .04, -size * .98, size * .23, Math.PI, Math.PI * 2.05);
  ctx.fill();
  ctx.restore();
}

export function createRenderer(canvas) {
  const ctx = canvas.getContext('2d', { alpha: false });

  function resize() {
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.min(2, globalThis.devicePixelRatio || 1);
    const cw = Math.max(1, Math.floor(rect.width * dpr));
    const ch = Math.max(1, Math.floor(rect.height * dpr));
    if (canvas.width !== cw || canvas.height !== ch) {
      canvas.width = cw;
      canvas.height = ch;
    }
  }

  function render(state, nowSeconds = 0) {
    resize();
    const w = canvas.width;
    const h = canvas.height;
    ctx.save();
    drawCity(ctx, w, h, nowSeconds);
    drawRoad(ctx, w, h, state.distance);
    [...state.entities].sort((a, b) => b.z - a.z).forEach((entity) => drawEntity(ctx, entity, w, h, nowSeconds));
    drawPlayer(ctx, state, w, h, nowSeconds);
    ctx.restore();
  }

  return { render, resize };
}
