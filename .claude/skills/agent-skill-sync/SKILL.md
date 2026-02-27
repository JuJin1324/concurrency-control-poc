---
name: agent-skill-sync
description: "다른 에이전트(Codex/Gemini) 스킬을 현재 에이전트(Claude) 스킬 형식으로 동기화/변환할 때 사용하는 스킬."
allowed-tools: Read,Write,Edit,Glob,Grep,Bash
disable-model-invocation: false
user-invocable: true
---

# Agent Skill Sync (Claude)

## 목적

다른 에이전트 스킬을 Claude 스킬 형식으로 빠르게 동기화합니다.

## 적용 범위

- 입력: source 스킬 폴더 (`.codex/skills`, `.gemini/skills`, 또는 다른 프로젝트 경로)
- 출력: 현재 저장소 `.claude/skills/<skill-name>/`
- 변환 규칙:
1. 디렉터리 전체 동기화 (`rsync -a`)
2. `SKILL.md` frontmatter를 Claude 형식으로 정규화
: `allowed-tools`, `disable-model-invocation`, `user-invocable` 필드 보장

## 실행

```bash
bash .claude/skills/agent-skill-sync/scripts/sync_to_claude.sh \
  ../problem-solving/.codex/skills \
  1h-agile-phase sprint-start 2w-brainstorm
```

## 검증

```bash
git status --short
sed -n '1,20p' .claude/skills/<skill-name>/SKILL.md
```

