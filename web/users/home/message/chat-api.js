// Talks to the same backend the native app uses. The native client sends a
// Firebase ID token as a bearer token; this does the same, so one account
// works across the app and the web.
(() => {
  // One place, not five: see /config.js. A page can override it before this loads.
    // config.js owns the address, and this is read when a request is made rather than when the
  // file loads: the modules load in a fixed order, and a copy captured at load time is how the
  // old host survived in five files after the shared one was fixed.
  function backendBase() {
    const value = String(window.SANKALP_BACKEND || '').replace(/\/+$/, '');

    if (!value) {
      console.error('Sankalp: config.js did not load, so the backend address is unknown.');
    }

    return value;
  }
  const READ_TIMEOUT_MS = 300000;
  const MAX_ATTACHMENTS = 10;
  const MAX_IMAGE_BYTES = 5000000;
  const MAX_DOCUMENT_BYTES = 8000000;
  const MAX_TOTAL_BYTES = 20000000;

  function firebaseUser() {
    return window.firebase?.auth?.().currentUser || null;
  }

  async function authToken() {
    const user = firebaseUser();

    if (!user) {
      throw new Error('Please sign in again.');
    }

    return user.getIdToken(false);
  }

  function bytesToBase64(bytes) {
    let binary = '';
    const chunk = 0x8000;

    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(
        null,
        bytes.subarray(i, i + chunk)
      );
    }

    return btoa(binary);
  }

  async function readFileBytes(file) {
    return new Uint8Array(await file.arrayBuffer());
  }

  function limitFor(mimeType) {
    return mimeType.startsWith('image/')
      ? MAX_IMAGE_BYTES
      : MAX_DOCUMENT_BYTES;
  }

  async function buildMediaPayload(attachments) {
    if (!attachments.length) {
      return null;
    }

    if (attachments.length > MAX_ATTACHMENTS) {
      throw new Error('You can attach up to 10 files per message.');
    }

    let totalBytes = 0;
    const media = [];

    for (const item of attachments) {
      const bytes = await readFileBytes(item.file);
      const mimeType = (item.file.type || 'application/octet-stream')
        .trim()
        .toLowerCase();
      const limit = limitFor(mimeType);

      if (bytes.length > limit) {
        // ChatApi.kt's own wording.
        throw new Error(
          mimeType.startsWith('image/')
            ? 'This attachment exceeds the 5 MB limit.'
            : 'This attachment exceeds the 8 MB limit.'
        );
      }

      totalBytes += bytes.length;

      if (totalBytes > MAX_TOTAL_BYTES) {
        throw new Error(
          'Attachments together exceed the 20 MB upload limit.'
        );
      }

      media.push({
        filename: item.file.name || 'attachment',
        mime_type: mimeType,
        content_base64: bytesToBase64(bytes)
      });
    }

    return media;
  }

  async function postJson(path, payload, signal) {
    const token = await authToken();
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(),
      READ_TIMEOUT_MS
    );

    if (signal) {
      signal.addEventListener('abort', () => controller.abort());
    }

    try {
      const response = await fetch(`${backendBase()}${path}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload),
        signal: controller.signal
      });

      if (!response.ok) {
        // The body is for the console, not for the person waiting for an answer.
        const detail = await response.text().catch(() => '');

        window.sankalpError?.report(
          new Error(`${response.status}: ${detail.slice(0, 300)}`),
          'chat request'
        );

        throw new Error(window.sankalpError?.fromStatus(response.status) || 'Something went wrong.');
      }

      return response.json();
    } catch (error) {
      // A fetch that never reached the server throws a TypeError with no
      // status. That is what a blocked cross-origin call looks like, and it
      // was being flattened into "could not reach" - which tells nobody
      // anything. Say which it is.
      if (error instanceof TypeError) {
        throw new Error(
          window.sankalpError?.fromError(error) ||
            'Sankalp could not reach the server. Check your connection and try again.'
        );
      }

      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  async function send({ message, conversationId, attachments = [], executionMode = 'default', clientTurnId = null, signal }) {
    const media = await buildMediaPayload(attachments);
    const payload = { message };
    if (executionMode === 'langgraph') {
      if (!/^turn_[a-f0-9]{32}$/i.test(String(clientTurnId || ''))) {
        throw new Error('Agent mode requires a fresh, stable turn ID.');
      }
      if (attachments.length) {
        throw new Error('Agent mode does not support attachments yet.');
      }
      payload.execution_mode = 'langgraph';
    }

    if (clientTurnId) {
      if (!/^turn_[a-f0-9]{32}$/i.test(String(clientTurnId))) {
        throw new Error('Invalid chat turn ID.');
      }
      payload.client_turn_id = clientTurnId;
    }

    if (conversationId) {
      payload.conversation_id = conversationId;
    }

    // The key is "attachments", not "media_attachments". ChatApi.kt builds the
    // same payload and names the array "attachments" (ChatApi.kt: put("attachments",
    // attachmentArray)); the backend reads that name. This page used to send
    // media_attachments, which the backend did not know, so a photo went up and
    // was ignored - the reply came back as if no photo had been sent.
    if (media && media.length) {
      payload.attachments = media;
    }

    const json = await postJson('/api/chat', payload, signal);

    if (!json.conversation_id) {
      throw new Error('Sankalp could not start this chat just now. Please try again.');
    }

    if (!json.reply) {
      throw new Error('Sankalp backend returned an empty reply.');
    }

    return {
      conversationId: json.conversation_id,
      reply: json.reply,
      // The pages a research answer used. The backend has always returned them and this page
      // dropped them, so a web answer arrived with no citations while the same answer in the
      // app carried its sources. A research answer without its sources is a claim without
      // evidence.
      sources: Array.isArray(json.sources) ? json.sources : [],
      // Follow-ups the backend offered with this reply. Empty for most answers; a held
      // write carries Confirm/Cancel so the user presses a button instead of typing a
      // word the reply had to spell out.
      chips: Array.isArray(json.action_chips) ? json.action_chips : [],
      agentRunId: typeof json.agent_run_id === 'string' ? json.agent_run_id : null,
      agentStatus: typeof json.agent_status === 'string' ? json.agent_status : null
    };
  }

  async function uploadFile(file) {
    const bytes = await readFileBytes(file);
    const json = await postJson('/api/files', {
      filename: file.name || 'file',
      content_base64: bytesToBase64(bytes)
    });

    return {
      fileId: json.file_id,
      filename: json.filename,
      bytes: json.bytes,
      textPreview: json.text_preview
    };
  }

  // The same pipeline as /api/chat, delivered as it is produced.
  //
  // The server sends `meta` (the conversation id), then `delta` events, then one `done`
  // that carries the finished reply and the chips it offered. Reading them here means the
  // caller can show the first words immediately and clear its "thinking" indicator on the
  // first delta instead of waiting for the whole answer.
  async function streamMessage({ message, conversationId, clientTurnId = null, signal, onMeta, onDelta }) {
    const token = await authToken();
    if (clientTurnId && !/^turn_[a-f0-9]{32}$/i.test(String(clientTurnId))) {
      throw new Error('Invalid chat turn ID.');
    }
    const payload = { message, conversation_id: conversationId };
    if (clientTurnId) payload.client_turn_id = clientTurnId;

    const response = await fetch(`${backendBase()}/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(payload),
      signal
    });

    if (!response.ok || !response.body) {
      throw new Error(`Sankalp stream failed (${response.status})`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let conversation = conversationId || '';
    let text = '';
    let final = null;

    while (true) {
      const { value, done } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });

      // Events are separated by a blank line; the last piece may be incomplete and stays
      // in the buffer until the next read completes it.
      const parts = buffer.split('\n\n');
      buffer = parts.pop() || '';

      for (const part of parts) {
        const line = part.split('\n').find(item => item.startsWith('data: '));

        if (!line) {
          continue;
        }

        let event;

        try {
          event = JSON.parse(line.slice('data: '.length));
        } catch {
          continue;
        }

        if (event.type === 'meta') {
          conversation = event.conversation_id || conversation;
          onMeta?.(conversation);
          continue;
        }

        if (event.type === 'delta' && event.text) {
          text += event.text;
          onDelta?.(event.text, text);
          continue;
        }

        if (event.type === 'done') {
          final = {
            conversationId: conversation,
            reply: event.reply || text,
            sources: Array.isArray(event.sources) ? event.sources : [],
            chips: Array.isArray(event.action_chips) ? event.action_chips : []
          };
        }
      }
    }

    // A stream that ends without its last event is not a reply. The caller falls back to
    // the blocking endpoint rather than showing half an answer as if it were whole.
    if (!final) {
      throw new Error('Sankalp stream ended without a reply');
    }

    return final;
  }

  async function chatAgentModeEnabled() {
    try {
      const result = await getApprovalJson('/api/chat/agent-mode');
      return result?.enabled === true;
    } catch (error) {
      // Older backends or feature-off deployments must not advertise the UI.
      return false;
    }
  }

  // LangGraph's /api/agent review flow is independent of /api/chat action chips.
  // All three calls use the SAME Firebase bearer identity as normal chat.
  function approvalPath(runId) {
    const id = String(runId || '').trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
      throw new Error('Invalid agent run identifier.');
    }
    return '/api/agent/runs/' + encodeURIComponent(id) + '/approval';
  }

  async function getApprovalJson(path) {
    const token = await authToken();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(backendBase() + path, {
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + token, 'Accept': 'application/json' },
        cache: 'no-store',
        signal: controller.signal
      });
      if (!response.ok) {
        const error = new Error('Could not load pending approvals.');
        error.status = response.status;
        throw error;
      }
      return response.json();
    } finally {
      clearTimeout(timer);
    }
  }

  async function listAgentApprovals() {
    const data = await getApprovalJson('/api/agent/approvals');
    return Array.isArray(data?.approvals) ? data.approvals : [];
  }

  function getAgentApproval(runId) {
    return getApprovalJson(approvalPath(runId));
  }

  // Writes NEVER retry on timeout or 409. A network failure can mean the
  // provider received the action; only a separate read may reconcile status.
  function decideAgentApproval(runId, decision) {
    if (decision !== 'approve' && decision !== 'deny') {
      throw new Error('Invalid approval decision.');
    }
    return postJson(approvalPath(runId), { decision });
  }

  window.IndooneChatApi = {
    send,
    streamMessage,
    uploadFile,
    chatAgentModeEnabled,
    listAgentApprovals,
    getAgentApproval,
    decideAgentApproval,
    backendUrl: backendBase()
  };
})();
