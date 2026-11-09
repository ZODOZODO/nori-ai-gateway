package com.nori.ai.gateway.acceptance;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
import static org.assertj.core.api.Assertions.*;

class F1SuiteTest {
    @Test void everyIngressFixtureChangesOnlyItsNamedBoundaryAndUsesActualSignatures()throws Exception {
        var key=new ECKeyGenerator(Curve.P_256).keyID("test-f1-matrix").generate();var rsa=new RSAKeyGenerator(2048).keyID("test-f1-rsa-matrix").generate();
        try(var fixture=new F1LocalFixture()){
            var checks=new F1SecurityChecks(fixture.gateway,fixture.hooks,fixture.oracle,new F1Assertions(key),fixture.token,Optional.of(F1SecurityChecks.trustedRsa(rsa,new JWKSet(rsa.toPublicJWK()))),s->{});
            var leaves=java.util.Arrays.stream(com.nori.ai.gateway.acceptance.suite.F1Suite.class.getDeclaredMethods()).flatMap(method->java.util.Arrays.stream(method.getAnnotationsByType(org.junit.jupiter.api.Tag.class))).map(org.junit.jupiter.api.Tag::value).filter(tag->tag.startsWith("ISO-04-")).toList();assertThat(leaves).hasSize(20);
            for(String leaf:leaves){var input=checks.prepare(leaf,F1JobChecks.MOCK_NOW,"d".repeat(32));
                if(leaf.equals("ISO-04-JWS-ABSENT")){assertThat(input.assertion()).isNull();continue;}
                var assertion=JWSObject.parse(input.assertion());if(leaf.equals("ISO-04-JWS-ALG"))assertThat(assertion.verify(new RSASSAVerifier(rsa.toPublicJWK()))).isTrue();else assertThat(assertion.verify(new ECDSAVerifier(key.toPublicJWK()))).isEqualTo(!leaf.equals("ISO-04-JWS-SIG"));
                var claims=assertion.getPayload().toJSONObject();if(!leaf.equals("ISO-04-JWS-SUB"))assertThat(claims.get("sub")).isEqualTo("f1-acceptance");if(!leaf.equals("ISO-04-JWS-PERM"))assertThat(claims.get("perm")).isEqualTo(F1Fixture.PERMISSIONS);
                if(!leaf.equals("ISO-04-JWS-SYS"))assertThat(claims.get("sys")).isEqualTo("TC");if(!leaf.equals("ISO-04-JWS-ISS"))assertThat(claims.get("iss")).isEqualTo("tc-ui-backend");if(!leaf.equals("ISO-04-JWS-AUD"))assertThat(claims.get("aud")).isEqualTo("ai-gateway");
            }
        }
    }
    @Test void productionLauncherDiscoversExactlyTheCatalogThirtyFiveAtomicLeaves()throws Exception {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(selectPackage(AcceptanceMain.SUITE_PACKAGE)).filters(TagFilter.includeTags("suite-F1")).build();var plan=LauncherFactory.create().discover(request);
        var tests=plan.getRoots().stream().flatMap(root->plan.getDescendants(root).stream()).filter(org.junit.platform.launcher.TestIdentifier::isTest).toList();assertThat(tests).hasSize(35);
        var expected=CanonicalJson.MAPPER.readTree(Files.readString(Path.of(System.getProperty("aigw.rootDir"),"gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json"))).path("producerIndex").path("F1");var actual=new TreeSet<String>();
        for(var test:tests){var tags=test.getTags().stream().map(org.junit.platform.engine.TestTag::getName).toList();assertThat(tags).contains("suite-F1","stage-F1","label-MOCK_API");var leaves=tags.stream().filter(tag->tag.matches("(AC|ISO)-[0-9]{2}.*")).toList();assertThat(leaves).hasSize(1);assertThat(actual.add(leaves.getFirst())).isTrue();}
        assertThat(actual).containsExactlyInAnyOrderElementsOf(java.util.stream.StreamSupport.stream(expected.spliterator(),false).map(node->node.asText()).toList());
    }
    @Test void loopbackJobCompletenessChecksTargetStateAndKeepsWholeResponseSuccess()throws Exception {
        try(var fixture=new F1LocalFixture()){fixture.authReason=null;fixture.checks().run("AC-46-EMPTY");fixture.checks().run("AC-46-HIT");fixture.checks().run("AC-46-TRIM");assertThat(F1RubricTargets.snapshot()).hasSize(2).allSatisfy(target->assertThat(target.answerSha256()).isEqualTo(CanonicalJson.sha256(new byte[0])));}
    }
    @Test void missingWarningIsFailureAndUnobservedSourceCannotBecomePass()throws Exception {
        try(var fixture=new F1LocalFixture()){fixture.authReason=null;fixture.warning=false;assertThatThrownBy(()->fixture.checks().run("AC-46-HIT")).isInstanceOf(AssertionError.class).hasMessage("F1_COMM_WARNING_MISSING");assertThatThrownBy(()->fixture.checks().run("AC-36")).isInstanceOf(org.opentest4j.TestAbortedException.class).hasMessage("MAX_WINDOW_UNDEFINED");}
    }
    @Test void ingressMissingPermissionUsesActualHttpAndAuditAndDoesNotCreateJobs()throws Exception {
        try(var fixture=new F1LocalFixture()){new F1SecurityChecks(fixture.gateway,fixture.hooks,fixture.oracle,fixture.signer,fixture.token,Optional.empty(),sentinels->assertThat(sentinels).contains(fixture.token)).run("ISO-04-JWS-PERM");assertThat(fixture.jobs).isZero();assertThat(fixture.selectedSql).anyMatch(sql->sql.contains("AUTH_REJECTED"));}
    }
    @Test void malformedCredentialCasesPreserveSingleViolationAndNanoLifetimeBoundary()throws Exception {
        try(var fixture=new F1LocalFixture()){
            var checks=new F1SecurityChecks(fixture.gateway,fixture.hooks,fixture.oracle,fixture.signer,fixture.token,Optional.empty(),s->{});String dlg="d".repeat(32);Instant now=F1JobChecks.MOCK_NOW;
            assertThat(JWSObject.parse(checks.prepare("ISO-04-JWS-SUB",now,dlg).assertion()).getPayload().toJSONObject()).doesNotContainKey("sub");assertThat(JWSObject.parse(checks.prepare("ISO-04-JWS-PERM",now,dlg).assertion()).getPayload().toJSONObject()).doesNotContainKey("perm");
            var claims=fixture.signer.claims(now,dlg);claims.put("exp",F1Assertions.date(now.plusSeconds(60).plusNanos(1)));assertThat(JWSObject.parse(fixture.signer.sign(claims)).getPayload().toString()).contains(F1Assertions.date(now.plusSeconds(60).plusNanos(1)).toPlainString());
            assertThatThrownBy(()->checks.prepare("ISO-04-JWS-ALG",now,dlg)).isInstanceOf(org.opentest4j.TestAbortedException.class).hasMessage("TRUSTED_RSA_FIXTURE_MISSING");
            RSAKey rsa=new RSAKeyGenerator(2048).keyID("test-rsa-f1").generate();String assertion=F1SecurityChecks.trustedRsa(rsa,new JWKSet(rsa.toPublicJWK())).sign(fixture.signer.claims(now,dlg));assertThat(JWSObject.parse(assertion).verify(new RSASSAVerifier(rsa.toPublicJWK()))).isTrue();
            assertThatThrownBy(()->F1SecurityChecks.trustedRsa(rsa,new JWKSet(new RSAKeyGenerator(2048).keyID("test-other").generate().toPublicJWK()))).isInstanceOf(AssertionError.class).hasMessage("F1_RSA_NOT_TRUSTED");
        }
    }
    @Test void rubricFixtureClaimsCannotBeInventedOrReboundToAnotherLeaf() {
        assertThat(F1Fixture.claimsFor(new RubricViewer.Target("AC-46-HIT","f1-retention-empty","job","a".repeat(64)))).isEmpty();assertThatThrownBy(()->F1RubricTargets.add("AC-41","invented","job",new byte[0])).hasMessage("RUBRIC_TARGET_INVALID");
    }
}
