package com.concurrency.poc.scenario.complex_transaction.controller;

import com.concurrency.poc.core.dto.StockDecreaseRequest;
import com.concurrency.poc.core.dto.StockDecreaseResponse;
import com.concurrency.poc.scenario.complex_transaction.service.ComplexTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bestfit/complex-transaction")
@RequiredArgsConstructor
public class ComplexTransactionController {

    private final Map<String, ComplexTransactionService> serviceMap;

    @PostMapping("/decrease")
    public ResponseEntity<StockDecreaseResponse> process(
            @RequestParam(defaultValue = "pessimistic") String method,
            @Valid @RequestBody StockDecreaseRequest request
    ) {
        String beanName = switch (method.toLowerCase()) {
            case "optimistic-no-retry" -> "optimisticNoRetryComplexTransactionService";
            case "optimistic-retry" -> "optimisticRetryComplexTransactionService";
            case "pessimistic" -> "pessimisticComplexTransactionService";
            default -> throw new IllegalArgumentException("지원하지 않는 방식입니다: " + method);
        };

        ComplexTransactionService service = serviceMap.get(beanName);
        if (service == null) {
            throw new IllegalStateException("해당 서비스 빈을 찾을 수 없습니다: " + beanName);
        }

        service.process(1L, request.stockId(), request.amount());
        return ResponseEntity.ok(StockDecreaseResponse.success(0));
    }
}