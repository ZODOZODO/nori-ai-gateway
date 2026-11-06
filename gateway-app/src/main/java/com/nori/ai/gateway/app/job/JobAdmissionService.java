package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.config.CapacityProfile;
import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.core.job.InferenceReadiness;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.maintenance.QueueMaintenanceRepository;
import com.nori.ai.gateway.persistence.queue.*;
import com.nori.ai.gateway.persistence.repository.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

public final class JobAdmissionService {
    public record AdmissionRequest(String path,String conversationId,String question,Map<String,Object> requestFields,String idempotencyKey,String delegation) {
        public AdmissionRequest {
            if(!Set.of("/v1/query","/v1/analyses").contains(path)&&!path.matches("/v1/conversations/[^/]+/messages"))throw new IllegalArgumentException("접수 경로가 잘못되었습니다");
            if(question==null||question.isBlank()||delegation==null||!delegation.matches("[A-Za-z0-9_-]{22,128}"))throw new IllegalArgumentException("접수 입력이 잘못되었습니다");
            // 선택 필드의 명시적 null을 멱등 digest에 보존하면서 호출자 Map 변경과 분리합니다.
            requestFields=com.nori.ai.gateway.core.job.JobRequestInput.immutableFields(requestFields);
            if(idempotencyKey!=null&&idempotencyKey.isBlank())throw new IllegalArgumentException("멱등 키가 비어 있습니다");
        }
        @Override public String toString(){return "AdmissionRequest[보호됨]";}
    }
    public record Accepted(String jobId,String conversationId,String messageId,int queuePosition){}
    public static final class Rejected extends RuntimeException {
        private final int status;private final String error;private final int retryAfter;
        public Rejected(int status,String error,int retryAfter){super("요청을 접수할 수 없습니다");this.status=status;this.error=error;this.retryAfter=retryAfter;}
        public int status(){return status;}public String error(){return error;}public int retryAfter(){return retryAfter;}
    }
    public record AdmissionCommitted(String jobId){}
    private final TransactionTemplate transactions;
    private final QueueMaintenanceRepository control;
    private final IdempotentAdmissionLookup lookup;
    private final InferenceReadiness readiness;
    private final ReadinessGate recovery;
    private final CapacityProfile capacity;
    private final QueueJobRepository queue;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final JobRepository jobs;
    private final JobEventRepository events;
    private final ContentSanitizer sanitizer;
    private final Optional<TestHookRegistry> hooks;
    private final Optional<PausePointRegistry> pauses;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final JsonMapper json=JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private final java.util.concurrent.atomic.AtomicInteger uniqueFallbacks=new java.util.concurrent.atomic.AtomicInteger();
    public int uniqueFallbackCount(){return uniqueFallbacks.get();}
    public JobAdmissionService(PlatformTransactionManager manager,QueueMaintenanceRepository control,IdempotentAdmissionLookup lookup,
            InferenceReadiness readiness,CapacityProfile capacity,QueueJobRepository queue,ConversationRepository conversations,
            MessageRepository messages,JobRepository jobs,JobEventRepository events,ContentSanitizer sanitizer,
            Optional<TestHookRegistry> hooks,Optional<PausePointRegistry> pauses,org.springframework.context.ApplicationEventPublisher publisher,ReadinessGate recovery){
        transactions=new TransactionTemplate(manager);transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.control=control;this.lookup=lookup;this.readiness=readiness;this.capacity=capacity;this.queue=queue;this.conversations=conversations;
        this.messages=messages;this.jobs=jobs;this.events=events;this.sanitizer=sanitizer;this.hooks=hooks;this.pauses=pauses;this.publisher=publisher;this.recovery=recovery;
    }
    public Accepted accept(VerifiedSystemScope scope,AdmissionRequest request){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("접수는 독립 트랜잭션이어야 합니다");
        String digest=digest(request);var fast=lookup.find(scope,request.idempotencyKey(),digest);if(fast.isPresent())return fast.get();
        pause(PausePointRegistry.Point.ADMISSION_FAST_MISS,scope.actorRef().value()+":"+request.idempotencyKey());
        try{return transactions.execute(tx->{
            control.lockControl();
            // READ COMMITTED의 새 문장 snapshot으로 직전 커밋을 먼저 확인합니다.
            var existing=lookup.find(scope,request.idempotencyKey(),digest);if(existing.isPresent())return existing.get();
            if(!recovery.ready()||!readiness.ready())throw new Rejected(503,"INFERENCE_UNAVAILABLE",60);
            int queued=queue.count("QUEUED");if(queued>=capacity.queuedDepth())throw new Rejected(503,"QUEUE_FULL",60);
            hooks.ifPresent(TestHookRegistry::beginAdmission);
            String conversation=request.conversationId();
            if(conversation==null){conversation=UUID.randomUUID().toString();if(!conversations.insert(scope,conversation))throw new IllegalStateException("대화를 저장할 수 없습니다");}
            else if(conversations.find(scope,conversation).isEmpty())throw new Rejected(404,null,0);
            String jobId=UUID.randomUUID().toString(),messageId=UUID.randomUUID().toString();
            if(hooks.flatMap(h->h.armed(scope.sub(),request.idempotencyKey())).map(TestHookRegistry.Scenario::failSanitizer).orElse(false))throw new IllegalStateException("마스킹 적용을 확인할 수 없습니다");
            var sanitized=sanitizer.sanitize(request.question());
            if(!messages.insert(scope,conversation,new MessageRepository.Message(messageId,"user",sanitized.text(),json.writeValueAsString(request.requestFields().getOrDefault("resourceRefs",List.of())),"[]",null,null)))throw new Rejected(404,null,0);
            if(!jobs.insert(scope,new JobRepository.NewJob(jobId,conversation,request.idempotencyKey(),digest,request.path(),messageId,request.requestFields()),request.delegation()))throw new Rejected(404,null,0);
            if(!events.insert(scope,jobId,"job.queued","{\"jobId\":\""+jobId+"\"}",false))throw new IllegalStateException("접수 이벤트를 저장할 수 없습니다");
            hooks.ifPresent(h->h.bindOnAdmission(scope.sub(),request.idempotencyKey(),jobId));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){hooks.ifPresent(TestHookRegistry::admissionCommitted);pause(PausePointRegistry.Point.ADMISSION_AFTER_COMMIT,jobId);publisher.publishEvent(new AdmissionCommitted(jobId));}
            });
            return new Accepted(jobId,conversation,messageId,queued+1);
        });}catch(DuplicateKeyException duplicate){
            uniqueFallbacks.incrementAndGet();
            // 최후 방어는 실패 tx가 완전히 rollback된 뒤에만 재조회합니다.
            return lookup.find(scope,request.idempotencyKey(),digest).orElseThrow(()->duplicate);
        }
    }
    private void pause(PausePointRegistry.Point point,String key){
        if(pauses.isEmpty())return;
        try{pauses.get().hit(point,key);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("접수 대기가 중단되었습니다");}
    }
    private String digest(AdmissionRequest request){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(Arrays.asList(request.path(),request.conversationId(),request.question().strip().replaceAll("\\s+"," "),request.requestFields()))));}
        catch(RuntimeException|NoSuchAlgorithmException failure){throw new IllegalArgumentException("접수 내용을 확인할 수 없습니다");}
    }
}
