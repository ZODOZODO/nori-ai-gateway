# F0 진단 사전등록 v3-diag-f — 존재 신호 Context 투영 + f 프롬프트 + 서버 구조화 출력 제약

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = 8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc`(C11)**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-{diag-a,diag-b,diag-c,diag-d,diag-e,regression,regression-d,gate,gate-d}.md`, 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 원래 골든 22문항·fixture v2·경계 9케이스·v2 메시지 입력은 바이트 그대로 참조만 한다.
- 등록 근거: 사용자 승인 "C11/C12 허용 후 진행(권장)"(2026-11-03, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C11 `8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc` = 후보 f 등록 대상·하네스 코드·관련 시험·Devreport 정확 12파일(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C12 = 후보 f 사전등록 3문서(`PREREG-v3-{diag-f,regression-f,gate-f}.md`)만 수정해 `registeredCommit`에 C11 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-03 C11 전까지 이 문서는 미등록 초안(C11/C12 사용자 미허용)이었고, 그 동안 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 이 초안도 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 진단 설계(후보 f)

- 근본 원인(Devreport §12.17, 진단 c·d·e 원문 없는 정제 필드·출력 SHA 근거):
  - ① G08 최종 t `UNS`: `EXTRACTION_V1` 이 `timeWindow`·`selectedResourceRefs` 값과 함께 "TC 자원(EQP)과 시간 창이 선택돼 있다"는 존재 신호까지 지웠다. e(투영 + c 프롬프트)의 G08 A·B 첫 출력 SHA 가 d 와 같고 c(FULL)와 다르다. SPEC AC-38-F0 은 고정 문항을 AC-38 공통 TC Context(EQP 선택·시간 창)와 함께 입력하도록 정한다.
  - ② 경계 NORMAL-01 t=`LQ`: 이 케이스의 Context 는 `timeWindow=null`·`selectedResourceRefs=[]` 라 투영이 지운 정보는 없고 user 메시지 바이트만 달라졌다. 같은 형태(설비 + 명시 구간 + "오류 로그 원인 찾아줘")의 G09 는 B 에서 c·d·e 모두 `LQ` 였다. INC/LQ 경계 규칙이 약해 경계 문항이 입력 바이트 변화에 흔들린 것으로 본다(추정).
  - ③ 경계 MAX-HANGUL eqp: d 출력 SHA 가 질문 코드포인트 `[0,16)` 접두를 eqp 로 쓴 직렬화와 정확히 같다(completion 107 = 106 + 종료 1, pin tokenizer 계수와 일치). 64자 식별자를 한글 낱말처럼 읽히는 부분 앞에서 끊었다.
- 후보 f = d 대비 두 요인을 바꾼다.

| 요인 | d | f(이 문서) |
|---|---|---|
| LLM user 메시지 `context` | `EXTRACTION_V1`: `timezone`·`referenceTime` 만 | `parameters.contextProjection=PRESENCE_V1`: 키 4개 유지, `timeWindow` 객체는 `true`, `selectedResourceRefs` 는 `type` 만 남긴다(시각·자원 ID 값 0). `null`·`[]` 인 Context 는 FULL 과 같은 바이트다(경계 9케이스 전부, 골든 22 중 20문항). 원 Context·fixture·판정·진단·Gateway 후적용은 그대로 |
| 프롬프트 | `classify-v3-d`·`plan-v3-d` | `classify-v3-f`·`plan-v3-f`: d 기반. INC 는 로그·DLQ·이력을 봐야 해도 원인·이유를 물으면 INC, 보낸 주체가 생략돼도 다른 시스템과 주고받은 메시지의 실패 이유는 INC, context 의 `timeWindow`·`selectedResourceRefs` 는 화면 선택 여부만 알리고 값은 Gateway 가 적용, eqp 는 일부만 고르거나 한글 낱말처럼 보이는 곳에서 끊지 않는다. 경계 입력 여유 확보를 위해 서버 스키마(`JSON_SCHEMA_V1`)가 이미 강제하는 문장 3개(설명 텍스트 금지·키 집합·qRef 원문/목록 밖 ID/q 키)를 뺐다. 정답 값·골든/회귀 문항 문구·자원 catalog·ID 상수 주입 0 |

- 같은 승인 qRef 계약(`qRefCatalog`·`JSON_SCHEMA_V1`), 같은 질문(`eval/golden/tc-f0-v2.jsonl` 22문항)·fixture(`tc-registry-fixture-v2.json`)·경계 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)·예산(입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096)·판정 규칙(`f0-judge-v2`)·정확 일치 oracle·고정 재시도 1회·원본 질문 전체 유지·Source 호출 0·유효 입력 절삭 0 이며 회차는 R=1 이다. 진단은 판정을 바꾸지 않는다.
- 투영은 LLM 에 보이는 입력만 바꾸는 내부 하네스 옵션이며 제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`) 변경이 아니다. A/B·경계·usage·token 조립이 같은 투영을 쓴다.
- 입력 audit `eval/f0/v3/input-audit-v3-f.json`: Java `f0 input-audit-render --context-projection PRESENCE_V1`(22문항 × A/B, 경계 9, 첫 시도 + 고정 오류 코드 11종 재시도, usage 2·token 13) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) = 651행, `OVER_BUDGET 0`, 최대 A 1616/1655·B 1390/1429·경계 3023/3062·usage 1376·token 2793. 최대 입력+출력 3254.
- R=1 결과로 정확도·지연 합격을 주장하지 않는다. 정식 판정은 별도 `PREREG-v3-gate-f.md`(R=5)로만 한다.

## 2. 실행 순서(C11/C12 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 터널·환경 검사는 Plan P03 0·1단계와 같다(`ssh … -N -L 11434:127.0.0.1:11434`, 별도 창). 측정 동안 모델 로드·pull·관리 API·num_ctx·서비스 변경 0.
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 진단 묶음(f→회귀 f) 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-f"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0), `$env:AIGW_PARENT_RUN_ID = "$runId-f"`.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-f.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-diag-f.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-f/preflight.json"` → exit 4 면 중단·NOT_RUN 기록(재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-diag-f.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-diag-f"`
4. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-f.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-f --out eval/f0/results/$runId-f/B"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-diag-f.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-f.txt --repeats 1 --run-id $runId-f --out eval/f0/results/$runId-f/A"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-diag-f.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-f.txt --repeats 1 --run-id $runId-f --out eval/f0/results/$runId-f/boundary"`
7. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-diag-f.md --run-id $runId-f --results eval/f0/results/$runId-f"`(`--out` 없음 → `eval/f0/results/$runId-f/diagnostic-report.md` 만, F0 leaf 증거 0)

- f 다음에는 같은 f 프롬프트·투영·스키마의 개발 회귀 등록(`PREREG-v3-regression-f.md`, 30문항)을 실행한다. 정식 후보 R5(`PREREG-v3-gate-f.md`)는 그 문서 1절의 착수 조건을 충족한 경우에만 실행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc",
  "runtimeCaptureDir": "eval/f0/chat-template/v3-diag-f",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v3/prompts/classify-v3-f.txt",
    "planPrompt": "eval/f0/v3/prompts/plan-v3-f.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v3/input-audit-v3-f.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v3/prompts/classify-v3-f.txt", "sha256": "aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98"},
    {"path": "eval/f0/v3/prompts/plan-v3-f.txt", "sha256": "38efd566d0084583816f2d6c8399fd58064c9bc2c710dcc1d87ca78cc875871b"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v3/input-audit-v3-f.json", "sha256": "6d9b0a516ee4bbf92ff248cc6e2cabbb4e381b485bb62fa8e7ae4ed5aa250277"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "35fd218fb9f3b12207c5eceaeeba1399a1f205499bce19c7e1f509154766bd10",
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
