---
name: agent-skill-sync
description: "다른 에이전트(Claude/Codex) 스킬을 현재 에이전트(Gemini) 스킬 형식으로 동기화/변환할 때 사용하는 스킬."
---

# Agent Skill Sync (Gemini)

## 목적

다른 에이전트 스킬을 Gemini 스킬 형식으로 빠르게 동기화합니다.

## 적용 범위

- 입력: source 스킬 폴더 (`.claude/skills`, `.codex/skills`, 또는 다른 프로젝트 경로)
- 출력: 현재 저장소 `.gemini/skills/<skill-name>/`
- 변환 규칙:
1. 디렉터리 전체 동기화 (`rsync -a`)
2. `SKILL.md` frontmatter에서 Gemini 비사용 필드 제거
: `allowed-tools`, `disable-model-invocation`, `user-invocable`

## 실행

```bash
bash .gemini/skills/agent-skill-sync/scripts/sync_to_gemini.sh \
  ../problem-solving/.claude/skills \
  1h-agile-phase sprint-start 2w-brainstorm
```

## 검증

```bash
git status --short
sed -n '1,20p' .gemini/skills/<skill-name>/SKILL.md
```

