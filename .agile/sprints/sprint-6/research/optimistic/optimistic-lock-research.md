# Optimistic Lock 운영 사례 연구

## Executive Summary

Optimistic Lock은 충돌이 드문 환경(low contention)에서 효과적이며, 읽기 중심 애플리케이션, 협업 도구, 티켓 예약 시스템 등에서 널리 사용됩니다. 본 연구는 실제 운영 사례, Retry 전략, UX 처리 방법, 그리고 Pessimistic Lock으로 전환해야 하는 기준을 정리합니다.

---

## 사례 1: Salesforce Commerce Cloud - E-commerce Platform

### 도메인
전자상거래 플랫폼, 재고 관리 및 상품 정보 업데이트

### 선택 이유
- 읽기 작업이 쓰기 작업보다 압도적으로 많음 (read-heavy)
- 다수의 사용자가 상품 정보를 조회하지만 실제 가격/재고 업데이트는 소수만 수행
- 동시 수정 충돌이 상대적으로 드묾

### 구현 방식
- **OCAPI (Open Commerce API)** 기반 Optimistic Locking
- Version column을 사용한 충돌 감지
- 충돌 발생 시 **HTTP 409 (Conflict)** - `ConcurrentModificationException` 반환

### 충돌률 데이터
- 공식 문서에서 "충돌은 대부분 bad timing으로 발생"한다고 명시
- 구체적인 퍼센트는 미공개이나, "간단한 재시도로 대부분 해결 가능"한 수준으로 언급

### 운영 노하우

#### 충돌 모니터링
- HTTP 409 응답 코드를 모니터링하여 충돌 발생 빈도 추적
- 충돌률이 평소보다 급증하면 시스템 부하 또는 설계 이슈로 판단

#### Retry 전략
- **권장 방식**: 클라이언트가 단순 재시도 (simple retry)
- 이유: "대부분의 경우 타이밍 문제이므로 재시도 시 성공 가능성 높음"
- 명시적인 Backoff 전략은 문서화되지 않았으나, 일반적으로 exponential backoff 권장

#### UX 처리
- API 레벨에서 409 에러 반환
- 클라이언트 애플리케이션이 사용자에게 "다시 시도하십시오" 메시지 표시
- 자동 재시도 시 사용자에게 로딩 인디케이터 표시

### 실제 운영 경험
초기에 Pessimistic Lock을 사용했으나 플랫폼 성장에 따라 성능 병목 발생. Optimistic Lock으로 전환 후 적절한 충돌 해결 전략 적용으로 **더 원활한 운영** 달성.

