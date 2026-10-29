# F0 사전등록(PREREG) — TC 골든셋 (A) LLM 계획 vs (B) 고정 workflow + 분류·인자 추출

## 0. 등록 상태 — **등록(REGISTERED), 등록 commit C1 = `ca3b266f8eb8c529ed6bf358712d6060a77b21c5`**

- 2026-10-30 로컬 `nori-ai-gateway` git 에 등록 commit C1(골든셋·fixture·프롬프트·arg-limits·경계 케이스·메시지 입력·렌더러 등록·tokenizer pin·분석 코드, 이 문서는 `registeredCommit=null` 상태로 포함)을 만들고, 이 문서의 `registeredCommit` 에 C1 SHA 를 적어 이 문서만 별도 PREREG commit C2 로 올렸다. C2 SHA 는 자기 해시 고리 0 원칙에 따라 이 문서에 적지 않는다(`git log -1 -- eval/f0/PREREG.md` 로 확인). 외부 push 0.
- 이력: 2026-10-29 P02 작성 시점에는 레포에 git commit 이 없어 `registeredCommit=null`·`UNCOMMITTED` 였다(가짜 SHA 를 적지 않음).
- 측정 명령(`f0 run`·`boundary-gen`·`gguf-count`·`usage-probe`·`token-golden`·`chat-template`)은 시작 시 이 문서를 검사하며, `registeredCommit` 이 없거나 아래 커밋 조건이 하나라도 성립하지 않으면 endpoint 호출 없이 `PREREG_NOT_COMMITTED` 등(exit 2)으로 거부한다.
- 현재 기계 판독 블록의 `inputs[].sha256`·`harnessDigest` 는 2026-10-30 작업 트리 바이트로 계산한 값이다(`f0 prereg-check` 출력 — 2026-10-29 값에서 렌더러 등록으로 `chat-renderers-v1.json` 이, tokenizer 원본·pin 추가로 `harnessDigest` 가 바뀌었다). 등록 전에 대상 파일이 바뀌면 이 값도 다시 계산해 갱신해야 한다.

## 1. 등록 절차(P03 착수 전 — 자기 해시 고리 0)

1. tokenizer 원본 준비(승인 필요): 승인된 사람이 HF `Qwen/Qwen3-4B-Instruct-2507` 의 `tokenizer.json` 을 `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json` 에 두고 그 SHA-256 을 `….tokenizer.json.sha256` 에 pin 한다(`eval/f0/**` 이므로 harnessDigest 에 포함). — 2026-10-30 완료(§6).
2. G-ARG-LIMITS 해소 또는 미해소 확정 후 `f0 cases` 로 `output-boundary-cases.jsonl` 재생성(현재 UNRESOLVED 3 → NORMAL 만).
3. `f0 prereg-check` 로 현재 입력 SHA·harnessDigest 를 얻어 아래 블록에 반영(`registeredCommit` 은 아직 `null`).
4. **등록 commit C1**: 골든셋·fixture·프롬프트·arg-limits·경계 케이스·메시지 입력·렌더러 등록 파일·분석 코드(`gateway-eval/.../eval/f0/**`, `eval/py/**`)를 커밋한다. 이 commit 에는 PREREG 의 `registeredCommit` 값이 들어가지 않는다.
5. **PREREG commit C2**: 이 문서의 `registeredCommit` 에 C1 SHA 를 적고(`registrationStatus` = `REGISTERED`) 별도 commit 으로 올린다. PREREG 는 자기 SHA 를 담지 않고, harnessDigest 는 PREREG.md 자신을 제외한다.
6. 러너 검사(매 측정 명령): C1 ≠ C2, C1 이 C2 의 조상, 등록 입력 바이트 = C1 트리 바이트 = 현재 작업 트리 바이트, harnessDigest(C1 트리) = 등록값, 대상 경로 작업 트리 clean(`eval/f0/results/`·`eval/f0/chat-template/` 제외), 측정 시작 시각 > C2 commit 시각.
7. 실행 후 이 문서·등록 입력은 수정하지 않는다. 수정이 필요하면 v2 파일을 새로 만들고 v1 결과·사유를 보존한다.

