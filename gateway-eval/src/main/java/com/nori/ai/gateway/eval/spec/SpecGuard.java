package com.nori.ai.gateway.eval.spec;

import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.util.Map;

public final class SpecGuard {
    public record CheckedSpec(SpecBinding binding, byte[] bytes) {
        public CheckedSpec { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private final SpecInputResolver resolver;
    public SpecGuard(Map<String,String> env) { resolver = new SpecInputResolver(env); }
    public SpecBinding check(String explicit, String property, Path baseline) {
        return readAndCheck(explicit, property, baseline).binding();
    }
    public CheckedSpec readAndCheck(String explicit, String property, Path baseline) {
        Path path = resolver.resolve(explicit, property);
        String actual; byte[] bytes;
        try { bytes = Json.bytes(path); actual = Json.sha(bytes); }
        catch (EvalException e) { throw new EvalException(2, "SPEC_INPUT_MISSING"); }
        SpecBaseline chain = SpecBaseline.parseChain(baseline);
        String origin = resolver.origin(path, actual);
        if (!chain.currentSha().equals(actual)) throw new EvalException(3, "SPEC_BASELINE_MISMATCH");
        return new CheckedSpec(new SpecBinding(actual, origin), bytes);
    }
}
