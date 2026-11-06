package com.nori.ai.gateway.core.time;

import com.nori.ai.gateway.core.plan.InputValidator;
import com.nori.ai.gateway.core.registry.WorkflowDefinition;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.retention.TimeWindow;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** 상위 시간 입력 오류를 이전 턴의 정상 창으로 덮지 않습니다. */
public final class TimeWindowResolver {
    public record Resolved(Optional<TimeWindow> window,Map<String,Object> values,boolean contextConflict) {
        public Resolved { values=Map.copyOf(values); }
    }
    public static final class Failure extends RuntimeException {
        private final ErrorCode code;
        public Failure(ErrorCode code) { super("시간 구간을 확인해 주세요");this.code=code; }
        public ErrorCode code() { return code; }
    }
    private final InputValidator parser=new InputValidator();
    private record Selection(Map<String,Object> selected,ZoneId zone,Instant reference,boolean conflict) {}
    public Resolved resolve(Map<String,Object> extraction,Map<String,Object> context,Supplier<Optional<Map<String,Object>>> previousTurnWindow,WorkflowDefinition workflow) {
        boolean required=workflow.defaultWindowLength()!=null && workflow.answerSource()!=WorkflowDefinition.AnswerSource.REGISTRY_VIEW;
        if (!required) return new Resolved(Optional.empty(),Map.of(),false);
        Selection selection=select(extraction,context);
        Map<String,Object> selected=selection.selected();
        // 영속 reader의 결속·손상 예외는 사용자 시간 입력 오류와 구별해 그대로 전파합니다.
        if(selected.isEmpty())selected=previousTurnWindow.get().orElse(Map.of());
        if(selected.isEmpty())throw new Failure(ErrorCode.TIME_WINDOW_REQUIRED);
        return finish(selected,selection);
    }
    private Selection select(Map<String,Object> extraction,Map<String,Object> context) {
        try {
            ZoneId zone=context.get("timezone")==null?null:ZoneId.of((String)context.get("timezone"));
            Instant reference=context.get("referenceTime")==null?null:parser.parseTime((String)context.get("referenceTime"),zone);
            Map<String,Object> supplied=window(extraction);
            Map<String,Object> selected=context.get("timeWindow")==null?Map.of():object(context.get("timeWindow"));
            boolean conflict=false;
            if (!supplied.isEmpty()) {
                if (!selected.isEmpty()) conflict=!parse(supplied,zone).equals(parse(selected,zone));
                selected=supplied;
            }
            if(!selected.isEmpty())parse(selected,zone);
            return new Selection(selected,zone,reference,conflict);
        } catch (Failure failure) { throw failure; }
        catch (java.time.DateTimeException | ClassCastException | IllegalArgumentException failure) { throw new Failure(ErrorCode.PLAN_INVALID); }
    }
    private Resolved finish(Map<String,Object> selected,Selection selection) {
        try {
            TimeWindow value=parse(selected,selection.zone());
            if (selection.reference()!=null && value.endAt().isAfter(selection.reference())) throw new Failure(ErrorCode.PLAN_INVALID);
            var normalized=new LinkedHashMap<String,Object>();
            normalized.put("from",normalize((String)selected.get("from"),value.startAt(),selection.zone()));
            normalized.put("to",normalize((String)selected.get("to"),value.endAt(),selection.zone()));
            return new Resolved(Optional.of(value),normalized,selection.conflict());
        } catch (Failure failure) { throw failure; }
        catch (java.time.DateTimeException | ClassCastException | IllegalArgumentException failure) { throw new Failure(ErrorCode.PLAN_INVALID); }
    }
    private Map<String,Object> window(Map<String,Object> extraction) {
        boolean from=extraction.get("from")!=null,to=extraction.get("to")!=null;
        if (from!=to) throw new Failure(ErrorCode.PLAN_INVALID);
        return from?Map.of("from",extraction.get("from"),"to",extraction.get("to")):Map.of();
    }
    private Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map)) throw new Failure(ErrorCode.PLAN_INVALID);
        var copy=new LinkedHashMap<String,Object>();map.forEach((key,item)->copy.put((String)key,item));return copy;
    }
    private TimeWindow parse(Map<String,Object> values,ZoneId zone) {
        if (!values.keySet().equals(Set.of("from","to"))) throw new Failure(ErrorCode.PLAN_INVALID);
        return new TimeWindow(parser.parseTime((String)values.get("from"),zone),parser.parseTime((String)values.get("to"),zone));
    }
    private String normalize(String original,Instant instant,ZoneId zone) {
        ZoneId target=zone==null?ZoneOffset.UTC:zone;
        int fraction=original.indexOf('.');int digits=0;
        if (fraction>=0) { int i=fraction+1;while(i<original.length()&&Character.isDigit(original.charAt(i))) {digits++;i++;} }
        boolean seconds=original.length()>16&&original.charAt(16)==':';
        String pattern="uuuu-MM-dd'T'HH:mm"+(seconds?":ss":"")+(digits>0?"."+"S".repeat(digits):"")+"XXX";
        return DateTimeFormatter.ofPattern(pattern).format(instant.atZone(target));
    }
}
