# F0 진단 사전등록 v3-probe-g08 — A 경로 G08 steps 공백 원인 확정 시험(후보 i 기준선, 8건 각 1회)

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록은 `f0 probe-g08` 명령 한 개만 쓰며, 결과는 `eval/f0/results/<runId>-probe-g08/` 의 정제 필드뿐이다. F0 leaf 증거·RUN PARENT·§14.5 제안서·`diagnostic-report.md` 를 만들지 않고 F0 게이트 PASS 근거가 될 수 없다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-*.md`(a~i), 그 등록 입력·측정 결과·보고서·증거·capture 를 바꾸지 않는다. 기준선 입력(골든 22문항·fixture v2·`plan-v3-i`·`classify-v3-f`·v2 메시지 입력·경계 9케이스)은 바이트 그대로 참조만 한다.
- 등록 예정: C19 = 이 문서(초안)·시험 입력·하네스 코드·관련 시험·Devreport, C20 = 이 문서의 `registrationStatus`·`registeredCommit` 만 기록. 등록 전에는 측정 명령·`f0 prereg-check` 가 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다.
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. f0 하네스 소스가 바뀌면 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다. 이 등록의 하네스 변경(`G08Probe` 신규, `OllamaChatClient`·`F0RunnerCli` 추가) 때문에 후보 i 3문서(C17/C18)는 현재 작업 트리 기준 stale(`PREREG_INPUT_CHANGED`)이 되며 이는 예상된 것이다.

## 1. 시험 설계

### 1.1 배경(Devreport §12.25, 원문 없는 정제 필드·출력 SHA·공식 소스 근거)

- 확정:
  - A G08(`WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했어?`)은 g·h·i 회차 모두 두 시도 출력 SHA 가 `d648833f1ddc7d17001e8fa6f14b60b99d11e448ebae6b0c934f96159257b242`(키 정렬·인자 전부 null·steps `[]`·t `INC`)이고 최종 `INVALID` 다. `"t":"` 앞까지는 같은 회차 UNS 문항 출력과 바이트가 같고 t 값만 다르다.
  - 생성 순서 eqp→from→qRef→steps→t→to 는 Ollama 0.31.1 chat 경로(`llm/llama_server.go` `llamaServerChatResponseFormat`)가 스키마를 Go `map[string]any` 로 재직렬화해 properties 를 알파벳순으로 만들기 때문이다(llama.cpp 문법은 받은 properties 순서를 따른다). 따라서 후보 h 의 "t 먼저 송신"은 서버에서 지워졌고, "t 를 먼저 생성하면 steps 가 채워진다"는 가설은 아직 시험되지 않았다.
  - `/api/generate` → Completion 경로는 `lsReq.JsonSchema = req.Format`(RawMessage)라 Ollama 쪽은 순서를 보존한다. llama-server `/completion` 쪽 보존 여부는 미확정이며 E0·E-ORD 로 관측한다.
  - Context 형태 단독·인자 전부 null 단독·하네스/판정 결함은 원인에서 배제된다(G20·R03·G04 반례).
- 추정(분리되지 않음): (가) 질문의 `WORK_ORDER_REPLY` ↔ fixture `eval/golden/tc-registry-fixture-v2.json:7` WM ownedAreas `LOT·Workorder 진행 상태…` 낱말 겹침, (나) `eval/f0/v3/prompts/plan-v3-i.txt:16` `TC 자료로 대신 채우지 않는다.`, (다) 질문에 원인·분석·에러·로그 단서 부재.

### 1.2 기준선과 시험 8건

