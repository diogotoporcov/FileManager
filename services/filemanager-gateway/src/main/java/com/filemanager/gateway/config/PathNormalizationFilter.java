package com.filemanager.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PathNormalizationFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        String path = exchange.getRequest().getURI().getPath();
        
        if (isTraversal(rawPath) || isTraversal(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isTraversal(String p) {
        if (p == null) return false;
        return p.contains("/../") || p.contains("/%2e%2e/") || p.contains("/%2E%2E/") ||
               p.endsWith("/..") || p.endsWith("/%2e%2e") || p.endsWith("/%2E%2E") ||
               p.contains("../") || p.contains("%2e%2e/") || p.contains("%2E%2E/");
    }
}
