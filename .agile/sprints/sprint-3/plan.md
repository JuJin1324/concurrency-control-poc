# Sprint 3: k6 부하 테스트 + 최종 성능 비교 분석

**기간:** 2026-01-27 ~ 2026-02-03 (7일)
**목표:** k6 부하 테스트로 4가지 동시성 제어 방법의 정량 지표를 측정하고, 실무 적용 가이드를 작성한다.

---

## Sprint Goal

> k6 부하 테스트를 통해 4가지 방법의 TPS, Latency, Success Rate를 측정하고, 데이터 기반 성능 비교 리포트를 완성한다.

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**

**핵심 원칙:**
1. 작은 단위로 반복 (인지 부하 최소화)
2. 빠른 피드백 루프 (Checkpoint로 검증)
3. 검증 후 구현 (잘못된 방향 사전 차단)

**Sprint 2 회고 반영:**
- 각 Iteration 완료 시 `iteration-N-summary.md` 파일 생성 필수
- 사용자 피드백 섹션 포함 (코드/아키텍처 수정 요청 가능)
- Summary 파일 수정 시 템플릿 구조 유지

---

## Tasks

### Sprint 시작 전: 테스트 코드 품질 개선 (Test Fixtures 도입)

#### US-3.0: Test Fixtures 설계 및 구현
- [x] 테스트 코드 중복 분석
  - Service 테스트 중복 파악 (동시성 테스트 로직 반복)
  - Controller 테스트 중복 파악 (Stock 생성, Request Body 생성)
  - 공통 패턴 정리 문서화
- [x] Test Fixtures 설계
  - **StockTestFixtures:** Stock 엔티티 생성 헬퍼
    - `createStock(productId, quantity)` - Stock 엔티티 생성
    - `createDefaultStock()` - 기본 Stock ("PRODUCT-001", 100)
    - `saveStock(repository, productId, quantity)` - 저장 헬퍼
  - **ConcurrencyTestSupport:** 동시성 테스트 공통 로직
    - `executeConcurrentRequests(task, threadCount, poolSize)` - 동시성 실행
    - `ConcurrencyTestResult` 클래스 - successCount, failCount 포함
    - ExecutorService, CountDownLatch, AtomicInteger 관리
  - **(선택) StockControllerTestSupport:** Controller 테스트 헬퍼
    - `createDecreaseRequest(stockId, amount)` - Request Map 생성
    - `toJson(object)` - JSON 변환 헬퍼
- [x] Test Fixtures 구현
  - `src/test/java/com/concurrency/poc/fixtures/StockTestFixtures.java`
  - `src/test/java/com/concurrency/poc/fixtures/ConcurrencyTestSupport.java`
  - (선택) `src/test/java/com/concurrency/poc/fixtures/StockControllerTestSupport.java`
  - 각 Fixtures에 대한 간단한 검증 코드 작성
- [x] 기존 테스트 리팩토링
  - `PessimisticLockStockServiceTest` 리팩토링
  - `OptimisticLockStockServiceTest` 리팩토링
  - `RedisLockStockServiceTest` 리팩토링
  - `LuaScriptStockServiceTest` 리팩토링
  - `StockControllerTest` 리팩토링 (선택)
- [x] 테스트 실행 및 검증
  - 모든 테스트 통과 확인 (`./gradlew test`)
  - 코드 커버리지 유지 확인
  - 테스트 가독성 개선 확인

**Acceptance Criteria:**
- Test Fixtures 구현 완료
- 기존 테스트 코드 중복 제거 (최소 30% 코드 라인 감소)
- 모든 테스트 통과 (ArchUnit 포함)
- 테스트 코드 가독성 향상

**🔍 Checkpoint 0:** 테스트 코드 품질 개선 완료 및 리뷰

**✅ Sprint 시작 전 완료:** 테스트 인프라 준비 완료, `sprint-start-summary.md` 생성

**Note:**
- 이 작업은 Sprint 3의 부하 테스트 시작 전에 완료
- k6 부하 테스트와는 독립적이지만, 테스트 코드 품질 향상을 위해 필수
- Controller 테스트 리팩토링은 선택 사항 (시간이 부족하면 생략 가능)

---

