# Concurrency Control PoC

> **재고 차감 동시성 제어 4가지 방법 성능 비교 프로젝트**
>
> High-Concurrency 환경에서 데이터 정합성을 보장하기 위한 다양한 락(Lock) 전략을 구현하고, k6를 통해 정량적인 성능 지표(TPS, Latency)를 비교 분석합니다.

---

## 🚀 프로젝트 개요 (Overview)

이 프로젝트는 **"선착순 이벤트와 같이 트래픽이 몰리는 상황에서 어떻게 재고를 안전하고 빠르게 차감할 것인가?"** 라는 질문에서 시작되었습니다.

단순한 기능 구현을 넘어, 엔터프라이즈 환경에서 고려해야 할 **동시성 문제(Race Condition)**를 재현하고, 이를 해결하는 4가지 핵심 기술을 동일한 환경에서 구현하여 장단점을 분석하는 것이 목표입니다.

### 🎯 핵심 목표
1.  **Race Condition 재현:** 100개의 재고에 10,000명의 유저가 동시에 접근할 때 발생하는 문제 확인.
2.  **4가지 해결책 구현:** Pessimistic Lock, Optimistic Lock, Redis Distributed Lock, Redis Lua Script.
3.  **정량적 비교:** k6 부하 테스트를 통해 각 방법의 **TPS(처리량)**와 **Latency(응답 속도)** 측정.

---

## 🛠 기술 스택 (Tech Stack)

| Category | Technology | Version | Note |
|----------|------------|---------|------|
| **Language** | Java | **21 (LTS)** | Record, Virtual Thread 호환 |
| **Framework** | Spring Boot | **4.0.1** | WebMVC, Data JPA, Data Redis |
| **Database** | MySQL | 8.0 | InnoDB (Row Lock) |
| **Cache/Lock** | Redis | 7.0 | Redisson, Lua Script |
| **Testing** | k6, JUnit 5 | Latest | Load Testing, Unit Testing |
| **Infra** | Docker Compose | - | 로컬 환경 구성 자동화 |

---

## 🏗 아키텍처 (Architecture)

이 프로젝트는 **Strict Layered Architecture**를 따르며, 도메인 객체의 오염을 방지하기 위해 Controller는 반드시 DTO를 통해서만 Service와 통신합니다.

- **[👉 시스템 전체 구성 (System Overview)](docs/architecture/system-overview.md):** C4 Container Diagram 및 시퀀스 다이어그램
- **[👉 애플리케이션 아키텍처](docs/architecture/application.md):** 패키지 구조 및 ArchUnit 규칙
- **[👉 인프라 아키텍처](docs/architecture/infrastructure.md):** Docker Compose 및 네트워크 구성

---

## 🚦 시작하기 (Quick Start)

Docker만 설치되어 있다면 1분 안에 전체 환경을 실행할 수 있습니다.

### 사전 요구사항 (Prerequisites)
- Docker & Docker Compose
- Java 21 (애플리케이션 코드 수정 시)

### 1. 실행 (Run)

```bash
# 1. 인프라 실행 (MySQL, Redis)
make up

# 2. 상태 확인 (Healthy 뜰 때까지 대기)
make ps

# 3. 데이터 초기화 (재고 100개 생성 - 최초 1회 자동 실행됨)
# 필요 시 리셋: make reset
```

### 2. 애플리케이션 실행

```bash
# Spring Boot 애플리케이션 실행
./gradlew bootRun
```

### 3. 테스트 (Test)

```bash
# 단위 및 통합 테스트 실행 (ArchUnit 포함)
./gradlew test
```

---

## 📚 구현 예정 기능 (Methods)

| 방법 | 설명 | 특징 |
|------|------|------|
| **1. Pessimistic Lock** | `SELECT ... FOR UPDATE` (MySQL) | 강력한 정합성, 성능 낮음 |
| **2. Optimistic Lock** | `@Version` (JPA) | 충돌 시 재시도, 충돌 적을 때 유리 |
| **3. Redis Lock** | Redisson Distributed Lock | DB 부하 감소, 스핀락 방지 (Pub/Sub) |
| **4. Lua Script** | Redis Atomic Execution | **가장 빠름**, 락 획득 과정 없음 |

---

## 📝 문서 (Documentation)

- **ADR (Architecture Decision Records)**
    - [ADR-001: 왜 이 4가지 방법을 비교하는가?](docs/adr/ADR-001-why-these-four-methods.md)
    - [ADR-004: 왜 MySQL과 Redis를 선택했는가?](docs/adr/ADR-004-why-mysql-and-redis.md)
    - [ADR-005: 왜 Layered Architecture를 선택했는가?](docs/adr/ADR-005-why-layered-architecture.md)

---

**Current Phase:** Sprint 0 (Foundation) Completed ✅
