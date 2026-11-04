# F0 진단 사전등록 v4-diag-v2-t0-b — 교체 추론 서버(qwen3:4b = Qwen3-4B-Thinking-2507) 진단: v2 계약 프롬프트 arm · temperature 0 · 답변 토큰 계수 방법 (b) (v4-b 묶음 공통 설계 포함)

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. 이 등록의 회차는 `f0 report` 가 `diagnostic-report.md` 만 쓰고 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다(`--out`·`--rejudge`·`--spec-record-check` 는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2). F0 게이트 PASS 근거가 될 수 없고, 문항·결과는 정식 분모·판정 밖이다(SPEC §16 F0, Plan P03 v4 ⑨).
- **대체 관계**: 이 문서는 `PREREG-v4-diag-v2-t0.md`(C22/C23 등록, 답변 토큰 계수 방법 (a))를 대체하는 새 등록이다. 바뀐 것은 답변 토큰 계수 방법 (a)→(b)(`parameters.answerTokenMethod = LOCAL_TOKENIZER_PIN_V1`, 지원 입력 `eval/py/count_answer_tokens_v4.py` 추가)·교차 확인 기록·그에 맞춘 하네스뿐이고, arm·temperature·예산·답변 상한·입력 audit·골든·fixture·경계 케이스·메시지 입력·프롬프트·런타임 신원은 바이트 그대로다.
- 이 문서는 v1 `PREREG.md`·v2 `PREREG-v2.md`·v3 `PREREG-v3-*.md`(Jetson 회차, C1~C20)와 기존 v4 등록 6문서(`PREREG-v4-{diag-v2-t0,diag-i-t0,diag-v2-t06,diag-i-t06,regression-v2,regression-i}.md`, C22/C23), 그 입력·측정 결과(1단계 실측 `eval/f0/results/20261104T034413Z-v2-t0/` 포함)·보고서·증거·capture 를 바꾸지 않는다. 기존 v4 6문서는 이번 하네스 변경으로 `harnessDigest` 가 stale 이 되어 `PREREG_INPUT_CHANGED` 로 기계 거부된다(정상 — 문서는 고치지 않음).
- 초안 상태에서는 측정 명령·`f0 prereg-check` 가 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부된다(요청 0). 등록은 사용자 허용(2026-11-04) 로컬 C24(등록 대상·하네스·이 초안) → C25(`registrationStatus=REGISTERED`, `registeredCommit`=C24) 두 commit 으로만 한다(push·`--force` 없음).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-04 작업 트리 실제 바이트로 계산했다. 하네스 코드가 바뀌면 다시 계산해야 하며, 불일치는 `PREREG_INPUT_CHANGED` 로 기계 거부된다.

## 1. 진단 설계 — v4-b 묶음 공통(다른 v4-b 등록 5문서는 이 절을 참조한다)

### 1.1 근거·범위

- 정본: SPEC SHA `bb91146a…d0de4`(기준선 seq5) §14.5(사고 상한 미정 진단 = 요청 `max_tokens` 를 ctx 상한(8192 − 입력 상한)으로 두어 자연 사고 길이 관찰, 답변 상한 초과·계수 불가 = 그 시도 실패·1회 재시도, 답변 토큰 계수 방법은 열린 항목 ⓒ — F0 진단 뒤·정식 GATE 등록 전 확정, 1199행), §16 F0(진단 등록 0.6 비교 arm, 겹친 호출만 지연 제외, digest·context·상주 변화 = 회차 무효, 1382행), Plan P03 v4 흐름 ④~⑨. SPEC·Plan 은 바꾸지 않는다.
- 사용자 결정(2026-11-04, 기존): 프롬프트 arm = v2 계약 프롬프트(필수) + v3 마지막 구성 i, temperature arm = 0(정식 기준) + 0.6(진단 비교, DQ-1), 사고 상한 없음·답변 상한 192/256 초과 = 실패(I2), 겹친 호출만 지연 제외(I5).
- 사용자 결정(2026-11-04, 이 등록): **방법 (b) — 답변 토큰 = 서버 응답 content(답변 부분)를 고정 tokenizer pin 으로 로컬 계수**, 원문은 저장하지 않고 메모리에서 개수만 산출. **교차 확인** — 같은 pin 으로 `reasoning` 필드(사고문)도 로컬 계수해 `로컬(사고) + 로컬(답변)` 과 서버 `completion_tokens` 의 차이를 시도마다 기록하고 차이 분포를 진단 보고서에 낸다. 크게 어긋나는 호출은 표시만 하고 판정은 바꾸지 않으며, 어긋남 판정 기준값은 근거가 없으므로 **관측 후 확정**한다(이 등록은 정하지 않음). 답변 상한 초과 = 실패(1회 재시도) 규칙은 유지.
- 진단은 판정 규칙(`f0-judge-v2`)·oracle·분모를 바꾸지 않는다. 결과는 ⑧(응답 파싱·답변 토큰 계수 방법·예산 규칙 확정 — 이 묶음의 교차 확인 분포가 방법 (b) 확정의 근거 입력)과 ⑩(사고 상한 `T_cls`·`T_pln` 제안)의 근거 입력이다.

