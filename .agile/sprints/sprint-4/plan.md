# Sprint 4: 문서화 + 프로젝트 완성

**기간:** 2026-01-28 ~ 2026-02-04 (7일)
**목표:** 프로젝트를 외부 공개 가능한 수준으로 완성하고, 이직용 포트폴리오로 활용 가능하도록 문서화 및 정리를 완료한다.

---

## Sprint Goal

> README 고도화, 블로그 포스팅 준비, 프로젝트 정합성 검증을 통해 "누구나 5분 안에 실행하고 이해할 수 있는" 완성된 포트폴리오를 만든다.

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**

**핵심 원칙:**
1. 작은 단위로 반복 (인지 부하 최소화)
2. 빠른 피드백 루프 (Checkpoint로 검증)
3. 검증 후 구현 (잘못된 방향 사전 차단)

**Sprint 3 회고 반영:**
- AI는 "다음 iteration 진행할까요?" 재촉 금지 → 사용자가 명시적으로 요청할 때만
- 사용자 허락 없이 자동 실행 절대 금지
- 문서 수정 시 "추가" vs "덮어쓰기" 명확히 구분
- how-diagram, plan.md 간소화 (인지 부하 감소)
- 각 Iteration 완료 시 `iteration-N-summary.md` 파일 생성 필수

---

## Tasks

### Iteration 1: 프로젝트 정합성 검증 (Alignment Check)

#### US-4.1: 기획 vs 실제 수행 괴리 검증

**목표:** 초기 기획(`brainstorm.md`, `how-diagram.md`)과 실제 수행(`Sprint 0-3`) 간의 일치 여부 전수 검사

- [x] brainstorm.md의 Sprint 계획 vs 실제 Sprint 수행 비교
  - Sprint 0: 플랫폼 엔지니어링 + 아키텍처 설계 (계획 vs 실제)
  - Sprint 1: DB Lock 구현 (계획 vs 실제)
  - Sprint 2: Redis 구현 (계획 vs 실제)
  - Sprint 3: 부하 테스트 (계획 vs 실제)
- [x] 핵심 KPI 달성 여부 확인
  - "대규모 트래픽 처리 경험" 증명 가능한가?
  - 정량 지표(TPS, Latency) 측정 완료했는가?
  - 4가지 방법 비교 완료했는가?
  - 재현 가능한 환경 구축했는가?
- [x] 누락된 항목 파악
  - brainstorm.md에 있지만 미완성된 항목
  - 추가로 수행했지만 기획에 없던 항목
- [x] 정합성 검증 리포트 작성 (`docs/alignment-check.md`)
  - 달성한 목표 (✅)
  - 미달성 목표 (⚠️)
  - 초과 달성 목표 (🎉)
  - Sprint 4에서 보완할 항목

**Acceptance Criteria:**
- brainstorm.md와 실제 수행 내용 비교 완료
- 핵심 KPI 달성 여부 명확히 정리
- 정합성 검증 리포트 작성 완료

**🔍 Checkpoint 1:** 정합성 검증 완료 및 리뷰

**✅ Iteration 1 완료:** 프로젝트 목표 달성 여부 명확화

---

### Iteration 2: Hell Test (선착순 이벤트 시나리오) 수행

#### US-4.2: 극한의 경쟁 상태 (Extreme Contention) 검증

**목표:** "재고 100개 vs 유저 10,000명" 시나리오를 통해 선착순 이벤트 상황에서의 동작 검증

**배경:**
- 기존 Extreme Load는 재고가 충분(10,000개)하여 경쟁이 상대적으로 낮았음
- 실제 선착순 이벤트는 재고가 매우 적고 유저가 폭주하는 상황 (Hell Test)

**Tasks:**
- [ ] k6 스크립트 설정 확인 (`stress-test.js`)
  - VUs: 10,000
  - Duration: 짧게 (순식간에 매진)
- [ ] 4가지 방법 테스트 수행
  - Pessimistic Lock: 데드락 발생 여부 및 대기열 처리 확인
  - Optimistic Lock: 재시도 폭주로 인한 실패율 확인
  - Redis Lock: 락 획득 실패(Timeout) 처리 확인
  - Lua Script: 처리 속도 및 Redis 부하 확인
- [ ] 결과 분석 및 리포트 업데이트 (`docs/performance-test-result.md`)
  - "Hell Test" 섹션 추가
  - 각 방법별 생존 여부 및 성능 기록

**Acceptance Criteria:**
- Hell Test 시나리오(100 stock vs 10k users) 수행 완료
- 4가지 방법별 거동 분석 완료
- 성능 리포트에 결과 반영

