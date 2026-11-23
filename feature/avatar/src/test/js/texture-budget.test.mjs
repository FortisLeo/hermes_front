import test from 'node:test';
import assert from 'node:assert/strict';
import { imageSize, fitSize, installTextureLimit } from '../../main/assets/avatar/texture-budget.js';

const png = (w, h) => { const b = new Uint8Array(32); b.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 0); const v = new DataView(b.buffer); v.setUint32(16, w); v.setUint32(20, h); return b; };
const jpeg = (w, h) => { const b = new Uint8Array(40); b.set([0xff, 0xd8, 0xff, 0xe0, 0, 4, 0, 0, 0xff, 0xc0, 0, 11, 8, h >> 8, h & 255, w >> 8, w & 255], 0); return b; };

test('reads PNG and JPEG sizes', () => { assert.deepEqual(imageSize(png(4096, 2048)), { w: 4096, h: 2048 }); assert.deepEqual(imageSize(jpeg(1024, 512)), { w: 1024, h: 512 }); });
test('unknown data has no size', () => { assert.equal(imageSize(new Uint8Array([1, 2, 3])), null); assert.equal(imageSize(new Uint8Array(40)), null); });
test('fitSize keeps small images and scales big ones by the long side', () => {
  assert.deepEqual(fitSize(512, 512, 1024), { w: 512, h: 512, scaled: false });
  assert.deepEqual(fitSize(4096, 2048, 1024), { w: 1024, h: 512, scaled: true });
  assert.deepEqual(fitSize(100, 4000, 1000), { w: 25, h: 1000, scaled: true });
});
test('createImageBitmap is called with a resize for big images only', async () => {
  const calls = []; const target = { createImageBitmap: async (s, o) => { calls.push(o); return 'bmp'; } };
  const undo = installTextureLimit(1024, target);
  await target.createImageBitmap(new Blob([png(4096, 4096)]), { premultiplyAlpha: 'none' });
  await target.createImageBitmap(new Blob([png(512, 512)]), { premultiplyAlpha: 'none' });
  await target.createImageBitmap('not a blob');
  assert.equal(calls[0].resizeWidth, 1024); assert.equal(calls[0].premultiplyAlpha, 'none'); assert.equal(calls[1].resizeWidth, undefined); assert.equal(calls[2], undefined);
  undo(); assert.equal(target.createImageBitmap.length, 2);
});
test('at most `parallel` decodes run at the same time', async () => {
  let running = 0, peak = 0;
  const target = { createImageBitmap: async () => { running++; peak = Math.max(peak, running); await new Promise(r => setTimeout(r, 5)); running--; return 'b'; } };
  installTextureLimit(1024, target, () => {}, 2);
  await Promise.all(Array.from({ length: 8 }, () => target.createImageBitmap(new Blob([png(512, 512)]))));
  assert.equal(peak, 2);
});
