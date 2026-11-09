# Missing Features and Gaps

Ordered by priority. Priority 1 items are security issues or broken behavior that should be fixed before adding anything new.

## Priority 1 — Security and correctness

1. **Authenticate every `/gateway/*` administrative endpoint.** Currently open on the LAN: pairing offer, device list, logs, pending requests, approve, reject, revoke, forget.
2. **Require the pairing code or token in the `hello` path.** The `hello` handler currently creates a pending request for any client.
3. **Encrypt gateway traffic.** The gateway serves plain HTTP and WebSocket. Pairing secrets and commands travel in clear text on the LAN.
4. **Require explicit user approval on the PC for every new pairing**, which the approve endpoint is supposed to represent, but only after authentication (see item 1).

## Priority 2 — Phone-first control (the goal of this project)

5. **Phone-side voice-to-command router.** Phone commands currently arrive only from the PC gateway. No on-device intent routing exists.
6. **Phone-side speech recognition** (Android SpeechRecognizer or Gemini Live) wired to the command handler.
7. **Runtime permission flow for Accessibility and Notification access.** The app requests SMS and call permissions, but Accessibility and notification access must still be enabled manually in Android settings. A guided screen is missing.
8. **Verification after actions.** For example, after `launch_app`, confirm the target app is in the foreground. Currently results are reported as success once the intent is sent.

## Priority 3 — Safety requirements from the directive

9. **Human approval for sensitive actions** on the phone: SMS, call, file delete, and anything that spends money or deletes data. No approval step exists.
10. **Emergency stop** that halts all running phone automation.
11. **Audit log** that records each command, its requester, and its result. The gateway has a log, but it is not tamper-evident and does not cover phone-side actions.
12. **Rate limiting** on the gateway and command endpoints.

## Priority 4 — Architecture items from the directive

13. **Shared long-term memory** between PC and phone, with source, timestamp and confidence per entry, and user edit/delete.
14. **Multi-agent orchestration**, the Master Brain → Specialized Agents model in the directive. Current code has a single intent dispatcher.
15. **Task verification and recovery engine** as a separate module.
16. **Structured request and response contracts** (`DeviceCommand`, `DeviceResponse`) as explicit types on both sides. The current protocol uses loose dictionaries.

## Not in scope of this phase

- The 110-module list in the master directive. Most of these are not started. They should be scheduled after Priorities 1–2 are complete, and each one should be implemented and tested before being marked done.
