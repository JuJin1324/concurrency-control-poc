# High Load Test Guide (일반적 대규모 트래픽)

## 🎯 테스트 목적

일반적인 대규모 트래픽 상황(재고 1,000개)에서 4가지 동시성 제어 방법의 효율성과 병목 지점(Bottleneck)을 조기 발견합니다.

**핵심 질문:**
- "실무에서 마주할 일반적인 부하에서 어떤 방법이 가장 효율적인가?"
- "병목 지점이 있는가?"

---

## 📋 테스트 조건

| 항목 | 스펙 |
|:---|:---|
| **시나리오** | 재고 1,000개 / Max 2,000 RPS |
| **환경** | Mac Studio M1 Max |
| **Application** | Tomcat 200 Threads |
| **Docker (App)** | 2 vCPU / 4GB (AWS t3.medium) |
| **Docker (MySQL)** | 2 vCPU / 4GB (AWS db.t3.medium) |
| **Docker (Redis)** | 1 vCPU / 1GB (AWS cache.t3.small) |
| **k6 설정** | 100 VUs, 1,000 Iterations |

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

### Lua Script (Best Efficiency)

**실행:**
```bash
make reset-1k && \
k6 run -e METHOD=lua-script --vus 100 --iterations 200 k6-scripts/high-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=lua-script k6-scripts/high-test.js; \
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

### Optimistic Lock (Best Latency)

**실행:**
```bash
make reset-1k && \
k6 run -e METHOD=optimistic --vus 100 --iterations 200 k6-scripts/high-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=optimistic k6-scripts/high-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

### Pessimistic Lock (Stable)

**실행:**
```bash
make reset-1k && \
k6 run -e METHOD=pessimistic --vus 100 --iterations 200 k6-scripts/high-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=pessimistic k6-scripts/high-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

### Redis Lock (⚠️ Bottleneck Detected)

**실행:**
```bash
make reset-1k && \
k6 run -e METHOD=redis-lock --vus 100 --iterations 200 k6-scripts/high-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=redis-lock k6-scripts/high-test.js; \
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
| 🥇 | **Lua Script** | **7.45ms / 3.97ms** | 834 req/s | 3 | ✅ | **Best Efficiency.** 최소 리소스로 최고 속도 |
| 🥈 | **Optimistic** | 7.79ms / 4.08ms | 834 req/s | 12 | ✅ | **Good Latency.** 안정적인 응답 속도 유지 |
| 🥉 | **Pessimistic** | 7.81ms / 4.14ms | 834 req/s | 4 | ✅ | **Stable.** Optimistic과 유사한 성능 |
| 4 | **Redis Lock** | 8.54ms / 4.49ms | 834 req/s | **5,000** | ✅ | **❌ FAIL.** 락 대기 오버헤드로 인한 VU 고갈 |

**데이터 정합성:** 모든 테스트 종료 후 MySQL/Redis 재고 0개 확인 완료 ✅

---

## 💡 주요 인사이트

### 1. 메트릭별 핵심 지표
- **성능 (Latency):** Lua Script가 평균 및 p95 모두 가장 우수함
- **안정성 (Max VUs):** Redis Lock을 제외한 모든 방식이 한 자릿수~십 대 VU로 안정적 처리
- **처리량 (TPS):** 테스트 시나리오상 834 RPS로 수렴 (재고 소진 속도에 의해 결정)

### 2. Redis Lock의 병목 메커니즘
- 다른 지표는 타 방식과 큰 차이가 없으나, **Stability(Max VUs)** 지표에서 극단적인 차이(5,000) 발생
- 이는 락을 얻지 못한 스레드가 반환되지 않고 대기하면서 시스템 가용성을 잠식하고 있음을 의미

### 3. 하드웨어 환경의 변수
- Mac Studio 환경에서는 단일 노드 성능이 매우 뛰어나 Latency 차이가 미미할 수 있음
- 실제 클라우드 인프라(t3계열)에서는 I/O Wait로 인해 지표 차이가 더 벌어질 것으로 예상됨

---

## ⚠️ 주의사항

### Warm-up 자동 포함
모든 실행 명령어에 Warm-up이 자동으로 포함되어 있습니다. 별도로 Warm-up을 실행할 필요가 없습니다.

### Redis Lock 주의
- High Load 이상에서는 Redis Lock 사용 시 성능 저하 주의
- 단일 서버 환경에서는 Lua Script 또는 Optimistic Lock 권장

---

## 🔗 관련 문서

- **[Performance Test Result](../performance-test-result.md)** - 전체 성능 분석 리포트
- **[Hell Test Guide](hell-test.md)** - 선착순 이벤트 시나리오 (5,000 VUs)
- **[Extreme Load Test Guide](extreme-test.md)** - 시스템 한계 탐색 테스트
- **[Practical Guide](../practical-guide.md)** - 실무 적용 가이드

---

*Last Updated: 2026-01-28*
