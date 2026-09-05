# iTantra SIH — Changes Required

**Purpose:** Every mistake found, every change needed, and the reason why.
**Date:** 2026-09-05
**Source:** Read-only audit of `iTantra-sih` repo + `sih-project-agile` reference

---

## How to Read This Document

Each entry has:

```
MISTAKE:   What is wrong
FILE:      Where the problem is
BRANCH:    Which branch has this issue
CHANGE:    What needs to be done
WHY:       Why the change matters
PRIORITY:  When to fix it (sprint-4 first / later / optional)
```

---

## PART A — BUILD FIXES (Do These First)

---

### M001 — sprint-3 Cannot Build on Any Other Machine

```
MISTAKE:  gradle.properties hardcodes a path to a specific developer's JDK
FILE:     iTantraSIH/gradle.properties
BRANCH:   sprint-3
CHANGE:   Remove the line:
              org.gradle.java.home=C:\\Users\\jetti\\.jdks\\jbr-21.0.11
          Let Gradle pick up JAVA_HOME from the environment instead.
WHY:      Every other team member gets a "Java home supplied is invalid"
          error and cannot build. This is the single biggest blocker.
PRIORITY: FIRST — before any merge or integration
```

Evidence:
```
> Task :app:assembleDebug FAILED
FAILURE: Build failed with an exception.
* What went wrong:
Value 'C:\Users\jetti\.jdks\jbr-21.0.11' given for org.gradle.java.home
Gradle property is invalid (Java home supplied is invalid)
```

---

### M002 — Dead Dependency in sprint-3

```
MISTAKE:  assemblyai-java:1.1.2 is declared as a dependency but never used
FILE:     iTantraSIH/app/build.gradle
BRANCH:   sprint-3
CHANGE:   Remove this line from dependencies:
              implementation("com.assemblyai:assemblyai-java:1.1.2")
WHY:      1. AssemblyAI is a cloud STT API — contradicts offline requirement.
          2. Zero .java files reference it (verified with grep).
          3. It pulls in cloud/network dependencies into an offline app.
          4. Adds unnecessary weight to the APK.
PRIORITY: FIRST — clean before merge
```

Evidence:
```
$ git grep -i assemblyai origin/sprint-3 -- "*.java"
(no results)
```

---

## PART B — ARCHITECTURE CHANGES (Sprint-4 Core Work)

---

### M003 — MainActivity Is a God Class (sprint-3)

```
MISTAKE:  A single 600+ line Activity handles STT, Wi-Fi P2P, socket I/O,
          peer discovery, connection management, STT→send, receive→display,
          permissions, and all UI updates.
FILE:     iTantraSIH/app/src/main/java/.../MainActivity.java
BRANCH:   sprint-3
CHANGE:   Break apart into separate modules:
            1. WifiDirectManager — handles discovery, connection, socket lifecycle
            2. SpeechManager — handles STT init, start, stop, results
            3. MainActivity — only UI wiring, delegates to managers
WHY:      The agile architecture says:
            "UI should not directly control STT, TTS, or Wi-Fi"
            "Wi-Fi code should not know anything about STT or TTS"
          Currently: UI directly creates OfflineSTT, directly calls
          socketManager.sendMessage, directly handles all callbacks.
          This makes it impossible to:
            - Test STT alone
            - Test Wi-Fi alone
            - Test TTS alone
            - Swap any module without rewriting everything
PRIORITY: HIGH — core of sprint-4 integration work
```

---

### M004 — No Message Model Exists Anywhere

```
MISTAKE:  Raw strings are sent directly over sockets. No common data structure.
FILE:     (missing — needs to be created)
BRANCH:   all
CHANGE:   Create models/Message.java with at minimum:
              class Message {
                  String text;
                  String senderId;
                  long timestamp;
                  String language;
                  String toJson();
                  static Message fromJson(String json);
              }
          Update WifiDirectSocketManager to send/receive JSON Messages
          instead of raw strings.
WHY:      1. Agile spec explicitly requires models/Message.java in Sprint 1.
          2. Without a message model, you cannot:
             - Distinguish who sent a message
             - Add timestamps for ordering
             - Route messages by language
             - Support message types (text, status, control)
             - Debug communication issues
          3. Raw println/println with no framing means newline characters
             inside a message will split it into multiple messages on the
             receiver side.
PRIORITY: HIGH — needed before integration testing
```

