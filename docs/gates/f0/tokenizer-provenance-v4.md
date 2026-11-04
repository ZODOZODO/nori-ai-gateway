# 공식 Qwen tokenizer 원본 pin — v4 Qwen3-4B-Thinking-2507 (P03 v4 ③)

- 작성일: 2026-11-04 (P03 v4 ①·③ 세션, 독립 Claude Code — claude-opus-5-5). 받은 파일 = 공식 HF `tokenizer.json` 1개(데이터 파일). 모델 가중치·코드·그 밖 파일 다운로드 0. v1~v3 의 Instruct-2507 pin(`docs/gates/f0/tokenizer-provenance.md`, `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json{,.sha256}`)은 바꾸지 않았다.
- 용도: v4 등록 profile `Prereg.RuntimeProfile.INFER_V4` 의 tokenizer 쌍(`eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json{,.sha256}`) — `eval/py/count_tokens.py`·입력 audit 계수기의 공식 계수와 측정 명령 런타임 manifest `tokenizerSha256`(`MeasurementSession.tokenizerSha` — pin 과 실제 바이트가 같을 때만 사용).

| 항목 | 값 |
|---|---|
| 저장소 | `Qwen/Qwen3-4B-Thinking-2507` (Hugging Face 공식) |
| 고정 revision | `768f209d9ea81521153ed38c47d515654e938aea`(HF model API `sha`, lastModified 2025-08-06T11:08:25Z) |
| 다운로드 URL | `https://huggingface.co/Qwen/Qwen3-4B-Thinking-2507/resolve/768f209d9ea81521153ed38c47d515654e938aea/tokenizer.json` |
| 응답 헤더(302 단계) | `X-Repo-Commit: 768f209d9ea81521153ed38c47d515654e938aea`, `X-Linked-Size: 11422654`, `X-Linked-ETag: "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"` |
| 메타 기대값 | 크기 11422654 바이트, LFS SHA-256 `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4` |
| 받은 실제 바이트 재계산 | 크기 **11422654**, SHA-256 **`aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`** — 메타와 일치 |
| 저장 위치 | `eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json` (받은 바이트 그대로 복사, 변환 0) |
| pin 파일 | `eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256` = `aeb13307…dae4  qwen3-4b-thinking-2507.tokenizer.json` + LF (`sha256sum -c` OK, pin 파일 SHA `91fd22b9ec0dbecfd30bf3879ef13290c3b3ffc5abd9e222f35e76ca66c6bc03`). Java `tokenizerSha`·Python `read_pin` 모두 첫 공백 구분 토큰만 읽는다 |

## Instruct-2507 pin 과 바이트 비교

- `cmp eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json` = 차이 0. **두 공식 `tokenizer.json` 은 바이트 동일**(크기 11422654, SHA `aeb13307…dae4` 같음 — Instruct 는 revision `cdbee75f…0554`, Thinking 은 `768f209d…aea`).
- pin 파일은 파일 이름 부분만 달라 바이트가 다르다(Instruct pin SHA `445bc540…2113`, Thinking pin SHA `91fd22b9…bc03`). 첫 토큰(tokenizer SHA)은 같다.
- 뜻: 공식 BPE vocab·merges·special token 정의가 같으므로 같은 렌더 문자열의 공식 계수는 두 pin 에서 같다. 계수 차이는 렌더 문자열(chat template)에서만 생긴다 — v4 렌더 차이는 `docs/gates/f0/template-review-v4.md` §5.
- 비교 대상 밖: `tokenizer_config.json`(HF chat_template 포함)은 받지 않았다. 서버 계수 대상 문자열은 HF Jinja 가 아니라 Ollama Go template 렌더이므로(같은 검토 §3) v4 렌더러 근거로 쓰지 않는다.

## 절차

1. 새 빈 임시 디렉터리(세션 scratchpad `hf-thinking-2507-dl/`, 생성 직후 항목 0 확인)에 `curl -sS --fail -L` 로 위 URL 1개만 받음. 헤더는 `curl -I` 출력에서 위 필드만 확인.
2. `wc -c`·`sha256sum` 으로 실제 바이트 재계산 → 메타 크기·LFS SHA 와 일치 확인 후에만 레포 경로에 복사.
3. 복사본으로 pin 파일 작성 후 `sha256sum -c` OK, Instruct 원본과 `cmp` 일치.

## 한계·주의

- 이 파일은 공식 HF tokenizer(BPE) 원본이며 서버 GGUF 내장 vocab(`tokenizer.ggml.model=gpt2`, `pre=qwen2`)과 같은 계수를 낸다는 증거가 아니다 — 두 계수 대조는 P03 v4 ⑤(token-golden)·⑧(usage) 몫이다.
- `eval/f0/**` 아래 새 파일이므로 v1 등록(`PREREG.md`, 비버전 harnessDigest = `eval/f0/**` 전체)의 harnessDigest 대상이 바뀐다. 기존 24건은 이미 `harnessDigest` stale(§12.28 3))이며 등록 문서는 고치지 않았다. versioned(v2·v3) harnessDigest 는 등록 inputs + f0 소스만 보므로 이 파일 추가와 무관하다.
