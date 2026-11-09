# Existing Features — Classification

Status key: `IMPLEMENTED AND TESTED` · `IMPLEMENTED BUT UNTESTED` · `PARTIALLY IMPLEMENTED` · `MISSING` · `BROKEN`

No test in this repository has been run in this audit (see `REPOSITORY_AUDIT.md`). So **no feature is marked `IMPLEMENTED AND TESTED`**.

## Desktop (PC)

| Feature | Evidence | Status |
|---|---|---|
| Main app window and UI | `ui.py` (large), `main.py` | IMPLEMENTED BUT UNTESTED |
| Gemini voice and tool calling | `main.py` tool declarations, `llm_client.py` | IMPLEMENTED BUT UNTESTED |
| Intent dispatch | `core/intent_dispatcher.py` (1051 lines) | IMPLEMENTED BUT UNTESTED — test file exists (`tests/test_intent_dispatcher.py`) but not run |
| Local brain | `core/local_brain.py` | IMPLEMENTED BUT UNTESTED — `test_local_brain.py` at root, not run |
| Memory cortex | `core/memory_cortex.py` | IMPLEMENTED BUT UNTESTED |
| Learned rules | `core/learned_rules.py` | IMPLEMENTED BUT UNTESTED |
| Self-heal / auto-repair | Described in README; `test_self_heal.py` at root. No dedicated `core/` module found with that name | PARTIALLY IMPLEMENTED — implementation location not confirmed |
| Skill forge / crucible (self-written tools) | `core/skill_forge.py`, `core/skill_crucible.py` | IMPLEMENTED BUT UNTESTED — writes code at runtime; sandboxing not verified |
| Spotify control | `actions/spotify_mcp_server/` (TypeScript) | IMPLEMENTED BUT UNTESTED |
| Smart home (Tuya, Home Assistant) | `smart_home/`, `smart_home_page_new.py` | IMPLEMENTED BUT UNTESTED |
| Study guard, game sentinel, net sentinel, space cleaner | `core/` and `features/` | IMPLEMENTED BUT UNTESTED — each has a test file, none run |
| Shopping agent | `core/shopping_agent.py`, `features/shopping_agent.py` | IMPLEMENTED BUT UNTESTED |
| Dashboard web server | `dashboard/server.py` | IMPLEMENTED BUT UNTESTED — auth present on command endpoints |
| Auto-updater | `core/updater.py`, `core/updater_ota.py`, `updater.py` | IMPLEMENTED BUT UNTESTED — not reviewed for signature verification |

## Brahma Connect gateway (PC side)

| Feature | Evidence | Status |
|---|---|---|
| WebSocket server and message protocol | `brahma_connect/gateway/server.py`, `protocol.py` | IMPLEMENTED BUT UNTESTED |
| Pairing offer with expiring code | `pairing.py`, `GET /gateway/pair` | **BROKEN (security)** — offer is returned without authentication |
| Device pairing approval | `approve_pending_request()` | **BROKEN (security)** — approve endpoint is unauthenticated |
| Device registry, revoke, forget | `device_manager.py`, `/gateway/devices/*` | PARTIALLY IMPLEMENTED — endpoints unauthenticated |
| Command router | `command_router.py` | IMPLEMENTED BUT UNTESTED |
| Mobile portal page | `mobile_portal.py`, `GET /mobile` | IMPLEMENTED BUT UNTESTED |
| Discovery (mDNS) | `README.md` says optional Zeroconf | PARTIALLY IMPLEMENTED — not verified |
| Encrypted transport | `grep` for `ssl`, `tls`, `certfile` in `brahma_connect/` returns nothing | MISSING — gateway serves plain `ws://`/`http://` on the LAN |

## Android companion (`brahma-connect-android`)

| Feature | Evidence | Status |
|---|---|---|
| Launch app by name | `DeviceCommandHandler.launchApp` | IMPLEMENTED BUT UNTESTED |
| Open URL | `openUrl` | IMPLEMENTED BUT UNTESTED |
| Volume get/set | `volumeGet`, `volumeSet` | IMPLEMENTED BUT UNTESTED |
| Flashlight | `flashlight` | IMPLEMENTED BUT UNTESTED |
| Battery, device info | `getBattery`, `getDeviceInfo` | IMPLEMENTED BUT UNTESTED |
| File list / read / write / delete | `fileList`, `fileRead`, `fileWrite`, `fileDelete` | IMPLEMENTED BUT UNTESTED — delete path not reviewed for scope limits |
| UI tree dump, tap, swipe, type | `uiDump`, `uiTap`, `uiSwipe`, `uiType`, accessibility service | IMPLEMENTED BUT UNTESTED |
| **SMS send** | `sendSms` (added in `4927b91`) | IMPLEMENTED BUT UNTESTED — **no runtime permission request before commit `dd9659b`**; now requested in `MainActivity` |
| **Phone call** | `makeCall` (added in `4927b91`) | IMPLEMENTED BUT UNTESTED — same permission note |
| **Notification list** | `BrahmaNotificationListenerService` (added in `4927b91`) | IMPLEMENTED BUT UNTESTED — requires user to enable notification access in Android settings |
| Phone unlock | `unlockPhone` | **BROKEN → now UNSUPPORTED** (fixed in `dd9659b`, reports honestly) |
| Gateway client over WebSocket | `BrahmaWebSocketClient.kt` | IMPLEMENTED BUT UNTESTED |
| Pairing from QR / code | `PairingPayloadParser.kt`, `PairingStorage.kt` | IMPLEMENTED BUT UNTESTED |

## Not present anywhere in the repository

Based on a search of the file names and the code in the files read:

- Voice recognition on the phone itself (a local voice-to-command router). Phone commands currently only arrive from the PC gateway.
- Local, on-device command routing for the phone without a PC.
- Human approval step for sensitive actions on the phone (SMS, call) before execution.
- Verification of action results on the phone (for example, confirming an app actually opened).
- Long-term memory shared across PC and phone.
