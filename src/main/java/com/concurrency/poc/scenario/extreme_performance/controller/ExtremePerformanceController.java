package com.concurrency.poc.scenario.extreme_performance.controller;

import com.concurrency.poc.core.dto.StockDecreaseRequest;
import com.concurrency.poc.core.dto.StockDecreaseResponse;
import com.concurrency.poc.scenario.extreme_performance.service.ExtremePerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/extreme-performance")
@RequiredArgsConstructor
public class ExtremePerformanceController {

    private final Map<String, ExtremePerformanceService> serviceMap;

    @PostMapping("/decrease")
    public ResponseEntity<StockDecreaseResponse> process(
            @RequestParam(defaultValue = "lua") String method,
            @Valid @RequestBody StockDecreaseRequest request
    ) {
        String beanName = switch (method.toLowerCase()) {
            case "pessimistic" -> "pessimisticExtremePerformanceService";
            case "optimistic" -> "optimisticExtremePerformanceService";
            case "redis" -> "redisExtremePerformanceService";
            case "lua" -> "luaExtremePerformanceService";
            default -> throw new IllegalArgumentException("지원하지 않는 방식: " + method);
        };

        ExtremePerformanceService service = serviceMap.get(beanName);
        if (service == null) {
            throw new IllegalStateException("해당 서비스 빈을 찾을 수 없습니다: " + beanName);
        }

        service.process(request.stockId(), request.amount());
        return ResponseEntity.ok(StockDecreaseResponse.success(0));
    }
}
