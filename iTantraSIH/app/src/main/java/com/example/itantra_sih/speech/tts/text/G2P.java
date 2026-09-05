package com.example.itantra_sih.speech.tts.text;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Port of tiny_tts/text/english.py grapheme_to_phoneme for English.
 *
 * Differences from the Python reference (per project decision):
 *  - Words are split on whitespace instead of using the bert-base-uncased
 *    wordpiece tokenizer.
 *  - Pronunciation comes from the bundled cmudict.rep dictionary only.
 *    Words not found fall back to a simple character-by-character pass.
 *
 * The cmudict.rep asset must be present at assets/tinytts/cmudict.rep.
 */
public final class G2P {
    private static final String TAG = "G2P";
    private static final String DICT_ASSET = "tinytts/cmudict.rep";
    private static final int START_LINE = 49; // data begins after 48 header lines

    private static final Map<String, String> REP_MAP = new HashMap<>();
    private static final Pattern ARPA_TONE = Pattern.compile("\\d$");
    private static final Map<String, List<String>> ENG_DICT = new HashMap<>();

    static {
        REP_MAP.put("：", ",");
        REP_MAP.put("；", ",");
        REP_MAP.put("，", ",");
        REP_MAP.put("。", ".");
        REP_MAP.put("！", "!");
        REP_MAP.put("？", "?");
        REP_MAP.put("\n", ".");
        REP_MAP.put("·", ",");
        REP_MAP.put("、", ",");
        REP_MAP.put("...", "…");
        REP_MAP.put("v", "V");
    }

    private G2P() {
    }

