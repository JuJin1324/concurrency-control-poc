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
 * Pessimistic Lock 동시성 테스트
 *
 * 목적: SELECT ... FOR UPDATE를 사용한 Pessimistic Lock이
 *      동시성 환경에서 재고 정합성을 보장하는지 검증한다.
 *
 * 시나리오: N개의 재고에 N개의 동시 요청이 각각 1개씩 차감하면,
 *          최종 재고는 정확히 0이 되어야 한다.
 */
@SpringBootTest
class PessimisticLockStockServiceTest {

    @Autowired
    private PessimisticLockStockService stockService;

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
    @DisplayName("Pessimistic Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
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
                .as("최종 재고는 (초기 재고 - 총 차감량)이어야 한다")
                .isEqualTo(expectedRemainingStock);

        assertThat(result.successCount())
                .as("모든 요청이 성공해야 한다")
                .isEqualTo(config.threadCount());

        assertThat(result.failCount())
                .as("실패한 요청이 없어야 한다")
                .isZero();
    }
}
