# Sprint 2: Redis 구현 (Distributed Lock + Lua Script)

**기간:** 2026-01-24 ~ 2026-01-31 (7일)
**목표:** Redis 기반 동시성 제어 2가지 방법을 구현하고, 4가지 방법 모두 동일 조건에서 비교할 수 있도록 검증한다.

---

## Sprint Goal

> Redis Distributed Lock과 Lua Script를 구현하고, DB Lock과 함께 4가지 방법을 동일한 API로 호출할 수 있도록 완성한다.

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**

**핵심 원칙:**
1. 작은 단위로 반복 (인지 부하 최소화)
2. 빠른 피드백 루프 (Checkpoint로 검증)
3. 검증 후 구현 (잘못된 방향 사전 차단)

**Sprint 1 회고 반영:**
- 각 Iteration 완료 시 `iteration-N-summary.md` 파일 생성 필수
- 사용자 피드백 섹션 포함 (코드/아키텍처 수정 요청 가능)

---

## Tasks

### Iteration 1: Redis Distributed Lock 구현 ✅ 완료

#### US-2.1: Redisson 설정 및 RLock 구현 ✅
- [x] Redisson 의존성 추가 (`org.redisson:redisson-spring-boot-starter:4.1.0`)
- [x] Redisson 설정 (Spring Boot 자동 설정 사용)
- [x] RedisLockStockService 구현 (StockService 구현체)
  - `RLock.tryLock(waitTime, leaseTime, TimeUnit)` 사용
  - Lock 획득 실패 시 예외 처리
  - Lock 해제 보장 (finally 블록)
- [x] Transaction 관리 주의: Lock과 @Transactional 순서
  - TransactionTemplate 사용하여 Lock 내부에서 트랜잭션 실행

**Acceptance Criteria:** ✅ 모두 충족
- RLock으로 재고 차감 시 Lock 획득/해제가 정상 동작
- Lock 획득 실패 시 적절한 예외 발생
- 로그에서 Lock 획득/해제 확인 가능

**🔍 Checkpoint 1:** ✅ Redis Lock 동작 확인 및 로그 검증 완료

---

#### US-2.2: Redis Distributed Lock 통합 테스트 ✅
- [x] 동시성 시뮬레이션 테스트 작성
  - 100개 재고에 100개 동시 요청
  - CountDownLatch + ExecutorService 사용
- [x] 재고 정합성 검증
  - 최종 재고가 정확한지 확인 (100 → 0)
- [x] Success Rate 측정 (100% 달성)

**Acceptance Criteria:** ✅ 모두 충족
- 동시 요청 시 재고가 음수가 되지 않음
- Success Rate 측정됨: **100%**
- 통합 테스트 통과

**✅ Iteration 1 완료:** Redis Distributed Lock이 동시성 문제를 해결함

---

### Iteration 2: Redis Lua Script 구현 ✅ 완료

#### US-2.3: Redis에 재고 데이터 저장 구조 설계 ✅
- [x] Redis 재고 데이터 구조 결정
  - Key: `stock:{stockId}` → Value: `quantity` (String)
  - 또는 Hash 구조 사용 여부 결정
- [x] 초기 재고 데이터 Redis에 저장하는 로직 구현
  - 애플리케이션 시작 시 MySQL → Redis 동기화
  - 또는 API를 통한 초기화

**Acceptance Criteria:**
- Redis에 재고 데이터가 저장됨
- `make redis`로 접속하여 데이터 확인 가능

---

#### US-2.4: Lua Script 작성 및 실행 ✅
- [x] Lua Script 작성 (재고 차감 원자적 연산)
  ```lua
  -- 재고 조회
  local stock = tonumber(redis.call('GET', KEYS[1]))
  -- 재고 검증
  if stock < tonumber(ARGV[1]) then
      return -1  -- 재고 부족
  end
  -- 재고 차감
  return redis.call('DECRBY', KEYS[1], ARGV[1])
  ```
- [x] LuaScriptStockService 구현 (StockService 구현체)
  - RedisTemplate의 `execute()` 사용
  - Script 로드 및 캐싱
- [x] MySQL 동기화 전략 결정
  - 옵션 1: 동기 (매 요청마다 MySQL 업데이트)
  - 옵션 2: 비동기 (주기적 동기화)
  - 옵션 3: 동기화 안 함 (Redis를 Source of Truth로)

**Acceptance Criteria:**
- Lua Script가 원자적으로 실행됨
- 재고 부족 시 적절한 응답 반환
- Lock 없이도 정합성 보장

**🔍 Checkpoint 2:** ✅ Lua Script 동작 확인 및 원자성 검증 완료

---

#### US-2.5: Lua Script 통합 테스트 ✅
- [x] 동시성 시뮬레이션 테스트 작성
  - 100개 재고에 100개 동시 요청
- [x] 재고 정합성 검증
- [x] Success Rate 및 성능 측정 (예상: 가장 빠름)

