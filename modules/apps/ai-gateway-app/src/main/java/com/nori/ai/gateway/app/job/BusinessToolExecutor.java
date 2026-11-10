package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.time.Clock;
import java.util.*;

public final class BusinessToolExecutor implements ToolExecutor {
    private final TcBusinessReadPort business;private final Clock clock;
    public BusinessToolExecutor(TcBusinessReadPort business,Clock clock){this.business=business;this.clock=clock;}
    @Override public ToolKind toolKind(){return ToolKind.BUSINESS;}
    @Override public ToolResultEnvelope execute(PlanStep step,CapabilityManifest manifest,VerifiedSystemScope scope,ExecutionContext context) {
        if(!(context.resolver() instanceof WorkflowResourceResolver resolver))return ToolResults.result(manifest,scope,ResultStatus.UNSUPPORTED,List.of(),clock.instant(),List.of(),Set.of(),List.of(),null);
        var args=step.arguments();String eqp=args.path("eqp").asString();var call=resolver.delegatedCall();
        resolver.beforeRead();
        return switch(manifest.capabilityId()) {
            case "tc.eqp.getOperationalSnapshot" -> project(manifest,scope,business.getEqpSnapshot(call,eqp));
            case "tc.event.getRecent" -> project(manifest,scope,business.getRecentEvents(call,eqp,window(step,context,scope)));
            case "tc.model.read" -> project(manifest,scope,business.readModel(call,Optional.ofNullable(args.get("modelId")).filter(value->!value.isNull()).map(value->value.asString())));
            case "tc.dlq.list" -> dlq(manifest,scope,call,eqp,window(step,context,scope),resolver);
            case "tc.commLogs.search" -> commLogs(manifest,scope,call,eqp,window(step,context,scope),resolver);
            default -> ToolResults.result(manifest,scope,ResultStatus.FAILED,List.of(),clock.instant(),List.of(),Set.of(),List.of(),null);
        };
    }
    private TimeWindow window(PlanStep step,ExecutionContext context,VerifiedSystemScope scope) {
        var parser=new InputValidator();return new TimeWindow(parser.parseTime(step.arguments().path("from").asString(),context.resolver().timezone(scope)),parser.parseTime(step.arguments().path("to").asString(),context.resolver().timezone(scope)));
    }
    private ToolResultEnvelope project(CapabilityManifest manifest,VerifiedSystemScope scope,TcBusinessReadPort.TcReadResult<?> result) {
        List<?> items=result.data()==null?List.of():result.data() instanceof List<?> list?list:List.of(result.data());
        return ToolResults.result(manifest,scope,result.status(),items,result.observedAt(),result.missingFields(),result.warnings(),result.retentionMeta().missingRanges().stream().map(value->new ToolResultMetadata.MissingRange(value.startAt(),value.endAt())).toList(),null);
    }
    private ToolResultEnvelope dlq(CapabilityManifest manifest,VerifiedSystemScope scope,TcSourceAuthorizationPort.DelegatedCall call,String eqp,TimeWindow window,WorkflowResourceResolver resolver) {
        resolver.beforeRead();var gateway=business.listDlq(call,eqp,"gateway",window);
        resolver.beforeRead();var backend=business.listDlq(call,eqp,"business",window);
        var results=List.of(gateway,backend);
        List<Map<String,Object>> items=new ArrayList<>();List<String> missing=new ArrayList<>();Set<Warning> warnings=new HashSet<>();int available=0;
        for(int i=0;i<results.size();i++) {
            var result=results.get(i);String source=i==0?"gateway":"business";warnings.addAll(result.warnings());
            if(result.status()==ResultStatus.FAILED)return ToolResults.result(manifest,scope,ResultStatus.FAILED,List.of(),result.observedAt(),List.of(),warnings,List.of(),null);
            if(result.status()==ResultStatus.DENIED)return ToolResults.result(manifest,scope,ResultStatus.DENIED,List.of(),result.observedAt(),List.of(),warnings,List.of(),null);
            if(result.status()==ResultStatus.SOURCE_UNAVAILABLE){missing.add(source);continue;}
            available++;if(result.status()==ResultStatus.PARTIAL)missing.addAll(result.missingFields().stream().map(field->source+"."+field).toList());
            if(result.data()!=null)for(var entry:result.data().items()){Map<String,Object> value=SchemaContracts.JSON.convertValue(entry,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});var attributed=new LinkedHashMap<>(value);attributed.put("source",source);items.add(attributed);}
        }
        ResultStatus status=available==0?ResultStatus.SOURCE_UNAVAILABLE:!missing.isEmpty()?ResultStatus.PARTIAL:items.isEmpty()?ResultStatus.EMPTY:ResultStatus.SUCCESS;
        return ToolResults.result(manifest,scope,status,items,results.stream().map(TcBusinessReadPort.TcReadResult::observedAt).max(Comparator.naturalOrder()).orElseThrow(),missing,warnings,List.of(),null);
    }
    private ToolResultEnvelope commLogs(CapabilityManifest manifest,VerifiedSystemScope scope,TcSourceAuthorizationPort.DelegatedCall call,String eqp,TimeWindow window,WorkflowResourceResolver resolver) {
        var results=new ArrayList<TcBusinessReadPort.TcReadResult<TcBusinessReadPort.CommLogData>>();
        for(String source:List.of("gateway","business")){resolver.beforeRead();results.add(Objects.requireNonNull(business.getCommLogs(call,eqp,source,window)));}
        var rows=new ArrayList<Map<String,Object>>();var evaluations=new ArrayList<com.nori.ai.gateway.core.retention.TrimCompletenessEvaluator.Evaluation>();
        var missing=new ArrayList<String>();var warnings=new HashSet<Warning>();var confirmed=new ArrayList<ToolResultMetadata.MissingRange>();
        for(int i=0;i<results.size();i++) {
            var value=results.get(i);String source=i==0?"gateway":"business";
            if(value.status()==ResultStatus.DENIED||value.status()==ResultStatus.FAILED) return ToolResults.result(manifest,scope,value.status(),List.of(),value.observedAt(),value.missingFields(),Set.of(),List.of(),null);
            if(!Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL,ResultStatus.OUT_OF_RETENTION,ResultStatus.SOURCE_UNAVAILABLE).contains(value.status()))throw new IllegalStateException("통신 로그 상태 계약이 다릅니다");
            if(value.status()==ResultStatus.SOURCE_UNAVAILABLE)missing.add(source);
            missing.addAll(value.missingFields().stream().map(field->source+"."+field).toList());warnings.addAll(value.warnings());
            var row=new LinkedHashMap<String,Object>();row.put("source",source);row.put("status",value.status().name());row.put("observedAt",value.observedAt());
            row.put("items",value.data()==null?List.of():value.data().items());row.put("completeness",value.data()==null?null:value.data().completeness());
            row.put("warnings",value.warnings());row.put("retentionMeta",value.retentionMeta());rows.add(row);
            value.retentionMeta().missingRanges().forEach(range->confirmed.add(new ToolResultMetadata.MissingRange(range.startAt(),range.endAt())));
            evaluations.add(new com.nori.ai.gateway.core.retention.TrimCompletenessEvaluator.Evaluation(value.status(),value.retentionMeta().missingRanges(),value.retentionMeta().unverifiedRanges(),value.warnings()));
        }
        ResultStatus status=new com.nori.ai.gateway.core.retention.TrimCompletenessEvaluator().aggregate(evaluations);
        return ToolResults.result(manifest,scope,status,rows,results.stream().map(TcBusinessReadPort.TcReadResult::observedAt).max(Comparator.naturalOrder()).orElseThrow(),missing,warnings,confirmed,null);
    }
}
