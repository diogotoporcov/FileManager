package com.diogotoporcov.accountservice.profile;

import com.diogotoporcov.accountservice.error.InvalidLocaleException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class LocaleUtil {

    private static final Pattern LOCALE_PATTERN = Pattern.compile("^[A-Za-z]{2,3}-[A-Za-z]{2}$");

    private static final Set<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private LocaleUtil() {}

    public static String normalizeOrThrow(String rawLocaleInput) {
        if (rawLocaleInput == null || rawLocaleInput.isBlank()) {
            throw new InvalidLocaleException("Locale is blank");
        }

        String trimmedLocale = rawLocaleInput.trim();
        if (!LOCALE_PATTERN.matcher(trimmedLocale).matches()) {
            throw new InvalidLocaleException("Invalid locale format (expected like en-US)");
        }

        String[] localeParts = trimmedLocale.split("-", 2);
        String languageCode = localeParts[0].toLowerCase(Locale.ROOT);
        String regionCode   = localeParts[1].toUpperCase(Locale.ROOT);

        if (!ISO_LANGUAGES.contains(languageCode)) {
            throw new InvalidLocaleException("Unknown language: " + languageCode);
        }

        if (!ISO_COUNTRIES.contains(regionCode)) {
            throw new InvalidLocaleException("Unknown region: " + regionCode);
        }

        return languageCode + "-" + regionCode;
    }
}
