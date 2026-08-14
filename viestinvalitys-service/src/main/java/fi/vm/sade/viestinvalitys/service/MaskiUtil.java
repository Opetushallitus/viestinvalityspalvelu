package fi.vm.sade.viestinvalitys.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MaskiUtil {

    private static final String DEFAULT_MASKI = "xxxxx";

    private MaskiUtil() {}

    public static String maskaaSalaisuudet(String input, Map<String, String> maskit) {
        if (input == null || maskit.isEmpty()) {
            return input;
        }
        String pattern = maskit.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            String maski = maskit.get(matcher.group());
            matcher.appendReplacement(masked, Matcher.quoteReplacement(maski != null ? maski : DEFAULT_MASKI));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }
}
