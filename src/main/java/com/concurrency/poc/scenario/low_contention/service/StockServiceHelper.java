package com.concurrency.poc.scenario.low_contention.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP Proxy를 정상적으로 적용하기 위한 Helper 컴포넌트.
 * OptimisticLowContentionService에서 직접 호출함으로써 @Transactional이 무시되는 문제를 해결함.
 */
@Component
@RequiredArgsConstructor
public class StockServiceHelper {

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public Stock fetchStock(Long stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));
    }

    @Transactional
    public void updateStock(Long id, int amount) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException(id));
        
        stock.decrease(amount);
        stockRepository.saveAndFlush(stock);
    }
}
