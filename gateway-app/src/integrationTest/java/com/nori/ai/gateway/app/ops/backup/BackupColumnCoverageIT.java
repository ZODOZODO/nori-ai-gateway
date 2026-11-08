package com.nori.ai.gateway.app.ops.backup;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

/** 백업이 내보내는 gw.job 열 목록 = 실제 스키마 열 − delegation_id. 새 열을 목록에 넣지 않으면 실패한다. */
@ExtendWith(DockerRequiredExtension.class)
class BackupColumnCoverageIT {
    @Test void jobColumnFileCoversEveryJobColumnExceptDelegation() throws Exception {
        Path root = Path.of(System.getProperty("aigw.rootDir"));
        List<String> listed = Files.readAllLines(root.resolve("ops/backup/job-columns-v1.txt")).stream().filter(line -> !line.isBlank()).toList();
        try (var database = new PgTestDatabase(System.getProperty("aigw.pgTestImage"), root).start()) {
            database.bootstrap(false);
            database.migrate();
            var actual = new ArrayList<String>();
            try (var connection = database.connection("aigw_backup"); var statement = connection.prepareStatement(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = 'gw' AND table_name = 'job' ORDER BY ordinal_position");
                 var rows = statement.executeQuery()) {
                while (rows.next()) actual.add(rows.getString(1));
            }
            assertThat(listed).doesNotHaveDuplicates().doesNotContain("delegation_id").contains("request_message_id", "request_input");
            var expected = new ArrayList<>(listed); expected.add("delegation_id");
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
