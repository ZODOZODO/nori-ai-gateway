package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.result.*;
import java.util.*;
public record ValidationOutcome(boolean accepted, List<Trace> trace, RejectReason rejectReason, ResultStatus status,
        ErrorCode errorCode, Clarification clarification) {
    public record Trace(int step, boolean passed, RejectReason reason) {}
    public ValidationOutcome { trace = List.copyOf(trace); }
}
