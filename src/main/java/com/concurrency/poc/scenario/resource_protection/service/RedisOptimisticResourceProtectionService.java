package com.concurrency.poc.scenario.resource_protection.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisOptimisticResourceProtectionService implements ResourceProtectionService {

    private static final String LOCK_PREFIX = "lock:resource-protection:stock:";
    private static final long WAIT_TIME = 3L;
    private static final long LEASE_TIME = 5L;

    private final RedissonClient redissonClient;
    private final StockRepository stockRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void process(Long stockId, int amount) {
        String lockKey = LOCK_PREFIX + stockId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // 1. Redis 계층에서 유입량 제어 (DB 커넥션 미사용)
            acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                throw new RuntimeException("Redis Lock 획득 실패 (Timeout): " + lockKey);
            }

            // 2. 락 획득 후 트랜잭션 진입
            transactionTemplate.executeWithoutResult(status -> {
                Stock stock = stockRepository.findById(stockId)
                        .orElseThrow(() -> new StockNotFoundException(stockId));

                // 비즈니스 로직 시뮬레이션 (100ms)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                stock.decrease(amount);
                // 3. 낙관적 락(Version) 최종 검증
                stockRepository.saveAndFlush(stock);
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock 획득 중 인터럽트 발생", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}