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

    /**
     * 초기 재고 수량 = 동시 요청 수
     * Retry 로직이 있으므로 대부분 성공해야 함
     */
    private static final int INITIAL_STOCK_QUANTITY = 100;
    private static final int CONCURRENT_REQUEST_COUNT = INITIAL_STOCK_QUANTITY;

    /**
     * 동시성 수준을 결정하는 스레드 풀 크기
     * Optimistic Lock은 충돌이 잦으므로 적절한 동시성 필요
     */
    private static final int THREAD_POOL_SIZE = 32;

    private static final int DECREASE_AMOUNT_PER_REQUEST = 1;

    @Autowired
    private OptimisticLockStockService stockService;

    @Autowired
    private StockRepository stockRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(new Stock("PRODUCT-001", INITIAL_STOCK_QUANTITY));
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("Optimistic Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
    void decreaseStock_withConcurrentRequests_shouldMaintainDataIntegrity() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < CONCURRENT_REQUEST_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decreaseStock(stock.getId(), DECREASE_AMOUNT_PER_REQUEST);
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
        int expectedRemainingStock = INITIAL_STOCK_QUANTITY - (successCount.get() * DECREASE_AMOUNT_PER_REQUEST);

        assertThat(updatedStock.getQuantity())
                .as("최종 재고는 (초기 재고 - 성공한 차감량)이어야 한다")
                .isEqualTo(expectedRemainingStock);

        assertThat(updatedStock.getQuantity())
                .as("재고는 음수가 되면 안 된다")
                .isGreaterThanOrEqualTo(0);

        System.out.printf("%n=== Optimistic Lock 테스트 결과 ===%n");
        System.out.printf("성공: %d, 실패: %d%n", successCount.get(), failCount.get());
        System.out.printf("Success Rate: %.1f%%%n", (successCount.get() * 100.0 / CONCURRENT_REQUEST_COUNT));
        System.out.printf("최종 재고: %d%n", updatedStock.getQuantity());
    }
}
