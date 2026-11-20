// A single, explicit opt-in for the NEXT chat turn only.
// Both backend and client default to legacy chat. This module cannot enable
// LangGraph server-side; it only shows the option if the authenticated backend
// says that the feature flag is ON.
(() => {
  let supported = false;
  let initialized = false;
  let owner = '';

  function input() {
    return document.getElementById('agentModeNextTurn');
  }
  function surface() {
    return document.getElementById('agentModeControl');
  }
  function activeUid() {
    return window.firebase?.auth?.().currentUser?.uid || '';
  }
  function hide() {
    supported = false;
    const check = input();
    if (check) check.checked = false;
    const control = surface();
    if (control) control.hidden = true;
  }
  async function refresh() {
    const uid = activeUid();
    owner = uid;
    if (!uid) {
      hide();
      return;
    }
    let available = false;
    try {
      available = (await window.IndooneChatApi?.chatAgentModeEnabled?.()) === true;
    } catch {
      available = false;
    }
    if (owner !== activeUid() || uid !== activeUid()) {
      hide();
      return;
    }
    supported = available;
    if (!available && input()) input().checked = false;
    if (surface()) surface().hidden = !available;
  }

  function selected() {
    return supported && !!input()?.checked && owner === activeUid();
  }
  function clear() {
    if (input()) input().checked = false;
  }
  function init() {
    if (initialized) return;
    if (!surface() || !input()) return;
    initialized = true;
    hide();
    void refresh();
    window.firebase?.auth?.().onAuthStateChanged?.(() => { hide(); void refresh(); });
  }
  window.IndooneAgentMode = { init, selected, clear, refresh };
})();
