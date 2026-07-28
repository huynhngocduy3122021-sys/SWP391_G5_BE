package Parking.Util;

import java.util.Locale;

public class LicensePlateNormalizer {
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim()
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }
}
