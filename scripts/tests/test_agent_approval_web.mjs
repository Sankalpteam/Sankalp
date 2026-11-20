import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const uuid = '12345678-1234-4234-a234-1234567890ab';
const source = readFileSync('web/users/home/message/chat-api.js', 'utf8');

function apiHarness({ uid = 'user-one', response, request } = {}) {
  let active = uid;
  const calls = [];
  const user = { get uid() { return active; }, getIdToken: async () => 'firebase-id-token' };
  const window = {
    SANKALP_BACKEND: 'https://backend.example',
    firebase: { auth: () => ({ get currentUser() { return active ? user : null; } }) }
  };
  const fetch = async (url, options) => {
    calls.push({ url, options });
    if (request) return request(url, options);
    return { ok: true, status: 200, json: async () => response || { approvals: [] } };
  };
  runInNewContext(source, {
    window, fetch, setTimeout, clearTimeout, AbortController, console,
    URL, Uint8Array
  });
  return { api: window.IndooneChatApi, calls, setUid: next => { active = next; } };
}

test('read-only approval list uses existing Firebase bearer and no-store GET', async () => {
  const run = { run_id: uuid, tool: 'github_delete_branch', payload: '{"branch":"old"}', status: 'pending' };
  const { api, calls } = apiHarness({ response: { approvals: [run] } });
  const items = await api.listAgentApprovals();
  assert.equal(items.length, 1);
  assert.equal(items[0].run_id, uuid);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://backend.example/api/agent/approvals');
  assert.equal(calls[0].options.method, 'GET');
  assert.equal(calls[0].options.cache, 'no-store');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer firebase-id-token');
});

test('details must use exact UUID; never a client-provided URL path', async () => {
  const { api, calls } = apiHarness({ response: { run_id: uuid, status: 'pending' } });
  await api.getAgentApproval(uuid);
  assert.equal(calls[0].url, 'https://backend.example/api/agent/runs/' + uuid + '/approval');
  assert.throws(() => api.getAgentApproval('../other?run=1'), /Invalid agent run identifier/);
  assert.equal(calls.length, 1);
});

test('decision is exactly one POST, with only approve or deny payload', async () => {
  const { api, calls } = apiHarness({ response: { run_id: uuid, status: 'denied' } });
  await api.decideAgentApproval(uuid, 'deny');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.method, 'POST');
  assert.deepEqual(JSON.parse(calls[0].options.body), { decision: 'deny' });
  assert.equal(calls[0].options.headers.Authorization, 'Bearer firebase-id-token');
  assert.throws(() => api.decideAgentApproval(uuid, 'approve;delete'), /Invalid approval decision/);
  assert.equal(calls.length, 1);
});

test('uncertain network outcome does not automatically resend approval', async () => {
  const { api, calls } = apiHarness({
    request: async () => { throw new TypeError('network failed'); }
  });
  await assert.rejects(() => api.decideAgentApproval(uuid, 'approve'));
  assert.equal(calls.length, 1);
});

test('signed out accounts cannot even list pending approvals', async () => {
  const { api, calls } = apiHarness({ uid: '' });
  await assert.rejects(() => api.listAgentApprovals(), /sign in/);
  assert.equal(calls.length, 0);
});

class Element {
  constructor(tag = 'div') {
    this.tagName = tag; this.children = []; this.listeners = {};
    this.parentNode = null; this.disabled = false; this.hidden = false;
    this.className = ''; this.textContent = ''; this.attributes = {};
    this.open = false;
  }
  append(...children) { children.forEach(node => { node.parentNode = this; this.children.push(node); }); }
  replaceChildren(...nodes) { this.children = []; this.append(...nodes); }
  remove() { if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(child => child !== this); }
  setAttribute(k, v) { this.attributes[k] = v; }
  addEventListener(type, callback) { (this.listeners[type] ||= []).push(callback); }
  click() { if (this.disabled) return; for (const cb of this.listeners.click || []) cb(); }
  querySelector(selector) {
    if (selector === '.chat-composer') return this.children.find(x => x.className === 'chat-composer');
    const queue = [...this.children];
    while (queue.length) {
      const item = queue.shift();
      if (selector.startsWith('.') && item.className.split(' ').includes(selector.slice(1))) return item;
      queue.push(...item.children);
    }
    return null;
  }
  insertBefore(node, before) {
    node.parentNode = this;
    const at = this.children.indexOf(before);
    this.children.splice(at < 0 ? this.children.length : at, 0, node);
  }
  showModal() { this.open = true; }
  close() { this.open = false; }
}

