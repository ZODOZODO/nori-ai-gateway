# AI Gateway 추론 서버 읽기 전용 확인·모델 적재 runbook

대상: 추론 서버(D44, `192.168.0.160:11434`, Ollama)의 운영자 절차(Plan P17, SPEC §14.6 "readiness"·"모델 적재 절차", §26.1 임차 원칙). 실행자 = 승인된 운영자. 이 문서와 스크립트를 만든 구현 담당은 서버에 접속하지 않았다(현재 NOT_RUN — 로컬 fixture selftest 만 수행).

## 임차 원칙 — 하지 않는 것

추론 서버는 빌려 쓰는 공유 자원이며 다른 사용자가 함께 쓴다. 이 runbook 의 어떤 단계도 아래를 하지 않는다.

- 서버 설정 변경(환경변수·바인딩·방화벽·proxy·systemd 포함), 서비스 재시작·중지.
- 모델 관리 API(`pull`·`create`·`delete`·`copy`·`push`) 호출. `ollama pull`·`ollama rm` 같은 CLI 동등 명령도 같다.
- `num_ctx`·`keep_alive` 등 요청 옵션 지정. 적재 요청은 모델 이름만 보내고 나머지는 서버 기본값을 쓴다(2026-11-09 읽기 전용 관측: `OLLAMA_CONTEXT_LENGTH=8192`, `OLLAMA_KEEP_ALIVE=24h` — 소유자 설정이다).
- API 응답 원문의 출력·저장. 기록은 아래 요약 필드만 남긴다.
- 서버에 파일 설치. 스크립트는 표준 입력으로 넘겨 실행한다.

접속 자격과 호스트 접속 세부(계정·키 경로 등)는 이 문서에 쓰지 않는다. 아래 `<승인된 로컬 SSH 수단>` 자리는 운영자가 따로 승인받은 접속 명령으로 채운다.

## (a) 읽기 전용 상태 확인

실측·수용 회차(F2 LIVE leaf·F3-PROBE·F4) 전에 반드시 수행하고, (b) 적재 뒤에도 수행한다.

### 입력

- 생성 모델 `qwen3:4b` 의 기대 digest: 배포 설정의 기대값(P19 readiness 가 대조하는 값과 같은 값). sha256 hex 64자 소문자 전체이며 `sha256:` 접두어는 붙이지 않는다. `ollama list` 의 12자 ID 는 받지 않는다.

### 명령

레포 루트(로컬)에서 스크립트를 표준 입력으로 서버 셸에 넘긴다. 서버에는 파일이 남지 않는다.

```bash
<승인된 로컬 SSH 수단> 'bash -s -- <기대 digest 64자>' < ops/inference-server/bin/aigw-ps-summary.sh
```

스크립트가 서버에서 하는 일은 loopback `GET http://127.0.0.1:11434/api/ps` 1회(`curl -s --fail --max-time 5`)뿐이다. 응답은 메모리에서 `python3 -I` 필터로만 넘어가고, 필터는 형식을 확인한 모델별 `name`·`digest`·`context_length` 와 `resident_count`·`ok` 만 출력한다. 서버에 `curl`·`python3`·`bash` 가 있어야 한다.

### 출력과 종료 코드

```json
{"models": [{"context_length": 8192, "digest": "<64 hex>", "name": "qwen3:4b"}, {"context_length": 8192, "digest": "<64 hex>", "name": "bge-m3:latest"}], "ok": true, "resident_count": 2}
```

- `ok=true` 조건 = `qwen3:4b` 상주 AND `bge-m3`(Ollama 는 `bge-m3:latest` 로 보고한다. 태그 없는 `bge-m3` 도 같은 모델로 본다) 상주 AND `qwen3:4b` digest = 인자 AND `qwen3:4b` `context_length=8192`. `bge-m3` 는 상주만 본다. P19 readiness `READY` 와 같은 기준이다.
- `resident_count` 는 상주 모델 전체 수다. 다른 사용자의 모델도 목록에 나올 수 있다.
- exit 0 = `ok=true`, 1 = `ok=false`, 2 = 사용법 오류(`PS_SUMMARY_USAGE: ...`, stderr)·조회 실패(`PS_SUMMARY_FETCH_FAILED` — curl 실패·비 2xx·5초 초과)·파싱 실패(`PS_SUMMARY_PARSE_FAILED` — 비 JSON·`/api/ps` 형식 아님·필드 형식 어긋남). 실패 문구는 고정이며 입력 원문을 담지 않는다.

### 기록

회차 증거에는 확인 시각(UTC)·위 출력 한 줄·종료 코드만 남긴다. `/api/ps` 원문을 따로 받아 붙이지 않는다.

### 판정별 대응

