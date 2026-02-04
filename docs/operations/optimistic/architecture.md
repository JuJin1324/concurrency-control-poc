# [Deep Dive] Optimistic Lock Architecture Patterns

**Parent Document:** [Optimistic Lock 운영 가이드](../optimistic-lock-ops.md)

낙관적 락은 "충돌은 드물다"는 가정하에 출발하지만, 실제 대규모 환경에서는 그 가정이 깨질 때 발생하는 **재시도 폭풍(Retry Storm)**이 가장 큰 아키텍처적 난제입니다. 이를 극복한 글로벌 기업들의 사례를 분석합니다.

---

## 1. 숙박 예약: Airbnb (The Lesson of High Contention)
**핵심 교훈:** 고경합(High Contention) 상황에서 순수 낙관적 락은 시스템을 파괴한다.

### 1.1 초기 문제: 재시도 폭풍 (Retry Storm)
*   **상황:** 인기 숙소 예약 시 수천 명이 동시에 결제 시도.
*   **동작:**
    1.  1,000명이 읽음 (Version 1).
    2.  1명이 수정 성공 (Version 2).
    3.  999명이 충돌 발생(`OptimisticLockException`) -> **전원 즉시 재시도**.
    4.  999명 중 1명 성공 -> 998명 재시도...
*   **결과:** DB CPU가 기하급수적으로 상승하고, 성공해야 할 요청조차 타임아웃으로 실패. (이론적으로 비용은 $O(N^2)$로 증가)

### 1.2 해결책: Soft Holds (방파제 전략)
*   **Redis 도입:** DB 낙관적 락으로 가기 전에, Redis에서 먼저 **임시 점유(Soft Hold)**를 획득하게 함.
*   **동작:** `SET resource:id user:id NX EX 300` (300초 점유)
*   **효과:** Redis 락을 획득한 소수만 DB 트랜잭션에 진입하므로, DB 레벨의 충돌 비용을 획기적으로 낮춤. (하이브리드 전략)

---

## 2. 협업 도구: Notion (Granularity Strategy)
**핵심 교훈:** 락의 범위(Granularity)를 쪼개면 충돌 확률이 낮아진다.

### 2.1 문제 정의: 동시 편집
*   여러 사용자가 같은 '페이지(Page)'를 동시에 보고 수정함.
*   페이지 전체에 버전을 걸면(Page-level locking), 상단을 고치는 A와 하단을 고치는 B가 충돌하여 저장이 안 됨.

### 2.2 해결책: Block-level Locking
*   **전략:** 페이지를 수천 개의 **'블록(Block)'** 단위로 쪼개고, 각 블록마다 별도의 `version`을 가짐.
*   **효과:**
    *   A는 `Block 1`의 버전을 올리고, B는 `Block 99`의 버전을 올림.
    *   서로 다른 Row를 수정하는 것이므로 **충돌률 0%**.
    *   극단적인 동시성(Real-time Collaboration)을 물리적 충돌 없이 구현.

---

## 3. 플랫폼: GitHub (Online Schema Migration)
**핵심 교훈:** 서비스 중단 없는 대규모 데이터 이동의 핵심 기술. `updated_at`을 버전으로 활용한 낙관적 검증.

### 3.1 문제: 달리는 자동차의 바퀴 갈아 끼우기 (Downtime)
*   **상황:** 10억 건의 데이터가 있는 `User` 테이블에 컬럼을 추가하거나 데이터 타입을 변경해야 함.
*   **전통적 방식:** `ALTER TABLE` 명령은 테이블 전체에 락(Table Lock)을 걸어 수 시간 동안 서비스 중단을 야기함.

### 3.2 해결책: gh-ost(GitHub Online Schema Transmogrifier) 아키텍처
GitHub는 테이블을 직접 수정하지 않고, **낙관적 검증을 통한 데이터 복제** 방식을 사용함.

1.  **Ghost Table 생성:** 원본과 동일하지만 스키마가 변경된 새로운 테이블(`User_New`)을 생성.
2.  **Chunk 복사:** 원본 테이블에서 데이터를 일정 범위(예: 1,000건)씩 쪼개어 새 테이블로 복사. 이 과정은 락 없이 수행됨.
3.  **Binlog 스트리밍:** 복사가 진행되는 동안 유저가 발생시킨 변경 사항(INSERT/UPDATE/DELETE)을 별도의 로그(Binlog)로 실시간 수집.
4.  **Optimistic Verification (Copy-and-Verify):**
    *   **Version 식별:** 원본 로우의 `updated_at` 타임스탬프를 **버전(Version)**으로 간주.
    *   **검증:** 데이터를 복사하여 `User_New`에 넣기 직전, 다시 한번 원본의 `updated_at`을 확인.
    *   **조치:** "내가 읽어온 시점보다 `updated_at`이 더 최신이라면?", 그 사이 유저가 데이터를 수정한 것이므로 복사를 중단하고 최신 데이터를 다시 읽어와서 복제함.
5.  **결과:** 단 1초의 서비스 중단 없이 10억 건의 데이터 이동과 스키마 변경을 완벽하게 완료.

---

## 4. 예약 플랫폼: Booking.com (Hybrid Approach)
**핵심 교훈:** 읽기는 낙관적으로, 쓰기는 비관적으로.

*   **검색(Read):** **낙관적 읽기(No Lock).** "방 있어요?" 물어볼 때는 락 없이 스냅샷을 보여줌. (0.1초 전에는 있었지만 지금은 없을 수도 있음 - 허용)
*   **결제(Write):** "예약하기" 버튼을 누르는 순간 **비관적 락(Pessimistic Lock)** 전환.
*   **이유:** 결제 단계에서의 실패는 고객 이탈(Conversion Drop)로 직결되므로, 여기서는 성능보다 **확실한 선점**을 우선시함.

---

## 5. 부모-자식 관계의 락킹 전략 (Aggregate Locking)
자식 엔티티(Child)가 수정될 때 부모 엔티티(Parent)의 버전을 올릴 것인가? 이는 **동시성**과 **정합성** 사이의 트레이드오프입니다.

### 5.1 전파 전략 (Propagation - DDD Style)
*   **동작:** 자식(`OrderItem`)이 추가/수정되면 부모(`Order`)의 버전도 강제로 증가시킴. (`OPTIMISTIC_FORCE_INCREMENT`)
*   **이유:** 자식의 변경이 부모의 불변식(Invariant, 예: 주문 총액)에 영향을 줄 때. 애그리거트 전체의 일관성을 강력하게 보장.
*   **단점:** 자식 A를 수정하는 동안 자식 B를 수정하려는 트랜잭션도 충돌 발생 (동시성 저하).

### 5.2 독립 전략 (Independence - High Concurrency)
*   **동작:** 자식(`Comment`)이 추가되어도 부모(`Post`)의 버전은 그대로 둠.
*   **이유:** 자식의 변경이 부모의 상태와 무관할 때. (예: 게시글 내용과 댓글은 별개)
*   **장점:** 부모 락 경합 없이 여러 사용자가 동시에 댓글을 달 수 있음. (Notion의 Block 방식과 유사)