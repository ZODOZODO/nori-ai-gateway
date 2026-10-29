# 공식 Qwen tokenizer 원본 pin (P03 준비)

- 작성일: 2026-10-30 (P03 준비 세션, 독립 Claude Code — claude-opus-5-5). 사용자 승인 범위: 공식 HF `tokenizer.json` 다운로드·SHA 고정. 모델 가중치·그 밖 파일 다운로드 0.
- 용도: `eval/py/count_tokens.py` 공식 계수(Plan P02/P03 4ⓐ)와 측정 명령 런타임 manifest 의 `tokenizerSha256`(`MeasurementSession.tokenizerSha` — pin 과 실제 바이트가 같을 때만 사용).

| 항목 | 값 |
|---|---|
| 저장소 | `Qwen/Qwen3-4B-Instruct-2507` (Hugging Face 공식) |
| 고정 revision | `cdbee75f17c01a7cc42f958dc650907174af0554` |
| 다운로드 URL | `https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507/resolve/cdbee75f17c01a7cc42f958dc650907174af0554/tokenizer.json` |
| 응답 헤더(302 단계) | `X-Repo-Commit: cdbee75f17c01a7cc42f958dc650907174af0554`, `X-Linked-Size: 11422654`, `X-Linked-ETag: "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4"` |
| 메타 기대값 | 크기 11422654 바이트, LFS SHA-256 `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4` |
| 받은 실제 바이트 재계산 | 크기 **11422654**, SHA-256 **`aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`** — 메타와 일치 |
| 저장 위치 | `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json` (받은 바이트 그대로 복사, 변환 0) |
| pin 파일 | `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256` = `aeb13307…dae4  qwen3-4b-instruct-2507.tokenizer.json` + LF (`sha256sum -c` OK). Java `tokenizerSha`·Python `read_pin` 모두 첫 공백 구분 토큰만 읽는다 |

## 절차

1. 작업 전용 임시 디렉터리에 `curl -sSL --fail` 로 위 URL 1개만 받음(리다이렉트 추적, 헤더는 임시 파일로만 확인 후 디렉터리 삭제).
2. `wc -c`·`sha256sum` 으로 실제 바이트 재계산 → 메타 크기·LFS SHA 와 일치 확인 후에만 레포 경로에 복사.
3. 복사본 SHA 재계산 일치, pin 파일 작성 후 `sha256sum -c` OK.

## 한계·주의

- 이 파일은 공식 HF tokenizer(BPE) 원본이며 Jetson 의 GGUF 내장 vocab(`tokenizer.ggml.*`)과 같은 계수를 낸다는 증거가 아니다 — 두 계수의 대조는 Plan P03 4ⓐ(공식)·4ⓑ(GGUF) 측정 몫이다.
- `eval/f0/**` 아래 새 파일이므로 PREREG `harnessDigest` 대상이 바뀐다. 이번 세션 범위에 따라 PREREG 입력 SHA·`harnessDigest` 는 갱신하지 않았다(다음 세션 C1/C2 전 갱신 필요).
