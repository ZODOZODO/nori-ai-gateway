package com.nori.ai.gateway.integration.loki;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class TcSourceAllowlistTest {
    private String config() throws Exception {
        try (var stream = getClass().getResourceAsStream("/config/sources/tc-sources-v1.json")) { assertThat(stream).isNotNull(); return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }
    @Test void shippedSourceDeclarationKeepsFourTcAppsAndDeniesUnapprovedGitlab() throws Exception {
        var policy = TcSourceAllowlist.parse(config());
        assertThat(policy.repository("nori-tc-gateway")).isEmpty();
    }
    @Test void foreignOwnerSelectorNamespaceOrMissingGitlabListsFailClosed() throws Exception {
        String config = config();
        for (String invalid : new String[]{config.replace("\"TC\"", "\"WM\""), config.replace("tc-business", "wm-ui-backend"),
                config.replace("\"namespace\": \"tc\"", "\"namespace\": \"wm\""), config.replace("\"repositories\": []", "\"repositories\": null"), "{}", "{"})
            assertThatThrownBy(() -> TcSourceAllowlist.parse(invalid)).isInstanceOf(IllegalArgumentException.class).hasMessage("TC Source 허용 목록이 잘못되었습니다");
    }
}
