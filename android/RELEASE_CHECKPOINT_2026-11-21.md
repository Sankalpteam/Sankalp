# Terminal APK release checkpoint — 2026-11-21

This documentation-only checkpoint triggers `Android Build` on the
`terminal` branch after a fast-forward sync from `main`.

Source baseline: `0a33bb9a7be7c83e887a2c4dd26faa5b771c5750`.
Backend production release: `d1d27b9d7f6dc39a96f665d4caeba1af46f96f69`.

No Android source, resources, build configuration, package signature or
backend environment is modified by this checkpoint.

CI must assemble, sign, verify and publish the terminal APK release;
the release job updates `web/update.json` on `main` with the new
`versionCode` and download link. GitHub Pages must then redeploy that
updated manifest, since workflow-originated GitHub token commits do
not themselves trigger another Pages workflow.

Real-device installation, Firebase login, chat and tool E2E are
separate release checks and are not asserted by this file.
