# Best Fit Scenarios Design (상황별 최적화 시나리오 설계)

**작성일:** 2026-02-06
**목적:** 각 동시성 제어 방식이 **필승**하는 시나리오를 설계하고, 가설을 검증한다.
**Sprint:** Sprint 7 - Iteration 1

---

## 🎯 설계 철학

> **"은탄환은 없다. 적재적소(Right Tool for Right Job)만 있을 뿐."**

**Sprint 0-5의 문제점:**
- 모든 방식을 동일한 기준(단순 재고 차감)으로 평가 → Lua Script가 압도적 우위
- **"트럭과 스포츠카를 F1 서킷에서 경주"**시킨 격
- **성능 측정에만 치중** → 안정성, 회복력 등 다른 가치 간과

**Sprint 7의 목표:**
- 각 방식이 **주인공이 되는 무대**를 만들어 검증
- 비즈니스 맥락에 따른 최적 선택 증명
- **다양한 평가 축** 활용: 성능뿐 아니라 안정성, 회복력, 복잡도 등

---

## 📊 다양한 평가 축 (Evaluation Dimensions)

| 평가 축 | 측정 방법 | 예시 지표 |
|---------|----------|-----------|
| **성능 (Performance)** | 정량 측정 | TPS, Latency, Throughput |
| **안정성 (Reliability)** | 정량 측정 | Rollback Count, Error Rate, Success Rate |
| **회복력 (Resilience)** | 정성 평가 | System Uptime, Graceful Degradation |
| **복잡도 (Complexity)** | 정성 평가 | 구현 난이도, 운영 부담 |
| **비용 (Cost)** | 정성 평가 | 인프라 비용, 개발 시간 |

**핵심 원칙:**
> 시나리오마다 **가장 중요한 평가 축**을 선정하고, 해당 축에서 의미 있는 비교 대상만 테스트합니다.

---

## 📋 4가지 Best Fit 시나리오

### Scenario 1: Complex Transaction (복합 트랜잭션) 🏆 Pessimistic Lock

**핵심 평가 축:** 🛡️ **안정성 (Reliability)**

#### 비즈니스 상황
- **도메인:** 이커머스 주문 결제
- **요구사항:** 재고 차감 + 포인트 사용 + 결제 이력 생성 (3개 테이블 동시 갱신)
- **핵심 제약:** ACID 보장, 부분 성공 불가, 롤백 비용 최소화

#### 비교 대상 (2개만)
**Pessimistic Lock vs Optimistic Lock**

**비교 이유:**
- 두 방식 모두 DB 트랜잭션 기반 (공정한 비교)
- 충돌이 많은 상황에서 락 전략의 차이가 명확히 드러남
- Redis/Lua는 복합 트랜잭션 구현 어려움 (제외)

#### 왜 Pessimistic Lock이 유리한가?
- **단일 트랜잭션 보장:** DB 레벨에서 여러 테이블을 한 번에 잠금
- **롤백 비용 최소화:** 락을 걸고 시작하므로 충돌 자체가 발생하지 않음
- **구현 단순성:** JPA `@Transactional`만으로 복잡한 로직 안전하게 처리

#### 가설 (Hypothesis)
> **"충돌이 많은 복합 트랜잭션에서는 Pessimistic Lock이 Rollback 0건, Optimistic Lock은 100+ 건 발생한다."**

**검증 지표 (정량 측정):**
- ✅ **Rollback Count** (롤백 발생 횟수) ← **핵심 지표!**
- ✅ Transaction Success Rate (트랜잭션 성공률)
- ✅ Data Consistency (데이터 정합성 검증)
- △ TPS/Latency (부차적, 참고용)

#### 테스트 시나리오 상세
```
Given: 재고 100개, 포인트 잔액 10,000원
When: 100명이 동시에 주문 (재고 1개 + 포인트 100원 사용)
Then:
  - 100건 성공, 0건 실패
  - 재고 0개, 각 사용자 포인트 정확히 차감
  - 결제 이력 100건 정확히 기록
  - 롤백 발생 0건 (Pessimistic) vs N건 (Others)
```

---

### Scenario 2: Low Contention Update (분산 업데이트) 🏆 Optimistic Lock

**핵심 평가 축:** ⚡ **성능 (Performance) - 락 오버헤드 비교**

#### 비즈니스 상황
- **도메인:** 다상품 이커머스 재고 차감 (Multi-Product Inventory)
- **요구사항:** 100개 상품, 1000명이 각자 랜덤하게 상품 구매
- **핵심 특징:** 수요가 여러 상품에 분산 → 동일 상품 충돌 희귀 (1-5%)

#### 비교 대상 (2개만)
**Optimistic Lock vs Pessimistic Lock**

**비교 이유:**
- 락 오버헤드 차이가 핵심 (충돌이 드문 상황)
- 동일한 재고 로직 사용 → 공정한 비교
- Redis/Lua는 참고용 (기존 Sprint 5 데이터 활용)

