# Branch Comparison

**Date:** 2026-09-06
**Branches compared:** `sprint-4` (local, **working tree = current code**) vs `test` (local tracking `origin/test`, 087ecc3)
**Mode:** read-only audit + baseline builds. No code modified.

---

## 1. General Difference

`sprint-4` is the **integrated, decoupled** implementation:

```
MainActivity (UI only)
   │
   ▼
VoiceCommunication (coordinator)
   │        ├── STTEngine (OfflineSTT / Vosk)
   │        ├── TTSEngine (PiperEngine / sherpa-onnx)
   │        └── MessageTransport (WifiDirectSocketManager)
```

- Has `models/Message.java`, `transport/MessageTransport.java`, `application/VoiceCommunication.java`.
- STT result → `Message` (JSON) → length-prefixed Wi-Fi frame.
- Wi-Fi receive → `Message` → text → TTS (off-worker-thread).
- Wi-Fi payload routing is decoupled from the UI; the activity only displays.

`test` is the **monolithic** implementation:

```
MainActivity (UI + STT + Wi-Fi + TTS + socket wiring)
   ├── OfflineSTT  (result callback → socketManager.sendMessage directly)
   ├── TTSManager(PiperEngine)  (onMessageReceived → speak directly)
   └── WifiDirectSocketManager  (raw line-based transport)
```

- No Message model, no transport interface, no coordinator.
- Data flows live inside `MainActivity` callbacks.

Not considered: `origin/multi-model` (per user instruction).

---

## 2. Project Structure

| Path | sprint-4 | test | Notes |
|---|---|---|---|
| `application/VoiceCommunication.java` | YES | — | Coordinator (sprint-4 only) |
| `models/Message.java` | YES | — | Data contract (sprint-4 only) |
| `transport/MessageTransport.java` | YES | — | Transport interface (sprint-4 only) |
| `MainActivity.java` | UI wiring (~666 lines) | monolith (~620 lines) | see §5 / §10 |
| `speech/stt/STTEngine.java` | interface w/o `acceptAudio` | interface w/ `acceptAudio` | see §6 |
| `speech/stt/OfflineSTT.java` | stop() = stop only | stop() = stop+shutdown+close recognizer | see §6 |
| `speech/tts/PiperEngine.java` | + `stopRequested` flag | no flag | see §7 |
| `speech/tts/TTSManager.java` | identical bytes | identical bytes | **dead in sprint-4** (no callers) |
| `speech/tts/TTSEngine.java` | identical | identical | — |
| `wifidirect/WifiDirectSocketManager.java` | framing + server loop + MessageTransport + shutdown | line-based, one-shot server | see §8 |
| `wifidirect/WifiDirectBroadcastReceiver.java` | cosmetic else-if | — | equivalent behavior |
| `wifidirect/DeviceAdapter.java` | trailing newline only | — | cosmetic |
| `res/layout/item_device.xml` | trailing newline only | — | cosmetic |
| `res/layout/activity_main.xml`, `AndroidManifest.xml`, `colors.xml`, `strings.xml`, `themes.xml`, gradle files, Vosk model, Piper ONNX, espeak-ng-data, sherpa AAR | identical | identical | no difference |
| `tokens.txt` | **CORRUPTED in working tree** | correct UTF-8, deduplicated | see §7 / §13 |
| Docs (`BRANCH_ANALYSIS.md`, `CHANGES_REQUIRED.md`, `REPOSITORY_AUDIT.md`, `README.md`) | YES | — | sprint-4 documentation only |

---

## 3. Gradle / Dependencies

Identical dependency set in both branches:

```
com.alphacephei:vosk-android:0.3.75
androidx appcompat / material / activity / constraintlayout
libs/sherpa-onnx-1.13.7.aar (local binary)
junit / espresso (scaffold tests)
```

Differences (`app/build.gradle`):

| Item | sprint-4 | test |
|---|---|---|
| `aaptOptions.noCompress` | includes `'onnx'` | does **not** include `'onnx'` |
| dependency ordering | vosk listed before androidx | vosk after sherpa AAR |

Analysis:
- `onnx` noCompress: only matters if the ONNX model is read directly from the APK. `PiperEngine` reads the model/tokens via `new OfflineTts(context.getAssets(), config)` — sherpa reads directly from assets, so **uncompressed** avoids decompressing a 63 MB file into a byte array. **sprint-4 is preferred** (lower peak RAM, faster model load).
- Dependency ordering is cosmetic (Gradle resolves equal).
- Verdict: **sprint-4 build.gradle preferred**. Both builds pass.

