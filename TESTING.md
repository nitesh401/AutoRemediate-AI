# AutoRemediate AI - Testing Guide

## 1. Prerequisites

- Java 21, Maven 3.9+, Git on `PATH`
- Internet access for the first Maven run (dependencies)
- For the live demo only: Docker (SonarQube), Snyk CLI + token, Anthropic API key

## 2. Unit tests (no network, no API keys)

```bash
mvn test                                   # all tests
mvn -Dtest=VerificationEngineTest test     # one class
mvn -Dtest='PomVersionUpdaterTest,PatchApplierTest' test
```

`CommandRunnerTest`, `GitToolTest` and `RepositoryToolTest` use the real `git` binary and temp directories;
they need `git` installed but touch nothing outside the temp dir.

| Test class | What it proves |
|---|---|
| `VerificationEngineTest` | Safety gate passes only when target is fixed, build/tests green, test count not lower, no new Sonar/Snyk findings, scanners still working; line shifts are not "new issues"; Snyk version change ignored |
| `PatchApplierTest` | Edit must match exactly once; ambiguous/missing match writes nothing; CRLF preserved; path escape refused |
| `PomVersionUpdaterTest` | Upgrades only the target dependency; handles dedicated property; refuses shared property, managed version, unknown dependency |
| `IssueClassifierTest` | Allow-listed rules = HIGH; security/sensitive paths = human review; major/transitive Snyk not automated; selection is HIGH only, by severity |
| `RemediationPolicyTest` | Edit/patch limits: `.java` only for Sonar, `pom.xml` only for Snyk, max files/lines, protected paths |
| `ParsersTest` | Sonar `/api/issues/search` and `snyk test --json` normalisation (direct vs transitive, fix version, duplicates, project arrays) |
| `TestToolTest` | Surefire/Failsafe XML aggregation; unreadable report is never a pass |
| `CommandRunnerTest` | Executable allow-list (bash, rm, empty rejected); output captured; secrets redacted |
| `GitToolTest` | Clone, `ai/remediation/` branch, stats ignore `target/`, revert to checkpoint, commit; origin `main` untouched; protected-branch push and option-injection refused |
| `RepositoryToolTest` | Metadata, module discovery, file search, suffix path resolution, path-escape and `.git` access blocked |
| `RepositoryUrlValidatorTest` | https/ssh accepted; `--option`, `ext::`, http, credentials-in-URL, local paths (when disabled) rejected |
| `PromptTemplatesTest` | All versioned prompts load; substitution is single-pass (prompt-injection safe); missing variable fails; system prompt marks repo content untrusted |
| `JobStatusTest` | State-machine transitions; illegal shortcuts forbidden |
| `VersionUtilTest`, `JsonExtractorTest` | Version comparison/major detection; JSON extraction from LLM replies |

Reports: `target/surefire-reports/`.

## 3. Scan-only smoke test (no Sonar, Snyk or LLM)

Proves clone, isolation, baseline build and reporting.

```bash
REPO=$(scripts/init-demo-repo.sh)
mvn spring-boot:run &
curl -s -X POST localhost:8080/api/remediation -H 'Content-Type: application/json' \
  -d "{\"repositoryUrl\":\"$REPO\"}"
curl -s localhost:8080/api/remediation/<jobId>
curl -s localhost:8080/api/remediation/<jobId>/report
```

Expected: status `COMPLETED`, result `NO_FINDINGS`, build `PASS`, report written to `work/reports/<jobId>/`.
The demo repo has 2 passing tests, so the baseline shows `2 run, 0 failed`.

## 4. Full end-to-end demo

Environment:

```bash
docker run -d -p 9000:9000 sonarqube:lts-community     # create a token (needs permission to create projects)
export SONAR_ENABLED=true SONAR_HOST_URL=http://localhost:9000 SONAR_TOKEN=...
export SNYK_ENABLED=true SNYK_TOKEN=...
export ANTHROPIC_API_KEY=... AI_MODEL=claude-sonnet-5-5
mvn spring-boot:run
```

Run `scripts/start-demo-job.sh "$(scripts/init-demo-repo.sh)" 5` and poll the job.

Expected (rule names depend on your Sonar profile; counts may vary slightly):

| Finding | Expected disposition |
|---|---|
| `java:S1128` unused import `ArrayList` | FIXED (1 line removed) |
| `java:S1481` unused `unusedCounter` | FIXED |
| `java:S3655` `Optional.get()` without check | FIXED, or `FAILED_TO_REMEDIATE` / `SKIPPED_NOT_AUTOMATABLE` if the model's patch breaks tests or is unsafe |
| Snyk `commons-text` 1.9 -> 1.10.0 | FIXED (`pom.xml` only) |

Then inspect `work/jobs/<jobId>/repo`:

```bash
git log --oneline            # one commit per verified fix, on ai/remediation/<jobId>
git diff main..HEAD          # minimal diffs only
```

The original repo (`$REPO`) must be unchanged. The report must show `newIssues = 0` and Sonar/Snyk counts going down.

## 5. Safety / failure-injection checks

| Scenario | How | Expected |
|---|---|---|
| Baseline already broken | Break a test in the demo repo before the job | Job `FAILED`, `BASELINE_FAILURE`, no changes attempted |
| No LLM key | Unset `ANTHROPIC_API_KEY` | Warning "scan-only mode", findings `SKIPPED_LLM_UNAVAILABLE`, no commits |
| Fix breaks tests | Hard to force with a real model; covered by `VerificationEngineTest` | Attempt `REJECTED_BY_GATE`, tree reset, up to 3 attempts, then `FAILED_TO_REMEDIATE`, no PR |
| Bad repository URL | POST `--upload-pack=x`, `http://...`, `ext::sh` | HTTP 400 |
| API key enforcement | Start with `AUTOREMEDIATE_API_KEY=k`, call without header | HTTP 401 |
| Push guard | `git.push-enabled=true`, no token | PR status `SKIPPED_NO_TOKEN`; `GitToolTest` shows `main` push refused |
| Secret leakage | `grep -r "$SONAR_TOKEN" work/` | No matches |

## 6. Definition-of-done checklist (from the design)

- [ ] Clone + isolated branch; main never modified
- [ ] Baseline build/tests recorded (`BASELINE_FAILURE` handled)
- [ ] Sonar and Snyk parsed; findings classified; only HIGH_CONFIDENCE modified
- [ ] Patch minimal and applied; build, tests, rescans run; before/after compared; new findings detected
- [ ] Max 3 attempts, then revert and report; no PR on failure
- [ ] Markdown + JSON report and audit log; PR only after gate passes; never auto-merged

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `GitToolTest` fails to init | `git` too old for `init -b`; use Git 2.28+ |
| Sonar scan fails with 401/403 | Token lacks "Execute Analysis" or project-creation permission |
| Sonar scan times out | Raise `remediation.sonar.task-wait-seconds` |
| `snyk` not found | Install the CLI (`npm i -g snyk`) or use the Docker image |
| Job stays at `SCANNING` | First Maven/Sonar run is downloading plugins; check `audit.log` |
| Fix keeps being rejected | Read `attempts` in `report.json`; the gate reasons say exactly why |
| Never run in CI as-is | The agent executes the target repo's build/tests; use a sandbox container |
