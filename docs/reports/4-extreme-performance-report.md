# 📊 [Scenario 4] Extreme Performance Comparison Test Report

## 🎯 테스트 목적

시나리오 3(Resource Protection)에서는 현실적인 비즈니스 로직 시뮬레이션(100ms 지연)을 포함하여 인프라 보호 능력을 검증하였다. 본 시나리오 4에서는 이 **비즈니스 지연(Thread.sleep)을 완전히 제거**한 극한의 환경에서, 각 동시성 제어 기술(Pessimistic, Optimistic, Redis Lock, Lua Script)이 가진 **순수 오버헤드와 처리량 한계**를 비교 측정한다. 특히 Redis를 주 저장소(Primary Storage)로 활용하는 Lua 방식이 타 방식 대비 어느 정도의 '순수 속도' 우위를 가지는지 정량적으로 확인한다.

**핵심 질문 (Key Questions):**
- "시나리오 3의 100ms 지연이 사라졌을 때, 각 기술의 처리량(TPS)은 어떻게 변화하는가?"
- "Lua Script의 압도적 TPS 이면에 숨겨진 '비즈니스 로직 구현의 제약'은 무엇인가?"
- "RDBMS 기반 방식은 지연이 없을 때 어느 정도까지 성능을 끌어올릴 수 있는가?"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (Standard POC Profile)
| 서비스 | 설정 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 2.0 vCPU, 2GB RAM | Virtual Threads 활성화 |
| **MySQL 8.0** | 2.0 vCPU, 2GB RAM | **의도적 제한:** HikariCP Max 10 |
| **Redis 7.0** | 2.0 vCPU, 1.37GB RAM | 분산 락 및 Lua 실행 |
| **Test Runner** | **k6 v0.56.0** | 내부망 직접 통신 |

### 1.2 Tuning Config
*   **스레드 모델:** `spring.threads.virtual.enabled: true` (Virtual Threads)
*   **DB Connection Pool:** HikariCP Max 10 (고의적 병목 환경 유지)
*   **Redis Lock Timeout:** 
    *   `waitTime`: 3s (락 획득 대기 시간)
    *   **Business Delay:** **0ms** (시나리오 3에서 사용된 비즈니스 로직 지연 100ms 제거)
*   **초기 상태:** 5개 상품, 각 재고 100,000개 (총 500,000개)

### 1.3 상세 테스트 시나리오 (Detailed Scenario)
1.  **순수 성능 비교:** 500 VUs가 5개의 상품에 대해 20초간 지연 없이 연속 요청.
2.  **전장 통일:** 시나리오 3에서 사용된 `Thread.sleep(100)`을 모든 방식에서 제거하여 기술 자체의 한계 성능 측정.
3.  **정합성 검증:** 테스트 종료 후 DB 및 Redis의 잔여 재고 합계 일치 여부 확인.

### 1.4 Scenario Spec 요약
| 항목 | 설정값 | 상세 설명 |
|:---|:---|:---|
| **Target Data** | **5 Rows** | 5개의 상품에 집중 접근 (Hotspot) |
| **Internal Delay** | **0ms** | 시나리오 3의 비즈니스 지연(100ms) 제거 |
| **Virtual Users** | **500 VUs** | 커넥션 풀(10) 대비 50배의 동시 사용자 |
| **Duration** | 20s | 순수 성능 한계 집중 테스트 |

---

## 🚀 실행 가이드 (Execution)

### 1. 테스트 수행 (Warm-up 자동 포함)
```bash
# Pessimistic (No-Delay) 테스트
make test-extreme-pessimistic

# Optimistic (No-Retry, No-Delay) 테스트
make test-extreme-optimistic

# Redis Lock + Optimistic (No-Delay) 테스트
make test-extreme-redis

# Lua Script (Redis as Primary, No-Delay) 테스트
make test-extreme-lua
```

