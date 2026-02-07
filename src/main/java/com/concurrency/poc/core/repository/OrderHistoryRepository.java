package com.concurrency.poc.core.repository;

import com.concurrency.poc.core.domain.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
}
