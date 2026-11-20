// Contract checks for the B2B console while live billing remains disabled.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(path, 'utf8');

test('key screen shows backend-driven owner-bound data, never mock credentials', () => {
  const html = read('web/developers/console/keys/index.html');
  const js = read('web/developers/console/keys/keys.js');

  assert.match(html, /id="dkRows"/);
  assert.match(html, /id="dkOneTime" hidden/);
  assert.match(html, /id="dkSecret"/);
  assert.match(html, /id="dkCreate" disabled/);
  assert.match(html, /firebase-app-compat\.js/);
  assert.match(html, /firebase-auth-compat\.js/);
  assert.match(html, /authentication\/firebase-config\.js/);
  assert.match(html, /keys\.js/);
  assert.doesNotMatch(html, /sk_live_|sk_test_|Yashwanth|Production server/);

  assert.match(js, /\.getIdToken\(false\)/);
  assert.match(js, /'\/api\/developers\/'/);
  assert.match(js, /api\('status'\)/);
  assert.match(js, /api\('keys'\)/);
  assert.match(js, /'DELETE'/);
  assert.match(js, /lastCreatedSecret = String\(result\.key/);
  assert.match(js, /secretCode\.textContent = lastCreatedSecret/);
  assert.doesNotMatch(js, /localStorage|sessionStorage/);
  assert.doesNotMatch(js, /sk_live_|sk_test_|sk_sankalp_/);
});

test('web plugin disconnect includes authenticated owner user_id', () => {
  const source = read('web/users/menu/plugins/script.js');
  assert.match(source, /provider\.disconnect, \{ method: 'POST', body: JSON\.stringify\(\{ user_id: window\.IndoonePlugins\.userId\(\) \}\)/);
});
