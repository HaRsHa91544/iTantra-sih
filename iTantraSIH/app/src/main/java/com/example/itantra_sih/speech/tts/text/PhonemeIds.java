package com.example.itantra_sih.speech.tts.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Port of tiny_tts/text/symbols.py + tiny_tts/text/__init__.py
 *
 * Holds the phoneme symbol vocabulary and maps phonemes/tones/languages to
 * integer IDs exactly as the Python reference does.
 */
public final class PhonemeIds {

    private static final String[] PUNCTUATION = {"!", "?", "…", ",", ".", "'", "-", "¿", "¡"};
    private static final String[] PU_SYMBOLS = concat(PUNCTUATION, new String[]{"SP", "UNK"});
    private static final String PAD = "_";

    private static final String[] ZH_SYMBOLS = {
            "E", "En", "a", "ai", "an", "ang", "ao", "b", "c", "ch", "d", "e", "ei", "en",
            "eng", "er", "f", "g", "h", "i", "i0", "ia", "ian", "iang", "iao", "ie", "in",
            "ing", "iong", "ir", "iu", "j", "k", "l", "m", "n", "o", "ong", "ou", "p", "q",
            "r", "s", "sh", "t", "u", "ua", "uai", "uan", "uang", "ui", "un", "uo", "v",
            "van", "ve", "vn", "w", "x", "y", "z", "zh", "AA", "EE", "OO",
    };

    private static final String[] JA_SYMBOLS = {
            "N", "a", "a:", "b", "by", "ch", "d", "dy", "e", "e:", "f", "g", "gy", "h",
            "hy", "i", "i:", "j", "k", "ky", "m", "my", "n", "ny", "o", "o:", "p", "py",
            "q", "r", "ry", "s", "sh", "t", "ts", "ty", "u", "u:", "w", "y", "z", "zy",
    };

    private static final String[] EN_SYMBOLS = {
            "aa", "ae", "ah", "ao", "aw", "ay", "b", "ch", "d", "dh", "eh", "er", "ey",
            "f", "g", "hh", "ih", "iy", "jh", "k", "l", "m", "n", "ng", "ow", "oy", "p",
            "r", "s", "sh", "t", "th", "uh", "uw", "V", "w", "y", "z", "zh",
    };

    private static final String[] KR_SYMBOLS = {
            "ᄌ", "ᅥ", "ᆫ", "ᅦ", "ᄋ", "ᅵ", "ᄅ", "ᅴ", "ᄀ", "ᅡ", "ᄎ", "ᅪ", "ᄑ", "ᅩ", "ᄐ", "ᄃ",
            "ᅢ", "ᅮ", "ᆼ", "ᅳ", "ᄒ", "ᄆ", "ᆯ", "ᆷ", "ᄂ", "ᄇ", "ᄉ", "ᆮ", "ᄁ", "ᅬ", "ᅣ", "ᄄ",
            "ᆨ", "ᄍ", "ᅧ", "ᄏ", "ᆸ", "ᅭ", "(", "ᄊ", ")", "ᅲ", "ᅨ", "ᄈ", "ᅱ", "ᅯ", "ᅫ", "ᅰ",
            "ᅤ", "~", "\\", "[", "]", "/", "^", ":", "ㄸ", "*",
    };

    private static final String[] ES_SYMBOLS = {
            "N", "Q", "a", "b", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o",
            "p", "s", "t", "u", "v", "w", "x", "y", "z", "ɑ", "æ", "ʃ", "ʑ", "ç", "ɯ", "ɪ",
            "ɔ", "ɛ", "ɹ", "ð", "ə", "ɫ", "ɥ", "ɸ", "ʊ", "ɾ", "ʒ", "θ", "β", "ŋ", "ɦ", "ɡ",
            "r", "ɲ", "ʝ", "ɣ", "ʎ", "ˈ", "ˌ", "ː",
    };

    private static final String[] FR_SYMBOLS = {"\u0303", "œ", "ø", "ʁ", "ɒ", "ʌ", "ɜ", "ɐ"};
    private static final String[] DE_SYMBOLS = {"ʏ", "̩"};
    private static final String[] RU_SYMBOLS = {"ɭ", "ʲ", "ɕ", "\"", "ɵ", "^", "ɬ"};

    private static final int NUM_ZH_TONES = 6;
    private static final int NUM_JA_TONES = 1;
    private static final int NUM_EN_TONES = 4;
    private static final int NUM_KR_TONES = 1;
    private static final int NUM_ES_TONES = 1;
    private static final int NUM_FR_TONES = 1;
    private static final int NUM_DE_TONES = 1;
    private static final int NUM_RU_TONES = 1;
    private static final int NUM_VI_TONES = 0;

    private static final String[] SYMBOLS;
    private static final Map<String, Integer> SYMBOL_TO_ID = new HashMap<>();

