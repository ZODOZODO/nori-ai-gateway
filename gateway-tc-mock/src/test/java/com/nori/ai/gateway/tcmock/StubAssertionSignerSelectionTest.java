package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class StubAssertionSignerSelectionTest {
    @TempDir Path directory;
    @Test void missingOrInvalidSecretFileNeverFallsBackAndEphemeralKidsDiffer() throws Exception {
        assertThatThrownBy(() -> StubAssertionSigner.fromPrivateJwkFile(directory.resolve("absent"))).hasMessage("시험 서명 자격을 준비할 수 없습니다").hasNoCause();
        var first = StubAssertionSigner.ephemeral("test-eph-first");
        var second = StubAssertionSigner.ephemeral("test-eph-second");
        assertThat(first.publicJwk().getKeyID()).isNotEqualTo(second.publicJwk().getKeyID());
        assertThatThrownBy(() -> StubAssertionSigner.ephemeral("production")).isInstanceOf(IllegalArgumentException.class);
        Path file = directory.resolve("synthetic-private.jwk");
        try {
            Files.createFile(file);
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            else {
                var view = Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class);
                var owner = Files.getOwner(file);
                view.setAcl(java.util.List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(owner).setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build()));
                assertThat(view.getAcl()).allMatch(entry -> entry.principal().equals(owner));
            }
            var key = new ECKeyGenerator(Curve.P_256).keyID("production").generate();
            Files.writeString(file,key.toJSONString());
            assertThatThrownBy(() -> StubAssertionSigner.fromPrivateJwkFile(file)).hasMessage("시험 서명 자격을 준비할 수 없습니다").hasNoCause();
            var testKey = new ECKeyGenerator(Curve.P_256).keyID("test-secret").generate();
            Files.writeString(file,testKey.toJSONString());
            assertThat(StubAssertionSigner.fromPrivateJwkFile(file).publicJwk().isPrivate()).isFalse();
            Files.writeString(file,"{}");
            assertThatThrownBy(() -> StubAssertionSigner.fromPrivateJwkFile(file)).hasNoCause();
        } finally { Files.deleteIfExists(file); }
        assertThat(Files.exists(file)).isFalse();
    }
}
