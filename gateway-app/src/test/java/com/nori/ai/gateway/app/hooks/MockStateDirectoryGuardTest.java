package com.nori.ai.gateway.app.hooks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MockStateDirectoryGuardTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static final AtomicInteger fileIo=new AtomicInteger();
    @Configuration(proxyBeanMethods=false) static class Probe {
        @Bean Object fileIoProbe(org.springframework.core.env.Environment environment)throws java.io.IOException {
            fileIo.incrementAndGet();String path=environment.getProperty("ai.gateway.tcmock.state-dir");
            if(path!=null){var state=java.nio.file.Path.of(path);java.nio.file.Files.createDirectories(state);java.nio.file.Files.writeString(state.resolve("io-probe"),"probe");}
            return new Object();
        }
    }
    private ApplicationContextRunner runner(String... profiles){return new ApplicationContextRunner().withUserConfiguration(MockStateDirectoryGuard.class,Probe.class)
        .withInitializer(context->context.getEnvironment().setActiveProfiles(profiles));}
    @Test void rejectsBeforeMockSingletonIoOutsideVerifyOrWithProdOrInvalidPath() {
        for(String[] profiles:new String[][]{{},{"prod"},{"verify","prod"}}){fileIo.set(0);runner(profiles).withPropertyValues("ai.gateway.tcmock.state-dir="+directory.resolve("rejected")).run(context->{assertThat(context).hasFailed();assertThat(fileIo).hasValue(0);assertThat(java.nio.file.Files.exists(directory.resolve("rejected"))).isFalse();});}
        for(String invalid:new String[]{""," ","invalid"+(char)0+"path"}){fileIo.set(0);runner("verify").withPropertyValues("ai.gateway.tcmock.state-dir="+invalid).run(context->{assertThat(context).hasFailed();assertThat(fileIo).hasValue(0);});}
    }
    @Test void absentOrValidVerifySettingPreservesNormalCreationWithoutNewHookEnabledPolicy() {
        fileIo.set(0);runner().run(context->assertThat(context).hasNotFailed());
        runner("verify").withPropertyValues("ai.gateway.tcmock.state-dir="+directory.resolve("accepted"),"ai.gateway.test-hooks.enabled=false").run(context->assertThat(context).hasNotFailed());
        assertThat(fileIo).hasValue(2);
        assertThat(java.nio.file.Files.exists(directory.resolve("accepted/io-probe"))).isTrue();
    }
}
