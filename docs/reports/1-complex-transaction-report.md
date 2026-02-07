# 📊 [Scenario 1] Complex Transaction Test Report

## 🎯 테스트 목적

재고 차감, 포인트 사용, 주문 이력 생성이 결합된 **복합 트랜잭션(Complex Transaction)** 상황에서 각 동시성 제어 방식의 데이터 정합성 보장 능력과 실질적 처리량을 검증한다.

**핵심 질문 (Key Questions):**
- "긴 트랜잭션(100ms+) 상황에서 낙관적 락의 재시도가 실효성이 있는가?"
- "비관적 락은 락 대기 시간에도 불구하고 낙관적 락보다 더 많은 성공 건수를 보장하는가?"
- "충돌 발생 시 모든 연관 데이터(재고, 포인트, 이력)가 원자적으로 롤백되는가?"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (Standard POC Profile)
| 서비스 | 자원 할당 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 2.0 vCPU, 2GB RAM | Java 21, Spring Boot 4.0 |
| **MySQL 8.0** | 2.0 vCPU, 2GB RAM | InnoDB, Point/OrderHistory 테이블 추가 |
| **Test Runner** | **k6 in Docker** | **내부망 직접 통신 (병목 제거)** |

### 1.2 Tuning Config
*   **스레드 모델:** `spring.threads.virtual.enabled: true` (Virtual Threads)
*   **DB Connection Pool:** HikariCP Max 10 (**의도적 제한:** 50 VUs 대비 적은 커넥션을 할당하여, 병목 구간에서의 자원 효율성과 매몰 비용을 극적으로 비교)
*   **로그 레벨:** `INFO` (성능 저하 방지)
*   **OS 튜닝:** `ulimits -n 65535`

### 1.3 상세 테스트 시나리오 (Detailed Scenario)
이 테스트는 **가장 가혹한 경합 상황(Hotspot)**을 시뮬레이션하기 위해 다음과 같이 설계되었습니다.

1.  **집중 경합 (Hotspot):** 모든 가상 사용자(50 VUs)가 **동일한 상품(ID:1)**과 **동일한 사용자(ID:1)**의 포인트에 접근합니다.
2.  **긴 트랜잭션 (Long-lived Transaction):** 각 요청은 트랜잭션 진입 후 DB 업데이트 직전에 **100ms의 인위적인 지연**(`Thread.sleep`)을 가집니다. 이는 포인트 서비스 호출이나 복잡한 계산 로직을 시뮬레이션합니다.
3.  **다중 엔티티 수정:** 하나의 트랜잭션 내에서 `Stock`, `Point`, `OrderHistory` 3개 테이블에 대한 쓰기 연산이 동시에 발생합니다.
4.  **부하 모델:** `constant-vus` 에디터를 사용하여 50명의 사용자가 15초 동안 **틈 없이(No-think time)** 요청을 쏟아붓습니다.

### 1.4 Scenario Spec 요약
| 항목 | 설정값 | 상세 설명 |
|:---|:---|:---|
| **Target Data** | **Single Row** | 상품 ID 1번 & 유저 ID 1번 (집중 경합) |
| **Internal Delay** | **100ms** | 트랜잭션 내부 비즈니스 로직 시뮬레이션 |
| **Virtual Users** | **50 VUs** | 동시에 50개의 트랜잭션이 하나의 로우를 선점하려고 시도 |
| **Initial Stock** | 1,000개 | 테스트 종료 후 남은 수량으로 정합성 확인 |
| **Initial Point** | 100,000점 | 테스트 종료 후 남은 포인트로 정합성 확인 |

---

## 🚀 실행 가이드 (Execution)

### 1. 테스트 수행 (Warm-up 자동 포함)
```bash
# Pessimistic Lock 테스트
make test-complex-pessimistic

# Optimistic Lock (재시도 없음) 테스트
make test-complex-optimistic-no-retry

# Optimistic Lock (3회 재시도) 테스트
make test-complex-optimistic-retry
```

