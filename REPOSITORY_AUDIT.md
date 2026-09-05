# iTantra SIH — Complete Repository Audit

**Audit Date:** 2026-09-05
**Repository:** https://github.com/HaRsHa91544/iTantra-sih
**Agile Reference:** https://github.com/HaRsHa91544/sih-project-agile
**Current Checkout:** `sprint-2` (clean, up to date with `origin/sprint-2`)
**Auditor Mode:** Read-only. No files modified, no commits, no pushes.

---

## Table of Contents

1. [Git Status & Branches](#1-git-status--branches)
2. [Branch Topology](#2-branch-topology)
3. [Branch Contents — sprint-1](#3-branch-contents--sprint-1)
4. [Branch Contents — sprint-2](#4-branch-contents--sprint-2)
5. [Branch Contents — sprint-3](#5-branch-contents--sprint-3)
6. [Branch Contents — main](#6-branch-contents--main)
7. [Agile Plan vs Actual Mapping](#7-agile-plan-vs-actual-mapping)
8. [Build Status (Verified)](#8-build-status-verified)
9. [Issue Register](#9-issue-register)
10. [Dependency Graph](#10-dependency-graph)
11. [Module Independence Audit](#11-module-independence-audit)
12. [Language Support Status](#12-language-support-status)
13. [Model Status](#13-model-status)
14. [Test Status](#14-test-status)
15. [Performance Summary](#15-performance-summary)
16. [Integration Readiness](#16-integration-readiness)

---

## 1. Git Status & Branches

```
On branch sprint-2
Your branch is up to date with 'origin/sprint-2'.
nothing to commit, working tree clean
```

### Local Branches
| Branch | Exists |
|--------|--------|
| `main` | YES |
| `sprint-1` | NO (remote only) |
| `sprint-2` | YES (current) |
| `sprint-3` | NO (remote only) |
| `sprint-4` | NO |

### Remote Tracking Branches
| Branch | Tip Commit | Message |
|--------|------------|---------|
| `origin/main` | `e80e8db` | Merge pull request #1 from sprint-1 |
| `origin/sprint-1` | `1d55da8` | Basic working STT model is done |
| `origin/sprint-2` | `7a01186` | Added the models for TTS |
| `origin/sprint-3` | `53069ce` | Basic "voice input" - "data transfer (WIFI-DIRECT)" is done |

---

## 2. Branch Topology

```
e7dbd43  Initial commit                       ←── main lineage
c630e77  Update project title with year        ←── main lineage
f2a41d8  initialize Android project            ←── sprint-1 lineage
9cac4d0  second commit                         ←── sprint-1 lineage
│
├── 274bf62  Basic working STT model (draft)   ←── sprint-1
├── 1d55da8  Basic working STT model is done   ←── sprint-1 TIP
│
├── 00c20a3  Basic voice input + WIFI-DIRECT   ←── sprint-3 (off sprint-1 tip)
├── 53069ce  Basic voice input + WIFI-DIRECT   ←── sprint-3 TIP
│
e80e8db  Merge PR #1                          ←── main TIP
│
├── 8067aa7  TTS Engine and OfflineTTS done    ←── sprint-2
├── d49bedb  Added TTSManager                  ←── sprint-2
├── 7a01186  Added the models for TTS          ←── sprint-2 TIP
```

Key observations:
- `sprint-3` is a child of `sprint-1` (STT branch). It builds on STT and adds Wi-Fi.
- `sprint-2` is a child of `main`. It builds on the clean scaffold and adds TTS.
- `sprint-1` STT code was merged to `main` via PR #1, but the STT files did NOT land in `main`'s tree (merge kept the scaffold only).
- `sprint-2` and `sprint-3` have **no common ancestor** beyond `main`/scaffold.

---

## 3. Branch Contents — sprint-1 (1d55da8)

**Intended purpose (agile):** Wi-Fi Messaging
**Actual code:** STT (Speech-to-Text) POC

### Files

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/example/itantra_sih/speech/stt/STTEngine.java` | Interface: init/start/stop/destroy/setOnResultListener/acceptAudio |
| `app/src/main/java/com/example/itantra_sih/speech/stt/OfflineSTT.java` | Vosk SpeechService-based STT implementation |
| `app/src/main/java/com/example/itantra_sih/MainActivity.java` | Navigation button → MainActivity3 |
| `app/src/main/java/com/example/itantra_sih/MainActivity3.java` | STT test activity: record → transcribe → display |
| `app/src/main/res/layout/activity_main.xml` | "Hello World" + navigation button |
| `app/src/main/res/layout/activity_main3.xml` | STT test: model status + transcribed text + toggle button |
| `app/src/main/assets/model-en-us/` | Vosk English model (~60MB) |
| `app/build.gradle` | vosk-android:0.3.75 dependency |

### Manifest Permissions
- `RECORD_AUDIO`

### Interface STTEngine Methods
```java
void init(Context context, OnInitListener listener);
void setOnResultListener(OnResultListener listener);
void start();
void acceptAudio(byte[] data, int length);   // declared but NO-OP in OfflineSTT
void stop();
void destroy();
```

### OfflineSTT Behavior
- Uses `StorageService.unpack()` to extract `model-en-us` from assets to internal storage
- Creates `Recognizer` + `SpeechService` on `start()`
- SpeechService handles microphone internally (Vosk does the AudioRecord)
- `acceptAudio()` is declared in the interface but the implementation says `// Handled automatically by SpeechService`
- On `stop()`: calls `speechService.stop()` and nulls it — does NOT call `shutdown()`, does NOT close `recognizer`
- On `destroy()`: stops + shuts down speechService, closes recognizer, closes model
- `onTimeout()` is overridden but does nothing (no callback forwarding)

### Build Status
**VERIFIED: BUILD SUCCESSFUL** (tested 2026-09-05)

---

## 4. Branch Contents — sprint-2 (7a01186)

**Intended purpose (agile):** Offline TTS
**Actual code:** Offline TTS POC (matches agile)

### Files

| Path | Purpose |
|------|---------|
| `app/src/main/java/.../speech/tts/TTSEngine.java` | Interface: speak/stop/release |
| `app/src/main/java/.../speech/tts/OfflineTTS.java` | Android native TextToSpeech wrapper |
| `app/src/main/java/.../speech/tts/PiperEngine.java` | sherpa-onnx Piper TTS engine |
| `app/src/main/java/.../speech/tts/TTSManager.java` | Thin facade over TTSEngine |
| `app/src/main/java/.../speech/tts/TinyTTS.java` | Custom ONNX 4-model chain engine |
| `app/src/main/java/.../speech/tts/TinyTTSPipeline.java` | ONNX inference pipeline |
| `app/src/main/java/.../speech/tts/OnnxModelLoader.java` | Model inspection utility |
| `app/src/main/java/.../speech/tts/text/G2P.java` | Grapheme-to-phoneme (English) |
| `app/src/main/java/.../speech/tts/text/Normalizer.java` | Text normalization (numbers, time, abbreviations) |
| `app/src/main/java/.../speech/tts/text/PhonemeIds.java` | Phoneme → integer ID mapping |
| `app/libs/sherpa-onnx-1.13.7.aar` | Local AAR (~38MB) |
| `app/src/main/assets/piper/en_IN-spicor-medium.onnx` | Piper model (~63MB) |
| `app/src/main/assets/piper/tokens.txt` | Piper token vocabulary (161 lines) |
| `app/src/main/assets/lespk/espeak-ng-data/` | espeak-ng phonemizer data (~20MB, ALL languages) |
| `app/src/main/assets/tinytts/text_encoder.onnx` | TinyTTS encoder (~3MB) |
| `app/src/main/assets/tinytts/duration_predictor.onnx` | TinyTTS duration predictor (~1MB) |
| `app/src/main/assets/tinytts/flow.onnx` | TinyTTS flow model (~15MB) |
| `app/src/main/assets/tinytts/decoder.onnx` | TinyTTS decoder (~14MB) |
| `app/src/main/assets/tinytts/cmudict.rep` | CMU pronunciation dictionary (129,530 lines) |
| `app/build.gradle` | onnxruntime-android:1.29.0 + local sherpa-onnx AAR |

### Manifest Permissions
NONE (no RECORD_AUDIO, no Wi-Fi, no location)

### Dependencies
```groovy
implementation libs.appcompat
implementation libs.material
implementation libs.activity
implementation libs.constraintlayout
implementation libs.onnxruntime.android             // onnxruntime 1.29.0
implementation files('libs/sherpa-onnx-1.13.7.aar') // local binary
```

### Packaging
```groovy
packagingOptions {
    jniLibs {
        pickFirsts += ['**/libonnxruntime.so']  // both onnxruntime + sherpa bundle this
    }
}
```

### Three TTS Engines

| Engine | Class | Status | Notes |
|--------|-------|--------|-------|
| Piper | `PiperEngine` | **ACTIVE** (wired in MainActivity) | sherpa-onnx, en_IN voice |
| Android TTS | `OfflineTTS` | UNUSED | Imported in MainActivity but never instantiated |
| TinyTTS | `TinyTTS` + `TinyTTSPipeline` | DEAD CODE | Self-described as "plumbing test, NOT real speech synthesis" |

### Dead Code Inventory

| File | Evidence |
|------|----------|
| `OnnxModelLoader.java` | `git grep "OnnxModelLoader"` returns only its own definition — zero callers |
| `OfflineTTS.java` | Imported in `MainActivity.java` but constructor never called |
| `TinyTTS.java` | Only referenced by itself and TinyTTSPipeline — never instantiated |
| `TinyTTSPipeline.java` | Only referenced by TinyTTS — never instantiated |
| `text/G2P.java` | Only referenced by TinyTTSPipeline |
| `text/Normalizer.java` | Only referenced by TinyTTSPipeline.synthesize() |
| `text/PhonemeIds.java` | Only referenced by G2P and TinyTTSPipeline |

### MainActivity (sprint-2)
```java
ttsManager = new TTSManager(new PiperEngine(this));  // Piper only
speakButton → ttsManager.speak("Define the data used between modules");
stopButton  → ttsManager.stop();
onDestroy   → ttsManager.release();
```
Unused import: `com.example.itantra_sih.speech.tts.OfflineTTS`

### Build Status
**VERIFIED: BUILD SUCCESSFUL** (tested 2026-09-05, 36 seconds)

---

## 5. Branch Contents — sprint-3 (53069ce)

**Intended purpose (agile):** STT (standalone)
**Actual code:** STT + Wi-Fi Direct integration (STT→text→Wi-Fi socket→display)

### Files

| Path | Purpose |
|------|---------|
| `app/src/main/java/.../speech/stt/STTEngine.java` | Same interface as sprint-1 |
| `app/src/main/java/.../speech/stt/OfflineSTT.java` | Same Vosk STT as sprint-1 |
| `app/src/main/java/.../wifidirect/DeviceAdapter.java` | ListView adapter for Wi-Fi P2P peers |
| `app/src/main/java/.../wifidirect/WifiDirectBroadcastReceiver.java` | BroadcastReceiver for Wi-Fi P2P events |
| `app/src/main/java/.../wifidirect/WifiDirectSocketManager.java` | TCP socket server/client manager |
| `app/src/main/java/.../MainActivity.java` | **Monolithic** (~600 lines): STT + Wi-Fi + UI |
| `app/src/main/res/layout/activity_main.xml` | Full Wi-Fi Direct UI (~300 lines XML) |
| `app/src/main/res/layout/item_device.xml` | Peer list item layout |
| `app/src/main/res/values/colors.xml` | 4 additional colors (connected/disconnected/discovering/card_bg) |
| `app/src/main/assets/model-en-us/` | Same Vosk English model as sprint-1 |
| `app/build.gradle` | vosk-android:0.3.75 + assemblyai-java:1.1.2 |
| `app/src/main/AndroidManifest.xml` | Wi-Fi + Location + Audio + Nearby permissions |

### Manifest Permissions
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="tiramisu" />
```

### Dependencies
```groovy
implementation 'com.alphacephei:vosk-android:0.3.75'
implementation libs.appcompat
implementation libs.material
implementation libs.activity
implementation libs.constraintlayout
implementation("com.assemblyai:assemblyai-java:1.1.2")  // DEAD DEPENDENCY
```

### gradle.properties
```properties
org.gradle.java.home=C:\\Users\\jetti\\.jdks\\jbr-21.0.11  // MACHINE-SPECIFIC — BREAKS BUILD
```

### Dead Dependency
`com.assemblyai:assemblyai-java:1.1.2` — declared in `app/build.gradle` but `git grep -i assemblyai` returns ZERO results across all .java files. AssemblyAI is a cloud STT API, contradicting the offline requirement.

### MainActivity (sprint-3) — Data Flow
```
onCreate
  → initViews()
  → initWifiDirect()      // WifiP2pManager + BroadcastReceiver
  → initSTTEngine()       // OfflineSTT → STTEngine.OnResultListener
  → setupListeners()      // button clicks
  → checkAndRequestPermissions()

User clicks "Discover Devices"
  → wifiP2pManager.discoverPeers()
  → BroadcastReceiver.onPeersDiscovered() → UI list

User clicks device in list
  → confirmAndConnect() → connectToDevice()
  → wifiP2pManager.connect()
  → BroadcastReceiver.onConnectionInfoAvailable()
    → if groupOwner: socketManager.startServer()
    → else: socketManager.startClient(groupOwnerAddress)

User clicks "Start speaking"
  → sttEngine.start()
  → STTEngine.OnResultListener.onFinalResult(text)
    → socketManager.sendMessage(text)   // <-- STT result goes DIRECTLY to Wi-Fi

Peer sends message
  → socketManager receives via BufferedReader
  → onMessageReceived(text)
    → appendReceivedData(text)  // displayed on screen only, NOT played as TTS
```

### WifiDirectSocketManager Details
- Server: `ServerSocket.accept()` — single client, no multi-client support
- Client: 5 retries, 1s delay between attempts, 5s connect timeout
- Transport: Plain TCP sockets, line-based (`PrintWriter.println` / `BufferedReader.readLine`)
- **No message framing** — newline characters in message will split into multiple messages
- **No encryption** — plaintext over local Wi-Fi Direct
- `sendExecutor` never shut down in `stop()` → thread leak
- Server thread blocked on `accept()` — no graceful shutdown (relies on socket close to unblock)

### WifiDirectBroadcastReceiver
- Registered with `ContextCompat.RECEIVER_EXPORTED` — correct for system broadcasts on API 33+
- Handles: WIFI_P2P_STATE_CHANGED, PEERS_CHANGED, CONNECTION_CHANGED, THIS_DEVICE_CHANGED

### Files Deleted vs sprint-1
- `MainActivity3.java` — removed (sprint-3 folded everything into MainActivity)
- `activity_main3.xml` — removed

### Build Status
**VERIFIED: BUILD FAILS** (hardcoded `org.gradle.java.home` path)
**AFTER FIX: BUILD SUCCESSFUL** (tested by removing the hardcoded line, 27 seconds)

---

## 6. Branch Contents — main (e80e8db)

- Android Studio scaffold created by PR #1 merge
- `MainActivity.java` = EdgeToEdge shell with default layout
- No speech code, no Wi-Fi code, no model assets
- No special permissions
- This is the base that sprint-2 diverged from

---

## 7. Agile Plan vs Actual Mapping

### Agile Sprint Definitions

| Sprint | Agile Plan | Target |
|--------|-----------|--------|
| Sprint 1 | Wi-Fi Messaging | Phone A → text → Wi-Fi → Phone B (bidirectional text) |
| Sprint 2 | Offline TTS | Text → Speech → Speaker (offline) |
| Sprint 3 | Offline STT | Microphone → Speech → Text (offline) |
| Sprint 4 | Integration | STT + Wi-Fi + TTS = voice-to-voice loop |

### Actual Branch Contents

| Branch | Actual Implementation |
|--------|----------------------|
| `sprint-1` | STT (Vosk) — NOT Wi-Fi |
| `sprint-2` | TTS (Piper) — matches agile |
| `sprint-3` | STT + Wi-Fi Direct — NOT standalone STT |

### Discrepancies

1. **sprint-1 branch = STT, not Wi-Fi** — the branch named "sprint-1" does the work that agile assigns to Sprint 3
2. **sprint-3 branch = STT+Wi-Fi combined**, not standalone STT — it already integrates STT with networking
3. **No branch contains pure Wi-Fi messaging** (text send/receive only, no STT)
4. **No Message model** exists anywhere — agile Sprint 1 requires `models/Message.java`
5. **No coordinator class** exists — agile Sprint 4 requires `application/VoiceCommunication.java`

### What We Actually Have

```
sprint-1 branch:  STT works independently ✓
sprint-2 branch:  TTS works independently ✓ (Piper only)
sprint-3 branch:  STT → Wi-Fi send works ✓, receive → display works ✓
                  BUT: receive → TTS is NOT connected
```

### What We Need for Sprint-4

```
STT → Text → Message → Wi-Fi send
                         ↓
                    Wi-Fi receive → Message → Text → TTS → Speaker
```

---

## 8. Build Status (Verified)

All builds tested 2026-09-05 using:
- JDK: JetBrains JBR 21.0.11 at `C:\Users\Reddy Sekhar Reddy\.jdks\jbr-21.0.11`
- Android SDK: `$env:LOCALAPPDATA\Android\Sdk`
- Platforms: android-36, android-37.0
- Build tools: 35.0.0, 36.0.0
- Gradle: 8.13 (via wrapper)

| Branch | Status | Duration | Notes |
|--------|--------|----------|-------|
| `sprint-1` | BUILD SUCCESSFUL | 35s | Clean build |
| `sprint-2` | BUILD SUCCESSFUL | 36s | 1 warning: "Unable to strip libonnxruntime.so and libsherpa-onnx-jni.so" |
| `sprint-3` | BUILD FAILS | — | `org.gradle.java.home` points to non-existent `C:\Users\jetti\.jdks\...` |
| `sprint-3` (after fix) | BUILD SUCCESSFUL | 27s | After removing hardcoded JDK path |

---

## 9. Issue Register

### Issue Format
```
Issue ID: [I###]
Category: [Build|Architecture|STT|TTS|WiFi|Integration|Performance|Code Quality]
Severity: [P0|P1|P2|P3|P4]
Branch: [sprint-1|sprint-2|sprint-3|all]
File: [path]
Problem: [description]
Evidence: [grep/log/diff]
Impact: [what breaks or degrades]
Recommended Fix: [action]
Decision Required: [yes/no]
```

---

### P0 — Build-Breaking

#### I001 — Hardcoded JDK Path
```
Issue ID:     I001
Category:     Build
Severity:     P0
Branch:       sprint-3
File:         iTantraSIH/gradle.properties
Line:         9
Problem:      org.gradle.java.home=C:\\Users\\jetti\\.jdks\\jbr-21.0.11
              This path does not exist on any machine except the original developer's.
Evidence:     Build fails with "Java home supplied is invalid"
Impact:       sprint-3 cannot be built by any other team member
Recommended:  Remove the line entirely; let JAVA_HOME or env handle JDK selection
Decision:     No (obvious fix)
```

---

### P1 — Core Functionality Broken / Architecture-Breaking

#### I002 — UI Contains All Business Logic
```
Issue ID:     I002
Category:     Architecture
Severity:     P1
Branch:       sprint-3
File:         iTantraSIH/app/src/main/java/.../MainActivity.java
Problem:      600+ line Activity directly manages:
              - STT model initialization
              - Wi-Fi P2P discovery and connection
              - Socket server/client lifecycle
              - STT result → Wi-Fi send
              - Wi-Fi receive → display
Evidence:     Single file contains STT init, WifiP2pManager, SocketManager,
              BroadcastReceiver registration, all UI callbacks
Impact:       Cannot test or reuse any module independently;
              Sprint-4 integration will require rewriting this file
Recommended:  Extract to: WifiService, STT coordinator, Application layer
Decision:     Yes — architecture change
```

#### I003 — No Message Model
```
Issue ID:     I003
Category:     Architecture
Severity:     P1
Branch:       all (missing from every branch)
Problem:      No models/Message.java exists anywhere.
              Raw strings are sent directly through sockets.
Evidence:     socketManager.sendMessage(spoken) sends raw text line
              onMessageReceived(message) receives raw text line
              No JSON, no sender ID, no timestamp, no language, no type
Impact:       Cannot distinguish message types (STT text vs status vs control)
              Cannot add language routing, sender identity, or message history
Recommended:  Create models/Message.java with: text, senderId, timestamp, language
Decision:     Yes — new class needed
```

#### I004 — Sprint-1 STT Code Not In main
```
Issue ID:     I004
Category:     Architecture
Severity:     P1
Branch:       main
Problem:      PR #1 merge (e80e8db) brought sprint-1 scaffold into main but
              the STT files (STTEngine.java, OfflineSTT.java, MainActivity3.java)
              did NOT land in main's tree. Main remains empty scaffold.
Evidence:     `git ls-tree main` shows only basic MainActivity.java
              No speech/ directory, no model-en-us/ assets in main
Impact:       sprint-2 branched from main (no STT)
              sprint-3 branched from sprint-1 (has STT but not TTS)
              No single branch has all three components
Recommended:  Sprint-4 must bring STT + TTS + Wi-Fi together from scratch
Decision:     No (informational)
```

---

### P2 — Integration Problem

#### I005 — STT Result Not Connected to TTS on Receive
```
Issue ID:     I005
Category:     Integration
Severity:     P2
Branch:       sprint-3
File:         iTantraSIH/app/src/main/java/.../MainActivity.java
Problem:      onMessageReceived() only calls appendReceivedData() —
              text is displayed but NOT fed to any TTS engine.
Evidence:     onMessageReceived() → tvReceivedData.setText(...)
              No TTS import, no TTSEngine reference, no speak() call
Impact:       Received messages cannot be spoken — half the voice loop is missing
Recommended:  Sprint-4 must connect: Wi-Fi receive → Message → TTS speak
Decision:     No (expected gap for sprint-4)
```

#### I006 — Dead Dependency: assemblyai-java
```
Issue ID:     I006
Category:     Build / Dependency
Severity:     P2
Branch:       sprint-3
File:         iTantraSIH/app/build.gradle
Line:         dependencies block
Problem:      `com.assemblyai:assemblyai-java:1.1.2` is declared but
              ZERO Java files reference it.
Evidence:     `git grep -i assemblyai origin/sprint-3 -- "*.java"` → no results
Impact:       Adds dead weight to APK; introduces cloud API dependency
              that contradicts the offline-only requirement
Recommended:  Remove the dependency line
Decision:     No (dead code removal)
```

#### I007 — Overlapping STT Implementations
```
Issue ID:     I007
Category:     Architecture
Severity:     P2
Branch:       sprint-1 + sprint-3
Problem:      STTEngine.java and OfflineSTT.java are IDENTICAL in sprint-1 and sprint-3.
              Two copies of the same model-en-us assets (~60MB each).
Evidence:     `git diff origin/sprint-1 origin/sprint-3 -- speech/` → 0 differences
              in STT code. Assets identical.
Impact:       Sprint-4 must deduplicate — only one copy should exist
Recommended:  Use sprint-3's STT (it's the newer branch with Wi-Fi added)
Decision:     No (obvious)
```

#### I008 — Package Structure Mismatch vs Agile
```
Issue ID:     I008
Category:     Architecture
Severity:     P2
Branch:       sprint-3
Problem:      Agile wants: communication/wifi/, models/, application/
              Actual: wifidirect/ (different name), no models/, no application/
              Agile namespace: com.itantra
              Actual: com.example.itantra_sih
Evidence:     Sprint-3 uses com.example.itantra_sih.wifidirect.*
              Agile spec says com.itantra.communication.wifi.*
Impact:       Sprint-4 integration should decide: follow agile structure or keep current?
Recommended:  Ask user before restructuring
Decision:     YES
```

#### I009 — gradle.properties Conflict
```
Issue ID:     I009
Category:     Build
Severity:     P2
Branch:       sprint-3
File:         iTantraSIH/gradle.properties
Problem:      sprint-3 has hardcoded org.gradle.java.home
              sprint-2 does not have this line.
              Merging sprint-2 + sprint-3 will create a conflict on this file.
Evidence:     sprint-3 gradle.properties line 9
              sprint-2 gradle.properties — no such line
Impact:       Merge conflict during sprint-4 creation
Recommended:  Remove the line from sprint-3 before merge
Decision:     No (obvious)
```

---

### P3 — Performance / Quality

#### I010 — Massive APK Assets
```
Issue ID:     I010
Category:     Performance
Severity:     P3
Branch:       sprint-2
Problem:      Total asset size ~150MB before compression:
              Piper model:           63MB
              espeak-ng-data:        ~20MB (ALL languages, only English used)
              TinyTTS 4 ONNX models: ~33MB (dead code)
              CMU dictionary:        ~12MB (dead code)
Evidence:     File sizes from git ls-tree
Impact:       APK will be enormous; download/install time increased
Recommended:  For sprint-4: keep only PiperEngine assets (63MB + espeak core)
              Consider: strip unused language dicts from espeak-ng-data
Decision:     Yes — affects what ships
```

#### I011 — No Thread Cleanup in WifiDirectSocketManager
```
Issue ID:     I011
Category:     Performance / Resource Leak
Severity:     P3
Branch:       sprint-3
File:         iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
Problem:      sendExecutor (ExecutorService) is created but never shutDown()
              in stop(). After multiple connect/disconnect cycles, threads accumulate.
Evidence:     stop() closes sockets but does not call sendExecutor.shutdown()
Impact:       Thread leak over long sessions
Recommended:  Add sendExecutor.shutdown() in stop()
Decision:     No (mechanical fix)
```

#### I012 — Line-Based Transport Without Escaping
```
Issue ID:     I012
Category:     WiFi / Transport
Severity:     P3
Branch:       sprint-3
File:         iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
Problem:      sendMessage uses println() — newline chars in message body
              will split into multiple lines on the receiver.
              No message length prefix or JSON framing.
Evidence:     printWriter.println(message) in sendMessage()
              bufferedReader.readLine() in startReceiving()
Impact:       Multi-line text from STT will be mangled
Recommended:  For sprint-4: use length-prefixed or JSON messages
Decision:     Yes — affects message format
```

#### I013 — One-Shot Server Socket
```
Issue ID:     I013
Category:     WiFi
Severity:     P3
Branch:       sprint-3
File:         iTantraSIH/app/src/main/java/.../wifidirect/WifiDirectSocketManager.java
Problem:      Server socket accepts ONE client, then server thread exits.
              If client disconnects and reconnects, server is dead.
Evidence:     serverSocket.accept() called once in while(isRunning) loop
              No loop back to accept() after client disconnects
Impact:       Reconnection after disconnect requires full restart
Recommended:  For sprint-4: either accept loop or re-initiate server
Decision:     Yes
```

#### I014 — PiperEngine Stop Doesn't Kill Synthesis
```
Issue ID:     I014
Category:     TTS
Severity:     P3
Branch:       sprint-2
File:         iTantraSIH/app/src/main/java/.../speech/tts/PiperEngine.java
Problem:      stop() sets currentTrack=null but the synthesis thread
              may still be blocked in tts.generate() or track.write().
              Worker thread is not interrupted.
Evidence:     stop() only nulls currentTrack
              synth thread may take seconds for long text
Impact:       Delayed response to user stop action
Recommended:  Set a volatile flag; check before/after generation; interrupt worker
Decision:     Yes
```

#### I015 — Unused Recognizer in OfflineSTT.stop()
```
Issue ID:     I015
Category:     STT / Resource
Severity:     P3
Branch:       sprint-1, sprint-3
File:         iTantraSIH/app/src/main/java/.../speech/stt/OfflineSTT.java
Problem:      stop() nulls speechService but does not close recognizer.
              Next start() creates a new Recognizer without closing the old one.
Evidence:     stop() implementation — only handles speechService
              start() creates new recognizer: recognizer = new Recognizer(model, SAMPLE_RATE)
Impact:       Resource churn; old Recognizer may hold native memory until GC
Recommended:  Close recognizer in stop() before creating new one in start()
Decision:     No (mechanical fix)
```

---

### P4 — Code Quality

#### I016 — Dead Code: OnnxModelLoader.java
```
Issue ID:     I016
Category:     Code Quality
Severity:     P4
Branch:       sprint-2
File:         iTantraSIH/app/src/main/java/.../speech/tts/OnnxModelLoader.java
Problem:      Never called from any other file
Evidence:     git grep returns only self-references
Impact:       ~100 lines of dead code in APK
Recommended:  Delete
Decision:     Yes — file deletion
```

#### I017 — Dead Code: TinyTTS + TinyTTSPipeline
```
Issue ID:     I017
Category:     Code Quality
Severity:     P4
Branch:       sprint-2
Files:        TinyTTS.java, TinyTTSPipeline.java, text/G2P.java,
              text/Normalizer.java, text/PhonemeIds.java
Problem:      7 files (~1500 lines) implementing a custom ONNX TTS chain
              that is self-described as "a plumbing test, NOT real speech synthesis"
              Never instantiated from any active code path
Evidence:     TinyTTSPipeline docstring: "This is a plumbing test"
              No constructor calls for TinyTTS or TinyTTSPipeline anywhere
Impact:       ~33MB of dead ONNX model assets + 12MB CMU dict + ~1500 lines dead code
Recommended:  Decide: keep as experimental fallback or delete entirely
Decision:     Yes — affects assets/code cleanup
```

#### I018 — Dead Code: OfflineTTS.java Import
```
Issue ID:     I018
Category:     Code Quality
Severity:     P4
Branch:       sprint-2
File:         iTantraSIH/app/src/main/java/.../MainActivity.java
Problem:      `import com.example.itantra_sih.speech.tts.OfflineTTS;` is unused
              OfflineTTS constructor is never called
Evidence:     import statement present; no new OfflineTTS() anywhere
Impact:       Unused import
Recommended:  Remove import
Decision:     No (mechanical)
```

#### I019 — Unused acceptAudio() in STTEngine Interface
```
Issue ID:     I019
Category:     Code Quality
Severity:     P4
Branch:       sprint-1, sprint-3
Problem:      acceptAudio(byte[], int) is declared in STTEngine interface
              but OfflineSTT says "Handled automatically by SpeechService"
              No code ever calls it.
Evidence:     STTEngine.java:42 declares it; OfflineSTT.java:85 is empty
Impact:       Confusing API surface
Recommended:  Remove from interface if not needed
Decision:     Yes
```

#### I020 — Duplicate File: STTEngine.java (sprint-1 ≡ sprint-3)
```
Issue ID:     I020
Category:     Code Quality
Severity:     P4
Problem:      STTEngine.java and OfflineSTT.java are byte-identical
              in sprint-1 and sprint-3 branches
Evidence:     git diff shows 0 changes
Impact:       Redundancy in branch tree; sprint-4 only needs one copy
Recommended:  Use sprint-3's copies
Decision:     No (informational)
```

#### I021 — TTS Interface Asymmetry vs STT Interface
```
Issue ID:     I021
Category:     Architecture
Severity:     P4
Problem:      STTEngine has: init(Context, listener), setOnResultListener, start, stop, destroy
              TTSEngine has: speak(text), stop, release
              No init() method in TTSEngine — PiperEngine loads in constructor
              Different lifecycle patterns: STT uses init→start→stop→destroy
              TTS uses constructor→speak→stop→release
Evidence:     STTEngine.java vs TTSEngine.java interface definitions
Impact:       Coordinator must handle asymmetric lifecycles
Recommended:  Consider unifying lifecycle pattern
Decision:     Yes — architecture decision
```

#### I022 — MaterialCardView in sprint-3 layout without Material theme
```
Issue ID:     I022
Category:     Build
Severity:     P4
Branch:       sprint-3
File:         iTantraSIH/app/src/main/res/layout/activity_main.xml
Problem:      Uses MaterialCardView but layout doesn't explicitly reference
              a Material theme. Works because `com.google.android.material:material`
              is a dependency.
Evidence:     activity_main.xml uses <com.google.android.material.card.MaterialCardView>
Impact:       None currently — works due to transitive Material theme
Recommended:  Verify Material theme is properly applied in themes.xml
Decision:     No
```

---

## 10. Dependency Graph

### Current State (per branch)

```
sprint-1:
  STTEngine ←── OfflineSTT ←── Vosk (vosk-android:0.3.75)
  MainActivity3 ──→ STTEngine
  MainActivity ──→ MainActivity3

sprint-2:
  TTSEngine ←── PiperEngine ←── sherpa-onnx (sherpa-onnx-1.13.7.aar)
  TTSEngine ←── OfflineTTS  ←── Android TextToSpeech (framework)
  TTSEngine ←── TinyTTS ←── TinyTTSPipeline ←── onnxruntime-android:1.29.0
  TinyTTSPipeline ←── G2P ←── PhonemeIds
  TinyTTSPipeline ←── Normalizer
  TTSManager ←── TTSEngine
  MainActivity ──→ TTSManager ──→ PiperEngine

sprint-3:
  STTEngine ←── OfflineSTT ←── Vosk
  WifiDirectSocketManager (raw TCP sockets)
  WifiDirectBroadcastReceiver ←── WifiP2pManager (Android framework)
  DeviceAdapter ←── WifiP2pDevice
  MainActivity ──→ OfflineSTT
  MainActivity ──→ WifiP2pManager
  MainActivity ──→ WifiDirectBroadcastReceiver
  MainActivity ──→ WifiDirectSocketManager
  MainActivity ──→ DeviceAdapter
```

### Missing (for Sprint-4)

```
Message model (NOBODY depends on it yet)
VoiceCommunication coordinator (NOBODY uses it yet)
Wi-Fi receive → TTS connection (disconnected)
STT → Message connection (disconnected — sends raw strings)
```

### Target Sprint-4 Architecture

```
                    ┌──────────────────┐
                    │    MainActivity   │  (UI only)
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ VoiceCommunication│  (coordinator)
                    │   (NEW class)     │
                    └──┬─────────┬─────┘
                       │         │
          ┌────────────┘         └────────────┐
          ▼                                   ▼
   ┌─────────────┐                    ┌─────────────┐
   │  STTEngine   │                    │  TTSEngine   │
   │  OfflineSTT  │                    │  PiperEngine │
   └──────┬──────┘                    └──────┬──────┘
          │                                   │
          │         ┌─────────────┐           │
          └────────▶│   Message   │◀──────────┘
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │ Wi-Fi Direct │
                    │ SocketManager│
                    └─────────────┘
```

---

## 11. Module Independence Audit

### STT Module
| Dependency | Allowed? | Present? | Notes |
|------------|----------|----------|-------|
| Audio/Microphone | YES | YES (via Vosk) | SpeechService handles AudioRecord |
| Speech Model | YES | YES (model-en-us) | Vosk model bundled |
| TTS | NO | NO ✓ | Independent |
| Wi-Fi | NO | NO ✓ | Independent |
| Message | NO | NO | No Message model exists |
| UI | NO | YES — VIOLATION | sprint-3: MainActivity directly initializes STT |

### TTS Module
| Dependency | Allowed? | Present? | Notes |
|------------|----------|----------|-------|
| Text input | YES | YES | speak(String text) |
| Audio output | YES | YES (AudioTrack) | PiperEngine handles playback |
| Voice model | YES | YES (piper/en_IN) | sherpa-onnx model |
| STT | NO | NO ✓ | Independent |
| Wi-Fi | NO | NO ✓ | Independent |
| Message | NO | NO | No Message model exists |
| UI | NO | NO ✓ | TTSManager is clean |

### Wi-Fi Module
| Dependency | Allowed? | Present? | Notes |
|------------|----------|----------|-------|
| Network | YES | YES | TCP sockets |
| Connection | YES | YES | Wi-Fi P2P + socket |
| Transport | YES | YES | PrintWriter/BufferedReader |
| Message | NO | NO | Raw strings only, no Message model |
| STT | NO | NO ✓ | Independent |
| TTS | NO | NO ✓ | Independent |
| UI | NO | YES — VIOLATION | sprint-3: MainActivity directly manages Wi-Fi |

### Application Coordinator
| Does it exist? | NO |
|----------------|-----|
| Sprint-4 creates it | YES (VoiceCommunication.java) |

---

## 12. Language Support Status

| Language | STT Status | TTS Status | Model Present | Notes |
|----------|------------|------------|---------------|-------|
| English | IMPLEMENTED (Vosk) | IMPLEMENTED (Piper) | YES (both) | Only working language |
| Telugu | NOT IMPLEMENTED | NOT IMPLEMENTED | NO STT model; espeak-ng dict only | Agile target language |
| Hindi | NOT IMPLEMENTED | NOT IMPLEMENTED | espeak-ng dict only | |
| Tamil | NOT IMPLEMENTED | NOT IMPLEMENTED | espeak-ng dict only | |
| Kannada | NOT IMPLEMENTED | NOT IMPLEMENTED | espeak-ng dict only | |
| Malayalam | NOT IMPLEMENTED | NOT IMPLEMENTED | espeak-ng dict only | |

---

## 13. Model Status

| Model | Type | Size | Branch | Used By | Status |
|-------|------|------|--------|---------|--------|
| Vosk model-en-us | STT | ~60MB | sprint-1, sprint-3 | OfflineSTT | ACTIVE |
| Piper en_IN-spicor-medium | TTS | 63MB | sprint-2 | PiperEngine | ACTIVE |
| espeak-ng-data (all langs) | TTS phonemizer | ~20MB | sprint-2 | PiperEngine | ACTIVE (mostly unused langs) |
| TinyTTS text_encoder | TTS | 3MB | sprint-2 | DEAD | DEAD CODE |
| TinyTTS duration_predictor | TTS | 1MB | sprint-2 | DEAD | DEAD CODE |
| TinyTTS flow | TTS | 15MB | sprint-2 | DEAD | DEAD CODE |
| TinyTTS decoder | TTS | 14MB | sprint-2 | DEAD | DEAD CODE |
| CMU dict (cmudict.rep) | G2P | 129K lines | sprint-2 | DEAD | DEAD CODE |
| sherpa-onnx-1.13.7.aar | TTS native libs | 38MB | sprint-2 | PiperEngine | ACTIVE |

---

## 14. Test Status

| Test Category | Status | Notes |
|---------------|--------|-------|
| Unit tests | NONE | ExampleUnitTest.java is unmodified scaffold |
| STT standalone test | NOT TESTED | Requires physical device + mic |
| TTS standalone test | NOT TESTED | Requires physical device + speaker |
| Wi-Fi text test | NOT TESTED | Requires 2 physical devices |
| STT→Wi-Fi integration | NOT TESTED | Requires 2 devices + mic |
| Wi-Fi→TTS integration | NOT TESTED | Requires 2 devices + speaker |
| Full voice loop | NOT TESTED | Requires 2 devices |
| Build verification | DONE | All 3 branches verified |
| Android lint | NOT RUN | |
| Static analysis | NOT RUN | |

---

## 15. Performance Summary

| Metric | Estimated | Notes |
|--------|-----------|-------|
| Total asset size (sprint-2) | ~150MB | Before compression |
| Total native lib size | ~50MB | onnxruntime + sherpa-onnx |
| Estimated APK size (debug) | 150-200MB | Conservative estimate |
| Vosk model load time | ~2-5s | Depends on device storage speed |
| Piper model load time | ~1-3s | espeak extraction on first run |
| STT latency | <500ms | Vosk is real-time |
| TTS latency (Piper) | ~1-3s | Model loading + synthesis |
| End-to-end (estimated) | ~3-5s | STT + Wi-Fi + TTS |

---

## 16. Integration Readiness

### What Sprint-4 Needs to Do

1. **Create `sprint-4` branch** (local only)
2. **Fix sprint-3 build issue** (remove hardcoded JDK path)
3. **Remove dead dependency** (assemblyai-java)
4. **Merge sprint-2 TTS into sprint-3 base** (or cherry-pick relevant files)
5. **Create Message model** (`models/Message.java`)
6. **Create coordinator** (`application/VoiceCommunication.java` or equivalent)
7. **Connect received text → TTS playback**
8. **Resolve merge conflicts** (MainActivity.java, activity_main.xml, build.gradle, gradle.properties)
9. **Test each module independently** before integration
10. **Test full voice loop** on 2 physical devices

### Predicted Merge Conflicts (sprint-2 + sprint-3)

| File | Conflict Type | Resolution |
|------|--------------|------------|
| `MainActivity.java` | Both branches rewrite it completely | Create new unified version |
| `activity_main.xml` | Both branches rewrite it completely | Create new unified version |
| `build.gradle` | Different dependencies + packaging | Merge both dependency sets |
| `gradle.properties` | sprint-3 has hardcoded JDK line | Remove sprint-3's line |
| `AndroidManifest.xml` | Different permission sets | Union of both |

---

*End of Repository Audit*
