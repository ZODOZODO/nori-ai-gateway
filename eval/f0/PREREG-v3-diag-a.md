# F0 진단 사전등록 v3-diag-a — 현 v2 프롬프트 + 비제약(진단 기준선)

## 0. 등록 상태 — **초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). 따라서 F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `eval/f0/PREREG.md`·v2 `eval/f0/PREREG-v2.md`, v1/v2 등록 입력·측정 결과·보고서·증거를 바꾸지 않는다. v2 등록 입력은 바이트 그대로 참조만 한다.
- 등록 commit(C5: 등록 대상·하네스 코드)과 이 문서의 `registeredCommit` 기록 commit(C6)은 아직 승인 전이며 만들지 않았다. 그 전에는 측정 명령이 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다.
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-02 작업 트리 실제 바이트로 계산했다. C5 직전 다시 대조한다.

## 1. 진단 설계(세 등록 a·b·c 공통)

- 같은 승인 qRef 계약, 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·회차 R=1 에서 한 요인씩만 바꾼다.

| 등록 | 프롬프트 | 서버 출력 제약(`responseFormat`) | 대조 목적 |
|---|---|---|---|
| **a(이 문서)** | v2 등록 프롬프트 원문(`classify-v2`·`plan-v2`, Path A의 "160토큰" 문구 포함 그대로) | `NONE`(v2 와 같은 요청 body) | v2 회차 `20261102T003840Z` 와 같은 조건의 진단 기준선. 새 시도별 진단(`attemptDiagnostics`)으로 원문 없이 오류를 세분 |
| b | a 와 같음 | `JSON_SCHEMA_V1` | a↔b = 서버 구조화 출력 단독 효과 |
| c | v3 정리 프롬프트(`classify-v3`·`plan-v3`, 256 정정) | `JSON_SCHEMA_V1` | b↔c = 프롬프트 정리 단독 효과 |

- 판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 은 v2 와 같다. 진단은 판정을 바꾸지 않는다.
- R=1 진단 결과로 정확도·지연 합격을 주장하지 않는다. 최종 정식 판정은 별도 GATE 등록(`PREREG-v3-gate.md`, R=5 기계 검사)·새 회차로 기존 F0 규칙을 적용한다. 개발 회귀 등록(`PREREG-v3-regression.md`)의 추가 문항·결과는 정식 분모·판정에 넣지 않는다.

## 2. 실행 순서(승인 후 — C5/C6 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 진단 묶음(a→b→c→회귀) 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-a"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-a"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-a.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-a.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-a/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-a.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-a"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-a.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-a --out eval/f0/results/$runId-a/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-a.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/plan-v2.txt --repeats 1 --run-id $runId-a --out eval/f0/results/$runId-a/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-a.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-a --out eval/f0/results/$runId-a/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-a.md --run-id $runId-a --results eval/f0/results/$runId-a"`(`--out` 없음 → `eval/f0/results/$runId-a/diagnostic-report.md` 만, F0 leaf 증거 0)

- 순서: a → b → c → 회귀(`PREREG-v3-regression.md`). a 는 v2 회차 `20261102T003840Z` 와 같은 요청 조건의 원문 없는 진단 기준선이다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-a",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v2/prompts/classify-v2.txt",
    "planPrompt": "eval/f0/v2/prompts/plan-v2.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v2/input-audit-v2.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v2/prompts/classify-v2.txt", "sha256": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217"},
    {"path": "eval/f0/v2/prompts/plan-v2.txt", "sha256": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v2/input-audit-v2.json", "sha256": "c30c3b6061c37ea394396137579991694df5dbfff5db8a7bf1cedb624e358ce0"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "ef72cc4297f2dcac06db23a38158c96ad1f8a6f3d61e439f3d488bfae5bfa7b8",
  "parameters": {
    "generationModel": "qwen3:4b-instruct",
    "embeddingModel": "bge-m3",
    "repeats": 1,
    "temperature": 0,
    "maxTokens": {"classify": 192, "plan": 256},
    "inputLimitTokens": {"classify": 3072, "plan": 3072},
    "contextTokens": 4096,
    "requestTimeoutMs": 120000,
    "outputLimitTokens": 192,
    "terminationMarginTokens": 1,
    "promptSha256": {"classify": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217", "plan": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "NONE"
  }
}
```
