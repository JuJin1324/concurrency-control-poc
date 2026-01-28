# Extreme Load Test Guide (시스템 한계 탐색)

## 🎯 테스트 목적

시스템의 물리적 한계(Knee Point)를 탐색하고, 극한 상황에서의 안정성을 검증합니다.

**핵심 질문:**
- "시스템이 견딜 수 있는 최대 부하는 어느 정도인가?"
- "한계에 도달했을 때 각 방법의 안정성은?"
- "부하가 커질수록 성능이 어떻게 변하는가?"

---

## 📋 테스트 조건

| 항목 | 스펙 |
|:---|:---|
| **시나리오** | 재고 10,000개 / Max 2,000 RPS |
| **환경** | Mac Studio M1 Max |
| **Application** | Tomcat 200 Threads |
| **Docker (App)** | 2 vCPU / 4GB (AWS t3.medium) |
| **Docker (MySQL)** | 2 vCPU / 4GB (AWS db.t3.medium) |
| **Docker (Redis)** | 1 vCPU / 1GB (AWS cache.t3.small) |
| **k6 설정** | 100 VUs, 10,000 Iterations |

**참고:** High Load 테스트에서 병목이 확인된 Redis Lock은 제외

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

### Lua Script (Best Average Latency)

**실행:**
```bash
make reset-10k && \
k6 run -e METHOD=lua-script --vus 100 --iterations 500 k6-scripts/extreme-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=lua-script k6-scripts/extreme-test.js; \
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

### Optimistic Lock (Best p95 Latency)

**실행:**
```bash
make reset-10k && \
k6 run -e METHOD=optimistic --vus 100 --iterations 500 k6-scripts/extreme-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=optimistic k6-scripts/extreme-test.js; \
make show-db
```

**검증:**
```bash
make show-db
# Expected: quantity = 0
```

---

### Pessimistic Lock (Warm-up Effect)

**실행:**
```bash
make reset-10k && \
k6 run -e METHOD=pessimistic --vus 100 --iterations 500 k6-scripts/extreme-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=pessimistic k6-scripts/extreme-test.js; \
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
| 🥇 | **Pessimistic** | **4.05ms / 2.35ms** | 834 req/s | 5 | ✅ | **Best p95.** 극한 상황에서 가장 뛰어난 안정성 |
| 🥈 | **Lua Script** | 4.26ms / **1.99ms** | 834 req/s | 2 | ✅ | **Best Avg.** Redis 원자 연산의 압도적 평균 속도 |
| 🥉 | **Optimistic** | 5.53ms / 7.81ms | 834 req/s | 7 | ✅ | **Stable.** 경합 증가로 인한 오버헤드 관찰됨 |

**데이터 정합성:** 모든 테스트 종료 후 MySQL/Redis 재고 0개 확인 완료 ✅

**시스템 한계 미도달:** Mac Studio M1 Max의 뛰어난 I/O 성능으로 인해 1만 건의 요청에도 Knee Point에 도달하지 않음. 단일 서버 환경의 물리적 한계는 여전히 높게 형성됨.

---

## 💡 주요 인사이트

### 1. Pessimistic Lock의 가치 재발견
- **Stability & Reliability:** 경합이 극심한 상황(1만 건)에서는 락 큐를 통한 순차 처리가 p95 지연 시간 방어에 가장 유리함
- 단순히 "느리다"는 고정관념과 달리, 데이터 정합성 보장과 성능 안정성 사이의 훌륭한 균형점 제공

### 2. 제어 방식별 메트릭 특징
- **Lua Script:** 평균 속도(Avg)에서 압도적 우위. 비즈니스 로직이 단순하고 Redis 활용이 가능할 때 최상의 선택지
- **Optimistic Lock:** 데이터 경합 증가 시 지연 시간(Avg)이 눈에 띄게 증가함. 재시도 로직의 비용이 가시화되는 지점 확인

### 3. 단일 노드 테스트의 한계
- 현재 모든 메트릭이 우수한 수치를 보여주는 것은 호스트 기기(M1 Max)의 성능 영향이 큼
- **Throughput(834 RPS)**은 현재 테스트 시나리오와 재고 수량 하에서의 측정치이며, 더 큰 데이터셋에서는 Knee Point 관찰이 필요함

---

## 🔬 시스템 한계 발견 (10,000 VUs 테스트)

**목표:** Knee Point 탐색을 위해 10,000 VUs로 추가 테스트 수행

**결과:** 모든 방식에서 **Connection Error 약 9% 발생** ❌

**원인:**
- 단일 서버(Single Node)의 **OS TCP Ephemeral Port 고갈**
- **Context Switching 비용 급증**
- 애플리케이션 레벨(Lock)이 아닌 **인프라 레벨의 물리적 한계**

**결론:**
- 10,000 VUs는 단일 서버의 물리적 한계를 초과
- Scale-out 필수
- Hell Test (5,000 VUs)가 안정적인 최대 부하

---

## ⚠️ 주의사항

### Warm-up 자동 포함
모든 실행 명령어에 Warm-up이 자동으로 포함되어 있습니다. 별도로 Warm-up을 실행할 필요가 없습니다.

### 시스템 리소스 모니터링
```bash
# 실시간 리소스 확인
make stats
```

### 백그라운드 앱 종료 권장
테스트 중 Chrome, Slack 등을 종료하면 데이터 노이즈를 제거할 수 있습니다.

---

## 🔗 관련 문서

- **[Performance Test Result](../performance-test-result.md)** - 전체 성능 분석 리포트
- **[Hell Test Guide](hell-test.md)** - 선착순 이벤트 시나리오 (5,000 VUs)
- **[High Load Test Guide](high-test.md)** - 일반적인 대규모 트래픽
- **[Recovery Test Guide](recovery-test.md)** - 회복력 검증 (이 테스트 직후 실행)

---

*Last Updated: 2026-01-28*
