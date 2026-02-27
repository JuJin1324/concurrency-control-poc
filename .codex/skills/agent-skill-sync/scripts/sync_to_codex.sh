#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <source-skills-dir> <skill-name> [skill-name ...]" >&2
  echo "Example: $0 ../problem-solving/.claude/skills 1h-agile-phase sprint-start" >&2
  exit 1
fi

SOURCE_DIR="$1"
shift

if [ ! -d "$SOURCE_DIR" ]; then
  echo "Source skills directory not found: $SOURCE_DIR" >&2
  exit 1
fi

for SKILL in "$@"; do
  SRC="$SOURCE_DIR/$SKILL"
  DST=".codex/skills/$SKILL"

  if [ ! -d "$SRC" ]; then
    echo "[skip] Missing source skill: $SRC" >&2
    continue
  fi

  mkdir -p "$DST"
  rsync -a "$SRC/" "$DST/"

  SKILL_MD="$DST/SKILL.md"
  if [ -f "$SKILL_MD" ]; then
    TMP="$(mktemp /tmp/codex-skill-sync.XXXXXX)"
    sed '/^allowed-tools:/d;/^disable-model-invocation:/d;/^user-invocable:/d' "$SKILL_MD" > "$TMP"
    cat "$TMP" > "$SKILL_MD"
    rm -f "$TMP"
  fi

  echo "[ok] Synced: $SKILL"
done
