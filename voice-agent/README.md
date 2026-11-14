# Purnima Bengali LiveKit Voice Agent

This service is the server-side voice agent dispatched by the Android app as
`purnima-voice-agent`. It uses LiveKit Agents, Gnani streaming STT/TTS with
the Bengali `bn-IN` locale, and a LiveKit Inference LLM.

## Required secrets

Configure these values in the agent host's secret manager. Do not commit real
credentials or put the LiveKit API secret in the Android app.

- `LIVEKIT_URL`: LiveKit Cloud WebSocket URL.
- `LIVEKIT_API_KEY` and `LIVEKIT_API_SECRET`: server-side LiveKit credentials.
- `GNANI_API_KEY`: Gnani Vachana key for Bengali speech services.

Optional:
- `GNANI_TTS_VOICE`: Gnani timbre-v2.5 voice name (default: `Nalini`).
- `PURNIMA_LLM_MODEL`: LiveKit Inference model (default: `google/gemma-4-31b-it`).

## Run locally

1. Install [uv](https://docs.astral.sh/uv/).
2. Copy `.env.example` to `.env` and fill in secrets locally.
3. Run:

```bash
uv sync
uv run python agent.py download-files
uv run python agent.py dev
```

Use the LiveKit Agents deployment guide for production deployment:
https://docs.livekit.io/deploy/agents/

## Android client connection

The Android Voice screen requests a token with agent name
`purnima-voice-agent`. Configure the app build with a token endpoint:

```bash
gradle assembleDebug -PlivekitTokenEndpoint=https://YOUR_AUTHENTICATED_BACKEND/getToken
```

Or, for local prototyping only, configure a LiveKit Development Token Server ID:

```bash
gradle assembleDebug -PlivekitDevTokenServerId=YOUR_DEV_TOKEN_SERVER_ID
```

**Important:** the development token server is intentionally insecure and is
only for prototyping. Production must use an authenticated token endpoint.
The endpoint must generate a participant token and pass the incoming
`room_config` through to the LiveKit access-token builder so the agent
dispatch request is preserved. See:
https://docs.livekit.io/frontends/build/authentication/endpoint/

No API key or API secret belongs in the APK.