Build baseline (this machine):
- sprint-4: `BUILD SUCCESSFUL` (incremental + forced fresh javac). APK 202.73 MB.
- test: `BUILD SUCCESSFUL` (fresh, 30 s). APK 198.05 MB. One deprecation note (BroadcastReceiver).
- Large APK is dominated by bundled Vosk model (~60 MB), Piper ONNX (~63 MB), espeak-ng-data (~20 MB), sherpa-aar jni libs. Equal in both.

---

## 4. AndroidManifest

Identical in both branches. Permissions: Wi-Fi state/change, network state/change, INTERNET, FINE/COARSE location, RECORD_AUDIO, NEARBY_WIFI_DEVICES (neverForLocation). No Bluetooth permissions (Google Nearby variant removed from both). No issues.

---

## 5. UI

Same layout (`activity_main.xml` identical). Same controls (status, my device, role, peer list, discover, disconnect, model status, received text, speaking status, PTT button). Same color set (`status_disconnected/discovering/connected`, `card_bg`).

Behavioral difference:

| Behavior | sprint-4 | test |
|---|---|---|
| Button enable logic | identical | identical |
| PTT → STT → send | via `voiceCommunication.startListening()` | via `sttEngine.start()` → callback → `socketManager.sendMessage()` |
| receive → display | via `VoiceCommunication.Listener.onMessageReceived` | via `SocketEventListener.onMessageReceived` |
| receive → TTS | delegated to coordinator (not required in UI) | `ttsManager.speak(message)` inside UI callback |
| Socket connection callbacks | Activity still implements connection-level listener (startServer/startClient wiring in Activity) | Activity implements `SocketEventListener` directly |

Both UIs are functional for the demo. `sprint-4` additionally routes payloads *through the coordinator*, satisfying the requirement that UI not touch module internals. Minor remaining coupling in both: the Activity still drives `WifiDirectSocketManager.startServer/startClient` (this is lifecycle/role wiring, acceptable).

---

## 6. STT

Same engine: Vosk `SpeechService` + bundled `model-en-us`, unpacked via `StorageService.unpack`.

| Aspect | sprint-4 | test |
|---|---|---|
| `STTEngine` interface | init / setOnResultListener / start / stop / destroy | + `acceptAudio(byte[],int)` (never called anywhere) |
| `OfflineSTT.stop()` | `speechService.stop()` + `shutdown()` (guarded), then **closes `recognizer`** and nulls it | `speechService.stop()` only; recognizer left allocated until next `start()`/`destroy()` |
| `OfflineSTT.start()` | stops+shuts down any previous service, creates new recognizer | identical |
| `OfflineSTT.destroy()` | full cleanup (service, recognizer, model) | identical |
| result handling | same JSON parse, partial/final forwarding | identical |

Analysis:
- `sprint-4` fixes a **native-memory leak** in its own committed code: `stop()` releases the Vosk native `recognizer` and shuts down the speech service, so repeated PTT start/stop cycles do not accumulate native memory. `test`'s `stop()` leaves the recognizer allocated until the next `start()` replaces it.
- `acceptAudio` is dead API surface in both (`OfflineSTT` body is a no-op comment; no caller). `sprint-4` correctly removed it from the interface.
- **Recommended as-is:** sprint-4 interface (without `acceptAudio`) **and** sprint-4 `stop()` cleanup. Sprint-4 wins on both aspects.

---

## 7. TTS

Same engine: sherpa-onnx `OfflineTts` with Piper `en_IN-spicor-medium.onnx` + `tokens.txt` + extracted `espeak-ng-data`. Identical model loading, extraction, AudioTrack playback, gain normalization code.

| Aspect | sprint-4 | test |
|---|---|---|
| `stop()` | sets `stopRequested=true`, clears queue, stops AudioTrack | only stops AudioTrack thread-local |
| speak() | resets flag, checks after synthesis | — |
| playback loop | checks `stopRequested` | waits full duration |
| release() | same (release native tts, shut down executor) | same |

