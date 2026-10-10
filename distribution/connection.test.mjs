import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parseOrigin, installLink, appLink, serverFromFragment } from './site/connection.mjs';

test('HTTPS connection survives install-page fragment and Android handoff', () => {
  const origin = 'https://agent.example:8443';
  const install = new URL(installLink(origin));
  assert.equal(install.search, '');
  assert.equal(serverFromFragment(install.hash), origin);
  assert.equal(appLink(origin), 'a0android://connect?server=https%3A%2F%2Fagent.example%3A8443');
  assert.match(appLink(origin, true), /package=com\.terminallylazy\.a0android;/);
});

test('credentials and arbitrary destinations cannot become connect actions', () => {
  for (const input of ['http://agent.example','https://u:p@agent.example','https://agent.example/path','https://agent.example?token=x','https://agent.example#x','javascript:alert(1)','https://agent.example\\@evil.example','https://agent.example/%2e','https://é.example']) {
    assert.equal(parseOrigin(input), null);
    assert.equal(appLink(input), null);
    assert.equal(new URL(installLink(input)).hash, '');
  }
  assert.equal(serverFromFragment('#server=%ZZ'), null);
  assert.equal(serverFromFragment('#server=https://agent.example&token=x'), null);
});

test('WebUI bridge and public landing page use identical connection contract', async () => {
  assert.equal(await readFile(new URL('./site/connection.mjs', import.meta.url), 'utf8'), await readFile(new URL('./webui/webui/components/modals/android-app/connection.mjs', import.meta.url), 'utf8'));
});
