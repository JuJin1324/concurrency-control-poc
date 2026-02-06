package com.concurrency.poc.core.dto;

public record StockResponse(
        Long id,
        String productId,
        int quantity
) {
}
