import { appLink, serverFromFragment } from './connection.mjs';

function renderConnection() {
  const server = serverFromFragment(location.hash);
  const section = document.querySelector('#connect');
  section.hidden = !server;
  document.querySelector('#server').textContent = server || '';
  const open = document.querySelector('#open-app');
  if (server) open.href = appLink(server, /Android/i.test(navigator.userAgent));
  else open.removeAttribute('href');
}
renderConnection();
window.addEventListener('hashchange', renderConnection);