### 2. 정합성 검증 (자동 실행)
```bash
# 테스트 종료 후 자동으로 출력되는 결과 확인
# Expected: Iterations = (Initial - Current Stock) = (Initial - Current Point/100) = History Count
make show-db
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

1.  **성공률 (Success Rate):**
    *   **의미:** 전체 요청 중 비즈니스 정합성을 지키며 완수된 비율.
    *   **판단 기준:** 결제 시스템 등 핵심 로직에서는 **100%** 달성이 최우선.

2.  **성공 시 Latency:**
    *   **의미:** 성공한 트랜잭션이 완료되기까지 걸린 시간.
    *   **판단 기준:** 비관적 락은 락 대기로 인해 길어지며, 낙관적 락은 재시도로 인해 길어짐.

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Mac Studio (M1 Max) / Docker Desktop

| 순위 | 방식 (Method) | TPS (req/s) | p95 Latency | 성공률 (Logic) | 비고 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Pessimistic** | 9.15 | 5.35s | **100.00%** | **실질 처리량 1위 (186건)** |
| **2** | **Optimistic (Retry 3)** | 20.57 | 2.88s | 43.73% | 56% 실패, 재시도 오버헤드 |
| **3** | **Optimistic (No-Retry)** | 91.02 | 0.45s | 10.00% | 대부분 실패, 응답만 빠름 |

---

## 💡 분석 및 결론 (Insights)

### 1. 안정성이 곧 실질적 처리량 (Throughput)
- **관찰:** 응답 속도가 가장 느린 비관적 락(Avg 4.64s)이 최종 성공 건수(186건)에서 낙관적 락(157건)을 앞지름.
- **원인:** 낙관적 락은 높은 경합 상황에서 대부분의 연산이 실패와 재시도로 낭비(Wasted Work)되지만, 비관적 락은 락 대기를 통해 확실하게 한 건씩 완수하기 때문.
- **결론:** 경합이 심한 복합 트랜잭션에서는 **"차라리 줄을 세우는 것이 전체 시스템 효율에 더 좋다."**

### 2. 재시도의 역설 (The Paradox of Retries)
- **관찰:** 낙관적 락에 재시도를 3회 추가하자 성공률은 4배 늘었으나, 응답 시간은 5배 이상 증가함.
- **원인:** 50명의 VUs가 100ms씩 점유하는 좁은 문을 통과하기 위해 여러 번 시도하는 과정에서, DB 커넥션 점유 시간만 늘어나고 병목이 심화됨.
- **결론:** 낙관적 락의 재시도는 충돌이 드문 환경(1-5%)에서만 유효하며, 고경합 환경에서는 **시스템 부하만 가중시키는 독**이 될 수 있음.

### 3. 자원 효율성 및 매몰 비용 (Resource Efficiency)
- **관찰:** HikariCP Max Pool Size가 10으로 제한된 환경에서 낙관적 락의 처리 효율이 급감함.
- **원인:** 낙관적 락은 트랜잭션 종료 시점에 충돌을 감지하므로, 실패하는 요청들이 커넥션을 점유하는 시간(100ms + 재시도 대기)은 시스템 입장에서 완전한 **매몰 비용(Wasted Work)**이 됨. 반면 비관적 락은 대기가 발생할지언정 커넥션을 점유한 동안은 확실한 성공을 보장함.
- **결론:** 자원이 제한적인 환경(Low Resource)일수록, 실패 확률이 높은 낙관적 락보다 비관적 락이 시스템의 전체적인 자원 효율성(Efficiency) 측면에서 유리함.

---

## 🚀 확장 전략 (Scalability Strategy)

*   **비관적 락 최적화:** 락 범위를 최소화하기 위해 트랜잭션 내부에서 I/O(외부 API)를 제거하고 순수 DB 연산만 남김.
*   **분산 트랜잭션 고려:** 서비스 규모 확대 시 2PC 대신 **Saga 패턴** 등을 통해 트랜잭션을 분리하고, 각 단계에서 적절한 락 전략(예: 보상 트랜잭션)을 선택.
*   **User Sharding:** 동일 사용자(`userId=1`)에 대한 집중 경합은 샤딩을 통해 물리적으로 분산시킬 수 없는 영역이므로, 비즈니스 레벨의 대기열(Redis Queue 등) 도입 검토.