### 2. 정합성 검증 (자동 실행)
```bash
# DB 및 Redis 재고 정합성 확인
make show-db
make show-redis
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

1.  **순수 오버헤드 (Pure Overhead):**
    *   **의미:** 비즈니스 로직 지연이 없을 때, 각 기술이 순수하게 동시성을 제어하기 위해 소모하는 시간.
    *   **판단 기준:** p95 Latency가 낮을수록 프레임워크 및 인프라 간 통신 오버헤드가 적음을 의미.

2.  **한계 TPS (Peak Throughput):**
    *   **의미:** 시스템이 도달할 수 있는 초당 최대 처리량.
    *   **판단 기준:** 로직이 단순할수록 Redis 기반 방식(특히 Lua)이 압도적 우위를 점하며, DB 방식은 커넥션 풀 크기에 의해 물리적 한계가 결정됨.

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Mac Studio (M1 Max) / Docker Desktop

| 순위 | 방식 (Method) | TPS (req/s) | p95 Latency | 성공률 (성공/총 요청) | 비고 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Lua Script** | **3,491.50** | **0.29s** | **70,106 / 70,106 (100.0%)** | **독보적 우위 (메모리 연산)** |
| **2** | **Optimistic** | 985.91 | 1.10s | 8,756 / 20,016 (43.7%) | DB 직접 충돌 (재시도 없음) |
| **3** | **Pessimistic** | 978.00 | 1.01s | **19,847 / 19,847 (100.0%)** | 짧은 점유 시간으로 효율 증대 |
| **4** | **Redis Lock** | 602.42 | 1.88s | 12,316 / 12,384 (99.4%) | 네트워크 RTT 병목 발생 |

---

## 💡 분석 및 결론 (Insights)

### 1. 아키텍처적 패러다임의 전환: "병목의 소멸과 이동"
- **Lua Script의 승리**: 애플리케이션-DB 간의 네트워크 왕복 및 트랜잭션 관리 비용이 제거된 **'순수 인메모리 원자적 연산'**이 시나리오 3 대비 압도적인 결과(TPS 150 -> 3,400)를 냈습니다.
- **Pessimistic의 반전**: 시나리오 3에서 100ms 지연으로 인해 TPS 36건에 머물며 시스템 마비를 초래했던 비관적 락이, 지연이 사라지자 약 1,000 TPS까지 급증했습니다. 이는 **비관적 락 자체의 오버헤드보다 '락을 잡은 채 유지되는 비즈니스 로직 시간'이 진짜 자원 고갈의 원인**이었음을 증명합니다.

### 2. "빠르다"가 아니라 "무엇을 포기했는가" (Trade-off)
시나리오 3와 4의 비교를 통해 우리는 실무에서 반드시 검토해야 할 뼈아픈 제약 사항들을 발견할 수 있습니다.

- **도메인 로직 간소화 및 재설계 비용 (Architectural Re-engineering)**: 현대적인 DDD/MSA 환경에서 도메인 로직은 필연적으로 복잡한 검증과 풍부한 행위를 포함합니다. 이를 Lua Script로 전환하는 것은 단순히 코드를 옮기는 것이 아니라, **Redis 엔진 제약에 맞게 도메인 모델을 극단적으로 파편화하고 간소화하는 재설계 과정**을 강제합니다. 이는 개발 생산성 저하와 유지보수 난이도 상승이라는 숨겨진 비용을 발생시킵니다.
- **비즈니스 로직의 제약**: Lua Script 내부에 시나리오 3와 같은 100ms 지연(외부 API 호출 등)을 포함하는 것은 **기술적으로 불가능**합니다. Redis는 싱글 스레드이므로 스크립트 하나가 지연되는 순간 **Redis 서버 전체가 마비**되기 때문입니다. 즉, 시나리오 4의 압도적 성능은 '로직의 풍부함'을 포기한 전제 조건 하에서만 가능합니다.
- **영속성(Durability)과 운영 비용**: Redis는 인메모리 저장소이므로 찰나의 순간에 데이터가 유실될 위험이 RDBMS보다 높습니다. 또한 Redis를 Primary로 쓰려면 결국 DB와의 최종 싱크(Eventual Consistency)를 위해 Kafka, CDC 등 **추가적인 동기화 인프라를 운영해야 하는 거대한 비용**이 발생합니다.
- **Lua Script 도입의 진짜 정당성: TPS가 아니라 DB 커넥션 보호**: Lua Script 도입을 위해 도메인 로직을 간소화했다면, 동일하게 간소화된 로직을 비관적 락으로 실행해도 시나리오 3 대비 **약 26.7배의 TPS 향상**(36 → 978 TPS)을 얻을 수 있습니다. 즉, 단순히 TPS 향상만을 목적으로 Lua Script를 도입하는 것은 정당화되기 어렵습니다. Lua Script 도입이 의미를 가지려면, TPS 향상에 더해 **DB 커넥션 자원 자체를 보호**해야 하는 명확한 의도가 있어야 합니다. 트래픽이 DB Pool 한계를 초과하여 커넥션 고갈이 현실적 위협이 되는 상황 — 바로 그 지점이 Lua Script라는 무거운 선택지를 꺼내야 할 때입니다.

### 3. Redis Lock의 역설: 로직이 짧을수록 독이 된다
- 분산 락 방식은 지연이 없는 환경에서 가장 낮은 성능(602 TPS)을 보였습니다. 
- 락 획득/해제를 위한 추가적인 Redis 네트워크 왕복(RTT) 시간이 비즈니스 로직 수행 시간보다 길어지면서, **오히려 동시성 제어 도구가 배보다 배꼽이 더 큰 병목**이 된 사례입니다.

### 4. 실무적 관점에서의 재랭킹 (Practical Ranking)
단순 TPS 수치가 아닌, **'데이터 정합성을 보장하며 실제 서비스가 가능한 방식'**으로 순위를 재편하면 다음과 같습니다.

1.  **Lua Script (압도적 우승)**: 성능과 정합성을 모두 잡은 극한의 솔루션.
2.  **Pessimistic Lock (실질적 2위)**: 지연이 짧은 환경에서는 분산 락의 네트워크 오버헤드보다 DB 내부 락 처리가 훨씬 빠름을 입증. 별도 인프라 없이 최상의 결과 도출.
3.  **Redis Lock + Optimistic (실질적 3위)**: 자원 보호 능력은 탁월하나, 지연이 없는 극한 성능 환경에서는 네트워크 RTT가 병목이 되어 비관적 락에 밀림.
4.  **Optimistic (탈락)**: 재시도 없는 낙관적 락은 고경합 환경에서 50% 이상의 실패율을 기록하여 실무적으로 사용 불가.

---

## 🚀 실무 적용 가이드 (Engineering Decision)

> **"성능 수치에 매몰되지 말고, 비즈니스 로직의 복잡도와 인프라 운영 역량을 먼저 보라."**

1.  **Pessimistic Lock (1순위 추천)**: 
    - 로직이 짧고 정합성이 중요하다면 가장 최선의 선택입니다. 추가 인프라 없이 RDBMS만으로 초당 1,000건(지연 없을 시)을 안정적으로 처리할 수 있습니다.
2.  **Redis + Optimistic (2순위 추천)**: 
    - DB 커넥션 풀을 보호해야 하는 상황에서 가장 밸런스 있는 선택입니다. (시나리오 3 결과 참고)
3.  **Lua Script (특수 상황)**: 
    - 로직이 매우 단순(단순 차감)하고, 초당 수천 건 이상의 극한 트래픽이 몰리는 특정 Hotspot 상품(예: 한정판 딜)에만 **선별적으로 도입**하십시오. 이때 유실 대응 및 DB 동기화 전략은 필수입니다.

---

## 🚀 확장 전략 (Scalability Strategy)

*   **Asynchronous DB Persistence:** Redis Lua Script로 처리된 결과를 메시지 큐(Kafka, RabbitMQ) 또는 CDC(Change Data Capture)를 통해 비동기적으로 DB에 영속화하여 데이터 유실 위험 최소화.
*   **Redis Cluster & Replication:** 단일 Redis 노드의 장애가 전체 시스템 마비로 이어지지 않도록 Redis Cluster 구성을 통한 고가용성(HA) 확보 및 부하 분산.
*   **Domain-Specific Optimization:** 모든 상품이 아닌, 극심한 경합이 예상되는 '이벤트 상품'만 동적으로 Redis Primary 방식으로 전환하여 운영 복잡도와 성능의 균형 달성.

---

**결론적으로, 지연이 없는 환경에서의 테스트는 '기술 자체의 속도'를 보여주지만, 실무는 항상 '비즈니스 지연'과 함께합니다. 우리는 성능 지표뿐만 아니라 그 뒤에 숨겨진 운영의 무게를 함께 보아야 합니다.**

---

**작성일:** 2026-02-09
**프로젝트:** Concurrency Control PoC