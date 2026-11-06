package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.workflow.LogSummarizer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LogSummarizerTest {
    @Test void extractsOnlyObservedIdentifiersInStableOrderWithoutInventingSearchTerms() {
        var lines=List.of("EQP_TIMEOUT java.net.SocketException com.nori.gateway.Worker traceId=0123456789abcdef0123456789abcdef", "EQP_TIMEOUT IO_ERROR trace_id=abcdef0123456789");
        var summarizer=new LogSummarizer();var result=summarizer.extract(lines);
        assertThat(result.errorCodes()).containsExactly("EQP_TIMEOUT","IO_ERROR");assertThat(result.exceptionClasses()).containsExactly("java.net.SocketException");assertThat(result.loggerNames()).contains("com.nori.gateway.Worker");assertThat(result.traceIds()).containsExactly("0123456789abcdef0123456789abcdef","abcdef0123456789");
        assertThat(result.terms()).doesNotContain("0123456789abcdef0123456789abcdef");assertThat(summarizer.extract(lines)).isEqualTo(result);assertThat(summarizer.extract(List.of("정상 관측")).terms()).isEmpty();
    }
}
