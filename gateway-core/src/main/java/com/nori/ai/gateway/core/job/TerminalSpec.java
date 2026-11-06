package com.nori.ai.gateway.core.job;

import com.nori.ai.gateway.core.result.*;
import java.util.*;

public record TerminalSpec(JobStatus jobStatus, ResultStatus resultStatus, ErrorCode errorCode,
        String eventType, boolean externalSuppressed, Map<String,Object> resultPayload,
        Map<String,Object> resultSourceDeps, Cause terminalCause, Optional<CommitData> commitData) {
    public TerminalSpec(JobStatus jobStatus,ResultStatus resultStatus,ErrorCode errorCode,String eventType,boolean externalSuppressed,
            Map<String,Object> resultPayload,Map<String,Object> resultSourceDeps,Cause terminalCause) {
        this(jobStatus,resultStatus,errorCode,eventType,externalSuppressed,resultPayload,resultSourceDeps,terminalCause,Optional.empty());
    }
    public record StoredEvidence(Evidence value,Optional<EvidenceSourceRef> source) {
        public StoredEvidence { Objects.requireNonNull(value);Objects.requireNonNull(source); }
        @Override public String toString(){return "StoredEvidence[보호됨]";}
    }
    public record AssistantMessage(String messageId,String contentMasked,List<com.nori.ai.gateway.core.plan.ResourceRef> resourceRefs,List<String> toolExecutionRefs,String responseRef) {
        public AssistantMessage { Objects.requireNonNull(messageId);Objects.requireNonNull(contentMasked);Objects.requireNonNull(responseRef);resourceRefs=List.copyOf(resourceRefs);toolExecutionRefs=List.copyOf(toolExecutionRefs); }
        @Override public String toString(){return "AssistantMessage[보호됨]";}
    }
    public record CommitData(com.nori.ai.gateway.core.scope.VerifiedSystemScope scope,String jobId,String conversationId,String requestMessageId,long jobSeq,
            List<StoredEvidence> evidence,Optional<AssistantMessage> assistantMessage,Optional<Map<String,Object>> context,
            Optional<Map<String,Object>> summary,ResultSourceDeps sourceDeps) {
        public CommitData {
            Objects.requireNonNull(scope);Objects.requireNonNull(jobId);Objects.requireNonNull(conversationId);Objects.requireNonNull(requestMessageId);Objects.requireNonNull(sourceDeps);
            if(jobSeq<=0)throw new IllegalArgumentException("요청 순번이 잘못되었습니다");
            evidence=List.copyOf(evidence);Objects.requireNonNull(assistantMessage);
            context=Objects.requireNonNull(context).map(JobRequestInput::immutableFields);summary=Objects.requireNonNull(summary).map(JobRequestInput::immutableFields);
        }
        @Override public String toString(){return "CommitData[보호됨]";}
    }
    public TerminalSpec withCommitData(CommitData data) {
        return new TerminalSpec(jobStatus,resultStatus,errorCode,eventType,externalSuppressed,resultPayload,resultSourceDeps,terminalCause,Optional.of(data));
    }
    public enum Cause { NORMAL, EXPIRY_EDGE, QUEUE_TIMEOUT, JOB_TIMEOUT, WORKER_INTERRUPTED,
        RESTORE_EXPIRED, RESTORE_LIMIT, RESTORE_INTERRUPTED, START_GATE_DENIED, START_GATE_UNAVAILABLE, CANCELLED }
    public TerminalSpec {
        Objects.requireNonNull(commitData);
        if(commitData.isPresent()&&(terminalCause!=Cause.NORMAL||!commitData.get().sourceDeps().equals(dependencies(resultSourceDeps))))throw new IllegalArgumentException("최종 자료 결속이 다릅니다");
        Objects.requireNonNull(jobStatus); Objects.requireNonNull(terminalCause);
        if(terminalCause==Cause.NORMAL&&resultStatus==null)throw new IllegalArgumentException("종결 결과가 필요합니다");
        resultSourceDeps = Map.copyOf(Objects.requireNonNull(resultSourceDeps));
        if (resultPayload != null) resultPayload = Map.copyOf(resultPayload);
        String expectedEvent = switch (jobStatus) {
            case SUCCEEDED -> "job.completed"; case PARTIAL -> "job.partial";
            case FAILED, TIMED_OUT -> "job.failed"; case CANCELLED -> "job.cancelled";
            default -> throw new IllegalArgumentException("종결 상태가 필요합니다");
        };
        boolean valid = expectedEvent.equals(eventType) && (resultStatus == null) == (resultPayload == null);
        valid &= switch (terminalCause) {
            case EXPIRY_EDGE, RESTORE_EXPIRED -> jobStatus == JobStatus.CANCELLED && resultStatus == null && errorCode == null && externalSuppressed;
            case CANCELLED -> jobStatus == JobStatus.CANCELLED && resultStatus == null && errorCode == null && !externalSuppressed;
            case QUEUE_TIMEOUT -> jobStatus == JobStatus.FAILED && resultStatus == ResultStatus.FAILED && errorCode == ErrorCode.QUEUE_TIMEOUT;
            case JOB_TIMEOUT, RESTORE_LIMIT -> jobStatus == JobStatus.TIMED_OUT && resultStatus == ResultStatus.TIMEOUT && errorCode == null;
            case WORKER_INTERRUPTED, RESTORE_INTERRUPTED -> jobStatus == JobStatus.FAILED && resultStatus == ResultStatus.FAILED && errorCode == ErrorCode.WORKER_INTERRUPTED;
            case START_GATE_DENIED -> jobStatus == JobStatus.FAILED && resultStatus == ResultStatus.DENIED && errorCode == null;
            case START_GATE_UNAVAILABLE -> jobStatus == JobStatus.FAILED && resultStatus == ResultStatus.SOURCE_UNAVAILABLE && errorCode == null;
            case NORMAL -> switch (jobStatus) {
                case SUCCEEDED -> Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.STALE,ResultStatus.CONFLICT,ResultStatus.OUT_OF_RETENTION,ResultStatus.UNSUPPORTED).contains(resultStatus) && errorCode == null;
                case PARTIAL -> resultStatus == ResultStatus.PARTIAL && (errorCode == null || errorCode == ErrorCode.LLM_UNAVAILABLE);
                case FAILED -> (resultStatus == ResultStatus.FAILED && errorCode != null && Set.of(ErrorCode.LLM_UNAVAILABLE,ErrorCode.PLAN_INVALID,ErrorCode.INPUT_BUDGET_EXCEEDED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.TIME_WINDOW_REQUIRED,ErrorCode.CONTRACT_INVALID).contains(errorCode))
                    || (Set.of(ResultStatus.DENIED,ResultStatus.SOURCE_UNAVAILABLE).contains(resultStatus) && errorCode == null);
                default -> false;
            };
        };
        if (terminalCause != Cause.EXPIRY_EDGE && terminalCause != Cause.RESTORE_EXPIRED) valid &= !externalSuppressed;
        if (resultStatus == null) valid &= resultSourceDeps.isEmpty();
        if (!valid) throw new IllegalArgumentException("승인된 종결 상태 조합이 아닙니다");
    }
    private static ResultSourceDeps dependencies(Map<String,Object> values) {
        if(values.isEmpty())return new ResultSourceDeps(List.of(),List.of());
        return com.nori.ai.gateway.core.registry.SchemaContracts.JSON.convertValue(values,ResultSourceDeps.class);
    }
    public static TerminalSpec outcome(JobStatus job, ResultStatus result, ErrorCode error, Map<String,Object> payload) {
        String event = job == JobStatus.SUCCEEDED ? "job.completed" : job == JobStatus.PARTIAL ? "job.partial" : "job.failed";
        return new TerminalSpec(job,result,error,event,false,payload,Map.of(),Cause.NORMAL);
    }
    public static TerminalSpec forCause(Cause cause) {
        return switch (cause) {
            case EXPIRY_EDGE, RESTORE_EXPIRED -> new TerminalSpec(JobStatus.CANCELLED,null,null,"job.cancelled",true,null,Map.of(),cause);
            case CANCELLED -> new TerminalSpec(JobStatus.CANCELLED,null,null,"job.cancelled",false,null,Map.of(),cause);
            case QUEUE_TIMEOUT -> failure(ResultStatus.FAILED,ErrorCode.QUEUE_TIMEOUT,cause);
            case WORKER_INTERRUPTED, RESTORE_INTERRUPTED -> failure(ResultStatus.FAILED,ErrorCode.WORKER_INTERRUPTED,cause);
            case JOB_TIMEOUT, RESTORE_LIMIT -> new TerminalSpec(JobStatus.TIMED_OUT,ResultStatus.TIMEOUT,null,"job.failed",false,Map.of(),Map.of(),cause);
            case START_GATE_DENIED -> failure(ResultStatus.DENIED,null,cause);
            case START_GATE_UNAVAILABLE -> failure(ResultStatus.SOURCE_UNAVAILABLE,null,cause);
            default -> throw new IllegalArgumentException("명시 종결 원인이 필요합니다");
        };
    }
    private static TerminalSpec failure(ResultStatus status, ErrorCode error, Cause cause) {
        return new TerminalSpec(JobStatus.FAILED,status,error,"job.failed",false,Map.of(),Map.of(),cause);
    }
}
