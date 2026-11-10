package com.nori.ai.gateway.core.job;

import com.nori.ai.gateway.core.result.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalSpecContractTest {
    @Test void allFifteenRowsAndComposerCombinationsAreClosed(){
        for(var cause:TerminalSpec.Cause.values())if(cause!=TerminalSpec.Cause.NORMAL)assertThat(TerminalSpec.forCause(cause)).isNotNull();
        for(var result:List.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.STALE,ResultStatus.CONFLICT,ResultStatus.OUT_OF_RETENTION,ResultStatus.UNSUPPORTED))assertThat(TerminalSpec.outcome(JobStatus.SUCCEEDED,result,null,Map.of())).isNotNull();
        assertThat(TerminalSpec.outcome(JobStatus.PARTIAL,ResultStatus.PARTIAL,null,Map.of())).isNotNull();
        assertThat(TerminalSpec.outcome(JobStatus.PARTIAL,ResultStatus.PARTIAL,ErrorCode.LLM_UNAVAILABLE,Map.of())).isNotNull();
        for(var error:List.of(ErrorCode.LLM_UNAVAILABLE,ErrorCode.PLAN_INVALID,ErrorCode.INPUT_BUDGET_EXCEEDED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.TIME_WINDOW_REQUIRED,ErrorCode.CONTRACT_INVALID))assertThat(TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.FAILED,error,Map.of())).isNotNull();
        for(var result:List.of(ResultStatus.DENIED,ResultStatus.SOURCE_UNAVAILABLE))assertThat(TerminalSpec.outcome(JobStatus.FAILED,result,null,Map.of())).isNotNull();
        assertThatThrownBy(()->TerminalSpec.outcome(JobStatus.SUCCEEDED,ResultStatus.DENIED,null,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.SUCCESS,null,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.FAILED,null,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new TerminalSpec(JobStatus.SUCCEEDED,ResultStatus.SUCCESS,null,"job.failed",false,Map.of(),Map.of(),TerminalSpec.Cause.NORMAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new TerminalSpec(JobStatus.CANCELLED,null,null,"job.cancelled",false,null,Map.of(),TerminalSpec.Cause.EXPIRY_EDGE)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiryUsesInclusiveBoundaryAndSemaphoreIsActuallyExclusive()throws Exception{
        var expiry=new com.nori.ai.gateway.core.expiry.ConversationExpiryGuard();var end=java.time.Instant.parse("2026-01-01T00:00:00Z");
        expiry.assertLive(end,end.minusNanos(1));assertThatThrownBy(()->expiry.assertLive(end,end)).isInstanceOf(com.nori.ai.gateway.core.expiry.ConversationExpiryGuard.ConversationExpired.class);
        var slots=new GenerationSemaphore(1);slots.acquire();assertThat(slots.availablePermits()).isZero();slots.release();assertThat(slots.availablePermits()).isOne();
    }
}
