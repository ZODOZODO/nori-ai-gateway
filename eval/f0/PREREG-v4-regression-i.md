# F0 개발 회귀 사전등록 v4-regression-i — 교체 추론 서버(qwen3:4b = Qwen3-4B-Thinking-2507): I(v3 마지막 구성 i) · temperature 0 · 원래 22문항 + 회귀 8문항

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC) 개발 회귀** 다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 문항·결과는 정식 분모·판정 밖이다(SPEC §16 F0, Plan P03 v4 ⑨).
- 이 문서는 v1~v3 등록 문서(Jetson 회차, C1~C20)와 그 입력·결과·보고서·증거·capture 를 바꾸지 않는다. 회귀 골든 `eval/f0/v3/golden-regression-v3.jsonl`(30문항 = 원래 22행 바이트 그대로 + 신규 8행)·fixture v2·경계 9케이스·v2 메시지 입력·프롬프트는 바이트 그대로 참조만 한다(SHA 는 v3 등록값과 같음).
- 초안 상태에서는 측정 명령·`f0 prereg-check` 가 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0). 등록은 사용자 허용 시 로컬 C22(등록 대상·하네스·이 초안) → C23(`registrationStatus=REGISTERED`, `registeredCommit`=C22) 두 commit 으로만 한다.
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-04 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 회귀 설계

- 공통 설계(arm 표·런타임·예산·입력 audit·Plan P03 v4 ⑥ 관측 필드와 판별 기준·호출 수와 예상 시간·무효 규칙·실행 전제)는 `PREREG-v4-diag-v2-t0.md` §1.1~§1.7 과 같다. 이 문서는 그 표의 한 행이다.
- 프롬프트 arm I(v3 마지막 구성 i): `classify-v3-f`/`plan-v3-i`, `contextProjection=PRESENCE_V1`, `responseFormat=JSON_SCHEMA_V1`, `retryHint=STEPS_DETAIL_V1` — C17/C18 등록 구성 i. temperature **0** — 정식 기준값.
- 같은 입력·예산을 쓰는 짝 등록: `PREREG-v4-diag-i-t0.md` (같은 arm·temperature 0 — 원래 22문항과 경계 요청의 메시지 바이트가 같고 회귀 8문항만 더함).
- 입력 audit `eval/f0/v4/input-audit-v4-regression-i.json`: `f0 input-audit-render --runtime-profile INFER_V4 --context-projection PRESENCE_V1 --retry-hint STEPS_DETAIL_V1` → `count_input_audit_v4.py` (933행, `OVER_BUDGET 0`, 최대 입력 + 요청 max_tokens 8,179).
- 신규 8행의 확인 축은 `PREREG-v3-regression.md` §1 표 그대로다(R01 인용 설비 ID, R02 설비 ID↔코드명, R03 Context 전용 값 비복사, R04 TC 송수신 실패, R05 WM 정본 UNS, R06 분 단위 구간 표현 변경, R07 의도상 두 번째 검색어, R08 64 코드포인트 EQP ID). 질문·기대값은 바꾸지 않았다.
- 생성 호출 수: (B 30 + A 30) × 시도 1~2 = 최소 60·최대 120. boundary-gen 은 실행하지 않는다(같은 arm temperature 0 진단 등록과 경계 요청 바이트 동일 — 공통 설계 §1.2).
- **착수 조건**: ①진단 4문서(`PREREG-v4-diag-{v2,i}-{t0,t06}`)가 exit 0·`complete=true` 로 끝나고 회차 무효 0 ②Plan ⑧(진단 결과로 하네스 응답 파싱·답변 토큰 계수 방법·예산 규칙 확정)에서 f0 하네스 소스·판정이 바뀌지 않음 — 바뀌면 이 등록의 `harnessDigest` 가 stale 이 되어 `PREREG_INPUT_CHANGED` 로 기계 거부되므로 이 문서를 고치지 않고 새 등록으로 대체한다 ③진단에서 답변 토큰 계수 방법 (a) 가 불가로 판명되면(전 시도 `ANSWER_TOKENS_UNCOUNTABLE`) 회귀의 정확도 축이 의미가 없으므로 실행 여부를 사용자가 정한다 ④C22/C23 뒤 `PREREG_VERIFIED`.
- R=1 결과로 정확도·지연 합격을 주장하지 않는다. 정식 판정은 사고 상한 T 의 SPEC 기록(Plan ⑩) 뒤 별도 GATE R5 등록으로만 한다(현재 하네스는 v4 GATE 등록 거부).