## 2. 축별 판정 규칙(문항별)

- 표본 출력: Path B = `classify-v1` 압축 JSON `{t, eqp, from, to, q}`, Path A = `plan-v1` 압축 계획 `{t, eqp, from, to, q, steps}`.
- 유효 출력: `finish_reason=stop` AND `usage.completion_tokens ≤ max_tokens`(B 128, A 160) AND JSON 파싱 성공 AND 키 집합 정확 일치 AND `t` ∈ {KQ, LQ, INC, GQ, UNS} AND 인자 = 비어 있지 않은 문자열 또는 null AND `from`/`to` = ISO 날짜시각 표기 AND (A) `steps` = capability ID 배열(GQ·UNS 는 `[]`, 그 밖은 1개 이상). 무효면 오류 요지(고정 코드)를 덧붙여 **1회만** 재시도한다.
- 실효 taskType: B = `t`. A = `steps` 에 Registry fixture 의 IMPLEMENTED 가 아닌·미등록 capability 가 있으면 `UNS`(SPEC §5.3 ⓐ 결정론 backstop), 아니면 `t`.
- **taskType 축**: 실효 taskType == 기대 `t`.
- **인자 축**: `eqp`·`from`·`to`·`q` 네 필드 모두 기대값과 코드포인트 단위 정확 일치(부재 = null 포함, 타입 포함). 기대 `eqp` 는 fixture 에 등록된 EQP 리소스다. 추출 규칙(프롬프트와 같음): `eqp` = 질문 본문에 적힌 EQP ID(없으면 null — Context `selectedResourceRefs` 는 Gateway 가 적용), `from`/`to` = 질문 속 시간 표현만 Context timezone 기준 `YYYY-MM-DDTHH:mm`(to exclusive, 상대 표현은 referenceTime 기준, 없으면 null — Context `timeWindow` 는 Gateway 가 적용, SPEC §22.2), `q` = 질문의 큰따옴표 검색어 원문(없으면 null).
- **UNSUPPORTED 축**: (실효 taskType == UNS) == 기대 `unsupported`. 모든 문항이 고정 양성(`unsupported=true`) 또는 음성이다. UNS 오탐 수 = 기대 비UNS 문항 중 실효 UNS 수(회차 1, 정보 기록).
- 무효(재시도 후)·잘림·timeout 표본은 세 축 모두 오답이다.

## 3. 정확도 표본 절차(L-4, SPEC §16 F0 "문항별 1개 표본")

- 각 경로는 골든셋 전체를 R=5 회차 라운드로빈으로 실행한다(회차 r 에서 모든 문항을 파일 순서대로 1회씩, 그다음 회차).
- 정확도 3축 표본 = **문항마다 회차 1 결과 1개**, 분모 = 문항 수(두 경로 동일, 22). 회차 1 이 재시도 후에도 실패·잘림·timeout 이면 그 문항은 오답으로 분모에 남는다. 회차 2~5 로 정답을 고르거나 바꾸지 않는다(best-of 0).
- 지연·JSON 유효율·재시도율·출력 토큰 = 회차 1~5 전부(분모 = 문항 수 × 5 = 110, 실패·timeout 포함). 지연 = 송신~최종 응답(스키마 재시도 포함), timeout 은 timeout 시각까지의 경과. 백분위 = nearest-rank(순위 = ⌈p/100 × N⌉, 보간 없음).
- JSON 유효율 = 최종 유효 표본 / 110, 1차 유효율 = 첫 시도 유효 / 110, 재시도율 = 재시도한 표본 / 110. 회차 간 답 불일치 문항 수는 정보로만 기록한다.
- 요청 timeout = 120,000 ms(기술 선택). 측정 중 timeout 외 endpoint 오류·모델 미상주는 측정 중단(exit 5, 부분 결과 보존) — 그 회차는 판정 입력이 아니다(complete=false).

