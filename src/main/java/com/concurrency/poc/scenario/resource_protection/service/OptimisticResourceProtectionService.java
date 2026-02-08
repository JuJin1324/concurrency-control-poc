package com.concurrency.poc.scenario.resource_protection.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticResourceProtectionService implements ResourceProtectionService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void process(Long stockId, int amount) {
        log.debug("Optimistic Resource Protection - Attempting to decrease stock: {}", stockId);

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        // 비즈니스 로직 시뮬레이션 (100ms)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        stock.decrease(amount);
        
        // Stock entity has @Version, so Spring Data JPA will handle Optimistic Locking on save/flush.
        stockRepository.saveAndFlush(stock);
    }
}
