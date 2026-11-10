package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.security.ServiceTokenAuthFilter;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;

final class ApiRequestInput {
    private ApiRequestInput() {}
    static VerifiedSystemScope scope(HttpServletRequest request) {
        return (VerifiedSystemScope)Objects.requireNonNull(request.getAttribute(ServiceTokenAuthFilter.VERIFIED_SCOPE_ATTRIBUTE));
    }
    static String text(Object value) {
        if(!(value instanceof String text)||text.isBlank())throw new IllegalArgumentException("문자열 입력이 필요합니다");return text;
    }
    static Map<String,Object> fields(Map<String,Object> body,VerifiedSystemScope scope,ContentSanitizer sanitizer) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("context","resourceRefs","options"))if(body.containsKey(key))result.put(key,body.get(key));
        if(result.containsKey("context")&&result.get("context")!=null) {
            var context=object(result.get("context"));var copy=new LinkedHashMap<String,Object>();
            ZoneId zone=ZoneId.of(text(context.get("timezone")));var validator=new InputValidator();
            String reference=text(context.get("referenceTime"));validator.parseTime(reference,zone);
            copy.put("timezone",context.get("timezone"));copy.put("referenceTime",reference);
            if(context.containsKey("timeWindow")) {
                Object window=context.get("timeWindow");
                if(window==null)copy.put("timeWindow",null);
                else {var time=object(window);String from=text(time.get("from")),to=text(time.get("to"));
                    if(!validator.parseTime(from,zone).isBefore(validator.parseTime(to,zone)))throw new IllegalArgumentException("시간 구간이 잘못되었습니다");
                    copy.put("timeWindow",Map.of("from",from,"to",to));}
            }
            result.put("context",copy);
        }
        if(result.containsKey("resourceRefs")&&result.get("resourceRefs")!=null) {
            if(!(result.get("resourceRefs") instanceof List<?> values))throw new IllegalArgumentException("리소스 목록이 필요합니다");
            var refs=new ArrayList<Map<String,Object>>();
            for(Object value:values) {var object=object(value);var ref=new ResourceRef(text(object.get("type")),text(object.get("ref")));
                if(!sanitizer.sanitize(ref.type()).text().equals(ref.type())||!sanitizer.sanitize(ref.ref()).text().equals(ref.ref()))throw new IllegalArgumentException("리소스 입력을 확인할 수 없습니다");
                if(ref.ref().startsWith("system://")&&!new com.nori.ai.gateway.core.scope.OwnedReference(scope.boundSystem(),ref.ref()).belongsTo(scope.boundSystem()))throw new IllegalArgumentException("리소스 소유 범위가 다릅니다");
                refs.add(Map.of("type",ref.type(),"ref",ref.ref()));}
            result.put("resourceRefs",refs);
        }
        if(result.containsKey("options")&&result.get("options")!=null) {
            var options=object(result.get("options"));var copy=new LinkedHashMap<String,Object>();
            for(String key:List.of("allowTools","allowKnowledge"))if(options.containsKey(key)) {
                Object value=options.get(key);if(value!=null&&!(value instanceof Boolean))throw new IllegalArgumentException("옵션 값이 잘못되었습니다");copy.put(key,value);
            }
            result.put("options",copy);
        }
        return result;
    }
    static Map<?,?> object(Object value) { if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("객체 입력이 필요합니다");return map; }
}
