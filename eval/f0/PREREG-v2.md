# F0 사전등록 v2(PREREG-v2) — qRef wire·v2 예산 profile 재측정

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = d44c51ddb275330881bc34fa025c04a6369014f8`(C3)**

- 이 문서는 v1 `eval/f0/PREREG.md`(C1 `ca3b266…`/C2 `bd61f36…`)와 v1 회차 `20261029T234506Z`의 판정·실패 증거를 바꾸지 않는 **별도 v2 사전등록**이다. v1 등록 입력 11개·v1 PREREG 바이트는 그대로다.
- 등록 근거: 사용자 최신 승인 "로컬 C3/C4 허용 후 계속 진행"(2026-11-02, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C3 `d44c51ddb275330881bc34fa025c04a6369014f8` = 등록 대상·분석 코드(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C4 = 이 문서만 수정해 `registeredCommit`에 C3 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-02 C3 전까지 이 문서는 미등록 초안이었고, 그 동안 측정 명령·`f0 prereg-check`는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest`는 2026-11-02 작업 트리 실제 바이트로 계산했고 C3 직전 다시 대조해 불일치 0이었다.

## 1. v1 대비 변경(사용자 승인 계약 + 기술 선정)

- 출력 wire: 분류 `{t, eqp, from, to, qRef}`, 계획 Path A는 `steps`를 더한다. 원문 `q`는 요청 메모리의 후보 바인딩(`QRefContext`)으로 복원한 resolved 값이며 정확도 oracle은 resolved 원문 정확 일치다(SPEC §14.5 분류 인자 계약 v2).
- 입력 역할 = v1 9개 역할 + `inputAudit`. `chatRenderers`는 검토된 v1 렌더러 등록 파일을 바이트 그대로 재사용한다.
- 토큰 예산 profile(단일 값, `Prereg.Budget.V2`): 입력 상한 분류 3072·계획 3072, 요청 `max_tokens` 분류 192·계획 256, 경계 출력 상한 192(= 분류 `max_tokens`), context 4096. 입력+출력 = 3264/3328 ≤ 4096. PREREG 검사·요청 `max_tokens`·판정(`usage.completion_tokens ≤ max_tokens`)·경계 판정(max(공식, GGUF) + 종료 여유 1 ≤ 192)·보고서·증거 metrics가 이 값 하나를 쓴다. 숫자는 JSON 정수여야 하며 profile과 다르면 `PREREG_INVALID`다.
- 호출 전 입력 확인(F0 전용): `inputAudit`은 등록 입력으로 하네스가 보낼 수 있는 모든 chat 메시지(골든 22문항 × A/B, 경계 9케이스, 각 첫 시도 + 고정 오류 코드 11종별 재시도, usage 2·token 13행)를 실제 Java `MessageAssembler`·검토 렌더러(template `40c21f34…b541`)·pin 공식 tokenizer로 센 표다(651행). 측정 명령은 등록 검사 후 전체 목록을, 세션은 매 호출 직전 그 메시지를 표에서 찾아 입력 ≤ 상한 AND 입력 + `max_tokens` ≤ 4096을 확인한다. 표에 없거나 초과면 송신하지 않는다(`INPUT_AUDIT_MISSING`/`INPUT_BUDGET_EXCEEDED`, exit 2). 측정 런타임 template·tokenizer SHA가 표와 다르면 run-binding 작성 전에 `INPUT_AUDIT_RUNTIME_MISMATCH`로 멈춘다. 이 표는 준비 정적 계수이며 Gateway 운영 사전 계수기(P18)가 아니다. 실제 응답 `usage.prompt_tokens`는 별도 열로 남기고 보고서에 사전 계수보다 작은 호출 수를 따로 적는다(판정 규칙 변경 없음).
- 준비 정적 계수(등록 전 준비 계산·모델 생성 아님): 입력 최대 A 첫 시도 1248·재시도 1287, B 1088/1127, 경계(B) 2701/2740, usage 1054, token 2793(TG12). 출력 기대 wire JSON 공식 계수 NORMAL 58/96, MAX·ESCAPE 145(`eval/f0/results/v2-qref-preparation/official-count.json`·`official-count-20261102.json`). GGUF 계수·실제 생성은 0이다.
- `token-golden-input-v2.jsonl`은 v1과 같은 13개 ID를 유지하고 `TG12-NEAR-COMPOSER-LIMIT`만 공식 tokenizer·검토 렌더러 기준 2793토큰(≤ 2816)으로 길이를 보정했다(v1 TG12는 실제 측정 3952). `usage-probe-input-v2.jsonl`은 v1과 같은 조립 행(G01·G02)이며 v2 역할(분류 v2 프롬프트·v2 골든·fixture)로 조립된다.

