# Fleet Pulse

A single page that reads `/health/kingdom` and says, in one line, whether the fleet is well.

It exists because agent 27 was producing telemetry that nobody was reading: the data was
already there, and a server that failed at night was found out by a user rather than by us.

Open it with `?api=https://your-backend-host` to point at another environment; it defaults to
the production host. No build step, no token — the endpoint is deliberately public so a monitor
without credentials can still report that the app is down.

What it shows:

- the overall roll-up: **HEALTHY**, **DEGRADED** or **CRITICAL**, with the summary line
- one card per pillar — provider circuit, host, database pool, WhatsApp quota, cost ledger
- the action chips the auditor emitted for that state
- the raw snapshot, folded away, for when the summary is not enough

It polls every 30 seconds. A red banner means the page could not reach the backend at all,
which is itself the most important thing it can tell you.
