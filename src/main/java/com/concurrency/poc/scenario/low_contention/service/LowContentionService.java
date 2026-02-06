package com.concurrency.poc.scenario.low_contention.service;

public interface LowContentionService {
    void process(Long stockId, int amount);
}
