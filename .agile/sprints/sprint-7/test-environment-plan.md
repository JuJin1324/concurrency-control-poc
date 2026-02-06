# US-7.2: 테스트 환경 설계 및 기존 리소스 활용 계획

**작성일:** 2026-02-06
**목적:** 기존 코드와 k6 스크립트를 최대한 활용하여 Best Fit 시나리오 테스트 구현
**원칙:** 새 파일 생성 최소화, 기존 리소스 재활용

---

## 📋 기존 리소스 현황

### 1. Java 구현체 (이미 완성됨 ✅)
```
src/main/java/com/concurrency/poc/service/
├── PessimisticLockStockService.java  ✅ 사용 가능
├── OptimisticLockStockService.java   ✅ 사용 가능
├── RedisLockStockService.java        ✅ 사용 가능
└── LuaScriptStockService.java        ✅ 사용 가능
```

**API 엔드포인트:**
- `POST /api/stock/decrease?method={method}` - 재고 차감
- `GET /api/stock/{id}` - 재고 조회

**Method 파라미터:**
- `pessimistic` / `pessimistic-lock`
- `optimistic` / `optimistic-lock`
- `redis-lock`
- `lua-script`

### 2. k6 테스트 스크립트 (이미 존재함 ✅)
```
k6-scripts/
├── capacity.js    ✅ 최대 처리량 측정 (shared-iterations)
├── contention.js  ✅ 경합 상황 측정 (constant-vus)
├── stress.js      ✅ 점진적 부하 증가 (ramping-arrival-rate)
└── warmup.js      ✅ 워밍업
```

**기존 스크립트 구조:**
- 환경 변수로 METHOD, VUS, ITERATIONS 등 제어
- `stockId: 1` 고정 (단일 상품)
- 결과: 200 (성공) 또는 409 (재고 부족)

---

## 🎯 시나리오별 기존 리소스 활용 계획

### Scenario 1: Complex Transaction (복합 트랜잭션)

**필요 작업:** ✏️ **새 구현 필요**

#### 왜 새로 만들어야 하는가?
- 기존 서비스: 단순 재고 차감만
- Scenario 1: 재고 + 포인트 + 결제 이력 (3개 테이블)
- **복합 트랜잭션 구현 필수**

#### 구현 방향
**Option A: 새 Service 추가 (최소 변경)**
```java
// 새 파일 생성 (최소한)
ComplexTransactionService.java
- decreaseStockWithPointsAndPayment()
- Pessimistic 방식으로 구현
- Optimistic 방식 비교용 별도 메서드

// 새 엔드포인트 추가
POST /api/stock/decrease-complex?method={method}
```

**k6 스크립트:**
```javascript
// 기존 capacity.js 복사 → complex-transaction.js
- 엔드포인트만 변경: /decrease-complex
- METHOD: pessimistic vs optimistic
```

**데이터베이스:**
```sql
-- 새 테이블 필요 (최소한)
CREATE TABLE user_points (...)
CREATE TABLE payment_history (...)
```

---

### Scenario 2: Low Contention Update (분산 업데이트)

**필요 작업:** ✏️ **k6 스크립트 수정만 (코드 변경 없음!)**

#### 기존 리소스 100% 활용 가능!
- ✅ Java 코드: 그대로 사용
- ✅ API 엔드포인트: 그대로 사용
- ✅ DB 테이블: 그대로 사용

#### k6 스크립트 수정
**기존 capacity.js:**
```javascript
// 문제: stockId 고정
const payload = JSON.stringify({ stockId: 1, amount: 1 });
```

**수정 방안: 랜덤 상품 선택**
```javascript
// capacity.js를 복사 → low-contention.js
export default function () {
  // 1~100 사이 랜덤 상품 선택
  const stockId = Math.floor(Math.random() * 100) + 1;
  const payload = JSON.stringify({ stockId: stockId, amount: 1 });

  // 나머지 동일
  const res = http.post(`${BASE_URL}/api/stock/decrease?method=${METHOD}`, payload, params);
  ...
}
```

**사전 준비:**
```sql
-- DB에 상품 100개 생성 (스크립트)
INSERT INTO stock (id, product_name, quantity) VALUES
  (1, 'Product-1', 100),
  (2, 'Product-2', 100),
  ...
  (100, 'Product-100', 100);
```

**테스트 실행:**
```bash
# Optimistic Lock
k6 run -e METHOD=optimistic -e VUS=1000 -e ITERATIONS=10000 k6-scripts/low-contention.js

# Pessimistic Lock
k6 run -e METHOD=pessimistic -e VUS=1000 -e ITERATIONS=10000 k6-scripts/low-contention.js
```

---

### Scenario 3: Resource Protection (리소스 보호)

**필요 작업:** ✏️ **k6 스크립트 + DB 부하 시뮬레이션**

#### 기존 리소스 활용
- ✅ Java 코드: 그대로 사용
- ✅ API 엔드포인트: 그대로 사용

#### DB 부하 시뮬레이션 방법

**Option A: MySQL 설정 변경 (가장 간단)**
```sql
-- max_connections를 극도로 제한
SET GLOBAL max_connections = 10;

-- 또는 innodb_buffer_pool_size 축소
SET GLOBAL innodb_buffer_pool_size = 8388608; -- 8MB
```

**Option B: Slow Query 주입**
```sql
-- 의도적으로 느린 쿼리 실행 (백그라운드)
SELECT SLEEP(5) FROM stock WHERE id = 1;
```

**Option C: Docker 리소스 제한**
```yaml
# docker-compose.yml
services:
  mysql:
    cpus: '0.5'  # CPU 제한
    mem_limit: 512m  # 메모리 제한
```

