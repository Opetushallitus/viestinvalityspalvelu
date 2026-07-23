package fi.vm.sade.viestinvalitys.util;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Detects the language(s) of a Viesti content when the {@code kielet} field is omitted. Ported from
 * the {@code vastaanotto} lambda's {@code LanguageDetection}; returns lower-case wire codes
 * ("fi"/"sv"/"en") matching the request format.
 */
public final class LanguageDetection {

    private LanguageDetection() {}

    private static final LanguageDetector DETECTOR =
            LanguageDetectorBuilder.fromLanguages(Language.FINNISH, Language.SWEDISH, Language.ENGLISH).build();

    private static final double LUOTTAMUS_RAJA = 0.85;

    public static Set<String> tunnistaKieli(String teksti) {
        Set<String> kielet = new LinkedHashSet<>();
        DETECTOR.computeLanguageConfidenceValues(teksti).forEach((kieli, luottamus) -> {
            if (luottamus > LUOTTAMUS_RAJA) {
                switch (kieli) {
                    case FINNISH -> kielet.add("fi");
                    case SWEDISH -> kielet.add("sv");
                    case ENGLISH -> kielet.add("en");
                    default -> { }
                }
            }
        });
        return kielet;
    }
}
