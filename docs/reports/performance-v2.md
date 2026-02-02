# 📊 Concurrency Control Performance Report (V2)

## 🏆 Executive Summary (요약)

4가지 동시성 제어 방식에 대해 **격리된 환경(Isolated Environment)**에서 **3가지 핵심 시나리오(Capacity, Contention, Stress)**를 검증한 결과입니다.

| 순위 | 방식 (Method) | 추천 대상 | 핵심 특징 |
|:---:|:---|:---|:---|
| 🥇 | **Lua Script** | **High Traffic** | **압도적 1위.** DB 락 대비 2.5배 성능, 경합 상황에서도 완벽한 방어력. |
| 🥈 | **Pessimistic Lock** | **General / Enterprise** | **가장 안정적.** 별도 인프라 없이 RDBMS만으로 준수한 성능(600+ TPS) 보장. |
| 🥉 | **Optimistic Lock** | **Low Contention** | **양날의 검.** 재고가 넉넉할 땐 재시도 비용으로 느리지만, 품절(Sold Out) 시엔 가장 빠름. |
| 4 | **Redis Lock** | **Easy Implementation** | **비효율적.** 구현은 쉽지만 네트워크 오버헤드로 인해 성능 효율이 가장 낮음. |

---

## 1. 테스트 환경 (Environment)

모든 테스트는 `k6`를 Docker 내부망에서 실행하여 네트워크 병목을 제거했으며, 매 테스트 전 인프라를 재시작하여 **격리성(Isolation)**을 확보했습니다.

- **AWS t3.small Profile (Standard):** 2 vCPU, 2GB RAM (Capacity, Stress Test)
- **AWS t3.xlarge Profile (Scale-up):** 4 vCPU, 4GB RAM (Contention Test - 5,000 VUs)

---

## 2. 시나리오별 상세 분석

### ① [Capacity Report (상세보기)](capacity-report.md)
*재고가 충분한 상황(10,000개)에서 시스템이 낼 수 있는 최대 성능(TPS) 측정*

| Method | TPS (req/s) | p95 Latency | 비교 |
|:---|:---:|:---:|:---|
| **Lua Script** | **1,583** | **120ms** | **DB 방식 대비 2.5배 빠름** |
| **Pessimistic** | 618 | 271ms | RDBMS의 한계 성능 |
| **Optimistic** | 455 | 310ms | 충돌 재시도(Retry) 비용 발생 |
| **Redis Lock** | 440 | 384ms | 네트워크 RTT 병목 |

> **Insight:** 단순 재고 차감 로직에서 DB의 I/O 작업은 병목이 됩니다. Lua Script는 이 연산을 메모리(Redis)로 옮겨 처리량을 극대화합니다.

### ② [Contention Report (상세보기)](contention-report.md)
*재고가 적은 상황(100개)에 5,000명의 사용자가 동시 접속하는 '선착순 이벤트' 상황*

| Method | TPS (req/s) | 성공률 | 특징 |
|:---|:---:|:---:|:---|
| **Lua Script** | **10,539** | 100% | **품절 후에도 트래픽 완벽 처리** |
| **Optimistic** | 6,238 | 100% | **Fast Fail 효과** (재고 없으면 즉시 실패) |
| **Pessimistic** | 3,773 | 100% | 대기열 발생으로 응답 지연 |
| **Redis Lock** | 1,218 | **57%** | **타임아웃 다수 발생 (서비스 불가)** |

> **Insight:** 재고가 '0'이 된 이후의 트래픽 처리가 핵심입니다. Optimistic Lock은 읽기만 하고 실패하므로 이 구간에서 비관적 락을 역전했습니다. Redis Lock은 5,000명의 락 쟁탈전을 감당하지 못하고 무너졌습니다.

### ③ [Stress Report (상세보기)](stress-report.md)
*부하를 0에서 1,000 RPS까지 점진적으로 증가시키며 지연 시간(Latency) 변화 관찰*

| Method | 1,000 RPS 도달? | p95 Latency | 상태 |
|:---|:---:|:---:|:---|
| **Lua Script** | ✅ Yes | **3.78ms** | 여유 (2,000 RPS도 가능) |
| **Pessimistic** | ✅ Yes | 5.18ms | 안정적 |
| **Optimistic** | ✅ Yes | 5.25ms | 안정적 |
| **Redis Lock** | ✅ Yes | **33.75ms** | **네트워크 오버헤드 관찰됨** |

> **Insight:** 1,000 RPS(초당 1,000명) 트래픽은 2 vCPU 환경에서 4가지 방식 모두 감당 가능합니다. 단, Redis Lock은 구조적 한계로 인해 타 방식 대비 응답 속도가 느립니다.

---

## 3. 최종 평가표 (Final Scorecard)

| 평가 항목 | Lua Script | Pessimistic | Optimistic | Redis Lock |
|:---|:---:|:---:|:---:|:---:|
| **처리량 (Throughput)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐ |
| **반응속도 (Latency)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **데이터 정합성** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **구현 난이도** | ⭐⭐ (Lua) | ⭐⭐⭐⭐⭐ (JPA) | ⭐⭐⭐⭐ (JPA) | ⭐⭐⭐ (Redisson) |
| **인프라 비용 효율** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐ |

---

## 🚀 Final Recommendation (도입 가이드)

### 상황 A: "우리는 트래픽이 엄청나요. (e.g., 선착순 예매, 핫딜)"
👉 **무조건 Redis Lua Script를 사용하세요.**
- DB 부하를 0으로 만들고, Redis의 단일 스레드 원자성을 이용해 최대의 처리량을 확보할 수 있습니다.

### 상황 B: "적당한 트래픽에, 인프라 추가 없이 DB만 쓰고 싶어요."
👉 **Pessimistic Lock (비관적 락)이 정답입니다.**
- 가장 구현하기 쉽고(JPA `@Lock`), 데이터 무결성을 DB 레벨에서 강력하게 보장합니다. Redis 운영 비용이 들지 않습니다.

### 상황 C: "충돌이 거의 없는 환경이에요."
👉 **Optimistic Lock (낙관적 락)을 고려해 보세요.**
- 락을 걸지 않으므로 평소 성능은 좋지만, 재고 차감 같은 '쓰기 위주' 로직보다는 '읽기 위주' 로직에 더 적합합니다.

### 🚫 주의: "Redis Distributed Lock (Redisson)은요?"
👉 **추천하지 않습니다.**
- Lua Script보다 느리고, 비관적 락보다 관리가 복잡합니다. 분산 DB 환경(Sharding) 등 특수한 제약 사항이 없다면 굳이 선택할 이유가 없습니다.
