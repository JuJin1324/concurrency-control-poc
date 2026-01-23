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

    /**
     * 초기 재고 수량 = 동시 요청 수
     * 모든 요청이 성공하면 재고가 정확히 0이 되어야 함
     */
    private static final int INITIAL_STOCK_QUANTITY = 100;
    private static final int CONCURRENT_REQUEST_COUNT = INITIAL_STOCK_QUANTITY;

    /**
     * 동시성 수준을 결정하는 스레드 풀 크기
     * - 너무 작으면: 동시성 테스트 효과 감소
     * - 너무 크면: 컨텍스트 스위칭 오버헤드 증가
     * - 32는 일반적인 DB 커넥션 풀 크기와 유사한 수준
     */
    private static final int THREAD_POOL_SIZE = 32;

    private static final int DECREASE_AMOUNT_PER_REQUEST = 1;

    @Autowired
    private PessimisticLockStockService stockService;

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
    @DisplayName("Pessimistic Lock: 동시 요청 시 재고 정합성이 보장되어야 한다")
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
        int expectedRemainingStock = INITIAL_STOCK_QUANTITY - (CONCURRENT_REQUEST_COUNT * DECREASE_AMOUNT_PER_REQUEST);

        assertThat(updatedStock.getQuantity())
                .as("최종 재고는 (초기 재고 - 총 차감량)이어야 한다")
                .isEqualTo(expectedRemainingStock);

        assertThat(successCount.get())
                .as("모든 요청이 성공해야 한다")
                .isEqualTo(CONCURRENT_REQUEST_COUNT);

        assertThat(failCount.get())
                .as("실패한 요청이 없어야 한다")
                .isZero();
    }
}
