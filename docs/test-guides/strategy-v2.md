# Test Strategy V2: 표준화된 성능 측정 가이드

**적용일:** 2026-01-30 (Sprint 5)
**변경 배경:** 기존 테스트 스크립트의 혼재된 목적(부하 vs 스트레스)과 Warm-up 비효율성을 개선하고, 재현 가능한 테스트 표준을 수립함.

---

## 1. 테스트 유형 재정의 (New Taxonomy)

기존의 `High`, `Extreme`, `Hell` 용어를 목적 중심의 명확한 용어로 대체합니다.

| 기존 용어 (Legacy) | **신규 유형 (Type)** | 스크립트 파일 | Executor | 핵심 지표 |
| :--- | :--- | :--- | :--- | :--- |
| High Load<br>Extreme Load | **Capacity Test**<br>(용량 테스트) | `capacity.js` | **Shared Iterations** | **TPS** (얼마나 빨리 처리하나?) |
| Hell Test | **Contention Test**<br>(경합 테스트) | `contention.js` | **Constant VUs** | **Stability** (안 죽고 버티나?) |
| (없음) | **Stress Test**<br>(한계 테스트) | `stress.js` | **Ramping Arrival** | **Knee Point** (언제 죽나?) |

---

## 2. 표준 실행 프로세스 (Standard Procedure)

모든 성능 테스트는 다음 3단계를 엄격히 준수해야 합니다.

### Step 1: 환경 초기화 (Reset)
DB와 Redis의 상태를 테스트 시작 전으로 완벽히 되돌립니다.
```bash
make reset-1k   # Capacity (High)
make reset-10k  # Capacity (Extreme)
make reset-100  # Contention (Hell)
```

### Step 2: 웜업 (Warm-up)
JVM JIT 컴파일러와 Connection Pool을 예열합니다. (필수)
```bash
# 가벼운 부하 (10 VUs, 200 Iterations)
k6 run -e METHOD=<method> k6-scripts/warmup.js
```

### Step 3: 본 테스트 (Main Test)
목적에 맞는 스크립트를 CLI 인자와 함께 실행합니다.
```bash
# Capacity Test (예: Extreme Load)
k6 run -e METHOD=<method> -e VUS=100 -e ITERATIONS=10000 k6-scripts/capacity.js

# Contention Test (예: Hell Test)
k6 run -e METHOD=<method> -e VUS=5000 -e DURATION=30s k6-scripts/contention.js
```

---

## 3. 스크립트 상세 명세 (Script Specs)

### 3.1 `warmup.js` (신규)
- **Executor:** Shared Iterations
- **기본값:** VUs 10, Iterations 200
- **특징:** Threshold 없음, 결과 무시. 오직 예열용.

### 3.2 `capacity.js` (구 high-test/extreme-test 대체)
- **Executor:** Shared Iterations
- **필수 인자:** `VUS`, `ITERATIONS`
- **시나리오 매핑:**
    - **High Load:** `-e VUS=100 -e ITERATIONS=1000`
    - **Extreme Load:** `-e VUS=100 -e ITERATIONS=10000`

### 3.3 `contention.js` (구 hell-test 대체)
- **Executor:** Constant VUs
- **필수 인자:** `VUS`, `DURATION`
- **시나리오 매핑:**
    - **Hell Test:** `-e VUS=5000 -e DURATION=30s`

---

## 4. 데이터 비교 기준 (Baseline)

새로운 전략에 따라 측정된 데이터는 아래 양식으로 기록합니다.

| 시나리오 | 제어 방식 | VUs | Target | TPS | p95 Latency | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **High** | Lua | 100 | 1k | 4,113 | 72ms | 0% |
| **Extreme** | Pessimistic | 100 | 10k | - | - | - |
| **Hell** | Redis | 5k | 30s | - | - | - |

---
*Created by Gemini CLI*
