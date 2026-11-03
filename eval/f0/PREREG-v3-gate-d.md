# F0 정식 사전등록 v3-gate-d — 후보 d(Context 투영 + d 정리 프롬프트 + 서버 구조화 출력 제약), R=5

## 0. 등록 상태 — **등록 완료(REGISTERED), `registeredCommit = 4e1e866440e50799b4466e23ed344e0651de6081`(C7)**

- 목적은 **정식(purpose=GATE)** 이다. `f0-prereg-v3` 의 GATE 등록은 `parameters.repeats=5` 만 유효하다(그 밖 값은 `PREREG_INVALID`, 실제 명령 `--repeats` 가 다르면 `ARGUMENT_NOT_REGISTERED` — 모두 endpoint 호출 전 거부).
- 입력·예산·스키마·투영은 진단 d(`PREREG-v3-diag-d.md`)와 바이트까지 같다: 원래 골든 22문항(`eval/golden/tc-f0-v2.jsonl`)·경계 9케이스·fixture v2·`classify-v3-d`·`plan-v3-d`·`responseFormat=JSON_SCHEMA_V1`·`contextProjection=EXTRACTION_V1`·d 입력 audit(`eval/f0/v3/input-audit-v3-d.json`)·입력 3072/3072, `max_tokens` 192/256, 경계 192, context 4096. 런타임 캡처 디렉터리만 별도(`eval/f0/chat-template/v3-gate-d`)다.
- 정식 분모는 원래 22문항 + 경계 9케이스다. 개발 회귀(`PREREG-v3-regression-d.md`)의 추가 문항·결과와 진단 a/b/c/d 결과는 이 분모·판정에 넣지 않는다.
- 판정은 기존 F0 규칙 그대로다(`f0-judge-v2`, 3축 정확도 A↔B 상대 비교·동률 B 유지, AC-37-OUT-* 경계 계수+완결 생성, AC-38-F0, 지연 예산). 정확도 새 절대 최소 임계·업무 범위 변경은 두지 않는다.
- 기존 `PREREG-v3-gate.md`(후보 c)와 그 §1 착수 조건은 과거 문서로 그대로 보존한다. 후보 c 는 진단에서 경계 2/9 로 그 조건 2 를 충족하지 못했으며, 이 문서는 c 를 대체하는 새 후보 d 의 정식 등록이다. 진단 a/b 는 서버 구조화 출력 효과의 이력일 뿐 이 문서의 R5 를 대체하지 않는다.
- 등록 근거: 사용자 승인 "로컬 C7/C8 허용 후 계속 진행(권장)"(2026-11-03, 로컬 commit 두 개만 허용, push·원격 publish 없음).
- C7 `4e1e866440e50799b4466e23ed344e0651de6081` = 후보 d 등록 대상·하네스 코드·관련 시험 16파일(이 문서는 그때 `registeredCommit=null`·UNCOMMITTED 초안 상태로 함께 커밋). C8 = 후보 d 사전등록 3문서(`PREREG-v3-{diag-d,regression-d,gate-d}.md`)만 수정해 `registeredCommit`에 C7 SHA, `registrationStatus=REGISTERED`를 기록한 commit이다.
- 이력: 2026-11-03 C7 전까지 이 문서는 미등록 초안(C7/C8 사용자 미허용)이었고, 그 동안 측정 명령·`f0 prereg-check` 는 endpoint 호출 없이 `PREREG_NOT_COMMITTED`(exit 2)로 거부됐다(요청 0).
- 아래 `inputs[].sha256`·`harnessDigest` 는 2026-11-03 작업 트리 실제 바이트로 계산했다. C7 직전 등록 대상 16파일을 root 검토 SHA-256 과 대조해 불일치 0이었다.

## 1. 착수 조건(이 조건 전에는 이 등록으로 blind 정식 측정을 하지 않는다)

1. C7/C8 등록 commit 뒤 이 문서·`PREREG-v3-diag-d.md`·`PREREG-v3-regression-d.md` 의 `f0 prereg-check` 가 `PREREG_VERIFIED` 다. C7/C8 은 기존 C3~C6 허용에 포함되지 않으며 C7/C8 구체 범위 검토 뒤 사용자의 별도 commit 허용이 필요하다. — 이 별도 허용은 2026-11-03 "로컬 C7/C8 허용 후 계속 진행(권장)"으로 받았다(이력 문구 보존).
2. 진단 d 와 개발 회귀 d 가 모두 exit 0·`complete=true` 로 끝났다. 서버 `response_format` 거부(exit 5) 0.
3. 진단 d 의 원문 없는 `diagnostic-report.md` 에서 기존 oracle 만으로:
   - B·A 모두 판정 `errorCode=QREF_INVALID` 0, 후보 0 문항의 `qRef.kind=ID_OUT_OF_RANGE` 0.
   - AC-38 문항(G08)의 최종 판정 t 가 A·B 모두 `INC` — 기존 AC-38-F0 조건(고정 재시도 1회 뒤 최종 시도 기준).
   - 경계 9케이스 boundary-gen 이 기존 oracle(완결 JSON·스키마·resolved 인자 정확 일치·≤192)로 9/9 모두 통과.
