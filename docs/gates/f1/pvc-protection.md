# G-PVC-PROTECT

Status: NOT_RUN

로컬 Helm 렌더에서 StatefulSet의 whenDeleted/whenScaled=Retain과 longhorn PVC를 확인했습니다. 실제 PVC/PV bind·PV Retain patch·삭제 복구는 실행하지 않았습니다. StorageClass는 수정하지 않았습니다.

승인 운영자는 main data-ai-gateway-db-0 또는 restore data-ai-gateway-db-restore-0의 spec.volumeName을 조회해 실제 bind PV를 얻고, 그 PV에만 persistentVolumeReclaimPolicy=Retain patch를 실행한 뒤 읽기 전용 조회로 Retain을 확인합니다. PV 이름을 사전 고정하거나 다른 PV를 일괄 수정하지 않습니다. StatefulSet의 보존 정책만으로 PV reclaimPolicy까지 보호된다고 주장하지 않습니다.

삭제 복구 증거는 AC-24-RESTORE 리허설에서 확인합니다. helm uninstall은 PVC/PV 보존을 전제로 하며 실제 PVC 삭제는 별도 운영자 승인 뒤에만 수행합니다. 복원 DB 삭제도 같은 원칙을 따릅니다. 미실행 상태로 보호 gate를 닫을 수 없습니다(2026-11-12 철회(seq17), SPEC §12.4: 이 문서의 restore PVC·AC-24-RESTORE 리허설·복원 DB 문장은 이력입니다).
