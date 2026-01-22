---
name: sprint-start
description: "새로운 Sprint를 시작하고 계획을 생성합니다. brainstorm.md 기반으로 Sprint Goal과 Tasks를 제안하고, 사용자 확정 후 공식 시작합니다."
---

# Sprint Start

## 목적

Sprint 단위로 프로젝트를 시작하며, 사용자가 주도권을 유지하도록 돕습니다.

**해결하는 문제:**
- ❌ Sprint 계획 없이 무작정 구현 시작 → 방향 상실
- ❌ AI에게 맡기면 → 주도성 상실
- ❌ 무엇을 해야 할지 매번 불명확

**제공하는 가치:**
- ✅ Sprint Goal 명확화 → 주도권 유지
- ✅ Task 구조화 → 진행 방향 명확
- ✅ 문서화 자동화 → 시간 절약

---

## 파일 구조

```
.agile/
├── config.yml                  # 설정 (Sprint 기본 기간 등)
├── current-sprint.txt          # 현재 Sprint 번호
├── sprints/
│   ├── sprint-0/
│   │   ├── plan.md            # Sprint 계획 (확정본)
│   │   └── retrospective.md   # 회고 (완료 후)
│   ├── sprint-1/
│   └── ...
└── templates/
    ├── sprint-plan.md         # Sprint 계획 템플릿
    └── retrospective.md       # 회고 템플릿
```

---

## 프로젝트 진행 철학

### AI 시대 워크플로우: 시각화 → 검토 → 구현

**전통적 방식:**
- 구현 → 문서화 (사후)

**AI 시대:**
- **시각화 → 검토 → 구현** (사전)
- AI가 비용 없이 다이어그램 생성
- 잘못된 방향으로 구현 방지

### 핵심 원칙

1. **인지 부하 최소화**: 작은 단위로 반복 (Iteration)
2. **빠른 피드백 루프**: Checkpoint로 검증
3. **검증 후 구현**: 잘못된 방향 사전 차단
4. **의사결정 기록**: 아키텍처 고민 발생 시점에 ADR 작성
5. **범위 준수**: brainstorm.md 같은 기획 문서 기반

### Iteration 구조

```
Sprint N
├── Sprint 시작: 프로젝트 방향 결정 (ADR 작성)
├── Iteration 1: {목표}
│   ├── 1. 시각화 (다이어그램, 필요시 ADR)
│   ├── 2. 🔍 Checkpoint (사용자 검토 및 확정)
│   ├── 3. 구현
│   └── 4. 검증
├── Iteration 2: {목표}
│   └── ...
└── Iteration N: 통합 및 문서화
```

---

## 작동 방식

### Step 1: 초기화 확인

**처음 실행 시:**
- `.agile/` 디렉터리가 없으면 생성
- `templates/` 디렉터리 및 템플릿 파일 생성
- `config.yml` 기본 설정 파일 생성

**이미 Sprint가 진행 중이면:**
- 경고 메시지 출력
- 현재 Sprint 완료를 먼저 권장
- 사용자가 강제로 시작을 선택할 수 있도록 옵션 제공

### Step 2: Sprint 계획 초안 생성

**2-1. 컨텍스트 수집:**
- `brainstorm.md` 또는 기존 문서 확인
- 다음 Sprint 번호 확인 (current-sprint.txt + 1)
- 프로젝트 전체 계획 파악

**2-2. Sprint 계획 초안 제안:**
- 파일 경로: `.agile/sprints/sprint-N/plan-draft.md`
- brainstorm.md의 Sprint 계획을 기반으로 초안 작성
- 템플릿 형식으로 작성:
  - Sprint Goal
  - 예상 기간
  - Tasks (User Story 형식 또는 체크리스트)
  - Iteration 구조 (복잡한 Sprint의 경우)
  - Definition of Done

**2-3. 사용자 검토 요청:**
```
📝 Sprint N 계획 초안을 생성했습니다.

📂 파일 위치: .agile/sprints/sprint-N/plan-draft.md

🎯 Sprint Goal: [목표]
📅 제안 기간: N일
📝 태스크: M개

---

이 계획을 검토하신 후 선택해주세요:
1. 이대로 확정 (Sprint 시작)
2. plan-draft.md 파일을 직접 수정 후 확정
3. 계획 수정 필요 (다시 논의)
```

### Step 3: 사용자 확정 대기

**사용자 선택:**
- **1번 선택:** plan-draft.md를 plan.md로 rename, Sprint 시작
- **2번 선택:** 사용자가 파일 수정 후 확정 명령 입력 대기
- **3번 선택:** 대화형으로 수정 사항 수집 후 다시 초안 생성

### Step 4: Sprint 시작 확정

**확정 후:**
- `plan-draft.md` → `plan.md` 로 rename
- `current-sprint.txt` 업데이트 (Sprint 번호 기록)
- Sprint 시작일 기록

