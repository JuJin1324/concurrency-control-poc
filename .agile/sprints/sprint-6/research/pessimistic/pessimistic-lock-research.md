# Pessimistic Lock 운영 사례 연구

**작성일:** 2026-02-03
**스프린트:** Sprint 6 (US-6.1)
**목적:** 실제 운영 환경에서 Pessimistic Lock 사용 사례 조사 및 운영 노하우 수집

---

## 1. 금융권 사례

### 1.1 결제 시스템 (Stripe, PayPal)

#### 도메인
- 글로벌 결제 처리 시스템
- Stripe: 연간 $1.4조 처리 (2024)
- PayPal: 연간 $1.5조 처리, 일일 4,100만 건 트랜잭션 (2023)

#### 선택 이유
결제 처리는 금융 거래의 핵심으로, 데이터 정합성이 절대적으로 중요합니다. 중복 결제나 잔액 부정합은 심각한 재무적, 법적 문제를 야기하므로 강력한 동시성 제어가 필수적입니다.

#### 구현 방식

**1. Idempotency Key 기반 동시성 제어**
- UUID를 idempotency key로 사용하여 중복 요청 방지
- HTTP 헤더에 idempotency key 포함
- 동일한 key 재요청 시 기존 트랜잭션 결과 반환

```
처리 흐름:
1. 클라이언트가 UUID를 idempotency_key로 전송
2. 시스템이 key 존재 여부 확인
3. 존재하면 기존 결과 반환, 없으면 새로 처리
4. 트랜잭션 완료 후 key-result 매핑 저장
```

**2. Apache Kafka를 통한 금융 데이터 처리**
- Stripe: Kafka를 financial source of truth로 사용
- 99.9999% 가용성 달성
- PayPal: 하루 1조 건 이상의 이벤트 스트리밍

**3. 분산 시스템 아키텍처**
- 3계층 구조: Frontend → Middleware → Transactional Layer
- Double-entry Bookkeeping을 활용한 Ledger 시스템
- Fraud Detection Module 통합

#### 성능 지표
- Sub-millisecond 레이턴시 요구사항
- 99.9999% 가용성 (Stripe Kafka)
- 초당 수백만 건 트랜잭션 처리 능력

#### 운영 노하우

**Deadlock 대응:**
- 트랜잭션을 짧고 작게 유지
- 여러 리소스 수정 시 일관된 순서 유지
- Retry logic with exponential backoff 구현

**Timeout 설정:**
- Application-level timeout과 database timeout 정렬
- 사용자 경험을 고려한 timeout 밸런싱
- 너무 짧으면 불필요한 실패, 너무 길면 blocking chain 증가

**모니터링:**
- Real-time lock wait time 추적
- Threshold 초과 시 알림 설정
- 트랜잭션 패턴 및 잠재적 deadlock 시나리오 정기 리포팅

