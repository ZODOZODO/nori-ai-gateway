package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.config.SourceConnectorConfiguration;
import com.nori.ai.gateway.app.job.HarnessObservationsEndpoint;
import com.nori.ai.gateway.app.job.P09QueueSupport;
import com.nori.ai.gateway.app.job.SourceRetentionAttributesProvider;
import com.nori.ai.gateway.core.retention.SourceRetentionAttributes;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.LokiLogConnector;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import com.nori.ai.gateway.integration.loki.SourceHttpClient;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/**
 * N13 보존 탐침 결속: 복구 완료 전 저장 0, 기동 직후 1회, Gateway Clock +24h 다음 주기, 매 주기 새 명시 범위, 실패 행 불변,
 * 저장값이 P13 SourceRetentionAttributesProvider를 거쳐 VERIFIED_OLDEST로 분류되는지 확인한다. 명시 local 범위는 운영 범위 승인을 대신하지 않는다.
 */
@Tag("stage-F1") @Tag("label-MOCK_API")
class RetentionProbeWiringIT extends P09QueueSupport {
    static final class LocalLoki implements AutoCloseable {
        final HttpServer server;
        final List<String> starts = new CopyOnWriteArrayList<>();
        volatile String failingApp;
        LocalLoki() throws java.io.IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    var query = new LinkedHashMap<String,String>();
                    for (String part : exchange.getRequestURI().getRawQuery().split("&")) { var pair = part.split("=", 2); query.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8)); }
                    String app = LokiSourcePolicy.APPS.stream().filter(value -> query.get("query").contains("app=\"" + value + "\"")).findFirst().orElseThrow();
                    starts.add(app + "@" + query.get("start"));
                    if (app.equals(failingApp)) { exchange.sendResponseHeaders(500, -1); return; }
                    // 창 시작 1시간 뒤의 가장 오래된 관측 한 줄만 돌려줍니다.
                    String oldest = new BigInteger(query.get("start")).add(BigInteger.valueOf(3_600_000_000_000L)).toString();
                    byte[] body = ("{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[{\"stream\":{\"app\":\"" + app
                        + "\",\"namespace\":\"tc\"},\"values\":[[\"" + oldest + "\",\"관측\"]]}]}}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
                }
            });
            server.start();
        }
        URI base() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
        @Override public void close() { server.stop(0); }
    }
    /** 시험이 명시 공급하는 주기별 확인 범위다. 호출마다 그 주기의 Gateway Clock 시각을 받는다. */
    static final class Windows {
        volatile Duration lookback = Duration.ofDays(7);
        final List<String> requested = new CopyOnWriteArrayList<>();
        Optional<TimeWindow> window(String sourceId, Instant now) { requested.add(sourceId); return Optional.of(new TimeWindow(now.minus(lookback), now)); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeInputs {
        @Bean LocalLoki localLoki() throws java.io.IOException { return new LocalLoki(); }
        @Bean Windows probeWindows() { return new Windows(); }
        @Bean SourceConnectorConfiguration.LokiProbeFactory lokiProbeFactory(LocalLoki loki, MeterRegistry meters, OpenTelemetry openTelemetry) {
            var observability = new SourceConnectorConfiguration.FactoryConfiguration().sourceObservability(meters, openTelemetry);
            return new SourceConnectorConfiguration.LokiProbeFactory(new SourceHttpClient(loki.base(), "loki", observability));
        }
        @Bean SourceConnectorConfiguration.ProbeWindowInput probeWindowInput(Windows windows) { return new SourceConnectorConfiguration.ProbeWindowInput(windows::window); }
    }
    @Override protected Class<?>[] extraSources() { return new Class<?>[]{HarnessObservationsEndpoint.class, ProbeInputs.class}; }
    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<>(super.extraApplicationProperties());
        properties.put("ai.gateway.recovery.restore-handler.enabled", false);
        return properties;
    }
    private Map<String,Instant[]> rows() {
        var rows = new TreeMap<String,Instant[]>();
        jdbc().sql("SELECT source_id, verified_oldest_at, probe_limit_at, probed_at FROM gw.source_retention_probe").query(rs -> {
            rows.put(rs.getString(1), new Instant[]{rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant()});
        });
        return rows;
    }

    @Test void probeRunsOnceAfterRecoveryThenEveryGatewayClockDayWithTheNewExplicitWindowAndKeepsFailedRows() throws Exception {
        var scheduler = context.getBean(SourceRetentionProbeScheduler.class);
        await(scheduler::started);
        await(() -> rows().size() == 4);
        Instant start = NOW.minus(Duration.ofDays(7));
        assertThat(rows().keySet()).containsExactlyElementsOf(LokiSourcePolicy.APPS.stream().map(LokiSourcePolicy::retentionSourceId).sorted().toList());
        assertThat(rows().values()).allSatisfy(row -> assertThat(row).containsExactly(start.plus(Duration.ofHours(1)), start, NOW));
        assertThat(context.getBean(SourceRetentionAttributesProvider.class).loki("tc-gateway")).isEqualTo(new SourceRetentionAttributes.VerifiedOldest(start.plus(Duration.ofHours(1))));
        var loki = context.getBean(LocalLoki.class);
        var windows = context.getBean(Windows.class);
        assertThat(loki.starts).hasSize(4);
        clock().advance(Duration.ofHours(23).toSeconds());
        Thread.sleep(2500);
        assertThat(loki.starts).as("24h 전에는 다음 주기가 없습니다").hasSize(4);
        windows.lookback = Duration.ofDays(3);
        loki.failingApp = "tc-business";
        var failures = context.getBean(MeterRegistry.class).counter("aigw.retention.probe.failures");
        double before = failures.count();
        clock().advance(Duration.ofHours(1).toSeconds());
        Instant second = NOW.plus(Duration.ofHours(24));
        // Scheduler는 APPS 순서로 앱을 차례로 처리하므로 첫 행 저장은 주기 완료가 아닙니다.
        // 성공 3앱(마지막 tc-ui-front 포함)의 이번 주기 저장·4앱 범위 요청·tc-business 실패 집계가 모두 끝날 때까지 기다립니다.
        List<String> succeeded = LokiSourcePolicy.APPS.stream().filter(app -> !app.equals("tc-business")).map(LokiSourcePolicy::retentionSourceId).toList();
        await(() -> succeeded.stream().allMatch(id -> rows().get(id)[2].equals(second)) && windows.requested.size() == 8 && failures.count() > before);
        Instant secondStart = second.minus(Duration.ofDays(3));
        assertThat(rows().get(LokiSourcePolicy.retentionSourceId("tc-gateway"))).containsExactly(secondStart.plus(Duration.ofHours(1)), secondStart, second);
        assertThat(rows().get(LokiSourcePolicy.retentionSourceId("tc-business"))).as("측정 실패 행은 바뀌지 않습니다").containsExactly(start.plus(Duration.ofHours(1)), start, NOW);
        assertThat(failures.count()).isGreaterThan(before);
        assertThat(loki.starts).contains("tc-gateway@" + LokiLogConnector.nanos(secondStart));
        assertThat(windows.requested).hasSize(8);
    }

    @Test void pendingRecoveryNeverStartsTheProbeOrWritesRows() throws Exception {
        await(() -> rows().size() == 4);
        context.close(); context = null;
        var migrator = JdbcClient.create(new DriverManagerDataSource(database.jdbcUrl(), "aigw_migrator", database.passwordFor("aigw_migrator")));
        migrator.sql("TRUNCATE gw.source_retention_probe").update();
        // 다른 DB 식별자 기록 = 복원 경로 필요(복구 완료 신호 없음)입니다.
        migrator.sql("UPDATE gw.instance_epoch SET db_system_identifier = 'p14-other-instance'").update();
        startApplication(extraSources());
        var scheduler = context.getBean(SourceRetentionProbeScheduler.class);
        Thread.sleep(2500);
        assertThat(scheduler.started()).isFalse();
        assertThat(context.getBean(RetentionPurgeJob.class).started()).isFalse();
        // 복구 완료 전에는 정리·24h 잔류 판정도 없으므로 실패 신호가 없습니다.
        assertThat(context.getBean(MeterRegistry.class).get(com.nori.ai.gateway.app.observability.MetricsCatalog.RETENTION_PURGE_FAILURES).counter().count()).isZero();
        assertThat(context.getBean(ExpiryEdgeJobCanceller.class).started()).isFalse();
        assertThat(rows()).isEmpty();
        assertThat(context.getBean(LocalLoki.class).starts).isEmpty();
    }
}
