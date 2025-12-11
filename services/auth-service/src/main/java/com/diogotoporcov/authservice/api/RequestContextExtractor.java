package com.diogotoporcov.authservice.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestContextExtractor {

    public SessionContext extract(HttpServletRequest req, String deviceName) {
        String userAgent = header(req);
        String ip = resolveIp(req);
        return new SessionContext(deviceName, userAgent, ip);
    }

    private static String header(HttpServletRequest req) {
        String value = req.getHeader("User-Agent");
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String resolveIp(HttpServletRequest req) {
        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        return req.getRemoteAddr();
    }

    public record SessionContext(String deviceName, String userAgent, String ipAddress) {}
}
