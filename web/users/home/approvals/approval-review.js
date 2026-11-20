// Review-only UI for upstream LangGraph approvals. /api/chat confirmation chips
// are a separate workflow and MUST NOT be re-sent as /api/agent decisions.
(() => {
  let started = false;
  let loading = false;
  let panel = null;
  let list = null;
  let feedback = null;
  let dialog = null;
  let currentUid = '';
  let reviewLocked = false;

  function signedInUid() {
    return window.firebase?.auth?.().currentUser?.uid || '';
  }

  function textNode(tag, className, value) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    node.textContent = String(value || '');
    return node;
  }

  function canReview(item) {
    return !!item &&
      item.status === 'pending' &&
      typeof item.run_id === 'string' &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.run_id) &&
      typeof item.tool === 'string' && item.tool.trim().length > 0 &&
      typeof item.payload === 'string' &&
      typeof item.expires_at === 'string' &&
      Number.isFinite(Date.parse(item.expires_at)) &&
      Date.parse(item.expires_at) > Date.now();
  }

  function showError(message) {
    if (!feedback) return;
    feedback.textContent = message || '';
    feedback.hidden = !message;
  }

  function closeDialog() {
    if (!dialog) return;
    if (dialog.open) dialog.close();
    dialog.remove();
    dialog = null;
  }

  function resetForAccount() {
    closeDialog();
    if (list) list.replaceChildren();
    if (panel) panel.hidden = true;
    currentUid = signedInUid();
    showError('');
  }

  async function refresh() {
    if (loading || !panel) return;
    const uid = signedInUid();
    if (!uid) {
      resetForAccount();
      return;
    }
    if (currentUid !== uid) resetForAccount();
    currentUid = uid;
    loading = true;
    try {
      const api = window.IndooneChatApi;
      if (!api?.listAgentApprovals) return;
      const response = await api.listAgentApprovals();
      if (currentUid !== signedInUid() || uid !== signedInUid()) {
        resetForAccount();
        return;
      }
      const approvals = response.filter(canReview);
      list.replaceChildren();
      for (const item of approvals) {
        const row = textNode('div', 'agent-approval-item', '');
        const tool = textNode('span', 'agent-approval-tool', item.tool);
        const button = textNode('button', 'agent-approval-open', 'Review');
        button.type = 'button';
        button.setAttribute('aria-label', 'Review pending ' + item.tool + ' action');
        button.addEventListener('click', () => { void review(item); });
        row.append(tool, button);
        list.append(row);
      }
      panel.querySelector('.agent-approval-heading').textContent =
        'Pending agent approvals (' + approvals.length + ')';
      panel.hidden = approvals.length === 0;
      showError('');
    } catch (error) {
      if (currentUid !== signedInUid()) {
        resetForAccount();
        return;
      }
      // A transient read failure must not present stale items as actionable.
      list.replaceChildren();
      panel.hidden = true;
    } finally {
      loading = false;
    }
  }

  async function review(summary) {
    if (reviewLocked || !canReview(summary) || !signedInUid()) return;
    const owner = signedInUid();
    reviewLocked = true;
    try {
      const detail = await window.IndooneChatApi.getAgentApproval(summary.run_id);
      if (owner !== signedInUid()) {
        resetForAccount();
        return;
      }
      // Re-fetch and compare: never approve a substituted/modified payload.
      if (!canReview(detail) ||
          detail.run_id !== summary.run_id ||
          detail.tool !== summary.tool ||
          detail.payload !== summary.payload ||
          detail.expires_at !== summary.expires_at) {
        showError('This approval changed or expired. Refresh and review the new request.');
        await refresh();
        return;
      }
      renderDialog(detail, owner);
    } catch (error) {
      showError('Could not verify this action. No approval was sent.');
    } finally {
      reviewLocked = false;
    }
  }

  function renderDialog(action, owner) {
    closeDialog();
    dialog = document.createElement('dialog');
    dialog.className = 'agent-approval-dialog';
    dialog.setAttribute('aria-labelledby', 'agentApprovalDialogTitle');

    const heading = textNode('h2', '', 'Approve this action?');
    heading.id = 'agentApprovalDialogTitle';
    const note = textNode('p', '', 'Review the exact tool and data before deciding. Approval may change your connected account.');
    const toolLabel = textNode('strong', '', 'Tool');
    const tool = textNode('p', 'agent-approval-dialog-tool', action.tool);
    const payloadLabel = textNode('strong', '', 'Exact action payload');
    const payload = textNode('pre', 'agent-approval-payload', action.payload);
    const expires = textNode('p', 'agent-approval-expiry', 'Expires: ' + new Date(action.expires_at).toLocaleString());
    const status = textNode('p', 'agent-approval-status', '');
    status.setAttribute('role', 'status');
    status.hidden = true;

    const buttons = textNode('div', 'agent-approval-buttons', '');
    const cancel = textNode('button', '', 'Close');
    const deny = textNode('button', '', 'Deny');
    const approve = textNode('button', 'agent-approval-confirm', 'Approve once');
    [cancel, deny, approve].forEach(button => { button.type = 'button'; });
    buttons.append(cancel, deny, approve);
    dialog.append(heading, note, toolLabel, tool, payloadLabel, payload, expires, status, buttons);
    document.body.append(dialog);
    cancel.addEventListener('click', closeDialog);

    async function decide(decision) {
      if (reviewLocked || owner !== signedInUid()) {
        closeDialog();
        return;
      }
      reviewLocked = true;
      [cancel, deny, approve].forEach(button => { button.disabled = true; });
      try {
        // The server performs its own atomic claim and owner/checkpoint checks.
        // Exactly one POST, even if it times out; never auto-resend a write.
        const outcome = await window.IndooneChatApi.decideAgentApproval(action.run_id, decision);
        status.textContent = outcome.status === 'completed'
          ? 'Action completed and verified.'
          : outcome.status === 'denied'
            ? 'Action denied. Nothing was sent.'
            : 'Outcome needs review. Do not repeat this action until verified.';
        status.hidden = false;
      } catch (error) {
        status.textContent =
          'The decision status is uncertain. Do not submit again. Refresh to check its status.';
        status.hidden = false;
      } finally {
        reviewLocked = false;
        // Deliberately keep the action buttons disabled after EVERY attempt.
        // A second click would be a second remote write request.
        cancel.disabled = false;
        cancel.textContent = 'Close';
        await refresh();
      }
    }
    approve.addEventListener('click', () => { void decide('approve'); });
    deny.addEventListener('click', () => { void decide('deny'); });
    dialog.showModal();
  }

  function init() {
    if (started) return;
    const home = document.getElementById('homeContent');
    const composer = home?.querySelector('.chat-composer');
    if (!home || !composer) return;
    started = true;

    panel = textNode('section', 'agent-approvals', '');
    panel.hidden = true;
    panel.setAttribute('aria-label', 'Pending agent approvals');
    const top = textNode('div', 'agent-approval-top', '');
    const heading = textNode('strong', 'agent-approval-heading', 'Pending agent approvals');
    const reload = textNode('button', 'agent-approval-refresh', 'Refresh');
    reload.type = 'button';
    reload.addEventListener('click', () => { void refresh(); });
    top.append(heading, reload);
    list = textNode('div', 'agent-approval-list', '');
    feedback = textNode('p', 'agent-approval-error', '');
    feedback.setAttribute('role', 'alert');
    feedback.hidden = true;
    panel.append(top, list, feedback);
    composer.parentNode.insertBefore(panel, composer);
    currentUid = signedInUid();
    void refresh();

    // Review is read-only until a person explicitly clicks Approve / Deny.
    // Refresh on returning to the page; no background write or retry loop.
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) void refresh();
    });
    window.firebase?.auth?.().onAuthStateChanged?.(() => {
      resetForAccount();
      void refresh();
    });
  }

  window.IndooneAgentApprovals = { init, refresh };
})();
