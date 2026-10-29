# G-SPEC-HASH (a) 로컬 바이트 대조 기록

- 대상: `C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`(승인 SPEC 원본, 레포에 사본 없음)
- 기준선: `docs/gates/spec-baseline.md` 마지막 항목 seq 1
- 승인 SHA-256: `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8`

| 확인 | 도구 | 일시(KST) | 계산값 | 결과 |
|---|---|---|---|---|
| 원본 바이트 SHA-256(착수 사전 확인) | Git Bash `sha256sum`(coreutils) | 2026-10-29 12:58 | `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` | 승인값과 일치 |
| P01 명령 ① `(Get-FileHash -LiteralPath <SPEC> -Algorithm SHA256).Hash.ToLower()` | PowerShell | 2026-10-29(root 실행) | `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` | exit 0, 승인값·기준선 seq 1 과 일치 |
| P01 명령 ③ `.\gradlew.bat :gateway-eval:run --args="spec-guard --spec <SPEC> --baseline docs/gates/spec-baseline.md" --console=plain` | `:gateway-eval:run`(`SpecGuard`) | 2026-10-29(root 실행) | `specSha` = `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` | exit 0, `specShaSource=FILE_HASHED`, `specInputOrigin=LOCAL_FILE`, BUILD SUCCESSFUL |

- 역사적 근거: P01 착수 시 승인된 Plan 스냅샷(진행 상태 갱신 전 원본)을 같은 날 측정한 SHA-256 은 `28cfae26d6574d65d68a2597449f3f2395c72060a6ac4a73a185a8b8acf24506` 로 착수값과 일치했습니다. 이는 현재 Plan 파일의 해시 주장이 아니며, 이후 P01 완료 진행 상태 표기만 갱신한 Plan 의 바이트 해시와는 다를 수 있습니다(계약·§12.4 내용 변경 0, SPEC 해시 무관).
- ①·③ 은 이전 Claude 세션의 실행 권한 제한으로 미실행이었으나, 2026-10-29 root 가 사용자 승인 범위의 표준 명령으로 직접 실행했습니다. 모든 Gradle 명령은 해당 프로세스 환경에만 `AIGW_SPEC_FILE`=SPEC 절대 경로를 주입했습니다(영구 환경 변경 0).
- 같은 회차 ② `:gateway-eval:test` 의 정제 요약(측정 시각 `2026-10-29T04:05:46.159206600Z` = KST 13:05:46)도 `specSha` = 승인값·`specShaSource=FILE_HASHED`·`specInputOrigin=LOCAL_FILE` 입니다.
- ①·③ 이 모두 실제 실행되어 승인값과 일치했으므로 G-SPEC-HASH (a) 는 완료입니다.
- 원격 패키지 공급 G-SPEC-HASH (b) 는 P01 완료 조건이 아니며 실행하지 않았습니다(G-REPO 이후 별도 게이트).
