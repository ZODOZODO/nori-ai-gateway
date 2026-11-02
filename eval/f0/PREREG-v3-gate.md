# F0 정식 사전등록 v3-gate — 후보 c(v3 정리 프롬프트 + 서버 구조화 출력 제약), R=5

## 0. 등록 상태 — **초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **정식(purpose=GATE)** 이다. `f0-prereg-v3` 의 GATE 등록은 `parameters.repeats=5` 만 유효하다(그 밖 값은 `PREREG_INVALID`, 실제 명령 `--repeats` 가 다르면 `ARGUMENT_NOT_REGISTERED` — 모두 endpoint 호출 전 거부).
- 입력·예산·스키마는 진단 c(`PREREG-v3-diag-c.md`)와 바이트까지 같다: 원래 골든 22문항(`eval/golden/tc-f0-v2.jsonl`)·경계 9케이스·fixture v2·v3 프롬프트·`responseFormat=JSON_SCHEMA_V1`·c 입력 audit(`eval/f0/v3/input-audit-v3-c.json`)·입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096. 런타임 캡처 디렉터리만 별도(`eval/f0/chat-template/v3-gate`)다.
- 정식 분모는 원래 22문항 + 경계 9케이스다. 개발 회귀 등록(`PREREG-v3-regression.md`)의 추가 문항·결과와 진단 a/b/c 결과는 이 분모·판정에 넣지 않는다.
- 판정은 기존 F0 규칙 그대로다(`f0-judge-v2`, 3축 정확도 A↔B 상대 비교·동률 B 유지, AC-37-OUT-* 경계 계수+완결 생성, AC-38-F0, 지연 예산). 정확도 새 절대 최소 임계는 두지 않는다.
- v1/v2 등록 문서·입력·측정 결과·보고서·증거는 바꾸지 않는다. 등록 commit(C5)과 `registeredCommit` 기록 commit(C6)은 아직 승인 전이다. 그 전에는 측정 명령이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다.
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-02 작업 트리 실제 바이트로 계산했다. C5 직전 다시 대조한다.

## 1. 착수 조건(이 조건 전에는 이 등록으로 blind 정식 측정을 하지 않는다)

1. 진단 a·b·c 와 개발 회귀가 모두 exit 0·`complete=true` 로 끝났다. b·c 에서 서버 `response_format` 거부(exit 5) 0.
2. 진단 c 의 원문 없는 `diagnostic-report.md` 에서 v2 회차 `20261102T003840Z` 실패 원인이 해소됐다. 판단에는 기존 oracle 만 쓴다.
   - B·A 모두 판정 `errorCode=QREF_INVALID` 0, 후보 0 문항의 `qRef.kind=ID_OUT_OF_RANGE` 0(스키마 실제 적용).
   - AC-38 문항(G08)이 A·B 모두 `t=INC`(UNS 0) — 기존 AC-38-F0 조건.
   - 경계 9케이스 boundary-gen 이 기존 oracle(완결 JSON·스키마·resolved 인자 정확 일치·≤192)로 모두 통과.
3. 개발 회귀 30문항 진단 보고서를 root 가 원문 없는 근거로 검토했다. 신규 8문항의 실패 축은 기록·사용자 보고하되 정식 분모에 넣지 않는다. 조건 1·2 를 충족하고 새 업무 결정·제품 지원 범위 변경이 없으면 이미 승인된 P03 범위 안에서 후속 정식 측정을 진행하며, 정식 측정 자체를 위한 별도 사용자 재승인은 요구하지 않는다.
4. 현재 대화의 P03 실행 승인과 별도로 필요한 C5/C6 로컬 commit 승인 범위 안에서 실행하고, C5/C6 등록 commit 뒤 `f0 prereg-check` 가 `PREREG_VERIFIED` 다. C5/C6 는 기존 C3/C4 허용에 포함되지 않으므로 아직 승인이 필요하다.

- 하나라도 충족하지 않으면 이 등록을 실행하지 않는다. 프롬프트·스키마 보완은 새 사전등록 파일로 하며 이 초안을 고쳐 재사용하지 않는다(입력이 바뀌면 `PREREG_INPUT_CHANGED` 로 기계 거부).