---

### M005 — No Application Coordinator

```
MISTAKE:  No VoiceCommunication or equivalent class exists to wire
          STT → Message → Wi-Fi → Message → TTS
FILE:     (missing — needs to be created)
BRANCH:   all
CHANGE:   Create application/VoiceCommunication.java that:
            1. Takes references to STTEngine, TTSEngine, WifiManager
            2. When STT produces text → wraps in Message → sends via Wi-Fi
            3. When Wi-Fi receives Message → extracts text → sends to TTS
          This class should NOT contain:
            - STT model code
            - TTS model code
            - Socket code
            - UI code
WHY:      Agile Sprint 4 says:
            "Sprint 4 must treat Sprint 1-3 as black boxes"
            "If Sprint 2 replaces its TTS model, Sprint 4 should
             continue working without changes"
          Currently there is NO class that connects these modules.
          The only "connection" is in MainActivity's STT callback,
          which is hardcoded and can only send (not receive→TTS).
PRIORITY: HIGH — this IS sprint-4
```

---

### M006 — STT Receive Is Not Connected to TTS

```
MISTAKE:  When phone B receives a text message from phone A,
          it displays the text but does NOT speak it through TTS.
FILE:     iTantraSIH/app/src/main/java/.../MainActivity.java
BRANCH:   sprint-3
CHANGE:   In onMessageReceived(), after displaying the text,
          also call TTSManager.speak(message):
            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> {
                    appendReceivedData(message);
                    // ADD THIS:
                    if (ttsManager != null) {
                        ttsManager.speak(message);
                    }
                });
            }
WHY:      The whole point of iTantra is voice-to-voice communication.
          Phone A speaks → text is sent → Phone B should SPEAK the text.
          Currently it only displays it. Half the pipeline is missing.
PRIORITY: HIGH — this is the sprint-4 core integration
```

---

### M007 — TTS Code Not Available in sprint-3

```
MISTAKE:  sprint-3 has no TTS classes at all. It only has STT + Wi-Fi.
          There is no PiperEngine, TTSEngine, TTSManager, or any TTS code.
BRANCH:   sprint-3
CHANGE:   Sprint-4 must bring TTS code from sprint-2 into sprint-3's base.
          Options:
            A) Cherry-pick TTS files from sprint-2 into sprint-4
            B) Merge sprint-2 into sprint-4 (will cause conflicts)
            C) Start from sprint-3, manually copy TTS files
WHY:      sprint-3 = STT + WiFi
          sprint-2 = TTS
          sprint-4 = all three connected
          TTS files must exist in sprint-4 for the voice loop to work.
PRIORITY: FIRST — needed for any integration
```

---

## PART C — FILE DELETIONS (Dead Code Cleanup)

---

### M008 — OnnxModelLoader.java Is Dead Code

```
MISTAKE:  OnnxModelLoader is never called from any file
FILE:     iTantraSIH/app/src/main/java/.../speech/tts/OnnxModelLoader.java
BRANCH:   sprint-2
CHANGE:   Delete the file
WHY:      Zero callers. It's a standalone utility that loads an ONNX model
          and prints metadata. Useful for debugging but not part of the
          application. Adding dead code to the integrated build adds confusion.
PRIORITY: LOW — clean up during sprint-4
```

---

### M009 — TinyTTS + Pipeline + Text Processing (~1500 Lines Dead Code)

