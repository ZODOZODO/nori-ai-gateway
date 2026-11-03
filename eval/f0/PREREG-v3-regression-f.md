# F0 개발 회귀 사전등록 v3-regression-f — 후보 f(존재 신호 Context 투영 + f 프롬프트 + 서버 구조화 출력 제약), 원래 22문항 + 회귀 8문항

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = 8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc`(C11)**

- 목적은 **진단(purpose=DIAGNOSTIC) 개발 회귀**다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 추가 문항·결과는 정식 분모·판정(SPEC §14.5)에 넣지 않는다.
- 기존 v1/v2/v3 등록 문서·입력·측정 결과·보고서·증거·capture 는 바꾸지 않는다. 회귀 골든 `eval/f0/v3/golden-regression-v3.jsonl`(30문항 = 원래 22행 바이트 그대로 + 신규 8행)은 `PREREG-v3-regression.md` 등록 바이트 그대로 참조만 한다.
- 등록 근거: 사용자 승인 "C11/C12 허용 후 진행(권장)"(2026-11-03, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C11 `8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc` = 후보 f 등록 대상·하네스 코드·관련 시험·Devreport 정확 12파일(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C12 = 후보 f 사전등록 3문서(`PREREG-v3-{diag-f,regression-f,gate-f}.md`)만 수정해 `registeredCommit`에 C11 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-03 C11 전까지 이 문서는 미등록 초안(C11/C12 사용자 미허용)이었고, 그 동안 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다.

## 1. 회귀 설계

- 후보 입력은 `PREREG-v3-diag-f.md` 와 같다: `classify-v3-f`·`plan-v3-f`, `parameters.contextProjection=PRESENCE_V1`(값 없이 존재 신호만, `null`·`[]` Context 는 FULL 과 같은 바이트), `responseFormat=JSON_SCHEMA_V1`, fixture v2·경계 9케이스·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`), 회차 R=1. golden·audit 만 다르다.
- 신규 8행의 확인 축은 `PREREG-v3-regression.md` §1 표 그대로다(R01 인용 설비 ID, R02 설비 ID↔코드명, R03 Context 전용 값 비복사, R04 TC 송수신 실패, R05 WM 정본 UNS, R06 분 단위 구간 표현 변경, R07 의도상 두 번째 검색어, R08 64 코드포인트 EQP ID). 신규 질문·기대값은 바꾸지 않았다. 30문항 중 값 있는 Context 는 G08·G20·R03 세 문항이며 이 셋만 FULL 과 다른 바이트(값 제거)다.
- 입력 audit `eval/f0/v3/input-audit-v3-regression-f.json`: Java `f0 input-audit-render --context-projection PRESENCE_V1`(30문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 843행, `OVER_BUDGET 0`, 최대 A 1632/1671·B 1406/1445·경계 3023/3062·usage 1376·token 2793. 최대 입력+출력 3254.

## 2. 실행 순서(C11/C12 등록 commit 뒤에만, 진단 f 다음, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId` 는 진단 묶음(f→회귀 f) 시작 때 정한 값을 그대로 쓴다. 이 등록의 회차 ID 는 `"$runId-regression-f"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-regression-f"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-regression-f.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-regression-f.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-regression-f/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-regression-f.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-regression-f"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression-f.md --path B --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-regression-f --out eval/f0/results/$runId-regression-f/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression-f.md --path A --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-f.txt --repeats 1 --run-id $runId-regression-f --out eval/f0/results/$runId-regression-f/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-regression-f.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-regression-f --out eval/f0/results/$runId-regression-f/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-regression-f.md --run-id $runId-regression-f --results eval/f0/results/$runId-regression-f"`(`--out` 없음 → `eval/f0/results/$runId-regression-f/diagnostic-report.md` 만, F0 leaf 증거 0)

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc",
  "runtimeCaptureDir": "eval/f0/chat-template/v3-regression-f",
  "inputRoles": {
    "golden": "eval/f0/v3/golden-regression-v3.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-f.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-f.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-regression-f.json"
  },
  "inputs": [
    {"path": "eval/f0/v3/golden-regression-v3.jsonl", "sha256": "ef1803d621ee3639f16c0d474d8cd6d151e4df5f1f0069e4dd7bb81ef9617337"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-f.txt", "sha256": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98"},
    {"path": "eval/f0/v3/prompts/plan-v3-f.txt", "sha256": "38efd566d0084583816f2d6c8399fd58064c9bc2c710dcc1d87ca78cc875871b"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-regression-f.json", "sha256": "2d8fd993a65079a18f8824dbd107bb9236618eb923464536e86a9081c37e3682"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "89cb4ebbf59fee4dd1af191941c52eb8458251e427ee39f6a0775d8a94a4455e",
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
    "promptSha256": {"classify": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98", "plan": "38efd566d0084583816f2d6c8399fd58064c9bc2c710dcc1d87ca78cc875871b"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "JSON_SCHEMA_V1",
    "contextProjection": "PRESENCE_V1"
  }
}
```
