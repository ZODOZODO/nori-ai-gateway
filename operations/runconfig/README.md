# 배포 실행 입력(runconfig)

Linux bash에서 AI 레포 루트를 cwd로 사용합니다. Python 3.12, Helm, Git, sha256sum, mktemp가 필요합니다. 자격 명령에는 kubectl·OpenSSL, 백업 경보 주입·rollback 리허설에는 psql, 인벤토리에는 AWS CLI가 추가로 필요합니다. Python 의존은 별도 가상환경에서 `python3 -m pip install -r operations/deploy/requirements.txt`로 준비합니다. 전역 설치는 필요하지 않습니다. 테스트 도구 이미지와 실제 버전은 Dev report와 scratch 결과에 기록합니다.

차트는 이 checkout 의 `operations/deploy/helm/ai-gateway/`(release `ai-gateway`·`ai-gateway-restore`)와 `operations/deploy/helm/ai-gateway-db-restore/`(release `ai-gateway-db-restore`, 복원 리허설 임시 인스턴스)입니다. 운영 DB 는 공유 서버 192.168.0.150:15432 의 `nori_ai_gateway` 이며 차트·runconfig 로 만들지 않습니다. DB 접속 값은 runconfig 가 아니라 Secret `ai-gateway-config` 입니다(`docs/runbooks/aigw-runtime-credentials.md`).

`verify.schema.json`은 JSON Schema 2020-12이며 `jsonschema`로 실제 검증합니다. 승인값은 `{ "value": 값, "approvalRef": "docs/gates/...md" }` 구조입니다. `purpose`는 lint/deploy, `profile`은 main/restore입니다. 공통 필수 키는 `pgImage.tag`, `pgImage.digest`(백업 도구 이미지·복원 인스턴스·시험 컨테이너의 PostgreSQL major 18·pgvector 0.8.7 pin)이고, restore profile 은 `restore.pvcSizeGi` 가 필수입니다(주 profile 의 PVC 키는 없습니다). digest는 접두사 없는 소문자 64hex입니다. tag는 PG18 태그여야 합니다. 용량은 양의 정수 GiB입니다. 기본 이미지·용량은 없습니다.

`backup.type`은 s3/sftp, `backup.enabled`, `backup.schedule`, `backup.destination`, `backup.ageRecipient`, `backup.retentionDays`는 주 앱 release 의 백업 CronJob `ai-gateway-db-backup` 입력(비밀 아님)입니다. Seaweed 메타데이터 조사 입력은 `seaweed.s3Endpoint`, `corpusInventory.keysFile`이며 각각 승인 문서를 참조합니다. YAML 목록은 Plan P22의 `ownerSystem: TC`, `documents[].owner`, `documents[].sourceRef`를 검사합니다. sourceRef는 정확한 객체 key 또는 같은 버킷의 s3 URI여야 하며 논리 참조를 임의로 key로 변환하지 않습니다. `--keys-from`도 keysFile이 지목한 동일 파일만 허용합니다. 읽기 전용 identity는 원천 승인자가 환경변수로 제공합니다.

누락 키·참조 파일은 `RUNCONFIG_MISSING:<key>` exit 2입니다. 구조·타입 오류는 `RUNCONFIG_INVALID:<key>`, profile 충돌은 `RUNCONFIG_PROFILE_MISMATCH`, restore 금지 키는 `RUNCONFIG_KEY_CONFLICT:<key>`입니다. 입력값은 오류에 출력하지 않습니다. 승인 문서는 레포 안의 일반 파일이어야 하며 배포 시 `docs/gates/**` 아래 `Status: APPROVED` 행이 필요합니다. `Status: BLOCKED`, `NOT_RUN`, `PENDING` 문서는 승인으로 사용할 수 없습니다. 운영자가 근거를 검토해 승인한 실제 기록만 이 상태를 가질 수 있습니다. 이 표기는 승인을 생성하는 기능이 아닙니다. main/restore 승인 파일은 레포 밖에서 별도로 관리하며 lint fixture를 배포용으로 바꾸지 않습니다.

최상위 JSON은 객체여야 하며 purpose/profile 누락은 RUNCONFIG_MISSING:<key>로 구분합니다. JSON 파싱·최상위 타입 오류는 RUNCONFIG_INVALID(exit 2)이며 원시 입력을 출력하지 않습니다. 양의 정수 GiB는 JSON Schema의 수학적 정수 계약을 따르므로 1.0도 1Gi로 정규화하고 분수·0·음수는 거부합니다.

## 로컬 검증

다음 명령을 각각 실행합니다.

