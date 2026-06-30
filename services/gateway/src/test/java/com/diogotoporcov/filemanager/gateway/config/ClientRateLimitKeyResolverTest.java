package com.diogotoporcov.filemanager.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientRateLimitKeyResolverTest {

    @Test
    void resolvesPrincipalBeforeClientIp() {
        ClientRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        Principal principal = new TestPrincipal("user-123");
        ServerWebExchange exchange = exchange("10.0.0.10", "203.0.113.9")
                .mutate()
                .principal(Mono.just(principal))
                .build();

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(key("principal", "user-123"));
    }

    @Test
    void resolvesForwardedClientIpFromTrustedProxy() {
        ClientRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        ServerWebExchange exchange = exchange("10.0.0.10", "203.0.113.9, 198.51.100.4");

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(key("ip", "203.0.113.9"));
    }

    @Test
    void ignoresForwardedClientIpFromUntrustedRemoteAddress() {
        ClientRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        ServerWebExchange exchange = exchange("198.51.100.10", "203.0.113.9");

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(key("ip", "198.51.100.10"));
    }

    @Test
    void skipsInvalidForwardedClientIpEntries() {
        ClientRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        ServerWebExchange exchange = exchange("10.0.0.10", "unknown, 203.0.113.9");

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(key("ip", "203.0.113.9"));
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedHeadersAreDisabled() {
        RateLimitProperties properties = properties(List.of("10.0.0.0/8"));
        properties.setForwardedHeadersEnabled(false);
        ClientRateLimitKeyResolver resolver = new ClientRateLimitKeyResolver(properties);
        ServerWebExchange exchange = exchange("10.0.0.10", "203.0.113.9");

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo(key("ip", "10.0.0.10"));
    }

    @Test
    void rejectsInvalidTrustedProxyCidr() {
        RateLimitProperties properties = properties(List.of("10.0.0.0"));

        assertThatThrownBy(() -> new ClientRateLimitKeyResolver(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trusted proxy CIDR");
    }

    private ClientRateLimitKeyResolver resolver(List<String> trustedProxyCidrs) {
        return new ClientRateLimitKeyResolver(properties(trustedProxyCidrs));
    }

    private RateLimitProperties properties(List<String> trustedProxyCidrs) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxyCidrs(trustedProxyCidrs);
        return properties;
    }

    private ServerWebExchange exchange(String remoteAddress, String forwardedFor) {
        try {
            MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/files")
                    .remoteAddress(new InetSocketAddress(InetAddress.getByName(remoteAddress), 12345));
            if (forwardedFor != null) {
                builder.header("X-Forwarded-For", forwardedFor);
            }

            return MockServerWebExchange.from(builder);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String key(String prefix, String value) {
        return prefix + ":" + sha256(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record TestPrincipal(String name) implements Principal {

        @Override
        public String getName() {
            return name;
        }
    }
}
