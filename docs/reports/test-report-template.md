# 📊 [Test Name] Test Report (테스트 결과 분석)

## 🎯 테스트 목적

이 테스트가 검증하고자 하는 핵심 목표를 한 문장으로 정의합니다.
(예: "극한의 트래픽 상황에서 시스템이 죽지 않고 버티는지 확인한다.")

**핵심 질문 (Key Questions):**
- "질문 1 (예: 5,000명이 동시에 접속하면 서버가 터지는가?)"
- "질문 2 (예: 재고가 0이 되었을 때 초과 판매가 발생하는가?)"

---

## 📋 테스트 조건 (Test Specifications)

### 1.1 Infrastructure (AWS t3.small Profile)
| 서비스 | 자원 할당 (Docker Limit) | 비고 |
| :--- | :--- | :--- |
| **App (Spring Boot)** | 2.0 vCPU, 2GB RAM | Java 21, Virtual Threads |
| **MySQL 8.0** | 2.0 vCPU, 2GB RAM | InnoDB, HikariCP Max 50 |
| **Redis 7.0** | 2.0 vCPU, 1.37GB RAM | Lettuce Max 50 |
| **Test Runner** | **k6 in Docker** | **내부망 직접 통신 (병목 제거)** |

### 1.2 Tuning Config
*   **스레드 모델:** `spring.threads.virtual.enabled: true`
*   **로그 레벨:** `INFO` (성능 저하 방지)
*   **OS 튜닝:** `ulimits -n 65535`

### 1.3 Scenario Spec
| 항목 | 설정값 | 비고 |
|:---|:---|:---|
| **Stock** | [수량] | 예: 100개 (경합), 10,000개 (용량) |
| **VUs** | [사용자 수] | 예: 100 (High), 5,000 (Hell) |
| **Executor** | [실행 방식] | 예: `shared-iterations`, `constant-vus`, `ramping-arrival-rate` |
| **Duration** | [시간] | 예: 30s |

---

## 🚀 실행 가이드 (Execution)

이 테스트는 **Docker 컨테이너 내부에서 k6를 실행**하여 네트워크 오버헤드를 최소화하며, 각 테스트 간의 **격리성(Isolation)**을 보장하기 위해 매회 인프라를 재시작합니다.

### 1. 테스트 격리 및 실행
```bash
# 1. 인프라 완전 재시작 (격리성 보장 - 필수)
# 이전 테스트의 커넥션 풀, OS 캐시, Redis 메모리 상태를 초기화합니다.
make clean && make up && sleep 15

# 2. 데이터 초기화 (데이터 리셋)
# 옵션: reset-100 (경합용), reset-1k (일반), reset-10k (대용량)
make reset-[amount]

# 3. 테스트 수행 (Warm-up 자동 포함)
# METHOD: [lua-script | optimistic | pessimistic | redis-lock]
make test-[name] METHOD=[method] [OPTIONS]
```

### 2. 정합성 검증
```bash
make show-db     # Expected: 0 (or 남은 수량)
make show-redis  # Expected: 0 (or 남은 수량)
```

---

## 📊 지표 해석 가이드 (Interpretation Guide)

이 테스트 데이터 분석 시 집중해야 할 포인트입니다.

1.  **TPS (Throughput):**
    *   **의미:** 초당 처리 건수.
    *   **판단 기준:** [기준 설명] (예: 경합 상황에서는 높을수록 방어력이 좋음)

2.  **Latency (p95):**
    *   **의미:** 하위 95% 요청의 최대 응답 시간.
    *   **판단 기준:** [기준 설명] (예: 락 대기열이 길어지면 급증함)

3.  **Error Rate:**
    *   **HTTP 409 (Conflict):** 비즈니스 로직에 의한 정상 거절. (긍정적 신호)
    *   **HTTP 500 / Timeout:** 시스템 장애. (실패 신호)

---

## 📝 측정 결과 (Measured Data)

**실행 환경:** Mac Studio (M1 Max) / Docker Desktop

| 순위 | 방식 (Method) | TPS (req/s) | p95 Latency | 성공률 (Logic) | 비고 |
|:---:|:---|:---:|:---:|:---:|:---|
| **1** | **Lua Script** | - | - | - | - |
| **2** | **Optimistic** | - | - | - | - |
| **3** | **Pessimistic** | - | - | - | - |
| **4** | **Redis Lock** | - | - | - | - |

---

## 💡 분석 및 결론 (Insights)

### 1. [Insight Title 1]
- **관찰:** (예: Lua Script가 가장 높은 처리량을 보임)
- **원인:** (예: 락 획득 과정 없이 Redis 내부 원자성 활용)
- **결론:** (예: 단순 재고 차감 로직에는 Lua가 최적)

### 2. [Insight Title 2]
- **관찰:** (예: Redis Lock에서 타임아웃 다수 발생)
- **원인:** (예: 분산 락 네트워크 RTT 오버헤드)
- **결론:** (예: 고트래픽 환경에서 Redisson Lock 주의 필요)

---

## 🚀 확장 전략 (Scalability Strategy)

테스트 결과를 바탕으로 더 높은 트래픽을 감당하기 위한 제언입니다.

*   **Scale-up (수직 확장):** CPU/Memory 증설이 효과적인가?
*   **Scale-out (수평 확장):** 노드 추가가 효과적인가? (Redis Cluster, DB Sharding 등)
*   **Architecture:** 캐싱, 비동기 큐 도입 등 구조적 개선 필요성.
