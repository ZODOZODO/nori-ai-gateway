package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.tcmock.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class RetentionMockIT extends P09QueueSupport {
    @Test void actualWiredMockMaintainsUnverifiedHitEmptyTrimAndDlqFailureDistinctions(){
        var api=context.getBean(MockTcBusinessReadAdapter.class);var scenario=context.getBean(MockScenarioStore.class);var call=new DelegatedCall("actor",registeredDlg("actor"));var window=new TimeWindow(NOW.minusSeconds(3000),NOW.plusSeconds(1));
        assertThat(context.getBean(RetentionClassifier.class).classify(new TimeWindow(NOW.minusSeconds(7200),NOW.minusSeconds(3600)),new SourceRetentionAttributes.Configured(Duration.ofHours(1)),NOW)).isInstanceOf(RetentionClassifier.OutOfRetention.class);
        assertThat(api.getCommLogs(call,"EQP-001","gateway",window).warnings()).contains(Warning.RETENTION_UNVERIFIED);
        scenario.select("empty");var empty=api.getCommLogs(call,"EQP-001","gateway",window);assertThat(empty.status()).isEqualTo(ResultStatus.EMPTY);assertThat(empty.warnings()).contains(Warning.RETENTION_UNVERIFIED);
        scenario.select("comm-trim");var trim=api.getCommLogs(call,"EQP-001","gateway",window);assertThat(trim.retentionMeta().unverifiedRanges()).isNotEmpty();assertThat(trim.retentionMeta().missingRanges()).isEmpty();
        scenario.select("dlq-partial");assertThat(api.listDlq(call,"gateway",window).missingFields()).containsExactly("decodeFailures=1");
        scenario.select("dlq-down");assertThat(api.listDlq(call,"gateway",window).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);assertThat(api.listDlq(call,"business",window).status()).isEqualTo(ResultStatus.SUCCESS);
    }
}
