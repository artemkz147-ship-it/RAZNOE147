const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const lerp = (a, b, t) => a + (b - a) * t;
const INTERPOLATED_KEYS = [
  'cx', 'cy', 'vx', 'vy', 'lean', 'la', 'ra', 'le', 're',
  'lwv', 'rwv', 'lk', 'rk', 'hip', 'knee', 'jump', 'conf',
];

function interpolate(previous, current, amount) {
  const result = { ...current };
  for (const key of INTERPOLATED_KEYS) {
    const a = previous[key];
    const b = current[key];
    if (Number.isFinite(a) && Number.isFinite(b)) result[key] = lerp(a, b, amount);
  }
  if (Number.isFinite(previous.t) && Number.isFinite(current.t)) {
    result.t = lerp(previous.t, current.t, amount);
  }
  return result;
}

function sanitize(state) {
  if (!state) return null;
  const out = { ...state };
  if (Number.isFinite(out.cx)) out.cx = clamp(out.cx, -2.5, 2.5);
  if (Number.isFinite(out.cy)) out.cy = clamp(out.cy, -2, 2);
  if (Number.isFinite(out.lean)) out.lean = clamp(out.lean, -2, 2);
  if (Number.isFinite(out.conf)) out.conf = clamp(out.conf, 0, 1);
  return out;
}

export function createMotionInterpolator() {
  let previous = null;
  let current = null;

  return {
    push(snapshot, receivedAtMs = performance.now()) {
      if (!snapshot || typeof snapshot !== 'object') return;
      const entry = { state: { ...snapshot }, receivedAtMs };
      if (current && receivedAtMs < current.receivedAtMs) return;
      previous = current;
      current = entry;
    },

    sample(nowMs = performance.now()) {
      if (!current) return null;
      if (previous && nowMs <= current.receivedAtMs) {
        const span = Math.max(1, current.receivedAtMs - previous.receivedAtMs);
        const amount = clamp((nowMs - previous.receivedAtMs) / span, 0, 1);
        return sanitize(interpolate(previous.state, current.state, amount));
      }

      const ageMs = Math.max(0, nowMs - current.receivedAtMs);
      const predictionMs = Math.min(45, ageMs);
      const predictionSeconds = predictionMs / 1000;
      const out = { ...current.state };
      const vx = Number.isFinite(out.vx) ? out.vx : 0;
      const vy = Number.isFinite(out.vy) ? out.vy : 0;
      if (Number.isFinite(out.cx)) out.cx += vx * predictionSeconds;
      if (Number.isFinite(out.cy)) out.cy += vy * predictionSeconds;
      if (Number.isFinite(out.conf) && ageMs > 120) {
        out.conf *= Math.exp(-(ageMs - 120) / 100);
      }
      return sanitize(out);
    },

    reset() {
      previous = null;
      current = null;
    },
  };
}
