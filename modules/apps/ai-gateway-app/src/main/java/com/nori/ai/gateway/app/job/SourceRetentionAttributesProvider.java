package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.port.SourceRetentionReader;
import com.nori.ai.gateway.core.retention.SourceRetentionAttributes;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import java.time.Duration;

/** 앱별 실제 probe 키를 쓰며 aggregate 최저값으로 다른 앱 창을 줄이지 않습니다. */
public final class SourceRetentionAttributesProvider {
    private final org.springframework.beans.factory.ObjectProvider<SourceRetentionReader> readers;
    public SourceRetentionAttributesProvider(org.springframework.beans.factory.ObjectProvider<SourceRetentionReader> readers){this.readers=readers;}
    public SourceRetentionAttributes loki(String app){var reader=readers.getIfAvailable();return reader==null?new SourceRetentionAttributes.Unverified():reader.find(LokiSourcePolicy.retentionSourceId(app)).<SourceRetentionAttributes>map(value->new SourceRetentionAttributes.VerifiedOldest(value.verifiedOldestAt())).orElseGet(SourceRetentionAttributes.Unverified::new);}
    public SourceRetentionAttributes configured(Duration retention){return new SourceRetentionAttributes.Configured(retention);}
}
