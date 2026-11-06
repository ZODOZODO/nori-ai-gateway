package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.job.InferenceReadiness;

public final class DisabledInferenceReadiness implements InferenceReadiness {
    private final boolean ready;
    public DisabledInferenceReadiness(boolean verify,boolean hooks,boolean classificationHook){ready=verify&&hooks&&classificationHook;}
    @Override public boolean ready(){return ready;}
}
