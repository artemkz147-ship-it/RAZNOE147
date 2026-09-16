const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
function dec(value, dt) { return Math.max(0, value - dt); }
function multiplierFor(streak) { return Math.min(5, 1 + Math.floor(streak / 3)); }
function success(state, baseScore = 0) {
  state.streak += 1;
  state.combo = state.streak;
  state.comboMultiplier = multiplierFor(state.streak);
  state.lastSuccessAt = state.elapsed;
  state.score += baseScore * state.comboMultiplier;
}
function resetCombo(state) { state.streak = 0; state.combo = 0; state.comboMultiplier = 1; }

export function createGameState(seed = 1) {
  return {
    seed, elapsed: 0, distance: 0, speed: 12, score: 0, coins: 0, health: 3, gameOver: false,
    combo: 0, comboMultiplier: 1, streak: 0, nearMisses: 0, lastSuccessAt: 0, currentZone: 'neon-city',
    player: { lane: 0, y: 0, vy: 0, crouchRemaining: 0, punchRemaining: 0, punchSide: null, leftHandRemaining: 0, rightHandRemaining: 0 },
    effects: { shieldRemaining: 0, boostRemaining: 0, magnetRemaining: 0 },
    entities: [], events: [],
  };
}
export function spawnEntity(state, entity) { state.entities.push({ consumed: false, nearMissAwarded: false, ...entity }); return entity; }
export function applyPattern(state, entities) { for (const entity of entities) spawnEntity(state, entity); return state; }
export function applyAction(state, action) {
  if (state.gameOver) return; const p = state.player;
  switch (action) {
    case 'MOVE_LEFT': p.lane = clamp(p.lane - 1, -1, 1); break;
    case 'MOVE_RIGHT': p.lane = clamp(p.lane + 1, -1, 1); break;
    case 'JUMP': if (p.y === 0 && p.crouchRemaining === 0) p.vy = 8.2; break;
    case 'CROUCH': if (p.y === 0) p.crouchRemaining = Math.max(p.crouchRemaining, .68); break;
    case 'PUNCH_LEFT': p.punchSide = 'left'; p.punchRemaining = .34; break;
    case 'PUNCH_RIGHT': p.punchSide = 'right'; p.punchRemaining = .34; break;
    case 'RAISE_LEFT': p.leftHandRemaining = Math.max(p.leftHandRemaining, .42); break;
    case 'RAISE_RIGHT': p.rightHandRemaining = Math.max(p.rightHandRemaining, .42); break;
    case 'RAISE_BOTH': p.leftHandRemaining = Math.max(p.leftHandRemaining, .48); p.rightHandRemaining = Math.max(p.rightHandRemaining, .48); break;
  }
}
function raisedFor(player, hand) { if (hand === 'left') return player.leftHandRemaining > 0; if (hand === 'right') return player.rightHandRemaining > 0; return player.leftHandRemaining > 0 && player.rightHandRemaining > 0; }
function awardPickup(state, entity) {
  success(state, entity.pickup === 'coin' ? 10 : 25);
  if (entity.pickup === 'coin') state.coins += 1;
  else if (entity.pickup === 'shield') state.effects.shieldRemaining = 8;
  else if (entity.pickup === 'boost') state.effects.boostRemaining = 5;
  else if (entity.pickup === 'magnet') state.effects.magnetRemaining = 7;
  state.events.push({ type: 'pickup', pickup: entity.pickup });
}
function damage(state) {
  if (state.effects.shieldRemaining > 0) { state.effects.shieldRemaining = 0; state.events.push({ type: 'shield-break' }); return; }
  state.health = Math.max(0, state.health - 1); resetCombo(state); state.events.push({ type: 'hit' }); if (state.health === 0) state.gameOver = true;
}
function resolveEntity(state, entity) {
  const p = state.player; if (entity.lane !== p.lane) return false; if (entity.z > 1.05 || entity.z < .05) return false;
  if (entity.type === 'pickup') { awardPickup(state, entity); return true; }
  if (entity.type === 'airPickup') { if (!raisedFor(p, entity.hand || 'both')) return false; awardPickup(state, entity); return true; }
  if (entity.type === 'enemy') {
    if (p.punchRemaining > 0 && (!entity.side || entity.side === p.punchSide)) { success(state, 50); state.events.push({ type: 'enemy-hit', side: p.punchSide }); return true; }
    damage(state); return true;
  }
  if (entity.type === 'obstacle') {
    let avoided = false; if (entity.obstacle === 'low') avoided = p.y > .65; else if (entity.obstacle === 'high') avoided = p.crouchRemaining > 0;
    if (!avoided) damage(state); else { success(state, 5); state.events.push({ type: 'avoid', obstacle: entity.obstacle }); }
    return true;
  }
  return false;
}
function updateEnemy(entity, effectiveSpeed, dt) {
  if (entity.variant === 'sweeper' && !entity.swept && Number.isFinite(entity.sweepTarget)) { entity.lane = clamp(entity.sweepTarget, -1, 1); entity.swept = true; }
  const extra = entity.variant === 'charger' ? effectiveSpeed * .55 : 0;
  entity.z -= (effectiveSpeed + extra) * dt;
}
export function stepGame(state, dt) {
  if (!Number.isFinite(dt) || dt <= 0) return state; dt = Math.min(dt, .1); state.events.length = 0; if (state.gameOver) return state;
  state.elapsed += dt; state.speed = 12 + Math.min(13, state.elapsed * .14); const effectiveSpeed = state.effects.boostRemaining > 0 ? state.speed * 1.3 : state.speed;
  state.distance += effectiveSpeed * dt; state.score += effectiveSpeed * dt * .45 * state.comboMultiplier;
  const p = state.player;
  if (p.y > 0 || p.vy > 0) { p.vy -= 19.5 * dt; p.y += p.vy * dt; if (p.y <= 0) { p.y = 0; p.vy = 0; } }
  p.crouchRemaining = dec(p.crouchRemaining, dt); p.punchRemaining = dec(p.punchRemaining, dt); if (p.punchRemaining === 0) p.punchSide = null;
  p.leftHandRemaining = dec(p.leftHandRemaining, dt); p.rightHandRemaining = dec(p.rightHandRemaining, dt);
  state.effects.shieldRemaining = dec(state.effects.shieldRemaining, dt); state.effects.boostRemaining = dec(state.effects.boostRemaining, dt); state.effects.magnetRemaining = dec(state.effects.magnetRemaining, dt);
  for (const entity of state.entities) {
    const previousZ = entity.z;
    if (entity.type === 'enemy') updateEnemy(entity, effectiveSpeed, dt); else entity.z -= effectiveSpeed * dt;
    if (!entity.consumed && resolveEntity(state, entity)) entity.consumed = true;
    if (!entity.consumed && !entity.nearMissAwarded && ['obstacle', 'enemy'].includes(entity.type) && Math.abs(entity.lane - p.lane) === 1 && previousZ > .05 && entity.z <= .05) {
      entity.nearMissAwarded = true; state.nearMisses += 1; success(state, 18); state.events.push({ type: 'near-miss', lane: entity.lane });
    }
  }
  state.entities = state.entities.filter((entity) => !entity.consumed && entity.z > -1.2); return state;
}
export function snapshot(state) {
  return { elapsed: state.elapsed, distance: state.distance, speed: state.speed, score: Math.floor(state.score), coins: state.coins, health: state.health, gameOver: state.gameOver,
    combo: state.combo, comboMultiplier: state.comboMultiplier, streak: state.streak, nearMisses: state.nearMisses, currentZone: state.currentZone,
    player: { ...state.player }, effects: { ...state.effects }, entities: state.entities.map((entity) => ({ ...entity })), events: state.events.map((event) => ({ ...event })) };
}
