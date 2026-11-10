package com.nori.ai.gateway.core.blocks;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import java.util.*;

/** raw output 계약과 최종 UI 계약은 서로 다른 시점에 검증합니다. */
public final class BlockBuilder {
    private final UiSchemaValidator validator;
    public BlockBuilder(UiSchemaValidator validator){this.validator=validator;}
    public List<ResultBlock> build(CapabilityManifest manifest,ToolResultEnvelope result) {
        List<ResultBlock> blocks=new ArrayList<>();
        for(var raw:result.blocks()) {
            var content=raw.content();
            if(content==null||!SchemaContracts.matches(manifest.outputSchema(),content))throw new IllegalArgumentException("원천 블록 계약이 다릅니다");
            var items=content.get("items");
            if(items==null||!items.isArray())throw new IllegalArgumentException("원천 행을 확인할 수 없습니다");
            LinkedHashSet<String> columns=new LinkedHashSet<>();
            for(var item:items) {if(!item.isObject())throw new IllegalArgumentException("원천 행 형식이 잘못되었습니다");item.properties().forEach(entry->columns.add(entry.getKey()));}
            var ui=SchemaContracts.JSON.createObjectNode();var names=ui.putArray("columns");columns.forEach(names::add);ui.set("rows",items.deepCopy());
            var block=new ResultBlock("table",raw.dataRef(),ui);validator.validate(block,manifest.ownerSystem());blocks.add(block);
        }
        return List.copyOf(blocks);
    }
}