```
MISTAKE:  7 Java files implement a custom ONNX TTS chain that is
          self-described as "a plumbing test, NOT real speech synthesis"
FILES:    - TinyTTS.java
          - TinyTTSPipeline.java
          - text/G2P.java
          - text/Normalizer.java
          - text/PhonemeIds.java
BRANCH:   sprint-2
CHANGE:   DECISION NEEDED from user:
            Option A: Delete all 7 files + associated assets (~58MB)
            Option B: Keep as experimental fallback code
          If kept, they should be clearly marked as experimental.
WHY:      1. Never instantiated — zero callers.
          2. TinyTTSPipeline docstring: "This is a plumbing test, NOT
             real speech synthesis."
          3. Associated assets (4 ONNX models + CMU dict) = ~58MB of
             dead weight in the APK.
          4. PiperEngine is the actual working TTS.
PRIORITY: DECISION REQUIRED from user
```

---

### M010 — OfflineTTS.java (Unused Android TTS Wrapper)

```
MISTAKE:  OfflineTTS wraps Android's native TextToSpeech but is never used
FILE:     iTantraSIH/app/src/main/java/.../speech/tts/OfflineTTS.java
BRANCH:   sprint-2
CHANGE:   DECISION NEEDED from user:
            Option A: Delete (it's unused and Android TTS may not work offline)
            Option B: Keep as a fallback if PiperEngine fails
          If kept, remove the unused import in MainActivity.java.
WHY:      1. Constructor never called from any active code path.
          2. Android native TTS depends on device manufacturer — some
             devices require internet for first-time setup.
          3. Does NOT follow the TTSEngine interface lifecycle (no init method).
PRIORITY: DECISION REQUIRED from user
```

---

### M011 — Unused Import in MainActivity (sprint-2)

```
MISTAKE:  `import com.example.itantra_sih.speech.tts.OfflineTTS;` in MainActivity
          but OfflineTTS is never used
FILE:     iTantraSIH/app/src/main/java/.../MainActivity.java
BRANCH:   sprint-2
CHANGE:   Remove the unused import line
WHY:      Unused import — code quality
PRIORITY: LOW — mechanical fix
```

---

## PART D — STT MODULE FIXES

---

### M012 — STT Module Is Duplicated Across sprint-1 and sprint-3

```
MISTAKE:  STTEngine.java and OfflineSTT.java are byte-identical in
          sprint-1 and sprint-3. The Vosk model-en-us assets are
          also duplicated (~60MB each branch).
FILES:    - speech/stt/STTEngine.java
          - speech/stt/OfflineSTT.java
          - assets/model-en-us/* (~60MB)
BRANCH:   sprint-1 AND sprint-3
CHANGE:   Sprint-4 should use only one copy (from sprint-3).
          Sprint-1's STT becomes unnecessary once sprint-3 exists.
WHY:      Redundancy wastes space and creates confusion about which
          version is authoritative.
PRIORITY: LOW — sprint-4 automatically resolves this
```

---

### M013 — STT Lifecycle: stop() Doesn't Clean Up Recognizer

```
MISTAKE:  OfflineSTT.stop() calls speechService.stop() and nulls it,
          but does NOT close the Recognizer. When start() is called
          again, it creates a NEW Recognizer without closing the old one.
FILE:     iTantraSIH/app/src/main/java/.../speech/stt/OfflineSTT.java
BRANCH:   sprint-1, sprint-3
CHANGE:   In stop(), add recognizer cleanup:
            public void stop() {
                if (speechService != null) {
                    speechService.stop();
                    speechService = null;
                }
                if (recognizer != null) {
                    recognizer.close();
                    recognizer = null;
                }
            }
WHY:      The old Recognizer holds native memory. Without closing it,
          native memory leaks until the JVM's garbage collector runs
          (which may never collect native refs). On repeated
          start/stop cycles, this accumulates.
PRIORITY: MEDIUM — fix during sprint-4 STT stabilization
```

---

### M014 — acceptAudio() Is Declared But Never Used

