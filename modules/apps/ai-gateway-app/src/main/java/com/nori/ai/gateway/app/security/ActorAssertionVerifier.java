package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import static com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.*;

public final class ActorAssertionVerifier {
    private final SecurityCredentialFiles credentials;
    private final Clock clock;
    private final RouteClassifier routes;
    public ActorAssertionVerifier(SecurityCredentialFiles credentials, Clock clock, RouteClassifier routes) {
        this.credentials = Objects.requireNonNull(credentials); this.clock = Objects.requireNonNull(clock); this.routes = Objects.requireNonNull(routes);
    }
    record VerifiedAssertion(OwnerSystem owner, ActorRef actor, Set<String> perm, String pdg,
            Instant issuedAt, Instant expiresAt, Instant verifiedAt, Optional<String> delegation) {
        VerifiedAssertion { perm = Set.copyOf(perm); }
    }
    static final class Rejected extends RuntimeException {
        private final AuthRejectionReason reason;
        Rejected(AuthRejectionReason reason) { super("인입 인증이 거절되었습니다", null, false, false); this.reason = reason; }
        AuthRejectionReason reason() { return reason; }
    }
    VerifiedAssertion verify(String serialized, VerifiedServiceIdentity service, HttpServletRequest request) {
        if (serialized == null || serialized.isBlank()) throw new Rejected(ACTOR_ASSERTION_MISSING);
        final JWSObject jws;
        try { jws = JWSObject.parse(serialized); }
        catch (Exception failure) { throw new Rejected(SIGNATURE_INVALID); }
        var header = jws.getHeader();
        if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) throw new Rejected(ALGORITHM_NOT_ALLOWED);
        if (hasUntrustedKeyReference(jws)) throw new Rejected(UNTRUSTED_KEY_REFERENCE);
        JWK key = credentials.key(header.getKeyID());
        if (key == null) throw new Rejected(UNKNOWN_KID);
        if (!(key instanceof ECKey ec) || !Curve.P_256.equals(ec.getCurve()) || ec.getAlgorithm() != null && !JWSAlgorithm.ES256.equals(ec.getAlgorithm()))
            throw new Rejected(SIGNATURE_INVALID);
        try { if (!jws.verify(new ECDSAVerifier(ec))) throw new Rejected(SIGNATURE_INVALID); }
        catch (JOSEException failure) { throw new Rejected(SIGNATURE_INVALID); }
        SignedClaims claims = readSignedClaims(jws.getPayload().toBytes());
        var values = claims.values();
        if (!"tc-ui-backend".equals(values.get("iss"))) throw new Rejected(ISSUER_INVALID);
        Object audience = values.get("aud");
        if (!("ai-gateway".equals(audience) || audience instanceof List<?> list && list.size() == 1 && "ai-gateway".equals(list.getFirst())))
            throw new Rejected(AUDIENCE_INVALID);
        Instant issued = readNumericDate(claims.iat()); Instant expires = readNumericDate(claims.exp());
        Instant now = clock.instant();
        Duration lifetime = Duration.between(issued,expires);
        if (lifetime.compareTo(Duration.ZERO) <= 0 || lifetime.compareTo(Duration.ofSeconds(60)) > 0) throw new Rejected(ASSERTION_LIFETIME_INVALID);
        if (issued.isAfter(now)) throw new Rejected(ASSERTION_TIME_INVALID);
        if (!now.isBefore(expires)) throw new Rejected(ASSERTION_EXPIRED);
        if (!(values.get("sub") instanceof String sub) || !sub.matches("[A-Za-z0-9_-]{1,64}")) throw new Rejected(SUBJECT_INVALID);
        if (!"READ".equals(values.get("scope"))) throw new Rejected(READ_SCOPE_INVALID);
        if (!(values.get("perm") instanceof List<?> perms) || perms.stream().anyMatch(p -> !(p instanceof String s) || !s.matches("[A-Z0-9_]{1,64}")))
            throw new Rejected(PERMISSIONS_INVALID);
        if (!values.containsKey("pdg")) throw new Rejected(PDG_MISSING);
        if (!(values.get("pdg") instanceof String pdg) || !pdg.matches("[0-9a-f]{64}")) throw new Rejected(PDG_INVALID);
        boolean job = routes.isJob(request);
        if (!job && values.containsKey("dlg")) throw new Rejected(DELEGATION_FORBIDDEN);
        if (job && (!(values.get("dlg") instanceof String dlg) || !dlg.matches("[A-Za-z0-9_-]{22,128}"))) throw new Rejected(DELEGATION_INVALID);
        if (!service.ownerSystem().name().equals(values.get("sys"))) throw new Rejected(SYSTEM_SCOPE_MISMATCH);
        var permitted = new HashSet<String>(); for (var value : perms) permitted.add((String)value);
        return new VerifiedAssertion(service.ownerSystem(),new ActorRef(sub),permitted,pdg,issued,expires,now,
            job ? Optional.of((String)values.get("dlg")) : Optional.empty());
    }
    private record SignedClaims(Map<String,Object> values, BigDecimal iat, BigDecimal exp) {}
    private static boolean hasUntrustedKeyReference(JWSObject jws) {
        try {
            byte[] bytes = jws.getHeader().getParsedBase64URL().decode();
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            var factory = JsonFactory.builder().disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            // Nimbus getter JSON은 null 필드를 지우므로 원본 헤더의 실제 키 존재를 검사합니다.
            try (var parser = factory.createParser(ObjectReadContext.empty(),bytes)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException();
                boolean untrusted = false;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw new IllegalArgumentException();
                    untrusted |= Set.of("jku","jwk","x5u").contains(parser.currentName());
                    if (parser.nextToken() == null) throw new IllegalArgumentException();
                    parser.skipChildren();
                }
                if (parser.nextToken() != null) throw new IllegalArgumentException();
                return untrusted;
            }
        } catch (Exception failure) { throw new Rejected(SIGNATURE_INVALID); }
    }
    private static SignedClaims readSignedClaims(byte[] payload) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload));
            // UTF-16의 ASCII/NUL 바이트도 UTF-8 decoder는 통과하므로 자동 인코딩 감지를 별도로 끕니다.
            var factory = JsonFactory.builder().disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            try (var parser = factory.createParser(ObjectReadContext.empty(),payload)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException();
                var values = new LinkedHashMap<String,Object>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw new IllegalArgumentException();
                    String name = parser.currentName(); parser.nextToken(); values.put(name,readValue(parser));
                }
                if (parser.nextToken() != null) throw new IllegalArgumentException();
                if (!(values.get("iat") instanceof BigDecimal iat) || !(values.get("exp") instanceof BigDecimal exp)) throw new IllegalArgumentException();
                return new SignedClaims(Collections.unmodifiableMap(values),iat,exp);
            }
        } catch (Exception failure) { throw new Rejected(ASSERTION_TIME_INVALID); }
    }
    private static Object readValue(JsonParser parser) {
        return switch (parser.currentToken()) {
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_NULL -> null;
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case START_ARRAY -> {
                var values = new ArrayList<Object>();
                while (parser.nextToken() != JsonToken.END_ARRAY) values.add(readValue(parser));
                yield Collections.unmodifiableList(values);
            }
            case START_OBJECT -> {
                var values = new LinkedHashMap<String,Object>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw new IllegalArgumentException();
                    String name = parser.currentName(); parser.nextToken(); values.put(name,readValue(parser));
                }
                yield Collections.unmodifiableMap(values);
            }
            default -> throw new IllegalArgumentException();
        };
    }
    private static Instant readNumericDate(BigDecimal value) {
        try {
            BigDecimal seconds = value.setScale(0,RoundingMode.FLOOR);
            return Instant.ofEpochSecond(seconds.longValueExact(),value.subtract(seconds).movePointRight(9).intValueExact());
        } catch (Exception failure) { throw new Rejected(ASSERTION_TIME_INVALID); }
    }
}