**🔍 Checkpoint 2:** Hell Test 결과 분석 및 리뷰

**✅ Iteration 2 완료:** 극한 상황 데이터 확보

---

### Iteration 3: README.md 고도화 (The Face of Project)

#### US-4.3: README.md 전면 개편

**목표:** "별도의 문서 없이도 누구나 5분 안에 실행 가능한 온보딩 문서"로 전환

**Phase 1: 구조 설계**
- [ ] README.md 새 구조 설계
  - Quick Start 최상단 배치 (원라이너 명령어)
  - 프로젝트 소개 (What, Why, How 한 문단)
  - 주요 기능 및 4가지 방법 요약
  - 성능 테스트 결과 요약 (Hell Test 결과 포함)
  - 상세 문서 링크 (docs/)
  - 기여 가이드 (선택)

**Phase 2: 콘텐츠 작성**
- [ ] Quick Start 섹션 작성
  ```bash
  # 1. 인프라 시작 (MySQL + Redis)
  make up

  # 2. 애플리케이션 빌드 및 시작
  make build

  # 3. 재고 초기화 (100개)
  make reset

  # 4. API 테스트 (4가지 방법 중 선택)
  curl -X POST http://localhost:8080/api/stock/decrease?method=pessimistic
  ```
- [ ] 프로젝트 소개 섹션 작성
  - 한 문단으로 What, Why, How 요약
  - brainstorm.md의 핵심 내용 반영
- [ ] 4가지 방법 요약 섹션 작성
  - 각 방법의 핵심 특징 (3-4줄)
  - 다이어그램 링크
  - 상세 문서 링크
- [ ] 성능 테스트 결과 요약 추가
  - 간단한 비교 표 (TPS, Latency, Success Rate)
  - Hell Test 결과 하이라이트
  - 상세 리포트(`docs/performance-test-result.md`) 링크
- [ ] Sprint별 요약 섹션 추가 (선택)
  - Sprint 1: DB Lock 구현
  - Sprint 2: Redis 구현
  - Sprint 3: 부하 테스트
  - Sprint 4: Hell Test + 문서화

**Phase 3: 검증**
- [ ] README만 보고 실행 가능한지 테스트
- [ ] 링크 유효성 확인
- [ ] 마크다운 렌더링 확인

**Acceptance Criteria:**
- Quick Start가 최상단에 배치됨
- 5분 안에 프로젝트 실행 가능
- 성능 테스트 결과(Hell Test 포함) 요약 포함
- 모든 링크 유효

**🔍 Checkpoint 3:** README.md 개편 완료 및 리뷰

**✅ Iteration 3 완료:** README.md 고도화 완료

---

### Iteration 3: 블로그 포스팅 준비

#### US-4.3: 블로그 포스팅 초안 작성

**목표:** 기술 블로그에 발행할 포스팅 초안 작성 (Medium, Velog, Tistory 등)

**구성 (brainstorm.md 기반):**

**Phase 1: 아웃라인 작성**
- [ ] 블로그 포스팅 구조 설계
  1. 문제 정의 (Race Condition 재현)
  2. 4가지 해결 방법 설명 (코드 + 다이어그램)
  3. 성능 테스트 결과 (그래프)
  4. 실무 적용 가이드
  5. GitHub 링크

**Phase 2: 콘텐츠 작성**
- [ ] 1. 문제 정의 섹션 작성
  - 100개 재고에 1000개 요청 시나리오
  - Race Condition 설명
  - 간단한 코드 예제로 문제 재현
- [ ] 2. 4가지 해결 방법 섹션 작성
  - Method 1: Pessimistic Lock (DB 비관적 락)
  - Method 2: Optimistic Lock (DB 낙관적 락)
  - Method 3: Redis Distributed Lock (분산 락)
  - Method 4: Redis Lua Script (원자적 연산)
  - 각 방법마다: 동작 원리, 핵심 코드, Sequence Diagram
- [ ] 3. 성능 테스트 결과 섹션 작성
  - k6 부하 테스트 설명
  - TPS/Latency 비교 표
  - 그래프 (이미지 포함)
  - 결과 분석
- [ ] 4. 실무 적용 가이드 섹션 작성
  - 어떤 상황에 어떤 방법을 쓸 것인가?
  - 각 방법의 Trade-off
  - 실무 도입 시 주의사항
- [ ] 5. 마무리 섹션 작성
  - 프로젝트 GitHub 링크
  - 재현 방법 안내
  - 참고 자료

