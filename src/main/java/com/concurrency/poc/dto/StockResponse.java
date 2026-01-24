package com.concurrency.poc.dto;

public record StockResponse(
        Long id,
        String productId,
        int quantity
) {
}