### 1.2 1단계 실측 관측(기존 `PREREG-v4-diag-v2-t0` 등록, 회차 `20261104T034413Z-v2-t0`, 등록 §2 의 1~7단계·postflight, run B 착수 전 중단 — Devreport §12.30 끝 "v4 진단 1단계 실측" 소절)

- 계수 동등성: token-golden 13행 `promptTokens == auditInputTokens` **13/13** — v4 렌더러 `OLLAMA_GO_QWEN3_THINKING_V1`·Thinking pin 계수 = 서버 `prompt_tokens`. 이 pin 이 서버 모델 공식 tokenizer 와 바이트 같다는 사실(Devreport §12.29 2))과 함께 방법 (b) 가 같은 pin 을 쓰는 근거다.
- `max_tokens:1` 계수 호출 17행(token-golden 13 + usage-probe 4): 17/17 `finishReason=length`·`completionTokens=1`·`contentCodepoints=0`·`separateFields=["reasoning"]`·content 사고 표지 0·**`reasoningTokens=null`**.
- 판별(기존 §1.4 기준): 사고문 분리 필드 = `reasoning`(분리), `max_tokens` 의 사고 포함 = **포함**(completion 에 사고 포함), 사고 도중 상한 `finish_reason` = `length`, 방법 (a) `completion − reasoning_tokens` = **불가** — Ollama 0.40.13 `openai/openai.go` 의 `Usage` 에 `completion_tokens_details` 가 없고 `ToUsage` 도 설정하지 않는다. 그래서 하네스 방법 (a)는 전 시도 `ANSWER_TOKENS_UNCOUNTABLE` 이 되며, 이 등록은 방법 (b)로 대체한다.
- G-USAGE: `usage.json` `equalOnCacheHit{usage:true, evalCount:true}`(일치). preflight·postflight 의 모델 목록(name·digest·context_length) 동일, 측정 창 GIN 요청 78건 전부 loopback(NORI 밖 출발 0). run 지연은 측정하지 않아 1.7 의 예상 시간은 다시 계산하지 않았다.

### 1.3 arm 구성(6 등록, 각 R=1, 경로 A·B 는 같은 등록 안의 두 명령)

| 등록 | 대체 대상 | 프롬프트 arm | 분류(B·경계·usage·token) / 계획(A) 프롬프트 | Context 투영 | `responseFormat` | `retryHint` | temperature | 골든 | boundary-gen | 입력 audit |
|---|---|---|---|---|---|---|---|---|---|---|
| `PREREG-v4-diag-v2-t0-b`(이 문서) | `PREREG-v4-diag-v2-t0` | V2 | `classify-v2` / `plan-v2` | FULL(키 없음) | NONE | 키 없음 | 0 | 22문항 | 9케이스 | `input-audit-v4-v2.json` |
| `PREREG-v4-diag-i-t0-b` | `PREREG-v4-diag-i-t0` | I | `classify-v3-f` / `plan-v3-i` | PRESENCE_V1 | JSON_SCHEMA_V1 | STEPS_DETAIL_V1 | 0 | 22문항 | 9케이스 | `input-audit-v4-i.json` |
| `PREREG-v4-diag-v2-t06-b` | `PREREG-v4-diag-v2-t06` | V2 | 같음 | FULL | NONE | 키 없음 | 0.6 | 22문항 | 9케이스 | `input-audit-v4-v2.json` |
| `PREREG-v4-diag-i-t06-b` | `PREREG-v4-diag-i-t06` | I | 같음 | PRESENCE_V1 | JSON_SCHEMA_V1 | STEPS_DETAIL_V1 | 0.6 | 22문항 | 9케이스 | `input-audit-v4-i.json` |
| `PREREG-v4-regression-v2-b` | `PREREG-v4-regression-v2` | V2 | 같음 | FULL | NONE | 키 없음 | 0 | 30문항(22 + 회귀 8) | 실행 안 함 | `input-audit-v4-regression-v2.json` |
| `PREREG-v4-regression-i-b` | `PREREG-v4-regression-i` | I | 같음 | PRESENCE_V1 | JSON_SCHEMA_V1 | STEPS_DETAIL_V1 | 0 | 30문항 | 실행 안 함 | `input-audit-v4-regression-i.json` |

- 6 등록 모두 `answerTokenMethod = LOCAL_TOKENIZER_PIN_V1` 이다. V2 arm = `PREREG-v2.md` 와 같은 요청 구성(전체 Context, `response_format` 없음, 고정 오류 코드 재시도), I arm = C17/C18 등록 구성 i. 두 arm 의 비교가 곧 "`response_format`+thinking" 관측이다(1.6).
- 같은 프롬프트 arm 의 t0·t06 두 등록은 temperature 값 하나만 다르다(입력·audit·판정·예산·계수 방법 바이트 동일).
- 개발 회귀는 temperature 0 만 두고 boundary-gen 을 실행하지 않는다(기존 v4 등록과 같은 근거: SPEC §16 F0 의 0.6 은 진단 비교 arm, 회귀 audit 의 `boundary/*` 108행이 같은 arm 진단 audit 와 동일).
- 하나의 사전등록은 프롬프트·temperature 를 하나씩만 담으므로(하네스 `Prereg.Document`) arm 마다 등록을 나눴다(v3 `diag-a/b/c`·기존 v4 선례). 문서 이름의 `-b` 는 답변 토큰 계수 방법 (b) 재등록을 뜻한다(경로 B 와 무관).

