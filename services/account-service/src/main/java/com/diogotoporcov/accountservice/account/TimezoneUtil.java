package com.diogotoporcov.accountservice.account;

import com.diogotoporcov.accountservice.error.InvalidTimezoneException;

import java.time.ZoneId;

public final class TimezoneUtil {

    private TimezoneUtil() {}

    public static String normalizeOrThrow(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidTimezoneException("Timezone is blank");
        String v = raw.trim();
        try {
            ZoneId zone = ZoneId.of(v);
            return zone.getId();
        } catch (Exception e) {
            throw new InvalidTimezoneException("Invalid timezone (expected IANA like America/Sao_Paulo or UTC)");
        }
    }
}