## 2. 판정 규칙(v1 §2~§5와 같고 아래만 다름)

- 유효 출력: v1 조건에서 상한을 B 192·A 256으로, 키 집합을 wire(`qRef`)로, `qRef`는 null(후보 없을 때만) 또는 그 요청 발급 참조, eqp ≤ 64·resolved q ≤ 256 CODEPOINT, `from`/`to`는 v2 시간 문법과 실제 날짜·offset 유효성으로 바꾼다. 무효면 고정 오류 코드만 덧붙여 같은 바인딩으로 1회 재시도한다.
- 정확도 3축·표본·분모(문항 22, 회차 R=5, 회차 1 표본, 지연·유효율 분모 110)·잠정 후보·AC-38-F0·경로 지연 예산(UNS 15 s, LQ·KQ·GQ 30 s, INC 90 s)은 v1과 같다. 합격 기준·분모·회차를 완화하지 않는다.
- AC-37-OUT-{NORMAL, MAX, ESCAPE}: 등록 케이스 집합을 분모 정본으로, 각 케이스 max(공식, GGUF) + 종료 여유 1 ≤ 192 AND `boundary-gen` 회차 1~5 전부 pass(stop·스키마 유효·resolved 인자 정확 일치·`completion_tokens` ≤ 192, 재시도 1회 허용). 보정(구 예시 57/53)이 어긋나면 `NOT_RUN`.
- `f0 report --rejudge`의 런타임 대조는 `runtimeCaptureDir`(`eval/f0/chat-template/v2-qref`) 캡처만 쓴다.

## 3. 등록 절차(이력: 2026-11-02 사용자 허용 후 수행)

1. root가 최종 SPEC SHA를 승인 기준선(seq3)에 반영하고 whole test·`test-evidence verify`를 실행했다.
2. C3 직전 `inputs`·`harnessDigest`를 다시 계산해 불일치 0을 확인했다.
3. C3: 이 문서(`registeredCommit=null`)와 등록 대상·분석 코드를 커밋했다. C4: `registeredCommit`에 C3 SHA, `registrationStatus=REGISTERED`로 이 문서만 커밋했다. 이후 `f0 chat-template --out eval/f0/chat-template/v2-qref`부터 새 runId로 측정한다.

## 4. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v2",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "d44c51ddb275330881bc34fa025c04a6369014f8",
  "runtimeCaptureDir": "eval/f0/chat-template/v2-qref",
  "inputRoles": {
    "golden": "eval/golden/tc-f0-v2.jsonl",
    "fixture": "eval/golden/tc-registry-fixture-v2.json",
    "classifyPrompt": "eval/f0/v2/prompts/classify-v2.txt",
    "planPrompt": "eval/f0/v2/prompts/plan-v2.txt",
    "argLimits": "eval/f0/v2/arg-limits-v2.json",
    "boundaryCases": "eval/f0/v2/output-boundary-cases.jsonl",
    "usageMessages": "eval/f0/v2/usage-probe-input-v2.jsonl",
    "tokenMessages": "eval/f0/v2/token-golden-input-v2.jsonl",
    "chatRenderers": "eval/f0/chat-renderers-v1.json",
    "inputAudit": "eval/f0/v2/input-audit-v2.json"
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
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/f0/v2/input-audit-v2.json", "sha256": "c30c3b6061c37ea394396137579991694df5dbfff5db8a7bf1cedb624e358ce0"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"},
    {"path": "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256", "sha256": "445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113"},
    {"path": "eval/py/count_tokens_qref_v2.py", "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"},
    {"path": "eval/py/count_input_audit_v2.py", "sha256": "7eb6e47e3d97e9b55f2e96198ad6192ea24916c0bdc8582e0302dfa07a8da0f0"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "98611f510266172915c825b5efcc75c7aad1ceab943caf0a34ece1c385a1407d",
  "parameters": {
    "generationModel": "qwen3:4b-instruct",
    "embeddingModel": "bge-m3",
    "repeats": 5,
    "temperature": 0,
    "maxTokens": {"classify": 192, "plan": 256},
    "inputLimitTokens": {"classify": 3072, "plan": 3072},
    "contextTokens": 4096,
    "requestTimeoutMs": 120000,
    "outputLimitTokens": 192,
    "terminationMarginTokens": 1,
    "promptSha256": {"classify": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217", "plan": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v2"
  }
}
```
