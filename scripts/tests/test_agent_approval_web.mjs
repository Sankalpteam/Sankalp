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


test('explicit agent send includes exactly one stable turn id and agent mode', async () => {
  const turnId = 'turn_1234567890abcdef1234567890abcdef';
  const { api, calls } = apiHarness({
    response: {
      conversation_id: uuid, reply: 'Awaiting approval', action_chips: [],
      agent_run_id: uuid, agent_status: 'awaiting_approval'
    }
  });
  const output = await api.send({
    message: 'count words: hi', conversationId: uuid,
    executionMode: 'langgraph', clientTurnId: turnId
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://backend.example/api/chat');
  const payload = JSON.parse(calls[0].options.body);
  assert.equal(payload.execution_mode, 'langgraph');
  assert.equal(payload.client_turn_id, turnId);
  assert.equal(output.agentRunId, uuid);
  assert.equal(output.agentStatus, 'awaiting_approval');
  await assert.rejects(api.send({
    message: 'hi', executionMode: 'langgraph', clientTurnId: '../untrusted'
  }), /stable turn ID/);
  assert.equal(calls.length, 1);
});

test('default chat request retains existing payload and does not opt in', async () => {
  const { api, calls } = apiHarness({
    response: { conversation_id: uuid, reply: 'Normal reply' }
  });
  await api.send({ message: 'hi', conversationId: uuid });
  const payload = JSON.parse(calls[0].options.body);
  assert.equal(payload.message, 'hi');
  assert.equal(payload.conversation_id, uuid);
  assert.equal(Object.hasOwn(payload, 'execution_mode'), false);
  assert.equal(Object.hasOwn(payload, 'client_turn_id'), false);
});

test('agent mode capability fails closed when unsupported or unauthenticated', async () => {
  const on = apiHarness({ response: { enabled: true } });
  assert.equal(await on.api.chatAgentModeEnabled(), true);
  const off = apiHarness({ response: { enabled: false } });
  assert.equal(await off.api.chatAgentModeEnabled(), false);
  const noSession = apiHarness({ uid: '' });
  assert.equal(await noSession.api.chatAgentModeEnabled(), false);
  assert.equal(noSession.calls.length, 0);
});

test('agent-mode opt-in remains hidden until authenticated server supports it', async () => {
  const checkbox = new Element('input');
  checkbox.checked = false;
  const label = new Element('label');
  label.hidden = true;
  let uid = 'alice', enabled = false;
  const window = {
    firebase: { auth: () => ({
      get currentUser() { return uid ? { uid } : null; },
      onAuthStateChanged: () => {}
    }) },
    IndooneChatApi: { chatAgentModeEnabled: async () => enabled }
  };
  const document = {
    getElementById: id => id === 'agentModeControl' ? label
      : id === 'agentModeNextTurn' ? checkbox : null
  };
  runInNewContext(
    readFileSync('web/users/home/agent-mode.js', 'utf8'),
    { window, document, console }
  );
  window.IndooneAgentMode.init();
  await flush();
  assert.equal(label.hidden, true);
  assert.equal(window.IndooneAgentMode.selected(), false);
  enabled = true;
  await window.IndooneAgentMode.refresh();
  assert.equal(label.hidden, false);
  checkbox.checked = true;
  assert.equal(window.IndooneAgentMode.selected(), true);
  window.IndooneAgentMode.clear();
  assert.equal(window.IndooneAgentMode.selected(), false);
  uid = 'bob';
  assert.equal(window.IndooneAgentMode.selected(), false);
  await window.IndooneAgentMode.refresh();
  assert.equal(checkbox.checked, false);
});


test('web chat cache is scoped to the active Firebase identity, even when offline', async () => {
  const file = readFileSync('web/users/home/message/chat-store.js', 'utf8');
  const cache = new Map();
  let owner = 'account-one';
  const localStorage = {
    getItem: key => cache.get(key) ?? null,
    setItem: (key, value) => cache.set(key, String(value)),
    removeItem: key => cache.delete(key),
  };
  const window = {
    IndooneFirebase: { auth: { get currentUser() { return owner ? { uid: owner } : null; } } },
    IndooneCloudChat: { loadLatest: async () => { throw new Error('offline'); } },
    crypto: { randomUUID: () => '12345678-1234-4234-a234-1234567890ab' },
  };
  runInNewContext(file, { window, localStorage, console });
  const store = window.IndooneChatStore;
  store.add({ text: 'first account private chat', mine: true });
  assert.ok(cache.has('indoone_chat_thread_v1:account-one'));

  owner = 'account-two';
  assert.equal(store.getMessages().length, 0);
  store.add({ text: 'second account private chat', mine: true });
  assert.ok(cache.has('indoone_chat_thread_v1:account-two'));

  owner = 'account-one';
  await store.load();
  assert.equal(store.getMessages().length, 1);
  assert.equal(store.getMessages()[0].text, 'first account private chat');
  owner = null;
  assert.equal(store.getMessages().length, 0);
});

test('in-flight chat cannot leak to a second account or be auto-replayed', () => {
  const file = readFileSync('web/users/home/message/inflight.js', 'utf8');
  const cache = new Map();
  let owner = 'account-one';
  const localStorage = {
    getItem: key => cache.get(key) ?? null,
    setItem: (key, value) => cache.set(key, String(value)),
    removeItem: key => cache.delete(key),
  };
  const window = {
    IndooneFirebase: { auth: { get currentUser() { return owner ? { uid: owner } : null; } } },
  };
  runInNewContext(file, { window, localStorage, console });
  const inflight = window.IndooneInflight;
  inflight.begin({ text: 'write to GitHub', conversationId: 'thread-1', clientTurnId: 'turn_' + 'a'.repeat(32) });
  inflight.markDispatched();
  assert.equal(inflight.pending().wasDispatched, true);
  assert.equal(inflight.pending().clientTurnId, 'turn_' + 'a'.repeat(32));

  owner = 'account-two';
  assert.equal(inflight.pending(), null);
  owner = null;
  assert.equal(inflight.pending(), null);
  owner = 'account-one';
  assert.equal(inflight.pending().wasDispatched, true);
  inflight.finish();
  assert.equal(inflight.pending(), null);
});

test('web sender does not replay a failed tool stream as a blocking POST', async () => {
  const file = readFileSync('web/users/home/send/send.js', 'utf8');
  const messages = [];
  let streamCalls = 0;
  let blockingCalls = 0;
  let dispatchMarkers = 0;
  let clearedMarkers = 0;
  const uid = 'account-one';
  const uuid = () => '12345678-1234-4234-a234-1234567890ab';
  const input = { value: 'create my GitHub issue', style: { height: '' } };
  const document = {
    getElementById: id => ({
      chatInput: input,
      sendBtn: { querySelector: () => null },
      chatWelcome: { toggleAttribute: () => {} },
    })[id] || null,
  };
  const store = {
    add: item => { messages.push(item); return item; },
    remove: item => { const index = messages.indexOf(item); if (index >= 0) messages.splice(index, 1); },
    getMessages: () => messages,
    ensureConversationId: () => 'thread-1',
    setConversationId: () => {},
  };
  const window = {
    firebase: { auth: () => ({ currentUser: { uid } }) },
    IndooneChatStore: store,
    IndooneAgentMode: { selected: () => false },
    IndooneMessages: { render: () => {} },
    IndooneChatScroll: { toBottom: () => {} },
    IndooneChatHome: { syncComposer: () => {} },
    IndooneCloudChat: { appendMessage: async () => {} },
    IndooneInflight: {
      begin: () => {},
      markDispatched: () => { dispatchMarkers += 1; },
      finish: () => { clearedMarkers += 1; },
    },
    IndooneChatApi: {
      streamMessage: async ({ clientTurnId }) => {
        assert.equal(clientTurnId, 'turn_' + uuid().replaceAll('-', ''));
        streamCalls += 1;
        throw new Error('connection lost after provider dispatch');
      },
      send: async () => { blockingCalls += 1; throw new Error('unsafe fallback'); },
    },
  };
  runInNewContext(file, {
    window, document, crypto: { randomUUID: uuid }, console,
    setTimeout: callback => { callback(); return 0; },
    AbortController, URL: { revokeObjectURL: () => {} },
  });
  await window.IndooneSend.send();
  assert.equal(streamCalls, 1);
  assert.equal(blockingCalls, 0);
  assert.equal(dispatchMarkers, 1);
  assert.equal(clearedMarkers, 0);
  assert.equal(messages.length, 2);
  assert.match(messages[1].text, /outcome could not be confirmed/i);
});
