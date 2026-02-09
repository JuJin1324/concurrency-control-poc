package com.concurrency.poc.scenario.extreme_performance.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisExtremePerformanceService implements ExtremePerformanceService {

    private final RedissonClient redissonClient;
    private final StockRepository stockRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void process(Long stockId, int amount) {
        String lockKey = "lock:extreme:stock:" + stockId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!acquired) throw new RuntimeException("Lock 획득 실패");

            transactionTemplate.executeWithoutResult(status -> {
                Stock stock = stockRepository.findById(stockId)
                        .orElseThrow(() -> new StockNotFoundException(stockId));
                stock.decrease(amount);
                stockRepository.saveAndFlush(stock);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
