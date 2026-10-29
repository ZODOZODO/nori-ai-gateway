# F0 GGUF 계수 절차 (`f0 gguf-count`)

- 목적: 출력 경계 케이스의 **기대 출력 JSON 문자열**(`output-boundary-cases.jsonl` 의 `expectedJson`, RFC 8785)의 토큰 수를 실제 Ollama GGUF tokenizer 로 센다.
- 이 값은 "기대 출력 문자열의 토큰 수" 이며 모델이 그 출력을 완결 생성했다는 증거가 아니다. 완결 생성 증거는 `f0 boundary-gen` 의 회차별 oracle(완결 JSON·스키마·인자 코드포인트 정확 일치·`completion_tokens ≤ 128`)이다.

## 명령

```
.\gradlew.bat :gateway-eval:run --args="f0 gguf-count --endpoint http://127.0.0.1:11434 --cases eval/f0/output-boundary-cases.jsonl --out eval/f0/results/<runId>/gguf"
```

- `<runId>` 는 환경변수 `AIGW_PARENT_RUN_ID` 로 받는다(P03 0단계에서 설정). `--out` 이 `eval/f0/results/` 아래면 그 다음 디렉터리 이름이 runId 와 같아야 한다.
- 시작 순서(모든 측정 명령 공통): PREREG 커밋 검사 → SPEC 실제 바이트 검사 → preflight(`/api/ps`·`/api/tags`, 실패 exit 4, digest 불일치 exit 6) → `<out>/run-binding.json` 신규 작성(이미 있으면 exit 2) → 측정.

## 호출

- 계수 호출: `POST /api/generate {"model":"qwen3:4b-instruct","prompt":<expectedJson>,"raw":true,"stream":false,"options":{"num_predict":1}}` 의 `prompt_eval_count`.
- 각 계수 호출 직전 flush: 같은 형식으로 `prompt` = `flush-<n>`(n = 1부터 증가, 기대 JSON 과 첫 토큰부터 다름) — prompt cache 재사용 배제.
- `num_ctx` 는 보내지 않는다. 관리 API(pull/create/delete/copy/push)·embedding 경로는 하네스에 없다.
- 각 호출 전 `/api/ps` 로 두 모델 상주를 다시 확인하며, 상주하지 않으면 추론 호출 없이 중단(exit 5, 부분 결과 보존)한다.

## 보정 확인

- SPEC §14.5 과거 검증값을 같은 방식으로 계수한다: 구 예시 `{"t":"INC","eqp":"PHOTO_001","from":"2026-10-03T00:00","to":"2026-10-03T23:59","q":null}` = 57, `q` 생략형 = 53.
- 둘 중 하나라도 다르면 `f0 report` 는 AC-37-OUT-* 를 `NOT_RUN`(`TOKEN_COUNT_CALIBRATION_MISMATCH`)으로 기록한다. 공식 tokenizer 계수(`eval/py/count_tokens.py`)도 같은 보정 행을 낸다.

## 출력

- `<out>/gguf-count.json`: `{runId, repeats:1, complete, abortReason, rows:[{caseId, kind, ggufTokens}], calibration:[{id, ggufTokens, expectedTokens}]}` — 문자열 원문·응답 원문 저장 0.