- **sprint-4 implements the prompt-stop fix (M017).** If synthesis is long, test keeps playing to the end; sprint-4 aborts promptly. Winner: **sprint-4**.
- **CRITICAL BUG (sprint-4 worktree only):** `assets/piper/tokens.txt` is corrupted. The committed sprint-4 HEAD has correct UTF-8 (161 lines, includes duplicate `3 133`). The current working tree has a re-encoded/garbled file: 161 lines, only 160 unique ids, and many IPA tokens collapsed or wrong (`ð`→`d`, `ø`→`o`, `ŋ`→`h`, `↓`→control char, etc.). sherpa-onnx builds its token→id map from this file; the corrupted file yields wrong phoneme mapping or an **abort** at TTS init. `test` contains a correct UTF-8 `tokens.txt` and additionally **removed the duplicate `3 133`** (friend's commit message: prevents sherpa-onnx hard abort).
- **Recommended:** restore sprint-4's token file to the correct UTF-8 content **without** the duplicate `3 133` (i.e., equivalent to `test`'s file). Requires user approval (§18 / §21).

---

## 8. Wi-Fi

Same Wi-Fi Direct stack (WifiP2pManager + BroadcastReceiver + TCP on port 8988). Transport differs significantly:

| Aspect | sprint-4 | test |
|---|---|---|
| framing | **length-prefixed**: 4-byte big-endian length + UTF-8 bytes | `println`/`readLine` (newline splitting bug) |
| max payload guard | 256 KB (`MAX_MESSAGE_BYTES`), rejects invalid lengths | none (unbounded line allocation) |
| server accept loop | **loops** on `accept()`; survives client reconnect | accepts one client, thread exits |
| executor cleanup | `shutdown()` + `shutdownNow()` | never shuts down `sendExecutor` |
| interface | implements `MessageTransport` | none (raw `sendMessage(String)`) |
| receive callback | delivered to `InboundListener` (coordinator) | delivered to Activity `onMessageReceived` |
| client retry | 5 retries, 5 s connect timeout | same |
| connection notify | notifies + waits for client disconnect before next accept | — |

- **sprint-4 is strictly better on transport:** correct framing for arbitrary text (STT output can contain punctuation/spaces; newline split risk), bounded memory, reconnect-capable server, no thread leak, module decoupling.
- Winner: **sprint-4** on every meaningful aspect.

---

## 9. Message Model

| Aspect | sprint-4 | test |
|---|---|---|
| class | `models/Message.java`: `text`, `senderId`, `timestamp`, `language` (+`DEFAULT_LANGUAGE="en"`), `toJson()/fromJson()` | none — raw strings on the wire |
| wire format | JSON via `Message.toJsonString()` (stable, extensible) | plain text line |
| metadata | sender, timestamp, language present | none |
| validation | `fromJson` returns null on bad JSON; coordinator falls back to raw text | n/a |

- `sprint-4` satisfies the agile-spec requirement (`models/Message.java`) and future language routing. Winner: **sprint-4**.

---

## 10. Application Layer

| Aspect | sprint-4 | test |
|---|---|---|
| coordinator | `VoiceCommunication` connects STT→Message→Transport and Transport→Message→TTS; only knows interfaces; delivers UI events via `Listener` on main thread | none — MainActivity does all wiring |
| coupling | STT/TTS/Wi-Fi/Message independent; UI talks to coordinator | UI directly couples STT, TTS, Wi-Fi |
| produces | `List<module>` clean layering | god-activity |

- Winner: **sprint-4** (matches the desired architecture in the brief).
- Note: `TTSManager` is unused (dead) in sprint-4 because the coordinator holds a `TTSEngine` directly; `PiperEngine implements TTSEngine`. `TTSManager` is used in test.

---

## 11. Threading

| Aspect | sprint-4 | test |
|---|---|---|
| STT | Vosk `SpeechService` threading internal; callbacks on its thread → forwarded | same |
| TTS | synthesis+playback on single-thread executor; speak() callbacks marshalled to main via listener | speak triggered from UI callback; PiperEngine queues on its own executor |
| inbound socket | `startReceiving` background thread → coordinator → executor task for TTS; UI updates via `runOnUiThread` | receive thread → `runOnUiThread` → `ttsManager.speak` (fires executor) |
| cleanup | receiver unregistered onPause; coordinator `release()` shuts executor + transport; socket threads interrupted | sockets closed; executor threads **not** shut down |

- Keep sprint-4's executor hygiene; fix `OfflineSTT.stop()` recognizer leak (from test). No ANR paths found in either.

---

## 12. Lifecycle

Both handle the basics: onResume re-registers broadcast receiver, onPause unregisters, onDestroy tears down.

