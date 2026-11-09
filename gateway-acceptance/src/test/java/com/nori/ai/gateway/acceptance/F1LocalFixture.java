package com.nori.ai.gateway.acceptance;

import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.Curve;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;

/** local 모의 서버와 JDBC 응답은 운영 증거 저장소에 쓰지 않습니다. */
final class F1LocalFixture implements AutoCloseable {
    final HttpServer server;final GatewayClient gateway;final HookClient hooks;final OracleQueries oracle;
    final F1Assertions signer;final String token="f1-local-service-token-sentinel";
    String scenario="default",seam="",capability="",returnedJob=null;long jobs;boolean warning=true;String authReason="PERMISSIONS_INVALID";int authStatus=401;
    JsonNode lastRequest;final Map<String,Object> calls=new TreeMap<>();final Map<String,List<Object[]>> toolRows=new HashMap<>();
    final List<String> selectedSql=new ArrayList<>();final Instant now=F1JobChecks.MOCK_NOW;
    F1LocalFixture()throws Exception {
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);server.createContext("/",this::handle);server.start();
        URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var http=HttpClient.newHttpClient();hooks=new HookClient(http,base,"f1-hook");gateway=new GatewayClient(http,base,token,StubAssertionSigner.ephemeral("test-eph-f1-local"),hooks);signer=new F1Assertions(new ECKeyGenerator(Curve.P_256).keyID("test-f1-local").generate());oracle=new OracleQueries(this::connect);
    }
    F1JobChecks checks(){return new F1JobChecks(gateway,hooks,oracle,Optional.empty(),sentinels->{});}
    private void handle(HttpExchange exchange)throws java.io.IOException {
        try(exchange){String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();byte[] input=exchange.getRequestBody().readAllBytes();if(input.length>0)lastRequest=CanonicalJson.MAPPER.readTree(input);
            Object result=Map.of();int status=200;
            if(path.equals("/__test/clock"))result=Map.of("now",now.toString());
            else if(path.equals("/__test/reset")){calls.clear();toolRows.clear();scenario="default";seam="";status=204;}
            else if(path.equals("/__test/tcmock/calls"))result=calls;
            else if(path.equals("/__test/tcmock/scenario")){if(method.equals("POST")){scenario=lastRequest.path("id").asText();status=204;}else result=Map.of("id",scenario);}
            else if(path.equals("/__test/arm")){seam=lastRequest.path("scenario").path("seam").path("kind").asText();capability=lastRequest.path("scenario").path("seam").path("capabilityId").asText();status=204;}
            else if(path.startsWith("/__test/"))status=204;
            else if(path.equals("/v1/conversations")&&method.equals("POST")){status=201;result=Map.of("id","11111111-1111-1111-1111-111111111111");}
            else if(path.startsWith("/v1/conversations/")&&method.equals("GET")&&!exchange.getRequestHeaders().getFirst("X-Actor-Assertion").contains("dummy")){result=Map.of("conversation",Map.of("id","11111111-1111-1111-1111-111111111111"),"messages",List.of());}
            else if(path.equals("/v1/query")) {
                if(authReason!=null){status=authStatus;result=Map.of("message","인입 거부","requestId","11111111-1111-1111-1111-111111111111");}
                else {jobs++;createTools();status=202;result=Map.of("jobId","job-"+jobs);}
            }else if(path.startsWith("/v1/analyses/")){result=response(path.substring(path.lastIndexOf('/')+1));}
            byte[] body=CanonicalJson.MAPPER.writeValueAsBytes(result);exchange.sendResponseHeaders(status,status==204?-1:body.length);if(status!=204)exchange.getResponseBody().write(body);
        }
    }
    private void createTools(){String status=scenario.equals("empty")?"EMPTY":"SUCCESS";calls.put("getEqpSnapshot",seam.isEmpty()?1:0);
        var sources=new ArrayList<Map<String,Object>>();for(String source:List.of("gateway","business")){
            var row=new LinkedHashMap<String,Object>();row.put("source",source);row.put("status",status);row.put("items",scenario.equals("empty")?List.of():List.of(Map.of("occurredAt",now.minusSeconds(100).toString())));row.put("warnings",warning?List.of("RETENTION_UNVERIFIED"):List.of());
            row.put("completeness",Map.of("count",5000,"maxEntriesPerKey",5000,"oldestRetainedAt",now.minusSeconds(600).toString(),"retentionHours",1,"appliedValuesVerified",true,"otherDeletionPathsExcluded",true));
            row.put("retentionMeta",Map.of("missingRanges",List.of(),"unverifiedRanges",scenario.equals("comm-trim")&&source.equals("gateway")?List.of(Map.of("startAt",now.minusSeconds(1800).plusNanos(1).toString(),"endAt",now.minusSeconds(600).toString())):List.of()));sources.add(row);
        }
        toolRows.put("tc.commLogs.search",List.<Object[]>of(new Object[]{F1SecurityChecks.json(Map.of("status",status,"result",Map.of("blocks",List.of(Map.of("content",Map.of("items",sources))),"warnings",List.of("RETENTION_UNVERIFIED"))))}));
        if(!seam.isEmpty())toolRows.clear();
    }
    private Object response(String job){var payload=new LinkedHashMap<String,Object>();payload.put("status",seam.isEmpty()?"SUCCESS":"UNSUPPORTED");payload.put("blocks",List.of());if(!seam.isEmpty())payload.put("validationTrace",List.of(Map.of("step",6,"passed",false,"reason",seam)));return Map.of("jobId",returnedJob==null?job:returnedJob,"jobStatus","SUCCEEDED","result",Map.of("status",payload.get("status"),"payload",payload));}
    private Connection connect(String database)throws SQLException {
        if(!database.equals("ai_gateway"))throw new SQLException("hba","28000");
        return (Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("prepareStatement")){String sql=(String)a[0];selectedSql.add(sql);var args=new ArrayList<Object>();return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(sp,sm,sa)->{
                if(sm.getName().equals("setObject")){args.add(sa[1]);return null;}
                if(sm.getName().equals("executeQuery")){List<Object[]> rows;
                    if(sql.startsWith("SELECT payload"))rows=toolRows.getOrDefault(args.get(1).toString(),List.of());
                    else if(sql.contains("row_to_json")||sql.contains("verified_oldest_at"))rows=List.of();
                    else rows=List.<Object[]>of(new Object[]{sql.contains("status IN")?0L:sql.equals("SELECT count(*) FROM gw.job")?jobs:1L});
                    var iterator=rows.iterator();return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},new InvocationHandler(){Object[] row;public Object invoke(Object rp,Method rm,Object[] ra){return switch(rm.getName()){case "next"->{boolean next=iterator.hasNext();if(next)row=iterator.next();yield next;}case "getString"->row[(int)ra[0]-1].toString();case "getLong"->((Number)row[(int)ra[0]-1]).longValue();case "getInt"->((Number)row[(int)ra[0]-1]).intValue();default->null;};}});
                }return null;});}return null;});
    }
    @Override public void close(){server.stop(0);F1RubricTargets.clear();}
}
