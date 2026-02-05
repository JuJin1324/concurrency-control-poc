# [Ops] Redis Distributed Lock 운영 가이드 (Hub)

**작성일:** 2026-02-03
**Update:** Hub & Spoke 구조 적용
**목적:** Redis 분산 락의 올바른 사용법, 인프라 토폴로지별 트레이드오프, 그리고 장애 대응 전략을 집대성한 가이드.

---

## 1. Executive Summary (3줄 요약)
1.  **정의:** Redis 분산 락은 분산 환경에서 여러 WAS가 동일 자원에 접근할 때 사용하는 **"문지기(Throttling)"** 도구다.
2.  **가치:** DB 락 대비 압도적으로 빠른 성능과 낮은 비용으로 **"DB 커넥션 풀"**을 보호하고 전체 시스템 처리량을 극대화한다.
3.  **위험:** 네트워크 복제 지연(Split-brain)이나 클라이언트 정지(GC) 시 **락 유실 가능성**이 존재하므로, DB 정합성 수단을 반드시 병행해야 한다.

---

## 2. Decision Framework (의사결정 기준)

| 구분 | **Use Case (권장)** | **Anti-Pattern (금지)** |
| :--- | :--- | :--- |
| **상황** | 선착순 쿠폰, 인기 상품 재고, 중복 결제 방지 | 수 분 이상의 장기 트랜잭션(배차), 100% 정합성 보장 필수 금융 |
| **목적** | **DB 보호 및 고속 처리** | **무결성 사수 (단독 사용 시)** |
| **대안** | Pessimistic Lock (고경합 시) | Zookeeper, Etcd (CP 보장 필요 시) |

---

## 3. Detailed Guides (상세 가이드)

이 문서는 4개의 심화 가이드로 구성되어 있습니다. 각 주제별로 상세 내용을 확인하세요.

### 🏛️ [Architecture Patterns](./redis/architecture.md)
*   **배민:** Lock-free Pattern(SADD)으로 초고부하를 견디는 **가성비 전략**.
*   **토스:** 무거운 Redisson 대신 Lettuce를 튜닝하고 **Cluster Sharding**으로 확장성을 확보한 사례.
*   **쿠팡:** Redis가 SPOF가 되었을 때의 장애 회고와 **서킷 브레이커** 생존 전략.
*   **그랩/우버:** 락의 한계(TTL)를 넘어 **워크플로우 엔진(State Machine)**으로 진화.

### ⚙️ [Internal Mechanics](./redis/mechanics.md)
*   **Mechanics:** 스핀 락(Lettuce)의 CPU 낭비 vs Pub/Sub(Redisson)의 효율성 비교.
*   **Redlock Debate:** "분산 환경에서 시간은 믿을 수 없다"는 Martin Kleppmann의 비판과 실무적 대안.
*   **Atomicity:** `GET`과 `DEL` 사이의 틈을 메우는 **Lua Script**의 마법.

### 🚨 [Troubleshooting & Checklists](./redis/troubleshooting.md)
*   **Lock Leaks:** 좀비 락을 방지하는 **Watchdog** 패턴과 필수 체크리스트.
*   **GC Pause:** 클라이언트가 멈춘 사이 발생하는 데이터 오염을 막는 **Fencing Token**.
*   **Fallback:** Redis 장애 시 DB 락으로 우회하는 **3단계 방어 전략**.

### 💻 [Implementation Guide](./redis/implementation.md)
*   **Redisson:** `leaseTime = -1` (Watchdog) 설정의 중요성과 표준 템플릿.
*   **Lettuce:** Lua Script를 활용한 **Safe Unlock** 직접 구현하기.
*   **Lua Pattern:** 락 없이 재고를 차감하는 **Atomic Check & Decrease** 스크립트.

---

## 4. Final Thoughts

> **"Redis 분산 락은 DB 락의 대체재가 아니라 '방패'다."**

Redis 락은 DB를 보호하기 위한 훌륭한 문지기이지만, 분산 시스템의 특성상 100% 완벽할 수는 없다. **"Redis는 빠르게 막고, DB는 정확하게 지킨다"**는 하이브리드 정신이 안정적인 운영의 핵심이다.