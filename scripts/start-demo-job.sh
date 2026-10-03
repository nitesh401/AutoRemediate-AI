#!/usr/bin/env bash
# Usage: scripts/start-demo-job.sh /absolute/path/or/https-url [maxIssues]
set -euo pipefail
REPO="${1:?repository path or URL}"
MAX="${2:-5}"
HDR=()
[ -n "${AUTOREMEDIATE_API_KEY:-}" ] && HDR=(-H "X-API-Key: ${AUTOREMEDIATE_API_KEY}")
curl -s -X POST http://localhost:8080/api/remediation "${HDR[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"repositoryUrl\":\"${REPO}\",\"maxIssues\":${MAX},\"createPullRequest\":false}"
echo
