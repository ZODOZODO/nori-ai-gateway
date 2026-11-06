package com.nori.ai.gateway.core.blocks;

import com.nori.ai.gateway.core.result.ResultBlock;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import tools.jackson.databind.JsonNode;
import java.util.*;

public final class UiSchemaValidator {
    private static final Set<String> TYPES=Set.of("text","table","status-card","chart","timeline","code-block","evidence-link");
    public void validate(ResultBlock block,OwnerSystem owner) {
        if(!TYPES.contains(block.type())||block.dataRef()!=null&&!block.dataRef().belongsTo(owner)||block.content()==null||!block.content().isObject())throw new IllegalArgumentException("표시 블록이 잘못되었습니다");
        JsonNode value=block.content();
        validateNumbers(value);
        switch(block.type()) {
            case "table" -> {
                if(!value.path("columns").isArray()||!value.path("rows").isArray())throw new IllegalArgumentException("표 형식이 잘못되었습니다");
                Set<String> columns=new LinkedHashSet<>();
                for(var column:value.get("columns"))if(!column.isString()||!columns.add(column.asString()))throw new IllegalArgumentException("표 열이 잘못되었습니다");
                for(var row:value.get("rows"))if(!row.isObject()||row.properties().stream().anyMatch(entry->!columns.contains(entry.getKey())))throw new IllegalArgumentException("표 행이 잘못되었습니다");
            }
            case "text","code-block" -> {if(!value.path("text").isString())throw new IllegalArgumentException("표시 내용이 잘못되었습니다");}
            case "status-card" -> {if(!value.has("status"))throw new IllegalArgumentException("상태 값이 없습니다");}
            case "chart" -> {if(!value.path("series").isArray())throw new IllegalArgumentException("차트 값이 잘못되었습니다");}
            case "timeline" -> {if(!value.path("items").isArray())throw new IllegalArgumentException("시각 목록이 잘못되었습니다");}
            case "evidence-link" -> {if(!value.path("evidenceId").isString())throw new IllegalArgumentException("근거 참조가 잘못되었습니다");}
            default -> throw new IllegalArgumentException("지원하지 않는 표시 형식입니다");
        }
    }
    private void validateNumbers(JsonNode value) {
        if(value.isFloat()&&!Float.isFinite(value.floatValue())||value.isDouble()&&!Double.isFinite(value.doubleValue()))throw new IllegalArgumentException("표시 숫자가 유효하지 않습니다");
        if(value.isObject())value.properties().forEach(entry->validateNumbers(entry.getValue()));
        else if(value.isArray())value.forEach(this::validateNumbers);
    }
}
