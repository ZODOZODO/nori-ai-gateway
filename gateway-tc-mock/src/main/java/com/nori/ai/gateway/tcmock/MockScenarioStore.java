package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.TcBusinessReadPort.*;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MockScenarioStore {
    public record Scenario(String id, String contractVersion, boolean authorizationUnavailable, boolean delegationInvalid,
            boolean catalogAvailable, String catalogRevision, Set<String> grantedPermissions, EqpSnapshot eqpSnapshot,
            ActiveWorkLots workLots, List<RecentEvent> events, Map<String, DlqData> dlqs,
            Map<String, CommLogData> commLogs, List<ModelNode> models, List<Resource> resources) {
        public Scenario {
            if (id == null || !id.matches("[a-z0-9-]+") || contractVersion == null || catalogRevision == null || catalogRevision.isBlank())
                throw new IllegalArgumentException("Mock 시나리오 계약이 잘못되었습니다");
            grantedPermissions = Set.copyOf(grantedPermissions); events = List.copyOf(events);
            dlqs = Map.copyOf(dlqs); commLogs = Map.copyOf(commLogs); models = List.copyOf(models); resources = List.copyOf(resources);
            Objects.requireNonNull(eqpSnapshot); Objects.requireNonNull(workLots);
        }
    }
    record ScenarioDefinition(Scenario legacy,Map<String,List<MockDlqRow>> attributed) {
        ScenarioDefinition { attributed=Map.copyOf(attributed); }
    }
    private final Map<String, ScenarioDefinition> scenarios;
    private final Path stateFile;
    private Scenario current;
    public MockScenarioStore() { this(null); }
    public MockScenarioStore(Path stateDirectory) {
        Map<String, ScenarioDefinition> loaded = new HashMap<>();
        for (var item : resource("tcmock/scenarios/index.json")) {
            if (!item.isString() || !item.asString().matches("[a-z0-9-]+")) throw new IllegalArgumentException("Mock 시나리오 목록이 잘못되었습니다");
            var raw=resource("tcmock/scenarios/" + item.asString() + ".json");
            Map<String,List<MockDlqRow>> attributed=new HashMap<>();
            for (var source : raw.get("dlqs").properties()) {
                List<MockDlqRow> rows=new ArrayList<>();
                for (var row : source.getValue().get("items")) {
                    var eqp=row.get("eqpId");
                    if (eqp==null || !eqp.isString() || eqp.asString().isBlank() || eqp.asString().codePointCount(0,eqp.asString().length())>64) throw new IllegalArgumentException("DLQ 설비 귀속이 잘못되었습니다");
                    ((tools.jackson.databind.node.ObjectNode)row).remove("eqpId");
                    rows.add(new MockDlqRow(eqp.asString(),SchemaContracts.JSON.treeToValue(row,DlqEntry.class)));
                }
                attributed.put(source.getKey(),List.copyOf(rows));
            }
            Scenario scenario = SchemaContracts.JSON.treeToValue(raw, Scenario.class);
            if (!scenario.id().equals(item.asString()) || loaded.put(scenario.id(), new ScenarioDefinition(scenario,attributed)) != null)
                throw new IllegalArgumentException("Mock 시나리오 목록이 잘못되었습니다");
        }
        scenarios = Map.copyOf(loaded);
        stateFile = stateDirectory == null ? null : stateDirectory.resolve("scenario.json");
        String initial = "default";
        if (stateFile != null && Files.exists(stateFile)) {
            try {
                var stored = SchemaContracts.parse(Files.readString(stateFile));
                if (!stored.isString()) throw new IllegalArgumentException("Mock 저장 상태가 잘못되었습니다");
                initial = stored.asString();
            } catch (IOException failure) { throw new IllegalStateException("Mock 저장 상태를 읽을 수 없습니다"); }
        }
        current = requireScenario(initial);
    }
    public synchronized Scenario current() { return current; }
    synchronized ScenarioDefinition attributedSnapshot() { return scenarios.get(current.id()); }
    public synchronized void select(String id) {
        Scenario next = requireScenario(id);
        try { if (stateFile != null) MockStateFiles.write(stateFile, SchemaContracts.JSON.writeValueAsString(id)); }
        catch (IOException | RuntimeException failure) { throw new IllegalStateException("Mock 시나리오를 저장할 수 없습니다"); }
        current = next;
    }
    public Set<String> ids() { return scenarios.keySet(); }
    private Scenario requireScenario(String id) {
        ScenarioDefinition value = scenarios.get(id);
        if (value == null) throw new IllegalArgumentException("등록되지 않은 Mock 시나리오입니다");
        return value.legacy();
    }
    private tools.jackson.databind.JsonNode resource(String name) {
        try (var input = MockScenarioStore.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalArgumentException("Mock 시나리오가 없습니다");
            return SchemaContracts.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) { throw new IllegalStateException("Mock 시나리오를 읽을 수 없습니다"); }
    }
}
