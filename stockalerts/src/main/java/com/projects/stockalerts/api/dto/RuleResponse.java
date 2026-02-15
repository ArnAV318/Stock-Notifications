package com.projects.stockalerts.api.dto;

import java.math.BigDecimal;
import com.projects.stockalerts.domain.Direction;

public record RuleResponse(
        String userId,
        String ruleId,
        String ticker,
        Direction direction,
        BigDecimal threshold,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}