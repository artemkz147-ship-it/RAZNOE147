import test from 'node:test';
import assert from 'node:assert/strict';
import { ObjectPool } from '../android/app/src/main/assets/web/src/object-pool.mjs';

test('pool reuses released objects', () => {
  let made = 0;
  const pool = new ObjectPool(() => ({ id: ++made }), (object) => { object.reset = true; });
  const first = pool.acquire();
  pool.release(first);
  const second = pool.acquire();
  assert.equal(first, second);
  assert.equal(made, 1);
  assert.equal(second.reset, true);
});
