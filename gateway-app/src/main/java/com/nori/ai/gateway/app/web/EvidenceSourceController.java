package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.persistence.repository.EvidenceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public final class EvidenceSourceController {
    private final ResultResponseAssembler responses;private final EvidenceRepository evidence;private final EvidenceSourceReader sources;
    public EvidenceSourceController(ResultResponseAssembler responses,EvidenceRepository evidence,EvidenceSourceReader sources) { this.responses=responses;this.evidence=evidence;this.sources=sources; }
    @GetMapping("/v1/analyses/{jobId}/evidence/{evidenceId}/source") public Object source(HttpServletRequest request,@PathVariable String jobId,@PathVariable String evidenceId) {
        var scope=ApiRequestInput.scope(request);var access=responses.assemble(scope,jobId);
        if(!access.allowed()) {responses.assertLive(access);return access.response();}
        var entry=evidence.find(scope,jobId,evidenceId).orElseThrow(UniformNotFound::new);
        if(entry.sourceRef().startsWith("system://")&&!new com.nori.ai.gateway.core.scope.OwnedReference(scope.boundSystem(),entry.sourceRef()).belongsTo(scope.boundSystem()))throw new UniformNotFound();
        if(entry.chunkId()==null||entry.revision()==null)throw new UniformNotFound();
        var reference=new EvidenceSourceRef(scope.boundSystem(),entry.sourceRef(),entry.chunkId(),entry.revision());
        var outcome=sources.read(scope,scope.perm(),reference);responses.assertLive(access);
        if(outcome instanceof EvidenceSourceReader.Found found)return Map.of("maskedText",found.maskedText());throw new UniformNotFound();
    }
}