**완료 메시지:**
```
✅ Sprint N이 공식 시작되었습니다!

📋 Sprint Goal: [목표]
📅 기간: {시작일} ~ {종료일} (N일)
📝 태스크: M개

📂 파일 위치: .agile/sprints/sprint-N/plan.md

💡 진행 방법:
1. plan.md 파일을 직접 수정하며 진행 상황 업데이트
2. 완료한 태스크: [ ] → [x]
3. 진행 중인 태스크: "Status: In Progress" 추가

다음 명령어:
- /sprint-status : 진행 상황 확인
- /sprint-complete : Sprint 완료 및 회고
```

---

## Sprint Plan 템플릿

### Iteration 구조 템플릿 (복잡한 Sprint용)

```markdown
# Sprint {N}: {Sprint Goal 짧은 제목}

**기간:** {시작일} ~ {종료일} ({X}일)
**목표:** {Sprint Goal 상세}

---

## Sprint Goal

> {한 문장으로 Sprint 목표 요약}

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**

**핵심 원칙:**
1. 작은 단위로 반복 (인지 부하 최소화)
2. 빠른 피드백 루프 (Checkpoint로 검증)
3. 검증 후 구현 (잘못된 방향 사전 차단)

---

## Tasks

### Sprint 시작: 프로젝트 방향 결정 (선택)
<!-- 프로젝트 전체 방향을 결정하는 ADR 작성 -->
- [ ] ADR-XXX: {범위 결정 근거}
- [ ] ADR-XXX: {워크플로우 결정 근거}

---

### Iteration 1: {목표}

#### {US-X.X}: {시각화 작업명}
- [ ] {다이어그램 작성}
- [ ] {ADR 작성 (기술 선택이 필요한 경우)}

**Acceptance Criteria:**
- {완료 조건 1}
- {완료 조건 2}

**🔍 Checkpoint 1:** {검토 및 확정}

---

#### {US-X.X}: {구현 작업명}
- [ ] {구현 태스크 1}
- [ ] {구현 태스크 2}

**Acceptance Criteria:**
- {완료 조건}

**✅ Iteration 1 완료 조건:** {완료 기준}

---

### Iteration 2: {목표}
<!-- 반복 구조 -->

---

## Sprint {N} Definition of Done

### Sprint 시작 ✅
- [ ] ADR 완성 (필요 시)

### Iteration 1 ✅
- [ ] 시각화 완성
- [ ] Checkpoint 통과
- [ ] 구현 완료
- [ ] 검증 완료

### Iteration 2 ✅
<!-- 반복 -->

### 최종 검증
- [ ] 모든 Acceptance Criteria 충족
- [ ] 문서화 완료

---

## Blockers

- 없음

---

## Notes

<!-- 기타 메모 -->
```

### 단순 템플릿 (간단한 Sprint용)

```markdown
# Sprint {N}: {Sprint Goal 짧은 제목}

**기간:** {시작일} ~ {종료일} ({X}일)
**목표:** {Sprint Goal 상세}

---

## Sprint Goal

> {한 문장으로 Sprint 목표 요약}

---

## Tasks

### 📋 To Do
- [ ] {태스크 1}
- [ ] {태스크 2}
- [ ] {태스크 3}

### 🔄 In Progress
<!-- 진행 중인 태스크 -->

### ✅ Completed
<!-- 완료한 태스크는 여기로 이동 -->

---

## Blockers

- 없음

---

## Notes

<!-- 기타 메모 -->
```

---

## 중요 원칙

### 1. 사용자 주도권 유지

- ✅ AI는 도구일 뿐, 사용자가 Sprint 목표 결정
- ✅ AI는 초안만 제안, 사용자가 최종 확정
- ✅ plan.md 파일을 직접 수정 (AI가 대신하지 않음)

### 2. brainstorm.md 기반 계획

- ✅ 기획 문서가 있으면 반드시 참조
- ✅ Sprint 범위를 명확히 정의
- ✅ 범위를 벗어나지 않음

### 3. ADR 작성 시점

ADR은 **아키텍처 고민이 발생한 시점에 자연스럽게 작성**:
- Sprint 시작 시: 프로젝트 전체 방향 결정
- Iteration 시작 시: 기술 스택 또는 아키텍처 패턴 선택

---

## ✅ AI가 해야 할 것

- `.agile/` 구조 자동 생성
- brainstorm.md 기반 Sprint 계획 초안 생성
- 템플릿 자동 제공
- 사용자 선택에 따라 파일 rename 및 Sprint 시작

## ❌ AI가 하지 말아야 할 것

- 사용자 대신 Sprint Goal 결정
- 사용자 대신 태스크 생성 (초안 제안은 OK, 강요는 NO)
- 사용자 동의 없이 Sprint 시작
- plan.md 파일을 사용자 동의 없이 수정

---

## 다음 스텝

Sprint 시작 후 사용자가 할 일:

1. **plan.md 파일 직접 수정** - 진행하면서 체크리스트 업데이트
2. **`/sprint-status`** - 진행 상황 확인
3. **`/sprint-complete`** - Sprint 완료 및 회고 작성

---

**버전:** 2.0.0
**최종 업데이트:** 2026-01-22
**변경 사항:**
- agile-sprint를 3개의 독립 스킬로 분리 (sprint-start, sprint-status, sprint-complete)
- sprint-start에 필요한 컨텍스트만 포함
