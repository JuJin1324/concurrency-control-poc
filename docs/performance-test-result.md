# 성능 테스트 결과 리포트 (Final)

**작성일:** 2026-01-28
**작성자:** Gemini Agent

---

## 1. 테스트 개요

### 1.1 목적
다양한 트래픽 시나리오(일반 부하, 극한 부하, 선착순 이벤트)에서 4가지 동시성 제어 방식의 **성능(Latency, Throughput)**과 **안정성(Stability)**을 다각도로 검증함.

### 1.2 테스트 환경
- **Host:** Mac Studio M1 Max (32GB)
- **환경 설정 (Spec):**
  - **기본 (Base):** Tomcat Threads 200, CPU 2.0 (High/Extreme/Recovery 시나리오)
  - **튜닝 (Tuned):** Tomcat Threads 500, CPU 4.0 (Hell Test 시나리오 전용)

---

## 2. 시나리오별 테스트 결과

### 2.1 시나리오 A: High Load (재고 1,000개 / Max 2,000 RPS)
- **목적:** 일반적인 대규모 트래픽 상황에서의 효율성 비교.

| Method | 지연 시간 p(95) (Success) | 최대 동시 접속자 (Max VUs) | 결과 |
| :--- | :--- | :--- | :--- |
| **Pessimistic** | 6.34ms | 3 | **Stable.** 매우 안정적으로 처리됨. |
| **Optimistic** | **6.15ms** | **3** | **Unexpectedly Good.** 충돌 분산 효과로 비관적 락보다 빠름. |
| **Redis Lock** | 6.52ms | **5,000** | **Bottleneck.** 락 대기 시간으로 인해 VU 고갈 및 요청 드랍 발생. |
| **Lua Script** | 5.87ms | 4 | **Best Efficiency.** 가장 적은 리소스로 처리. |

### 2.2 시나리오 B: Extreme Load (재고 10,000개 / Max 2,000 RPS)
- **목적:** 시스템 물리적 한계 시험 (Redis Lock 제외).

| Method | 지연 시간 p(95) | 평균 지연 시간 | 최대 동시 접속자 | 결과 |
| :--- | :--- | :--- | :--- | :--- |
| **Pessimistic** | 3.52ms | 2.25ms | 5 | Warm-up 효과로 성능 30% 향상. |
| **Optimistic** | **3.35ms** | 3.00ms | **3** | 1만 건 충돌에도 끄떡없는 성능. |
| **Lua Script** | 3.76ms | **1.85ms** | 5 | 평균 응답 속도 최강. |

### 2.3 시나리오 C: Recovery Baseline (재고 100개)
- **목적:** Extreme 테스트 직후 시스템 회복력 검증.
- **결과:** 지연 시간 p(95) **2.29ms** 기록. 리소스 누수 없이 완벽하게 복구됨.

---

## 3. 시나리오 D: Hell Test (선착순 이벤트 / 재고 100개 / 5,000 VUs)

**조건:** 튜닝 스펙 (Tomcat 500 / CPU 4.0) 적용. 순간적인 폭주(Burst) 상황.

| 순위 | Method | 처리량 (RPS) | 지연 시간 p(95) | 결과 |
| :--- | :--- | :--- | :--- | :--- |
| 🥇 | **Lua Script** | **3,602 /s** | **1.09s** | **압도적 성능.** DB I/O가 없어 가장 빠름. |
| 🥈 | **Optimistic** | 3,080 /s | 1.27s | **Fast Fail.** 충돌 시 즉시 리턴되어 대기 시간이 짧음. |
| 🥉 | **Pessimistic** | 1,416 /s | 3.15s | **Blocking.** DB Lock 대기로 인해 처리량이 절반 이하로 떨어짐. |
| 4 | **Redis Lock** | 858 /s | 5.21s | **Bottleneck.** 분산 락 획득 오버헤드 심각. |

> **참고:** 모든 방식은 데이터 정합성(재고 0)을 완벽하게 보장했습니다.

---

## 4. 심층 분석: 한계와 극복 (Engineering Deep Dive)

단순히 결과만 기록한 것이 아니라, 시스템의 물리적 임계점을 찾기 위한 단계별 검증을 수행했습니다.

### 4.1 The Wall: 10,000 VUs의 벽
- **현상:** 어떤 Lock 방식을 써도 약 9%의 **연결 오류(Connection Error)** 발생.
- **분석:** 단일 서버 OS의 TCP Ephemeral Port 고갈 및 컨텍스트 스위칭 비용 급증이 원인.
- **결론:** 1만 명 이상의 선착순 트래픽은 **Scale-out**과 **대기열 시스템**이 필수적임.

### 4.2 튜닝의 효과: 기본(Default) vs 튜닝(Tuned) (5,000 VUs)
- **기본 스펙 (Threads 200 / CPU 2.0):** Warm-up 후에도 **7.08초** 소요 (불안정).
- **튜닝 스펙 (Threads 500 / CPU 4.0):** **1.09초**로 안정적 처리 가능.
- **결론:** 대규모 동시 접속 환경에서는 비즈니스 로직(Lock) 뿐만 아니라 **WAS 스레드 튜닝**이 성능에 결정적인 영향을 미침.

---

## 5. 종합 결론

1.  **Best Performance:** **Lua Script** (평균 응답 속도 및 리소스 효율 최상)
2.  **Best Stability:** **Pessimistic Lock** (어떤 부하 상황에서도 예측 가능한 데이터 정합성)
3.  **Surprise:** **Optimistic Lock** (웹 환경에서는 의외로 강력한 성능과 빠른 피드백 제공)
4.  **Caution:** **Redis Lock** (단일 서버 환경에서는 관리 비용이 더 큼)
5.  **Engineering Insight:** 동시성 제어 기술만으로는 대규모 트래픽을 감당할 수 없으며, **인프라 튜닝(Scale-up)**과 **아키텍처 확장(Scale-out/Queue)**이 반드시 동반되어야 함.
