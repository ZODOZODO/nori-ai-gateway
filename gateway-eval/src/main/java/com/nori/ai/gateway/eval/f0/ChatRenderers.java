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
}