**Phase 3: 이미지 준비**
- [ ] Sequence Diagram 이미지 추출 (4개)
- [ ] 성능 테스트 그래프 이미지 추출
- [ ] 코드 스크린샷 (선택)

**Acceptance Criteria:**
- 블로그 포스팅 초안 완성
- 4가지 방법 설명 명확
- 성능 테스트 결과 포함
- 이미지 준비 완료

**🔍 Checkpoint 3:** 블로그 포스팅 초안 리뷰

**Note:** 실제 발행은 Sprint 4 이후 사용자가 직접 진행 (Velog, Medium 등 플랫폼 선택)

**✅ Iteration 3 완료:** 블로그 포스팅 초안 작성 완료

---

### Iteration 4: 프로젝트 구조 정리 (TODO.md 반영)

#### US-4.4: 파일 및 디렉터리 정리

**목표:** TODO.md에 명시된 프로젝트 구조 개선 사항 적용

**Phase 1: 문서 파일 정리**
- [ ] 루트의 기획 문서 정리 검토
  - `how-diagram.md` - 현재 위치 검토
  - `brainstorm.md` - 현재 위치 검토
  - `SCAFFOLDING_REPORT.md` - 현재 위치 검토
- [ ] 정리 방안 논의 (사용자와 상의)
  - Option 1: `docs/planning/` 디렉터리로 이동
  - Option 2: 현재 위치 유지 (루트)
  - Option 3: 아카이브 처리

**Phase 2: Docker 관련 파일 정리 (선택)**
- [ ] Docker 파일 구조 검토
  - 현재: `docker-compose.yml` (루트), `mysql-init/` (루트)
  - 제안: `docker/` 디렉터리로 통합
- [ ] 정리 방안 결정 (사용자와 상의)
  - Option 1: `docker/` 디렉터리로 이동
  - Option 2: 현재 구조 유지
- [ ] 관련 파일 업데이트 (이동 시)
  - `Makefile` 수정 (경로 변경)
  - `CLAUDE.md` 수정 (문서 업데이트)
  - `README.md` 수정 (경로 반영)

**Phase 3: 불필요한 파일 제거**
- [ ] 불필요한 파일 확인
  - 임시 파일, 로그 파일
  - 미사용 스크립트
- [ ] .gitignore 업데이트

**Acceptance Criteria:**
- TODO.md의 정리 항목 처리 완료
- 프로젝트 구조가 명확하고 일관됨
- 관련 문서 업데이트 완료

**🔍 Checkpoint 4:** 프로젝트 구조 정리 완료 및 리뷰

**✅ Iteration 4 완료:** 프로젝트 구조 정리 완료

---

### Iteration 5: 최종 점검 및 GitHub 공개 준비

#### US-4.5: 최종 검증 및 GitHub 정리

**Phase 1: 코드 품질 점검**
- [ ] 모든 테스트 실행 및 통과 확인
  ```bash
  ./gradlew test
  ```
- [ ] ArchUnit 테스트 통과 확인
- [ ] 불필요한 주석 제거
- [ ] 코드 포맷팅 정리

**Phase 2: 문서 최종 검증**
- [ ] 모든 문서 링크 유효성 확인
- [ ] 마크다운 렌더링 확인
- [ ] 오타 및 문법 확인

**Phase 3: 재현 가능성 검증**
- [ ] 클린 환경에서 Quick Start 실행 테스트
  ```bash
  make clean
  make up
  make build
  make reset
  ```
- [ ] API 호출 테스트 (4가지 방법)
- [ ] k6 부하 테스트 실행 테스트

**Phase 4: GitHub 정리**
- [ ] .gitignore 최종 확인
- [ ] 불필요한 파일 삭제
- [ ] 커밋 히스토리 정리 (필요 시)
- [ ] Git 태그 생성 (v1.0.0)
  ```bash
  git tag -a v1.0.0 -m "Release v1.0.0: Concurrency Control PoC Complete"
  git push origin v1.0.0
  ```

**Phase 5: GitHub Repository 설정**
- [ ] Repository Description 작성
- [ ] Topics 추가 (spring-boot, concurrency, redis, mysql, k6, performance-testing)
- [ ] README.md를 GitHub에서 확인
- [ ] License 파일 추가 (선택: MIT)

**Acceptance Criteria:**
- 모든 테스트 통과
- 클린 환경에서 재현 가능
- GitHub Repository 정리 완료
- v1.0.0 태그 생성 완료

**🔍 Checkpoint 5:** 최종 검증 완료

**✅ Iteration 5 완료:** GitHub 공개 준비 완료

