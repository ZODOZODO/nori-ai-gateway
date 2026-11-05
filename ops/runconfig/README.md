# P06 DB 실행 입력

Linux bash에서 AI 레포 루트를 cwd로 사용합니다. Python 3.12, Helm, Git, sha256sum, mktemp가 필요합니다. 자격 명령에는 kubectl·OpenSSL, 인벤토리에는 AWS CLI가 추가로 필요합니다. Python 의존은 별도 가상환경에서 `python3 -m pip install -r ops/deploy/requirements.txt`로 준비합니다. 전역 설치는 필요하지 않습니다. 테스트 도구 이미지와 실제 버전은 Dev report와 scratch 결과에 기록합니다.

`verify.schema.json`은 JSON Schema 2020-12이며 `jsonschema`로 실제 검증합니다. 승인값은 `{ "value": 값, "approvalRef": "docs/gates/...md" }` 구조입니다. `purpose`는 lint/deploy, `profile`은 main/restore입니다. DB 차트는 `execChartSha`, `pgImage.tag`, `pgImage.digest`와 해당 profile의 `pvcSizeGi` 또는 `restore.pvcSizeGi`를 사용합니다. digest는 접두사 없는 소문자 64hex입니다. tag는 PG18 태그여야 합니다. 용량은 양의 정수 GiB입니다. 기본 이미지·용량은 없습니다.

`backup.type`은 s3/sftp, `backup.schedule`, `backup.destination`, `backup.ageRecipient`, `backup.retentionDays`는 P15 확장용 비밀 아닌 입력입니다. 이 단계에는 CronJob이 없습니다. Seaweed 메타데이터 조사 입력은 `seaweed.s3Endpoint`, `corpusInventory.keysFile`이며 각각 승인 문서를 참조합니다. YAML 목록은 Plan P22의 `ownerSystem: TC`, `documents[].owner`, `documents[].sourceRef`를 검사합니다. sourceRef는 정확한 객체 key 또는 같은 버킷의 s3 URI여야 하며 논리 참조를 임의로 key로 변환하지 않습니다. `--keys-from`도 keysFile이 지목한 동일 파일만 허용합니다. 읽기 전용 identity는 원천 승인자가 환경변수로 제공합니다.

누락 키·참조 파일은 `RUNCONFIG_MISSING:<key>` exit 2입니다. 구조·타입 오류는 `RUNCONFIG_INVALID:<key>`, profile 충돌은 `RUNCONFIG_PROFILE_MISMATCH`, restore 금지 키는 `RUNCONFIG_KEY_CONFLICT:<key>`입니다. 입력값은 오류에 출력하지 않습니다. 승인 문서는 레포 안의 일반 파일이어야 하며 배포 시 `docs/gates/**` 아래 `Status: APPROVED` 행이 필요합니다. `Status: BLOCKED`, `NOT_RUN`, `PENDING` 문서는 승인으로 사용할 수 없습니다. 운영자가 근거를 검토해 승인한 실제 기록만 이 상태를 가질 수 있습니다. 이 표기는 승인을 생성하는 기능이 아닙니다. main/restore 승인 파일은 레포 밖에서 별도로 관리하며 lint fixture를 배포용으로 바꾸지 않습니다.

로컬 검증은 다음 명령을 각각 실행합니다.

```bash
bash ops/deploy/aigw-helm.sh --release ai-gateway-db --mode lint --exec-dir ../nori-tc-exec --runconfig ops/runconfig/fixtures/lint-verify.json
bash ops/deploy/aigw-helm.sh --release ai-gateway-db-restore --mode render --exec-dir ../nori-tc-exec --runconfig ops/runconfig/fixtures/lint-verify.json
bash ops/deploy/aigw-helm.sh --release ai-gateway-db --mode render --exec-dir ../nori-tc-exec --runconfig ops/runconfig/negative-missing-pvc.json --expect-fail RENDER_REQUIRED_persistence.size
bash ops/gates/aigw-runtime-credentials-selftest.sh
python3 -B ops/deploy/runconfig-selftest.py
```

