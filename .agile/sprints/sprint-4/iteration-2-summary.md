# Iteration 2 Summary: Hell Test (선착순 이벤트 검증)

**Sprint:** Sprint 4 - 문서화 + 프로젝트 완성
**Iteration:** 2/3
**완료일:** 2026-01-28
**상태:** ✅ 완료 (한계 검증 및 성능 비교 완료)

---

## 1. 테스트 목적 및 시나리오 수정

**목표:**
> "재고 100개 vs 유저 5,000명"이라는 제어된 극한 상황(Controlled Extreme Condition)에서 4가지 동시성 제어 방법의 정량적 성능과 데이터 정합성을 검증한다.

**시나리오 (Hell Test):**
- **Target:** 5,000 Concurrent Users (VUs)
- **Condition:** **Tuned Spec** (Tomcat Threads 500 / CPU 4.0 / Mem 4G) + Warm-up applied
- **Metric:** Latency (p95), Throughput (RPS), Success Rate, Data Consistency

---

## 2. 테스트 수행 결과 (Quantitative Analysis)

**Tuned Spec** (Threads 500, CPU 4.0) 환경에서 5,000명의 동시 접속자를 대상으로 수행한 결과입니다. (RPS 높은 순 정렬)

| Rank | Method | Throughput (RPS) | Latency p(95) | Duration (Total) | 특징 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 🥇 | **Lua Script** | **3,602 /s** | **1.09s** | **1.4s** | **압도적 성능.** DB I/O가 없어 처리량이 가장 높음. |
| 🥈 | **Optimistic** | 3,080 /s | 1.27s | 1.6s | **Fast Fail.** 충돌 시 즉시 리턴되어 대기 시간이 짧음. |
| 🥉 | **Pessimistic** | 1,416 /s | 3.15s | 3.5s | **Blocking.** DB Lock 대기로 인해 처리량이 절반 이하로 떨어짐. |
| 4 | **Redis Lock** | 858 /s | 5.21s | 5.8s | **Overhead.** 5,000개의 분산 락 요청 처리로 인한 심각한 병목. |

> **Verification:** 모든 테스트 직후 `make show-db`를 통해 **재고가 정확히 0개**로 마감되었음을 검증했습니다.

---

## 3. 심층 분석: 한계 돌파 리포트 (Post-Mortem)

"왜 1만 명은 실패했는가?"를 규명하기 위해 수행한 단계별 실험 결과입니다.

### 3.1 The Wall: 10,000 VUs
- **결과:** 모든 방식에서 **Connection Error (약 9%)** 발생.
- **원인:** 단일 서버(Single Node)의 **OS TCP Ephemeral Port 고갈** 및 **Context Switching 비용** 급증.
- **결론:** 애플리케이션 레벨(Lock)이 아닌 인프라 레벨의 물리적 한계. Scale-out 필수.

### 3.2 The Sweet Spot: 5,000 VUs
- **Tuning 효과:**
    - **Default Spec (Threads 200 / CPU 2.0):** 처리 시간 **7초 이상** (불안정).
    - **Tuned Spec (Threads 500 / CPU 4.0):** 처리 시간 **1초 대** (안정적).
- **결론:** **Tomcat Thread(500)** 및 **Docker Resource(CPU 4.0)** 튜닝을 통해 처리 용량을 2.5배 이상 개선함.

---

## 4. 재현 가이드 (How to Reproduce)

이 테스트 결과를 직접 검증해 볼 수 있는 명령어입니다.

### Step 1: 환경 설정 (Tuning)
`src/main/resources/application.yml` 및 `docker-compose.yml`에서 스펙을 상향해야 합니다. (Thread 500, CPU 4.0)

### Step 2: 테스트 실행 (One-liner)
인프라 실행 -> 데이터 리셋 -> 웜업 -> 본 테스트(5k) -> DB 검증을 순차적으로 수행합니다.

**Lua Script:**
```bash
make reset && \
k6 run -e METHOD=lua-script --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=lua-script -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db; make show-redis
```

**Pessimistic Lock:**
```bash
make reset && \
k6 run -e METHOD=pessimistic --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=pessimistic -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

**Optimistic Lock:**
```bash
make reset && \
k6 run -e METHOD=optimistic --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=optimistic -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

**Redis Lock:**
```bash
make reset && \
k6 run -e METHOD=redis-lock --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1 && \
echo "Warm-up Done" && sleep 5 && \
k6 run -e METHOD=redis-lock -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js; \
make show-db
```

---

## 사용자 피드백 (User Feedback)
- [ ] 문서(`docs/performance-test-result.md`, `README.md`) 업데이트 승인
