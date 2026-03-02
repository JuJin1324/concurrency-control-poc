---
name: sprint-start
description: "새로운 Sprint를 시작하고 계획을 생성합니다. 2w-brainstorm.md와 how-diagram.md(Phase)를 기반으로 Sprint Goal과 Tasks를 제안하고, 사용자 확정 후 공식 시작합니다."
allowed-tools: Read,Write,Edit,Glob,Grep,Bash
disable-model-invocation: false
user-invocable: true
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

## 공통 출력 규칙 (Quick Guide + Intent/Review/Cleanup)

모든 계획 문서는 본문 앞에 30초 내 확인 가능한 `Quick Guide`를 포함합니다.

**Quick Guide 필수 구성 (최대 8줄, 내용 파악 중심):**
1. 이번 문서의 핵심 결론 1문장
2. 이번 문서에서 확정된 결정 2~3개 (값/선택지 포함, 각 US 완료 목표일 포함)
3. 실행에 바로 영향 주는 항목 2~3개 (무엇을 먼저 할지)
4. 핵심 가정/근거 1~2개 (왜 이렇게 정했는지)
5. 미확정 항목/리스크 1~2개 (추가 확인 필요 사항)

**Intent/판단 근거 규칙:**
- US 또는 핵심 변경에는 `Intent`를 짧게 명시합니다.
- 필요 시 선택 이유를 1줄로 추가합니다.

**Review/Cleanup 규칙:**
1. 초안에는 Quick Guide와 Intent 설명을 포함해 리뷰를 요청합니다.
2. 리뷰 반영 후 최종본에서는 임시 해설 문구를 제거하거나 최소 요약만 남깁니다.
3. 최종 문서는 실행 본문과 증적 중심으로 정리합니다.
4. 결과물 이해 보조 목적의 Summary는 Quick Guide로 대체합니다.

---

## 파일 구조

```
.agile/
├── config.yml                  # 설정 (Sprint 기본 기간 등)
├── current-sprint.txt          # 현재 Sprint 번호
├── sprints/
│   ├── sprint-0/
│   │   ├── plan.md            # Sprint 계획 (확정본)
│   │   ├── step-0.1-log.md    # Step 0.1 증적 로그 (선택)
│   │   ├── step-0.2-log.md    # Step 0.2 증적 로그 (선택)
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

1. **인지 부하 최소화**: 작은 단위로 반복 (Step 단위)
2. **빠른 피드백 루프**: Step마다 리뷰로 검증
3. **검증 후 구현**: 잘못된 방향 사전 차단
4. **의사결정 기록**: 아키텍처 고민 발생 시점에 ADR 작성
5. **범위 준수**: 실행 계획 기반 타임박싱
   - 🎯 **주 참조**: `how-diagram.md` (루트 또는 `docs/planning/`) - Phase/Sprint 실행 로드맵
   - 📚 **부 참조**: `2w-brainstorm.md` (루트 또는 `docs/planning/`) - 프로젝트 배경 컨텍스트 (What/Why)

### Sprint > US > Step 진행 구조

> **Sprint > US > Step** 3단계 구조로 작업을 설계한다.
> - **US (User Story):** 리뷰하고 넘어가는 단위. 하나의 논리적 목표를 달성한다.
> - **Step:** US 안의 가장 작은 실행 단위. **작성 → 리뷰** 쌍으로, 하나를 완료하고 다음으로 넘어간다.

**Step 네이밍 규칙:** `Step-{Sprint}.{US}.{Step}` (예: `Step-0.1.1`, `Step-1.2.3`)

```
Sprint N
├── Sprint 시작: 프로젝트 방향 결정 (ADR 작성, 선택)
├── US-N.1: {목표}
│   ├── Step-N.1.1: {작업} → 🔍 리뷰
│   ├── Step-N.1.2: {작업} → 🔍 리뷰
│   └── Step-N.1.3: {작업} → 🔍 리뷰
├── US-N.2: {목표}
│   ├── Step-N.2.1: {작업} → 🔍 리뷰
│   └── Step-N.2.2: {작업} → 🔍 리뷰
└── US-N.M: 통합 및 문서화
    └── ...
