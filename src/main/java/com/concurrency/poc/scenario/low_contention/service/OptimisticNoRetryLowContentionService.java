package com.concurrency.poc.scenario.low_contention.service;

import com.concurrency.poc.core.domain.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [Scenario 2-A] Optimistic Lock - No Retry
 * 순수 충돌률 측정을 위한 재시도 없는 버전입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticNoRetryLowContentionService implements LowContentionService {

    private final StockServiceHelper stockServiceHelper;

    @Override
    public void process(Long stockId, int amount) {
        Stock stock = stockServiceHelper.fetchStock(stockId);
        simulateComplexLogic();
        stockServiceHelper.updateStock(stock.getId(), amount);
    }

    private void simulateComplexLogic() {
        try {
            Thread.sleep(100); // 1000ms는 너무 길어서 100ms로 조정하여 더 많은 샘플 확보
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