| Aspect | sprint-4 | test |
|---|---|---|
| onDestroy | `voiceCommunication.release()` (executor + transport + TTS + STT), `socketManager.shutdown()`, `removeGroup` | `stt.destroy()`, `socketManager.stop()`, `ttsManager.release()`, `removeGroup` |
| orientation change | Activity recreated → new PiperEngine reloads model (same both) | same |
| backgrounded | STT keeps running if recording (both); no explicit pause of STT/TTS | same |

No structural difference. sprint-4 transports a single `shutdown()` call path instead of `stop()`.

---

## 13. Error Handling

| Aspect | sprint-4 | test |
|---|---|---|
| STT init failure | Toast + status text | same |
| STT runtime error | forwarded `onError` → Toast | same |
| socket errors | `onError` → logged; MessageTransport errors routed to coordinator | logged |
| invalid/oversized payload | length guard; JSON fallback | none |
| TTS failure | logged inside PiperEngine | logged |
| reconnect | server loop allows client rejoin (sprint-4) | must restart server |

Winner: **sprint-4** (bounded input, JSON fallback, reconnect-capable server). Both lack user-visible retry policies for sockets; acceptable for MVP.

---

## 14. Resource Management

| Resource | sprint-4 | test |
|---|---|---|
| STT recognizer on stop | closed (shutdown + recognizer.close) | LEAK (not closed until next start) |
| socket executor | `shutdown()` provided; `shutdown()` called by coordinator release | never shut down (leak) |
| AudioTrack | released after playback / on stop (both) | same |
| Piper espeak extraction | overwritten each init, no skip-if-exists (both) | same |
| model lifecycle | Vosk model kept for activity lifetime; closed on destroy (both) | same |

Combine: sprint-4 socket/audio resource handling **+** test STT recognizer cleanup.

---

## 15. Model Management

Identical in both branches:
- Vosk `model-en-us` unpacked by `StorageService` (cached by vosk), kept until `destroy()`.
- Piper ONNX + tokens read from assets; espeak-ng-data (~20 MB, all languages) extracted to files dir **every init**.
- No dynamic language switching; English only.
- single models loaded once per activity; no duplicate loading within a session.

Only difference: tokens.txt validity (see §7) — **sprint-4 worktree is broken, test is correct**.

---

## 16. Performance

Measured (baseline):
- sprint-4 APK: 202.73 MB | test APK: 198.05 MB.
- Both compile in ~30 s cold; identical native libs.

Static latency profile (identical engines):
- STT: Vosk real-time (16 kHz), model load 2–5 s first time.
- TTS: Piper synthesis + AudioTrack; prompt-stop responsive only in sprint-4.
- Wi-Fi: TCP on P2P; sprint-4 framing adds negligible overhead; length-prefix reduces receive-time memory copies vs unbounded line buffer.
- `noCompress 'onnx'` (sprint-4) avoids decompressing a 63 MB asset → lower peak RAM for sherpa reads.

Winner: sprint-4 (better stop latency, bounded buffers, uncompressed model reads). End-to-end latency is dominated by STT finalization + TTS synthesis — unchanged between branches.

---

## 17. Testing

| Aspect | sprint-4 | test |
|---|---|---|
| unit/instrumentation tests | scaffold examples only (unused) | scaffold examples only (unused) |
| build verification | PASS (this machine) | PASS (this machine) |
| runtime/device tests | NOT TESTED (no devices attached) | NOT TESTED (no devices attached) |
| two-device voice loop | NOT TESTED | NOT TESTED |

Neither branch has real tests. Identical starting point.

---

## 18. Problem Statement Compliance

| Requirement | sprint-4 | test |
|---|---|---|
| offline STT | PASS (Vosk, bundled model) | PASS |
| offline TTS | PASS *IF tokens.txt fixed; currently BROKEN (corrupt tokens)* | PASS |
| local comm (Wi-Fi Direct, no internet) | PASS (TCP on P2P) | PASS (with newline/framing caveat) |
| low-data-rate / latency | PASS directionally (framing + prompt stop) | PARTIAL (no framing, no prompt stop) |
| push-to-talk UI | PASS (PTT button) | PASS |
| voice-to-voice, both directions | IMPLEMENTED (coordinator routes both directions) | IMPLEMENTED (through Activity callbacks) |
| language support | PARTIAL — English only (both) | PARTIAL — English only |

The only correctness fixes required for sprint-4 are the tokens.txt restoration (§7) — already applied in the working tree. The STT `stop()` cleanup is already present in sprint-4.