```

**Step의 크기 기준:**
- 하나의 산출물(문서, 다이어그램, 구현 단위)을 만들고 리뷰받을 수 있는 단위
- 학습 Sprint: Step = 개념 1개 (학습 문서 작성 + 리뷰)
- 구현 Sprint: Step = 시각화/구현/검증 등 독립적으로 리뷰 가능한 작업 단위

### Step Evidence Log 파일 (선택)

> Quick Guide가 문서 이해 보조를 담당하므로, 별도 요약 문서는 증적이 필요할 때만 작성합니다.

**파일 경로:** `.agile/sprints/sprint-N/step-N.M-log.md`

**생성 시점:** 특정 Step/US 종료 후 증적 보존이 필요한 경우

**포함 권장 내용 (증적 중심):**
1. 완료한 작업 목록
2. 생성/수정된 파일 목록 (경로 포함)
3. 주요 결정사항 및 트레이드오프
4. 테스트/검증 결과

**템플릿:** `templates/step-log.md`를 읽어 초안을 생성하세요.

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

### Step 2: Sprint 브랜치 생성

- 다음 Sprint 번호 확인 (`current-sprint.txt + 1` → `N`)
- 브랜치명 규칙: `sprint-{N}`
- 브랜치 생성/전환:
  - 브랜치가 없으면 `git checkout -b sprint-{N}`
  - 이미 있으면 `git checkout sprint-{N}`
- 이미 `sprint-{N}` 브랜치에 있으면 그대로 진행
- 미커밋 변경이 많아 브랜치 전환 리스크가 있으면 사용자에게 확인 후 진행

### Step 3: Sprint 계획 초안 생성

**3-1. 컨텍스트 수집:**
- **문서 탐색 순서:**
  1. 프로젝트 루트 (`./how-diagram.md`, `./2w-brainstorm.md`)
  2. `docs/planning/` 폴더
- 🎯 `how-diagram.md` 확인 (실행 로드맵 - 메인)
  - 다음 Sprint에 해당하는 Phase/Sprint 섹션 찾기
  - Phase의 목표와 산출물 파악
- 📚 `2w-brainstorm.md` 확인 (배경 컨텍스트 - 참고용)

**3-2. Phase 목표 일정 확인 및 조정:**
- `how-diagram.md`에서 해당 Phase의 **목표 날짜**를 확인한다
- `how-diagram.md`에 `일정 전략 (Scheduling Strategy)` 섹션이 있으면 아래 값을 우선 사용:
  - 전략: `tight` 또는 `buffered`
  - 버퍼: `+N일`
  - 총 목표 기간: `순수 작업일 + 버퍼일`
- ⚠️ how-diagram.md의 날짜는 **확정 일정이 아닌 목표 날짜**임을 인지
- 전략 정보가 있으면 사용자에게 다음처럼 안내하고 확인:
  ```
  📅 Phase N의 목표 일정: MM/DD ~ MM/DD (N일)
  일정 전략: buffered (+2일 버퍼 포함)
  오늘 날짜: MM/DD

  이 일정대로 진행할까요?
  ```
- 전략 정보가 없으면(레거시 문서) 기존 방식으로 확인:
  ```
  📅 Phase N의 목표 일정: MM/DD ~ MM/DD (N일)
  오늘 날짜: MM/DD

  이 일정대로 진행할까요?
  ```
- 전략 정보가 없고 사용자가 일정 전략을 아직 확정하지 않았다면, 반드시 재질문:
  ```
  ⚠️ how-diagram.md에 일정 전략 정보가 없습니다.
  일정 전략을 먼저 확정할게요:
  1) tight (버퍼 0일)
  2) buffered (버퍼 포함)
  ```
  - 이 경우 전략 확정 전에는 기간 계산 확정/plan-draft 생성 단계로 진행하지 않는다.
- **오늘이 목표 시작일을 이미 넘긴 경우**, 아래 대응을 제안:
  1. 지연 일수 계산 및 안내
  2. 남은 전체 기간에서 각 Phase 기간을 비례 축소하여 **조정안 제안** (버퍼 포함 총기간 기준)
  3. Phase 범위를 축소하여 기간 내 완료 가능하도록 **범위 축소안 제안**
  4. 사용자 선택에 따라 진행

**3-3. Sprint 계획 초안 제안 (타임박싱):**
- 파일 경로: `.agile/sprints/sprint-N/plan-draft.md`
- **how-diagram.md의 Phase를 Sprint로 타임박싱**
  - Phase의 논리적 목표 → Sprint Goal
  - 산출물 → Tasks (체크리스트)
  - 기간 설정 (타임박스, 3-2에서 확정된 일정 반영)
- 템플릿 형식으로 작성:
  - **Quick Guide (리뷰 전 30초 가이드)**
  - Sprint Goal
  - 예상 기간
  - **Sprint 흐름 요약 (US > Step 구성 + 의존 관계)**
  - Tasks (US > Step 형식, 각 Step은 작성 → 리뷰 쌍)
  - **각 US에 Intent(의도) 포함 — 왜 이 US가 필요한지**
  - **각 US에 완료 목표일 명시 — 일정 선행/지연 판단 기준**
  - **각 Step에 산출물과 리뷰 체크박스 포함**
  - Definition of Done

**3-4. 사용자 검토 요청:**
```
📝 Sprint N 계획 초안을 생성했습니다.
🌿 작업 브랜치: sprint-N

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

