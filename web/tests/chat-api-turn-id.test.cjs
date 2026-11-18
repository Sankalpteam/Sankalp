"use strict";

const assert = require("node:assert/strict");
const { test } = require("node:test");
const fs = require("node:fs");
const vm = require("node:vm");

const source = fs.readFileSync("web/users/home/message/chat-api.js", "utf8");

function loadClient(fakeFetch) {
  const window = {
    SANKALP_BACKEND: "https://backend.test",
    firebase: { auth: () => ({ currentUser: { getIdToken: async () => "mock-user-token" } }) },
  };
  vm.runInNewContext(source, {
    window,
    fetch: fakeFetch,
    AbortController,
    TextDecoder,
    console,
    setTimeout,
    clearTimeout,
  }, { filename: "chat-api.js" });
  return window.IndooneChatApi;
}

test("blocking chat forwards a stable client turn id and auth token", async () => {
  let called = 0;
  const api = loadClient(async (url, options) => {
    called++;
    assert.equal(url, "https://backend.test/api/chat");
    assert.equal(options.headers.Authorization, "Bearer mock-user-token");
    const payload = JSON.parse(options.body);
    assert.deepEqual(payload, {
      message: "show my repositories",
      client_turn_id: "turn-web-001",
      conversation_id: "conversation-1",
    });
    return { ok: true, json: async () => ({ conversation_id: "conversation-1", reply: "Not connected" }) };
  });

  const result = await api.send({
    message: "show my repositories",
    conversationId: "conversation-1",
    clientTurnId: "turn-web-001",
  });
  assert.equal(result.reply, "Not connected");
  assert.equal(called, 1);
});

test("streaming chat sends the same turn id in its JSON body", async () => {
  const chunks = [
    'data: {"type":"meta","conversation_id":"conversation-1"}\n\n',
    'data: {"type":"delta","text":"Hello"}\n\n' +
      'data: {"type":"done","reply":"Hello","action_chips":[]}\n\n',
  ];
  let index = 0;
  const api = loadClient(async (url, options) => {
    assert.equal(url, "https://backend.test/api/chat/stream");
    const payload = JSON.parse(options.body);
    assert.equal(payload.client_turn_id, "turn-web-002");
    assert.equal(payload.conversation_id, "conversation-1");
    return {
      ok: true,
      body: {
        getReader: () => ({
          read: async () => index < chunks.length
            ? { value: Buffer.from(chunks[index++], "utf8"), done: false }
            : { done: true },
        }),
      },
    };
  });
  const result = await api.streamMessage({
    message: "hello",
    conversationId: "conversation-1",
    clientTurnId: "turn-web-002",
  });
  assert.equal(result.reply, "Hello");
});

test("failed stream never issues a second HTTP request within ChatApi", async () => {
  let calls = 0;
  const api = loadClient(async (_url, _options) => {
    calls++;
    return { ok: false, status: 503 };
  });
  await assert.rejects(
    api.streamMessage({
      message: "create github branch",
      conversationId: "conversation-1",
      clientTurnId: "turn-web-003",
    }),
    /stream failed/,
  );
  assert.equal(calls, 1);
});
