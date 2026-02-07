package com.concurrency.poc.service;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.repository.StockRepository;
import com.concurrency.poc.scenario.standard.service.LuaScriptStockService;
import com.concurrency.poc.scenario.standard.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("test") // test 프로파일 사용 (필요 시)
class StockServiceBenchmarkTest {

    @Autowired
    private Map<String, StockService> stockServiceMap;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private LuaScriptStockService luaScriptStockService; // Redis 초기화용

    private static final int STOCK_COUNT = 100;
    private static final int THREAD_COUNT = 100;

    @Test
    @DisplayName("4가지 동시성 제어 방식 성능 비교")
    void benchmarkAll() throws InterruptedException {
        System.out.println("\n==================================================");
        System.out.println("🔥 Performance Benchmark Started (100 Requests) 🔥");
        System.out.println("==================================================");

        // 1. Pessimistic Lock
        runBenchmark("pessimisticLockStockService", "Pessimistic Lock");

        // 2. Optimistic Lock (재시도 로직 없으면 실패 많음 주의)
        runBenchmark("optimisticLockStockService", "Optimistic Lock");

        // 3. Redis Distributed Lock
        runBenchmark("redisLockStockService", "Redis Distributed Lock");

        // 4. Lua Script
        runBenchmark("luaScriptStockService", "Redis Lua Script");

        System.out.println("==================================================");
        System.out.println("✅ Benchmark Finished");
        System.out.println("==================================================");
    }

    private void runBenchmark(String beanName, String displayName) throws InterruptedException {
        // 1. 데이터 초기화
        Stock stock = stockRepository.save(new Stock("BENCH-PROD", STOCK_COUNT));
        Long stockId = stock.getId();

        // Lua Script용 Redis 초기화
        if (beanName.contains("lua") || beanName.contains("redis")) {
             luaScriptStockService.syncStockToRedis(stockId);
        }

        StockService service = stockServiceMap.get(beanName);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    service.decreaseStock(stockId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.printf("| %-25s | Time: %5d ms | Success: %3d | Fail: %3d |%n",
                displayName, duration, successCount.get(), failCount.get());

        // 정리
        stockRepository.deleteAll();
    }
}