### 1.4 공통 런타임·예산·입력 audit

- 런타임(G-INFER-ENV `docs/gates/f0/runtime-env-20261104T021454Z.json`): 서버 192.168.0.160 Ollama 0.40.13, 생성 모델 `qwen3:4b` digest `359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7`(등록 `generationModelDigest`, preflight 불일치 = `MODEL_DIGEST_MISMATCH` exit 6), `bge-m3` digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`(상주만 검사), context 8192(`OLLAMA_CONTEXT_LENGTH`, 요청에 `num_ctx` 미포함), template SHA `2d54db2b…97f9`(Go template, 렌더러 `OLLAMA_GO_QWEN3_THINKING_V1` — `eval/f0/v4/chat-renderers-v4.json`, 근거 `docs/gates/f0/template-review-v4.md` §5·§7), tokenizer `Qwen/Qwen3-4B-Thinking-2507` pin `aeb13307…dae4`(Instruct pin 과 바이트 동일, 서버 모델 공식 tokenizer 와 바이트 동일).
- 예산(`Prereg.Budget.V4_DIAGNOSTIC`): 입력 상한 3,072/3,072, 요청 `max_tokens` 5,120/5,120(= 8192 − 3,072, 사고 상한 없음), 답변(사고 제외) 상한 분류 192·계획 256(`answerLimitTokens` — 초과 = `ANSWER_LIMIT_EXCEEDED`, 계수 불가 = `ANSWER_TOKENS_UNCOUNTABLE`, 둘 다 1회 재시도·재시도 요지 `USAGE_EXCEEDS_LIMIT`), 경계 판정 상한 192(답변 토큰), 종료 여유 1, R=1, 판정 `f0-judge-v2`. 답변 토큰 = 방법 (b)(1.5).
- `requestTimeoutMs` 600,000(10분): 새 장비 처리 속도는 미측정이다. 유일한 실측 근거인 Jetson ctx 8192 이력(SPEC §14.5 표: prefill 350.9 tok/s, 생성 12.5 tok/s)으로 최대 호출(입력 3,072 + 출력 5,120)을 계산하면 3,072/350.9 + 5,120/12.5 ≈ 8.8 + 409.6 ≈ 418초다. `OLLAMA_NUM_PARALLEL=1` 이라 192.168.0.201 요청 뒤 서버 큐 대기가 더해질 수 있어 약 1.4배 여유를 둔 값이다. timeout 은 무효가 아니라 그 시도의 실패(`TIMEOUT`, callWindow 기록)다.
- 호출 전 입력 audit: 기존 v4 등록의 4개 파일을 **재사용**한다. 근거: 이번 하네스 변경은 응답 처리(계수·기록·보고)만 바꾸고 요청 메시지 조립·렌더러·예산·재시도 요지 집합·입력 계수기를 바꾸지 않았으며, 현재 하네스로 같은 명령(Java `f0 input-audit-render --runtime-profile INFER_V4 --renderers eval/f0/v4/chat-renderers-v4.json --template-sha256 2d54db2b…97f9` → `eval/py/count_input_audit_v4.py`, pin `aeb13307…dae4`)을 다시 실행한 결과가 4개 모두 렌더 입력(jsonl)·계수 표(JSON) **바이트 동일**이었다(2026-11-04, `cmp` 차이 0).

| audit | 행 | OVER_BUDGET | 최대 입력(A 초회/재시도 · B 초회/재시도 · 경계 초회/재시도 · usage · token) | 최대 입력 + 요청 max_tokens | SHA-256 |
|---|---|---|---|---|---|
| `input-audit-v4-v2.json` | 651 | 0 | 1250/1284 · 1090/1124 · 2703/2737 · 1056 · 2795 | 7857 | `65f54494df5e34a2a17c0f3ca981f8d20630a1140aa8e87fec1379427e978c5b` |
| `input-audit-v4-i.json` | 717 | 0 | 1943/1978 · 1392/1426 · 3025/3059 · 1378 · 2795 | 8179 | `ee16e33d69836db3c3247fd82822536aa994ff597a62b964ab1f0cd6424f8384` |
| `input-audit-v4-regression-v2.json` | 843 | 0 | 1250/1284 · 1090/1124 · 2703/2737 · 1056 · 2795 | 7857 | `660780babeea53200a0a7eb63f0c268bce3f69872e7e0a616bf5cd4ab4c4d472` |
| `input-audit-v4-regression-i.json` | 933 | 0 | 1959/1994 · 1408/1442 · 3025/3059 · 1378 · 2795 | 8179 | `5d2ada83e0d0a1b574a4577ecfb04ded29a6c0cfaa461b4f85a673f518814136` |

- 최대 입력 + 요청 `max_tokens` 는 I arm 경계 재시도 3,059 + 5,120 = 8,179 ≤ 8,192(여유 13)다. 1단계에서 token-golden 13/13 동등성을 확인했지만, 이 등록도 1.8 의 동등성 게이트를 장시간 회차 전에 다시 둔다(측정 결속은 등록마다 따로다).

### 1.5 답변 토큰 계수 방법 (b)·교차 확인

- **정의**: 답변 토큰 = 응답 `choices[0].message.content` 전체의 pin 토큰 수(`tokenizers` 0.23.3 `encode(text, add_special_tokens=False)` 개수 — 입력 audit 계수 규칙과 같음). 사고문은 별도 `reasoning` 필드라 답변에 넣지 않는다. content 에 `<think>`/`</think>` 표지가 있으면(사고 혼입) 기존과 같이 계수 불가 실패다. 짝 없는 surrogate 가 든 문자열도 셀 수 없어 계수 불가다.
- **판정**: 답변 토큰 > 답변 상한(분류 192·계획 256) = `ANSWER_LIMIT_EXCEEDED`, 계수 불가 = `ANSWER_TOKENS_UNCOUNTABLE`, 둘 다 그 시도 실패·1회 재시도(재시도 요지 `USAGE_EXCEEDS_LIMIT`, 사전 audit 이 계수한 재시도 메시지 집합 그대로). 사고 토큰은 답변 상한에 넣지 않는다. 경계 판정 상한 192 도 이 답변 토큰에 적용한다.
- **계수 경로(원문 비저장)**: 하네스(`AnswerTokenCounter`)가 측정 세션 시작 때 endpoint 호출 전에 `$env:AIGW_PYTHON -I -B eval/py/count_answer_tokens_v4.py --tokenizer <Thinking pin> --expect-sha256-file <pin 파일>` 을 상주 프로세스로 한 번 띄운다. 계수기는 pin SHA 를 검사하고(불일치 exit 3) `READY <tokenizer SHA> <tokenizers 버전>` 을 쓰며, 하네스는 그 SHA 가 세션 tokenizer pin 과 같을 때만 측정을 시작한다. 응답마다 `[답변 content, 사고문]` 을 **stdin 한 줄(JSON, 비 ASCII 는 `\u` 이스케이프)** 로 보내고 stdout 한 줄의 `[정수|null, 정수|null]` 만 받는다. 문자열은 파일·명령줄·로그·예외 메시지에 쓰지 않고, 계수기 stderr 는 버리며(실패는 고정 코드·exit 코드만), `-B` 로 bytecode 캐시도 쓰지 않는다. 계수기의 pin 검사·오류 코드 함수는 `count_input_audit_v4.py` 의 것을 그대로 불러 쓴다(같은 로직 중복 없음).
- **교차 확인(판정 불변)**: 시도마다 `localTokenCounts[]` = `{answerTokens, thinkingTokens, completionMinusLocal}` 을 결과 행에 남긴다. `completionMinusLocal` = 서버 `usage.completion_tokens` − (로컬 사고 + 로컬 답변)이며, 사고문 분리 필드가 없거나 셀 수 없으면 null 이다. 1단계 관측(`max_tokens` 의 사고 포함)대로 completion 에 사고가 포함되면 차이는 사고·답변 경계의 특수 토큰(`</think>` 등)으로 생기는 작은 상수에 모일 것으로 예상하지만(예상 — 미관측), 이 등록은 그 값을 가정하지 않는다. token-golden·usage-probe 의 `max_tokens:1` 행에도 같은 값 `localTokenCount` 를 남긴다.
- **보고**: `diagnostic-report.md` 의 "v4 답변 토큰 로컬 계수 교차 확인" 절이 출처(A·B·boundary)·시도(초회·재시도)별과 전체의 시도 수·답변 계수 불가 수·사고 계수 없음 수·차이 계산 수·**차이 min/p50/p95/max(nearest-rank)·0 아닌 차이 건수·차이 값=건수 전체**를 원문 없이 낸다.
- **어긋남 표시 기준값: 관측 후 확정(이 등록은 정하지 않음)**. 근거가 없으므로 임계로 판정·제외하지 않으며, 차이 값=건수 전체를 보고해 ⑧ 에서 기준값을 정하는 입력으로 쓴다.

### 1.6 Plan P03 v4 ⑥ 최소 입력 항목 — 관측 필드(원문 0)와 판별 기준

| 항목 | 관측 필드(결과 파일) | 판별 기준(진단 보고 문구) |
|---|---|---|
| 사고문 분리 필드 | 시도별 `thinkingObservations[].separateFields`·`contentThinkOpen`·`contentThinkClose`·`thinkingCodepoints`(A·B·경계 행) | **분리** = 사고 관측 시도 전부에서 분리 필드 이름이 1개 이상이고 content 표지 0(1단계 17/17 `reasoning`). **content 혼입** = 표지 1건 이상(그 시도는 계수 불가 실패). 분리 필드도 표지도 없으면 **사고 미관측** |
| `max_tokens` 의 사고 포함 여부 | token-golden·usage-probe 의 `max_tokens:1` 행 `finishReason`·`completionTokens`·`thinking.contentCodepoints`·`thinking.separateFields`·`localTokenCount` | **포함** = 전 행 `finishReason=length`·`completionTokens=1`·`contentCodepoints=0`(1단계 17/17 포함). 그 밖이면 값 분포만 기록하고 ⑧ 에서 해석 |
| 답변 토큰 계수 방법(열린 항목 ⓒ) | 시도별 `answerTokens`(= 로컬 답변)·`localTokenCounts[]`·`completionTokens`·`thinking.reasoningTokens` | 방법 (a) = 1단계에서 **불가 확정**(`reasoningTokens` 17/17 null). 방법 (b) **사용 가능** = 전 시도에서 `answerTokens` null 0건(표지·surrogate 0). 교차 확인 차이가 전 시도 같은 값 c 이면 "서버 completion = 사고 + 답변 + c" 로 보고, 값이 흩어지면 분포만 보고하고 ⑧ 에서 해석(판정 불변, 1.5) |
| 사고 도중 상한 도달 `finish_reason` | 위 `max_tokens:1` 행 + 시도별 `attemptDiagnostics.finish`·`contentCodepoints`·`thinkingCodepoints` | `max_tokens:1` 행의 `finishReason` 값이 그 값이다(1단계 `length`). 측정 시도 중 finish `LENGTH`·content 0·사고 코드포인트 > 0 인 시도 = 자연 사고가 5,120 을 넘은 시도이며 건수를 보고한다(T 근거 분포의 꼬리) |
| `response_format` + thinking | I arm 대 V2 arm 의 사고 관측 비율·로컬 사고 토큰(`localTokenCounts[].thinkingTokens`) 분포·JSON 유효율, I arm 명령의 서버 거부 | **거부** = I arm `run`·`boundary-gen` 이 `ENDPOINT_HTTP_ERROR`(exit 5, 부분 결과 보존, JSON 모드·비제약 재송신 0). **사고 단계 제약** = I arm 시도의 사고 관측이 없고(분리 필드 없음·로컬 사고 토큰 0 또는 null) V2 arm 은 있음. **답변만 제약** = 두 arm 모두 사고 관측 + I arm JSON 유효 |
| properties 순서 | 시도별 `outputKeyOrders`(최상위 키 출력 순서, 스키마 키 이름만 — 그 밖 키면 null) | I arm: `JSON_SCHEMA_V1` 은 properties 를 키 정렬 순서로 송신하므로 출력이 정렬 순서(`eqp,from,qRef,(steps,)t,to`)면 "송신 순서를 따름", 아니면 "따르지 않음". V2 arm: 스키마 없이 프롬프트 표시 순서를 따르는 비율. **한계: 서버가 선언 순서를 보존하는지(재정렬 여부)는 선언 순서 송신 arm(`JSON_SCHEMA_PLAN_ORDERED_V1`)이 이 묶음에 없어 판별할 수 없다** |
| `/api/ps` 필드 | `preflight.json`·`postflight.json`(name·digest·context_length) | 두 파일 동일 + 생성 모델 digest = 등록값 + `context_length` 8192 + `bge-m3` 상주 |
| G-USAGE | `usage.json` `equalOnCacheHit{usage, evalCount}`(이 문서만) | 둘 다 true = 일치(1단계 일치). false 면 Plan G-USAGE 불일치 경로(SPEC 소유자 판단, P18 2차 방어 보류) |
| (temperature 비교) | t0·t06 등록 쌍의 finish `LENGTH` 건수·로컬 사고 토큰 분포·유효율 | 비종결(사고 5,120 도달) 건수와 분포 차이를 보고만 한다. 0 에서 비종결이 많아도 자동 전환 0(SPEC §16 F0) |

- 사고 출력 개수는 `diagnostic-report.md` 의 "v4 사고 출력 관측" 절이(그 절의 `reasoning_tokens` 보고 열은 서버 미보고라 `0/시도 수`, 사고 토큰 분포 열은 `-` 로 나온다), 로컬 사고·답변 토큰과 교차 확인 차이는 1.5 의 절이 원문 없이 집계한다(`F0ReportWriter`). 사고 상한 T 제안(⑩)의 사고 토큰 분포 근거는 `localTokenCounts[].thinkingTokens` 다.

### 1.7 호출 수 상한과 예상 소요 시간(기존 v4 등록과 같은 근거로 재기재)

- 등록 하나의 생성 호출(요청 `max_tokens` 5,120) 수: 진단 = (B 22 + A 22 + 경계 9) × 시도 1~2 = **최소 53·최대 106**, 회귀 = (B 30 + A 30) × 1~2 = **최소 60·최대 120**. 하네스는 문항당 최대 2회(초회 + 1회 재시도)만 보낸다.
- 이 문서만 계수 호출(출력 1): token-golden 13 chat + 13 flush generate, usage-probe 4 chat + 4 generate raw + 4 flush = **38회**(prefill 만, 입력 ≤ 2,795).
- 묶음 합계: 생성 호출 **최소 332·최대 664**(4 × 53 + 2 × 60 / 4 × 106 + 2 × 120) + 계수 38. `/api/ps`(호출 직전 상주 확인)·`/api/show`·`/api/tags` 는 추론이 아니라 세지 않는다. 로컬 계수(응답마다 계수기 한 줄 왕복)는 추론 호출이 아니며 시간 표에 넣지 않는다(미측정).
- 방법 (a) 에서는 계수 불가로 모든 문항이 재시도되어 최대 호출 쪽이 확정적이었으나(1단계 판단), 방법 (b) 에서는 실제 판정 실패(종료·스키마·답변 상한 등)만 재시도된다. 실제 재시도율은 미측정이므로 상한·하한을 그대로 둔다.
- 호출당 시간 = 입력/prefill 속도 + 출력 토큰/생성 속도. 새 장비 속도와 사고 길이는 1단계에서도 측정하지 않았으므로(run 미실행) 아래 값 중 **최악값만 근거 있는 상한**이고 가정값은 근거 없는 계획용 수치다.

| 구분 | 호출당 | 묶음 최소 호출(332) | 묶음 최대 호출(664) | 근거 |
|---|---|---|---|---|
| 최악값 A(timeout 상한) | 600초 | 55.3시간 | 110.7시간 | 등록 `requestTimeoutMs` — 하네스가 시도를 끊는 절대 상한 |
| 최악값 B(출력 상한 × Jetson 속도) | 418초 | 38.6시간 | 77.2시간 | 입력 3,072/350.9 + 출력 5,120/12.5(SPEC §14.5 Jetson ctx 8192 실측 이력 — 새 장비 값 아님) |
| 가정값(근거 없음) | 약 40초 | 약 3.7시간 | 약 7.4시간 | 가정: 생성 40 tok/s·prefill 1,000 tok/s·평균 출력(사고+답변) 1,500 토큰 |

- 등록별 최악값 B: 진단 1건 53~106호출 = 6.2~12.3시간, 회귀 1건 60~120호출 = 7.0~13.9시간. 계수 호출 38회는 최악값 B 기준 약 5분(2,795/350.9 ≈ 8초 × 38)이다.
- 계획 규칙: 첫 등록(이 문서) `run B` 가 끝나면 `latencyMs` 중앙값으로 나머지 예상 시간을 다시 계산해 사용자에게 보고한다(등록·순서 변경 없음, 계속 여부는 사용자 결정).

### 1.8 무효 규칙

- **회차 전체 무효(NOT_RUN, 결과 보존·같은 문항 재측정)**: preflight·postflight·측정 중 상주 확인에서 생성 모델 digest·`context_length`·두 모델 상주가 등록값과 다름(`MODEL_DIGEST_MISMATCH` exit 6, `CONTEXT_LENGTH_MISMATCH` exit 4, `MODEL_NOT_RESIDENT` exit 4/5). 모델 load·pull·unload·서비스 재시작·`num_ctx` 변경으로 복구하지 않는다.
- **계수 동등성 게이트(E0 류 대체)**: token-golden 13행의 `promptTokens` 가 같은 행 `auditInputTokens`(이 등록 audit 의 `token/<id>` 계수)와 **13/13 같아야** 다음 단계(usage-probe·run)로 간다. 하나라도 다르면 묶음 전체를 중단·보고한다(렌더러·tokenizer pin 이 서버와 동등하지 않으면 audit 의 예산 근거와 방법 (b) 의 pin 근거가 무효). 각 회차 진단 보고서의 "실제 `usage.prompt_tokens` 대 사전 audit 계수 — 작음/큼" 이 0/0 이 아니면 그 회차 보고에 원인을 적는다(작음 = 잘림 의심, SPEC §14.5 2차 방어).
- **계수기 준비 실패**: `AIGW_PYTHON_MISSING`·`ANSWER_COUNTER_UNAVAILABLE`(·`_EXIT_<n>`)·`ANSWER_COUNTER_TOKENIZER_MISMATCH` 는 endpoint 호출 0·run-binding 작성 전 exit 2 다. 측정이 시작되지 않았으므로 무효가 아니며, 원인(경로·venv·pin)을 고친 뒤 같은 명령을 다시 실행한다.
- **측정 중 계수기 오류(`ANSWER_COUNTER_FAILED`, exit 5)·endpoint 오류(exit 5 — HTTP 오류·응답 형식·연결)**: 그 명령 결과를 `complete=false`·`abortReason` 로 보존하고, 같은 결과 디렉터리로 재실행하지 않는다(재측정은 새 회차 ID). `response_format` 거부는 1.6 의 관측 사실로 기록한다.
- **E0 류 경로 동등성 실험은 두지 않는다**: v4 측정 호출은 모두 같은 `/v1/chat/completions` 경로다. 계수 경로 동등성은 위 게이트로 확인한다. usage-probe 의 `/api/generate` raw 지표는 G-USAGE 비교용이며 측정 판정에 쓰지 않는다.
- **공유 서버 겹침**: 회차 무효가 아니다. 시도별 `callWindows`(송신~수신 UTC)와 서버 GIN 로그의 NORI 밖 요청 구간이 닫힌 구간으로 교차한 문항 회차의 지연 표본만 `f0 share-exclude` 집계에서 뺀다(정확도·유효율·재시도율은 그대로). 구간 파일이 없으면 지연은 "겹침 미판정" 으로만 보고한다.
- **시도 실패는 무효가 아니다**: timeout·답변 상한 초과·계수 불가·스키마 실패 시도는 분모에 남는다(등록 판정 그대로). 교차 확인 차이가 크다는 이유로 시도를 무효·제외하지 않는다(1.5).

### 1.9 실행 전제(측정 직전 확인, 이 문서 작성 세션은 실행하지 않음)

- C24/C25 뒤 6문서 `f0 prereg-check` = `PREREG_VERIFIED`, `spec-guard` exit 0(seq5 `bb91146a…d0de4`). 측정 실행은 사용자 승인(G-F0-ACCESS) 뒤에만 한다.
- **계수기 환경**: `$env:AIGW_PYTHON` = `eval/py/requirements.txt` 를 `--require-hashes` 로 설치한 Python 3.12 실행 파일 절대 경로(현재 측정 PC: `C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe`). `tokenizers` 버전이 0.23.3 이 아니면 측정하지 않는다(2절 0단계).
- 서버 상태: 160 은 재시작이 필요 없다(Ollama active, 두 모델 상주, `OLLAMA_KEEP_ALIVE=24h` — ① 기록, 1단계 전후 동일). 측정 직전 상태 확인은 `f0 preflight`(등록 digest·8192·`qwen3:4b`+`bge-m3` 상주)로 하며, 상주가 아니면 load 하지 않고 NOT_RUN 으로 기록한 뒤 사용자에게 보고한다. 측정 동안 load·pull·unload·재시작·설정 변경 0.
- 각 등록의 마지막 측정 명령 뒤 같은 명령으로 postflight(`--out …/postflight.json`)를 남기고 preflight 와 대조한다.
- 출발지 집계(G-INFER-SHARE): 서버 `journalctl -u ollama` GIN 요청 로그(서버 시간대 UTC, `ubuntu` 계정 adm 그룹)에서 그 회차 측정 창(UTC) 안의 출발지별 건수와 NORI 밖 출발 요청 구간(UTC 시작·끝만)만 파이프로 뽑아 `docs/gates/f0/share-window-<회차 ID>.json`(schema `f0-share-intervals-v1`)을 만든다. 로그 원문·IP·경로·본문은 출력·저장하지 않는다. GIN 줄 시각이 완료 시각인지는 첫 수집 때 확인한다(Devreport §12.27 5) 열린 항목 11). 하네스 시각도 UTC(`Z`)라 변환하지 않는다.
- 터널은 로컬 `127.0.0.1:11434` → 서버 `127.0.0.1:11434` 만 쓴다(하네스 endpoint 는 로컬 끝점만 허용).

## 2. 실행 순서(C24/C25 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 실행자는 `llm-notebook` skill 정보로 `$env:AIGW_INFER_SSH_KEY`·`$env:AIGW_INFER_TARGET` 를 설정하고 값 출력 없이 `if (-not (Test-Path -LiteralPath $env:AIGW_INFER_SSH_KEY) -or [string]::IsNullOrEmpty($env:AIGW_INFER_TARGET)) { throw 'AIGW_INFER_ENV_MISSING' }` 로 검사한다(Plan §4.1). 터널(별도 창): `ssh -i "$env:AIGW_INFER_SSH_KEY" -o BatchMode=yes -o ConnectTimeout=12 -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434 $env:AIGW_INFER_TARGET`.
- `$env:AIGW_PYTHON = 'C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe'`(1.9).
- `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')` 는 v4-b 진단 묶음 시작 때 한 번 정한다. 이 등록의 회차 ID 는 `"$runId-diag-v2-t0-b"` 이다.
- `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트), `$env:AIGW_PARENT_RUN_ID = "$runId-diag-v2-t0-b"`.

0. 계수기 환경(값 출력 없이 판정만): `if (-not (Test-Path -LiteralPath $env:AIGW_PYTHON)) { throw 'AIGW_PYTHON_MISSING' }; & $env:AIGW_PYTHON -I -B -c "import tokenizers; print(tokenizers.__version__)"` → `0.23.3` 이 아니면 중단
1. `.\gradlew.bat :gateway-eval:run --args="spec-guard"` → exit 0
2. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md"` → `PREREG_VERIFIED`
3. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-diag-v2-t0-b/preflight.json"` → exit 4/6 이면 중단·NOT_RUN 기록(재시작·로드 금지)
4. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --model qwen3:4b --out eval/f0/chat-template/v4-diag-v2-t0-b"` → `qwen3-4b.template.sha256` 가 `2d54db2b…97f9` 인지 확인(다르면 중단 — 렌더러·audit 무효)
5. `.\gradlew.bat :gateway-eval:run --args="f0 token-golden --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/token-golden-input-v2.jsonl --out eval/f0/results/$runId-diag-v2-t0-b/token-golden.jsonl"`
6. 계수 동등성 게이트(값 출력 없이 개수만): `$rows = @(Get-Content eval/f0/results/$runId-diag-v2-t0-b/token-golden.jsonl | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }); "TOKEN_GOLDEN_AUDIT_MATCH $(@($rows | Where-Object { $_.promptTokens -eq $_.auditInputTokens }).Count)/$($rows.Count)"` → `13/13` 이 아니면 묶음 전체 중단·보고
7. `.\gradlew.bat :gateway-eval:run --args="f0 usage-probe --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/usage-probe-input-v2.jsonl --out eval/f0/results/$runId-diag-v2-t0-b/usage.json"`
8. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-diag-v2-t0-b --out eval/f0/results/$runId-diag-v2-t0-b/B"` → 끝나면 1.7 의 예상 시간 재계산·보고
9. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/plan-v2.txt --repeats 1 --run-id $runId-diag-v2-t0-b --out eval/f0/results/$runId-diag-v2-t0-b/A"`
10. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-diag-v2-t0-b --out eval/f0/results/$runId-diag-v2-t0-b/boundary"`
11. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-diag-v2-t0-b/postflight.json"` → `preflight.json` 과 모델 목록 대조(다르면 1.8 회차 무효)
12. (사용자·실행자) G-INFER-SHARE 구간 파일 `docs/gates/f0/share-window-$runId-diag-v2-t0-b.json` 작성(1.9)
13. `.\gradlew.bat :gateway-eval:run --args="f0 share-exclude --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --results eval/f0/results/$runId-diag-v2-t0-b --intervals docs/gates/f0/share-window-$runId-diag-v2-t0-b.json"` → 경로별 제외 건수·비율·제외 뒤 p50/p95(출력만, 원시 결과 불변)
14. 터널 종료 뒤 `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v4-diag-v2-t0-b.md --run-id $runId-diag-v2-t0-b --results eval/f0/results/$runId-diag-v2-t0-b"`(`--out` 없음 → `diagnostic-report.md` 만, F0 leaf 증거 0)

