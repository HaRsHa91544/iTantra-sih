package com.example.itantra_sih.speech.tts.text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of tiny_tts/text/english_utils/{number_norm,time_norm,abbreviations}.py
 *
 * Expands numbers, times, and abbreviations in English text to spoken words,
 * matching the Python reference (which uses the `inflect` library).
 */
public final class Normalizer {

    private static final Pattern COMMA_NUMBER_RE = Pattern.compile("([0-9][0-9,]+[0-9])");
    private static final Pattern DECIMAL_NUMBER_RE = Pattern.compile("([0-9]+\\.[0-9]+)");
    private static final Pattern CURRENCY_RE = Pattern.compile("(£|\\$|¥)([0-9,\\.]*[0-9]+)");
    private static final Pattern ORDINAL_RE = Pattern.compile("[0-9]+(st|nd|rd|th)");
    private static final Pattern NUMBER_RE = Pattern.compile("-?[0-9]+");
    private static final Pattern TIME_RE = Pattern.compile(
            "(?i)\\b((0?[0-9])|(1[0-1])|(1[2-9])|(2[0-3])):([0-5][0-9])\\s*(a\\.m\\.|am|pm|p\\.m\\.|a\\.m|p\\.m)?\\b");

    private static final String[][] ABBREVIATIONS = {
            {"mrs", "misess"},
            {"mr", "mister"},
            {"dr", "doctor"},
            {"st", "saint"},
            {"co", "company"},
            {"jr", "junior"},
            {"maj", "major"},
            {"gen", "general"},
            {"drs", "doctors"},
            {"rev", "reverend"},
            {"lt", "lieutenant"},
            {"hon", "honorable"},
            {"sgt", "sergeant"},
            {"capt", "captain"},
            {"esq", "esquire"},
            {"ltd", "limited"},
            {"col", "colonel"},
            {"ft", "fort"},
    };

    private Normalizer() {
    }

    public static String normalizeText(String text) {
        text = text.toLowerCase(Locale.ROOT);
        text = expandTimeEnglish(text);
        text = normalizeNumbers(text);
        text = expandAbbreviations(text);
        return text;
    }

    private static String normalizeNumbers(String text) {
        text = replaceAll(COMMA_NUMBER_RE, text, m -> m.group(1).replace(",", ""));
        text = replaceAll(CURRENCY_RE, text, m -> expandCurrency(m.group(1), m.group(2)));
        text = replaceAll(DECIMAL_NUMBER_RE, text, m -> m.group(1).replace(".", " point "));
        text = replaceAll(ORDINAL_RE, text, m -> NumberToWords.ordinalWords(m.group(0)));
        text = replaceAll(NUMBER_RE, text, m -> NumberToWords.numberToWords(Long.parseLong(m.group(0))));
        return text;
    }

    private static String expandCurrency(String unit, String value) {
        // Mirrors __expand_currency / _expand_currency in number_norm.py
        String[] parts = value.replace(",", "").split("\\.");
        if (parts.length > 2) {
            return value + " " + "??"; // unexpected format
        }
        StringBuilder text = new StringBuilder();
        long integer = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
        long fraction = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : 0;

        String singular = unitOf(unit, 1);
        String plural = unitOf(unit, 2);
        String fracSingular = unitOf(unit, 11);
        String fracPlural = unitOf(unit, 12);

        if (integer > 0) {
            text.append(integer).append(" ").append(integer == 1 ? singular : plural);
        }
        if (fraction > 0) {
            if (text.length() > 0) text.append(" ");
            text.append(fraction).append(" ").append(fraction == 1 ? fracSingular : fracPlural);
        }
        if (text.length() == 0) {
            return "zero " + plural;
        }
        return text.toString();
    }

    private static String unitOf(String unit, int key) {
        switch (unit) {
            case "$":
                switch (key) {
                    case 1: return "dollar";
                    case 2: return "dollars";
                    case 11: return "cent";
                    case 12: return "cents";
                }
                break;
            case "£":
                switch (key) {
                    case 1: return "pound sterling";
                    case 2: return "pounds sterling";
                    case 11: return "penny";
                    case 12: return "pence";
                }
                break;
            case "¥":
                switch (key) {
                    case 2: return "yen";
                    case 12: return "sen";
                }
                break;
            default:
                return "???";
        }
        return "???";
    }

