package com.nori.ai.gateway.integration.tokenizer;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 런타임 다운로드 0: SDK 다운로드 경로는 오프라인 모드로 실패해야 하고, 계수기 적재 중 원격 연결 시도가 없어야 한다. */
class TokenizerOfflineTest {
    /** docs/gates/f1/stack-compat.md OTLP·DJL 절의 DJL tokenizers 0.40.0 JAR·native pin. */
    static final String DJL_JAR_SHA256 = "569b1fa597e362ac11cd527e5deacd52cd827c8f5133a573572e2cd7051c5768";
    static final Map<String, String> NATIVE_SHA256 = Map.of(
        "native/lib/linux-aarch64/cpu/libtokenizers.so", "f78a889b3305953a5048c5809bb477d4feb3655456a26acb49a0326699672e39",
        "native/lib/linux-x86_64/cpu/libtokenizers.so", "edf58c92155e0fe4fae5231e782a1f02deeb5881de7bbcd1195f6e25009da90a",
        "native/lib/osx-aarch64/cpu/libtokenizers.dylib", "cca1c15bcbfa47b1c942e9d395ea8353161e5ed709f74826ad5faebcd05544ab",
        "native/lib/win-x86_64/cpu/libgcc_s_seh-1.dll", "32ce10abc802edc83c36d62e0e9f9389bab0c4bfeacf01cc98726da81094b80a",
        "native/lib/win-x86_64/cpu/libstdc++-6.dll", "a0b8f623f0da655a30873ef7d6b6be0e6942c34535abcaecce142fa0ab1cef4f",
        "native/lib/win-x86_64/cpu/libwinpthread-1.dll", "567ca7ae7ecdfb8799b28c41ac3d1a2cc46f4ee2e72c511f027c44250498e893",
        "native/lib/win-x86_64/cpu/tokenizers.dll", "7a13309eaeea059aa7a796ed4ab4727cfe0affcafb7b8171cf887cf7b07fea57");

    /** 모든 http(s) 연결 시도를 기록하고 닫힌 loopback 포트로 보내 실패시킨다(외부 네트워크 접촉 0). */
    static final class RecordingProxySelector extends ProxySelector {
        final List<URI> attempts = new CopyOnWriteArrayList<>();
        private final Proxy closed;
        RecordingProxySelector(int closedPort) { closed = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", closedPort)); }
        @Override public List<Proxy> select(URI uri) { attempts.add(uri); return List.of(closed); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) {}
    }

    private ProxySelector previous;
    private RecordingProxySelector recorder;
    private String offline;

    @BeforeEach void installRecorder() throws IOException {
        previous = ProxySelector.getDefault();
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        recorder = new RecordingProxySelector(port);
        ProxySelector.setDefault(recorder);
        offline = System.getProperty(HfTokenizerCounter.OFFLINE_PROPERTY);
    }
    @AfterEach void restore() {
        ProxySelector.setDefault(previous);
        if (offline == null) System.clearProperty(HfTokenizerCounter.OFFLINE_PROPERTY); else System.setProperty(HfTokenizerCounter.OFFLINE_PROPERTY, offline);
    }

    @Test void recorderObservesARealConnectionAttempt() {
        // 기록기가 실제 연결 시도를 잡는다는 대조(아래 0건 판정이 공허하지 않음을 보인다).
        assertThatThrownBy(() -> URI.create("http://download.invalid/probe").toURL().openConnection().connect()).isInstanceOf(IOException.class);
        assertThat(recorder.attempts).extracting(URI::getHost).contains("download.invalid");
    }

    @Test void loadingTheCounterForcesOfflineModeAndOpensNoRemoteConnection() {
        System.clearProperty(HfTokenizerCounter.OFFLINE_PROPERTY);
        try (var counter = new HfTokenizerCounter()) {
            assertThat(System.getProperty(HfTokenizerCounter.OFFLINE_PROPERTY)).isEqualTo("true");
            assertThat(Utils.isOfflineMode()).isTrue();
            assertThat(counter.countText("설비 상태를 조회해 주세요.")).isPositive();
        }
        assertThat(recorder.attempts).isEmpty();
    }

    @Test void sdkDownloadAttemptFailsInOfflineModeWithoutAnyConnection() {
        HfTokenizerCounter.requireOfflineMode();
        // DJL native 다운로드(LibUtils.downloadJniLib)와 hub 적재가 쓰는 SDK 다운로드 진입점
        assertThatThrownBy(() -> { try (InputStream ignored = Utils.openUrl("https://publish.djl.ai/tokenizers/0.40.0/jnilib/probe")) { fail("다운로드가 열렸습니다"); } })
            .isInstanceOf(IOException.class).hasMessageContaining("Offline mode");
        assertThat(recorder.attempts).isEmpty();
    }

    @Test void explicitOnlineModeRefusesToStart() {
        System.setProperty(HfTokenizerCounter.OFFLINE_PROPERTY, "false");
        assertThatThrownBy(HfTokenizerCounter::new).isInstanceOf(IllegalStateException.class).hasMessage("TOKENIZER_OFFLINE_REQUIRED");
        assertThat(recorder.attempts).isEmpty();
    }

    @Test void bundledDjlJarAndNativeBinariesMatchTheStackCompatPins() throws Exception {
        Path jar = Path.of(HuggingFaceTokenizer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(HfTokenizerCounter.sha256(Files.readAllBytes(jar))).isEqualTo(DJL_JAR_SHA256);
        for (var entry : NATIVE_SHA256.entrySet()) {
            var urls = Collections.list(HfTokenizerCounter.class.getClassLoader().getResources(entry.getKey()));
            assertThat(urls).as(entry.getKey()).hasSize(1);
            try (InputStream in = urls.getFirst().openStream()) { assertThat(HfTokenizerCounter.sha256(in.readAllBytes())).as(entry.getKey()).isEqualTo(entry.getValue()); }
        }
    }
}
