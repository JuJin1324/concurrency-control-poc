# Iteration 2 Summary: 부하 테스트 환경 구축 및 k6 시나리오 작성

**Sprint:** Sprint 3 - k6 부하 테스트 + 최종 성능 비교 분석
**Iteration:** 2/3
**완료일:** 2026-01-27
**상태:** ✅ 완료

---

## 구현 내용

### 1. 애플리케이션 컨테이너화 및 인프라 구축

- **Dockerfile:** `eclipse-temurin:21-jre-alpine`을 기반으로 JAR 실행 환경 구축.
- **docker-compose.yml 확장:**
  - `app` 서비스를 추가하고 `mysql`, `redis`와의 의존성(healthcheck) 설정.
  - **리소스 제한 적용:**
    - `app`: CPU 2.0, Memory 2GB (Limits), 1.0/1GB (Reservations)
    - `mysql`: CPU 2.0, Memory 1GB (Limits), 1.0/512MB (Reservations)
    - `redis`: CPU 1.0, Memory 512MB (Limits), 0.5/256MB (Reservations)
- **Makefile 고도화:** `make build`, `make reset`, `make stats` 등을 통해 컨테이너 환경 관리 자동화.

---

### 2. k6 부하 테스트 시나리오 작성

**위치:** `k6-scripts/`

- **4가지 방식 시나리오 완성:**
  - `pessimistic-lock-test.js`
  - `optimistic-lock-test.js`
  - `redis-lock-test.js`
  - `lua-script-test.js`
- **시나리오 설계:** `constant-arrival-rate` 익스큐터를 사용하여 초당 100개 요청을 10초간(총 1,000회) 고정적으로 발생시킴.
- **검증 로직:** HTTP 상태 코드 및 응답 내 `success` 필드 확인.

---

### 3. 환경 검증 테스트 (Pessimistic Lock)

**테스트 조건:**
- 초기 재고: 100개
- 총 요청 수: 1,001회 (초당 100회)

**테스트 결과:**
- **성공(HTTP 200):** 100회
- **실패(HTTP 409):** 901회 (재고 부족으로 인한 의도된 실패)
- **정합성:** DB 최종 재고 0개 확인.
- **성능:** p(95) Latency 약 14.68ms (Pessimistic Lock 기준).

---

## 사용자 가이드 (How-to)

### 1. 인프라 구축 및 검증
Docker 컨테이너 환경을 구축하고 리소스 제한이 적용되었는지 확인합니다.

```bash
# 1. 애플리케이션 빌드 및 실행 (전체 스택 재시작)
make build && make up

# 2. 리소스 제한(Limit) 적용 확인 (CPU, MEMORY 확인)
make stats

# 3. 컨테이너 헬스체크 (healthy 상태 확인)
make ps
```

### 2. 부하 테스트 직접 실행하기
각 동시성 제어 방식별로 다음 명령어를 사용하여 테스트할 수 있습니다.
`make reset`은 매 테스트 전 필수이며, 테스트 종료 후 상태 확인 명령어를 추가하여 정합성을 검증합니다.
*참고: k6가 Threshold 실패 시 Exit Code를 반환하므로, 명령어 연결 시 `;`를 사용하여 뒷 명령어가 항상 실행되도록 합니다.*

**Pessimistic Lock 테스트:**
```bash
make reset && k6 run k6-scripts/pessimistic-lock-test.js; make show-db
```

**Optimistic Lock 테스트:**
```bash
make reset && k6 run k6-scripts/optimistic-lock-test.js; make show-db
```

**Redis Lock 테스트:**
```bash
make reset && k6 run k6-scripts/redis-lock-test.js; make show-db
```

**Lua Script 테스트 (DB + Redis 확인):**
```bash
make reset && k6 run k6-scripts/lua-script-test.js; make show-db; make show-redis
```

### 3. 결과 분석 가이드 (집중 점검 포인트)

