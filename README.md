# Concurrency Control PoC: High-Traffic Inventory System

> **이커머스 재고 시스템의 대규모 트래픽 처리를 위한 동시성 제어 전략 검증 프로젝트**
>
> 5,000명의 동시 접속자가 100개의 한정 수량을 경쟁하는 **선착순 이벤트(Hot Deal)** 상황에서, 데이터 정합성을 보장하면서 최대의 성능을 내는 최적의 아키텍처를 엔지니어링 관점에서 탐구했습니다.

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-green?style=flat-square)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7.0-red?style=flat-square)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square)](https://www.mysql.com/)
[![k6](https://img.shields.io/badge/k6-Load%20Test-purple?style=flat-square)](https://k6.io/)

---

## 🏆 Executive Summary (핵심 성과)

격리된 테스트 환경(Isolated Environment)에서 4가지 동시성 제어 기법을 정량적으로 비교한 결과, **Redis Lua Script**가 모든 지표에서 압도적인 우위를 점했습니다.

| 시나리오 | 최적 방식 (Best Practice) | 달성 성과 | 핵심 요인 |
| :--- | :--- | :--- | :--- |
| **선착순 이벤트**<br>(5,000 VUs 경합) | 🥇 **Lua Script** | **10,539 TPS**<br>(Latency 1.0s) | • **Lock-free:** 락 대기 시간 제거<br>• **Fast Fail:** 재고 소진 시 즉시 응답 |
| **최대 처리량**<br>(재고 10,000개) | 🥇 **Lua Script** | **1,583 TPS**<br>(Latency 120ms) | • **I/O 최소화:** DB 접근 없이 메모리 연산<br>• **원자성:** 스크립트 실행의 Atomic 보장 |
| **안정성 임계점**<br>(RPS 1,000) | 🥇 **Lua Script** | **Latency 3ms** | • **리소스 효율:** 2 vCPU로 2,000 RPS 방어 |

> **결론:** 대규모 트래픽 환경에서는 **Redis Lua Script**를 도입하여 DB 부하를 0으로 만들고 처리량을 극대화해야 합니다.
>
> 📄 **[상세 성능 분석 리포트 보기 (Performance V2)](docs/reports/performance-v2.md)**

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
- **해결:** `make clean && make up` 파이프라인을 통해 매 테스트 직전 인프라를 **Cold Start** 상태로 초기화.

### 2. 시나리오 세분화
- **Capacity Test:** 재고가 넉넉할 때(10k) 시스템이 처리할 수 있는 최대 TPS 측정. → **[리포트](docs/reports/capacity-report.md)**
- **Contention Test:** 재고가 부족한(100개) 핫딜 상황에서 5,000명 동시 접속 시 안정성 검증. → **[리포트](docs/reports/contention-report.md)**
- **Stress Test:** 부하를 점진적으로 늘려가며 응답 속도가 급증하는 임계점(Knee Point) 탐색. → **[리포트](docs/reports/stress-report.md)**

### 3. 최적화 (Optimization)
- **Virtual Threads:** Java 21 가상 스레드 도입으로 I/O 블로킹 비용 최소화.
- **OS Tuning:** `ulimit`, `sysctl` 튜닝으로 10,000+ Connection 수용.

---

## 📊 성능 분석 (Performance Deep Dive)

### 왜 Redis Distributed Lock은 느린가?
- **관찰:** 1,000 RPS 부하에서 p95 Latency가 **33ms**로 급증 (타 방식 3ms 대비 10배).
- **원인:** 락을 획득(`lock`)하고 해제(`unlock`)하는 과정에서 **최소 2번 이상의 네트워크 왕복(RTT)**이 발생합니다. 트래픽이 몰릴수록 이 네트워크 비용이 누적되어 병목을 유발합니다.

### 왜 Lua Script는 압도적인가?
- **관찰:** 2,000 RPS 부하에서도 Latency **2.7ms** 유지.
- **원인:** 로직 전체를 Redis 서버로 전송하여 한 번에 실행합니다. **네트워크 왕복을 1회로 줄이고**, Redis의 싱글 스레드 특성을 이용해 **락 없이도 원자성을 보장**하므로 컨텍스트 스위칭 비용이 없습니다.

---

## 🛡️ 운영 관점의 고찰 (Engineering Insights)

단순한 구현을 넘어, 실제 서비스 환경에서의 **트레이드오프(Trade-off)**와 **장애 대응**을 심층 연구했습니다. (상세: [Practical Guide](docs/operations/PRACTICAL_GUIDE.md))

### 2.1 실무 의사결정 가이드 (Decision Tree)
상황에 맞는 최적의 동시성 제어 도구를 선택하는 가이드라인입니다.

```mermaid
flowchart TD
    Start([🚀 선택 시작]) --> Q1{충돌 발생 시<br/>재시도가 자유롭고<br/>비용이 적은가?}
    
    Q1 -- "YES - 재시도 OK" --> Optimistic["⚡ Optimistic Lock<br/>낙관적 락"]
    Q1 -- "NO - 한 번에 성공해야 함" --> Q2{트래픽이 순간적으로<br/>폭주하는가?}
    
    Q2 -- "NO - 일반적 트래픽" --> Pessimistic["🔒 Pessimistic Lock<br/>비관적 락"]
    Q2 -- "YES - 고트래픽/핫스팟" --> Q3{비즈니스 로직이<br/>단순한가?}
    
    Q3 -- "YES - 단순 연산" --> LuaScript["🏎️ Redis Lua Script<br/>원자적 실행"]
    Q3 -- "NO - 복잡/외부연동" --> RedisLock["🛡️ Redis Distributed Lock<br/>분산 락"]
    
    Optimistic --> Result1["✅ 장점: 락 비용 Zero<br/>⚠️ 조건: 충돌 드물어야 함"]
    Pessimistic --> Result2["✅ 장점: 정합성 최고<br/>⚠️ 조건: DB 부하 감당"]
    LuaScript --> Result3["✅ 장점: 극한의 TPS<br/>⚠️ 조건: 로직 간결 & Hot Key"]
    RedisLock --> Result4["✅ 장점: DB 보호 & 제어<br/>💡 팁: 하이브리드 전략 가능"]
```

### 2.2 상세 운영 가이드 (Ops Guides)
*   **[Pessimistic Lock 가이드](docs/operations/pessimistic-lock-ops.md):** DB 인덱스 락의 원리와 데드락 방지 전략.
*   **[Optimistic Lock 가이드](docs/operations/optimistic-lock-ops.md):** 재시도 폭풍($O(N^2)$) 방지와 UX 패턴 분석.
*   **[Redis Distributed Lock 가이드](docs/operations/redis-lock-ops.md):** Throttling을 통한 DB 보호 및 3단계 Fallback.
*   **[Lua Script 가이드](docs/operations/lua-script-ops.md):** 성능의 경제학(Trust vs UX)과 벌크헤드(Bulkhead) 격리.

---

## 🚀 프로젝트 마일스톤 (Milestones)

- [x] **Sprint 0-2:** 4가지 동시성 제어 방식 구현 및 인프라 구축
- [x] **Sprint 3-5:** k6 기반 부하 테스트 및 한계 돌파 (Virtual Threads 최적화)
- [x] **Sprint 6:** **심화 연구 (Deep Dive)** - 실무 사례 분석 및 운영 가이드 집대성
- [ ] **Sprint 7 (Upcoming):** **상황별 최적화 검증** - 각 방식의 'Best Fit' 시나리오 실제 증명

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

- **[Performance Report V2](docs/reports/performance-v2.md)**: 최종 성능 분석 리포트
- **[Practical Guide](docs/reports/practical-guide.md)**: 실무 적용 가이드
- **[Architecture](docs/architecture/system-overview.md)**: 시스템 설계도
- **[k6 Study](docs/technology/k6-study.md)**: 부하 테스트 방법론

---

### 👨‍💻 Author
**JuJin** (Backend Engineer)
> "데이터로 증명하고, 자동화로 해결합니다."