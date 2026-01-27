package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport.ConcurrencyTestConfig;
import com.concurrency.poc.fixtures.ConcurrencyTestSupport.ConcurrencyTestResult;
import com.concurrency.poc.repository.StockRepository;
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

@SpringBootTest
class RedisLockStockServiceTest {

    @Autowired
    private RedisLockStockService redisLockStockService;

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
    @DisplayName("Redis Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
    void decreaseStock_concurrency_withRedisLock() throws InterruptedException {
        // given
        ConcurrencyTestConfig config = ConcurrencyTestConfig.withDefaults();

        // when
        ConcurrencyTestResult result = ConcurrencyTestSupport.executeConcurrentRequests(
            () -> redisLockStockService.decreaseStock(stock.getId(), DEFAULT_DECREASE_AMOUNT),
            config
        );

        // then
        Stock updatedStock = stockRepository.findById(stock.getId()).orElseThrow();
        int expectedRemainingStock = DEFAULT_QUANTITY - (result.successCount() * DEFAULT_DECREASE_AMOUNT);

        // 결과 출력
        result.printResult("Redis Lock");
        System.out.printf("최종 재고: %d%n", updatedStock.getQuantity());

        // Redis Lock은 100% 성공을 목표로 함
        assertThat(result.successCount()).isEqualTo(config.threadCount());
        assertThat(updatedStock.getQuantity()).isEqualTo(expectedRemainingStock);
    }
}
