# Sankalp

Sankalp is a Firebase-backed TOTP authenticator prototype.

## Storage and security

- Firebase Authentication is used for the Sankalp user login session.
- Authenticator accounts are stored under `users/<uid>/accounts` in Firebase Realtime Database.
- TOTP codes are generated in the app from the stored TOTP secret and are not persisted as generated codes.
- The previous local encrypted account vault is no longer used for account storage; legacy local vault keys are cleared by the app.
- Firebase Realtime Database rules should restrict each user's account data to their own Firebase UID.

Native Android camera and biometric integration are included separately from the web UI.
