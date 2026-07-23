package fi.vm.sade.viestinvalitys.validation;

import java.util.Optional;
import java.util.UUID;

public final class ParametriUtil {

    private ParametriUtil() {}

    public static Optional<UUID> asUUID(String tunniste) {
        if (tunniste == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tunniste));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> asUUID(Optional<String> tunniste) {
        return tunniste.flatMap(ParametriUtil::asUUID);
    }

    public static Optional<Integer> asInt(String arvo) {
        if (arvo == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(arvo));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Integer> asInt(Optional<String> arvo) {
        return arvo.flatMap(ParametriUtil::asInt);
    }
}
