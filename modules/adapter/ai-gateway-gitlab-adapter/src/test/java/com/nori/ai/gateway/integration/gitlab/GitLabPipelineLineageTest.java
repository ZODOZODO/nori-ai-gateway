package com.nori.ai.gateway.integration.gitlab;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.integration.ConnectorTestSupport.Capture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;
import static com.nori.ai.gateway.integration.gitlab.GitLabFixture.*;

class GitLabPipelineLineageTest {
    @TempDir Path directory;
    @Test void commitNotFoundAndUnavailableAreDistinctAndAnAllowedPhysicalRetryCanVerify() throws Exception {
        try(var fixture=new GitLabFixture(directory)) {
            fixture.commitStatus=503;
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF),ENTRY,123).reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SOURCE_UNAVAILABLE);
            assertThat(fixture.stub.requests.stream().filter(req->req.path().contains("/repository/commits/"))).hasSize(2);
            fixture.stub.requests.clear();fixture.commitStatus=404;
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF),ENTRY,123).reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SHORT_SHA_UNRESOLVED);
            assertThat(fixture.stub.requests.stream().filter(req->req.path().contains("/repository/commits/"))).hasSize(1);
            fixture.stub.requests.clear();var attempts=new java.util.concurrent.atomic.AtomicInteger();
            fixture.stub.responder=req->req.path().endsWith("/trace")?new Response(200,fixture.trace):req.path().contains("/repository/commits/")?new Response(attempts.incrementAndGet()==1?500:200,fixture.commit):new Response(200,fixture.jobs);
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF),ENTRY,123).commitSha()).isEqualTo(FULL_SHA);assertThat(attempts).hasValue(2);
        }
    }
    @Test void commitsApiIsTheOnlySourceOfVerifiedFortyCharacterSiblingSha() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.VERIFIED); assertThat(lineage.commitSha()).isEqualTo(FULL_SHA);
            assertThat(lineage.sourceRepo()).isEqualTo(REPO); assertThat(lineage.service()).isEqualTo("tc-gateway"); assertThat(lineage.execPipelineId()).isEqualTo(123);
            assertThat(fixture.stub.requests).hasSize(3).allSatisfy(req -> { assertThat(req.method()).isEqualTo("GET"); assertThat(req.token()).isEqualTo(TOKEN); });
            assertThat(fixture.stub.requests.getFirst().query()).containsEntry("scope[]", "success").containsEntry("include_retried", "true");
            assertThat(fixture.stub.requests.getLast().path()).isEqualTo("/api/v4/projects/22/repository/commits/" + SHORT_SHA);
            assertThat(fixture.stub.requests).noneSatisfy(req -> assertThat(req.path()).contains("main"));
        }
    }
    @Test void zeroDuplicateOrRetriedPackageJobsNeverReadTrace() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            for (String jobs : List.of("[]", "[{\"id\":1,\"name\":\"package\",\"status\":\"failed\"}]",
                    "[{\"id\":1,\"name\":\"package\",\"status\":\"success\"},{\"id\":2,\"name\":\"package\",\"status\":\"success\",\"retried\":true}]")) {
                fixture.jobs = jobs; fixture.stub.requests.clear();
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.UNKNOWN);
                assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.PACKAGE_JOB_AMBIGUOUS);
                assertThat(fixture.stub.requests).hasSize(1); assertThat(lineage.commitSha()).isNull();
            }
        }
    }
    @Test void aDuplicateOnALaterJobsPageIsStillAmbiguous() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            fixture.stub.responder = req -> new Response(200, fixture.jobs, Map.of("X-Next-Page", req.query().get("page").equals("1") ? "2" : ""));
            var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.PACKAGE_JOB_AMBIGUOUS); assertThat(fixture.stub.requests).hasSize(2);
        }
    }
    @Test void zeroDuplicateOrMalformedSiblingLinesNeverResolveCommits() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            String valid = "sibling " + REPO + " @ " + SHORT_SHA;
            for (String trace : List.of("", "CI_COMMIT_SHA=" + FULL_SHA, valid + "\n" + valid + "\n", "sibling nori-wm @ " + SHORT_SHA,
                    "prefix " + valid, valid + " suffix", "sibling " + REPO + " @ ABCDEF1", "x".repeat(10000) + valid)) {
                fixture.trace = trace; fixture.stub.requests.clear();
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SIBLING_COMMIT_AMBIGUOUS);
                assertThat(lineage.commitSha()).isNull(); assertThat(fixture.stub.requests).hasSize(2);
            }
        }
    }
    @Test void unresolvedAmbiguousMismatchingAndMissingCommitAreUnknownAndNeverFallBackToExecSha() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            for (String commit : List.of("[]", "[{\"id\":\"" + FULL_SHA + "\"},{\"id\":\"" + FULL_SHA + "\"}]", "{}", "{\"id\":\"" + SHORT_SHA + "\"}", "{\"id\":\"" + "9".repeat(40) + "\"}")) {
                fixture.commit = commit;
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SHORT_SHA_UNRESOLVED); assertThat(lineage.commitSha()).isNull();
            }
            fixture.commitStatus = 404;
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123).reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SHORT_SHA_UNRESOLVED);
        }
    }
    @Test void rawTraceTokenAndArbitraryLinesAreAbsentFromLogsSpansMetricsResultsAndErrors() throws Exception {
        try (var fixture = new GitLabFixture(directory); var capture = new Capture()) {
            String sentinel = "TRACE_TOKEN_SENTINEL";
            fixture.trace = sentinel + "\n" + "x".repeat(10000) + sentinel + "\n" + "sibling " + REPO + " @ " + SHORT_SHA + "\n";
            var reader = new GitLabPipelineLineageReader(new SourceHttpClient(fixture.stub.base(), "gitlab", capture.observations), access(), fixture.policy, fixture.tokenFile);
            var lineage = reader.siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.VERIFIED);
            assertThat(lineage.toString()).doesNotContain(sentinel, TOKEN, "CI_COMMIT_SHA", "x".repeat(100));
            capture.assertNoSecret(sentinel, fixture.stub.base().toString()); capture.assertNoSecret(TOKEN, fixture.stub.base().toString());
        }
    }
    @Test void sourceFailuresAndCurrentAuthorizationDenialProduceUnknownWithoutTraceFallback() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            fixture.stub.responder = req -> new Response(500, TOKEN);
            var failure = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
            assertThat(failure.status()).isEqualTo(GitLabPipelineLineageReader.Status.UNKNOWN); assertThat(failure.commitSha()).isNull();
            fixture.stub.requests.clear();
            var denied = new GitLabPipelineLineageReader(fixture.stub.http("gitlab"), access((call, required) -> new com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegationInvalid()), fixture.policy, fixture.tokenFile);
            assertThat(denied.siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123).reason()).isEqualTo(GitLabPipelineLineageReader.Reason.DENIED);
            assertThat(fixture.stub.requests).isEmpty();
        }
    }
    @Test void noUnapprovedExecProjectCanBeReadEvenWithTheSameSiblingRepo() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            var altered = new GitLabSourcePolicy.LineageEntry("tc-gateway", 999, "package", REPO, 22, "SIBLING_CLONE_LOG", "stub-lineage-approval");
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), altered, 123).reason()).isEqualTo(GitLabPipelineLineageReader.Reason.NOT_APPROVED);
            assertThat(fixture.stub.requests).isEmpty();
        }
    }
    @Test void traceCrLfAndNoFinalNewlineKeepTheExactSiblingLineContract() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            for (String trace : List.of("ignored\r\nsibling " + REPO + " @ " + SHORT_SHA + "\r\n", "sibling " + REPO + " @ " + SHORT_SHA)) {
                fixture.trace = trace;
                assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123).commitSha()).isEqualTo(FULL_SHA);
            }
        }
    }
    @Test void nonIntegralMissingStringOrOutOfRangeJobIdsStopBeforeTraceAndIntegralIdsRemainExact() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            for (String field : List.of("\"id\":1.5,", "\"id\":1.0,", "\"id\":\"1\",", "", "\"id\":null,", "\"id\":0,", "\"id\":-1,",
                    "\"id\":9223372036854775808,", "\"id\":-9223372036854775809,", "\"id\":true,", "\"id\":{},", "\"id\":[],")) {
                fixture.jobs = "[{" + field + "\"name\":\"package\",\"status\":\"success\"}]";
                fixture.stub.requests.clear();
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.UNKNOWN);
                assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.PACKAGE_JOB_AMBIGUOUS);
                assertThat(lineage.commitSha()).isNull();
                assertThat(fixture.stub.requests).singleElement().satisfies(req -> assertThat(req.path()).endsWith("/pipelines/123/jobs"));
            }
            for (long id : List.of(1L, Long.MAX_VALUE)) {
                fixture.jobs = "[{\"id\":" + id + ",\"name\":\"package\",\"status\":\"success\"}]";
                fixture.stub.requests.clear();
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.VERIFIED);
                assertThat(fixture.stub.requests).hasSize(3);
                assertThat(fixture.stub.requests.get(1).path()).isEqualTo("/api/v4/projects/11/jobs/" + id + "/trace");
            }
        }
    }
    @Test void commitIdentifierMustBeATextualFortyCharacterShaWithoutNumericCoercion() throws Exception {
        try (var fixture = new GitLabFixture(directory)) {
            String shortSha = "1234567", fullSha = shortSha + "1".repeat(33);
            fixture.trace = "sibling " + REPO + " @ " + shortSha + "\n";
            for (String commit : List.of("{\"id\":" + fullSha + "}", "{\"id\":" + fullSha + ".0}", "{\"id\":null}", "{\"id\":true}", "{\"id\":[]}", "{\"id\":{}}", "{}")) {
                fixture.commit = commit; fixture.stub.requests.clear();
                var lineage = fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123);
                assertThat(lineage.status()).isEqualTo(GitLabPipelineLineageReader.Status.UNKNOWN);
                assertThat(lineage.reason()).isEqualTo(GitLabPipelineLineageReader.Reason.SHORT_SHA_UNRESOLVED);
                assertThat(lineage.commitSha()).isNull(); assertThat(fixture.stub.requests).hasSize(3);
            }
            fixture.commit = "{\"id\":\"" + fullSha + "\"}";
            assertThat(fixture.reader().siblingCommits(request(GitLabSourcePolicy.SOURCE_REF), ENTRY, 123).commitSha()).isEqualTo(fullSha);
        }
    }
}
