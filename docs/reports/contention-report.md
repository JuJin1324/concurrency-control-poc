# 📊 Contention Test Report (극한 경합 방어력 분석)

## 🎯 테스트 목적

극도로 높은 동시성(Concurrency) 환경에서 시스템이 붕괴되지 않고 요청을 안정적으로 처리하는지 검증하고, 재고 소진 후의 시스템 방어력을 측정합니다.

**핵심 질문 (Key Questions):**
- "5,000명이 동시에 100개의 재고를 두고 경쟁할 때 시스템이 멈추지 않는가?"
- "재고가 정확히 0으로 마감되는가? (Over-selling 방지)"
- "어떤 방식이 대규모 트래픽 유입 시 자원을 가장 효율적으로 사용하는가?"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (AWS t3.xlarge Profile - Scale-up)
| 서비스 | 자원 할당 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 4.0 vCPU, 4GB RAM | Java 21, Virtual Threads |
| **MySQL 8.0** | 4.0 vCPU, 4GB RAM | InnoDB, HikariCP Max 50 |
| **Redis 7.0** | 4.0 vCPU, 3GB RAM | Lettuce Max 50 |
| **Test Runner** | **k6 in Docker** | **내부망 직접 통신 (병목 제거)** |

### 1.2 Tuning Config
*   **스레드 모델:** `spring.threads.virtual.enabled: true`
*   **로그 레벨:** `INFO/WARN`
*   **OS 튜닝:** `ulimits -n 65535`

### 1.3 Scenario Spec
| 항목 | 설정값 | 비고 |
|:---|:---|:---|
| **Stock** | 100개 | 극한의 경합 유도 |
| **VUs** | 5,000 | 선착순 5,000명 시나리오 |
| **Duration** | 30s | 지속 부하 |
| **Executor** | `constant-vus` | 고정된 동시 접속자 유지 |

---

## 🚀 실행 가이드 (Execution)

이 테스트는 **Docker 컨테이너 내부에서 k6를 실행**하여 네트워크 오버헤드를 최소화하며, 각 테스트 간의 **격리성(Isolation)**을 보장하기 위해 매회 인프라를 재시작합니다.

### 1. 테스트 격리 및 실행
```bash
# 1. 인프라 완전 재시작 (격리성 보장 - 필수)
make clean && make up && sleep 15

# 2. 데이터 초기화 (재고 100개)
make reset

# 3. 테스트 수행 (Warm-up 자동 포함)
make test-contention METHOD=[lua-script | optimistic | pessimistic | redis-lock] VUS=5000 DURATION=30s
```

