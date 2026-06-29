package com.diogotoporcov.filemanager.gateway.config;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Component("clientRateLimitKeyResolver")
@NullMarked
public class ClientRateLimitKeyResolver implements KeyResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IPV4_ADDRESS = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final RateLimitProperties properties;
    private final List<IpSubnet> trustedProxySubnets;

    public ClientRateLimitKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
        this.trustedProxySubnets = properties.getTrustedProxyCidrs().stream()
                .map(IpSubnet::parse)
                .toList();
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(StringUtils::hasText)
                .map(name -> key("principal", name))
                .switchIfEmpty(Mono.fromSupplier(() -> key("ip", resolveClientIp(exchange.getRequest()))));
    }

    private String resolveClientIp(ServerHttpRequest request) {
        InetAddress remoteAddress = remoteAddress(request);

        if (remoteAddress != null && properties.isForwardedHeadersEnabled() && isTrustedProxy(remoteAddress)) {
            String forwardedClient = firstForwardedForAddress(request);
            if (StringUtils.hasText(forwardedClient)) {
                return forwardedClient;
            }
        }

        if (remoteAddress != null) {
            return remoteAddress.getHostAddress();
        }

        return "unknown";
    }

    private @Nullable InetAddress remoteAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress == null ? null : remoteAddress.getAddress();
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxySubnets.stream().anyMatch(subnet -> subnet.contains(address));
    }

    private @Nullable String firstForwardedForAddress(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }

        for (String candidate : forwardedFor.split(",")) {
            String normalized = candidate.trim();
            if (isIpAddress(normalized)) {
                return normalized;
            }
        }

        return null;
    }

    private boolean isIpAddress(String value) {
        if (value.contains(":")) {
            return isValidAddress(value);
        }

        if (!IPV4_ADDRESS.matcher(value).matches()) {
            return false;
        }

        for (String octet : value.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidAddress(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.getAddress().length > 0;
        } catch (UnknownHostException e) {
            return false;
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
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record IpSubnet(InetAddress networkAddress, int prefixLength) {

        private static IpSubnet parse(String value) {
            String[] parts = value.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Trusted proxy CIDR must include a prefix length: " + value);
            }

            try {
                InetAddress networkAddress = InetAddress.getByName(parts[0]);
                int prefixLength = Integer.parseInt(parts[1]);
                int maxPrefixLength = networkAddress.getAddress().length * Byte.SIZE;
                if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix length: " + value);
                }

                return new IpSubnet(networkAddress, prefixLength);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value, e);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] networkBytes = networkAddress.getAddress();
            byte[] addressBytes = address.getAddress();
            if (networkBytes.length != addressBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != addressBytes[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (networkBytes[fullBytes] & mask) == (addressBytes[fullBytes] & mask);
        }
    }
}