```bash
bash operations/deploy/aigw-helm.sh --release ai-gateway --mode lint --runconfig operations/runconfig/fixtures/lint-verify.json --image-digests operations/runconfig/fixtures/lint-image-digests.json --suite none
bash operations/deploy/aigw-helm.sh --release ai-gateway-restore --mode lint --runconfig operations/runconfig/fixtures/lint-verify.json --image-digests operations/runconfig/fixtures/lint-image-digests.json --suite none
bash operations/deploy/aigw-helm.sh --release ai-gateway-db-restore --mode lint --runconfig operations/runconfig/fixtures/lint-verify.json --image-digests operations/runconfig/fixtures/lint-image-digests.json --suite none
bash operations/deploy/aigw-helm.sh --release ai-gateway-db-restore --mode render --runconfig operations/runconfig/negative-missing-pvc.json --expect-fail RENDER_REQUIRED_persistence.size
bash operations/deploy/aigw-helm.sh --release ai-gateway --mode render --runconfig operations/runconfig/negative-missing-backup.json --image-digests operations/runconfig/fixtures/lint-image-digests.json --suite none --expect-fail RENDER_REQUIRED_backup.destination
bash operations/deploy/aigw-helm.sh --release ai-gateway-restore --mode render --runconfig operations/runconfig/fixtures/negative-restore-ingest.json --image-digests operations/runconfig/fixtures/lint-image-digests.json --suite none --expect-fail RUNCONFIG_KEY_CONFLICT:ingest.enabled
bash operations/gates/aigw-runtime-credentials-selftest.sh
python3 -B operations/deploy/runconfig-selftest.py
```

음성 렌더는 정확한 expected code에만 성공합니다. `--expect-fail`은 render 전용입니다. lint fixture에서 PVC 누락은 차트의 required guard로 전달하며 deploy 누락은 입력 검증에서 거부합니다. schema도 deploy의 restore PVC를 조건부 필수로 검증합니다.

앱 release lint 는 안에서 guard 음성(용량·Secret 생성/이름 schema·digest 누락·prod·release/복원 모드 불일치·ingest/Source 읽기·복원 플래그·backup point·SPEC SHA 불일치·백업 목적지 누락·복원 release 백업 금지)과 §11.9 렌더 행렬(행마다 config Secret 투영 키·단일 계정 env·DB egress(주 = 192.168.0.150/32:15432, 복원 = 복원 인스턴스)·추론 모드 env·egress 대상·수용 Job Secret·수용 DB 자격·spec-verify 해제 SHA)·백업 CronJob 양성(공유 서버·단일 계정·backup digest)·재배포 0 suite 의 Deployment 렌더 불변을 확인합니다. 복원 DB release lint 는 차트 안 `bootstrap.sql` 바이트·Service 15432→Pod 5432·자격 키(ai-gateway-config 두 키)·HBA overlay 0 을 확인합니다. `lint-spec-bytes.md`·`lint-spec-baseline.md`·`lint-fixture-catalog.yaml`·`lint-image-digests.json` 은 LINT-ONLY 이며 deploy 는 `--baseline docs/gates/spec-baseline.md` 만 받습니다.

## 배포

승인 운영자만 `--mode deploy --runconfig "$AIGW_RUNCONFIG"`(복원은 `$AIGW_RESTORE_RUNCONFIG`)를 실행합니다. 부작용 전에 차트 checkout 을 확인합니다: `operations/` 의 미커밋·미추적 변경이 있으면 `CHART_CHECKOUT_DIRTY`, 앱 release 에서 HEAD 가 `image-digests.json` 의 `buildIdentity.gitSha`(앱 이미지 `GIT_SHA` label 과 같은 값)와 다르면 `CHART_CHECKOUT_SHA_MISMATCH`, git checkout 이 아니면 `CHART_CHECKOUT_UNREADABLE` 로 exit 2 입니다. 이어서 postgres-image·corpus-inventory·pvc-capacity·pvc-protection·backup-destination(앱 release 는 netpol-targets 추가)의 실제 승인과 런타임 자격 check 를 요구합니다. `--wait --timeout 600s` 후 rollout(복원 DB 는 bootstrap Job 완료까지), status=deployed와 양의 revision을 모두 확인해야 성공 증거(`chartRepoSha`·`chartDigest`·helm revision·digest)를 기록합니다. restore는 RESTORE 자격 단계로 검사합니다.

## P15 앱 release 입력

앱 release(`ai-gateway`·`ai-gateway-restore`)는 `--image-digests <image-digests.json>`(`app`·`backup`·`acceptance` = `sha256:<64hex>`, `buildIdentity`)과 `--suite none|<§11.9 배포 suite>`가 필수입니다. 같은 runconfig 에 `netpol.targets.<key>`·`egressProbe.targets`·`inference.*`·`kafka.*`·`seaweed.{s3Endpoint,fixturePrefix}`·`acceptance.readback.*`를 `{value, approvalRef}`로 둡니다. Secret 이름과 suite 별 플래그는 스크립트·차트의 §11.9 고정값이며 runconfig 로 바꾸지 않습니다. `profile=restore` 파일은 공통 키·`restore.*`·`seaweed.s3Endpoint`·`netpol`·`egressProbe` 밖 leaf 가 있으면 `RUNCONFIG_KEY_CONFLICT:<leaf>` 입니다. 백업 CronJob 은 주 release 에만 `backup.enabled=true` 와 `backup.{type,schedule,destination,ageRecipient,retentionDays}`, `--image-digests` 의 `backup` digest 로 렌더합니다(lint fixture 는 enabled 를 두지 않고 lint 모드가 내부에서 켜서 양성·음성을 확인).
