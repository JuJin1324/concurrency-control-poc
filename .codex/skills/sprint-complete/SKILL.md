---
name: sprint-complete
description: "Sprint를 완료하고 회고(Retrospective)를 작성합니다. Sprint Metrics를 계산하고, 잘된 점/아쉬운 점/개선 사항을 정리합니다."
---

# 스프린트 완료

## 목적

Sprint를 공식적으로 완료하고, 회고를 통해 다음 Sprint에서 개선할 수 있도록 학습합니다.

**해결하는 문제:**
- ❌ Sprint 완료 후 회고 없이 다음 Sprint 시작 → 개선 없음
- ❌ 무엇을 잘했고 못했는지 기록 없음 → 반복되는 실수
- ❌ 애자일 학습 기회 상실

**제공하는 가치:**
- ✅ Sprint Metrics 자동 계산 → 정량적 평가
- ✅ 회고 템플릿 제공 → 구조화된 회고
- ✅ Action Items 도출 → 다음 Sprint 개선

---

## 파일 구조

```
.agile/
├── current-sprint.txt          # 현재 Sprint 번호 (완료 후 초기화)
└── sprints/
    └── sprint-N/
        ├── plan.md            # Sprint 계획
        └── retrospective.md   # 회고 (완료 후 생성)
```

---

## 작동 방식

### Step 1: Sprint 완료 확인

```
Q: "Sprint N을 완료하시겠습니까? (y/n)"
> (사용자 입력)
```

**아니오인 경우:**
- Sprint 계속 진행 안내
- `/sprint-status`로 현황 확인 권장

### Step 2: Sprint Metrics 계산

**자동 계산:**
- 전체 태스크 수
- 완료한 태스크 수
- 완료율 (%)
- 예상 vs 실제 기간 (선택)

**plan.md 파싱:**
```markdown
## Tasks
- [x] 완료 태스크 1
- [x] 완료 태스크 2
- [ ] 미완료 태스크
```

**계산 예시:**
- 전체: 15개
- 완료: 15개
- 완료율: 100%

### Step 3: 회고 작성 (대화형 또는 자동)

#### 방법 1: 대화형 회고 (기본)

```
🎉 Sprint N 완료!

📊 Sprint Metrics
- 계획한 Task: N개
- 완료한 Task: M개
- 완료율: X%

---

📝 회고를 작성해주세요:

Q1: 잘된 점은 무엇인가요? (What Went Well)
> (사용자 입력)

Q2: 아쉬운 점은 무엇인가요? (What Didn't Go Well)
> (사용자 입력)

Q3: 다음 Sprint에서 개선할 점은 무엇인가요? (Action Items)
> (사용자 입력)

Q4: 한 문장으로 핵심 교훈을 요약하면?
> (사용자 입력)
```

#### 방법 2: plan.md 기반 자동 회고 (선택)

사용자가 "자동으로 작성해줘"를 선택하면:
- plan.md의 완료/미완료 태스크 분석
- Definition of Done 달성 여부 확인
- Blockers 섹션 내용 요약
- 초안 생성 후 사용자 검토 요청

### Step 4: Retrospective 파일 생성

**파일 경로:** `.agile/sprints/sprint-N/retrospective.md`

**파일 내용:** (템플릿 참조)

### Step 5: Sprint 종료 처리

**완료 후:**
- `current-sprint.txt` 초기화 (선택) 또는 다음 Sprint 번호로 업데이트
- Sprint 완료일 기록

### Step 6: 완료 메시지 출력

```
✅ Sprint N 회고가 완료되었습니다!

📂 회고 파일: .agile/sprints/sprint-N/retrospective.md

📊 Sprint Metrics
- 완료율: X%
- 기간: {시작일} ~ {종료일}

💡 Action Items (다음 Sprint에 반영하세요)
- {개선 사항 1}
- {개선 사항 2}

---

다음 Sprint를 시작하려면:
- /sprint-start
```

---

## Retrospective 템플릿