| 결과 | 대응 |
|---|---|
| `ok=true` | 회차를 진행한다. |
| `qwen3:4b` 또는 `bge-m3` 미상주 | (b) 모델 적재 절차로 간다. |
| `qwen3:4b` digest 불일치 | 서버의 모델은 소유자 영역이다. 적재·`pull` 로 고치지 않고 기록한 뒤 회차를 보류한다(readiness 는 fail-closed 로 `READY` 가 아니다). |
| `qwen3:4b` `context_length≠8192` | 서버 설정의 소유자 영역이다. 서버를 바꾸지 않고 기록한 뒤 회차를 보류한다(readiness `MODEL_CONTEXT_MISMATCH`). |
| exit 2 `PS_SUMMARY_FETCH_FAILED`·`PS_SUMMARY_PARSE_FAILED` | API 무응답·형식 오류(readiness `INFERENCE_UNAVAILABLE`)다. 서비스를 재시작하지 않고 기록한 뒤 회차를 보류한다. |

## (b) 모델 적재 절차

### 신호

- Gateway 의 600s 지속 `MODEL_LOADING` 경보(SPEC §14.6 관측 유예 600s 초과), 또는
- (a) 에서 `qwen3:4b`·`bge-m3` 중 하나라도 미상주.

Gateway 는 스스로 모델을 적재하지 않는다. 적재는 운영자의 이 절차뿐이다.

### 명령

운영자가 `<승인된 로컬 SSH 수단>` 으로 서버 셸에 접속해 서버 loopback 에서 실행한다. 미상주 모델에 해당하는 줄만 실행한다. 둘 다 일반 Ollama 요청이며 모델 관리 API 가 아니다. `-o /dev/null` 로 응답 본문은 출력하지 않고 HTTP 상태 코드만 출력한다. 콜드 로드가 길 수 있어 상한을 관측 유예와 같은 600s 로 둔다.

생성 모델 `qwen3:4b`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' --max-time 600 http://127.0.0.1:11434/api/generate -d '{"model":"qwen3:4b"}'
```

- 근거: Ollama API 문서 `POST /api/generate` 의 "Load a model" 항목 — prompt 를 비운 요청은 모델을 메모리에 적재만 한다(생성 없음). 기대 출력 `200`.

임베딩 모델 `bge-m3`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' --max-time 600 http://127.0.0.1:11434/api/embed -d '{"model":"bge-m3","input":""}'
```

- **실측 확인(2026-11-09, Ollama 0.40.13):** 위 요청이 `200` 을 냈고 (a) 재확인에서 `bge-m3:latest` 가 `context_length=8192` 로 상주했다(서버 설정 변경 0). Ollama 공식 문서의 적재(preload) 안내는 `/api/generate`·`/api/chat` 의 빈 요청만 들고 있어 `/api/embed` 빈 `input` 적재는 문서 근거가 아니라 이 실측이 근거다. `bge-m3` 는 임베딩 전용 모델이라 `/api/generate` 적재 요청은 쓸 수 없다. 서버 Ollama 버전이 바뀐 뒤 `200` 이 아니거나 재확인에서 `bge-m3` 가 상주하지 않으면 다른 요청 형식·옵션을 시도하지 않고 결과(상태 코드만)를 기록한 뒤 회차를 보류한다.

### 재확인

적재 요청 뒤 (a) 명령을 다시 실행한다.

- `ok=true` → 완료. 출력 한 줄·종료 코드를 기록한다.
- 상태 코드가 `200` 이 아니거나(`000` = 600s 초과·연결 실패 포함) 재확인에서도 미상주 → 적재 요청을 반복하지 않고 기록한 뒤 회차를 보류한다. 서비스 재시작·설정 변경·`pull` 로 넘어가지 않는다.
- 적재 뒤 digest 불일치·`context_length≠8192` 가 나오면 (a) 판정별 대응을 따른다.

## 스크립트 시험(로컬 전용)

`--input-file <path>` 는 fixture 로 파싱만 시험하는 로컬 전용 옵션이며 서버 실행에 쓰지 않는다. `AIGW_PS_PYTHON` 은 로컬에 `python3` 가 없을 때 인터프리터 경로만 바꾼다(기본 `python3`).

```bash
bash -n ops/inference-server/bin/aigw-ps-summary.sh
bash ops/inference-server/bin/tests/aigw-ps-summary-selftest.sh   # SELFTEST_PASSED·exit 0
```

시험 사례: 정상(두 모델 상주·8192·기대 digest) → `ok=true`·exit 0, 생성 모델 미상주·`bge-m3` 미상주·`context_length=4096`·digest 불일치 → `ok=false`·exit 1, 비 JSON·빈 입력·digest 형식 오류 → `PS_SUMMARY_PARSE_FAILED`·exit 2·원문 미출력, 입력에 넣은 sentinel 최상위 필드와 모델별 미지 필드 → 출력에 없음, 사용법 오류 → exit 2.

## 되돌리기

이 runbook 은 서버를 바꾸지 않으므로 서버 쪽 되돌리기는 없다. 레포 산출물은 문서·스크립트 revert 로 되돌린다.
