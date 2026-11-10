package com.nori.ai.gateway.integration.source;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.*;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import java.util.*;

/** 전역 표준 handler의 추가 태그를 막기 위해 Source 전용 registry를 소유합니다. */
public final class SourceObservability {
    private final ObservationRegistry registry;
    private SourceObservability(ObservationRegistry registry) { this.registry = registry; }
    public static SourceObservability noop() { return new SourceObservability(ObservationRegistry.NOOP); }
    public static SourceObservability create(MeterRegistry meters, ObservationHandler<ClientRequestObservationContext> spans) {
        Objects.requireNonNull(meters); Objects.requireNonNull(spans);
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(spans);
        registry.observationConfig().observationHandler(new ObservationHandler<ClientRequestObservationContext>() {
            @Override public boolean supportsContext(Observation.Context context) { return context instanceof ClientRequestObservationContext; }
            @Override public void onStart(ClientRequestObservationContext context) { context.put(Timer.Sample.class, Timer.start(meters)); }
            @Override public void onStop(ClientRequestObservationContext context) {
                List<Tag> tags = new ArrayList<>();
                for (var value : context.getLowCardinalityKeyValues()) {
                    if (!Set.of("source_id", "method", "status_class").contains(value.getKey())) throw new IllegalStateException("Source 관측 속성이 잘못되었습니다");
                    tags.add(Tag.of(value.getKey(), value.getValue()));
                }
                Timer.Sample sample = context.getRequired(Timer.Sample.class);
                sample.stop(meters.timer(context.getName(), tags));
            }
        });
        return new SourceObservability(registry);
    }
    ObservationRegistry registry() { return registry; }
}
