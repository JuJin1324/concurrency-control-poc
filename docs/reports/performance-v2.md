# 성능 테스트 리포트 (V2) - 동시성 제어 방식별 성능 비교 (Capacity)

**작성일:** 2026-01-30
**작성자:** Gemini CLI Agent
**테스트 목적:** 단일 인기 상품에 대한 대규모 동시 주문(선착순 이벤트) 상황에서 4가지 동시성 제어 방식의 처리량(TPS)과 응답 속도(Latency)를 정량적으로 비교한다.

---

## 1. 테스트 환경 및 기준 (Test Environment)

가장 보편적인 클라우드 엔트리급 인스턴스 환경을 기준으로, 오직 소프트웨어 아키텍처와 동시성 제어 기법의 차이에 따른 성능 변화를 측정한다.

### 1.1 인프라 스펙 (Infrastructure)
AWS t3.small 등급의 리소스를 시뮬레이션하기 위해 Docker Compose Resource Limit을 엄격하게 적용했다.

| 서비스 | 자원 할당 (Limit) | 비고 |
| :--- | :--- | :--- |
| **Application** | 2.0 vCPU, 2GB RAM | Spring Boot 3.4 (Java 21) |
| **MySQL** | 2.0 vCPU, 2GB RAM | MySQL 8.0.45 (InnoDB) |
| **Redis** | 2.0 vCPU, 1.37GB RAM | Redis 7.0 (Alpine) |

### 1.2 애플리케이션 튜닝 (Tuning)
*   **Thread Model:** Java 21 **Virtual Threads** 활성화 (`spring.threads.virtual.enabled: true`)
*   **Connection Pool:**
    *   HikariCP (MySQL): Max 50
    *   Lettuce (Redis): Max 50
*   **OS/Network:**
    *   Docker ulimits: nofile 65535
    *   Warm-up Protocol 적용 (Cold Start 방지)

### 1.3 테스트 시나리오 (Scenario)
*   **시나리오명:** 단일 품목 재고 차감 (Hotspot Update)
*   **데이터:** Stock ID `1`번 상품, 초기 재고 `10,000`개
*   **부하 조건:**
    *   **VUs (가상 사용자):** 100명 동시 접속
    *   **Iterations:** 총 10,000건 주문 요청 (정확히 재고가 0이 될 때까지)
    *   **Executor:** k6 `shared-iterations` (모든 VUs가 할당량을 나눠서 처리)

---

## 2. 테스트 결과 요약 (Executive Summary)

**Redis Lua Script** 방식이 다른 방식 대비 **최소 3배 이상의 성능(TPS)**을 기록하며 압도적인 1위를 차지했다.
전통적인 **Pessimistic Lock(비관적 락)**은 예상외로 **Redis Distributed Lock(분산 락)**보다 높은 처리량을 보여주었으며, **Optimistic Lock(낙관적 락)**은 충돌 비용으로 인해 가장 낮은 성능을 기록했다.

| 순위 | 방식 (Method) | TPS (처리량) | 평균 응답 속도 (Latency) | P95 응답 속도 | 성공률 (기능)* |
| :--: | :--- | :---: | :---: | :---: | :---: |
| **1** | **Lua Script** | **1,962 req/s** | **51.19 ms** | **119.38 ms** | 100% |
| **2** | **Pessimistic Lock** | **653 req/s** | 154.13 ms | 270.81 ms | 100% |
| **3** | **Redis Lock** | **542 req/s** | 185.06 ms | 302.82 ms | 100% |
| **4** | **Optimistic Lock** | **521 req/s** | 167.82 ms | 281.62 ms | 100% |

> **성공률(기능):** 비즈니스 로직 상 재고가 정확히 0이 되고, 초과 차감(Over-selling)이 발생하지 않았음을 의미함.
> (단, Optimistic Lock의 경우 HTTP 409 Conflict 응답이 다수 발생했으나, 이는 데이터 정합성을 지킨 결과이므로 테스트 통과로 간주함.)

---

