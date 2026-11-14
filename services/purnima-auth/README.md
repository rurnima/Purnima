# Purnima Device Authentication

This service provides silent, per-installation authentication. It does not show a login form and does not ask the user to paste tokens, API keys, issuer URLs, or credentials.

## Flow
1. The Android app creates a P-256 key pair in Android Keystore. The private key is non-exportable.
2. The app registers only its public key and stores the returned device ID locally.
3. When an API session is needed, the app requests a one-time challenge and signs it with the Keystore private key.
4. This Worker verifies the proof and issues a 15-minute RS256 access token.
5. Knowledge Access verifies the token using this service's public JWKS. The JWT subject is the registered device ID.

No long-lived bearer token is stored in the Android app. It can request another short-lived token by signing a fresh challenge.

## Automatic signing-key provisioning
On the first JWKS or token request, the Worker generates an RSA-2048 signing key pair automatically. It persists the PKCS#8 private key and public JWK in the dedicated D1 `auth_keys` table; the key is never placed in source code, app preferences, or a user-facing setup screen. D1 provides encryption at rest and the Worker is the only application component bound to this database. The key ID is stable across deployments.

## API
- POST /v1/device/register — JSON public P-256 JWK
- POST /v1/device/challenge — JSON deviceId
- POST /v1/device/token — JSON deviceId, challenge, timestamp and signature
- GET /.well-known/jwks.json — public RS256 signing key
- GET /health

The signature is over deviceId.challenge.timestamp. Challenges are single-use and expire after five minutes. Access tokens expire after 15 minutes.

## Identity boundary
This is a device identity, not an email/phone/Google account. A new installation creates a new isolated identity; reinstalling the app or changing devices does not automatically recover the old device identity. Account recovery and multi-device linking require a separate product decision. No login credentials or user-supplied secrets are required for this device-bound flow.

## Deployment
Apply migrations `0001_devices.sql` and `0002_auth_signing_key.sql`, then deploy the Worker with its D1 binding. There are no user-provided or manually pasted secrets to configure.
