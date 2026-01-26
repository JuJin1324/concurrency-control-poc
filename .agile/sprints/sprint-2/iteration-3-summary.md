# Iteration 3 Summary: API 통합 및 테스트 가이드

**Sprint:** Sprint 2 - Redis 구현
**Iteration:** 3/3
**완료일:** 2026-01-26
**상태:** 🔄 진행 중 (API 통합 완료, 심층 문서 작성 예정)

---

## 구현 내용

### 1. StockController 확장

**통합된 Endpoint:**
`POST /api/stock/decrease`

**Parameter:**
- `method` (Query Param): 동시성 제어 방식 선택
  - `pessimistic`: MySQL `SELECT ... FOR UPDATE`
  - `optimistic`: JPA `@Version`
  - `redis-lock`: Redisson Distributed Lock
  - `lua-script`: Redis Lua Script (Atomic)

**Request Body:**
```json
{
  "stockId": 1,
  "amount": 1
}
```

---

## 사용자 테스트 가이드 (How to Test)

터미널에서 `curl` 명령어를 사용하여 4가지 방식을 직접 테스트해볼 수 있습니다.

### 1. 사전 준비
서버와 인프라가 실행 중이어야 합니다.
```bash
make up   # MySQL, Redis 실행
./gradlew bootRun # Spring Boot 애플리케이션 실행
```

### 2. 데이터 초기화
프로젝트에서 제공하는 `Makefile` 명령어를 사용하여 간편하게 데이터를 초기화할 수 있습니다.
```bash
make reset-db      # MySQL stock 테이블 초기화 (id=1, qty=100)
make reset-redis   # Redis stock:1 초기화 (qty=100) -> Lua Script 테스트용
```

### 3. API 호출 예시

#### Case 1: Pessimistic Lock (MySQL 비관적 락)
```bash
curl -X POST "http://localhost:8080/api/stock/decrease?method=pessimistic" \
     -H "Content-Type: application/json" \
     -d '{"stockId": 1, "amount": 1}'
```

#### Case 2: Optimistic Lock (MySQL 낙관적 락)
```bash
curl -X POST "http://localhost:8080/api/stock/decrease?method=optimistic" \
     -H "Content-Type: application/json" \
     -d '{"stockId": 1, "amount": 1}'
```

#### Case 3: Redis Distributed Lock (Redisson)
```bash
curl -X POST "http://localhost:8080/api/stock/decrease?method=redis-lock" \
     -H "Content-Type: application/json" \
     -d '{"stockId": 1, "amount": 1}'
```

#### Case 4: Redis Lua Script (초고속 모드) 🚀
**주의:** Lua Script 방식은 Redis를 Source of Truth로 사용하므로 `make reset-redis`가 선행되어야 합니다.
```bash
# Redis 데이터 초기화 (필수)
make reset-redis

# API 호출
curl -X POST "http://localhost:8080/api/stock/decrease?method=lua-script" \
     -H "Content-Type: application/json" \
     -d '{"stockId": 1, "amount": 1}'
```

---

## 검증 방법

### 1. DB 재고 조회 (MySQL)
```bash
make show-db
```

### 2. Redis 재고 조회
```bash
make show-redis
```

### 3. 로그 확인
애플리케이션 로그에서 각 서비스가 호출되었는지 확인합니다.
- `PessimisticLockStockService`
- `LuaScriptStockService` 등 클래스명 확인

---

## 4. 최종 성능 비교 (Benchmark Result)

### 테스트 환경 및 방법 (Methodology)
결과의 신뢰성을 위해 다음과 같은 조건에서 테스트를 수행했습니다.

*   **Hardware:** Local Environment (Mac OS, Docker Desktop)
*   **Infrastructure:**
    *   MySQL 8.0 (Container)
    *   Redis 7.0 (Container)
*   **Test Code:** `StockServiceBenchmarkTest.java` (JUnit 5)
    *   **Concurrency:** `ExecutorService` (FixedThreadPool 32) + `CountDownLatch` 사용
    *   **Traffic:** 100 Concurrent Threads (동시 요청 100개)
    *   **Measurement:** `System.currentTimeMillis()`로 전체 작업 완료 시간(Total Duration) 측정
    *   **Reset:** 각 테스트 수행 전 `StockRepository.save()` 및 Redis 초기화로 동일 조건 보장

### 결과 요약 (Result)

| 순위 | 방식 | 실행 시간 (Total) | Success | Fail | 특징 및 분석 |
|:---:|:---|:---:|:---:|:---:|:---:|
| **1** | **Redis Lua Script** 🚀 | **33 ms** | **100** | 0 | **압도적 성능.** Lock-Free & 비동기 아키텍처의 승리. |
| 2 | Pessimistic Lock | 318 ms | 100 | 0 | **가장 안정적.** 단일 DB 환경에서는 네트워크 비용이 적어 Redis Lock보다 빠름. |
| 3 | Redis Dist. Lock | 512 ms | 100 | 0 | 네트워크 왕복(RTT) 비용 발생. 분산 환경 확장에 유리함. |
| 4 | Optimistic Lock | 6,723 ms | 93 | 7 | **고트래픽 부적합.** 충돌(Conflict)이 잦아 재시도 비용이 매우 큼. |

### 분석 요약
1.  **Lua Script의 압승:** DB 트랜잭션을 기다리지 않고 메모리에서 처리하는 방식이 타 방식 대비 **약 10배 이상** 빠릅니다.
2.  **Pessimistic vs Redis Lock:** 로컬 단일 DB 환경에서는 DB Lock이 더 효율적이었습니다. 하지만 서버가 여러 대이거나 DB 부하를 줄여야 한다면 Redis Lock이 유효합니다.
3.  **Optimistic Lock의 한계:** 선착순 이벤트처럼 경합이 심한 곳에서는 절대 사용하면 안 됩니다.

---

## 다음 단계 (Sprint 3 예고)
**"Visualization & Refactoring"**
- 이번 스프린트에서 얻은 인사이트를 바탕으로 아키텍처 다이어그램(C4 Model)을 현행화합니다.
- `comparison-report.md`를 정식 문서로 작성합니다.

