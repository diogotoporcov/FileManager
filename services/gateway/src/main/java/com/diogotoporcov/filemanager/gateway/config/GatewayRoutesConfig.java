package com.diogotoporcov.filemanager.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    private final GatewayProperties gatewayProperties;
    private final GatewayRequestProperties gatewayRequestProperties;
    private final RateLimitProperties rateLimitProperties;
    private final KeyResolver clientRateLimitKeyResolver;

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(
                rateLimitProperties.getReplenishRate(),
                rateLimitProperties.getBurstCapacity(),
                rateLimitProperties.getRequestedTokens()
        );
    }

    @Bean
    public RouteLocator fileManagerRoutes(RouteLocatorBuilder routes, RedisRateLimiter redisRateLimiter) {
        return routes.routes()
                .route("internal_stop", route -> route
                        .path("/api/v1/internal/**", "/internal/**")
                        .filters(filters -> filters.setStatus(HttpStatus.NOT_FOUND))
                        .uri("no://op"))
                .route("filemanager_api_v1", route -> route
                        .path("/api/v1/**")
                        .filters(filters -> apiFilters(filters, redisRateLimiter))
                        .uri(gatewayProperties.getBaseUrl()))
                .build();
    }

    private GatewayFilterSpec apiFilters(GatewayFilterSpec filters, RedisRateLimiter redisRateLimiter) {
        GatewayFilterSpec sizedFilters = filters.setRequestSize(gatewayRequestProperties.getMaxRequestSize());

        if (!rateLimitProperties.isEnabled()) {
            return sizedFilters.rewritePath("/api/v1/(?<segment>.*)", "/${segment}");
        }

        return sizedFilters
                .requestRateLimiter(config -> {
                    config.setKeyResolver(clientRateLimitKeyResolver);
                    config.setRateLimiter(redisRateLimiter);
                })
                .rewritePath("/api/v1/(?<segment>.*)", "/${segment}");
    }
}
