#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <source-skills-dir> <skill-name> [skill-name ...]" >&2
  echo "Example: $0 ../problem-solving/.codex/skills 1h-agile-phase sprint-start" >&2
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
  DST=".claude/skills/$SKILL"

  if [ ! -d "$SRC" ]; then
    echo "[skip] Missing source skill: $SRC" >&2
    continue
  fi

  mkdir -p "$DST"
  rsync -a "$SRC/" "$DST/"

  SKILL_MD="$DST/SKILL.md"
  if [ -f "$SKILL_MD" ]; then
    TMP1="$(mktemp /tmp/claude-skill-sync.XXXXXX)"
    TMP2="$(mktemp /tmp/claude-skill-sync.XXXXXX)"

    sed '/^allowed-tools:/d;/^disable-model-invocation:/d;/^user-invocable:/d' "$SKILL_MD" > "$TMP1"

    awk '
      BEGIN { sep = 0 }
      /^---$/ {
        sep++
        if (sep == 2) {
          print "allowed-tools: Read,Write,Edit,Glob,Grep,Bash"
          print "disable-model-invocation: false"
          print "user-invocable: true"
        }
        print
        next
      }
      { print }
    ' "$TMP1" > "$TMP2"

    cat "$TMP2" > "$SKILL_MD"
    rm -f "$TMP1" "$TMP2"
  fi

  echo "[ok] Synced: $SKILL"
done

