# Sprint 5: 한계 돌파 및 테스트 엔지니어링 (Optimization & Engineering)

**기간:** 2026-01-30 ~ (TBD)
**목표:** 10,000 VUs 테스트 실패 원인을 해결하고, 목적 중심(Capacity/Contention/Stress)의 새로운 테스트 표준을 수립하여 시스템 성능을 재측정한다.

---

## Sprint Goal
> **"시스템 물리적 한계를 극복하고, 재현 가능하고 신뢰성 높은 성능 테스트 환경을 구축한다."**

---

## Tasks

### Iteration 1: 기초 체력 강화 (Infrastructure Tuning) ✅ 완료

- [x] **[OS]** Host (macOS) TCP Port 범위 확장 (`sysctl`)
- [x] **[Docker]** Container `ulimit` (File Descriptors) 설정 추가
- [x] **[MySQL]** `max_connections` 설정 증설 (151 -> 1000)
- [x] **[App]** HikariCP Connection Pool 최적화 (10 -> 50)
- [x] **[App]** Redis (Lettuce) Connection Pool 최적화

---

### Iteration 2: 코어 엔진 교체 (Virtual Threads) ✅ 완료

- [x] **[App]** `application.yml`에 Virtual Threads 활성화 설정 추가
- [x] **[App]** Tomcat Thread Pool 설정 제거 (Virtual Thread 위임)

---

### Iteration 3: 최종 검증 (Validation) [🛑 HOLD]

**원래 목표:** 튜닝된 환경에서 10,000 VUs 테스트를 재수행하고 드라마틱한 성능 향상을 증명한다.
*보류 사유: 테스트 스크립트의 동작 방식 혼선으로 인해 데이터 신뢰성 확보 불가. Iteration 5 선행 필요.*

- [ ] **[Test]** Extreme Test (10,000 VUs) 재수행 (기존 실패 시나리오)
- [ ] **[Analyze]** 에러율 0% 달성 여부 및 TPS/Latency 변화 분석
- [ ] **[Docs]** Performance Report 업데이트 (Before/After 차트 포함)

---

### Iteration 4: 튜닝 효과 검증 리포트 (Comparison & Analysis) [🛑 HOLD]

**원래 목표:** 튜닝 전후의 설정값과 성능 변화를 체계적으로 정리하여 "왜 빨라졌는지"를 증명한다.
*보류 사유: 신뢰할 수 있는 비교 데이터(Baseline) 부재 및 측정 표준 재정립 필요.*

- [ ] **[Docs]** Tuning Parameter Comparison 문서 작성 (`docs/reports/tuning-parameters.md`) ✅ (일부 완료)
- [ ] **[Docs]** Scenario Comparison Report 문서 작성 (`docs/reports/scenario-comparison.md`)
- [ ] **[Docs]** 10,000 VUs Method Analysis 개별 리포트 작성

---

### Iteration 5: 테스트 시나리오 전면 재설계 (Test Engineering) 🚀 [Current Focus]

**목표:** 기존 `High/Extreme/Hell` 구분을 폐기하고, k6 Executor 기반의 목적별 테스트 체계로 리팩토링한다.

**1단계: 지식 자산화 및 표준 수립 ✅ 완료**
- [x] **[Docs]** k6 핵심 개념 정리 (`docs/technology/k6-deep-dive.md`)
- [x] **[Docs]** 신규 테스트 전략 수립 (`docs/test-guides/strategy-v2.md`)

**2단계: 스크립트 및 인프라 리팩토링 (Implement & Clean-up)**
- [ ] **[Clean-up]** 레거시 스크립트 정리
    - 기존 `high-test.js`, `extreme-test.js`, `hell-test.js` 등 삭제 (또는 `k6-scripts/legacy/` 이동)
- [ ] **[Standardize]** 신규 목적별 스크립트 구현
    - `capacity.js`: 처리량(TPS) 측정용 (Shared Iterations)
    - `contention.js`: 경합/안정성 측정용 (Constant VUs)
    - `stress.js`: 시스템 한계 탐색용 (Ramping Arrival Rate)
    - `warmup.js`: 예열 전용 경량 스크립트
- [ ] **[Infra]** `Makefile` 명령어 전면 개편
    - `make test-capacity`, `make test-contention`, `make test-stress` 도입
    - 기존 `make test-high` 등 레거시 명령어 삭제

**3단계: 신규 표준 기반 성능 재측정 (Re-Baseline)**
- [ ] **[Docs]** 상세 테스트 플랜 수립 (`docs/test-guides/test-plan-v2.md`)
    - 테스트 시나리오별 실행 순서, 파라미터, 예상 결과 정의
- [ ] **[Test]** Capacity Test (Stock: 10k, VUs: 100) - 4 Methods
    - TPS Baseline 확보 (순수 처리량 측정)
- [ ] **[Test]** Contention Test (Stock: 100, VUs: 5k) - 4 Methods
    - Stability 검증 (에러율 및 Latency 측정)
- [ ] **[Docs]** 신규 표준 기반 통합 성능 리포트 작성 (`docs/reports/performance-v2.md`)

**✅ Iteration 5 완료 조건:**
- `k6-deep-dive.md` 및 `test-plan-v2.md` 문서 생성
- 목적별로 명확히 분리된 k6 스크립트 확보
- 재측정된 성능 데이터 리포트 완료

---

## Blockers
- 없음

## Notes
- **Strategy Shift:** 단순히 "얼마나 많은 부하를 주느냐"에서 "이 테스트를 통해 무엇을 검증하느냐"로 관점을 완전히 전환함.
- **SSOT (Single Source of Truth):** `strategy-v2.md`가 이제 프로젝트의 유일한 테스트 표준임.