package com.filemanager.gateway.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void gatewayProperties_Valid() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("http://localhost:8081");

        Set<ConstraintViolation<GatewayProperties>> violations = validator.validate(properties);
        assertThat(violations).isEmpty();
    }

    @Test
    void gatewayProperties_Blank_Fail() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("");

        Set<ConstraintViolation<GatewayProperties>> violations = validator.validate(properties);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void gatewayProperties_InvalidUrl_Fail() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("not-a-url");

        Set<ConstraintViolation<GatewayProperties>> violations = validator.validate(properties);
        assertThat(violations).isNotEmpty();
    }
}
