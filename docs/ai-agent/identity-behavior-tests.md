# Identity behavior regression tests

## Purpose

These tests protect the identity instructions sent to the model. They exercise the real Worker request handler with a controlled Workers AI stub, so they run in CI without production credentials or model usage.

## Covered cases

- The same canonical Wife identity system instruction is sent for Chat and Voice.
- Persona-switch and prompt-injection attempts remain user-role input and cannot replace the system instruction.
- Proposed write/code-changing actions remain approval-gated and are never reported as executed.
- Unsupported channel values are rejected before the model is called.

## Important limitation

These are deterministic prompt-contract and request-boundary regression tests. They do **not** prove that a live language model will always obey the prompt. A separate opt-in live-model evaluation against the deployed Workers AI model is still needed to measure actual generated responses, including Bengali and English persona-switch attempts. Live evaluation must use a controlled test account, avoid production user data, and must not run on every pull request because it can consume model quota and produce nondeterministic results.
