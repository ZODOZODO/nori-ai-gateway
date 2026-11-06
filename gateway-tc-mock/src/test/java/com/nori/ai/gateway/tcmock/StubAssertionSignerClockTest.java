package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StubAssertionSignerClockTest {
    @Test void signsProvidedInstantWithExactlySixtySecondsAndPublicKeyOnly() throws Exception {
        var signer = StubAssertionSigner.ephemeral("test-eph-clock");
        var instant = Instant.parse("2000-01-01T00:00:00.123456789Z");
        var jws = JWSObject.parse(signer.sign(Map.of("sub","actor","exp",0),instant));
        assertThat(jws.verify(new ECDSAVerifier(signer.publicJwk()))).isTrue();
        assertThat(signer.publicJwk().isPrivate()).isFalse();
        String payload = jws.getPayload().toString();
        assertThat(payload).contains("946684800.123456789", "946684860.123456789");
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    }
}