#### 왜 Optimistic Lock이 유리한가?
- **락 오버헤드 Zero:** 충돌이 드물므로 재시도 비용 최소화
- **DB 커넥션 효율:** 락 점유 시간 = 0 → 높은 동시성
- **분산 업데이트 최적화:** 서로 다른 row 접근 → 병렬 처리 극대화

#### 가설 (Hypothesis)
> **"수요가 분산된 환경(충돌률 1-5%)에서는 Optimistic Lock이 Pessimistic Lock 대비 2배 이상 높은 TPS를 달성한다."**

**검증 지표 (정량 측정):**
- ✅ **TPS** (처리량) ← **핵심 지표!**
- ✅ **Retry Count** (재시도 횟수, 1-5% 예상)
- ✅ Lock Wait Time (Pessimistic의 대기 시간)
- △ p95 Latency (부차적)

**참고 데이터:**
- Redis/Lua는 기존 Sprint 5 결과 활용 (재측정 불필요)

#### 테스트 시나리오 상세
```
Given: 상품 100개, 각 상품 재고 100개
When: 1,000명이 동시 접속하여 랜덤하게 상품 구매
  - 랜덤 분포: 일부 인기 상품 집중, 대부분 분산
  - 예상 충돌률: 1-5% (일부 인기 상품에서만 발생)

Then:
  - Optimistic Lock:
    * 충돌 희귀 → 재시도 10-50건
    * 락 오버헤드 없음 → 높은 TPS
  - Pessimistic Lock:
    * 충돌 없어도 매번 락 대기
    * 직렬 처리 → 낮은 TPS

Expected: Optimistic이 2배 이상 높은 TPS
```

**실제 상황 비유:**
- **쿠팡:** 수십만 개 상품, 수요가 분산 → 동일 상품 충돌 드뭄
- **Sprint 3 Contention:** 100개 상품, 5000명 몰림 → 충돌 많음 (극단)
- **이번 시나리오:** 100개 상품, 1000명 분산 → 충돌 희귀 (현실적)

---

### Scenario 3: Resource Protection (리소스 보호) 🏆 Redis Distributed Lock

**핵심 평가 축:** 🏥 **회복력 (Resilience) - 정성 평가**

#### 비즈니스 상황
- **도메인:** 주문 폭주로 인한 DB 과부하 상황
- **요구사항:** DB CPU 80%+ 상태에서 시스템 안정성 유지
- **핵심 제약:** DB 다운 방지, Graceful Degradation

#### 비교 대상 (2-3개)
**Redis Lock vs DB Locks (Pessimistic/Optimistic)**

**비교 이유:**
- DB 보호 효과 비교가 핵심
- Lua는 이미 DB 보호 (Redis만 사용) → 동일 효과, 제외 가능

#### 왜 Redis Distributed Lock이 유리한가?
- **Throttling (유입 제어):** Redis에서 동시 요청 수를 제한하여 DB 보호
- **Circuit Breaker 역할:** DB 부하가 높을 때 요청을 Redis에서 차단
- **Fast Fail:** DB까지 가지 않고 빠르게 실패 응답

#### 가설 (Hypothesis)
> **"DB 포화 시, Redis Lock은 시스템을 유지하지만 DB Locks는 시스템 다운을 초래한다."**

**검증 지표 (정성 평가 중심):**
- ✅ **System Uptime** (시스템 다운 여부) ← **핵심 지표!**
- ✅ DB CPU Usage (DB CPU 사용률)
- ✅ Graceful Degradation (우아한 성능 저하 여부)
- △ Request Success Rate (부차적)

**측정 방법:**
- 정성적: Redis Lock → 시스템 유지 ✅ / DB Locks → 다운 ❌
- 정량적: DB CPU 사용률, 에러율

#### 테스트 시나리오 상세
```
Given: DB를 인위적으로 부하 상태로 만듦 (CPU 80%+)
When: 2,000 RPS 트래픽 유입
Then:
  - Redis Lock: DB 요청을 제한하여 시스템 안정 유지
  - Pessimistic/Optimistic: DB 커넥션 풀 고갈 → 시스템 다운
  - 시스템 가용성 비교
```

**시뮬레이션 방법:**
- MySQL `max_connections` 제한 (예: 10개)
- 또는 의도적으로 Slow Query 실행하여 DB 부하 생성

---

### Scenario 4: Atomic Counter (단순 고속 처리) 🏆 Lua Script

**핵심 평가 축:** 🚀 **극한 성능 (Extreme Performance)**

#### 비즈니스 상황
- **도메인:** 선착순 쿠폰 발급, 티켓팅
- **요구사항:** 단순 카운터 증감, 초고부하 (10만 RPS)
- **핵심 제약:** 극한의 성능, 원자성 보장

#### 비교 대상 (기존 데이터 활용)
**기존 Sprint 5 Contention Test 결과 재활용**

