# [Deep Dive] Optimistic Lock Troubleshooting & Checklists

**Parent Document:** [Optimistic Lock 운영 가이드](../optimistic-lock-ops.md)

낙관적 락 운영의 핵심은 **"충돌(Conflict)을 어떻게 관리할 것인가"**입니다. 충돌은 에러가 아니라 **예상된 비즈니스 흐름**으로 취급되어야 합니다.

---

## 1. 재시도 전략 (Retry Strategy): "무작정 다시 하지 마라"

단순 `while(true)` 루프는 시스템을 자가 파괴(Self-destruction)하게 만듭니다. **Thundering Herd(양떼 효과)**를 막기 위해 과학적인 접근이 필요합니다.

### 1.1 Exponential Backoff (지수 백오프)
실패 횟수가 늘어날수록 대기 시간을 배수로 늘립니다.
*   1회차: 100ms
*   2회차: 200ms
*   3회차: 400ms

### 1.2 Jitter (지터): 무작위성의 마법
모든 클라이언트가 똑같이 100ms 뒤에 재시도하면, 100ms 뒤에 또 충돌합니다. (Lockstep 현상)
*   **전략:** `wait_time = min(cap, base * 2^attempt) + random_between(0, base)`
*   **효과:** 요청을 시간 축상에 고르게 퍼뜨려(Spread out) DB 부하를 분산시킵니다. **이것이 없으면 낙관적 락은 위험합니다.**

---

## 2. 모니터링: "충돌률을 감시하라"

낙관적 락은 **성공보다 실패(충돌) 정보가 더 중요**합니다.

### 2.1 핵심 지표 (Metrics)
*   **`optimistic_lock_failure_rate`:** 전체 요청 대비 충돌 비율.
    *   **Alert:** **1~5%**를 넘어가면 비관적 락이나 큐 도입을 고려해야 하는 **전환점(Pivot Point)**입니다.
*   **`retry_count_distribution`:** 성공하기까지 몇 번 재시도했는가?
    *   P99가 3회 이상이라면, 사용자 경험이 심각하게 저해되고 있다는 신호입니다.

---

## 3. UX 처리 방안 (Conflict Psychology)

기술적 재시도만으로는 해결되지 않는 "논리적 충돌"은 사용자에게 선택권을 줘야 합니다.

### 3.1 Silent Retry (투명한 처리)
*   **대상:** 내부 카운터, 로그성 데이터.
*   **동작:** 사용자는 모르게 서버가 알아서 재시도하여 성공시킴.

### 3.2 Auto-Merge (자동 병합)
*   **대상:** Notion/Wiki의 서로 다른 블록 수정.
*   **동작:** 최신 버전을 다시 읽어와서(`re-read`), 내 변경 사항(Delta)만 다시 적용(`re-apply`). 충돌이 없으면 저장.

### 3.3 User Intervention (사용자 개입)
*   **대상:** 티켓팅, 동일 텍스트 동시 수정.
*   **동작:** "죄송합니다. 다른 사용자가 먼저 예약했습니다." 팝업 노출. 사용자가 포기하거나 다른 좌석을 고르게 유도.
