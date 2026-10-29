# qwen3:4b-instruct chat template 검토 — usage-probe 렌더러 등록 근거 (P03 준비)

- 작성일: 2026-10-30 (P03 준비 세션, 독립 Claude Code — claude-opus-5-5). 추론·모델 로드·서비스/설정 변경 0. Jetson 접근은 읽기 조회뿐이며 응답 원문은 출력·저장하지 않았다(`/api/show` 의 `template` 필드만 저장).
- 판정: **일치 확인 — `CHATML_NO_SYSTEM_DEFAULT` 등록**(`eval/f0/chat-renderers-v1.json`). 적용 범위는 아래 §5 의 조건(system 1개 + user 1개, tools 없음, assistant 이력 없음 = usage-probe U1·U2)으로 한정한다.

## 1. 검토 대상

| 항목 | 값 |
|---|---|
| 런타임 | Jetson Ollama `0.31.1`(`/api/version`), 모델 `qwen3:4b-instruct` digest `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0`(직전 독립 조회, ctx 4096, `bge-m3:latest` 와 함께 상주) |
| template 사전검토 파일 | `docs/gates/f0/preparation-template.template` — `/api/show {"model":"qwen3:4b-instruct"}` 의 `template` 필드만 원격에서 추출해 UTF-8 바이트 그대로 저장. 4040 바이트, CR 0(LF), 끝 개행 없음(마지막 바이트 `{%- endif %}`) |
| template SHA-256 | **`40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541`** — 직전 독립 조회 값과 일치 |
| 형식 | HF Jinja(`{%- … %}`·`{{- … }}`). Go `text/template` 이 아님 |
| `/api/show` 메타 요약(키·판별 값만) | `details.format=gguf`, `family=qwen3`, `general.architecture=qwen3`, `renderer`·`parser` 필드 없음, `capabilities=[tools, thinking, completion]`, Modelfile 지시어에 `SYSTEM`·`MESSAGE`·`RENDERER`·`PARSER` 없음 |
| 공식 측정 캡처 위치 | `eval/f0/chat-template/` 는 P03 6단계 `f0 chat-template` 명령용으로 **만들지 않고 비워 둠** |

## 2. 공식 source (tagged)

- Ollama `v0.31.1` = commit `710292ff4f191d8da9f6a4230804fbc693338d4a`(`git ls-remote` 의 `refs/tags/v0.31.1`). 아래 `O:` = `https://github.com/ollama/ollama/blob/710292ff4f191d8da9f6a4230804fbc693338d4a/`.
- 그 tag 의 `LLAMA_CPP_VERSION` = `b9840` → llama.cpp `b9840` = commit `8c146a8366304c871efc26057cc90370ccf58dad`(`refs/tags/b9840`). 아래 `L:` = `https://github.com/ggml-org/llama.cpp/blob/8c146a8366304c871efc26057cc90370ccf58dad/`.
- `server/prompt.go`·`template/template.go`(legacy Go 경로)는 이 모델에서 선택되지 않음을 아래 §3 로 확인했으므로 근거로 쓰지 않는다.

## 3. 실제 경로: native(llama-server Jinja) 모드

