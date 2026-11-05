package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.result.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ResultStatusClosedEnumTest {
    @Test void enumsAreExactClosedContracts() {
        assertThat(ResultStatus.values()).extracting(Enum::name).containsExactly("SUCCESS","EMPTY","PARTIAL","OUT_OF_RETENTION","STALE","CONFLICT","DENIED","UNSUPPORTED","SOURCE_UNAVAILABLE","TIMEOUT","FAILED","ACCEPTED_ASYNC");
        assertThat(JobStatus.values()).extracting(Enum::name).containsExactly("QUEUED","RUNNING","SUCCEEDED","PARTIAL","FAILED","CANCELLED","TIMED_OUT");
        assertThat(ErrorCode.values()).extracting(Enum::name).containsExactly("INPUT_BUDGET_EXCEEDED","PLAN_INVALID","LLM_UNAVAILABLE","RESOURCE_NOT_FOUND","TIME_WINDOW_REQUIRED","QUEUE_TIMEOUT","WORKER_INTERRUPTED","CONTRACT_INVALID","SYSTEM_SCOPE_MISMATCH");
        assertThat(AdmissionErrorCode.values()).extracting(Enum::name).containsExactly("QUEUE_FULL","MODEL_LOADING","MODEL_CONTEXT_MISMATCH","INFERENCE_UNAVAILABLE");
        assertThat(Warning.values()).extracting(Enum::name).containsExactly("RETENTION_UNVERIFIED","LLM_UNAVAILABLE");
        assertThat(Verdict.values()).extracting(Enum::name).containsExactly("CONFIRMED","PROBABLE","INSUFFICIENT_EVIDENCE","CONFLICTING_EVIDENCE");
        assertThatThrownBy(() -> SchemaContractsForTest.status("SKIPPED_NO_INPUT")).isInstanceOf(IllegalArgumentException.class);
    }
    private static class SchemaContractsForTest { static ResultStatus status(String name) { return ResultStatus.valueOf(name); } }
}
