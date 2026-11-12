package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.app.job.JobTimeoutSweeper;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.persistence.queue.QueueJobRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/__test")
public final class TestHookController {
    private final TestHookRegistry registry;private final ControllableClock clock;private final PausePointRegistry pauses;
    private final QueueJobRepository jobs;private final JobTimeoutSweeper sweeper;
    private final SeamRegistry seams;private final com.nori.ai.gateway.tcmock.MockScenarioStore scenarios;private final com.nori.ai.gateway.tcmock.MockCallCounter calls;
    public TestHookController(TestHookRegistry registry,ControllableClock clock,PausePointRegistry pauses,QueueJobRepository jobs,JobTimeoutSweeper sweeper,
            SeamRegistry seams,com.nori.ai.gateway.tcmock.MockScenarioStore scenarios,com.nori.ai.gateway.tcmock.MockCallCounter calls){
        this.registry=registry;this.clock=clock;this.pauses=pauses;this.jobs=jobs;this.sweeper=sweeper;
        this.seams=seams;this.scenarios=scenarios;this.calls=calls;
    }
    @PostMapping("/arm") public ResponseEntity<Void> arm(@RequestBody JsonNode body){
        fields(body,Set.of("actorSub","requestKey","scenario"));JsonNode scenario=body.required("scenario");
        fields(scenario,Set.of("taskType","pauseAt","haltAt","failSanitizer","seam","llmScript","promptWatch"));
        Optional<SeamRegistry.Directive> directive=Optional.empty();
        if(scenario.has("seam")){
            var seam=scenario.get("seam");fields(seam,Set.of("kind","capabilityId","sourceId","ownerSystem"));
            if(!seam.has("kind")||seam.properties().stream().anyMatch(entry->!entry.getValue().isString()))throw new IllegalArgumentException();
            var value=new SeamRegistry.Directive(SeamRegistry.Kind.valueOf(seam.required("kind").asString()),
                seam.has("capabilityId")?seam.get("capabilityId").asString():null,seam.has("sourceId")?seam.get("sourceId").asString():null,
                seam.has("ownerSystem")?com.nori.ai.gateway.core.scope.OwnerSystem.valueOf(seam.get("ownerSystem").asString()):null);
            seams.validate(value);directive=Optional.of(value);
        }
        var points=new HashSet<PausePointRegistry.Point>();
        if(scenario.has("pauseAt")){if(!scenario.get("pauseAt").isArray())throw new IllegalArgumentException();for(var p:scenario.get("pauseAt"))points.add(PausePointRegistry.Point.valueOf(p.asString()));}
        var halt=scenario.hasNonNull("haltAt")?PausePointRegistry.Point.valueOf(scenario.get("haltAt").asString()):null;
        registry.arm(body.required("actorSub").asString(),body.required("requestKey").asString(),new TestHookRegistry.Scenario(
            TaskType.valueOf(scenario.required("taskType").asString()),points,halt,scenario.has("failSanitizer")&&scenario.get("failSanitizer").asBoolean(),directive,
            llmScript(scenario),promptWatch(scenario)));
        return ResponseEntity.noContent().build();
    }
    private static List<TestHookRegistry.LlmReply> llmScript(JsonNode scenario){
        if(!scenario.has("llmScript"))return List.of();
        var array=scenario.get("llmScript");if(!array.isArray()||array.isEmpty())throw new IllegalArgumentException();
        var replies=new ArrayList<TestHookRegistry.LlmReply>();
        for(var entry:array){
            fields(entry,Set.of("callType","content","finishReason","usage"));var usage=entry.required("usage");fields(usage,Set.of("promptTokens","completionTokens"));
            var content=entry.required("content");var completion=usage.required("completionTokens");var prompt=usage.get("promptTokens");
            if(!entry.required("callType").isString()||!entry.required("finishReason").isString()||!(content.isNull()||content.isString())
                ||!completion.isIntegralNumber()||!completion.canConvertToInt()||prompt!=null&&(!prompt.isIntegralNumber()||!prompt.canConvertToInt()))throw new IllegalArgumentException();
            replies.add(new TestHookRegistry.LlmReply(com.nori.ai.gateway.core.budget.CallType.valueOf(entry.get("callType").asString()),content.isNull()?null:content.asString(),
                entry.get("finishReason").asString(),prompt==null?null:prompt.asInt(),completion.asInt()));
        }
        return List.copyOf(replies);
    }
    private static Optional<TestHookRegistry.PromptWatch> promptWatch(JsonNode scenario){
        if(!scenario.has("promptWatch"))return Optional.empty();
        var watch=scenario.get("promptWatch");fields(watch,Set.of("sentinels"));var sentinels=watch.required("sentinels");if(!sentinels.isArray())throw new IllegalArgumentException();
        var values=new ArrayList<String>();for(var sentinel:sentinels){if(!sentinel.isString())throw new IllegalArgumentException();values.add(sentinel.asString());}
        return Optional.of(new TestHookRegistry.PromptWatch(values));
    }
    @PostMapping("/reset") public ResponseEntity<?> reset(){
        if(!registry.resetIfIdle(this::noActiveJobs,()->{scenarios.select("default");calls.reset();}))return ResponseEntity.status(409).body(Map.of("error","ACTIVE_JOBS"));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/clock") public Map<String,Object> clock(){return Map.of("now",clock.instant().toString(),"mode",clock.mode().name());}
    @PostMapping("/clock") public Map<String,Object> clock(@RequestBody JsonNode body){
        fields(body,Set.of("op","at","seconds"));
        switch(body.required("op").asString()){
            case "FREEZE_AT"->registry.freezeAt(Instant.parse(body.required("at").asString()),this::noActiveJobs);
            case "ADVANCE"->{var seconds=body.required("seconds");if(!seconds.isIntegralNumber()||!seconds.canConvertToLong())throw new IllegalArgumentException();clock.advance(seconds.asLong());}
            case "RESUME_SYSTEM"->clock.resumeSystem();
            default->throw new IllegalArgumentException();
        }return clock();
    }
    @PostMapping("/pause") public ResponseEntity<Void> pause(@RequestBody JsonNode body){
        fields(body,Set.of("point","jobId","op"));var point=PausePointRegistry.Point.valueOf(body.required("point").asString());
        String jobId=body.has("jobId")?body.get("jobId").asString():"*";
        if(!jobId.matches("[A-Za-z0-9_:-]{1,160}|\\*"))throw new IllegalArgumentException();
        switch(body.required("op").asString()){case "HOLD"->pauses.hold(point,jobId);case "RELEASE"->pauses.release(point,jobId);default->throw new IllegalArgumentException();}
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/sweep-now") public ResponseEntity<Void> sweep(){sweeper.sweepNow();return ResponseEntity.noContent().build();}
    @ExceptionHandler(TestHookRegistry.Conflict.class) public ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("error","HOOK_CONFLICT"));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class,java.time.DateTimeException.class}) public ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","훅 입력이 잘못되었습니다"));}
    private boolean noActiveJobs(){return jobs.count("QUEUED")==0&&jobs.count("RUNNING")==0;}
    private static void fields(JsonNode node,Set<String> allowed){if(!node.isObject()||node.properties().stream().anyMatch(e->!allowed.contains(e.getKey())))throw new IllegalArgumentException();}
}
