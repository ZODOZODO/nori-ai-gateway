# G-NETPOL-TARGETS

Status: NOT_RUN

Gateway·수용 pod NetworkPolicy 의 실제 egress 대상 확인 기록이다. 확인 담당자가 읽기 전용으로 확인한 값만 이 문서에 적고 운영자가 근거를 검토해 `Status: APPROVED` 로 바꾼다. 이 문서는 현재 승인 기록이 아니며 값·주소를 추정해 채우지 않았다.

## 확인해야 하는 대상(차트 `netpol.targets.<key>` — runconfig 로만 주입)

| key | 사용처 | 필요 값 |
|---|---|---|
| `dns` | 앱·수용 | kube-dns namespace·pod label, 53 UDP+TCP |
| `loki` | 앱(Source)·수용(readback) | namespace·pod label, 3100 |
| `tempo` | 앱(Source)·수용(readback) | namespace·pod label, 조회 포트 |
| `alloy` | 앱(OTLP) | `alloy-gateway` namespace·pod label, 4318 |
| `gitlab` | 앱(Source) | ipBlock CIDR 또는 in-cluster selector, 포트 |
| `metrics` | 수용(readback) | G-OBS-READBACK 이 확인한 metric 읽기 API 의 namespace·pod label·포트 |
| `inference` | 앱(F2~) — 수용 pod 는 어느 suite 에서도 0 | 추론 endpoint ipBlock `192.168.0.160/32`, 포트(`inference.port` 와 같아야 함 — 서버 기존 endpoint `http://192.168.0.160:11434`, SPEC §26.1) |
| `seaweed` | 앱(F3~)·fixture-writer 수용 | SeaweedFS S3 ipBlock, S3 포트 |
| `kafka` | 앱(F3~) | `mes-cluster` broker selector·포트 |

각 대상은 `approvalRef` 로 이 문서(승인 후)를 가리킨다. 플래그가 거짓인 대상은 렌더하지 않는다(§11.9).

## egress probe 대상(runconfig `egressProbe.targets`)

`ALLOWED` ≥ 2(주 DB Service 5432, F2 이후 추론 endpoint `192.168.0.160:11434`)와 `BLOCKED` ≥ 2(allowlist 밖 외부 주소 1, allowlist 밖 클러스터 Service 1 — 예 namespace `tc` 의 `tc-ui-front` Service 포트)를 `{id, host, port, expect, approvalRef}` 로 적는다.

## Source 선언과의 관계

`gateway-app/src/main/resources/config/sources/tc-sources-v1.json` 의 Loki/Tempo/GitLab 주소는 같은 승인의 NetworkPolicy 대상과 같아야 하며, G-SOURCE-SVC·G-GITLAB-LIST 승인값이 모두 있을 때만 이미지 commit 이전에 반영한다(Plan P15 "Source 선언 반영"). 승인 전에는 현 비활성 선언을 유지하고 관련 운영 leaf 는 NOT_RUN 이다.

## 현재 상태

root 의 읽기 전용 metadata 확인(2026-11-07, 인계 기록)은 kube-dns label·Loki/Tempo 존재 등 사실 수집일 뿐 이 gate 의 승인이 아니다. Cilium 존재만으로 NetworkPolicy 실제 차단을 PASS 로 보지 않으며 AC-35-EGRESS 판정은 P21 이다.
