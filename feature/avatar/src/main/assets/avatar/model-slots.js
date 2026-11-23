// Keeps at most two VRMs resident in the viewer: the one on screen (active) and the next one staged behind it.
// Pure logic (no three.js): the host passes load/show/hide/dispose, so it can be tested with fakes.
//
//   setModel(key, url)  show a model: instant if it was staged, otherwise load it (the old staged model is dropped first)
//   stage(key, url)     load a model in the background without showing it (deferred while another load is running)
//   unload()            drop everything (used when the face goes back to the eyes)
//
// Events (onEvent): {type:'model', key, ms, staged}, {type:'staged', key, ms}, {type:'error', key, message}, {type:'unloaded'}

export function createSlots({ load, show, hide, dispose, now = () => Date.now(), onEvent = () => {} }) {
  let active = null;      // {key, obj}
  let staged = null;      // {key, obj}
  let loading = null;     // {key, token, kind}
  let pendingStage = null;
  let token = 0;

  const resident = () => (active ? 1 : 0) + (staged ? 1 : 0) + (loading ? 1 : 0);

  function dropStaged() { if (staged) { dispose(staged.obj); staged = null; } }

  async function runLoad(key, url, kind) {
    const my = ++token; const t0 = now();
    const rec = { key, token: my, kind }; loading = rec;
    try {
      const obj = await load(url, key);
      if (my !== token) { dispose(obj); return; }          // superseded (unload or a newer setModel)
      loading = null;
      if (rec.kind === 'model') {
        const old = active;
        if (old) hide(old.obj);
        active = { key, obj }; show(obj, key);
        if (old) dispose(old.obj);
        onEvent({ type: 'model', key, ms: now() - t0, staged: false });
      } else {
        staged = { key, obj };
        onEvent({ type: 'staged', key, ms: now() - t0 });
      }
    } catch (e) {
      if (my === token) { loading = null; onEvent({ type: 'error', key, message: String(e && e.message || e) }); }
    }
    if (!loading && pendingStage) { const p = pendingStage; pendingStage = null; stage(p.key, p.url); }
  }

  function setModel(key, url) {
    if (active && active.key === key && !loading) return;
    if (loading && loading.key === key) { loading.kind = 'model'; return; }   // already loading (maybe only staging): just promote it
    if (staged && staged.key === key) {
      const t0 = now(); const old = active;
      token++; loading = null;                              // a staging load for another model is now obsolete
      if (old) hide(old.obj);
      active = staged; staged = null; show(active.obj, key);
      if (old) dispose(old.obj);
      onEvent({ type: 'model', key, ms: now() - t0, staged: true });
      return;
    }
    dropStaged(); pendingStage = null;
    if (loading) token++;                                   // cancel an in-flight staging load: its result will be disposed
    loading = null;
    runLoad(key, url, 'model');
  }

  function stage(key, url) {
    if ((active && active.key === key) || (staged && staged.key === key)) return;
    if (loading) { pendingStage = { key, url }; return; }
    dropStaged();
    runLoad(key, url, 'stage');
  }

  function unload() {
    token++; loading = null; pendingStage = null;
    dropStaged(); if (active) { hide(active.obj); dispose(active.obj); active = null; }
    onEvent({ type: 'unloaded' });
  }

  return { setModel, stage, unload, resident, snapshot: () => ({ active: active && active.key, staged: staged && staged.key, loading: loading && loading.key }) };
}
