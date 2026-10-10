import { appLink, serverFromFragment } from './connection.mjs';

function renderConnection() {
  const server = serverFromFragment(location.hash);
  const section = document.querySelector('#connect');
  section.hidden = !server;
  document.querySelector('#server').textContent = server || '';
  const open = document.querySelector('#open-app');
  const android = /Android/i.test(navigator.userAgent);
  const quickOpen = document.querySelector('#quick-open');
  quickOpen.hidden = !server || !android;
  if (server) {
    open.href = appLink(server, android);
    quickOpen.href = open.href;
  }
  else open.removeAttribute('href');
}
renderConnection();
window.addEventListener('hashchange', renderConnection);
