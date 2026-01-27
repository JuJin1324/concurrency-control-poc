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
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.concurrency.poc.fixtures.ConcurrencyTestSupport.DEFAULT_DECREASE_AMOUNT;
import static com.concurrency.poc.fixtures.StockTestFixtures.DEFAULT_QUANTITY;
import static com.concurrency.poc.fixtures.StockTestFixtures.saveDefaultStock;
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
        // 1. DB에 재고 저장
        Stock stock = saveDefaultStock(stockRepository);
        stockId = stock.getId();

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
        ConcurrencyTestConfig config = ConcurrencyTestConfig.withDefaults();

        // when
        ConcurrencyTestResult result = ConcurrencyTestSupport.executeConcurrentRequests(
            () -> stockService.decreaseStock(stockId, DEFAULT_DECREASE_AMOUNT),
            config
        );

        // then
        // 1. Redis 재고 확인
        String redisStock = redisTemplate.opsForValue().get("stock:" + stockId);
        assertThat(redisStock).isNotNull();
        assertThat(Long.parseLong(redisStock)).isZero();

        // 2. 성공/실패 횟수 확인
        assertThat(result.successCount()).isEqualTo(config.threadCount());
        assertThat(result.failCount()).isZero();

        // 결과 출력
        result.printResult("Lua Script");
    }
}