### Iteration 1: k6 개념 학습 및 환경 설정

#### US-3.1: k6 개념 및 역사 학습 (Sprint 2 Action Item 반영)
- [x] k6 기술 문서 작성 (`docs/technology/k6-overview.md`)
  - k6가 무엇이고 왜 만들어졌는지
  - k6의 핵심 철학과 다른 부하 테스트 도구와의 차이점 (JMeter, Gatling, Locust)
  - VU (Virtual User), RPS (Request Per Second) 개념
  - Threshold, Metric, Check 개념
- [x] 성능 측정 방법론 학습 및 문서화
  - 성능 측정의 핵심 지표 (TPS, Latency, Percentile)
  - 부하 테스트의 종류 (Load Test, Stress Test, Spike Test, Soak Test)
  - 공정한 비교를 위한 테스트 환경 설정
  - 결과 분석 및 해석 방법론

**Acceptance Criteria:**
- `k6-overview.md` 문서 완성 (Redis 문서 수준의 품질)
- k6 기본 개념 이해
- 성능 측정 방법론 명확히 정리

**🔍 Checkpoint 1:** k6 개념 및 성능 측정 방법론 이해 완료

---

#### US-3.2: k6 설치 및 기본 사용법 실습
- [x] k6 설치
  - macOS: `brew install k6`
  - 또는 Docker 이미지 사용
- [x] 간단한 예제로 실습
  - Hello World 시나리오 작성 (JavaScript)
  - VU 설정 및 실행
  - 결과 확인 (stdout 출력)
- [x] k6 스크립트 템플릿 작성
  - 재사용 가능한 기본 템플릿
  - 공통 설정 (threshold, stages)

**Acceptance Criteria:**
- k6 실행 가능 (버전 확인)
- 간단한 시나리오 작성 및 실행 성공
- 기본 템플릿 작성 완료

**✅ Iteration 1 완료:** k6 개념 이해 및 실습 환경 준비 완료

---

### Iteration 2: 부하 테스트 환경 구축 및 k6 시나리오 작성

#### US-3.3: 부하 테스트 환경 구축 (Docker 컨테이너화 + 리소스 제한)

**Phase 1: 애플리케이션 컨테이너화**
- [x] Dockerfile 작성
  - Base Image: `eclipse-temurin:21-jre-alpine`
  - JAR 파일 복사 및 실행 설정
  - EXPOSE 8080
- [x] .dockerignore 작성
  - build/, .gradle/, .git/ 제외
- [x] Gradle 빌드 스크립트 확인
  - `./gradlew bootJar` 명령 확인

**Phase 2: docker-compose.yml 확장**
- [x] app 서비스 추가
  - Spring Boot 애플리케이션 컨테이너 정의
  - 환경 변수 설정 (SPRING_DATASOURCE_URL, SPRING_DATA_REDIS_HOST)
  - 포트 매핑: 8080:8080
  - depends_on 설정 (mysql, redis 헬스체크 의존)
- [x] 리소스 제한 설정 (deploy.resources)
  - **app:** CPU 2코어, Memory 2GB (limits), CPU 1코어, Memory 1GB (reservations)
  - **mysql:** CPU 2코어, Memory 1GB (limits), CPU 1코어, Memory 512MB (reservations)
  - **redis:** CPU 1코어, Memory 512MB (limits), CPU 0.5코어, Memory 256MB (reservations)
- [x] 헬스체크 설정 (app 서비스)
  - HTTP GET /actuator/health (Spring Boot Actuator 사용)
  - interval: 10s, timeout: 5s, retries: 3

**Phase 3: Makefile 업데이트**
- [x] `make build` 명령 추가
  - `./gradlew clean bootJar` 실행
  - `docker-compose build` 실행
- [x] `make up` 명령 수정
  - app 포함하여 전체 스택 실행
- [x] `make logs` 명령 추가
  - `docker-compose logs -f app` 실행

**Acceptance Criteria:**
- Dockerfile 작성 완료
- docker-compose.yml에 app 서비스 추가 완료
- 리소스 제한 설정 완료 (deploy.resources)
- `make build && make up` 실행 시 전체 스택 정상 실행
- 헬스체크 통과 확인 (`make ps`)

