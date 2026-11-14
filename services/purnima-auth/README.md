# Purnima Device Authentication

This service provides silent, per-installation authentication. It does not show a login form and does not ask the user to paste tokens, API keys, issuer URLs, or credentials.

## Flow
1. The Android app creates a P-256 key pair in Android Keystore. The private key is non-exportable.
2. The app registers only its public key and stores the returned device ID locally.
3. When an API session is needed, the app requests a one-time challenge and signs it with the Keystore private key.
4. This Worker verifies the proof and issues a 15-minute RS256 access token.
5. Knowledge Access verifies the token using this service's public JWKS. The JWT subject is the registered device ID.

No long-lived bearer token is stored in the Android app. It can request another short-lived token by signing a fresh challenge.

## API
- POST /v1/device/register — JSON public P-256 JWK
- POST /v1/device/challenge — JSON deviceId
- POST /v1/device/token — JSON deviceId, challenge, timestamp and signature
- GET /.well-known/jwks.json — public RS256 signing key
- GET /health

The signature is over deviceId.challenge.timestamp. Challenges are single-use and expire after five minutes. Access tokens expire after 15 minutes.

## Identity boundary
This is a device identity, not an email/phone/Google account. A new installation creates a new isolated identity; reinstalling the app or changing devices does not automatically recover the old device identity. Account recovery and multi-device linking require a separate product decision. No login credentials or user-supplied secrets are required for this device-bound flow.

## Deployment prerequisites
The Worker requires AUTH_PRIVATE_KEY_PKCS8 as a Cloudflare Worker secret and AUTH_PUBLIC_JWK as a non-secret Worker variable. Never commit the private key. The signing key must remain stable across deployments. Apply migrations/0001_devices.sql before deploying.