---

## 19. Security / Robustness

| Aspect | sprint-4 | test |
|---|---|---|
| input size bound | 256 KB frame guard | unbounded readLine |
| malformed message | JSON parse guarded, raw-text fallback | n/a |
| plaintext/P2P | expected for local demo (both) | same |
| logging | no sensitive data logged (both) | same |
| permissions | neverForLocation on NEARBY_WIFI_DEVICES (both) | same |

Winner: sprint-4 (bounded reads avoid memory-exhaustion from a buggy/malicious peer).

---

## 20. Recommended Final Design

Adopt from **sprint-4**:
- `VoiceCommunication` coordinator + `Message` model + `MessageTransport` interface (modularity, agile compliance).
- `WifiDirectSocketManager` framing, server loop, executor shutdown, MessageTransport impl.
- `PiperEngine` `stopRequested` prompt-stop.
- `STTEngine` without dead `acceptAudio`.
- `noCompress 'onnx'` in aaptOptions.

Adopt from **test**:
- Correct `piper/tokens.txt` (UTF-8, duplicate `3 133` removed) — already restored in the sprint-4 working tree.

Already correct in sprint-4 (no change needed):
- `OfflineSTT.stop()` releases the Vosk recognizer (`shutdown()` + `recognizer.close()`).

Remove/clean in sprint-4 (pending user approval):
- `TTSManager` is dead code after coordinator refactor (optional deletion).

Unchanged in both (identical): manifest, layout, Wi-Fi receiver, adapter, gradle versioning, models, espeak data.

The net result is **sprint-4 as-is for architecture and STT, plus the fixed tokens.txt**, which is the recommended final sprint-4.

---

## 21. Decisions Required From User

1. **tokens.txt** — Fix the corrupted worktree file by restoring correct UTF-8 content and removing the duplicate `3 133` (same as test)? ✅ **User approved — DONE in working tree** (verified byte-identical to test's blob `2e8d4b38`). (`tokens.txt` is a model-vocabulary file; changing it is important.)
2. **STT stop() cleanup** — ✅ **Already present in sprint-4** (committed `stop()` does `speechService.shutdown()` + `recognizer.close()`). No change needed. Verified identical between HEAD and worktree.
3. **TTSManager** — Delete unused `TTSManager.java` (coordinator uses `TTSEngine` directly), or keep as facade?
4. **.gitattributes** — Keep the worktree's generalized binary-attribute change (`*.onnx binary`, `*.dict binary`, `tokens.txt -text` at repo root) or restore the previous path-scoped rules?
5. **Runtime/device testing** — No devices attached; approve analysis-only for now and test on two physical devices later (required for end-to-end verification)?
6. **Commits** — `sprint-4` working tree has uncommitted changes (`.gitattributes`, `tokens.txt`, BroadcastReceiver edit). After the above, should these become the final committed state (only after user tests + `GO COMMIT`)?

---

## Post-Comparison Addendum (2026-09-06, implemented)

User decisions applied: fix tokens.txt ✅, STT cleanup already correct ✅, keep TTSManager ✅, keep generalized `.gitattributes` ✅, proceed with static fixes now ✅. Then, per user direction, an **independence + coding-style refactor** was completed:

- **New `transport/SocketConnection`** — neutral link-lifecycle interface (`DEFAULT_PORT 8988`, `ConnectionListener`, `startServer`/`connectToServer`/`stop`/`shutdown`).
- **`WifiDirectSocketManager`** — now `implements SocketConnection, MessageTransport`; no-arg ctor + `setConnectionListener`; cross-thread fields made `volatile`; magic numbers → constants; `closeQuietly` helper (no empty catch blocks).
- **`VoiceCommunication`** — holds both `MessageTransport` and `SocketConnection`; forwards connection events to the UI listener; `startConnection(boolean isGroupOwner, InetAddress)` / `stopConnection()`; `listener` `volatile`; `release()` shuts down everything.
- **`MainActivity`** — no longer references the socket class; socket created once in `initEngines` (composition root) and injected into the coordinator; implements `onConnectionEstablished`/`onConnectionLost`.
- **`WifiDirectBroadcastReceiver`** — deprecated `getParcelableExtra` replaced with API-safe (`api 33` gate) typed helper.

Result: UI ↔ modules fully decoupled; clean `assembleDebug` PASS; `lintDebug` 0 errors / 57 warnings (pre-existing).