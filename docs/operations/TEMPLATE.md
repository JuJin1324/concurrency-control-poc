# [Ops] {Technology Name} 운영 가이드 (Hub Template)

**작성일:** 2026-MM-DD
**Update:** Initial Draft
**목적:** {Technology Name}의 올바른 사용법과 아키텍처 패턴, 운영 노하우를 집대성한 가이드.

---

## 1. Executive Summary (3줄 요약)
1.  **정의:** 이 기술의 본질적인 정의와 역할.
2.  **가치:** 시스템에서 어떤 문제를 해결하기 위해 존재하는가?
3.  **위험:** 잘못 사용했을 때 어떤 부작용(Side Effect)이 있는가?

---

## 2. Decision Framework (의사결정 기준)

| 구분 | **Use Case (권장)** | **Anti-Pattern (금지)** |
| :--- | :--- | :--- |
| **상황** | (예: 금융 원장, 실시간 채팅) | (예: 단순 조회, 로그 수집) |
| **목적** | (예: Strong Consistency) | (예: Eventual Consistency) |
| **대안** | (예: 대체 불가) | (예: Redis, Kafka) |

---

## 3. Detailed Guides (상세 가이드)

이 문서는 N개의 심화 가이드로 구성되어 있습니다. 각 주제별로 상세 내용을 확인하세요.

### 🏛️ [Architecture Patterns](./{tech}/architecture.md)
*   **Case 1:** 도메인별 적용 사례 1.
*   **Case 2:** 도메인별 적용 사례 2.
*   **Architecture:** 시스템 구성도 및 흐름.

### ⚙️ [Internal Mechanics](./{tech}/internals.md)
*   **Principle:** 기술의 내부 동작 원리.
*   **Deep Dive:** 엔진 레벨의 특성 (예: Redis Single Thread, DB Lock Mode).

### 🚨 [Troubleshooting & Checklists](./{tech}/troubleshooting.md)
*   **Checklist:** 배포 전 확인해야 할 설정값.
*   **Monitoring:** 필수 모니터링 지표 (P99, Error Rate).
*   **War Game:** 주요 장애 시나리오 및 대응 매뉴얼.

### 💻 [Implementation Guide](./{tech}/implementation.md)
*   **Code Pattern:** 올바른 구현 코드 예시.
*   **Framework:** Spring/JPA 등 프레임워크 사용 시 주의사항.

---

## 4. Final Thoughts

> **"{Technology Name}에 대한 시니어의 통찰력 한 줄 요약"**

이 기술을 마스터하기 위한 핵심 조언이나 철학.
