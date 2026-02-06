package com.concurrency.poc.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Stock(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public void decrease(int amount) {
        if (!isAvailable(amount)) {
            throw new InsufficientStockException(this.quantity, amount);
        }
        this.quantity -= amount;
    }

    public boolean isAvailable(int amount) {
        return this.quantity >= amount;
    }
}
