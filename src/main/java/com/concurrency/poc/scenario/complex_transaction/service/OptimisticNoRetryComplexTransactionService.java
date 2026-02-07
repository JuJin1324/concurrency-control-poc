package com.concurrency.poc.scenario.complex_transaction.service;

import com.concurrency.poc.core.domain.OrderHistory;
import com.concurrency.poc.core.domain.Point;
import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.OrderHistoryRepository;
import com.concurrency.poc.core.repository.PointRepository;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Scenario 1-A] Optimistic Lock - No Retry
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticNoRetryComplexTransactionService implements ComplexTransactionService {

    private final StockRepository stockRepository;
    private final PointRepository pointRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    @Override
    @Transactional
    public void process(Long userId, Long stockId, int amount) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("사용자 포인트를 찾을 수 없습니다. userId: " + userId));

        simulateWait(100);

        stock.decrease(amount);
        point.use((long) amount * 100);
        
        orderHistoryRepository.save(new OrderHistory(userId, stockId, amount));
        
        stockRepository.saveAndFlush(stock);
        pointRepository.saveAndFlush(point);
    }

    private void simulateWait(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