**🔍 Checkpoint 2-1:** 애플리케이션 컨테이너화 완료

---

#### US-3.4: 초기 데이터 준비 및 환경 검증
- [x] 컨테이너 환경 검증
  - `docker stats` 명령으로 리소스 사용량 확인
  - CPU/Memory 제한이 실제로 적용되는지 확인
  - 각 컨테이너의 헬스 상태 확인
- [x] 초기 데이터 준비 스크립트 개선
  - 컨테이너 환경에서 재고 100개 생성
  - Redis 캐시 초기화 (Lua Script용)
  - `make reset` 명령 컨테이너 환경에 맞게 수정
- [x] API 동작 확인
  - 4가지 방법 모두 정상 동작 확인
  - curl 또는 Postman으로 간단한 테스트
  - 각 방법별 1회 수동 테스트 실행

**Acceptance Criteria:**
- `docker stats`로 리소스 제한 확인 완료
- `make reset` 실행 시 데이터 초기화 완료 (컨테이너 환경)
- 4가지 방법 모두 컨테이너 환경에서 정상 동작
- 일관된 테스트 환경 구축 (재현 가능성 100%)

**🔍 Checkpoint 2-2:** 부하 테스트 환경 검증 완료

---

#### US-3.5: k6 시나리오 작성 (4개)
- [x] `k6-scripts/pessimistic-lock-test.js` 작성
- [x] `k6-scripts/optimistic-lock-test.js` 작성
- [x] `k6-scripts/redis-lock-test.js` 작성
- [x] `k6-scripts/lua-script-test.js` 작성

**각 스크립트 공통 구성:**
- VU (Virtual User) 설정
- 시나리오: 재고 100개, 동시 요청 100/1000개
- Ramp-up 설정 (점진적 부하 증가)
- Threshold 설정 (성공 기준)
  - `http_req_failed` < 5%
  - `http_req_duration` p95 < 500ms
- Check 함수: 응답 상태 코드 검증

**Acceptance Criteria:**
- 4개 스크립트 모두 작성 완료
- 각 스크립트가 해당 API를 정확히 호출
- 결과 출력 형식이 일관됨

**🔍 Checkpoint 2-3:** k6 시나리오 작성 완료

---

#### US-3.6: 단일 방법 테스트 실행 (Pessimistic Lock)
- [x] Pessimistic Lock k6 스크립트 실행
- [x] 결과 수집 및 분석
  - TPS (Transactions Per Second)
  - Latency (p50, p95, p99)
  - Success Rate
  - HTTP 요청 실패율
- [x] 결과 저장 (JSON 또는 CSV)

**Acceptance Criteria:**
- Pessimistic Lock 부하 테스트 성공
- 정량 지표 측정 완료
- 결과 데이터 저장됨

**✅ Iteration 2 완료:** 부하 테스트 환경 구축 및 k6 시나리오 검증 완료

---

### Iteration 3: 전체 방법 부하 테스트 및 결과 분석

#### US-3.7: 4가지 방법 전체 부하 테스트
- [x] 각 방법별 3회 이상 반복 측정
  - Pessimistic Lock
  - Optimistic Lock
  - Redis Lock
  - Lua Script
- [x] 테스트 시나리오 다양화
  - 시나리오 1: 재고 100개, 동시 요청 100개
  - 시나리오 2: 재고 100개, 동시 요청 1000개
  - (선택) 시나리오 3: 장시간 안정성 테스트 (5분간 지속 트래픽)
- [x] 시스템 리소스 모니터링
  - CPU/Memory 사용률 기록
  - Docker stats 모니터링

**Acceptance Criteria:**
- 4가지 방법 모두 동일 조건에서 테스트 완료
- 각 방법별 3회 측정 완료 (평균값 계산)
- 시스템 리소스 데이터 수집

**🔍 Checkpoint 3-1:** 전체 부하 테스트 실행 및 데이터 수집 완료

---

