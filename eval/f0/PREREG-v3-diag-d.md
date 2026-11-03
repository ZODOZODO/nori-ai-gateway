# F0 진단 사전등록 v3-diag-d — LLM 추출용 Context 투영 + d 정리 프롬프트 + 서버 구조화 출력 제약

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-{diag-a,diag-b,diag-c,regression,gate}.md`, 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 원래 골든 22문항·fixture v2·경계 9케이스·v2 메시지 입력은 바이트 그대로 참조만 한다.
- 현재 상태: 등록 대상·하네스 코드 commit(C7 예정)과 이 문서의 `registeredCommit` 기록 commit(C8 예정)은 **사용자 미허용**이다. 그 전까지 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 이 초안도 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 진단 설계(후보 d)

- 근본 원인(§12.12 진단 c·회귀 원문 없는 근거): ① LLM 에 전체 Context(timeWindow·selectedResourceRefs 포함)를 보내 G08·R03 에서 Context 시각·자원 복사가 생겼다. SPEC §14.5/§22.2 는 Context 선택 리소스·시간 창의 적용을 LLM 출력 뒤 Gateway 책임으로 확정한다. ② 경계 7건·R08 은 Unicode 64 코드포인트 설비 ID 중 영문·숫자 부분만 고른 eqp 오류였다. ③ R07 은 제외·대조로 언급한 검색어 선택, R04 A 는 명시 정밀 시각 원문 불일치였다.
- 후보 d = c 대비 두 요인만 바꾼다.

| 요인 | c | d(이 문서) |
|---|---|---|
| LLM user 메시지 `context` | 전체 Context(키 없음 = FULL, 기존 바이트 그대로) | `parameters.contextProjection=EXTRACTION_V1`: `timezone`·`referenceTime` 만 전달. `timeWindow`·`selectedResourceRefs` 는 LLM 입력에서 제외(원 Context·fixture·판정·진단·Gateway 후적용 보존) |
| 프롬프트 | `classify-v3`·`plan-v3` | `classify-v3-d`·`plan-v3-d`: eqp = 질문이 설비로 지칭한 Unicode 식별자 전체(최대 64자, 영문·숫자 부분만 선택 금지), 질문에 없는 설비·시간은 null, 초·소수초·offset 절대 시각은 원문 그대로(시간대 변환은 Gateway), 상대·분 단위 시간만 timezone·referenceTime 기준, qRef 는 질문이 요구하는 검색어(제외·대조 언급 선택 금지), steps 는 최종 t 에 맞춤. 정답 값·골든/회귀 문항·자원 catalog·ID 상수 주입 0 |

- 같은 승인 qRef 계약(`qRefCatalog`·`JSON_SCHEMA_V1`), 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 이며 회차는 R=1 이다. 진단은 판정을 바꾸지 않는다.
- 투영은 LLM 에 보이는 입력만 줄이는 내부 하네스 옵션이며 제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`) 변경이 아니다. A/B·경계·usage·token 조립이 같은 투영을 쓴다.
- 입력 audit `eval/f0/v3/input-audit-v3-d.json`: Java `f0 input-audit-render --context-projection EXTRACTION_V1`(22문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 651행, `OVER_BUDGET 0`, 최대 A 1610/1649·B 1382/1421·경계 3015/3054·usage 1368·token 2793.
- R=1 결과로 정확도·지연 합격을 주장하지 않는다. 정식 판정은 별도 `PREREG-v3-gate-d.md`(R=5)로만 한다.

## 2. 실행 순서(C7/C8 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 진단 묶음(d→회귀 d) 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-d"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-d"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-d.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-d.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-d/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-d.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-d"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-d.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 1 --run-id $runId-d --out eval/f0/results/$runId-d/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-d.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-d.txt --repeats 1 --run-id $runId-d --out eval/f0/results/$runId-d/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-d.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 1 --run-id $runId-d --out eval/f0/results/$runId-d/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-d.md --run-id $runId-d --results eval/f0/results/$runId-d"`(`--out` 없음 → `eval/f0/results/$runId-d/diagnostic-report.md` 만, F0 leaf 증거 0)

- d 다음에는 같은 d 프롬프트·투영·스키마의 개발 회귀 등록(`PREREG-v3-regression-d.md`, 30문항)을 실행한다. 정식 후보 R5(`PREREG-v3-gate-d.md`)는 그 문서 1절의 착수 조건을 충족한 경우에만 실행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-d",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-d.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-d.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-d.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-d.txt", "sha256": "9f52c9c081bc85a2d967a617d74192a1432254bacbc0aa8d51534c3271b180c7"},
    {"path": "eval/f0/v3/prompts/plan-v3-d.txt", "sha256": "b3297926b5208ce9dffb126bd4a6dd95bdba31d2d4f9374ac42bec0449893c86"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-d.json", "sha256": "f77adec8b074a899a70389e3aacb8e9892b0e5b92b709f2179c861ba0c1ae24f"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "cda6456e390fc6b50d5b7f6548aa70a5a7a5a4633264da744a02bf38b588525c",
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
    "promptSha256": {"classify": "9f52c9c081bc85a2d967a617d74192a1432254bacbc0aa8d51534c3271b180c7", "plan": "b3297926b5208ce9dffb126bd4a6dd95bdba31d2d4f9374ac42bec0449893c86"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1",
    "contextProjection": "EXTRACTION_V1"
  }
}
```
