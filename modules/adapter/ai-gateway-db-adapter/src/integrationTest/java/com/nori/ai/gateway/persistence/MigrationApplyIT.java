package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationApplyIT extends PgIntegrationSupport {
    @Test void v5RejectsExistingJobWithoutPartialColumnsOrBackfill() throws Exception {
        try(var database=rawDatabase()) {
            database.bootstrap();
            var flyway=database.flywayConfiguration().target("4").load();flyway.migrate();
            try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
                sql.execute("INSERT INTO gw.conversation VALUES ('legacy-c','TC','actor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '1 day','ACTIVE')");
                sql.execute("INSERT INTO gw.job(id,conversation_id,owner_system,actor_ref,status,created_at,request_digest,expected_path,request_perm,request_pdg) VALUES ('legacy-j','legacy-c','TC','actor','QUEUED',CURRENT_TIMESTAMP,'digest','/v1/query',ARRAY[]::text[],repeat('a',64))");
            }
            assertThatThrownBy(database::migrate).hasStackTraceContaining("V5 requires an empty gw.job");
            try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
                try(var rows=sql.executeQuery("SELECT count(*) FROM gw.job WHERE id='legacy-j'")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isOne();}
                try(var rows=sql.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='gw' AND table_name='job' AND column_name IN ('request_message_id','request_input')")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
                try(var rows=sql.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("4");}
            }
        }
    }
    @Test void v7RejectsActiveJobWithoutAuditColumnsOrBackfillAndAppliesOverTerminalJobs() throws Exception {
        try(var database=rawDatabase()) {
            database.bootstrap();
            var flyway=database.flywayConfiguration().target("5").load();flyway.migrate();
            try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
                sql.execute("INSERT INTO gw.conversation VALUES ('legacy-c','TC','actor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '1 day','ACTIVE')");
                for(String[] job:new String[][]{{"legacy-done","SUCCEEDED"},{"legacy-active","RUNNING"}}) {
                    sql.execute("INSERT INTO gw.message(id,conversation_id,owner_system,role,created_at,content_masked,resource_refs,tool_execution_refs) VALUES ('m-"+job[0]+"','legacy-c','TC','user',CURRENT_TIMESTAMP,'질문','[]','[]')");
                    sql.execute("INSERT INTO gw.job(id,conversation_id,owner_system,actor_ref,status,created_at,request_digest,expected_path,request_perm,request_pdg,request_message_id,request_input) VALUES ('"+job[0]+"','legacy-c','TC','actor','"+job[1]+"',CURRENT_TIMESTAMP,'digest','/v1/query',ARRAY[]::text[],repeat('a',64),'m-"+job[0]+"','{}')");
                }
            }
            assertThatThrownBy(database::migrate).hasStackTraceContaining("V7 requires no active gw.job");
            try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
                try(var rows=sql.executeQuery("SELECT count(*) FROM gw.audit_event")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
                try(var rows=sql.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='gw' AND table_name='audit_event' AND column_name IN ('service_ref','task_type','result_status')")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
                try(var rows=sql.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("5");}
                sql.execute("UPDATE gw.job SET status='CANCELLED', terminal_at=CURRENT_TIMESTAMP WHERE id='legacy-active'");
            }
            // terminal Job만 남으면 원점 없는 active Job이 없으므로 적용되며 가짜 감사 행을 만들지 않습니다.
            database.migrate();
            try(var connection=database.connection(database.user());var sql=connection.createStatement()) {
                try(var rows=sql.executeQuery("SELECT count(*) FROM gw.audit_event")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
                try(var rows=sql.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='gw' AND table_name='audit_event' AND column_name IN ('service_ref','task_type','result_status') AND is_nullable='YES'")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(3);}
                try(var rows=sql.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("7");}
            }
        }
    }
    @Test void singleAccountAppliesExactlyV1ToV3AndOwnsAllObjects() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap();
            database.flywayConfiguration().target("3").load().migrate();
            var checksums = new java.util.LinkedHashMap<String, Integer>();
            try (var connection = database.connection(database.user()); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new java.util.ArrayList<String>(); while (rows.next()) versions.add(rows.getString(1));
                    assertThat(versions).containsExactly("1", "2", "3");
                }
                try (var rows = statement.executeQuery("SELECT version, checksum FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    while (rows.next()) checksums.put(rows.getString(1), rows.getInt(2));
                }
                try (var rows = statement.executeQuery("SELECT attnotnull FROM pg_attribute WHERE attrelid = 'gw.audit_event'::regclass AND attname IN ('owner_system','actor_ref')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getBoolean(1)).isTrue(); } assertThat(count).isEqualTo(2);
                }
                try (var rows = statement.executeQuery("SELECT tablename, tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getString(2)).isEqualTo(database.user()); }
                    assertThat(count).isEqualTo(16);
                }
            }
            database.migrate(); database.migrate();
            try (var connection = database.connection(database.user()); var statement = connection.createStatement()) {
                try(var columns=statement.executeQuery("SELECT column_name,is_nullable,column_default FROM information_schema.columns WHERE table_schema='gw' AND table_name='job' ORDER BY ordinal_position")){
                    var names=new java.util.ArrayList<String>();int authority=0;
                    while(columns.next()){String name=columns.getString(1);names.add(name);if(java.util.Set.of("request_perm","request_pdg").contains(name)){authority++;assertThat(columns.getString(2)).isEqualTo("NO");assertThat(columns.getString(3)).isNull();}}
                    assertThat(authority).isEqualTo(2);
                    var backup=new java.util.HashSet<>(java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("aigw.rootDir"),"ops/backup/job-columns-v1.txt")));assertThat(backup).doesNotContain("delegation_id");backup.add("delegation_id");assertThat(names).containsExactlyInAnyOrderElementsOf(backup);
                }
                try (var rows = statement.executeQuery("SELECT version, checksum FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new java.util.ArrayList<String>();
                    while (rows.next()) { String version = rows.getString(1); versions.add(version); if (checksums.containsKey(version)) assertThat(rows.getInt(2)).isEqualTo(checksums.get(version)); }
                    assertThat(versions).containsExactly("1", "2", "3", "3.1", "4", "5", "7");
                }
                try (var rows = statement.executeQuery("SELECT tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getString(1)).isEqualTo(database.user()); } assertThat(count).isEqualTo(16);
                }
            }
        }
    }
    @Test void missingExtensionFailsMigrationWithoutCreatingGatewayObjects() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap();
            try (var connection = database.connection("postgres"); var statement = connection.createStatement()) { statement.execute("DROP EXTENSION vector"); }
            assertThatThrownBy(database::migrate).isInstanceOf(RuntimeException.class).hasStackTraceContaining("필수 vector 확장 버전이 일치하지 않습니다");
            try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname IN ('gw','kn')")) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
