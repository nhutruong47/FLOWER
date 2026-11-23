#!/usr/bin/env bash
# Self-test for protect-secrets.sh. It runs the hook on sample PreToolUse payloads
# and checks the decision for each one.
# Run from anywhere: bash .claude/hooks/scripts/test-protect-secrets.sh
# Exit code 0 means every case passed.

set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
hook="$here/protect-secrets.sh"
pass=0
fail=0

# expect <case name> <block|ask|allow> <PreToolUse JSON payload>
expect() {
  local name="$1" want="$2" json="$3" out code got
  out="$(printf '%s' "$json" | bash "$hook" 2>/dev/null)"
  code=$?
  if [ "$code" -eq 2 ]; then
    got=block
  elif [ "$code" -eq 0 ] && printf '%s' "$out" | grep -q '"permissionDecision":"ask"'; then
    got=ask
  elif [ "$code" -eq 0 ] && [ -z "$out" ]; then
    got=allow
  else
    got="unexpected(exit=$code)"
  fi
  if [ "$got" = "$want" ]; then
    pass=$((pass + 1)); printf 'PASS  %-48s %s\n' "$name" "$got"
  else
    fail=$((fail + 1)); printf 'FAIL  %-48s want=%s got=%s\n' "$name" "$want" "$got"
  fi
}

P='"session_id":"t","cwd":"C:\\proj","hook_event_name":"PreToolUse"'

# File tools: secret files are blocked, templates and normal files are allowed
expect "Read .env (Windows path)"            block '{'"$P"',"tool_name":"Read","tool_input":{"file_path":"C:\\proj\\.env"}}'
expect "Read backend/.env.local (POSIX)"     block '{'"$P"',"tool_name":"Read","tool_input":{"file_path":"/proj/backend/.env.local"}}'
expect "Read .env.production"                block '{'"$P"',"tool_name":"Read","tool_input":{"file_path":"C:\\proj\\.env.production"}}'
expect "Read .env.example"                   allow '{'"$P"',"tool_name":"Read","tool_input":{"file_path":"C:\\proj\\.env.example"}}'
expect "Read src/app.ts"                     allow '{'"$P"',"tool_name":"Read","tool_input":{"file_path":"C:\\proj\\src\\app.ts"}}'
expect "Edit config/server.key"              block '{'"$P"',"tool_name":"Edit","tool_input":{"file_path":"C:\\proj\\config\\server.key","old_string":"a","new_string":"b"}}'
expect "Write keystore.jks"                  block '{'"$P"',"tool_name":"Write","tool_input":{"file_path":"/proj/keystore.jks","content":"x"}}'
expect "Write README mentioning .env"        allow '{'"$P"',"tool_name":"Write","tool_input":{"file_path":"C:\\proj\\README.md","content":"Copy .env.example to .env and fill \"file_path\": .env"}}'
expect "Write .env.example"                  allow '{'"$P"',"tool_name":"Write","tool_input":{"file_path":"C:\\proj\\.env.example","content":"DB_URL=changeme"}}'
expect "NotebookEdit .env"                   block '{'"$P"',"tool_name":"NotebookEdit","tool_input":{"notebook_path":"/proj/.env","new_source":"x"}}'
expect "Grep in .env"                        block '{'"$P"',"tool_name":"Grep","tool_input":{"pattern":"PASSWORD","path":"C:\\proj\\.env"}}'
expect "Grep with glob .env*"                block '{'"$P"',"tool_name":"Grep","tool_input":{"pattern":"PASSWORD","glob":".env*"}}'
expect "Grep with glob *.pem"                block '{'"$P"',"tool_name":"Grep","tool_input":{"pattern":"BEGIN","glob":"*.pem"}}'
expect "Grep with glob *.ts"                 allow '{'"$P"',"tool_name":"Grep","tool_input":{"pattern":"branchId","glob":"*.ts","path":"C:\\proj\\frontend"}}'
expect "Glob listing **/.env* (names only)"  allow '{'"$P"',"tool_name":"Glob","tool_input":{"pattern":"**/.env*"}}'

# Shell tools: a command mentioning a .env file asks the human; everything else is allowed
expect "Bash cat .env"                       ask   '{'"$P"',"tool_name":"Bash","tool_input":{"command":"cat .env","description":"x"}}'
expect "Bash type C:\\proj\\.env.local"      ask   '{'"$P"',"tool_name":"Bash","tool_input":{"command":"type C:\\proj\\.env.local"}}'
expect "Bash cp .env.example .env"           ask   '{'"$P"',"tool_name":"Bash","tool_input":{"command":"cp .env.example .env"}}'
expect "Bash heredoc line starting .env"     ask   '{'"$P"',"tool_name":"Bash","tool_input":{"command":"cat <<EOF\n.env\nEOF"}}'
expect "Bash cat ./.env (path prefix)"       ask   '{'"$P"',"tool_name":"Bash","tool_input":{"command":"cat ./.env"}}'
expect "Bash cat .env.example"               allow '{'"$P"',"tool_name":"Bash","tool_input":{"command":"cat .env.example"}}'
expect "Bash process.env / import.meta.env"  allow '{'"$P"',"tool_name":"Bash","tool_input":{"command":"node -e \"console.log(process.env.X, import.meta.env)\""}}'
expect "Bash direnv .envrc"                  allow '{'"$P"',"tool_name":"Bash","tool_input":{"command":"direnv allow .envrc"}}'
expect "Bash git status"                     allow '{'"$P"',"tool_name":"Bash","tool_input":{"command":"git status"}}'
expect "PowerShell Get-Content .env.prod"    ask   '{'"$P"',"tool_name":"PowerShell","tool_input":{"command":"Get-Content .env.prod"}}'

# Other tools and bad input never block
expect "WebFetch (other tool)"               allow '{'"$P"',"tool_name":"WebFetch","tool_input":{"url":"https://example.com/.env"}}'
expect "Empty input"                         allow ''

echo "----"
echo "passed: $pass  failed: $fail"
[ "$fail" -eq 0 ]
