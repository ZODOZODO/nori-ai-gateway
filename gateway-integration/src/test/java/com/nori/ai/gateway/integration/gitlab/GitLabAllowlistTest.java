package com.nori.ai.gateway.integration.gitlab;

import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import com.nori.ai.gateway.integration.loki.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;
import static com.nori.ai.gateway.integration.gitlab.GitLabFixture.*;

class GitLabAllowlistTest {
    @TempDir Path directory;
    @Test void emptyApprovedListsRejectCodeAndLineageWithoutReadingTokenOrNetwork() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var nonexistent = new GitLabReadTokenFile(directory.resolve("absent"));
            var connector = new GitLabCodeConnector(stub.http("gitlab"), access(), GitLabSourcePolicy.denyAll(), nonexistent);
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main").status()).isEqualTo(ResultStatus.DENIED);
            var lineage = new GitLabPipelineLineageReader(stub.http("gitlab"), access(), GitLabSourcePolicy.denyAll(), nonexistent).siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.UNKNOWN);
            assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.NOT_APPROVED);
            assertThat(stub.requests).isEmpty();
        }
    }
    @Test void onlyApprovedRepoRefAndPathCanExecuteAndReturnedPathsAreRechecked() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            fixture.stub.responder = req -> new Response(200, "[{\"path\":\"src/main/A.java\",\"data\":\"허용\"},{\"path\":\"src/main-private/B.java\",\"data\":\"비공개\"},"
                + "{\"path\":\"src/main/../private/C.java\",\"data\":\"비공개\"},{\"path\":\"private/D.java\",\"data\":\"비공개\"}]");
            var connector = new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, fixture.tokenFile);
            var result = connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "한글 +&?/%", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "release/a+b");
            assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(result.snippets()).singleElement().satisfies(snippet -> assertThat(snippet.path()).isEqualTo("src/main/A.java"));
            assertThat(fixture.stub.requests).singleElement().satisfies(req -> {
                assertThat(req.method()).isEqualTo("GET"); assertThat(req.path()).isEqualTo("/api/v4/projects/22/search");
                assertThat(req.query()).containsEntry("scope", "blobs").containsEntry("search", "한글 +&?/%").containsEntry("ref", "release/a+b");
                assertThat(req.token()).isEqualTo(TOKEN);
            });
            for (var scope : List.of(new GitLabCodeConnector.CodeScope("nori-wm", "src/main"), new GitLabCodeConnector.CodeScope(REPO, "private"),
                    new GitLabCodeConnector.CodeScope(REPO, "src/main/../private"), new GitLabCodeConnector.CodeScope(REPO, "src/main%2fprivate")))
                assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", scope, "main").status()).isEqualTo(ResultStatus.DENIED);
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "other").status()).isEqualTo(ResultStatus.DENIED);
            assertThat(fixture.stub.requests).hasSize(1);
        }
    }
    @Test void onlyVerifiedSiblingCommitCanExtendApprovedRefsAndExecHeadCannotBeUsed() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            var connector = new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, fixture.tokenFile);
            var scope = new GitLabCodeConnector.CodeScope(REPO, "src/main");
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", scope, FULL_SHA).status()).isEqualTo(ResultStatus.DENIED);
            var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            fixture.stub.responder = req -> new Response(200, "[]");
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", scope, FULL_SHA, Optional.of(lineage)).status()).isEqualTo(ResultStatus.EMPTY);
            assertThat(fixture.stub.requests.getLast().query()).containsEntry("ref", FULL_SHA);
            int reads = fixture.stub.requests.size();
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", scope, "9".repeat(40), Optional.of(lineage)).status()).isEqualTo(ResultStatus.DENIED);
            assertThat(fixture.stub.requests).hasSize(reads);
        }
    }
    @Test void snippetCapUsesCodepointsAndMissingTokenNeverFallsBackToEnvironment() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            String text = "🙂".repeat(2100);
            fixture.stub.responder = req -> new Response(200, "[{\"path\":\"src/main/A\",\"data\":\"" + text + "\"}]");
            var connector = new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, fixture.tokenFile);
            var result = connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main");
            assertThat(result.snippets().getFirst().text()).isEqualTo("🙂".repeat(2000));
            var missing = new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, new GitLabReadTokenFile(directory.resolve("absent")));
            assertThat(missing.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main").status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
            assertThat(fixture.stub.requests).hasSize(1);
        }
    }
    @Test void foreignSourceAndRevokedPermissionNeverReadCode() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            var denied = access((call, required) -> new com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegationInvalid());
            var connector = new GitLabCodeConnector(fixture.stub.http("gitlab"), denied, fixture.policy, fixture.tokenFile);
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main").status()).isEqualTo(ResultStatus.DENIED);
            assertThat(new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, fixture.tokenFile)
                .searchCode(request("sources://WM/git"), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main").status()).isEqualTo(ResultStatus.DENIED);
            assertThat(fixture.stub.requests).isEmpty();
        }
    }
    @Test void searchPaginationCannotHideAdditionalAllowedHits() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            fixture.stub.responder = req -> new Response(200, "[{\"path\":\"src/main/" + req.query().get("page") + "\",\"data\":\"자료\"}]",
                Map.of("X-Next-Page", req.query().get("page").equals("1") ? "2" : ""));
            var connector = new GitLabCodeConnector(fixture.stub.http("gitlab"), access(), fixture.policy, fixture.tokenFile);
            assertThat(connector.searchCode(request(GitLabSourcePolicy.SOURCE_REF), "query", new GitLabCodeConnector.CodeScope(REPO, "src/main"), "main").snippets())
                .extracting(GitLabCodeConnector.Snippet::path).containsExactly("src/main/1", "src/main/2");
        }
    }
}
