package com.concurrency.poc.scenario.low_contention.service;

import com.concurrency.poc.core.domain.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * [Scenario 2-B] Optimistic Lock - With 3 Retries
 * 최소한의 재시도로 100% 성공을 지향하는 버전입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticRetryLowContentionService implements LowContentionService {

    private final StockServiceHelper stockServiceHelper;

    @Override
    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 50
    )
    public void process(Long stockId, int amount) {
        Stock stock = stockServiceHelper.fetchStock(stockId);
        simulateComplexLogic();
        stockServiceHelper.updateStock(stock.getId(), amount);
    }

    private void simulateComplexLogic() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
