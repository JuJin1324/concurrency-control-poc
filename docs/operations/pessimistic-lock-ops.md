# [Ops] Pessimistic Lock 운영 가이드 (Hub)

**작성일:** 2026-02-03
**Update:** Hub & Spoke 구조 적용
**목적:** 비관적 락(Pessimistic Lock)의 올바른 사용법과 아키텍처 패턴, 운영 노하우를 집대성한 가이드.

---

## 1. Executive Summary (3줄 요약)
1.  **정의:** 비관적 락은 **"데이터 정합성의 최후 보루"**이자 **"가장 비싼 줄 세우기(Queueing)"** 도구다.
2.  **역할:** 현대 아키텍처(MSA)에서는 Redis 분산 락 뒤에 배치되어, **물리적 데이터 변경을 확정하는 찰나의 순간**에만 사용해야 한다.
3.  **위험:** 잘못 사용하면(Gap Lock, Long Transaction) **DB 커넥션 풀을 고갈**시켜 전체 서비스를 마비시킨다.

---

## 2. Decision Framework (의사결정 기준)

| 구분 | **Use Case (권장)** | **Anti-Pattern (금지)** |
| :--- | :--- | :--- |
| **상황** | 금융 원장, 재고 차감, 선착순 상태 변경 | 단순 조회, 통계/리포트, 대규모 핫딜 이벤트(단독 사용 시) |
| **목적** | **Strong Consistency** (데이터 무결성) | **Read-Only** (단순 읽기) |
| **대안** | 대체 불가 (Core Logic) | MVCC (Snapshot Read), Redis Caching |

---

## 3. Detailed Guides (상세 가이드)

이 문서는 4개의 심화 가이드로 구성되어 있습니다. 각 주제별로 상세 내용을 확인하세요.

### 🏛️ [Architecture Patterns](./pessimistic/architecture.md)
*   **금융권:** Redis + DB 락을 섞어 쓰는 **계층형 방어(Layered Defense)** 전략.
*   **이커머스:** 인기 상품(Hot Item)과 일반 상품을 나누는 **하이브리드 전략**.
*   **티켓팅:** `SKIP LOCKED`를 활용한 **고속 선점** 처리.
*   **핀테크:** 락을 아예 없애버린 **뱅크샐러드(Lock-free)** 사례.

### ⚙️ [DB Internals Deep Dive](./pessimistic/db-internals.md)
*   **MVCC와 락의 관계:** Eventual Consistency vs Strong Consistency.
*   **MySQL:** 인덱스 기반 락킹과 **Gap Lock**의 공포.
*   **PostgreSQL:** `FOR NO KEY UPDATE` 등 세분화된 락 모드 활용법.

### 🚨 [Troubleshooting & Checklists](./pessimistic/troubleshooting.md)
*   **Deadlock:** FK, 인덱스 부재로 인한 데드락 원인과 해결책(정렬).
*   **Timeout:** "50초는 영원이다". **Fail-Fast (1~3초)** 전략.
*   **Monitoring:** 평균의 함정을 피하는 **P99 지표** 모니터링.
*   **War Game:** Flash Sale, Long-Running Transaction 장애 대응 시나리오.

### 💻 [Implementation Guide](./pessimistic/implementation.md)
*   **JPA 타협:** Native Query와 Flush를 활용한 제어권 회복.
*   **DDD 전략:** 도메인 로직을 오염시키지 않고 **Persistence Adapter**에 락을 숨기는 법.

---

## 4. Final Thoughts

> **"비관적 락은 속도를 위한 가속 페달이 아니라, 데이터 정합성을 위한 브레이크다."**

이 브레이크가 시스템 전체를 멈추게 하지 않으려면, **"짧게 잡고(Short Scope), 빨리 놓고(Fail-Fast), 줄을 잘 세우는(Redis/Queue)"** 지혜가 필요하다.