음성 렌더는 정확한 expected code에만 성공합니다. `--expect-fail`은 render 전용입니다. lint fixture에서 PVC 누락은 차트의 required guard로 전달하며 deploy 누락은 입력 검증에서 거부합니다. schema도 deploy의 PVC를 profile별 조건부 필수로 검증합니다.

최상위 JSON은 객체여야 하며 purpose/profile 누락은 RUNCONFIG_MISSING:<key>로 구분합니다. JSON 파싱·최상위 타입 오류는 RUNCONFIG_INVALID(exit 2)이며 원시 입력을 출력하지 않습니다. 양의 정수 GiB는 JSON Schema의 수학적 정수 계약을 따르므로 1.0도 1Gi로 정규화하고 분수·0·음수는 거부합니다. lint fixture의 execChartSha는 형식 전용 더미여서 CLI SHA와 사업 승인값 결속을 비교하지 않지만 실제 clone·checkout·rev-parse는 언제나 CLI SHA와 일치해야 합니다. deploy 입력은 승인 execChartSha와 CLI SHA의 일치도 요구합니다. Git 원시 오류는 메모리에서만 분류하며 상속된 Git trace 파일 설정을 제거합니다.

배포는 승인된 exec 40hex SHA와 일치하는 `execChartSha`를 요구합니다. `--exec-dir`은 lint/render에서만 허용합니다. CI는 CI_JOB_TOKEN, 운영자는 기존 승인된 AIGW_EXEC_READ_USER/TOKEN을 사용합니다. 일회용 0700 ASKPASS helper가 환경에서만 자격을 읽고 종료 시 삭제됩니다. 자격 누락은 EXEC_READ_CREDENTIAL_MISSING, 401/403은 EXEC_READ_FORBIDDEN(exit 2)입니다. inbound 목록·권한은 수정하지 않습니다. clone 오류 원문은 출력하지 않습니다. helm의 원시 실패 대신 HELM_* 오류 코드로 전파합니다.

승인 운영자만 `--mode deploy --exec-sha "$AIGW_EXEC_CHART_SHA" --runconfig "$AIGW_RUNCONFIG"`를 실행합니다. 배포 전 exec-chart-ref·postgres-image·corpus-inventory·pvc-capacity·pvc-protection·backup-destination의 실제 승인과 자격 검사를 요구합니다. `--wait --timeout 600s` 후 해당 StatefulSet rollout·bootstrap 완료, 실제 HBA 경로/정본 SHA/규칙 검사, status=deployed와 양의 revision을 모두 확인해야 성공 증거를 기록합니다. restore는 RESTORE 자격 단계로 검사합니다. acceptance-ro가 활성인 두 검증 DB hook에는 해당 Secret이 필수이며 `--acceptance-role disabled`일 때만 검사·참조를 생략하고 false를 전달합니다. overlay는 항상 유지합니다. HBA는 정본 파일로만 주입하며 runconfig로 임의 HBA·서버 옵션을 추가하지 않습니다. 2026-11-06 승인 A로 역할 접속 계약은 확정되었고, 실제 운영 승인·배포 게이트는 계속 BLOCKED/NOT_RUN입니다. 기존 세션 종료와 양쪽 release 자격 정리는 자격 runbook 절차를 따릅니다.

selftest 소스 배치: 기본값은 Gateway checkout과 같은 부모 디렉터리의 `../nori-tc-exec`입니다. 컨테이너 등에서 exec checkout을 다른 위치(예: `/exec`)에 두었다면 `AIGW_SELFTEST_EXEC_DIR=/exec bash ops/gates/aigw-runtime-credentials-selftest.sh` 및 `AIGW_SELFTEST_EXEC_DIR=/exec python3 -B ops/deploy/runconfig-selftest.py`처럼 기존 지원 변수를 지정합니다. 실제 exec 소스 checkout(HEAD와 DB 차트 포함)이 필요하며, 이 변수는 selftest 입력 경로만 지정합니다. sibling 배치에서는 지정하지 않아도 됩니다.