| # | 근거 | 내용 |
|---|---|---|
| 3.1 | `O:llm/llama_server.go#L1-L6` | "Models with explicit Ollama renderers/parsers, Harmony handling, MLX, or an enabled Go TEMPLATE layer still render prompts in Go and call /completion. Other GGUF chat models use llama-server's chat_template handling through /v1/chat/completions." |
| 3.2 | `O:server/routes.go#L2381-L2409` | `chatModeForModel` = Rendered iff `IsMLX() \|\| usesOllamaRenderedChat(m)`; `usesOllamaRenderedChat` = `Renderer!="" \|\| Parser!="" \|\| shouldUseHarmony \|\| shouldUseGoTemplate`; `llamaServerConfigForModel.DisableJinja = usesOllamaRenderedChat(m)` |
| 3.3 | `O:llm/llama_server.go#L718-L729`(호출 `#L368`) | `DisableJinja` 일 때만 `--no-jinja --chat-template chatml` 추가 — 그 밖에는 llama-server 기본값 |
| 3.4 | `L:common/common.h#L631` | llama-server `use_jinja = true` 기본(`L:common/arg.cpp#L1163-L1172` 에서 SERVER 예제는 끄지 않음) |
| 3.5 | `O:server/images.go#L673-L707`, `#L754-L759` | `HasChatTemplate` = GGUF `tokenizer.chat_template` 존재, `HasGoTemplate` = template layer 존재, `PreferChatTemplate` 조건 |
| 3.6 | `O:server/routes.go#L1567`, `#L1618-L1625` | `/api/show` 의 `template` = `HasChatTemplate && native` 이면 GGUF `tokenizer.chat_template`, 아니면 Go template. **Jinja 문자열이 반환됐다는 것 자체가 native 모드 + GGUF chat_template 반환 분기**임을 뜻함(Go template 파서는 이 Jinja 를 받아들이지 못함) |
| 3.7 | Jetson 읽기 조회(값 요약만) | 생성용 llama-server 프로세스 인자에 `--no-jinja`·`--chat-template` **없음**(임베딩 프로세스도 동일), ollama 로그 `template selection` 의 `model=…/qwen3:4b-instruct selected=gguf_chat_template`(4건 모두), 서비스 unit 에 `OLLAMA_GO_TEMPLATE` 미설정. 선택 출처 문자열은 `O:server/images.go#L314-L330`(`selectedTemplateSource`)·`#L369-L382`(`logTemplateSelection`) |

결론: 이 모델의 `/v1/chat/completions` 는 Ollama Go 렌더가 아니라 llama-server 가 GGUF `tokenizer.chat_template`(= 저장한 template, SHA `40c21f34…b541`)를 llama.cpp Jinja 엔진으로 렌더한다.

## 4. 요청 경로 대조 (chat 과 raw generate)

### 4.1 `/v1/chat/completions` (F0 `OllamaChatClient` body = `{model, messages, temperature, max_tokens, stream:false}`)

