package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.integration.gitlab.*;
import com.nori.ai.gateway.integration.source.SourceReadAccess;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;

/** 이미지 버전이 없는 경우 현재 승인 코드가 당시 배포 코드라고 주장하지 않습니다. */
public final class DeploymentEvidenceResolver {
    public record Resolution(Optional<GitLabPipelineLineageReader.Lineage> lineage,String deployedSourceCommit,List<String> missingEvidence,String codeRefKind) {
        public Resolution {Objects.requireNonNull(lineage);missingEvidence=List.copyOf(missingEvidence);}
    }
    private final ObjectProvider<GitLabPipelineLineageReader> reader;
    public DeploymentEvidenceResolver(ObjectProvider<GitLabPipelineLineageReader> reader){this.reader=reader;}
    public Resolution resolve(SourceReadAccess.Request request,String service,String version,List<GitLabSourcePolicy.LineageEntry> entries) {
        if(version==null||!version.matches("[1-9][0-9]*"))return unknown();
        final long pipeline;try{pipeline=Long.parseLong(version);}catch(NumberFormatException invalid){return unknown();}
        var selected=entries.stream().filter(entry->entry.service().equals(service)).toList();if(selected.size()!=1)return unknown();
        var actual=reader.getIfAvailable();if(actual==null)return unknown();
        var lineage=actual.siblingCommits(request,selected.getFirst(),pipeline);
        return lineage.status()==GitLabPipelineLineageReader.Status.VERIFIED&&lineage.commitSha()!=null&&lineage.commitSha().matches("[0-9a-f]{40}")
            ?new Resolution(Optional.of(lineage),lineage.commitSha(),List.of("plugin JAR digest","effectiveConfig"),"DEPLOYED_SOURCE_COMMIT")
            :new Resolution(Optional.of(lineage),"UNKNOWN",List.of("deployed source commit","plugin JAR digest","effectiveConfig"),"CURRENT_APPROVED_REF");
    }
    private Resolution unknown(){return new Resolution(Optional.empty(),"UNKNOWN",List.of("deployed source commit","plugin JAR digest","effectiveConfig"),"CURRENT_APPROVED_REF");}
}
