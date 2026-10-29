package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.util.*;

/** TC Registry·리소스 fixture. 분류 프롬프트의 참고 정보 블록과 Path A Tool 목록을 결정론으로 렌더한다. */
public final class RegistryFixture {
    public static final List<String> TASK_TYPES = List.of("KQ", "LQ", "INC", "GQ", "UNS");
    private final JsonNode data;
    private final byte[] bytes;
    private final Map<String,String> capabilityStatus = new LinkedHashMap<>();
    private final Map<String,String> workflowByT = new LinkedHashMap<>();
    private final Map<String,List<String>> stepsByT = new LinkedHashMap<>();
    private final ResourceFixture resources;

    private RegistryFixture(byte[] bytes) {
        this.bytes = bytes.clone(); this.data = Json.parse(bytes);
        if (!"TC".equals(data.path("boundSystem").asText()) || !data.path("systems").isArray() || data.path("systems").isEmpty()) throw new EvalException(2, "FIXTURE_INVALID");
        for (JsonNode c : data.path("capabilities")) {
            String id = c.path("capabilityId").asText(""), status = c.path("status").asText("");
            if (!id.matches("tc\\.[a-z]+\\.[A-Za-z]+") || !Set.of("IMPLEMENTED", "DISABLED", "PLANNED").contains(status) || capabilityStatus.put(id, status) != null) throw new EvalException(2, "FIXTURE_INVALID");
        }
        for (JsonNode w : data.path("workflows")) {
            String t = w.path("t").asText("");
            if (!TASK_TYPES.contains(t) || t.equals("UNS") || workflowByT.put(t, w.path("taskType").asText()) != null) throw new EvalException(2, "FIXTURE_INVALID");
            List<String> steps = new ArrayList<>(); w.path("steps").forEach(s -> steps.add(s.asText()));
            for (String step : steps) if (!"IMPLEMENTED".equals(capabilityStatus.get(step))) throw new EvalException(2, "FIXTURE_INVALID");
            stepsByT.put(t, List.copyOf(steps));
        }
        if (!workflowByT.keySet().equals(Set.of("KQ", "LQ", "INC", "GQ"))) throw new EvalException(2, "FIXTURE_INVALID");
        resources = new ResourceFixture(data.path("resources"));
    }
    public static RegistryFixture load(Path path) { return new RegistryFixture(Json.bytes(path)); }
    public byte[] bytes() { return bytes.clone(); }
    public ResourceFixture resources() { return resources; }
    /** 분류 결과 t 에 대응하는 고정 workflow(taskType 이름). UNS 는 workflow 없음. */
    public String workflowFor(String t) { return workflowByT.get(t); }
    public List<String> stepsFor(String t) { return stepsByT.getOrDefault(t, List.of()); }
    public boolean implemented(String capabilityId) { return "IMPLEMENTED".equals(capabilityStatus.get(capabilityId)); }
    public boolean known(String capabilityId) { return capabilityStatus.containsKey(capabilityId); }

    public String renderRegistry() {
        StringBuilder text = new StringBuilder("boundSystem: TC\n");
        for (JsonNode s : data.path("systems")) {
            text.append("- 시스템 ").append(s.path("system").asText()).append(" (별칭: ").append(join(s.path("aliases"))).append(") 소유 영역: ").append(join(s.path("ownedAreas"))).append('\n');
        }
        List<String> active = new ArrayList<>(), inactive = new ArrayList<>();
        for (JsonNode c : data.path("capabilities")) (c.path("status").asText().equals("IMPLEMENTED") ? active : inactive).add(c.path("capabilityId").asText() + (c.path("status").asText().equals("IMPLEMENTED") ? "" : "(" + c.path("phase").asText() + " 비활성)"));
        text.append("- TC 활성 기능(1차): ").append(String.join(", ", active)).append('\n');
        text.append("- TC 비활성 기능(후속 차수): ").append(String.join(", ", inactive)).append('\n');
        return text.toString();
    }
    public String renderTools() {
        StringBuilder text = new StringBuilder();
        for (JsonNode c : data.path("capabilities")) if (c.path("status").asText().equals("IMPLEMENTED")) text.append("- ").append(c.path("capabilityId").asText()).append(" 인자: ").append(c.path("args").isEmpty() ? "없음" : join(c.path("args"))).append('\n');
        return text.toString();
    }
    private static String join(JsonNode values) { List<String> items = new ArrayList<>(); values.forEach(v -> items.add(v.asText())); return String.join(", ", items); }
}
