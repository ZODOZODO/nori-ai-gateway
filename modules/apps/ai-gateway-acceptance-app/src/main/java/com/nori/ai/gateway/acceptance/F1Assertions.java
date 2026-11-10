package com.nori.ai.gateway.acceptance;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 기존 정상 signer가 iat/exp를 보정하므로 인입 단일 위반용 서명은 여기서 원래 claims를 그대로 서명합니다. */
public final class F1Assertions {
    private final ECKey key;
    public F1Assertions(ECKey key) {
        if (key == null || !key.isPrivate() || !Curve.P_256.equals(key.getCurve()) || key.getKeyID() == null || !key.getKeyID().startsWith("test-")) throw new IllegalArgumentException("F1_SIGNER_INVALID");
        this.key = key;
    }
    public Map<String,Object> claims(Instant now, String dlg) {
        var values = new LinkedHashMap<String,Object>(F1Fixture.claims());
        values.put("iat", date(now)); values.put("exp", date(now.plusSeconds(60))); values.put("dlg", dlg); return values;
    }
    public String sign(Map<String,Object> claims) { return sign(claims, key, key.getKeyID()); }
    public String invalidSignature(Map<String,Object> claims) {
        try { return sign(claims, new ECKeyGenerator(Curve.P_256).generate(), key.getKeyID()); }
        catch (JOSEException e) { throw new IllegalStateException("F1_SIGNING_FAILED"); }
    }
    public String unknownKid(Map<String,Object> claims) { return sign(claims, key, "test-f1-untrusted-" + UUID.randomUUID()); }
    private static String sign(Map<String,Object> claims, ECKey key, String kid) {
        try { var object = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(kid).build(), new Payload(JSONObjectUtils.toJSONString(claims))); object.sign(new ECDSASigner(key)); return object.serialize(); }
        catch (JOSEException e) { throw new IllegalStateException("F1_SIGNING_FAILED"); }
    }
    public static BigDecimal date(Instant value) { return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9)); }
}
