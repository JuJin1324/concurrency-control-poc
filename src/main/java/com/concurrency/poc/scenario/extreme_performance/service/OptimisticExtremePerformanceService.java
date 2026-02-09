package com.concurrency.poc.scenario.extreme_performance.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticExtremePerformanceService implements ExtremePerformanceService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void process(Long stockId, int amount) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));
        stock.decrease(amount);
        stockRepository.saveAndFlush(stock);
    }
}
