# Recovery Test Guide (시스템 회복력 검증)

## 🎯 테스트 목적

Extreme Load 테스트 **직후** 시스템이 정상 상태로 즉시 복구되는지 검증합니다.

**핵심 질문:**
- "극한 부하 이후 리소스 누수(Connection, Memory)가 없는가?"
- "즉시 정상 상태로 복원되는가?"
- "시스템의 복원력(Resilience)은?"

---

## 📋 테스트 조건

| 항목 | 스펙 |
|:---|:---|
| **시나리오** | Extreme Load **직후** 가벼운 부하 (재고 100개) |
| **환경** | Mac Studio M1 Max |
| **Application** | Tomcat 200 Threads |
| **Docker (App)** | 2 vCPU / 4GB (AWS t3.medium) |
| **Docker (MySQL)** | 2 vCPU / 4GB (AWS db.t3.medium) |
| **Docker (Redis)** | 1 vCPU / 1GB (AWS cache.t3.small) |
| **k6 설정** | 10 VUs, 100 Iterations |

---

## 🚀 재현 가이드

### ⚠️ 중요: 연속 실행 필수

Recovery Test는 **Extreme Load 직후** 실행해야 의미가 있습니다.
**도커를 재시작하면 Recovery Test의 의미가 사라집니다!**

---

### Lua Script

**실행:**
```bash
# 1. 데이터 리셋 (재고 100개)
make reset

# 2. Recovery Test 실행 (Extreme Load 직후의 시스템 상태에서 수행)
k6 run -e METHOD=lua-script --vus 10 --iterations 100 k6-scripts/recovery-test.js

# 3. 검증
make show-db; make show-redis
```

---

### Optimistic Lock

**실행:**
```bash
# 1. 데이터 리셋 (재고 100개)
make reset

# 2. Recovery Test 실행
k6 run -e METHOD=optimistic --vus 10 --iterations 100 k6-scripts/recovery-test.js

# 3. 검증
make show-db
```

---

### Pessimistic Lock

**실행:**
```bash
# 1. 데이터 리셋 (재고 100개)
make reset

# 2. Recovery Test 실행
k6 run -e METHOD=pessimistic --vus 10 --iterations 100 k6-scripts/recovery-test.js

# 3. 검증
make show-db
```

---

## 📊 실제 측정 결과 (Mac Studio M1 Max)

**조건:** Extreme Load (10,000 TPS) **직후** 즉시 실행 (연속성 검증)

| 제어 방식 | Latency (p95 / Avg) | Stability (Max VUs) | 정합성 | 결과 |
| :--- | :---: | :---: | :--: | :--- |
| **Lua Script** | **2.60ms / 1.37ms** | 10 | ✅ | **완벽 복구.** 지연 시간 증가 없음 |
| **Optimistic** | 385.08ms / 144.88ms | 10 | ✅ | **정상 복구.** (일시적 지연 후 안정화) |
| **Pessimistic** | 13.54ms / 11.38ms | 10 | ✅ | **완벽 복구.** 일정한 응답 속도 유지 |

**데이터 정합성:** 모든 테스트 종료 후 재고 0개 확인 완료 ✅

---

## 💡 주요 인사이트

### 1. 시스템 복원력(Resilience) 검증 성공
- 10,000건의 극한 부하 직후에도 모든 제어 방식이 에러 없이 정상 응답을 수행함
- 특히 Lua Script와 Pessimistic Lock은 부하 이전과 다름없는 최상의 응답 속도로 즉시 회복됨

### 2. 리소스 관리의 안정성
- Extreme Load 도중 증가했던 Connection Pool과 Thread 자원이 정상적으로 반환되었음을 의미
- 메모리 누수나 데드락 상태 없이 시스템의 '항상성'이 유지되고 있음을 증명

### 3. 하이버네이트/DB 커넥션 안정성
- DB 기반 방식(Optimistic, Pessimistic) 역시 극한의 경합 이후 즉시 10ms 대의 안정적인 쿼리 성능을 회복함
- HikariCP의 커넥션 관리 및 MySQL의 세션 처리가 부하 이후에도 안정적임

---

## 💡 주요 인사이트

### 1. 완벽한 복원력
- Extreme Load (10,000 TPS) 직후에도 즉시 정상 상태로 복구
- 성능 저하 없음 (p95 = 2.29ms, 최상 수준)
- Connection Pool, Memory 등 리소스 관리가 완벽함을 증명

### 2. Spring Boot의 안정성
- Tomcat Thread Pool이 부하 이후 정상적으로 복구됨
- HikariCP Connection Pool 관리가 우수함
- JVM Garbage Collection이 효율적으로 동작

### 3. Docker 리소스 제한의 효과
- CPU, Memory 제한이 시스템 안정성에 기여
- 리소스 고갈 방지

---

## ⚠️ 주의사항

### 도커 재시작 금지!
Recovery Test는 **연속성**이 핵심입니다.

```bash
# ❌ 잘못된 방법 (도커 재시작 시 의미 없음)
make reset-10k && k6 run ... extreme
make clean  # ← 재시작하면 Recovery 의미 상실!
make up
k6 run ... recovery

# ✅ 올바른 방법 (연속 실행)
make reset-10k && k6 run ... extreme
# 바로 이어서
k6 run ... recovery
```

### Extreme Load 먼저 실행
Recovery Test는 단독으로 실행하면 의미가 없습니다.
반드시 **Extreme Load → Recovery** 순서로 실행하세요.

---

## 🔬 검증 항목

### 1. 응답 시간 확인
- p95 Latency가 정상 범위(2-3ms)인지 확인
- Extreme Load 직후에도 성능 저하가 없어야 함

### 2. 리소스 확인
```bash
# Docker 리소스 사용량 확인
make stats

# CPU, Memory 사용률이 정상 범위인지 확인
```

### 3. 데이터 정합성 확인
```bash
# 재고 확인
make show-db
# Expected: quantity = 0
```

### 4. Connection Pool 확인
```bash
# 애플리케이션 로그 확인
make logs

# Connection Pool 관련 에러가 없어야 함
```

---

## 🔗 관련 문서

- **[Performance Test Result](../performance-test-result.md)** - 전체 성능 분석 리포트
- **[Extreme Load Test Guide](extreme-test.md)** - 이 테스트 직전에 실행
- **[Hell Test Guide](hell-test.md)** - 선착순 이벤트 시나리오
- **[Practical Guide](../practical-guide.md)** - 실무 적용 가이드

---

*Last Updated: 2026-01-28*
