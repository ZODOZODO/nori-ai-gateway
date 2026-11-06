package com.nori.ai.gateway.integration.loki;

import io.micrometer.common.KeyValues;
import org.springframework.http.client.observation.*;
import java.io.IOException;
import java.util.Set;

public final class AllowlistClientRequestObservationConvention implements ClientRequestObservationConvention {
    private final String sourceId;
    public AllowlistClientRequestObservationConvention(String sourceId) {
        if (!Set.of("loki", "tempo", "gitlab").contains(sourceId)) throw new IllegalArgumentException("Source 식별자가 잘못되었습니다");
        this.sourceId = sourceId;
    }
    @Override public String getName() { return "aigw.source.requests"; }
    @Override public String getContextualName(ClientRequestObservationContext context) { return sourceId + " GET"; }
    @Override public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
        String status = "IO_ERROR";
        try { if (context.getResponse() != null) status = context.getResponse().getStatusCode().value() / 100 + "xx"; }
        catch (IOException failure) { status = "IO_ERROR"; }
        return KeyValues.of("source_id", sourceId, "method", "GET", "status_class", status);
    }
    @Override public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) { return KeyValues.empty(); }
}
