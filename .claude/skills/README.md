# Claude Code Skills

이 디렉터리에는 프로젝트에서 사용하는 커스텀 Claude Code 스킬이 포함되어 있습니다.

## 사용 가능한 스킬

### 1. sprint-start
**명령어:** `/sprint-start`

새로운 Sprint를 시작하고 계획을 생성합니다.
- 🎯 `docs/planning/how-diagram.md`의 Phase를 Sprint로 타임박싱
- 사용자 검토 후 공식 Sprint 시작
- `.agile/sprints/sprint-N/plan.md` 생성

### 2. sprint-status
**명령어:** `/sprint-status`

현재 Sprint의 진행 상황을 시각화합니다.
- 완료/진행 중/대기 중 태스크 집계
- 진행률 계산 및 Progress Bar 표시
- 남은 기간 확인

### 3. sprint-complete
**명령어:** `/sprint-complete`

Sprint를 완료하고 회고를 작성합니다.
- Sprint Metrics 자동 계산 (완료율, 기간)
- 회고 템플릿 제공 및 대화형 가이드
- `.agile/sprints/sprint-N/retrospective.md` 생성

## 사용 방법

### Sprint 시작
```bash
/sprint-start
```
1. AI가 `docs/planning/how-diagram.md`의 Phase를 Sprint로 타임박싱하여 계획 초안 생성
2. 사용자가 초안을 검토하고 수정
3. 확정 후 공식 Sprint 시작

**AI의 역할:** Phase (논리적 단계) → Sprint (시간 단위 실행 박스) 변환

### 진행 상황 확인
```bash
/sprint-status
```
- 언제든지 실행하여 현재 진행 상황 확인
- plan.md를 직접 수정하면 즉시 반영

### Sprint 완료 및 회고
```bash
/sprint-complete
```
1. AI가 Sprint Metrics 자동 계산
2. 대화형 질문으로 회고 가이드
3. retrospective.md 파일 생성

## 파일 구조

```
.agile/
├── config.yml                  # 설정
├── current-sprint.txt          # 현재 Sprint 번호
├── sprints/
│   ├── sprint-0/
│   │   ├── plan.md            # Sprint 계획
│   │   └── retrospective.md   # 회고
│   ├── sprint-1/
│   └── ...
└── templates/
    ├── sprint-plan.md         # Sprint 계획 템플릿
    └── retrospective.md       # 회고 템플릿
```

## 변경 이력

### v2.0.0 (2026-01-22)
- **Breaking Change:** `agile-sprint` 스킬을 3개의 독립 스킬로 분리
  - `sprint-start`: Sprint 시작 및 계획 생성
  - `sprint-status`: 진행 상황 조회
  - `sprint-complete`: Sprint 완료 및 회고
- 이유: Claude Code는 각 스킬을 독립적인 디렉터리로 인식하므로 분리 필요
- 백업: `.claude/skills/agile-sprint.backup/`에 원본 보관

### v1.1.0 (2026-01-06)
- 프로젝트 진행 철학 섹션 추가
- Iteration 구조 가이드 추가
- ADR 작성 시점 가이드 추가

## 주의 사항

### 사용자 주도권 유지
- AI는 도구일 뿐, 사용자가 Sprint 목표 결정
- plan.md 파일은 사용자가 직접 수정
- 회고는 사용자가 직접 작성 (AI는 템플릿만 제공)

### AI의 역할
- ✅ 템플릿 자동 생성
- ✅ 진행 상황 파싱 및 시각화
- ✅ Sprint Metrics 자동 계산
- ❌ 사용자 대신 의사결정
- ❌ 사용자 동의 없이 파일 수정
