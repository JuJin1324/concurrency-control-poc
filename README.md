# Concurrency Control PoC

> **대규모 트래픽 환경에서의 재고 차감 동시성 제어 전략 비교 및 검증**
>
> 데이터 정합성을 보장하기 위한 **4가지 동시성 제어 기법**을 구현하고, 일반적인 부하(High Load)부터 극한의 선착순 이벤트(Hell Test)까지 단계별로 성능과 안정성을 정량적으로 분석한 엔지니어링 프로젝트입니다.

---

## ⚡ Quick Start

5분 안에 프로젝트를 실행하고 성능 테스트 결과를 재현할 수 있습니다.

### 1. 인프라 시작
```bash
# MySQL + Redis 컨테이너 실행
make up

# 컨테이너 상태 확인 (Healthy 대기)
make ps
```

### 2. 애플리케이션 빌드
```bash
# 애플리케이션 빌드 및 도커 이미지 생성
make build
```

### 3. Hell Test 실행 (대표 예시)

**Lua Script (Best Performance - 3,602 RPS):**
```bash
make reset && \
k6 run -e METHOD=lua-script --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=lua-script -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db; make show-redis
```

**검증:**
```bash
make show-db
# Expected: quantity = 0 ✅
```

> 💡 **더 많은 테스트 시나리오:**
> - **[Hell Test 완전 가이드](docs/test-guides/hell-test.md)** - 4가지 메서드 전체 재현 명령어
> - **[High Load Test](docs/test-guides/high-test.md)** - 일반적 대규모 트래픽 (재고 1,000개)
> - **[Extreme Load Test](docs/test-guides/extreme-test.md)** - 시스템 한계 탐색 (재고 10,000개)
> - **[Recovery Test](docs/test-guides/recovery-test.md)** - 회복력 검증 (Extreme 직후 실행)

---

## 📊 성능 테스트 결과 (Hell Test)

**조건:** 재고 100개 vs 5,000명 동시 접속 / Tomcat 200 Threads + AWS t3.medium 급

| 순위 | 제어 방식 | Latency (p95 / Avg) | Throughput | Stability (Max VUs) | 정합성 | 특이사항 |
| :--: | :--- | :---: | :---: | :---: | :--: | :--- |
| 🥇 | **Lua Script** | **1.12s / 447ms** | **3,518 req/s** | 5,000 | ✅ | **선착순 이벤트 최강자.** 압도적 처리량 |
| 🥈 | **Optimistic** | 1.33s / 615ms | 3,354 req/s | 5,000 | ✅ | **Fast Fail.** 충돌 시 즉시 리턴으로 고성능 유지 |
| 🥉 | **Pessimistic** | 3.58s / 1.48s | 1,280 req/s | 5,000 | ✅ | **Stable.** DB 락 대기로 처리량 하락 |
| 4 | **Redis Lock** | 5.82s / 2.15s | 742 req/s | 5,000 | ✅ | **Overhead.** 분산 락 부하로 인한 성능 병목 |

---

## 📈 종합 성능 요약 (Scenario Comparison)

각 부하 시나리오별 최적의 제어 방식(🥇)을 정리한 결과입니다.

| 시나리오 | 규모 (재고 / RPS) | 최적 방식 (🥇) | p95 Latency | 핵심 인사이트 |
| :--- | :---: | :---: | :---: | :--- |
| **High Load** | 1,000 / 2,000 | **Lua Script** | **7.45ms** | 일반적 부하에서 가장 적은 리소스로 최고 효율 |
| **Extreme Load** | 10,000 / 2,000 | **Pessimistic** | **4.05ms** | 극한의 경합 시 락 큐를 통한 순차 처리가 가장 안정적 |
| **Hell Test** | 100 / 5,000 VUs | **Lua Script** | **1.12s** | 선착순 이벤트와 같은 폭발적 요청에 최적화 |
| **Recovery** | 100 / 가벼운 부하 | **All Methods** | **~3ms** | 모든 방식이 극한 부하 이후 즉시 정상 성능으로 복구됨 |

> 💡 **상세 테스트 가이드:**
> - [Hell Test (선착순)](docs/test-guides/hell-test.md) / [High Load](docs/test-guides/high-test.md) / [Extreme Load](docs/test-guides/extreme-test.md) / [Recovery](docs/test-guides/recovery-test.md)

---

## 🎯 프로젝트 목표

