package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.domain.StockNotFoundException;
import com.concurrency.poc.dto.StockResponse;
import com.concurrency.poc.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optimistic Lock 기반 재고 차감 서비스
 *
 * <h3>Pessimistic Lock과의 비교</h3>
 * <ul>
 *   <li>Pessimistic: Lock 대기 → 100% 성공, 낮은 동시성</li>
 *   <li>Optimistic: 충돌 시 재시도 → 높은 동시성, 성공률은 설정에 의존</li>
 * </ul>
 *
 * <h3>트레이드 오프: 성공률 vs 성능</h3>
 * <ul>
 *   <li>재시도 적음 → 빠른 실패, 낮은 성공률 (~60-70%)</li>
 *   <li>재시도 많음 + Exponential Backoff → 높은 성공률 (~100%), 긴 처리 시간</li>
 * </ul>
 *
 * <h3>적합한 상황</h3>
 * <ul>
 *   <li>충돌이 드문 경우 (읽기 > 쓰기)</li>
 *   <li>짧은 트랜잭션</li>
 *   <li>일부 실패를 허용할 수 있는 경우</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticLockStockService implements StockService {

    private final StockRepository stockRepository;

    /**
     * Spring Framework 7 네이티브 @Retryable 설정:
     * - includes: 버전 충돌 시 발생하는 예외
     * - maxRetries: 최대 10회 재시도
     * - delay/multiplier/maxDelay: Exponential Backoff (50ms → 100ms → ... → 1000ms)
     */
    @Override
    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 10,
            delay = 50,
            multiplier = 2,
            maxDelay = 1000
    )
    @Transactional
    public void decreaseStock(Long stockId, int amount) {
        log.debug("Attempting to decrease stock: stockId={}, amount={}", stockId, amount);

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        stock.decrease(amount);
        stockRepository.saveAndFlush(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse getStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        return new StockResponse(stock.getId(), stock.getProductId(), stock.getQuantity());
    }
}