#### 출처
- [Building a Payment System: Stripe's Architecture](https://dev.to/sgchris/building-a-payment-system-stripes-architecture-for-financial-transactions-3mlg)
- [How Global Payment Processors Use Data Streaming to Scale](https://www.kai-waehner.de/blog/2025/08/25/how-global-payment-processors-like-stripe-and-paypal-use-data-streaming-to-scale/)
- [Designing a Payment System by Gergely Orosz](https://newsletter.pragmaticengineer.com/p/designing-a-payment-system)

---

### 1.2 카카오페이 트랜잭션 관리

#### 도메인
- 모바일 결제 및 금융 서비스
- 국내 대표 핀테크 플랫폼

#### 선택 이유
대규모 동시 접속과 거래가 발생하는 환경에서 JPA Transactional과 동시성 제어의 실제 적용 사례를 확인할 수 있습니다.

#### 구현 방식

**1. JPA Transactional 최적화**
- 단일 select-update 작업에서 Transactional만으로는 완전한 동시성 제어 불가
- MySQL에서 Phantom Read 문제 발생 가능
- 추가 조치 필요: Serializable 격리 수준 또는 Optimistic Lock 적용

**2. 대안적 접근**
- Redis를 활용한 서비스 API 레벨의 중복 요청 제어
- READ COMMITTED 격리 수준 사용 고려 (old snapshot 허용 시)
- FOR UPDATE 또는 FOR SHARE 절 선택적 사용

#### 성능 지표
- 트랜잭션 미적용 시 select 쿼리 2-3배 증가
- DB 사용 성능 개선
- API 테스트 결과 약 2배 성능 향상

#### 운영 노하우

**동시성 제어 전략:**
- 동시성 제어가 필요한 케이스 명확히 식별
- 격리 수준과 락 메커니즘의 trade-off 이해
- 성능과 정합성 균형점 찾기

**모니터링:**
- DB Transaction 이해 필수
- Spring Transactional 동작 방식 숙지
- 데이터 소스 설정 최적화

#### 출처
- [JPA Transactional 잘 알고 쓰고 계신가요? - 카카오페이 기술 블로그](https://tech.kakaopay.com/post/jpa-transactional-bri/)

---

## 2. 전자상거래 재고 관리 사례

### 2.1 E-commerce 재고 시스템

#### 도메인
- 전자상거래 플랫폼 재고 관리
- 실시간 재고 업데이트 시스템
- 과판매(overselling) 방지

#### 선택 이유
재고 관리는 비관적 락의 전형적인 사용 사례로, 특히 재고가 1개만 남았을 때 두 명의 사용자가 동시에 구매하는 상황을 방지해야 합니다.

#### 구현 방식

**1. Pessimistic Lock for High-Contention Items**
- 초기에는 과판매 방지를 위해 pessimistic locking 사용
- 플랫폼 성장에 따라 locking이 성능 병목으로 작용
- Hybrid approach로 전환

**2. Hybrid Locking Strategy**
```
고가치/한정 상품: Pessimistic Lock + Reservation Timestamp
일반 상품: Optimistic Concurrency Control
```

**3. Transaction Isolation Levels**
- READ COMMITTED: 일반적인 재고 조회
- SERIALIZABLE: 중요 재고 업데이트 (성능 trade-off 있음)

#### 성능 지표
- 충돌 발생 빈도에 따라 성능 차이 발생
- High contention 시나리오에서 pessimistic lock 유리
- Low contention 시나리오에서는 optimistic lock이 더 효율적

#### 운영 노하우

**Deadlock 대응:**
- 재고 업데이트 순서를 상품 ID 오름차순으로 표준화
- 트랜잭션 처리 시간 최소화
- Composite index로 lock 범위 최소화

**Timeout 설정:**
- Lock timeout을 사용자 경험을 고려하여 설정
- 재고 확보 실패 시 즉시 사용자에게 알림
- Retry logic 구현 (최대 3회, exponential backoff)

**모니터링:**
- Innodb_row_lock_waits 메트릭 추적
- Lock wait time 평균 및 최대값 모니터링
- 특정 상품의 lock contention 패턴 분석

#### 장애 시나리오 및 대응

**시나리오 1: 플래시 세일로 인한 Lock Contention**
- 증상: 수천 명이 동시에 같은 상품 구매 시도
- 영향: Lock wait timeout 급증, 응답 시간 저하
- 대응:
  - Queue 시스템 도입 (대기열 번호 발급)
  - 배치 처리로 주문 묶음 처리
  - Lock 보유 시간 단축

**시나리오 2: Long-Running Transaction**
- 증상: 일부 트랜잭션이 비정상적으로 오래 실행
- 영향: 다른 트랜잭션들이 lock 대기
- 대응:
  - Statement timeout 설정 (예: 10초)
  - Connection pool에서 idle connection 정리
  - Monitoring으로 slow query 식별 및 최적화

#### 출처
- [Managing concurrent transactions in E-commerce: Isolation levels and locking strategies](https://wjaets.com/node/782)
- [Optimistic Locking vs. Pessimistic Locking Guide](https://www.linkedin.com/pulse/optimistic-locking-vs-pessimistic-guide-system-designers-yeshwanth-n-19goc)
- [Concurrency-Related Latency: Pessimistic Locking](https://dashmind.com/concurrency-related-latency-pessimistic-locking/)

---

### 2.2 티켓팅 시스템 (콘서트 예매)

#### 도메인
- 공연 티켓 예매 시스템
- 좌석 예약 및 결제 처리
- 중복 예매 방지

#### 선택 이유
티켓팅 시스템은 극도로 높은 동시성(예: 10만 명이 동시에 같은 티켓 예매)과 엄격한 데이터 정합성 요구사항을 가진 대표적인 사례입니다.

#### 구현 방식

**1. Pessimistic Locking with Time-Limited Reservation**
```sql
-- 좌석 선택 시 락 획득
SELECT * FROM seats
WHERE seat_id = ?
FOR UPDATE;

-- 좌석 상태를 RESERVED로 변경
UPDATE seats
SET status = 'RESERVED',
    reserved_at = NOW(),
    reserved_by = ?
WHERE seat_id = ?;
```

**2. Reservation Flow**
```
1. 사용자가 좌석 선택
2. SELECT FOR UPDATE로 좌석 락 획득
3. 좌석을 5-10분간 RESERVED 상태로 변경
4. 사용자가 결제 완료 → BOOKED 상태로 전환
5. 시간 초과 또는 취소 → AVAILABLE 상태로 복귀
```

**3. BookMyShow 실제 동작 확인**
- 동시 예매 테스트: 한 사용자가 결제 페이지에 있을 때 다른 사용자는 해당 좌석을 예약됨으로 표시
- 결제 미완료 시 일정 시간 후 좌석 자동 해제
- 새로고침 시 좌석이 다시 available로 전환

#### 성능 지표
- 10만 명 동시 접속 처리 목표
- Flash sale 시나리오 대응
- Sub-second 응답 시간 유지

#### 운영 노하우

**Deadlock 대응:**
- 좌석 락 획득 순서를 seat_id 기준으로 정렬
- 최소한의 테이블만 락 (seat 테이블만, user/payment는 제외)
- 트랜잭션 스코프 최소화

**Timeout 설정:**
```
Lock Timeout: 5초 (빠른 실패)
Reservation Timeout: 5-10분 (결제 완료 대기)
Connection Timeout: 30초
```

**모니터링:**
- 좌석별 예약 시도 횟수 추적
- Lock contention 높은 좌석 식별 (VIP석 등)
- 예약 완료율(conversion rate) 모니터링

#### 장애 시나리오 및 대응

**시나리오 1: Flash Sale - 10만 명 동시 접속**
- 증상: 티켓 오픈 시간에 트래픽 폭증
- 영향: DB 커넥션 풀 고갈, 응답 지연
- 대응:
  - Virtual waiting room 구현 (대기열 시스템)
  - Connection pool 크기 동적 조정
  - Rate limiting 적용

**시나리오 2: 결제 미완료로 인한 좌석 점유**
- 증상: 결제하지 않은 사용자가 좌석을 장시간 점유
- 영향: 실제 구매 의사가 있는 사용자의 기회 박탈
- 대응:
  - Scheduled job으로 만료된 예약 정리 (1분마다 실행)
  - WebSocket으로 실시간 좌석 상태 업데이트
  - 결제 페이지에 카운트다운 타이머 표시

#### 출처
- [Building a Ticketing System: Concurrency, Locks, and Race Conditions](https://codefarm0.medium.com/building-a-ticketing-system-concurrency-locks-and-race-conditions-182e0932d962)
- [Booking System with Pessimistic Locks](https://medium.com/javarevisited/booking-system-with-pessimistic-locks-4ec107e4bd5)
- [DB Locking in Reservation Systems](https://akshitbansall.medium.com/db-locking-in-reservation-systems-3b3d574c7676)
- [How To Build a High-Concurrency Ticket Booking System](https://dev.to/zenstack/how-to-build-a-high-concurrency-ticket-booking-system-with-prisma-184n)

---

## 3. 운영 체크리스트

### 3.1 Deadlock 관리

- [ ] **Deadlock 감지 설정**
  - MySQL: `innodb_print_all_deadlocks = ON` (모든 deadlock 로깅)
  - 정기적으로 `SHOW ENGINE INNODB STATUS` 실행
  - Deadlock 로그 분석 및 패턴 파악

- [ ] **Deadlock 방지 전략**
  - 여러 리소스 접근 시 일관된 순서 유지 (예: ID 오름차순)
  - 트랜잭션 크기 최소화 (짧고 작게)
  - 불필요한 FOR UPDATE 사용 지양
  - Composite index로 lock 범위 최소화

- [ ] **Deadlock 발생 시 대응**
  - Application-level retry logic 구현 (최대 3회)
  - Exponential backoff 적용 (예: 100ms → 200ms → 400ms)
  - Deadlock 발생 빈도가 높은 쿼리 식별 및 최적화

### 3.2 Lock Timeout 설정

- [ ] **Database Level Timeout**
  ```sql
  -- MySQL InnoDB 설정
  SET GLOBAL innodb_lock_wait_timeout = 50;  -- 기본값 50초
  SET GLOBAL innodb_rollback_on_timeout = OFF; -- 현재 statement만 rollback
  ```

- [ ] **Connection Pool Timeout**
  - Pool max idle time을 wait_timeout보다 10-15% 짧게 설정
  - Connection validation query 설정
  - Idle connection 정리 주기 설정

- [ ] **Application Level Timeout**
  ```java
  @Transactional(timeout = 10) // 10초
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({
      @QueryHint(name = "javax.persistence.lock.timeout", value = "5000") // 5초
  })
  ```

- [ ] **Timeout 튜닝 전략**
  - 너무 짧으면: 불필요한 트랜잭션 실패 증가
  - 너무 길면: Blocking chain 증가, 사용자 경험 저하
  - 비즈니스 요구사항과 사용자 경험을 고려하여 설정
  - 로컬 환경에서 충분한 테스트 후 운영 적용

### 3.3 모니터링 지표

#### Database Level Metrics

- [ ] **Lock Wait 지표**
  ```sql
  -- MySQL Performance Schema 활성화
  UPDATE performance_schema.setup_instruments
  SET ENABLED = 'YES'
  WHERE NAME = 'wait/lock/metadata/sql/mdl';

  -- Lock wait 확인
  SHOW STATUS LIKE 'Innodb_row_lock_waits';
  SHOW STATUS LIKE 'Innodb_row_lock_time';
  SHOW STATUS LIKE 'Innodb_row_lock_time_avg';
  ```

- [ ] **Deadlock 지표**
  ```sql
  SHOW ENGINE INNODB STATUS\G
  -- LATEST DETECTED DEADLOCK 섹션 확인
  ```

- [ ] **Connection Pool 지표**
  - Active connections
  - Idle connections
  - Connection wait time
  - Connection creation rate

#### Application Level Metrics

- [ ] **Transaction 지표**
  - Transaction duration (평균, P95, P99)
  - Transaction retry count
  - Transaction failure rate by error type

- [ ] **Business 지표**
  - Order completion rate (주문 완료율)
  - Lock timeout failure rate (락 타임아웃으로 인한 실패율)
  - Concurrent users (동시 접속자 수)

#### Monitoring Tools

- [ ] **Prometheus + Grafana**
  - MySQL Exporter로 DB 메트릭 수집
  - Lock wait time, deadlock count 시각화
  - Alert rule 설정 (예: lock wait time > 5s)

- [ ] **MySQL Performance Schema**
  - data_locks 테이블: 보유/요청된 락 정보
  - data_lock_waits 테이블: 락 대기 관계
  - metadata_locks 테이블: 메타데이터 락
  - 메모리/CPU 오버헤드 없이 실시간 수집

- [ ] **APM (Application Performance Monitoring)**
  - Transaction trace
  - Slow query detection
  - Database call stack

### 3.4 알림 설정

- [ ] **Critical Alerts**
  - Deadlock rate > 10/min
  - Lock wait time avg > 5 seconds
  - Transaction failure rate > 5%
  - Database connection pool utilization > 90%

- [ ] **Warning Alerts**
  - Lock wait time avg > 2 seconds
  - Transaction duration P95 > 10 seconds
  - Deadlock detected (informational)

- [ ] **Alert Channels**
  - PagerDuty / OpsGenie for critical
  - Slack for warnings
  - Email for daily summary

---

## 4. 장애 시나리오 및 대응 방안

### 4.1 Lock Wait Timeout 대량 발생

#### 증상
```
ERROR 1205 (HY000): Lock wait timeout exceeded;
try restarting transaction
```
- 애플리케이션 로그에 Lock timeout 에러 급증
- 사용자에게 "일시적인 오류" 메시지 표시
- 주문/결제 완료율 급감

#### 원인 분석
1. **Long-running transaction**
   - 하나의 트랜잭션이 비정상적으로 오래 실행
   - 예: 외부 API 호출을 트랜잭션 내부에서 수행

2. **Hot row contention**
   - 동일한 row를 많은 트랜잭션이 동시에 수정 시도
   - 예: 인기 상품의 재고 업데이트

3. **Index 부재로 인한 Table Lock**
   - WHERE 조건에 인덱스가 없어 전체 테이블 스캔
   - InnoDB가 필요 이상으로 많은 row에 lock

#### 대응 방안

**즉시 조치 (5분 이내):**
```sql
-- 1. 현재 lock 상황 확인
SELECT * FROM performance_schema.data_lock_waits;

-- 2. Blocking query 식별
SELECT
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 3. 필요시 blocking thread 강제 종료
KILL <blocking_thread_id>;
```

**단기 조치 (1시간 이내):**
- Application 재시작으로 connection pool 초기화
- Lock timeout 임시로 증가 (예: 50초 → 100초)
- Rate limiting 강화하여 동시 요청 제한
- Circuit breaker 패턴 적용

**중기 조치 (1주일 이내):**
```java
// 1. Transaction scope 최소화
@Transactional
public void processOrder(OrderRequest request) {
    // 외부 API 호출을 트랜잭션 밖으로 이동
    PaymentResponse payment = paymentClient.charge(request);

    // 최소한의 DB 작업만 트랜잭션 내부에서
    orderRepository.save(order);
    inventoryService.decreaseStock(request.getProductId());
}

// 2. Retry logic 개선
@Retryable(
    value = {CannotAcquireLockException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
public void updateInventory(Long productId, int quantity) {
    // pessimistic lock with retry
}

// 3. Lock timeout hint 명시적 설정
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({
    @QueryHint(name = "javax.persistence.lock.timeout", value = "5000")
})
Optional<Product> findByIdForUpdate(Long id);
```

**장기 조치 (1개월 이내):**
- Hot row에 대한 캐싱 전략 도입 (Redis)
- 재고 관리를 Redis Lua Script로 마이그레이션
- Sharding 고려 (상품별, 카테고리별 분산)
- Read replica 활용하여 read 부하 분산

---

### 4.2 Deadlock 빈발 발생

#### 증상
```
ERROR 1213 (40001): Deadlock found when trying to get lock;
try restarting transaction
```
- 2015년 Holiday Season 실제 사례 (Productive 앱)
- 코드 변경 없이 deadlock 에러 급증
- 데이터베이스 커넥션 대기 큐 증가
- 응답 시간 증가

#### 원인 분석

**실제 사례 분석 (Productive 앱):**
```sql
-- Transaction A
UPDATE table1 WHERE id = 1;
UPDATE table2 WHERE id = 2;

-- Transaction B (역순으로 업데이트)
UPDATE table2 WHERE id = 2;
UPDATE table1 WHERE id = 1;
```

**일반적인 원인:**
1. **Lock 획득 순서 불일치**
   - 트랜잭션 A: Row 1 → Row 2
   - 트랜잭션 B: Row 2 → Row 1
   - 서로 상대방이 가진 lock 대기 → Deadlock

2. **Index 스캔 범위 문제**
   ```sql
   -- 의도: id=10만 lock
   -- 실제: id=10, 11, 12까지 lock (next-key lock)
   SELECT * FROM orders WHERE id >= 10 FOR UPDATE LIMIT 1;
   ```

3. **Foreign Key Lock**
   - 자식 테이블 업데이트 시 부모 테이블에도 lock
   - 순환 참조 시 deadlock 발생 가능

#### 대응 방안

**즉시 조치:**
```sql
-- 1. Deadlock 상세 정보 확인
SHOW ENGINE INNODB STATUS\G

-- 2. 최근 deadlock 로그 분석
SELECT * FROM mysql.error_log
WHERE data LIKE '%LATEST DETECTED DEADLOCK%'
ORDER BY logged DESC LIMIT 10;
```

**근본 원인 해결:**

```java
// Before: Deadlock 발생 가능
public void updateInventoryAndOrder(Long orderId, Long productId) {
    orderRepository.updateStatus(orderId);      // Random order
    productRepository.decreaseStock(productId);  // Random order
}

// After: ID 정렬로 순서 보장
public void updateInventoryAndOrder(Long orderId, Long productId) {
    // 항상 작은 ID부터 lock 획득
    if (orderId < productId) {
        orderRepository.updateStatus(orderId);
        productRepository.decreaseStock(productId);
    } else {
        productRepository.decreaseStock(productId);
        orderRepository.updateStatus(orderId);
    }
}

// Best Practice: 단일 row만 정확히 lock
@Query("SELECT p FROM Product p WHERE p.id = :id")
@Lock(LockModeType.PESSIMISTIC_WRITE)
Product findByIdForUpdate(@Param("id") Long id);
```

**실제 해결 사례 (Productive 앱):**
1. 즉시 조치: Row 단위로 순차 업데이트 (동시성 제거)
2. 근본 해결: Database update 호출 부분에 retry logic 추가

**모니터링 강화:**
```java
@Aspect
@Component
public class DeadlockMonitoringAspect {

    @Around("@annotation(Transactional)")
    public Object monitorDeadlock(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (DeadlockLoserDataAccessException e) {
            // Deadlock 메트릭 기록
            meterRegistry.counter("db.deadlock.count",
                "method", pjp.getSignature().getName()
            ).increment();

            // 상세 로그
            log.error("Deadlock detected: method={}, args={}",
                pjp.getSignature(), pjp.getArgs(), e);

            throw e;
        }
    }
}
```

---

### 4.3 성능 저하 (Lock Contention)

#### 증상
- TPS(Transactions Per Second) 급감
- 평균 응답 시간 3배 이상 증가
- `Innodb_row_lock_waits` 메트릭 급증
- Connection pool active connections 90% 이상 유지

#### 원인 분석

**1. Hot Spot 문제**
```sql
-- 인기 상품 재고 업데이트가 병목
UPDATE products
SET stock = stock - 1
WHERE id = 12345;  -- 100명이 동시에 접근

-- Performance Schema로 확인
SELECT object_name, count_star as lock_count
FROM performance_schema.table_lock_waits_summary_by_table
ORDER BY count_star DESC;
```

**2. Lock Escalation**
- Row lock이 Table lock으로 확대
- 원인: 한 트랜잭션이 너무 많은 row lock
- MySQL: 일반적으로 발생하지 않음 (InnoDB는 항상 row-level)

**3. Lock 보유 시간 과다**
```java
// Bad: Transaction 범위가 너무 넓음
@Transactional
public void processOrder(OrderRequest request) {
    Product product = productRepository.findByIdForUpdate(id); // Lock 시작

    // 외부 API 호출 (3-5초 소요) - Lock 보유 중!
    PaymentResponse payment = externalPaymentApi.charge(request);

    // Email 발송 (1-2초 소요) - 여전히 Lock 보유!
    emailService.sendConfirmation(request.getEmail());

    orderRepository.save(order); // Lock 해제
}
```

#### 대응 방안

**즉시 조치:**
```sql
-- 1. 현재 lock wait 상황 파악
SELECT
    waiting_trx_id,
    waiting_pid,
    blocking_trx_id,
    blocking_pid,
    TIMESTAMPDIFF(SECOND, waiting_trx_started, NOW()) as wait_seconds
FROM sys.innodb_lock_waits
WHERE wait_seconds > 5;

-- 2. 오래 실행 중인 트랜잭션 식별
SELECT
    trx_id,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) as running_seconds,
    trx_query
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 10
ORDER BY running_seconds DESC;
```

**Transaction Scope 최적화:**
```java
// Good: Transaction 범위 최소화
public void processOrder(OrderRequest request) {
    // 1. Lock 없이 데이터 조회
    Product product = productRepository.findById(id)
        .orElseThrow();

    // 2. 외부 API 호출 (트랜잭션 외부)
    PaymentResponse payment = externalPaymentApi.charge(request);

    // 3. 최소한의 DB 작업만 트랜잭션 내부
    updateInventoryAndOrder(id, request, payment);
}

@Transactional
private void updateInventoryAndOrder(Long id, OrderRequest request, PaymentResponse payment) {
    // SELECT FOR UPDATE (Lock 획득)
    Product product = productRepository.findByIdForUpdate(id);

    // 재고 확인 및 업데이트
    if (product.getStock() < request.getQuantity()) {
        throw new OutOfStockException();
    }
    product.decreaseStock(request.getQuantity());

    // 주문 저장
    Order order = Order.create(request, payment);
    orderRepository.save(order);

    // Lock 해제 (트랜잭션 종료)
}
```

**Hot Spot 완화 전략:**

**Option 1: Redis 캐싱**
```java
@Service
public class InventoryService {

    // Redis에서 재고 차감 (Lua Script로 atomic 보장)
    public boolean decreaseStockInCache(Long productId, int quantity) {
        String script =
            "local stock = redis.call('GET', KEYS[1]) " +
            "if stock and tonumber(stock) >= tonumber(ARGV[1]) then " +
            "  redis.call('DECRBY', KEYS[1], ARGV[1]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList("product:stock:" + productId),
            String.valueOf(quantity)
        );

        return result == 1;
    }

    // 주기적으로 Redis → DB 동기화 (bulk update)
    @Scheduled(fixedRate = 10000) // 10초마다
    public void syncStockToDatabase() {
        // Batch update로 DB 부하 최소화
    }
}
```

**Option 2: Queue 기반 처리**
```java
// 동시 요청을 Queue에 적재하고 순차 처리
@Service
public class OrderQueueProcessor {

    private final BlockingQueue<OrderRequest> queue =
        new LinkedBlockingQueue<>(10000);

    @PostConstruct
    public void startProcessing() {
        // 단일 스레드로 순차 처리 (lock contention 없음)
        executorService.submit(() -> {
            while (true) {
                OrderRequest request = queue.take();
                processOrderWithoutLock(request);
            }
        });
    }

    public void enqueueOrder(OrderRequest request) {
        if (!queue.offer(request)) {
            throw new QueueFullException();
        }
    }
}
```

**Option 3: Optimistic Lock으로 전환**
```java
// High contention이 아닌 경우 Optimistic Lock 고려
@Entity
public class Product {
    @Version
    private Long version;  // JPA Optimistic Locking

    private Integer stock;
}

// Application-level retry
@Retryable(
    value = {OptimisticLockException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
public void decreaseStock(Long productId, int quantity) {
    Product product = productRepository.findById(productId)
        .orElseThrow();
    product.decreaseStock(quantity);
    productRepository.save(product);  // Version check
}
```

---

### 4.4 Database 장애 시나리오

#### 증상
- MySQL 재시작 또는 Failover
- 모든 진행 중인 트랜잭션 롤백
- Application에서 connection refused 에러

#### 대응 방안

**Connection Pool 설정 최적화:**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000  # 30초
      validation-timeout: 5000   # 5초
      max-lifetime: 1800000      # 30분 (MySQL wait_timeout보다 짧게)
      maximum-pool-size: 20
      minimum-idle: 5

      # Connection validation
      connection-test-query: SELECT 1

      # Leak detection
      leak-detection-threshold: 60000  # 60초
```

**Circuit Breaker 패턴:**
```java
@Service
public class InventoryService {

    @CircuitBreaker(
        name = "inventory",
        fallbackMethod = "decreaseStockFallback"
    )
    @Retry(name = "inventory")
    public void decreaseStock(Long productId, int quantity) {
        productRepository.decreaseStockWithLock(productId, quantity);
    }

    private void decreaseStockFallback(Long productId, int quantity, Exception e) {
        log.error("Circuit breaker activated for product {}", productId, e);
        // Fallback: Queue에 적재하여 나중에 처리
        orderQueue.enqueue(new PendingOrder(productId, quantity));
        throw new ServiceUnavailableException("재고 시스템 일시 장애");
    }
}

// application.yml
resilience4j:
  circuitbreaker:
    instances:
      inventory:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  retry:
    instances:
      inventory:
        max-attempts: 3
        wait-duration: 1s
```

---

## 5. Spring Boot + JPA 운영 경험에서 얻은 교훈

### 5.1 Database별 Lock Timeout 동작 차이

**중요한 발견:**
- Oracle, PostgreSQL: 기본적으로 LockTimeoutException을 발생시키지 않음
- MySQL: 기본 50초 대기 후 timeout
- Apache Derby: Pessimistic lock 테스트에 가장 적합

**교훈:**
```java
// Database별 분기 처리 필요
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({
    @QueryHint(
        name = "javax.persistence.lock.timeout",
        value = "5000"  // MySQL에서만 동작
    )
})
Product findByIdForUpdate(Long id);

// Oracle, PostgreSQL은 별도 처리 필요
@Query(value = "SELECT * FROM products WHERE id = :id FOR UPDATE WAIT 5",
       nativeQuery = true)
Product findByIdForUpdateOracle(@Param("id") Long id);
```

### 5.2 Lock Timeout 테스트의 어려움

**문제:**
- JPA의 LockTimeout이 RDBMS별로 지원이 다름
- H2 같은 in-memory DB는 프로덕션 동작과 다름
- 통합 테스트에서 재현이 어려움

**해결책:**
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)  // 실제 DB 사용
@Testcontainers
class PessimisticLockTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    void lockTimeoutTest() throws Exception {
        // ExecutorService로 동시 트랜잭션 시뮬레이션
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch latch = new CountDownLatch(1);

        // Transaction 1: Lock 획득 후 대기
        executor.submit(() -> {
            transactionTemplate.execute(status -> {
                Product p = repository.findByIdForUpdate(1L);
                latch.countDown();
                Thread.sleep(10000);  // 10초 대기
                return null;
            });
        });

        latch.await();  // Transaction 1이 lock 획득할 때까지 대기

        // Transaction 2: Lock 대기 후 timeout
        assertThrows(CannotAcquireLockException.class, () -> {
            transactionTemplate.execute(status -> {
                Product p = repository.findByIdForUpdate(1L);
                return null;
            });
        });
    }
}
```

### 5.3 Trade-offs 이해

**Pessimistic Lock의 장단점:**

**장점:**
- 데이터 정합성 보장 (ACID 준수)
- 구현이 간단하고 직관적
- Conflict가 자주 발생하는 경우 효율적

**단점:**
- 동시성(Concurrency) 저하
- Deadlock 위험
- 리소스 사용량 증가 (lock 유지 비용)
- 확장성(Scalability) 제한

**언제 사용해야 하는가:**
```
✅ Pessimistic Lock 사용이 적합한 경우:
- 충돌 발생 확률이 높은 경우 (예: 인기 상품 재고)
- 데이터 정합성이 절대적으로 중요한 경우 (예: 금융 거래)
- Retry 비용이 높은 경우 (예: 복잡한 비즈니스 로직)

❌ Pessimistic Lock 사용이 부적합한 경우:
- 충돌 발생 확률이 낮은 경우
- 높은 동시성이 요구되는 경우
- Read-heavy 워크로드
- Distributed system (single DB lock은 한계)
```

---

## 6. 권장 사항 및 Best Practices

### 6.1 개발 단계

**1. Lock 전략 선택 가이드**
```
Decision Tree:

충돌 발생 빈도는?
├─ 높음 (>10%) → Pessimistic Lock 고려
│   └─ 동시성 요구사항은?
│       ├─ 낮음 → Pessimistic Lock (추천)
│       └─ 높음 → Queue/Redis 고려
│
└─ 낮음 (<10%) → Optimistic Lock 고려
    └─ Retry 비용은?
        ├─ 낮음 → Optimistic Lock (추천)
        └─ 높음 → Pessimistic Lock 고려
```

**2. Index 설계**
```sql
-- Pessimistic Lock 사용 시 필수 Index
CREATE INDEX idx_product_id ON products(id);  -- Primary Key는 자동 index

-- Composite index로 lock 범위 최소화
CREATE INDEX idx_order_user_status
ON orders(user_id, status, created_at);

-- SELECT FOR UPDATE 쿼리 최적화
EXPLAIN SELECT * FROM orders
WHERE user_id = 123 AND status = 'PENDING'
FOR UPDATE;
-- Extra: Using index condition (Good!)
```

**3. Transaction Boundary 최소화**
```java
// Principle: Lock을 가능한 짧게 보유
@Transactional
public void processOrder(OrderRequest request) {
    // ❌ Bad: 외부 호출을 트랜잭션 내부에
    Payment payment = externalApi.charge(request);  // 3-5초
    Order order = createOrder(request, payment);
    orderRepository.save(order);
}

// ✅ Good: 외부 호출을 트랜잭션 외부로
public void processOrder(OrderRequest request) {
    Payment payment = externalApi.charge(request);  // 트랜잭션 외부
    saveOrder(request, payment);  // 최소 범위만 트랜잭션
}

@Transactional
private void saveOrder(OrderRequest request, Payment payment) {
    Order order = createOrder(request, payment);
    orderRepository.save(order);  // 0.1초
}
```

### 6.2 운영 단계

**1. 모니터링 대시보드 구성**

```yaml
# Grafana Dashboard 예시
Dashboard: Database Lock Monitoring
├─ Panel 1: Lock Wait Time (avg, p95, p99)
│   Query: innodb_row_lock_time / innodb_row_lock_waits
│   
├─ Panel 2: Lock Waits Count
│   Query: rate(innodb_row_lock_waits[5m])
│
├─ Panel 3: Deadlock Count
│   Query: increase(mysql_global_status_innodb_deadlocks[5m])
│
├─ Panel 4: Active Transactions
│   Query: mysql_info_schema_innodb_trx_count
│
└─ Panel 5: Long Running Queries (>5s)
    Query: mysql_perf_schema_events_statements_total{time>5}
```

**2. Alert Rule 설정**

```yaml
# Prometheus Alert Rules
groups:
  - name: database_locks
    interval: 30s
    rules:
      - alert: HighLockWaitTime
        expr: |
          mysql_global_status_innodb_row_lock_time_avg > 5000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High lock wait time detected"
          description: "Average lock wait time is {{ $value }}ms"

      - alert: DeadlockDetected
        expr: |
          increase(mysql_global_status_innodb_deadlocks[5m]) > 10
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Multiple deadlocks detected"
          description: "{{ $value }} deadlocks in last 5 minutes"

      - alert: LockTimeoutExceeded
        expr: |
          increase(mysql_errors_total{error="lock_wait_timeout"}[5m]) > 50
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High rate of lock timeouts"
          description: "{{ $value }} lock timeouts in last 5 minutes"
```

**3. Runbook 작성**

```markdown
# Runbook: Pessimistic Lock 장애 대응

## Severity: P1 - Critical

### Symptoms
- Lock wait timeout 에러 급증
- TPS 급감 (>50% drop)
- User complaints about timeouts

### Immediate Actions (5분 이내)
1. 확인: Grafana Dashboard 확인
2. 식별: `SHOW ENGINE INNODB STATUS\G` 실행
3. 조치: Blocking query 강제 종료 (필요시)
   ```sql
   SELECT * FROM information_schema.innodb_lock_waits;
   KILL <blocking_thread_id>;
   ```

### Investigation (30분 이내)
1. Deadlock 로그 분석
2. Slow query log 확인
3. Application log에서 패턴 파악

### Resolution (1시간 이내)
1. Hot path identification
2. Transaction scope 최소화
3. Index 추가 (필요시)
4. Cache layer 추가 (필요시)

### Post-Mortem
- RCA (Root Cause Analysis) 문서 작성
- Preventive measures 계획
- Monitoring/Alerting 개선
```

### 6.3 성능 튜닝

**1. Connection Pool 최적화**

```yaml
# HikariCP 권장 설정 (MySQL)
spring:
  datasource:
    hikari:
      # Pool size = ((core_count * 2) + effective_spindle_count)
      # 예: 4 core + 1 disk = 9
      maximum-pool-size: 10
      minimum-idle: 5

      # Timeout 설정
      connection-timeout: 30000     # 30초
      validation-timeout: 5000      # 5초
      idle-timeout: 600000          # 10분
      max-lifetime: 1800000         # 30분

      # MySQL wait_timeout (default 28800초 = 8시간)
      # max-lifetime을 wait_timeout보다 짧게 설정
```

**2. Query Hint 활용**

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Lock timeout 명시적 설정
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "5000"),
        @QueryHint(name = "javax.persistence.query.timeout", value = "10000")
    })
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // SKIP LOCKED (MySQL 8.0+, PostgreSQL 9.5+)
    // Lock 대기하지 않고 건너뛰기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "-2") // SKIP LOCKED
    })
    @Query("SELECT p FROM Product p WHERE p.stock > 0 ORDER BY p.id LIMIT 1")
    Optional<Product> findAvailableProductSkipLocked();

    // NOWAIT (즉시 실패)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "0") // NOWAIT
    })
    Optional<Product> findByIdForUpdateNoWait(Long id);
}
```

**3. Batch Processing**

```java
// Lock contention 완화를 위한 Batch 처리
@Service
public class InventoryBatchService {

    @Scheduled(fixedRate = 5000) // 5초마다
    @Transactional
    public void processPendingOrders() {
        // 대기 중인 주문 조회 (no lock)
        List<Order> pendingOrders = orderRepository
            .findByStatus(OrderStatus.PENDING, PageRequest.of(0, 100));

        if (pendingOrders.isEmpty()) return;

        // Product별로 그룹화
        Map<Long, List<Order>> ordersByProduct = pendingOrders.stream()
            .collect(Collectors.groupingBy(Order::getProductId));

        // Product ID 정렬 (deadlock 방지)
        List<Long> sortedProductIds = new ArrayList<>(ordersByProduct.keySet());
        Collections.sort(sortedProductIds);

        // 순차적으로 처리
        for (Long productId : sortedProductIds) {
            List<Order> orders = ordersByProduct.get(productId);
            processOrdersForProduct(productId, orders);
        }
    }

    private void processOrdersForProduct(Long productId, List<Order> orders) {
        // Single lock per product
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow();

        int totalQuantity = orders.stream()
            .mapToInt(Order::getQuantity)
            .sum();

        if (product.getStock() >= totalQuantity) {
            product.decreaseStock(totalQuantity);
            orders.forEach(order -> order.setStatus(OrderStatus.CONFIRMED));
        } else {
            // 재고 부족 처리
            orders.forEach(order -> order.setStatus(OrderStatus.OUT_OF_STOCK));
        }
    }
}
```

---

## 7. 참고 자료

### 공식 문서
- [MySQL InnoDB Lock and Lock-Wait Information](https://dev.mysql.com/doc/refman/8.0/en/innodb-information-schema-understanding-innodb-locking.html)
- [MySQL InnoDB Deadlock Detection](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlock-detection.html)
- [MySQL Performance Schema Lock Tables](https://dev.mysql.com/doc/mysql-perfschema-excerpt/8.0/en/performance-schema-lock-tables.html)
- [Spring Data JPA - @Lock Annotation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#locking)

### 기술 블로그 및 사례 연구

#### 금융권
- [JPA Transactional 잘 알고 쓰고 계신가요? - 카카오페이 기술 블로그](https://tech.kakaopay.com/post/jpa-transactional-bri/)
- [Building a Payment System: Stripe's Architecture](https://dev.to/sgchris/building-a-payment-system-stripes-architecture-for-financial-transactions-3mlg)
- [How Global Payment Processors Use Data Streaming](https://www.kai-waehner.de/blog/2025/08/25/how-global-payment-processors-like-stripe-and-paypal-use-data-streaming-to-scale/)
- [Designing a Payment System](https://newsletter.pragmaticengineer.com/p/designing-a-payment-system)

#### 전자상거래
- [Managing concurrent transactions in E-commerce](https://wjaets.com/node/782)
- [Optimistic Locking vs. Pessimistic Locking Guide](https://www.linkedin.com/pulse/optimistic-locking-vs-pessimistic-guide-system-designers-yeshwanth-n-19goc)
- [Concurrency-Related Latency: Pessimistic Locking](https://dashmind.com/concurrency-related-latency-pessimistic-locking/)

#### 티켓팅 시스템
- [Building a Ticketing System: Concurrency, Locks, and Race Conditions](https://codefarm0.medium.com/building-a-ticketing-system-concurrency-locks-and-race-conditions-182e0932d962)
- [Booking System with Pessimistic Locks](https://medium.com/javarevisited/booking-system-with-pessimistic-locks-4ec107e4bd5)
- [DB Locking in Reservation Systems](https://akshitbansall.medium.com/db-locking-in-reservation-systems-3b3d574c7676)
- [How To Build a High-Concurrency Ticket Booking System](https://dev.to/zenstack/how-to-build-a-high-concurrency-ticket-booking-system-with-prisma-184n)

#### Deadlock 대응
- [How we handled MySQL deadlocks in Productive](https://dev.to/productive/how-we-handled-mysql-deadlocks-in-productive-part-1-15ce)
- [Lessons Learned from Debugging a Deadlock in Production MySQL](https://medium.com/@rizqimulkisrc/lessons-learned-from-debugging-a-deadlock-in-production-mysql-c0b2f558ebaf)
- [Diagnosing and Resolving MySQL deadlocks](http://techblog.spanning.com/2016/02/02/Diagnosing-and-Resolving-MySQL-deadlocks/)
- [MySQL Deadlock Resolution Guide](https://codevnexus.com/blog/mysql-deadlock-resolution-guide/)

#### Spring JPA 실무
- [Pessimistic Locking in JPA - Baeldung](https://www.baeldung.com/jpa-pessimistic-locking)
- [Testing Pessimistic Locking with Spring Boot and JPA](https://blog.mimacom.com/testing-pessimistic-locking-handling-spring-boot-jpa/)
- [Handling Pessimistic Locking with JPA on Multiple Databases](https://blog.mimacom.com/handling-pessimistic-locking-jpa-oracle-mysql-postgresql-derbi-h2/)

#### 모니터링
- [How to Fix Lock Wait Timeout in MySQL](https://severalnines.com/blog/how-fix-lock-wait-timeout-exceeded-error-mysql/)
- [MySQL Performance Schema](https://dev.mysql.com/doc/mysql-perfschema-excerpt/8.0/en/performance-schema-lock-tables.html)
- [Monitoring MySQL with Prometheus and Grafana](https://shrihariharidas73.medium.com/unlocking-database-insights-monitoring-mysql-with-prometheus-and-grafana-ddd2c4f01929)
- [Monitor errors in Amazon RDS for MySQL using CloudWatch](https://aws.amazon.com/blogs/database/monitor-errors-in-amazon-aurora-mysql-and-amazon-rds-for-mysql-using-amazon-cloudwatch-and-send-notifications-using-amazon-sns/)

### 도서
- "High Performance MySQL" by Baron Schwartz et al. - Chapter 1: MySQL Architecture and Locking
- "Designing Data-Intensive Applications" by Martin Kleppmann - Chapter 7: Transactions
- "Database Internals" by Alex Petrov - Chapter 5: Transaction Processing and Recovery

---

## 8. 결론

### 주요 발견 사항

1. **Pessimistic Lock은 만능이 아니다**
   - 충돌 빈도가 높은 경우(>10%)에만 효과적
   - 동시성 요구사항이 높으면 Redis, Queue 등 대안 고려 필요

2. **운영에서 가장 중요한 것은 모니터링**
   - Lock wait time, deadlock count 실시간 추적
   - Alert 설정으로 문제를 조기 발견
   - Performance Schema 활용 (오버헤드 최소)

3. **Deadlock은 피할 수 없다**
   - Lock 순서를 일관되게 유지
   - 트랜잭션을 짧게 유지
   - Application-level retry 필수

4. **Database별 동작 차이 이해**
   - MySQL, PostgreSQL, Oracle의 lock timeout 동작이 다름
   - 프로덕션 환경과 동일한 DB로 테스트
   - Native query 사용 고려

5. **Trade-off를 이해하고 선택**
   - 정합성 vs 성능
   - 단순성 vs 확장성
   - 비즈니스 요구사항에 맞는 전략 선택

### Next Steps

Sprint 6의 다음 태스크에서는:
- US-6.2: 이번 연구 결과를 바탕으로 Pessimistic Lock 구현
- US-6.3: k6 부하 테스트로 성능 측정
- US-6.4: 운영 관점의 모니터링 대시보드 구성

이번 연구를 통해 확보한 Best Practices를 실제 구현에 반영하여, 운영 환경에서도 안정적으로 동작하는 시스템을 구축하겠습니다.
