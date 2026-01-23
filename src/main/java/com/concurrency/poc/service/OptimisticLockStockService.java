package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.domain.StockNotFoundException;
import com.concurrency.poc.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticLockStockService implements StockService {

    /**
     * 최대 재시도 횟수
     * Optimistic Lock 충돌 시 이 횟수만큼 재시도
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 재시도 간 대기 시간 (ms)
     * 충돌 가능성을 줄이기 위한 백오프
     */
    private static final long RETRY_DELAY_MS = 50;

    private final StockRepository stockRepository;

    /**
     * Self-injection for @Transactional to work on internal method calls.
     * Spring AOP proxies don't intercept self-invocation,
     * so we inject the proxy to ensure transactional behavior.
     */
    @Lazy
    @Autowired
    private OptimisticLockStockService self;

    @Override
    public void decreaseStock(Long stockId, int amount) {
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                self.doDecreaseStock(stockId, amount);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.debug("Optimistic lock conflict, retry {}/{}", retryCount, MAX_RETRY_COUNT);

                if (retryCount >= MAX_RETRY_COUNT) {
                    throw e;
                }

                sleep(RETRY_DELAY_MS);
            }
        }
    }

    @Transactional
    public void doDecreaseStock(Long stockId, int amount) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        stock.decrease(amount);
        stockRepository.saveAndFlush(stock);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
