package com.nori.ai.gateway.app.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 전역 관측의 속성을 허용 목록으로 줄인다. low-cardinality는 지표 목록의 라벨 키만 남기고,
 * high-cardinality(URL·식별자 등)는 모두 제거한다. 값의 닫힌 집합 검사는 {@link MetricsCatalog#filter(com.nori.ai.gateway.core.registry.CapabilityRegistry)}가 한다.
 */
public final class AllowlistObservationFilter implements ObservationFilter {
    private static final Set<String> KEYS = MetricsCatalog.specs().values().stream().flatMap(spec -> spec.labels().keySet().stream()).collect(Collectors.toUnmodifiableSet());
    @Override public Observation.Context map(Observation.Context context) {
        for (String key : context.getLowCardinalityKeyValues().stream().map(KeyValue::getKey).toList())
            if (!KEYS.contains(key)) context.removeLowCardinalityKeyValue(key);
        for (String key : context.getHighCardinalityKeyValues().stream().map(KeyValue::getKey).toList())
            context.removeHighCardinalityKeyValue(key);
        return context;
    }
}
