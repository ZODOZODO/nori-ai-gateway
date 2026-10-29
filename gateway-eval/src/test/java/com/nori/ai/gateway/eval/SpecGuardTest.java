package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.eval.TestSupport.error;

class SpecGuardTest {
    @TempDir Path directory;
    @Test void hashesApprovedBytesAndRejectsOneByteChangeOrChangedBaseline() throws Exception {
        TestSupport f = new TestSupport(directory); String approved = f.binding().specSha();
        assertThat(approved).isEqualTo(Json.sha(Files.readAllBytes(f.spec))); assertThat(f.binding().specShaSource()).isEqualTo("FILE_HASHED");
        byte[] bytes = Files.readAllBytes(f.spec); bytes[0] ^= 1; Files.write(f.spec, bytes);
        error(f::binding, 3, "SPEC_BASELINE_MISMATCH"); f.baseline(bytes); assertThat(f.binding().specSha()).isEqualTo(Json.sha(bytes));
        f.baseline("other".getBytes()); error(f::binding, 3, "SPEC_BASELINE_MISMATCH");
    }
    @Test void resolvesExplicitThenPropertyThenInjectedEnvironment() throws Exception {
        TestSupport f = new TestSupport(directory); Path other = directory.resolve("other"); Files.writeString(other, "other");
        SpecInputResolver resolver = new SpecInputResolver(Map.of("AIGW_SPEC_FILE", other.toString()));
        assertThat(resolver.resolve(f.spec.toString(), "missing")).isEqualTo(f.spec);
        assertThat(resolver.resolve(null, f.spec.toString())).isEqualTo(f.spec);
        assertThat(resolver.resolve(null, null)).isEqualTo(other);
        assertThat(new SpecGuard(Map.of()).check(f.spec.toString(), null, f.baseline).specInputOrigin()).isEqualTo("LOCAL_FILE");
    }
    @Test void requiresRegularReadableInputEvenWhenApprovedShaExists() throws Exception {
        TestSupport f = new TestSupport(directory);
        error(() -> new SpecGuard(Map.of("AIGW_APPROVED_SPEC_SHA", f.binding().specSha())).check(null, null, f.baseline), 2, "SPEC_INPUT_MISSING");
        error(() -> new SpecGuard(Map.of()).check(directory.toString(), null, f.baseline), 2, "SPEC_INPUT_MISSING");
        error(() -> new SpecGuard(Map.of()).check(directory.resolve("absent").toString(), null, f.baseline), 2, "SPEC_INPUT_MISSING");
    }
    @Test void requiresVerifiedReceiptInCiAndDerivesAllThreeOrigins() throws Exception {
        TestSupport f = new TestSupport(directory); String sha = f.binding().specSha();
        Map<String,String> ci = Map.of("CI", "true", "AIGW_APPROVED_SPEC_SHA", sha);
        error(() -> new SpecGuard(ci).check(f.spec.toString(), null, f.baseline), 2, "SPEC_INPUT_UNVERIFIED_SOURCE");
        Json.write(f.spec.resolveSibling("fetch-receipt.json"), TestSupport.receipt(f.spec,sha));
        assertThat(new SpecGuard(ci).check(f.spec.toString(), null, f.baseline).specInputOrigin()).isEqualTo("CI_VERIFIED_PACKAGE");
        assertThat(f.binding().specInputOrigin()).isEqualTo("OPERATOR_VERIFIED_PACKAGE");
        Json.write(f.spec.resolveSibling("fetch-receipt.json"), Map.of("package", "aigw-spec-input", "version", sha, "sha256", "f".repeat(64), "apiHost", "gitlab.noricloud.org"));
        error(() -> new SpecGuard(ci).check(f.spec.toString(), null, f.baseline), 2, "SPEC_INPUT_UNVERIFIED_SOURCE");
    }
    @Test void repositoryBaselineForgeryCannotOverrideApprovedCiBytes() throws Exception {
        TestSupport f = new TestSupport(directory); byte[] approved = Files.readAllBytes(f.spec); String approvedSha = f.binding().specSha();
        Path ciFile = directory.resolve("ci/spec.md"); Files.createDirectories(ciFile.getParent()); Files.write(ciFile, approved);
        Json.write(ciFile.resolveSibling("fetch-receipt.json"), TestSupport.receipt(ciFile,approvedSha));
        Files.writeString(f.spec, "forged"); f.baseline(Files.readAllBytes(f.spec)); assertThat(f.binding().specSha()).isNotEqualTo(approvedSha);
        error(() -> new SpecGuard(Map.of("CI", "true", "AIGW_APPROVED_SPEC_SHA", approvedSha)).check(ciFile.toString(), null, f.baseline), 3, "SPEC_BASELINE_MISMATCH");
    }
    @Test void validatesChainContinuityAndUsesLastEntryOnly() throws Exception {
        TestSupport f = new TestSupport(directory); String sha = f.binding().specSha(), next = Json.sha("next".getBytes());
        f.baselineChain(List.of(f.entry(1, sha, null, "ALL", true), f.entry(2, next, "0".repeat(64), List.of(), false)));
        error(f::binding, 2, "SPEC_BASELINE_INVALID");
        f.baselineChain(List.of(f.entry(1, sha, null, "ALL", true), f.entry(3, next, sha, List.of(), false)));
        error(f::binding, 2, "SPEC_BASELINE_INVALID");
        f.baselineChain(List.of(f.entry(1, sha, null, "ALL", true), f.entry(2, next, sha, List.of(), false)));
        error(f::binding, 3, "SPEC_BASELINE_MISMATCH"); Files.writeString(f.spec, "next"); assertThat(f.binding().specSha()).isEqualTo(next);
    }
    @Test void specBindingCanOnlyBeConstructedByGuard() throws Exception {
        assertThat(SpecBinding.class.getDeclaredConstructors()).allSatisfy(c -> assertThat(java.lang.reflect.Modifier.isPublic(c.getModifiers())).isFalse());
        Path source=Path.of(System.getProperty("aigw.rootDir")).resolve("gateway-eval/src/main/java");
        try(var files=Files.walk(source)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) if(Files.readString(file).contains("new SpecBinding("))assertThat(file.getFileName().toString()).isEqualTo("SpecGuard.java");}
    }
    @Test void returnsImmutableSingleReadSnapshotForPackageInput() throws Exception {
        TestSupport f = new TestSupport(directory); byte[] approved = Files.readAllBytes(f.spec);
        var checked = new SpecGuard(Map.of()).readAndCheck(f.spec.toString(), null, f.baseline);
        Files.writeString(f.spec, "changed-after-verification"); byte[] returned = checked.bytes(); returned[0] ^= 1;
        assertThat(checked.bytes()).isEqualTo(approved); assertThat(Json.sha(checked.bytes())).isEqualTo(checked.binding().specSha());
    }
}
