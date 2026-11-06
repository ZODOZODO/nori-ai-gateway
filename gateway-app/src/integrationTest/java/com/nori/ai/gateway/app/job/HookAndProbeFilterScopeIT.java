package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.TestHookAuthFilter;
import com.nori.ai.gateway.app.security.ServiceTokenAuthFilter;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HookAndProbeFilterScopeIT extends P09QueueSupport {
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{P09AdmissionEndpoint.class,Observation.class};}
    @TestConfiguration(proxyBeanMethods=false) static class Observation{
        @Bean static BeanPostProcessor observe(){return new BeanPostProcessor(){@Override @SuppressWarnings({"rawtypes","unchecked"})public Object postProcessAfterInitialization(Object bean,String name){if(bean instanceof FilterRegistrationBean registration)registration.setFilter(spy(registration.getFilter()));return bean;}};}
    }
    @Test void actualHttpFiltersHaveExactlyTheirOwnRoutesAndProbesAreOutsideBoth()throws Exception{
        var registrations=context.getBeansOfType(FilterRegistrationBean.class).values();
        var service=registrations.stream().filter(r->r.getFilter() instanceof ServiceTokenAuthFilter).findFirst().orElseThrow();
        var hooks=registrations.stream().filter(r->r.getFilter() instanceof TestHookAuthFilter).findFirst().orElseThrow();
        assertThat(service.getUrlPatterns()).containsExactly("/v1/*");assertThat(hooks.getUrlPatterns()).containsExactly("/__test/*");
        assertThat(context.getBeansOfType(ServiceTokenAuthFilter.class)).isEmpty();assertThat(context.getBeansOfType(TestHookAuthFilter.class)).isEmpty();
        for(String probe:List.of("/actuator/health/liveness","/actuator/health/readiness")){
            clearInvocations(service.getFilter(),hooks.getFilter());var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(probe)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");verify(service.getFilter(),never()).doFilter(any(),any(),any());verify(hooks.getFilter(),never()).doFilter(any(),any(),any());
        }
        clearInvocations(service.getFilter(),hooks.getFilter());assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/v1/analyses/missing")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        verify(service.getFilter(),times(1)).doFilter(any(),any(),any());verify(hooks.getFilter(),never()).doFilter(any(),any(),any());
        for(String token:Arrays.asList(null,"wrong",hookToken)){
            clearInvocations(service.getFilter(),hooks.getFilter());var response=hook("/__test/clock","GET",null,token);assertThat(response.statusCode()).isEqualTo(hookToken.equals(token)?200:404);
            verify(hooks.getFilter(),times(1)).doFilter(any(),any(),any());verify(service.getFilter(),never()).doFilter(any(),any(),any());
        }
        var serviceOnly=HttpRequest.newBuilder(uri("/__test/clock")).header("Authorization","Bearer "+serviceToken).GET().build();assertThat(HttpClient.newHttpClient().send(serviceOnly,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
    }
}
