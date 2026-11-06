package com.nori.ai.gateway.tcmock.verify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** AC-41 검사기의 디코드 보장을 양성(인코딩마다 실제 위치·인코딩 보고)과 무관 음성으로 확인한다. */
class ResidueScannerTest {
    private static final String SENTINEL = "SENTINEL:residue-probe";

    private static String unicodeEscaped(String value) {
        var out = new StringBuilder();
        value.chars().forEach(c -> out.append(String.format("\\u%04x", c)));
        return out.toString();
    }
    private static String base64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String base64Url(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }

    @Test void plainAndLongBase64AreFound() {
        var report = ResidueScanner.scan(Map.of("db:row", "앞 " + SENTINEL + " 뒤", "log:line", "value=" + base64("prefix " + SENTINEL + " suffix")), Set.of(SENTINEL));
        assertThat(report.matches()).contains(new ResidueScanner.Match("db:row", "PLAIN"), new ResidueScanner.Match("log:line", "BASE64_DECODED"));
        assertThat(report.matches()).doesNotContain(new ResidueScanner.Match("log:line", "PLAIN"));
    }

    @Test void jsonUnicodeEscapeIsFound() {
        String json = "{\"answer\":\"" + unicodeEscaped(SENTINEL) + "\"}";
        assertThat(json).doesNotContain(SENTINEL);
        var report = ResidueScanner.scan(Map.of("response:body", json), Set.of(SENTINEL));
        assertThat(report.matches()).containsExactly(new ResidueScanner.Match("response:body", "JSON_UNESCAPED"));
    }

    @Test void jwsPayloadWithJsonUnicodeEscapeIsFound() {
        String header = base64Url("{\"alg\":\"ES256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"actor\",\"dlg\":\"" + unicodeEscaped(SENTINEL) + "\"}");
        String jws = header + "." + payload + "." + base64Url("signature-bytes-not-checked");
        var report = ResidueScanner.scan(Map.of("event:payload", "{\"assertion\":\"" + jws + "\"}"), Set.of(SENTINEL));
        assertThat(report.matches()).containsExactly(new ResidueScanner.Match("event:payload", "BASE64_JSON_UNESCAPED"));
    }

    @Test void urlEncodedJsonUnicodeEscapeIsFound() {
        String query = "q=" + URLEncoder.encode("{\"text\":\"" + unicodeEscaped(SENTINEL) + "\"}", StandardCharsets.UTF_8);
        assertThat(query).doesNotContain(SENTINEL).doesNotContain("\\u0053");
        var report = ResidueScanner.scan(Map.of("log:url", query), Set.of(SENTINEL));
        assertThat(report.matches()).containsExactly(new ResidueScanner.Match("log:url", "URL_JSON_UNESCAPED"));
    }

    @Test void shortSentinelsOfEightToElevenBytesAreFoundInStandardAndUrlBase64() {
        for (String sentinel : List.of("S8:abcde", "S9:abcdef", "S10:abcdef", "S11:abcdefg")) {
            assertThat(sentinel.getBytes(StandardCharsets.UTF_8).length).isBetween(8, 11);
            var report = ResidueScanner.scan(Map.of("audit:standard", "{\"v\":\"" + base64(sentinel) + "\"}", "audit:url", "token " + base64Url(sentinel) + " end"), Set.of(sentinel));
            assertThat(report.matches()).as(sentinel)
                .containsExactlyInAnyOrder(new ResidueScanner.Match("audit:standard", "BASE64_DECODED"), new ResidueScanner.Match("audit:url", "BASE64_DECODED"));
        }
    }

    @Test void unrelatedTextAndEncodingsReportNothingAndTheReportCarriesNoRawValue() {
        var sources = Map.of(
            "db:row", "{\"answer\":\"" + unicodeEscaped("SENTINEL:other-value") + "\",\"note\":\"SENTINEL residue probe\"}",
            "log:line", "value=" + base64("unrelated content of similar length") + " q=" + URLEncoder.encode("{\"x\":\"" + unicodeEscaped("harmless") + "\"}", StandardCharsets.UTF_8),
            "event:jws", base64Url("{\"alg\":\"ES256\"}") + "." + base64Url("{\"sub\":\"" + unicodeEscaped("another-actor") + "\"}") + ".c2ln");
        var report = ResidueScanner.scan(sources, Set.of(SENTINEL));
        assertThat(report.matchCount()).isZero();
        assertThat(report.matches()).isEmpty();
        var found = ResidueScanner.scan(Map.of("db:row", SENTINEL), Set.of(SENTINEL));
        assertThat(found.toString()).doesNotContain(SENTINEL).contains("matchCount=").contains("db:row");
    }

    @Test void shortOrMissingSentinelsAreRejected() {
        assertThatThrownBy(() -> ResidueScanner.scan(Map.of("db:row", "x"), Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResidueScanner.scan(Map.of("db:row", "x"), Set.of("short"))).isInstanceOf(IllegalArgumentException.class);
    }
}
