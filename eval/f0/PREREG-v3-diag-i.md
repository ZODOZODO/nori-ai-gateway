# F0 진단 사전등록 v3-diag-i — 후보 g 의 입력 고정 + 계획 재시도 세부 코드(STEPS_DETAIL_V1) + i 계획 프롬프트 + 서버 구조화 출력 제약

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = 76baa655f3487c8666398b44b9129b7ac219fc16`(C17)**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-{diag-a,diag-b,diag-c,diag-d,diag-e,diag-f,diag-g,diag-h,regression,regression-d,regression-f,regression-g,regression-h,gate,gate-d,gate-f,gate-g,gate-h}.md`, 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 원래 골든 22문항·fixture v2·경계 9케이스·v2 메시지 입력·`classify-v3-f` 는 바이트 그대로 참조만 한다.
- 등록 근거: 사용자 승인 "C17/C18 허용 후 진행(권장)"(2026-11-03, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C17 `76baa655f3487c8666398b44b9129b7ac219fc16` = 후보 i 등록 대상·plan 프롬프트·입력 audit·재시도 세부 코드 opt-in 하네스 코드·관련 시험·Devreport 정확 15파일(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C18 = 후보 i 사전등록 3문서(`PREREG-v3-{diag-i,regression-i,gate-i}.md`)만 수정해 `registeredCommit`에 C17 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-03 C17 전까지 이 문서는 미등록 초안(C17/C18 사용자 미허용)이었고, 그 동안 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 이 초안도 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 진단 설계(후보 i)

- 근본 원인(Devreport §12.19~§12.22, 원문 없는 정제 필드·출력 SHA 근거):
  - ① A G08 은 f·g·h 모두 두 시도 출력이 같다. g·h 의 출력 SHA 는 `d648833f…b242`(키 정렬·공백 없는 `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC","to":null}`)이고, steps 가 빈 배열이라 `STEPS_INVALID` 2회로 최종 `INVALID` 다(최종 시도 `attemptDiagnostics.steps.toolConsistency=EMPTY_FOR_TOOL_TASK`).
  - ② 계획 프롬프트(f·g)와 계획 스키마 송신 키 순서(h, 서버가 송신 순서를 출력 순서로 따르지 않음)로는 G08 출력이 바뀌지 않았다.
  - ③ 재시도는 원 메시지에 고정 코드 한 줄(`직전 출력이 무효다(오류: STEPS_INVALID). …`, `MessageAssembler.retry`)만 덧붙인다. `STEPS_INVALID` 는 steps 형식 위반과 t·steps 불일치를 함께 뜻하므로 무엇을 고칠지 알려 주지 않는다. 재시도 입력이 초회와 거의 같고 temperature 0 이라 같은 출력이 반복됐다.
- 후보 i = g 대비 계획(A) 요청만 두 가지 바꾼다.

| 요인 | g | i(이 문서) |
|---|---|---|
| 분류 프롬프트(B·경계·usage·token) | `classify-v3-f` | `classify-v3-f`(바이트 동일) |
| LLM user 메시지 `context` | `PRESENCE_V1` | `PRESENCE_V1`(동일) |
| 응답 형식(`parameters.responseFormat`) | `JSON_SCHEMA_V1` | `JSON_SCHEMA_V1`(동일, 요청 바이트 동일) |
| 재시도 힌트(`parameters.retryHint`) | 키 없음(기존 재시도) | `STEPS_DETAIL_V1`: 계획 판정이 `STEPS_INVALID` 일 때 같은 검사 분기가 찾은 세부 원인을 재시도 요지로 보낸다 — `EMPTY_FOR_TOOL_TASK`(t 가 KQ·LQ·INC 인데 steps 가 빈 배열), `NONEMPTY_FOR_NO_TOOL_TASK`(t 가 GQ·UNS 인데 steps 가 비지 않음), `ITEM_FORMAT_INVALID`(capabilityId 형식이 아닌 항목). 배열 아님·10개 초과·문자열 아닌 항목과 그 밖의 오류는 기존 코드 그대로다. 재시도 메시지 형식(`직전 출력이 무효다(오류: <코드>). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.`)·재시도 1회·직전 출력 원문 미첨부는 같다. 분류(B·경계) 재시도는 힌트와 무관하게 기존 그대로다 |
| 계획 프롬프트(A) | `plan-v3-g` | `plan-v3-i`: g 기반, 1줄을 6줄로 바꿨다. "뒤에 쓰는 t 는 앞서 쓴 steps 와 맞는 t 다" 는 그대로 두고, `STEPS_INVALID` 한 문장 설명을 재시도 코드 4종의 뜻과 고치는 방법으로 바꿨다(`EMPTY_FOR_TOOL_TASK` → t 에 맞는 Tool 로 steps 를 채운다, INC 면 `tc.logs.search` 포함 / `NONEMPTY_FOR_NO_TOOL_TASK` → steps 를 `[]` 로 쓴다 / `ITEM_FORMAT_INVALID` → Tool 목록의 capabilityId 를 그대로 쓴다 / `STEPS_INVALID` → capabilityId 문자열 배열(최대 10개)로 쓴다). 출력 스키마 표시·[t 분류]·[소유 시스템 판단]·[인자]·t 별 Tool 규칙은 g 와 바이트가 같다. 정답 값·골든/회귀 문항 문구·자원 catalog·EQP ID 주입 0 |

- 기반 후보 선택(g, h 아님): h 의 계획 스키마 송신 순서는 서버 출력 순서를 바꾸지 못했고(진단 h 의 판별 가능 A 15시도 모두 정렬 순서, Devreport §12.22 4)), 회귀 h 에서 A R07 q 가 새로 실패했다(회귀 g 는 통과, §12.22 5)). g 는 응답 형식 송신 바이트가 이미 측정됐고 회귀 R01~R08 을 A·B 모두 통과했다. 따라서 단순한 g(`JSON_SCHEMA_V1`)에 재시도 힌트만 더한다.
- 판정은 바꾸지 않는다. `f0-judge-v2` 의 valid·errorCode 와 결과 행 `errorCodes` 는 그대로(`STEPS_INVALID`)이며, 세부 원인은 판정의 같은 검사 분기에서 나온다(`ClassificationJudge.judge`). 하네스 변경은 재시도 힌트 opt-in 과 그 사전 계수 연결뿐이다(f0 패키지 `ClassificationJudge`·`Prereg`·`InputAudit`·`F0Measurements`·`F0RunnerCli`). retryHint 키가 없는 기존 등록의 재시도 메시지 바이트는 바뀌지 않는다(시험 `F0RetryHintTest`, `F0V3RegistrationDraftTest` 의 등록 audit 행 전수 대조).
- 같은 승인 qRef 계약(`qRefCatalog`), 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 이며 회차는 R=1 이다. 진단은 판정을 바꾸지 않는다.
- B·경계·usage·token 요청은 g 와 메시지·`response_format` 바이트가 같다(아래 audit 에서 B 264·경계 108·usage 2·token 13 행의 id 순서·messagesSha256·계수가 g audit 와 전부 일치). 바뀌는 것은 A 요청뿐이다.
- 입력 audit `eval/f0/v3/input-audit-v3-i.json`: Java `f0 input-audit-render --context-projection PRESENCE_V1 --retry-hint STEPS_DETAIL_V1`(22문항 × A 첫 시도 + 재시도 코드 14종·B 첫 시도 + 고정 오류 코드 11종, 경계 9, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 717행, `OVER_BUDGET 0`, 최대 A 1941/1981·B 1390/1429·경계 3023/3062·usage 1376·token 2793. 최대 입력+출력 3254(경계), A 최대 입력+출력 2237. g 대비 기존 A 264행은 모두 +150 이고, 세부 코드 재시도 66행(문항당 3)이 더해졌다.
- 효과 가설과 위험(실측 전 미확정):
  - G08 재시도 입력이 처음으로 초회와 다른 지시를 담는다(`EMPTY_FOR_TOOL_TASK` + 프롬프트의 "t 에 맞는 Tool 로 steps 를 채운다(INC 면 tc.logs.search 포함)").
  - 계획 시스템 프롬프트가 바뀌므로 A 초회 출력도 g 와 달라질 수 있다(h 에서 −13토큰 변경으로 A 9문항 출력 SHA 변화·R07 q 악화가 관찰됐다).
  - 재시도에서 steps 를 채우지 않고 t 를 GQ·UNS 로 바꿔 유효해질 수 있다. 그러면 G08 최종 t 가 `INC` 가 아니다.
  - 정렬 순서상 steps 가 t 보다 먼저 생성되므로, 재시도의 steps 는 아직 쓰지 않은 t 에 관한 지시에 기대어 정해진다.
- R=1 결과로 정확도·지연 합격을 주장하지 않는다. 정식 판정은 별도 `PREREG-v3-gate-i.md`(R=5)로만 한다.

## 2. 실행 순서(C17/C18 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 진단 묶음(i→회귀 i) 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-i"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-i"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-i.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-i.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-i/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-i.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-i"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-i.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-i --out eval/f0/results/$runId-i/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-i.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-i.txt --repeats 1 --run-id $runId-i --out eval/f0/results/$runId-i/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-i.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-i --out eval/f0/results/$runId-i/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-i.md --run-id $runId-i --results eval/f0/results/$runId-i"`(`--out` 없음 → `eval/f0/results/$runId-i/diagnostic-report.md` 만, F0 leaf 증거 0)

- i 다음에는 같은 i 프롬프트·투영·응답 형식·재시도 힌트의 개발 회귀 등록(`PREREG-v3-regression-i.md`, 30문항)을 실행한다. 정식 후보 R5(`PREREG-v3-gate-i.md`)는 그 문서 1절의 착수 조건을 충족한 경우에만 실행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "76baa655f3487c8666398b44b9129b7ac219fc16",
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-i",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-f.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-i.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-i.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-f.txt", "sha256": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98"},
    {"path": "eval/f0/v3/prompts/plan-v3-i.txt", "sha256": "0d5311096ca35f3ef24b230cd3e54c3b0bd8865e5524e94e8752186af1917997"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-i.json", "sha256": "f30685cdae92b48991a05ebc72d723ebda7bb333ef25757c33de09f4978e91dd"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "9832870eade5edc9f20cd9f96d4a3a84d21896002e6cb09d221aacb3b153f2de",
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
    "promptSha256": {"classify": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98", "plan": "0d5311096ca35f3ef24b230cd3e54c3b0bd8865e5524e94e8752186af1917997"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1",
    "contextProjection": "PRESENCE_V1",
    "retryHint": "STEPS_DETAIL_V1"
  }
}
```
