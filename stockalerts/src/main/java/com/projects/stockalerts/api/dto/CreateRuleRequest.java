package com.projects.stockalerts.api.dto;

import java.math.BigDecimal;

import com.projects.stockalerts.domain.Direction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

public record CreateRuleRequest (
     @NotBlank String ticker,
        @NotNull Direction direction,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal threshold,
        Boolean enabled
) {}
