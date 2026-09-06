# Baseline Results

**Date:** 2026-09-06
**Branch under test:** `sprint-4` (local, working tree) and `test` (origin/test @ 087ecc3)
**Method:** cold `:app:assembleDebug` via Gradle wrapper 8.13, JDK 21 (JBR), Android SDK android-36.
**Devices:** none attached → runtime tests NOT performed (marked NOT TESTED).

---

## sprint-4

| Area | Status | Evidence / Notes |
|---|---|---|
| Build | PASS | BUILD SUCCESSFUL (clean + incremental). APK 202.73 MB |
| Lint | PASS (0 errors / 57 warnings) | all warnings pre-existing (SetTextI18n, dependency-version bumps, overdraw) |
| STT | STATIC PASS / RUNTIME NOT TESTED | Vosk offline model present; stop() fully releases recognizer (service shutdown + close) |
| TTS | STATIC PASS / RUNTIME NOT TESTED | tokens.txt fixed (correct UTF-8, deduped, byte-identical `2e8d4b38`); ONNX model present |
| Wi-Fi | STATIC PASS / RUNTIME NOT TESTED | P2P + TCP length-prefixed transport; connection decoupled behind `SocketConnection` |
| End-to-end (voice→STT→Wi-Fi→TTS) | NOT TESTED | requires 2 physical devices |
| Known errors | none at build time (BroadcastReceiver deprecation fixed via API-safe helper) | |
| Performance | APK 202.73 MB; model load + synth dominated by Vosk + Piper (estimated 3–5 s end-to-end, unmeasured) | |

## test

| Area | Status | Evidence / Notes |
|---|---|---|
| Build | PASS | BUILD SUCCESSFUL (fresh, 30 s). APK 198.05 MB |
| STT | STATIC PASS / RUNTIME NOT TESTED | Vosk offline model present; stop() cleans recognizer |
| TTS | STATIC PASS / RUNTIME NOT TESTED | tokens.txt correct (UTF-8, duplicate removed); prompt-stop absent |
| Wi-Fi | STATIC PASS / RUNTIME NOT TESTED | line-based transport; one-shot server; no executor shutdown |
| End-to-end | NOT TESTED | requires 2 physical devices |
| Known errors | none at build time; 1 deprecation note (BroadcastReceiver) | |
| Performance | APK 198.05 MB; same engines as sprint-4 | |

---

## Code differences relevant to runtime (summary)

| Area | sprint-4 (better / worse) | test (better / worse) |
|---|---|---|
| Transport framing | better (length-prefix) | worse (newline) |
| Server reconnect | better (accept loop) | worse (one-shot) |
| Socket executor cleanup | better (`shutdown()`) | worse (leak) |
| TTS stop latency | better (stopRequested) | worse |
| STT recognizer lifecycle | better (stop() closes recognizer) | worse (leak until next start) |
| TTS token vocabulary | ⚠️ was corrupt → **fixed (correct UTF-8, deduped)** | correct (source of fix) |
| Architecture decoupling | better | worse |

## Post-baseline refinements applied to sprint-4 (2026-09-06)

1. `assets/piper/tokens.txt` restored to correct UTF-8 + deduped `3 133` (was corrupt in the working tree).
2. Deprecated `getParcelableExtra` in `WifiDirectBroadcastReceiver` → API-safe typed helper.
3. Independence refactor: new `transport/SocketConnection`; `WifiDirectSocketManager implements SocketConnection + MessageTransport`; `VoiceCommunication` now owns link lifecycle (`startConnection`/`stopConnection`); `MainActivity` only talks to `VoiceCommunication`.
All verified: clean `assembleDebug` PASS, `lintDebug` 0 errors / 57 warnings.

Full analysis: `BRANCH_COMPARISON.md`.