### Step 4: 사용자 확정 대기

**사용자 선택:**
- **1번 선택:** plan-draft.md를 plan.md로 rename, Sprint 시작
- **2번 선택:** 사용자가 파일 수정 후 확정 명령 입력 대기
- **3번 선택:** 대화형으로 수정 사항 수집 후 다시 초안 생성

### Step 5: Sprint 시작 확정

**확정 후:**
- `plan-draft.md` → `plan.md` 로 rename
- 리뷰 완료 기준으로 Quick Guide/임시 해설 문구 정리
- `current-sprint.txt` 업데이트 (Sprint 번호 기록)
- Sprint 시작일 기록

**완료 메시지:**
```
✅ Sprint N이 공식 시작되었습니다!

📋 Sprint Goal: [목표]
🌿 브랜치: sprint-N
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

**3-3. Sprint 계획 초안 생성:**
템플릿 파일을 읽어 초안을 생성하세요:
- 복잡한 Sprint (US > Step 구조): `templates/sprint-plan-us.md`
- 간단한 Sprint (태스크 리스트): `templates/sprint-plan-simple.md`

---

## 중요 원칙

### 1. 사용자 주도권 유지

- ✅ AI는 도구일 뿐, 사용자가 Sprint 목표 결정
- ✅ AI는 초안만 제안, 사용자가 최종 확정
- ✅ plan.md 파일을 직접 수정 (AI가 대신하지 않음)

### 2. 2w-brainstorm.md 및 how-diagram.md 기반 계획

- ✅ 기획 문서가 있으면 반드시 참조
- ✅ Sprint 범위를 명확히 정의 (Phase 범위 내)
- ✅ 범위를 벗어나지 않음

### 3. ADR 작성 시점

ADR은 **아키텍처 고민이 발생한 시점에 자연스럽게 작성**:
- Sprint 시작 시: 프로젝트 전체 방향 결정
- US 시작 시: 기술 스택 또는 아키텍처 패턴 선택

---

## ✅ AI가 해야 할 것

- `.agile/` 구조 자동 생성
- 다음 Sprint 번호 기준 브랜치(`sprint-N`) 생성/전환
- `how-diagram.md` (루트/docs) 기반 Sprint 계획 초안 생성
- 템플릿 자동 제공
- 사용자 선택에 따라 파일 rename 및 Sprint 시작
- 필요 시 **step-N.M-log.md 증적 로그 생성** (선택, 증적/메트릭/결정 로그 중심)

## ❌ AI가 하지 말아야 할 것

- 사용자 대신 Sprint Goal 결정
- 사용자 대신 태스크 생성 (초안 제안은 OK, 강요는 NO)
- 사용자 동의 없이 위험한 브랜치 전환 강행
- 사용자 동의 없이 Sprint 시작
- plan.md 파일을 사용자 동의 없이 수정
- Quick Guide와 중복되는 설명용 Summary를 강제 생성

---

## 다음 스텝

Sprint 시작 후 사용자가 할 일:

1. **plan.md 파일 직접 수정** - 진행하면서 체크리스트 업데이트
2. **`/sprint-status`** - 진행 상황 확인
3. **`/sprint-complete`** - Sprint 완료 및 회고 작성

---