**1) Latency 왜곡 주의 (p95)**
- 실패 응답(409)은 성공 로직보다 처리 속도가 매우 빠를 수 있어 전체 p95를 낮추는 왜곡이 발생합니다.
- **해결:** `http_req_duration { expected_response:true }` 항목을 확인하세요. 이것이 **성공한 요청들의 실제 처리 속도**입니다.

**2) TPS (Throughput)**
- `http_reqs`의 `rate`를 통해 초당 얼마나 많은 요청을 처리했는지 확인합니다.

**3) 데이터 정합성**
- 테스트 종료 후 반드시 `make show-db` (Lua의 경우 `make show-redis` 포함)를 실행하여 재고가 정확히 `0`인지 확인합니다.

---

## 주요 성과

| 항목 | 성과 |
|------|------|
| **인프라 재현성** | Docker Compose를 통해 누구나 동일한 리소스 제한 환경에서 테스트 가능 |
| **자동화** | `make build && make up` 한 번으로 전체 테스트 환경 구축 완료 |
| **시나리오 준비** | 비교 분석을 위한 4가지 방식의 k6 스크립트 모두 확보 |

---

## 생성/수정된 파일

| 파일 | 설명 |
|------|------|
| `Dockerfile`, `.dockerignore` | 애플리케이션 컨테이너 빌드 설정 |
| `docker-compose.yml` | 리소스 제한 및 서비스 정의 업데이트 |
| `Makefile` | 빌드 및 관리 명령어 업데이트 |
| `k6-scripts/*-test.js` | 4가지 방식별 부하 테스트 스크립트 (4개) |

---

## 다음 Iteration 예고

**Iteration 3: 전체 방법 부하 테스트 및 결과 분석**
- 4가지 방식에 대해 동일 조건(100/1000회)으로 반복 측정
- 성능 비교표 및 그래프 작성
- 최종 성능 리포트 및 실무 적용 가이드 작성

---

## 사용자 피드백 및 Q&A

### 1. 인프라 및 Docker 리소스
- **Docker limits vs reservations:** `reservations`는 컨테이너 시작 시 보장받는 최소 리소스(Soft Limit)이며, `limits`는 초과 불가능한 최대 리소스(Hard Limit)입니다. 호스트 OS는 이 범위 내에서 자원을 동적으로 스케줄링합니다.

### 2. 락 동작 및 데이터 정합성
- **Pessimistic Lock과 Version 컬럼:** 엔티티에 `@Version`이 정의되어 있어 JPA 업데이트 시 자동으로 증가합니다. 비관적 락 자체에는 영향을 주지 않으나 JPA의 기본 동작으로 기록됩니다.
- **정합성 확인:** 테스트별 용도에 맞춰 `make show-db`와 `make show-redis`를 구분하여 사용합니다. Redis Lock은 락 용도로만 사용하므로 DB만 확인하며, Lua Script는 Redis 내 데이터를 직접 조작하므로 양쪽 모두 확인이 필요합니다.

### 3. k6 부하 테스트 해석
- **왜 요청이 1001개인가요?:** k6 `constant-arrival-rate` 실행기의 시간 경계 처리 오차(0~10초 사이의 틱 계산)로 인해 발생할 수 있는 정상적인 현상입니다.
- **JUnit vs k6 성능 차이:** JUnit(함수 호출)은 경합 밀도가 극도로 높지만, k6(네트워크 요청)는 네트워크 및 WAS 오버헤드가 자연스러운 **Pacing(속도 조절)** 역할을 하여 경합을 미세하게 분산시킵니다. 이로 인해 k6 환경에서 Optimistic Lock 등의 성능이 상대적으로 더 잘 나올 수 있습니다.
- **명령어 연결:** k6가 임계치 미달로 에러 종료될 경우 `&&` 뒤의 명령어가 실행되지 않으므로, 항상 상태를 확인하기 위해 `;`를 사용하도록 가이드를 수정했습니다.