#### US-3.8: 결과 분석 및 비교표 작성
- [x] 성능 비교표 작성 (Markdown)
  ```markdown
  | Method            | TPS   | p95 Latency | p99 Latency | Success Rate | Lock Contention |
  |-------------------|-------|-------------|-------------|--------------|-----------------|
  | Pessimistic Lock  | X     | Xms         | Xms         | X%           | High            |
  | Optimistic Lock   | X     | Xms         | Xms         | X%           | Low (Retry)     |
  | Redis Lock        | X     | Xms         | Xms         | X%           | Medium          |
  | Lua Script        | X     | Xms         | Xms         | X%           | None            |
  ```
- [x] 그래프 생성 (선택)
  - TPS 비교 막대 그래프
  - Latency 분포 그래프 (p50/p95/p99)
  - Success Rate 비교
- [x] 장단점 분석 문서 작성
  - 각 방법의 Trade-off 정리
  - 성능 차이의 원인 분석
  - 예상 결과 vs 실제 결과 비교

**Acceptance Criteria:**
- 비교표 완성 (정량 지표 포함)
- 장단점 분석 문서 작성 완료
- 성능 차이의 원인 명확히 설명됨

**🔍 Checkpoint 3-2:** 결과 분석 및 비교 완료

---

#### US-3.9: 성능 리포트 및 실무 적용 가이드 작성
- [x] `docs/performance-test-result.md` 작성
  - 테스트 환경 명세
  - 4가지 방법 성능 비교 결과
  - 그래프 및 비교표 포함
  - 결론 및 권장사항
- [x] 실무 적용 가이드 작성 (`docs/practical-guide.md`)
  - "어떤 상황에 어떤 방법을 쓸 것인가?"
  - 각 방법의 적합한 사용 사례
  - 주의사항 및 함정
  - 실무 도입 시 체크리스트

**Acceptance Criteria:**
- 성능 리포트 문서 완성
- 실무 적용 가이드 완성
- 데이터 기반 권장사항 제시

**✅ Iteration 3 완료:** 전체 부하 테스트 완료 및 분석 문서 작성 완료

---

## Sprint 3 Definition of Done

### Sprint 시작 전: 테스트 코드 품질 개선 ✅
- [x] Test Fixtures 설계 및 구현 완료
- [x] StockTestFixtures 구현 완료
- [x] ConcurrencyTestSupport 구현 완료
- [x] 기존 Service 테스트 리팩토링 (4개)
- [x] (선택) Controller 테스트 리팩토링
- [x] 모든 테스트 통과 (ArchUnit 포함)
- [x] Checkpoint 0 통과
- [x] `sprint-start-summary.md` 생성

### Iteration 1: k6 개념 학습 및 환경 설정 ✅
- [x] k6-overview.md 문서 작성 (Redis 문서 수준)
- [x] 성능 측정 방법론 정리
- [x] k6 설치 및 실습 완료
- [x] k6 스크립트 템플릿 작성
- [x] Checkpoint 1 통과
- [x] `iteration-1-summary.md` 생성

### Iteration 2: 부하 테스트 환경 구축 및 k6 시나리오 작성 ✅
- [x] 애플리케이션 Dockerfile 작성 완료
- [x] docker-compose.yml 확장 (app 서비스 추가)
- [x] 리소스 제한 설정 완료 (CPU/Memory)
- [x] 컨테이너 환경 검증 완료 (`docker stats`)
- [x] 초기 데이터 준비 스크립트 개선
- [x] 4개 k6 스크립트 작성
- [x] 단일 방법 테스트 실행 및 검증
- [x] Checkpoint 2-1, 2-2, 2-3 통과
- [x] `iteration-2-summary.md` 생성

### Iteration 3: 전체 방법 부하 테스트 및 결과 분석 ✅
- [x] 4가지 방법 전체 부하 테스트 완료
- [x] 정량 지표 측정 (TPS, Latency, Success Rate)
- [x] 시스템 리소스 모니터링 완료
- [x] 성능 비교표 작성
- [x] 장단점 분석 문서 작성
- [x] 성능 리포트 문서 작성 (`performance-test-result.md`)
- [x] 실무 적용 가이드 작성 (`practical-guide.md`)
- [x] Checkpoint 3-1, 3-2 통과
- [x] `iteration-3-summary.md` 생성

