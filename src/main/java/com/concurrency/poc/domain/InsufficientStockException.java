package com.concurrency.poc.domain;

public class InsufficientStockException extends RuntimeException {

    private static final String MESSAGE_FORMAT = "재고가 부족합니다. 현재 재고: %d, 요청 수량: %d";

    public InsufficientStockException(int currentQuantity, int requestedAmount) {
        super(String.format(MESSAGE_FORMAT, currentQuantity, requestedAmount));
    }
}
