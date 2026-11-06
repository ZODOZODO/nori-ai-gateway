package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.tcmock.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MockDelegationHookIT extends P09QueueSupport {
    @Test void actualHookRegistersDuplicateRevokesAndExpiresAtThreeHundredSeconds() throws Exception {
        String delegation=dlg();var body=json.writeValueAsString(Map.of("sub","actor","dlg",delegation));String endpoint="/__test/tcmock/delegations";
        assertThat(hook(endpoint,"POST",body,null).statusCode()).isEqualTo(404);
        assertThat(hook(endpoint,"POST",body,hookToken).statusCode()).isEqualTo(204);
        assertThat(hook(endpoint,"POST",body,hookToken).statusCode()).isEqualTo(409);
        assertThat(hook(endpoint,"POST",json.writeValueAsString(Map.of("sub","actor","dlg",dlg(),"ttl",600)),hookToken).statusCode()).isEqualTo(400);
        var call=new TcSourceAuthorizationPort.DelegatedCall("actor",delegation);var api=context.getBean(TcSourceAuthorizationPort.class);
        assertThat(api.authorize(call,Set.of())).isInstanceOf(TcSourceAuthorizationPort.Allowed.class);
        clock().advance(299);assertThat(api.authorize(call,Set.of())).isInstanceOf(TcSourceAuthorizationPort.Allowed.class);
        clock().advance(1);assertThat(api.authorize(call,Set.of())).isInstanceOf(TcSourceAuthorizationPort.DelegationInvalid.class);
        String revoked=registeredDlg("actor");assertThat(hook(endpoint+"/"+MockDelegationRegistry.hash(revoked),"DELETE",null,hookToken).statusCode()).isEqualTo(204);
        assertThat(api.authorize(new TcSourceAuthorizationPort.DelegatedCall("actor",revoked),Set.of("EQP_READ"))).isInstanceOf(TcSourceAuthorizationPort.DelegationInvalid.class);
    }
    @Test void scenarioAndCountersResetOnlyWhenIdleWithoutRevivingDelegations() throws Exception {
        String delegation=registeredDlg("actor");context.getBean(MockDelegationRegistry.class).revoke(MockDelegationRegistry.hash(delegation));
        assertThat(hook("/__test/tcmock/scenario","POST","{\"id\":\"logout\"}",hookToken).statusCode()).isEqualTo(204);
        assertThat(hook("/__test/tcmock/scenario","POST","{\"id\":\"unknown\",\"url\":\"rejected\"}",hookToken).statusCode()).isEqualTo(400);
        assertThat(hook("/__test/reset","POST","{}",hookToken).statusCode()).isEqualTo(204);
        assertThat(context.getBean(MockScenarioStore.class).current().id()).isEqualTo("default");
        assertThat(context.getBean(MockDelegationRegistry.class).validate(new TcSourceAuthorizationPort.DelegatedCall("actor",delegation))).isEqualTo(MockDelegationRegistry.Validity.INVALID);
        assertThat(hook("/__test/tcmock/calls","GET",null,hookToken).statusCode()).isEqualTo(200);
    }
}
