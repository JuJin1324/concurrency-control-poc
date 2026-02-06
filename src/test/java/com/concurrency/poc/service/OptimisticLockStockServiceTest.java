package com.concurrency.poc.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport.ConcurrencyTestConfig;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport.ConcurrencyTestResult;
import com.concurrency.poc.core.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.concurrency.poc.fixtures.ConcurrencyTestSupport.DEFAULT_DECREASE_AMOUNT;
import static com.concurrency.poc.fixtures.StockTestFixtures.DEFAULT_QUANTITY;
import static com.concurrency.poc.fixtures.StockTestFixtures.saveDefaultStock;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic Lock 동시성 테스트
 *
 * 목적: @Version을 사용한 Optimistic Lock이 동시성 환경에서
 *      재고 정합성을 보장하는지 검증한다.
 *
 * 특징: Pessimistic Lock과 달리 충돌 시 재시도가 필요하며,
 *      극단적인 동시성 상황에서는 일부 요청이 실패할 수 있다.
 *
 * 시나리오: N개의 재고에 N개의 동시 요청이 각각 1개씩 차감하면,
 *          성공한 요청 수만큼 재고가 감소해야 한다.
 */
@SpringBootTest
class OptimisticLockStockServiceTest {

    @Autowired
    private OptimisticLockStockService stockService;

    @Autowired
    private StockRepository stockRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = saveDefaultStock(stockRepository);
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("Optimistic Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
    void decreaseStock_withConcurrentRequests_shouldMaintainDataIntegrity() throws InterruptedException {
        // given
        ConcurrencyTestConfig config = ConcurrencyTestConfig.withDefaults();

        // when
        ConcurrencyTestResult result = ConcurrencyTestSupport.executeConcurrentRequests(
            () -> stockService.decreaseStock(stock.getId(), DEFAULT_DECREASE_AMOUNT),
            config
        );

        // then
        Stock updatedStock = stockRepository.findById(stock.getId()).orElseThrow();
        int expectedRemainingStock = DEFAULT_QUANTITY - (result.successCount() * DEFAULT_DECREASE_AMOUNT);

        assertThat(updatedStock.getQuantity())
                .as("최종 재고는 (초기 재고 - 성공한 차감량)이어야 한다")
                .isEqualTo(expectedRemainingStock);

        assertThat(updatedStock.getQuantity())
                .as("재고는 음수가 되면 안 된다")
                .isGreaterThanOrEqualTo(0);

        // 결과 출력
        result.printResult("Optimistic Lock");
        System.out.printf("최종 재고: %d%n", updatedStock.getQuantity());
    }
}
