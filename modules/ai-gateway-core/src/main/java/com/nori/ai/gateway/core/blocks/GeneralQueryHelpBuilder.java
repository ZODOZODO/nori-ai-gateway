package com.nori.ai.gateway.core.blocks;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.ResultBlock;
import java.util.*;

public final class GeneralQueryHelpBuilder {
    public List<ResultBlock> build(RegistryView view,Set<String> requestPerm) {
        var value=SchemaContracts.JSON.createObjectNode();value.putArray("columns").add("capabilityId").add("description").add("exampleQuestions");var rows=value.putArray("rows");
        for(var manifest:view.capabilities()) {
            if(manifest.availability()!=Availability.IMPLEMENTED||manifest.requiredPermission().stream().noneMatch(requestPerm::contains))continue;
            var row=rows.addObject().put("capabilityId",manifest.capabilityId()).put("description",manifest.description());row.set("exampleQuestions",SchemaContracts.JSON.valueToTree(manifest.exampleQuestions()));
        }
        var block=new ResultBlock("table",null,value);new UiSchemaValidator().validate(block,view.ownerSystem());return List.of(block);
    }
}
