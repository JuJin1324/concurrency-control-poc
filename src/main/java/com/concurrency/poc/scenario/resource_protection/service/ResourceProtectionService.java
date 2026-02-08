package com.concurrency.poc.scenario.resource_protection.service;

public interface ResourceProtectionService {
    void process(Long stockId, int amount);
}