**Acceptance Criteria:**
- 동시 요청 시 재고가 음수가 되지 않음
- Success Rate 100%
- DB Lock보다 빠른 처리 속도

**✅ Iteration 2 완료:** Lua Script가 정상 동작하고 가장 빠름

---

### Iteration 3: REST API 통합 및 4가지 방법 비교 ✅ 완료

#### US-2.6: StockController 확장 ✅
- [x] REST API 확장
  - `POST /api/stock/decrease?method=redis-lock`
  - `POST /api/stock/decrease?method=lua-script`
- [x] StockService Map에 새로운 구현체 등록
- [x] API 통합 테스트 추가

**Acceptance Criteria:**
- 4가지 method 파라미터 모두 정상 동작
- API 통합 테스트 통과

---

#### US-2.8: Redis 심층 탐구 및 아키텍처 문서화 (New) ✅
- [x] 별도 MD 파일 작성 (`docs/technology/redis-deep-dive.md`)
- [x] Redis의 역사와 목적 탐구
- [x] Single Thread 아키텍처와 원자성(Atomicity)의 관계 정리
- [x] 프로젝트 적용 분석:
  - Distributed Lock (Redisson)의 원리
  - Lua Script의 원리 (Atomic Operation)

**Acceptance Criteria:**
- Redis의 특성과 프로젝트 활용 방식이 논리적으로 정리된 문서 생성

---

#### US-2.7: 4가지 방법 예비 비교 테스트 ✅
- [x] 동일 조건에서 4가지 방법 비교
  - 100개 재고, 100개 동시 요청
  - 각 방법별 Success Rate, 처리 시간 측정
- [x] 비교 결과 문서화 (간단한 표)

**Acceptance Criteria:**
- 4가지 방법 모두 동일 조건에서 테스트됨
- 예상 순위: Lua Script > Redis Lock > Optimistic > Pessimistic

**✅ Iteration 3 완료:** 4가지 방법 모두 API로 호출 가능하고, 심층 문서 및 성능 비교 완료

---

## Sprint 2 Definition of Done

### Iteration 1: Redis Distributed Lock ✅ 완료 (2026-01-24)
- [x] Redisson 설정 완료
- [x] RedisLockStockService 구현
- [x] RLock 동작 확인 (로그)
- [x] 동시성 통합 테스트 통과 (100% Success Rate 달성)
- [x] Checkpoint 1 통과
- [x] `iteration-1-summary.md` 생성

### Iteration 2: Redis Lua Script ✅ 완료 (2026-01-26)
- [x] Redis 재고 데이터 구조 설계
- [x] Lua Script 작성 및 실행
- [x] LuaScriptStockService 구현
- [x] 동시성 통합 테스트 통과
- [x] Checkpoint 2 통과
- [x] `iteration-2-summary.md` 생성

### Iteration 3: API 통합 및 비교 ✅ 완료 (2026-01-26)
- [x] REST API 확장 (method=redis-lock, lua-script)
- [x] 4가지 방법 예비 비교 테스트
- [x] API 통합 테스트 통과
- [x] `iteration-3-summary.md` 생성
- [x] Redis 심층 탐구 문서(`redis-deep-dive.md`) 작성

### 최종 검증
- [x] 4가지 전략 모두 정상 동작
- [x] 동시 요청 시 재고 정합성 보장
- [x] ArchUnit 테스트 통과
- [x] README 업데이트 (Redis 방법 추가)

---

## Blockers

- 없음

---

## Notes

### Redis Distributed Lock vs Lua Script 차이

| 특성 | Distributed Lock | Lua Script |
|------|------------------|------------|
| Lock 필요 | O (RLock) | X (원자적 실행) |
| DB 접근 | 필요 (MySQL) | 선택 (Redis만 가능) |
| 복잡도 | 중간 | 낮음 |
| 성능 | 빠름 | 가장 빠름 |
| 적합 상황 | DB 정합성 필요 | 초고속 처리 |

### Redisson vs Lettuce

- **Redisson:** 분산 락, 분산 자료구조 등 고급 기능 제공
- **Lettuce:** Spring Boot 기본, 단순 명령어 실행에 적합
- **선택:** Redisson (RLock 기능 필요)

### MySQL 동기화 전략

Sprint 2에서는 **옵션 3 (동기화 안 함)**으로 진행:
- PoC 목적: 성능 비교에 집중
- Redis를 Source of Truth로 사용
- 실무에서는 비동기 동기화 권장

---

## Sprint 2 목표 요약

✅ **Redis Distributed Lock 구현** (Redisson RLock 사용)
✅ **Redis Lua Script 구현** (원자적 재고 차감)
✅ **4가지 방법 API 통합** (method 파라미터로 선택)
✅ **예비 성능 비교** (Sprint 3 부하 테스트 준비)
✅ **Iteration Summary 파일 생성** (Sprint 1 회고 반영)
