package com.concurrency.poc.controller;

import com.concurrency.poc.domain.Stock;
import com.concurrency.poc.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockRepository stockRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(new Stock("PRODUCT-001", 100));
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/stock/{id} - 재고 조회 성공")
    void getStock_success() throws Exception {
        mockMvc.perform(get("/api/stock/{id}", stock.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stock.getId()))
                .andExpect(jsonPath("$.productId").value("PRODUCT-001"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    @DisplayName("GET /api/stock/{id} - 존재하지 않는 Stock")
    void getStock_notFound() throws Exception {
        mockMvc.perform(get("/api/stock/{id}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/stock/decrease - Pessimistic Lock으로 재고 차감 성공")
    void decreaseStock_pessimistic_success() throws Exception {
        Map<String, Object> request = Map.of(
                "stockId", stock.getId(),
                "amount", 10
        );

        mockMvc.perform(post("/api/stock/decrease")
                        .param("method", "pessimistic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.remainingQuantity").value(90));
    }

    @Test
    @DisplayName("POST /api/stock/decrease - Optimistic Lock으로 재고 차감 성공")
    void decreaseStock_optimistic_success() throws Exception {
        Map<String, Object> request = Map.of(
                "stockId", stock.getId(),
                "amount", 10
        );

        mockMvc.perform(post("/api/stock/decrease")
                        .param("method", "optimistic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.remainingQuantity").value(90));
    }

    @Test
    @DisplayName("POST /api/stock/decrease - 재고 부족")
    void decreaseStock_insufficientStock() throws Exception {
        Map<String, Object> request = Map.of(
                "stockId", stock.getId(),
                "amount", 999
        );

        mockMvc.perform(post("/api/stock/decrease")
                        .param("method", "pessimistic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/stock/decrease - 유효성 검증 실패 (amount < 1)")
    void decreaseStock_validationFail() throws Exception {
        Map<String, Object> request = Map.of(
                "stockId", stock.getId(),
                "amount", 0
        );

        mockMvc.perform(post("/api/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/stock/decrease - 지원하지 않는 Lock 방식")
    void decreaseStock_invalidMethod() throws Exception {
        Map<String, Object> request = Map.of(
                "stockId", stock.getId(),
                "amount", 10
        );

        mockMvc.perform(post("/api/stock/decrease")
                        .param("method", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
