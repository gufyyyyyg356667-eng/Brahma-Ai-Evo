# Implementation Plan

Derived from `MISSING_FEATURES.md`. Each step lists what it changes, how it is verified, and what remains unverified.

Rule for every step: do not mark a step done unless its test was actually executed. A step that cannot be tested in this environment is marked **code-complete, untested**.

## Step 0 — Environment (required before any test can run)

- Install Python dependencies for `brahma_connect` (at minimum `fastapi`, `uvicorn`, `pydantic`, `websockets`). Package installation is currently blocked in this session.
- Install the Android SDK and Gradle to compile the companion app.
- Without Step 0, all tests in this plan remain unexecuted.

## Step 1 — Gateway authentication (Priority 1, items 1–2)

**Changes:**
- `brahma_connect/gateway/server.py`: require a valid admin credential (from the desktop app session) on every `/gateway/*` route except the health check.
- `GET /gateway/pair`: keep for the desktop UI only, and require the same admin credential.
- `_pair_device` and the `hello` branch: refuse a pending request unless it carries a valid, unexpired `pairing_code` or `pairing_token`.

**Verification:**
- Unit test: an unauthenticated request to each `/gateway/*` route returns 401.
- Unit test: a `hello` without a valid code creates no pending request.
- Regression test: the existing pairing test still passes with a valid code.

**Unverified until Step 0 is done.**

## Step 2 — Transport encryption (Priority 1, item 3)

**Changes:**
- Add TLS to the gateway (self-signed certificate created at pairing time, pinned by the Android app), or restrict binding to localhost and use a documented tunnel.

**Verification:** connection test over TLS; certificate pinning test on Android.

**Not started. Design decision needed from the owner first.**

## Step 3 — Phone-side voice and command router (Priority 2, items 5–6)

**Changes:**
- Android: a `VoiceCommandRouter.kt` that maps a recognized utterance to `DeviceCommandHandler.handle(action, parameters)`.
- Android: wire SpeechRecognizer (or Gemini Live from `mahi-ai`) to the router.

**Verification:**
- Unit test: a fixed set of utterances maps to the expected action and parameters.
- Device test (manual): spoken command runs with the phone not connected to the PC.

**Not started.**

## Step 4 — Permission and setup screens (Priority 2, item 7)

**Changes:** a setup screen in `MainActivity` that shows the state of Accessibility, Notification access, SMS and call permissions, and opens the matching Android settings page for each.

**Verification:** UI test on a device; state readback from `AccessibilityService.instance` and the listener's connected flag.

**Not started.**

## Step 5 — Action verification (Priority 2, item 8)

**Changes:** after `launch_app`, check the foreground package with `UsageStatsManager` or the accessibility window list before reporting success. After `send_sms`, report `sent` only after the system's sent-status callback.

**Verification:** device test: open an app, confirm the reported package matches the foreground package.

**Not started.**

## Step 6 — Approval, audit, emergency stop (Priority 3, items 9–12)

**Changes:** approval prompt for SMS, call, delete; an audit log entry for each command; an emergency stop command that cancels in-flight commands.

**Not started.**

## Step 7 — Shared memory and multi-agent architecture (Priority 4)

**Not started.** Requires a design review before code is written.

## What is not in this plan

The 110-module specification from the master directive. It is too large to implement and test in one pass without producing placeholders, which the directive itself forbids. Modules will be added one at a time after Steps 1–5 are complete.
