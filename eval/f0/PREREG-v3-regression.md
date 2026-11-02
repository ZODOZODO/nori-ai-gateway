# F0 개발 회귀 사전등록 v3-regression — v3 정리 프롬프트 + 서버 구조화 출력 제약, 원래 22문항 + 회귀 8문항

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = 05c5facab7daa5d9d46bad2caa5ec26e13760e77`(C5)**

- 목적은 **진단(purpose=DIAGNOSTIC) 개발 회귀**다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 추가 문항·결과는 정식 분모·판정(SPEC §14.5)에 넣지 않는다.
- 진단 a/b/c 비교(원래 22문항 + 경계 9케이스, R=1)의 분모는 바꾸지 않는다. 이 등록은 별도 골든 파일과 별도 입력 audit 을 쓴다.
- v1/v2 등록 문서·입력·측정 결과·보고서·증거는 바꾸지 않는다. 원래 v2 골든 22행은 바이트 그대로 새 파일 앞부분에 복사했다(시험이 접두 바이트 일치를 확인).
- 등록 근거: 사용자 승인 "로컬 C5/C6 허용 후 계속 진행"(2026-11-02, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C5 `05c5facab7daa5d9d46bad2caa5ec26e13760e77` = 등록 대상·하네스 코드(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C6 = v3 사전등록 5문서(`PREREG-v3-{diag-a,diag-b,diag-c,regression,gate}.md`)만 수정해 `registeredCommit`에 C5 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-02 C5 전까지 이 문서는 미등록 초안이었고, 그 동안 측정 명령·`f0 prereg-check`는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-02 작업 트리 실제 바이트로 계산했다. C5 직전 등록 대상 30파일을 검토 SHA-256 과 대조해 불일치 0이었다.

## 1. 회귀 설계

- 골든 `eval/f0/v3/golden-regression-v3.jsonl` 30문항 = `eval/golden/tc-f0-v2.jsonl` 22행(바이트 그대로) + 신규 8행. 신규 질문·기대값은 실제 모델 출력을 보기 전에 고정했다. 기존 GoldenSet 제약(≥20문항, AC38 문항 정확히 1, fixture 에 있는 eqp, 분 단위 시각)을 그대로 통과하며 GoldenSet 범용 리팩터는 하지 않았다. 초·소수초·offset 표기는 기존 경계 케이스(NORMAL-PRECISE)와 ArgLimits 단위 시험이 계속 맡는다.
- 신규 8행(category — 확인 축):

| id | category | 확인 축 |
|---|---|---|
| R01 | REG_QUOTED_EQP | 큰따옴표로 인용한 정상 설비 ID 도 eqp 로 원문 추출(후보 1개이므로 qRef 는 그 ID) |
| R02 | REG_EQP_VS_CODE_TERM | 설비 ID 와 인용된 코드명(검색어 역할)의 구분 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | Context 의 selectedResourceRefs·timeWindow 에만 있는 ID·시간을 wire 로 복사하지 않음 |
| R04 | REG_TC_TRANSFER_FAILURE | 다른 시스템 이름이 있어도 TC 송수신 실패 원인은 TC 자료(INC) |
| R05 | REG_WM_OWNED_RECORD | WM 정본 자료 요청은 UNS(R04 와 대조 쌍) |
| R06 | REG_PARAPHRASED_RANGE | 기존 분 단위 구간 질문(G22)의 표현 변경 |
| R07 | REG_SECOND_QUOTED_INTENT | 인용 후보 2개 중 질문이 명시한 의도상 두 번째 검색어 선택 |
| R08 | REG_LONG_EQP_ID | fixture 에 있는 기존 64 코드포인트 EQP ID(모델에 자원 catalog 주입 0) |

- 프롬프트·스키마·fixture·경계 9케이스·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·`responseFormat=JSON_SCHEMA_V1`·판정 규칙(`f0-judge-v2`)은 진단 c 와 같고 회차는 R=1 이다.
- 입력 audit `eval/f0/v3/input-audit-v3-regression.json`: Java `f0 input-audit-render`(30문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 843행, `OVER_BUDGET 0`, 최대 A 1610/1649·B 1402/1441·경계 3015/3054.

## 2. 실행 순서(승인 후 — C5/C6 등록 commit 뒤에만, 진단 c 다음, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId` 는 진단 묶음(a→b→c→회귀) 시작 때 정한 값을 그대로 쓴다. 이 등록의 회차 ID 는 `"$runId-regression"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-regression"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-regression.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-regression.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-regression/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-regression.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-regression"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression.md --path B --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 1 --run-id $runId-regression --out eval/f0/results/$runId-regression/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-regression.md --path A --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3.txt --repeats 1 --run-id $runId-regression --out eval/f0/results/$runId-regression/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-regression.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3.txt --repeats 1 --run-id $runId-regression --out eval/f0/results/$runId-regression/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-regression.md --run-id $runId-regression --results eval/f0/results/$runId-regression"`(`--out` 없음 → `eval/f0/results/$runId-regression/diagnostic-report.md` 만, F0 leaf 증거 0)

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "05c5facab7daa5d9d46bad2caa5ec26e13760e77",
  "runtimeCaptureDir": "eval/f0/chat-template/v3-regression",
  "inputRoles": {
    "golden": "eval/f0/v3/golden-regression-v3.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-regression.json"
  },
  "inputs": [
    {"path": "eval/f0/v3/golden-regression-v3.jsonl", "sha256": "ef1803d621ee3639f16c0d474d8cd6d151e4df5f1f0069e4dd7bb81ef9617337"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3.txt", "sha256": "2772aa56043827619b4975e672c5c45df6ce1a678d4cece77df41b5b96815143"},
    {"path": "eval/f0/v3/prompts/plan-v3.txt", "sha256": "43da76c3811714e204e9f61c737197392671e2266993cd81b30b52fa88cc7982"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-regression.json", "sha256": "f25948931c21e90b8815c10d30c8ed90eee14ec8f9fd37c1734db2bb34088e24"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "5a8f0f0f9d85339df9d3c9d8d6246af7453fe20fbd76765f9f6055f280f7daaa",
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
    "responseFormat": "JSON_SCHEMA_V1"
  }
}
```
