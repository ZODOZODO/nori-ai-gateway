package com.nori.ai.gateway.acceptance;

import java.util.*;

/** launcher 한 회차 동안만 MEASUREMENT 레코드 입력(측정 ID·정제 값)을 모은다. 벡터·응답 원문·자격은 받지 않는다. */
public final class AcceptanceMeasurements {
    public record Measurement(String measurementId, Map<String,Object> values) {
        public Measurement { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    private static final ThreadLocal<List<Measurement>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private AcceptanceMeasurements() {}
    public static void clear() { CURRENT.remove(); }
    public static void add(String measurementId, Map<String,Object> values) {
        if (measurementId == null || !measurementId.matches("[a-z0-9]+(-[a-z0-9]+)*:[a-z0-9]+(-[a-z0-9]+)*")) throw new IllegalArgumentException("MEASUREMENT_ID_INVALID");
        if (CURRENT.get().stream().anyMatch(m -> m.measurementId().equals(measurementId))) throw new IllegalStateException("MEASUREMENT_DUPLICATE");
        CURRENT.get().add(new Measurement(measurementId, values));
    }
    public static List<Measurement> snapshot() { return List.copyOf(CURRENT.get()); }
}