## 2. 실행 순서(C22/C23 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건·터널·환경 검사는 `PREREG-v4-diag-v2-t0.md` §2 와 같다. `$runId` 는 v4 진단 묶음 시작 때 정한 값을 그대로 쓴다. 이 등록의 회차 ID 는 `"$runId-regression-i"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로, `$env:AIGW_PARENT_RUN_ID = "$runId-regression-i"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v4-regression-i.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-regression-i.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-regression-i/preflight.json"` → exit 4/6 이면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v4-regression-i.md --endpoint http://127.0.0.1:11434 --model qwen3:4b --out eval/f0/chat-template/v4-regression-i"` → `qwen3-4b.template.sha256` 가 `2d54db2b…97f9` 인지 확인
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-regression-i.md --path B --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-regression-i --out eval/f0/results/$runId-regression-i/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-regression-i.md --path A --endpoint http://127.0.0.1:11434 --golden eval/f0/v3/golden-regression-v3.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-i.txt --repeats 1 --run-id $runId-regression-i --out eval/f0/results/$runId-regression-i/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-regression-i.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-regression-i/postflight.json"` → `preflight.json` 과 모델 목록 대조(다르면 공통 설계 §1.6 회차 무효)
7. (사용자·실행자) G-INFER-SHARE 구간 파일 `docs/gates/f0/share-window-$runId-regression-i.json` 작성(공통 설계 §1.7)
8. `.\gradlew.bat :gateway-eval:run --args="f0 share-exclude --prereg eval/f0/PREREG-v4-regression-i.md --results eval/f0/results/$runId-regression-i --intervals docs/gates/f0/share-window-$runId-regression-i.json"`
9. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v4-regression-i.md --run-id $runId-regression-i --results eval/f0/results/$runId-regression-i"`(`--out` 없음 → `diagnostic-report.md` 만, F0 leaf 증거 0)

- v4 진단·개발 회귀 묶음 끝(그다음은 Plan ⑩ 사고 상한 T 제안).

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v4",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v4-regression-i",
  "inputRoles": {
    "golden": "eval/f0/v3/golden-regression-v3.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-f.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-i.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/v4/chat-renderers-v4.json",
    "inputAudit": "eval/f0/v4/input-audit-v4-regression-i.json"
  },
  "inputs": [
    {"path": "eval/f0/v3/golden-regression-v3.jsonl", "sha256": "ef1803d621ee3639f16c0d474d8cd6d151e4df5f1f0069e4dd7bb81ef9617337"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-f.txt", "sha256": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98"},
    {"path": "eval/f0/v3/prompts/plan-v3-i.txt", "sha256": "0d5311096ca35f3ef24b230cd3e54c3b0bd8865e5524e94e8752186af1917997"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/v4/chat-renderers-v4.json", "sha256": "6b6d8bd7a4a716fa2209f81cccf5e95e677b12302b38d88f619250e5c690bd56"},
    {"path": "eval/f0/v4/input-audit-v4-regression-i.json", "sha256": "5d2ada83e0d0a1b574a4577ecfb04ded29a6c0cfaa461b4f85a673f518814136"},
    {"path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256", "sha256": "91fd22b9ec0dbecfd30bf3879ef13290c3b3ffc5abd9e222f35e76ca66c6bc03"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v4.py", "sha256": "d6cb5985062d70ed6ba597863c4c4e25c4f36db7d6785b1225d2351c5d5abf7a"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "50ba7cc3dd40b81af777faf91124b0a38c4ccc2a661d6448fe8567c00bd0ad76",
  "parameters": {
    "generationModel": "qwen3:4b",
    "generationModelDigest": "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7",
    "embeddingModel": "bge-m3",
    "repeats": 1,
    "temperature": 0,
    "maxTokens": {"classify": 5120, "plan": 5120},
    "inputLimitTokens": {"classify": 3072, "plan": 3072},
    "contextTokens": 8192,
    "requestTimeoutMs": 600000,
    "outputLimitTokens": 192,
    "answerLimitTokens": {"classify": 192, "plan": 256},
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
