# [Deep Dive] Pessimistic Lock Architecture Patterns

**Parent Document:** [Pessimistic Lock 운영 가이드](../pessimistic-lock-ops.md)

이 문서는 엔터프라이즈 환경에서 비관적 락을 도입할 때 참고할 수 있는 실제 기업들의 아키텍처 패턴을 심층 분석합니다. 단순한 기능 적용을 넘어, 각 기업이 직면했던 문제와 이를 해결하기 위한 구조적 의사결정 과정을 상세히 기술합니다.

---

## 1. 금융권 사례: 토스증권 (Toss Securities)
**핵심 전략:** 계층형 방어 전략 (Layered Defense Strategy)

### 1.1 도입 배경 및 아키텍처적 난제
*   **도메인:** 해외 주식 결제 및 원장 관리 시스템.
*   **문제점:**
    *   **MSA 환경의 한계:** 데이터베이스가 서비스별로 파편화되어 있어 단일 DB 락만으로는 전체 트랜잭션의 정합성을 보장하기 어려움.
    *   **외부 API 지연:** 해외 브로커 망과의 통신 레이턴시(Network Latency)가 길어질 경우, DB 락을 잡은 상태로 대기하게 되면 커넥션 풀이 순식간에 고갈됨.
    *   **재시도 위험:** 단순 재시도는 중복 결제(Double Spending) 위험을 초래함.

### 1.2 구현 방식: 하이브리드 락 전략
토스증권은 순수 비관적 락의 한계를 극복하기 위해 **Redis 분산 락, DB 낙관적 락, DB 비관적 락**을 모두 사용하는 하이브리드 전략을 채택했습니다.

1.  **Level 1: Redis 분산 락 (Traffic Throttling)**
    *   **역할:** "문지기". 트래픽이 DB까지 도달하기 전에 메모리 레벨에서 대기열을 관리합니다.
    *   **구현:** 사용자 ID나 계좌 번호를 Key로 하는 Redis 락을 획득합니다. (Redisson 사용)
    *   **효과:** DB 커넥션을 점유하지 않고 대기하므로, 트래픽 폭주 시에도 DB를 보호합니다.
2.  **Level 2: DB Optimistic Lock (Safety Net)**
    *   **역할:** "안전장치". Redis 락이 TTL 만료 등으로 뚫렸을 때를 대비한 최종 검증.
    *   **구현:** JPA `@Version`을 사용하여 CAS(Compare-And-Set) 연산을 수행합니다.
    *   **효과:** 갱신 손실(Lost Update)을 원천 차단합니다.
3.  **Level 3: DB Pessimistic Lock (Final Commit)**
    *   **역할:** "최후의 보루". 실제 잔액 차감 시점에 짧고 굵게 점유하여 물리적 정합성을 보장합니다.
    *   **구현:** `SELECT ... FOR UPDATE`로 잔액을 확인하고 즉시 차감합니다.

### 1.3 외부 API 연동 패턴 (Facade Pattern)
락 보유 시간을 최소화하기 위해 트랜잭션 범위를 물리적으로 분리했습니다.

*   **Bad Pattern (Long Transaction):**
    `[TX Start] -> [DB Lock] -> [External API Call (3s)] -> [Commit & Lock Release]`
    *   **문제:** 외부 통신 3초 동안 DB 커넥션과 락을 계속 점유함.
*   **Good Pattern (Short Transaction):**
    1.  `[TX 1 Start] -> [DB Lock & Update] -> [Commit & Lock Release]` **(0.01s)**
    2.  `[External API Call]` **(3s, No DB Connection)**
    3.  `[TX 2 Start] -> [Result Update] -> [Commit]` **(0.01s)**
    *   **효과:** DB는 찰나의 순간만 일하고, 긴 대기 시간(3s)은 WAS 스레드만 사용함.

---

## 2. 핀테크 사례: 뱅크샐러드 (Banksalad)
**핵심 전략:** 락 제거 및 도메인 로직 재설계 (Lock-Free)

### 2.1 도입 배경
*   **서비스:** '일해라 김뱅샐' (자산 연동 리워드 지급).
*   **문제점:** 포인트 지급 요청이 동시에 들어올 때(따닥), 비관적 락을 쓰자니 성능 저하가 우려되고, 낙관적 락을 쓰자니 재시도(Retry) 처리가 복잡했습니다.

### 2.2 해결책: Time-delta 보상 알고리즘
기술적인 락을 도입하는 대신, **도메인 로직을 변경**하여 동시성 문제를 해결했습니다.

*   **기존 로직:** "현재 요청에 대한 포인트 10원 지급"
*   **변경 로직:** "마지막 지급 시점(`last_rewarded_at`)부터 **지금까지 쌓인 시간만큼**의 포인트를 일괄 지급"
*   **동작 원리:**
    1.  요청 A와 B가 동시에 도착.
    2.  A가 먼저 성공 (`last_rewarded_at` 갱신).
    3.  B는 실패하거나(낙관적 락), 성공하더라도 지급할 포인트가 0원이 됨 (이미 A가 갱신했으므로).
    4.  다음 요청 C가 들어오면, A 이후부터 C까지의 시간을 계산하여 지급.
*   **결과:** B 요청이 실패해도 사용자는 손해를 보지 않음(C에서 합산 지급). **결과적 정합성(Eventual Consistency)**을 통해 락 없는 고성능 시스템 구축.

