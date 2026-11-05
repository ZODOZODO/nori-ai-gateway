\set ECHO none
\set ON_ERROR_STOP on
-- 비밀번호가 오류 문맥에 섞이지 않도록 클라이언트 오류를 SQLSTATE로 제한합니다.
\set VERBOSITY sqlstate
\getenv migrator_pw AIGW_MIGRATOR_PASSWORD
\getenv app_pw AIGW_APP_PASSWORD
\getenv backup_pw AIGW_BACKUP_PASSWORD
BEGIN;
CREATE EXTENSION IF NOT EXISTS vector;
SELECT 'CREATE ROLE aigw_migrator LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_migrator') \gexec
SELECT 'CREATE ROLE aigw_app LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_app') \gexec
SELECT 'CREATE ROLE aigw_backup LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_backup') \gexec
ALTER ROLE aigw_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'migrator_pw';
ALTER ROLE aigw_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_pw';
ALTER ROLE aigw_backup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'backup_pw';
REVOKE ALL ON DATABASE ai_gateway FROM PUBLIC;
GRANT CONNECT ON DATABASE ai_gateway TO aigw_migrator, aigw_app, aigw_backup;
GRANT CREATE ON DATABASE ai_gateway TO aigw_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT EXECUTE ON FUNCTION pg_control_system() TO aigw_app;
\if :acceptance_role_enabled
\getenv acceptance_pw AIGW_ACCEPTANCE_PASSWORD
SELECT 'CREATE ROLE aigw_acceptance_ro LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_acceptance_ro') \gexec
ALTER ROLE aigw_acceptance_ro LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 2 PASSWORD :'acceptance_pw';
ALTER ROLE aigw_acceptance_ro SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE ai_gateway TO aigw_acceptance_ro;
GRANT pg_read_all_data TO aigw_acceptance_ro;
\else
SELECT 'REVOKE pg_read_all_data FROM aigw_acceptance_ro' WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_acceptance_ro') \gexec
SELECT 'REVOKE ALL ON DATABASE ai_gateway FROM aigw_acceptance_ro' WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_acceptance_ro') \gexec
DROP ROLE IF EXISTS aigw_acceptance_ro;
\endif
COMMIT;
