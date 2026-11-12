# G-PG-IMAGE

Status: BLOCKED

운영자가 승인한 Harbor mirror·tag·digest·확장 probe 기록이 없습니다. 실제 Harbor 조회·미러·클러스터 배포는 실행하지 않았으며 승인된 이미지 pin은 만들지 않았습니다. 실제 배포·P16·F1 완료·F2 착수는 기존 운영 게이트와 승인 Harbor digest 재시험을 충족해야 합니다.

2026-11-06 승인 B는 P06 로컬 HBA 결과의 부모 검증 이후 P07→P15 순서의 코드 구현·격리 로컬 시험만 운영 입력 대기와 분리합니다. 이번 범위는 P06이며 P07 착수·완료를 기록하지 않습니다. 실제 Source 연결·운영 수용은 NOT_RUN입니다.

로컬 candidate는 공개 `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`입니다. PostgreSQL 18.6/vector 0.8.7이며 pg_trgm은 가용·미설치·미사용입니다. 네트워크 없는 일회용 Docker 시험용이고 Harbor 승인값이 아닙니다. 승인 배포 digest 확보 후 bootstrap/HBA·migrate/권한·저장 관련 시험을 동일 digest로 재실행하고(2026-11-12 대체(seq17), SPEC §12.4 — 이력: 저장·백업/복원 관련 시험) 부모 검증하기 전 운영 증거로 승격하지 않습니다. 기존 scratch와 P06 JSON 증거는 그대로 보존합니다.

2026-11-06 승인 A는 기존 `pg_read_all_data` 부여를 유지하고 검증 역할의 타 DB TCP/Unix socket **연결/유효 접근 0**을 HBA로 강제합니다. literal 타 DB SQL ACL 0을 주장하지 않습니다. `default_transaction_read_only=on`은 방어층이며 off 후 실제 쓰기 권한 거부(SQLSTATE 42501)를 별도 검사합니다. 기존 bootstrap SQL의 역할·확장·DB 권한 정책과 다른 DB PUBLIC ACL은 유지합니다.

정본 HBA·release별 ConfigMap·읽기 전용 mount·기동 인자·checksum·실제 서버 규칙 검사는 [자격 runbook](../../runbooks/aigw-runtime-credentials.md)에 연결됩니다. 로컬 시험은 실제 차트 HBA/args로 새 PGDATA 및 기존 볼륨을 기동하고 원본 HBA/서버 기본값·다른 역할 인증 보존, 역할 활성/삭제/재활성, 타 DB 연결 HBA 거부·쓰기 권한 거부·기동 음성·자원 정리를 검사합니다. 로컬 JSON의 성공을 `postgres-extension.json` 또는 운영 게이트 PASS로 바꾸지 않습니다.

이번 P06 최종 로컬 증거는 [p06-hba-local-approved-20261106-r4.json](../../../evidence/F1/gates/p06-hba-local-approved-20261106-r4.json)입니다. 실제 bash 진입점에서 전체 보안 회귀와 정리를 통과했습니다. 부모 독립 검증 대기이며 P06 전체 완료·운영 게이트 PASS·P07 착수를 뜻하지 않습니다.