### 최종 검증
- [x] 4개 k6 스크립트 모두 정상 동작
- [x] 정량 지표 측정 완료 (3회 이상 반복)
- [x] 성능 비교 문서 완성
- [x] 실무 적용 가이드 완성
- [x] "어떤 상황에 어떤 방법을 쓸 것인가" 명확히 정리됨

---

## Blockers

- 없음

---

## Notes

### k6 vs JMeter vs Gatling

| 도구 | 언어 | 장점 | 단점 |
|------|------|------|------|
| k6 | JavaScript | 가볍고 빠름, CLI 친화적, 개발자 친화적 | GUI 없음 |
| JMeter | Java | GUI, 풍부한 플러그인 | 무겁고 느림 |
| Gatling | Scala | 높은 성능, 리포트 우수 | Scala 러닝 커브 |

**선택 이유:** k6
- 가볍고 빠름
- JavaScript (개발자 친화적)
- CLI 기반 (CI/CD 통합 쉬움)
- Grafana 연동 가능 (확장성)

---

### 리소스 제한 설정 근거

부하 테스트의 공정성과 재현성을 위해 모든 컨테이너에 리소스 제한을 설정합니다.

**애플리케이션 (app):**
- **CPU:** 2코어 (limits), 1코어 (reservations)
- **Memory:** 2GB (limits), 1GB (reservations)
- **근거:**
  - Spring Boot + JVM 오버헤드 고려
  - 동시성 처리에 충분한 리소스
  - 실제 프로덕션 환경 시뮬레이션

**MySQL:**
- **CPU:** 2코어 (limits), 1코어 (reservations)
- **Memory:** 1GB (limits), 512MB (reservations)
- **근거:**
  - InnoDB Buffer Pool 크기 고려
  - Lock 대기 시간 감소
  - SELECT ... FOR UPDATE 성능 최적화

**Redis:**
- **CPU:** 1코어 (limits), 0.5코어 (reservations)
- **Memory:** 512MB (limits), 256MB (reservations)
- **근거:**
  - 단일 스레드 특성 고려 (1코어면 충분)
  - 메모리 기반 데이터 저장
  - Lua Script 실행 오버헤드 고려

**왜 리소스 제한이 중요한가?**
1. **공정한 비교:** 모든 방법이 동일한 리소스 제한 하에서 테스트
2. **재현성:** 누구나 동일한 환경에서 결과 재현 가능
3. **신뢰성:** 성능 측정 결과의 신뢰도 향상
4. **이식성:** 다른 환경에서도 동일한 결과 기대 가능

---

### 성능 측정 지표 설명

**TPS (Transactions Per Second):**
- 초당 처리 가능한 요청 수
- 높을수록 좋음

**Latency (p50, p95, p99):**
- p50: 중앙값 (50%의 요청이 이보다 빠름)
- p95: 95%의 요청이 이보다 빠름
- p99: 99%의 요청이 이보다 빠름
- 낮을수록 좋음

**Success Rate:**
- 성공한 요청 비율
- 100%가 이상적 (데이터 정합성 보장)

**Lock Contention:**
- Lock 경합 정도
- 낮을수록 좋음

---

### 예상 결과 (가설)

Sprint 2의 예비 테스트 결과를 바탕으로 다음과 같은 순위를 예상:

**TPS 순위 (예상):**
1. Lua Script (가장 빠름)
2. Redis Lock
3. Optimistic Lock
4. Pessimistic Lock (가장 느림)

**Success Rate 순위 (예상):**
1. Pessimistic Lock (100%)
2. Redis Lock (100%)
3. Lua Script (100%)
4. Optimistic Lock (Retry로 인해 낮을 수 있음)

**실제 측정 후 가설 검증 필요!**

---

## Sprint 3 목표 요약

✅ **테스트 코드 품질 개선** (Test Fixtures 도입, Sprint 시작 전)
✅ **k6 개념 및 성능 측정 방법론 학습** (Sprint 2 Action Item 반영)
✅ **k6 시나리오 작성** (4개)
✅ **전체 부하 테스트 실행** (정량 지표 측정)
✅ **성능 비교 리포트 작성** (데이터 기반)
✅ **실무 적용 가이드 작성** ("어떤 상황에 어떤 방법을 쓸 것인가")
✅ **Iteration Summary 파일 생성** (Sprint 2 회고 반영)