## 4. 잠정 후보·AC-38-F0·출력 경계

- (A) 잠정 후보 = 3축 모두 (A) ≥ (B) AND 1축 이상 엄격 우위 AND (A) 의 기대 taskType 별 지연 p95 ≤ SPEC §14.5 경로 예산(UNS 15 s, LQ·KQ·GQ 30 s, INC 90 s). 아니면(동률 포함) `KEEP_B`. 최종 승격 기록 0(F4 AC-24-PERF).
- AC-38-F0(고정 문항 G08, SPEC §27.2 공통 TC Context) = 두 경로의 회차 1~5 전 표본이 유효 AND 실효 t=INC AND 기대 workflow(B: INC 고정 workflow = INCIDENT_ANALYSIS, A: 같은 t 이면서 `steps` 에 `tc.logs.search` 포함) AND t=UNS 0건. Tool 실행은 판정 대상 아님.
- AC-37-CMP(F0R) = 두 경로가 완결(분모 drift 0)이고 위 규칙대로 기록됨. AC-37 은 F0R AND SPR(SPEC §14.5 기록 블록 확인) 두 component.
- AC-37-OUT-{NORMAL, MAX, ESCAPE}(F0R) = 그 kind 의 모든 케이스가 max(공식 계수, GGUF 계수) + 종료 여유 1 ≤ 128 AND `boundary-gen` 회차 1~5 전부 pass(finish_reason=stop·스키마 유효·디코드 인자 코드포인트 정확 일치·completion_tokens ≤ 128, 재시도 1회 허용, 실패 회차 분모 유지). 계수 보정(SPEC §14.5 구 예시 57·q 생략 53)이 공식·GGUF 둘 중 하나라도 다르면 `NOT_RUN`(`TOKEN_COUNT_CALIBRATION_MISMATCH`). 공식 계수 파일의 tokenizer SHA 가 run-binding pin 과 다르거나 케이스 SHA 가 등록값과 다르면 입력 없음(레코드 미생성).
- PREREG commit 이후 시작하지 않은 측정·필수 결과 파일 부재·미완결 결과 = 해당 leaf 레코드 없음(gate NOT_RUN).

## 5. 결속·재판정(N4)

- 측정 명령마다 시작 시 `run-binding.json`(이미 있으면 exit 2)에 `specSha`(실제 바이트)·`harnessDigest`·런타임 manifest `{modelDigest, templateSha256, tokenizerSha256}`·파라미터 manifest(아래 `parameters`)·SHA·runId·등록/PREREG commit 을 쓴다. F0 부모 manifest(RUN PARENT)의 빌드 신원 = `{harnessDigest, runtimeManifestSha256, parameterManifestSha256}`.
- `f0 report --rejudge` 는 측정 SPEC 뒤 사슬 항목이 모두 `measurementAffecting=false` 이고 현재 하네스·런타임(캡처 template·모델 digest·tokenizer pin)·파라미터가 run-binding 과 같고 원시 결과 SHA 가 원 레코드와 같을 때만 영향 leaf 의 새 레코드를 추가한다(기존 레코드·원시 파일 변경 0).

## 6. 미확정·차단 사항(측정 전 조건)

