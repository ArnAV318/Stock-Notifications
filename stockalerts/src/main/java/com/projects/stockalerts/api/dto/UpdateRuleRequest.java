package com.projects.stockalerts.api.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import com.projects.stockalerts.domain.Direction;

public record UpdateRuleRequest(
        String ticker,
        Direction direction,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal threshold,
        Boolean enabled
) {}