```
MISTAKE:  STTEngine interface declares acceptAudio(byte[], int) but:
          1. OfflineSTT says "Handled automatically by SpeechService"
          2. No code ever calls it
FILES:    - speech/stt/STTEngine.java (declaration)
          - speech/stt/OfflineSTT.java (empty body)
BRANCH:   sprint-1, sprint-3
CHANGE:   DECISION NEEDED:
            Option A: Remove from interface and implementation
            Option B: Keep for future manual audio feeding
          If removed, the interface becomes: init/start/stop/destroy/setOnResultListener
WHY:      Confusing API surface — implementers might think they need to
          feed audio data manually, but the SpeechService handles it.
PRIORITY: LOW — cleanup
```

---

## PART E — TTS MODULE FIXES

---

### M015 — TTS Lifecycle Asymmetry

```
MISTAKE:  STT uses: init(Context, listener) → start() → stop() → destroy()
          TTS uses: constructor(context) → speak(text) → stop() → release()
          Different lifecycle patterns make the coordinator harder to write.
FILE:     TTSEngine.java vs STTEngine.java
BRANCH:   sprint-2 vs sprint-1/3
CHANGE:   DECISION NEEDED:
            Option A: Add init() method to TTSEngine for consistency
            Option B: Accept the asymmetry (simpler, fewer methods)
            Option C: Make STT also use constructor-based init
WHY:      The coordinator (VoiceCommunication) needs to manage both
          STT and TTS. Asymmetric lifecycles add complexity.
PRIORITY: LOW — can work with asymmetry
```

---

### M016 — PiperEngine Starts Loading in Constructor

```
MISTAKE:  PiperEngine calls loadAsync() in its constructor, which spawns
          a thread to extract espeak-ng-data to disk and load the ONNX
          model. If the TTS engine is never used, resources are wasted.
FILE:     iTantraSIH/app/src/main/java/.../speech/tts/PiperEngine.java
BRANCH:   sprint-2
CHANGE:   DECISION NEEDED:
            Option A: Accept eager loading (simpler, model ready faster)
            Option B: Add explicit init() method like STTEngine
          If keeping eager loading, no change needed.
WHY:      Resource management concern. On low-RAM devices, loading a
          63MB ONNX model + espeak-ng-data extraction in background
          may consume significant memory before TTS is needed.
PRIORITY: LOW — works but not ideal
```

---

### M017 — PiperEngine.stop() Doesn't Stop Synthesis

```
MISTAKE:  stop() only sets currentTrack=null to stop playback, but the
          synthesis thread may still be blocked in tts.generate() or
          track.write(). The worker thread is not interrupted.
FILE:     iTantraSIH/app/src/main/java/.../speech/tts/PiperEngine.java
BRANCH:   sprint-2
CHANGE:   Add a volatile `stopRequested` flag:
            - Set it in stop()
            - Check it before tts.generate() and after
            - Interrupt the worker thread
            - Reset it in the next speak() call
WHY:      User presses "Stop" → expects immediate silence.
          Currently, if synthesis is running, it continues until
          the AudioTrack buffer finishes playing.
PRIORITY: MEDIUM — noticeable UX issue
```

---

## PART F — WIFI MODULE FIXES

---

### M018 — Thread Leak in WifiDirectSocketManager

```
MISTAKE:  sendExecutor (ExecutorService) is created in the constructor
          but never shutDown() in stop(). After multiple connect/disconnect
          cycles, executor threads accumulate.
FILE:     iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
BRANCH:   sprint-3
CHANGE:   Add to stop():
            sendExecutor.shutdownNow();
WHY:      Each connect/disconnect cycle creates a new ExecutorService
          (because the manager is NOT recreated — it's created once in
          initViews). The old executor is never reclaimed. After N cycles,
          N executor threads are leaked.
PRIORITY: MEDIUM — fix during sprint-4 WiFi stabilization
```

---

### M019 — Line-Based Transport Has No Message Framing

