import test from 'node:test';
import assert from 'node:assert/strict';
import { zoneForDistance, ZONE_NAMES } from '../android/app/src/main/assets/web/src/zones.mjs';

test('five zones cycle deterministically with bounded transition', () => {
  assert.equal(ZONE_NAMES.length, 5);
  assert.deepEqual([0, 220, 440, 660, 880].map((d) => zoneForDistance(d).name), ZONE_NAMES);
  for (let distance = 0; distance < 1200; distance += 13) {
    const zone = zoneForDistance(distance);
    assert.ok(zone.progress >= 0 && zone.progress <= 1);
    assert.ok(ZONE_NAMES.includes(zone.name));
    assert.ok(ZONE_NAMES.includes(zone.next));
  }
});
