package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.result.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import tools.jackson.databind.*;

public final class MockTcBusinessReadAdapter implements TcBusinessReadPort {
    private final MockScenarioStore scenarios;
    private final TcSourceAuthorizationPort authorization;
    private final Clock clock;
    private final MockCallCounter calls;
    private final Map<String, CapabilityManifest> manifests;
    private static final JsonNode RESPONSE_SCHEMA = SchemaContracts.parse("""
        {"type":"object","required":["contractVersion","result"],"additionalProperties":false,"properties":{
        "contractVersion":{"const":"1.0"},"result":{"type":"object","required":["status","observedAt","data","missingFields","warnings","retentionMeta"],
        "additionalProperties":false,"properties":{"status":{"type":"string"},"observedAt":{"type":"string","format":"date-time"},"data":{},
        "missingFields":{"type":"array","items":{"type":"string"}},"warnings":{"type":"array","items":{"enum":["RETENTION_UNVERIFIED","LLM_UNAVAILABLE"]}},
        "retentionMeta":{"type":"object","required":["missingRanges","unverifiedRanges"],"additionalProperties":false,
        "properties":{"missingRanges":{"type":"array"},"unverifiedRanges":{"type":"array"}}}}}}}
        """);
    public MockTcBusinessReadAdapter(MockScenarioStore scenarios, TcSourceAuthorizationPort authorization, Clock clock,
            MockCallCounter calls, List<CapabilityManifest> manifests) {
        this.scenarios = Objects.requireNonNull(scenarios); this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock); this.calls = Objects.requireNonNull(calls);
        Map<String, CapabilityManifest> byId = new HashMap<>();
        manifests.forEach(m -> {
            if (!SchemaContracts.digest(m.inputSchema(), m.outputSchema()).equals(m.schemaDigest()))
                throw new IllegalArgumentException("업무 계약 Schema digest가 일치하지 않습니다");
            byId.put(m.toolId(), m);
        });
        this.manifests = Map.copyOf(byId);
    }
    public String schemaDigest(String capabilityId) {
        CapabilityManifest manifest = manifests.get(capabilityId);
        if (manifest == null) throw new IllegalArgumentException("등록되지 않은 업무 계약입니다");
        return SchemaContracts.digest(manifest.inputSchema(), manifest.outputSchema());
    }
    @Override public TcReadResult<EqpSnapshot> getEqpSnapshot(DelegatedCall call, String eqpId) {
        return read(call, "EQP_READ", "getEqpSnapshot", EqpSnapshot.class, () -> {
            EqpSnapshot snapshot = scenarios.current().eqpSnapshot();
            return result(snapshot.eqpId().equals(eqpId) ? ResultStatus.SUCCESS : ResultStatus.EMPTY,
                snapshot.eqpId().equals(eqpId) ? snapshot : null, List.of(), Set.of(), RetentionMeta.none());
        });
    }
    @Override public TcReadResult<ActiveWorkLots> getActiveWorkLots(DelegatedCall call, String eqpId) {
        return read(call, "AI_WORKLOT_READ", "getActiveWorkLots", ActiveWorkLots.class, () -> {
            ActiveWorkLots lots = scenarios.current().eqpSnapshot().eqpId().equals(eqpId) ? scenarios.current().workLots()
                : new ActiveWorkLots(WorkLotState.UNKNOWN, List.of(), Optional.empty());
            return result(lots.state() == WorkLotState.ACTIVE ? ResultStatus.SUCCESS : ResultStatus.PARTIAL,
                lots, lots.state() == WorkLotState.ACTIVE ? List.of() : List.of("currentLot"), Set.of(), RetentionMeta.none());
        });
    }
    @Override public TcReadResult<List<RecentEvent>> getRecentEvents(DelegatedCall call, String eqpId, TimeWindow window) {
        return read(call, "AI_EVENT_READ", "getRecentEvents", listType(RecentEvent.class), () -> {
            var events = scenarios.current().eqpSnapshot().eqpId().equals(eqpId) ? scenarios.current().events().stream()
                .filter(e -> contains(window, e.occurredAt())).toList() : List.<RecentEvent>of();
            return result(events.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, events, List.of(), Set.of(), RetentionMeta.none());
        });
    }
    @Override public TcReadResult<DlqData> listDlq(DelegatedCall call, String sourceId, TimeWindow window) {
        return read(call, "DLQ_READ", "listDlq", DlqData.class, () -> {
            var data = scenarios.current().dlqs().get(sourceId);
            if (data == null) return unavailable();
            var evaluated = new DlqCompletenessEvaluator().evaluate(data.completeness());
            var selected = data.items().stream().filter(item -> contains(window, item.occurredAt())).toList();
            ResultStatus status = evaluated.status() == ResultStatus.SUCCESS && selected.isEmpty() ? ResultStatus.EMPTY : evaluated.status();
            return result(status, status == ResultStatus.SOURCE_UNAVAILABLE ? null : new DlqData(selected, data.completeness()),
                evaluated.missingFields(), evaluated.warnings(), RetentionMeta.none());
        });
    }
    @Override public TcReadResult<DlqData> listDlq(DelegatedCall call,String eqpId,String sourceId,TimeWindow window) {
        if (eqpId==null || eqpId.isBlank() || eqpId.codePointCount(0,eqpId.length())>64 || window==null) throw new IllegalArgumentException("설비와 시간 구간을 확인해 주세요");
        return read(call,"DLQ_READ","listDlq",DlqData.class,()-> {
            var snapshot=scenarios.attributedSnapshot();
            var original=snapshot.legacy().dlqs().get(sourceId);
            if (original==null) return unavailable();
            var rows=snapshot.attributed().get(sourceId);
            if (rows==null) throw new IllegalStateException("DLQ 설비 귀속을 확인할 수 없습니다");
            var evaluated=new DlqCompletenessEvaluator().evaluate(original.completeness());
            var selected=rows.stream().filter(row->eqpId.equals(row.eqpId()) && contains(window,row.projection().occurredAt())).map(MockDlqRow::projection).toList();
            var status=evaluated.status()==ResultStatus.SUCCESS && selected.isEmpty()?ResultStatus.EMPTY:evaluated.status();
            return result(status,status==ResultStatus.SOURCE_UNAVAILABLE?null:new DlqData(selected,original.completeness()),evaluated.missingFields(),evaluated.warnings(),RetentionMeta.none());
        });
    }
    @Override public TcReadResult<CommLogData> getCommLogs(DelegatedCall call, String eqpId, String sourceId, TimeWindow window) {
        return read(call, "COMM_LOG_READ", "getCommLogs", CommLogData.class, () -> {
            var data = scenarios.current().commLogs().get(sourceId);
            if (data == null || !scenarios.current().eqpSnapshot().eqpId().equals(eqpId)) return unavailable();
            // 동일 조회의 분류와 항목 필터가 서로 다른 보존 경계를 사용하지 않게 합니다.
            Instant queryNow = clock.instant();
            var evaluated = new TrimCompletenessEvaluator().evaluate(window, data.completeness(), queryNow);
            var selected = data.items().stream().filter(item -> contains(window, item.occurredAt()))
                .filter(item -> !data.completeness().appliedValuesVerified() || !data.completeness().otherDeletionPathsExcluded()
                    || !item.occurredAt().isBefore(queryNow.minus(Duration.ofHours(data.completeness().retentionHours())))).toList();
            ResultStatus status = evaluated.status() == ResultStatus.SUCCESS && selected.isEmpty() ? ResultStatus.EMPTY : evaluated.status();
            return result(status, status == ResultStatus.SOURCE_UNAVAILABLE || status == ResultStatus.OUT_OF_RETENTION ? null : new CommLogData(selected, data.completeness()),
                List.of(), evaluated.warnings(), new RetentionMeta(evaluated.missingRanges(), evaluated.unverifiedRanges()));
        });
    }
    @Override public TcReadResult<List<ModelNode>> readModel(DelegatedCall call, Optional<String> modelId) {
        return read(call, "MODEL_READ", "readModel", listType(ModelNode.class), () -> {
            var models = scenarios.current().models().stream().filter(m -> modelId.isEmpty() || modelId.get().equals(m.modelId())).toList();
            return result(models.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, models, List.of(), Set.of(), RetentionMeta.none());
        });
    }
    @Override public TcReadResult<List<Resource>> resolveResource(DelegatedCall call, String type, String ref) {
        String permission = switch (type) { case "EQP" -> "EQP_READ"; case "MODEL" -> "MODEL_READ"; default -> null; };
        if (permission == null) return result(ResultStatus.DENIED, null, List.of(), Set.of(), RetentionMeta.none());
        return read(call, permission, "resolveResource", listType(Resource.class), () -> {
            var found = scenarios.current().resources().stream().filter(r -> r.type().equals(type) && r.ref().equals(ref)).toList();
            return result(found.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, found, List.of(), Set.of(), RetentionMeta.none());
        });
    }
    private <T> TcReadResult<T> read(DelegatedCall call, String permission, String operation, Class<T> type, Supplier<TcReadResult<T>> source) {
        return read(call, permission, operation, SchemaContracts.JSON.constructType(type), source);
    }
    private <T> TcReadResult<T> read(DelegatedCall call, String permission, String operation, JavaType type, Supplier<TcReadResult<T>> source) {
        Outcome outcome;
        try { outcome = authorization.authorize(call, Set.of(permission)); }
        catch (RuntimeException failure) { return unavailable(); }
        if (outcome == null || outcome instanceof Unavailable) return unavailable();
        if (outcome instanceof Allowed allowed && (allowed.pdg() == null || !allowed.pdg().matches("[0-9a-f]{64}")
                || !Set.of(permission).containsAll(allowed.allowed()))) return unavailable();
        if (!(outcome instanceof Allowed allowed) || !allowed.allowed().contains(permission))
            return result(ResultStatus.DENIED, null, List.of(), Set.of(), RetentionMeta.none());
        try {
            TcReadResult<T> result = source.get();
            if (result.status() != ResultStatus.OUT_OF_RETENTION) calls.increment(operation);
            validateManifestOutput(operation, result.data());
            var wire = SchemaContracts.JSON.createObjectNode().put("contractVersion", "resolveResource".equals(operation) && "contract-v2".equals(scenarios.current().id()) ? "1.0" : scenarios.current().contractVersion())
                .set("result", SchemaContracts.JSON.valueToTree(result));
            var decoded = SchemaContracts.parse(SchemaContracts.JSON.writeValueAsString(wire));
            if (!SchemaContracts.matches(RESPONSE_SCHEMA, decoded))
                return result(ResultStatus.FAILED, null, List.of("contractVersion"), Set.of(), RetentionMeta.none());
            return SchemaContracts.JSON.treeToValue(decoded.get("result"),
                SchemaContracts.JSON.getTypeFactory().constructParametricType(TcReadResult.class, type));
        } catch (RuntimeException failure) { return unavailable(); }
    }
    private void validateManifestOutput(String operation, Object data) {
        String capability = switch (operation) {
            case "getEqpSnapshot" -> "tc.eqp.getOperationalSnapshot";
            case "getRecentEvents" -> "tc.event.getRecent";
            case "listDlq" -> "tc.dlq.list";
            case "readModel" -> "tc.model.read";
            default -> null;
        };
        if (capability == null || data == null) return;
        CapabilityManifest manifest = manifests.get(capability);
        if (manifest == null) throw new IllegalArgumentException("등록되지 않은 업무 계약입니다");
        var output = SchemaContracts.JSON.createObjectNode();
        var items = output.putArray("items");
        if (data instanceof Collection<?> collection) collection.forEach(item -> items.add(SchemaContracts.JSON.valueToTree(item)));
        else if (data instanceof DlqData dlq) dlq.items().forEach(item -> items.add(SchemaContracts.JSON.valueToTree(item)));
        else items.add(SchemaContracts.JSON.valueToTree(data));
        if (!SchemaContracts.matches(manifest.outputSchema(), output)) throw new IllegalArgumentException("업무 응답 Schema가 일치하지 않습니다");
    }
    private JavaType listType(Class<?> item) { return SchemaContracts.JSON.getTypeFactory().constructCollectionType(List.class, item); }
    private boolean contains(TimeWindow window, Instant at) { return !at.isBefore(window.startAt()) && at.isBefore(window.endAt()); }
    private <T> TcReadResult<T> result(ResultStatus status, T data, List<String> missing, Set<Warning> warnings, RetentionMeta retention) {
        return new TcReadResult<>(status, clock.instant(), data, missing, warnings, retention);
    }
    private <T> TcReadResult<T> unavailable() { return result(ResultStatus.SOURCE_UNAVAILABLE, null, List.of(), Set.of(), RetentionMeta.none()); }
}