    private static final Map<String, Integer> LANGUAGE_ID_MAP;
    private static final Map<String, Integer> LANGUAGE_TONE_START_MAP;

    static {
        Set<String> normal = new TreeSet<>();
        for (String[] set : new String[][]{ZH_SYMBOLS, JA_SYMBOLS, EN_SYMBOLS, KR_SYMBOLS,
                ES_SYMBOLS, FR_SYMBOLS, DE_SYMBOLS, RU_SYMBOLS}) {
            normal.addAll(Arrays.asList(set));
        }
        List<String> all = new ArrayList<>();
        all.add(PAD);
        all.addAll(normal);
        all.addAll(Arrays.asList(PU_SYMBOLS));
        SYMBOLS = all.toArray(new String[0]);
        for (int i = 0; i < SYMBOLS.length; i++) {
            SYMBOL_TO_ID.put(SYMBOLS[i], i);
        }

        LANGUAGE_ID_MAP = new HashMap<>();
        LANGUAGE_ID_MAP.put("ZH", 0);
        LANGUAGE_ID_MAP.put("JP", 1);
        LANGUAGE_ID_MAP.put("EN", 2);
        LANGUAGE_ID_MAP.put("ZH_MIX_EN", 3);
        LANGUAGE_ID_MAP.put("KR", 4);
        LANGUAGE_ID_MAP.put("ES", 5);
        LANGUAGE_ID_MAP.put("SP", 5);
        LANGUAGE_ID_MAP.put("FR", 6);
        LANGUAGE_ID_MAP.put("DE", 7);
        LANGUAGE_ID_MAP.put("RU", 8);
        LANGUAGE_ID_MAP.put("VI", 9);

        int zh = 0;
        int jp = NUM_ZH_TONES;
        int en = NUM_ZH_TONES + NUM_JA_TONES;
        int kr = en + NUM_EN_TONES;
        int es = kr + NUM_KR_TONES;
        int fr = es + NUM_ES_TONES;
        int de = fr + NUM_FR_TONES;
        int ru = de + NUM_DE_TONES;
        int vi = ru + NUM_RU_TONES;

        LANGUAGE_TONE_START_MAP = new HashMap<>();
        LANGUAGE_TONE_START_MAP.put("ZH", zh);
        LANGUAGE_TONE_START_MAP.put("ZH_MIX_EN", zh);
        LANGUAGE_TONE_START_MAP.put("JP", jp);
        LANGUAGE_TONE_START_MAP.put("EN", en);
        LANGUAGE_TONE_START_MAP.put("KR", kr);
        LANGUAGE_TONE_START_MAP.put("ES", es);
        LANGUAGE_TONE_START_MAP.put("SP", es);
        LANGUAGE_TONE_START_MAP.put("FR", fr);
        LANGUAGE_TONE_START_MAP.put("DE", de);
        LANGUAGE_TONE_START_MAP.put("RU", ru);
        LANGUAGE_TONE_START_MAP.put("VI", vi);
    }

    private PhonemeIds() {
    }

    public static int getUnkId() {
        Integer unk = SYMBOL_TO_ID.get("UNK");
        return unk == null ? -1 : unk;
    }

    public static boolean containsSymbol(String symbol) {
        return SYMBOL_TO_ID.containsKey(symbol);
    }

    /** Port of phonemes_to_ids(cleaned_text, tones, language). */
    public static Result toIds(List<String> phones, List<Integer> tones, String language) {
        int unkId = getUnkId();
        List<Integer> phoneIds = new ArrayList<>(phones.size());
        for (String symbol : phones) {
            Integer id = SYMBOL_TO_ID.get(symbol);
            phoneIds.add(id == null ? unkId : id);
        }
        int toneStart = LANGUAGE_TONE_START_MAP.get(language);
        List<Integer> toneIds = new ArrayList<>(tones.size());
        for (int tone : tones) {
            toneIds.add(tone + toneStart);
        }
        int langId = LANGUAGE_ID_MAP.get(language);
        List<Integer> langIds = new ArrayList<>(phoneIds.size());
        for (int i = 0; i < phoneIds.size(); i++) {
            langIds.add(langId);
        }
        return new Result(phoneIds, toneIds, langIds);
    }

    /** Port of commons.insert_blanks(list, 0): result[1::2] = list. */
    public static List<Integer> insertBlanks(List<Integer> list) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < list.size() * 2 + 1; i++) {
            result.add(0);
        }
        for (int i = 0; i < list.size(); i++) {
            result.set(i * 2 + 1, list.get(i));
        }
        return result;
    }

    public static final class Result {
        public final List<Integer> phoneIds;
        public final List<Integer> toneIds;
        public final List<Integer> langIds;

        public Result(List<Integer> phoneIds, List<Integer> toneIds, List<Integer> langIds) {
            this.phoneIds = phoneIds;
            this.toneIds = toneIds;
            this.langIds = langIds;
        }
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
