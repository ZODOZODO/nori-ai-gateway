package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import jakarta.servlet.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.*;

@Tag("stage-F1") @Tag("label-MOCK_API")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngressAuthIT extends StackContextSupport {
    static final Instant NOW = Instant.ofEpochSecond(1760000000);
    static class MutableClock extends Clock {
        volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfiguration {
        @Bean MutableClock testClock() { return new MutableClock(); }
        @Bean static BeanPostProcessor observeRealRegisteredFilter() {
            return new BeanPostProcessor() {
                @Override @SuppressWarnings({"rawtypes","unchecked"}) public Object postProcessAfterInitialization(Object bean,String name) {
                    if (bean instanceof FilterRegistrationBean registration && registration.getFilter() instanceof ServiceTokenAuthFilter filter)
                        registration.setFilter(spy(filter));
                    return bean;
                }
            };
        }
    }
    static final class MalformedAssertionFixture {
        final ECKey key;
        MalformedAssertionFixture() throws Exception { key = new ECKeyGenerator(Curve.P_256).keyID("test-malformed").generate(); }
        String sign(String payload) throws Exception { return sign(payload,new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),key); }
        String signBytes(byte[] payload) throws Exception {
            var jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),new Payload(payload));
            jws.sign(new ECDSASigner(key)); return jws.serialize();
        }
        String sign(String payload,JWSHeader header,ECKey signingKey) throws Exception {
            var jws = new JWSObject(header,new Payload(payload)); jws.sign(new ECDSASigner(signingKey)); return jws.serialize();
        }
    }
    static final class DisallowedAlgorithmFixture {
        final RSAKey key;
        DisallowedAlgorithmFixture() throws Exception { key = new RSAKeyGenerator(2048).keyID("test-rsa-trusted").algorithm(JWSAlgorithm.RS256).generate(); }
        String sign(Map<String,Object> claims) throws Exception {
            var jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),new Payload(JSONObjectUtils.toJSONString(claims)));
            jws.sign(new RSASSASigner(key));
            assertThat(jws.verify(new RSASSAVerifier(key.toPublicJWK()))).as("허용목록 음성의 RSA 서명 양성").isTrue();
            assertThat(jws.getHeader().getKeyID()).isEqualTo(key.getKeyID());
            assertThat(jws.getPayload().toJSONObject().get("sys")).isEqualTo("TC");
            return jws.serialize();
        }
    }
    MalformedAssertionFixture malformed;
    DisallowedAlgorithmFixture disallowed;
    ECKey otherCurve;
    HttpClient client;
    MutableClock clock;
    ServiceTokenAuthFilter observed;
    IngressTerminalFixture.Counters counters;
    final List<String> logMessages = new CopyOnWriteArrayList<>();
    final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
    @Override protected WebApplicationType applicationType() { return WebApplicationType.SERVLET; }
    @Override protected List<JWK> trustedPublicKeys() { return List.of(signer.publicJwk(),malformed.key.toPublicJWK(),disallowed.key.toPublicJWK(),otherCurve); }
    @BeforeAll void prepare(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        securityDirectory = directory;
        malformed = new MalformedAssertionFixture(); disallowed = new DisallowedAlgorithmFixture();
        otherCurve = new ECKeyGenerator(Curve.P_384).keyID("test-other-curve").algorithm(JWSAlgorithm.ES384).generate().toPublicJWK();
        start(IngressTerminalFixture.class,ObservationConfiguration.class);
        // Spring logging 초기화가 LoggerContext를 reset해 앞서 붙인 appender를 떼므로 활성 컨텍스트 시작 뒤에 붙입니다.
        attachCapture();
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build();
        clock = context.getBean(MutableClock.class); counters = context.getBean(IngressTerminalFixture.Counters.class);
        observed = (ServiceTokenAuthFilter)context.getBean("serviceTokenAuthFilterRegistration",FilterRegistrationBean.class).getFilter();
    }
    // 이 클래스는 한 앱/DB 수명에서 요청별 데이터를 격리하며 마지막에 공통 teardown을 실행합니다.
    @Override protected void close() {}
    @AfterAll void release() {
        try { super.close(); }
        finally { ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(appender); appender.stop(); }
    }
    /** 격리 앱의 기동·종료도 logging을 다시 초기화하므로 매 시험 전에 같은 캡처가 root에 붙어 동작 중인지 보장합니다. */
    void attachCapture() {
        var root = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (!appender.isStarted()) appender.start();
        if (!root.isAttached(appender)) root.addAppender(appender);
    }
    @BeforeEach void positiveControlAndReset() throws Exception {
        attachCapture();
        clock.now = NOW; resetRows();
        String assertion = signer.sign(claims(true),NOW);
        var response = send("/v1/query",assertion,serviceToken,Map.of("query","본문 재독 확인"));
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(JSONObjectUtils.parse(response.body()).get("bodyRead")).isEqualTo(true);
        assertThat(count("gw.conversation")).isEqualTo(1); assertThat(count("gw.job")).isEqualTo(1);
        assertThat(counters.downstreamJob.get()).isEqualTo(1); assertThat(counters.source.get()).isEqualTo(1); assertThat(counters.cache.get()).isEqualTo(1); assertThat(counters.roundTrips.get()).isEqualTo(1);
        verify(observed,times(1)).doFilter(any(),any(),any());
        assertNoResidue(response.body(),List.of(assertion,serviceToken,(String)claims(true).get("dlg")));
        assertRequestOrigin(1);
        resetRows();
    }
    /** 정상 인증 요청 1건의 API_REQUEST와 그 요청의 Job 접수 원점이 같은 서버 requestId·service·SDK trace로 결속되는지 확인하고 trace를 돌려준다. */
    String assertRequestOrigin(int jobs) {
        var rows = jdbc().sql("SELECT event_type, request_id, trace_id, service_ref, job_id FROM gw.audit_event ORDER BY id").query().listOfRows();
        assertThat(rows.stream().map(row -> row.get("event_type")).toList()).isEqualTo(jobs == 1 ? List.of("API_REQUEST","JOB_ACCEPTED") : List.of("API_REQUEST"));
        var request = rows.getFirst();
        assertThat(UUID.fromString((String)request.get("request_id")).toString()).isEqualTo(request.get("request_id")).isNotEqualTo("client-forged-id");
        assertThat((String)request.get("trace_id")).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(request.get("job_id")).isNull();
        for (var row : rows) {
            assertThat(row.get("request_id")).isEqualTo(request.get("request_id")); assertThat(row.get("trace_id")).isEqualTo(request.get("trace_id"));
            assertThat(row.get("service_ref")).isEqualTo("service:TC");
        }
        return (String)request.get("trace_id");
    }
    void resetRows() throws Exception {
        try (var connection = database.connection("postgres"); var statement = connection.createStatement()) { statement.execute("TRUNCATE gw.conversation, gw.audit_event CASCADE"); }
        counters.reset(); clearInvocations(observed); appender.list.clear();
    }
    JdbcClient jdbc() { return context.getBean(JdbcClient.class); }
    int count(String table) { if (!Set.of("gw.conversation","gw.message","gw.job","gw.audit_event").contains(table)) throw new IllegalArgumentException(); return jdbc().sql("SELECT count(*) FROM " + table).query(Integer.class).single(); }
    Map<String,Object> claims(boolean job) {
        var values = new LinkedHashMap<String,Object>();
        values.put("iss","tc-ui-backend"); values.put("aud","ai-gateway"); values.put("sys","TC"); values.put("sub","actor");
        values.put("scope","READ"); values.put("perm",List.of("EQP_READ")); values.put("pdg","a".repeat(64));
        values.put("iat",1760000000); values.put("exp",1760000060); if (job) values.put("dlg","delegation_sentinel_0123456789");
        return values;
    }
    String signed(Map<String,Object> claims) throws Exception { return malformed.sign(JSONObjectUtils.toJSONString(claims)); }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + path); }
    HttpResponse<String> send(String path,String assertion,String token,Map<String,Object> body) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("X-AIGW-Auth-Probe","1").header("Content-Type","application/json").header("X-Request-Id","client-forged-id");
        if (token != null) builder.header("Authorization","Bearer " + token);
        if (assertion != null) builder.header("X-Actor-Assertion",assertion);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    void assertNoResidue(String response,List<String> secrets) {
        var rows = jdbc().sql("SELECT recorded_at, owner_system, actor_ref, request_id, conversation_id, job_id, trace_id, event_type, tool_id, source_ref, decision, status, error_code, timings, token_usage, versions, reduction FROM gw.audit_event").query().listOfRows();
        String serialized = rows.toString();
        String logs = appender.list.stream().map(event -> event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()))).reduce("",String::concat);
        for (String secret : secrets) {
            assertThat(response.contains(secret) || serialized.contains(secret) || logs.contains(secret)).as("응답·감사·로그의 원문 잔류 건수").isFalse();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
            assertThat(response.contains(encoded) || serialized.contains(encoded) || logs.contains(encoded)).as("인코딩 잔류 건수").isFalse();
        }
        var payloads = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+\\.([A-Za-z0-9_-]+)\\.[A-Za-z0-9_-]+").matcher(response + serialized + logs);
        while (payloads.find()) {
            String decoded = new String(Base64.getUrlDecoder().decode(payloads.group(1)),StandardCharsets.UTF_8);
            for (String secret : secrets) assertThat(decoded.contains(secret)).as("JWS 디코드 payload 잔류 건수").isFalse();
        }
    }
    void rejected(String path,String assertion,String token,Map<String,Object> body,AuthRejectionReason reason,String owner,String actor) throws Exception {
        var response = send(path,assertion,token,body);
        assertThat(response.statusCode()).isEqualTo(reason.httpStatus());
        assertThat(count("gw.job")).isZero(); assertThat(count("gw.conversation")).isZero(); assertThat(count("gw.message")).isZero();
        assertThat(counters.downstream()).isZero(); verify(observed,times(1)).doFilter(any(),any(),any());
        var rows = jdbc().sql("SELECT owner_system,actor_ref,request_id,event_type,decision,status,conversation_id,job_id,trace_id,tool_id,source_ref,error_code,timings,token_usage,versions,reduction,service_ref,task_type,result_status FROM gw.audit_event").query().listOfRows();
        assertThat(rows).hasSize(1); var row = rows.getFirst();
        assertThat(row.get("owner_system")).isEqualTo(owner); assertThat(row.get("actor_ref")).isEqualTo(actor);
        assertThat(row.get("event_type")).isEqualTo("AUTH_REJECTED"); assertThat(row.get("decision")).isEqualTo(reason.name()); assertThat(row.get("status")).isEqualTo(Integer.toString(reason.httpStatus()));
        var responseJson = JSONObjectUtils.parse(response.body());
        assertThat(responseJson.keySet()).containsExactlyInAnyOrder("message","requestId");
        assertThat(UUID.fromString((String)responseJson.get("requestId")).toString()).isEqualTo(row.get("request_id"));
        for (String column : List.of("conversation_id","job_id","trace_id","tool_id","source_ref","error_code","timings","token_usage","versions","reduction","service_ref","task_type","result_status")) assertThat(row.get(column)).isNull();
        var sentinels = new ArrayList<String>(); sentinels.add(serviceToken); sentinels.add((String)claims(true).get("dlg"));
        if (assertion != null) { sentinels.add(assertion); sentinels.add(JWSObject.parse(assertion).getPayload().toString()); }
        assertNoResidue(response.body(),sentinels);
    }
    void changed(String key,Object value,AuthRejectionReason reason) throws Exception {
        var values = claims(true); values.put(key,value); rejected("/v1/query",signed(values),serviceToken,Map.of(),reason,"TC",null);
    }
    @Test @Tag("ISO-04-SYSTEM") void bodySystemMismatch() throws Exception { rejected("/v1/query",signed(claims(true)),serviceToken,Map.of("system","WM"),SYSTEM_SCOPE_MISMATCH,"TC","actor"); }
    @Test @Tag("ISO-04-TARGET") void bodyTargetMismatch() throws Exception { rejected("/v1/query",signed(claims(true)),serviceToken,Map.of("targetSystem","WM"),SYSTEM_SCOPE_MISMATCH,"TC","actor"); }
    @Test @Tag("ISO-04-TOKEN-MISSING") void tokenMissing() throws Exception { rejected("/v1/query",signed(claims(true)),null,Map.of(),SERVICE_TOKEN_MISSING,null,null); }
    @Test @Tag("ISO-04-TOKEN-INVALID") void tokenInvalid() throws Exception { rejected("/v1/query",signed(claims(true)),UUID.randomUUID().toString(),Map.of(),SERVICE_TOKEN_INVALID,null,null); }
    @Test @Tag("ISO-04-JWS-ABSENT") void assertionMissing() throws Exception { rejected("/v1/query",null,serviceToken,Map.of(),ACTOR_ASSERTION_MISSING,"TC",null); }
    @Test @Tag("ISO-04-JWS-SIG") void signatureInvalidWithTrustedKid() throws Exception {
        var other = new ECKeyGenerator(Curve.P_256).keyID(malformed.key.getKeyID()).generate();
        String token = malformed.sign(JSONObjectUtils.toJSONString(claims(true)),new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(malformed.key.getKeyID()).build(),other);
        rejected("/v1/query",token,serviceToken,Map.of(),SIGNATURE_INVALID,"TC",null);
    }
    @Test @Tag("ISO-04-JWS-KID") void unknownKid() throws Exception { rejected("/v1/query",StubAssertionSigner.ephemeral("test-eph-untrusted").sign(claims(true),NOW),serviceToken,Map.of(),UNKNOWN_KID,"TC",null); }
    @Test @Tag("ISO-04-JWS-ALG") void validTrustedRsaSignatureOnlyViolatesAllowedAlgorithm() throws Exception { rejected("/v1/query",disallowed.sign(claims(true)),serviceToken,Map.of(),ALGORITHM_NOT_ALLOWED,"TC",null); }
    @Test @Tag("ISO-04-JWS-ISS") void issuerInvalid() throws Exception { changed("iss","other",ISSUER_INVALID); }
    @Test @Tag("ISO-04-JWS-AUD") void audienceInvalid() throws Exception { changed("aud","other",AUDIENCE_INVALID); }
    @Test @Tag("ISO-04-JWS-EXP") void expiredAtExactBoundary() throws Exception {
        var values = claims(true); values.put("iat",1759999940); values.put("exp",1760000000); rejected("/v1/query",signed(values),serviceToken,Map.of(),ASSERTION_EXPIRED,"TC",null);
    }
    @Test @Tag("ISO-04-JWS-LIFE") void exactRawSixtySecondsPlusOneNanosecondRejected() throws Exception { changed("exp",new BigDecimal("1760000060.000000001"),ASSERTION_LIFETIME_INVALID); }
    @Test @Tag("ISO-04-JWS-SYS") void assertionSystemMismatchNeverAdoptsActor() throws Exception { changed("sys","WM",SYSTEM_SCOPE_MISMATCH); }
    @Test @Tag("ISO-04-JWS-SUB") void subjectInvalid() throws Exception { changed("sub","not valid",SUBJECT_INVALID); }
    @Test @Tag("ISO-04-JWS-SCOPE") void scopeInvalid() throws Exception { changed("scope","WRITE",READ_SCOPE_INVALID); }
    @Test @Tag("ISO-04-JWS-PERM") void permissionsInvalid() throws Exception { changed("perm",List.of("not valid"),PERMISSIONS_INVALID); }
    @Test @Tag("ISO-04-JWS-PDG") void digestInvalid() throws Exception { changed("pdg","A".repeat(64),PDG_INVALID); }
    @Test @Tag("ISO-04-JWS-PDG-MISSING") void digestAbsent() throws Exception {
        var values = claims(true); values.remove("pdg"); rejected("/v1/query",signed(values),serviceToken,Map.of(),PDG_MISSING,"TC",null);
    }
    @Test @Tag("ISO-04-JWS-DLG") void delegationAbsentOnJobRoute() throws Exception { rejected("/v1/query",signed(claims(false)),serviceToken,Map.of(),DELEGATION_INVALID,"TC",null); }
    @Test @Tag("ISO-04-JWS-DLG-EXTRA") void delegationForbiddenOnRealNonJobRoute() throws Exception {
        var positive = send("/v1/conversations",signed(claims(false)),serviceToken,Map.of("query","본문 재독 확인"));
        assertThat(positive.statusCode()).isEqualTo(202); assertThat(counters.source.get()).isEqualTo(1); assertThat(counters.cache.get()).isEqualTo(1); assertThat(count("gw.job")).isZero();
        resetRows(); rejected("/v1/conversations",signed(claims(true)),serviceToken,Map.of(),DELEGATION_FORBIDDEN,"TC",null);
    }
    @Test void remoteTraceparentIsNotTrustedAndEachAuthenticatedRequestRecordsOneApiRequest() throws Exception {
        String remoteTrace = "4bf92f3577b34da6a3ce929d0e0e4736";
        for (String path : List.of("/v1/query","/v1/conversations")) {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("X-AIGW-Auth-Probe","1").header("Content-Type","application/json")
                .header("Authorization","Bearer " + serviceToken).header("X-Actor-Assertion",signed(claims(path.equals("/v1/query"))))
                .header("traceparent","00-" + remoteTrace + "-00f067aa0ba902b7-01").header("tracestate","aigw=SENTINEL").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"본문 재독 확인\"}")).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(202);
            // 원격 traceparent를 부모로 쓰지 않고 이 Gateway SDK가 만든 trace만 남깁니다.
            assertThat(assertRequestOrigin(path.equals("/v1/query") ? 1 : 0)).isNotEqualTo(remoteTrace);
            resetRows();
        }
    }
    @Test void allJobRoutesReplayBodyContextRefAndAllowOnlyMatchingLegacyFields() throws Exception {
        for (String path : List.of("/v1/query","/v1/conversations/id/messages","/v1/analyses")) {
            var body = new LinkedHashMap<String,Object>(Map.of("query","본문 재독 확인","system","TC","originSystem","TC","targetSystem","TC","authorizedSystems",List.of("TC")));
            body.put("contextRef",signed(claims(true)));
            var response = send(path,null,serviceToken,body); assertThat(response.statusCode()).isEqualTo(202); assertThat(JSONObjectUtils.parse(response.body()).get("bodyRead")).isEqualTo(true);
            assertThat(counters.roundTrips.get()).isEqualTo(1); resetRows();
        }
        for (var body : List.of(Map.of("originSystem","WM"),Map.of("authorizedSystems",List.of("TC","WM")))) {
            rejected("/v1/query",signed(claims(true)),serviceToken,new LinkedHashMap<>(body),SYSTEM_SCOPE_MISMATCH,"TC","actor"); resetRows();
        }
    }
    @Test void singleRegistrationProbeTestBoundaryAndMvcParserAgree() throws Exception {
        var servlet = context.getBean(jakarta.servlet.ServletContext.class);
        var registrations = servlet.getFilterRegistrations().values().stream().filter(r -> r.getClassName().contains("ServiceTokenAuthFilter")).toList();
        assertThat(registrations).hasSize(1); assertThat(registrations.getFirst().getName()).isEqualTo("serviceTokenAuthFilter"); assertThat(registrations.getFirst().getUrlPatternMappings()).containsExactly("/v1/*");
        var mappings = context.getBean("requestMappingHandlerMapping",RequestMappingHandlerMapping.class);
        assertThat(mappings.getPatternParser()).isNotNull(); assertThat(mappings.getPatternParser().isCaseSensitive()).isTrue();
        for (String path : List.of("/actuator/health/liveness","/actuator/health/readiness","/__test/absent")) {
            var response = client.send(HttpRequest.newBuilder(uri(path)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isNotEqualTo(401); verifyNoInteractions(observed);
        }
        for (String path : List.of("/v1/query;tag=x","/v1/conversations/id;tag=x/messages","/v1/analyses;tag=x")) {
            var positive = send(path,signed(claims(true)),serviceToken,Map.of("query","본문 재독 확인"));
            assertThat(positive.statusCode()).isEqualTo(202); resetRows();
            rejected(path,signed(claims(false)),serviceToken,Map.of(),DELEGATION_INVALID,"TC",null); resetRows();
        }
        var nullDelegation = claims(false); nullDelegation.put("dlg",null);
        rejected("/v1/conversations;tag=x",signed(nullDelegation),serviceToken,Map.of(),DELEGATION_FORBIDDEN,"TC",null);
    }
    @Test void servletNormalizesDotAliasesBeforeSameJobBoundaryAndRejectsOtherAliases() throws Exception {
        for (String path : List.of("/v1/./query","/v1/x/../query","/v1/%2e/query","/v1/x/%2e%2e/query")) {
            resetRows();
            var response = send(path,signed(claims(false)),serviceToken,Map.of());
            assertThat(response.statusCode()).isBetween(400,499); assertThat(counters.downstream()).isZero();
            if (response.statusCode() == 401) {
                assertThat(jdbc().sql("SELECT decision FROM gw.audit_event").query(String.class).list()).containsExactly("DELEGATION_INVALID");
                verify(observed,times(1)).doFilter(any(),any(),any());
                continue;
            }
            long filtered = mockingDetails(observed).getInvocations().stream().filter(invocation -> invocation.getMethod().getName().equals("doFilter")).count();
            // 컨테이너가 filter 전에 거부한 별칭은 감사 0행입니다. filter 인증을 통과한 뒤 handler가 4xx로 거부한 별칭은
            // AUTH_REJECTED 없이 인입 API_REQUEST 1행(Job 0)만 남고 그 행은 검증된 service·UUID requestId·유효 SDK trace를 가집니다.
            if (filtered == 0) assertThat(count("gw.audit_event")).isZero();
            else { assertThat(filtered).isOne(); assertRequestOrigin(0); }
        }
        for (String path : List.of("/v1/Query","/v1/query/")) {
            resetRows(); var response = send(path,signed(claims(false)),serviceToken,Map.of());
            assertThat(response.statusCode()).isEqualTo(404); assertThat(counters.downstream()).isZero();
        }
    }
    @Test void rawPrecisionTimeBoundariesAndMalformedClaimsUseActualSignedHttp() throws Exception {
        for (String encoding : List.of("UTF-16LE","UTF-16BE","UTF-32LE","UTF-32BE")) {
            rejected("/v1/query",malformed.signBytes(JSONObjectUtils.toJSONString(claims(true)).getBytes(java.nio.charset.Charset.forName(encoding))),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
        }
        rejected("/v1/query",malformed.signBytes(new byte[]{'{',(byte)0xff,'}'}),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
        var success = claims(true); success.put("iat",new BigDecimal("1759999999.123456789")); success.put("exp",new BigDecimal("1760000059.123456789"));
        assertThat(send("/v1/query",signed(success),serviceToken,Map.of()).statusCode()).isEqualTo(202); resetRows();
        success.put("exp",new BigDecimal("1760000059.123456790")); rejected("/v1/query",signed(success),serviceToken,Map.of(),ASSERTION_LIFETIME_INVALID,"TC",null); resetRows();
        clock.now = NOW.plusSeconds(60).minusNanos(1);
        assertThat(send("/v1/query",signed(claims(true)),serviceToken,Map.of()).statusCode()).isEqualTo(202); resetRows();
        for (Instant now : List.of(NOW.plusSeconds(60),NOW.plusSeconds(60).plusNanos(1))) { clock.now = now; rejected("/v1/query",signed(claims(true)),serviceToken,Map.of(),ASSERTION_EXPIRED,"TC",null); resetRows(); }
        clock.now = NOW;
        var future = claims(true); future.put("iat",new BigDecimal("1760000000.000000001")); future.put("exp",new BigDecimal("1760000060.000000001"));
        rejected("/v1/query",signed(future),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
        for (String name : List.of("iat","exp")) {
            var values = claims(true); values.remove(name); rejected("/v1/query",signed(values),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
            for (Object invalid : Arrays.asList(null,"1760000000",true,List.of(),Map.of(),new BigDecimal("1e100"),new BigDecimal("1760000000.0000000001"))) {
                values = claims(true); values.put(name,invalid); rejected("/v1/query",signed(values),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
            }
        }
        for (long exp : List.of(1760000000L,1759999999L)) { var values = claims(true); values.put("exp",exp); rejected("/v1/query",signed(values),serviceToken,Map.of(),ASSERTION_LIFETIME_INVALID,"TC",null); resetRows(); }
        String base = JSONObjectUtils.toJSONString(claims(true));
        for (String invalid : List.of(base.substring(0,base.length()-1) + ",\"extra\":{\"x\":1,\"x\":2}}",base.replace("\"iat\":1760000000","\"iat\":1760000000,\"iat\":1760000000"),base.replace("1760000060","NaN"))) {
            rejected("/v1/query",malformed.sign(invalid),serviceToken,Map.of(),ASSERTION_TIME_INVALID,"TC",null); resetRows();
        }
    }
    @Test void mandatoryClaimAbsentNullWrongTypeAndUntrustedHeaderNeverAdoptActor() throws Exception {
        var reasons = Map.of("iss",ISSUER_INVALID,"aud",AUDIENCE_INVALID,"sys",SYSTEM_SCOPE_MISMATCH,"sub",SUBJECT_INVALID,"scope",READ_SCOPE_INVALID,"perm",PERMISSIONS_INVALID,"pdg",PDG_MISSING,"dlg",DELEGATION_INVALID);
        for (var entry : reasons.entrySet()) {
            var values = claims(true); values.remove(entry.getKey()); rejected("/v1/query",signed(values),serviceToken,Map.of(),entry.getValue(),"TC",null); resetRows();
            for (Object invalid : Arrays.asList(null,Map.of("wrong",true))) {
                values = claims(true); values.put(entry.getKey(),invalid);
                rejected("/v1/query",signed(values),serviceToken,Map.of(),entry.getKey().equals("pdg") ? PDG_INVALID : entry.getValue(),"TC",null); resetRows();
            }
        }
        var networkCalls = new java.util.concurrent.atomic.AtomicInteger();
        var trustServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        trustServer.createContext("/",exchange -> { networkCalls.incrementAndGet(); exchange.sendResponseHeaders(503,-1); exchange.close(); }); trustServer.start();
        try {
            URI untrusted = URI.create("http://127.0.0.1:" + trustServer.getAddress().getPort() + "/untrusted");
            for (String field : List.of("jku","jwk","x5u")) {
                var nullHeader = JWSHeader.parse(com.nimbusds.jose.util.Base64URL.encode("{\"alg\":\"ES256\",\"kid\":\"" + malformed.key.getKeyID() + "\",\"" + field + "\":null}"));
                String nullToken = malformed.sign(JSONObjectUtils.toJSONString(claims(true)),nullHeader,malformed.key);
                assertThat(JWSObject.parse(nullToken).getHeader().getParsedBase64URL().decodeToString()).contains("\"" + field + "\":null");
                rejected("/v1/query",nullToken,serviceToken,Map.of(),UNTRUSTED_KEY_REFERENCE,"TC",null); resetRows();
                var builder = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(malformed.key.getKeyID());
                if (field.equals("jwk")) builder.jwk(malformed.key.toPublicJWK()); else if (field.equals("jku")) builder.jwkURL(untrusted); else builder.x509CertURL(untrusted);
                rejected("/v1/query",malformed.sign(JSONObjectUtils.toJSONString(claims(true)),builder.build(),malformed.key),serviceToken,Map.of(),UNTRUSTED_KEY_REFERENCE,"TC",null); resetRows();
            }
            assertThat(networkCalls.get()).isZero();
        } finally { trustServer.stop(0); }
        for (String kid : List.of(disallowed.key.getKeyID(),otherCurve.getKeyID())) {
            String token = malformed.sign(JSONObjectUtils.toJSONString(claims(true)),new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(kid).build(),malformed.key);
            rejected("/v1/query",token,serviceToken,Map.of(),SIGNATURE_INVALID,"TC",null); resetRows();
        }
        var empty = claims(true); empty.put("perm",List.of()); assertThat(send("/v1/query",signed(empty),serviceToken,Map.of()).statusCode()).isEqualTo(202);
    }

    class IsolatedApplication extends StackContextSupport implements AutoCloseable {
        final Map<String,Object> properties;
        IsolatedApplication(Map<String,Object> properties) throws Exception { this.properties = properties; securityDirectory = Files.createTempDirectory(IngressAuthIT.this.securityDirectory,"owned-http-"); }
        @Override protected WebApplicationType applicationType() { return WebApplicationType.SERVLET; }
        @Override protected Map<String,Object> extraApplicationProperties() { return properties; }
        void open() { start(IngressTerminalFixture.class,ObservationConfiguration.class); }
        com.nori.ai.gateway.persistence.testing.PgTestDatabase ownedDatabase() { return database; }
        org.springframework.context.ConfigurableApplicationContext application() { return context; }
        StubAssertionSigner signing() { return signer; }
        String serviceToken() { return serviceToken; }
        void stopDatabase() { database.close(); database = null; }
        HttpResponse<String> authenticated() throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/v1/query"))
                .timeout(Duration.ofSeconds(10)).header("X-AIGW-Auth-Probe","1").header("Content-Type","application/json").header("Authorization","Bearer " + serviceToken)
                .header("X-Actor-Assertion",signer.sign(IngressAuthIT.this.claims(true),NOW));
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"본문 재독 확인\"}")).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> reject(boolean bodyMismatch) throws Exception {
            var values = IngressAuthIT.this.claims(true);
            String assertion = signer.sign(values,NOW);
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + properties.getOrDefault("server.servlet.context-path","") + "/v1/query"))
                .timeout(Duration.ofSeconds(10)).header("X-AIGW-Auth-Probe","1").header("Content-Type","application/json").header("Authorization","Bearer " + serviceToken);
            if (bodyMismatch) request.header("X-Actor-Assertion",assertion);
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(bodyMismatch ? "{\"system\":\"WM\"}" : "{}")).build(),HttpResponse.BodyHandlers.ofString());
        }
        void assertUnavailable(boolean bodyMismatch,boolean connected) throws Exception {
            var response = reject(bodyMismatch);
            assertThat(response.statusCode()).isEqualTo(503); assertThat(response.headers().firstValue("Retry-After")).contains("1");
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
            var json = JSONObjectUtils.parse(response.body()); assertThat(json.keySet()).containsExactlyInAnyOrder("message","requestId");
            assertThat(json.get("message")).isEqualTo("요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            assertThat(UUID.fromString((String)json.get("requestId"))).isNotNull();
            assertThat(context.getBean(IngressTerminalFixture.Counters.class).downstream()).isZero();
            if (connected) for (String table : List.of("gw.conversation","gw.message","gw.job","gw.audit_event")) assertThat(context.getBean(JdbcClient.class).sql("SELECT count(*) FROM " + table).query(Integer.class).single()).isZero();
            assertThat(response.body().contains(serviceToken)).as("장애 응답 자격 잔류 건수").isFalse();
        }
        @Override public void close() {
            try { super.close(); }
            finally {
                try { Files.deleteIfExists(securityDirectory.resolve("service-tokens.json")); Files.deleteIfExists(securityDirectory.resolve("jwks.json")); Files.deleteIfExists(securityDirectory); }
                catch (java.io.IOException failure) { throw new IllegalStateException("소유 시험 파일 정리에 실패했습니다",failure); }
            }
        }
    }
    @Test void apiRequestAuditStorageFailureReturnsTheFixed503BeforeTheHandler() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of())) {
            isolated.open();
            assertThat(isolated.authenticated().statusCode()).isEqualTo(202);
            try (var connection = isolated.ownedDatabase().connection("postgres"); var statement = connection.createStatement()) { statement.execute("TRUNCATE gw.conversation, gw.audit_event CASCADE"); }
            isolated.application().getBean(IngressTerminalFixture.Counters.class).reset();
            try (var connection = isolated.ownedDatabase().connection(isolated.ownedDatabase().user()); var statement = connection.createStatement()) {
                statement.execute("CREATE FUNCTION gw.reject_api_request_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '시험 요청 감사 거부'; END $$");
                statement.execute("CREATE TRIGGER reject_api_request_audit BEFORE INSERT ON gw.audit_event FOR EACH ROW WHEN (NEW.event_type = 'API_REQUEST') EXECUTE FUNCTION gw.reject_api_request_audit()");
            }
            var failures = isolated.application().getBean(io.micrometer.core.instrument.MeterRegistry.class).get(com.nori.ai.gateway.app.observability.MetricsCatalog.AUDIT_WRITE_FAILURES).counter();
            double before = failures.count();
            // 격리 앱 기동이 logging을 다시 초기화할 수 있으므로 그 활성 컨텍스트 기동 뒤에 이 시험 전용 캡처를 설치합니다.
            var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); captured.start();
            var root = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME); root.addAppender(captured);
            try {
                var response = isolated.authenticated();
                assertThat(response.statusCode()).isEqualTo(503); assertThat(response.headers().firstValue("Retry-After")).contains("1");
                var json = JSONObjectUtils.parse(response.body()); assertThat(json.keySet()).containsExactlyInAnyOrder("message","requestId");
                assertThat(json.get("message")).isEqualTo("요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."); assertThat(UUID.fromString((String)json.get("requestId"))).isNotNull();
                // handler·Job·Source·Cache 0이며 감사 실패는 counter와 고정 코드로 관측됩니다.
                assertThat(isolated.application().getBean(IngressTerminalFixture.Counters.class).downstream()).isZero();
                for (String table : List.of("gw.conversation","gw.message","gw.job","gw.audit_event")) assertThat(isolated.application().getBean(JdbcClient.class).sql("SELECT count(*) FROM " + table).query(Integer.class).single()).as(table).isZero();
                assertThat(failures.count()).isEqualTo(before + 1);
                assertThat(captured.list.stream().filter(event -> event.getLoggerName().equals("com.nori.ai.gateway.app.observability.AuditRecorder")).map(event -> event.getMessage()).toList()).containsExactly("AUDIT_WRITE_FAILED");
                assertThat(captured.list.stream().filter(event -> "AUDIT_WRITE_FAILED".equals(event.getMessage()))).allSatisfy(event -> { assertThat(event.getArgumentArray()).isNullOrEmpty(); assertThat(event.getThrowableProxy()).isNull(); });
            } finally { root.detachAppender(captured); captured.stop(); }
        }
    }
    @Test void realInsertPermissionFailureConverts401And403To503() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of())) {
            isolated.open();
            try (var connection = isolated.ownedDatabase().connection("postgres"); var statement = connection.createStatement()) { statement.execute("REVOKE INSERT ON gw.audit_event FROM " + isolated.ownedDatabase().user()); }
            isolated.assertUnavailable(false,true); isolated.assertUnavailable(true,true);
        }
    }
    @Test void realDeferredCommitFailureConverts401And403To503() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of())) {
            isolated.open();
            try (var connection = isolated.ownedDatabase().connection(isolated.ownedDatabase().user()); var statement = connection.createStatement()) {
                statement.execute("CREATE FUNCTION gw.reject_auth_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '시험 감사 커밋 실패'; END $$");
                statement.execute("CREATE CONSTRAINT TRIGGER reject_auth_commit AFTER INSERT ON gw.audit_event DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION gw.reject_auth_commit()");
            }
            isolated.assertUnavailable(false,true); isolated.assertUnavailable(true,true);
        }
    }
    @Test void realLockTimeoutConverts401And403To503() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of("spring.datasource.hikari.connection-init-sql","SET lock_timeout = '200ms'"))) {
            isolated.open();
            try (var lock = isolated.ownedDatabase().connection("postgres"); var statement = lock.createStatement()) {
                lock.setAutoCommit(false); statement.execute("LOCK TABLE gw.audit_event IN SHARE MODE");
                try { isolated.assertUnavailable(false,false); isolated.assertUnavailable(true,false); }
                finally { lock.rollback(); }
            }
            assertThat(isolated.application().getBean(JdbcClient.class).sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isZero();
        }
    }
    @Test void realConnectionFailureConverts401And403To503() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of("spring.datasource.hikari.connection-timeout",1000))) {
            isolated.open();
            assertThat(isolated.application().getBean(JdbcClient.class).sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isZero();
            isolated.stopDatabase();
            isolated.assertUnavailable(false,false); isolated.assertUnavailable(true,false);
        }
    }
    @Test void separateServletContextPathUsesSameMatrixJobRules() throws Exception {
        try (var isolated = new IsolatedApplication(Map.of("server.servlet.context-path","/gateway"))) {
            isolated.open();
            String token = isolated.signing().sign(claims(false),NOW);
            for (String path : List.of("/v1/query;tag=x","/v1/conversations/id;tag=x/messages","/v1/analyses;tag=x")) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + isolated.application().getEnvironment().getProperty("local.server.port") + "/gateway" + path))
                    .header("Authorization","Bearer " + isolated.serviceToken()).header("X-Actor-Assertion",token).header("X-AIGW-Auth-Probe","1").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401); assertThat(isolated.application().getBean(IngressTerminalFixture.Counters.class).downstream()).isZero();
            }
            var spy = (ServiceTokenAuthFilter)isolated.application().getBean("serviceTokenAuthFilterRegistration",FilterRegistrationBean.class).getFilter();
            verify(spy,times(3)).doFilter(any(),any(),any());
            assertThat(isolated.application().getBean(JdbcClient.class).sql("SELECT decision FROM gw.audit_event").query(String.class).list()).containsOnly("DELEGATION_INVALID").hasSize(3);
        }
    }
}
