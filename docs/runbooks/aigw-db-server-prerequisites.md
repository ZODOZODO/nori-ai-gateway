# AI Gateway 운영 DB 서버 선행조건 runbook

대상: 공유 DB 서버 192.168.0.150(호스트 `mes-db`, k8s 밖, PostgreSQL 18.6)의 AI Gateway 전용 DB `nori_ai_gateway`. 이 서버는 빌려 쓰는 공유 자원이다(같은 서버에 `nori_tc`·`nori_wm`). Gateway 는 서버 설정을 바꾸지 않고, 서버에 DDL·권한 SQL 을 실행하지 않으며, 서버 변경을 게이트로 두지 않는다. 아래 항목은 서버 담당자가 하는 일이고 이 레포는 결과만 기록한다.

## 선행조건과 상태

| 항목 | 내용 | 상태 |
|---|---|---|
| DB | `nori_ai_gateway` 생성 | 완료(서버 담당자) |
| 확장 | `vector` 0.8.7 설치(V1 이 버전을 검사하고 다르면 migration 실패) | 완료(서버 담당자) |
| 계정 | DB 계정 하나 발급. 앱·migration·백업·수용 oracle 이 같은 계정을 쓴다 | 완료(서버 담당자) |
| 스키마 생성 권한 | 계정이 `nori_ai_gateway` 의 소유자이고 CREATE 권한을 가진다 — Flyway 가 `gw`·`kn`·`gw_flyway` 를 만든다 | 2026-11-10 읽기 전용 확인 |
| `pg_control_system()` | PUBLIC 실행 가능(ACL `{=X/postgres,postgres=X/postgres}`) — `DbSystemIdentifierReader` 에 GRANT 불필요 | 2026-11-10 읽기 전용 확인 |
| 접속 허용 | k8s 노드 IP 192.168.0.101~106 의 pg_hba 허용(앱·CI 러너·백업 Job 이 클러스터 안) | 첫 배포 때 확인 |

## 접속

- 포트 15432 직접 연결이다(pgbouncer 16432 아님 — 백업의 `pg_dump` snapshot·Flyway 가 세션 연결을 쓴다).
- 계정 이름과 비밀번호는 이 문서·코드·차트·로그에 쓰지 않는다. 환경변수 `AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD` 로만 주입한다(URL 은 `AI_GATEWAY_DB_URL`). 명령행 인자로 비밀번호를 넘기지 않는다.
- migration 은 루트 Gradle Flyway(`flywayInfo`·`flywayMigrate`, 위치 `flyway/sql`, 이력 schema `gw_flyway`)가 CI `migrate` stage 에서 적용한다. 앱은 `spring.flyway.enabled=false` 이고 `SchemaVersionGuard` 로 버전만 확인한다.

## 읽기 전용 확인(같은 계정, 첫 배포 때)

결과는 값이 아니라 판정만 기록한다. 쓰기·DDL 은 하지 않는다.

```sql
SELECT version();
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
SELECT pg_get_userbyid(datdba) = current_user AS owner, has_database_privilege(current_database(), 'CREATE') AS can_create
  FROM pg_database WHERE datname = current_database();
SELECT has_function_privilege('pg_control_system()', 'EXECUTE');
```

## 시험 환경의 재현

우리가 superuser 인 임시 인스턴스(격리 시험 컨테이너·복원 리허설 인스턴스)에서는 `operations/deploy/helm/ai-gateway-db-restore/bootstrap.sql` 이 같은 선행조건(vector·DB 를 소유하는 단일 계정·`pg_control_system()` PUBLIC 실행)을 env 로 받은 계정 이름으로 재현한다. 이 스크립트는 운영 공유 서버에 실행하지 않는다.
