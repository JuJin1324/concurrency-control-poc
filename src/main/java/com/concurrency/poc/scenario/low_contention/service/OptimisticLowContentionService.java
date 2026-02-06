package com.concurrency.poc.scenario.low_contention.service;

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
 * [Best Fit Scenario 2] Low Contention Update
 * 
 * 낙관적 락이 주인공이 되는 시나리오를 시뮬레이션합니다.
 * 충돌이 적은 환경에서 DB 락 대기 없이 비즈니스 로직을 병렬로 수행하여
 * 최고의 TPS를 뽑아내는 낙관적 락의 성능적 우위를 증명합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticLowContentionService implements LowContentionService {

    private final StockServiceHelper stockServiceHelper;

    @Override
    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 10,
            delay = 50,
            multiplier = 2,
            maxDelay = 1000
    )
    // 1. 진입점: 트랜잭션 없음 (커넥션 미점유)
    public void process(Long stockId, int amount) {
        // 2. 데이터 조회: 별도 트랜잭션 후 커넥션 반납
        Stock stock = stockServiceHelper.fetchStock(stockId);

        // 3. 비즈니스 로직 수행: 커넥션 없이 1초 대기
        simulateComplexLogic();

        // 4. 업데이트: 새로운 트랜잭션으로 버전 체크 및 수정
        stockServiceHelper.updateStock(stock.getId(), amount);
    }

    private void simulateComplexLogic() {
        try {
            // 극한의 지연 상황 (1초)
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