1. `O:server/routes.go#L1911` → `middleware.ChatMiddleware`(`O:middleware/openai.go#L408-L424`) → `openai.FromChatRequest`(`O:openai/openai.go#L482-L498`): 문자열 content 를 그대로 `api.Message{Role, Content}` 로. `reasoning_effort` 미전송이므로 `think=nil`(`#L635-L647`).
2. `ChatHandler`: 모델 capability 에 thinking 이 있어 `req.Think=true` 기본(`O:server/routes.go#L2640-L2645`). 첫 메시지가 `system` 이므로 `m.System` 주입 없음, `m.Messages` 는 Modelfile `MESSAGE` 가 없어 빈 목록(`#L2680-L2684`). `filterThinkTags` 는 마지막 user 앞 assistant 만 건드림(`#L3141-L3167`) — U1·U2 에 assistant 없음. native 분기 `#L2698-L2699` → `handleNativeChat`(`#L2968-L2980`). `truncateNativeChatMessages`(`#L3059-L3116`)는 렌더 토큰 수 > `num_ctx` 일 때만 메시지를 자름(내용 변경 0).
3. `O:llm/llama_server.go#L2045-L2095` `llamaServerChatRequest`: `messages`(content 문자열 그대로, `#L2113-L2134`), tools 는 `len(req.Tools)>0` 일 때만(`#L2078`), `chat_template_kwargs={"enable_thinking":true}`(`#L2085-L2087`, `#L2097-L2110`), `response_format` 은 `format` 있을 때만. `Chat` 은 llama-server `/v1/chat/completions` 로 POST(`#L1830`).
4. llama-server `post_chat_completions`(`L:tools/server/server-context.cpp#L4716-L4730`) → `oaicompat_chat_params_parse`: `add_generation_prompt` 기본 true(`L:tools/server/server-common.cpp#L1044`), 마지막이 assistant 가 아니므로 prefill 없음(`#L1048-L1058`), kwargs `enable_thinking` 반영(`#L1072-L1086`), `common_chat_templates_apply` 결과 `chat_params.prompt` 가 그대로 `llama_params["prompt"]`(`#L1091-L1094`).
5. `L:common/chat.cpp#L2826-L2831` → `common_chat_templates_apply_jinja`(`#L2629-L2730`): `render_message_to_json`(`#L471-L501`) — caps 가 string content 만 허용(`L:common/jinja/caps.cpp#L103-L137`: content 를 배열로 접근하지 않음, 기본값 `L:common/jinja/caps.h#L20-L21`)이므로 content 는 문자열 유지. specialized template 판별(`#L2536-L2627`)의 표지(`[SYSTEM_PROMPT]`, `<|channel|>`, `>>>all`, `<|tool_calls_section_begin|>`, `<|START_TEXT|>`, `<|tool_list_start|>`, `List of tools: [`, `<|role_sep|>`, `dsml_token`, `'<|tool_call>call:'`, `Tool usage guidelines:`)는 저장 template 에 모두 0회 → autoparser 경로. `L:common/chat-auto-parser-generator.cpp#L41-L70`: `data.prompt = common_chat_template_direct_apply(tmpl, inputs)`, 이후 `continue_final_message` 일 때만 덧붙임(해당 없음).
6. `common_chat_template_direct_apply_impl`(`L:common/chat.cpp#L883-L937`): Jinja 전역 = `messages`, `bos_token`, `eos_token`, `enable_thinking`, extra(`datetime`·`date_string`, `#L2526-L2534`)·kwargs, `add_generation_prompt=true`(`#L913`), `tools` 는 비어 있지 않을 때만(`#L897`). 저장 template 은 `enable_thinking`·`datetime`·`date_string`·`reasoning_effort`·`bos_token`·`eos_token`·`preserve_reasoning` 을 **참조 0회** → 렌더 결과 영향 0. 결과의 BOS/EOS 제거(`#L930-L935`)는 결과가 BOS 로 시작하거나 EOS 로 끝날 때만 — ChatML 결과(`<|im_start|>` 시작, `assistant\n` 끝)에는 해당 없음.
7. 토큰화: `handle_completions_impl` 에서 `tokenize_input_prompts(…, add_special=true, parse_special=true)`(`L:tools/server/server-context.cpp#L4114`; 텍스트 모델이라 mtmd 분기 아님). `generation_prompt` 는 sampler/grammar prefill 용으로만 쓰이고 프롬프트에 덧붙지 않음(`L:common/sampling.cpp#L266-L279`).

### 4.2 `/api/generate raw:true` (usage-probe 의 비교 호출)

- `O:server/routes.go#L510-L512`: `prompt := req.Prompt; if !req.Raw { … }` — raw 는 template·native apply 를 모두 건너뜀, `leadingBOS=""`.
- `O:llm/llama_server.go#L232-L244`·`#L268-L271`: `completionPrompt` 는 tokenizer 가 BOS 를 추가하는 모델에서 문자열이 `<bos>` 로 시작할 때만 그 접두를 제거 — ChatML 문자열은 해당 없음. llama-server `/completion`(`#L1536`) → `post_completions`(`L:tools/server/server-context.cpp#L4692-L4702`) → 같은 `handle_completions_impl` 토큰화(`add_special=true, parse_special=true`, `#L4114`).
- 따라서 두 경로의 토큰화 입력이 같은 문자열이면 같은 토큰화 규칙이 적용된다(prompt cache 효과는 G-USAGE 측정 대상이며 이 검토 범위 밖).

## 5. Jinja 렌더 ↔ `ChatRenderers.chatMl` (U1·U2) 대조

