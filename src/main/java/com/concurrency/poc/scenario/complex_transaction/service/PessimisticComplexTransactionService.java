package com.concurrency.poc.scenario.complex_transaction.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Best Fit Scenario 1] Complex Transaction
 * 
 * 비관적 락이 주인공이 되는 시나리오를 시뮬레이션합니다.
 * 복잡한 비즈니스 로직(포인트 차감, 결제 이력 생성 등)이 포함되어 트랜잭션이 길어지는 상황에서
 * 데이터 정합성을 가장 안정적으로 보장하는 비관적 락의 강점을 증명합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticComplexTransactionService implements ComplexTransactionService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void process(Long stockId, int amount) {
        // 1. 비관적 락 획득 (트랜잭션 종료 시까지 로우 잠금)
        Stock stock = stockRepository.findByIdWithPessimisticLock(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        // 2. 복잡한 비즈니스 로직 시뮬레이션 (약 50ms 소요 가정)
        // 실제 상황: 포인트 서비스 호출, 결제 로그 생성, 타 도메인 업데이트 등
        simulateComplexLogic();

        // 3. 재고 차감
        stock.decrease(amount);
        
        // 4. 트랜잭션 종료 및 락 해제
    }

    private void simulateComplexLogic() {
        try {
            // 복잡한 비즈니스 로직 시뮬레이션 (50ms -> 200ms 상향)
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
