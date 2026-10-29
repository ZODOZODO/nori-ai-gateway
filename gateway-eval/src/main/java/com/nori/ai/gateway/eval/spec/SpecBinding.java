package com.nori.ai.gateway.eval.spec;

public final class SpecBinding {
    private final String specSha;
    private final String specInputOrigin;
    SpecBinding(String specSha, String specInputOrigin) { this.specSha = specSha; this.specInputOrigin = specInputOrigin; }
    public String specSha() { return specSha; }
    public String specShaSource() { return "FILE_HASHED"; }
    public String specInputOrigin() { return specInputOrigin; }
}
