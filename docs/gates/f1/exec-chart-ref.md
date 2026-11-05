# G-EXEC-CHART-REF

Status: BLOCKED

차트 위치는 exec/deploy/helm/ai-gateway/db-chart로 확정되어 있습니다. 이번 변경은 로컬 후보이며 커밋·push·MR·merge를 실행하지 않았습니다. 승인된 병합 commit SHA와 MR 검토 기록, Gateway CI_JOB_TOKEN inbound 읽기 허용 확인 기록은 아직 없습니다. 부모 직접 검증 전까지 후보를 승인 ref로 취급하지 않습니다.

lint/render는 로컬 exec-dir를 읽기 전용으로 사용할 수 있습니다. deploy는 승인된 exec SHA의 임시 clone/checkout과 실제 HEAD 일치를 요구합니다. 운영자는 이미 승인된 read_repository 계정만 사용하며 새 토큰·권한·inbound 목록을 생성/변경하지 않습니다. ASKPASS 자격 전달과 cleanup은 stub git 경계 시험으로 확인했으며 실제 사내 GitLab 인증/clone은 하지 않았습니다.
