package com.nori.ai.gateway.core.port;

import java.util.*;

public interface TcSourceAuthorizationPort {
    Outcome authorize(DelegatedCall call, Set<String> requiredPermCodes);
    record DelegatedCall(String sub, String dlg) {
        public DelegatedCall {
            if (sub == null || sub.isBlank() || dlg == null || !dlg.matches("[A-Za-z0-9_-]{22,128}"))
                throw new IllegalArgumentException("실행 위임 형식이 잘못되었습니다");
        }
        @Override public String toString() { return "DelegatedCall[보호됨]"; }
    }
    sealed interface Outcome permits Allowed, DelegationInvalid, Unavailable {}
    record Allowed(Set<String> allowed, String pdg) implements Outcome {
        public Allowed { allowed = Set.copyOf(allowed); }
    }
    record DelegationInvalid() implements Outcome {}
    record Unavailable() implements Outcome {}
}
