# Concurrency Control PoC

> **대규모 트래픽 환경에서의 재고 차감 동시성 제어 전략 비교 및 검증**
>
> 데이터 정합성을 보장하기 위한 **4가지 동시성 제어 기법**을 구현하고, **Capacity(최대 용량)**, **Contention(극한 경합)**, **Stress(임계점)** 등 다양한 시나리오에서 시스템 성능을 정량적으로 분석한 엔지니어링 프로젝트입니다.

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

### 3. 성능 테스트 실행 (Capacity Test 예시)

**Lua Script (Best Performance - 1,500+ TPS):**
```bash
# 격리된 환경에서 테스트 실행 (데이터 초기화 + Warmup 자동 포함)
make test-capacity METHOD=lua-script
```

> 💡 **테스트 결과 상세 리포트:**
> - **[Capacity Report](docs/reports/capacity-report.md)** - 최대 처리량(TPS) 분석
> - **[Contention Report](docs/reports/contention-report.md)** - 선착순 이벤트 (5,000 VUs) 방어력 분석
> - **[Stress Report](docs/reports/stress-report.md)** - 시스템 임계점(Knee Point) 분석

---

## 📊 성능 테스트 결과 요약 (Executive Summary)

**[📄 전체 리포트 보기 (Performance Report V2)](docs/reports/performance-v2.md)**

격리된 환경(Isolated Environment)에서 검증한 4가지 방식의 최종 성적표입니다.

| 순위 | 방식 (Method) | 추천 대상 | 핵심 특징 |
|:---:|:---|:---|:---|
| 🥇 | **Lua Script** | **High Traffic** | **압도적 1위.** DB 락 대비 2.5배 성능, 경합 상황에서도 완벽한 방어력. |
| 🥈 | **Pessimistic Lock** | **General / Enterprise** | **가장 안정적.** 별도 인프라 없이 RDBMS만으로 준수한 성능(600+ TPS) 보장. |
| 🥉 | **Optimistic Lock** | **Low Contention** | **양날의 검.** 재고가 넉넉할 땐 재시도 비용으로 느리지만, 품절(Sold Out) 시엔 가장 빠름. |
| 4 | **Redis Lock** | **Easy Implementation** | **비효율적.** 구현은 쉽지만 네트워크 오버헤드로 인해 성능 효율이 가장 낮음. |

### 시나리오별 상세 지표

| 시나리오 | 최적 방식 (🥇) | p95 Latency | TPS (Max) | 정합성 |
| :--- | :---: | :---: | :---: | :---: |
| **Capacity** (10k Stock) | **Lua Script** | **120ms** | **1,583 req/s** | ✅ |
| **Contention** (Sold Out) | **Lua Script** | **1.06s** | **10,539 req/s** | ✅ |
| **Stress** (1k RPS) | **Lua Script** | **3.78ms** | **Stable** | ✅ |

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
- **Backend:** Java 21, Spring Boot 3.4.1, Virtual Threads
- **Database:** MySQL 8.0
- **Cache:** Redis 7.0
- **Load Test:** k6 (Dockerized)
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

## 💡 핵심 인사이트 (Engineering Insights)

이번 프로젝트를 통해 얻은 기술적 교훈과 통찰입니다.

### 1. 격리성(Isolation)은 테스트의 기본
- 초기 테스트에서 데이터가 들쭉날쭉했던 원인은 '이전 테스트의 잔재(Connection Pool, Dirty Page)'였습니다.
- `make clean && make up`으로 매번 인프라를 초기화하는 파이프라인을 구축한 뒤, 데이터 신뢰도를 100% 확보했습니다.

### 2. Redis Distributed Lock의 배신 (네트워크 병목)
- "Redis니까 빠르겠지?"라는 통념과 달리, **분산 락은 가장 느렸습니다.**
- 락 획득/해제를 위한 반복적인 네트워크 통신(RTT)이 병목이 되어, 1,000 RPS 수준에서 이미 응답 속도가 타 방식 대비 6배 이상 느려지는 현상(Knee Point)을 발견했습니다.

