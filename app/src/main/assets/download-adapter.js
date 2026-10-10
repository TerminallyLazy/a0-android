(() => {
  'use strict';
  if (window !== window.top || window.__a0DownloadAdapter) return;
  const bridge = window.A0Downloads;
  if (!bridge || typeof bridge.postMessage !== 'function') return;
  window.__a0DownloadAdapter = true;
  const MAX = 100 * 1024 * 1024, CHUNK = 64 * 1024;
  const documentId = crypto.randomUUID();
  const blobs = new Map(), replies = new Map();
  let epoch = null, active = null, helloResolve = null;
  const tracks = new Set(), audioContexts = new Set();
  let foreground = true;
  if (navigator.mediaDevices?.getUserMedia) {
    const getMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getUserMedia = async constraints => {
      const stream = await getMedia(constraints);
      if (!foreground) {
        stream.getTracks().forEach(track => track.stop());
        throw new DOMException('App is in the background', 'NotAllowedError');
      }
      for (const track of stream.getTracks()) {
        tracks.add(track); track.addEventListener('ended', () => tracks.delete(track), {once: true});
      }
      return stream;
    };
  }
  if (window.AudioContext) {
    window.AudioContext = new Proxy(window.AudioContext, { construct(Type, args) {
      const context = new Type(...args); audioContexts.add(context);
      context.addEventListener('statechange', () => { if (context.state === 'closed') audioContexts.delete(context); });
      return context;
    }});
  }
  function background() {
    foreground = false;
    for (const track of tracks) track.stop();
    tracks.clear();
    for (const context of audioContexts) if (context.state === 'running') void context.suspend();
    document.querySelectorAll('audio,video').forEach(media => media.pause());
  }
  function resumed() { foreground = true; }
  const ready = new Promise(resolve => { helloResolve = resolve; });
  bridge.onmessage = event => {
    let message;
    try { message = JSON.parse(event.data); } catch { return; }
    if (message.kind === 'hello' && message.documentId === documentId) {
      epoch = message.epoch; helloResolve(); return;
    }
    if (message.epoch !== epoch || message.id !== active) return;
    const key = message.replyTo;
    const waiter = replies.get(key);
    if (!waiter) return;
    clearTimeout(waiter.timer); replies.delete(key);
    if (message.ok) waiter.resolve();
    else waiter.reject(new Error('Download canceled or rejected'));
  };
  function hello() {
    if (epoch !== null) return;
    bridge.postMessage(JSON.stringify({v: 1, kind: 'hello', documentId}));
    setTimeout(hello, 500);
  }
  hello();
  function send(kind, payload = {}) {
    const replyTo = `${kind}:${payload.sequence ?? ''}`;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { replies.delete(replyTo); reject(new Error('Download timed out')); }, 180000);
      replies.set(replyTo, {resolve, reject, timer});
      bridge.postMessage(JSON.stringify({v: 1, kind, epoch, documentId, id: active, replyTo, ...payload}));
    });
  }
  const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL);
  URL.createObjectURL = object => {
    const url = create(object);
    if (object instanceof Blob) blobs.set(url, object);
    return url;
  };
  URL.revokeObjectURL = url => { blobs.delete(String(url)); revoke(url); };
  async function transfer(blobPromise, name) {
    if (active !== null) return;
    active = crypto.randomUUID();
    try {
      const blob = await blobPromise;
      await ready;
      await send('begin', {name: name || 'download', mime: blob.type || 'application/octet-stream', size: blob.size});
      if (blob.size > MAX) throw new Error('Download exceeds limit');
      let sequence = 0;
      for (let offset = 0; offset < blob.size; offset += CHUNK) {
        const bytes = new Uint8Array(await blob.slice(offset, offset + CHUNK).arrayBuffer());
        let binary = '';
        for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
        await send('chunk', {sequence: sequence++, data: btoa(binary)});
      }
      await send('finish');
    } catch {
      if (epoch !== null) bridge.postMessage(JSON.stringify({v: 1, kind: 'cancel', epoch, documentId, id: active}));
    } finally {
      for (const waiter of replies.values()) { clearTimeout(waiter.timer); waiter.reject(new Error('Canceled')); }
      replies.clear(); active = null;
    }
  }
  function capture(anchor) {
    const url = anchor.href;
    if (!url.startsWith('blob:') && !url.startsWith('data:')) return false;
    // Only download anchors; ordinary media/blob navigation is not native file access.
    if (!anchor.hasAttribute('download')) return false;
    const heldBlob = blobs.get(url); // Hold the Blob before stock UI immediately revokes the URL.
    const blobPromise = heldBlob ? Promise.resolve(heldBlob) :
      url.length > MAX * 1.4 ? Promise.reject(new Error('Too large')) : fetch(url).then(response => response.blob());
    void transfer(blobPromise, anchor.download);
    return true;
  }
  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (!capture(this)) originalClick.call(this);
  };
  function clicked(event) {
    const anchor = event.target.closest?.('a[download]');
    if (anchor && capture(anchor)) { event.preventDefault(); event.stopImmediatePropagation(); }
  }
  function attach() {
    document.removeEventListener('click', clicked, true); document.addEventListener('click', clicked, true);
    window.removeEventListener('a0-app-background', background); window.addEventListener('a0-app-background', background);
    window.removeEventListener('a0-app-foreground', resumed); window.addEventListener('a0-app-foreground', resumed);
  }
  attach();
  // Agent Zero's splash replaces the document in place. Reattach handlers after that replacement.
  const originalClose = Document.prototype.close;
  Document.prototype.close = function (...args) { const result = originalClose.apply(this, args); attach(); return result; };
})();
