package com.nori.ai.gateway.core.job;

import com.nori.ai.gateway.core.plan.ResourceRef;
import java.util.*;

/** 대화의 최신 Context와 독립적으로 접수 시점의 정확한 입력을 보존합니다. */
public record JobRequestInput(String messageId, long jobSeq, String maskedQuestion, Map<String,Object> requestFields) {
    public JobRequestInput {
        Objects.requireNonNull(messageId); Objects.requireNonNull(maskedQuestion);
        if (messageId.isBlank() || maskedQuestion.isBlank() || jobSeq <= 0) throw new IllegalArgumentException("요청 입력 연결이 잘못되었습니다");
        requestFields = immutableFields(requestFields);
        validateFields(requestFields);
    }
    public static Map<String,Object> immutableFields(Map<String,Object> fields) {
        Objects.requireNonNull(fields);
        var copy = new LinkedHashMap<String,Object>();
        fields.forEach((key,value) -> copy.put(Objects.requireNonNull(key), immutable(value)));
        return Collections.unmodifiableMap(copy);
    }
    private static Object immutable(Object value) {
        if (value instanceof Map<?,?> map) {
            var copy = new LinkedHashMap<String,Object>();
            map.forEach((key,item) -> { if (!(key instanceof String text)) throw new IllegalArgumentException("입력 키가 잘못되었습니다"); copy.put(text,immutable(item)); });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(JobRequestInput::immutable).toList());
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double
            || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) return value;
        throw new IllegalArgumentException("입력 값 형식이 잘못되었습니다");
    }
    private static void validateFields(Map<String,Object> fields) {
        // API 검증 뒤에도 손상된 영속 입력을 기본 질문이나 기본 Context로 실행하지 않습니다.
        if(fields.get("context")!=null) {
            var context=object(fields.get("context"));var zone=java.time.ZoneId.of(text(context.get("timezone")));
            var times=new com.nori.ai.gateway.core.plan.InputValidator();times.parseTime(text(context.get("referenceTime")),zone);
            if(context.get("timeWindow")!=null) {var window=object(context.get("timeWindow"));
                if(!times.parseTime(text(window.get("from")),zone).isBefore(times.parseTime(text(window.get("to")),zone)))throw new IllegalArgumentException("시간 구간이 잘못되었습니다");}
        }
        if(fields.get("resourceRefs")!=null) {
            if(!(fields.get("resourceRefs") instanceof List<?> refs))throw new IllegalArgumentException("리소스 목록이 잘못되었습니다");
            for(var value:refs){var ref=object(value);text(ref.get("type"));text(ref.get("ref"));}
        }
        if(fields.get("options")!=null) {
            var options=object(fields.get("options"));
            for(String key:List.of("allowTools","allowKnowledge"))if(options.get(key)!=null&&!(options.get(key) instanceof Boolean))throw new IllegalArgumentException("옵션 값이 잘못되었습니다");
        }
    }
    private static Map<?,?> object(Object value) {if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("입력 객체가 잘못되었습니다");return map;}
    private static String text(Object value) {if(!(value instanceof String text)||text.isBlank())throw new IllegalArgumentException("입력 문자열이 잘못되었습니다");return text;}
    public List<ResourceRef> selectedResourceRefs() {
        if (!(requestFields.get("resourceRefs") instanceof List<?> refs)) return List.of();
        return refs.stream().map(value -> { var ref = (Map<?,?>)value; return new ResourceRef((String)ref.get("type"),(String)ref.get("ref")); }).toList();
    }
    @Override public String toString() { return "JobRequestInput[보호됨]"; }
}
