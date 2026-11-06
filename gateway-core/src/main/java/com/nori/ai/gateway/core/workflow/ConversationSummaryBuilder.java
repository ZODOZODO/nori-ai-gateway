package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.result.*;
import java.util.*;

public final class ConversationSummaryBuilder {
    public Map<String,Object> build(JobContext job,Map<String,Object> window,TerminalSpecView result) {
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("taskType",job.classification().get("taskType"));summary.put("selectedResourceRefs",job.requestInput().selectedResourceRefs());
        if(!window.isEmpty())summary.put("timeWindow",window);
        summary.put("status",result.status().name());summary.put("blocks",result.blocks());
        summary.put("jobId",job.jobId());summary.put("requestMessageId",job.requestInput().messageId());summary.put("jobSeq",job.requestInput().jobSeq());summary.put("resultSourceDeps",result.sourceDeps());summary.put("knowledgeDocuments",result.sourceDeps().knowledgeDocuments());return summary;
    }
    public record TerminalSpecView(ResultStatus status,List<ResultBlock> blocks,ResultSourceDeps sourceDeps) {public TerminalSpecView {blocks=List.copyOf(blocks);}}
}
