package com.concurrency.poc.service;

public interface StockService {

    void decreaseStock(Long stockId, int amount);
}
