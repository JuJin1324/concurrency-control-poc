package com.concurrency.poc.scenario.complex_transaction.service;

public interface ComplexTransactionService {
    void process(Long stockId, int amount);
}
