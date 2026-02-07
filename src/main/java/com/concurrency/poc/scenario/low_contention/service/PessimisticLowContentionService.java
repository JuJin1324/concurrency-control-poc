package com.concurrency.poc.scenario.low_contention.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Scenario 2 - Comparison Target] Pessimistic Lock
 * 공정한 비교를 위해 지연 시간을 100ms로 맞추었습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticLowContentionService implements LowContentionService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void process(Long stockId, int amount) {
        Stock stock = stockRepository.findByIdWithPessimisticLock(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        simulateComplexLogic();

        stock.decrease(amount);
    }

    private void simulateComplexLogic() {
        try {
            Thread.sleep(100); // 1000ms에서 100ms로 하향 조정
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}