```
MISTAKE:  Messages are sent with println() and received with readLine().
          If a message contains a newline character, it will be split
          into multiple messages on the receiver.
FILE:     iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
BRANCH:   sprint-3
CHANGE:   DECISION NEEDED (must be decided before sprint-4 integration):
            Option A: Length-prefixed messages
                Send: write(int length) + write(bytes)
                Receive: read int → read that many bytes
            Option B: JSON messages with newline escaping
                Send: write(jsonString + "\n")
                Receive: readLine() — but must escape \n in content
            Option C: Keep simple (risk: STT text rarely has newlines)
WHY:      STT output is usually a single line, so this works in practice.
          But for robustness, especially with longer text, multi-line
          content (pasted text, paragraphs), proper framing is needed.
PRIORITY: MEDIUM — should decide before integration testing
```

---

### M020 — Server Socket Is One-Shot

```
MISTAKE:  After one client connects to the server socket, the server thread
          exits. If the client disconnects and reconnects, the server is dead.
FILE:     iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
BRANCH:   sprint-3
CHANGE:   Add a loop around the accept() call:
            while (isRunning) {
                Socket socket = serverSocket.accept();
                // handle client
                // when client disconnects, loop back to accept()
            }
          OR re-initiate server on disconnect.
WHY:      In the voice communication use case, Wi-Fi Direct may reconnect.
          The server must be ready for new connections.
PRIORITY: MEDIUM — depends on reconnect strategy
```

---

### M021 — Wi-Fi Receive Callback Runs on Background Thread

```
MISTAKE:  WifiDirectSocketManager.startReceiving() runs on a background
          thread. The listener.onMessageReceived() is called from this
          thread. The caller (MainActivity) wraps UI updates in
          runOnUiThread(), but the callback itself is not documented
          as thread-safe.
FILE:     iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
BRANCH:   sprint-3
CHANGE:   DECISION NEEDED:
            Option A: Document that callbacks arrive on background thread
                      (callers must use runOnUiThread)
            Option B: Wrap callbacks in Handler(Looper.getMainLooper()).post()
                      so all callbacks arrive on main thread
            Option B is safer and matches how Android frameworks work.
WHY:      Currently works because MainActivity uses runOnUiThread().
          But any new caller might forget this and crash.
PRIORITY: LOW — works but fragile
```

---

## PART G — BUILD SYSTEM FIXES

---

### M022 — Merge Will Conflict on Multiple Files

```
MISTAKE:  Merging sprint-2 + sprint-3 will create conflicts in at least
          5 files because both branches modified the same files.
FILES:    - MainActivity.java (completely different in each branch)
          - activity_main.xml (completely different in each branch)
          - build.gradle (different dependencies)
          - gradle.properties (sprint-3 has hardcoded JDK line)
          - AndroidManifest.xml (different permission sets)
BRANCH:   sprint-2 + sprint-3
CHANGE:   Sprint-4 strategy (DECISION NEEDED):
            Option A: Start from sprint-3, manually add TTS files
                      (avoids merge conflicts, more control)
            Option B: Merge sprint-2 into sprint-3 and resolve conflicts
                      (preserves git history, but more work)
            Option C: Create fresh from main, bring files from both
                      (cleanest but most work)
WHY:      The merge is non-trivial because both branches rewrote
          MainActivity.java from scratch (different code, different UI).
          Automated merge will fail on every shared file.
PRIORITY: DECISION REQUIRED — determines sprint-4 workflow
```

---

### M023 — Missing aaptOptions for ONNX in sprint-2

```
MISTAKE:  sprint-1/sprint-3 have aaptOptions for 'tflite', 'bin', 'fst',
          'mdl', 'raw' but not 'onnx'.
          sprint-2 does NOT have aaptOptions at all (uses packagingOptions).
          ONNX model files in assets will be compressed by default.
FILE:     iTantraSIH/app/build.gradle
BRANCH:   sprint-2
CHANGE:   Add to android{} block:
            aaptOptions {
                noCompress 'onnx', 'tflite', 'bin', 'fst', 'mdl', 'raw'
            }
WHY:      Compressing ONNX models means they must be decompressed when
          loaded into memory. For large models (63MB Piper), this adds
          startup latency. OnnxRuntime reads them as byte arrays anyway,
          so compression wastes CPU for no savings.
PRIORITY: LOW — performance optimization
```

