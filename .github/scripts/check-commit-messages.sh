#!/bin/sh
# Runs .githooks/commit-msg on every commit in <base>..<head> so CI enforces
# the same message rules as the local hook (spec/09-git-workflow.md §6, §10).
# Usage: check-commit-messages.sh <base> <head>
set -eu

base="$1"
head="$2"
hook="$(git rev-parse --show-toplevel)/.githooks/commit-msg"
msg=$(mktemp)
trap 'rm -f "$msg"' EXIT

failed=0
count=0
for sha in $(git rev-list --reverse "$base..$head"); do
  count=$((count + 1))
  git log -1 --format=%B "$sha" > "$msg"
  subject=$(git log -1 --format='%h %s' "$sha")
  if out=$(sh "$hook" "$msg" 2>&1); then
    echo "ok   $subject"
  else
    echo "FAIL $subject"
    echo "     $out"
    failed=1
  fi
done

echo "checked $count commit(s) in $base..$head"
exit $failed
