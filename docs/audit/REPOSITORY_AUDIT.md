# Repository Audit — Brahma AI Evo

Phase 1 audit. Scope: the repository at commit `c8d9c0f` plus local commits `4927b91` and `dd9659b`.

## How this audit was done

- Listed all 334 tracked files and counted them by type (193 Python, 16 Kotlin, 24 JavaScript, 10 TypeScript, 14 JSON, 9 Markdown, and others).
- Read the entry point and core modules, the Brahma Connect gateway, the dashboard HTTP server, the Android companion's command handler, manifest, and accessibility service.
- Traced the pairing and authentication path end to end.
- Attempted to run the Brahma Connect test suite.

**Coverage limit:** Not every one of the 193 Python files was read line by line. Modules outside the gateway, dashboard, and Android companion are listed by name and size only, and are marked accordingly in `EXISTING_FEATURES.md`.

## Languages and frameworks

| Area | Technology |
|---|---|
| Desktop app | Python, PyQt6 + WebEngine (`ui.py`, `main.py`) |
| AI | Google Gemini (`llm_client.py`, `or_client.py`, `core/local_brain.py`) |
| Gateway | Python, FastAPI + Uvicorn WebSocket (`brahma_connect/`) |
| Dashboard | Python FastAPI + HTML/JS (`dashboard/`) |
| Android companion | Kotlin, Jetpack Compose (`brahma-connect-android/`) |
| Spotify | MCP server, TypeScript (`actions/spotify_mcp_server/`) |
| Build | PowerShell and batch scripts for Windows; GitHub Actions workflow |

## Critical findings

### 1. Unauthenticated pairing bypass on the local network (CRITICAL)

The gateway binds to `0.0.0.0` by default (`brahma_connect/gateway/server.py`, `BrahmaGatewayConfig.host`).

Chain of events that a device on the same network can trigger without any credential:

1. `GET /gateway/pair` has no authentication and returns a full `PairingOffer`, including `pairing_token` and `pairing_code`.
2. Opening the WebSocket `/ws` and sending a `hello` message creates a pending pairing request. The `hello` branch does not check any pairing code or token.
3. `POST /gateway/pending/{id}/approve` has no authentication. Approving the request creates a device record and sends `device_secret` back over that WebSocket.

Result: any host on the LAN can obtain a paired device credential without the user ever approving it on the PC, provided the approve endpoint is called. Because the approve endpoint is also unauthenticated, the attacker can call it directly.

The same unauthenticated group also includes `GET /gateway/devices`, `GET /gateway/logs`, `GET /gateway/pending`, `POST /gateway/devices/{id}/revoke`, and `POST /gateway/devices/{id}/forget`.

This violates the directive's rule: "Do not expose unauthenticated administrative endpoints."

### 2. Phone unlock was a fake success (FIXED, commit `dd9659b`)

`BrahmaAccessibilityService.unlockPhone()` returned `true` without doing anything, and `unlock_phone` reported success. It now returns `UNSUPPORTED`.

### 3. The dashboard API is authenticated (OK)

`dashboard/server.py` checks `_auth(req)` on `/api/command`, `/api/wake`, `/api/action`, and `/api/revoke-devices`. `/api/device-login` requires a previously issued device token.

## Tests

**Not executed.** The Brahma Connect tests fail at collection because `fastapi` is not installed in this environment, and package installation is blocked. This means no test in this audit has been verified as passing.

## Build

**Not executed.** The Android project needs the Android SDK and Gradle, which are not available here. The Kotlin changes in `4927b91` and `dd9659b` have not been compiled.

## Git state

- Branch `main` has 2 local commits not pushed to `origin`.
- Push is blocked: the session has no authorization for `gufyyyyyg356667-eng/brahma-ai-evo`.
