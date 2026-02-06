package com.concurrency.poc.scenario.low_contention.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Best Fit Scenario 2 - Comparison Target]
 * 
 * 낙관적 락의 우위를 증명하기 위한 비교 대상(비관적 락)입니다.
 * 낙관적 락과 동일하게 50ms의 로직이 포함되지만, 락을 잡고 대기하므로
 * 저경합 상황에서도 전체 처리량이 어떻게 제한되는지 보여줍니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticLowContentionService implements LowContentionService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void process(Long stockId, int amount) {
        // 비관적 락 획득
        Stock stock = stockRepository.findByIdWithPessimisticLock(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        // 동일한 비즈니스 로직 지연 (50ms)
        simulateComplexLogic();

        stock.decrease(amount);
    }

    private void simulateComplexLogic() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
