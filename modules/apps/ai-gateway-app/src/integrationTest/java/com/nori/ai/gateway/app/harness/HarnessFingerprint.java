package com.nori.ai.gateway.app.harness;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

public final class HarnessFingerprint {
    private HarnessFingerprint(){}
    public static String calculate(String classpath)throws Exception{
        var items=new ArrayList<Object>();
        for(String entry:classpath.split(Pattern.quote(java.io.File.pathSeparator))){
            Path path=Path.of(entry);if(!Files.exists(path)){items.add("MISSING");continue;}
            if(Files.isDirectory(path)){
                var files=new ArrayList<List<String>>();try(var paths=Files.walk(path)){
                    for(var file:paths.filter(Files::isRegularFile).sorted(Comparator.comparing(p->path.relativize(p).toString().replace('\\','/'))).toList())
                        files.add(List.of(path.relativize(file).toString().replace('\\','/'),sha(Files.readAllBytes(file))));
                }items.add(files);
            }else items.add(sha(Files.readAllBytes(path)));
        }
        return sha(JsonMapper.builder().build().writeValueAsBytes(items));
    }
    private static String sha(byte[] input)throws NoSuchAlgorithmException{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));}
}
