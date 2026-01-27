# 성능 테스트 결과 리포트

**작성일:** 2026-01-27
**작성자:** Gemini Agent

---

## 1. 테스트 개요

### 1.1 목적
동시성 제어 방식 4가지(Pessimistic, Optimistic, Redis Lock, Lua Script)의 성능(Latency)과 안정성을 정량적으로 비교하여, 상황별 최적의 솔루션을 도출하기 위함.

### 1.2 테스트 환경
- **H/W Resource (Docker Limits):**
  - Application: 2 CPU, 2GB Memory
  - MySQL 8.0: 2 CPU, 1GB Memory
  - Redis 7.0: 1 CPU, 512MB Memory
- **Network:** Docker Bridge Network (Localhost)
- **Tool:** k6 v0.56.0
- **Scenario:** Spike Test (재고 100개, 초당 100회 요청, 총 1,000회 수행)

---

## 2. 테스트 결과 요약

| 방식 (Method) | TPS (Throughput) | Latency p(95) (Success) | Latency p(95) (Total) | Success Rate | 최종 정합성 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Pessimistic Lock** | 100.0/s | **4.82ms** | 6.06ms | 10% (100/1000) | 0 (OK) |
| **Optimistic Lock** | 100.0/s | **4.75ms** | 6.60ms | 10% (100/1000) | 0 (OK) |
| **Redis Lock** | 100.0/s | 5.89ms | 5.99ms | 10% (100/1000) | 0 (OK) |
| **Lua Script** | 100.0/s | 5.89ms | 6.02ms | 10% (100/1000) | "0" (Redis OK) |

> **Note:**
> - **TPS:** k6 스케줄러 설정(100 RPS)에 의해 모든 방식이 동일하게 측정됨.
> - **Latency p(95) (Success):** 실제 재고 차감에 성공한 요청들의 응답 속도. 실패 응답(409 Conflict)은 제외.

---

## 3. 결과 상세 분석

### 3.1 Pessimistic Lock (비관적 락)
- **성능:** 4.82ms로 매우 우수함.
- **원인:** DB가 로컬 네트워크에 있어 락 획득 대기 시간이 짧았고, 네트워크 오버헤드가 최소화됨.
- **특징:** 가장 안정적이며 데이터 정합성이 강력하게 보장됨.

### 3.2 Optimistic Lock (낙관적 락)
- **성능:** 4.75ms로 4가지 방식 중 수치상 가장 빠름.
- **원인:** k6의 HTTP 요청 간격(Pacing)이 자연스럽게 경합을 분산시켜, 충돌 후 재시도 비용이 예상보다 크지 않았음. (단위 테스트의 극한 경합 상황과는 다름)
- **특징:** 실제 트래픽 환경에서는 꽤 효율적일 수 있음을 시사함.

### 3.3 Redis Lock (분산 락)
- **성능:** 5.89ms로 비관적 락 대비 약 1ms 느림.
- **원인:** Application <-> Redis 간의 네트워크 왕복(RTT) 비용이 추가됨. (Lock 획득 + 해제)
- **특징:** DB 부하를 줄일 수 있다는 장점이 있으나, 로컬 테스트에서는 성능상 이점보다 네트워크 비용이 더 부각됨.

### 3.4 Lua Script (원자적 연산)
- **성능:** 5.89ms로 Redis Lock과 동일 수준.
- **원인:** Redis 내부 연산은 빠르지만, 결국 Application <-> Redis 간의 RTT가 전체 응답 시간을 지배함.
- **한계:** 현재 구현에서는 DB 동기화(Write-Back)가 빠져 있어 완전한 비교는 어려우나, Redis 처리 속도 자체는 준수함.

---

## 4. 결론

1.  **단일 DB 환경:** **Pessimistic Lock**이 가장 확실하고 성능도 우수함. 굳이 Redis를 도입할 필요 없음.
2.  **분산 환경:** DB 부하 분산이 필요하다면 **Redis Lock**이 유효하나, 네트워크 지연(RTT)을 고려해야 함.
3.  **극한의 성능:** **Lua Script**가 이론상 가장 빠르지만, DB 동기화 전략(비동기 큐 등)이 복잡해질 수 있음.
4.  **Optimistic Lock 재평가:** 웹 환경에서는 생각보다 성능 저하가 크지 않을 수 있으므로, 충돌 빈도가 낮다면 충분히 고려할 만함.