---

## 3. 이커머스 사례: 재고 관리 시스템
**핵심 전략:** 상품 인기도에 따른 이원화 (Hybrid Locking)

### 3.1 초기 아키텍처의 한계
초기에는 모든 상품에 `SELECT ... FOR UPDATE`를 사용했으나, **인기 상품(Hot Item)** 런칭 시 다음과 같은 문제가 발생했습니다.
*   **Hot Spot:** 특정 Row 하나에 수천 개의 트랜잭션이 줄을 서면서 DB CPU가 100% 치솟음.
*   **Connection Pool Exhaustion:** 대기 중인 트랜잭션들이 커넥션을 잡고 놓아주지 않아 전체 서비스 마비.

### 3.2 해결책: Hybrid Locking Strategy
상품의 특성에 따라 락 전략을 다르게 가져갑니다.

| 구분 | **일반 상품 (Long Tail)** | **인기 상품 (Hot Item)** |
| :--- | :--- | :--- |
| **특징** | 하루 판매량 10개 미만, 동시 접속 거의 없음 | 초당 수천 명 접속, 재고 순삭 |
| **전략** | **Optimistic Lock** | **Redis Lua Script + Micro-Batch** |
| **이유** | 충돌 확률이 낮아 락 비용이 아까움. | DB 락으로는 감당 불가. 메모리에서 먼저 처리. |

*   **Redis Lua Script (Atomic Pre-check):**
    *   **Why:** 단순하게 `GET` 후 `DECR`를 하면, 그 찰나의 순간에 다른 요청이 끼어들어 재고가 마이너스가 될 수 있습니다.
    *   **How:** Lua Script는 Redis 내부에서 "하나의 명령어"처럼 실행됩니다. 스크립트가 도는 동안 다른 요청은 절대 끼어들 수 없어 완벽한 원자성(Atomicity)을 보장합니다.
*   **Micro-Batch Update (Scheduled Task):**
    *   **Why:** 100명의 주문자가 각각 DB 락을 잡으면, DB는 100번 멈췄다 가야 합니다.
    *   **How:** 주문을 잠시 메모리 큐(Queue)에 모아둡니다. 그리고 0.1초마다 스케줄러가 깨어나 "쌓인 주문 100개"를 합쳐서 `UPDATE stock = stock - 100` 쿼리 **단 한 번**만 날립니다. 락 경합이 1/100로 줄어듭니다.

---

## 4. 티켓팅 사례: 공연 예매 시스템
**핵심 전략:** 선점(Reservation)과 고속 처리 (`SKIP LOCKED`)

### 4.1 문제 정의
*   **Unique Item:** 재고(수량)가 아니라 **특정 좌석 ID**를 선점해야 함.
*   **UX:** 사용자가 좌석을 클릭했을 때 "이미 선택된 좌석입니다"를 **즉시** 알려줘야 함 (대기 X).

### 4.2 해결책: SKIP LOCKED & NOWAIT
대기열(Queue)을 빠르게 처리하기 위해 DB의 고급 락 기능을 활용합니다.

*   **자동 배정 로직:**
    ```sql
    -- "빈 좌석 아무거나 1개 줘. 근데 남이 보고 있는 건 기다리지 말고 건너뛰어."
    SELECT * FROM seats
    WHERE status = 'AVAILABLE'
    LIMIT 1
    FOR UPDATE SKIP LOCKED;
    ```
*   **수동 지정 로직:**
    ```sql
    -- "이 좌석 줘. 누가 잡고 있으면 바로 에러 뱉어."
    SELECT * FROM seats
    WHERE id = ?
    FOR UPDATE NOWAIT;
    ```

### 4.3 운영 노하우: 좀비 예약 청소
결제 단계에서 이탈한 사용자로 인해 `RESERVED` 상태로 굳어버린 좌석을 처리합니다.
*   **Batch Job:** 1분마다 `reserved_at < NOW() - 5min`인 좌석을 찾아 `AVAILABLE`로 원복.
*   **주의:** 이 배치 작업 역시 `FOR UPDATE`를 사용하여, 청소 도중 결제 성공 요청과 충돌하지 않도록 해야 함.

---

## 5. 글로벌 결제 시스템 (Stripe, PayPal)
**핵심 전략:** Idempotency Key와 Event Sourcing

### 5.1 Idempotency Key (멱등성 키)
*   **역할:** 중복 요청(Double Charge) 방지 및 분산 환경 락 식별자.
*   **동작:** 클라이언트가 UUID를 `Idempotency-Key` 헤더로 전송. 서버는 이 키를 PK로 하여 `Transactions` 테이블을 조회/생성.
*   **효과:** `WHERE idempotency_key = ?` 조건은 유니크하므로 **Gap Lock이 발생하지 않음**. 오직 Record Lock만 사용하여 동시성을 확보함.

### 5.2 Kafka를 통한 금융 데이터 처리
*   **OLTP DB:** "지금 당장 인출 가능한가?"를 판단하는 **비관적 락** 영역. (Hot Data)
*   **Kafka:** "돈이 빠져나갔다"는 불변의 사실(Fact)을 이벤트로 발행하여 알림, 정산 등 후속 처리를 담당. **결과적 정합성** 영역.
