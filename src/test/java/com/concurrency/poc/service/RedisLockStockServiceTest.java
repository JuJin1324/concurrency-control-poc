package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisLockStockServiceTest {

    /**
     * 테스트 상수
     * - INITIAL_QUANTITY: 초기 재고 수량
     * - THREAD_COUNT: 동시 요청 수 (스레드 수)
     * - DECREASE_AMOUNT: 각 요청당 차감 수량
     */
    private static final int INITIAL_QUANTITY = 100;
    private static final int THREAD_COUNT = 100;
    private static final int DECREASE_AMOUNT = 1;

    @Autowired
    private RedisLockStockService redisLockStockService;

    @Autowired
    private StockRepository stockRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(new Stock("PRODUCT-REDIS-LOCK", INITIAL_QUANTITY));
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("Redis Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
    void decreaseStock_concurrency_withRedisLock() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 100개 스레드가 동시에 재고 차감 요청
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    redisLockStockService.decreaseStock(stock.getId(), DECREASE_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Stock updatedStock = stockRepository.findById(stock.getId()).orElseThrow();

        System.out.println();
        System.out.println("=== Redis Lock 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("Success Rate: " + (successCount.get() * 100.0 / THREAD_COUNT) + "%");
        System.out.println("최종 재고: " + updatedStock.getQuantity());

        // Redis Lock은 100% 성공을 목표로 함
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(updatedStock.getQuantity()).isEqualTo(INITIAL_QUANTITY - (successCount.get() * DECREASE_AMOUNT));
    }
}
