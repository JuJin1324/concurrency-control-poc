# [Deep Dive] Optimistic Lock Implementation Guide

**Parent Document:** [Optimistic Lock 운영 가이드](../optimistic-lock-ops.md)

---

## 1. JPA Standard Implementation

### 1.1 기본 설정 (@Version)
가장 표준적인 방법입니다. 엔티티에 버전 필드만 추가하면 JPA가 알아서 CAS 쿼리를 생성합니다.

```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long id;

    @Version // 핵심 어노테이션
    private Long version;

    private Long price;
}
```

### 1.2 예외 처리
충돌 발생 시 Spring Data JPA는 `ObjectOptimisticLockingFailureException`을 던집니다. 이를 잡아서 처리해야 합니다.

### 1.3 주의사항: Bulk Update와 @Version의 배신
Spring Data JPA의 모든 버전에서 **JPQL/SQL Bulk Update(`@Modifying`)**는 영속성 컨텍스트를 우회하여 직접 DB에 쿼리를 날립니다. 따라서 JPA의 Dirty Checking 기능인 `@Version` 자동 증가가 **구조적으로 동작하지 않습니다.**

*   **위험:** 수동으로 버전을 올리지 않으면, 버전이 그대로 유지되어 다른 트랜잭션이 변경 사실을 모르고 데이터를 덮어쓰는 **갱신 손실(Lost Update)**이 발생합니다.
*   **필수 조치:** 쿼리 내에서 반드시 **수동으로 버전을 증가**시켜야 합니다. (선택이 아닌 필수)
    ```java
    @Modifying
    @Query("UPDATE Product p SET p.price = :price, p.version = p.version + 1 WHERE p.id = :id")
    void updatePrice(@Param("id") Long id, @Param("price") Long price);
    ```

---

## 2. AOP 기반의 Retry 구현 (Auto-Retry)

비즈니스 로직에 `try-catch-retry`가 섞이면 코드가 지저분해집니다. AOP로 깔끔하게 분리하십시오.

### 2.1 @Retryable 어노테이션 정의
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptimisticRetry {
    int maxAttempts() default 3;
    long backoff() default 100; // ms
}
```

### 2.2 Aspect 구현 (with Jitter)
```java
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1) // 트랜잭션보다 바깥에서 돌아야 함
public class OptimisticLockRetryAspect {

    @Around("@annotation(retry)")
    public Object doRetry(ProceedingJoinPoint pjp, OptimisticRetry retry) throws Throwable {
        int attempts = 0;
        Exception lastException;

        do {
            try {
                return pjp.proceed(); // 비즈니스 로직 실행
            } catch (ObjectOptimisticLockingFailureException e) {
                lastException = e;
                attempts++;
                if (attempts >= retry.maxAttempts()) break;

                // Jitter: 50~100ms 사이 랜덤 대기 (Thundering Herd 방지)
                long waitTime = (long) (retry.backoff() + Math.random() * 50);
                Thread.sleep(waitTime);
            }
        } while (true);

        throw lastException; // 최대 횟수 초과 시 예외 전파
    }
}
```

### 2.3 사용 예시
```java
@Service
public class InventoryService {
    
    @OptimisticRetry(maxAttempts = 5) // 이제 알아서 재시도 함
    @Transactional
    public void decreaseStock(Long id, int count) {
        Product p = repository.findById(id).orElseThrow();
        p.decrease(count);
    }
}
```

---

## 3. 수동 재시도 패턴 (Manual Retry) - 중요!

**주의:** 모든 로직에 자동 재시도를 적용하는 것은 위험합니다. 쇼핑몰 주문이나 결제처럼 **"사용자의 의도 확인"**이 중요한 로직에서는 자동 재시도 중에 가격이나 조건이 변할 수 있기 때문입니다.

이럴 땐 AOP 대신 **Controller 레벨에서 예외를 잡고, 클라이언트에게 판단을 위임**해야 합니다.

```java
@PostMapping("/products/{id}")
public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody ProductDto dto) {
    try {
        service.update(id, dto);
        return ResponseEntity.ok().build();
    } catch (ObjectOptimisticLockingFailureException e) {
        // 서버가 재시도하지 않고, 클라이언트에게 명확한 상태 코드(409)를 반환
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("Conflict", "누군가 먼저 수정했습니다. 최신 데이터를 확인 후 다시 시도하세요."));
    }
}
```

---

## 4. Advanced Implementation Tips (실전 꿀팁)

### 4.1 Retry 소진 시 후속 처리 (DLQ)
API 요청이라면 에러 응답을 주면 되지만, **비동기 컨슈머(Kafka, RabbitMQ)**에서 재시도가 모두 실패하면 어떻게 해야 할까요?
*   **Action:** 무한 루프를 방지하기 위해 예외를 삼키고, 해당 메시지를 **Dead Letter Queue(DLQ)**나 **FailedJob 테이블**로 옮겨서 운영자가 나중에 수동으로 처리할 수 있게 해야 합니다.

### 4.2 Spring AOP의 함정: Self-Invocation
`@OptimisticRetry`는 Spring AOP(Proxy) 기반으로 동작합니다.
*   **문제:** 같은 클래스 내부의 메서드(`this.decreaseStock()`)를 호출하면 프록시를 거치지 않아 **재시도 로직이 동작하지 않습니다.**
*   **해결:** 반드시 **외부 클래스(Controller, Facade)**에서 호출하거나, 구조적으로 분리해야 합니다.

### 4.3 부모 버전 강제 증가 (Force Increment)
DDD의 애그리거트(Aggregate) 패턴에서, 자식 엔티티(`OrderItem`)만 수정되고 부모(`Order`)는 그대로일 때 부모의 버전이 올라가지 않는 문제가 있습니다.
*   **해결:** `LockModeType.OPTIMISTIC_FORCE_INCREMENT`를 사용하여 **"자식이 변하면 부모의 버전도 강제로 올려라"**고 명시해야, 애그리거트 전체의 정합성이 유지됩니다.
    ```java
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithForceIncrement(@Param("id") Long id);
    ```