### 2. 정합성 검증
```bash
make show-db     # Expected: 0
make show-redis  # Expected: 0
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

1.  **TPS (처리량) = "광클 방어력"**
    *   재고 소진 후 몰려드는 사용자에게 얼마나 빨리 "매진(409)"을 응답해주는가.
    *   높은 TPS = 시스템 자원을 낭비하지 않고 불필요한 트래픽을 빠르게 쳐냈음을 의미.

2.  **Latency (p95) = "락 대기 고통"**
    *   사용자가 결과를 듣기 위해 줄을 서서 기다린 시간. 락 경합이 심할수록 급증함.

3.  **성공률 (Logic):**
    *   100/5,000 = 약 2% 미만이어야 정상 (재고가 100개뿐이므로).

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Docker Internal Network (5,000 VUs) + **Scale-up (4 vCPU / 4GB RAM)**

| 순위 | 방식 (Method) | TPS (req/s) | p95 Latency | 시스템 안정성 (Stability) | 비고 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Lua Script** | **10,539** | **1.06 s** | **100%** | **압도적 1위 (Stable)** |
| **2** | **Optimistic Lock** | **6,238** | **1.91 s** | **100%** | Fast Fail 효과 |
| **3** | **Pessimistic Lock** | **3,773** | **2.94 s** | **100%** | 안정적 방어 |
| **4** | **Redis Lock** | **1,218** | **5.54 s** | **57%** | **불안정 (43% 실패)** |

> **참고:** '시스템 안정성'은 서버가 요청을 정상적으로 처리(200 OK 또는 409 Conflict 반환)한 비율입니다. Redis Lock은 43%의 요청이 타임아웃 또는 5xx 에러로 실패하여 서비스가 불가능한 상태임을 나타냅니다.

---

## 💡 분석 및 결론 (Insights)

### 1. Lua Script (10,539 TPS) - The Event Savior
- **유일한 해결책:** 5,000명의 동시 접속자가 몰리는 상황에서 유일하게 **10,000 TPS 이상**을 기록하며 안정적으로 서비스를 지탱했습니다.
- **리소스 효율:** 락 경합 없는 구조 덕분에 늘어난 하드웨어 자원(4 vCPU)을 온전히 요청 처리에 쏟아부을 수 있었습니다.

### 2. Optimistic Lock (6,238 TPS) - The Unexpected Hero
- **Fast Fail의 승리:** 재고가 순식간에 동나는 상황에서는 락을 잡고 기다리는 것보다, 빠르게 실패(409)하고 연결을 끊어주는 것이 전체 시스템 처리량에 도움이 됨을 증명했습니다.
- **Pessimistic보다 빠름:** 일반적인 상황과 달리, 극초단기 이벤트에서는 낙관적 락이 비관적 락보다 더 높은 처리량을 보였습니다.

### 4.3 Pessimistic Lock (3,773 TPS) - The Reliable Buffer
*   **안정성과 비용의 교환:** 100%의 시스템 안정성을 보장하지만, 모든 요청을 DB 락 대기열(Queue)에 세우는 과정에서 지연 시간이 발생했습니다.
*   **Latency 증가 원인:** 5,000명의 유저가 100개의 재고를 두고 경쟁하므로, 앞선 요청들이 처리될 때까지 대기하는 시간(Blocking)이 누적되어 p95 지연 시간이 2.94초까지 증가했습니다.

### 4.4 Redis Distributed Lock (1,218 TPS) - The Scalability Wall
*   **확장 실패:** 인프라를 2배로 증설했음에도 불구하고 성능 향상이 미미했으며, 오히려 43%의 요청이 타임아웃 등으로 실패했습니다.
*   **구조적 한계:** 5,000개의 클라이언트가 단일 Redis 인스턴스에 락을 요청하고 해제하는 네트워크 트래픽(Packet Storm)을 감당할 수 없습니다.

### 4.5 Latency 급증 원인 분석 (vs Capacity Test)
*   **Capacity Test (120ms) -> Contention Test (1.06s):** 재고가 100개로 줄고 접속자가 5,000명으로 늘어나면서 **'경합 밀도(Contention Density)'**가 수십 배 증가했습니다.
*   **대기열 효과:** 아무리 처리가 빠른 Lua Script라도, Redis 싱글 스레드에서 처리해야 할 명령어가 5,000개씩 쌓이게 되면 뒷순서의 요청은 필연적으로 대기하게 됩니다. 이것이 Latency 증가의 주원인입니다.

### 4. 결론 (Conclusion)
**"선착순 5,000명 이벤트"를 하려면 최소 4 vCPU 이상의 서버와 Lua Script 방식이 필수적입니다.**
인프라 스펙업(Scale-up)과 논-블로킹(Non-blocking) 아키텍처의 결합만이 대규모 트래픽을 견딜 수 있습니다.

---

## 🚀 확장 전략 (Scalability Strategy)

5,000 VUs 이상의 트래픽을 처리하기 위한 인프라 확장 가이드입니다.

### 1. RDBMS (Optimistic / Pessimistic)
*   **Scale-up (수직 확장):** **효과 매우 큼.**
    *   MySQL은 멀티 스레드를 지원하므로 CPU 코어가 늘어날수록 동시 처리량과 백그라운드 I/O 성능이 선형적으로 증가합니다.
    *   특히 낙관적 락(Optimistic)의 재시도 연산 비용을 감당하려면 고성능 CPU가 필수적입니다.
*   **Scale-out (수평 확장):** **효과 제한적.**
    *   재고 차감은 '쓰기(Write)' 작업이므로 결국 Master DB 하나에 부하가 집중됩니다.
    *   이를 해결하려면 **DB 샤딩(Sharding)**을 도입하여 상품 ID 별로 물리적 DB를 분리해야 합니다.

### 2. Redis (Lua Script / Distributed Lock)
*   **Scale-up (수직 확장):** **효과 제한적.**
    *   Redis는 요청 처리가 **싱글 스레드**이므로, 코어 수를 늘려도 단일 키(Key) 처리 속도는 빨라지지 않습니다.
    *   코어 수보다는 **CPU 클럭 속도(GHz)**와 메모리 대역폭이 중요합니다.
*   **Scale-out (수평 확장):** **조건부 효과.**
    *   Redis Cluster를 쓰면 키 분산이 가능하지만, **단일 상품(Hot Key)**에 대한 이벤트라면 결국 1개의 노드로 트래픽이 몰립니다.
    *   따라서 인프라 확장보다는 **Lua Script**와 같은 아키텍처 최적화가 선행되어야 합니다.