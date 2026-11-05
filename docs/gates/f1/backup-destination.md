# G-BACKUP-DEST

Status: BLOCKED

DB·Longhorn과 동시 손실하지 않는 실제 목적지, 독립성 근거, 사용자 승인, age 수신자 공개키 및 개인키 보관·접근 정책이 없습니다. s3/sftp 중 유형을 임의 선택하지 않았으며 저장 공간·bucket·크기·자격을 만들지 않았습니다.

승인 기록에는 DB/Longhorn과의 장애 영역 분리, 업로드 identity와 DB 역할 권한 분리, age 수신자 공개키, 개인키 보관 책임·위치·권한(개인키 값 제외), 7일 보관과 삭제 권한을 포함해야 합니다. 개인키는 DB/백업 Pod·레포에 두지 않습니다. 유형별 런타임 Secret 필드는 s3=access-key/secret-key, sftp=ssh-private-key/known-hosts입니다. 운영자가 등록하고 자격 검사는 필드 이름만 확인합니다.

P06은 배포 전제 문서만 준비합니다. 일일 백업 CronJob·gw.backup_run·덤프·복원 구현과 성공 실행 증거는 P15 이후 책임입니다. 현재 백업 구성/F1 exit를 완료로 표시하지 않습니다.
