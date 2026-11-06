package com.nori.ai.gateway.core.port;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.result.*;
import java.time.Instant;
import java.util.*;

public interface TcBusinessReadPort {
    TcReadResult<EqpSnapshot> getEqpSnapshot(DelegatedCall call, String eqpId);
    TcReadResult<ActiveWorkLots> getActiveWorkLots(DelegatedCall call, String eqpId);
    TcReadResult<List<RecentEvent>> getRecentEvents(DelegatedCall call, String eqpId, TimeWindow window);
    TcReadResult<DlqData> listDlq(DelegatedCall call, String sourceId, TimeWindow window);
    TcReadResult<CommLogData> getCommLogs(DelegatedCall call, String eqpId, String sourceId, TimeWindow window);
    TcReadResult<List<ModelNode>> readModel(DelegatedCall call, Optional<String> modelId);
    TcReadResult<List<Resource>> resolveResource(DelegatedCall call, String type, String ref);

    record TcReadResult<T>(ResultStatus status, Instant observedAt, T data, List<String> missingFields,
            Set<Warning> warnings, RetentionMeta retentionMeta) {
        public TcReadResult {
            Objects.requireNonNull(status); Objects.requireNonNull(observedAt); Objects.requireNonNull(retentionMeta);
            missingFields = List.copyOf(missingFields); warnings = Set.copyOf(warnings);
            if (status == ResultStatus.PARTIAL && missingFields.isEmpty() && retentionMeta.missingRanges().isEmpty())
                throw new IllegalArgumentException("부분 조회의 누락 정보가 필요합니다");
            if ((status == ResultStatus.SUCCESS || status == ResultStatus.PARTIAL) && data == null)
                throw new IllegalArgumentException("조회 자료가 필요합니다");
        }
    }
    record RetentionMeta(List<TimeWindow> missingRanges, List<TimeWindow> unverifiedRanges) {
        public RetentionMeta { missingRanges = List.copyOf(missingRanges); unverifiedRanges = List.copyOf(unverifiedRanges); }
        public static RetentionMeta none() { return new RetentionMeta(List.of(), List.of()); }
    }
    record EqpSnapshot(String eqpId, String connectionState, String controlState, String processState, String equipmentState) {}
    enum WorkLotState { ACTIVE, MULTI, ENDED, UNKNOWN }
    record WorkLot(String workId, String pjId, String lotId) {}
    record ActiveWorkLots(WorkLotState state, List<WorkLot> activeLots, Optional<String> currentLot) {
        public ActiveWorkLots {
            activeLots = List.copyOf(activeLots); Objects.requireNonNull(state); Objects.requireNonNull(currentLot);
            if ((state == WorkLotState.ACTIVE && (activeLots.size() != 1 || currentLot.isEmpty()
                    || !currentLot.get().equals(activeLots.getFirst().lotId())))
                    || (state != WorkLotState.ACTIVE && currentLot.isPresent())
                    || (state == WorkLotState.MULTI && activeLots.size() < 2)
                    || ((state == WorkLotState.ENDED || state == WorkLotState.UNKNOWN) && !activeLots.isEmpty()))
                throw new IllegalArgumentException("활성 LOT 조회 계약이 잘못되었습니다");
        }
    }
    record RecentEvent(String eventId, Instant occurredAt, String eventType) {}
    record DlqEntry(String entryRef, Instant occurredAt, String messageType) {}
    record DlqData(List<DlqEntry> items, DlqCompletenessEvaluator.Observation completeness) {
        public DlqData { items = List.copyOf(items); Objects.requireNonNull(completeness); }
    }
    record CommLogEntry(Instant occurredAt, String direction, String messageType) {}
    record CommLogData(List<CommLogEntry> items, TrimCompletenessEvaluator.KeyObservation completeness) {
        public CommLogData { items = List.copyOf(items); Objects.requireNonNull(completeness); }
    }
    record ModelNode(String modelId, String nodeId, String nodeType, List<ModelNode> children) {
        public ModelNode { children = List.copyOf(children); }
    }
    record Resource(String type, String ref) {}
}
