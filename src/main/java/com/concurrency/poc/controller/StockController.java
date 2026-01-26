package com.concurrency.poc.controller;

import com.concurrency.poc.dto.StockDecreaseRequest;
import com.concurrency.poc.dto.StockDecreaseResponse;
import com.concurrency.poc.dto.StockResponse;
import com.concurrency.poc.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final Map<String, StockService> stockServiceMap;

    /**
     * 재고 차감 API
     *
     * @param method 사용할 Lock 방식 (pessimistic, optimistic)
     * @param request 차감 요청 (stockId, amount)
     * @return 차감 결과
     */
    @PostMapping("/decrease")
    public ResponseEntity<StockDecreaseResponse> decreaseStock(
            @RequestParam(defaultValue = "pessimistic") String method,
            @Valid @RequestBody StockDecreaseRequest request
    ) {
        StockService stockService = getStockService(method);
        stockService.decreaseStock(request.stockId(), request.amount());

        StockResponse stock = stockService.getStock(request.stockId());
        return ResponseEntity.ok(StockDecreaseResponse.success(stock.quantity()));
    }

    /**
     * 재고 조회 API
     *
     * @param id Stock ID
     * @return 재고 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<StockResponse> getStock(@PathVariable Long id) {
        // 기본적으로 pessimistic 서비스 사용 (조회는 Lock 방식과 무관)
        StockService stockService = getStockService("pessimistic");
        return ResponseEntity.ok(stockService.getStock(id));
    }

    private StockService getStockService(String method) {
        String beanName = switch (method.toLowerCase()) {
            case "pessimistic" -> "pessimisticLockStockService";
            case "optimistic" -> "optimisticLockStockService";
            case "redis-lock" -> "redisLockStockService";
            case "lua-script" -> "luaScriptStockService";
            default -> throw new IllegalArgumentException("지원하지 않는 Lock 방식입니다: " + method);
        };

        StockService service = stockServiceMap.get(beanName);
        if (service == null) {
            throw new IllegalArgumentException("해당 서비스를 찾을 수 없습니다: " + beanName);
        }

        return service;
    }
}
