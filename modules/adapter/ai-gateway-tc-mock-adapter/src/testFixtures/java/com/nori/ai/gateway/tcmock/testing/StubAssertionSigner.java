package com.nori.ai.gateway.tcmock.testing;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class StubAssertionSigner {
    private final ECKey key;
    private StubAssertionSigner(ECKey key) { this.key = key; }
    public static StubAssertionSigner ephemeral(String kid) {
        if (kid == null || !kid.startsWith("test-eph-") || kid.length() <= 9)
            throw new IllegalArgumentException("시험용 키 식별자가 필요합니다");
        try { return new StubAssertionSigner(new ECKeyGenerator(Curve.P_256).keyID(kid).algorithm(JWSAlgorithm.ES256).generate()); }
        catch (JOSEException failure) { throw new IllegalStateException("시험 서명 키를 준비할 수 없습니다"); }
    }
    public static StubAssertionSigner fromPrivateJwkFile(Path path) {
        try {
            JWK parsed = JWK.parse(Files.readString(Objects.requireNonNull(path)));
            if (!(parsed instanceof ECKey ec) || !ec.isPrivate() || !Curve.P_256.equals(ec.getCurve())
                    || ec.getKeyID() == null || !ec.getKeyID().startsWith("test-")
                    || ec.getAlgorithm() != null && !JWSAlgorithm.ES256.equals(ec.getAlgorithm()))
                throw new IllegalArgumentException();
            new ECDSASigner(ec);
            return new StubAssertionSigner(ec);
        } catch (Exception failure) { throw new IllegalStateException("시험 서명 자격을 준비할 수 없습니다"); }
    }
    public ECKey publicJwk() { return key.toPublicJWK(); }
    public String sign(Map<String, Object> claims, Instant iat) {
        Objects.requireNonNull(claims); Objects.requireNonNull(iat);
        try {
            var values = new LinkedHashMap<>(claims);
            values.put("iat", numericDate(iat)); values.put("exp", numericDate(iat.plusSeconds(60)));
            var jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), new Payload(JSONObjectUtils.toJSONString(values)));
            jws.sign(new ECDSASigner(key));
            return jws.serialize();
        } catch (JOSEException failure) { throw new IllegalStateException("시험 단언에 서명할 수 없습니다"); }
    }
    private static BigDecimal numericDate(Instant value) {
        return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
    }
}
