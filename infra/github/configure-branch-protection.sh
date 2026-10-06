#!/usr/bin/env bash
# prompt 28: "Branch protection + required checks." Branch protection is a GitHub
# repo *setting*, not a file GitHub reads from the repo automatically (unlike
# CODEOWNERS) — this script is the as-code artifact: run it once, by a repo admin,
# against a real GitHub repo, and it configures the exact same protection every
# time (idempotent — the API call is a full PUT, safe to re-run).
#
# This repo has no git remote configured yet (confirmed via `git remote -v` —
# nothing has been pushed anywhere), so this has never actually been run. Requires
# the GitHub CLI (`gh`, already used elsewhere in this project's own tooling
# instructions) authenticated with repo-admin permissions.
#
# Usage: ./configure-branch-protection.sh <owner>/<repo> [branch]
set -euo pipefail

REPO="${1:?Usage: $0 <owner>/<repo> [branch]}"
BRANCH="${2:-main}"

# Required = a real correctness check, never expected to be flaky. Excludes the
# optional load-test job deliberately: p95-latency thresholds measured on a
# shared, noisy GitHub-hosted runner are inherently noisier than functional
# correctness — making it a hard merge-blocker risks blocking valid PRs on CI
# infrastructure jitter, not a real regression (see prompt 26's own "alerts
# actionable, not noisy" principle, same reasoning applied here to a merge gate
# instead of a paging alert). It still runs and is visible on every PR, just not
# required. CodeQL is intentionally not listed here: this repository uses
# GitHub's repository-level default setup, whose generated checks are managed by
# GitHub rather than by a checked-in workflow. Keeping an advanced CodeQL
# workflow alongside default setup causes SARIF uploads to fail with:
# "CodeQL analyses from advanced configurations cannot be processed when the
# default setup is enabled."
# The backend workflow has no frontend, E2E, or web-container checks because
# those projects are maintained outside this repository.
REQUIRED_CHECKS=(
  "Pipeline validation — observability, k8s manifests, workflow config"
  "Backend — build, unit tests, coverage gate"
  "Docker — API lint and image build"
)

CONTEXTS_JSON=$(printf '%s\n' "${REQUIRED_CHECKS[@]}" | jq -R . | jq -s .)

echo "Configuring branch protection on $REPO@$BRANCH..."
echo "Required checks:"
printf '  - %s\n' "${REQUIRED_CHECKS[@]}"

gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "repos/$REPO/branches/$BRANCH/protection" \
  --input - <<EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": $CONTEXTS_JSON
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

echo "Done. Verify at https://github.com/$REPO/settings/branches"
