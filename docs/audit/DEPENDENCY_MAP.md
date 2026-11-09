# Dependency Map

Only the connections confirmed in the code read during this audit are listed. Arrows show the direction of a call or import.

## PC side

```mermaid
flowchart TD
  UI["ui.py (PyQt6)"] --> MAIN["main.py (Gemini session, tool dispatch)"]
  MAIN --> LLM["llm_client.py / or_client.py"]
  MAIN --> DISP["core/intent_dispatcher.py"]
  MAIN --> ACT["actions/brahma_connect.py"]
  ACT --> SVC["brahma_connect/service.py"]
  SVC --> GW["brahma_connect/gateway/server.py"]
  GW --> ROUTER["gateway/command_router.py"]
  GW --> HUB["gateway/websocket.py (ConnectionHub)"]
  GW --> DEV["gateway/device_manager.py"]
  GW --> PAIR["gateway/pairing.py"]
  ROUTER --> HUB
  HUB -->|WebSocket /ws| ANDROID["Android companion"]
  DASH["dashboard/server.py (its outgoing connections not yet mapped)"]
```

Not confirmed in this audit: which `features/*` modules `core/intent_dispatcher.py` calls. The dispatcher does not import from `features/` directly.

## Android side

```mermaid
flowchart TD
  MA["MainActivity.kt"] --> SVCF["BrahmaConnectForegroundService.kt"]
  MA --> PERM["Runtime permissions (SMS, CALL, camera, notifications)"]
  SVCF --> WS["network/BrahmaWebSocketClient.kt"]
  WS --> CMD["commands/DeviceCommandHandler.kt"]
  CMD --> ACC["accessibility/BrahmaAccessibilityService.kt"]
  CMD --> NOTIF["notification/BrahmaNotificationListenerService.kt"]
  CMD --> DEVINFO["device/AndroidDeviceInfoProvider.kt"]
  CMD --> PAIRS["pairing/PairingStorage.kt"]
```

## Interfaces that must match across the boundary

| Interface | PC side | Android side | Status |
|---|---|---|---|
| WebSocket message envelope | `gateway/protocol.py` | `core/AppModels.kt` (`BrahmaProtocol`) | Both sides define the same message type names, but no shared schema file exists |
| Command execute | `gateway/command_router.py` sends `execute` | `DeviceCommandHandler.handle(action, parameters)` | Matching names confirmed for existing commands |
| Pairing | `gateway/pairing.py`, `GET /gateway/pair` | `pairing/PairingPayloadParser.kt` | Exchange confirmed; security gap, see audit |
| Result | `command_router` expects result dict | `CommandResult.toJson()` | Confirmed |

## Missing edges (planned, not present)

- Phone-side voice → `DeviceCommandHandler` (no voice module on the phone).
- Verification → Master Brain (no verification module).
- Shared memory → both devices (no shared memory module).
