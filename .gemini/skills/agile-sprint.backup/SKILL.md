---
name: agile-sprint
description: "AI-Human 협업 시 주도권을 유지하며 Sprint 단위로 프로젝트를 관리하는 애자일 도구. Sprint 목표 설정, 진행 상황 시각화, 회고 자동화."
---

# Agile Sprint Coach

## 목적

AI와 협업할 때 사용자가 주도권을 유지하면서, Sprint 단위로 프로젝트를 체계적으로 진행합니다.

**해결하는 문제:**
- ❌ AI에게 맡기면 → 주도성 상실 → 방향 상실
- ❌ 어디까지 진행했는지 시각화 도구 없음
- ❌ 다음 할 일이 매번 불명확

**제공하는 가치:**
- ✅ Sprint 단위 목표 설정 → 주도권과 마음의 평화
- ✅ 진행 상황 시각화 → 명확한 현황 파악
- ✅ 회고 자동화 → 애자일 학습 및 개선

---

## 핵심 명령어

### 사용자가 호출하는 명령어

```bash
/sprint-start       # Sprint 시작 (목표 설정, 태스크 생성)
/sprint-status      # 진행 상황 조회 (시각화)
/sprint-complete    # Sprint 완료 (회고 작성)
```

### 선택적 명령어 (나중에 추가)

```bash
/task-add           # 태스크 추가
/task-update        # 태스크 상태 업데이트
/sprint-list        # 전체 Sprint 목록 조회
```

---

## 파일 구조

```
.agile/
├── config.yml                  # 설정 (Sprint 기본 기간, 팀원 등)
├── sprints/
│   ├── sprint-1/
│   │   ├── plan.md            # Sprint 목표 + 태스크 목록
│   │   └── retrospective.md   # 회고
│   ├── sprint-2/
│   │   └── ...
│   └── current-sprint.txt     # 현재 진행 중인 Sprint 번호
└── templates/
    ├── sprint-plan.md         # Sprint 계획 템플릿
    └── retrospective.md       # 회고 템플릿
```

---

## 프로젝트 진행 철학

### 왜 순서가 중요한가?

순서는 절대적이지 않지만, **일관된 철학**이 있으면:
- ✅ 다음 프로젝트에서 재사용 가능한 패턴 확보
- ✅ 방향을 잃지 않고 체계적으로 진행
- ✅ AI와 협업 시 혼란 최소화

### 5가지 핵심 원칙

#### 1. AI 시대 워크플로우: 시각화 → 검토 → 구현

**전통적 방식:**
- 구현 → 문서화 (사후)
- 코드를 먼저 작성하고 나중에 설명

**AI 시대:**
- **시각화 → 검토 → 구현** (사전)
- AI가 비용 없이 다이어그램 생성
- 사람은 다이어그램이 코드보다 이해 쉬움
- 잘못된 방향으로 구현 방지

#### 2. 인지 부하 최소화: 작은 단위로 반복

**잘못된 방식:**
- ❌ 모든 시각화 → 모든 구현 (한꺼번에)
- ❌ 인지 과부하, 피드백 느림

**올바른 방식:**
- ✅ Iteration별로 시각화 → 구현 (하나씩)
- ✅ 한 번에 하나씩 집중
- ✅ 빠른 완결

#### 3. 빠른 피드백 루프: Checkpoint로 검증

- 각 Iteration마다 검토 시점 (Checkpoint)
- 사용자가 확정하기 전까지 구현 안 함
- 잘못된 방향 조기 차단

#### 4. 의사결정의 자연스러운 기록

**ADR (Architecture Decision Records) 작성 철학:**

**잘못된 방식:**
- ❌ "오늘은 ADR 작성하는 날"
- ❌ 모든 작업 끝나고 일괄 정리 (답정너)

**올바른 방식:**
- ✅ "이 기술을 왜 선택했는지 기록해야겠다"
- ✅ **아키텍처 고민이 발생한 시점에 즉시 작성**

**작성 시점 예시:**
- Sprint 시작 시: 프로젝트 범위, 목표, 워크플로우 결정
- 인프라 시각화 시: 기술 스택 선택 (MySQL vs PostgreSQL)
- 애플리케이션 시각화 시: 아키텍처 패턴 선택 (Layered vs Hexagonal)

#### 5. 범위 준수

- brainstorm.md 같은 기획 문서 기반
- Sprint 범위를 명확히 정의
- 범위를 벗어나지 않음

