# Sankalp

Sankalp is a Firebase-backed TOTP authenticator prototype.

## Storage and security

- Firebase Authentication is used for the Sankalp user login session.
- Authenticator accounts are stored under `users/<uid>/accounts` in Firebase Realtime Database.
- TOTP codes are generated in the app from the stored TOTP secret and are not persisted as generated codes.
- The previous local encrypted account vault is no longer used for account storage; legacy local vault keys are cleared by the app.
- Firebase Realtime Database rules should restrict each user's account data to their own Firebase UID.

Native Android camera and biometric integration are included separately from the web UI.

## Which folder belongs to whom

Opening this folder shows a lot of names, and one of them is not the app. That is the whole
list:

**The app** - everything below is served at `/app/` and is what a normal person uses:

    index.html          the app itself
    home/  menu/  settings/  lobby/  vibe/  legal/
    authentication/  login/  signup/
    shared/  assets/  branding/  style.css  config.js
    update.json  robots.txt  sitemap.xml  favicon.ico

**Not the app** - this one is for developers:

    developers/         the public API page, the docs, and the console

The two never load each other. The app does not link into `developers/`, and nothing under
`developers/` is loaded by `index.html`. If `developers/` were deleted tomorrow, the app would
still open and sign in exactly as it does now.

A note on moving things: the app's pages refer to `menu/`, `shared/` and `assets/` by path from
`/app/`, in 42 HTML files, 50 stylesheets and 16 scripts, and the deployed address for every
user is `/app/`. Moving the app into a subfolder means rewriting all of those and moving the
public URL, so it is its own job - not a tidy-up.