```markdown
# Sprint {N} 회고 (Retrospective)

**Sprint:** Sprint {N} - {Sprint Goal 제목}
**기간:** {시작일} ~ {종료일} ({X}일)
**회고 작성일:** {회고 날짜}
**상태:** ✅ 완료 (Completed)

---

## 1. Sprint Goal 달성 여부

> **목표:** {Sprint Goal}

### 결과: ✅ 목표 달성 / ⚠️ 부분 달성 / ❌ 미달성

{목표 달성 여부 설명}

---

## 2. Definition of Done 체크리스트

{plan.md의 DoD 체크리스트 복사}

---

## 3. 완료된 작업 (What Went Well)

### 3.1. {카테고리 1}
{잘된 점 설명}

### 3.2. {카테고리 2}
{잘된 점 설명}

---

## 4. 미흡했던 점 (What Didn't Go Well)

### 4.1. {문제점 1}
- **문제:** {설명}
- **원인:** {근본 원인}
- **영향:** {프로젝트에 미친 영향}

### 4.2. {문제점 2}
{설명}

---

## 5. 배운 점 (Lessons Learned)

### 5.1. {교훈 1}
{설명}

### 5.2. {교훈 2}
{설명}

---

## 6. 개선 사항 (Action Items for Next Sprint)

### 6.1. {개선 영역 1}
- [ ] {구체적 액션 아이템 1}
- [ ] {구체적 액션 아이템 2}

### 6.2. {개선 영역 2}
- [ ] {액션 아이템}

---

## 7. 메트릭 (Metrics)

### {카테고리 1}
- {메트릭 항목}: {값}

### {카테고리 2}
- {메트릭 항목}: {값}

### 시간
- 계획: {X}일
- 실제: {Y}일
- 달성률: {Z}%

---

## 8. 종합 평가

### Sprint {N} 성공 요인
1. {요인 1}
2. {요인 2}

### Sprint {N} 완성도
- **계획 대비 달성률:** {X}%
- **{평가 기준 1}:** 상/중/하
- **{평가 기준 2}:** 상/중/하

### Next Sprint로 넘어갈 준비 완료 여부
✅ **준비 완료** / ⚠️ **일부 미완료** / ❌ **미준비**

{설명}

---

## 9. 팀 피드백 (Sprint Review 내용)

> {개인 프로젝트의 경우 셀프 리뷰}

### 잘한 점
- {피드백 1}

### 아쉬운 점
- {피드백 1}

---

## 10. 다음 Sprint 준비사항

### Sprint {N+1} 목표 (예상)
- {다음 Sprint 목표}

### Sprint {N+1} 시작 전 확인 사항
- [ ] {확인 항목 1}
- [ ] {확인 항목 2}

---

## 결론

{Sprint 회고 종합 요약}

**Sprint {N+1} 시작 준비 완료 ✅**
```

---

## Sprint Metrics 계산 로직

### 1. 태스크 완료율

```
완료율 = (완료한 태스크 수 / 전체 태스크 수) * 100
```

**파싱 규칙:**
- `[x]` 개수 카운트 → 완료
- `[ ]` 개수 카운트 → 미완료

### 2. 기간 계산

**plan.md에서 파싱:**
```markdown
**기간:** 2026-01-06 ~ 2026-01-10 (5일)
```

**계산:**
- 예상 기간: 5일
- 실제 기간: (회고 작성일 - 시작일)
- 차이: 실제 - 예상

### 3. Definition of Done 달성률

**DoD 섹션 파싱:**
```markdown
## Sprint 0 Definition of Done

### Sprint 시작 ✅
- [x] ADR-001 완성
- [x] ADR-002 완성

### US-{N}.1 ✅
- [x] 인프라 다이어그램 완성
```

**계산:**
- 각 US별 완료율
- 전체 DoD 달성률

---

## 중요 원칙

### 1. 사용자 주도 회고

- ✅ AI는 템플릿 제공 및 초안 생성
- ✅ 사용자가 직접 회고 작성 (본질적 가치)
- ✅ AI는 대화형 질문으로 가이드

### 2. 정량적 + 정성적 평가

- **정량적:** Sprint Metrics (완료율, 기간)
- **정성적:** What Went Well, What Didn't Go Well

### 3. Action Items 도출

- 다음 Sprint에서 실제로 적용 가능한 구체적 개선 사항
- "더 열심히 하기" 같은 추상적 항목 지양

---

## ✅ AI가 해야 할 것

- plan.md 파싱하여 Sprint Metrics 자동 계산
- 회고 템플릿 제공
- 대화형 질문으로 회고 가이드
- retrospective.md 파일 생성
- Sprint 완료 처리 (current-sprint.txt 업데이트)

## ❌ AI가 하지 말아야 할 것

- 사용자 대신 회고 내용 작성 (초안 제안은 OK, 강요는 NO)
- 사용자 평가나 조언 ("이번 Sprint는 잘했어요" 같은 것)
- 임의로 Action Items 결정
- plan.md 파일 수정

---

## 다음 스텝

Sprint 완료 후:

1. **retrospective.md 검토** - 회고 내용 확인 및 수정
2. **Action Items 정리** - 다음 Sprint에 반영할 개선 사항 정리
3. **`/sprint-start`** - 다음 Sprint 시작

---

**버전:** 3.0.0
**최종 업데이트:** 2026-02-09
**변경 사항:**
- **Breaking:** Iteration 중간 계층 제거, Sprint > US 2단계 구조로 단순화
- DoD 계산: US별 완료율로 변경