### Iteration 구조

각 Sprint는 여러 Iteration으로 구성됩니다:

```
Sprint N
├── Sprint 시작: 프로젝트 방향 결정 (ADR 작성)
├── Iteration 1: {목표}
│   ├── 1. 시각화 (다이어그램, 필요시 ADR)
│   ├── 2. 🔍 Checkpoint (사용자 검토 및 확정)
│   ├── 3. 구현
│   └── 4. 검증
├── Iteration 2: {목표}
│   ├── 1. 시각화
│   ├── 2. 🔍 Checkpoint
│   ├── 3. 구현
│   └── 4. 검증
└── Iteration N: 통합 및 문서화
    ├── 전체 시스템 시각화
    └── README 작성
```

**핵심:**
- 작은 단위로 완결되는 Iteration
- 각 Iteration은 시각화 → Checkpoint → 구현 순서
- Checkpoint 통과 전까지 구현 안 함

---

## 작동 방식 (Workflow)

### 1. Sprint 시작 (`/sprint-start`)

#### Step 1: 초기화 확인

**처음 실행 시:**
- `.agile/` 디렉터리가 없으면 생성
- `templates/` 디렉터리 및 템플릿 파일 생성
- `config.yml` 기본 설정 파일 생성

**이미 Sprint가 진행 중이면:**
- 경고 메시지 출력
- 현재 Sprint 완료를 먼저 권장

#### Step 2: Sprint 계획 초안 생성 (사용자 주도)

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
  - Acceptance Criteria

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

#### Step 3: 사용자 확정 대기

**사용자 선택:**
- **1번 선택:** plan-draft.md를 plan.md로 rename, Sprint 시작
- **2번 선택:** 사용자가 파일 수정 후 확정 명령 입력 대기
- **3번 선택:** 대화형으로 수정 사항 수집 후 다시 초안 생성

#### Step 4: Sprint 시작 확정

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

### 2. 진행 상황 조회 (`/sprint-status`)

#### Step 1: 현재 Sprint 확인

- `.agile/current-sprint.txt` 읽기
- 현재 Sprint 번호 확인
- Sprint가 없으면 → `/sprint-start` 권장

#### Step 2: Sprint Plan 파일 파싱

**파일 읽기:** `.agile/sprints/sprint-N/plan.md`

**태스크 상태 집계:**
- `[x]` → Completed
- `[ ]` (+ "Status: In Progress") → In Progress
- `[ ]` (기본) → To Do

**진행률 계산:**
```
진행률 = (Completed / Total) * 100
```

#### Step 3: 시각화 출력

```
📊 Sprint N 진행 상황

🎯 Goal: [Sprint 목표]
📅 남은 기간: X일

📈 Progress: ████████░░ 60% (3/5)

✅ Completed (2)
  - Stock Domain 모델 구현
  - Pessimistic Lock Service 구현

🔄 In Progress (1)
  - Optimistic Lock Service 구현

📋 To Do (2)
  - REST API 구현
  - 통합 테스트 작성

💡 Tip: plan.md 파일을 직접 수정해서 진행 상황을 업데이트하세요!
     완료한 태스크는 [ ]를 [x]로 변경하면 됩니다.
```

---

### 3. Sprint 완료 (`/sprint-complete`)

#### Step 1: Sprint 완료 확인

```
Q: "Sprint N을 완료하시겠습니까? (y/n)"
> (사용자 입력)
```

**아니오인 경우:**
- Sprint 계속 진행 안내
- `/sprint-status`로 현황 확인 권장

#### Step 2: Sprint Metrics 계산

**자동 계산:**
- 전체 태스크 수
- 완료한 태스크 수
- 완료율 (%)
- 예상 vs 실제 기간 (선택)

#### Step 3: 회고 작성 (대화형)

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
```

#### Step 4: Retrospective 파일 생성

**파일 경로:** `.agile/sprints/sprint-N/retrospective.md`

**파일 내용:** (아래 템플릿 참조)

#### Step 5: 완료 메시지 출력

```
✅ Sprint N 회고가 완료되었습니다!

📂 회고 파일: .agile/sprints/sprint-N/retrospective.md

