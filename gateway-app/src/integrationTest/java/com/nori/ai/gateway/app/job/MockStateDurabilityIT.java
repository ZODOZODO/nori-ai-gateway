package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.tcmock.MockDelegationRegistry;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MockStateDurabilityIT extends WorkerRestartProcessSupport {
    @Test void halt137PreservesAbsoluteExpiryRevokeAndHashOnlyStateBeforeRecoveredWork() throws Exception {
        String valid=UUID.randomUUID().toString().replace("-",""),revoked=UUID.randomUUID().toString().replace("-","");harness.watch(valid);harness.watch(revoked);
        for(String delegation:List.of(valid,revoked))assertThat(hook("/__test/tcmock/delegations",Map.of("sub","actor","dlg",delegation)).statusCode()).isEqualTo(204);
        assertThat(send("/__test/tcmock/delegations/"+MockDelegationRegistry.hash(revoked),"DELETE",null,null,null,false).statusCode()).isEqualTo(204);
        var before=json.readTree(Files.readString(state.resolve("delegations.json")));String expiry=before.get(MockDelegationRegistry.hash(valid)).get("expiresAt").asString();
        assertThat(hook("/__test/tcmock/scenario",Map.of("id","comm-trim")).statusCode()).isEqualTo(204);
        haltWithRunningAndQueued();launch(NOW.plusSeconds(1));
        assertThat(json.readTree(send("/__test/tcmock/scenario","GET",null,null,null,false).body()).get("id").asString()).isEqualTo("comm-trim");
        var after=json.readTree(Files.readString(state.resolve("delegations.json")));assertThat(after.get(MockDelegationRegistry.hash(valid)).get("expiresAt").asString()).isEqualTo(expiry);assertThat(after.get(MockDelegationRegistry.hash(revoked)).get("revoked").asBoolean()).isTrue();
        assertThat(submitExisting("valid",valid)).isEqualTo("SUCCEEDED");assertThat(submitExisting("revoked",revoked)).isEqualTo("FAILED");
        try(var files=Files.walk(state)){for(Path file:files.filter(Files::isRegularFile).toList()){String text=Files.readString(file);assertThat(text.contains(valid)||text.contains(revoked)).isFalse();}}
    }
    private String submitExisting(String key,String delegation) throws Exception {
        arm("stored-"+key,null,List.of());Instant at=Instant.parse(json.readTree(send("/__test/clock","GET",null,null,null,false).body()).get("now").asString());
        var claims=new HashMap<String,Object>(Map.of("iss","tc-ui-backend","aud","ai-gateway","sys","TC","sub","actor","scope","READ","perm",List.of("EQP_READ"),"pdg",P09TestPorts.PDG,"dlg",delegation));String assertion=signer.sign(claims,at);harness.watch(assertion);
        var request=HttpRequest.newBuilder(harness.uri("/v1/query")).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",assertion).header("Idempotency-Key","stored-"+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query","보존 위임 시험","context",Map.of("timezone","UTC","referenceTime",at.toString()),"resourceRefs",List.of())))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(202);String id=json.readTree(response.body()).get("jobId").asString();await(()->Set.of("SUCCEEDED","FAILED").contains(state(id)));return state(id);
    }
}
