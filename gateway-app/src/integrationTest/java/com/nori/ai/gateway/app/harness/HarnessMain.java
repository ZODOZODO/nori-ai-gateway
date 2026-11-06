package com.nori.ai.gateway.app.harness;

import com.nori.ai.gateway.app.AiGatewayApplication;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import tools.jackson.databind.json.JsonMapper;

public final class HarnessMain {
    public static void main(String[] args)throws Exception{
        var parsed=new HashMap<String,String>();for(int i=0;i<args.length;i+=2)parsed.put(args[i],args[i+1]);
        String actual=HarnessFingerprint.calculate(System.getProperty("java.class.path"));
        if(!actual.equals(parsed.get("--fingerprint"))){System.out.println("HARNESS_CLASSPATH_MISMATCH");System.exit(70);}
        String config=parsed.get("--config");
        if(!Set.of(HarnessP09TestPorts.class.getName()).contains(config))throw new IllegalArgumentException("명시 시험 설정이 필요합니다");
        var context=new SpringApplicationBuilder(AiGatewayApplication.class).sources(Class.forName(config)).profiles("verify").run();
        if(context.getBean(ReadinessGate.class).ready()){
            int port=((WebServerApplicationContext)context).getWebServer().getPort();
            Path directory=Path.of(parsed.get("--state-dir")),temporary=directory.resolve("harness-ready.tmp"),ready=directory.resolve("harness-ready.json");
            var data=Map.of("pid",ProcessHandle.current().pid(),"port",port,"harnessClasspathSha256",actual,"envKeys",System.getenv().keySet().stream().sorted().toList());
            Files.writeString(temporary,JsonMapper.builder().build().writeValueAsString(data));Files.move(temporary,ready,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
