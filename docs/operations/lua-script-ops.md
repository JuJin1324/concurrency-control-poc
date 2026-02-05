# [Ops] Redis Lua Script 운영 가이드 (Hub)

**작성일:** 2026-02-03
**Update:** Hub & Spoke 구조 적용 (심화 인사이트 반영)
**목적:** Redis Lua Script를 단순한 스크립트가 아닌 **"고성능 원자적 연산 엔진"**으로 활용하기 위한 아키텍처 패턴과 운영 노하우 집대성.

---

## 1. Executive Summary (3줄 요약)
1.  **정의:** Redis 내부에서 **원자적(Atomic)**으로 실행되는 사용자 정의 명령어다. 락(Lock) 없이도 동시성을 제어하는 가장 강력한 무기다.
2.  **가치:** 네트워크 왕복(RTT)을 최소화하고, DB 병목을 제거하여 **초당 수십만 TPS**를 처리할 수 있다.
3.  **위험:** 실행 중 Redis 전체가 멈추는 **Blocking** 특성과, 슬레이브에서 재실행되는 **복제 메커니즘**을 깊이 이해하고 써야 한다.

---

## 2. Decision Framework (의사결정 기준)

| 구분 | **Use Case (권장)** | **Anti-Pattern (금지)** |
| :--- | :--- | :--- |
| **상황** | 선착순 이벤트, 재고 차감, Rate Limiter | 긴 비즈니스 로직, 외부 API 호출, 대량 데이터 조회 |
| **목적** | **Extreme Performance** (Lock-free) | **Complex Transaction** (복잡한 롤백 필요 시) |
| **대안** | Redis Distributed Lock (복잡도 낮음) | Redis Functions (Redis 7.0+) |

---

## 3. Detailed Guides (상세 가이드)

이 문서는 4개의 심화 가이드로 구성되어 있습니다. 각 주제별로 상세 내용을 확인하세요.

### 🏛️ [Architecture Patterns](./lua/architecture.md)
*   **성능의 경제학:** Redis로 번 성능을 **신뢰(정합성)**에 투자할 것인가, **경험(UX)**에 투자할 것인가에 대한 분석.
*   **Sync Strategies:** Synchronous(티켓링크) vs Asynchronous(배민) vs Micro-Batch(집계) 전략 비교.
*   **Reconciliation:** 비동기 방식의 데이터 불일치를 바로잡는 **SSCAN(Non-blocking)** 기반 정합성 보정.

### ⚙️ [Internal Mechanics](./lua/mechanics.md)
*   **Event Loop:** 싱글 스레드가 보장하는 **"Stop the World"** 원자성 원리.
*   **Replication Safety:** 슬레이브에서 코드를 **재실행(Re-execution)**하기 때문에 **순수 함수(Pure Function)**가 강제되는 이유.
*   **Optimization:** `SCRIPT LOAD`와 JIT 컴파일을 통한 CPU 및 네트워크 최적화.

### 🚨 [Troubleshooting & Checklists](./lua/troubleshooting.md)
*   **Physical Limits:** CPU 1코어의 물리적 한계와, 샤딩으로도 해결 안 되는 **Hot Key** 문제 및 해결책(Key Splitting).
*   **Bulkhead Pattern:** 장애 전파를 막기 위해 Lua 전용 Redis를 물리적으로 분리하는 **격벽(Bulkhead)** 전략.
*   **NOSCRIPT Recovery:** Redis 재시작 시 사라진 스크립트를 클라이언트 레벨에서 **자동 복구**하는 패턴.

### 💻 [Implementation Guide](./lua/implementation.md)
*   **Spring Support:** 굳이 직접 짤 필요 없는 `DefaultRedisScript`의 최적화 기능 활용법.
*   **Set Glossary:** **SISMEMBER**(포함여부), **SADD**(추가), **SCARD**(집합 크기) 등 핵심 명령어 해설.
*   **Versioning:** Git을 활용한 스크립트 형상 관리 및 배포 전략.

---

## 4. Final Thoughts

> **"Lua Script는 Redis를 연산 장치(Compute Node)로 격상시키지만, 그 대가는 운영자의 책임이다."**

잘 짜인 스크립트 하나는 서버 10대를 대체할 수 있지만, 잘못 짠 스크립트 하나는 전체 서비스를 멈추게 한다. **"결정론적으로 작성하고, 물리적으로 격리하라."**