package com.nori.ai.gateway.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public final class BlockExportController {
    private final ResultResponseAssembler responses;
    public BlockExportController(ResultResponseAssembler responses) { this.responses=responses; }
    @GetMapping("/v1/analyses/{jobId}/blocks/{blockId}/export") public Object export(HttpServletRequest request,@PathVariable String jobId,@PathVariable String blockId) {
        var access=responses.assemble(ApiRequestInput.scope(request),jobId);
        if(!access.allowed()){responses.assertLive(access);return access.response();}
        Object result=access.response().get("result");
        if(result instanceof java.util.Map<?,?> map&&map.get("payload") instanceof JsonNode payload) {
            var blocks=payload.get("blocks");
            if(blocks!=null&&blocks.isArray()) {
                var matches=new java.util.ArrayList<JsonNode>();
                for(var block:blocks)if(block.path("id").isString()&&blockId.equals(block.path("id").asString()))matches.add(block);
                if(matches.size()==1){responses.assertLive(access);return matches.getFirst();}
            }
        }
        throw new UniformNotFound();
    }
}
