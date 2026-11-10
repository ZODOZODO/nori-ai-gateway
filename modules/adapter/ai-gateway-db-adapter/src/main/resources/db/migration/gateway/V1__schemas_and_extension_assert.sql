-- 확장 설치 책임은 superuser bootstrap에만 두고 여기서는 계약을 검증합니다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector' AND extversion = '0.8.7') THEN
        RAISE EXCEPTION '필수 vector 확장 버전이 일치하지 않습니다';
    END IF;
END $$;
CREATE SCHEMA gw AUTHORIZATION aigw_migrator;
CREATE SCHEMA kn AUTHORIZATION aigw_migrator;
GRANT USAGE ON SCHEMA gw, kn, gw_flyway TO aigw_app, aigw_backup;
GRANT SELECT ON gw_flyway.flyway_schema_history TO aigw_app, aigw_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE aigw_migrator IN SCHEMA gw, kn GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aigw_app;
ALTER DEFAULT PRIVILEGES FOR ROLE aigw_migrator IN SCHEMA gw, kn GRANT USAGE, SELECT ON SEQUENCES TO aigw_app;
ALTER DEFAULT PRIVILEGES FOR ROLE aigw_migrator IN SCHEMA gw, kn GRANT SELECT ON TABLES TO aigw_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE aigw_migrator IN SCHEMA gw, kn GRANT SELECT ON SEQUENCES TO aigw_backup;
