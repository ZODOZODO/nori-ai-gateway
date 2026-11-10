package com.nori.ai.gateway.integration.gitlab;

import java.util.*;

public final class GitLabSourcePolicy {
    public static final String SOURCE_REF = "sources://TC/git";
    public record Repository(long projectId, String sourceRepo, Set<String> refs, Set<String> pathPrefixes, String approvalRef) {
        public Repository {
            if (projectId <= 0 || sourceRepo == null || !sourceRepo.matches("nori-[a-z0-9-]+") || approvalRef == null || approvalRef.isBlank())
                throw new IllegalArgumentException("코드 Source 승인이 잘못되었습니다");
            refs = Set.copyOf(refs); pathPrefixes = Set.copyOf(pathPrefixes);
            if (refs.stream().anyMatch(ref -> ref.isBlank()) || pathPrefixes.stream().anyMatch(prefix -> !validPath(prefix)))
                throw new IllegalArgumentException("코드 읽기 범위가 잘못되었습니다");
        }
    }
    public record LineageEntry(String service, long execProjectId, String packageJobName, String sourceRepo, long siblingProjectId,
            String method, String approvalRef) {
        public LineageEntry {
            if (!com.nori.ai.gateway.integration.loki.LokiSourcePolicy.APPS.contains(service) || execProjectId <= 0 || siblingProjectId <= 0
                    || !"package".equals(packageJobName) || sourceRepo == null || !sourceRepo.matches("nori-[a-z0-9-]+")
                    || !"SIBLING_CLONE_LOG".equals(method) || approvalRef == null || approvalRef.isBlank())
                throw new IllegalArgumentException("배포 계보 승인이 잘못되었습니다");
        }
    }
    private final List<Repository> repositories;
    private final List<LineageEntry> lineage;
    public GitLabSourcePolicy(List<Repository> repositories, List<LineageEntry> lineage) {
        this.repositories = List.copyOf(repositories); this.lineage = List.copyOf(lineage);
        if (repositories.stream().map(Repository::sourceRepo).distinct().count() != repositories.size()) throw new IllegalArgumentException("코드 Source 승인이 중복되었습니다");
    }
    public static GitLabSourcePolicy denyAll() { return new GitLabSourcePolicy(List.of(), List.of()); }
    public Optional<Repository> repository(String repo) { return repositories.stream().filter(entry -> entry.sourceRepo().equals(repo)).findFirst(); }
    public boolean allows(LineageEntry entry) {
        return lineage.contains(entry) && repository(entry.sourceRepo()).map(repo -> repo.projectId() == entry.siblingProjectId()).orElse(false);
    }
    public static boolean validPath(String path) {
        return path != null && !path.isBlank() && !path.startsWith("/") && !path.contains("\\") && !path.contains("%")
            && Arrays.stream(path.split("/", -1)).noneMatch(part -> part.equals(".") || part.equals("..")) && path.chars().noneMatch(c -> c < 32 || c == 127);
    }
    public static boolean pathAllowed(Repository repo, String path) {
        if (!validPath(path)) return false;
        return repo.pathPrefixes().stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/"));
    }
}
