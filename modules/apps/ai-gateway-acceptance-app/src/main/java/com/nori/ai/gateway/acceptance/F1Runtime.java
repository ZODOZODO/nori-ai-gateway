package com.nori.ai.gateway.acceptance;

import com.nimbusds.jose.jwk.*;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.acceptance.loki.LokiDirectQuery;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.opentest4j.TestAbortedException;

/** 배포 입력만 소비합니다. 로컬 fixture나 임시 자격으로 fallback하지 않습니다. */
public final class F1Runtime {
    public record Clients(GatewayClient gateway,HookClient hooks,OracleQueries oracle,F1Assertions assertions,String token) { @Override public String toString(){return "F1Clients[보호됨]";} }
    private F1Runtime() {}
    public static AcceptanceConfig config(){var properties=new HashMap<String,String>();System.getProperties().stringPropertyNames().stream().filter(key->key.startsWith("aigw.")).forEach(key->properties.put(key,System.getProperty(key)));return new AcceptanceConfig(System.getenv(),properties);}
    public static Clients clients(AcceptanceConfig config,boolean database) {
        if(!config.properties().getOrDefault("aigw.acceptance.signer","").equals("secret-file"))throw new TestAbortedException("VERIFY_CREDENTIALS_MISSING");
        try {
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();URI base=URI.create(required(config,"AIGW_GATEWAY_BASE_URL"));
            if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null)throw new IllegalArgumentException();
            var hooks=new HookClient(http,base,config.secret("AIGW_HOOK_TOKEN_FILE"));String token=config.secret("AIGW_SERVICE_TOKEN_FILE");
            Path key=Path.of(required(config,"AIGW_SIGNER_PRIVATE_JWK_FILE"));var signer=StubAssertionSigner.fromPrivateJwkFile(key);
            var gateway=new GatewayClient(http,base,token,signer,hooks);var assertions=new F1Assertions((ECKey)JWK.parse(Files.readString(key)));
            OracleQueries oracle=null;
            if(database){oracle=new OracleQueries(OracleQueries.postgres(required(config,"AIGW_DB_HOST"),config.secret("AIGW_DB_PASSWORD_FILE")));oracle.verifyDatabaseBoundary();}
            return new Clients(gateway,hooks,oracle,assertions,token);
        }catch(OracleQueries.BoundaryViolation e){throw new AssertionError("F1_DATABASE_BOUNDARY");}
        catch(Exception e){throw new TestAbortedException("VERIFY_CREDENTIALS_MISSING");}
    }
    public static Optional<F1SecurityChecks.RsaAssertion> rsa(AcceptanceConfig config) {
        String key=config.env().get("AIGW_RSA_SIGNER_PRIVATE_JWK_FILE"),trust=config.env().get("AIGW_RSA_TRUSTED_PUBLIC_JWKS_FILE");
        if(key==null&&trust==null)return Optional.empty();
        try {return Optional.of(F1SecurityChecks.trustedRsa((RSAKey)JWK.parse(Files.readString(Path.of(key))),JWKSet.parse(Files.readString(Path.of(trust)))));}
        catch(Exception e){throw new TestAbortedException("TRUSTED_RSA_FIXTURE_INVALID");}
    }
    public static void run(String leaf){var config=config();var clients=clients(config,true);Instant start=Instant.now().minusSeconds(1);
        F1SecurityChecks.ResidualCheck residual=sentinels->{String url=config.env().get("AIGW_LOKI_READ_URL");if(url==null||url.isBlank())throw new TestAbortedException("LOKI_READBACK_MISSING");String host=URI.create(required(config,"AIGW_GATEWAY_BASE_URL")).getHost().split("\\.")[0];
            var observation=new GatewayLogReadback(HttpClient.newHttpClient(),URI.create(url)).count(host,start,Instant.now().plusNanos(1),sentinels);
            if(observation.limited()||observation.lines()==0)throw new TestAbortedException("LOKI_READBACK_INCOMPLETE");F1SecurityChecks.require(observation.sentinelLines()==0,"F1_LOG_RESIDUE");};
        if(leaf.startsWith("ISO-04-"))new F1SecurityChecks(clients.gateway(),clients.hooks(),clients.oracle(),clients.assertions(),clients.token(),rsa(config),residual).run(leaf);
        else {String url=config.env().get("AIGW_LOKI_READ_URL");Optional<LokiDirectQuery> loki=url==null||url.isBlank()?Optional.empty():Optional.of(new LokiDirectQuery(new SourceHttpClient(URI.create(url),LokiSourcePolicy.SOURCE_REF,SourceObservability.noop())));new F1JobChecks(clients.gateway(),clients.hooks(),clients.oracle(),loki,residual).run(leaf);}
    }
    private static String required(AcceptanceConfig config,String name){String value=config.env().get(name);if(value==null||value.isBlank())throw new IllegalArgumentException("F1_INPUT_MISSING");return value;}
}
