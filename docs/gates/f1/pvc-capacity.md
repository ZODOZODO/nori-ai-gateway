# G-PVC-SIZE 용량 산정

Status: BLOCKED

승인 인벤토리·부하 가정·노드별 현재 가용량·사용자 GiB 승인이 없어 용량을 확정하지 않습니다. lint의 1Gi는 형식 시험용이며 업무 용량이 아닙니다. SPEC §16의 2026-10-27 클러스터 집계는 현재 가용량의 증거로 재사용하지 않습니다.

산정 입력과 식은 다음과 같습니다. C는 승인 문서로부터 산정한 chunk 수, T는 chunk 텍스트 사본 바이트, d는 차원, I는 정확 검색용 실제 테이블/인덱스 부가량, D30은 대화·메시지·Job·결과·증거 30일 누적, A90은 감사 90일 누적, M은 canonical metadata, L은 ingest ledger/checkpoint/tombstone, W는 WAL 여유입니다. 각 항목의 근거와 단위를 실측 인벤토리 및 승인 부하 가정으로 채워야 합니다.

임베딩 예상 바이트 = C × d × 4. 초기 d=1024는 [BGE-M3 공개 사양](https://github.com/FlagOpen/FlagEmbedding/tree/master/research/BGE_M3)의 가정이며 실제 모델 digest/dimension은 P22 probe 후 재검토합니다. Knowledge 예상 K = T + C×1024×4 + I + M + L. PVC 예산은 K + D30 + A90 + W + Knowledge 테이블 1배 재색인 임시 공간 + 명시 근거가 있는 운영 여유를 합산한 뒤 GiB로 올림합니다. chunk 경계·중복률·색인 부가량·일일 발생량은 아직 입력이 없으므로 숫자를 임의 대입하지 않습니다.

백업 덤프/암호화 작업 공간은 DB PVC 밖에 별도 확보합니다(P15). Longhorn replicas=3에 따른 물리 소비·snapshot 여유·각 노드별 현재 free/reserved/scheduled를 확인해야 하며 단순 총 여유 합계로 프로비저닝 가능을 단정하지 않습니다. 현재 클러스터 조회·PV 변경은 하지 않았습니다. 승인 후 main pvcSizeGi와 restore.pvcSizeGi를 별도로 기록합니다. restore 용량은 G-RESTORE-ENV 승인값이어야 합니다.
