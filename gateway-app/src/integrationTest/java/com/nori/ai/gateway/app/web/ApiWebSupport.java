package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.job.P09QueueSupport;
import java.net.http.*;
import java.time.Duration;

abstract class ApiWebSupport extends P09QueueSupport {
    @Override protected java.util.Map<String,Object> extraApplicationProperties() {
        var properties=new java.util.HashMap<>(super.extraApplicationProperties());properties.put("server.servlet.context-path","/api-test");return properties;
    }
    @Override protected java.net.URI uri(String path) {return super.uri("/api-test"+path);}
    protected HttpResponse<String> request(String method,String path,String actor,Object body,String key,boolean job) throws Exception {
        var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+serviceToken)
            .header("X-Actor-Assertion",signer.sign(claims(actor,job?dlg():null),clock().instant())).header("Content-Type","application/json");
        if(key!=null)builder.header("Idempotency-Key",key);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
}