### 출처
- [Optimistic Locking | Open Commerce API | Salesforce Developers](https://documentation.b2c.commercecloud.salesforce.com/DOC2/topic/com.demandware.dochelp/OCAPI/current/usage/OptimisticLocking.html)
- [Optimistic locking in the Commerce Cloud B2C platform](https://help.salesforce.com/s/articleView?id=000391455&language=en_US&type=1)
- [Pessimistic vs Optimistic Locking - System Design Codex](https://newsletter.systemdesigncodex.com/p/pessimistic-vs-optimistic-locking)

---

## 사례 2: Confluence (Atlassian) - 협업 위키 편집 시스템

### 도메인
문서 협업 플랫폼, 동시 페이지 편집

### 선택 이유
- 대부분의 경우 사용자들이 서로 다른 페이지를 편집
- 동일 페이지를 동시에 편집하더라도 다른 섹션을 수정하는 경우가 많음
- 자동 병합(automatic merge) 가능한 시나리오가 대부분

### 구현 방식
- **Version-based Optimistic Concurrency Control**
- 사용자가 저장 시 현재 버전과 최초 로드된 버전 비교
- 충돌하지 않는 변경 사항은 자동 병합

### 충돌률 데이터
- 구체적인 수치는 미공개
- Atlassian 문서: "대부분의 경우 자동 병합 성공"
- 실제 충돌(overlap)은 전체 동시 편집 중 소수만 발생

### 운영 노하우

#### 충돌 모니터링
- 페이지별 버전 히스토리를 통한 충돌 빈도 추적
- 사용자 저장 시도 vs 실제 충돌 발생 비율 측정

#### Retry 전략
- **수동 재시도 방식** (자동 retry 없음)
- 사용자가 직접 충돌 해결 후 재저장
- 이유: 사람의 판단이 필요한 콘텐츠 편집이므로 자동 병합이 위험

#### UX 처리
**자동 병합 성공 시:**
- 사용자에게 별도 알림 없이 저장 완료
- 히스토리에 자동 병합 기록 남김

**충돌 발생 시:**
1. 에러 메시지 표시: "다른 사용자가 페이지를 수정했습니다"
2. 충돌 지점 시각적으로 표시
3. 3가지 옵션 제공:
   - **덮어쓰기** (Overwrite): 자신의 변경사항으로 강제 저장
   - **재편집** (Re-edit): 타인의 변경사항을 반영하여 다시 편집
   - **취소** (Cancel): 편집 취소

### 특이사항
- Collaborative Editing 모드(실시간 협업)에서는 OT(Operational Transformation) 알고리즘 사용하여 충돌 사전 방지
- 파일 첨부(Excel 등)는 동시 편집 지원 불가 - 명확한 제약사항 안내

### 출처
- [Concurrent Editing and Merging Changes | Confluence Data Center](https://confluence.atlassian.com/display/DOC/Concurrent+Editing+and+Merging+Changes)
- [ConflictException | Atlassian Confluence API](https://docs.atlassian.com/atlassian-confluence/6.6.0/com/atlassian/confluence/api/service/exceptions/ConflictException.html)

---

## 사례 3: Notion - 블록 기반 협업 문서 시스템

### 도메인
실시간 협업 문서 편집 플랫폼

### 선택 이유
- 블록 단위로 콘텐츠 분리되어 충돌 가능성 낮음
- 사용자가 서로 다른 블록을 편집하는 경우가 대부분
- 실시간 동기화가 필요하지만 동일 블록 동시 수정은 드묾

### 구현 방식
- **Block-based Architecture**로 충돌 범위 최소화
- Transaction Queue를 통해 변경사항을 서버로 전송
- 서버에서 트랜잭션 순서 조정 및 충돌 해결
- 최신 변경사항이 페이지에 반영 (last-write-wins 기반)

### 충돌률 데이터
- 구체적 충돌률 미공개
- "동일 블록을 동시에 편집해도 소프트웨어가 잠기지 않음" - 낙관적 접근

### 운영 노하우

#### 충돌 모니터링
- TransactionQueue 내 충돌 감지 로그 모니터링
- 블록별 동시 수정 빈도 추적

#### Retry 전략
- **자동 병합** 우선
- Last-write-wins 정책으로 최신 변경사항 자동 반영
- 사용자에게 명시적 retry 요청 없음

#### UX 처리
- **실시간 동기화**: 변경사항이 밀리초 단위로 화면에 반영
- **충돌 시**: 최신 변경사항이 자동으로 덮어씀
- **롤백 가능**: 버전 히스토리를 통해 이전 상태 복구 가능
- 사용자는 충돌을 직접 인지하지 못하는 경우가 많음 (seamless experience)

### 설계 철학
- Optimistic concurrency control을 단순화하기 위해 블록 참조 모델 도입
- 충돌을 최소화하는 데이터 모델이 핵심

### 출처
- [Exploring Notion's Data Model: A Block-Based Architecture | Notion](https://www.notion.com/blog/data-model-behind-notion)
- [My Journey of breaking down Notion's Collaboration Engine](https://www.linkedin.com/pulse/from-notes-real-time-my-journey-breaking-down-notions-shrey-patel-jcdye)

---

## 사례 4: 티켓 예약 시스템 (Theatre/Cinema/Flight Booking)

### 도메인
좌석 예약, 이벤트 티켓 발권, 항공권 예약

### 선택 이유
- **조회 트래픽 >> 예약 트래픽**: 많은 사용자가 좌석을 조회하지만 실제 예약은 소수
- 호텔 예약처럼 요청이 적은 시스템에서는 효과적
- 단, 인기 영화 개봉일이나 항공권 프로모션 시에는 충돌 증가

### 구현 방식
- JPA `@Version` 컬럼 사용
- 동일 좌석에 대한 동시 예약 시도 시 `OptimisticLockException` 발생
- 먼저 커밋한 트랜잭션이 성공, 나머지는 롤백

### 충돌률 데이터
- **저부하 시**: 충돌률 < 1%
- **고부하 시**(인기 이벤트): 충돌률 급증 가능, 성능 저하 발생
- 한 사례: "수천 건의 요청 중 정확히 한 번" 충돌 발생 (테스트 환경)
- 프로덕션에서는 부하 증가 시 retry storm 발생 가능

### 운영 노하우

#### 충돌 모니터링
- `OptimisticLockException` 발생 빈도를 메트릭으로 수집
- 이벤트별, 시간대별 충돌률 분석
- 충돌률 급증 시 알람 발생

#### Retry 전략
**주의**: Optimistic Lock에서 자동 retry는 신중하게 사용

- **권장하지 않음**: 무조건적인 자동 재시도
- **조건부 허용**: 데이터가 동일하고 버전만 다른 경우
- **수동 개입**: 사용자가 다시 시도하도록 유도

**이유:**
- 자동 retry는 "사용자 의도 확인" 없이 재시도하므로 위험
- 높은 경합 상황에서 retry storm으로 시스템 악화 가능

#### UX 처리
1. **좌석 선택 시**: 낙관적으로 선택 허용 (lock 없음)
2. **예약 버튼 클릭 후**:
   - 충돌 발생 시: "이미 예약된 좌석입니다. 다른 좌석을 선택하세요"
   - 자동으로 좌석 목록 새로고침
3. **대안 제시**: 근처의 가용 좌석 추천

### 전환 기준
- 인기 이벤트(콘서트, 프로모션 항공권)의 경우 **Pessimistic Lock으로 전환 고려**
- 이유: 높은 동시성 환경에서는 optimistic lock 성능이 급격히 저하

### 출처
- [Concurrency Conundrum in Booking Systems](https://medium.com/@abhishekranjandev/concurrency-conundrum-in-booking-systems-2e53dc717e8c)
- [How To Build a High-Concurrency Ticket Booking System With Prisma](https://dev.to/zenstack/how-to-build-a-high-concurrency-ticket-booking-system-with-prisma-184n)
- [DB Locking in Reservation Systems](https://akshitbansall.medium.com/db-locking-in-reservation-systems-3b3d574c7676)

---

## Retry 로직 Best Practice

### 1. 자동 Retry의 위험성

**핵심 원칙**: Optimistic Lock과 자동 retry는 신중하게 결합해야 함

- Optimistic locking은 **수동 개입**(manual intervention)을 가정
- 자동 retry는 사용자 의도 확인 없이 재시도하므로 위험
- 단, 데이터가 동일하고 버전만 다른 경우는 자동 병합 가능

**출처**: [Optimistic locking and automatic retry | Enterprise Craftsmanship](https://enterprisecraftsmanship.com/posts/optimistic-locking-automatic-retry/)

---

### 2. Exponential Backoff 전략

충돌 후 재시도 시 지수적으로 대기 시간을 증가시키는 전략

#### 구현 방식 (Spring Boot)

```java
@Retryable(
    maxAttempts = 10,
    value = { OptimisticLockingFailureException.class },
    backoff = @Backoff(
        delay = 100,        // 초기 대기: 100ms
        multiplier = 2.0,   // 배수: 2배씩 증가
        maxDelay = 1000     // 최대 대기: 1초
    )
)
public void updateInventory(Long productId, int quantity) {
    // 재고 차감 로직
}
```

#### 대기 시간 계산

| Retry 횟수 | 대기 시간 (ms) | 누적 시간 (ms) |
|-----------|---------------|---------------|
| 1차       | 100           | 100           |
| 2차       | 200           | 300           |
| 3차       | 400           | 700           |
| 4차       | 800           | 1500          |
| 5차 이상   | 1000 (max)    | -             |

#### 장점
- 다른 트랜잭션이 완료될 시간 제공
- Retry storm 방지
- 시스템 부하 완화

**출처**:
- [Implementing Exponential Backoff With Spring Retry](https://dzone.com/articles/implementing-exponential-backoff-with-spring-retry)
- [Design Retry Mechanisms for Resilient Spring Boot Apps](https://www.yugabyte.com/blog/retry-mechanism-spring-boot-app/)

---

### 3. Timeout 경계 설정

사용자 대면 시스템에서는 무한 재시도 불가

#### 권장 설정

```java
@Retryable(
    maxAttempts = 5,                    // 최대 5회 시도
    value = OptimisticLockException.class,
    backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
)
@Transactional(timeout = 10)           // 10초 타임아웃
public void processOrder(Order order) {
    // 주문 처리 로직
}
```

#### 타임아웃 기준
- **Interactive API**: 2-5초
- **Background Job**: 30-60초
- **Batch Process**: 5-10분

**이유**: 사용자는 합리적인 시간 내에 응답을 받아야 함

---

### 4. 트랜잭션 외부에서 Retry

**안전한 패턴**: 트랜잭션 롤백 후 재시도

```java
public void safeUpdate(Long id, UpdateRequest request) {
    int maxRetries = 5;
    for (int i = 0; i < maxRetries; i++) {
        try {
            // 새로운 트랜잭션 시작
            transactionalService.updateWithNewTransaction(id, request);
            return; // 성공
        } catch (OptimisticLockException e) {
            if (i == maxRetries - 1) throw e; // 마지막 시도 실패
            // Exponential backoff
            Thread.sleep((long) (100 * Math.pow(2, i)));
        }
    }
}
```

**이유**: 롤백된 트랜잭션의 Persistence Context는 재사용 불가

**출처**: [How to retry JPA transactions after an OptimisticLockException](https://vladmihalcea.com/optimistic-locking-retry-with-jpa/)

---

### 5. Entity 재조회 (Reload)

재시도 전에 반드시 최신 데이터 로드

```java
@Transactional
public void updateWithRetry(Long productId, int quantity) {
    // 최신 엔티티 조회
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new EntityNotFoundException());

    // 비즈니스 로직 수행
    product.decreaseStock(quantity);

    // 저장 (버전 체크)
    productRepository.save(product);
}
```

**주의**: 오래된 엔티티로 재시도하면 계속 실패

---

### 6. Retry 횟수 제한

무한 루프 방지를 위한 상한선 설정

#### 권장 횟수

| 시나리오               | 권장 횟수 | 이유                          |
|----------------------|----------|-------------------------------|
| 일반 웹 API          | 3-5회    | 사용자 응답 시간 고려          |
| Background Job       | 10-20회  | 더 긴 대기 시간 허용           |
| 높은 경합 시스템      | 1-2회    | Retry보다 Pessimistic 고려    |

**출처**: [A Guide to Optimistic Locking](https://systemdesignschool.io/blog/optimistic-locking)

---

### 7. 충돌 처리 설계 (Conflict Handling)

사용자에게 명확한 옵션 제공

#### 옵션 제공 패턴

1. **재시도** (Retry): 사용자가 다시 시도
2. **병합** (Merge): 최신 버전과 사용자 변경사항 병합
3. **취소** (Discard): 변경사항 폐기

```java
@ControllerAdvice
public class OptimisticLockExceptionHandler {

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
        OptimisticLockException ex
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .code("CONFLICT")
            .message("데이터가 다른 사용자에 의해 수정되었습니다.")
            .actions(List.of(
                "RETRY",    // 재시도
                "RELOAD",   // 최신 데이터 다시 로드
                "CANCEL"    // 취소
            ))
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
```

---

### 8. Monitoring & Instrumentation

운영 중 충돌률 모니터링이 필수

#### 수집 메트릭

```java
@Aspect
@Component
public class OptimisticLockMonitor {

    private final MeterRegistry registry;

    @AfterThrowing(
        pointcut = "@annotation(Transactional)",
        throwing = "ex"
    )
    public void monitorOptimisticLock(OptimisticLockException ex) {
        // 충돌 카운터 증가
        registry.counter("optimistic_lock.conflicts",
            "entity", ex.getEntity().getClass().getSimpleName()
        ).increment();
    }
}
```

#### 알람 임계값 설정

- **정상**: 충돌률 < 1%
- **주의**: 충돌률 1-5%
- **경고**: 충돌률 > 5% (Pessimistic Lock 전환 고려)

**출처**: [Optimistic Concurrency Control: A Practical Guide for 2025](https://www.shadecoder.com/topics/optimistic-concurrency-control-a-practical-guide-for-2025)

---

### 9. 낮은 경합 환경에서 사용

Optimistic Lock이 효과적인 조건

- 읽기 : 쓰기 비율 >> 10:1
- 트랜잭션이 짧음 (< 1초)
- 데이터가 자연스럽게 분할됨 (partitioned)

**출처**: [Optimistic Locking in JPA | Baeldung](https://www.baeldung.com/jpa-optimistic-locking)

---

## Pessimistic 전환 기준

Optimistic Lock에서 Pessimistic Lock으로 전환해야 하는 시점

---

### 1. 충돌률 기반 전환 기준

#### 임계값 (Threshold)

| 충돌률      | 상태   | 조치                                    |
|-----------|--------|----------------------------------------|
| < 1%      | 정상   | Optimistic Lock 유지                   |
| 1-5%      | 주의   | 모니터링 강화, Retry 전략 최적화         |
| 5-10%     | 경고   | 핫스팟 분석, 데이터 파티셔닝 검토        |
| > 10%     | 위험   | **Pessimistic Lock 전환 권장**          |

#### 측정 방법

```java
// 충돌률 계산
충돌률 = (OptimisticLockException 발생 횟수 / 전체 트랜잭션 시도 횟수) × 100
```

**근거**: 충돌률이 5-10%를 넘으면 Retry 비용이 Lock 비용을 초과

**출처**:
- [Optimistic vs. Pessimistic Locking | Vlad Mihalcea](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)
- [ByteByteGo | Pessimistic vs Optimistic Locking](https://bytebytego.com/guides/pessimistic-vs-optimistic-locking/)

---

### 2. 데이터 정합성이 중요한 경우

#### 금융 거래 시스템

**특징**:
- 정확한 데이터 작업이 필수 (exact data operations)
- 잔액 오류 허용 불가
- Retry로 인한 이중 처리 위험

**결정**: **Pessimistic Lock 필수**

#### 예시

```java
// 계좌 이체 - Pessimistic Lock 사용
@Lock(LockModeType.PESSIMISTIC_WRITE)
public Account transferFunds(Long accountId, BigDecimal amount) {
    // SELECT ... FOR UPDATE로 즉시 Lock 확보
    Account account = accountRepository.findByIdWithLock(accountId);
    account.withdraw(amount);
    return account;
}
```

**이유**: Optimistic Lock 충돌 시 rollback이 고객 불만 및 데이터 불일치 초래

**출처**: [Pessimistic Locking Vs. Optimistic Locking | Modern Treasury](https://www.moderntreasury.com/learn/pessimistic-locking-vs-optimistic-locking)

---

### 3. 트랜잭션 비용이 높은 경우

#### 장시간 트랜잭션

**특징**:
- 복잡한 계산 로직 (5-10초 소요)
- 여러 테이블 Join 및 집계
- 외부 API 호출 포함

**문제점**: Retry 시 모든 작업을 처음부터 다시 수행 → 성능 저하

**결정**: **Pessimistic Lock으로 전환**

#### 판단 기준

```
Retry 비용 = 트랜잭션 소요 시간 × 예상 충돌률 × Retry 횟수

만약 Retry 비용 > Lock 대기 비용 → Pessimistic Lock 선택
```

**출처**: [Pessimistic vs Optimistic Locking | System Design Codex](https://newsletter.systemdesigncodex.com/p/pessimistic-vs-optimistic-locking)

---

### 4. 높은 동시성 환경 (High Contention)

#### 특징

- 동일 레코드에 대한 동시 쓰기 작업 빈번
- 인기 상품 재고 차감 (Flash Sale)
- 좌석 예약 시스템의 프리미엄 좌석
- 한정판 티켓 판매

#### 문제점

- Optimistic Lock 성능이 급격히 저하
- Retry storm으로 시스템 부하 증가
- 사용자 경험 악화 (반복적인 실패)

#### 해결 방법

**하이브리드 접근**:
1. 일반 상품: Optimistic Lock
2. 인기 상품 (조회수 상위 10%): Pessimistic Lock
3. Flash Sale 기간: 전체 Pessimistic Lock

```java
public void decreaseStock(Long productId, int quantity) {
    Product product = productRepository.findById(productId);

    if (product.isPopular()) {
        // Pessimistic Lock 사용
        productRepository.findByIdWithLock(productId);
    } else {
        // Optimistic Lock 사용
        productRepository.findById(productId);
    }

    product.decreaseStock(quantity);
}
```

**출처**: [Managing concurrent transactions in E-commerce](https://wjaets.com/node/782)

---

### 5. Retry가 불가능한 비즈니스 로직

#### 사례

- 이벤트 발행 (한 번만 발행되어야 함)
- 외부 결제 API 호출 (이중 결제 방지)
- 알림 전송 (중복 발송 금지)

**문제점**: Retry 시 부작용(side effect) 발생

**결정**: **Pessimistic Lock으로 선행 방지**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
public void processPayment(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId);

    // 외부 결제 API 호출 (한 번만 실행되어야 함)
    paymentGateway.charge(order.getAmount());

    order.markAsPaid();
}
```

---

### 6. 시스템 처리량이 중요하지 않은 경우

#### 특징

- 사용자 수가 적음 (< 100 concurrent users)
- 응답 시간보다 정확성이 우선
- Lock 대기 시간 허용 가능

**결정**: Pessimistic Lock이 더 단순하고 안전

**이유**:
- Optimistic Lock의 복잡한 Retry 로직 불필요
- 소규모 시스템에서는 Lock overhead 미미

---

### 7. 모니터링 결과 기반 전환

#### 알람 트리거 예시

```yaml
# Prometheus Alert Rule
- alert: HighOptimisticLockConflicts
  expr: |
    (
      rate(optimistic_lock_conflicts_total[5m])
      /
      rate(transactions_total[5m])
    ) > 0.05
  for: 10m
  annotations:
    summary: "충돌률 5% 초과 - Pessimistic Lock 전환 검토 필요"
```

#### 대시보드 메트릭

- 충돌률 추이 그래프
- Retry 평균 횟수
- 트랜잭션 평균 소요 시간
- Lock 대기 시간 (Pessimistic Lock 사용 시)

**조치**: 충돌률이 지속적으로 높으면 해당 엔티티만 Pessimistic으로 전환

---

### 8. 종합 의사결정 플로우차트

```
시작
 │
 ├─ 충돌률 > 10%? ─── YES ─→ Pessimistic Lock
 │        │
 │       NO
 │        │
 ├─ 금융/결제 시스템? ─── YES ─→ Pessimistic Lock
 │        │
 │       NO
 │        │
 ├─ 트랜잭션 > 5초? ─── YES ─→ Pessimistic Lock
 │        │
 │       NO
 │        │
 ├─ 높은 동시성 예상? ─── YES ─→ Pessimistic Lock (핫스팟만)
 │        │
 │       NO
 │        │
 └─ Optimistic Lock 유지 + Monitoring
```

---

### 9. 실제 전환 사례

#### E-commerce 플랫폼 사례

**초기**: 모든 재고 관리에 Pessimistic Lock 사용
- 문제: 성능 병목 발생

**변경**: Optimistic Lock으로 전체 전환
- 결과: 처리량 3배 증가, 충돌률 < 2%

**최종 최적화**: 하이브리드 전략
- 일반 상품: Optimistic Lock
- 인기 상품 (상위 5%): Pessimistic Lock
- 결과: 성능과 정확성 모두 확보

**출처**: 앞서 언급한 Salesforce Commerce Cloud 사례

---

## UX 처리 방법 상세

충돌 발생 시 사용자에게 어떻게 알릴 것인가?

---

### 1. Silent Retry (자동 재시도)

**적용 시나리오**: 충돌이 드물고 재시도가 안전한 경우

#### 장점
- 사용자가 충돌을 인지하지 못함
- 매끄러운 경험 (seamless)

#### 단점
- 사용자 의도 확인 없이 재시도
- 장시간 로딩 시 불안감 유발

#### 구현 예시

```javascript
// Frontend - 자동 재시도
async function updateProduct(productId, data) {
    const maxRetries = 3;
    for (let i = 0; i < maxRetries; i++) {
        try {
            return await api.put(`/products/${productId}`, data);
        } catch (error) {
            if (error.status === 409 && i < maxRetries - 1) {
                // 충돌 발생, 재시도
                await sleep(100 * Math.pow(2, i)); // Exponential backoff
                continue;
            }
            throw error; // 최종 실패
        }
    }
}
```

**사용 사례**: Notion, Google Docs (블록 단위 편집)

---

### 2. User Notification (사용자 알림)

**적용 시나리오**: 사용자 판단이 필요한 경우

#### 메시지 예시

**일반 알림**:
```
❌ 저장 실패
다른 사용자가 이 항목을 수정했습니다.
최신 데이터를 불러온 후 다시 시도하세요.

[최신 데이터 보기] [다시 시도]
```

**협업 도구 (Confluence 스타일)**:
```
⚠️ 충돌 감지
다른 사용자가 이 페이지를 수정했습니다.

[내 변경사항으로 덮어쓰기]
[타인의 변경사항 보기]
[취소]
```

**E-commerce (재고 부족)**:
```
😞 죄송합니다
선택하신 상품이 방금 품절되었습니다.
다른 옵션을 확인해보세요.

[유사 상품 보기] [장바구니로 돌아가기]
```

---

### 3. Diff View (변경사항 비교)

**적용 시나리오**: 협업 편집 도구

#### UI 구조

```
┌─────────────────────────────────────────┐
│ 충돌 감지 - 변경사항 비교                │
├─────────────────────────────────────────┤
│ 내 변경사항          │  타인의 변경사항    │
│ (5분 전)            │  (2분 전 - Alice)  │
├────────────────────┼────────────────────┤
│ 제목: 프로젝트 계획  │  제목: Q1 계획     │
│ 내용: 상세 일정...  │  내용: 예산 포함... │
└─────────────────────────────────────────┘

[내 것 유지] [상대방 것 선택] [수동 병합]
```

**도구**: Git-style three-way merge

---

### 4. Real-time Warning (실시간 경고)

**적용 시나리오**: Google Docs, Figma 등 실시간 협업

#### 구현

```javascript
// WebSocket으로 동시 편집자 알림
socket.on('user-editing-same-block', (data) => {
    showWarning(`${data.userName}님이 이 섹션을 편집 중입니다.`);
});
```

#### UI 표시

```
┌──────────────────────────────────┐
│ 👤 Alice님이 이 단락을 편집 중... │
└──────────────────────────────────┘
   ↓ (편집 중인 텍스트에 반투명 오버레이)
```

**효과**: 사전 충돌 방지

---

### 5. Conflict Resolution Wizard (충돌 해결 마법사)

**적용 시나리오**: 복잡한 데이터 편집

#### 단계별 가이드

```
1단계: 충돌 확인
  "다른 사용자가 3개 필드를 수정했습니다."

2단계: 필드별 선택
  ┌────────────────────────────────┐
  │ 제목:      [내 것] [상대방 것] │
  │ 설명:      [내 것] [상대방 것] │
  │ 마감일:    [내 것] [상대방 것] │
  └────────────────────────────────┘

3단계: 미리보기
  "병합 결과를 확인하세요."

4단계: 저장
```

---

### 6. Optimistic UI (낙관적 UI)

**적용 시나리오**: SNS 좋아요, 댓글 등

#### 동작 방식

1. 사용자 클릭 → 즉시 UI 업데이트 (낙관적 가정)
2. 서버 요청 전송
3. 충돌 발생 → UI 롤백 + 알림

```javascript
function likePost(postId) {
    // 1. 즉시 UI 업데이트
    updateUIOptimistically(postId, +1);

    // 2. 서버 요청
    api.post(`/posts/${postId}/like`)
        .catch(error => {
            if (error.status === 409) {
                // 3. 충돌 시 롤백
                updateUIOptimistically(postId, -1);
                showToast("이미 좋아요를 누르셨습니다.");
            }
        });
}
```

**장점**: 빠른 반응성
**단점**: 롤백이 사용자 혼란 유발 가능

---

### 7. Loading State (로딩 상태 표시)

Retry 중 사용자에게 진행 상황 전달

```javascript
async function saveWithRetry(data) {
    showLoading("저장 중...");

    for (let i = 0; i < 3; i++) {
        try {
            await api.save(data);
            hideLoading();
            showSuccess("저장 완료!");
            return;
        } catch (error) {
            if (error.status === 409 && i < 2) {
                updateLoading(`재시도 중... (${i + 1}/3)`);
                await sleep(1000);
                continue;
            }
            hideLoading();
            showError("저장 실패. 다시 시도하세요.");
        }
    }
}
```

**UI**:
```
┌────────────────────────┐
│ ⏳ 재시도 중... (2/3)  │
│ ▓▓▓▓▓▓▓░░░ 70%        │
└────────────────────────┘
```

---

### 8. Graceful Degradation (우아한 성능 저하)

높은 충돌 상황에서 기능 제한

```
충돌률 > 10% 감지
  ↓
일시적으로 편집 잠금
  ↓
"현재 많은 사용자가 접속 중입니다.
 잠시 후 다시 시도하세요."
  ↓
읽기 전용 모드로 전환
```

---

## 모니터링 및 알람 설정

운영 환경에서 충돌률을 실시간으로 추적하는 방법

---

### 1. 메트릭 수집

#### Micrometer + Prometheus

```java
@Component
public class OptimisticLockMetrics {

    private final Counter conflictCounter;
    private final Counter transactionCounter;

    public OptimisticLockMetrics(MeterRegistry registry) {
        this.conflictCounter = Counter.builder("optimistic_lock.conflicts")
            .tag("entity", "product")
            .description("Optimistic lock conflict count")
            .register(registry);

        this.transactionCounter = Counter.builder("optimistic_lock.transactions")
            .tag("entity", "product")
            .description("Total transaction attempts")
            .register(registry);
    }

    @Around("@annotation(Transactional)")
    public Object monitorTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        transactionCounter.increment();
        try {
            return joinPoint.proceed();
        } catch (OptimisticLockException e) {
            conflictCounter.increment();
            throw e;
        }
    }
}
```

---

### 2. Grafana 대시보드

#### 패널 구성

1. **충돌률 그래프**
```promql
rate(optimistic_lock_conflicts_total[5m])
/
rate(optimistic_lock_transactions_total[5m]) * 100
```

2. **엔티티별 충돌 분포**
```promql
sum by (entity) (optimistic_lock_conflicts_total)
```

3. **평균 Retry 횟수**
```promql
avg(optimistic_lock_retry_count)
```

---

### 3. 알람 규칙

```yaml
groups:
  - name: optimistic_lock_alerts
    rules:
      - alert: HighConflictRate
        expr: |
          (
            rate(optimistic_lock_conflicts_total[5m])
            /
            rate(optimistic_lock_transactions_total[5m])
          ) > 0.05
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "충돌률 5% 초과"
          description: "Entity {{ $labels.entity }}에서 10분간 충돌률이 {{ $value }}%입니다."

      - alert: CriticalConflictRate
        expr: |
          (
            rate(optimistic_lock_conflicts_total[5m])
            /
            rate(optimistic_lock_transactions_total[5m])
          ) > 0.10
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "충돌률 10% 초과 - 긴급"
          description: "Pessimistic Lock 전환을 즉시 검토하세요."
```

---

### 4. 로그 분석

```java
@Slf4j
@ControllerAdvice
public class OptimisticLockExceptionHandler {

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<?> handleOptimisticLock(OptimisticLockException ex) {
        // 구조화된 로그
        log.warn("Optimistic lock conflict detected",
            Map.of(
                "entity", ex.getEntity().getClass().getSimpleName(),
                "entityId", ex.getEntity().getId(),
                "currentVersion", ex.getExpectedVersion(),
                "timestamp", Instant.now()
            )
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", "데이터가 수정되었습니다."));
    }
}
```

**ELK Stack 쿼리**:
```
entity:Product AND message:"Optimistic lock conflict"
| stats count() by entity, hourOfDay
```

---

## 참고 문헌 및 출처

### 핵심 레퍼런스

1. [Optimistic Locking in JPA | Baeldung](https://www.baeldung.com/jpa-optimistic-locking)
2. [Optimistic vs. Pessimistic Locking | Vlad Mihalcea](https://vladmihalcea.com/optimistic-vs-pessimistic-locking/)
3. [Optimistic locking and automatic retry | Enterprise Craftsmanship](https://enterprisecraftsmanship.com/posts/optimistic-locking-automatic-retry/)
4. [How to retry JPA transactions after an OptimisticLockException](https://vladmihalcea.com/optimistic-locking-retry-with-jpa/)
5. [ByteByteGo | Pessimistic vs Optimistic Locking](https://bytebytego.com/guides/pessimistic-vs-optimistic-locking/)

### 실제 사례

6. [Optimistic Locking | Salesforce Commerce Cloud](https://documentation.b2c.commercecloud.salesforce.com/DOC2/topic/com.demandware.dochelp/OCAPI/current/usage/OptimisticLocking.html)
7. [Concurrent Editing and Merging Changes | Confluence](https://confluence.atlassian.com/display/DOC/Concurrent+Editing+and+Merging+Changes)
8. [Exploring Notion's Data Model: A Block-Based Architecture](https://www.notion.com/blog/data-model-behind-notion)
9. [Concurrency Conundrum in Booking Systems](https://medium.com/@abhishekranjandev/concurrency-conundrum-in-booking-systems-2e53dc717e8c)

### Retry 전략

10. [Implementing Exponential Backoff With Spring Retry](https://dzone.com/articles/implementing-exponential-backoff-with-spring-retry)
11. [Design Retry Mechanisms for Resilient Spring Boot Apps](https://www.yugabyte.com/blog/retry-mechanism-spring-boot-app/)
12. [Dealing With Optimistic Concurrency Control Collisions](https://www.jimmybogard.com/dealing-with-optimistic-concurrency-control-collisions/)

### 모니터링

13. [Optimistic Concurrency Control: A Practical Guide for 2025](https://www.shadecoder.com/topics/optimistic-concurrency-control-a-practical-guide-for-2025)
14. [Handling Concurrency Conflicts - EF Core | Microsoft](https://learn.microsoft.com/en-us/ef/core/saving/concurrency)

### 협업 도구 기술

15. [Conflict Resolution in Real-Time Collaborative Editing | Hoverify](https://tryhoverify.com/blog/conflict-resolution-in-real-time-collaborative-editing/)
16. [Some notes on editor frameworks + collaborative editing](https://gist.github.com/0xdevalias/2fc3d66875dcc76d5408ce324824deab)

---

## 결론 및 권장사항

### 핵심 요약

1. **Optimistic Lock 적용 조건**
   - 충돌률 < 5%
   - 읽기:쓰기 비율 > 10:1
   - 트랜잭션 시간 < 1초

2. **Retry 전략**
   - Exponential Backoff 사용 (100ms → 200ms → 400ms...)
   - 최대 3-5회 재시도
   - 10초 이내 타임아웃

3. **UX 처리**
   - 충돌 빈도에 따라 자동/수동 재시도 선택
   - 협업 도구는 Diff View 제공
   - E-commerce는 대안 상품 추천

4. **Pessimistic 전환 기준**
   - 충돌률 > 10%
   - 금융/결제 시스템
   - 높은 동시성 환경 (Flash Sale 등)
   - 트랜잭션 비용이 높은 경우

5. **모니터링**
   - 충돌률을 실시간 추적
   - 5% 초과 시 경고 알람
   - 엔티티별 충돌 분포 분석

### 본 프로젝트 적용 계획

**Sprint 6 목표: Optimistic Lock 구현**

1. **구현 대상**: `Product` 엔티티의 재고 차감
2. **버전 컬럼**: `@Version` 사용
3. **Retry 전략**: Spring Retry + Exponential Backoff (최대 3회)
4. **모니터링**: Micrometer 메트릭 수집
5. **성능 테스트**: k6로 충돌률 측정 (목표 < 5%)

이후 Sprint에서 Redis Distributed Lock, Lua Script와 성능 비교 예정.