### 3. Lua Script의 압도적 효율성
- Redis의 단일 스레드 원자성(Atomicity)을 활용하여 **락을 아예 제거(Lock-free)**했습니다.
- 그 결과, DB 락 대비 2.5배 이상의 처리량(TPS)과 가장 안정적인 응답 속도를 보여주며 "고부하 트래픽의 유일한 해법"임을 증명했습니다.

### 4. Optimistic Lock의 재발견 (Fast Fail)
- 재고가 충분할 땐 충돌 재시도 비용 때문에 느렸지만, **재고가 '0'이 되는 순간(품절)**에는 가장 빨랐습니다.
- DB에 쓰기 작업을 하지 않고 읽기만 하고 튕겨내는 **Fast Fail** 효과 덕분에, 선착순 이벤트 상황에서 비관적 락보다 우수한 방어력을 보였습니다.

---

## 🛠️ Makefile 명령어

### 인프라 관리
```bash
make build      # 애플리케이션 빌드 + 도커 이미지 생성
make up         # 컨테이너 실행 (MySQL + Redis + App)
make down       # 컨테이너 종료
make clean      # 컨테이너 및 볼륨 삭제 (완전 초기화)
```

### 테스트 실행 (자동화)
```bash
make test-capacity METHOD=lua-script    # Capacity Test 실행
make test-contention METHOD=pessimistic # Contention Test 실행
make test-stress METHOD=redis-lock      # Stress Test 실행
```

### 데이터 관리
```bash
make reset-100  # 재고 100개 초기화 (경합용)
make reset-10k  # 재고 10,000개 초기화 (용량/스트레스용)
make show-db    # MySQL 재고 조회
make show-redis # Redis 재고 조회
```

---

## 🚀 Sprint 진행 과정

### Sprint 0: 플랫폼 엔지니어링 + 아키텍처 설계
- Docker Compose 환경 구축 (MySQL + Redis)
- Spring Boot 프로젝트 스캐폴딩

### Sprint 1~2: 동시성 제어 구현
- 4가지 동시성 제어 방식 구현 (DB Lock, Redis Lock, Lua Script)

### Sprint 3~4: 초기 성능 테스트
- k6 초기 도입 및 기초 데이터 확보 (High/Extreme/Hell)
- *Lesson Learned: OS 튜닝 및 테스트 격리성 부족으로 인한 데이터 노이즈 확인*

### Sprint 5: 테스트 엔지니어링 및 최적화 (Completed)
- **Test Engineering:** 목적별 시나리오(Capacity/Contention/Stress) 재설계
- **Optimization:** Virtual Threads 도입, HikariCP/Lettuce 튜닝
- **Final Result:** 격리된 환경에서 신뢰할 수 있는 최종 데이터 확보 (`performance-v2.md`)

---

## 🎓 학습 목표 달성

✅ **대규모 트래픽 처리 경험 증명**
- 5,000 VUs 동시 접속 트래픽을 Lua Script로 완벽 방어 (10,000+ TPS)
- 1,000 RPS 지속 부하 상황에서 시스템 안정성 검증

✅ **동시성 제어 전문성 확보**
- 4가지 방법의 Trade-off를 정량적 데이터로 증명
- "상황에 따라 정답이 다르다"는 것을 시나리오별(Capacity/Contention) 테스트로 입증

✅ **DevOps 엔지니어링 역량**
- Docker Compose 기반의 IaC(Infrastructure as Code) 환경 구축
- Makefile을 이용한 테스트 자동화 및 파이프라인 구축

---

## 👨‍💻 Author

**JuJin** (Backend Engineer)

> "데이터로 증명하고 자동화로 해결하는 엔지니어링을 지향합니다."

---

## 📄 License

MIT License

---

*Last Updated: 2026-02-02 (Sprint 5 Completed)*
