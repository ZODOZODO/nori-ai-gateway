# qwen3:4b(Qwen3-4B-Thinking-2507) chat template 검토 — v4 렌더러 등록 근거 (P03 v4 ③)

- 작성일: 2026-11-04 (P03 v4 ①·③ 세션, 독립 Claude Code — claude-opus-5-5). 추론 호출·모델 load/unload/pull·서비스 재시작·설정 변경 0. 서버 접근은 읽기 조회(`/api/version`·`/api/ps`·`/api/tags`·`/api/show`, `systemctl show`, `nvidia-smi`, `/proc/<pid>/cmdline` 플래그 유무, `journalctl` 의 `template selection` 줄의 `model=`·`selected=` 값 집계)뿐이며 응답 원문은 출력·저장하지 않았다(`/api/show` 의 `template` 필드만 저장).
- 판정: **기존 `CHATML_NO_SYSTEM_DEFAULT`(v1 `ChatRenderers.chatMl`)는 이 template 을 재현하지 않는다 → 새 렌더러 `OLLAMA_GO_QWEN3_THINKING_V1` 을 v4 전용 등록 파일 `eval/f0/v4/chat-renderers-v4.json` 에 등록.** 적용 범위는 §5 의 조건(system 첫 위치·선택 + user 1개 이상, tools·assistant·tool 없음)과 §7 의 확장(마지막 user 앞 assistant 대화, 2026-11-04 P03 v4 ④⑤ 세션)으로 한정한다. v1~v3 등록 파일 `eval/f0/chat-renderers-v1.json`(SHA `ba743ec7…c7e`)과 `chatMl` 은 바꾸지 않았다.

## 1. 검토 대상

| 항목 | 값 |
|---|---|
| 런타임 | LLM 노트북 서버 192.168.0.160, Ollama `0.40.13`(`/api/version`), 모델 `qwen3:4b` digest `359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7`(`/api/ps`·`/api/tags` 같음), context 8192, `bge-m3:latest` 와 함께 상주. 고정 기록 `docs/gates/f0/runtime-env-20261104T021454Z.json` |
| template 사전검토 파일 | `docs/gates/f0/preparation-template-v4.template` — `/api/show {"model":"qwen3:4b"}` 의 `template` 필드만 서버에서 추출해 UTF-8 바이트 그대로 저장. 1506 바이트, CR 0(LF), 끝 개행 없음(마지막 바이트 `{{- end }}`) |
| template SHA-256 | **`2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9`** — 같은 세션의 두 번 조회(필드 요약·바이트 저장)가 같은 값 |
| 형식 | Ollama Go `text/template`(`{{- … }}`, `{%` 0회). HF Jinja 가 아님 — v1 Instruct(Jetson 0.31.1)의 GGUF Jinja 경로와 다르다 |
| `/api/show` 메타 요약(키·판별 값만) | `template_source=go_template`, `renderer`·`parser` 없음, `capabilities=[completion, tools, thinking]`, `thinking={default:true, values:[true]}`, Modelfile 지시어 = FROM 1·TEMPLATE 1·PARAMETER 6·LICENSE 1(`SYSTEM`·`MESSAGE`·`RENDERER`·`PARSER` 없음), `general.finetune=Thinking`, `tokenizer.ggml.add_bos_token=false` |
| 정식 측정 캡처 위치 | v4 사전등록(P03 v4 ⑥)의 `runtimeCaptureDir`(`eval/f0/chat-template/<이름>`) 아래에 `f0 chat-template --model qwen3:4b` 가 `qwen3-4b.template`·`.sha256`·`model-digest.txt`·`run-binding.json` 을 쓴다(v2/v3 관례). 그 명령은 등록 문서가 있어야 시작하므로 이번에는 만들지 않았다 — 이 문서의 사전검토 파일은 v1 의 `preparation-template.template` 과 같은 역할이다 |

## 2. 공식 source (tagged)

- Ollama `v0.40.13` = commit `04088e7c9d41cba5d33bafbf788d87a465c79d5b`(`git ls-remote` 의 `refs/tags/v0.40.13`). 아래 `O:` = `https://github.com/ollama/ollama/blob/04088e7c9d41cba5d33bafbf788d87a465c79d5b/`. 읽은 파일은 raw 로 받아 읽기만 했다(빌드·실행 0).

## 3. 실제 경로: Ollama Go 렌더(rendered) 모드

