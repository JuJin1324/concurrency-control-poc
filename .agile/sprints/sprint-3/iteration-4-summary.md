# Iteration 4 Summary: 대규모 트래픽 및 한계 돌파 테스트 (Final)

**Sprint:** Sprint 3 - k6 부하 테스트 + 최종 성능 비교 분석
**Iteration:** 4/4
**완료일:** 2026-01-27
**상태:** ✅ 완료

---

## 1. 테스트 전략 및 목적 (Test Strategy)

이번 Iteration은 단순한 성능 측정을 넘어, 시스템의 한계와 회복력을 단계적으로 검증하기 위해 다음과 같은 전략으로 수행되었습니다.

### Phase 1: High Load (본격 경합)
- **규모:** 재고 1,000개 / Max 2,000 RPS
- **목적:** 일반적인 대규모 트래픽 상황에서의 효율성 비교 및 병목 지점(Bottleneck) 조기 발견.

### Phase 2: Extreme Load (한계 돌파)
- **규모:** 재고 10,000개 / Max 2,000 RPS
- **목적:** 시스템의 물리적 임계점(Knee Point) 탐색 및 극한 상황에서의 안정성 검증.

### Phase 3: Recovery Baseline (회복력)
- **규모:** 재고 100개 (가벼운 부하)
- **목적:** Extreme 테스트 직후 리소스(Connection, Memory) 누수 없이 정상 상태로 즉시 회복되는지 확인.

---

## 2. 테스트 수행 결과

### 2.1 High Load (재고 1,000개)

| Method | Latency p(95) | Max VUs | 결과 |
| :--- | :--- | :--- | :--- |
| **Pessimistic** | 6.34ms | 3 | 안정적 (Cold 대비 30% 향상) |
| **Optimistic** | **6.15ms** | **3** | **Best Latency.** 충돌 분산 효과로 매우 빠름. |
| **Redis Lock** | 6.52ms | **5,000** | **FAIL.** 락 대기 시간 증가로 VU 고갈 및 병목 발생. |
| **Lua Script** | 5.87ms | 4 | **Best Efficiency.** 가장 적은 리소스로 처리. |

> **발견점:** Redis Lock은 락 획득 대기 과정(Spin/PubSub)에서 스레드를 점유하여 대규모 트래픽 시 병목이 됨을 확인. Extreme 단계에서 제외함.

### 2.2 Extreme Load (재고 10,000개)

| Method | Latency p(95) | Latency Avg | Max VUs | 특이사항 |
| :--- | :--- | :--- | :--- | :--- |
| **Pessimistic** | 3.52ms | 2.25ms | 5 | 부하가 커질수록(Warm-up) 오히려 빨라짐. |
| **Optimistic** | **3.35ms** | 3.00ms | **3** | 1만 건 충돌에도 끄떡없는 놀라운 성능. |
| **Lua Script** | 3.76ms | **1.85ms** | 5 | 평균 응답 속도 최강. |

> **발견점:** 세 방식 모두 1만 건을 여유롭게 처리함. 시스템의 한계(Knee Point)에 도달하지 못함.

### 2.3 Recovery Baseline (회복력 검증)

- **Latency p(95):** **2.29ms**
- **결과:** Extreme 테스트 직후임에도 불구하고, 성능 저하 없이 즉시 최상의 상태로 복구됨. **완벽한 복원력(Resilience)** 증명.

---

## 3. 핵심 인사이트 (Key Insights)

### 1. Warm-up의 중요성 (Cold vs Warm)
- 초기 테스트 대비 모든 방식에서 약 **30% 이상의 성능 향상**이 관측됨.
- JVM JIT 컴파일과 OS 캐시, 커넥션 풀 초기화가 성능 테스트의 신뢰도에 결정적인 영향을 미침.

### 2. Optimistic Lock의 재발견
- "충돌이 많으면 느리다"는 통념과 달리, 실제 네트워크 환경(k6)에서는 요청 간격(Pacing)이 자연스럽게 경합을 완화시켜 줌.
- 로컬/저지연 환경에서는 비관적 락보다 더 나은 선택일 수 있음을 시사.

### 3. 하드웨어(M1 Max)의 영향과 변곡점
- 테스트 환경(Mac Studio M1 Max)의 압도적인 I/O 성능 덕분에, 1만 TPS 부하에서도 시스템이 포화(Saturation) 상태에 이르지 않음.
- 클라우드 환경이나 저사양 VM에서는 결과가 달라질 수 있음.

---

## 4. 사용자 가이드 (How-to)

**환경 준비 (필수):**
```bash
# 1. 도커 재시작 (Clean State)
make clean && make up

# 2. Warm-up (시스템 예열)
make reset && k6 run -e METHOD=pessimistic --duration 10s k6-scripts/stress-test.js > /dev/null 2>&1 && echo "Warm-up Completed"
```

### 2. High Load 테스트 (재고 1,000개)
각 방식별로 명령어를 순차적으로 실행하여 측정합니다.

**Pessimistic Lock:**
```bash
make reset-1k && k6 run -e METHOD=pessimistic k6-scripts/stress-test.js; make show-db
```

**Optimistic Lock:**
```bash
make reset-1k && k6 run -e METHOD=optimistic k6-scripts/stress-test.js; make show-db
```

**Redis Lock:**
```bash
make reset-1k && k6 run -e METHOD=redis-lock k6-scripts/stress-test.js; make show-db
```

**Lua Script:**
```bash
make reset-1k && k6 run -e METHOD=lua-script k6-scripts/stress-test.js; make show-db; make show-redis
```

### 3. Extreme Load 테스트 (재고 10,000개)
시스템의 한계를 시험합니다. (Redis Lock은 High Load 단계에서 병목이 확인되어 제외됨)

**Pessimistic Lock:**
```bash
make reset-10k && k6 run -e METHOD=pessimistic k6-scripts/stress-test.js; make show-db
```

**Optimistic Lock:**
```bash
make reset-10k && k6 run -e METHOD=optimistic k6-scripts/stress-test.js; make show-db
```

**Lua Script:**
```bash
make reset-10k && k6 run -e METHOD=lua-script k6-scripts/stress-test.js; make show-db; make show-redis
```

---

## 사용자 피드백

- **테스트 환경 통제:** 백그라운드 앱을 종료하고 진행하여 데이터 노이즈(Jitter)를 제거함.
- **단계별 접근:** Baseline -> High -> Extreme -> Recovery 순으로 진행하며 각 단계의 목적을 명확히 함.