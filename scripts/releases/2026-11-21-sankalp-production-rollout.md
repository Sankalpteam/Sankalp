# Sankalp production release checkpoint — 2026-11-21

This record accompanies the reviewed fast-forward of `Sankalpteam/Sankalp` main to
`8cc484ab7ff6f73e6424f5064379cd13c817c0d6`.

- Previous web main: `7d7c84349b45624f5a9ed9460f1ff2482fb02cef`
- Preserved web backup ref: `backup/main-before-agent-approval-ui-20261121`
- Backend release commit: `d1d27b9d7f6dc39a96f665d4caeba1af46f96f69`
- Backend backup ref: `backup/main-before-architecture-20261118`
- New agent approval and LangGraph features remain opt-in / feature-flag gated.
- Web deployment must be checked in GitHub Pages Actions and tested in a real browser.
- Android release distribution is separate from the web Pages workflow.
- A code ref is not a backup of user databases, OAuth state, media or Firebase data.
- Backend database rollback requires a separate reviewed migration/restore plan:
  never run old code directly against revision `0006_prepaid_reserve`.
- Do not equate a successful web deployment with completed signed-in E2E QA.

This documentation-only change is under `scripts/`, which triggers the approved
`Deploy Indoone Web` GitHub Actions workflow for main.