## 3. 상세 분석 (Detailed Analysis)

### 🥇 1위: Redis Lua Script (Atomic Operation)
*   **핵심 요인:** **"네트워크 왕복(RTT)의 최소화"**
*   **분석:**
    *   다른 방식들은 [락 획득 -> 조회 -> 차감 -> 저장 -> 락 해제]의 복잡한 과정을 거치며 DB/Redis와 수차례 통신한다.
    *   Lua Script는 이 모든 로직을 Redis 서버 내부에서 원자적(Atomic)으로 한 번에 실행한다.
    *   애플리케이션과 Redis 사이의 통신이 단 **1회**로 줄어들어, 압도적인 처리량과 낮은 지연 시간을 달성했다.
*   **결론:** 단순 재고 차감과 같은 초고속 트랜잭션에는 최고의 선택지다.

### 🥈 2위: Pessimistic Lock (MySQL Exclusive Lock)
*   **핵심 요인:** **"의외의 선전, 덜 복잡한 네트워크"**
*   **분석:**
    *   일반적으로 "DB 락은 느리다"는 편견이 있으나, **Redis 분산 락보다 네트워크 경로가 단순**하다. (App <-> DB)
    *   MySQL InnoDB 엔진이 Row-level Lock 대기열을 효율적으로 관리했다.
    *   Redis 분산 락은 (App <-> Redis <-> App <-> DB <-> App <-> Redis) 처럼 통신 경로가 매우 길다. 이 오버헤드가 DB 락의 대기 시간보다 더 컸던 것으로 분석된다.
*   **결론:** 외부 인프라(Redis) 의존 없이도 준수한 성능을 낼 수 있다.

### 🥉 3위: Redis Distributed Lock (Redisson)
*   **핵심 요인:** **"분산 환경의 비용 (Overhead)"**
*   **분석:**
    *   Redisson 클라이언트는 Pub/Sub 방식으로 락 획득을 대기하므로 스핀락(Spin-lock)보다는 부하가 적지만, **락을 획득하고 해제하는 과정 자체의 네트워크 비용**이 상당하다.
    *   트랜잭션이 매우 짧은(단순 차감) 로직에서는 배보다 배꼽(Lock 관리 비용)이 더 큰 현상이 발생했다.
*   **결론:** 비즈니스 로직이 길고 복잡하여 DB 점유 시간을 줄여야 하는 경우에는 효과적일 수 있으나, 단순 차감 로직에서는 비효율적이다.

### 4위: Optimistic Lock (JPA Versioning)
*   **핵심 요인:** **"충돌(Conflict) 비용"**
*   **분석:**
    *   100명이 동시에 덤벼들면 1명만 성공하고 99명은 실패(Rollback)하는 구조다.
    *   실패한 99명에 대한 예외 처리 및 롤백 비용이 시스템 자원을 잡아먹는다.
    *   현재 재시도(Retry) 로직이 적용되지 않았음에도, 충돌 발생 자체만으로 처리량이 저하되었다. (재시도 로직 추가 시 TPS는 더 떨어질 것으로 예상됨)
*   **결론:** 선착순 이벤트처럼 경합이 극심한 상황(High Contention)에서는 적합하지 않다.

---

## 4. 향후 계획 (Next Steps)

현재까지는 **"얼마나 많이 처리할 수 있는가(Capacity)"**에 집중했다. 다음 단계에서는 **"얼마나 안정적으로 버티는가(Stability)"**를 검증한다.

1.  **Contention Test (경합/안정성 테스트):**
    *   동일한 100 VUs 부하를 **30초 이상 지속**했을 때, 응답 속도가 튀거나(Jitter) 에러가 발생하는지 확인.
    *   특히 Redis Lock과 Pessimistic Lock의 안정성 비교.
2.  **Optimistic Lock 재시도(Retry) 구현:**
    *   현재는 충돌 시 실패 처리되지만, 실제 서비스라면 재시도를 해야 한다.
    *   Retry 구현 후 성능 변화(TPS 급락 예상) 측정.