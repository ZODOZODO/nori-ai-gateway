package com.nori.ai.gateway.integration.gitlab;

import com.nori.ai.gateway.integration.ConnectorTestSupport;
import java.nio.file.*;
import java.util.*;

final class GitLabFixture implements AutoCloseable {
    static final String REPO = "nori-tc-gateway";
    static final String SHORT_SHA = "abcdef1";
    static final String FULL_SHA = SHORT_SHA + "2".repeat(33);
    static final String TOKEN = "stub-read-token-sentinel";
    static final GitLabSourcePolicy.Repository REPOSITORY = new GitLabSourcePolicy.Repository(22, REPO, Set.of("main", "release/a+b"), Set.of("src/main"), "stub-owner-approval");
    static final GitLabSourcePolicy.LineageEntry ENTRY = new GitLabSourcePolicy.LineageEntry("tc-gateway", 11, "package", REPO, 22, "SIBLING_CLONE_LOG", "stub-lineage-approval");
    final ConnectorTestSupport stub;
    final GitLabReadTokenFile tokenFile;
    final GitLabSourcePolicy policy = new GitLabSourcePolicy(List.of(REPOSITORY), List.of(ENTRY));
    String jobs = "[{\"id\":33,\"name\":\"package\",\"status\":\"success\",\"pipeline\":{\"sha\":\"" + "9".repeat(40) + "\"}}]";
    String trace = "CI_COMMIT_SHA=" + "9".repeat(40) + "\n" + TOKEN + "\nsibling " + REPO + " @ " + SHORT_SHA + "\n";
    String commit = "{\"id\":\"" + FULL_SHA + "\"}";
    int commitStatus = 200;
    private final Path file;
    GitLabFixture(Path directory) throws Exception {
        stub = new ConnectorTestSupport();
        file = directory.resolve("read-token.txt"); Files.writeString(file, TOKEN);
        tokenFile = new GitLabReadTokenFile(file);
        stub.responder = req -> {
            if (req.path().endsWith("/trace")) return new ConnectorTestSupport.Response(200, trace);
            if (req.path().contains("/repository/commits/")) return new ConnectorTestSupport.Response(commitStatus, commit);
            return new ConnectorTestSupport.Response(200, jobs);
        };
    }
    GitLabPipelineLineageReader reader() { return new GitLabPipelineLineageReader(stub.http("gitlab"), ConnectorTestSupport.access(), policy, tokenFile); }
    @Override public void close() throws Exception { stub.close(); Files.deleteIfExists(file); }
}
