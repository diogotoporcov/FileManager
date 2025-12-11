package com.diogotoporcov.accountservice.config;

import com.diogotoporcov.accountservice.application.GetMyProfileUseCase;
import com.diogotoporcov.accountservice.security.AccountStatusGuardFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    AccountStatusGuardFilter accountStatusGuardFilter(GetMyProfileUseCase getMyProfile, ObjectMapper objectMapper) {
        return new AccountStatusGuardFilter(getMyProfile, objectMapper);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AccountStatusGuardFilter guard) {
        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info"
                ).permitAll()
                .anyRequest().authenticated()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        http.addFilterAfter(guard, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
