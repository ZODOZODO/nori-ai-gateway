# F0 진단 사전등록 v4-diag-v2-t06-c — 교체 추론 서버(qwen3:4b = Qwen3-4B-Thinking-2507): V2(기존 classify-v2 / plan-v2 대조) · temperature 0.6 · 답변 토큰 계수 방법 (b)

## 0. 등록 상태 — **미등록 초안(UNCOMMITTED), `registeredCommit = null`**

- 목적은 **진단(purpose=DIAGNOSTIC)** 이다. `f0 report`는 `diagnostic-report.md`만 쓰며 F0 leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않는다. 진단은 정식 분모·판정 밖이고 F0 게이트 PASS 근거가 아니다(`--out`·`--rejudge`·`--spec-record-check` = `DIAGNOSTIC_NOT_GATE_ELIGIBLE`, exit 2).
- 비교 기준은 `PREREG-v4-diag-v2-t06-b.md`(C24/C25)다. 2026-11-05 사용자 승인에 따른 P03 ⑨ 후반·⑩ 재진단이며, 기존 등록·입력·결과·증거·capture는 수정하지 않는다. 하네스 코드 변경 0이며 기존 -b 등록은 stale로 만들지 않는다.
- 등록 근거: 사용자 승인(2026-11-05), 로컬 C26(새 프롬프트·새 audit·4초안·Devreport) → C27(`registrationStatus=REGISTERED`, `registeredCommit`=C26) 두 commit. push·amend·`--force` 없음. 미등록 초안은 endpoint 호출 없이 `PREREG_NOT_COMMITTED`로 거부된다.
- 아래 SHA와 harnessDigest는 현재 실제 바이트로 계산한다. v4 harnessDigest는 등록 inputs의 경로·SHA와 기존 f0 패키지 소스의 경로·SHA를 정렬해 계산하므로 후보 입력 변경을 반영한 digest 차이는 하네스 코드 변경을 뜻하지 않는다.

## 1. 진단 설계

- 공통 런타임·예산·계수 방법 (b)·교차 확인·관측 필드·무효 규칙·공유 서버 규칙은 기존 `PREREG-v4-diag-v2-t0-b.md` §1.1~§1.9를 그대로 따른다. 이 문서의 arm·audit·실행 순서와 호출 수는 아래 새 등록값을 적용한다.
- 프롬프트 arm V2(기존 classify-v2 / plan-v2 대조), temperature **0.6**, R=1. Context FULL(투영 키 없음), `responseFormat=NONE`, retryHint 키 없음. 프롬프트·설정·입력 audit는 기존 V2 대조와 바이트 동일하다.
- 골든 `eval/golden/tc-f0-v2.jsonl` 22문항·fixture v2·경계 `eval/f0/v2/output-boundary-cases.jsonl` 9케이스는 기존 바이트를 그대로 쓴다.
- 입력 상한 3,072/3,072, 요청 max_tokens 5,120/5,120, context 8,192, 답변 상한 classify 192·plan 256, 경계 판정 상한 192, 종료 여유 1, timeout 600,000ms, `LOCAL_TOKENIZER_PIN_V1`, 판정 `f0-judge-v2` 유지. 사고 상한은 미정이다.
- 입력 audit `eval/f0/v4/input-audit-v4-v2.json` 재사용: 651행·OVER_BUDGET 0·최대 입력 2,795·입력 + 요청 max_tokens 최대 7,857.
- 생성 호출 수: 등록마다 (B 22 + A 22 + 경계 9) × 시도 1~2 = 최소 53·최대 106, 네 등록 합계 212~424. 기존 -b와 같이 token-golden·usage-probe는 V2 t0 대조에서만 실행한다(계수 호출 38회 별도).
- 연습 통과 기준은 AC-37-CMP·AC-37-OUT-NORMAL·AC-37-OUT-MAX·AC-37-OUT-ESCAPE·AC-38-F0 다섯 leaf와 같다. R=1 DIAGNOSTIC의 매핑은 참고용이며 정식 게이트 판정·승격을 하지 않는다. T 확정·SPEC 기록·정식 R5 등록은 이번 범위 밖이다.

