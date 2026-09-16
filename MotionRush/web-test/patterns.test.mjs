import test from 'node:test';
import assert from 'node:assert/strict';
import { PATTERN_FAMILIES, createPatternDirector } from '../android/app/src/main/assets/web/src/patterns.mjs';

const REQUIRED = [
  'jump', 'slide', 'side-choice', 'alternating', 'coin-arc',
  'hand-catch', 'jump-hand', 'crouch-punch', 'enemy-obstacle', 'risk-reward',
];

test('catalog exposes every required authored runner family', () => {
  assert.deepEqual([...PATTERN_FAMILIES].sort(), [...REQUIRED].sort());
});

test('materialized patterns stay on valid lanes with monotonic offsets and a survivable route', () => {
  const director = createPatternDirector(147);
  for (const family of REQUIRED) {
    const entities = director.materialize({ family }, 40);
    assert.ok(entities.length > 0, `${family} should materialize entities`);
    let previousZ = -Infinity;
    for (const entity of entities) {
      assert.ok([-1, 0, 1].includes(entity.lane), `${family} invalid lane ${entity.lane}`);
      assert.ok(entity.z >= previousZ, `${family} z offsets must be monotonic`);
      previousZ = entity.z;
    }
    assert.ok(director.hasSurvivablePath(entities), `${family} must leave at least one survivable route`);
  }
});

test('difficulty unlocks pattern families gradually and deterministically', () => {
  const a = createPatternDirector(99);
  const b = createPatternDirector(99);
  const lowA = Array.from({ length: 12 }, () => a.nextPattern(0).family);
  const lowB = Array.from({ length: 12 }, () => b.nextPattern(0).family);
  assert.deepEqual(lowA, lowB);
  assert.ok(lowA.every((family) => ['jump', 'slide', 'side-choice', 'coin-arc'].includes(family)));

  const high = createPatternDirector(99);
  const unlocked = new Set(Array.from({ length: 80 }, () => high.nextPattern(4).family));
  for (const family of REQUIRED) assert.ok(unlocked.has(family), `difficulty 4 never produced ${family}`);
});

test('mandatory authored actions are not packed inside an impossible reaction window', () => {
  const director = createPatternDirector(5);
  for (let difficulty = 0; difficulty <= 4; difficulty++) {
    for (let n = 0; n < 40; n++) {
      const entities = director.materialize(director.nextPattern(difficulty), 35);
      const mandatory = entities.filter((entity) => entity.mandatoryAction);
      for (let i = 1; i < mandatory.length; i++) {
        if (mandatory[i].mandatoryAction !== mandatory[i - 1].mandatoryAction) {
          assert.ok(mandatory[i].z - mandatory[i - 1].z >= 6.6,
            `mandatory actions too close: ${mandatory[i - 1].mandatoryAction} -> ${mandatory[i].mandatoryAction}`);
        }
      }
    }
  }
});
