package com.concurrency.poc.core.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long stockId;

    private int amount;

    private LocalDateTime orderedAt;

    public OrderHistory(Long userId, Long stockId, int amount) {
        this.userId = userId;
        this.stockId = stockId;
        this.amount = amount;
        this.orderedAt = LocalDateTime.now();
    }
}
