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

---

## 2. AOP 기반의 Retry 구현 (Best Practice)

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
