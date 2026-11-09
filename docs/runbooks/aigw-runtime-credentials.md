# 검증 환경 런타임 자격

Status: NOT_RUN

실제 클러스터 자격의 조회·생성·교체를 이번 구현에서 실행하지 않았습니다. 아래는 namespace tc의 기존 쓰기 권한을 가진 승인 운영자 절차입니다. 권한 부여·Secret 값의 출력·파일 저장은 하지 않습니다.

`bash ops/gates/aigw-runtime-credentials.sh provision-db`는 superuser/migrator/app/backup 네 역할 Secret이 없는 경우에만 OpenSSL CSPRNG 32바이트의 base64 값을 stdin 파이프로 생성합니다. 기존 Secret은 PRESENT로 건너뜁니다. 조회 권한·통신 오류를 NotFound로 해석하지 않습니다. `--from-file=password=/dev/stdin`을 사용하며 비밀번호는 argv에 없습니다. 동시 provision 경쟁 시 create 실패를 전파하고 재실행하면 기존 Secret을 보존합니다.

`check --stage F1|F2|F3|F4|RESTORE`는 DB 네 역할의 password·GitLab token을 검사합니다. F1~F4는 백업 목적지의 s3 access-key/secret-key 또는 sftp ssh-private-key/known-hosts, F3/F4/RESTORE는 Seaweed access-key/secret-key가 필요합니다. 추론 자격 Secret은 어느 단계에도 없습니다(F2~F4 = F1 + SeaweedFS read(F3~), Plan §11.9). RESTORE는 백업 목적지를 요구하지 않습니다. 모든 누락을 표시하고 exit 13을 반환합니다. 증거는 evidence/<stage>/gates/runtime-credentials-check-<UTC+고유값>.json에 이름·필드·PRESENT/MISSING만 기록합니다. 원시 Secret JSON은 조회하지 않습니다. 존재 검사는 identity 권한·값의 유효성을 증명하지 않습니다.

검증 수용 자격(service-tokens, actor-jwks, test-hooks, acceptance-signer/client/db-acceptance-ro)은 Plan §11.7의 별도 G-VERIFY-CREDS 생산자(P15)가 준비합니다. provision-db가 acceptance-ro를 임의 생성하지 않습니다. 검증 DB hook은 활성 상태에서 acceptance-ro password를 필수 Secret 환경변수로 받으며 배포 스크립트도 필드 존재를 검사합니다. `--acceptance-role disabled`는 Secret 검사·hook 환경변수 참조를 생략하고 bootstrap에 false를 전달합니다. 역할 정리는 기존 SQL의 REVOKE/DROP 책임이며 HBA overlay는 유지합니다. 2026-11-06 승인 A로 접속 경계가 확정되었고 실제 운영 배포 게이트는 계속 BLOCKED/NOT_RUN입니다.

`rotate-db --role app|migrator|backup`는 먼저 두 고정 DB release 중 실제 존재하는 StatefulSet과 bootstrap Job을 확인합니다. 같은 Secret을 공유하므로 양쪽 활성 DB hook을 재생성해 모두 ALTER PASSWORD한 뒤 app 역할일 때만 대응 ai-gateway/ai-gateway-restore Deployment를 재시작하고 rollout을 기다립니다. migrator/backup은 매번 Secret을 읽는 Job이므로 다른 워크로드를 재시작하지 않습니다. 중간 실패는 RECOVERY_REQUIRED 오류로 전파합니다.

회전 실패 시 추가 회전을 중지하고 앱 트래픽과 백업/마이그레이션 실행을 정지하는 운영 절차를 따릅니다. 새 Secret은 이미 바뀌었을 수 있으므로 Secret만 되돌리지 않습니다. 실패한 release의 bootstrap Job 원인을 값 출력 없이 확인하고 현재 Secret을 사용하는 hook을 재실행·완료 확인합니다. 양쪽 DB와 앱 접속 확인 후 해당 workload만 재시작합니다. 기존 값으로 복구하려면 운영자가 관리하는 승인된 비밀 저장소의 값을 파이프로 Secret과 양쪽 DB에 동일하게 반영해야 합니다. 이 스크립트는 이전 비밀번호를 보관하지 않습니다.

