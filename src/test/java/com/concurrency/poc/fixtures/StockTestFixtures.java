package com.concurrency.poc.fixtures;

import com.concurrency.poc.core.domain.Stock;
import com.concurrency.poc.core.repository.StockRepository;

/**
 * Stock 엔티티 생성을 위한 Test Fixture
 *
 * 목적: 테스트에서 반복적으로 사용되는 Stock 엔티티 생성 로직을 중앙화하여
 *      코드 중복을 제거하고 일관성을 보장한다.
 *
 * 사용 예시:
 * <pre>
 * // 기본 Stock 생성 및 저장
 * Stock stock = StockTestFixtures.saveDefaultStock(stockRepository);
 *
 * // 커스텀 Stock 생성 및 저장
 * Stock stock = StockTestFixtures.saveStock(stockRepository, "PRODUCT-001", 50);
 * </pre>
 */
public class StockTestFixtures {

    // 기본 상수
    public static final String DEFAULT_PRODUCT_ID = "PRODUCT-TEST";
    public static final int DEFAULT_QUANTITY = 100;

    /**
     * Stock 엔티티 생성 (저장하지 않음)
     *
     * @param productId 상품 ID
     * @param quantity 재고 수량
     * @return Stock 엔티티
     */
    public static Stock createStock(String productId, int quantity) {
        return new Stock(productId, quantity);
    }

    /**
     * 기본 Stock 엔티티 생성 (저장하지 않음)
     *
     * @return Stock 엔티티 (PRODUCT-TEST, 100)
     */
    public static Stock createDefaultStock() {
        return new Stock(DEFAULT_PRODUCT_ID, DEFAULT_QUANTITY);
    }

    /**
     * Stock 엔티티 생성 및 저장
     *
     * @param repository StockRepository
     * @param productId 상품 ID
     * @param quantity 재고 수량
     * @return 저장된 Stock 엔티티
     */
    public static Stock saveStock(StockRepository repository, String productId, int quantity) {
        return repository.save(new Stock(productId, quantity));
    }

    /**
     * 기본 Stock 엔티티 생성 및 저장
     *
     * @param repository StockRepository
     * @return 저장된 Stock 엔티티 (PRODUCT-TEST, 100)
     */
    public static Stock saveDefaultStock(StockRepository repository) {
        return repository.save(new Stock(DEFAULT_PRODUCT_ID, DEFAULT_QUANTITY));
    }
}
