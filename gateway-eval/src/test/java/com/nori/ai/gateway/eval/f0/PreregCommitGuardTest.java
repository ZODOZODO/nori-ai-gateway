package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.EvalCli;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PreregCommitGuardTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    @TempDir Path directory;

    void refused(F0TestRoot f, String code) throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(F0RunTest.runB(f, stub, "P1")).as(code).isEqualTo(2);
            assertThat(f.lastError).isEqualTo(code);
            assertThat(stub.requests).as("사전등록 검사 전 endpoint 호출 0").isEmpty();
            assertThat(f.root.resolve("eval/f0/results/P1")).doesNotExist();
        }
    }
    @Test void uncommittedOrInconsistentRegistrationRefusesMeasurementBeforeAnyCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        f.prereg(null); refused(f, "PREREG_NOT_COMMITTED");
        f.prereg(F0TestRoot.REGISTERED);
        f.git.committed = false; refused(f, "PREREG_NOT_COMMITTED"); f.git.committed = true;
        f.git.inside = false; refused(f, "PREREG_NOT_COMMITTED"); f.git.inside = true;
        f.git.status = " M eval/golden/tc-f0-v1.jsonl\n"; refused(f, "PREREG_WORKTREE_DIRTY");
        f.git.status = "?? eval/f0/results/old/B/f0-results.json\n?? eval/f0/chat-template/model-digest.txt\n";
        f.git.ordered = false; refused(f, "PREREG_COMMIT_ORDER_INVALID"); f.git.ordered = true;
        f.git.blobOverride.put("eval/f0/prompts/classify-v1.txt", "다른 등록 바이트".getBytes(StandardCharsets.UTF_8)); refused(f, "PREREG_REGISTERED_COMMIT_MISMATCH"); f.git.blobOverride.clear();
        Files.writeString(f.root.resolve("eval/golden/tc-f0-v1.jsonl"), Files.readString(f.root.resolve("eval/golden/tc-f0-v1.jsonl")) + "\n");
        refused(f, "PREREG_INPUT_CHANGED");
        f.prereg(F0TestRoot.REGISTERED);
        f.write("eval/f0/extra-harness-note.md", "등록 뒤 추가된 하네스 파일");
        refused(f, "PREREG_INPUT_CHANGED");
        Files.delete(f.root.resolve("eval/f0/extra-harness-note.md"));
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0RunTest.runB(f, stub, "P2")).isZero();
        }
        assertThat(f.git.calls).allSatisfy(call -> assertThat(GitReader.READ_ONLY).contains(call.getFirst()));
        assertThat(f.git.calls.stream().map(List::getFirst)).doesNotContain("commit", "checkout", "restore", "stash", "push", "reset", "add");
    }
    @Test void sameCommitForRegistrationAndPreregIsRejectedSoTheTwoCommitsStayDistinguishable() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        f.prereg(F0TestRoot.PREREG_COMMIT);
        refused(f, "PREREG_COMMIT_ORDER_INVALID");
    }
    @Test void harnessDigestExcludesPreregResultsAndChatTemplateButCoversHarnessSources() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        String before = HarnessDigest.compute(f.root);
        f.write("eval/f0/PREREG.md", "변경"); f.write("eval/f0/results/x/y.json", "{}"); f.write("eval/f0/chat-template/model-digest.txt", "x");
        f.write("eval/golden/other.jsonl", "{}"); f.write("gateway-eval/src/test/java/X.java", "class X {}");
        assertThat(HarnessDigest.compute(f.root)).isEqualTo(before);
        f.write("gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Probe.java", "class Probe {}");
        String withSource = HarnessDigest.compute(f.root);
        assertThat(withSource).isNotEqualTo(before);
        f.write("eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "{\"changed\":1}");
        assertThat(HarnessDigest.compute(f.root)).isNotEqualTo(withSource);
    }
    @Test void realGitReaderIsReadOnlyAndNonRepositoryMeansNotCommitted() throws Exception {
        GitReader git = GitReader.process();
        assertThatThrownBy(() -> git.run(directory, List.of("commit", "-m", "x"))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("GIT_COMMAND_FORBIDDEN"));
        assertThatThrownBy(() -> git.run(directory, List.of("checkout", "x"))).isInstanceOf(EvalException.class);
        F0TestRoot f = new F0TestRoot(directory.resolve("plain"));
        assertThatThrownBy(() -> Prereg.verify(f.root, f.root.resolve(Prereg.DEFAULT_PATH), git)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_NOT_COMMITTED"));
    }
    /** 미등록 PREREG(registeredCommit=null)는 실제 EvalCli 측정 진입점에서 endpoint 호출·결과 생성 없이 거부되어야 합니다(실제 레포 등록 상태와 무관한 임시 루트 fixture). */
    @Test void runnerEntryRefusesUnregisteredPreregWithoutAnyCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        f.prereg(null);
        Prereg.Document fixture = Prereg.parse(f.root.resolve(Prereg.DEFAULT_PATH));
        assertThat(fixture.registeredCommit()).as("명시적 미등록 fixture").isNull();
        assertThat(Prereg.localMismatches(f.root, fixture)).isEmpty();
        try (OllamaStub stub = new OllamaStub()) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int exit = new EvalCli(f.env, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8), f.root)
                .execute("f0", "run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", "eval/golden/tc-f0-v1.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
                    "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", String.valueOf(fixture.repeats()), "--run-id", "P02GUARDTEST", "--out", "eval/f0/results/P02GUARDTEST/B");
            assertThat(exit).isEqualTo(2); assertThat(err.toString(StandardCharsets.UTF_8).strip()).isEqualTo("PREREG_NOT_COMMITTED");
            assertThat(stub.requests).isEmpty();
            assertThat(f.root.resolve("eval/f0/results")).doesNotExist();
            assertThat(ROOT.resolve("eval/f0/results/P02GUARDTEST")).doesNotExist();
        }
    }
    /**
     * 레포 PREREG 는 등록 SHA 가 현재 바이트와 같고, 등록 상태 표기가 실제 등록 수명주기와 맞아야 합니다.
     * 미등록(registeredCommit=null)이면 UNCOMMITTED 이고 실제 검사가 PREREG_NOT_COMMITTED, 등록 후면 REGISTERED 이고 실제 git 으로 C1/C2 검사가 통과해야 합니다.
     */
    @Test void repositoryPreregRegistrationStateMatchesLifecycleAndCurrentBytes() throws Exception {
        Path preregPath = ROOT.resolve(Prereg.DEFAULT_PATH);
        Prereg.Document document = Prereg.parse(preregPath);
        assertThat(Prereg.localMismatches(ROOT, document)).isEmpty();
        assertThat(document.repeats()).isEqualTo(5); assertThat(document.inputs().keySet()).containsAll(Prereg.REQUIRED_INPUTS).contains("eval/py/count_tokens.py", "eval/py/requirements.txt");
        String text = Files.readString(preregPath, StandardCharsets.UTF_8);
        assertThat(text).doesNotContain(Json.sha(Files.readAllBytes(preregPath)));
        String status = document.block().path("registrationStatus").asText();
        if (document.registeredCommit() == null) {
            assertThat(status).isEqualTo("UNCOMMITTED");
            assertThatThrownBy(() -> Prereg.verify(ROOT, preregPath, GitReader.process())).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_NOT_COMMITTED"));
        } else {
            assertThat(status).isEqualTo("REGISTERED");
            Prereg.State state = Prereg.verify(ROOT, preregPath, GitReader.process());
            assertThat(state.document().registeredCommit()).isEqualTo(document.registeredCommit());
            assertThat(state.preregCommit()).matches("[0-9a-f]{40}").isNotEqualTo(document.registeredCommit());
        }
    }
}
