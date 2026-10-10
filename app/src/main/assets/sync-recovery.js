(() => {
  'use strict';
  if (window !== window.top || window.__a0SyncRecovery) return;
  window.__a0SyncRecovery = true;
  let foreground = true;
  let selected = null;
  let sequence = null;
  let lastPush = Date.now();
  let nextRead = 0;
  let failures = 0;
  let timer = null;
  let reading = false;

  function schedule() {
    clearTimeout(timer);
    if (foreground && !document.hidden) timer = setTimeout(tick, 1000);
  }
  async function tick() {
    if (reading || !foreground || document.hidden) return;
    const sync = window.Alpine?.store('sync');
    const context = window.getContext?.();
    const now = Date.now();
    if (context !== selected) {
      selected = context; sequence = sync?.lastSeq; lastPush = now; nextRead = now + 2000; failures = 0;
    } else if (sync?.lastSeq !== sequence) {
      sequence = sync?.lastSeq; lastPush = now;
    }
    // A socket acknowledgement alone does not prove that state_push is arriving.
    // Recover through the WebUI's authenticated, cursor-aware read path; never replay Send.
    // The WebUI already polls DEGRADED/DISCONNECTED sessions, so leave those to it.
    if (context && sync?.mode === 'HEALTHY' && typeof window.poll === 'function' &&
        document.getElementById('chat-input') && now >= nextRead && now - lastPush >= 5000) {
      reading = true;
      try {
        const result = await window.poll();
        failures = result?.ok === false ? Math.min(failures + 1, 4) : 0;
      } catch { failures = Math.min(failures + 1, 4); }
      finally {
        reading = false;
        const running = window.Alpine?.store('chats')?.selectedContext?.running;
        nextRead = Date.now() + (failures ? Math.min(30000, 2000 * 2 ** failures) : running ? 2000 : 10000);
      }
    }
    schedule();
  }
  function expectReply(event) {
    if (event.type === 'click' ? event.target.closest?.('#send-button') :
        event.key === 'Enter' && !event.shiftKey && event.target.id === 'chat-input') {
      nextRead = Date.now() + 2000; lastPush = Date.now() - 5000;
    }
  }
  function background() { foreground = false; clearTimeout(timer); }
  function resumed() { foreground = true; nextRead = 0; lastPush = Date.now() - 5000; schedule(); }
  function attach() {
    document.removeEventListener('click', expectReply, true); document.addEventListener('click', expectReply, true);
    document.removeEventListener('keydown', expectReply, true); document.addEventListener('keydown', expectReply, true);
    document.removeEventListener('visibilitychange', schedule); document.addEventListener('visibilitychange', schedule);
    window.removeEventListener('a0-app-background', background); window.addEventListener('a0-app-background', background);
    window.removeEventListener('a0-app-foreground', resumed); window.addEventListener('a0-app-foreground', resumed);
    if (!reading) schedule();
  }
  attach();
  const originalClose = Document.prototype.close;
  Document.prototype.close = function (...args) { const result = originalClose.apply(this, args); attach(); return result; };
})();