| # | 근거 | 내용 |
|---|---|---|
| 3.1 | `O:server/images.go#L383-L399` | `selectedTemplateSource`: renderer·parser·harmony 가 아니고 `shouldUseGoTemplate(m)` 이면 `"go_template"`. `/api/show` 의 `template_source` 가 이 값이다(`O:server/routes.go#L1950`) |
| 3.2 | `O:server/routes.go#L2951-L2963` | `usesOllamaRenderedChat` = `Renderer!="" \|\| Parser!="" \|\| shouldUseHarmony \|\| shouldUseGoTemplate`; `chatModeForModel` = Rendered iff MLX 또는 위 조건(`#L2934-L2940`) |
| 3.3 | `O:server/routes.go#L2144-L2151` | `/api/show` 의 `template` = native 모드 + GGUF chat_template 일 때만 GGUF 값, 아니면 `m.Template.String()`(= Go template 원문, `O:template/template.go#L167-L169`). 저장한 template 이 실행되는 template 자체다 |
| 3.4 | 서버 읽기 조회(값 요약만) | `/api/show` `template_source=go_template`; ollama 로그 `template selection` 의 `model=registry.ollama.ai/library/qwen3:4b selected=go_template`(2건 모두); 생성용 llama-server(`-c 8192`, `--embedding` 없음) 인자에 `--no-jinja --chat-template chatml` 있음 = `DisableJinja=usesOllamaRenderedChat`(`O:server/routes.go#L2944`, `O:llm/llama_server.go#L812-L822` — "chatml is a startup-only placeholder and must not be used for request routing"); 서비스 환경에 `OLLAMA_GO_TEMPLATE` 없음 |

결론: 이 모델의 `/v1/chat/completions` 는 llama-server Jinja 가 아니라 Ollama 가 저장한 Go template(SHA `2d54db2b…97f9`)을 `text/template` 으로 렌더해 llama-server `/completion` 에 문자열로 보낸다.

## 4. 요청 경로 대조 (`/v1/chat/completions`)

1. `O:server/routes.go#L2436` → `middleware.ChatMiddleware` → `openai.FromChatRequest`(`O:openai/openai.go#L620-L640`): 문자열 content 를 그대로 `api.Message{Role, Content}` 로. `reasoning_effort` 미전송이면 think 는 모델 thinking 메타 기본값으로 정해진다(`#L796-L812`, `O:server/routes.go#L3207-L3216`). 아래 §5 렌더 결과는 think 값과 무관하다(template 이 `IsThinkSet` 을 assistant 분기에서만 읽음).
2. `chatMessagesForRequest`(`O:server/routes.go#L3649-L3660`): Modelfile `MESSAGE` 없음(빈 목록), 첫 메시지가 system 이면 `m.System` 주입 없음(Modelfile `SYSTEM` 도 없음). `filterThinkTags`(`#L3662-`)는 마지막 user 앞 assistant 만 건드림 — 대상 입력에 assistant 없음.
3. `chatModeForModel(m)` = Rendered 이므로 native 분기(`#L3269`)를 타지 않고 `chatPrompt`(`#L3295`, `O:server/prompt.go#L23-L92`)로 간다. 이미지 없음 → `imageTaggedMessages` 는 content 를 바꾸지 않음(`#L94-L133`). truncate 는 렌더 토큰 수 > `num_ctx`(8192)일 때만 앞 메시지를 뺀다.
4. `renderPrompt`(`O:server/prompt.go#L135-L156`): `Config.Renderer==""` 이므로 `m.Template.Execute(Values{Messages, Tools, Think, ThinkLevel, IsThinkSet})`.
5. `Template.Execute`(`O:template/template.go#L257-L282`): `collate`(`#L357-L374`)가 ①system 내용들을 `"\n\n"` 으로 이어 `.System` 으로 ②**연속 같은 역할(tool 제외) 메시지를 `"\n\n"` 으로 한 메시지로 합친다**. template 이 `.Messages` 를 쓰므로 messages 분기 — 전역 = `System`·`Messages`(합친 목록, system 메시지 포함)·`Tools`(nil)·`Response=""`·`Think`·`ThinkLevel`·`IsThinkSet`. 파서 = `text/template`, `missingkey=zero`(`#L14`, `#L145-L146`) — HTML escape 0.
6. 결과 문자열은 `LeadingBOS=leadingBOSForModel(m)`(Renderer 없음 → `""`, `O:server/routes.go#L2905-L2911`)과 함께 `Completion` 으로 가고, `completionPrompt`(`O:llm/llama_server.go#L272-L305`)는 tokenizer 가 BOS 를 붙이는 모델에서만 접두를 뗀다(`add_bos_token=false` → 변경 0). 요청은 llama-server `/completion`(`#L1778`)에 문자열 prompt 로 간다.
7. 따라서 계수 대상 문자열 = §5 의 Go template 렌더 결과다. llama-server `/completion` 의 special token 해석(`<|im_start|>` 등)은 v1 검토 §4.1-7 의 llama.cpp 근거를 따르며, 0.40.13 번들 llama.cpp 빌드 번호는 이번에 확인하지 않았다(미확인 — 실제 토큰 수 일치는 P03 v4 ⑤ token-golden·⑧ usage 대조 몫).