**What (무엇을):**
- 이커머스 재고 차감 시나리오에서 발생하는 동시성 문제를 4가지 방법으로 해결

**Why (왜):**
- 대기업 백엔드 엔지니어 포지션의 "대규모 트래픽 처리 경험" 요구사항 증명
- 정량 지표(TPS, Latency)로 검증된 포트폴리오 구축

**How (어떻게):**
1. **Pessimistic Lock** - DB `SELECT ... FOR UPDATE`
2. **Optimistic Lock** - JPA `@Version` + Retry
3. **Redis Distributed Lock** - Redisson `RLock`
4. **Redis Lua Script** - Atomic Operation

---

## 🏗️ 아키텍처

### Tech Stack
- **Backend:** Java 21, Spring Boot 3.4.1
- **Database:** MySQL 8.0
- **Cache:** Redis 7.0
- **Load Test:** k6
- **Container:** Docker Compose

### 패키지 구조 (Layered Architecture)
```
src/main/java/com/concurrency/poc/
├── domain/          # Entity + 비즈니스 로직
├── repository/      # Spring Data JPA
├── service/         # 전략 패턴 (4가지 구현체)
└── controller/      # REST API + DTO
```

> 📐 **[아키텍처 상세 문서](docs/architecture/)** - C4 Diagram, Sequence Diagram 포함

---

## 🧪 테스트 시나리오

프로젝트는 4가지 테스트 시나리오를 제공하며, 각 시나리오별 상세 가이드 문서가 있습니다.

| 시나리오 | 목적 | 재고 | 부하 | 가이드 |
| :--- | :--- | :---: | :---: | :---: |
| **High Load** | 일반적 대규모 트래픽 효율성 비교 | 1,000개 | ~2,000 RPS | **[가이드 →](docs/test-guides/high-test.md)** |
| **Extreme Load** | 시스템 한계(Knee Point) 탐색 | 10,000개 | ~2,000 RPS | **[가이드 →](docs/test-guides/extreme-test.md)** |
| **Hell Test** | 선착순 이벤트 (극한 경합) | 100개 | 5,000 VUs | **[가이드 →](docs/test-guides/hell-test.md)** |
| **Recovery** | 극한 부하 이후 회복력 검증 | 100개 | 가벼운 부하 | **[가이드 →](docs/test-guides/recovery-test.md)** |

각 가이드에는 **4가지 메서드별 재현 명령어**와 **실제 측정 결과 (Mac Studio M1 Max 기준)**가 포함되어 있습니다.

---

## 🛠️ Makefile 명령어

### 인프라 관리
```bash
make build      # 애플리케이션 빌드 + 도커 이미지 생성
make up         # 컨테이너 실행 (MySQL + Redis + App)
make down       # 컨테이너 종료
make clean      # 컨테이너 및 볼륨 삭제
make ps         # 컨테이너 상태 확인
make logs       # 애플리케이션 로그 확인
make stats      # 리소스 사용량 확인
```

### 데이터 관리
```bash
make reset      # 재고 100개로 초기화
make reset-1k   # 재고 1,000개로 초기화
make reset-10k  # 재고 10,000개로 초기화
make show-db    # MySQL 재고 조회
make show-redis # Redis 재고 조회
```

### 테스트 유틸리티
```bash
make warmup METHOD=lua-script  # 시스템 예열 (JVM JIT, Connection Pool)
```

### DB/Redis 접속
```bash
make mysql      # MySQL 접속 (app_user/app_password)
make redis      # Redis CLI 접속
```

---

## 📚 프로젝트 문서

### 테스트 가이드
- **[Hell Test](docs/test-guides/hell-test.md)** - 선착순 이벤트 (재고 100개 vs 5,000 VUs)
- **[High Load Test](docs/test-guides/high-test.md)** - 일반적 대규모 트래픽 (재고 1,000개)
- **[Extreme Load Test](docs/test-guides/extreme-test.md)** - 시스템 한계 탐색 (재고 10,000개)
- **[Recovery Test](docs/test-guides/recovery-test.md)** - 회복력 검증 (Extreme 직후)

### 아키텍처
- **[System Overview](docs/architecture/system-overview.md)** - C4 Diagram, Sequence Diagram
- **[Application Architecture](docs/architecture/application.md)** - 패키지 구조, ArchUnit 규칙
- **[Infrastructure](docs/architecture/infrastructure.md)** - Docker Compose 구성