- 기준선 = 후보 i(C17/C18 등록 구성: `PRESENCE_V1`·`plan-v3-i`·`JSON_SCHEMA_V1`)의 A/G08 첫 시도 입력. messagesSha256 `c64d1f75a93ba9e7c3ee6a8178747be5b65f1d2751a24b64453692c405bdda9f`(= `input-audit-v3-i.json` 의 `A/G08/first`, pin tokenizer 1916토큰), 기대 출력 SHA `d648833f1ddc7d17001e8fa6f14b60b99d11e448ebae6b0c934f96159257b242`.
- 공통: temperature 0, 각 1회, **첫 시도만(재시도 0)**, 계획 출력 상한 `max_tokens`/`num_predict` 256, 입력 상한 3072, ctx 4096(변경 0). 등록 순서 E0 → E-ORD → E1 → E2 → E3 → E5 → E6 → N1 로 한 명령 안에서 보낸다. `parameters` 는 진단 i 와 같다(`retryHint` 는 기준선 구성 표기이며 이 명령은 재시도를 보내지 않는다).
- 시험 입력은 `eval/f0/v3/probe-g08/probe-g08-v1.json`(manifest) 한 파일이 정한다. 질문 변형은 manifest 의 `edit`, 프롬프트·fixture 변형은 별도 파일이며, 하네스는 측정 전에 ① 기준선 메시지 SHA = manifest 값, ② `edit.find` 가 기준 대상에 정확히 1회, ③ 변형 파일 바이트 = 기준 파일에 edit 를 적용한 바이트, ④ 질문 변형은 user 메시지만·파일 변형은 system 메시지만 다름을 확인하고 하나라도 어기면 `PROBE_INPUT_INVALID`·`PROBE_BASELINE_MISMATCH`(exit 2, 요청 0)로 거부한다.

| id | 호출 | 기준선 대비 변경(정확히 1개) | messagesSha256 | 사전 계수 |
|---|---|---|---|---|
| E0 | `/api/generate` raw=true, prompt = 등록 렌더러(`chat-renderers-v1.json`, 측정 시점 template SHA 로 선택)로 기준 메시지를 렌더한 문자열, `format` = 키 정렬(현행 chat 경로가 서버에 보내는 순서) 계획 스키마, `logprobs`=true, `top_logprobs`=5, options = temperature 0·num_predict 256 | 호출 경로(chat → generate raw) | `c64d1f75…da9f`(기준선) | 1916 |
| E-ORD | E0 과 같음 | `format` properties 선언 순서만 t, eqp, from, to, qRef, steps | `c64d1f75…da9f`(기준선) | 1916 |
| E1 | chat(등록 `JSON_SCHEMA_V1`) | question `WORK_ORDER_REPLY` → `메시지` | `67d2b44b3b41a5152a2e49b36e6696d1db94d702c9db11af97076f7acbfec527` | 1916 |
| E2 | chat | question 에서 `WM 으로 보낸 ` 삭제 | `5eae0babd23ebce158c5fa5f8e064f828f1b6fd57d6d2677134ebb7fb68b6861` | 1910 |
| E3 | chat | question `가 왜 실패했어?` → `가 실패한 원인 분석해줘` | `462ffde53b3b9828dd02f3987dc8619886b976c852073364e15e3450ae66243d` | 1918 |
| E5 | chat | 계획 프롬프트 = `plan-v3-i.txt:16` 의 ` TC 자료로 대신 채우지 않는다.`(앞 공백 포함) 한 문장만 삭제한 `eval/f0/v3/probe-g08/plan-v3-i-e5.txt` | `f6dd94d84ba7ec771a88fe23f306c21839abf1d841d3ad948de58a16c351870d` | 1903 |
| E6 | chat | fixture = WM ownedAreas `LOT·Workorder 진행 상태` → `LOT 진행 상태` 인 `eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json` | `8f9db8bec5cd49c0a1495623cfb112966cb8dba9c7c53efe5e674878a2f99f5e` | 1913 |
| N1 | chat | question `실패했어?` → `실패했니?`(뜻이 같은 대조군) | `8b5a1c71782fc29be62ae04deab1cf7424cc284108ddfe8663515485211b7b4e` | 1916 |

- 사전 계수: `eval/f0/v3/input-audit-probe-g08.json` = Java `f0 probe-g08-render`(같은 manifest·기준 역할·`PRESENCE_V1`·template `40c21f34…b541`) → `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`) 8행, `OVER_BUDGET 0`, 최대 1918. 측정 명령은 송신 직전 이 표로 메시지마다 계수·예산을 확인한다(표에 없으면 `INPUT_AUDIT_MISSING`, 요청 0).

### 1.3 기록 축(원문 금지)

