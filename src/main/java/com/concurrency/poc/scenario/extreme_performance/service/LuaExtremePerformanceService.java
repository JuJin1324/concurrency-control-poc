package com.concurrency.poc.scenario.extreme_performance.service;

import com.concurrency.poc.scenario.standard.service.LuaScriptStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LuaExtremePerformanceService implements ExtremePerformanceService {

    private final LuaScriptStockService luaScriptStockService;

    @Override
    public void process(Long stockId, int amount) {
        luaScriptStockService.decreaseStock(stockId, amount);
    }
}
