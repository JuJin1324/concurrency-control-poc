# 📊 Stress Test Report (안정성 임계점 분석)

## 🎯 테스트 목적

RPS(Requests Per Second)를 점진적으로 증가시키며 시스템의 **임계점(Knee Point)**을 탐색하고, 고부하 상황에서의 지연 시간 변화를 분석합니다.

**핵심 질문 (Key Questions):**
- "사용자가 점점 늘어날 때, 응답 속도가 어디까지 안정적으로 유지되는가?"
- "Redis 분산 락의 네트워크 오버헤드는 몇 RPS부터 사용자 경험을 해치기 시작하는가?"
- "2 vCPU의 제한된 환경에서 각 방식이 견딜 수 있는 최대 부하는 얼마인가?"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (AWS t3.small Profile)
| 서비스 | 자원 할당 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 2.0 vCPU, 2GB RAM | Java 21, Virtual Threads |
| **MySQL 8.0** | 2.0 vCPU, 2GB RAM | InnoDB, HikariCP Max 50 |
| **Redis 7.0** | 2.0 vCPU, 1.37GB RAM | Lettuce Max 50 |
| **Test Runner** | **k6 in Docker** | **내부망 직접 통신 (병목 제거)** |

### 1.2 Scenario Spec (Ramping Load)
| 항목 | 설정값 | 비고 |
|:---|:---|:---|
| **Stock** | 10,000개 | 충분한 재고 (품절 방지) |
| **Target RPS** | **1,000 req/s** | 0에서 1,000까지 선형 증가 |
| **Executor** | `ramping-arrival-rate` | 지정된 RPS 도달을 목표로 VU 자동 조절 |
| **Duration** | 4m 30s | 완만한 부하 증가 (Ramping) |

---

## 🚀 실행 가이드 (Execution)

이 테스트는 **Docker 컨테이너 내부에서 k6를 실행**하여 네트워크 오버헤드를 최소화하며, 각 테스트 간의 **격리성(Isolation)**을 보장하기 위해 매회 인프라를 재시작합니다.

### 1. 테스트 격리 및 실행
```bash
# 1. 인프라 완전 재시작 (격리성 보장 - 필수)
make clean && make up && sleep 15

# 2. 데이터 초기화 (재고 10,000개)
make reset-10k

# 3. Stress Test 수행 (Target RPS = 1,000)
# METHOD: [lua-script | optimistic | pessimistic | redis-lock]
make test-stress METHOD=[method] TARGET_RPS=1000
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

1.  **RPS 달성률 (Throughput):**
    *   목표(1,000 RPS)를 실제 시스템이 받아냈는지 확인합니다. 받아내지 못했다면 시스템 용량 초과입니다.
2.  **Knee Point (Latency p95):**
    *   부하는 선형적으로 늘어나는데, Latency가 갑자기 지수적으로 튀어 오르는 지점을 찾습니다.
    *   일반적으로 **100ms**를 넘어가면 사용자가 지연을 느끼기 시작하는 신호로 봅니다.

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Standard Spec (2 vCPU) / Docker Isolated

| 순위 | 방식 (Method) | 목표 RPS | 달성 RPS | p95 Latency | 결과 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Lua Script** | 1,000 | 1,000 | **3.78 ms** | 🌟 **Excellent** |
| **2** | **Pessimistic** | 1,000 | 1,000 | 5.18 ms | ✅ Stable |
| **3** | **Optimistic** | 1,000 | 1,000 | 5.25 ms | ✅ Stable |
| **4** | **Redis Lock** | 1,000 | 1,000 | **33.75 ms** | ⚠️ Noticeable Overhead |

---

## 💡 분석 및 결론 (Insights)

### 1. 전반적인 서비스 안정성 확보
- **관찰:** 모든 방식이 목표 부하인 **1,000 RPS**를 성공적으로 처리했습니다.
- **해석:** p95 응답 속도 역시 가장 느린 Redis Lock조차 **33ms**로, 일반적인 웹 서비스 기준(200ms)을 훨씬 상회하는 우수한 성능입니다.
- **결론:** 1,000 RPS 수준의 트래픽에서는 어떤 방식을 선택해도 안정적인 서비스 운영이 가능합니다.

### 2. Redis Distributed Lock의 오버헤드 확인
- **관찰:** 타 방식(3~5ms) 대비 약 **6~9배 높은 지연 시간(33ms)**이 관측되었습니다.
- **원인:** Redis Lock 획득 및 해제를 위한 **네트워크 왕복(RTT)** 비용이 부하가 증가함에 따라 누적되기 때문입니다.
- **의미:** 현재는 문제없지만, 트래픽이 2,000~3,000 RPS로 증가할 경우 가장 먼저 병목(Knee Point)에 도달할 후보입니다.

### 3. Lua Script의 압도적 효율성 (Bonus Test)
- 1,000 RPS 테스트에서 리소스가 너무 많이 남아, **2,000 RPS**까지 추가 테스트를 수행했습니다.
    - **결과:** 2,000 RPS 달성 / p95 Latency **2.76ms**
- **결론:** Lua Script는 2 vCPU 환경의 물리적 한계를 최대한으로 활용할 수 있는 가장 효율적인 소프트웨어적 해법입니다.

---

## 🚀 확장 전략 (Scalability Strategy)

### 1. Knee Point 대응 전략
- **Scale-up:** Redis Lock 사용 시 지연 시간이 50ms를 초과하기 시작하면, App/Redis 서버의 CPU를 증설하여 처리 속도를 높여야 합니다.
- **Method Change:** 인프라 증설 없이 성능을 높이고 싶다면, Redis Lock을 **Lua Script**로 전환하는 것만으로도 처리 용량을 2배 이상 확보할 수 있습니다.

### 2. 안전 장치 (Safety Net)
- **Optimistic Lock:** 2,000 RPS 이상의 과부하 상황에서는 재시도(Retry) 폭증으로 인한 **시스템 마비(Hang)** 위험이 있습니다. 반드시 **Rate Limiter**를 앞단에 두어 유입량을 제어해야 합니다.