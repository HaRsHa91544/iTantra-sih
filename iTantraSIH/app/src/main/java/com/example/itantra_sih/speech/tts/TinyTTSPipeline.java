package com.example.itantra_sih.speech.tts;

import android.content.Context;
import android.util.Log;

import com.example.itantra_sih.speech.tts.text.G2P;
import com.example.itantra_sih.speech.tts.text.Normalizer;
import com.example.itantra_sih.speech.tts.text.PhonemeIds;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Runs the full TinyTTS 4-model chain with synthetic (placeholder) phoneme input,
 * implementing the alignment math that the Python reference does in NumPy.
 *
 * This is a plumbing test, NOT real speech synthesis. It proves the whole
 * text_encoder -> duration_predictor -> alignment -> flow -> decoder chain
 * runs on-device and produces a raw 44100 Hz waveform.
 */
public class TinyTTSPipeline {
    private static final String TAG = "TinyTTSPipeline";
    private static final float SAMPLING_RATE = 44100f;
    private static final float NOISE_SCALE = 0.667f;
    private static final float LENGTH_SCALE = 1.0f;

    private final OrtEnvironment env;
    private final OrtSession encoder;
    private final OrtSession durationPredictor;
    private final OrtSession flow;
    private final OrtSession decoder;
    private final Context context;

    public TinyTTSPipeline(Context context) throws IOException, OrtException {
        this.context = context;
        this.env = OrtEnvironment.getEnvironment();
        this.encoder = createSession(context, "tinytts/text_encoder.onnx");
        this.durationPredictor = createSession(context, "tinytts/duration_predictor.onnx");
        this.flow = createSession(context, "tinytts/flow.onnx");
        this.decoder = createSession(context, "tinytts/decoder.onnx");
        Log.i(TAG, "All 4 sessions loaded");
    }

    private static OrtSession createSession(Context context, String assetPath) throws IOException, OrtException {
        byte[] bytes = readAsset(context, assetPath);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        return OrtEnvironment.getEnvironment().createSession(bytes, options);
    }

