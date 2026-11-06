package com.nori.ai.gateway.core.port;

import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.Optional;

public interface JobRequestInputReader {
    Optional<JobRequestInput> findRequestInput(VerifiedSystemScope scope, String jobId);
}
