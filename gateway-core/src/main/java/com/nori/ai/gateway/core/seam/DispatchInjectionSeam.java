package com.nori.ai.gateway.core.seam;

import com.nori.ai.gateway.core.dispatch.DispatchTarget;
@FunctionalInterface
public interface DispatchInjectionSeam {
    DispatchTarget inspect(DispatchTarget target);
    final class NoOp implements DispatchInjectionSeam {
        @Override public DispatchTarget inspect(DispatchTarget target) { return target; }
    }
}
