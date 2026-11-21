# Sankalp — Open-source chat release manual gates

**Development-only checklist.** Do not merge, enable opt-in feature flags or deploy to production merely because CI is green. Use dedicated QA accounts and a disposable GitHub repository/branch. Record evidence (test date, APK/web build SHA, server SHA, masked logs); never include personal email contents, auth tokens or API keys.

## 1. Prerequisites (manual actions)

- [ ] Identify the exact Firebase project and ruleset used by the QA Web and Android clients. **Do not guess IDs or download credentials into Git.**
- [ ] Review deployed Firestore rules for `conversations/{conversationId}` and `messages`: only the authenticated owner may create, read, update, or delete. In particular, an existing document's `userId` must never be mutable by another account. Test the rules in the Firebase emulator or console with two QA users.
- [ ] Apply and verify the **collection-scoped** composite index described in `config/firebase/firestore.indexes.json` (`conversations`: `userId` ASC, `updatedAt` DESC). That file is an index definition, **not** an automatic deployment. Check index is ready before interpreting newest-first history tests.
- [ ] Ensure Android debug signing and OAuth configuration are valid for QA. Do not expose Firebase service account keys, signing stores or test-mail secrets.
- [ ] Confirm staging backend and client URLs, supported Gemini/Groq permissions, and the connected test-account scopes for any service being tested.
- [ ] Keep `INDOONE_CHAT_LANGGRAPH_ENABLED=false`, `INDOONE_CHAT_LANGGRAPH_WEB_RESEARCH_ENABLED=false` and `INDOONE_CHAT_LANGGRAPH_ZOHO_READ_ENABLED=false` until their individual test gates are met.

## 2. Web and Android — account and history parity

- [ ] Sign in as QA user A; create a conversation, rename it, send another user turn and receive an assistant reply. The manually chosen title must remain unchanged.
- [ ] Create over 50 conversations, then open the latest on Web and Android. Verify the newest thread is selected (not an arbitrary Firestore page).
- [ ] Create a QA conversation with at least 501 messages (preferably 905) and delete it. Verify Firestore child messages, parent record, Realtime Database index and backend history. Do **not** repeat on actual user data.
- [ ] Sign out of A and sign in as QA user B on the same browser/device. A's cache, pending drafts, history and real-time index must not appear under B. Test switching accounts while history loads.
- [ ] With A signed in, try to open/rename/delete B's conversation ID. All must be denied. Check the **server rules**, not only the client message.
- [ ] Kill/restart Android and refresh Web after a normal read turn and after an interrupted stream. Verify no silently duplicated or auto-replayed provider operation.

## 3. Connected tools and approval

- [ ] With opt-in LangGraph enabled only on a disposable QA backend, check read-only native tools and a user-owned GitHub read. Denied connector permissions must fail closed.
- [ ] Validate the human approval interrupt, payload review, expiration, deny, owner switch and **one-time** approval on a disposable provider action.
- [ ] Interrupt the response after a server-side write. The Web and Android clients must **not** send a second blocking request or WorkManager replay.
- [ ] For the separate public research adapter, test source sanitization and reliable links/citations against real provider results before any rollout.
- [ ] For the separate Zoho inbox adapter, use a dedicated test mailbox: an explicitly requested listing may show up to five sanitized headers. Confirm missing OAuth/scope, another user, send, delete, draft, Gmail, and mixed-provider requests never trigger this adapter. Inspect the agent audit for absence of mail-header content.
- [ ] Do not enable arbitrary MCP servers. Audit each trusted server, schema, user mapping, scopes, timeout and write approval in isolation.

## 4. Release acceptance

- [ ] Frontend Web contract checks and signed Android APK build are green on the **final** branch HEAD.
- [ ] Backend CI, Postgres migration checks and security tests are green on the **final** branch HEAD.
- [ ] Verify staging host memory/CPU/disk headroom first, especially the shared **2 GiB** AWS instance. Do not install unapproved daemons or run concurrent load tests against the live host.
- [ ] Record a tested rollback, backup/restore route, and DB migration compatibility before merging.
- [ ] Confirm the implementation parity for Web, Android, chat history, connected-tool authorization and pending human approvals. Production feature flags stay OFF until explicit release approval.

## Currently out of scope

Developer API key sales, wallet top-up, billing and monetization; destructive cleanup of unverified legacy files; autonomous multi-write resume without a durable verified checkpoint.
