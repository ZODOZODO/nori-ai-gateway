\set ECHO none
\set ON_ERROR_STOP on
-- 공유 운영 서버(192.168.0.150)의 서버 담당자 선행조건(docs/runbooks/aigw-db-server-prerequisites.md)을 우리가 superuser 인
-- 격리 시험 컨테이너에 재현한다: vector 0.8.7, DB 를 소유하는 단일 계정, pg_control_system() PUBLIC 실행.
-- 운영 공유 서버에는 실행하지 않는다. 계정 이름·비밀번호는 env 로만 받고, 스키마·테이블 객체는 참조하지 않는다(Flyway 소유).
-- 비밀번호가 오류 문맥에 섞이지 않도록 클라이언트 오류를 SQLSTATE로 제한합니다.
\set VERBOSITY sqlstate
\getenv account_name AI_GATEWAY_DB_USER
\getenv account_pw AI_GATEWAY_DB_PASSWORD
BEGIN;
CREATE EXTENSION IF NOT EXISTS vector;
SELECT format('CREATE ROLE %I LOGIN', :'account_name') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'account_name') \gexec
ALTER ROLE :"account_name" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'account_pw';
SELECT format('ALTER DATABASE %I OWNER TO %I', current_database(), :'account_name') \gexec
GRANT EXECUTE ON FUNCTION pg_control_system() TO PUBLIC;
COMMIT;