---

## PART H — ASSET REDUCTION (Sprint-4 After Integration)

---

### M024 — espeak-ng-data Contains ALL Languages

```
MISTAKE:  The espeak-ng-data directory contains dictionaries and voice data
          for 100+ languages. Only English is used.
FILE:     iTantraSIH/app/src/main/assets/lespk/espeak-ng-data/
BRANCH:   sprint-2
CHANGE:   DECISION NEEDED:
            Option A: Keep all languages (future multilingual support)
            Option B: Strip to only English (en_dict + lang/gmw/en*)
                      Save ~15MB
            Option C: Keep Indian languages (hi, te, ta, kn, ml, bn, etc.)
                      Save ~10MB
WHY:      Each dictionary is 5KB-8MB. The ru_dict alone is 8.5MB.
          For the MVP, only English STT+TTS is proven. Indian language
          support would require new STT models anyway.
PRIORITY: DECISION REQUIRED — affects APK size
```

---

### M025 — TinyTTS Assets Are Dead Weight

```
MISTAKE:  4 ONNX models + CMU dictionary (~58MB total) are in assets
          but TinyTTS is dead code
FILES:    - assets/tinytts/text_encoder.onnx (3MB)
          - assets/tinytts/duration_predictor.onnx (1MB)
          - assets/tinytts/flow.onnx (15MB)
          - assets/tinytts/decoder.onnx (14MB)
          - assets/tinytts/cmudict.rep (129K lines)
BRANCH:   sprint-2
CHANGE:   Delete these 5 files IF TinyTTS is decided to be removed (see M009)
WHY:      58MB of assets for code that is never used.
PRIORITY: Depends on M009 decision
```

---

## PART I — MISSING FEATURES (Sprint-4 Must Create)

---

### M026 — No Bidirectional Voice Loop

```
MISTAKE:  Sprint-4 requires A speaks → B receives + B speaks → A receives
          Currently sprint-3 only does A speaks → B displays text
          The reverse direction (B → A) is not implemented.
BRANCH:   sprint-3
CHANGE:   Sprint-4 must ensure:
            1. Both phones run the same app
            2. Both have STT enabled (Start speaking button)
            3. Both have TTS enabled (receive → speak)
            4. Both can send and receive
WHY:      The agile spec says:
            "Phone A captures speech → STT → text → Wi-Fi → Phone B
             receives text → TTS → speaker. Then reverse it."
          Currently only the A→B direction works, and even that only
          displays text (no TTS).
PRIORITY: HIGH — this IS the sprint-4 goal
```

---

### M027 — No Language Selection UI

```
MISTAKE:  Language is hardcoded everywhere:
          - STT: "model-en-us" (English only)
          - TTS: "en_IN-spicor" (English only)
          - No UI to select language
BRANCH:   all
CHANGE:   FOR NOW (sprint-4 MVP): Accept English-only.
          FOR LATER: Add language selector that swaps STT/TTS models.
WHY:      Agile architecture says:
            "The system must eventually support language selection."
            "Do not hardcode one language into the overall architecture."
          For sprint-4 MVP, English-only is acceptable but the architecture
          should not prevent future language addition.
PRIORITY: LOW for MVP — MEDIUM for future sprints
```

---

### M028 — No Error Handling in Integration Layer

```
MISTAKE:  No module has structured error handling:
          - STT error: shows Toast only
          - TTS error: logged only
          - Wi-Fi error: logged only
          - No user-visible error state for connection failures
          - No retry logic when TTS fails
BRANCH:   sprint-2, sprint-3
CHANGE:   Sprint-4 should add:
            1. Connection status indicator in UI
            2. Error callback to show user-friendly messages
            3. Basic retry for socket connection
            4. Graceful degradation (show text if TTS fails)
WHY:      Real-world usage will encounter: no peer found, connection lost,
          mic permission denied, model not loaded. The user needs feedback.
PRIORITY: MEDIUM — important for real usage
```

