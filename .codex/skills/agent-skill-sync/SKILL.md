---
name: agent-skill-sync
description: "다른 에이전트(Claude/Gemini) 스킬 디렉터리를 현재 에이전트(Codex) 형식으로 동기화/변환할 때 사용하는 스킬."
---

# Agent Skill Sync

## 목적

다른 에이전트 스킬을 현재 에이전트(Codex) 스킬 형식으로 빠르게 동기화합니다.

**해결하는 문제:**
- 에이전트별 `SKILL.md` 메타데이터 형식 불일치
- 반복적인 수동 복사/정리 작업
- 다중 스킬 동기화 시 누락 위험

## 적용 범위

- 입력: `source` 에이전트 스킬 폴더 (`.claude/skills`, `.gemini/skills`, 또는 다른 프로젝트 경로)
- 출력: 현재 저장소의 `.codex/skills/<skill-name>/`
- 변환 규칙:
1. 디렉터리 전체 동기화 (`rsync -a`)
2. `SKILL.md` frontmatter에서 Codex 비필수 필드 제거
: `allowed-tools`, `disable-model-invocation`, `user-invocable`

## 실행 절차

### Step 1: 소스/타겟 확인

- 소스 경로가 유효한지 확인
- 대상 스킬 목록 확정

예시:
```bash
find ../problem-solving/.claude/skills -maxdepth 2 -name SKILL.md
```

### Step 2: 스크립트로 동기화

```bash
bash .codex/skills/agent-skill-sync/scripts/sync_to_codex.sh \
  ../problem-solving/.claude/skills \
  1h-agile-phase sprint-start 2w-brainstorm
```

### Step 3: 결과 검증

```bash
git status --short
sed -n '1,20p' .codex/skills/<skill-name>/SKILL.md
```

검증 기준:
- `.codex/skills/<skill-name>/` 생성/갱신 완료
- `SKILL.md` frontmatter에 `name`, `description`만 존재
- 템플릿/에셋/참조 파일이 누락 없이 복사됨

## 주의사항

- `.codex/skills` 쓰기가 샌드박스에서 제한될 수 있으므로, 실패 시 권한 상승 실행을 사용합니다.
- 스킬 본문 내용(워크플로우)은 자동 번역/재작성하지 않고, 형식 정리만 수행합니다.
- 타겟 스킬에 기존 변경이 있으면 덮어쓰므로 `git diff`로 확인 후 진행합니다.
