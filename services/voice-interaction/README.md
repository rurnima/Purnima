# Purnima Voice Interaction

This isolated service uses LiveKit Agents for real-time voice sessions. The Android client remains the native Purnima app; this PR does not create a separate voice app or Home Screen control.

## Current implementation

- Python LiveKit Agents entrypoint at `agent.py`.
- Bengali-first assistant instructions with language-following behavior.
- STT, LLM, and TTS model identifiers are environment configuration, not hard-coded provider choices.
- No API keys, LiveKit secrets, or room tokens are committed.

## Required environment

- `LIVEKIT_URL`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `PUR_NIMA_STT_MODEL`
- `PUR_NIMA_LLM_MODEL`
- `PUR_NIMA_TTS_MODEL`

Choose and test STT/TTS models that support Bengali well before production. Model availability, language coverage, latency, and price must be checked against current LiveKit Inference/provider documentation. Never silently fall back to an English-only voice for Bengali conversations.

## Android connection

Use the official LiveKit Android SDK in the existing Chat/Voice experience. A trusted backend must issue short-lived room tokens; never embed LiveKit API secrets or static tokens in the APK. Request microphone permission at runtime and connect only after user action.

The token endpoint and Android UI integration are intentionally separate follow-up tasks; this PR establishes the voice service boundary without changing Home or the Agent Runtime PR.

## Verification still required

- Add tests for session startup, Bengali language behavior, disconnect, and provider errors.
- Confirm selected STT/TTS model IDs and Bengali quality with real audio.
- Provision LiveKit credentials in the deployment secret store.
- Build and test the Android SDK integration in its own integration step.

## Official references

- [LiveKit Agents voice AI quickstart](https://docs.livekit.io/agents/start/voice-ai/)
- [LiveKit AgentSession](https://docs.livekit.io/agents/logic/sessions/)
- [LiveKit Android Compose quickstart](https://docs.livekit.io/transport/sdk-platforms/android-compose/)
