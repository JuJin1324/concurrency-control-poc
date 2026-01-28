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
- [x] 핵심 KPI 달성 여부 확인
- [x] 누락된 항목 파악
- [x] 정합성 검증 리포트 작성 (`docs/alignment-check.md`)

**Acceptance Criteria:**
- brainstorm.md와 실제 수행 내용 비교 완료
- 핵심 KPI 달성 여부 명확히 정리
- 정합성 검증 리포트 작성 완료

**🔍 Checkpoint 1:** 정합성 검증 완료 및 리뷰

**✅ Iteration 1 완료:** 프로젝트 목표 달성 여부 명확화

---

### Iteration 2: Hell Test (선착순 이벤트 시나리오) 수행

#### US-4.2: 극한의 경쟁 상태 (Extreme Contention) 검증

**목표:** "재고 100개 vs 유저 5,000명" 시나리오를 통해 선착순 이벤트 상황에서의 동작 검증

**Tasks:**
- [x] k6 스크립트 설정 확인 (`hell-test.js`)
- [x] 4가지 방법 테스트 수행 (Lua, Optimistic, Pessimistic, Redis Lock)
- [x] 결과 분석 및 리포트 업데이트 (`docs/test-guides/hell-test.md`)

**Acceptance Criteria:**
- Hell Test 시나리오(100 stock vs 5k users) 수행 완료
- 4가지 방법별 거동 분석 완료
- 테스트 가이드에 결과 반영

**🔍 Checkpoint 2:** Hell Test 결과 분석 및 리뷰

**✅ Iteration 2 완료:** 극한 상황 데이터 확보

---

### Iteration 3: README.md 고도화 (The Face of Project)

#### US-4.3: README.md 전면 개편

**목표:** "별도의 문서 없이도 누구나 5분 안에 실행 가능한 온보딩 문서"로 전환

- [x] README.md 새 구조 설계
- [x] Quick Start 섹션 작성 (최상단 배치)
- [x] 프로젝트 소개 및 4가지 제어 방법 요약 작성
- [x] 종합 성능 테스트 결과 요약 표 추가
- [x] 불필요한 상세 리포트 링크 정리

**Acceptance Criteria:**
- Quick Start가 최상단에 배치됨
- 5분 안에 프로젝트 실행 가능
- 모든 시나리오별 성능 결과 요약 포함

**🔍 Checkpoint 3:** README.md 개편 완료 및 리뷰

**✅ Iteration 3 완료:** README.md 고도화 완료

---

### Iteration 4: 블로그 포스팅 준비

#### US-4.4: 블로그 포스팅 초안 작성

**목표:** 기술 블로그에 발행할 포스팅 초안 작성 (Medium, Velog, Tistory 등)

- [ ] 블로그 포스팅 구조 설계
- [ ] 문제 정의, 4가지 해결 방법, 성능 결과, 가이드 등 콘텐츠 작성
- [ ] 다이어그램 및 그래프 이미지 준비

**Acceptance Criteria:**
- 블로그 포스팅 초안 완성
- 4가지 방법 설명 명확 및 이미지 준비 완료

**🔍 Checkpoint 4:** 블로그 포스팅 초안 리뷰

**✅ Iteration 4 완료:** 블로그 포스팅 초안 작성 완료

---

### Iteration 5: 프로젝트 구조 정리 (TODO.md 반영)

#### US-4.5: 파일 및 디렉터리 정리

**목표:** TODO.md에 명시된 프로젝트 구조 개선 사항 적용

- [ ] 불필요한 문서 파일 정리 (brainstorm.md, SCAFFOLDING_REPORT.md 등 위치 검토)
- [ ] Docker 관련 파일 정리 및 경로 업데이트 검토
- [ ] .gitignore 최신화 및 미사용 파일 제거

**Acceptance Criteria:**
- TODO.md의 정리 항목 처리 완료
- 프로젝트 구조의 일관성 확보

**🔍 Checkpoint 5:** 프로젝트 구조 정리 완료 및 리뷰

**✅ Iteration 5 완료:** 프로젝트 구조 정리 완료

---

### Iteration 6: 최종 점검 및 GitHub 공개 준비

#### US-4.6: 최종 검증 및 GitHub 정리

- [ ] 모든 테스트(Unit, ArchUnit) 통과 확인
- [ ] 클린 환경에서의 재현 가능성 최종 테스트
- [ ] Git 태그 생성 (v1.0.0) 및 Repository 설정

**Acceptance Criteria:**
- 모든 테스트 통과 및 재현 가능 확인
- GitHub 공개 가능 상태 확보

**🔍 Checkpoint 6:** 최종 검증 완료

**✅ Iteration 6 완료:** GitHub 공개 준비 완료

---

## Sprint 4 Definition of Done

### Iteration 1: 프로젝트 정합성 검증 ✅
- [x] 정합성 검증 리포트 작성 (`docs/alignment-check.md`)
- [x] `iteration-1-summary.md` 생성 (기존 파일 확인 필요)

### Iteration 2: Hell Test 수행 ✅
- [x] 4가지 방식 테스트 및 가이드 업데이트
- [x] `iteration-2-summary.md` 생성

### Iteration 3: README.md 고도화 ✅
- [x] README 전면 개편 및 성능 요약 추가
- [x] `iteration-3-summary.md` 생성

### Iteration 4: 블로그 포스팅 준비 📅
- [ ] 블로그 포스팅 초안 및 이미지 준비
- [ ] `iteration-4-summary.md` 생성

### Iteration 5: 프로젝트 구조 정리 📅
- [ ] TODO.md 기반 파일 정리
- [ ] `iteration-5-summary.md` 생성

### Iteration 6: 최종 점검 및 GitHub 공개 준비 📅
- [ ] 모든 테스트 통과 및 v1.0.0 태그
- [ ] `iteration-6-summary.md` 생성

---

## Blockers
- 없음

---

## Notes
- Iteration 번호 중복 수정 완료 (3이 두 개였던 것을 3, 4로 분리하고 이후 번호 조정)
- 현재까지 진행된 Hell Test와 README 고도화를 완료 상태로 업데이트함