## 5. Go template 렌더 ↔ `ChatRenderers.ollamaGoQwen3Thinking` 대조

- 대상 입력(`MessageAssembler`): 첫 시도 = `[{system, S}, {user, U}]`, 재시도 = `[{system, S}, {user, U}, {user, R}]`(`MessageAssembler.retry` — 직전 출력 원문 없이 user 1개 추가). tools·assistant·tool 없음, content 는 문자열.
- 줄 단위 렌더(입력: `collate` 뒤 Messages = `[system S, user U']`, `U' = U` 또는 `U + "\n\n" + R`, `.System = S`, `.Tools` nil):
  - L1–L4: `$lastUserIdx` 계산만(대입은 출력 0), 모든 태그가 `{{-`/`-}}` 라 사이 개행 제거. L5 `{{-` 가 L4 뒤 개행 제거.
  - L5 `{{- if or .System .Tools }}` 참 → `<|im_start|>system\n`. L6 `{{ if .System }}{{ .System }}` → `S`, 이어 L6 끝·L7 빈 줄의 `\n\n` 은 `{{ end }}`(L8, `-` 없음) 앞 텍스트라 **그대로 남는다**. L8 뒤 `\n` 은 L9 `{{- if .Tools }}` 가 제거. `.Tools` 거짓 → L24 `{{- end -}}` 가 뒤따르는 `\n` 을 제거하고 `<|im_end|>\n`(L25) 출력. L26 `{{ end }}` 뒤 `\n` 은 L27 `{{-` 가 제거.
  - L27 range: system 메시지는 user/assistant/tool 분기 모두 거짓, L49 조건(`$last` 거짓) 거짓 → 출력 0. user 메시지(마지막, `$last` 참) → L29–L30 `<|im_start|>user\n` + `U'` + `<|im_end|>\n`(L31 `{{ else if` 에 `-` 없음 → 개행 유지). L49 `{{- if and (ne .Role "assistant") $last }}` 참 → `<|im_start|>assistant\n<think>\n`(L51 `{{ end }}` 앞 개행 유지). 나머지 개행은 `{{-` 로 제거.
  - 합계 = `<|im_start|>system\n` + S + `\n\n<|im_end|>\n<|im_start|>user\n` + U' + `<|im_end|>\n<|im_start|>assistant\n<think>\n`.
- v1 `chatMl` 과의 차이 3가지: ①system 내용 뒤 `\n\n` ②재시도의 두 user 가 한 블록(`\n\n` 연결)으로 합쳐짐(`chatMl` 은 user 블록 2개) ③생성 접두부 끝 `<think>\n`. 그래서 `chatMl` 재사용은 계수 문자열이 달라 부적합하다.
- `ChatRenderers.ollamaGoQwen3Thinking`(`gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/ChatRenderers.java`): 첫 system(내용이 빈 문자열이면 블록 생략 — template 의 `if .System` 과 같음) + 연속 user 를 `"\n\n"` 으로 이은 user 블록 1개 + `<|im_start|>assistant\n<think>\n`. 범위 밖 입력은 `RENDERER_INPUT_UNSUPPORTED`(exit 2)로 렌더하지 않는다.
- **실제 Go 엔진 대조(이 세션, 로컬):** Docker `golang:1.24-alpine`(go1.24.13, `--network none`)에서 저장 template 을 `text/template`(`missingkey=zero`)으로 파싱하고 Ollama `collate` 를 같은 규칙으로 옮긴 값(`System`·`Messages`·`Tools=nil`·`Response=""`·`Think`·`IsThinkSet`)으로 실행했다. 6 사례(①S/U ②여러 줄 system·끝 개행·한국어·JSON user + 재시도 user ③v2 분류 프롬프트 원문 system + JSON user ④③ + 재시도 ⑤system 없음 ⑥앞뒤 공백·개행 content) 모두 위 합계 공식과 **바이트 동일**(MATCH 6/6). 이 대조 프로그램은 레포에 넣지 않았다(Ollama 의 `template` 패키지 자체가 아니라 같은 값 구성의 재현이다).
- 레포 시험 `F0V4ChatRendererTest` 가 등록 파일 → 렌더러 선택, 위 문자열(첫 시도·재시도), v1 등록·`chatMl` 불변, 범위 밖 거부를 검사한다.

## 6. 적용 한계(등록 조건)

