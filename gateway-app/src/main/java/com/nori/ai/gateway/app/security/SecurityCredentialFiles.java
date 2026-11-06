package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class SecurityCredentialFiles {
    private static final Set<String> PRIVATE_FIELDS = Set.of("d","p","q","dp","dq","qi","oth","k");
    private final List<TokenBinding> tokens;
    private final Map<String,JWK> keys;
    private record TokenBinding(byte[] digest, OwnerSystem owner) {}

    public SecurityCredentialFiles(Path serviceTokensFile, Path actorJwksFile) {
        try {
            var mapper = JsonMapper.builder(JsonFactory.builder().disable(tools.jackson.core.TokenStreamFactory.Feature.CHARSET_DETECTION)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            var tokenJson = mapper.readTree(read(serviceTokensFile));
            if (!tokenJson.isObject() || tokenJson.isEmpty()) throw new IllegalArgumentException();
            var loadedTokens = new ArrayList<TokenBinding>();
            for (var entry : tokenJson.properties()) {
                if (!entry.getKey().matches("[0-9a-f]{64}") || !entry.getValue().isString()) throw new IllegalArgumentException();
                loadedTokens.add(new TokenBinding(HexFormat.of().parseHex(entry.getKey()), OwnerSystem.valueOf(entry.getValue().asString())));
            }
            var jwks = mapper.readTree(read(actorJwksFile));
            rejectPrivate(jwks);
            if (!jwks.isObject() || jwks.get("keys") == null || !jwks.get("keys").isArray() || jwks.get("keys").isEmpty()) throw new IllegalArgumentException();
            var loadedKeys = new LinkedHashMap<String,JWK>();
            for (var node : jwks.get("keys")) {
                JWK key = JWK.parse(mapper.writeValueAsString(node));
                validatePublicKey(key);
                if (key.getKeyID() == null || key.getKeyID().isBlank() || loadedKeys.putIfAbsent(key.getKeyID(), key) != null) throw new IllegalArgumentException();
            }
            tokens = List.copyOf(loadedTokens); keys = Map.copyOf(loadedKeys);
        } catch (Exception failure) {
            // 파서와 JCA 예외에는 입력 키·파일 경로가 포함될 수 있으므로 원인을 전달하지 않습니다.
            throw new IllegalStateException("인입 인증 자격 파일이 잘못되었습니다");
        }
    }
    private static byte[] read(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(Objects.requireNonNull(path));
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
        return bytes;
    }
    private static void rejectPrivate(JsonNode node) {
        if (node.isObject()) for (var entry : node.properties()) {
            if (PRIVATE_FIELDS.contains(entry.getKey())) throw new IllegalArgumentException();
            rejectPrivate(entry.getValue());
        }
        else if (node.isArray()) for (var child : node) rejectPrivate(child);
    }
    private static void validatePublicKey(JWK key) throws Exception {
        if (key.isPrivate()) throw new IllegalArgumentException();
        String algorithm = key.getAlgorithm() == null ? null : key.getAlgorithm().getName();
        Set<String> compatible;
        if (key instanceof ECKey ec) {
            ec.toECPublicKey();
            compatible = Curve.P_256.equals(ec.getCurve()) ? Set.of("ES256") : Curve.P_384.equals(ec.getCurve()) ? Set.of("ES384")
                : Curve.P_521.equals(ec.getCurve()) ? Set.of("ES512") : Set.of();
        } else if (key instanceof RSAKey rsa) {
            rsa.toRSAPublicKey(); compatible = Set.of("RS256","RS384","RS512","PS256","PS384","PS512");
        } else if (key instanceof OctetKeyPair okp) {
            okp.toPublicKey(); compatible = Set.of("EdDSA","Ed25519","Ed448");
            if (!Set.of(Curve.Ed25519,Curve.Ed448).contains(okp.getCurve())) throw new IllegalArgumentException();
        } else throw new IllegalArgumentException();
        if (compatible.isEmpty() || algorithm != null && !compatible.contains(algorithm)) throw new IllegalArgumentException();
    }
    Optional<VerifiedServiceIdentity> authenticate(String token) {
        if (token == null || token.isEmpty()) return Optional.empty();
        final byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("서비스 인증을 처리할 수 없습니다"); }
        OwnerSystem owner = null;
        for (var binding : tokens) if (MessageDigest.isEqual(digest,binding.digest())) owner = binding.owner();
        return owner == null ? Optional.empty() : Optional.of(new VerifiedServiceIdentity(owner,"service:" + owner.name()));
    }
    JWK key(String kid) { return kid == null ? null : keys.get(kid); }
}
