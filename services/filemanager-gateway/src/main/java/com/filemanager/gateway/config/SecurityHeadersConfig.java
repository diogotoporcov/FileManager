package com.filemanager.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityHeadersConfig {

    @Bean
    public WebFilter securityHeadersFilter() {
        return (exchange, chain) -> {
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponse().getHeaders().set("X-Frame-Options", "DENY");
                exchange.getResponse().getHeaders().set("Referrer-Policy", "no-referrer");
                return Mono.empty();
            });
            return chain.filter(exchange);
        };
    }
}
