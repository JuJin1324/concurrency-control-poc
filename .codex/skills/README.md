# Codex Skills

이 디렉터리는 Codex용 프로젝트 스킬 모음입니다.

## Skills

- `2w-brainstorm`: What/Why 문제 정의
- `1h-agile-phase`: How 다이어그램/Phase/지표 설계
- `sprint-start`: Sprint 계획 시작
- `sprint-status`: Sprint 진행 현황 집계
- `sprint-complete`: Sprint 회고/완료 정리
- `us-complete`: US 완료 요약 및 커밋 가이드
- `agent-skill-sync`: 다른 에이전트 스킬을 Codex 형식으로 동기화

## 형식 규칙

- `SKILL.md` frontmatter는 Codex 형식을 사용합니다.
- 필수 메타 필드:
  - `name`
  - `description`
- UI 노출 정보가 필요하면 `agents/openai.yaml`을 함께 관리합니다.

## 동기화 메모

- 공통 스킬 업데이트 시 `.claude/skills`, `.gemini/skills`와 함께 동기화합니다.
- 대량 동기화는 `agent-skill-sync` 스킬의 스크립트를 사용합니다.
