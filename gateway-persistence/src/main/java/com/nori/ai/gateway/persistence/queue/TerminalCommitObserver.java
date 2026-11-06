package com.nori.ai.gateway.persistence.queue;

public interface TerminalCommitObserver {
    void beforeCommit(String jobId);
    void afterCommit(String jobId);
}
