package com.nori.ai.gateway.core.job;

public interface WorkflowExecutor {
    TerminalSpec execute(JobContext context) throws Exception;
}
