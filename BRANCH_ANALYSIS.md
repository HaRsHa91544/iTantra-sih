# iTantra SIH — Branch Analysis

## Source: read-only audit of `https://github.com/HaRsHa91544/iTantra-sih`

This document records what each branch contains, independently of the intended
(Agile) plans in `sih-project-agile`. It is part of the `sprint-4` integration
baseline. Nothing in this document is a claim that a feature "works" — those
claims require the evidence in `REPOSITORY_AUDIT.md`.

---

## 1. Branch topology

```
e7dbd43  Initial commit                     (main lineage, no app)
c630e77  Update project title with year      (main lineage)
f2a41d8  initialize Android project          (sprint-1 lineage)
9cac4d0  second commit                       (sprint-1 lineage)
   └─ 274bf62  Basic working STT model is done   (sprint-1)
   └─ 1d55da8  Basic working STT model is done   (sprint-1, tip)
   └─ 00c20a3  Basic voice input + WIFI-DIRECT   (sprint-3 lineage)
   └─ 53069ce  Basic voice input + WIFI-DIRECT   (sprint-3, tip)

e80e8db  Merge PR #1 (main * 9cac4d0)        (main, tip) — merges sprint-1
   └─ 8067aa7  TTS Engine and OfflineTTS completed         (sprint-2)
   └─ d49bedb  Added TTSManager                              (sprint-2)
   └─ 7a01186  Added the models for TTS                     (sprint-2, tip)
```

Local branches: `main`, `sprint-2` (current checkout).
Remote tracking: `origin/main`, `origin/sprint-1`, `origin/sprint-2`, `origin/sprint-3`.
No `sprint-4` exists locally or remotely.

---

## 2. `main` (e80e8db)

- Android Studio scaffold merged from PR #1 (`sprint-1`).
- `MainActivity.java` = plain EdgeToEdge shell. No speech / wifi / models.
- Layout `activity_main.xml` is the framework default equivalent.
- No STT/TTS/Wi-Fi code, no model assets, no permissions beyond defaults.
- Purpose: the shared skeleton that the feature branches diverged from.

## 3. `sprint-1` (1d55da8) — STT POC

Intended per Agile as **Wi-Fi messaging**; actual code is the **STT** POC.

- `speech/stt/STTEngine.java` — interface (init/setOnResultListener/start/
  acceptAudio/stop/destroy).
- `speech/stt/OfflineSTT.java` — Vosk `SpeechService`-based offline STT.
- `MainActivity.java` — navigates to `MainActivity3`.
- `MainActivity3.java` — UI driving STT (record → transcribe → display).
- Asset: Vosk English model `model-en-us` (~large, bundled).
- Depends on `com.alphacephei:vosk-android:0.3.75`.
- Mimetypes: `RECORD_AUDIO` permission added.
- Build: verified SUCCESS.

Java files: `STTEngine`, `OfflineSTT`, `MainActivity`, `MainActivity3`.

## 4. `sprint-2` (7a01186) — TTS POC

Actual code is the **TTS** POC.

- `speech/tts/TTSEngine.java` — interface (speak/stop/release).
- `speech/tts/OfflineTTS.java` — Android native `TextToSpeech` wrapper.
- `speech/tts/PiperEngine.java` — sherpa-onnx Piper (`en_IN-spicor-medium.onnx`),
  extracts `espeak-ng-data` to files dir, `AudioTrack` playback.
- `speech/tts/TTSManager.java` — thin facade over `TTSEngine`.
- `speech/tts/TinyTTS.java` + `TinyTTSPipeline.java` — full 4-ONNX-model chain
  (text_encoder/duration_predictor/flow/decoder) via onnxruntime; pipeline is a
  **plumbing test** (class docstring states it is NOT real speech synthesis for
  the synthetic path; `synthesize(String)` runs the G2P chain).
- `speech/tts/text/` — `G2P`, `Normalizer`, `PhonemeIds` (TinyTTS English text→IDs).
- `speech/tts/OnnxModelLoader.java` — **dead code** (no callers anywhere).
- `MainActivity.java` — drives `PiperEngine` via `TTSManager` with a test button.
- Assets: `piper/en_IN-spicor-medium.onnx` (≈63 MB), `lespk/espeak-ng-data`
  (many languages incl. `te`/`hi`/`ta`/`ml`/`kn` dicts), `tinytts/*` (4 onnx,
  `cmudict.rep`).
- Depends: `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`,
  local `libs/sherpa-onnx-1.13.7.aar` (bundled binary), appcompat/material etc.
- Manifest: **no** `RECORD_AUDIO`; no Wi-Fi permissions.
- Build: verified SUCCESS.

## 5. `sprint-3` (53069ce) — STT + Wi-Fi Direct

Actual code integrates **STT → Wi-Fi Direct → display received text** (no TTS).

- `speech/stt/STTEngine.java`, `OfflineSTT.java` — same Vosk STT as sprint-1.
- `wifidirect/DeviceAdapter.java`, `WifiDirectBroadcastReceiver.java`,
  `WifiDirectSocketManager.java` — Wi-Fi Direct discovery/connection + socket
  send/receive (plain text lines via `PrintWriter`/`BufferedReader`).
- `MainActivity.java` — full coordinator: discovers peers, connects, and on
  final STT result sends the text over the socket; received text is displayed.
- `MainActivity3.java` and `activity_main3.xml` — **deleted** (vs sprint-1).
- Manifest: Wi-Fi Direct + Location + Record-audio + Nearby permissions.
- Depends: `vosk-android:0.3.75`, `com.assemblyai:assemblyai-java:1.1.2`
  (**unused in any .java file — dead dependency**), appcompat/material etc.
- `gradle.properties`: **hardcoded** `org.gradle.java.home=C:\Users\jetti\...`
  which is invalid on this machine → build FAILS out of the box; succeeds once
  that line is removed.
- Build: FAILS by default (invalid JDK path); SUCCESS once that property removed.

---

## 6. Divergence: Agile plan vs. actual code

| Agile plan            | Attribute   | Actual branch/material |
| --------------------- | ----------- | ---------------------- |
| Sprint 1 : Wi-Fi msg  | Actual code | Sprint-1 = STT POC      |
| Sprint 2 : TTS        | Actual code | Sprint-2 = TTS POC      |
| Sprint 3 : STT        | Actual code | Sprint-3 = STT + Wi-Fi  |
| Sprint 3 per prompt   | "Supposed" S1 = STT, S2 = TTS | branch names swapped vs. prompt's section 60 |

So the real mapping used by the authors is:

- `sprint-1` branch → STT
- `sprint-2` branch → TTS
- `sprint-3` branch → STT + Wi-Fi Direct

The minimum working core for `sprint-4` is: **sprint-3 (STT + Wi-Fi) + sprint-2
(TTS)**. Sprint-1's STT is functionally duplicated by sprint-3's STT.

## 7. Key reuse candidates

- STT: `speech/stt/STTEngine`, `OfflineSTT` (sprint-3).
- Wi-Fi: `wifidirect/WifiDirectSocketManager`, `WifiDirectBroadcastReceiver`,
  `DeviceAdapter` (sprint-3).
- TTS: `speech/tts/TTSEngine`, `PiperEngine`, `TTSManager` (sprint-2); note
  `OfflineTTS` is a native `TextToSpeech` wrapper (online-engine fallback).