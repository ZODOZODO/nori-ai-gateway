package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.util.*;

/**
 * /api/generate raw 에 보낼 "chat 과 같은 렌더 문자열" 의 렌더러. 모델 chat template 을 추정하지 않기 위해,
 * 캡처한 template 의 SHA-256 이 사전등록 파일(eval/f0/chat-renderers-v1.json)에 검토 기록과 함께 등록된 경우에만 렌더러를 쓴다.
 */
public final class ChatRenderers {
    public static final String REGISTRY = "eval/f0/chat-renderers-v1.json";
    public interface Renderer { String render(List<Map<String,String>> messages); }
    private ChatRenderers() {}
    public static Renderer forTemplate(Path registry, String templateSha256) {
        JsonNode root = Json.read(registry);
        if (!root.path("renderers").isArray()) throw new EvalException(2, "RENDERER_REGISTRY_INVALID");
        for (JsonNode entry : root.path("renderers")) {
            if (!entry.path("templateSha256").asText().matches("[0-9a-f]{64}") || entry.path("reviewRef").asText("").isBlank()) throw new EvalException(2, "RENDERER_REGISTRY_INVALID");
            if (!entry.path("templateSha256").asText().equals(templateSha256)) continue;
            if (entry.path("renderer").asText().equals("CHATML_NO_SYSTEM_DEFAULT")) return ChatRenderers::chatMl;
            if (entry.path("renderer").asText().equals(OLLAMA_GO_QWEN3_THINKING)) return ChatRenderers::ollamaGoQwen3Thinking;
            throw new EvalException(2, "RENDERER_REGISTRY_INVALID");
        }
        throw new EvalException(2, "TEMPLATE_RENDERER_UNVERIFIED");
    }
    /** ChatML 형식(&lt;|im_start|&gt;역할\n내용&lt;|im_end|&gt;\n … &lt;|im_start|&gt;assistant\n). 등록 검토가 이 형식과 캡처 template 의 일치를 확인한 경우에만 선택된다. */
    static String chatMl(List<Map<String,String>> messages) {
        StringBuilder text = new StringBuilder();
        for (Map<String,String> m : messages) text.append("<|im_start|>").append(m.get("role")).append('\n').append(m.get("content")).append("<|im_end|>\n");
        return text.append("<|im_start|>assistant\n").toString();
    }
    /** v4 등록 렌더러 이름(eval/f0/v4/chat-renderers-v4.json, 근거 docs/gates/f0/template-review-v4.md). */
    public static final String OLLAMA_GO_QWEN3_THINKING = "OLLAMA_GO_QWEN3_THINKING_V1";
    /**
     * Ollama 0.40.13 Go template(qwen3:4b = Qwen3-4B-Thinking-2507)이 system(첫 위치, 선택) + user·assistant 대화(마지막은 user)를 렌더한 문자열.
     * chatMl 과 다른 점: Ollama template.collate 가 연속 같은 역할을 "\n\n" 으로 한 메시지로 합치고(재시도 메시지), system 내용 뒤에 빈 줄을 두며,
     * 생성 접두부가 "&lt;think&gt;\n" 을 연다. 마지막 user 앞 assistant 는 사고 블록 없이 내용만 렌더됩니다(template 의 IsThinkSet·Thinking 분기 미진입).
     * 검토 범위 밖(tool 메시지, 첫 위치가 아닌 system, 마지막이 user 가 아님, filterThinkTags 가 내용을 바꿀 수 있는 assistant 내용)은 렌더하지 않습니다.
     */
    static String ollamaGoQwen3Thinking(List<Map<String,String>> messages) {
        int first = !messages.isEmpty() && "system".equals(messages.get(0).get("role")) ? 1 : 0;
        List<String[]> turns = new ArrayList<>();
        for (Map<String,String> m : messages.subList(first, messages.size())) {
            String role = m.get("role"), content = m.get("content");
            if (!"user".equals(role) && !("assistant".equals(role) && thinkFilterKeeps(content))) throw new EvalException(2, "RENDERER_INPUT_UNSUPPORTED");
            if (!turns.isEmpty() && turns.getLast()[0].equals(role)) turns.getLast()[1] += "\n\n" + content;
            else turns.add(new String[]{role, content});
        }
        if (turns.isEmpty() || !"user".equals(turns.getLast()[0])) throw new EvalException(2, "RENDERER_INPUT_UNSUPPORTED");
        StringBuilder text = new StringBuilder();
        if (first == 1 && !messages.get(0).get("content").isEmpty()) text.append("<|im_start|>system\n").append(messages.get(0).get("content")).append("\n\n<|im_end|>\n");
        for (String[] turn : turns) text.append("<|im_start|>").append(turn[0]).append('\n').append(turn[1]).append("<|im_end|>\n");
        return text.append("<|im_start|>assistant\n<think>\n").toString();
    }
    /**
     * Ollama filterThinkTags(qwen3)는 마지막 user 앞 assistant 내용을 thinking parser 로 다시 쓴다. 첫 글자가 공백류도 '&lt;' 도 아닌 비어 있지 않은 내용만
     * parser 가 원문 그대로 돌려주므로(여는 태그·그 접두·공백만 내용은 바뀜) 그 범위만 렌더합니다.
     */
    private static boolean thinkFilterKeeps(String content) {
        if (content.isEmpty()) return false;
        int c = content.codePointAt(0);
        return c != '<' && !Character.isWhitespace(c) && !Character.isSpaceChar(c);
    }
}
