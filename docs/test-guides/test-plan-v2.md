# Performance Test Plan V2 (Re-Baseline)

**작성일:** 2026-01-30
**목표:** 튜닝된 환경(Virtual Threads + Optimization)에서 4가지 동시성 제어 방식의 성능을 재측정하여 신뢰할 수 있는 지표를 확보한다.

---

## 1. 테스트 환경 (Environment Spec)

### 1.1 Host Machine
- **Model:** MacBook Air (M4)
- **OS:** macOS Sequoia (15.x)
- **Core:** 8-core CPU / 10-core GPU
- **RAM:** 16GB Unified Memory

### 1.2 Infrastructure (Docker Compose)
**표준 클라우드 인스턴스 규격(AWS t3.small Profile)**을 벤치마킹하여 리소스 제한을 적용한다. (2026-01-30 기준 사양 반영)
이는 특정 호스트 머신의 성능에 의존하지 않고, 가상화된 클라우드 자원 환경에서의 효율성을 객관적으로 검증하기 위함이다.

| Service | CPU Limit | Memory Limit | AWS Mapping |
| :--- | :---: | :---: | :---: |
| **App (Spring Boot)** | 2.0 vCPU | 2GB | EC2 t3.small |
| **MySQL 8.0** | 2.0 vCPU | 2GB | RDS db.t3.small |
| **Redis 7.0** | 2.0 vCPU | 1.37GB | ElastiCache cache.t3.small |

### 1.3 Application Configuration
- **Language:** Java 21 (Amazon Corretto or Temurin)
- **Framework:** Spring Boot 3.4
- **Key Tuning:**
    - `spring.threads.virtual.enabled: true` (Virtual Threads 활성화)
    - `spring.datasource.hikari.maximum-pool-size: 50`
    - `spring.data.redis.lettuce.pool.max-active: 50`

---

## 2. 테스트 시나리오 (Test Scenarios)

### Scenario A: Capacity Test (최대 처리량 측정)
> **"재고 10,000개를 얼마나 빨리 처리할 수 있는가?"**

- **목적:** 시스템의 순수 처리량(TPS) 측정 (Throughput)
- **조건:**
    - **Stock:** 10,000개
    - **VUs:** 100 (적당한 동시성으로 작업량 처리 집중)
    - **Iterations:** 10,000 (재고 소진 시 종료)
- **실행 명령어:**
    ```bash
    make reset-10k
    make test-capacity METHOD=<method> VUS=100 ITERATIONS=10000
    ```

### Scenario B: Contention Test (극한 경합 안정성)
> **"5,000명이 동시에 접속했을 때 서버가 버티는가?"**

- **목적:** 대규모 동시 접속 상황에서의 안정성(Stability) 및 지연 시간(Latency) 측정
- **조건:**
    - **Stock:** 100개 (극소량 재고로 경합 유도)
    - **VUs:** 5,000 (선착순 이벤트 상황)
    - **Duration:** 30s (지속 부하)
- **실행 명령어:**
    ```bash
    make reset
    make test-contention METHOD=<method> VUS=5000 DURATION=30s
    ```

---

## 3. 실행 방법 (How to Execute)

모든 테스트는 **재현 가능성(Reproducibility)**을 위해 Cold Start 상태에서 시작하는 것을 원칙으로 한다.

### 3.1 표준 테스트 프로토콜
1. **인프라 초기화 및 구동**: `make clean && make up`
2. **인프라 안정화 대기**: `sleep 15` (컨테이너 및 Spring Boot 내부 초기화 시간 확보)
3. **데이터 초기화**: `make reset-10k`
4. **테스트 실행**: `make test-capacity METHOD=[method] ...` (내부적으로 `warmup` 선행 포함)

### 3.2 테스트별 명령어 예시

#### A. Capacity Test (처리량 측정)
```bash
# Lua Script 예시
make clean && make up && sleep 15 && make reset-10k && make test-capacity METHOD=lua-script VUS=100 ITERATIONS=10000
```

#### B. Contention Test (안정성 측정)
```bash
# Redis Lock 예시
make clean && make up && sleep 15 && make reset-10k && make test-contention METHOD=redis-lock VUS=50 DURATION=30s
```

#### C. Stress Test (임계점 측정)
```bash
# Optimistic Lock 예시
make clean && make up && sleep 15 && make reset-10k && make test-stress METHOD=optimistic-lock TARGET_RPS=5000
```

---

## 4. 결과 기록 양식 (Metrics to Capture)

| Scenario | Method | Duration | TPS (req/s) | p95 Latency | Success Rate | Note |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Capacity** | Lua | - | - | - | - | - |
| | Pessimistic | - | - | - | - | - |
| | Optimistic | - | - | - | - | - |
| | Redis | - | - | - | - | - |
| **Contention** | Lua | - | - | - | - | - |
| | Pessimistic | - | - | - | - | - |
| | Optimistic | - | - | - | - | - |
| | Redis | - | - | - | - | - |

---
*Created by Gemini CLI*