### 기술 탐구
- **[Redis Deep Dive](docs/technology/redis-deep-dive.md)** - Single Thread 아키텍처와 원자성
- **[k6 Overview](docs/technology/k6-overview.md)** - k6 개념 및 성능 측정 방법론

### 의사결정 기록
- **[ADR-001](docs/adr/ADR-001-why-these-four-methods.md)** - 왜 이 4가지 방법인가?
- **[ADR-002](docs/adr/ADR-002-why-poc-scope.md)** - 왜 PoC 범위로 축소했는가?
- **[ADR-003](docs/adr/ADR-003-why-visualization-first.md)** - 왜 시각화를 먼저 하는가?
- **[ADR-004](docs/adr/ADR-004-why-mysql-and-redis.md)** - 왜 MySQL과 Redis인가?
- **[ADR-005](docs/adr/ADR-005-why-layered-architecture.md)** - 왜 Layered Architecture인가?

### 성능 분석
- **[Practical Guide](docs/practical-guide.md)** - 실무 적용 가이드
- **[Alignment Check](docs/alignment-check.md)** - 기획 vs 실제 구현 정합성 검증
- **[Performance Metrics](docs/test-guides/high-test.md#📊-실제-측정-결과-mac-studio-m1-max)** - 테스트 가이드별 실제 측정 지표 포함

---

## 🚀 Sprint 진행 과정

### Sprint 0: 플랫폼 엔지니어링 + 아키텍처 설계
- Docker Compose 환경 구축 (MySQL + Redis)
- Spring Boot 프로젝트 스캐폴딩
- C4 Diagram, Sequence Diagram 작성
- 5개 ADR 작성 (의사결정 기록)

### Sprint 1: DB Lock 구현
- Stock Domain 모델 구현 (JPA Entity + 비즈니스 로직)
- Pessimistic Lock 구현 (`SELECT ... FOR UPDATE`)
- Optimistic Lock 구현 (`@Version` + Retry)
- REST API 구현 (Strategy Pattern)

### Sprint 2: Redis 구현
- Redis Distributed Lock 구현 (Redisson `RLock`)
- Redis Lua Script 구현 (Atomic Operation)
- 4가지 방법 통합 및 예비 비교

### Sprint 3: k6 부하 테스트 + 성능 비교
- k6 환경 구축 및 시나리오 작성
- High Load, Extreme Load 테스트
- 성능 비교 리포트 작성
- 실무 적용 가이드 작성

### Sprint 4: 문서화 + 프로젝트 완성 (진행 중)
- 프로젝트 정합성 검증 (Iteration 1)
- Hell Test 수행 및 리포트 업데이트 (Iteration 2)
- 테스트 인프라 재구성 (Iteration 3)
- README.md 고도화 (Iteration 4 - 현재)

---

## 💡 핵심 인사이트

### 1. Warm-up의 중요성
- 초기 테스트 대비 약 **30% 이상 성능 향상**
- JVM JIT 컴파일, OS 캐시, Connection Pool 초기화가 결정적 영향

### 2. Optimistic Lock의 재발견
- "충돌이 많으면 느리다"는 통념과 달리, 실제 네트워크 환경에서는 요청 간격(Pacing)이 자연스럽게 경합을 완화
- 저지연 환경에서 Pessimistic Lock보다 우수한 성능

### 3. 시스템 한계 발견
- 10,000 VUs 테스트 시 모든 방식에서 Connection Error 발생 (약 9%)
- 원인: OS TCP Ephemeral Port 고갈 + Context Switching 비용
- 해결: Tuning (Tomcat Threads 500 / CPU 4.0) → 5,000 VUs에서 안정화

---

## 🎓 학습 목표 달성

✅ **대규모 트래픽 처리 경험 증명**
- 5,000 VUs 동시 접속 처리
- TPS, Latency, Success Rate 정량 지표 측정

✅ **동시성 제어 전문성 확보**
- 4가지 방법의 Trade-off 명확히 이해
- 실무 적용 가능한 가이드 작성

✅ **재현 가능성 보장**
- Docker Compose로 일관된 환경
- k6 스크립트로 즉시 재현 가능
- Makefile로 반복 작업 자동화

---

## 👨‍💻 Author

**JuJin** (Backend Engineer)

> "데이터로 증명하고 자동화로 해결하는 엔지니어링을 지향합니다."

---

## 📄 License

MIT License

---

*Last Updated: 2026-01-28*
