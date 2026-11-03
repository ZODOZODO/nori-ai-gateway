# F0 진단 사전등록 v3-diag-g — 후보 f 의 분류 입력 고정 + g 계획 프롬프트(steps·t 출력 순서 정합) + 서버 구조화 출력 제약

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-{diag-a,diag-b,diag-c,diag-d,diag-e,diag-f,regression,regression-d,regression-f,gate,gate-d,gate-f}.md`, 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 원래 골든 22문항·fixture v2·경계 9케이스·v2 메시지 입력·`classify-v3-f` 는 바이트 그대로 참조만 한다.
- 현재 상태: 등록 대상 commit(C13 예정)과 이 문서의 `registeredCommit` 기록 commit(C14 예정)은 **사용자 미허용**이다. 그 전까지 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 이 초안도 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 진단 설계(후보 g)

- 근본 원인(Devreport §12.19, 진단 c·d·e·f 원문 없는 정제 필드·출력 SHA 근거):
  - ① A G08 `STEPS_INVALID` 2회: f 의 두 시도 출력 SHA 가 같고, 키 정렬 직렬화에 공백 하나가 든 `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC", "to":null}` 과 정확히 같다. `steps` 가 `t` 보다 먼저 생성됐다.
  - ② 하네스는 요청 본문 전체를 키 정렬 canonical JSON 으로 보낸다(`Json.canonicalString`·`OllamaChatClient.send`). 따라서 `response_format` 스키마의 `properties` 도 `eqp, from, qRef, steps, t, to` 순서로 전달된다. c·d·e·f A 출력 중 값으로 복원 가능한 39건은 모두 이 순서였고 `t` 가 앞인 출력은 0건이다.
  - ③ `plan-v3-d`·`plan-v3-f` 는 "t 를 먼저 정하고 steps 는 그 최종 t 에 맞춘다"고 지시하고 스키마를 `t` 부터 보여 준다. 그러나 실제 생성은 `steps` 를 먼저 확정한 뒤 `t` 를 쓴다. c~f 의 A `STEPS_INVALID` 시도 20건 중 17건이 steps/t 불일치다(`NONEMPTY_FOR_NO_TOOL_TASK` 15·`EMPTY_FOR_TOOL_TASK` 2). 나머지 3건은 항목 형식(`ITEM_FORMAT_INVALID`, d·e G17)이다.
  - ④ A G06·G19: c~f 모두 steps 는 `tc.eqp.getOperationalSnapshot`(LQ workflow Tool)인데 뒤에 쓴 t 가 `KQ` 다(G19 d 제외). B 는 같은 문항을 c~f 모두 `LQ` 로 분류했다.
- 후보 g = f 대비 계획 프롬프트 1개만 바꾼다.

| 요인 | f | g(이 문서) |
|---|---|---|
| 분류 프롬프트(B·경계·usage·token) | `classify-v3-f` | `classify-v3-f`(바이트 동일) |
| LLM user 메시지 `context` | `PRESENCE_V1` | `PRESENCE_V1`(동일) |
| 계획 프롬프트(A) | `plan-v3-f` | `plan-v3-g`: f 기반. 출력 스키마를 실제 출력 키 순서(`eqp, from, qRef, steps, t, to`)로 보여 주고 steps 가 t 보다 먼저 나온다고 알린다. steps 를 쓰기 전에 [t 분류]·[소유 시스템 판단]으로 t 를 정하게 하고, t 별 Tool(fixture v2 고정 workflow·SPEC §6.1~§6.3: KQ = `tc.knowledge.search`·`tc.git.searchCode`, LQ = `tc.eqp.getOperationalSnapshot`·`tc.event.getRecent`, INC = `tc.logs.search` 포함, GQ·UNS = `[]`)을 적는다. 뒤에 쓰는 t 는 앞서 쓴 steps 와 맞아야 하며, 재시도 오류 코드 `STEPS_INVALID` 의 뜻을 적는다. [t 분류]·[소유 시스템 판단]·[인자] 문구는 f 와 같다. 정답 값·골든/회귀 문항 문구·자원 catalog·EQP ID 주입 0 |

- 같은 승인 qRef 계약(`qRefCatalog`·`JSON_SCHEMA_V1`), 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회(메시지 그대로)·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 이며 회차는 R=1 이다. 진단은 판정을 바꾸지 않는다. 하네스 코드 변경 0.
- B·경계·usage·token 요청은 f 와 같은 분류 프롬프트·투영·스키마이므로 메시지 바이트가 f 와 같다(아래 audit 에서 B 264·경계 108·usage 2·token 13 행의 id·messagesSha256·계수가 f audit 와 전부 일치). 바뀌는 것은 A 요청뿐이다.
- 입력 audit `eval/f0/v3/input-audit-v3-g.json`: Java `f0 input-audit-render --context-projection PRESENCE_V1`(22문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 651행, `OVER_BUDGET 0`, 최대 A 1791/1830·B 1390/1429·경계 3023/3062·usage 1376·token 2793. 최대 입력+출력 3254.
- R=1 결과로 정확도·지연 합격을 주장하지 않는다. 정식 판정은 별도 `PREREG-v3-gate-g.md`(R=5)로만 한다.

## 2. 실행 순서(C13/C14 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 진단 묶음(g→회귀 g) 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-g"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-g"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-g.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-g.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-g/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-g.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-g"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-g.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-g --out eval/f0/results/$runId-g/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-g.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-g.txt --repeats 1 --run-id $runId-g --out eval/f0/results/$runId-g/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-g.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-g --out eval/f0/results/$runId-g/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-g.md --run-id $runId-g --results eval/f0/results/$runId-g"`(`--out` 없음 → `eval/f0/results/$runId-g/diagnostic-report.md` 만, F0 leaf 증거 0)

- g 다음에는 같은 g 프롬프트·투영·스키마의 개발 회귀 등록(`PREREG-v3-regression-g.md`, 30문항)을 실행한다. 정식 후보 R5(`PREREG-v3-gate-g.md`)는 그 문서 1절의 착수 조건을 충족한 경우에만 실행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-g",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-f.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-g.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-g.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-f.txt", "sha256": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98"},
    {"path": "eval/f0/v3/prompts/plan-v3-g.txt", "sha256": "89852f7252b73b963a23232eb6b4a57a1cbce2ac8e1accef6a3e432c921a2d74"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-g.json", "sha256": "4735ba32a1944ef59df744c5117c6719c3be7806853eb6fdb908d8ed7ad16744"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "4de7eadb9c3fb06f4fd987b08417eaad20aa41637b53daf5b00080ec4cad5597",
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
    "promptSha256": {"classify": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98", "plan": "89852f7252b73b963a23232eb6b4a57a1cbce2ac8e1accef6a3e432c921a2d74"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1",
    "contextProjection": "PRESENCE_V1"
  }
}
```
