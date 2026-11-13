package com.nori.ai.gateway.acceptance;

import java.util.*;

/**
 * F2 자유 서술 leaf 의 rubric 대상(launcher 한 회차 동안만 ID·해시). AC-22-LIVE 골든셋은 문항마다 대상이 하나라 키는 (leaf, fixture) 다.
 * 원문·자격은 보존하지 않는다.
 */
public final class F2RubricTargets {
    private static final ThreadLocal<List<RubricViewer.Target>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private F2RubricTargets() {}
    public static void clear() { CURRENT.remove(); }
    public static void add(String leaf, String fixture, String job, byte[] narrative) {
        var target = new RubricViewer.Target(leaf, fixture, job, CanonicalJson.sha256(narrative));
        if (F2Fixture.claimsFor(target).isEmpty() || job == null || !job.matches("[A-Za-z0-9_:-]{1,160}")) throw new IllegalArgumentException("RUBRIC_TARGET_INVALID");
        if (CURRENT.get().stream().anyMatch(t -> t.leafId().equals(leaf) && t.fixtureId().equals(fixture))) throw new IllegalStateException("RUBRIC_TARGET_DUPLICATE");
        CURRENT.get().add(target);
    }
    public static List<RubricViewer.Target> snapshot() { return List.copyOf(CURRENT.get()); }
}
