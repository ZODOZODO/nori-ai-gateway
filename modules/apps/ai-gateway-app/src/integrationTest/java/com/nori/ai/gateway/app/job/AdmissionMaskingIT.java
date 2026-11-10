package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.registry.TaskType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionMaskingIT extends P09QueueSupport {
    @Test void eventFailureRollsBackMessageJobSnapshotAndConversationInTheSameAdmission() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
            sql.execute("CREATE FUNCTION gw.p10_reject_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '접수 원자성 시험 실패'; END $$");
            sql.execute("CREATE TRIGGER p10_reject_event BEFORE INSERT ON gw.job_event FOR EACH ROW EXECUTE FUNCTION gw.p10_reject_event()");
        }
        var response=postQuery("actor","event-rollback","원자 접수 시험");assertThat(response.statusCode()).isEqualTo(500);assertThat(response.body()).doesNotContain("p10_reject_event");
        for(String table:java.util.List.of("message","job","job_event","conversation"))assertThat(count(table)).as(table).isZero();
        assertThat(jdbc().sql("SELECT count(request_input) FROM gw.job").query(Integer.class).single()).isZero();
    }
    static final String MASKED_QUESTION="SENTINEL_PRIVATE_123 someone@example.test api_key=abcdef 이름: 홍길동 010-1234-5678";
    static volatile com.nori.ai.gateway.core.sanitize.ContentSanitizer.Sanitized observed;
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,Observation.class};}
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) static class Observation{
        // 같은 접수 tx의 JOB_ACCEPTED 감사도 같은 sanitizer로 serviceRef를 검사하므로(AuditFieldAllowlist) 질문 원문 호출만 관측합니다.
        @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanPostProcessor sanitizerObservation(){return new org.springframework.beans.factory.config.BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){
            if(bean instanceof com.nori.ai.gateway.core.sanitize.ContentSanitizer){var sanitizer=spy((com.nori.ai.gateway.core.sanitize.ContentSanitizer)bean);doAnswer(invocation->{var value=(com.nori.ai.gateway.core.sanitize.ContentSanitizer.Sanitized)invocation.callRealMethod();if(MASKED_QUESTION.equals(invocation.getArgument(0)))observed=value;return value;}).when(sanitizer).sanitize(anyString());return sanitizer;}return bean;
        }};}
    }
    @Test void actualPostStoresOnlySanitizedUserMessageAndFailureRollsBackAllRows()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        var response=postQuery("actor","masked",MASKED_QUESTION);assertThat(response.statusCode()).isEqualTo(202);
        String content=jdbc().sql("SELECT content_masked FROM gw.message").query(String.class).single();assertThat(content).doesNotContain("PRIVATE_123","someone@","abcdef","홍길동","010-").contains("[마스킹]");
        assertThat(observed.text()).isEqualTo(content);assertThat(observed.ruleSetVersion()).isEqualTo("mask-v1");assertThat(observed.applied()).isTrue();
        var before=java.util.List.of(count("job"),count("message"),count("conversation"),count("job_event"));
        hooks().arm("actor","mask-fail",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));
        doThrow(new IllegalStateException("마스킹 적용 확인 실패")).when(context.getBean(com.nori.ai.gateway.core.sanitize.ContentSanitizer.class)).sanitize("비밀 입력");
        var failed=postQuery("actor","mask-fail","비밀 입력");assertThat(failed.statusCode()).isEqualTo(500);assertThat(failed.body()).doesNotContain("비밀 입력");
        assertThat(java.util.List.of(count("job"),count("message"),count("conversation"),count("job_event"))).isEqualTo(before);assertThat(hooks().armed("actor","mask-fail")).isPresent();
    }
}
