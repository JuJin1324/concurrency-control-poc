package com.concurrency.poc.dto;

public record StockDecreaseResponse(
        boolean success,
        int remainingQuantity,
        String message
) {
    public static StockDecreaseResponse success(int remainingQuantity) {
        return new StockDecreaseResponse(true, remainingQuantity, "재고 차감 성공");
    }
}
