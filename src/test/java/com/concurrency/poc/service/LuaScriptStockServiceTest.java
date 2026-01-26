package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LuaScriptStockServiceTest {

    @Autowired
    private LuaScriptStockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long stockId;

    @BeforeEach
    void setUp() {
        // 1. DB에 재고 100개 저장
        Stock stock = new Stock("PROD-001", 100);
        Stock savedStock = stockRepository.save(stock);
        stockId = savedStock.getId();

        // 2. DB 데이터를 Redis로 동기화 (테스트 대상이 Redis이므로 필수)
        stockService.syncStockToRedis(stockId);
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
        // Redis 데이터도 정리
        redisTemplate.delete("stock:" + stockId);
    }

    @Test
    @DisplayName("Lua Script: 동시에 100명이 주문하면 재고가 0이 되어야 한다")
    void decrease_concurrency_100() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decreaseStock(stockId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("Decrease failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        // then
        // 1. Redis 재고 확인
        String redisStock = redisTemplate.opsForValue().get("stock:" + stockId);
        assertThat(redisStock).isNotNull();
        assertThat(Long.parseLong(redisStock)).isEqualTo(0L);

        // 2. 성공/실패 횟수 확인
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        System.out.println("=== Lua Script 테스트 결과 ===");
        System.out.println("Time taken: " + (endTime - startTime) + "ms");
        System.out.println("Success: " + successCount.get() + ", Fail: " + failCount.get());
    }
}