---

## PART J — AGILE MISMATCHES (Informational)

---

### M029 — Branch Names Don't Match Agile Sprint Numbers

```
MISTAKE:  Agile Sprint 1 = Wi-Fi messaging
          Agile Sprint 2 = TTS
          Agile Sprint 3 = STT
          
          But in the repo:
          sprint-1 branch = STT (not Wi-Fi)
          sprint-2 branch = TTS (matches)
          sprint-3 branch = STT + Wi-Fi (not standalone STT)
BRANCH:   all
CHANGE:   No change needed — this is just a naming mismatch.
          Sprint-4 must understand the actual mapping.
WHY:      If someone reads the agile spec and looks at sprint-1,
          they'll expect Wi-Fi code but find STT code.
PRIORITY: INFORMATIONAL — no action needed
```

---

### M030 — Agile Wants com.itantra, Repo Uses com.example.itantra_sih

```
MISTAKE:  Agile spec says package name should be com.itantra.*
          Actual code uses com.example.itantra_sih.*
BRANCH:   all
CHANGE:   DECISION NEEDED:
            Option A: Keep com.example.itantra_sih (less work, just for dev)
            Option B: Rename to com.itantra (matches spec, more work)
          Renaming the package affects every file's package declaration,
          all imports, and possibly R.java generation.
WHY:      "com.example" is the Android Studio default for new projects.
          For a real product, it should be a proper namespace.
          For a hackathon/project, com.example is fine.
PRIORITY: DECISION REQUIRED — but low urgency
```

---

## SUMMARY — Priority Order

### Must Fix Before Sprint-4 Can Start
| ID | Priority | What |
|----|----------|------|
| M001 | P0 | Remove hardcoded JDK path (sprint-3 build fix) |
| M002 | P2 | Remove dead assemblyai dependency (sprint-3 cleanup) |

### Sprint-4 Core Work (Integration)
| ID | Priority | What |
|----|----------|------|
| M007 | HIGH | Bring TTS code from sprint-2 into sprint-4 |
| M003 | HIGH | Break apart monolithic MainActivity |
| M004 | HIGH | Create Message model |
| M005 | HIGH | Create VoiceCommunication coordinator |
| M006 | HIGH | Connect receive → TTS playback |
| M026 | HIGH | Enable bidirectional voice loop |
| M022 | REQUIRED | Decide merge strategy (how to combine sprint-2 + sprint-3) |

### Sprint-4 Stabilization
| ID | Priority | What |
|----|----------|------|
| M013 | MEDIUM | Fix STT recognizer cleanup in stop() |
| M017 | MEDIUM | Fix PiperEngine stop() behavior |
| M018 | MEDIUM | Fix thread leak in WifiDirectSocketManager |
| M019 | MEDIUM | Decide message framing format |
| M020 | MEDIUM | Fix one-shot server socket |
| M028 | MEDIUM | Add error handling to integration |

### Cleanup / Code Quality
| ID | Priority | What |
|----|----------|------|
| M008 | LOW | Delete OnnxModelLoader.java |
| M011 | LOW | Remove unused import in MainActivity |
| M014 | LOW | Remove acceptAudio from interface |
| M023 | LOW | Add aaptOptions for ONNX |
| M024 | LOW | Consider stripping unused espeak-ng languages |

### User Decisions Required
| ID | Decision |
|----|----------|
| M009 | Keep or delete TinyTTS + pipeline (~1500 lines + 58MB assets)? |
| M010 | Keep or delete OfflineTTS.java (Android TTS wrapper)? |
| M014 | Remove acceptAudio() from STTEngine interface? |
| M019 | Message framing: length-prefix / JSON / keep simple? |
| M022 | Merge strategy: cherry-pick / merge / fresh start? |
| M024 | Strip espeak-ng to English-only or keep all languages? |
| M030 | Keep com.example.itantra_sih or rename to com.itantra? |

---

*End of Changes Required*