    private static String expandTimeEnglish(String text) {
        return replaceAll(TIME_RE, text, m -> expandTime(m));
    }

    private static String expandTime(Matcher m) {
        String hourStr = m.group(1);
        int hour = Integer.parseInt(hourStr);
        boolean pastNoon = hour >= 12;
        if (hour > 12) {
            hour -= 12;
        } else if (hour == 0) {
            hour = 12;
            pastNoon = true;
        }
        StringBuilder time = new StringBuilder();
        time.append(NumberToWords.numberToWords(hour));
        String minuteStr = m.group(m.groupCount() - 1); // minutes group
        int minute = Integer.parseInt(minuteStr);
        if (minute > 0) {
            if (minute < 10) time.append(" oh");
            time.append(" ").append(NumberToWords.numberToWords(minute));
        }
        String amPm = m.group(m.groupCount()); // am/pm group
        if (amPm == null) {
            time.append(pastNoon ? " p m" : " a m");
        } else {
            String cleaned = amPm.replace(".", "");
            for (char c : cleaned.toCharArray()) {
                time.append(" ").append(c);
            }
        }
        return time.toString().trim();
    }

    private static String expandAbbreviations(String text) {
        for (String[] pair : ABBREVIATIONS) {
            Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(pair[0]) + "\\.");
            Matcher matcher = p.matcher(text);
            text = matcher.replaceAll(pair[1]);
        }
        return text;
    }

    private static String replaceAll(Pattern pattern, String input,
                                     java.util.function.Function<Matcher, String> replacer) {
        Matcher m = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Minimal English cardinal/ordinal number->words, matching inflect's "and"-less style. */
    private static final class NumberToWords {
        private static final String[] ONES = {
                "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
                "seventeen", "eighteen", "nineteen"
        };
        private static final String[] TENS = {
                "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        };

        static String numberToWords(long n) {
            if (n < 20) return ONES[(int) n];
            if (n < 100) {
                int t = (int) (n / 10), o = (int) (n % 10);
                return TENS[t] + (o == 0 ? "" : "-" + ONES[o]);
            }
            if (n < 1000) {
                long h = n / 100, r = n % 100;
                return ONES[(int) h] + " hundred" + (r == 0 ? "" : " " + numberToWords(r));
            }
            if (n < 1_000_000) {
                long k = n / 1000, r = n % 1000;
                return numberToWords(k) + " thousand" + (r == 0 ? "" : " " + numberToWords(r));
            }
            if (n < 1_000_000_000L) {
                long m = n / 1_000_000L, r = n % 1_000_000L;
                return numberToWords(m) + " million" + (r == 0 ? "" : " " + numberToWords(r));
            }
            long b = n / 1_000_000_000L, r = n % 1_000_000_000L;
            return numberToWords(b) + " billion" + (r == 0 ? "" : " " + numberToWords(r));
        }

        /** Input like "3rd" -> "third". Covers 1st-20th and tens/teens via pattern. */
        static String ordinalWords(String token) {
            String digits = token.replaceAll("[^0-9]", "");
            long n = digits.isEmpty() ? 0 : Long.parseLong(digits);
            return ordinal(n);
        }

        private static String ordinal(long n) {
            long v = Math.abs(n);
            if (v < 20) {
                switch ((int) v) {
                    case 1: return "first";
                    case 2: return "second";
                    case 3: return "third";
                    case 4: return "fourth";
                    case 5: return "fifth";
                    case 6: return "sixth";
                    case 7: return "seventh";
                    case 8: return "eighth";
                    case 9: return "ninth";
                    case 10: return "tenth";
                    case 11: return "eleventh";
                    case 12: return "twelfth";
                    case 13: return "thirteenth";
                    case 14: return "fourteenth";
                    case 15: return "fifteenth";
                    case 16: return "sixteenth";
                    case 17: return "seventeenth";
                    case 18: return "eighteenth";
                    case 19: return "nineteenth";
                    default: return "zeroth";
                }
            }
            long tens = (v / 10) % 10, ones = v % 10;
            if (tens == 1) {
                return numberToWords(v) + "th"; // 20th..99 approximated
            }
            String base = numberToWords(v);
            if (ones == 1) return base + "st";
            if (ones == 2) return base + "nd";
            if (ones == 3) return base + "rd";
            return base + "th";
        }
    }
}
