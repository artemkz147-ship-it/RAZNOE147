import test from 'node:test';
import assert from 'node:assert/strict';
import { computeCharacterPose } from '../android/app/src/main/assets/web/src/character-rig.mjs';

const baseView = (overrides = {}) => ({
  distance: 10,
  player: { lane: 0, y: 0, vy: 0, crouchRemaining: 0, punchRemaining: 0, punchSide: null, leftHandRemaining: 0, rightHandRemaining: 0, ...overrides },
  events: [], health: 3,
});

test('continuous body lean immediately affects torso roll', () => {
  const neutral = computeCharacterPose(baseView(), { lean: 0, conf: 1 }, 1);
  const leaned = computeCharacterPose(baseView(), { lean: .8, conf: 1 }, 1);
  assert.ok(Math.abs(leaned.torsoRoll) > Math.abs(neutral.torsoRoll) + .12);
});

test('raised left arm overlays running legs instead of cancelling locomotion', () => {
  const pose = computeCharacterPose(baseView(), { la: 1.2, ra: 0, conf: 1 }, 1.3);
  assert.ok(Math.abs(pose.leftShoulderZ) > 1);
  assert.ok(Math.abs(pose.leftHipX - pose.rightHipX) > .2);
});

test('airborne, crouch and landing/ground poses are distinct', () => {
  const ground = computeCharacterPose(baseView(), null, 1);
  const air = computeCharacterPose(baseView({ y: 1.1, vy: 2 }), null, 1);
  const crouch = computeCharacterPose(baseView({ crouchRemaining: .4 }), null, 1);
  assert.notEqual(ground.bodyScaleY, air.bodyScaleY);
  assert.ok(crouch.bodyScaleY < ground.bodyScaleY);
  assert.ok(air.leftKneeX < ground.leftKneeX || air.rightKneeX < ground.rightKneeX);
});

test('punch overrides only relevant upper limb', () => {
  const normal = computeCharacterPose(baseView(), null, 1.2);
  const punch = computeCharacterPose(baseView({ punchRemaining: .17, punchSide: 'left' }), null, 1.2);
  assert.ok(Math.abs(punch.leftShoulderX - normal.leftShoulderX) > .35);
  assert.ok(Math.abs(punch.rightShoulderX - normal.rightShoulderX) < .25);
});