**접근 방법:**
- ✅ **새로운 측정 불필요** - 이미 Sprint 5에서 검증 완료
- ✅ 기존 데이터 분석 및 사례 연구 중심 리포트 작성
- ✅ 배민/토스 등 실무 사례와 연결

**Sprint 5 검증 결과:**
- Lua Script: 10,539 TPS (압도적 1위)
- Optimistic: 6,238 TPS
- Pessimistic: 3,773 TPS
- Redis Lock: 1,218 TPS
- **성능 격차: 10배 이상 증명됨**

#### 왜 Lua Script가 유리한가?
- **Lock-Free:** 락 획득/해제 과정 없음
- **I/O 최소화:** DB 접근 없이 Redis 메모리 연산
- **원자성:** 스크립트 전체가 단일 명령으로 실행

#### 가설 (Hypothesis)
> **"단순 로직에서는 Lua Script가 타 방식 대비 압도적인 TPS와 낮은 Latency를 달성한다."**
> *(이미 Sprint 5에서 검증 완료)*

**검증 지표 (기존 데이터 활용):**
- ✅ TPS (초당 처리량) ← Sprint 5 Contention Test
- ✅ p95 Latency ← Sprint 5 데이터
- ✅ Fast Fail Rate (품절 후 응답 속도) ← Sprint 5 인사이트

**리포트 작성 방향:**
- 기존 수치 재정리
- 배민 선착순 쿠폰, 티켓링크 티켓팅 사례와 연결
- "왜 이런 차이가 나는가" 기술적 분석

**k6 스크립트:**
- `k6-scripts/contention.js` 사용 (bestfit/ 디렉토리에 별도 생성 안 함)
- 리포트에서 기존 스크립트 참조
- 정직성: 재활용을 명확히 표시

---

## 🎯 검증 매트릭스 (Verification Matrix)

| Scenario | 핵심 평가 축 | 비교 대상 | 핵심 지표 | 예상 결과 | 측정 방법 |
|----------|-------------|-----------|-----------|-----------|----------|
| **Complex Transaction** | 🛡️ 안정성 | Pessimistic vs Optimistic | Rollback Count | 0 vs 100+ | 정량 측정 |
| **Low Contention Update** | ⚡ 성능 | Optimistic vs Pessimistic | TPS | 2배 이상 차이 | 정량 측정 |
| **Resource Protection** | 🏥 회복력 | Redis Lock vs DB Locks | System Uptime | 유지 vs 다운 | 정성 평가 |
| **Atomic Counter** | 🚀 극한 성능 | 기존 데이터 활용 | TPS | 10배 이상 차이 | 참고 (Sprint 5) |

---

## 📊 성공 기준 (Success Criteria)

각 시나리오에서 해당 방식이 **명확한 우위**를 보여야 함:

### 정량적 지표 (Quantitative)
- 성능/안정성 지표에서 **2배 이상** 차이
- 또는 통계적으로 유의미한 차이 (20% 이상)

### 정성적 지표 (Qualitative)
- **결정적 차이** (예: 시스템 다운 여부, 구현 가능 여부)
- 비즈니스 영향도 (예: 사용자 경험, 운영 부담)

**실패 조건:**
- 예상 우승자가 2위 이하로 밀림
- 차이가 통계적으로 유의미하지 않음 (< 20%)
- 정성적 우위가 명확하지 않음

---

## 📋 효율적 접근법 요약 (Summary)

### 핵심 전략
1. **선택과 집중:** 의미 있는 비교 대상만 선정 (2-3개)
2. **다양한 평가 축:** 성능/안정성/회복력 등 다양한 관점
3. **기존 데이터 활용:** Sprint 5 결과 재활용으로 시간 절약
4. **정량 + 정성:** 수치와 실무 가치 모두 평가

### 장점
- ⏱️ **시간 효율:** 불필요한 측정 제거
- 🎯 **집중도:** 핵심 비교에 집중
- 🌈 **다양성:** 단순 성능 비교를 넘어선 다면적 평가
- 💼 **현실성:** 실무 의사결정 과정과 유사

### 예상 소요 시간
- Scenario 1 (Complex Transaction): 구현 + 테스트 (2-3일)
- Scenario 2 (Read-Heavy): 테스트 스크립트만 (1일)
- Scenario 3 (Resource Protection): 시뮬레이션 + 테스트 (1-2일)
- Scenario 4 (Atomic Counter): 리포트 작성만 (0.5일)
- **총 예상: 4-6일** (vs 전체 비교 시 10+ 일)

---

## 🔄 다음 단계

1. ADR 작성: Best Fit Verification 접근 방법 정당화
2. k6 테스트 스크립트 설계
3. 각 시나리오별 구현 및 검증
4. 결과 리포트 작성

---

**작성자:** Sprint 7 Team
**검토 필요:** Checkpoint 1 - 사용자 승인 후 구현 진행