- U1·U2(`eval/f0/usage-probe-input-v1.jsonl`, `MessageAssembler.messages`) = `[{role:system, content:S}, {role:user, content:U}]`, tools 없음, assistant 이력 없음, content 는 문자열.
- 저장 template 줄 단위 렌더(입력: 위 messages, `tools` 미정의, `add_generation_prompt=true`):
  - L1 `{%- if tools %}` 거짓 → L12–L15: `messages[0].role == 'system'` 이므로 L14 `'<|im_start|>system\n' + S + '<|im_end|>\n'`.
  - L17–L24: `ns` 계산만, 출력 0.
  - L25–L83: i=0 system 은 `loop.first` 라 L31 조건 거짓, assistant/tool 아님 → 출력 0. i=1 user → L26–L27 `content = U`, L31–L32 `'<|im_start|>user\n' + U + '<|im_end|>\n'`.
  - L84–L86: `'<|im_start|>assistant\n'`.
  - 합계 = `<|im_start|>system\nS<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\n`.
- `ChatRenderers.chatMl`(`gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/ChatRenderers.java:29-32`): 메시지마다 `<|im_start|>` + role + `\n` + content + `<|im_end|>\n`, 끝에 `<|im_start|>assistant\n` → 위 합계와 **바이트 동일**.
- 시스템 메시지: template 은 system 이 없으면 기본 system 을 넣지 않음(L13–L15) — 렌더러 이름 `CHATML_NO_SYSTEM_DEFAULT` 와 일치. Ollama 측 기본 system 주입도 없음(§4.1-2).
- assistant 생성 접두부: `add_generation_prompt=true`(llama-server 기본) → `<|im_start|>assistant\n` 만. `enable_thinking=true` 가 전달되지만 template 이 참조하지 않으므로 `<think>` 접두 추가 0 — `chatMl` 의 끝과 동일.
- whitespace: template 의 태그 사이 텍스트는 모두 개행·들여쓰기뿐이고 모든 태그가 `{%-`/`{{-` 로 시작하므로 llama.cpp 렉서가 앞 텍스트를 rstrip(`L:common/jinja/lexer.cpp#L197-L203`), 추가로 lstrip_blocks·trim_blocks 기본 true(`#L112-L118`, `#L162-L189`). 템플릿 끝 개행 없음(`#L56-L58` 무관), 원본 CR 0(`#L48-L51` 무관). 문자열 리터럴 `\n` 은 개행으로 해석(`L:common/jinja/lexer.h#L87-L97`). content(S·U)는 어떤 strip·치환도 받지 않음(L14·L32 는 `+` 연결만).
- 이전 세션의 정적 렌더(system+user, tools 없음) 결과가 `chatMl` 과 같았다는 기록과도 일치한다.

## 6. 적용 한계(등록 조건)

- 등록은 template SHA `40c21f34…b541` 에만 묶인다(`ChatRenderers.forTemplate` 가 측정 시점 `/api/show` template SHA 와 대조). template·모델 digest 가 바뀌면 재검토 필요.
- 근거 범위: system 1 + user 1, tools 없음, assistant 이력 없음. 다중 턴(assistant 가 마지막 user 뒤에 있으면 L43–L48 의 `<think>` 블록), tools, tool 메시지, system 이 첫 위치가 아닌 경우는 `chatMl` 과 다를 수 있으며 이 등록의 근거가 아니다. 현재 `ChatRenderers` 사용처는 usage-probe(U1·U2)뿐이다(`F0RunnerCli.java:93-96`).
- `truncateNativeChatMessages` 는 렌더 토큰 수가 `num_ctx`(4096)를 넘을 때만 개입한다. U1·U2 가 4096 이하라는 것은 P03 측정값(`promptTokens`)으로 확인된다.
- Jetson 의 llama-server 바이너리 빌드 번호는 로그에서 직접 관측하지 못했다. 근거는 Ollama `0.31.1` 공식 tag 의 `LLAMA_CPP_VERSION=b9840` 이다(공식 릴리스 바이너리를 쓴다는 전제).
- 추론 호출 0 — 실제 토큰 수 동일성·cache 거동은 P03 usage-probe 측정 몫이다.
