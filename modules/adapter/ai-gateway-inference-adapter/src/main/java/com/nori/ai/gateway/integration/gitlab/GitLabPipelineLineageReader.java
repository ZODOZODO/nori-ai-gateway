package com.nori.ai.gateway.integration.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.integration.loki.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public final class GitLabPipelineLineageReader {
    public enum Status { VERIFIED, UNKNOWN }
    public enum Reason { VERIFIED, NOT_APPROVED, PACKAGE_JOB_AMBIGUOUS, SIBLING_COMMIT_AMBIGUOUS, SHORT_SHA_UNRESOLVED, SOURCE_UNAVAILABLE, DENIED }
    public static final class Lineage {
        private final String service, sourceRepo, commitSha;
        private final long execPipelineId;
        private final Status status;
        private final Reason reason;
        private Lineage(String service, long pipelineId, String sourceRepo, String commitSha, Status status, Reason reason) {
            this.service = service; this.execPipelineId = pipelineId; this.sourceRepo = sourceRepo; this.commitSha = commitSha; this.status = status; this.reason = reason;
        }
        public String service() { return service; }
        public long execPipelineId() { return execPipelineId; }
        public String sourceRepo() { return sourceRepo; }
        public String commitSha() { return commitSha; }
        public Status status() { return status; }
        public Reason reason() { return reason; }
        @Override public String toString() { return "Lineage[status=" + status + ", reason=" + reason + "]"; }
    }
    private static final Pattern SIBLING = Pattern.compile("^sibling (nori-[a-z0-9-]+) @ ([0-9a-f]{7,40})$");
    private static final int MAX_TRACE_LINE = 256;
    private final SourceHttpClient http;
    private final SourceReadAccess access;
    private final GitLabSourcePolicy policy;
    private final GitLabReadTokenFile tokenFile;
    public GitLabPipelineLineageReader(SourceHttpClient http, SourceReadAccess access, GitLabSourcePolicy policy, GitLabReadTokenFile tokenFile) {
        this.http = Objects.requireNonNull(http); this.access = Objects.requireNonNull(access); this.policy = Objects.requireNonNull(policy); this.tokenFile = Objects.requireNonNull(tokenFile);
    }
    public Lineage siblingCommits(SourceReadAccess.Request request,GitLabSourcePolicy.LineageEntry entry,long pipelineId) {
        try{return siblingCommitsGuarded(request,entry,pipelineId);}
        catch(SourceReadAccess.GuardStopped stopped){throw stopped.rethrow();}
        catch(SourceReadAccess.Rejected rejected){return unknown(entry,pipelineId,rejected.status()==ResultStatus.DENIED?Reason.DENIED:Reason.SOURCE_UNAVAILABLE);}
    }
    private Lineage siblingCommitsGuarded(SourceReadAccess.Request request, GitLabSourcePolicy.LineageEntry entry, long pipelineId) {
        Objects.requireNonNull(entry);
        if (!policy.allows(entry) || pipelineId <= 0 || "nori-common".equals(entry.sourceRepo())) return unknown(entry, pipelineId, Reason.NOT_APPROVED);
        ResultStatus authorized = access.check(request, GitLabSourcePolicy.SOURCE_REF);
        if (authorized != ResultStatus.SUCCESS) return unknown(entry, pipelineId, authorized == ResultStatus.DENIED ? Reason.DENIED : Reason.SOURCE_UNAVAILABLE);
        long deadline = http.deadline(Duration.ofSeconds(30));
        try {
            String token = tokenFile.read(); Long packageJob = null; int page = 1;
            do {
                requireAccess(request);
                var response = http.json(http.uri(List.of("api", "v4", "projects", Long.toString(entry.execProjectId()), "pipelines", Long.toString(pipelineId), "jobs"),
                    Map.of("scope[]", "success", "include_retried", true, "per_page", 100, "page", page)), token, deadline,()->access.require(request,GitLabSourcePolicy.SOURCE_REF));
                if (!response.body().isArray()) return unknown(entry, pipelineId, Reason.PACKAGE_JOB_AMBIGUOUS);
                for (JsonNode job : response.body()) if (job.path("name").isTextual() && job.path("status").isTextual()
                        && entry.packageJobName().equals(job.path("name").asText()) && "success".equals(job.path("status").asText())) {
                    // 변환 가능 범위만 검사하면 소수 ID가 다른 정수 Job으로 축소될 수 있습니다.
                    if (packageJob != null || !job.path("id").isIntegralNumber() || !job.path("id").canConvertToLong()
                            || job.path("id").asLong() <= 0) return unknown(entry, pipelineId, Reason.PACKAGE_JOB_AMBIGUOUS);
                    packageJob = job.path("id").asLong();
                }
                String next = response.nextPage();
                // 페이지 헤더 없이 꽉 찬 응답은 유일성을 증명할 수 없습니다.
                if (next == null && response.body().size() == 100) return unknown(entry, pipelineId, Reason.PACKAGE_JOB_AMBIGUOUS);
                if (next == null || next.isBlank()) break;
                if (!next.equals(Integer.toString(page + 1))) return unknown(entry, pipelineId, Reason.PACKAGE_JOB_AMBIGUOUS);
                page++;
            } while (true);
            if (packageJob == null) return unknown(entry, pipelineId, Reason.PACKAGE_JOB_AMBIGUOUS);
            requireAccess(request);
            List<String> matches = http.stream(http.uri(List.of("api", "v4", "projects", Long.toString(entry.execProjectId()), "jobs", Long.toString(packageJob), "trace"), Map.of()),
                token, deadline, stream -> siblingLines(stream, entry.sourceRepo()),()->access.require(request,GitLabSourcePolicy.SOURCE_REF));
            if (matches.size() != 1) return unknown(entry, pipelineId, Reason.SIBLING_COMMIT_AMBIGUOUS);
            String shortSha = matches.getFirst();
            requireAccess(request);
            try {
                JsonNode commit = http.json(http.uri(List.of("api", "v4", "projects", Long.toString(entry.siblingProjectId()), "repository", "commits", shortSha), Map.of()), token, deadline,()->access.require(request,GitLabSourcePolicy.SOURCE_REF)).body();
                if (!commit.isObject() || !commit.path("id").isTextual()) return unknown(entry, pipelineId, Reason.SHORT_SHA_UNRESOLVED);
                String sha = commit.path("id").asText();
                if (!sha.matches("[0-9a-f]{40}") || !sha.startsWith(shortSha)) return unknown(entry, pipelineId, Reason.SHORT_SHA_UNRESOLVED);
                return new Lineage(entry.service(), pipelineId, entry.sourceRepo(), sha, Status.VERIFIED, Reason.VERIFIED);
            } catch (SourceHttpClient.Failure failure) { return unknown(entry, pipelineId, failure.httpNotFound()?Reason.SHORT_SHA_UNRESOLVED:Reason.SOURCE_UNAVAILABLE); }
        } catch (AccessDenied failure) { return unknown(entry, pipelineId, Reason.DENIED); }
        catch(SourceReadAccess.GuardStopped | SourceReadAccess.Rejected guarded){throw guarded;}
        catch (RuntimeException failure) { return unknown(entry, pipelineId, Reason.SOURCE_UNAVAILABLE); }
    }
    private static List<String> siblingLines(InputStream stream, String sourceRepo) throws IOException {
        List<String> found = new ArrayList<>();
        // readLine은 무제한 행을 할당하므로 제한된 버퍼만 사용하고 초과 행은 즉시 폐기합니다.
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder line = new StringBuilder(); boolean overflow = false; int current;
        while ((current = reader.read()) != -1) {
            if (current == '\n') {
                if (!overflow) match(line, sourceRepo, found);
                if (found.size() > 1) return found;
                line.setLength(0); overflow = false;
            } else if (!overflow) {
                if (line.length() == MAX_TRACE_LINE) { overflow = true; line.setLength(0); }
                else line.append((char) current);
            }
        }
        if (!overflow) match(line, sourceRepo, found);
        return found;
    }
    private static void match(StringBuilder line, String sourceRepo, List<String> found) {
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
        var matcher = SIBLING.matcher(line);
        if (matcher.matches() && matcher.group(1).equals(sourceRepo)) found.add(matcher.group(2));
    }
    private static Lineage unknown(GitLabSourcePolicy.LineageEntry entry, long pipelineId, Reason reason) {
        return new Lineage(entry.service(), pipelineId, entry.sourceRepo(), null, Status.UNKNOWN, reason);
    }
    private static final class AccessDenied extends RuntimeException {}
    private void requireAccess(SourceReadAccess.Request request) {
        ResultStatus result = access.check(request, GitLabSourcePolicy.SOURCE_REF);
        if (result == ResultStatus.DENIED) throw new AccessDenied();
        if (result != ResultStatus.SUCCESS) throw new SourceHttpClient.Failure(false, false);
    }
}
