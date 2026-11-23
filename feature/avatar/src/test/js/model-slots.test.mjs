import test from 'node:test';
import assert from 'node:assert/strict';
import { createSlots } from '../../main/assets/avatar/model-slots.js';

function harness({ failFor = null } = {}) {
  const log = []; let live = 0, peak = 0; const gates = {};
  const load = (url, key) => new Promise((res, rej) => {
    const make = () => { if (failFor === key) return rej(new Error('boom')); live++; peak = Math.max(peak, live); res({ key }); };
    if (gates[key] === 'hold') gates[key] = make; else setTimeout(make, 1);
  });
  const slots = createSlots({
    load, show: (o) => log.push('show ' + o.key), hide: (o) => log.push('hide ' + o.key), dispose: (o) => { live--; log.push('dispose ' + o.key); },
    onEvent: e => log.push('ev ' + e.type + (e.key ? ' ' + e.key : '') + (e.staged ? ' staged' : '')),
  });
  return { slots, log, gates, peak: () => peak, live: () => live };
}
const tick = (ms = 10) => new Promise(r => setTimeout(r, ms));

test('first model loads and shows', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick();
  assert.deepEqual(h.log, ['show a', 'ev model a']); assert.equal(h.slots.snapshot().active, 'a');
});
test('staged model swaps in instantly and the old one is disposed', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.slots.stage('b', 'u/b'); await tick();
  assert.equal(h.slots.snapshot().staged, 'b'); h.log.length = 0;
  h.slots.setModel('b', 'u/b');   // synchronous: no await needed
  assert.deepEqual(h.log, ['hide a', 'show b', 'dispose a', 'ev model b staged']); assert.equal(h.live(), 1);
});
test('never more than two models resident, even when swiping around', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.slots.stage('b', 'u/b'); await tick();
  h.slots.setModel('c', 'u/c'); await tick(); h.slots.stage('d', 'u/d'); await tick(); h.slots.setModel('d', 'u/d'); await tick(); h.slots.stage('a', 'u/a'); await tick();
  assert.ok(h.peak() <= 2, 'peak ' + h.peak()); assert.equal(h.live(), 2);
});
test('an unstaged setModel drops the stale staged model before loading', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.slots.stage('b', 'u/b'); await tick(); h.slots.setModel('c', 'u/c');
  assert.ok(h.log.includes('dispose b')); await tick(); assert.equal(h.slots.snapshot().active, 'c'); assert.equal(h.live(), 1);
});
test('stage requested while a model is loading waits for it', async () => {
  const h = harness(); h.gates.a = 'hold'; h.slots.setModel('a', 'u/a'); h.slots.stage('b', 'u/b'); await tick();
  assert.equal(h.slots.snapshot().staged, null); h.gates.a(); await tick(10); assert.equal(h.slots.snapshot().active, 'a'); assert.equal(h.slots.snapshot().staged, 'b'); assert.ok(h.peak() <= 2);
});
test('a superseded load is disposed, not shown', async () => {
  const h = harness(); h.gates.a = 'hold'; h.slots.setModel('a', 'u/a'); h.slots.setModel('b', 'u/b'); await tick(); h.gates.a(); await tick();
  assert.equal(h.slots.snapshot().active, 'b'); assert.ok(!h.log.includes('show a')); assert.equal(h.live(), 1);
});
test('load failure reports an error and keeps the current model', async () => {
  const h = harness({ failFor: 'bad' }); h.slots.setModel('a', 'u/a'); await tick(); h.slots.setModel('bad', 'u/bad'); await tick();
  assert.ok(h.log.includes('ev error bad')); assert.equal(h.slots.snapshot().active, 'a');
});
test('unload frees everything', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.slots.stage('b', 'u/b'); await tick(); h.slots.unload(); assert.equal(h.live(), 0); assert.equal(h.slots.resident(), 0);
});
test('setting the already active model is a no-op', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.log.length = 0; h.slots.setModel('a', 'u/a'); await tick(); assert.deepEqual(h.log, []);
});
test('swiping to the model that is still being staged promotes that load instead of starting another', async () => {
  const h = harness(); h.slots.setModel('a', 'u/a'); await tick(); h.gates.b = 'hold'; h.slots.stage('b', 'u/b'); h.slots.setModel('b', 'u/b'); h.gates.b(); await tick();
  assert.equal(h.slots.snapshot().active, 'b'); assert.ok(h.log.includes('ev model b')); assert.equal(h.live(), 1);
});
