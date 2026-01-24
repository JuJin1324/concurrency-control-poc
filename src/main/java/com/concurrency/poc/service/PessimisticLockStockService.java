package com.concurrency.poc.service;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.domain.StockNotFoundException;
import com.concurrency.poc.dto.StockResponse;
import com.concurrency.poc.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLockStockService implements StockService {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void decreaseStock(Long stockId, int amount) {
        Stock stock = stockRepository.findByIdWithPessimisticLock(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        stock.decrease(amount);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse getStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new StockNotFoundException(stockId));

        return new StockResponse(stock.getId(), stock.getProductId(), stock.getQuantity());
    }
}
