# AI Gateway 검증 환경 백업·격리 복원 runbook

대상: namespace `tc` 의 주 DB release `ai-gateway-db`(백업 CronJob 은 이 release 에만)와 격리 복원 release `ai-gateway-db-restore`(Plan §11.8). 실제 배포·첫 백업 실행·복원 리허설은 G-BACKUP-DEST·G-PG-IMAGE·G-EXEC-CHART-REF 승인과 승인 backup 이미지 digest 뒤에만 한다(현재 NOT_RUN).

## 입력

- runconfig(main) `backup.{enabled,type,schedule,destination,ageRecipient,retentionDays}` — 승인 문서 참조 필수. `aigw-helm.sh --release ai-gateway-db --image-digests <image-digests.json>` 가 `backup` digest 와 함께 차트에 넘기며 하나라도 없으면 렌더 실패.
- `backup.destination` 형식: `s3` = `https://<endpoint host[:port]>/<bucket>[/<prefix>]`(경로 방식 S3 호환), `sftp` = `sftp://<user>@<host>[:port]/<path>`. 유형은 이미지 빌드 인자 `BACKUP_DEST_TYPE` 와 같아야 한다.
- Secret: `ai-gateway-db-backup`(`password`, 역할 `aigw_backup`), `ai-gateway-backup-destination`(s3 = `access-key`·`secret-key`, sftp = `ssh-private-key`·`known-hosts`). age 수신자 공개키는 runconfig 값(비밀 아님). 개인키는 복원 실행자만 보유하며 클러스터·이미지·레포에 두지 않는다.

## 백업 확인

```bash
kubectl -n tc get cronjob ai-gateway-db-backup
kubectl -n tc get jobs -l app=ai-gateway-backup   # 없으면 Job 이름으로 조회
```

결과의 정본은 `gw.backup_run`(행마다 `outcome` = `SUCCESS`|`FAILED`, 실패는 `detail_code` = 단계 코드 `SNAPSHOT`·`DUMP`·`JOB_COPY`·`MANIFEST`·`BUNDLE`·`UPLOAD`·`RETENTION`·`RESIDUE`·`RECORD`)과 지표 `aigw_backup_last_run_failed`·`aigw_backup_last_success_timestamp_seconds`(경보 AIGW-BACKUP-FAILED·AIGW-BACKUP-STALE)이다. Job 로그에는 `BACKUP_SUCCESS bundle=<이름>` 또는 `BACKUP_FAILED:<단계>` 한 줄만 남는다.

## 수동 1회 실행

```bash
kubectl -n tc create job --from=cronjob/ai-gateway-db-backup ai-gateway-db-backup-manual-$(date -u +%Y%m%dT%H%M%SZ)
```

## 백업 내용

같은 snapshot(세션 A `REPEATABLE READ READ ONLY` + `pg_export_snapshot`)으로 `pg_dump -Fc --exclude-table-data=gw.job`(스키마·제약·sequence 포함)과 `gw.job` sanitised binary COPY(열 = `ops/backup/job-columns-v1.txt`, `delegation_id` 는 항상 NULL)를 각각 `age` 로 암호화하고, 업무 값 없는 manifest(형식 버전·snapshot 시각·Flyway 최신 V·열 목록 SHA·암호문 SHA·`gw.job` 행 수·`delegationExcluded=true`)도 암호화해 하나의 `aigw-<UTC>.bundle`(tar)로 업로드한 뒤 보관 기간 초과분을 지운다. 평문 덤프는 파일로 쓰지 않는다.

## 격리 복원(복원 실행자 Linux 호스트)

1. 복원 DB release 를 새로 설치해 bootstrap hook 완료를 확인한다(`ai-gateway-db-restore`, 주 DB 를 대상으로 쓰지 않음).
2. 다른 터미널에서 `kubectl -n tc port-forward svc/ai-gateway-db-restore 15432:5432`(복원 release Service 만).
3. bundle 을 목적지에서 내려받아 읽기 전용 디렉터리에 두고, 같은 backup 이미지 digest 로 실행한다. 비밀번호는 env 로만 넣는다(명령행·출력 0).

```bash
read -rs AIGW_RESTORE_PGPASSWORD; export AIGW_RESTORE_PGPASSWORD   # aigw_migrator (복원 release)
docker run --rm --network host -v "$BUNDLE_DIR:/in:ro" -v "$AGE_IDENTITY_FILE:/run/age/identity:ro" -v "$OUT_DIR:/out" \
  -e AIGW_RESTORE_PGPASSWORD "harbor.nori.local/nori-tc/ai-gateway-backup@$BACKUP_DIGEST" /opt/aigw/aigw-restore.sh \
  --bundle "/in/$BUNDLE_NAME" --target-db-url postgresql://aigw_migrator@127.0.0.1:15432/ai_gateway \
  --expect-db-release ai-gateway-db-restore --age-identity-file /run/age/identity --receipt-dir /out
```

종료 코드: 0 성공(`/out/restore-receipt.json` = bundle SHA·`snapshotAt`·Flyway V·표별 행 수·TOC 제외 항목 이름, 비밀·원문 0), 2 입력 오류, 7 `RESTORE_TARGET_REJECTED`(비어 있지 않은 DB·bootstrap 역할 부재·release/포트 불일치 — 쓰기 0), 8 `RESTORE_STEP_FAILED:<단계>`(pg_restore `--exit-on-error`, 오류 1건이면 이후 단계 0). 실패하면 복원 release 를 폐기하고 어떤 Gateway 도 그 DB 로 전환하지 않는다. `snapshotAt` 이 P27 의 `--backup-point` 입력이다.

TOC 필터는 bootstrap(superuser)이 이미 만든 항목만 뺀다: `EXTENSION`·`COMMENT ON EXTENSION`·`SCHEMA public` ACL/COMMENT·`DATABASE` 항목·`pg_catalog` 객체 ACL. `gw`·`kn`·`gw_flyway` 스키마·테이블·`SEQUENCE SET`·ACL 은 그대로 복원한다(`setval` 보정 0).

## 개인키 보관·접근

- age 키 쌍은 복원 실행자가 오프라인에서 만들고 공개키(`age1…`)만 runconfig 승인 기록에 올린다.
- 개인키 파일은 복원 실행자 호스트의 0400 파일로만 두고 복원 때 읽기 전용 bind mount 로만 컨테이너에 넣는다. Secret·ConfigMap·이미지·레포·채팅·증거에 넣지 않는다.
- 키 교체: 새 공개키를 승인 runconfig 에 반영해 재배포한다. 이전 키로 암호화된 bundle 은 보관 기간 동안 이전 개인키로만 복원 가능하므로 보관 기간이 지난 뒤 이전 개인키를 폐기한다.

## 경보 주입

`ops/gates/aigw-backup-alert-injection.sh` 는 수동 Job 의 컨테이너 env 에만 `AIGW_BACKUP_DEST_OVERRIDE=unreachable-probe` 를 넣어 실패 행·경보를 확인한 뒤 override 없는 수동 백업으로 복귀를 확인한다(스크립트는 그 고정 값 외 override 를 exit 2 로 거부).
