# Agile Sprint 관리

이 디렉터리는 `agile-sprint` Skill을 사용하여 프로젝트를 Sprint 단위로 관리합니다.

## 디렉터리 구조

```
.agile/
├── config.yml              # Sprint 기본 설정
├── current-sprint.txt      # 현재 진행 중인 Sprint 번호
├── sprints/                # Sprint별 계획 및 회고
│   ├── sprint-1/
│   │   ├── plan.md
│   │   └── retrospective.md
│   └── sprint-2/
│       └── ...
└── templates/              # Sprint 템플릿
    ├── sprint-plan.md
    └── retrospective.md
```

## 사용 방법

### Sprint 시작

```bash
/sprint-start
```

대화형으로 Sprint 목표, 기간, 태스크를 입력하면 `sprints/sprint-N/plan.md` 파일이 자동 생성됩니다.

### 진행 상황 확인

```bash
/sprint-status
```

현재 Sprint의 진행 상황을 시각화하여 보여줍니다.

### Sprint 완료 및 회고

```bash
/sprint-complete
```

Sprint를 완료하고, 회고를 작성합니다.

## 태스크 관리

`sprints/sprint-N/plan.md` 파일을 직접 수정하여 태스크 상태를 업데이트합니다:

- `[ ]` → To Do
- `[ ]` (+ "Status: In Progress") → In Progress
- `[x]` → Completed

## Phase 1: 혼자 PoC

현재 Phase 1 단계로, 다음 기능만 사용합니다:

- ✅ Sprint Planning (목표 설정)
- ✅ Sprint Status (진행 상황 조회)
- ✅ Sprint Review (회고)
- ❌ Daily Standup (혼자라 불필요)

## Phase 2: 협업 (나중에)

지인 협업 시 추가 기능:

- 태스크 할당 (Assignee)
- 팀원 관리
- Daily/Weekly 리마인드

## 참고

- Skill 위치: `.claude/skills/agile-sprint/SKILL.md`
- 브레인스토밍 문서: `../problem-solving/problems/agile-coach/brainstorm.md`
