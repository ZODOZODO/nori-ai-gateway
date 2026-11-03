# F0 진단 사전등록 v3-diag-e — 단일 요인 분리: LLM 추출용 Context 투영 + 기존 c 프롬프트 + 서버 구조화 출력 제약

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 어떤 정식 R5·게이트 착수 조건에도 성적을 더하지 않는다. root 의 원인 해석용이다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-{diag-a,diag-b,diag-c,diag-d,regression,regression-d,gate,gate-d}.md`, 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 원래 골든 22문항·fixture v2·경계 9케이스·v2 메시지 입력·c 프롬프트는 바이트 그대로 참조만 한다.
- 등록에는 사용자 허용이 필요한 로컬 commit 두 개가 필요하다. C9 = 이 문서(UNCOMMITTED 초안)·`eval/f0/v3/input-audit-v3-e.json`·Devreport, C10 = 이 문서만 수정해 `registrationStatus=REGISTERED`·`registeredCommit`=C9 SHA·등록 설명을 기록한다. 그 전까지 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다(하네스 소스는 C8 과 같고 수정 0). 하네스 코드나 입력이 바뀌면 이 초안도 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.
- 이 회차 결과가 나온 뒤 설계·입력을 바꾸려면 새 진단 등록이 필요하다. 이 문서를 고쳐 다시 쓰지 않는다.

## 1. 진단 설계(단일 요인 e)

- 배경(§12.14): 진단 d 는 c 대비 두 요인(LLM 입력 Context 투영 `EXTRACTION_V1`, d 프롬프트)을 함께 바꿨다. 결과(G08 A·B 최종 t `UNS`, 경계 7/9)를 어느 요인이 만들었는지 분리되지 않는다.
- e 는 새 제품 기능·정책·수정안이 아니라 요인 분리 진단이다. d 와 비교해 프롬프트만 기존 c 로 되돌린다.

| 등록 | LLM user 메시지 `context` | 프롬프트 |
|---|---|---|
| c(기존) | 전체 Context(FULL) | `classify-v3`·`plan-v3` |
| d(기존) | `EXTRACTION_V1`(`timezone`·`referenceTime` 만) | `classify-v3-d`·`plan-v3-d` |
| e(이 문서) | `EXTRACTION_V1`(`timezone`·`referenceTime` 만) | `classify-v3`·`plan-v3`(c 와 바이트 동일, 새 프롬프트 0) |

- 비교 범위: c↔e 는 c 프롬프트 고정에서 투영의 영향을, e↔d 는 투영 고정에서 c/d 프롬프트의 영향을 본다. 2×2 의 한 칸(FULL + d 프롬프트)이 없으므로 두 요인의 상호작용은 분리하지 못한다. R=1 이며, e 결과로 전체 원인을 유일하게 확정하거나 다음 PASS 를 보장한다고 주장하지 않는다.
- 같은 승인 qRef 계약(`qRefCatalog`·`JSON_SCHEMA_V1`), 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·usage 2·token 13 메시지 입력(v2)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·`requestTimeoutMs`·지연 예산·판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 이다. 원 코드·schema·골든·임계·분모 변경 0. 진단은 판정을 바꾸지 않는다.
- 투영은 LLM 에 보이는 입력만 줄이는 내부 하네스 옵션이며 제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`) 변경이 아니다. A/B·경계·usage·token 조립이 같은 투영을 쓴다.
- 입력 audit `eval/f0/v3/input-audit-v3-e.json`: Java `f0 input-audit-render --context-projection EXTRACTION_V1`(c 프롬프트, 22문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`)로 실제 계수 = 651행, `OVER_BUDGET 0`, 최대 A 1580/1619·B 1372/1411·경계 3005/3044·usage 1358·token 2793. 최대 입력+출력 3044+192=3236 ≤ 4096.

## 2. 실행 순서(C9/C10 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 e 시작 때 새로 한 번 정한다(기존 run prefix 재사용 0). 이 등록의 회차 ID 는 `"$runId-e"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-e"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-e.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-e.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-e/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-e.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-e"`(사전 부재 확인, 신규 capture)
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-e.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 1 --run-id $runId-e --out eval/f0/results/$runId-e/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-e.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3.txt --repeats 1 --run-id $runId-e --out eval/f0/results/$runId-e/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-e.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 1 --run-id $runId-e --out eval/f0/results/$runId-e/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-e.md --run-id $runId-e --results eval/f0/results/$runId-e"`(`--out` 없음 → `eval/f0/results/$runId-e/diagnostic-report.md` 만, F0 leaf 증거 0)

- e 뒤 정식 R5·회귀·게이트 실행은 이 등록의 범위가 아니다. 다음 조치는 root 가 e·c·d 진단 보고서를 원문 없이 대조해 정한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-e",
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
    "inputAudit": "eval/f0/v3/input-audit-v3-e.json"
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
    {"path": "eval/f0/v3/input-audit-v3-e.json", "sha256": "be57fd5e3104fad15f897e4fd477968a19b2e56f76ebbf11c3072b3732d23062"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "ad8ca310513ddcba9712af03d0e6bf969c61837edc5cc5163558e6290a5cf290",
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
    "promptSha256": {"classify": "2772aa56043827619b4975e672c5c45df6ce1a678d4cece77df41b5b96815143", "plan": "43da76c3811714e204e9f61c737197392671e2266993cd81b30b52fa88cc7982"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1",
    "contextProjection": "EXTRACTION_V1"
  }
}
```
