const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

export function createDiagnosticsHud(root) {
  if (!root) return { update() {}, toggle() {}, isExpanded() { return false; } };
  const find = (name) => root.querySelector(`[data-diag="${name}"]`);
  const compact = find('compact');
  const details = find('details');
  const canvas = find('body');
  let expanded = false;
  let lastUpdate = 0;

  function toggle(force) {
    expanded = typeof force === 'boolean' ? force : !expanded;
    root.classList.toggle('expanded', expanded);
    if (details) details.hidden = !expanded;
    return expanded;
  }

  function draw(motion) {
    if (!canvas || !expanded) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    ctx.clearRect(0, 0, w, h);
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.strokeStyle = 'rgba(113,241,255,.9)';
    ctx.fillStyle = 'rgba(113,241,255,.9)';
    const conf = clamp(motion?.conf || 0, 0, 1);
    const lean = clamp(motion?.lean || 0, -1, 1);
    const la = clamp(motion?.la || 0, 0, 1.4);
    const ra = clamp(motion?.ra || 0, 0, 1.4);
    const cx = w / 2 + lean * 10;
    ctx.globalAlpha = .25 + .75 * conf;
    ctx.beginPath(); ctx.arc(cx, 15, 7, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.moveTo(cx, 22); ctx.lineTo(cx + lean * 10, 62); ctx.stroke();
    const shoulderY = 32, hipY = 62, shoulderX = cx + lean * 3;
    ctx.beginPath();
    ctx.moveTo(shoulderX, shoulderY); ctx.lineTo(shoulderX - 18, shoulderY - 22 * la);
    ctx.moveTo(shoulderX, shoulderY); ctx.lineTo(shoulderX + 18, shoulderY - 22 * ra);
    ctx.moveTo(cx + lean * 10, hipY); ctx.lineTo(cx - 13, 92);
    ctx.moveTo(cx + lean * 10, hipY); ctx.lineTo(cx + 13, 92);
    ctx.stroke();
    ctx.globalAlpha = 1;
  }

  function update(data = {}) {
    const now = performance.now();
    if (now - lastUpdate < 240) return;
    lastUpdate = now;
    const motion = data.motion || {};
    const game = Math.round(data.gameFps || 0);
    const pose = Math.round(motion.poseFps || data.poseFps || 0);
    const latency = Math.round(motion.latency || data.latency || 0);
    if (compact) compact.textContent = `${game} FPS · POSE ${pose} · ${latency}ms`;
    const set = (key, value) => { const el = find(key); if (el) el.textContent = String(value); };
    set('backend', motion.backend || '—');
    set('quality', data.quality || '—');
    set('zone', data.zone || '—');
    set('conf', `${Math.round((motion.conf || 0) * 100)}%`);
    set('lean', (motion.lean || 0).toFixed(2));
    set('knee', (motion.knee || 0).toFixed(2));
    set('jump', (motion.jump || 0).toFixed(2));
    set('action', data.lastAction || '—');
    draw(motion);
  }

  if (compact) compact.addEventListener('click', () => toggle());
  return { update, toggle, isExpanded: () => expanded };
}
