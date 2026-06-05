package com.filemanager.api.auth.security;

import com.filemanager.api.config.InternalApiProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiTokenFilterTest {

    private static final String VALID_TOKEN = "12345678901234567890123456789012";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingAuthorizationHeader() throws Exception {
        MockHttpServletResponse response = doFilter(null);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsNonBearerAuthorizationHeader() throws Exception {
        MockHttpServletResponse response = doFilter("Basic abc");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsEmptyBearerToken() throws Exception {
        MockHttpServletResponse response = doFilter("Bearer ");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWhitespaceBearerToken() throws Exception {
        MockHttpServletResponse response = doFilter("Bearer    ");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongBearerToken() throws Exception {
        MockHttpServletResponse response = doFilter("Bearer wrong-token");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsCorrectBearerToken() throws Exception {
        MockHttpServletResponse response = doFilter("Bearer " + VALID_TOKEN);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_INTERNAL_SERVICE");
    }

    @Test
    void validatesConfiguredTokenIsPresentAndLongEnough() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validateApiToken(validator, null)).isNotEmpty();
            assertThat(validateApiToken(validator, "")).isNotEmpty();
            assertThat(validateApiToken(validator, "short-token")).isNotEmpty();
            assertThat(validateApiToken(validator, VALID_TOKEN)).isEmpty();
        }
    }

    private MockHttpServletResponse doFilter(String authHeader) throws Exception {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiToken(VALID_TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalApiTokenFilter(properties).doFilter(request, response, new MockFilterChain());

        return response;
    }

    private Set<ConstraintViolation<InternalApiProperties>> validateApiToken(Validator validator, String token) {
        InternalApiProperties properties = new InternalApiProperties();
        properties.setApiToken(token);

        return validator.validate(properties);
    }
}