4. root 가 진단 d·개발 회귀 d 의 원문 없는 보고서를 검토했다. 신규 8문항의 실패 축은 기록·사용자 보고하되 정식 분모에 넣지 않는다. 조건 1~3 을 충족하고 새 업무 결정·제품 지원 범위 변경이 없으면 이미 승인된 P03 범위 안에서 정식 측정을 진행하며, 정식 측정 자체를 위한 별도 사용자 재승인은 요구하지 않는다.

- 과거 후보 c 의 진단 결과(경계 2/9)는 이 문서의 조건이 아니다. 하나라도 충족하지 않으면 이 등록을 실행하지 않는다. 프롬프트·투영·스키마 보완은 새 사전등록 파일로 하며 이 초안을 고쳐 재사용하지 않는다(입력이 바뀌면 `PREREG_INPUT_CHANGED` 로 기계 거부).

## 2. 실행 순서(착수 조건 충족 후, 각 명령 별도·exit 0 확인)

- 공통 조건: PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`. 측정 동안 Jetson 서비스 재시작·모델 로드·pull·관리 API·num_ctx 변경 0. `$env:AIGW_SPEC_FILE` = 정본 SPEC 경로(기준선 마지막 항목과 같은 바이트, `spec-guard` exit 0).

0. 환경 검사(값 출력 0): `if (-not (Test-Path -LiteralPath $env:AIGW_JETSON_SSH_KEY) -or [string]::IsNullOrEmpty($env:AIGW_JETSON_TARGET)) { throw 'AIGW_JETSON_ENV_MISSING' }`; `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')`; `$env:AIGW_PARENT_RUN_ID = $runId`(새 부모 회차).
1. 터널(별도 창): `ssh -i $env:AIGW_JETSON_SSH_KEY -o BatchMode=yes -o ConnectTimeout=12 -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434 $env:AIGW_JETSON_TARGET`
2. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-gate-d.md"` → `PREREG_VERIFIED`
3. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId/preflight.json"` → exit 4 면 중단·NOT_RUN 기록
4. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --model qwen3:4b-instruct --out eval/f0/chat-template/v3-gate-d"`
5. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-gate-d.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/B"`
6. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v3-gate-d.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/plan-v3-d.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/A"`
7. `C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe -I -B eval\py\count_tokens_qref_v2.py --tokenizer eval\f0\tokenizer\qwen3-4b-instruct-2507.tokenizer.json --expect-sha256-file eval\f0\tokenizer\qwen3-4b-instruct-2507.tokenizer.json.sha256 --cases eval\f0\v2\output-boundary-cases.jsonl --out eval\f0\results\$runId\official-count.json`
8. `.\gradlew.bat :gateway-eval:run --args="f0 gguf-count --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --out eval/f0/results/$runId/gguf"`
9. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v3/prompts/classify-v3-d.txt --repeats 5 --run-id $runId --out eval/f0/results/$runId/boundary"`
10. `.\gradlew.bat :gateway-eval:run --args="f0 usage-probe --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/usage-probe-input-v2.jsonl --out eval/f0/results/$runId/usage.json"`
11. `.\gradlew.bat :gateway-eval:run --args="f0 token-golden --prereg eval/f0/PREREG-v3-gate-d.md --endpoint http://127.0.0.1:11434 --messages eval/f0/v2/token-golden-input-v2.jsonl --out eval/f0/results/$runId/token-golden.jsonl"`
12. 터널 종료(본인 창만). 이후 터널 없이 진행한다.
13. `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v3-gate-d.md --run-id $runId --results eval/f0/results/$runId --out evidence/F0"`
14. `.\gradlew.bat :gateway-eval:run --args="gate --stage F0 --parent-run-id $runId --spec $env:AIGW_SPEC_FILE --out eval/f0/results/gate-reports/$runId.md"`(회차 파생 보고서를 새 파일로만 쓴다. 기존 `evidence/F0/GATE-REPORT.md`·측정 원시 디렉터리 쓰기 0)

- 이 시점 AC-37 하위는 `SPEC_RECORD` component 가 없어 NOT_RUN 이 정상이다. §14.5 기록 반영·재판정은 Plan P03 9단계(로컬 G-SPEC-HASH (a) 기준)로 별도 진행한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v3",
  "purpose": "GATE",
  "registrationStatus": "REGISTERED",
  "registeredCommit": "4e1e866440e50799b4466e23ed344e0651de6081",
  "runtimeCaptureDir": "eval/f0/chat-template/v3-gate-d",
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
    "repeats": 5,
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