- G-ARG-LIMITS: `from`·`to`·`q` UNRESOLVED → AC-37-OUT-MAX·ESCAPE NOT_RUN(`docs/gates/f0/arg-limits.md`).
- tokenizer 원본 pin: **준비 완료(2026-10-30)** — HF `Qwen/Qwen3-4B-Instruct-2507` revision `cdbee75f17c01a7cc42f958dc650907174af0554` `tokenizer.json`, 11422654 바이트, SHA-256 `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`(`docs/gates/f0/tokenizer-provenance.md`). pin 과 실제 바이트가 다르거나 파일이 없으면 측정 명령은 여전히 `TOKENIZER_PIN_MISSING`(exit 2)으로 시작하지 않는다.
- `usage-probe` 의 `/api/generate raw` 렌더 문자열: **검토·등록 완료(2026-10-30)** — template SHA-256 `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` → `CHATML_NO_SYSTEM_DEFAULT`(`eval/f0/chat-renderers-v1.json`, 근거 `docs/gates/f0/template-review.md`, 범위 system 1 + user 1·tools·assistant 이력 없음). 측정 시점 `/api/show` template SHA 가 등록값과 다르면 여전히 `TEMPLATE_RENDERER_UNVERIFIED`(exit 2, 추론 호출 0).
- `token-golden-input-v1.jsonl` 의 `TG12-NEAR-COMPOSER-LIMIT` 는 ≈2,800 토큰 근처를 목표로 만든 근사 입력이며 실제 토큰 수는 P03 측정값으로만 기록한다.

## 7. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v1",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "ca3b266f8eb8c529ed6bf358712d6060a77b21c5",
  "inputs": [
    {"path": "eval/golden/tc-f0-v1.jsonl", "sha256": "211a029489962873030c1a09cb2b1ee6b9b23ce313e2c981109f3ca19400b952"},
    {"path": "eval/golden/tc-registry-fixture-v1.json", "sha256": "9bed27d03d10d37896af1cf6068ea76540aa69d5af25abf5c6d45a69f24955c9"},
    {"path": "eval/f0/prompts/classify-v1.txt", "sha256": "8bedf017cbcfa2f388e6a8d4a46be9507daef7382c5bf28b31fda80d45e522c0"},
    {"path": "eval/f0/prompts/plan-v1.txt", "sha256": "a34ed2a31635eff405e9c9ad69d7a0365b7ec1b4ae62fe4ee8941688bdf32506"},
    {"path": "eval/f0/arg-limits-v1.json", "sha256": "4fc0c6bbb9c56d2a7ecfabaec2274eb738e4bff28c1af2bfea6602089242315a"},
    {"path": "eval/f0/output-boundary-cases.jsonl", "sha256": "488aa42783e27326f27935a8a8a0e0f8b8b69d6f233c8ab4bb6afad84a20dac9"},
    {"path": "eval/f0/usage-probe-input-v1.jsonl", "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"},
    {"path": "eval/f0/token-golden-input-v1.jsonl", "sha256": "33eebcd86745e5241fe44e94e0c234131746bb8bbffa682df45806f08c50d5a3"},
    {"path": "eval/f0/chat-renderers-v1.json", "sha256": "ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e"},
    {"path": "eval/py/count_tokens.py", "sha256": "df9252936814778d549ae70d08d8b4afbc8ec646878c2a5cc75ce21663197b3c"},
    {"path": "eval/py/requirements.txt", "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"}
  ],
  "harnessDigest": "3b3c0a407271d3eac2850a479da584453188d8be2cfbf7380bfa4ec3e8a4bac4",
  "parameters": {
    "generationModel": "qwen3:4b-instruct",
    "embeddingModel": "bge-m3",
    "repeats": 5,
    "temperature": 0,
    "maxTokens": {"classify": 128, "plan": 160},
    "requestTimeoutMs": 120000,
    "outputLimitTokens": 128,
    "terminationMarginTokens": 1,
    "promptSha256": {"classify": "8bedf017cbcfa2f388e6a8d4a46be9507daef7382c5bf28b31fda80d45e522c0", "plan": "a34ed2a31635eff405e9c9ad69d7a0365b7ec1b4ae62fe4ee8941688bdf32506"},
    "latencyBudgetMsByT": {"UNS": 15000, "LQ": 30000, "KQ": 30000, "GQ": 30000, "INC": 90000},
    "judgeRulesVersion": "f0-judge-v1"
  }
}
```