---

## Sprint 4 Definition of Done

### Iteration 1: 프로젝트 정합성 검증 ✅
- [ ] brainstorm.md vs 실제 수행 비교 완료
- [ ] 핵심 KPI 달성 여부 확인 완료
- [ ] 정합성 검증 리포트 작성 (`docs/alignment-check.md`)
- [ ] Checkpoint 1 통과
- [ ] `iteration-1-summary.md` 생성

### Iteration 2: README.md 고도화 ✅
- [ ] README.md 새 구조 설계 완료
- [ ] Quick Start 섹션 작성 (최상단 배치)
- [ ] 프로젝트 소개, 4가지 방법 요약 작성
- [ ] 성능 테스트 결과 요약 추가
- [ ] 5분 안에 실행 가능 확인
- [ ] Checkpoint 2 통과
- [ ] `iteration-2-summary.md` 생성

### Iteration 3: 블로그 포스팅 준비 ✅
- [ ] 블로그 포스팅 구조 설계
- [ ] 5개 섹션 콘텐츠 작성 완료
- [ ] 이미지 준비 완료 (다이어그램, 그래프)
- [ ] 블로그 포스팅 초안 완성
- [ ] Checkpoint 3 통과
- [ ] `iteration-3-summary.md` 생성

### Iteration 4: 프로젝트 구조 정리 ✅
- [ ] 문서 파일 정리 완료
- [ ] Docker 파일 구조 정리 (선택)
- [ ] 관련 문서 업데이트 (Makefile, CLAUDE.md, README.md)
- [ ] Checkpoint 4 통과
- [ ] `iteration-4-summary.md` 생성

### Iteration 5: 최종 점검 및 GitHub 공개 준비 ✅
- [ ] 모든 테스트 통과 확인
- [ ] 재현 가능성 검증 완료
- [ ] GitHub Repository 정리 완료
- [ ] v1.0.0 태그 생성
- [ ] Checkpoint 5 통과
- [ ] `iteration-5-summary.md` 생성

### 최종 검증
- [ ] README만 보고 5분 안에 실행 가능
- [ ] 블로그 포스팅 초안 완성
- [ ] 프로젝트 구조 정리 완료
- [ ] GitHub 공개 가능 상태

---

## Blockers

- 없음

---

## Notes

### Sprint 4의 특징

**문서화 중심 Sprint:**
- 코드 구현보다 문서 작성에 집중
- 프로젝트 완성도 향상
- 외부 공개 준비

**사용자 주도 Sprint:**
- 정합성 검증: 사용자가 직접 확인하고 판단
- 구조 정리: 사용자가 최종 결정
- 블로그 포스팅: AI는 초안만 제공, 사용자가 최종 편집

### TODO.md 항목 처리

Sprint 4에서 처리할 항목:
- ✅ 프로젝트 정합성 검증 (Iteration 1)
- ✅ README.md 고도화 (Iteration 2)
- ⚠️ 문서 파일 정리 (Iteration 4에서 사용자와 상의)
- ⚠️ Docker 파일 정리 (Iteration 4에서 선택 사항)

### 블로그 포스팅 발행

**Sprint 4 범위:**
- ✅ 초안 작성 (AI가 지원)
- ✅ 이미지 준비

**Sprint 4 이후 (사용자가 직접):**
- 최종 편집 및 교정
- 플랫폼 선택 (Velog, Medium, Tistory 등)
- 발행 및 공유

### 확장 가능성 (Sprint 5 이후)

**brainstorm.md의 Sprint 5 (선택):**
- Option 1: 모니터링 추가 (Prometheus + Grafana)
- Option 2: 조회 최적화 PoC 추가
- Option 3: 비동기 처리 추가 (Kafka)

**Sprint 4 완료 후 결정:**
- 프로젝트 완성도에 따라 Sprint 5 진행 여부 결정
- 1-2달 제약 조건 고려

---

## Sprint 4 목표 요약

✅ **프로젝트 정합성 검증** (기획 vs 실제 수행 괴리 확인)
✅ **README.md 고도화** (5분 안에 실행 가능한 온보딩 문서)
✅ **블로그 포스팅 준비** (기술 블로그 발행용 초안)
✅ **프로젝트 구조 정리** (TODO.md 반영)
✅ **GitHub 공개 준비** (v1.0.0 태그, Repository 설정)

**Sprint 4 완료 시:**
- 이직용 포트폴리오로 활용 가능
- 기술 블로그 발행 준비 완료
- GitHub에 공개 가능한 완성된 프로젝트