async function flush() {
  for (let i = 0; i < 16; i++) await Promise.resolve();
}

test('review UI demands fresh detail, displays payload as text, and never double-posts', async () => {
  const summary = {
    run_id: uuid, tool: 'github_delete_branch',
    payload: '{"branch":"<unsafe markup>"}',
    status: 'pending', expires_at: '2999-01-01T00:00:00+00:00'
  };
  const home = new Element(); const composer = new Element();
  composer.className = 'chat-composer'; home.append(composer);
  const body = new Element('body');
  const uid = { value: 'alice' };
  const counters = { list: 0, details: 0, post: 0 };
  const api = {
    listAgentApprovals: async () => { counters.list++; return [summary]; },
    getAgentApproval: async () => { counters.details++; return { ...summary }; },
    decideAgentApproval: async () => { counters.post++; return { status: 'completed' }; }
  };
  const window = {
    IndooneChatApi: api,
    firebase: { auth: () => ({
      get currentUser() { return uid.value ? { uid: uid.value } : null; },
      onAuthStateChanged: () => {}
    }) }
  };
  const document = {
    body, hidden: false, createElement: tag => new Element(tag),
    getElementById: name => name === 'homeContent' ? home : null,
    addEventListener: () => {}
  };
  runInNewContext(
    readFileSync('web/users/home/approvals/approval-review.js', 'utf8'),
    { window, document, console, Date, Number }
  );
  window.IndooneAgentApprovals.init();
  await flush();
  assert.equal(counters.list, 1);
  assert.equal(counters.post, 0);
  const panel = home.querySelector('.agent-approvals');
  assert.equal(panel.hidden, false);
  panel.querySelector('.agent-approval-open').click();
  await flush();
  assert.equal(counters.details, 1);
  assert.equal(counters.post, 0);
  const dialog = body.children.find(node => node.tagName === 'dialog');
  assert.equal(dialog?.open, true);
  assert.equal(dialog.querySelector('.agent-approval-payload').textContent, summary.payload);
  const approve = dialog.querySelector('.agent-approval-confirm');
  approve.click(); approve.click();
  await flush();
  assert.equal(counters.post, 1);
  assert.equal(approve.disabled, true);
});

test('account switch before review does not POST any write', async () => {
  const summary = { run_id: uuid, tool: 'github_delete_branch',
    payload: '{}', status: 'pending', expires_at: '2999-01-01T00:00:00+00:00' };
  const home = new Element(), composer = new Element(); composer.className = 'chat-composer'; home.append(composer);
  const body = new Element('body');
  let current = 'alice', posts = 0;
  const window = {
    IndooneChatApi: {
      listAgentApprovals: async () => [summary],
      getAgentApproval: async () => ({ ...summary }),
      decideAgentApproval: async () => { posts++; }
    },
    firebase: { auth: () => ({ get currentUser() { return current ? { uid: current } : null; }, onAuthStateChanged: () => {} }) }
  };
  const document = { body, hidden: false, createElement: tag => new Element(tag),
    getElementById: n => n === 'homeContent' ? home : null, addEventListener: () => {} };
  runInNewContext(
    readFileSync('web/users/home/approvals/approval-review.js', 'utf8'),
    { window, document, console, Date, Number }
  );
  window.IndooneAgentApprovals.init();
  await flush();
  current = 'bob';
  home.querySelector('.agent-approval-open').click();
  await flush();
  assert.equal(posts, 0);
  assert.equal(body.children.filter(el => el.tagName === 'dialog').length, 0);
});