## 2. 실행 순서(C26/C27 등록 commit 뒤에만, 각 명령 별도·exit 0 확인)

- PowerShell cwd `C:/workspace_nori/nori-ai-gateway`. `$env:AIGW_SPEC_FILE='C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md'`, `$env:AIGW_PYTHON='C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe'`. 계수기 버전 0.23.3 확인, Python은 -B 사용.
- 터널: `ssh -i "C:/Users/DongGeon/.ssh/k8s-node01" -o BatchMode=yes -o ConnectTimeout=12 -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434 ubuntu@192.168.0.160`. 백그라운드는 숨긴 창·로그 파일로 실행한다.
- `$runId`는 네 진단 시작 시 UTC로 한 번 정하고, `$env:AIGW_PARENT_RUN_ID="$runId-diag-v2-t06-c"`. 실행 순서는 **diag-c-t0 → diag-c-t06 → diag-v2-t0-c → diag-v2-t06-c**.
- preflight는 검증만 한다. qwen3:4b digest `359d7dd4bcda…`·context 8192·bge-m3 상주가 아니면 즉시 중단·NOT_RUN. 모델 load/pull/unload·서비스 재시작·설정 변경·sudo 금지. 측정 중 또는 postflight에서 변경되면 회차 무효다.

0. `.\gradlew.bat :gateway-eval:run --args="spec-guard"` → exit 0, 계수기 환경 확인.
1. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md"` → `PREREG_VERIFIED`
2. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-diag-v2-t06-c/preflight.json"`
3. `.\gradlew.bat :gateway-eval:run --args="f0 chat-template --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --endpoint http://127.0.0.1:11434 --model qwen3:4b --out eval/f0/chat-template/v4-diag-v2-t06-c"` → template SHA `2d54db2b…97f9` 확인.
6. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --path B --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-diag-v2-t06-c --out eval/f0/results/$runId-diag-v2-t06-c/B"`
7. `.\gradlew.bat :gateway-eval:run --args="f0 run --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --path A --endpoint http://127.0.0.1:11434 --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/plan-v2.txt --repeats 1 --run-id $runId-diag-v2-t06-c --out eval/f0/results/$runId-diag-v2-t06-c/A"`
8. `.\gradlew.bat :gateway-eval:run --args="f0 boundary-gen --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --endpoint http://127.0.0.1:11434 --cases eval/f0/v2/output-boundary-cases.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 1 --run-id $runId-diag-v2-t06-c --out eval/f0/results/$runId-diag-v2-t06-c/boundary"`
9. `.\gradlew.bat :gateway-eval:run --args="f0 report --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --run-id $runId-diag-v2-t06-c --results eval/f0/results/$runId-diag-v2-t06-c"`(`--out` 없음 → diagnostic-report.md만).
10. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-diag-v2-t06-c/postflight.json"` → preflight의 모델·digest·context·상주와 대조.
11. 읽기 전용 journal/GIN 수집으로 `docs/gates/f0/share-window-$runId-diag-v2-t06-c.json`(f0-share-intervals-v1)을 새로 작성한다. 원문·IP·본문은 출력·저장하지 않고 출발지별 집계·외부 요청 UTC 구간만 기록한다. 기존 규칙대로 시각 보정 없이 닫힌 구간 교차를 쓴다.
12. `.\gradlew.bat :gateway-eval:run --args="f0 share-exclude --prereg eval/f0/PREREG-v4-diag-v2-t06-c.md --results eval/f0/results/$runId-diag-v2-t06-c --intervals docs/gates/f0/share-window-$runId-diag-v2-t06-c.json"` → 겹친 문항 회차의 지연만 제외(정확도 유지), p50/p95·제외 건수 보고. 본인 터널만 종료한다.

## 3. 기계 판독 블록

