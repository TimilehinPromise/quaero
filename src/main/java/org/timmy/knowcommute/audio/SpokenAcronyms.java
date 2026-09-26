package org.timmy.knowcommute.audio;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pins the pronunciation of acronyms that are said as words.
 *
 * <p>Kokoro's phonemiser reads any all-capitals word missing from its lexicon one letter at a
 * time, which is right for CEO and wrong for EBITDA. Prompting the summariser to respell them
 * is unreliable: it leaves a term alone when it judges it well known, and well known to a
 * reader is not the same as known to the synthesiser. So the terms that recur in the reading
 * list get their phonemes pinned here, in misaki's inline-override syntax, and only on the text
 * sent for synthesis — the stored summary keeps the ordinary spelling.
 *
 * <p>Matching ignores case because the prompt asks the summariser to write these as Ebitda or
 * Nasdaq; both spellings should land on the same pronunciation. Phonemes use misaki's alphabet,
 * where {@code A} is /eɪ/ and {@code O} is /oʊ/, and each entry must be checked against the
 * deployment's {@code /dev/phonemize} before it is trusted.
 */
final class SpokenAcronyms {

    private static final Map<String, String> PHONEMES = Map.of(
            "EBITDA", "ibˈɪtdɑ",
            "NASDAQ", "nˈæzdæk",
            "OPEC", "ˈOpɛk",
            "NATO", "nˈATO",
            "NASA", "nˈæsə",
            "FIFA", "fˈifə",
            "UNICEF", "jˈunəsɛf",
            "UNESCO", "junˈɛskO");

    private static final Pattern KNOWN = Pattern.compile(
            PHONEMES.keySet().stream().collect(Collectors.joining("|", "\\b(", ")\\b")),
            Pattern.CASE_INSENSITIVE);

    private SpokenAcronyms() {
    }

    static String pin(String narration) {
        Matcher matcher = KNOWN.matcher(narration);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(
                "[" + match.group() + "](/" + PHONEMES.get(match.group().toUpperCase(Locale.ROOT)) + "/)"));
    }
}
