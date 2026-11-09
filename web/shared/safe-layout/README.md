# Sankalp Safe Layout

This folder is the single home for the device-agnostic safe-layout architecture.

## Architecture

Android `WindowInsets` is the runtime source of truth for native Android builds. The values are exposed to the WebView through CSS custom properties. Browser/WebView `env(safe-area-inset-*)` values remain available as a fallback.

## Migration policy

- Preserve existing app functionality.
- Do not add Android-version-specific pixel hacks.
- Move layout responsibilities here gradually and verify each step.
- Remove legacy/duplicate layout rules only after equivalent behavior is confirmed.

## Planned files

- `safe-layout.css` — shared CSS contract and safe-area layout primitives.
- `safe-layout.js` — WebView-side runtime hook for safe-layout values.
