package com.concurrency.poc.scenario.standard.service;

import com.concurrency.poc.core.domain.InsufficientStockException;
import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.domain.StockNotFoundException;
import com.concurrency.poc.core.dto.StockResponse;
import com.concurrency.poc.core.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

@Slf4j
@Service("luaScriptStockService")
@RequiredArgsConstructor
public class LuaScriptStockService implements StockService {

    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;
    private DefaultRedisScript<Long> stockDecreaseScript;

    private static final String STOCK_KEY_PREFIX = "stock:";

    @PostConstruct
    public void init() {
        stockDecreaseScript = new DefaultRedisScript<>();
        stockDecreaseScript.setLocation(new ClassPathResource("scripts/stock-decrease.lua"));
        stockDecreaseScript.setResultType(Long.class);
    }

    /**
     * DB의 재고 데이터를 Redis로 동기화합니다. (테스트/초기화 용도)
     * 실제 운영 환경에서는 별도의 배치나 CDC 등을 사용할 수 있습니다.
     */
    public void syncStockToRedis(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));
        
        String key = getStockKey(stockId);
        redisTemplate.opsForValue().set(key, String.valueOf(stock.getQuantity()));
        log.info("Synced stock to Redis. Key: {}, Quantity: {}", key, stock.getQuantity());
    }

    @Override
    public void decreaseStock(Long stockId, int amount) {
        String key = getStockKey(stockId);

        // execute script
        Long result = redisTemplate.execute(
                stockDecreaseScript,
                Collections.singletonList(key), // KEYS[1]
                String.valueOf(amount)          // ARGV[1]
        );

        if (result == null) {
            throw new RuntimeException("Redis script execution returned null");
        }

        if (result == -1) {
            // Redis에 데이터가 없는 경우 DB에서 동기화 후 재시도할 수도 있으나,
            // 현재 정책은 Redis를 믿거나 예외를 던짐. 여기서는 예외 처리.
            // 필요하다면 syncStockToRedis(stockId) 호출 후 재시도 로직 추가 가능.
            throw new StockNotFoundException(stockId); 
            // 주의: -1은 스크립트에서 "Not Found"로 정의함
        }

        if (result == -2) {
            throw new InsufficientStockException(0, amount);
        }

        log.debug("Stock decreased using Lua Script. Remaining: {}", result);
        
        // 중요: Redis만 업데이트하고 DB는 업데이트하지 않음 (Sprint 2 정책)
        // 실제 운영에서는 비동기 큐 등을 통해 DB에 반영해야 함.
    }

    @Override
    public StockResponse getStock(Long stockId) {
        String key = getStockKey(stockId);
        String quantityStr = redisTemplate.opsForValue().get(key);

        if (quantityStr != null) {
            return new StockResponse(stockId, "REDIS_CACHED", Integer.parseInt(quantityStr));
        }

        // Redis에 없으면 DB 조회 (Read-Through)
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));
        
        // 조회한 김에 Redis에 적재
        redisTemplate.opsForValue().set(key, String.valueOf(stock.getQuantity()));
        
        return new StockResponse(stock.getId(), stock.getProductId(), stock.getQuantity());
    }

    private String getStockKey(Long stockId) {
        return STOCK_KEY_PREFIX + stockId;
    }
}
