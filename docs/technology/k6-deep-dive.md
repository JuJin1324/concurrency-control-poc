# k6 Deep Dive: 부하 테스트 핵심 개념과 전략

**작성일:** 2026-01-30
**목적:** k6의 작동 원리(Executor)를 정확히 이해하고, 목적에 맞는 테스트 스크립트를 설계하기 위한 가이드.

---

## 1. 핵심 개념: Executor (실행기)

k6가 부하를 생성하는 방식은 **Executor**에 의해 결정됩니다. "VU(가상 유저)를 어떻게 움직일 것인가?"에 대한 전략입니다.

### 1.1 Shared Iterations (공유 반복) - "작업량 중심"
> **"100명이 힘을 합쳐 벽돌 1,000장을 날라라."**

- **동작 방식:**
    - 총 실행 횟수(`iterations`)가 고정됩니다.
    - VU들은 하나의 작업 큐를 공유하며, 할당량이 끝나면 테스트가 즉시 종료됩니다.
- **특징:**
    - 시스템 성능이 좋으면 테스트가 빨리 끝납니다. (시간 = 작업량 / TPS)
    - **처리량(TPS)** 측정에 가장 적합합니다.
- **적합한 시나리오:**
    - **High/Extreme Load:** "재고 1,000개가 소진될 때까지 얼마나 걸리는가?"

```javascript
// k6 설정 예시
executor: 'shared-iterations',
vus: 100,
iterations: 1000,
```

### 1.2 Constant VUs (고정 동시 접속) - "동시성 중심"
> **"100명이 10분 동안 쉬지 않고 문을 두드려라."**

- **동작 방식:**
    - 지정된 시간(`duration`) 동안 VU 수(`vus`)가 일정하게 유지됩니다.
    - 반복 횟수는 보장되지 않으며, 시간이 되면 강제로 종료됩니다.
- **특징:**
    - **서버의 안정성(Stability)** 테스트에 적합합니다.
    - 특정 동시 접속자 수를 유지하며 에러율이나 지연 시간을 관찰할 때 사용합니다.
- **적합한 시나리오:**
    - **Hell Test (선착순):** "5,000명이 동시에 접속해서 30초 동안 클릭할 때 서버가 죽는가?"

```javascript
// k6 설정 예시
executor: 'constant-vus',
vus: 5000,
duration: '30s',
```

### 1.3 Ramping Arrival Rate (단계적 부하) - "스트레스 중심"
> **"처음엔 초당 10명, 나중엔 초당 2,000명이 오게 해봐."**

- **동작 방식:**
    - VU 수가 아니라 **RPS (초당 요청 수)**를 목표(`target`)로 삼습니다.
    - k6가 목표 RPS를 맞추기 위해 필요한 만큼 VU를 동적으로 생성합니다.
- **특징:**
    - **Knee Point (한계점)** 탐색에 최적화되어 있습니다.
    - 시스템이 언제 뻗는지, 복구는 되는지 확인할 때 씁니다.
- **적합한 시나리오:**
    - **Stress Test:** "점점 부하를 높여가며 한계 성능 측정"

```javascript
// k6 설정 예시
executor: 'ramping-arrival-rate',
stages: [
    { duration: '1m', target: 500 }, // RPS 500 도달
    { duration: '2m', target: 2000 }, // RPS 2000 도달
],
```

---

## 2. 프로젝트 적용 전략 (Strategy V2)

기존의 모호했던 스크립트를 위의 개념에 맞춰 3가지 유형으로 재편합니다.

| 유형 | 파일명 (New) | 기존 매핑 | Executor | 목적 | CLI 제어 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Capacity** | `capacity.js` | High/Extreme | **Shared Iterations** | TPS (처리량) 측정 | `VUS`, `ITERATIONS` |
| **Contention** | `contention.js` | Hell Test | **Constant VUs** | 동시성 경합/안정성 | `VUS`, `DURATION` |
| **Stress** | `stress.js` | (없음) | **Ramping Arrival** | 한계점(Knee Point) 탐색 | `TARGET_RPS` |

### 2.1 Warm-up 전략
Main Test와 동일한 스크립트를 쓰되, **부하량만 줄여서** 짧게 실행합니다.

- **Warm-up 스크립트:** `warmup.js` (신규 생성)
- **설정:** Shared Iterations (VUs: 10, Iterations: 100)
- **목적:** JVM JIT 컴파일, Connection Pool 초기화

---

## 3. k6 용어 사전

- **VU (Virtual User):** 가상의 사용자. 스레드와 유사하게 동작.
- **Iteration:** 스크립트(`default function`)가 처음부터 끝까지 한 번 실행되는 것.
- **Duration:** 테스트가 지속되는 시간.
- **RPS (Requests Per Second):** 초당 요청 처리 수. (TPS와 혼용되나 k6에서는 주로 Iterations/s)
- **Check:** 응답이 성공했는지 검증하는 로직 (성능에 영향 없음).
- **Threshold:** 테스트 성공/실패를 가르는 기준 (예: p95 < 500ms).

---
*Created by Gemini CLI*
