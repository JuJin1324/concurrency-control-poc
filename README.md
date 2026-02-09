# Concurrency Control PoC: High-Traffic Inventory System

> **이커머스 재고 시스템의 대규모 트래픽 처리를 위한 동시성 제어 전략 검증 프로젝트**
>
> "은탄환은 없다. 적재적소(Right Tool for Right Job)만 있을 뿐." — 4가지 동시성 제어 방식을 각각의 **Best Fit 시나리오**에서 검증하여, 기술의 우열이 아닌 **상황에 따른 최적 선택**을 실제 성능으로 증명했습니다.

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-green?style=flat-square)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7.0-red?style=flat-square)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square)](https://www.mysql.com/)
[![k6](https://img.shields.io/badge/k6-Load%20Test-purple?style=flat-square)](https://k6.io/)

---

## 🏆 Executive Summary (핵심 성과)

4가지 동시성 제어 기법을 **상황별 Best Fit 시나리오**에서 검증한 결과, 절대적인 우승자는 없으며 비즈니스 맥락에 따라 최적 선택이 달라짐을 정량적으로 증명했습니다.

| 시나리오 | 우승자 | 핵심 인사이트 |
|:---|:---|:---|
| **복합 트랜잭션** (50 VUs, 고경합) | **Pessimistic Lock** | 자원 낭비 제로, 실질 처리량 1위 |
| **저경합 분산 환경** (100 VUs, 100 상품) | **Pessimistic Lock** | TPS 차이 2.8%에 불과, 성공률 100% |
| **고부하 자원 보호** (500 VUs, DB Pool 10) | **Redis + Optimistic** | Fail-fast로 DB 커넥션 보호 |
| **극한 성능** (500 VUs, No Delay) | **Lua Script** | 독보적 3,491 TPS, 단 비즈니스 로직 제약 |

> **결론:** 비관적 락을 기본으로 시작하고, 낙관적 락(`@Version`) 충돌 모니터링으로 경합 지점을 식별한 뒤, 인프라 자원이 부족해지는 시점에 Redis를 도입하라.
>
> 📄 **[상세 성능 분석 리포트 (Performance V3)](docs/reports/performance-v3.md)** | [V2 (Sprint 5-6 기록)](docs/reports/performance-v2.md)

---

## 🏗️ 아키텍처 및 기술적 접근

**"어떻게 동시성을 제어할 것인가?"**에 대한 4가지 해답을 구현하고 비교했습니다.

| 방식 | 기술 스택 | 특징 | Trade-off |
| :--- | :--- | :--- | :--- |
| **Pessimistic Lock** | MySQL `FOR UPDATE` | 데이터 정합성 최우선 | 동시성 저하 (직렬 처리) |
| **Optimistic Lock** | JPA `@Version` | 락 없이 버전 관리 | 충돌 시 재시도 비용 발생 |
| **Distributed Lock** | Redisson (Pub/Sub) | 분산 환경 락 제어 | 네트워크 RTT 오버헤드 |
| **Lua Script** | Redis `EVAL` | **서버 사이드 원자성** | 스크립트 관리 필요 |

### System Architecture
```mermaid
flowchart LR
    User["Client (k6)"] -->|Traffic| App["Spring Boot<br/>(Virtual Threads)"]
    App -->|Cache/Lock| Redis["Redis<br/>(Lettuce)"]
    App -->|Persistence| DB["MySQL<br/>(HikariCP)"]
```

---

## 🧪 테스트 엔지니어링 (Test Engineering)

단순한 부하 주입이 아닌, **목적에 맞는 검증 시나리오**를 설계하여 데이터의 신뢰성을 확보했습니다.

### 1. 격리성 (Isolation)
- **문제:** 이전 테스트의 잔재(Connection Pool, Cache)가 다음 테스트에 영향을 줌.
- **해결:** `reset-infra`(Docker 완전 재시작) 파이프라인을 통해 매 테스트 직전 인프라를 **Cold Start** 상태로 초기화.

### 2. Best Fit 시나리오 (Sprint 7)
각 방식이 가장 빛나는 시나리오를 설계하여, 기술의 우열이 아닌 상황에 따른 최적 선택을 검증했습니다.

- **Scenario 1: Complex Transaction** — 복합 ACID 트랜잭션에서 비관적 락의 안정성 검증 → **[리포트](docs/reports/1-complex-transaction-report.md)**
- **Scenario 2: Low Contention** — 저경합 분산 환경에서 낙관적 락 vs 비관적 락 종합 비교 → **[리포트](docs/reports/2-low-contention-report.md)**
- **Scenario 3: Resource Protection** — 고부하(500 VUs)에서 Redis 계층적 방어의 필요성 증명 → **[리포트](docs/reports/3-resource-protection-report.md)**
- **Scenario 4: Extreme Performance** — 비즈니스 지연 제거 시 순수 기술 오버헤드 비교 → **[리포트](docs/reports/4-extreme-performance-report.md)**

### 3. 절대 비교 시나리오 (Sprint 5-6)
동일 조건에서 4가지 방식을 절대적으로 비교하여 각 기술의 기본 특성을 파악했습니다.

- **Capacity Test:** 재고가 넉넉할 때(10k) 최대 TPS 측정 → **[리포트](docs/reports/capacity-report.md)**
- **Contention Test:** 재고 부족(100개) 핫딜 상황에서 5,000명 동시 접속 → **[리포트](docs/reports/contention-report.md)**
- **Stress Test:** 부하 점진 증가, 임계점(Knee Point) 탐색 → **[리포트](docs/reports/stress-report.md)**

### 4. 최적화 (Optimization)
- **Virtual Threads:** Java 21 가상 스레드 도입으로 I/O 블로킹 비용 최소화.
- **의도적 자원 제한:** DB Pool 10으로 제한하여 자원 부족 상황을 유도, 각 방식의 병목 특성을 극대화하여 관찰.

---

## 📊 핵심 인사이트 (Key Insights)

### "낙관적 락이 비관적 락보다 빠르다"는 통념의 검증
- 저경합 환경에서조차 TPS 차이 **2.8%에 불과**, 재시도 포함 시 비관적 락에 역전.
- 낙관적 락은 성능이 아닌 **경합 감지 보험(`@Version`)과 커넥션 효율성**에 존재 이유가 있음.

### 비즈니스 로직 지연에 따른 순위 반전
- 100ms 지연 시: Redis+Optimistic 1위 (154 TPS) vs Pessimistic 최하위 (36 TPS)
- 0ms 지연 시: Pessimistic 2위 (978 TPS) vs Redis Lock 최하위 (602 TPS)
- **기술 선택은 비즈니스 로직의 복잡도에 의해 결정**되어야 함.

### Redis 도입의 정당한 시점
- 자원이 널널한 환경(DB Pool 50)에서 Redis 도입은 네트워크 홉만 추가하여 오히려 성능 저하.
- **트래픽 대비 인프라 자원이 부족해지기 시작할 때**가 Redis를 꺼내야 할 시점.

---

## 🛡️ 실무 도입 전략 (Engineering Insights)

### 점진적 최적화 흐름 (Progressive Optimization)

```
전체 엔티티 @Version 적용 (경합 감지 보험)
        ↓
낙관적 락 충돌 모니터링 (신호 감지)
        ↓
경합 빈번 엔티티 식별 (분석)
        ↓
해당 엔티티만 비관적 락 적용 (타겟 솔루션)
        ↓
트래픽 증가 시 Redis 계층 추가 (스케일)
```

### 의사결정 가이드 (Decision Tree)

```mermaid
flowchart TD
    Start([🚀 선택 시작]) --> Base["0. 전체 엔티티 @Version 적용<br/>(경합 감지 보험)"]

    Base --> Monitor{"충돌 모니터링 결과<br/>경합이 빈번한가?"}

    Monitor -- "NO - 충돌 드묾" --> Keep["✅ 낙관적 락 유지<br/>추가 조치 불필요"]
    Monitor -- "YES - 경합 식별됨" --> Q1{DB 커넥션 풀이<br/>병목인가?}

    Q1 -- "NO - 자원 여유" --> Pessimistic["🔒 Pessimistic Lock<br/>해당 엔티티만 적용"]
    Q1 -- "YES - 자원 부족" --> Q2{비즈니스 로직이<br/>단순한가?}

    Q2 -- "NO - 복잡/지연 있음" --> RedisOpt["🛡️ Redis + Optimistic<br/>계층적 방어"]
    Q2 -- "YES - 단순 연산" --> LuaScript["🏎️ Lua Script<br/>선별적 도입"]
```

상세 가이드: **[Practical Guide](docs/reports/practical-guide.md)**

---

## 🚀 프로젝트 마일스톤 (Milestones)

- [x] **Sprint 0-2:** 4가지 동시성 제어 방식 구현 및 인프라 구축
- [x] **Sprint 3-5:** k6 기반 부하 테스트 및 한계 돌파 (Virtual Threads 최적화)
- [x] **Sprint 6:** **심화 연구 (Deep Dive)** - 실무 사례 분석 및 운영 가이드 집대성
- [x] **Sprint 7:** **상황별 최적화 검증 (Best Fit Verification)** - "은탄환은 없다"를 4가지 시나리오로 증명

---

## ⚡ Quick Start

프로젝트를 로컬에서 즉시 실행해볼 수 있습니다.

```bash
# 1. 인프라 실행
make up

# 2. 애플리케이션 빌드
make build

# 3. Capacity Test 실행 (Lua Script 모드)
make test-capacity METHOD=lua-script
```

---

## 📚 문서 (Documentation)

- **[Performance Report V3](docs/reports/performance-v3.md)**: 상황별 최적화 검증 통합 리포트 (Sprint 7)
- **[Performance Report V2](docs/reports/performance-v2.md)**: 절대 비교 성능 리포트 (Sprint 5-6)
- **[Practical Guide](docs/reports/practical-guide.md)**: 실무 적용 가이드
- **[Architecture](docs/architecture/system-overview.md)**: 시스템 설계도
- **[k6 Study](docs/technology/k6-study.md)**: 부하 테스트 방법론

---

### 👨‍💻 Author
**JuJin** (Backend Engineer)
> "데이터로 증명하고, 자동화로 해결합니다."