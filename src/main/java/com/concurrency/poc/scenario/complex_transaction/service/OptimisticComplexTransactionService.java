package com.concurrency.poc.scenario.complex_transaction.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Best Fit Scenario 1 - Comparison Target]
 * 
 * 비관적 락의 우위를 증명하기 위한 비교 대상(낙관적 락)입니다.
 * 복잡한 트랜잭션 상황에서 낙관적 락은 빈번한 재시도로 인해
 * 오히려 성능이 저하되거나 안정성이 떨어질 수 있음을 보여줍니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticComplexTransactionService implements ComplexTransactionService {

    private final StockRepository stockRepository;

    @Override
    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 10,
            delay = 50,
            multiplier = 2,
            maxDelay = 1000
    )
    @Transactional
    public void process(Long stockId, int amount) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        simulateComplexLogic();

        stock.decrease(amount);
        stockRepository.saveAndFlush(stock);
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
