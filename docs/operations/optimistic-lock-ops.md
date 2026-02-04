# [Ops] Optimistic Lock 운영 가이드 (Hub)

**작성일:** 2026-02-03
**Update:** Initial Hub & Spoke
**목적:** 낙관적 락(Optimistic Lock)의 올바른 사용법, 재시도 폭풍 방지 전략, 그리고 사용자 경험(UX) 최적화 노하우를 집대성한 가이드.

---

## 1. Executive Summary (3줄 요약)
1.  **정의:** 낙관적 락은 "락(Lock)"이 아니라 **"충돌 감지(Conflict Detection)"** 시스템이다.
2.  **가치:** 데이터베이스 락을 걸지 않아(Lock-free) **동시성(Concurrency)과 처리량(Throughput)**을 극대화한다.
3.  **위험:** 충돌이 잦은 환경(Hotspot)에서는 **재시도 폭풍(Retry Storm)**을 일으켜 DB를 파괴할 수 있다.

---

## 2. Decision Framework (의사결정 기준)

| 구분 | **Use Case (권장)** | **Anti-Pattern (금지)** |
| :--- | :--- | :--- |
| **상황** | 위키/문서 편집, 일반 상품 재고, 프로필 수정 | 선착순 이벤트, 티켓팅, 실시간 주식 거래 |
| **목적** | **High Throughput** (처리량 중시) | **Strong Consistency** (순서 보장 필수) |
| **대안** | Pessimistic Lock (충돌이 잦을 때) | Redis Queue, Kafka |

---

## 3. Detailed Guides (상세 가이드)

이 문서는 4개의 심화 가이드로 구성되어 있습니다. 각 주제별로 상세 내용을 확인하세요.

### 🏛️ [Architecture Patterns](./optimistic/architecture.md)
*   **Airbnb:** 재시도 폭풍을 막기 위한 **Soft Holds (Redis)** 하이브리드 전략.
*   **Notion:** 페이지 단위가 아닌 **Block 단위 락킹**으로 충돌 확률을 0에 가깝게 낮춘 비결.
*   **GitHub:** 무중단 스키마 마이그레이션을 위한 **Optimistic Verification**.

### ⚙️ [Mechanics & Internals](./optimistic/mechanics.md)
*   **CAS (Compare-And-Swap):** 하드웨어 원리를 SQL로 구현한 `WHERE version = ?`.
*   **Lost Update:** 갱신 손실을 어떻게 막는가?
*   **ABA Problem:** 단순 값 비교가 아닌 **Version(단조 증가)**이 필요한 이유.

### 🚨 [Troubleshooting & Checklists](./optimistic/troubleshooting.md)
*   **Retry Strategy:** `while(true)`는 금지. **Exponential Backoff**와 **Jitter(무작위 대기)** 필수.
*   **Monitoring:** 충돌률(**Failure Rate**)이 **1~5%**를 넘으면 아키텍처를 재검토해야 한다.
*   **UX:** Silent Retry vs Auto-Merge vs User Intervention (사용자에게 알리기).

### 💻 [Implementation Guide](./optimistic/implementation.md)
*   **JPA:** `@Version` 어노테이션의 동작 원리.
*   **AOP Retry:** 비즈니스 로직을 더럽히지 않고 깔끔하게 재시도를 구현하는 **Aspect** 코드 예시.

---

## 4. Final Thoughts

> **"낙관적 락은 '충돌이 예외적인 상황'일 때만 낙관적이다."**

충돌이 일상이 되는 순간, 낙관적 락은 그 어떤 비관적 락보다 더 비관적인 결과를 초래한다. **"언제 포기하고 락을 걸 것인가(Pivot)"**를 아는 것이 운영의 핵심이다.