    /** Load the CMU dictionary from assets (idempotent). */
    public static synchronized void ensureLoaded(Context context) throws IOException {
        if (!ENG_DICT.isEmpty()) {
            return;
        }
        try (InputStream in = context.getAssets().open(DICT_ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineIndex = 1;
            while ((line = reader.readLine()) != null) {
                if (lineIndex >= START_LINE) {
                    parseDictLine(line.trim());
                }
                lineIndex++;
            }
        }
        Log.i(TAG, "cmudict loaded, entries: " + ENG_DICT.size());
    }

    /** doc separation of tokens into words and punctuation, preserving order. */
    private static List<String> splitGroups(String text) {
        List<String> groups = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c) || c == '\'' || c == '-') {
                word.append(c);
            } else if (!Character.isWhitespace(c)) {
                if (word.length() > 0) {
                    groups.add(word.toString());
                    word.setLength(0);
                }
                groups.add(String.valueOf(c));
            } else {
                if (word.length() > 0) {
                    groups.add(word.toString());
                    word.setLength(0);
                }
            }
        }
        if (word.length() > 0) {
            groups.add(word.toString());
        }
        return groups;
    }

    private static void parseDictLine(String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("  "); // two spaces
        if (parts.length < 2) {
            return;
        }
        String word = parts[0];
        String[] syllables = parts[1].split(" - ");
        List<String> phones = new ArrayList<>();
        for (String syllable : syllables) {
            for (String ph : syllable.split(" ")) {
                phones.add(ph);
            }
        }
        ENG_DICT.put(word, phones);
    }

    /** Port of grapheme_to_phoneme using whitespace + punctuation word-splitting. */
    public static PhonesResult graphemeToPhoneme(String normalizedText) {
        List<String> groups = splitGroups(normalizedText);
        List<String> phones = new ArrayList<>();
        List<Integer> tones = new ArrayList<>();
        List<Integer> word2ph = new ArrayList<>();

        for (String group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            List<String> phns = new ArrayList<>();
            List<Integer> tns = new ArrayList<>();
            boolean isWord = group.matches("(?i)[a-z']+");

            if (isWord) {
                List<String> dictPhones = ENG_DICT.get(group.toUpperCase(java.util.Locale.ROOT));
                if (dictPhones != null) {
                    for (String ph : dictPhones) {
                        String[] parsed = parsePhoneme(ph);
                        phns.add(parsed[0]);
                        tns.add(Integer.parseInt(parsed[1]));
                    }
                } else {
                    fallbackPhonemes(group, phns, tns);
                }
            } else {
                // Pure punctuation/fullstop token -> emit as-is (matches reference over g2p_en)
                phones.add(group);
                tones.add(0);
                word2ph.add(1);
                continue;
            }

            phones.addAll(phns);
            tones.addAll(tns);
            int wordLen = 1; // one word per whitespace token
            word2ph.addAll(distributePhone(phns.size(), wordLen));
        }

        for (int i = 0; i < phones.size(); i++) {
            phones.set(i, mapPhoneme(phones.get(i)));
        }

        // pad_start_end = True
        List<String> paddedPhones = new ArrayList<>();
        paddedPhones.add("_");
        paddedPhones.addAll(phones);
        paddedPhones.add("_");

        List<Integer> paddedTones = new ArrayList<>();
        paddedTones.add(0);
        paddedTones.addAll(tones);
        paddedTones.add(0);

        List<Integer> paddedWord2ph = new ArrayList<>();
        paddedWord2ph.add(1);
        paddedWord2ph.addAll(word2ph);
        paddedWord2ph.add(1);

        return new PhonesResult(paddedPhones, paddedTones, paddedWord2ph);
    }

    /** parse_phoneme: strip trailing tone digit, lowercase phoneme. */
    private static String[] parsePhoneme(String ph) {
        int tone = 0;
        if (ARPA_TONE.matcher(ph).find()) {
            tone = Integer.parseInt(ph.substring(ph.length() - 1)) + 1;
            ph = ph.substring(0, ph.length() - 1);
        }
        return new String[]{ph.toLowerCase(java.util.Locale.ROOT), String.valueOf(tone)};
    }

    /** distribute_phone: spread n_phone across n_word as evenly as possible (port of english.py:13). */
    private static List<Integer> distributePhone(int nPhone, int nWord) {
        int[] perWord = new int[nWord];
        for (int task = 0; task < nPhone; task++) {
            int minTasks = Integer.MAX_VALUE;
            for (int v : perWord) minTasks = Math.min(minTasks, v);
            List<Integer> minIndices = new ArrayList<>();
            for (int i = 0; i < nWord; i++) {
                if (perWord[i] == minTasks) minIndices.add(i);
            }
            int chosen = minIndices.get(minIndices.size() / 2);
            perWord[chosen]++;
        }
        List<Integer> result = new ArrayList<>();
        for (int v : perWord) result.add(v);
        return result;
    }

    /** map_phoneme: normalize special chars / UNK mapping (port of english.py:43). */
    private static String mapPhoneme(String ph) {
        String rep = REP_MAP.get(ph);
        if (rep != null) {
            ph = rep;
        }
        // Should be in the symbol set; if not, mark UNK.
        if (!isInSymbols(ph)) {
            return "UNK";
        }
        return ph;
    }

    private static boolean isInSymbols(String ph) {
        return PhonemeIds.containsSymbol(ph);
    }

    /**
     * Fallback for words not in cmudict: emit each character's phoneme-ish token.
     * Keeps the pipeline runnable for out-of-vocabulary words.
     */
    private static void fallbackPhonemes(String token, List<String> phones, List<Integer> tones) {
        for (char c : token.toCharArray()) {
            if (Character.isLetter(c)) {
                phones.add(Character.toLowerCase(c) + "");
                tones.add(0);
            } else if (!Character.isWhitespace(c)) {
                phones.add(Character.toString(c));
                tones.add(0);
            }
        }
    }

    public static final class PhonesResult {
        public final List<String> phones;
        public final List<Integer> tones;
        public final List<Integer> word2ph;

        public PhonesResult(List<String> phones, List<Integer> tones, List<Integer> word2ph) {
            this.phones = phones;
            this.tones = tones;
            this.word2ph = word2ph;
        }
    }
}