    private static byte[] readAsset(Context context, String path) throws IOException {
        InputStream stream = context.getAssets().open(path);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            stream.close();
        }
    }

    /** Run the chain with a placeholder phoneme ID sequence. Returns the raw waveform samples. */
    public float[] synthesizeSynthetic() throws OrtException {
        // Placeholder phoneme ids (with blanks already interleaved, pad first/last).
        // Represents a short dummy utterance; any valid symbol ids will do for a plumbing test.
        int[] phoneIds = {0, 17, 0, 30, 0, 25, 0, 11, 0, 26, 0};
        int[] toneIds = new int[phoneIds.length];
        int[] langIds = new int[phoneIds.length];
        return runChain(phoneIds, toneIds, langIds);
    }

    /**
     * Real-text synthesis: normalize -> G2P -> IDs -> blanks -> run the chain.
     * Returns the raw 44100 Hz waveform.
     */
    public float[] synthesize(String text) throws OrtException, IOException {
        G2P.ensureLoaded(context);
        String normalized = Normalizer.normalizeText(text);
        G2P.PhonesResult g2p = G2P.graphemeToPhoneme(normalized);

        PhonemeIds.Result ids = PhonemeIds.toIds(g2p.phones, g2p.tones, "EN");
        List<Integer> phoneIdsList = PhonemeIds.insertBlanks(ids.phoneIds);
        List<Integer> toneIdsList = PhonemeIds.insertBlanks(ids.toneIds);
        List<Integer> langIdsList = PhonemeIds.insertBlanks(ids.langIds);

        int[] phoneIds = toIntArray(phoneIdsList);
        int[] toneIds = toIntArray(toneIdsList);
        int[] langIds = toIntArray(langIdsList);

        Log.i(TAG, "synth text='"+text+"' phones="+g2p.phones+" ids_len="+phoneIds.length);
        Log.i(TAG, "DIAG ids phone=" + java.util.Arrays.toString(phoneIds));
        Log.i(TAG, "DIAG ids tone=" + java.util.Arrays.toString(toneIds));
        return runChain(phoneIds, toneIds, langIds);
    }

    private float[] runChain(int[] phoneIds, int[] toneIds, int[] langIds) throws OrtException {
        int t = phoneIds.length;

        long[] x = toLong1D(phoneIds);
        long[] xLen = {t};
        long[] tone = toLong1D(toneIds);
        long[] lang = toLong1D(langIds);
        long[] sid = {0};
        float[] bert = new float[1024 * t];
        float[] jaBert = new float[768 * t];

        // 1. Text encoder
        Map<String, OnnxTensor> encIn = new LinkedHashMap<>();
        encIn.put("phone_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(x), new long[]{1, t}));
        encIn.put("phone_lengths", OnnxTensor.createTensor(env, LongBuffer.wrap(xLen), new long[]{1}));
        encIn.put("tone_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tone), new long[]{1, t}));
        encIn.put("language_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(lang), new long[]{1, t}));
        encIn.put("bert", OnnxTensor.createTensor(env, FloatBuffer.wrap(bert), new long[]{1, 1024, t}));
        encIn.put("ja_bert", OnnxTensor.createTensor(env, FloatBuffer.wrap(jaBert), new long[]{1, 768, t}));
        encIn.put("speaker_id", OnnxTensor.createTensor(env, LongBuffer.wrap(sid), new long[]{1}));

        float[] xEnc, mP, logsP, xMask, g;
        try (OrtSession.Result enc = encoder.run(encIn)) {
            xEnc = floatOut(enc, "x_enc");
            mP = floatOut(enc, "m_p");
            logsP = floatOut(enc, "logs_p");
            xMask = floatOut(enc, "x_mask");
            g = floatOut(enc, "g");
        } finally {
            closeAll(encIn.values());
        }

        int channels = 80;
        Log.i(TAG, "encoder out: x_enc len=" + xEnc.length + " (" + channels + "xT)");

        // 2. Duration predictor
        Map<String, OnnxTensor> dpIn = new LinkedHashMap<>();
        dpIn.put("x", floatTensor(env, xEnc, new long[]{1, channels, t}));
        dpIn.put("x_mask", floatTensor(env, xMask, new long[]{1, 1, t}));
        dpIn.put("g", floatTensor(env, g, new long[]{1, 80, 1}));
        float[] logw;
        try (OrtSession.Result dp = durationPredictor.run(dpIn)) {
            logw = floatOutByIndex(dp, 0);
        } finally {
            closeAll(dpIn.values());
        }

        // 3. Alignment (Java port of infer_onnx.py NumPy math)
        float[] w = new float[t];
        for (int i = 0; i < t; i++) {
            float expW = (float) Math.exp(logw[i]) * xMask[i] * LENGTH_SCALE;
            w[i] = (float) Math.ceil(expW);
        }
        long yLen = 1;
        for (float v : w) yLen += (long) v;

        // x_mask is [1,1,T] -> T values at indices 0..T-1
        float[][] attn = computeAlignment(w, yLen, xMask); // [yLen][T]

        // Expand prior stats: m_p_exp[c, frame] = sum_x attn[y, x] * m_p[c, x]
        float[][] mPexp = matmulExpand(attn, mP, channels, t, (int) yLen);
        float[][] logsPexp = matmulExpand(attn, logsP, channels, t, (int) yLen);

        // 4. Sample z_p (gaussian noise) — fresh randomness each run (matches reference)
        Random rng = new Random();
        float[][] zP = new float[channels][(int) yLen];
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < yLen; y++) {
                float noise = (float) rng.nextGaussian();
                zP[c][y] = mPexp[c][y] + noise * (float) Math.exp(logsPexp[c][y]) * NOISE_SCALE;
            }
        }

        // y_mask: [1,1,T_y] all ones
        float[] yMask = new float[(int) yLen];
        java.util.Arrays.fill(yMask, 1f);

        // 5. Flow (reverse)
        Map<String, OnnxTensor> flowIn = new LinkedHashMap<>();
        flowIn.put("z_p", floatTensor(env, flatten(zP), new long[]{1, channels, (int) yLen}));
        flowIn.put("y_mask", floatTensor(env, yMask, new long[]{1, 1, (int) yLen}));
        flowIn.put("g", floatTensor(env, g, new long[]{1, 80, 1}));
        float[] z;
        try (OrtSession.Result fl = flow.run(flowIn)) {
            z = floatOutByIndex(fl, 0);
        } finally {
            closeAll(flowIn.values());
        }

        // z_masked = z * y_mask (all ones, so unchanged) [1,1,samples]
        int samples = z.length / channels;
        Log.i(TAG, "flow out: z len=" + z.length + " samples(est)=" + samples);

        // 6. Decoder -> audio [1,1,samples]
        Map<String, OnnxTensor> decIn = new LinkedHashMap<>();
        decIn.put("z", floatTensor(env, z, new long[]{1, channels, samples}));
        decIn.put("g", floatTensor(env, g, new long[]{1, 80, 1}));
        float[] audio;
        try (OrtSession.Result dc = decoder.run(decIn)) {
            audio = floatOutByIndex(dc, 0);
        } finally {
            closeAll(decIn.values());
        }

        Log.i(TAG, "DECODER AUDIO produced: samples=" + audio.length + " sr=" + (int) SAMPLING_RATE);
        logAudioStats(audio);
        return audio;
    }

    private static void logAudioStats(float[] audio) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, sum = 0f, sqSum = 0f;
        for (float v : audio) {
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
            sqSum += v * v;
        }
        float mean = sum / audio.length;
        float rms = (float) Math.sqrt(sqSum / audio.length);
        Log.i(TAG, String.format("DIAG audio len=%d min=%.4f max=%.4f mean=%.4f rms=%.4f",
                audio.length, min, max, mean, rms));
        StringBuilder first = new StringBuilder("first10=[");
        int count = Math.min(10, audio.length);
        for (int i = 0; i < count; i++) {
            if (i > 0) first.append(", ");
            first.append(String.format("%.4f", audio[i]));
        }
        first.append("]");
        Log.i(TAG, "DIAG " + first);
    }

    // ── Alignment helpers (port of infer_onnx.py) ────────────────────────────

    /** attn[y][x] = 1 if frame y belongs to phone x. */
    private static float[][] computeAlignment(float[] wCeil, long yLen, float[] xMaskT) {
        int t = wCeil.length;
        long[] cum = new long[t];
        long acc = 0;
        for (int i = 0; i < t; i++) {
            acc += (long) wCeil[i];
            cum[i] = acc;
        }
        float[][] attn = new float[(int) yLen][t];
        for (int y = 0; y < yLen; y++) {
            for (int x = 0; x < t; x++) {
                long start = (x == 0) ? 0 : cum[x - 1];
                long end = cum[x];
                if (y >= start && y < end && xMaskT[x] > 0f) {
                    attn[y][x] = 1f;
                }
            }
        }
        return attn;
    }

    /** out[c][y] = sum_x attn[y][x] * in[c, x*T + x?]. in is [1,C,T] flattened C*T. */
    private static float[][] matmulExpand(float[][] attn, float[] inFlat, int channels, int t, int yLen) {
        float[][] out = new float[channels][yLen];
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < yLen; y++) {
                float s = 0f;
                for (int x = 0; x < t; x++) {
                    s += attn[y][x] * inFlat[c * t + x];
                }
                out[c][y] = s;
            }
        }
        return out;
    }

    // ── Tensor/file helpers ───────────────────────────────────────────────────

    private static long[] toLong1D(int[] a) {
        long[] out = new long[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i];
        return out;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private static float[] flatten(float[][] a) {
        List<Float> flat = new ArrayList<>();
        for (float[] row : a) for (float v : row) flat.add(v);
        float[] out = new float[flat.size()];
        for (int i = 0; i < flat.size(); i++) out[i] = flat.get(i);
        return out;
    }

    private static OnnxTensor floatTensor(OrtEnvironment env, float[] data, long[] shape) throws OrtException {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
    }

    private static float[] floatOut(OrtSession.Result result, String name) throws OrtException {
        java.util.Optional<OnnxValue> v = result.get(name);
        if (!v.isPresent()) throw new OrtException("missing output " + name);
        OnnxTensor t = (OnnxTensor) v.get();
        FloatBuffer buf = t.getFloatBuffer().duplicate();
        float[] out = new float[buf.remaining()];
        buf.get(out);
        return out;
    }

    private static float[] floatOutByIndex(OrtSession.Result result, int index) throws OrtException {
        OnnxValue v = result.get(index);
        OnnxTensor t = (OnnxTensor) v;
        FloatBuffer buf = t.getFloatBuffer().duplicate();
        float[] out = new float[buf.remaining()];
        buf.get(out);
        return out;
    }

    private static void closeAll(Iterable<OnnxTensor> tensors) {
        for (OnnxTensor t : tensors) {
            try {
                t.close();
            } catch (Exception ignored) {
            }
        }
    }

    public int getSampleRate() {
        return (int) SAMPLING_RATE;
    }

    public void close() {
        try { if (decoder != null) decoder.close(); } catch (Exception ignored) {}
        try { if (flow != null) flow.close(); } catch (Exception ignored) {}
        try { if (durationPredictor != null) durationPredictor.close(); } catch (Exception ignored) {}
        try { if (encoder != null) encoder.close(); } catch (Exception ignored) {}
    }
}
