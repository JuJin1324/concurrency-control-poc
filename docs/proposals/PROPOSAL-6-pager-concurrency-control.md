# [제안서] 고동시성(High-Concurrency) 제어 기술 검증 및 성능 비교 PoC

**작성자:** [본인 이름]
**수신:** 홍수석
**날짜:** 2026년 1월 21일

## 1. 개요 (Introduction)
본 문서는 시니어 백엔드 엔지니어의 핵심 역량인 '대규모 트래픽 처리' 능력을 구체적인 데이터로 증명하기 위한 **동시성 제어 PoC(Proof of Concept)** 프로젝트를 제안합니다.

많은 엔지니어들이 '대규모 트래픽'을 막연히 MSA(Microservices Architecture)나 복잡한 인프라 구축으로 오인하곤 합니다. 하지만 실제 커머스 및 핀테크 도메인(네이버, 쿠팡, 토스 등)에서 요구하는 본질적인 역량은 한정된 자원 내에서 데이터 정합성을 보장하며 높은 처리량(Throughput)을 유지하는 **동시성 제어 능력**입니다.

이 프로젝트는 가장 빈번하게 발생하는 **'재고 차감 경쟁 상태(Race Condition)'** 문제를 정의하고, 이를 해결하는 4가지 핵심 기술(Pessimistic Lock, Optimistic Lock, Redis Distributed Lock, Redis Lua Script)을 구현하여 동일 환경에서 정량적 성능 지표(TPS, Latency)를 비교 분석하는 것을 목표로 합니다.

## 2. 문제 정의 (Problem Statement)
현재 이직 시장과 실무 환경에서는 다음과 같은 문제와 오해가 존재합니다.

1.  **역량 증명의 모호성:** 이력서나 면접에서 "대규모 트래픽을 경험했다"고 주장하지만, 구체적으로 어떤 트래픽 상황에서 어떤 기술적 의사결정을 통해 문제를 해결했는지 정량적으로 설명하지 못하는 경우가 많습니다.
2.  **도구 중심의 학습 (Cargo Cult):** 문제 해결(Why)보다 도구(How)에 집중하는 경향이 있습니다. 예를 들어, 동시성 문제가 발생했을 때 RDBMS의 기본 기능을 활용하기보다 무조건적으로 Kafka나 Redis를 도입하려 하여 시스템 복잡도를 불필요하게 높이는 '오버 엔지니어링'이 발생합니다.
3.  **깊이의 부재:** 여러 기술을 얕게(Broad but Shallow) 아는 것은 시니어 레벨에서 차별화되지 않습니다. 하나의 문제를 끝까지 파고들어(Deep Dive) 트레이드오프를 명확히 이해하고 설명할 수 있는 깊이가 필요합니다.

## 3. 제안 솔루션 (Proposed Solution)
위 문제를 해결하기 위해 **'재고 차감 동시성 제어 4가지 방법 성능 비교 PoC'**를 수행합니다. 이 프로젝트는 MVP(Minimum Viable Product)가 아닌 기술적 깊이에 집중한 PoC 형태로 진행합니다.

### 3.1. 핵심 범위
*   **단일 도메인:** 재고(Stock) 시스템만 격리하여 구현합니다.
*   **단일 기능:** 100개의 재고에 10,000명의 유저가 동시에 몰리는 상황을 시뮬레이션합니다.
*   **4가지 구현체 비교:**
    1.  **MySQL Pessimistic Lock:** 강력한 정합성 보장, 성능 한계 확인.
    2.  **MySQL Optimistic Lock:** 충돌이 적은 상황에서의 성능 이점 및 Retry 비용 확인.
    3.  **Redis Distributed Lock (Redisson):** 분산 환경에서의 락 관리 및 타임아웃 처리.
    4.  **Redis Lua Script:** 네트워크 라운드트립을 최소화한 원자적 연산의 성능 극대화.

### 3.2. 검증 방법
*   **부하 테스트:** k6를 활용하여 동시 접속자 수(100, 1000, 10000 VU)를 단계별로 증가시킵니다.
*   **측정 지표:** 각 방법론 별 TPS(Transactions Per Second), Latency(p50, p95, p99), Success Rate(데이터 정합성)를 측정합니다.
*   **산출물:** 재현 가능한 GitHub 리포지토리, 성능 비교 그래프 및 분석 리포트, 기술 블로그 포스팅.

