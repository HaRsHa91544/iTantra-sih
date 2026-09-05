package com.example.itantra_sih.speech.tts;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.ValueInfo;

public class OnnxModelLoader {
    private static final String TAG = "OnnxModelLoader";

    public static boolean loadTextEncoder(Context context) {
        String assetPath = "tinytts/text_encoder.onnx";
        try {
            byte[] modelBytes = readAsset(context, assetPath);
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            OrtSession session = env.createSession(modelBytes, options);
            Log.i(TAG, "TINY-TTS MODEL LOAD SUCCESS: " + assetPath);
            logMetadata(session);
            session.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "TINY-TTS MODEL LOAD FAILED (IO): " + assetPath, e);
            return false;
        } catch (OrtException e) {
            Log.e(TAG, "TINY-TTS MODEL LOAD FAILED (ONNX): " + assetPath, e);
            return false;
        }
    }

    private static void logMetadata(OrtSession session) throws OrtException {
        Map<String, NodeInfo> inputs = session.getInputInfo();
        Map<String, NodeInfo> outputs = session.getOutputInfo();
        Log.i(TAG, "--- " + session.getInputNames().size() + " INPUTS ---");
        for (Map.Entry<String, NodeInfo> entry : inputs.entrySet()) {
            Log.i(TAG, describe(entry.getKey(), entry.getValue()));
        }
        Log.i(TAG, "--- " + outputs.size() + " OUTPUTS ---");
        for (Map.Entry<String, NodeInfo> entry : outputs.entrySet()) {
            Log.i(TAG, describe(entry.getKey(), entry.getValue()));
        }
    }

    private static String describe(String name, NodeInfo info) {
        ValueInfo valueInfo = info.getInfo();
        if (valueInfo instanceof TensorInfo) {
            TensorInfo tensorInfo = (TensorInfo) valueInfo;
            long[] shape = tensorInfo.getShape();
            StringBuilder sb = new StringBuilder(name)
                    .append(" type=").append(tensorInfo.type)
                    .append(" shape=[");
            for (int i = 0; i < shape.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(shape[i]);
            }
            sb.append("]");
            return sb.toString();
        }
        return name + " type=non-tensor";
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
}
