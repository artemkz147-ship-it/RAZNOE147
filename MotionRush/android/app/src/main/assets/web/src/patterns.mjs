const LANES = [-1, 0, 1];
export const PATTERN_FAMILIES = Object.freeze([
  'jump', 'slide', 'side-choice', 'alternating', 'coin-arc',
  'hand-catch', 'jump-hand', 'crouch-punch', 'enemy-obstacle', 'risk-reward',
]);

function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

const tierFamilies = [
  ['jump', 'slide', 'side-choice', 'coin-arc'],
  ['jump', 'slide', 'side-choice', 'coin-arc', 'alternating', 'hand-catch'],
  ['jump', 'slide', 'side-choice', 'coin-arc', 'alternating', 'hand-catch', 'jump-hand'],
  ['jump', 'slide', 'side-choice', 'coin-arc', 'alternating', 'hand-catch', 'jump-hand', 'crouch-punch', 'enemy-obstacle'],
  PATTERN_FAMILIES,
];

function idFactory() {
  let n = 0;
  return (prefix) => `${prefix}-${++n}`;
}

function normalizeStart(startZ) { return Number.isFinite(startZ) ? startZ : 40; }

export function createPatternDirector(seed = 1) {
  const random = mulberry32(seed || 1);
  const nextId = idFactory();

  function lane(except = null) {
    const choices = except == null ? LANES : LANES.filter((v) => v !== except);
    return choices[Math.floor(random() * choices.length)];
  }

  function nextPattern(difficulty = 0) {
    const tier = Math.max(0, Math.min(4, Math.floor(difficulty)));
    const unlocked = tierFamilies[tier];
    return { family: unlocked[Math.floor(random() * unlocked.length)], difficulty: tier };
  }

  function materialize(pattern, startZ = 40) {
    const family = PATTERN_FAMILIES.includes(pattern?.family) ? pattern.family : 'jump';
    const z0 = normalizeStart(startZ);
    const a = lane();
    const b = lane(a);
    const c = LANES.find((v) => v !== a && v !== b) ?? 0;
    const e = [];
    const push = (entity) => e.push({ id: entity.id || nextId(family), ...entity });

    switch (family) {
      case 'jump':
        push({ type: 'pickup', pickup: 'coin', lane: b, z: z0 });
        push({ type: 'obstacle', obstacle: 'low', lane: a, z: z0 + 4, mandatoryAction: 'JUMP' });
        push({ type: 'pickup', pickup: 'coin', lane: a, z: z0 + 8 });
        break;
      case 'slide':
        push({ type: 'pickup', pickup: 'coin', lane: c, z: z0 });
        push({ type: 'obstacle', obstacle: 'high', lane: a, z: z0 + 4, mandatoryAction: 'CROUCH' });
        push({ type: 'pickup', pickup: 'coin', lane: a, z: z0 + 8 });
        break;
      case 'side-choice':
        push({ type: 'obstacle', obstacle: 'solid', lane: a, z: z0 + 2 });
        push({ type: 'pickup', pickup: 'coin', lane: b, z: z0 + 4 });
        push({ type: 'pickup', pickup: 'coin', lane: c, z: z0 + 4.4 });
        break;
      case 'alternating':
        push({ type: 'pickup', pickup: 'coin', lane: -1, z: z0 });
        push({ type: 'obstacle', obstacle: 'solid', lane: 0, z: z0 + 4 });
        push({ type: 'pickup', pickup: 'coin', lane: 1, z: z0 + 8 });
        push({ type: 'obstacle', obstacle: 'solid', lane: 0, z: z0 + 12 });
        push({ type: 'pickup', pickup: 'coin', lane: -1, z: z0 + 16 });
        break;
      case 'coin-arc':
        for (let i = 0; i < 7; i++) push({ type: 'pickup', pickup: 'coin', lane: [a, b, c, b, a, b, c][i], z: z0 + i * 2.2 });
        break;
      case 'hand-catch':
        push({ type: 'pickup', pickup: 'coin', lane: b, z: z0 });
        push({ type: 'airPickup', pickup: 'shield', hand: random() < .5 ? 'left' : 'right', lane: a, z: z0 + 4.5, mandatoryAction: 'RAISE_HAND' });
        push({ type: 'pickup', pickup: 'coin', lane: a, z: z0 + 8.5 });
        break;
      case 'jump-hand':
        push({ type: 'obstacle', obstacle: 'low', lane: a, z: z0, mandatoryAction: 'JUMP' });
        push({ type: 'airPickup', pickup: 'boost', hand: 'both', lane: a, z: z0 + 7, mandatoryAction: 'RAISE_BOTH' });
        push({ type: 'pickup', pickup: 'coin', lane: b, z: z0 + 11 });
        break;
      case 'crouch-punch':
        push({ type: 'obstacle', obstacle: 'high', lane: a, z: z0, mandatoryAction: 'CROUCH' });
        push({ type: 'enemy', variant: 'guard', side: random() < .5 ? 'left' : 'right', lane: a, z: z0 + 7, mandatoryAction: 'PUNCH' });
        push({ type: 'pickup', pickup: 'coin', lane: b, z: z0 + 11 });
        break;
      case 'enemy-obstacle':
        push({ type: 'enemy', variant: random() < .5 ? 'sweeper' : 'guard', side: random() < .5 ? 'left' : 'right', lane: a, sweepTarget: b, z: z0 });
        push({ type: 'obstacle', obstacle: random() < .5 ? 'low' : 'high', lane: b, z: z0 + 7 });
        push({ type: 'pickup', pickup: 'coin', lane: c, z: z0 + 11 });
        break;
      case 'risk-reward':
        push({ type: 'obstacle', obstacle: 'solid', lane: b, z: z0 });
        push({ type: 'pickup', pickup: 'coin', lane: a, z: z0 + 1.5 });
        push({ type: 'enemy', variant: 'charger', side: 'left', lane: a, z: z0 + 7 });
        push({ type: 'airPickup', pickup: 'magnet', hand: 'right', lane: c, z: z0 + 12 });
        break;
    }

    e.sort((x, y) => x.z - y.z);
    return e;
  }

  function hasSurvivablePath(entities) {
    const groups = new Map();
    for (const entity of entities) {
      if (!['obstacle', 'enemy'].includes(entity.type)) continue;
      const key = Math.round(entity.z * 2) / 2;
      const set = groups.get(key) || new Set();
      if (entity.obstacle === 'solid' || entity.type === 'enemy') set.add(entity.lane);
      groups.set(key, set);
    }
    return [...groups.values()].every((blocked) => blocked.size < 3);
  }

  return { nextPattern, materialize, hasSurvivablePath };
}
