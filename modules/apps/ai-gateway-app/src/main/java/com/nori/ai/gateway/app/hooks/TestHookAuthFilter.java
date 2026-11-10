package com.nori.ai.gateway.app.hooks;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class TestHookAuthFilter implements Filter {
    private final byte[] expected;
    public TestHookAuthFilter(Path hashFile){
        try{
            String hash=Files.readString(hashFile,StandardCharsets.UTF_8);
            if(!hash.matches("[0-9a-f]{64}\\n?"))throw new IllegalArgumentException();
            expected=HexFormat.of().parseHex(hash.strip());
        }catch(Exception failure){throw new IllegalStateException("시험 훅 인증 파일이 잘못되었습니다");}
    }
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        var http=(HttpServletRequest)request;var output=(HttpServletResponse)response;String token=http.getHeader("X-AIGW-Hook-Token");
        byte[] digest;
        try{digest=MessageDigest.getInstance("SHA-256").digest((token==null?"":token).getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("훅 인증 해시를 계산할 수 없습니다");}
        if(token==null||Collections.list(http.getHeaders("X-AIGW-Hook-Token")).size()!=1||!MessageDigest.isEqual(expected,digest)){output.setStatus(404);return;}
        chain.doFilter(request,response);
    }
}