## 4. 수행 원칙 (Tenets)
이 프로젝트를 관통하는 의사결정 원칙입니다.

1.  **넓이보다 깊이 (Depth over Width):**
    *   다양한 기능(주문, 결제, 회원)을 만드는 대신, '동시성'이라는 하나의 문제에 집중합니다. MSA나 Kafka 도입은 본 PoC의 범위를 벗어납니다.
2.  **구현보다 시각화 우선 (Visualization First):**
    *   코드를 작성하기 전, 시스템의 구조와 흐름을 시각화(C4 Model, Sequence Diagram)하여 설계를 검증합니다. 이는 잘못된 구현으로 인한 매몰 비용을 최소화합니다.
3.  **정성보다는 정량 (Data over Opinion):**
    *   "Redis가 빠를 것이다"라는 추측 대신, "Redis Lua Script가 Pessimistic Lock보다 TPS가 6.5배 높다"와 같은 데이터로 증명합니다.
4.  **재현 가능성 (Reproducibility):**
    *   Docker Compose와 Makefile을 통해 누구나 명령 한 줄(`make up`)로 테스트 환경을 구축하고 결과를 재현할 수 있어야 합니다.

## 5. 실행 계획 (Execution Plan)
총 4주(4 Sprints)의 일정으로 진행합니다.

*   **Sprint 0 (Foundation):** 인프라 구축(Docker), 아키텍처 시각화, ADR 작성.
*   **Sprint 1 (DB Locks):** MySQL 기반의 Pessimistic/Optimistic Lock 구현 및 단위 테스트.
*   **Sprint 2 (Redis Locks):** Redis 기반의 Distributed Lock, Lua Script 구현.
*   **Sprint 3 (Verification):** k6 부하 테스트 수행, 성능 지표 수집 및 분석 리포트 작성.
*   **Sprint 4 (Documentation):** README, 블로그 작성 및 지식 공유.

## 6. 예상되는 질문 (FAQ)

**Q: 왜 Kafka(이벤트 기반 아키텍처)는 다루지 않습니까?**
**A:** Kafka는 비동기 처리를 통한 유량 제어(Flow Control)에 탁월하지만, 동시성 제어(Concurrency Control)의 본질인 'Locking'과 'Atomic Operation'을 직접적으로 비교하기에는 변수가 너무 많습니다. 1~2달이라는 시간 제약 속에서 깊이 있는 결과를 얻기 위해, 동기식 처리에서의 동시성 제어로 범위를 한정했습니다.

**Q: 실무에서는 Redis가 장애가 날 수 있는데, 이에 대한 대비책은 포함됩니까?**
**A:** 이번 PoC의 주 목적은 '성능 한계치(Performance Baseline)'를 확인하는 것입니다. 고가용성(HA) 구성이나 장애 조치(Failover) 시나리오는 '시스템 안정성' 영역이므로, Phase 2(심화 과정)에서 다룰 예정입니다. 다만, Redis Lock 사용 시 TTL 설정 등을 통해 데드락을 방지하는 기본적인 안전 장치는 구현합니다.

**Q: 이 프로젝트가 팀/회사에 어떤 기여를 할 수 있습니까?**
**A:**
1.  우리 팀이 향후 트래픽 급증 시 선택할 수 있는 기술적 옵션의 **성능 기준표(Baseline)**를 확보할 수 있습니다.
2.  신규 입사자를 위한 **동시성 제어 온보딩 교육 자료**로 활용 가능합니다.
3.  문제를 깊이 있게 파고들어 해결하는 엔지니어링 문화를 전파하는 사례가 될 것입니다.

## 7. 부록 (Appendix)
*   [참고] 아키텍처 다이어그램 (`docs/architecture/`)
*   [참고] [ADR-001: 왜 이 4가지 방법을 비교하는가?](../adr/ADR-001-why-these-four-methods.md)
*   [참고] [ADR-004: 왜 MySQL과 Redis를 선택했는가?](../adr/ADR-004-why-mysql-and-redis.md)
