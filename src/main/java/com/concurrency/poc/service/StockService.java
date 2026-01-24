package com.concurrency.poc.service;

import com.concurrency.poc.dto.StockResponse;

public interface StockService {

    void decreaseStock(Long stockId, int amount);

    StockResponse getStock(Long stockId);
}