#### k6 스크립트
```javascript
// 기존 stress.js 활용
// DB 부하 상황에서 Redis Lock vs DB Locks 비교

// Method 1: Redis Lock (DB 접근 최소화)
k6 run -e METHOD=redis-lock -e TARGET_RPS=2000 k6-scripts/stress.js

// Method 2: Pessimistic Lock (DB 직접 부하)
k6 run -e METHOD=pessimistic -e TARGET_RPS=2000 k6-scripts/stress.js
```

**측정 지표:**
- System Uptime (다운 여부)
- DB Connection Pool Exhaustion
- Error Rate (5xx)

---

### Scenario 4: Atomic Counter (극한 성능)

**필요 작업:** ❌ **새 측정 불필요 - 기존 데이터 활용**

#### 기존 Sprint 5 결과 활용
```
기존 데이터:
- Contention Test 결과 (docs/reports/contention-report.md)
- Lua Script: 10,539 TPS
- Pessimistic: 3,773 TPS
- 성능 격차: 10배 이상 증명됨
```

#### 작업 내용
- ✅ 기존 리포트 재정리
- ✅ 배민/토스 사례와 연결
- ✅ 기술적 분석 추가 (왜 이런 차이가 나는가)
- ❌ 새로운 측정 불필요

---

## 📊 활용 계획 요약표

| Scenario | 기존 Java 코드 | 기존 k6 스크립트 | 필요 작업 | 비용 |
|----------|---------------|-----------------|----------|------|
| **1. Complex Transaction** | ❌ 새 Service 필요 | ✏️ capacity.js 복사 | 새 구현 (3개 테이블) | 높음 (2-3일) |
| **2. Low Contention** | ✅ 그대로 사용 | ✏️ capacity.js 수정 | stockId 랜덤화만 | 낮음 (0.5일) |
| **3. Resource Protection** | ✅ 그대로 사용 | ✅ stress.js 활용 | DB 부하 시뮬레이션 | 중간 (1일) |
| **4. Atomic Counter** | ✅ 그대로 사용 | ✅ 기존 데이터 활용 | 리포트 작성만 | 낮음 (0.5일) |

**총 예상 소요 시간:** 4-5일

---

## 🔧 구현 우선순위 (추천)

### Phase 1: 빠른 승리 (Quick Wins)
1. **Scenario 2** (0.5일) - 가장 쉬움, 즉시 검증 가능
2. **Scenario 4** (0.5일) - 리포트 작성만

### Phase 2: 중간 난이도
3. **Scenario 3** (1일) - DB 부하 시뮬레이션

### Phase 3: 복잡한 구현
4. **Scenario 1** (2-3일) - 새 Service 구현

**전략:**
- 빠른 성과 → 사용자 피드백 → 방향 조정
- Scenario 2, 4 먼저 완료하여 50% 진행률 확보
- Scenario 1은 마지막 (가장 복잡)

---

---

## ✅ 최종 합의 사항 (2026-02-06)

### 1. k6 스크립트 구조: 시나리오 중심 채택 ✅
```
k6-scripts/
├── capacity.js, contention.js, stress.js, warmup.js  # Sprint 5 원본 보존
└── bestfit/                                           # Sprint 7 신규
    ├── 1-complex-transaction.js   (새 구현)
    ├── 2-low-contention.js        (capacity.js 기반 수정)
    ├── 3-resource-protection.js   (stress.js 기반 수정)
    └── README.md

# Scenario 4는 bestfit/에 포함 안 함 → 리포트에서 contention.js 참조
```

**결정 이유:**
- 포트폴리오 가치: 문서와 1:1 매핑, 면접 설명 용이
- 스토리텔링 강화: 비즈니스 시나리오 중심
- 정직성: 기존 데이터 재활용을 명확히 표시

### 2. Scenario 4: Contention Test만 활용 ✅
- **기존 Sprint 5 Contention Test = 선착순 이벤트 상황**
- Lua Script 10배 우위 (10,539 vs 3,773 TPS)
- **capacity, stress 테스트는 의미 없음 → 제외**
- 리포트에서 기존 데이터 인용 + 사례 연구 추가
- 새 측정 불필요 (시간 절약)

### 3. 구현 순서: Quick Wins 우선 ✅
```
Phase 1: Scenario 2 (0.5일) → Scenario 4 리포트 (0.5일)  ← 먼저!
Phase 2: Scenario 3 (1일)
Phase 3: Scenario 1 (2-3일)

총 예상: 4-5일
```

### 4. 측정 지표 확정 ✅
| Scenario | 핵심 지표 | 측정 방법 |
|----------|-----------|----------|
| 1. Complex Transaction | Rollback Count | 정량 (카운트) |
| 2. Low Contention | TPS, Retry Count | 정량 (k6) |
| 3. Resource Protection | System Uptime | 정성 (다운 여부) |
| 4. Atomic Counter | TPS | 참조 (Sprint 5 Contention) |

---

## 📝 US-7.2 완료 체크

### ✅ 완료 조건 달성
- [x] 기존 리소스 현황 파악
- [x] 시나리오별 활용 계획 수립
- [x] **사용자 승인 완료**
- [x] **우선순위 확정** (2 → 4 → 3 → 1)
- [x] **k6 스크립트 구조 결정** (시나리오 중심)
- [x] **측정 지표 정의**

### 🎉 US-7.2 완료!

**다음 단계:** Iteration 2 구현 시작
- **US-7.4:** Scenario 2 (Low Contention) ← Phase 1, 먼저 시작!
- **US-7.3:** Scenario 1 (Complex Transaction) ← Phase 3, 나중에

---

**작성자:** Sprint 7 Team
**최종 업데이트:** 2026-02-06
**상태:** ✅ 완료 및 승인됨
