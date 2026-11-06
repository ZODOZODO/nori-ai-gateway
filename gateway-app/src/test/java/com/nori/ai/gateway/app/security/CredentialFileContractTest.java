package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class CredentialFileContractTest {
    @TempDir Path directory;
    Path tokens, jwks;
    String token;
    StubAssertionSigner signer;
    @BeforeEach void prepare() throws Exception {
        tokens = directory.resolve("service-tokens.json"); jwks = directory.resolve("jwks.json");
        token = UUID.randomUUID().toString(); signer = StubAssertionSigner.ephemeral("test-eph-credential");
        Files.writeString(tokens,"{\"" + hash(token) + "\":\"TC\"}");
        Files.writeString(jwks,new JWKSet(signer.publicJwk()).toString());
    }
    static String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    void rejected() {
        assertThatThrownBy(() -> new SecurityCredentialFiles(tokens,jwks)).isInstanceOf(IllegalStateException.class)
            .hasMessage("인입 인증 자격 파일이 잘못되었습니다").hasNoCause();
    }
    @Test void loadsMixedPublicKeysOnceAndKeepsOnlyImmutableTrust() throws Exception {
        var rsa = new RSAKeyGenerator(2048).keyID("test-rsa").algorithm(JWSAlgorithm.RS256).generate().toPublicJWK();
        Files.writeString(jwks,new JWKSet(List.of(signer.publicJwk(),rsa)).toString());
        var credentials = new SecurityCredentialFiles(tokens,jwks);
        Files.writeString(tokens,"{}"); Files.delete(jwks);
        assertThat(credentials.authenticate(token).orElseThrow().ownerSystem()).isEqualTo(com.nori.ai.gateway.core.scope.OwnerSystem.TC);
        assertThat(credentials.authenticate(UUID.randomUUID().toString())).isEmpty();
        assertThat(credentials.key("test-rsa")).isNotNull();
        assertThat(credentials.key(signer.publicJwk().getKeyID()).isPrivate()).isFalse();
    }
    @Test void rejectsMalformedEmptyUnknownOwnerAndDuplicateTokenKeysWithoutCauses() throws Exception {
        for (String data : List.of("", "{}", "[]", "{", "{\"plain-token\":\"TC\"}", "{\"" + "A".repeat(64) + "\":\"TC\"}",
                "{\"" + hash(token) + "\":\"OTHER\"}", "{\"" + hash(token) + "\":\"TC\",\"" + hash(token) + "\":\"WM\"}",
                "{\"" + hash(token) + "\":null}", "{} {}")) {
            Files.writeString(tokens,data); rejected();
        }
        Files.delete(tokens); rejected();
        assertThatThrownBy(() -> new IngressSecurityConfiguration().securityCredentialFiles(new MockEnvironment()))
            .hasMessage("인입 인증 자격 파일이 잘못되었습니다").hasNoCause();
    }
    @Test void rejectsPrivateFieldsAtEveryDepthDuplicateKidsIllegalKeysAndAlgCurveMismatch() throws Exception {
        String valid = new JWKSet(signer.publicJwk()).toString();
        for (String field : List.of("d","p","q","dp","dq","qi","oth","k")) {
            var node = new LinkedHashMap<String,Object>(signer.publicJwk().toJSONObject());
            node.put("extra",List.of(Map.of("nested",Map.of(field,"private-sentinel"))));
            Files.writeString(jwks,JSONObjectUtils.toJSONString(Map.of("keys",List.of(node)))); rejected();
        }
        for (String data : List.of("", "{}", "{\"keys\":[]}", "{\"keys\":[{}]}", "{\"keys\":[" + signer.publicJwk().toJSONString() + "," + signer.publicJwk().toJSONString() + "]}",valid + "{}")) {
            Files.writeString(jwks,data); rejected();
        }
        var invalid = new LinkedHashMap<String,Object>(signer.publicJwk().toJSONObject()); invalid.put("alg","RS256");
        Files.writeString(jwks,JSONObjectUtils.toJSONString(Map.of("keys",List.of(invalid)))); rejected();
        invalid.put("alg","ES256"); invalid.put("x","AA");
        Files.writeString(jwks,JSONObjectUtils.toJSONString(Map.of("keys",List.of(invalid)))); rejected();
        var p384 = new ECKeyGenerator(Curve.P_384).keyID("test-curve").algorithm(JWSAlgorithm.ES256).generate().toPublicJWK();
        Files.writeString(jwks,new JWKSet(p384).toString()); rejected();
        var rsa = new LinkedHashMap<String,Object>(new RSAKeyGenerator(2048).keyID("test-rsa").generate().toPublicJWK().toJSONObject()); rsa.put("alg","ES256");
        Files.writeString(jwks,JSONObjectUtils.toJSONString(Map.of("keys",List.of(rsa)))); rejected();
        Files.writeString(jwks,valid.replace("\"keys\":", "\"keys\":[],\"keys\":")); rejected();
        Files.delete(jwks); rejected();
    }
    @Test void rejectsAlternateEncodingEvenWhenAllBytesPassUtf8Decoder() throws Exception {
        String tokenJson = Files.readString(tokens); String keyJson = Files.readString(jwks);
        for (String charset : List.of("UTF-16LE","UTF-16BE","UTF-32LE","UTF-32BE")) {
            Files.write(tokens,tokenJson.getBytes(java.nio.charset.Charset.forName(charset))); rejected();
            Files.writeString(tokens,tokenJson);
            Files.write(jwks,keyJson.getBytes(java.nio.charset.Charset.forName(charset))); rejected();
            Files.writeString(jwks,keyJson);
        }
    }
}