다음 Sprint를 시작하려면:
- /sprint-start
```

---

## 템플릿

### Sprint Plan 템플릿

#### 기본 템플릿 (Iteration 구조)

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

**🔍 Checkpoint 1:** {검토 및 확정}

#### {US-X.X}: {구현 작업명}
- [ ] {구현 태스크 1}
- [ ] {구현 태스크 2}

**✅ Iteration 1 완료 조건:** {완료 기준}

---

### Iteration 2: {목표}

#### {US-X.X}: {시각화 작업명}
- [ ] {다이어그램 작성}
- [ ] {ADR 작성 (필요시)}

**🔍 Checkpoint 2:** {검토 및 확정}

#### {US-X.X}: {구현 작업명}
- [ ] {구현 태스크}

**✅ Iteration 2 완료 조건:** {완료 기준}

---

## Sprint Progress

### ✅ Completed
<!-- 완료한 태스크는 여기로 이동 -->

### 🔄 In Progress
<!-- 진행 중인 태스크 -->

### 📋 To Do
<!-- 아직 시작하지 않은 태스크 -->

---

## Blockers
<!-- 진행 중 발생한 장애물 -->
- 없음

---

## Notes
<!-- 기타 메모 -->
```

#### 단순 템플릿 (Iteration 없는 경우)

Iteration이 필요 없는 간단한 Sprint의 경우:

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
<!-- 진행 중 발생한 장애물 -->
- 없음

---

## Notes
<!-- 기타 메모 -->
```

**예시:**

```markdown
# Sprint 1: DB Lock 구현

**기간:** 2026-01-06 ~ 2026-01-12 (7일)
**목표:** MySQL 기반 동시성 제어 2가지 구현 및 검증

---

## Sprint Goal
> Pessimistic Lock과 Optimistic Lock을 구현하고, 동시성 테스트로 검증한다.

---

## Tasks

### ✅ Completed
- [x] Stock Domain 모델 구현 (2026-01-06)
- [x] Pessimistic Lock Service 구현 (2026-01-07)

### 🔄 In Progress
- [ ] Optimistic Lock Service 구현
  - Status: 50% (Version 컬럼 추가 완료, Retry 로직 작성 중)

### 📋 To Do
- [ ] REST API 구현
- [ ] 통합 테스트 작성

---

## Blockers
- 없음

---

## Notes
- Optimistic Lock Retry 로직: 최대 3회 재시도
```

---

### Retrospective 템플릿

```markdown
# Sprint {N} Retrospective

**날짜:** {회고 날짜}
**참여자:** {이름}

---

## Sprint Metrics

- 계획한 Task: {N}개
- 완료한 Task: {M}개
- 완료율: {X}%

---

## What Went Well (잘된 점)

{사용자 입력}

---

## What Didn't Go Well (아쉬운 점)

{사용자 입력}

---

## Action Items (다음 Sprint 개선 사항)

- [ ] {개선 사항 1}
- [ ] {개선 사항 2}

---

## 핵심 교훈

> {한 문장 요약}
```

**예시:**

```markdown
# Sprint 1 Retrospective

**날짜:** 2026-01-12
**참여자:** jujin

---

## Sprint Metrics

- 계획한 Task: 5개
- 완료한 Task: 4개
- 완료율: 80%

---

## What Went Well (잘된 점)

- Stock Domain 설계가 깔끔했음 (DDD 패턴 적용)
- Pessimistic Lock 구현이 예상보다 빨랐음

---

## What Didn't Go Well (아쉬운 점)

- Optimistic Lock Retry 로직 구현에 예상보다 시간 소요
- 통합 테스트 작성을 미루다가 마지막에 몰림

---

## Action Items (다음 Sprint 개선 사항)

- [ ] 테스트 코드를 기능 구현과 동시에 작성
- [ ] Retry 로직같은 공통 로직은 먼저 설계 후 구현

---

## 핵심 교훈

