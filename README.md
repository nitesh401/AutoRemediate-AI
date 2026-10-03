# AutoRemediate AI

Autonomous remediation agent for Java/Maven repositories: it finds Sonar and Snyk findings, fixes the
high-confidence ones with minimal patches, **proves** the result with deterministic tools, and produces an
auditable report plus an optional draft pull request. It never touches your main branch.

> **LLM proposes. Deterministic tools verify. Human approves.**

The full design is in [`docs/design.md`](docs/design.md). This repository implements all phases of the V1 plan.

## What it does

```
clone (isolated) -> branch ai/remediation/<job> -> baseline build+tests -> Sonar scan -> Snyk scan
 -> classify -> pick HIGH_CONFIDENCE findings -> for each finding (max 3 attempts):
      context -> LLM plan -> validated search/replace patch -> policy check -> LLM review
      -> mvn clean verify -> test reports -> Sonar rescan -> Snyk rescan -> SAFETY GATE
      PASS: commit on the AI branch      FAIL: diagnose, reset to checkpoint, retry / revert
 -> Markdown + JSON report -> optional push + draft PR (never merged automatically)
```

**Safety gate** (`VerificationEngine`, no LLM involved). A fix is accepted only if: the baseline was green, the build
passes, tests pass with no drop in test count, the target finding is gone, there is no new Sonar issue, no new Snyk
vulnerability, and every scanner that worked on the baseline also worked on the re-scan.

## Requirements

Java 21, Maven 3.9+, Git. For real scans: a SonarQube/SonarCloud server + token, Snyk CLI + token.
For fixes: an Anthropic API key (`AI_MODEL` is configurable; the LLM client is behind the `LlmClient` interface).

## Run

```bash
cp .env.example .env        # fill in values, then: set -a; source .env; set +a
mvn test                    # unit tests
mvn spring-boot:run         # or: docker compose up --build
```

## Try the demo (3 Sonar findings + 1 vulnerable dependency)

```bash
REPO=$(scripts/init-demo-repo.sh)          # fresh git repo from demo/sample-app
export SONAR_ENABLED=true SNYK_ENABLED=true # plus SONAR_TOKEN / SNYK_TOKEN / ANTHROPIC_API_KEY
mvn spring-boot:run &                       # in another shell:
scripts/start-demo-job.sh "$REPO" 5
curl localhost:8080/api/remediation/<jobId>                 # status
curl "localhost:8080/api/remediation/<jobId>/report"        # Markdown report (?format=json)
curl localhost:8080/api/remediation/<jobId>/audit           # audit trail
```

Without scanners/LLM configured the job still runs the baseline build and reports honestly (scan-only mode).
Fixed code lives in `work/jobs/<jobId>/repo` on branch `ai/remediation/<jobId>`, one commit per verified fix.

## API

| Method | Path | |
|---|---|---|
| POST | `/api/remediation` | `{"repositoryUrl","branch","maxIssues","createPullRequest"}` -> `{"jobId","status":"STARTED"}` |
| GET | `/api/remediation/{jobId}` | status, fixed counts, build/tests, new issues, PR status |
| GET | `/api/remediation/{jobId}/report?format=markdown\|json` | auditable report |
| GET | `/api/remediation/{jobId}/audit` | audit trail (secrets redacted) |

Set `AUTOREMEDIATE_API_KEY` to require header `X-API-Key` on `/api/**`.

## V1 scope (by design)

Automated: Sonar `S1128, S1481, S1068, S3655, S1155, S1612, S125` (configurable in `remediation.policy`) and
direct, same-major Snyk dependency upgrades to the scanner-recommended version. Everything else (security findings,
sensitive paths like `auth`/`security`, transitive/major upgrades, managed versions) is reported as needing human review.

## Safety model

- Fresh clone + `ai/remediation/` branch only; pushing to `main/master/develop/release` is refused; no auto-merge; PRs are drafts.
- The LLM only returns JSON. Nothing it writes is ever executed: all processes go through `CommandRunner`
  (executable allow-list: mvn/mvnw/git/snyk, no shell, timeouts, scrubbed environment so build/test code never sees your keys).
- Repository content is untrusted data (wrapped in tags + system-prompt rule); edits must match the original file exactly once,
  touch only `.java` files, and stay within size limits.
- Sonar runs against a scratch project (`<key>-autoremediate`), so your real project history is not overwritten.
- Secrets come from env vars, are redacted from logs/reports, and are never sent to the LLM.
- **Run it in a container/sandbox**: it executes the target repository's build and tests. `docker-compose.yml` shows resource limits.

## Layout

`api/` REST, `orchestration/` job state machine + orchestrator, `tools/` git/maven/sonar/snyk/repo/PR tools,
`agent/` LLM client + remediation/review/verification agents, `remediation/` classifier, policy, patching,
`verification/` safety gate + comparators, `report/`, `security/`, `src/main/resources/prompts/` versioned prompts (recorded per job).

## Known limits / next steps

- Single-module Sonar file paths are resolved by suffix for multi-module repos; per-module Sonar keys are not modelled.
- Test selection is "run the full build" (no targeted tests yet); jobs are in memory (lost on restart); GitHub only for PRs.
- `ai.temperature` is optional and omitted by default because some models reject sampling parameters.
- Phase 7 items still open: sandbox orchestration (K8s/ephemeral containers), persistent queue, RBAC, rate limits.
