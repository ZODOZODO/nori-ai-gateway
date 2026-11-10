package com.nori.ai.gateway.persistence.schema;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@Tag("stage-F1") @Tag("label-MOCK_API")
class SchemaVersionGuardTest {
    @Test void comparesNumericVersionsIncludingV10AndSegments() {
        assertThat(SchemaVersionGuard.latestVersion(List.of("V3__three.sql", "V3_1__auth.sql")).getVersion()).isEqualTo("3.1");
        assertThat(SchemaVersionGuard.latestVersion(List.of("V3_1__auth.sql", "V4__four.sql")).getVersion()).isEqualTo("4");
        assertThat(SchemaVersionGuard.latestVersion(List.of("V2__two.sql", "V10__ten.sql", "V9__nine.sql")).getVersion()).isEqualTo("10");
        assertThat(SchemaVersionGuard.latestVersion(List.of("V10_2__two.sql", "V10_11__eleven.sql")).getVersion()).isEqualTo("10.11");
        assertThatThrownBy(() -> SchemaVersionGuard.latestVersion(List.of("R__repeatable.sql"))).isInstanceOf(IllegalStateException.class);
    }
}