GitLab·SeaweedFS·백업 목적지는 각 원천 게이트 소유자가 이미 승인한 자격을 등록합니다. 저장소·로그·채팅에 값을 남기지 않습니다. 추론 서버는 Gateway가 빌려 쓰는 공유 자원이며 Gateway는 기존 endpoint(`inference.baseUrl`)를 자격 없이 직접 호출합니다(SPEC §26.1). 추론 자격 Secret·토큰 회전 절차는 없고, 비 2xx(401·403 포함)·파싱 불가 응답은 P19 readiness가 `INFERENCE_UNAVAILABLE`(사유 `ENDPOINT_ERROR`)로 분류합니다. 이번 단계에는 추론 서버 접근을 하지 않습니다.

selftest는 격리 임시 디렉터리와 가짜 kubectl/git만 사용합니다. 실제 클러스터 자격을 읽지 않으며 운영 게이트 증거가 아닙니다.

P06 검토 보정: F1~F4의 `check`는 `AIGW_RUNCONFIG`에서 `purpose=deploy`, `profile=main`, 승인된 `backup.type`만 확인합니다. s3이면 access-key/secret-key, sftp이면 ssh-private-key/known-hosts를 요구하며 다른 유형의 필드로 대체하지 않습니다. 입력 부재·오류·미승인은 exit 2, Secret 누락은 exit 13입니다. 미래 단계의 다른 사업 입력은 검사하지 않습니다. 배포 스크립트는 자신의 `--runconfig`를 이 환경변수로 전달합니다. RESTORE는 백업 runconfig도 요구하지 않습니다.

회전 전에는 모든 대상 bootstrap Job의 release 이름·label·namespace와 종료 상태(Complete 또는 Failed, active=0)를 확인합니다. 실행 중이거나 종료 상태가 불명확하면 BOOTSTRAP_JOB_BUSY로 Secret 생성·변경 전에 중단합니다. 운영자는 동시 Helm 배포·회전을 중지한 상태에서 실행합니다. 서버 생성 식별자를 제거한 종료 Job template은 메모리에만 유지하며 일반 delete --wait=true 후 create로 재생성합니다. 강제 교체는 사용하지 않습니다. 양쪽 DB hook이 모두 완료된 뒤에만 앱을 재시작합니다. Secret 교체 후 hook 실패 시 앱을 재시작하지 않고 RECOVERY_REQUIRED로 중단합니다. 삭제 후 생성 실패로 Job이 없어진 경우에는 승인된 동일 release 차트의 hook을 현재 Secret으로 복구하고 양쪽 완료를 확인하는 운영자 복구가 필요합니다. 새 비밀번호를 다시 생성하는 rotate 재실행으로 복구하지 않습니다.

selftest 소스 배치: 기본값은 Gateway checkout과 같은 부모 디렉터리의 `../nori-tc-exec`입니다. 컨테이너 등에서 exec checkout을 다른 위치(예: `/exec`)에 두었다면 `AIGW_SELFTEST_EXEC_DIR=/exec bash ops/gates/aigw-runtime-credentials-selftest.sh` 및 `AIGW_SELFTEST_EXEC_DIR=/exec python3 -B ops/deploy/runconfig-selftest.py`처럼 기존 지원 변수를 지정합니다. 실제 exec 소스 checkout(HEAD와 DB 차트 포함)이 필요하며, 이 변수는 selftest 입력 경로만 지정합니다. sibling 배치에서는 지정하지 않아도 됩니다.

## 승인된 검증 역할 접속 경계

2026-11-06 승인 A는 `aigw_acceptance_ro`의 `ai_gateway` 접속만 TCP/Unix socket SCRAM으로 허용하고 타 DB 연결을 HBA에서 거부합니다. `pg_read_all_data`와 다른 DB PUBLIC ACL은 유지하므로 literal SQL ACL 0을 뜻하지 않습니다. 기본 read-only는 방어층이며 off 이후에도 실제 쓰기 권한 거부가 필요합니다. 다른 역할은 원본 HBA 인증을 그대로 사용합니다.

정본은 `ops/postgres/acceptance-pg_hba.conf` 한 곳입니다. 모든 DB lint/render/deploy가 `--set-file hba.overlay`로 주입하고 release별 `<release>-hba` ConfigMap을 `/etc/aigw/postgres`에 읽기 전용으로 마운트합니다. PG18 원본 `/var/lib/postgresql/18/docker/pg_hba.conf`는 마지막 필수 include로 보존합니다. PGDATA·postgresql.conf·postgresql.auto.conf 변경이나 ALTER SYSTEM은 하지 않습니다. 이미지 ENTRYPOINT를 유지하고 `postgres -c hba_file=/etc/aigw/postgres/pg_hba.conf`로 시작합니다.

