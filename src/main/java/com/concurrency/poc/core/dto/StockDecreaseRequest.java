package com.concurrency.poc.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockDecreaseRequest(
        @NotNull(message = "stockId는 필수입니다")
        Long stockId,

        @NotNull(message = "amount는 필수입니다")
        @Min(value = 1, message = "amount는 1 이상이어야 합니다")
        Integer amount
) {
}
