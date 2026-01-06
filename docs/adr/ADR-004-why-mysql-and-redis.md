# ADR-004: 왜 MySQL과 Redis를 선택했는가?

**날짜:** 2026-01-06
**상태:** Accepted

## Context

동시성 제어 PoC를 구현하기 위해서는 다음 2가지 인프라가 필요합니다:

1. **관계형 데이터베이스:** 재고 데이터 영속화 + DB Lock 구현
2. **캐시/분산 락:** Redis Lock + Lua Script 구현

### 데이터베이스 선택지

| 옵션 | 장점 | 단점 |
|------|------|------|
| **MySQL** | - 가장 널리 사용<br/>- Lock 메커니즘 성숙<br/>- 학습 자료 풍부 | - NoSQL 대비 확장성 낮음 |
| **PostgreSQL** | - 고급 기능 (JSONB, Full-text)<br/>- MVCC 성능 우수 | - MySQL 대비 점유율 낮음 |
| **MariaDB** | - MySQL Fork<br/>- 오픈소스 친화적 | - MySQL과 크게 다르지 않음 |

### 캐시/분산 락 선택지

| 옵션 | 장점 | 단점 |
|------|------|------|
| **Redis** | - 가장 널리 사용<br/>- Lua Script 지원<br/>- Redisson 라이브러리 성숙 | - 단일 스레드 (복잡한 연산 부적합) |
| **Memcached** | - 단순하고 빠름 | - Lua Script 미지원<br/>- 분산 락 기능 약함 |
| **Hazelcast** | - Java 네이티브<br/>- 분산 락 기본 지원 | - Redis 대비 점유율 낮음 |
| **Zookeeper** | - 분산 락 전문 | - 오버엔지니어링<br/>- 셋업 복잡 |

## Decision

**MySQL 8.0 + Redis 7.0을 선택합니다.**

### MySQL 8.0 선택 근거

1. **범용성 (Universality)**
   - 전 세계 가장 널리 사용되는 RDBMS
   - 대부분의 회사가 MySQL 또는 MySQL 호환 DB 사용 (Aurora, RDS)
   - 이직 시 가장 어필하기 좋은 기술 스택

2. **Lock 메커니즘 성숙도**
   - Pessimistic Lock: `SELECT ... FOR UPDATE` (Row Lock)
   - Optimistic Lock: `@Version` 컬럼 지원 (JPA 통합 우수)
   - InnoDB 스토리지 엔진의 Lock 구현이 안정적

3. **학습 자료 및 커뮤니티**
   - Stack Overflow, 블로그 글 풍부
   - 트러블슈팅 용이
   - 면접에서 MySQL 관련 질문 빈도 높음

4. **실무 적용성**
   - 네카라쿠배 대부분 MySQL 계열 사용
   - 뱅크샐러드, 토스 등 핀테크도 MySQL 사용

### Redis 7.0 선택 근거

1. **범용성 (Universality)**
   - 가장 널리 사용되는 In-Memory 데이터베이스
   - 캐싱 + 분산 락 + Pub/Sub 등 다목적 활용

2. **Lua Script 지원**
   - Redis 내에서 원자적 연산 가능
   - `EVAL` 명령어로 복잡한 로직 구현
   - 이 PoC의 핵심 기능 중 하나 (4번째 방법)

3. **Redisson 라이브러리**
   - Spring Boot 통합 우수
   - `RLock` 인터페이스로 간편한 분산 락 구현
   - TTL, Retry 로직 기본 제공

4. **성능**
   - In-Memory 기반 초고속 처리
   - 단일 스레드지만 PoC 수준에서는 문제없음
   - TPS 비교 시 가장 높은 성능 예상

5. **실무 적용성**
   - 대부분의 회사가 Redis 사용 (캐싱 + 세션 관리)
   - 분산 환경에서 필수 기술
   - 면접에서 Redis Lock 관련 질문 빈도 높음

### 제외된 선택지

**PostgreSQL 제외 이유:**
- MySQL 대비 점유율 낮음
- Lock 메커니즘은 유사 (이 PoC에서 차별점 없음)
- 이직 시 MySQL이 더 범용적

**Zookeeper 제외 이유:**
- 분산 락 전문이지만 오버엔지니어링
- 셋업 복잡, 학습 곡선 높음
- PoC 범위에서 불필요

**Memcached 제외 이유:**
- Lua Script 미지원 (4번째 방법 구현 불가)
- 분산 락 기능 약함
- Redis가 모든 면에서 우수

## Consequences

### 긍정적 영향

1. **범용성 확보**
   - ✅ 가장 널리 사용되는 기술 스택
   - ✅ 이직 시 어필 가능
   - ✅ 면접 질문 대응 용이

2. **학습 자료 풍부**
   - ✅ 트러블슈팅 용이
   - ✅ 블로그 포스팅 시 독자층 넓음
   - ✅ 커뮤니티 지원 활발

3. **실무 적용성**
   - ✅ 타겟 회사들이 실제 사용하는 기술
   - ✅ PoC 결과를 실무에 적용 가능
   - ✅ 성능 비교 데이터의 신뢰성 높음

4. **구현 용이성**
   - ✅ Spring Boot와 통합 우수
   - ✅ JPA + Redisson 라이브러리로 간편 구현
   - ✅ Docker Compose로 로컬 환경 구축 쉬움

### 부정적 영향

1. **확장성 제한**
   - ⚠️ MySQL은 수평 확장(Sharding)이 어려움
   - **완화:** PoC 수준에서는 문제없음, 확장성은 이 프로젝트의 목표가 아님

2. **NoSQL 경험 부족**
   - ⚠️ MongoDB, DynamoDB 등 NoSQL 경험은 쌓지 못함
   - **완화:** 동시성 제어는 RDBMS가 더 적합, NoSQL은 Sprint 5에서 선택 가능

3. **PostgreSQL 고급 기능 미사용**
   - ⚠️ JSONB, Full-text Search 등 고급 기능 경험 안 됨
   - **완화:** 이 PoC의 목표는 동시성 제어, 고급 기능은 불필요

### 예상 성능

| Method            | Database | Cache/Lock | 예상 TPS |
|-------------------|----------|------------|----------|
| Pessimistic Lock  | MySQL    | -          | 1,200    |
| Optimistic Lock   | MySQL    | -          | 3,500    |
| Redis Lock        | MySQL    | Redis      | 5,000    |
| Lua Script        | -        | Redis      | 8,000    |

**근거:**
- DB Lock은 Lock Contention으로 TPS 낮음
- Redis는 In-Memory 기반으로 초고속 처리
- Lua Script는 DB 접근 없이 Redis만 사용하여 가장 빠름

## 참고

- MySQL 공식 문서: [InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- Redis 공식 문서: [Distributed Locks](https://redis.io/docs/manual/patterns/distributed-locks/)
- Redisson 문서: [Distributed Lock](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)
- brainstorm.md: 타겟 회사 기술 스택 분석