입력의 행 순서·역할·DB·인증 방식·include 계약과 렌더된 바이트·args·mount·checksum을 검사합니다. 입력 누락/변조는 배포 전 실패하고, 마운트 파일 부재·include 대상 부재·구문 오류는 PostgreSQL 기동 실패입니다. 정책 fallback은 없습니다. deploy는 Helm 완료 후 해당 StatefulSet rollout·bootstrap 완료를 기다리고 Pod 내부 관리자 env 자격으로 고정 검사 SQL을 실행합니다. 실제 data_directory/hba_file·overlay SHA·pg_hba_file_rules 앞 네 규칙/원본 include·오류 0을 확인해야 성공 증거를 만듭니다. 원시 SQL 오류는 출력하지 않고 실패 증거를 기록합니다.

HBA 변경은 checksum에 따라 StatefulSet Pod를 교체해 **기존 세션까지 종료**합니다. reload만으로 완료를 판정하지 않습니다. 동일 checksum에서 재기동이 필요한 승인된 리허설은 주 DB `kubectl -n tc rollout restart statefulset/ai-gateway-db` 후 `kubectl -n tc rollout status statefulset/ai-gateway-db --timeout=300s`, 복원 DB는 자기 이름 `ai-gateway-db-restore`로 각각 실행합니다. 이번 로컬 구현에서는 실제 kubectl/Helm deploy를 실행하지 않았습니다.

검증 자격 정리는 수용 Job 종료 후 **존재하는 두 DB release 각각**에서 다음 SQL을 Pod 내부 관리자 env 경로로 먼저 실행합니다. NOLOGIN과 대상 세션 종료가 완료된 뒤 같은 승인 runconfig/exec SHA의 배포 명령에 `--acceptance-role disabled`를 주고 역할 부재·재접속 거부를 양쪽에서 확인한 뒤 공유 Secret을 삭제합니다. 해당 인자만으로 기존 세션 정리가 끝났다고 판단하지 않습니다. 역할이 없으면 아래 SQL은 멱등입니다. 활성/삭제 후 재활성도 HBA/기동 검사와 양쪽 bootstrap 성공 확인이 선행합니다. P15가 전체 수용 자격 수명을 연결합니다.

```sql
SELECT 'ALTER ROLE aigw_acceptance_ro NOLOGIN'
WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_acceptance_ro') \gexec
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE usename = 'aigw_acceptance_ro' AND pid <> pg_backend_pid();
```

실행 경로는 `kubectl -n tc exec -i <대상release>-0 -- sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -X -w -h 127.0.0.1 -U postgres -d ai_gateway -v ON_ERROR_STOP=1 -Atf -'`의 stdin에 위 SQL을 전달하는 방식입니다. 고정 두 release만 대상으로 삼고 비밀번호 값은 argv/파일/로그에 넣지 않습니다. 실패하면 정리 성공으로 처리하지 않으며 HBA를 제거한 revision으로 rollback하지 않습니다.

로컬 보안 회귀는 레포 루트에서 `bash ops/postgres/aigw-hba-local-test.sh --image pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2 --out evidence/F1/gates/<새-고유-파일명>.json`으로 실행합니다. Docker와 기존 Helm/Python 의존성이 필요하고 새 결과 경로만 허용합니다. Windows에서는 같은 Python 파일을 기존 venv로 실행할 수 있습니다. `AIGW_SELFTEST_EXEC_DIR`은 exec checkout 경로입니다. `AIGW_HBA_TOOLS_IMAGE`에 이미 설치된 Linux 도구 이미지 ID를 명시하면 두 checkout을 읽기 전용 마운트한 `--network none --pull=never` 컨테이너에서 실제 배포 스크립트의 Helm 렌더를 실행합니다. PG 컨테이너도 네트워크/호스트 port 없이 자기 소유 임시 볼륨만 사용하고 CSPRNG 가짜 자격은 env로만 전달합니다. CODE/LOCAL_VALIDATED 범위이며 승인 Harbor digest로 재시험하기 전 운영 PASS로 승격할 수 없습니다.
