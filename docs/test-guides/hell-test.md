# Hell Test Guide (선착순 이벤트 시나리오)

## 🎯 테스트 목적

선착순 이벤트 상황(재고 100개 vs 5,000명 동시 접속)에서 4가지 동시성 제어 방법의 처리량, 지연 시간, 안정성을 검증합니다.

**핵심 질문:**
- "극한의 경합 상황에서 어떤 방법이 가장 효율적인가?"
- "데이터 정합성은 보장되는가?" (재고가 음수가 되지 않는가?)

---

## 📋 테스트 조건

| 항목 | 스펙 |
|:---|:---|
| **시나리오** | 재고 100개 vs 5,000명 동시 접속 |
| **환경** | Mac Studio M1 Max |
| **Application** | Tomcat 200 Threads |
| **Docker (App)** | 2 vCPU / 4GB (AWS t3.medium) |
| **Docker (MySQL)** | 2 vCPU / 4GB (AWS db.t3.medium) |
| **Docker (Redis)** | 1 vCPU / 1GB (AWS cache.t3.small) |
| **k6 설정** | 5,000 VUs, 5,000 Iterations |      

---

## 🚀 재현 가이드

### 사전 준비

```bash
# 0. 기존 컨테이너 완전 삭제 (처음 한 번만)
make clean

# 1. 인프라 시작 (처음 한 번만)
make up

# 2. 애플리케이션 빌드 (처음 한 번만)
make build

# 3. 컨테이너 상태 확인
make ps  # 모든 컨테이너가 Healthy인지 확인
```

---

### Lua Script (Best Performance)

**실행:**
```bash
make reset && \
k6 run -e METHOD=lua-script --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=lua-script -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db; make show-redis
```

**검증:**
```bash
# MySQL 재고 확인
make show-db
# Expected: quantity = 0

# Redis 재고 확인
make show-redis
# Expected: 0
```

---

### Optimistic Lock (Fast Fail)

**실행:**
```bash
make reset && \
k6 run -e METHOD=optimistic --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=optimistic -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

### Pessimistic Lock (Absolute Stability)

**실행:**
```bash
make reset && \
k6 run -e METHOD=pessimistic --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=pessimistic -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

### Redis Lock (Distributed Lock)

**실행:**
```bash
make reset && \
k6 run -e METHOD=redis-lock --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=redis-lock -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

## 📊 실제 측정 결과 (Mac Studio M1 Max)

**조건:** Tomcat 200 Threads + AWS t3.medium 급 (App: 2 vCPU/4GB, MySQL: 2 vCPU/4GB, Redis: 1 vCPU/1GB) + Warm-up 적용

| 순위 | 제어 방식 | Latency (p95 / Avg) | Throughput | Stability (Max VUs) | 정합성 | 특이사항 |
| :--: | :--- | :---: | :---: | :---: | :--: | :--- |
| 🥇 | **Lua Script** | **1.12s / 447ms** | **3,518 req/s** | 5,000 | ✅ | **Best Performance.** 선착순 이벤트 최강자 |
| 🥈 | **Optimistic** | 1.33s / 615ms | 3,354 req/s | 5,000 | ✅ | **Fast Fail.** 충돌 시 즉시 리턴으로 효율 극대화 |
| 🥉 | **Pessimistic** | 3.58s / 1.48s | 1,280 req/s | 5,000 | ✅ | **Stable.** DB 락 대기로 처리량 하락 |
| 4 | **Redis Lock** | 5.82s / 2.15s | 742 req/s | 5,000 | ✅ | **Overhead.** 분산 락 부하로 인한 성능 최하위 |

**데이터 정합성:** 모든 테스트 종료 후 MySQL/Redis 재고 0개 확인 완료 ✅

---

## 💡 주요 인사이트

### 1. Lua Script의 압도적 우위 (선착순 이벤트 최적)
- **RPS 3,500+**: DB I/O 없이 Redis 내에서만 연산 수행하여 처리량 극대화
- 5,000명이 동시에 달려드는 상황에서 가장 빠르고 안정적인 성능을 입증함

### 2. Optimistic Lock의 효율적 실패 (Fast Fail)
- 경합이 극심한 상황에서 실패한 요청이 즉시 리턴됨으로써 전체적인 시스템 지연 시간을 Lua Script 수준으로 방어함
- DB 쓰기 비용이 발생함에도 불구하고 TPS 3,300+를 기록하며 뛰어난 성과를 보임

### 3. Redis Lock의 한계점 확인
- 분산 락 획득/해제 과정의 오버헤드가 단일 노드 환경에서 가장 큰 병목으로 작용함
- 5,000명의 유저가 몰리는 선착순 이벤트에는 적합하지 않은 방식임을 데이터로 검증함

### 4. 데이터 정합성 보장
- 5,000명의 유저가 100개의 재고를 두고 경합했음에도, 모든 방식에서 정확히 **재고 0개**로 마감됨
- 비즈니스 로직상의 결함 없이 동시성 제어가 완벽하게 작동함을 확인

---

## ⚠️ 주의사항

### 스펙 정보
Hell Test는 **AWS t3.medium 급 스펙 (Tomcat 200 Threads / App 2 vCPU)**에서 테스트되었습니다.
일반적인 클라우드 초기 스펙으로 충분히 재현 가능합니다.

### Warm-up 필수
- JVM JIT 컴파일, Connection Pool 초기화, OS 캐시 등이 성능에 결정적 영향
- 초기 테스트 대비 약 **30% 이상 성능 향상**

### 백그라운드 앱 종료 권장
- 테스트 중 Chrome, Slack 등 백그라운드 앱을 종료하면 데이터 노이즈(Jitter)를 제거할 수 있습니다.

---

## 🔗 관련 문서

- **[Performance Test Result](../performance-test-result.md)** - 전체 성능 분석 리포트
- **[Practical Guide](../practical-guide.md)** - 실무 적용 가이드
- **[High Load Test Guide](high-test.md)** - 일반적인 대규모 트래픽 테스트
- **[Extreme Load Test Guide](extreme-test.md)** - 시스템 한계 탐색 테스트

---

*Last Updated: 2026-01-28*
