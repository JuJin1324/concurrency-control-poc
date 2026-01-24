package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.domain.StockNotFoundException;
import com.concurrency.poc.dto.StockResponse;
import com.concurrency.poc.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis Distributed Lock 기반 재고 차감 서비스
 *
 * <h3>Pessimistic Lock과의 비교</h3>
 * <ul>
 *   <li>Pessimistic: DB Row Lock → DB 부하 증가, 단일 DB에서만 유효</li>
 *   <li>Redis Lock: 분산 락 → DB 부하 감소, 다중 인스턴스에서 유효</li>
 * </ul>
 *
 * <h3>Lock과 Transaction 순서</h3>
 * <ol>
 *   <li>Lock 획득 (Redis)</li>
 *   <li>Transaction 시작 (DB)</li>
 *   <li>재고 조회 및 차감</li>
 *   <li>Transaction 커밋</li>
 *   <li>Lock 해제</li>
 * </ol>
 * <p>
 * Transaction이 Lock 안에서 실행되어야 커밋 후 Lock이 해제됩니다.
 * 그렇지 않으면 다른 스레드가 커밋되지 않은 데이터를 읽을 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockStockService implements StockService {

    private static final String LOCK_PREFIX = "lock:stock:";
    private static final long WAIT_TIME = 5L;   // Lock 획득 대기 시간 (초)
    private static final long LEASE_TIME = 3L;  // Lock 유지 시간 (초)

    private final RedissonClient redissonClient;
    private final StockRepository stockRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void decreaseStock(Long stockId, int amount) {
        String lockKey = LOCK_PREFIX + stockId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                throw new RuntimeException("Lock 획득 실패: " + lockKey);
            }

            log.debug("Lock 획득 성공: {}", lockKey);

            // Transaction 안에서 재고 차감 실행
            transactionTemplate.executeWithoutResult(status -> {
                Stock stock = stockRepository.findById(stockId)
                        .orElseThrow(() -> new StockNotFoundException(stockId));

                stock.decrease(amount);
                stockRepository.saveAndFlush(stock);
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock 획득 중 인터럽트 발생", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock 해제: {}", lockKey);
            }
        }
    }

    @Override
    public StockResponse getStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        return new StockResponse(stock.getId(), stock.getProductId(), stock.getQuantity());
    }
}
