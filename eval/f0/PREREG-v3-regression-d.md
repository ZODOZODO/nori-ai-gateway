# F0 개발 회귀 사전등록 v3-regression-d — 후보 d(Context 투영 + d 정리 프롬프트 + 서버 구조화 출력 제약), 원래 22문항 + 회귀 8문항

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC) 개발 회귀**다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 추가 문항·결과는 정식 분모·판정(SPEC §14.5)에 넣지 않는다.
- 기존 v1/v2/v3 등록 문서·입력·측정 결과·보고서·증거·capture 는 바꾸지 않는다. 회귀 골든 `eval/f0/v3/golden-regression-v3.jsonl`(30문항 = 원래 22행 바이트 그대로 + 신규 8행)은 `PREREG-v3-regression.md` 등록 바이트 그대로 참조만 한다.
- 현재 상태: 등록 대상·하네스 코드 commit(C7 예정)과 이 문서의 `registeredCommit` 기록 commit(C8 예정)은 **사용자 미허용**이다. 그 전까지 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다.

## 1. 회귀 설계

- 후보 입력은 `PREREG-v3-diag-d.md` 와 같다: `classify-v3-d`·`plan-v3-d`, `parameters.contextProjection=EXTRACTION_V1`(LLM 에는 `timezone`·`referenceTime` 만), `responseFormat=JSON_SCHEMA_V1`, fixture v2·경계 9케이스·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`), 회차 R=1. golden·audit 만 다르다.
- 신규 8행의 확인 축은 `PREREG-v3-regression.md` §1 표 그대로다(R01 인용 설비 ID, R02 설비 ID↔코드명, R03 Context 전용 값 비복사, R04 TC 송수신 실패, R05 WM 정본 UNS, R06 분 단위 구간 표현 변경, R07 의도상 두 번째 검색어, R08 64 코드포인트 EQP ID). 신규 질문·기대값은 바꾸지 않았다.
- 입력 audit `eval/f0/v3/input-audit-v3-regression-d.json`: Java `f0 input-audit-render --context-projection EXTRACTION_V1`(30문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 843행, `OVER_BUDGET 0`, 최대 A 1626/1665·B 1398/1437·경계 3015/3054·usage 1368·token 2793.

## 2. 실행 순서(C7/C8 등록 commit 뒤에만, 진단 d 다음, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId` 는 진단 묶음(d→회귀 d) 시작 때 정한 값을 그대로 쓴다. 이 등록의 회차 ID 는 `"$runId-regression-d"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-regression-d"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-regression-d.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-regression-d.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-regression-d/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-regression-d.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-regression-d"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression-d.md --path B --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 1 --run-id $runId-regression-d --out eval/f0/results/$runId-regression-d/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression-d.md --path A --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-d.txt --repeats 1 --run-id $runId-regression-d --out eval/f0/results/$runId-regression-d/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-regression-d.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 1 --run-id $runId-regression-d --out eval/f0/results/$runId-regression-d/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-regression-d.md --run-id $runId-regression-d --results eval/f0/results/$runId-regression-d"`(`--out` 없음 → `eval/f0/results/$runId-regression-d/diagnostic-report.md` 만, F0 leaf 증거 0)

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-regression-d",
  "inputRoles": {
    "golden": "eval/f0/v3/golden-regression-v3.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-d.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-d.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-regression-d.json"
  },
  "inputs": [
    {"path": "eval/f0/v3/golden-regression-v3.jsonl", "sha256": "ef1803d621ee3639f16c0d474d8cd6d151e4df5f1f0069e4dd7bb81ef9617337"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-d.txt", "sha256": "9f52c9c081bc85a2d967a617d74192a1432254bacbc0aa8d51534c3271b180c7"},
    {"path": "eval/f0/v3/prompts/plan-v3-d.txt", "sha256": "b3297926b5208ce9dffb126bd4a6dd95bdba31d2d4f9374ac42bec0449893c86"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-regression-d.json", "sha256": "799cf3004167272e19c641f1afb55398cc83d3308c6d803ab5195087e95e4a62"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "a863b09f46b689ce76c7677eaecb766b190c440a50dd90b061a06461fa94be48",
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
