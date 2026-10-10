(() => {
  'use strict';
  if (window !== window.top || window.__a0MobileLayout) return;
  window.__a0MobileLayout = true;
  const css = `
    @media (max-width: 767px) {
      body:has(#chat-input) .right-canvas-rail { display: none !important; }
      #a0-tools-toggle { position: fixed; top: 6px; left: 64px; z-index: 3401;
        min-width: 64px; min-height: 44px; padding: 0 12px; border-radius: 12px;
        border: 1px solid var(--color-border, #3b4558); background: var(--color-panel, #171e2d);
        color: var(--color-text, #eef1fa); font: 600 14px var(--font-family-main, sans-serif); }
      #a0-tools-backdrop { position: fixed; inset: 0; background: #0006; z-index: 3399; }
      #a0-tools-toggle[hidden], #a0-tools-backdrop[hidden] { display: none !important; }
      body:has(.modal.show) #a0-tools-toggle { display: none; }
      body:has(#chat-input) #right-panel { padding-top: 56px; box-sizing: border-box; }
      body:has(#chat-input) #chat-history { padding-top: 16px; }
      body:has(#chat-input) #sidebar-header-panel { top: 6px; left: 8px; width: 44px;
        min-height: 44px; padding: 0; border-radius: 10px; }
      body:has(#chat-input) #sidebar-header-panel #logo-bar { display: none; }
      body:has(#chat-input) #hide-button { margin-top: 0; gap: 0; }
      body:has(#chat-input) #sidebar-header-panel button { width: 44px; height: 44px; }
      body:has(#chat-input) #sidebar-header-panel.sidebar-header-panel-open { width: 220px; z-index: 3402; }
      body:has(.sidebar-header-panel-open) #a0-tools-toggle { visibility: hidden; }
      body:has(#chat-input) #time-date-container { top: 6px; right: 8px; margin-top: 0; gap: 8px; }
      body:has(#chat-input) #time-date { display: none; }
      body:has(#chat-input) .project-dropdown-button { min-height: 44px; max-width: min(30vw, 128px);
        overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      body:has(#chat-input).a0-tools-open .right-canvas-rail {
        display: grid !important; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px;
        position: fixed; top: auto !important; right: 12px; bottom: 12px; left: 12px;
        width: auto; height: auto; max-height: calc(100dvh - 88px); padding: 12px;
        transform: none !important; box-sizing: border-box; overflow-y: auto;
        border: 1px solid var(--color-border, #3b4558); border-radius: 18px; }
      body:has(#chat-input).a0-tools-open .right-canvas-rail-button {
        display: flex; flex-direction: column; gap: 6px; width: 100%; min-width: 44px;
        height: auto; min-height: 76px; padding: 10px 4px; opacity: 1; }
      body:has(#chat-input).a0-tools-open .right-canvas-rail-button::after {
        content: attr(aria-label); font-size: 12px; line-height: 1.3; white-space: normal; text-align: center; }
      body:has(#chat-input).a0-tools-open .right-canvas-rail-grip,
      body:has(#chat-input).a0-tools-open .right-canvas-rail-separator,
      body:has(#chat-input).a0-tools-open .right-canvas-toggle-button { display: none; }
      body.a0-mobile-host #right-canvas { position: fixed; top: 0; right: 0; bottom: 0;
        z-index: 3400; height: 100%; max-width: none; transform: none !important; }
      body.a0-mobile-host #right-canvas.is-closed { pointer-events: none; box-shadow: none; }
      body.a0-mobile-host #right-canvas .right-canvas-rail { pointer-events: auto; }
      body.a0-mobile-host #right-canvas.is-open { width: 100% !important; pointer-events: auto; }
      body.a0-mobile-host #right-canvas.is-open .right-canvas-shell { display: flex !important; }
      body.a0-mobile-host .right-canvas-resize-handle { display: none; }
      body.a0-mobile-host .right-canvas-header { min-height: 56px; padding: 6px 12px; box-sizing: border-box; }
      body.a0-mobile-host .right-canvas-toolbar { padding: 0; }
      body.a0-mobile-host .right-canvas-icon-button { min-width: 44px; min-height: 44px; }
      body.a0-mobile-host .right-canvas-tab:not(.is-active) { display: none; }
      body.a0-mobile-host .right-canvas-tab.is-active { min-height: 44px; opacity: 1; }
      body.a0-mobile-host .right-canvas-tab-label { display: block; font-size: 16px; }
      body.a0-mobile-host:has(#right-canvas.is-open) #a0-tools-toggle { display: none; }
      #a0-tools-status { position: fixed; left: 16px; right: 16px; bottom: 16px; z-index: 3500;
        padding: 16px; border-radius: 12px; background: #293247; color: #fff; font: 14px/1.5 sans-serif; }
      #a0-tools-status[hidden] { display: none; }
      body:has(#chat-input) .input-row {
        display: grid; grid-template-columns: 44px minmax(0, 1fr); gap: 6px 10px; padding: 8px 10px;
        border-radius: 16px; min-width: 0; box-sizing: border-box; }
      body:has(#chat-input) #chat-input-container { grid-column: 1 / -1; grid-row: 1; min-width: 0; }
      body:has(#chat-input) #chat-input { min-height: 44px; max-height: min(24dvh, 160px);
        box-sizing: border-box; padding: 8px 44px 8px 4px; font-size: 16px; line-height: 1.5; }
      body:has(#chat-input) #expand-button { width: 44px; height: 44px; top: 0; bottom: auto; }
      body:has(#chat-input) .chat-more-dropdown { grid-row: 2; grid-column: 1; }
      body:has(#chat-input) #chat-buttons-wrapper { grid-row: 2; grid-column: 2; justify-self: end; gap: 8px; }
      body:has(#chat-input) .chat-more-trigger,
      body:has(#chat-input) #chat-buttons-wrapper button { min-width: 44px; min-height: 44px; }
    }
    @media (min-width: 768px) { #a0-tools-toggle, #a0-tools-backdrop { display: none !important; } }
  `;
  let observer = null;
  let canvas = null;
  let statusTimer = null;
  const mobile = matchMedia('(max-width: 767px)');
  function showFailure() {
    let status = document.getElementById('a0-tools-status');
    if (!status) {
      status = document.createElement('div'); status.id = 'a0-tools-status'; status.setAttribute('role', 'alert');
      document.body.append(status);
    }
    status.textContent = 'This tool could not open. Select a chat if the tool requires one, then try again.';
    status.hidden = false;
    clearTimeout(statusTimer); statusTimer = setTimeout(() => { status.hidden = true; }, 6000);
  }
  function prepareCanvas() {
    const store = window.Alpine?.store('rightCanvas');
    if (!store || typeof store.updateLayoutMode !== 'function' || typeof store.open !== 'function') return;
    canvas = store;
    document.body.classList.add('a0-mobile-host');
    if (store.__a0MobileHost) return;
    store.__a0MobileHost = true;
    const updateLayoutMode = store.updateLayoutMode;
    const shouldRender = store.shouldRender;
    const open = store.open;
    // Match the Swift workspace contract: use the registered docked lifecycle for
    // panel-only plugins. Surface visibility and canOpen still belong to the server.
    store.updateLayoutMode = function (...args) {
      if (!mobile.matches) return updateLayoutMode.apply(this, args);
      this.isMobileMode = false; this.isOverlayMode = true;
    };
    store.shouldRender = function (...args) { return mobile.matches || shouldRender.apply(this, args); };
    store.open = async function (...args) {
      try {
        const result = await open.apply(this, args);
        if (mobile.matches && result === false) showFailure();
        return result;
      } catch (error) {
        if (mobile.matches) showFailure();
        throw error;
      } finally { if (mobile.matches) close(); }
    };
    store.applyLayoutState();
  }
  function close() {
    document.body?.classList.remove('a0-tools-open');
    const toggle = document.getElementById('a0-tools-toggle');
    if (toggle) { if (toggle.textContent !== 'Tools') toggle.textContent = 'Tools'; toggle.setAttribute('aria-expanded', 'false'); }
    const backdrop = document.getElementById('a0-tools-backdrop');
    if (backdrop) backdrop.hidden = true;
  }
  function refresh() {
    if (!document.head || !document.body) return;
    prepareCanvas();
    if (!document.getElementById('a0-mobile-style')) {
      const style = document.createElement('style'); style.id = 'a0-mobile-style'; style.textContent = css;
      document.head.append(style);
    }
    const rail = document.querySelector('.right-canvas-rail');
    let toggle = document.getElementById('a0-tools-toggle');
    if (!rail || !document.getElementById('chat-input')) { if (toggle) toggle.hidden = true; close(); return; }
    if (!toggle) {
      toggle = document.createElement('button'); toggle.id = 'a0-tools-toggle'; toggle.type = 'button';
      toggle.textContent = 'Tools'; toggle.setAttribute('aria-label', 'Workspace tools'); toggle.setAttribute('aria-expanded', 'false');
      toggle.onclick = () => {
        if (document.body.classList.contains('a0-tools-open')) { close(); return; }
        document.body.classList.add('a0-tools-open'); toggle.textContent = 'Close'; toggle.setAttribute('aria-expanded', 'true');
        document.getElementById('a0-tools-backdrop').hidden = false;
        [...rail.querySelectorAll('button')].find(button => !button.disabled && button.getClientRects().length)?.focus();
      };
      const backdrop = document.createElement('div'); backdrop.id = 'a0-tools-backdrop'; backdrop.hidden = true;
      backdrop.setAttribute('aria-hidden', 'true'); backdrop.onclick = close;
      document.body.append(backdrop, toggle);
    }
    if (!rail.id) rail.id = 'a0-mobile-tools';
    toggle.setAttribute('aria-controls', rail.id);
    toggle.hidden = false;
  }
  function clicked(event) {
    // Keep the server's original surface handlers, eligibility and modal lifecycles.
    if (event.target.closest?.('.right-canvas-rail-button')) queueMicrotask(close);
  }
  function keyed(event) {
    if (event.key !== 'Escape') return;
    if (document.body?.classList.contains('a0-tools-open')) close();
    else if (mobile.matches && canvas?.isOpen) canvas.close();
  }
  window.__a0MobileBack = () => {
    if (document.querySelector('.modal') && typeof window.closeModal === 'function') { window.closeModal(); return true; }
    if (document.body?.classList.contains('a0-tools-open')) { close(); return true; }
    if (mobile.matches && canvas?.isOpen) { canvas.close(); return true; }
    return false;
  };
  function attach() {
    observer?.disconnect();
    // Components arrive after the shell. Observe structure only, never message text or attributes.
    observer = new MutationObserver(refresh); observer.observe(document, {childList: true, subtree: true});
    document.removeEventListener('click', clicked, true); document.addEventListener('click', clicked, true);
    document.removeEventListener('keydown', keyed); document.addEventListener('keydown', keyed);
    window.removeEventListener('a0-app-background', close); window.addEventListener('a0-app-background', close);
    refresh();
  }
  mobile.addEventListener('change', () => { close(); canvas?.applyLayoutState(); });
  attach();
  const originalClose = Document.prototype.close;
  Document.prototype.close = function (...args) { const result = originalClose.apply(this, args); attach(); return result; };
})();
