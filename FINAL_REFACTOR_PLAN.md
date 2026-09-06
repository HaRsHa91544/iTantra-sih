# Final Refactor Plan

**Branch:** `sprint-4` working tree. Scope = merge of `sprint-4` + `test` best-parts, per user-approved decisions (2026-09-06).

## Approved changes — DONE

| # | Change | Status | Verification |
|---|---|---|---|
| 1 | Restore `assets/piper/tokens.txt` to correct UTF-8 content, duplicate `3 133` removed (src = test's fixed blob) | DONE | byte-identical to test blob `2e8d4b381b6ccf2793448082a3c4a69713919b6e` (962 B); `assembleDebug` PASS |
| 2 | STT `stop()` recognizer cleanup | NO ACTION NEEDED | sprint-4 already ships `speechService.shutdown()` + `recognizer.close()`; identical HEAD↔worktree |
| 3 | Keep `TTSManager` (dead code) as future facade | NO ACTION | approved |
| 4 | Keep generalized `.gitattributes` | NO ACTION | approved |
| 5 | Fix deprecated `getParcelableExtra` in WifiDirectBroadcastReceiver | DONE | API-safe `getParcelableExtra(intent, key, Class)` helper, TIRAMISU-gated; clean compile (deprecation note gone) |
| 6 | Independence: decouple UI from socket class | DONE | new `transport/SocketConnection` interface; `WifiDirectSocketManager implements SocketConnection, MessageTransport` (no-arg ctor + `setConnectionListener`); `VoiceCommunication` now owns payload AND link lifecycle (`startConnection`/`stopConnection`); `MainActivity` only knows `VoiceCommunication` + Wi-Fi Direct framework |

Build + lint: `assembleDebug` SUCCESS (clean + incremental), `lintDebug` 0 errors / 57 warnings (all pre-existing: SetTextI18n, dependency-version bumps, overdraw; none from the refactor).

## What the final design is (now in sprint-4)

- UI (`MainActivity`) owns only rendering + Wi-Fi Direct framework plumbing; it depends on `VoiceCommunication` (and the framework types), never on socket/STT/TTS classes.
- `VoiceCommunication` coordinator owns both the payload path (STT→Message→Transport, Transport→Message→TTS) and the link lifecycle (server/client connect, disconnect).
- `Message` model (text/senderId/timestamp/language) = JSON wire format.
- `WifiDirectSocketManager` implements the neutral `MessageTransport` + `SocketConnection`: length-prefix frames, 256 KB guard, server accept-loop, `shutdown()`, `closeQuietly`.
- `PiperEngine` prompt-stop (`stopRequested` + queue clear) + uncompressed ONNX read (`noCompress 'onnx'`); `OfflineSTT` releases recognizer on stop(); no dead `acceptAudio`.

## Verified against `test` (no action needed)

- Manifest, layout, colors/strings/themes, Wi-Fi receiver, DeviceAdapter, item_device.xml, gradle versioning, models, espeak data — identical or cosmetic-diff only.

## Remaining work (requires user)

| # | Item | Blocked by |
|---|---|---|
| 1 | Two-device end-to-end runtime test (voice→STT→Wi-Fi→TTS both directions) | physical devices (none attached) |
| 2 | Objective latency checks / tuning of Vosk endPointingMs + Piper tts speed | run on device |
| 3 | Optional: delete `TTSManager.java` | user decision (currently keep) |
| 4 | Optional future: Telugu support, espeak-ng-data trimming (~20 MB), model-unload on disconnect | new sprint, out of current scope |

## Commit

Not committed until user tests on devices and says **GO COMMIT**. Working tree currently holds: fixed `tokens.txt`, `.gitattributes`, `WifiDirectBroadcastReceiver` else-if edit, plus docs (`BRANCH_COMPARISON.md`, `BASELINE_RESULTS.md`, `FINAL_REFACTOR_PLAN.md`).