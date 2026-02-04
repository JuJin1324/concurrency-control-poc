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
**핵심 교훈:** 서비스 중단 없는 대규모 데이터 이동의 핵심 기술.

### 3.1 문제: 무중단 마이그레이션
*   수십억 건의 데이터를 새로운 테이블/DB로 옮기는 동안(`gh-ost`), 서비스는 계속 돌아가야 함(데이터 변경 발생).

### 3.2 해결책: Optimistic Verification
*   **Double Write:** 데이터를 복사하는 동안 애플리케이션은 원본과 변경 로그를 동시에 기록.
*   **Verification:** 복사 스레드가 데이터를 타겟에 넣기 직전, 원본 데이터의 `updated_at`이나 버전을 확인.
    *   버전이 바뀌었다면? -> "복사하는 동안 유저가 수정했네?" -> 해당 건은 복사 취소하고 최신 데이터 다시 로드.
*   **결과:** Table Lock 없이 데이터 정합성을 100% 보장하며 마이그레이션 완료.

---

## 4. 예약 플랫폼: Booking.com (Hybrid Approach)
**핵심 교훈:** 읽기는 낙관적으로, 쓰기는 비관적으로.

*   **검색(Read):** **낙관적 읽기(No Lock).** "방 있어요?" 물어볼 때는 락 없이 스냅샷을 보여줌. (0.1초 전에는 있었지만 지금은 없을 수도 있음 - 허용)
*   **결제(Write):** "예약하기" 버튼을 누르는 순간 **비관적 락(Pessimistic Lock)** 전환.
*   **이유:** 결제 단계에서의 실패는 고객 이탈(Conversion Drop)로 직결되므로, 여기서는 성능보다 **확실한 선점**을 우선시함.