- 시험마다: `outputSha256`·`sameAsBaselineOutput`, t(`attemptDiagnostics.t.value`), steps 의 `toolConsistency`, `stepsIncludeLogsSearch`(YES·NO·NOT_EVALUABLE), prompt/completion 토큰(chat usage·generate `prompt_eval_count`/`eval_count`), 지연 ms, `finishReason`, 첫 시도 판정(valid·errorCode·stepsDetail, `f0-judge-v2` 그대로), messagesSha256·사전 계수·format SHA, 원문 없는 `attemptDiagnostics` 전체.
- E0·E-ORD 추가: 선택 토큰을 이어 붙인 생성 텍스트에서 ① `"steps":[` 직후 첫 비공백 문자를 정하는 토큰 위치의 top_logprobs 를 `]`(CLOSE_BRACKET) 대 `"`(QUOTE) 로, ② `"t":"` 직후 첫 문자를 정하는 토큰 위치의 top_logprobs 를 `I`(INC) 대 `U`(UNS) 로 분류한 확률. 후보 토큰 문자열은 이 두 후보 집합에 드는 것만 남기고, 그 밖 후보는 개수·확률 합만 남긴다. logprobs 필드가 없으면 `status=ABSENT` 로 기록한다. 확률이 문법 적용 전 값인지 후 값인지는 미확정이다.
- 결과 파일 `eval/f0/results/<runId>-probe-g08/probe-g08-results.json` 과 `run-binding.json` 만 쓴다. 응답 원문·API 응답 원문은 저장·출력하지 않는다. 측정 중 endpoint 오류(timeout 제외)는 지금까지의 행을 `complete=false`·`abortReason` 으로 보존하고 exit 5, timeout 은 그 행을 `status=TIMEOUT` 으로 남기고 다음 시험으로 간다. 재실행은 새 runId 로만 한다(같은 디렉터리 `run-binding.json` 이 있으면 `RUN_BINDING_EXISTS`).

### 1.4 판별 기준(원인 분석 보고 §6 표 그대로, 실행 전 고정)

| # | 바꾸는 것 | 판별 기준 |
|---|---|---|
| E0 | (대조) `/api/generate` raw, 렌더된 프롬프트 그대로, `format` = 정렬된 스키마, `logprobs`, `top_logprobs=5` | SHA 가 `d648833f` 면 경로가 동등하다고 본다. 다르면 generate 기반 실험(E0·E-ORD)은 무효다. 같은 호출에서 `"steps":[` 다음 토큰의 `]` 대 `"` 확률과 t 값의 INC 대 UNS 확률을 기록한다(확률이 문법 적용 전 값인지 후 값인지는 미확정). |
| E-ORD | E0 과 같고 `format` 의 properties 만 t, eqp, from, to, qRef, steps 순서 | t=INC 이고 steps 에 Tool 이 있으면 "생성 순서에 따른 분열"을 확정한다. t=INC 인데 steps 가 `[]` 면 steps 쪽 내용 단서가 원인이고, t=UNS 면 경계 문항 판단 자체가 UNS 쪽이다. |
| E1 | question 의 `WORK_ORDER_REPLY` → `메시지` | Tool 이 생기면 (가)(낱말 겹침)를 지지한다. |
| E2 | question 에서 `WM 으로 보낸 ` 삭제 | Tool 이 생기면 시스템 이름 요인을 지지한다. |
| E3 | `가 왜 실패했어?` → `가 실패한 원인 분석해줘` | Tool 이 생기면 원인·분석 단서 부재 요인을 지지한다. |
| E5 | `plan-v3-i.txt:16` 의 `TC 자료로 대신 채우지 않는다.` 한 문장 삭제 | Tool 이 생기면 (나)를 지지한다. |
| E6 | fixture WM ownedAreas 의 `LOT·Workorder 진행 상태` → `LOT 진행 상태` | E1 과 함께 (가)를 양쪽에서 확인한다. |
| N1 | 뜻이 같은 무관 변경(`실패했어?` → `실패했니?`) | 미세 교란 민감도 대조다. N1 에서도 바뀌면 E1~E6 의 R=1 결과를 요인 근거로 쓰지 않는다. |

