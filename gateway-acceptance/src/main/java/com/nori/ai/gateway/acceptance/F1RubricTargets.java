package com.nori.ai.gateway.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** launcher 한 회차 동안만 ID·해시를 모으며 원문·자격은 보존하지 않습니다. */
public final class F1RubricTargets {
    private static final ThreadLocal<List<RubricViewer.Target>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private F1RubricTargets() {}
    public static void clear() { CURRENT.remove(); }
    public static void add(String leaf, String fixture, String job, byte[] narrative) {
        var target = new RubricViewer.Target(leaf, fixture, job, CanonicalJson.sha256(narrative));
        if (F1Fixture.claimsFor(target).isEmpty() || job == null || !job.matches("[A-Za-z0-9_:-]{1,160}")) throw new IllegalArgumentException("RUBRIC_TARGET_INVALID");
        if (CURRENT.get().stream().anyMatch(t -> t.leafId().equals(leaf))) throw new IllegalStateException("RUBRIC_TARGET_DUPLICATE");
        CURRENT.get().add(target);
    }
    public static List<RubricViewer.Target> snapshot() { return List.copyOf(CURRENT.get()); }
}
