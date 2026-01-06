# ADR-001: 왜 이 4가지 동시성 제어 방법을 비교하는가?

**날짜:** 2026-01-06
**상태:** Accepted

## Context

동시성 제어 방법은 매우 다양합니다:
- DB Lock (Pessimistic, Optimistic, Row Lock, Table Lock 등)
- 분산 Lock (Redis, Zookeeper, etcd 등)
- 메시지 큐 기반 직렬화 (Kafka, RabbitMQ)
- Application Level Lock (Synchronized, ReentrantLock)
- Atomic 연산 (CAS, INCR/DECR)

모든 방법을 다루면 범위가 너무 넓어지고, 각각을 얕게 다루게 되어 전문성을 증명하기 어렵습니다.

**문제:**
- 어떤 방법을 선택해야 이직용 포트폴리오로 효과적인가?
- 실무에서 가장 많이 사용하는 방법은 무엇인가?
- 비교 가능한 정량 지표를 수집할 수 있는 방법은?

## Decision

다음 4가지 방법으로 범위를 제한합니다:

### 1. Pessimistic Lock (DB 비관적 락)
- **이유:** 가장 전통적이고 널리 사용되는 방법
- **적용:** 금융권, 커머스에서 강한 정합성 필요 시
- **특징:** 100% 정합성 보장, Lock Contention 높음

### 2. Optimistic Lock (DB 낙관적 락)
- **이유:** Pessimistic의 대안, 충돌이 적을 때 효율적
- **적용:** 충돌 빈도가 낮고 Retry 가능한 시나리오
- **특징:** Version 컬럼 사용, Retry 로직 필요

### 3. Redis Distributed Lock (분산 락)
- **이유:** 분산 환경에서 필수, DB와 독립적
- **적용:** MSA 환경, 멀티 인스턴스 배포
- **특징:** Redisson 라이브러리, TTL로 Deadlock 방지

### 4. Redis Lua Script (원자적 연산)
- **이유:** 최고 성능, Lock 없이 원자성 보장
- **적용:** 초고속 처리 필요, DB 동기화 불필요한 경우
- **특징:** Redis 내에서 모든 연산 완료, 가장 높은 TPS

## Consequences

### 긍정적 영향
- ✅ **실무 적용 가능:** 4가지 모두 실무에서 자주 사용
- ✅ **비교 가능성:** 동일 조건에서 TPS, Latency, Success Rate 측정
- ✅ **깊이 있는 분석:** 각 방법의 Trade-off 명확히 이해
- ✅ **블로그 포스팅:** "재고 차감 동시성 제어 4가지 방법 성능 비교" 작성 가능
- ✅ **면접 어필:** 정량 데이터 기반 기술 선택 근거 설명 가능

### 부정적 영향
- ⚠️ **다른 방법 제외:** Zookeeper, Kafka 기반 방법은 다루지 않음
  - **완화:** 확장 가능성 유지 (Sprint 5에서 선택 가능)
- ⚠️ **범위 제한:** Application Level Lock은 제외
  - **완화:** 분산 환경에서는 부적합하므로 제외가 합리적

### 예상 결과

| Method            | TPS   | p95 Latency | Success Rate | 적합한 상황 |
|-------------------|-------|-------------|--------------|------------|
| Pessimistic Lock  | 1,200 | 85ms        | 100%         | 강한 정합성 필요 |
| Optimistic Lock   | 3,500 | 45ms        | 92%          | 충돌 적고 재시도 가능 |
| Redis Lock        | 5,000 | 30ms        | 100%         | 분산 환경, 빠른 처리 |
| Lua Script        | 8,000 | 20ms        | 100%         | 초고속, DB 동기화 불필요 |

## 참고

- 타겟 회사 기술 블로그 분석 결과: 네이버, 쿠팡, 토스 모두 동시성 제어 핵심 기술 사용
- brainstorm.md: "동시성 제어가 핵심!" (타겟 회사 공통점)
- 뱅크샐러드: "데이터 정합성 + 동시성" (돈이 걸려있음)
