// Texture budget for the avatar viewer (runs in the WebView).
// A VRM's textures are decoded to full size on the GPU: one 4096x4096 texture is ~85 MB, and this phone has 3.5 GB of RAM in total.
// installTextureLimit() wraps createImageBitmap, which three.js' GLTFLoader uses for every embedded image, so each image is decoded
// straight at the capped size (the browser scales while decoding) and the full-size bitmap never exists.

/** Width/height of a PNG or JPEG from its first bytes, or null when unknown. */
export function imageSize(b) {
  if (b.length > 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) {
    const u32 = o => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;
    return { w: u32(16), h: u32(20) };
  }
  if (b.length > 4 && b[0] === 0xff && b[1] === 0xd8) {
    let i = 2;
    while (i + 9 < b.length) {
      if (b[i] !== 0xff) { i++; continue; }
      const m = b[i + 1];
      if (m === 0xff) { i++; continue; }
      if (m === 0xd8 || m === 0x01 || (m >= 0xd0 && m <= 0xd7)) { i += 2; continue; }
      const len = (b[i + 2] << 8) | b[i + 3];
      if (m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) return { h: (b[i + 5] << 8) | b[i + 6], w: (b[i + 7] << 8) | b[i + 8] };
      i += 2 + len;
    }
  }
  return null;
}

/** Scale (w,h) down so the longer side is at most cap, keeping the aspect ratio. */
export function fitSize(w, h, cap) {
  const longest = Math.max(w, h);
  if (!(cap > 0) || longest <= cap) return { w, h, scaled: false };
  const k = cap / longest;
  return { w: Math.max(1, Math.round(w * k)), h: Math.max(1, Math.round(h * k)), scaled: true };
}

/** Make every embedded image decode at most `cap` pixels (a number, or a function read at each decode) on its longer side. Returns an uninstall function. */
export function installTextureLimit(capOrFn, target = globalThis, log = () => {}, parallel = 2) {
  const cap = () => (typeof capOrFn === 'function' ? capOrFn() : capOrFn);
  const original = target.createImageBitmap;
  if (typeof original !== 'function') return () => {};
  // Decode a couple of images at a time: all of them together is a memory spike on a 3.5 GB phone.
  let running = 0; const waiting = [];
  const slot = () => running < parallel ? (running++, Promise.resolve()) : new Promise(r => waiting.push(r));
  const release = () => { const next = waiting.shift(); if (next) next(); else running--; };
  target.createImageBitmap = async function (src, ...rest) {
    await slot();
    try { return await decode(src, rest); } finally { release(); }
  };
  async function decode(src, rest) {
    try {
      if (typeof Blob !== 'undefined' && src instanceof Blob) {
        const head = new Uint8Array(await src.slice(0, 262144).arrayBuffer());
        const d = imageSize(head);
        if (d) {
          const f = fitSize(d.w, d.h, cap());
          if (f.scaled) {
            log('texture ' + d.w + 'x' + d.h + ' -> ' + f.w + 'x' + f.h);
            return original.call(target, src, Object.assign({}, rest[0] || {}, { resizeWidth: f.w, resizeHeight: f.h, resizeQuality: 'high' }));
          }
        }
      }
    } catch (e) { log('texture limit skipped: ' + e); }
    return original.call(target, src, ...rest);
  }
  return () => { target.createImageBitmap = original; };
}
