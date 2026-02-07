package com.concurrency.poc.core.repository;

import com.concurrency.poc.core.domain.Point;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PointRepository extends JpaRepository<Point, Long> {
    
    Optional<Point> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Point p where p.userId = :userId")
    Optional<Point> findByUserIdWithPessimisticLock(Long userId);
}