```json
{
  "schema": "f0-prereg-v4",
  "purpose": "DIAGNOSTIC",
  "registrationStatus": "UNCOMMITTED",
  "registeredCommit": null,
  "runtimeCaptureDir": "eval/f0/chat-template/v4-diag-v2-t06-c",
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
    {
      "path": "eval/golden/tc-f0-v2.jsonl",
      "sha256": "260da1e41b6623fb5b905b62cdbc46264c2d09a1a0049fecab672357c8e05a2b"
    },
    {
      "path": "eval/golden/tc-registry-fixture-v2.json",
      "sha256": "87efe85b131be4164cb3118827ac16d8a621044ad8e9d6ed001e9b2810b91697"
    },
    {
      "path": "eval/f0/v2/prompts/classify-v2.txt",
      "sha256": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217"
    },
    {
      "path": "eval/f0/v2/prompts/plan-v2.txt",
      "sha256": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"
    },
    {
      "path": "eval/f0/v2/arg-limits-v2.json",
      "sha256": "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"
    },
    {
      "path": "eval/f0/v2/output-boundary-cases.jsonl",
      "sha256": "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962"
    },
    {
      "path": "eval/f0/v2/usage-probe-input-v2.jsonl",
      "sha256": "105a4edf8a4ae2b39e480b36d3695e50858eb89b13433766998c13900ccef87c"
    },
    {
      "path": "eval/f0/v2/token-golden-input-v2.jsonl",
      "sha256": "b1e865ce6b7b580e9f286b59e0ee0f780af21ce3a74c41794ec4124d9e5fb233"
    },
    {
      "path": "eval/f0/v4/chat-renderers-v4.json",
      "sha256": "6b6d8bd7a4a716fa2209f81cccf5e95e677b12302b38d88f619250e5c690bd56"
    },
    {
      "path": "eval/f0/v4/input-audit-v4-v2.json",
      "sha256": "65f54494df5e34a2a17c0f3ca981f8d20630a1140aa8e87fec1379427e978c5b"
    },
    {
      "path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json",
      "sha256": "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"
    },
    {
      "path": "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256",
      "sha256": "91fd22b9ec0dbecfd30bf3879ef13290c3b3ffc5abd9e222f35e76ca66c6bc03"
    },
    {
      "path": "eval/py/count_tokens_qref_v2.py",
      "sha256": "6bccf55a5a59ce890c4d3a9ecdfe47cdc0b3c078e0b3741125a15b0aca1ad295"
    },
    {
      "path": "eval/py/count_input_audit_v4.py",
      "sha256": "d6cb5985062d70ed6ba597863c4c4e25c4f36db7d6785b1225d2351c5d5abf7a"
    },
    {
      "path": "eval/py/count_answer_tokens_v4.py",
      "sha256": "139136b7893e949a70c48df7909efb482ac3a7497ef8e19934f9f21d78edf6bc"
    },
    {
      "path": "eval/py/requirements.txt",
      "sha256": "40616768269d97564267708bfc32881ce05e2d4d8228db7107f1a0a040ee3351"
    }
  ],
  "harnessDigest": "91580fb716f40189d4c609a32a44f86a1349a9b420be12e6fe1fca14b59eae35",
  "parameters": {
    "generationModel": "qwen3:4b",
    "generationModelDigest": "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7",
    "embeddingModel": "bge-m3",
    "repeats": 1,
    "temperature": 0.6,
    "maxTokens": {
      "classify": 5120,
      "plan": 5120
    },
    "inputLimitTokens": {
      "classify": 3072,
      "plan": 3072
    },
    "contextTokens": 8192,
    "requestTimeoutMs": 600000,
    "outputLimitTokens": 192,
    "answerLimitTokens": {
      "classify": 192,
      "plan": 256
    },
    "answerTokenMethod": "LOCAL_TOKENIZER_PIN_V1",
    "terminationMarginTokens": 1,
    "promptSha256": {
      "classify": "64126ba113cf7cf332fd9216701be72a14b0888fc66705ee647f681430177217",
      "plan": "3d8bb0fbdbd2e5ad5e7f613da3034a6da09d46c42f8292a5f4a96ed9fe37325c"
    },
    "latencyBudgetMsByT": {
      "UNS": 15000,
      "LQ": 30000,
      "KQ": 30000,
      "GQ": 30000,
      "INC": 90000
    },
    "judgeRulesVersion": "f0-judge-v2",
    "responseFormat": "NONE"
  }
}
```
