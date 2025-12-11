package com.diogotoporcov.accountservice.profile;

import com.diogotoporcov.accountservice.error.InvalidLocaleException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LocaleUtil {

    private static final Pattern LOCALE_PATTERN = Pattern.compile("^[A-Za-z]{2,3}-[A-Za-z]{2}$");

    private LocaleUtil() {}

    public static String normalizeOrThrow(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidLocaleException("Locale is blank");
        String v = raw.trim();
        if (!LOCALE_PATTERN.matcher(v).matches()) throw new InvalidLocaleException("Invalid locale format (expected like en-US)");

        String[] parts = v.split("-", 2);
        String lang = parts[0].toLowerCase(Locale.ROOT);
        String region = parts[1].toUpperCase(Locale.ROOT);

        Locale parsed = Locale.forLanguageTag(lang + "-" + region);
        if (parsed.getLanguage() == null || parsed.getLanguage().isBlank()) {
            throw new InvalidLocaleException("Invalid locale");
        }

        return lang + "-" + region;
    }
}