> 테스트 코드를 미루면 나중에 더 많은 시간이 걸린다. TDD는 오버엔지니어링이 아니다.
```

---

## Sprint 체크리스트

### Sprint 시작 전 확인
- [ ] 이전 Sprint가 완료되었는가?
- [ ] Sprint Goal이 명확한가?
- [ ] 태스크 개수가 적절한가? (5-10개 권장)
- [ ] 기간이 적절한가? (1-2주 권장)

### Sprint 진행 중 확인
- [ ] 매일 또는 주 2-3회 `/sprint-status`로 현황 확인하는가?
- [ ] plan.md 파일을 직접 수정해서 진행 상황 업데이트하는가?
- [ ] Blocker가 발생하면 즉시 기록하는가?

### Sprint 완료 후 확인
- [ ] 회고를 작성했는가?
- [ ] Action Items를 다음 Sprint에 반영했는가?
- [ ] Sprint Metrics를 기록했는가?

---

## 중요 원칙

### 1. YAGNI (You Aren't Gonna Need It)
- **Phase 1:** 혼자 PoC → 최소 기능 (Sprint 시작/상태/완료)
- **Phase 2:** 지인 협업 → 태스크 할당, 팀원 관리
- **Phase 3:** 회사 적용 → Jira 연동, 대시보드

**지금 필요한 것만 구현합니다.**

### 2. 사용자 주도권 유지

- ✅ AI는 도구일 뿐, 사용자가 Sprint 목표 결정
- ✅ plan.md 파일을 직접 수정 (AI가 대신하지 않음)
- ✅ 회고는 사용자가 직접 작성 (AI는 템플릿만 제공)

### 3. 완성 우선

**완성된 Sprint:**
- ✅ Sprint Goal이 명확함
- ✅ 진행 상황이 시각화됨
- ✅ 회고가 작성됨

**미완성 Sprint:**
- ❌ "구현 중입니다"
- ❌ 회고 없이 다음 Sprint 시작
- ❌ plan.md만 있고 업데이트 안 됨

---

## 애자일 학습 목표

### Scrum Lite 적용

**Phase 1 (혼자):**
- ✅ Sprint Planning (목표 설정)
- ✅ Sprint Review (회고)
- ❌ Daily Standup (혼자라 불필요)

**Phase 2 (협업):**
- ✅ Sprint Planning
- ✅ Daily Standup (간략하게)
- ✅ Sprint Review
- ✅ Sprint Retrospective

### 회사 애자일 vs 내 방식 비교

**비교 기준 확보:**
- 내 방식으로 Sprint를 진행해본 경험
- 무엇이 잘 되고 안 되는지 직접 느낌
- 회사 방식을 만났을 때 능동적으로 비교 가능

---

## Success Criteria

### 필수 (Must Have)

- [ ] `/sprint-start`로 Sprint 시작 가능
- [ ] `/sprint-status`로 진행 상황 시각화
- [ ] `/sprint-complete`로 회고 자동 작성
- [ ] `.agile/` 디렉터리 구조가 Git으로 관리됨
- [ ] Sprint Plan과 Retrospective 템플릿 동작

### 선택 (Nice to Have)

- [ ] 태스크 할당 (Assignee) - Phase 2
- [ ] 팀원 관리 - Phase 2
- [ ] Daily/Weekly 리마인드 - Phase 2
- [ ] Jira 연동 - Phase 3
- [ ] 번다운 차트 - Phase 3

### 최종 목표

- [ ] 사용자가 "AI와 협업할 때 주도권을 유지한다"고 느낌
- [ ] Sprint 단위로 프로젝트를 체계적으로 진행
- [ ] 회사 애자일 vs 내 방식 비교 기준 확보

---

## 주의 사항

### ✅ AI가 해야 할 것

- 템플릿 자동 생성
- 진행 상황 파싱 및 시각화
- Sprint Metrics 자동 계산
- 회고 템플릿 제공

### ❌ AI가 하지 말아야 할 것

- 사용자 대신 Sprint Goal 결정
- 사용자 대신 태스크 생성
- 사용자 대신 회고 작성
- plan.md 파일을 사용자 동의 없이 수정

**핵심: 사용자가 주도권을 유지해야 AI도 방향을 잃지 않는다.**

---

## 다음 스텝

### 즉시 실행 가능

```bash
/sprint-start
```

→ 첫 Sprint를 시작하고, `.agile/` 구조가 자동 생성됩니다.

### 진행 중 확인

```bash
/sprint-status
```

→ 언제든지 현재 진행 상황을 확인할 수 있습니다.

### Sprint 완료

```bash
/sprint-complete
```

→ 회고를 작성하고, 다음 Sprint를 준비합니다.

---

**버전:** 1.1.0
**최종 업데이트:** 2026-01-06
**변경 사항:**
- 프로젝트 진행 철학 섹션 추가 (AI 시대 워크플로우, 5가지 핵심 원칙)
- Iteration 구조 가이드 추가
- ADR 작성 시점 가이드 추가
- Sprint Plan 템플릿에 Iteration 구조 반영
- "체크포인트" → "Sprint 체크리스트"로 명칭 변경

**대상 프로젝트:** `concurrency-control-poc` (Phase 1)
