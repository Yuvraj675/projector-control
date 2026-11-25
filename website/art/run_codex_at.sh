#!/bin/bash
# Waits until 06:30 (the Codex quota reset), then runs the job; retries every 10 minutes while the usage limit message appears.
cd "$(dirname "$0")"
TARGET=$(date -d '06:30' +%s)
NOW=$(date +%s)
[ $NOW -lt $TARGET ] && sleep $((TARGET - NOW))
for try in 1 2 3 4 5 6; do
  /usr/lib/chatgpt/resources/codex exec --skip-git-repo-check -s workspace-write -C "$PWD" - < prompt-I.md > codex-I.log 2>&1
  if grep -q "usage limit" codex-I.log && [ ! -f raw4/hv-up.png ]; then sleep 600; else break; fi
done
echo "codex-I finished $(date)" >> codex-I.done