- 적용 규칙(고정):
  - "Tool 이 생긴다" = 그 시험 첫 시도의 steps 가 비지 않음(`toolConsistency` ≠ `EMPTY_FOR_TOOL_TASK`·steps 에 Tool 1개 이상). 지지 강도 기록에는 `stepsIncludeLogsSearch`·t 를 함께 적는다.
  - **E0 출력 SHA ≠ `d648833f…b242` 이면 E0·E-ORD 의 결론은 무효**이며 E-ORD 판별을 적용하지 않는다(logprob 수치는 기록만).
  - **N1 출력 SHA ≠ `d648833f…b242` 이면 E1~E6 결과를 요인 근거로 쓰지 않는다**(기록만).
  - E1~E6·N1 의 기준선은 g·h·i 에서 반복된 기록 SHA 이며, 이 명령 안에서 chat 기준선을 다시 보내지 않는다(사용자 승인 범위 8건).
  - R=1·단일 결정 출력 근거이며 정확도·지연 합격을 주장하지 않는다. 결과 해석·다음 후보 결정은 root·사용자가 한다.

## 2. 실행 전제·순서(C19/C20 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 실행 전제:
  1. 측정 직전 사용자가 Jetson 에서 `sudo systemctl restart ollama` 를 실행한다(세션은 재시작·모델 로드·pull·관리 API·num_ctx·서비스 변경 0).
  2. warmup 이 두 모델을 적재할 때까지 읽기 전용으로만 확인한다: 서비스 active·ActiveEnterTimestamp(재시작 시각 이후)·NRestarts 0, 재시작 이후 journal `library=cpu` 0·`Load failed` 0·`out of memory` 0, `GET /api/ps` 에 `qwen3:4b-instruct`(digest `0edcdef3…8ba0`)·`bge-m3:latest`(digest `79076464…6bab`) 2모델, 둘 다 ctx 4096·size_vram=size(100% GPU). 하나라도 아니면 NOT_RUN 으로 기록하고 중단한다.
  3. 터널: Plan P03 0·1단계와 같다(`ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`, 별도 창). 로컬 11434 사전 미사용을 확인한다.
- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`, `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0). `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 를 한 번 정하며 이 등록의 회차 ID 는 `"$runId-probe-g08"` 이다. 결과 디렉터리 사전 부재를 확인한다.

1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-probe-g08.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-probe-g08.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-probe-g08/preflight.json"` → `F0_PREFLIGHT_OK 2`(exit 4 면 중단·NOT_RUN 기록, 재시작·로드 금지)
3. `.\gradlew.bat :gateway-eval:run --args="f0 probe-g08 --prereg eval/f0/PREREG-v3-probe-g08.md --endpoint http://127.0.0.1:11434 --probe eval/f0/v3/probe-g08/probe-g08-v1.json --run-id $runId-probe-g08 --out eval/f0/results/$runId-probe-g08"` → `F0_PROBE_G08_COMPLETED 8`
4. 터널 종료. 실행 뒤 같은 읽기 전용 확인(서비스·journal·2모델 상주·메모리)을 1회 기록한다.

- chat-template capture·`f0 report` 단계는 없다. template 은 측정 세션이 `/api/show` 로 읽은 SHA 를 run-binding 에 기록하고, 사전 계수 표 template SHA·렌더러 등록과 다르면 요청 전에 거부된다(`INPUT_AUDIT_RUNTIME_MISMATCH`·`TEMPLATE_RENDERER_UNVERIFIED`). 아래 `runtimeCaptureDir` 는 v3 형식상 값이며 이 등록은 쓰지 않는다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v3-probe-g08",
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
    "inputAudit": "eval/f0/v3/input-audit-probe-g08.json"
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
    {"path": "eval/f0/v3/input-audit-probe-g08.json", "sha256": "c75a6ffeb11f7f184a9bba0328659129f8ef16d37d13a712b0f0e93b2aae19db"},
    {"path": "eval/f0/v3/probe-g08/probe-g08-v1.json", "sha256": "99968394fca22695ebcb498e920623bf7fa916429752395ba337ec164f5d8422"},
    {"path": "eval/f0/v3/probe-g08/plan-v3-i-e5.txt", "sha256": "cc67c15c92c6766b814786c98ccdfe6fd89ec43b94a53a5abe92d93b79f212df"},
    {"path": "eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json", "sha256": "8f4783b2e0a69182aac294c59e61030db5cd155d0c1e4343516d80026682b3c9"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "b2109add63d14049369ceb5fb1a4e5e30c730f4e9b409ac8ac579d3a620a0c62",
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
