export const ZONE_NAMES = Object.freeze(['neon-city', 'industrial', 'tunnel', 'rooftop', 'megacity']);
const SPAN = 220;
const TRANSITION = 48;
export const ZONE_STYLES = Object.freeze({
  'neon-city': { bg: 0x07101d, fog: 0x07101d, fogDensity: .024, key: 0xb9dcff, neon: 0x22d3ee, road: 0x131a29 },
  industrial: { bg: 0x120f12, fog: 0x181114, fogDensity: .029, key: 0xffd6a1, neon: 0xff7a32, road: 0x242025 },
  tunnel: { bg: 0x04060b, fog: 0x080b12, fogDensity: .038, key: 0xb3c8ff, neon: 0x758cff, road: 0x11151d },
  rooftop: { bg: 0x09142b, fog: 0x10294a, fogDensity: .017, key: 0xddeaff, neon: 0xff4fb8, road: 0x151d2d },
  megacity: { bg: 0x10051e, fog: 0x190b29, fogDensity: .025, key: 0xffd4ff, neon: 0xb44cff, road: 0x171126 },
});
export function zoneForDistance(distance = 0) {
  const d = Math.max(0, Number(distance) || 0);
  const cycle = SPAN * ZONE_NAMES.length;
  const local = d % cycle;
  const index = Math.floor(local / SPAN);
  const within = local - index * SPAN;
  const name = ZONE_NAMES[index];
  const next = ZONE_NAMES[(index + 1) % ZONE_NAMES.length];
  const progress = Math.max(0, Math.min(1, (within - (SPAN - TRANSITION)) / TRANSITION));
  return { name, next, progress, index, within, style: ZONE_STYLES[name], nextStyle: ZONE_STYLES[next] };
}
