package com.concurrency.poc.scenario.low_contention.controller;

import com.concurrency.poc.core.dto.StockDecreaseRequest;
import com.concurrency.poc.core.dto.StockDecreaseResponse;
import com.concurrency.poc.scenario.low_contention.service.LowContentionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bestfit/low-contention")
@RequiredArgsConstructor
public class LowContentionController {

    private final Map<String, LowContentionService> serviceMap;

    @PostMapping("/decrease")
    public ResponseEntity<StockDecreaseResponse> process(
            @RequestParam(defaultValue = "optimistic") String method,
            @Valid @RequestBody StockDecreaseRequest request
    ) {
        String beanName = method.equalsIgnoreCase("pessimistic")
                ? "pessimisticLowContentionService"
                : "optimisticLowContentionService";

        LowContentionService service = serviceMap.get(beanName);
        if (service == null) {
            throw new IllegalArgumentException("지원하지 않는 방식입니다: " + method);
        }

        service.process(request.stockId(), request.amount());
        return ResponseEntity.ok(StockDecreaseResponse.success(0));
    }
}
