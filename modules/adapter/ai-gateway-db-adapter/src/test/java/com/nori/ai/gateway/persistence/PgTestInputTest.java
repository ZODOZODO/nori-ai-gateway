package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("stage-F1") @Tag("label-MOCK_API")
class PgTestInputTest {
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "postgres:latest", "pgvector/pgvector:0.8.7-pg18-trixie", "@sha256:0000000000000000000000000000000000000000000000000000000000000000",
        "pgvector/pgvector:tag@sha256:INVALID"})
    void missingUnpinnedAndMalformedImagesFailBeforeAnyContainer(String image) {
        assertThatThrownBy(() -> new PgTestDatabase(image, Path.of(System.getProperty("aigw.rootDir"))))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("고정된 PostgreSQL 시험 이미지가 필요합니다");
    }
    @Test void validPinStillRequiresActualCanonicalSourceHashBinding() {
        assertThatThrownBy(() -> new PgTestDatabase("pgvector/pgvector:tag@sha256:" + "0".repeat(64),
            Path.of(System.getProperty("aigw.rootDir"))))
            .isInstanceOf(IllegalStateException.class).hasMessage("PostgreSQL 정본 입력 지문이 일치하지 않습니다");
    }
}