- 묶음 순서: 이 문서 → `PREREG-v4-diag-i-t0-b` → `PREREG-v4-diag-v2-t06-b` → `PREREG-v4-diag-i-t06-b`(각 문서 §2, 같은 `$runId`). 터널은 진단 4문서 동안 유지해도 된다(각 문서의 report 단계만 터널 불필요). 개발 회귀 2문서(`PREREG-v4-regression-{v2,i}-b`)는 Plan ⑧(진단 결과로 하네스 응답 파싱·답변 토큰 계수 방법·예산 규칙 확정) 뒤에 각 문서 1절의 착수 조건을 충족할 때만 실행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v4",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v4-diag-v2-t0-b",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v2/prompts/classify-v2.txt",
    "planPrompt": "eval/f0/v2/prompts/plan-v2.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/v4/chat-renderers-v4.json",
    "inputAudit": "eval/f0/v4/input-audit-v4-v2.json"
  },
  "inputs": [
    {"path": "eval/golden/tc-f0-v2.jsonl", "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"},
    {"path": "eval/golden/tc-registry-fixture-v2.json", "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"},
    {"path": "eval/f0/v2/prompts/classify-v2.txt", "sha256": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217"},
    {"path": "eval/f0/v2/prompts/plan-v2.txt", "sha256": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"},
    {"path": "eval/f0/v2/arg-limits-v2.json", "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"},
    {"path": "eval/f0/v2/output-boundary-cases.jsonl", "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"},
    {"path": "eval/f0/v2/usage-probe-input-v2.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/v2/token-golden-input-v2.jsonl", "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"},
    {"path": "eval/f0/v4/chat-renderers-v4.json", "sha256": "6b6d8bd7a4a716fa2209f81cccf5e95e677b12302b38d88f619250e5c690bd56"},
    {"path": "eval/f0/v4/input-audit-v4-v2.json", "sha256": "65f54494df5e34a2a17c0f3ca981f8d20630a1140aa8e87fec1379427e978c5b"},
    {"path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256", "sha256": "91fd22b9ec0dbecfd30bf3879ef13290c3b3ffc5abd9e222f35e76ca66c6bc03"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v4.py", "sha256": "d6cb5985062d70ed6ba597863c4c4e25c4f36db7d6785b1225d2351c5d5abf7a"},
    {"path": "eval/py/count_answer_tokens_v4.py", "sha256": "139136b7893e949a70c48df7909efb482ac3a7497ef8e19934f9f21d78edf6bc"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "91580fb716f40189d4c609a32a44f86a1349a9b420be12e6fe1fca14b59eae35",
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
    "answerTokenMethod": "LOCAL_TOKENIZER_PIN_V1",
    "terminationMarginTokens": 1,
    "promptSha256": {"classify": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217", "plan": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "NONE"
  }
}
```