- 등록은 template SHA `2d54db2b…97f9` 에만 묶인다(`ChatRenderers.forTemplate` 가 측정 시점 `/api/show` template SHA 와 대조). template·모델 digest·Ollama 버전이 바뀌면 재검토가 필요하다.
- 근거 범위: system(첫 위치·선택) + user 1개 이상, tools 없음, assistant·tool 메시지 없음. assistant 이력(L31–L43 의 `<think>{{ .Thinking }}</think>`·`IsThinkSet` 조건), tools(L9–L24), tool 응답(L44–L48)은 다르게 렌더되며 이 등록의 근거가 아니다. 단 마지막 user 앞 assistant(사고 블록 미진입) 대화는 §7 에서 범위에 넣었다.
- 이 template 은 생성 접두부에서 `<think>\n` 을 열므로 모델 출력은 사고문부터 시작한다. Ollama 는 think 가 켜져 있고 template 에서 여닫는 태그를 추론하면(`thinkingparser.InferTags`, `O:server/routes.go#L3316-L3327`) 프롬프트 끝의 여는 태그를 파서에 먼저 넣어 사고문을 분리한다. 응답의 사고 분리 필드·usage 포함 여부는 이 검토 범위 밖이며 P03 v4 ⑧ 진단 몫이다(§12.26 8) 미확인 항목 그대로).
- `num_ctx` 8192 초과 시 `chatPrompt` truncate 가 앞 메시지를 뺀다. 입력 상한 3,072 는 이보다 작다.
- 추론 호출 0 — 실제 `prompt_tokens` 와 pin tokenizer 계수의 일치는 이후 단계(⑤ token-golden, ⑧ usage)에서 확인한다.

## 7. 범위 확장 — 마지막 user 앞 assistant 대화 (2026-11-04, P03 v4 ④⑤ 세션, 독립 Claude Code — claude-opus-5-5)

- 사유: v4 입력 audit(⑤)는 등록 token-golden 입력 `eval/f0/v2/token-golden-input-v2.jsonl` 을 바이트 그대로 재사용한다. 그 `TG07-MULTI-TURN`(system·user·assistant·user — Plan P03 token golden 의 "다중 턴" 요구 행)이 §5 범위 밖이라 `RENDERER_INPUT_UNSUPPORTED` 로 audit 를 만들 수 없었다. 추론 호출·서버 접근 0(이 절은 저장 template 과 tagged source 만 읽음).
- source(같은 tag `04088e7c…d5b`): `O:server/routes.go#L3662-L3688` `filterThinkTags` — 모델 family 가 qwen3 이면 마지막 user 앞 assistant 의 content 를 `thinking.Parser{<think>, </think>}.AddContent` 의 content 출력으로 바꾼다. `O:thinking/parser.go` `eat`: 왼쪽 공백을 뺀 값이 여는 태그로 시작하지 않고, 여는 태그의 접두도 아니고, 비어 있지 않으면 **공백을 빼기 전 원문 그대로** 돌려준다(`ThinkingDone`). 그래서 첫 글자가 공백류도 `<` 도 아닌 비어 있지 않은 content 는 바뀌지 않는다. 그 밖(빈 값·공백 시작·`<` 시작)은 렌더러가 거부한다(`RENDERER_INPUT_UNSUPPORTED`).
- template 렌더(collate 뒤): 마지막이 user 이므로 그 앞 assistant 는 `$last` 거짓·`gt $i $lastUserIdx` 거짓 → L33 사고 블록 미진입, L36 `{{ if .Content }}{{ .Content }}{{ end }}`, L43 `{{ if not $last }}<|im_end|>
{{ end }}` → `<|im_start|>assistant
` + C + `<|im_end|>
`. user 는 위치와 무관하게 `<|im_start|>user
` + C + `<|im_end|>
`. 연속 같은 역할은 `collate` 가 `

` 으로 합친다. 마지막 user 뒤 생성 접두부 `<|im_start|>assistant
<think>
` 은 §5 와 같다.
- `ChatRenderers.ollamaGoQwen3Thinking` 를 위 규칙으로 넓혔다(system 첫 위치·선택, 그 뒤 user·assistant, 마지막은 user). §5 범위 입력의 출력은 바이트 그대로다.
- **실제 Go 엔진 대조:** Docker `golang:1.24-alpine`(go1.24.13, `--network none`)에서 Ollama v0.40.13 `thinking/parser.go` 바이트 그대로 + `filterThinkTags`(qwen3 분기)·`collate` 를 같은 규칙으로 옮겨 저장 template(SHA `2d54db2b…97f9`)을 실행했다. 7 사례(①TG07 원문 ②system·user·assistant·user·재시도 user ③연속 assistant 병합(끝 개행) ④content 중간 `</think>` ⑤빈 system ⑥S/U ⑦user 만)의 렌더 SHA-256 을 레포 시험 `F0V4ChatRendererTest.v4RendererMatchesGoTemplateForAssistantTurnsBeforeTheLastUser` 가 Java 렌더 SHA 와 대조한다(⑥⑦ 은 §5 결과와 같음). 대조 프로그램은 레포에 넣지 않았다.
- 한계: assistant `Thinking` 필드(OpenAI 호환 `reasoning` 입력)·tool·tools·마지막 assistant(이어 쓰기)는 여전히 범위 밖이다.
