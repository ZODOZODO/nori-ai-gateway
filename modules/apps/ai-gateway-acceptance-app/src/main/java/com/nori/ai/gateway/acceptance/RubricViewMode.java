package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * {@code AcceptanceMain --mode rubric-view} 입력 검사. SPEC 입력과 rubric 대상 레코드(같은 부모 회차의 RUN COMPLETED)를 확인한다.
 * F1·F2 고정 fixture의 actor 단언으로 같은 Spec·빌드·부모 회차의 실제 Job을 다시 조회하고, reviewer 입력 없이는 판정하지 않습니다.
 * F2-RUBRIC 대상은 사전등록 {@code rubric/f2-v1.json} fixture 와 (leaf, fixture) 가 정확히 맞아야 하며 세 자유 서술 leaf 를 모두 덮어야 합니다.
 */
final class RubricViewMode {
    private RubricViewMode() {}

    static int run(AcceptanceConfig config, PrintStream out) {
        String suite = config.suite().orElse(null);
        String stage = suite == null ? null : AcceptanceConfig.stageOf(suite).orElseThrow();
        String runId = UUID.randomUUID().toString(), parent = config.parentRunId().orElse(null);
        JsonNode identity = config.buildIdentity().orElse(null);
        String specSha;
        try { specSha = SpecInputVerifier.verify(config.specGzipFile().orElse(null), config.expectedSpecSha256().orElse(null)); }
        catch (SpecInputVerifier.Failure failure) {
            EvidenceBlockWriter.write(out, List.of(EvidenceBlockWriter.record(new EvidenceBlockWriter.Context(stage, suite, runId, parent, null, identity),
                Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", failure.code()))));
            return 3;
        }
        var context = new EvidenceBlockWriter.Context(stage, suite, runId, parent, specSha, identity);
        if (suite == null || !suite.endsWith("-RUBRIC") || parent == null || targets(config.env().get("AIGW_RUBRIC_TARGETS_FILE"), parent).isEmpty()) {
            EvidenceBlockWriter.write(out, List.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "RUBRIC_TARGETS_INVALID"))));
            return 2;
        }
        var selected=targets(config.env().get("AIGW_RUBRIC_TARGETS_FILE"),parent).orElseThrow();
        String automatic=suite.substring(0,suite.length()-"-RUBRIC".length());
        try {
            var source=CanonicalJson.MAPPER.readTree(Files.readAllBytes(Path.of(config.env().get("AIGW_RUBRIC_TARGETS_FILE"))));
            if(!source.path("stage").asText().equals(stage)||!source.path("suite").asText().equals(automatic)||!source.path("specSha").asText().equals(specSha)||!source.path("buildIdentity").equals(identity))return abort(out,context,"RUBRIC_TARGETS_INVALID");
            var copy=((com.fasterxml.jackson.databind.node.ObjectNode)source).deepCopy();String expected=copy.path("contentSha256").asText();copy.remove("contentSha256");
            if(!expected.equals(CanonicalJson.sha256(CanonicalJson.bytes(copy))))return abort(out,context,"RUBRIC_TARGETS_INVALID");
        }catch(IOException|RuntimeException failure){return abort(out,context,"RUBRIC_TARGETS_INVALID");}
        if(!fixturesBound(suite,selected))return abort(out,context,"RUBRIC_FIXTURE_CLAIMS_UNDEFINED");
        String reviewer=config.env().getOrDefault("AIGW_RUBRIC_REVIEWER_REF",""),version=config.env().getOrDefault("AIGW_RUBRIC_VERSION","");
        if(!reviewer.matches("[A-Za-z0-9_:/.-]{1,160}")||!version.matches("[A-Za-z0-9_.-]{1,64}"))return abort(out,context,"RUBRIC_REVIEWER_INPUT_MISSING");
        try {
            var clients=F1Runtime.clients(config,false);
            var records=new java.util.concurrent.CopyOnWriteArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
            int port=Integer.parseInt(config.properties().getOrDefault("aigw.acceptance.rubricPort","8765"));
            int seconds=Integer.parseInt(config.properties().getOrDefault("aigw.acceptance.rubricTimeoutSeconds","3600"));
            if(port<0||port>65535||seconds<1||seconds>3600)return abort(out,context,"RUBRIC_VIEW_INPUT_INVALID");
            try(var viewer=RubricViewer.start(port,selected,target->{
                return answer(clients.gateway(),target);
            },records::add,context,reviewer,version)) {
                long deadline=System.nanoTime()+java.time.Duration.ofSeconds(seconds).toNanos();
                while(!viewer.finished()&&System.nanoTime()<deadline)Thread.sleep(100);
                EvidenceBlockWriter.write(out,records);
                boolean passed=records.size()==selected.size()&&records.stream().allMatch(record->record.path("result").asText().equals("PASS"));
                EvidenceBlockWriter.write(out,List.of(EvidenceBlockWriter.record(context,Map.of("kind","RUN","status","COMPLETED","executedTestCount",records.size()))));return passed?0:1;
            }
        }catch(org.opentest4j.TestAbortedException failure){return abort(out,context,"VERIFY_CREDENTIALS_MISSING");}
        catch(InterruptedException failure){Thread.currentThread().interrupt();return abort(out,context,"RUBRIC_VIEW_INTERRUPTED");}
        catch(IOException|IllegalArgumentException failure){return abort(out,context,"RUBRIC_VIEW_INPUT_INVALID");}
    }
    /** F1 은 두 retention fixture 정확히, F2 는 사전등록 fixture 와 맞는 (leaf, fixture) 중복 없이 세 자유 서술 leaf 전부. */
    static boolean fixturesBound(String suite,List<RubricViewer.Target> selected){
        if(suite.equals("F1-RUBRIC"))return selected.size()==2&&selected.stream().map(RubricViewer.Target::leafId).distinct().count()==2&&selected.stream().noneMatch(target->F1Fixture.claimsFor(target).isEmpty());
        if(!suite.equals("F2-RUBRIC")||selected.stream().anyMatch(target->F2Fixture.claimsFor(target).isEmpty()))return false;
        return selected.stream().map(target->target.leafId()+"/"+target.fixtureId()).distinct().count()==selected.size()&&selected.stream().map(RubricViewer.Target::leafId).collect(java.util.stream.Collectors.toSet()).equals(F2Fixture.FREE_TEXT_LEAVES);
    }
    static Optional<byte[]> answer(GatewayClient gateway,RubricViewer.Target target){
        boolean f1=F1Fixture.claimsFor(target).isPresent();
        var claims=f1?F1Fixture.claimsFor(target):F2Fixture.claimsFor(target);
        if(claims.isEmpty())return Optional.empty();
        var response=gateway.get("/v1/analyses/"+target.jobId(),claims.get());if(response.statusCode()!=200)return Optional.empty();
        try{var result=CanonicalJson.MAPPER.readTree(response.body());String status=result.path("jobStatus").asText();
            // F1 대상은 성공 Job 만, F2 LIVE 는 LLM 실패(PARTIAL 등)도 기록 대상이라 terminal 이면 같은 서술 해시로만 결속한다.
            if(!result.path("jobId").asText().equals(target.jobId())||(f1?!status.equals("SUCCEEDED"):Set.of("QUEUED","RUNNING").contains(status)||status.isEmpty())||!result.hasNonNull("result"))return Optional.empty();return Optional.of(F1JobChecks.narrative(result));}catch(IOException failure){return Optional.empty();}
    }
    private static int abort(PrintStream out,EvidenceBlockWriter.Context context,String code){EvidenceBlockWriter.write(out,List.of(EvidenceBlockWriter.record(context,Map.of("kind","RUN","status","ABORTED","reasonCode",code))));return 2;}

    /** 대상 레코드의 {@code parentRunId} 가 이번 회차와 다르면 빈 값(exit 2). */
    static Optional<List<RubricViewer.Target>> targets(String file, String parent) {
        if (file == null) return Optional.empty();
        try {
            JsonNode record = CanonicalJson.MAPPER.readTree(Files.readAllBytes(Path.of(file)));
            if (!"RUN".equals(record.path("kind").asText()) || !"COMPLETED".equals(record.path("status").asText()) || !parent.equals(record.path("parentRunId").asText())
                || !record.path("rubricTargets").isArray()) return Optional.empty();
            var targets = new ArrayList<RubricViewer.Target>();
            for (JsonNode target : record.path("rubricTargets")) {
                if (!target.path("answerSha256").asText().matches("[0-9a-f]{64}")||!target.path("jobId").asText().matches("[A-Za-z0-9_:-]{1,160}")) return Optional.empty();
                targets.add(new RubricViewer.Target(target.path("leafId").asText(), target.path("fixtureId").asText(), target.path("jobId").asText(), target.path("answerSha256").asText()));
            }
            return Optional.of(List.copyOf(targets));
        } catch (IOException | RuntimeException e) { return Optional.empty(); }
    }
}
