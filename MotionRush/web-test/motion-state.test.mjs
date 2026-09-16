import test from 'node:test';
import assert from 'node:assert/strict';
import { createMotionInterpolator } from '../android/app/src/main/assets/web/src/motion-state.mjs';

test('interpolates body state between native snapshots', () => {
  const motion = createMotionInterpolator();
  motion.push({ t: 1000, cx: 0, cy: 0, vy: 0, lean: 0, lwv: 0, rwv: 0, conf: 1 }, 1000);
  motion.push({ t: 1033, cx: 1, cy: -.2, vy: -.5, lean: .5, lwv: 1, rwv: -1, conf: 1 }, 1033);

  const state = motion.sample(1016.5);
  assert.ok(state.cx > .4 && state.cx < .6);
  assert.ok(state.lean > .2 && state.lean < .3);
  assert.ok(state.cy < 0 && state.cy > -.2);
});

test('prediction is bounded and stale confidence decays', () => {
  const motion = createMotionInterpolator();
  motion.push({ t: 1000, cx: 0, cy: 0, vx: 2, vy: -1, lean: 0, conf: 1 }, 1000);
  motion.push({ t: 1033, cx: .1, cy: -.05, vx: 2, vy: -1, lean: .1, conf: 1 }, 1033);

  const predicted = motion.sample(1100);
  assert.ok(predicted.cx <= .20, `prediction exceeded 45ms cap: ${predicted.cx}`);

  const stale = motion.sample(1200);
  assert.ok(stale.conf < .7, `stale confidence should decay: ${stale.conf}`);
});
