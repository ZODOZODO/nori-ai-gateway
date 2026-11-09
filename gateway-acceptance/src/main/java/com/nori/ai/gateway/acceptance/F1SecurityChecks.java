package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import java.time.Instant;
import java.util.*;
import org.opentest4j.TestAbortedException;

/** 인입 음성은 실제 HTTP·감사·Job·Mock 후속 호출을 함께 검사합니다. 실패 메시지에 본문/서명을 넣지 않습니다. */
public final class F1SecurityChecks {
    public record Input(String path, String assertion, String token, Map<String,Object> body, String reason, int status) { @Override public String toString(){return "F1SecurityInput[보호됨]";} }
    @FunctionalInterface public interface RsaAssertion { String sign(Map<String,Object> claims); }
    @FunctionalInterface public interface ResidualCheck { void verify(Set<String> sentinels); }
    private final GatewayClient gateway;
    private final HookClient hooks;
    private final OracleQueries oracle;
    private final F1Assertions signer;
    private final String token;
    private final Optional<RsaAssertion> rsa;
    private final ResidualCheck residual;
    public F1SecurityChecks(GatewayClient gateway, HookClient hooks, OracleQueries oracle, F1Assertions signer, String token, Optional<RsaAssertion> rsa, ResidualCheck residual) {
        this.gateway=gateway; this.hooks=hooks; this.oracle=oracle; this.signer=signer; this.token=token; this.rsa=rsa; this.residual=residual;
    }
    public void run(String leaf) {
        require(oracle.nonTerminalJobCount()==0, "ISOLATION_NOT_IDLE"); hooks.reset();
        Instant now=hooks.clockNow(); String dlg=gateway.newDlg();
        Input input=prepare(leaf,now,dlg);
        long before=oracle.jobCount(); JsonNode calls=hooks.get("/__test/tcmock/calls");
        String path=input.path(); String method="POST";
        if(leaf.equals("ISO-04-JWS-DLG-EXTRA")) {
            var valid=signer.claims(now,dlg);valid.remove("dlg");String assertion=signer.sign(valid);
            var created=gateway.request("/v1/conversations",assertion,token,"{}");require(created.statusCode()==201,"F1_CONVERSATION_CREATE");
            String id=parse(created.body()).path("id").asText();require(id.matches("[a-f0-9-]{36}"),"F1_CONVERSATION_ID");
            path="/v1/conversations/"+id;method="GET";
            require(gateway.request(method,path,assertion,token,"{}").statusCode()==200,"F1_NON_JOB_POSITIVE_CONTROL");
            calls=hooks.get("/__test/tcmock/calls");
        }
        var response=gateway.request(method,path,input.assertion(),input.token(),json(input.body()));
        require(response.statusCode()==input.status(), "F1_AUTH_HTTP_STATUS");
        JsonNode body=parse(response.body());
        require(body.isObject()&&body.size()==2&&body.hasNonNull("message")&&body.hasNonNull("requestId"), "F1_AUTH_RESPONSE_SHAPE");
        String requestId=body.path("requestId").asText();
        require(requestId.matches("[a-f0-9-]{36}"), "F1_AUTH_REQUEST_ID");
        require(oracle.authRejectionCount(requestId,input.reason(),input.status())==1, "F1_AUTH_AUDIT_REASON");
        require(oracle.jobCount()==before, "F1_AUTH_CREATED_JOB");
        require(hooks.get("/__test/tcmock/calls").equals(calls), "F1_AUTH_DOWNSTREAM_CALL");
        var sentinels=new HashSet<String>(Set.of(dlg,token));
        if(input.token()!=null)sentinels.add(input.token());
        if(input.assertion()!=null)sentinels.add(input.assertion());
        require(ResidueScanner.scan(Map.of("response",response.body()),sentinels).matchCount()==0, "F1_AUTH_RESPONSE_RESIDUE");
        require(oracle.residueScan(sentinels).matchCount()==0,"F1_AUTH_STORED_RESIDUE");
        residual.verify(sentinels);
    }
    public Input prepare(String leaf, Instant now, String dlg) {
        var claims=signer.claims(now,dlg); var body=new LinkedHashMap<String,Object>();
        String path="/v1/query", reason, service=token; int status=401;
        String suffix=leaf.substring("ISO-04-".length());
        switch(suffix) {
            case "SYSTEM" -> { body.put("system","WM"); reason="SYSTEM_SCOPE_MISMATCH"; status=403; }
            case "TARGET" -> { body.put("targetSystem","WM"); reason="SYSTEM_SCOPE_MISMATCH"; status=403; }
            case "TOKEN-MISSING" -> { service=null; reason="SERVICE_TOKEN_MISSING"; }
            case "TOKEN-INVALID" -> { service="invalid-"+UUID.randomUUID(); reason="SERVICE_TOKEN_INVALID"; }
            case "JWS-ABSENT" -> { return new Input(path,null,service,body,"ACTOR_ASSERTION_MISSING",401); }
            case "JWS-SIG" -> { return new Input(path,signer.invalidSignature(claims),service,body,"SIGNATURE_INVALID",401); }
            case "JWS-KID" -> { return new Input(path,signer.unknownKid(claims),service,body,"UNKNOWN_KID",401); }
            case "JWS-ALG" -> { return new Input(path,rsa.orElseThrow(()->new TestAbortedException("TRUSTED_RSA_FIXTURE_MISSING")).sign(claims),service,body,"ALGORITHM_NOT_ALLOWED",401); }
            case "JWS-ISS" -> { claims.put("iss","other"); reason="ISSUER_INVALID"; }
            case "JWS-AUD" -> { claims.put("aud","other"); reason="AUDIENCE_INVALID"; }
            case "JWS-EXP" -> { claims.put("iat",F1Assertions.date(now.minusSeconds(60))); claims.put("exp",F1Assertions.date(now)); reason="ASSERTION_EXPIRED"; }
            case "JWS-LIFE" -> { claims.put("exp",F1Assertions.date(now.plusSeconds(61))); reason="ASSERTION_LIFETIME_INVALID"; }
            case "JWS-SYS" -> { claims.put("sys","WM"); reason="SYSTEM_SCOPE_MISMATCH"; status=403; }
            case "JWS-SUB" -> { claims.remove("sub"); reason="SUBJECT_INVALID"; }
            case "JWS-SCOPE" -> { claims.put("scope","WRITE"); reason="READ_SCOPE_INVALID"; }
            case "JWS-PERM" -> { claims.remove("perm"); reason="PERMISSIONS_INVALID"; }
            case "JWS-PDG" -> { claims.put("pdg","A".repeat(64)); reason="PDG_INVALID"; }
            case "JWS-PDG-MISSING" -> { claims.remove("pdg"); reason="PDG_MISSING"; }
            case "JWS-DLG" -> { claims.remove("dlg"); reason="DELEGATION_INVALID"; }
            case "JWS-DLG-EXTRA" -> { path="/v1/conversations"; reason="DELEGATION_FORBIDDEN"; }
            default -> throw new IllegalArgumentException("F1_AUTH_LEAF_INVALID");
        }
        return new Input(path,signer.sign(claims),service,Map.copyOf(body),reason,status);
    }
    /** 로컬 신뢰 fixture는 정상 공개키와 실제 서명 양성부터 확인합니다. 운영 trust 입력 부재를 우회하지 않습니다. */
    public static RsaAssertion trustedRsa(RSAKey privateKey, JWKSet trusted) {
        JWK publicKey=trusted.getKeyByKeyId(privateKey.getKeyID());
        require(publicKey instanceof RSAKey&&!publicKey.isPrivate()&&publicKey.toPublicJWK().equals(privateKey.toPublicJWK()),"F1_RSA_NOT_TRUSTED");
        return claims->{
            try { var object=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(privateKey.getKeyID()).build(),new Payload(JSONObjectUtils.toJSONString(claims))); object.sign(new RSASSASigner(privateKey)); require(object.verify(new RSASSAVerifier((RSAKey)publicKey)),"F1_RSA_SIGNATURE_INVALID"); return object.serialize(); }
            catch(JOSEException e){throw new IllegalStateException("F1_RSA_SIGNING_FAILED");}
        };
    }
    static void require(boolean condition,String code){if(!condition)throw new AssertionError(code);}
    static JsonNode parse(String value){try{return CanonicalJson.MAPPER.readTree(value);}catch(java.io.IOException e){throw new AssertionError("F1_RESPONSE_INVALID");}}
    static String json(Object value){try{return CanonicalJson.MAPPER.writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalStateException("F1_REQUEST_INVALID");}}
}
