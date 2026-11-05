# G-PVC-SIZE 인벤토리 입력

Status: BLOCKED

문서 소유자가 승인한 정확한 key 목록과 승인 참조·Seaweed endpoint runconfig·읽기 전용 identity가 제공되지 않았습니다. 실제 AWS/Seaweed 요청은 0건입니다. 승인 없는 버킷 전체 나열·상한 추정·객체 내용 읽기는 수행하지 않습니다. 실측 JSON을 만들지 않았으며 건수/총 바이트/최대 크기는 미확정입니다. fixture 시험의 집계를 실측으로 사용하지 않습니다.

운영자는 owner 승인 기록을 docs/gates 아래 남기고 purpose=deploy runconfig의 seaweed.s3Endpoint와 corpusInventory.keysFile 각각에 {value, approvalRef}를 제공합니다. keysFile은 정확한 객체 key를 한 줄씩 담거나 Plan P22의 tc-documents YAML이어야 합니다. YAML은 ownerSystem·documents[].owner·sourceRef를 확인하며 --keys-from은 승인된 keysFile과 같은 파일만 허용합니다. sourceRef의 정확 key 또는 같은 버킷 s3 URI만 처리하고 다른 논리 참조는 추측하지 않고 거부합니다. prefix 스캔은 하지 않습니다.

```bash
bash ops/seaweedfs/bin/corpus-inventory.sh --runconfig "$AIGW_RUNCONFIG" --bucket nori-tc --out docs/gates/f1/corpus-inventory.json
```

각 승인 key에 head-object --query '{len:ContentLength,type:ContentType}'만 실행합니다. 출력은 count/totalBytes/maxBytes/확장자·MIME 분포이며 key 원문·응답 메타데이터 본문·자격을 기록하지 않습니다. 소유자 승인 누락은 AWS 실행 전 exit 2입니다. 형식 지원 여부 확정은 P22의 별도 책임입니다.
