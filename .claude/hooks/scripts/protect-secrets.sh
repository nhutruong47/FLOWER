#!/usr/bin/env bash
# protect-secrets.sh: Claude Code PreToolUse hook for FlowerChain.
#
# Keeps agents away from secret files (.env files and key stores):
#   - File tools (Read, Edit, Write, MultiEdit, NotebookEdit, Grep, Glob) aimed at a secret
#     file are BLOCKED. Exit 2; the reason on stderr is shown to Claude.
#   - Shell tools (Bash, PowerShell) whose input mentions a .env file make Claude Code
#     ASK the human first (JSON permissionDecision "ask").
#   - Everything else: exit 0, and the normal permission rules apply.
# Allowed templates: .env.example, .env.sample, .env.template
#
# This is a second layer of defence. The first is permissions.deny in .claude/settings.json.
# Text matching cannot catch every way a shell command could read a file.
# Needs only bash, sed, grep and tr (no jq).
# Self-test: bash .claude/hooks/scripts/test-protect-secrets.sh

set -u

input="$(cat)"

# Print the first string value of a JSON key. Good enough for tool names and paths,
# which contain no escaped quotes.
json_field() {
  printf '%s' "$input" | sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
}

is_secret_name() {
  case "$1" in
    .env.example|.env.sample|.env.template) return 1 ;;
    .env|.env.*) return 0 ;;
    *.pem|*.key|*.p12|*.jks) return 0 ;;
  esac
  return 1
}

block() {
  echo "Blocked by .claude/hooks/scripts/protect-secrets.sh: $1 Agents must not read or write secret files. Ask the human to handle it, or use a .env.example file with placeholder values." >&2
  exit 2
}

tool="$(json_field tool_name)"

case "$tool" in
  Read|Edit|Write|MultiEdit|NotebookEdit|Grep|Glob)
    for key in file_path notebook_path path; do
      value="$(json_field "$key")"
      [ -n "$value" ] || continue
      value="$(printf '%s' "$value" | tr '\\' '/')"
      name="${value##*/}"
      if is_secret_name "$name"; then
        block "'$name' is a secret file."
      fi
    done
    if [ "$tool" = "Grep" ]; then
      filter="$(json_field glob)"
      case "$filter" in
        *.env.example*|*.env.sample*|*.env.template*) ;;
        *.env*|*.pem*|*.key|*.p12*|*.jks*) block "the Grep filter '$filter' would search secret files." ;;
      esac
    fi
    ;;
  Bash|PowerShell)
    # Turn JSON escapes (\n, \r, \t) into spaces, split into file-name-like tokens,
    # and keep tokens that are a .env file name. Templates are not secrets.
    hit="$(printf '%s' "$input" \
      | sed 's/\\[nrt]/ /g' \
      | tr -c 'A-Za-z0-9_.-' '\n' \
      | grep -E '^\.env(\.[A-Za-z0-9_-]*)*$' \
      | grep -vE '^\.env\.(example|sample|template)$' \
      | head -n 1)"
    if [ -n "$hit" ]; then
      printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"This command mentions the secret file %s. Approve only if it cannot print or change secret values (protect-secrets.sh)."}}\n' "$hit"
      exit 0
    fi
    ;;
esac

exit 0
