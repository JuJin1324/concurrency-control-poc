package com.concurrency.poc.domain;

public class StockNotFoundException extends RuntimeException {

    private static final String MESSAGE_FORMAT = "Stock을 찾을 수 없습니다. ID: %d";

    public StockNotFoundException(Long stockId) {
        super(String.format(MESSAGE_FORMAT, stockId));
    }
}
