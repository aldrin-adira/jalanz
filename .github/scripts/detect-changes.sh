#!/bin/sh
# Decides which CI jobs run (spec/09-git-workflow.md §7.1). Jobs are
# path-filtered to keep CI lean (spec/11-zero-cost-testing.md §7). An area
# runs only when its project exists and either its files changed since the
# merge base of <base> and <head>, or <run_all> is "true" (pushes to develop
# and main). Writes area=true|false lines to stdout for $GITHUB_OUTPUT.
# Usage: detect-changes.sh <base> <head> <run_all>
set -eu

base="$1"
head="$2"
run_all="$3"
changed=$(git diff --name-only "$base...$head")

# CI config changes re-run every existing project.
if echo "$changed" | grep -q '^\.github/'; then
  run_all=true
fi

# area <name> <marker file that proves the project exists> <path regex>
area() {
  name="$1"; marker="$2"; paths="$3"
  run=false
  if [ -f "$marker" ]; then
    if [ "$run_all" = "true" ] || echo "$changed" | grep -Eq "$paths"; then
      run=true
    fi
  fi
  echo "$name=$run"
}

# The toll engine is shared by app and server, so fixtures feed both.
area android   android/gradlew           '^(android|fixtures)/'
area backend   backend/gradlew           '^(backend|fixtures)/'
area web_share web-share/package.json    '^web-share/'
area pipelines pipelines/pyproject.toml  '^(pipelines|fixtures)/'
area infra     infra/compose.yaml        '^infra/'
