# 📊 Capacity Test Report (최대 처리량 분석)

## 🎯 테스트 목적

시스템이 안정적인 상태에서 처리할 수 있는 **최대 처리량(Max Throughput/TPS)**을 측정하고 각 방식별 성능 격차를 분석합니다.

**핵심 질문 (Key Questions):**
- "재고 10,000개를 처리하는 데 가장 빠른 방식은 무엇인가?"
- "각 동시성 제어 방식의 순수 처리 성능(TPS) 격차는 어느 정도인가?"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (AWS t3.small Profile)
| 서비스 | 자원 할당 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 2.0 vCPU, 2GB RAM | Java 21, Virtual Threads |
| **MySQL 8.0** | 2.0 vCPU, 2GB RAM | InnoDB, HikariCP Max 50 |
| **Redis 7.0** | 2.0 vCPU, 1.37GB RAM | Lettuce Max 50 |
| **Test Runner** | **k6 in Docker** | **내부망 직접 통신 (병목 제거)** |

### 1.2 Tuning Config
*   **스레드 모델:** `spring.threads.virtual.enabled: true`
*   **로그 레벨:** `INFO/WARN` (성능 저하 방지)
*   **OS 튜닝:** `ulimits -n 65535`

### 1.3 Scenario Spec
| 항목 | 설정값 | 비고 |
|:---|:---|:---|
| **Stock** | 10,000개 | 충분한 작업량 |
| **VUs** | 100 | 적절한 동시성으로 작업 집중 |
| **Iterations** | 10,000 | 재고 소진 시 종료 |
| **Executor** | `shared-iterations` | 정해진 작업량을 얼마나 빨리 끝내는지 측정 |

---

## 🚀 실행 가이드 (Execution)

이 테스트는 **Docker 컨테이너 내부에서 k6를 실행**하여 네트워크 오버헤드를 최소화하며, 각 테스트 간의 **격리성(Isolation)**을 보장하기 위해 매회 인프라를 재시작합니다.

### 1. 테스트 격리 및 실행
```bash
# 1. 인프라 완전 재시작 (격리성 보장 - 필수)
# 이전 테스트의 커넥션 풀, OS 캐시 등을 초기화합니다.
make clean && make up && sleep 15

# 2. 데이터 초기화 (데이터 리셋 10,000개)
make reset-10k

# 3. 테스트 수행 (Warm-up 자동 포함)
# METHOD: [lua-script | optimistic | pessimistic | redis-lock]
make test-capacity METHOD=lua-script VUS=100 ITERATIONS=10000
```

### 2. 정합성 검증
```bash
make show-db     # Expected: 0
make show-redis  # Expected: 0
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

이 테스트 데이터 분석 시 집중해야 할 포인트입니다.

1.  **TPS (Throughput):**
    *   **의미:** 초당 재고 차감 처리 건수.
    *   **판단 기준:** 높을수록 우수함. 이 테스트의 가장 중요한 지표입니다.

2.  **Latency (p95):**
    *   **의미:** 하위 95% 요청의 최대 응답 시간.
    *   **판단 기준:** 500ms 미만 권장. 처리량이 높아도 Latency가 너무 길면 사용자 경험이 나쁩니다.

3.  **Error Rate:**
    *   **HTTP 500 / Timeout:** 시스템 장애. 0%여야 정상입니다.

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Docker Internal Network (Host Proxy Bypass)

| 순위 | 방식 (Method) | TPS (req/s) | p95 Latency | 성공률 (Logic) | 비고 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Lua Script** | **1,583** | **120 ms** | 100% | **압도적 1위 (2.5x Faster)** |
| **2** | **Pessimistic** | 618 | 271 ms | 100% | 안정적 2위 |
| **3** | **Optimistic** | 455 | 310 ms | 99.98% | 재시도 비용 발생 |
| **4** | **Redis Lock** | 440 | 384 ms | 100% | 네트워크 오버헤드 큼 |

---

## 💡 분석 및 결론 (Insights)

### 1. Lua Script의 압도적 효율성
- **관찰:** Pessimistic Lock 대비 2.5배, Redis Lock 대비 3.5배 높은 처리량 기록.
- **원인:** Redis 내부에서 원자적으로 실행되어 `Lock Acquire/Release` 과정이 없고, 네트워크 왕복(RTT)이 최소화됨.
- **결론:** 단순 재고 차감 로직에는 Lua Script가 최상의 선택지임.

### 2. Pessimistic Lock의 가치 재발견
- **관찰:** 전통적인 DB 락 방식임에도 Redis 분산락보다 약 40% 더 높은 성능을 보임.
- **원인:** 분산 락 획득을 위한 네트워크 비용보다, DB 내부에서 Row Lock을 대기하는 비용이 더 저렴함.
- **결론:** 단일 DB 환경에서는 굳이 복잡한 Redis 분산락을 쓸 필요가 없음.

### 3. Optimistic Lock의 한계
- **관찰:** 충돌이 빈번한(High Contention) 상황에서는 재시도(Retry) 로직이 CPU와 DB 커넥션을 점유하여 성능 저하 유발.
- **결론:** 충돌이 적은 환경에는 적합하나, 재고 차감 같은 핫한 이벤트에는 부적합할 수 있음.

---

## 🚀 확장 전략 (Scalability Strategy)

용량(Capacity) 한계를 극복하기 위한 아키텍처 제언입니다.

### 1. 워크로드 분리 (Workload Separation)
*   **전략:** "이벤트성 핫 데이터는 Redis(Lua Script)로, 정합성이 중요한 콜드 데이터는 DB로."
*   **효과:** Lua Script가 DB 방식보다 2.5배 빠르다는 것은, DB의 부하를 Redis로 옮기는 것만으로도 **별도 스케일 업 없이 전체 처리량을 2.5배 늘릴 수 있음**을 의미합니다.

### 2. 비관적 락의 가성비 (Cost-Efficiency)
*   **전략:** "Redis 인프라 도입이 부담스럽다면, DB 스케일 업(Scale-up)에 투자하라."
*   **이유:** 단일 DB 환경에서는 비관적 락(618 TPS)이 Redis 분산 락(440 TPS)보다 더 빠르고 단순합니다. 복잡한 분산 락 구현보다 **DB CPU 증설**이 더 확실한 해결책일 수 있습니다.

### 3. 낙관적 락의 사용 조건 (The Retry Trap)
*   **주의:** 재고가 충분하여 **'판매 중'인 상황에서의 잦은 충돌**은 재시도(Retry) 폭증을 유발하여 시스템을 느리게 만듭니다. (Capacity Test 결과)
*   **비교:** 반면, 재고가 소진된 **'품절' 상황(Contention Test)**에서는 Fast Fail 덕분에 오히려 성능이 좋아집니다. 즉, 낙관적 락은 **"재고가 많고 경쟁이 치열한 구간"**에서 가장 취약합니다.