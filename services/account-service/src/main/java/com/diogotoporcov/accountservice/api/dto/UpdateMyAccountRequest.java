package com.diogotoporcov.accountservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateMyAccountRequest(
        String fullName,

        @JsonSetter(nulls = Nulls.FAIL)
        String username,

        @JsonSetter(nulls = Nulls.FAIL)
        String locale,

        @JsonSetter(nulls = Nulls.FAIL)
        String timezone
) {}
