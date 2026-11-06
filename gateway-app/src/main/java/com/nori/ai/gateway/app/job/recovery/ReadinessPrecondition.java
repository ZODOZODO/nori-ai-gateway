package com.nori.ai.gateway.app.job.recovery;

public interface ReadinessPrecondition {
    int order();
    void run();
}
