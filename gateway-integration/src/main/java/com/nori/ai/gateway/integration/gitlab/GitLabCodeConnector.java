package com.nori.ai.gateway.integration.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.integration.loki.*;
import java.time.Duration;
import java.util.*;

public final class GitLabCodeConnector {
    public static final int MAX_SNIPPET_CODEPOINTS = 2000;
    public record CodeScope(String sourceRepo, String pathPrefix) {}
    public record Snippet(String sourceRepo, String path, String ref, String text) {
        @Override public String toString() { return "Snippet[보호됨]"; }
    }
    public record CodeResult(ResultStatus status, List<Snippet> snippets) {
        public CodeResult { snippets = List.copyOf(snippets); }
    }
    private final SourceHttpClient http;
    private final SourceReadAccess access;
    private final GitLabSourcePolicy policy;
    private final GitLabReadTokenFile tokenFile;
    public GitLabCodeConnector(SourceHttpClient http, SourceReadAccess access, GitLabSourcePolicy policy, GitLabReadTokenFile tokenFile) {
        this.http = Objects.requireNonNull(http); this.access = Objects.requireNonNull(access); this.policy = Objects.requireNonNull(policy); this.tokenFile = Objects.requireNonNull(tokenFile);
    }
    public CodeResult searchCode(SourceReadAccess.Request request, String term, CodeScope scope, String ref) {
        return searchCode(request, term, scope, ref, Optional.empty());
    }
    public CodeResult searchCode(SourceReadAccess.Request request,String term,CodeScope scope,String ref,Optional<GitLabPipelineLineageReader.Lineage> lineage) {
        try{return searchGuarded(request,term,scope,ref,lineage);}
        catch(SourceReadAccess.GuardStopped stopped){throw stopped.rethrow();}
        catch(SourceReadAccess.Rejected rejected){return result(rejected.status());}
    }
    private CodeResult searchGuarded(SourceReadAccess.Request request,String term,CodeScope scope,String ref,Optional<GitLabPipelineLineageReader.Lineage> lineage) {
        Objects.requireNonNull(scope); Objects.requireNonNull(lineage);
        var repo = policy.repository(scope.sourceRepo()).orElse(null);
        if (repo == null || !GitLabSourcePolicy.pathAllowed(repo, scope.pathPrefix()) || ref == null
                || !(repo.refs().contains(ref) || lineage.filter(value -> value.status() == GitLabPipelineLineageReader.Status.VERIFIED
                    && value.sourceRepo().equals(scope.sourceRepo()) && ref.equals(value.commitSha())).isPresent())) return result(ResultStatus.DENIED);
        if (term == null || term.isBlank()) throw new IllegalArgumentException("검색어가 필요합니다");
        ResultStatus authorized = access.check(request, GitLabSourcePolicy.SOURCE_REF);
        if (authorized != ResultStatus.SUCCESS) return result(authorized);
        long deadline = http.deadline(Duration.ofSeconds(30));
        try {
            String token = tokenFile.read(); int page = 1; List<Snippet> snippets = new ArrayList<>();
            do {
                authorized = access.check(request, GitLabSourcePolicy.SOURCE_REF);
                if (authorized != ResultStatus.SUCCESS) return result(authorized);
                var response = http.json(http.uri(List.of("api", "v4", "projects", Long.toString(repo.projectId()), "search"),
                    Map.of("scope", "blobs", "search", term, "ref", ref, "per_page", 100, "page", page)), token, deadline,()->access.require(request,GitLabSourcePolicy.SOURCE_REF));
                if (!response.body().isArray()) throw new SourceHttpClient.Failure(false, false);
                for (JsonNode hit : response.body()) {
                    String path = hit.path("path").asText();
                    if (!GitLabSourcePolicy.pathAllowed(repo, path) || !(path.equals(scope.pathPrefix()) || path.startsWith(scope.pathPrefix().endsWith("/") ? scope.pathPrefix() : scope.pathPrefix() + "/"))) continue;
                    if (!hit.path("data").isTextual()) throw new SourceHttpClient.Failure(false, false);
                    String text = hit.path("data").asText();
                    int count = text.codePointCount(0, text.length());
                    if (count > MAX_SNIPPET_CODEPOINTS) text = text.substring(0, text.offsetByCodePoints(0, MAX_SNIPPET_CODEPOINTS));
                    snippets.add(new Snippet(repo.sourceRepo(), path, ref, text));
                }
                String next = response.nextPage();
                if (next == null && response.body().size() == 100) throw new SourceHttpClient.Failure(false, false);
                if (next == null || next.isBlank()) break;
                if (!next.equals(Integer.toString(page + 1))) throw new SourceHttpClient.Failure(false, false);
                page++;
            } while (true);
            return new CodeResult(snippets.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, snippets);
        } catch(SourceReadAccess.GuardStopped | SourceReadAccess.Rejected guarded){throw guarded;}
        catch (RuntimeException failure) { return result(ResultStatus.SOURCE_UNAVAILABLE); }
    }
    private static CodeResult result(ResultStatus status) { return new CodeResult(status, List.of()); }
}
