import test from 'node:test';
import assert from 'node:assert/strict';
import { createGameState, spawnEntity, stepGame, applyAction, applyPattern } from '../android/app/src/main/assets/web/src/game.mjs';

test('successful actions build streak and multiplier up to five', () => {
  const state = createGameState(1);
  for (let i = 0; i < 12; i++) {
    spawnEntity(state, { id: `c${i}`, type: 'pickup', pickup: 'coin', lane: 0, z: .6 });
    stepGame(state, .001);
  }
  assert.ok(state.streak >= 12);
  assert.equal(state.comboMultiplier, 5);
});

test('a real hit resets streak while shield absorption does not cost health', () => {
  const state = createGameState(2);
  state.streak = 8;
  state.comboMultiplier = 3;
  state.effects.shieldRemaining = 2;
  spawnEntity(state, { id: 'shield-hit', type: 'obstacle', obstacle: 'solid', lane: 0, z: .6 });
  stepGame(state, .001);
  assert.equal(state.health, 3);

  spawnEntity(state, { id: 'real-hit', type: 'obstacle', obstacle: 'solid', lane: 0, z: .6 });
  stepGame(state, .001);
  assert.equal(state.health, 2);
  assert.equal(state.streak, 0);
  assert.equal(state.comboMultiplier, 1);
});

test('adjacent dangerous pass awards one near miss without damage', () => {
  const state = createGameState(3);
  spawnEntity(state, { id: 'near', type: 'obstacle', obstacle: 'solid', lane: 1, z: .08 });
  const before = state.score;
  stepGame(state, .02);
  assert.equal(state.health, 3);
  assert.equal(state.nearMisses, 1);
  assert.ok(state.score > before);
  assert.ok(state.events.some((event) => event.type === 'near-miss'));
  stepGame(state, .02);
  assert.equal(state.nearMisses, 1);
});

test('applyPattern appends authored entities with consumed state initialized', () => {
  const state = createGameState(4);
  applyPattern(state, [
    { id: 'a', type: 'pickup', pickup: 'coin', lane: -1, z: 20 },
    { id: 'b', type: 'enemy', variant: 'guard', lane: 1, z: 24 },
  ]);
  assert.equal(state.entities.length, 2);
  assert.equal(state.entities[0].consumed, false);
});

test('enemy variants have distinct deterministic motion behavior', () => {
  const guard = createGameState(5);
  spawnEntity(guard, { id: 'g', type: 'enemy', variant: 'guard', lane: 1, side: 'left', z: 15 });
  stepGame(guard, .1);
  assert.equal(guard.entities[0].lane, 1);

  const sweeper = createGameState(5);
  spawnEntity(sweeper, { id: 's', type: 'enemy', variant: 'sweeper', lane: -1, side: 'right', z: 15, sweepTarget: 0 });
  stepGame(sweeper, .1);
  assert.equal(sweeper.entities[0].lane, 0);

  const charger = createGameState(5);
  spawnEntity(charger, { id: 'c', type: 'enemy', variant: 'charger', lane: 1, side: 'left', z: 15 });
  stepGame(charger, .1);
  assert.ok(charger.entities[0].z < guard.entities[0].z);
});

test('successful obstacle avoidance and enemy punch count toward streak', () => {
  const state = createGameState(6);
  applyAction(state, 'JUMP');
  state.player.y = 1;
  spawnEntity(state, { id: 'o', type: 'obstacle', obstacle: 'low', lane: 0, z: .6 });
  stepGame(state, .001);
  assert.equal(state.streak, 1);

  applyAction(state, 'PUNCH_LEFT');
  spawnEntity(state, { id: 'e', type: 'enemy', variant: 'guard', side: 'left', lane: 0, z: .6 });
  stepGame(state, .001);
  assert.equal(state.streak, 2);
});
