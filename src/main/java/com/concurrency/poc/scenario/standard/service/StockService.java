package com.concurrency.poc.scenario.standard.service;

import com.concurrency.poc.core.dto.StockResponse;

public interface StockService {

    void decreaseStock(Long stockId, int amount);

    StockResponse getStock(Long stockId);
}
