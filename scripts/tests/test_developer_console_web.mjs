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

test('usage screen uses only server-reported tokens, without fabricated spend', () => {
  const html = read('web/developers/console/usage/index.html');
  const js = read('web/developers/console/usage/usage.js');
  assert.match(html, /id="duRequests"/);
  assert.match(html, /id="duInput"/);
  assert.match(html, /id="duOutput"/);
  assert.match(html, /id="duBilling"/);
  assert.match(html, /usage\.js/);
  assert.doesNotMatch(html, /1,284|412K|96K|39\.80|704K|174K/);
  assert.match(js, /\/api\/developers\/usage/);
  assert.match(js, /\.getIdToken\(false\)/);
  assert.match(js, /data\.input_tokens/);
  assert.match(js, /data\.output_tokens/);
  assert.doesNotMatch(js, /localStorage|sessionStorage|tokens\s*\/\s*4/);
});

test('overview, wallet and model catalogue cannot impersonate live revenue', () => {
  const overview = read('web/developers/console/index.html');
  const wallet = read('web/developers/console/wallet/index.html');
  const models = read('web/developers/console/models/index.html');
  assert.match(overview, /paid inference disabled/);
  assert.match(overview, /development preview/i);
  assert.doesNotMatch(overview, /1,284|508K|39\.80|460\.20|req_8f21/);
  assert.match(wallet, /payments not enabled/);
  assert.match(wallet, /disabled>Payments unavailable/);
  assert.doesNotMatch(wallet, /460\.20|39\.80|top up<\/td>/i);
  assert.match(models, /model tiers not launched/);
  assert.match(models, /planned/);
  assert.doesNotMatch(models, /all available|>available<|&#8377;120\.00/);
});
