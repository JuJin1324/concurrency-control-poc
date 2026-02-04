# [Deep Dive] Pessimistic Lock Implementation Guide

**Parent Document:** [Pessimistic Lock 운영 가이드](../pessimistic-lock-ops.md)

---

## 1. ORM(JPA) 환경에서의 타협과 전략

JPA는 DB를 몰라도 되게 하려는(추상화) 도구지만, 비관적 락은 DB의 심장부(엔진 특성)를 찔러야 하는 기능입니다. 이 둘의 **부조화(Impedance Mismatch)**를 인정하고 타협해야 합니다.

### 1.1 Native Query 권장
*   **이유:** JPA 표준(JPQL)에는 인덱스를 강제하거나 `NOWAIT` 등을 세밀하게 제어하는 문법이 부족합니다.
*   **Action:** 락킹 쿼리만큼은 `@Query(nativeQuery=true)`로 작성하여 인덱스 힌트(`FORCE INDEX`)나 `NOWAIT`, `SKIP LOCKED` 처럼 DB 고유 기능을 직접 제어하십시오.

### 1.2 명시적 Flush
*   **이유:** JPA의 쓰기 지연(Write-Behind) 저장소 때문에 `UPDATE` 쿼리가 언제 나갈지 보장할 수 없습니다. 이는 데드락의 원인이 됩니다.
*   **Action:** 락 획득 직전/직후에 `entityManager.flush()`를 호출하여 쿼리 순서를 강제하십시오.

### 1.3 트랜잭션 분리 (Facade 패턴)
*   **원칙:** 외부 API 호출(결제 등)은 절대 `@Transactional` 안에 두지 마십시오. 락을 잡고 외부 통신을 하는 순간 서버는 멈춥니다.
*   **구현:** `락 획득/반환` -> `커밋` -> `API 호출` 순서로 쪼개야 합니다.

---

## 2. DDD 환경에서의 Lock 위치 선정

"도메인 모델은 인프라(Lock)를 몰라야 한다"는 원칙과 "락을 안 걸면 데이터가 꼬인다"는 현실 사이의 해법입니다.

### 2.1 권장 패턴: Persistence Adapter 은닉
*   **도메인(Domain):** "저장해줘(`save`)"라고만 명령합니다. (POJO 유지)
*   **어댑터(Adapter):** 실제 DB와 대화하는 어댑터 안에서 `Native Query`, `Flush`, `Lock` 등 지저분한 기술 코드를 모두 처리합니다.
*   **효과:** 도메인 로직의 순수성을 지키면서도, `NOWAIT`이나 `Index Hint` 같은 DB 종속적인 튜닝을 자유롭게 할 수 있습니다.

```java
// Application Service (UseCase)
public void order(OrderCommand cmd) {
    // 서비스는 락을 모름. 그냥 "저장해줘"라고만 함.
    orderPort.saveWithConcurrencyControl(cmd);
}

// Infrastructure (Adapter)
@Component
public class OrderPersistenceAdapter implements OrderPort {
    @Override
    @Transactional
    public void saveWithConcurrencyControl(Order order) {
        // 여기서 지지고 볶고(Native Query, Lock, Flush) 다 함
        repository.lock(order.getId());
        repository.save(mapper.toEntity(order));
    }
}
```

### 2.2 주의: Multi-Aggregate Transaction
*   **상황:** `주문`도 저장하고 `재고`도 깎아야 한다면? (두 개의 애그리거트)
*   **해결:**
    1.  **Service에 건다:** 어쩔 수 없습니다. 정합성을 위해 범위를 넓혀야 합니다.
    2.  **Eventual Consistency:** `주문`만 Adapter 트랜잭션으로 저장하고, `재고`는 이벤트를 발행해서 비동기로 깎습니다. (이게 진정한 DDD 스타일)
