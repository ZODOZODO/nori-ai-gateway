package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.*;

class ActorAssertionVerifierContractTest {
    static final Instant NOW = Instant.ofEpochSecond(1760000000);
    @TempDir Path directory;
    ActorAssertionVerifier verifier;
    VerifiedServiceIdentity service;
    ECKey key;
    MockHttpServletRequest request;
    @BeforeEach void prepare() throws Exception {
        key = new ECKeyGenerator(Curve.P_256).keyID("test-malformed").generate();
        var p384 = new ECKeyGenerator(Curve.P_384).keyID("test-p384").generate().toPublicJWK();
        var tokens = directory.resolve("tokens.json"); var jwks = directory.resolve("jwks.json");
        Files.writeString(tokens,"{\"" + CredentialFileContractTest.hash("unit-token") + "\":\"TC\"}");
        Files.writeString(jwks,new JWKSet(List.of(key.toPublicJWK(),p384)).toString());
        var credentials = new SecurityCredentialFiles(tokens,jwks);
        service = credentials.authenticate("unit-token").orElseThrow();
        verifier = new ActorAssertionVerifier(credentials,Clock.fixed(NOW,ZoneOffset.UTC),new RouteClassifier());
        request = new MockHttpServletRequest("POST","/v1/query");
    }
    Map<String,Object> claims() {
        var values = new LinkedHashMap<String,Object>();
        values.put("iss","tc-ui-backend"); values.put("aud","ai-gateway"); values.put("sys","TC"); values.put("sub","actor");
        values.put("scope","READ"); values.put("perm",List.of()); values.put("pdg","f".repeat(64)); values.put("dlg","a".repeat(22));
        values.put("iat",1760000000); values.put("exp",1760000060); return values;
    }
    String sign(String json,JWSHeader header) throws Exception {
        var jws = new JWSObject(header,new Payload(json)); jws.sign(new ECDSASigner(key)); return jws.serialize();
    }
    String sign(Map<String,Object> values) throws Exception { return sign(JSONObjectUtils.toJSONString(values),new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build()); }
    void reject(Map<String,Object> values,AuthRejectionReason reason) throws Exception {
        String token = sign(values);
        assertThatThrownBy(() -> verifier.verify(token,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,
            failure -> assertThat(failure.reason()).isEqualTo(reason)).hasNoCause();
    }
    @Test void exactNumericDatesKeepNanosecondsNegativeEpochAndStrictSixtySecondLimit() throws Exception {
        var values = claims();
        var assertion = verifier.verify(sign(values),service,request);
        assertThat(assertion.issuedAt()).isEqualTo(NOW); assertThat(assertion.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(assertion.pdg()).isEqualTo("f".repeat(64));
        values.put("exp",new BigDecimal("1760000060.000000001")); reject(values,ASSERTION_LIFETIME_INVALID);
        values.put("iat",new BigDecimal("1759999999.123456789")); values.put("exp",new BigDecimal("1760000059.123456789"));
        assertion = verifier.verify(sign(values),service,request);
        assertThat(assertion.issuedAt().getNano()).isEqualTo(123456789);
        values.put("exp",new BigDecimal("1760000059.123456790")); reject(values,ASSERTION_LIFETIME_INVALID);
        values = claims(); values.put("iat",new BigDecimal("-1.123456789")); values.put("exp",new BigDecimal("58.876543211"));
        var earlier = new ActorAssertionVerifier(credentials(),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),new RouteClassifier()).verify(sign(values),service,request);
        assertThat(earlier.issuedAt()).isEqualTo(Instant.ofEpochSecond(-2,876543211));
    }
    SecurityCredentialFiles credentials() { return new SecurityCredentialFiles(directory.resolve("tokens.json"),directory.resolve("jwks.json")); }
    @Test void rejectsMissingNullAndWrongTypeTimeClaimsWithoutCoercionOrCauses() throws Exception {
        for (String name : List.of("iat","exp")) {
            var values = claims(); values.remove(name); reject(values,ASSERTION_TIME_INVALID);
            for (Object invalid : Arrays.asList(null,"1760000000",true,List.of(),Map.of(),new BigDecimal("1760000000.0000000001"),new BigDecimal("9999999999999999999999999999"))) {
                values = claims(); values.put(name,invalid); reject(values,ASSERTION_TIME_INVALID);
            }
        }
        for (String payload : List.of("{}{}","{\"iat\":NaN}","{\"iat\":1,\"iat\":2}","{\"extra\":{\"x\":1,\"x\":2}}")) {
            String token = sign(payload,new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build());
            assertThatThrownBy(() -> verifier.verify(token,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,f -> assertThat(f.reason()).isEqualTo(ASSERTION_TIME_INVALID));
        }
    }
    @Test void rejectsAllMandatoryClaimBoundariesAndPathSpecificNullDelegation() throws Exception {
        var reasons = Map.of("iss",ISSUER_INVALID,"aud",AUDIENCE_INVALID,"sys",SYSTEM_SCOPE_MISMATCH,"sub",SUBJECT_INVALID,"scope",READ_SCOPE_INVALID,"perm",PERMISSIONS_INVALID,"pdg",PDG_MISSING,"dlg",DELEGATION_INVALID);
        for (var entry : reasons.entrySet()) {
            var values = claims(); values.remove(entry.getKey()); reject(values,entry.getValue());
            values = claims(); values.put(entry.getKey(),null); reject(values,entry.getKey().equals("pdg") ? PDG_INVALID : entry.getValue());
            values = claims(); values.put(entry.getKey(),Map.of("wrong",true)); reject(values,entry.getKey().equals("pdg") ? PDG_INVALID : entry.getValue());
        }
        request = new MockHttpServletRequest("POST","/v1/conversations");
        var values = claims(); values.put("dlg",null); reject(values,DELEGATION_FORBIDDEN);
        values.remove("dlg"); assertThat(verifier.verify(sign(values),service,request).delegation()).isEmpty();
        request = new MockHttpServletRequest("POST","/v1/query");
        values = claims(); values.put("perm",Arrays.asList("EQP_READ",null)); reject(values,PERMISSIONS_INVALID);
    }
    @Test void enforcesExpiryFutureIssueLifetimeHeaderTrustAndKeyCurve() throws Exception {
        var values = claims(); values.put("iat",1759999940); values.put("exp",1760000000); reject(values,ASSERTION_EXPIRED);
        values = claims(); values.put("iat",new BigDecimal("1760000000.000000001")); values.put("exp",new BigDecimal("1760000060.000000001")); reject(values,ASSERTION_TIME_INVALID);
        for (long seconds : List.of(1760000000L,1759999999L,1760000061L)) { values = claims(); values.put("exp",seconds); reject(values,ASSERTION_LIFETIME_INVALID); }
        for (String field : List.of("jku","jwk","x5u")) {
            var nullHeader = JWSHeader.parse(com.nimbusds.jose.util.Base64URL.encode("{\"alg\":\"ES256\",\"kid\":\"" + key.getKeyID() + "\",\"" + field + "\":null}"));
            String nullToken = sign(JSONObjectUtils.toJSONString(claims()),nullHeader);
            assertThat(JWSObject.parse(nullToken).getHeader().getParsedBase64URL().decodeToString()).contains("\"" + field + "\":null");
            assertThatThrownBy(() -> verifier.verify(nullToken,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,f -> assertThat(f.reason()).isEqualTo(UNTRUSTED_KEY_REFERENCE));
            var builder = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID());
            if (field.equals("jwk")) builder.jwk(key.toPublicJWK());
            else if (field.equals("jku")) builder.jwkURL(java.net.URI.create("http://127.0.0.1:1/untrusted"));
            else builder.x509CertURL(java.net.URI.create("http://127.0.0.1:1/untrusted"));
            var header = builder.build();
            String token = sign(JSONObjectUtils.toJSONString(claims()),header);
            assertThatThrownBy(() -> verifier.verify(token,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,f -> assertThat(f.reason()).isEqualTo(UNTRUSTED_KEY_REFERENCE));
        }
        String wrongCurve = sign(JSONObjectUtils.toJSONString(claims()),new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("test-p384").build());
        assertThatThrownBy(() -> verifier.verify(wrongCurve,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,f -> assertThat(f.reason()).isEqualTo(SIGNATURE_INVALID));
    }
    @Test void classifiesMatrixContextCaseAndTrailingSlashAsMvcPatterns() {
        var routes = new RouteClassifier();
        for (String path : List.of("/v1/query;tag=x","/v1/conversations/id;tag=x/messages","/v1/analyses;tag=x")) assertThat(routes.isJob(new MockHttpServletRequest("POST",path))).isTrue();
        var contextRequest = new MockHttpServletRequest("POST","/gateway/v1/query;tag=x"); contextRequest.setContextPath("/gateway");
        assertThat(routes.isJob(contextRequest)).isTrue();
        for (String path : List.of("/v1/Query","/v1/query/","/v1/conversations")) assertThat(routes.isJob(new MockHttpServletRequest("POST",path))).isFalse();
    }
    @Test void rejectsActuallySignedNonUtf8AndMalformedUtf8Payloads() throws Exception {
        var payloads = new ArrayList<byte[]>();
        for (String encoding : List.of("UTF-16LE","UTF-16BE","UTF-32LE","UTF-32BE")) payloads.add(JSONObjectUtils.toJSONString(claims()).getBytes(java.nio.charset.Charset.forName(encoding)));
        payloads.add(new byte[]{'{',(byte)0xff,'}'});
        for (byte[] payload : payloads) {
            var jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),new Payload(payload));
            jws.sign(new ECDSASigner(key)); String token = jws.serialize();
            assertThatThrownBy(() -> verifier.verify(token,service,request)).isInstanceOfSatisfying(ActorAssertionVerifier.Rejected.class,
                failure -> assertThat(failure.reason()).isEqualTo(ASSERTION_TIME_INVALID)).hasNoCause();
        }
    }
}
