package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.PlanStep;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.core.result.ToolResultEnvelope;
public interface ToolExecutor {
    ToolKind toolKind();
    ToolResultEnvelope execute(PlanStep step, CapabilityManifest manifest, VerifiedSystemScope scope, ExecutionContext context);
}