## 2. 실행 순서(착수 조건 충족 후, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 측정 동안 Jetson 서비스 재시작·모델 로드·pull·관리 API·num_ctx 변경 0. `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0).

0. 환경 검사(값 출력 0): `if (-not (Test-Path -LiteralPath $env:AIGW_JETSON_SSH_KEY) -or [string]::IsNullOrEmpty($env:AIGW_JETSON_TARGET)) { throw 'AIGW_JETSON_ENV_MISSING' }`; `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')`; `$env:AIGW_PARENT_RUN_ID = $runId`(새 부모 회차).
1. 터널(별도 창): `ssh -i $env:AIGW_JETSON_SSH_KEY -o BatchMode=yes -o ConnectTimeout=12 -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434 $env:AIGW_JETSON_TARGET`
2. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-gate.md"` → `PREREG_VERIFIED`
3. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId/preflight.json"` → exit 4 면 중단·NOT_RUN 기록
4. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-gate"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-gate.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/B"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-gate.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/A"`
7. `C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe -I -B eval\py\count_tokens_qref_v2.py --tokenizer eval\f0\tokenizer\qwen3-4b-instruct-2507.tokenizer.json --expect-sha256-file eval\f0\tokenizer\qwen3-4b-instruct-2507.tokenizer.json.sha256 --cases eval\f0\v2\output-boundary-cases.jsonl --out eval\f0\results\$runId\official-count.json`
8. `.\gradlew.bat :gateway-eval:run --args="f0 gguf-count --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --out eval/f0/results/$runId/gguf"`
9. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/boundary"`
10. `.\gradlew.bat :gateway-eval:run --args="f0 usage-probe --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/usage-probe-input-v2.jsonl --out eval/f0/results/$runId/usage.json"`
11. `.\gradlew.bat :gateway-eval:run --args="f0 token-golden --prereg eval/f0/PREREG-v3-gate.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/token-golden-input-v2.jsonl --out eval/f0/results/$runId/token-golden.jsonl"`
12. 터널 종료(본인 창만). 이후 터널 없이 진행한다.
13. `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-gate.md --run-id $runId --results eval/f0/results/$runId --out evidence/F0"`
14. `.\gradlew.bat :gateway-eval:run --args="gate --stage F0 --parent-run-id $runId --spec $env:AIGW_SPEC_FILE --out eval/f0/results/gate-reports/$runId.md"`(회차 파생 보고서를 새 파일로만 쓴다. 기존 `evidence/F0/GATE-REPORT.md`·측정 원시 디렉터리 쓰기 0)

- 이 시점 AC-37 하위는 `SPEC_RECORD` component 가 없어 NOT_RUN 이 정상이다. §14.5 기록 반영·재판정은 Plan P03 9단계(로컬 G-SPEC-HASH (a) 기준)로 별도 진행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "GATE",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-gate",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-c.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3.txt", "sha256": "2772aa56043827619b4975e672c5c45df6ce1a678d4cece77df41b5b96815143"},
    {"path": "eval/f0/v3/prompts/plan-v3.txt", "sha256": "43da76c3811714e204e9f61c737197392671e2266993cd81b30b52fa88cc7982"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-c.json", "sha256": "b56e8a1110a401fad232c6082597be8eb8960fca05048e947a5b8e2ed8585d43"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "55d61c55333b7ecc210d7d45fc5fdd1e7e213c3847511b69343258283b6bf408",
  "parameters": {
    "generationModel": "qwen3:4b-instruct",
    "embeddingModel": "bge-m3",
    "repeats": 5,
    "temperature": 0,
    "maxTokens": {"classify": 192, "plan": 256},
    "inputLimitTokens": {"classify": 3072, "plan": 3072},
    "contextTokens": 4096,
    "requestTimeoutMs": 120000,
    "outputLimitTokens": 192,
    "terminationMarginTokens": 1,
    "promptSha256": {"classify": "2772aa56043827619b4975e672c5c45df6ce1a678d4cece77df41b5b96815143", "plan": "43da76c3811714e204e9f61c737197392671e2266993cd81b30b52fa88cc7982"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1"
  }
}
```
