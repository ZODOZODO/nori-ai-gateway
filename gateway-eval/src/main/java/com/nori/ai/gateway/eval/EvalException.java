package com.nori.ai.gateway.eval;

public final class EvalException extends RuntimeException {
    private final int exitCode;
    public EvalException(int exitCode, String code) { super(code); this.exitCode = exitCode; }
    public int exitCode() { return exitCode; }
}
