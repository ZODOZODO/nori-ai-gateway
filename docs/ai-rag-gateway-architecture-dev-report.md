# ai-rag-gateway-architecture Dev Report

- 원 SPEC: `C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`(읽기 전용, 변경 0)
- Plan: `C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture-plan.md`(확정 계약 유지 — 이번 마감에서 P01 승인·완료 진행 상태 메타 2행과 P01 체크박스·완료 기록 1줄만 갱신, 계약·수치·명령·phase 구성·§12 권위표 변경 0)
- 대상 레포: `C:/workspace_nori/nori-ai-gateway`(독립 새 레포, 미커밋)
- 이번 보고 범위: **P01 레포 부트스트랩·eval 모듈·ac-report (F0)** 만. P02 이후는 미착수(사용자 별도 지시 대기).
- 보고서 위치: SDD 기본 경로 `.claude/` 쓰기가 restricted 권한 정책으로 거부되어 이 `docs/` 경로에 유일한 Dev Report 로 작성했습니다(정책 우회 0).

## 1. 현재 판정

| 항목 | 상태 |
|---|---|
| P01 구현 | 완료 |
| P01 시험 ②(`:gateway-eval:test`) | 58건 = 58 PASS / 0 FAIL / 0 ERROR / 0 SKIP / 0 ABORTED |
| G-SPEC-HASH (a)(①·③) | 승인값 일치 — 완료 |
| `test-reporting-version.txt`(④) | 실제 해석 좌표 2줄 기록 |
| P02 착수 전제 Python 3.12 툴체인 | 준비·검증 완료(§6) |
| root 최종 파일/범위 대조 | **root 독립 최종 대조 완료**(2026-10-29, §10 — root 보고 인용이며 이 문서 작성 세션은 명령을 실행하지 않음) |

- 실행 주체: 이전 Claude 세션이 P01 소스 4개 파일을 마지막으로 수정했으나 Bash 실행·`.claude/` 쓰기가 정책상 거부되어 종료되었습니다. 아래 §3 의 모든 명령은 **root(main) 가 사용자 승인 범위의 표준 명령으로 직접 실행**한 결과이며, 이 문서를 작성한 세션은 명령을 실행하지 않았습니다(Read/Grep/Glob/Edit/Write 만 사용).

## 2. 구현 범위(P01)

- 독립 새 레포 bootstrap: Java 21 toolchain, Gradle 9.2.1 wrapper, Spring Boot BOM 4.0.7, `gateway-eval` application(mainClass `com.nori.ai.gateway.eval.EvalCli`).
- SPEC 입력·기준선 사슬: `SpecInputResolver`(`--spec` → Gradle 속성 → `AIGW_SPEC_FILE`, CI 출처 제한·`specInputOrigin` 결정), `SpecGuard`(원본 바이트 SHA-256 결속, `SpecBinding` 단일 생성 경로), `SpecBaseline.parseChain()`(`prevSha256` 연결·`seq` 정수 연속·SHA 형식).
- `spec-input pack/publish/fetch`: 엄격 단일 gzip member·후행 바이트 0·압축/해제 상한, GitLab Generic Package 용(NuGet 아님), 허용 host 1개·redirect 미추종, 자격 값 비노출. **시험은 JDK `HttpServer` stub 만**(실제 GitLab 호출 0).
- RFC 8785 JSON 정규화(`io.Json`)와 `contentSha256`.
- `leaf-catalog-v2.json`: 상위 61·leaf 216·원자 23, (단계, leaf·원자)별 §12.4 권위 component 와 suite 역색인.
- `GateEvaluator`: component 권위 producer·부모 회차 안 마지막 시도·빌드 신원·라벨 분리·기준선 사슬 적격 기반 집계, 부재 = NOT_RUN(레코드 생성 0). `GateReportWriter`.
- OTR 정제(`OtrLeafExtractor`, StAX — 메시지·스택·attachment 폐기), 필수 시험 0건 방지(`TestCountGuard`·`RequiredTestSelection`·`--require-executed`), `aigwInputFingerprint`·`run-binding.json`·build cache 재사용, TestKit 기능 시험.
- `ingest`·`record`(manual user-run/rubric)·`perf report`·`test-evidence verify`·`leaf-extract`·`gate` CLI.
- 미구현 하위 명령 `f0`(P02)·`f3`(P22)·`ci-evidence`(P15)는 호출 시 명시 실패합니다(조용한 성공 0).

### 2.1 root 독립 정적 대조(root 수행 결과 인용)

- SPEC ID 누락/추가 0.
- 권위 stage 집합: F0=5, F1=35, F2=25, F3=28, F4=129. §12.4 componentId 불일치 0.
- 공유 leaf 4개 및 AC40→AC25 정합.

## 3. 실행 명령과 실제 결과(root 실행, 2026-10-29)

공통: cwd `C:/workspace_nori/nori-ai-gateway`, 모든 Gradle 명령 프로세스 환경에만 `AIGW_SPEC_FILE`=원 SPEC 절대 경로 주입. wrapper 가 환경/명령을 echo 하지 않도록 `DEBUG` 는 해당 명령 프로세스에서만 빈 값(영구 환경 변경 0).

| # | 명령 | 결과 |
|---|---|---|
| ① | `(Get-FileHash -LiteralPath C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md -Algorithm SHA256).Hash.ToLower()` | exit 0, `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` = Plan 승인값 = 기준선 seq 1 |
| ② | `.\gradlew.bat :gateway-eval:test --console=plain` | exit 0, BUILD SUCCESSFUL in 2m10s, 6 actionable tasks: 5 executed, 1 up-to-date. `test`·`testLeafEvidence` 모두 실제 실행. JUnit XML 과 정제 OTR 요약 독립 대조 58 = 58 PASS / 0 FAIL / 0 ERROR / 0 SKIP / 0 ABORTED |
| ②b | (② 직후 별도) `.\gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain` | exit 0, `TEST_EVIDENCE_VERIFIED`, BUILD SUCCESSFUL 3s |
| ③ | `.\gradlew.bat :gateway-eval:run --args="spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --console=plain` | exit 0, `specSha`=승인값, `specShaSource=FILE_HASHED`, `specInputOrigin=LOCAL_FILE`, BUILD SUCCESSFUL 2s |
| ④ | `.\gradlew.bat :gateway-eval:dependencies --configuration testRuntimeClasspath --console=plain` | exit 0, BUILD SUCCESSFUL 1s. `org.junit.platform:junit-platform-reporting:6.0.3`, `org.junit.platform:junit-platform-launcher:6.0.3` |

- 개별 명령의 초 단위 시각은 기록하지 않습니다. 시험 완료 시각 근거는 정제 요약 `recordedAt` = `2026-10-29T04:05:46.159206600Z`(KST 13:05:46) 뿐입니다.

### 3.1 정제 증거 요약(root 실제 읽기, 원문·민감값 없음)

| 필드 | 값 |
|---|---|
| `runId` | `603fbbaa-07ec-4a44-89a4-7369f5c93c21` |
| `kind` / `producer` / `stage` / `status` | `RUN` / `JUNIT_OTR` / `F0` / `COMPLETED` |
| `module` / `taskPath` | `:gateway-eval` / `:gateway-eval:test` |
| `executedTestCount` / `passed` / `failed` / `skipped` / `aborted` | 58 / 58 / 0 / 0 / 0 |
| `specSha` | `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8`(`FILE_HASHED`, `LOCAL_FILE`) |
| `inputFingerprint` | `1849382380e2bbd22faa19372be7c3626343c0679d3d57e36e5813ec3035e55d` |
| `otrSha256` | `e59c562754763bb67e8bc5bf87cb2798cb391c573b7093ad90728373606d216a` |
| `contentSha256` | `eb657c7e022fae268ad6f8ea42cce53a72f33947fadbdc2f47deb8e767d0e979` |
| `buildIdentity.gitSha` / `runtimeClasspathSha256` | `null` / `null`(P01~P03 허용 — 미커밋·P04 runtime 없음, 가짜 값 생성 0) |
| `leafRecordCount` | 0(P01 기반 시험에는 직접 Gateway AC 태그가 없음 — 정상) |
| root 기준 소스 digest | `5cd0b1bef7cd158fc2e42c2e2eb7898cfc70f9d3ae4856c7a1ace06e051c112f`(46파일: Gradle·wrapper·`gateway-eval` 소스·fixture, build/.gradle/문서 제외) |

- 58건 기반 시험 PASS 는 Runtime AC·ISO 합격으로 변환하지 않습니다(AC 추적: 직접 leaf 없음).
- root 가 문서 작성 뒤 다시 계산한 46파일 소스 digest 는 위 값과 같았습니다(시험 통과 당시와 동일 — root 최종 대조 결과 인용). 이번 마감의 문서 3개 수정(이 문서·`docs/gates/f0/spec-hash.md`·Plan 진행 상태)은 시험 소스·classpath 입력이 아니므로 시험 재실행·빌드는 필요하지 않습니다.

### 3.2 증거 경로

- 정제(artifact 허용): `gateway-eval/build/aigw-evidence/test/leaf-results.json`(`records` 0, `executedTestCount` 58, 위 `runId` — 이 세션에서 읽기로 일치 확인)
- 원시(artifact 금지): `gateway-eval/build/aigw-otr/test/open-test-report.xml`, `gateway-eval/build/aigw-otr/test/run-binding.json`
- Gradle JUnit XML: `gateway-eval/build/test-results/test/TEST-com.nori.ai.gateway.eval.*.xml`(12개)
- SPEC 결속 기록: `docs/gates/f0/spec-hash.md`
- 해석 버전: `docs/gates/f0/test-reporting-version.txt`

### 3.3 시험 클래스(12개 / 58건)

`EvidenceIngestGuardTest`, `GateEvaluatorTest`, `JsonCanonicalTest`, `LeafCatalogTest`, `LeafReportRoundTripFunctionalTest`, `PerfReporterTest`, `RecordCliTest`, `RunWorkingDirFunctionalTest`, `SpecGuardTest`, `SpecInputPackageTest`, `TestCountGuardFunctionalTest`, `TestEvidenceVerifyTest`(모두 `com.nori.ai.gateway.eval`).

### 3.4 과거 실패 이력(현재 FAIL 아님)

- 중간 회차 43/56 → 54/56 → 55/58 은 개발 중 이력이며 최종 회차 58/58 로 대체되었습니다. 마지막 수정 근거:
  - `EvidenceRecord`: SPEC 결속 오류를 다른 검증보다 우선 판정, 누락된 주석 복구.
  - `PerfReporter`: 집계값(히스토그램·평균) 입력 거부를 다른 오류보다 우선.
  - `TestCountGuardFunctionalTest`: 산출물 직접 변조를 `verify` 경로가 재빌드로 덮어 버리던 오라클 수정.
  - `SpecBaseline`: `seq` 정수형 검증.
  - 제품 코드가 아닌 불필요 탐색 fixture(`otr-probe` 의 `build.gradle.kts`·`settings.gradle.kts`) 2개 삭제.

## 4. 근본 검증 핵심

- **실제 OTR 왕복**: `LeafReportRoundTripFunctionalTest` 가 TestKit 으로 Gradle 9.2.1 실제 빌드·실제 JUnit 실행 → OTR → `leaf-extract` 왕복(PASS/FAIL/SKIP/ABORTED/동일 tag 혼합/시험 없는 leaf/catalog 밖 tag exit 4), 가짜 XML 입력 아님.
- **cache 재사용·입력 변경 재측정**: `TestCountGuardFunctionalTest` 가 `UP-TO-DATE`·`FROM-CACHE` 시 원 측정값(`runId`·`specSha`·`inputFingerprint`) 유지, SPEC·기준선·catalog·시험 resource 바이트 변경 시 실제 재실행·새 `runId`, 옛 OTR 잔류 → `STALE_TEST_EVIDENCE`, 필수 task 0건 → `NO_TESTS_EXECUTED`.
- **원문 비노출**: 정제 JSON·stdout·stderr·영수증·예외 메시지에 sentinel·실패 메시지·스택·자격 값 0.
- **회차·권위·라벨**: `GateEvaluatorTest` 가 마지막 시도만 사용, 부분 회차 무시(이전 PASS 부활 0), 부모 회차 혼합 음성, 권위 밖 producer 는 정보로만, 빌드 신원 불일치 제외, 사슬 밖·영향 leaf 옛 레코드 제외.
- **엄격 package**: `SpecInputPackageTest` 가 CRLF·BOM·말미 개행 없음 바이트 일치 왕복, 다중 member·후행 바이트·깨진 gzip·gzip bomb exit 4, host/scheme/redirect exit 2, 404/403 exit 5, 다른 내용 덮어쓰기 0.
- **성능 필수 측정 완전성**: `PerfReporterTest` 가 원시 `MEASUREMENT` 만 입력, 다른 부모 회차 표본 혼합 0, 기준 파일 부재 시 레코드 0, 집계값 입력 거부.

## 5. Spec/Plan 대비 차이

- 코드 계약 변경 0. 원 SPEC 변경 0(승인 SHA256 `14d6cc2e…18ef8` 불변, 실제 `spec-baseline.md`·hash guard 변경 0).
- Plan 은 확정 계약을 유지하며, 이번 마감에서 진행 상태 표기(메타 "Plan 상태"·"실행·증거 상태" 행, P01 체크박스 `[x]`·완료 기록 1줄)만 갱신했습니다(P02~P30 체크박스·SPEC AC/ISO 변경 0).
- 카탈로그 `leaf-catalog-v2.json` 의 `sourcePlanSha256`=`28cfae26d6574d65d68a2597449f3f2395c72060a6ac4a73a185a8b8acf24506` 은 P01 착수 시 승인된 Plan 의 스냅샷 SHA 입니다. 이번 Plan 진행 상태 표시 갱신은 계약·§12.4 내용을 바꾸지 않으므로 카탈로그를 새 해시로 재발급하지 않습니다.
- Dev Report 경로만 `.claude/` → `docs/ai-rag-gateway-architecture-dev-report.md`(restricted 쓰기 거부 때문).
- Plan 의 명령 실행 주체는 구현 세션이 아니라 root(정책상 구현 세션 Bash 거부).
- 명령에 `--console=plain`·`-LiteralPath` 를 더한 것은 출력 형식·경로 해석만 바꾸며 판정 의미는 같습니다.

## 6. P02 착수 전제: Python 3.12 툴체인(root 수행)

- 출처: CPython 공식 Windows 문서 §4.3 <https://docs.python.org/3.12/using/windows.html#the-nuget-org-packages>, CPython 소유 패키지 <https://www.nuget.org/packages/python/3.12.10>(공식 64bit 빌드). NuGet 관리 도구 설치 없음.
- 무결성: 공식 V2 메타데이터 `PackageHashAlgorithm`=SHA512·`PackageHash` 와 V3 flatcontainer 다운로드 바이트 대조 일치 후 저장. 14,515,433 bytes, SHA256 `0eb85c2dfccccf1b17352de4c397f69194035b7d37149eacc16f1147d93de3b8`, SHA512(base64) `u9pNz2iKlCEbYtUJaKkbOPMF0LjR7NkCafdKhvigpPzrt8oWKgdTpHaR6z3wyWQAm9PYGUxv0Zr66NX9AeHMDw==`.
- 해제: `C:/workspace_nori/.tools/aigw-python-3.12.10/python.3.12.10.nupkg`, entry 1,329개 전부 최종 절대 경로가 root 안·중복/ADS/절대 경로/심볼릭 링크 거부 검사 후 해제.
- 기준 실행 파일 `C:/workspace_nori/.tools/aigw-python-3.12.10/tools/python.exe`. 프로세스 env `AIGW_PYTHON_EXE` 로 Plan 검사: `-I --version` → Python 3.12.10 exit 0.
- `& $env:AIGW_PYTHON_EXE -I -m venv C:/workspace_nori/.venvs/aigw-eval` → exit 0(프로세스 종료 확인). venv `Scripts/python.exe -I --version` → Python 3.12.10 exit 0, `sys.prefix != sys.base_prefix` → True.
- 전역 PATH·레지스트리·영구 환경 변수·기존 Python 설정 변경 0. P02 `eval/py` requirements 설치·생성 없음. `.tools`·`.venvs` 는 Plan 실행 도구 준비에 따른 task-local 파일(root 허용).
- 따라서 P02 는 "Python 미준비 차단"이 아니라 "사용자 별도 지시 대기"로 미착수입니다.

## 7. 알려진 한계

- 수용 로그 증거 블록 0개·깨짐: suite 컨텍스트를 추측하지 않고 부모 수준 `ABORTED` 장벽으로 기록 — 그 이전 수용 PASS 는 제외, 이후 명시 suite `COMPLETED` 만 회복.
- Gradle 9.2.1 고정. `DefaultTestFilter` 내부 API 를 사용하므로 Gradle 업그레이드 시 재검증 필요.
- 실제 빌드에서 Gradle 10 비호환 deprecated 경고가 출력되나 현재 9.2.1 에서는 실패가 아닙니다.

## 8. 실행하지 않은 것(P01 완료 조건과 분리된 후속 게이트)

- G-REPO 원격 프로젝트 생성, `spec-input publish`/`fetch` 실제 레지스트리 실행(G-SPEC-HASH (b)).
- SSH·k8s·실 Source/API 호출, 모델·배포, 실 Gateway AC·ISO 판정.
- TC Backend/Front·기존 exec 조립·운영 설정 변경 0. git commit/push 0.
- 원문·민감값·실제 API 응답 본문은 이 문서에 기록하지 않았습니다.

## 9. 변경 파일(build/.gradle/.git·외부 `.tools`/`.venvs` 제외)

- 루트·빌드: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `.gitattributes`, `.gitignore`
- 문서: `README.md`, `docs/README.md`, `evidence/README.md`, `docs/gates/spec-baseline.md`, `docs/gates/f0/spec-hash.md`, `docs/gates/f0/test-reporting-version.txt`, `docs/ai-rag-gateway-architecture-dev-report.md`(이 문서)
- `gateway-eval/build.gradle.kts`
- `gateway-eval/src/main/java/com/nori/ai/gateway/eval/`: `EvalCli.java`, `EvalException.java`, `Args.java`, `io/Json.java`, `spec/SpecBinding.java`, `spec/SpecGuard.java`, `spec/SpecBaseline.java`, `spec/SpecInputResolver.java`, `spec/SpecInputPackage.java`, `report/LeafCatalog.java`, `report/GateEvaluator.java`, `report/GateReportWriter.java`, `report/EvidenceRecord.java`, `report/EvidenceRepositoryFs.java`, `report/EvidenceIngestor.java`, `report/OtrLeafExtractor.java`, `report/TestEvidenceVerifier.java`, `report/ManualRecorder.java`, `report/PerfReporter.java`, `report/BuildIdentity.java`
- `gateway-eval/src/main/resources/com/nori/ai/gateway/eval/`: `leaf-catalog-v2.json`, `user-run-schema-v1.json`
- `gateway-eval/src/test/java/com/nori/ai/gateway/eval/`: 시험 12개(§3.3) + `TestSupport.java`, `TestKitSupport.java`
- `gateway-eval/src/test/resources/`: `policy/plan-catalog-contract.txt`, `fixtures/otr-probe/src/test/java/probe/ProbeTest.java`
- 레포 밖(진행 상태 표기만): `C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture-plan.md` — 메타 2행·P01 체크박스·완료 기록 1줄(계약 변경 0, §5)

## 10. 다음 단계

- root 독립 최종 파일·범위 대조 완료(2026-10-29, root 보고 인용):
  - 58건 전부 PASS / 0 FAIL·ERROR·SKIP·ABORTED(실제 TestKit 포함), 직후 별도 `test-evidence verify` exit 0 `TEST_EVIDENCE_VERIFIED`, `spec-guard` exit 0 `FILE_HASHED`·`LOCAL_FILE`.
  - 실제 SPEC SHA256 = 승인값 불변. reporting/launcher 6.0.3, Python 3.12.10·venv 검증 완료.
  - 46파일 소스 digest `5cd0b1be…c112f` 재계산 일치(시험 통과 당시와 동일). `summaryContentSha` 재계산 일치, summary·`run-binding.json`·current fingerprint 의 `runId`·`inputFingerprint` 일치(`603fbbaa-…3c21` / `18493823…e55d` 그대로), 카탈로그 stage 필수 집합 불일치 0, UTF-8 BOM/CR 이상 0.
  - 기존 16개 레포 git status 전후 동일(exec SPEC 의 기존 미커밋 수정·common `package.json` 원상 보존), commit/push 0.
- P01 도구 시험 완료는 Gateway runtime AC/ISO 합격이 아닙니다(전부 NOT_RUN 유지).
- P02 이후는 미착수이며 사용자 별도 지시를 기다립니다.

---

## 11. P02 F0 하네스 — 골든셋 사전등록·토큰 계수·A/B 러너 (2026-10-29, 사용자 "P02 진행해서 보고해" 지시)

- 위 §1~§10 은 P01 기록이며 그대로 보존합니다. 그 안의 "P02 미착수" 문장은 P01 마감 시점 기록이고, 현재 상태는 이 §11 이 정본입니다.
- 실행 주체: 이 세션(독립 Claude Code, claude-opus-5-5)이 아래 명령을 직접 실행했습니다. P03(실측)·이후 phase 는 시작하지 않았습니다.
- 입력 해시 확인(착수 전, 실제 바이트): SPEC `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8`, Plan `b3d9e53be4263448987f4bc974d9e0402ea601f73bcd0596f72c299178d26461` — 지정값과 일치. SPEC·Plan 은 읽기 전용(변경 0).
- 적용 지침 확인: `nori-ai-gateway`·`nori-tc-exec`·`C:/workspace_nori` 에 AGENTS.md/CLAUDE.md 없음. 사용자 지정 exec 문서를 기준으로 진행했습니다.

### 11.1 현재 판정

| 항목 | 상태 |
|---|---|
| P02 코드·데이터·문서 구현 | 완료(로컬) |
| ① `:gateway-eval:test` | exit 0 — 107건 = 107 PASS / 0 FAIL / 0 SKIP / 0 ABORTED (P01 기존 58건 회귀 + P02 신규 49건, 실제 TestKit 포함) |
| ①b `test-evidence verify --tasks :gateway-eval:test` | exit 0 `TEST_EVIDENCE_VERIFIED`(① 직후 별도 실행) |
| ② Python 3.12 검사·venv 재사용 | exit 0 — 기준 `Python 3.12.10`, venv `Python 3.12.10` |
| ③ `pip install --require-hashes -r eval\py\requirements.txt` | exit 0 — 17개 패키지 설치, `pip check` 이상 없음 |
| ④ `f0 cases …` | **exit 2 `ARG_LIMITS_UNRESOLVED` — PASS 아님.** G-ARG-LIMITS 차단 기록(NORMAL 1행만 생성, MAX·ESCAPE 거부) |
| Python 도구 시험(추가) | 10건 = 10 PASS(`unittest`, tiny fixture tokenizer — 공식 Qwen 계수 아님) |
| PREREG commit SHA(완료 증거) | **미완료** — 레포 git commit 0, 이번 지시상 commit 금지. `registeredCommit=null`, 상태 `UNCOMMITTED` 명시 |
| tokenizer 원본 HF pin | **미완료** — 외부 다운로드 승인 필요(이번 세션 다운로드 0) |
| G-ARG-LIMITS | **UNRESOLVED 3(from·to·q)** → AC-37-OUT-MAX·ESCAPE NOT_RUN, F0 exit 차단 |
| 실측·토큰 계수·AC 판정 | 실행 0(P03 범위). AC-37·AC-38 은 전부 NOT_RUN 유지 |

### 11.2 구현 범위

- 골든셋 `eval/golden/tc-f0-v1.jsonl`(22문항): 2026-10-19 계획기 4건(G01 장애 로그→코드, G02 "EQP_001 상태+LOT", G03 "WM 에서 LOT-001 재고·납기"(`t=UNS`), G04 SPEC §6.1 지식 질문), GQ 양성 G05 "여기서 뭘 물어볼 수 있어?", GQ 우회 음성 G06·G19, 후속 차수 G07 "이 모델 체크아웃해줘"(`t=UNS`)·G16 Replay, AC-38 G08 "WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했어?"(공통 TC Context: `selectedResourceRefs=[EQP PHOTO_001]`, `+09:00`, `[2026-10-03T00:00, 2026-10-04T00:00)`, 기대 `t=INC`·`INCIDENT_ANALYSIS`), 타 시스템 UNS(G14 HRM·G15 WM), 이름 언급 TC 질문 G17, Context 창 적용 G20 등. `t` enum·taskType·workflow 는 SPEC §5.3·§6·§14.5 값만 사용(새 enum 0).
- Registry fixture `eval/golden/tc-registry-fixture-v1.json`: 시스템 TC/WM/HRM 이름·별칭·소유 영역(SPEC §16 문구), capability(1차 IMPLEMENTED 7, PHASE_2/3 DISABLED 8 — SPEC §5.3 ID), workflow(§6.1~§6.3·§5.3 GQ), EQP 리소스(`PHOTO_001`·`EQP_001`·`ETCH_002`·`CVD_003` + 한글·숫자·ASCII 혼합 64코드포인트 최대 길이 ID).
- 프롬프트 `eval/f0/prompts/classify-v1.txt`(정적 접두부 = 시스템 지시·압축 스키마·"이름 언급이 아니라 요청 대상 자료의 소유 시스템으로 판단" 지시·`{{REGISTRY}}`, 출력 128), `plan-v1.txt`(Path A, `{{TOOLS}}`, 출력 160). 질문·Context 는 마지막 user 메시지(RFC 8785)에만.
- G-ARG-LIMITS: `docs/gates/f0/arg-limits.md` + `eval/f0/arg-limits-v1.json`(두 본 일치 시험). `t` CONFIRMED(SPEC §14.5 enum, 3), `eqp` CONFIRMED 64 코드포인트(`TcEqpEntity.java:56` `length = 64`, `V1__baseline.sql:420` `VARCHAR(64)`, `EqpCreateRequest.java:33` 문자 제한 없음 — 시험이 실제 줄 내용 대조), `from`/`to` UNRESOLVED(`TimeWindowResolver` P13 미구현, SPEC 은 예시 표기뿐 — 16자 고정 안 함), `q` UNRESOLVED(Loki·GitLab 공식 상한 미확인, Knowledge P23 미구현 — 임의 64 안 함).
- 경계 케이스 `eval/f0/output-boundary-cases.jsonl`(현재 NORMAL-01 = SPEC §14.5 예시 형태, RFC 8785 `expectedJson`) + 차단 표지 `output-boundary-cases.blocked.json`(`MAX`·`ESCAPE`, `from`·`to`·`q`). CONFIRMED 시 MAX(MIXED·HANGUL·DIGIT·ASCII 4표본, fixture 등록 최대 EQP)·ESCAPE(`"`·`\`·U+0001 원문 보존) 생성 로직은 시험으로 검증.
- 사전등록 `eval/f0/PREREG.md`: 미커밋 상태 명시, 등록 절차(C1 등록 commit ≠ C2 PREREG commit, 자기 해시 고리 0), 축별 판정 규칙, 회차 1 정확도·전 회차 지연(nearest-rank)·실패 분모 유지, AC-37-OUT/AC-38-F0/잠정 후보 규칙, N4 결속·재판정, 기계 판독 블록(입력 11개 SHA·`harnessDigest`=`188d30b0acd20e63281e23c57a5276933fb13fc3e78644ffd90d9fe6d8931a0e`·파라미터 R=5·max_tokens 128/160·temperature 0·timeout 120 s·경로 예산).
- 메시지 입력 `eval/f0/usage-probe-input-v1.jsonl`(U1·U2 = classify-v1 실제 조립, 시스템 접두부 공유), `eval/f0/token-golden-input-v1.jsonl`(13행: 한국어·영어·숫자·특수·제어문자·system+user·다중 턴·분류 실제 조립 2·CJK/이모지·JSON형·≈2,800 근처 근사 1·ASCII 식별자), `eval/f0/chat-renderers-v1.json`(빈 등록 — §11.6), `eval/f0/GGUF-COUNT.md`.
- Java(`gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/**`): `F0RunnerCli`(cases·preflight·run·boundary-gen·gguf-count·chat-template·usage-probe·token-golden·report[--rejudge|--spec-record-check]·prereg-check), `OllamaChatClient`(JDK HttpClient, 경로 허용 5개, loopback endpoint 만, preflight 증표 필수, `num_ctx` 미포함), `Preflight`, `RegistryFixture`, `ResourceFixture`, `GoldenSet`, `MessageAssembler`, `ClassificationJudge`(1회 재시도), `PlanJudge`(ⓐ backstop), `MetricsAggregator`, `ArgLimits`, `BoundaryCases`, `HarnessDigest`, `GitReader`(읽기 전용 허용 목록), `Prereg`, `MeasurementSession`, `ChatRenderers`, `F0Measurements`, `F0Judgement`, `F0ReportWriter`. `f3/EmbeddingProfile`(직접 HTTP 0).
- Python(`eval/py/`): `requirements.txt`(tokenizers 0.23.3 + 전이 16개, 모두 `==`·sha256 — 2026-10-29 PyPI JSON API 공개 sha256 과 다운로드 바이트 17/17 일치 확인, Windows x86_64 wheel 기준), `count_tokens.py`(인자 4개 필수 exit 2, pin 불일치 exit 3, 비 tokenizer 파일 exit 4, 출력 덮어쓰기 거부, 결과 = caseId·kind·토큰 수·SHA·보정 행만), `pdg_vectors.py`(hashlib, Plan P08 고정 벡터 V1~V4·음성 N1~N3), `tests/`(시험 2파일 + Plan 벡터 사본 fixture).

### 11.3 P01 공통 코드 연결(최소 변경)

- `EvalCli.java`: `f0`·`f3` 를 공통 인자 해석 전에 `F0RunnerCli`·`EmbeddingProfile` 로 분기(자체 플래그 `--rejudge`·`--spec-record-check` 때문), 기존 `COMMAND_NOT_IMPLEMENTED_IN_P01` 분기에서 f0·f3 제거(`ci-evidence` 는 유지). P01 `RecordCliTest` 의 `f0 report`·`f3 report` exit 2 기대는 그대로 충족.
- `gateway-eval/build.gradle.kts`: `test` 의 선언 입력에 `eval/**`(results·chat-template·`__pycache__` 제외)와 `docs/gates/f0/arg-limits.md` 추가 — 레포 루트 사전등록 입력 변경 시 시험 재실행.
- `EvidenceRecord` 필드·문자열 allowlist 확장 0: F0 레코드는 기존 허용 필드(`metrics`·`rawResultsSha256`·`measurementSpecSha`·`rawRunId`·`buildIdentity` 등)만 쓰고 문자열 값은 식별자 형식만. 루트 `build.gradle.kts`·settings·README·카탈로그 변경 0.
- F0 부모 manifest: `f0 report` 가 run-binding 신원으로 `RUN PARENT`(buildIdentity = `harnessDigest`·`runtimeManifestSha256`·`parameterManifestSha256`, P01 `BuildIdentity` F0 키)를 쓰고, `GateEvaluator` 로 실제 판정되는지 `F0RejudgeTest` 가 확인(원 판정 후 AC-38-F0 PASS·AC-37 NOT_RUN(SPR 없음) → SPEC 반영·재판정·SPR 후 AC-37-CMP·OUT-NORMAL PASS, MAX·ESCAPE NOT_RUN, 단계 NOT_RUN).

### 11.4 실행 명령과 실제 결과(이 세션, 각 명령 별도 실행)

공통: cwd `C:/workspace_nori/nori-ai-gateway`, Gradle 명령은 `C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile` 로 실행하고 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC 경로·`DEBUG`=빈 값. Python 은 프로세스 env `AIGW_PYTHON_EXE` 만(영구 env·PATH 변경 0).

| # | 명령 | 결과 |
|---|---|---|
| ① | `.\gradlew.bat :gateway-eval:test --console=plain` | exit 0, BUILD SUCCESSFUL in 2m 19s. 정제 요약 107/107 PASS, 26 클래스 |
| ①b | `.\gradlew.bat :gateway-eval:run --console=plain -q --args='test-evidence verify --tasks :gateway-eval:test'` | exit 0, `TEST_EVIDENCE_VERIFIED` |
| ② | §4.1 `AIGW_PYTHON_EXE` 검사 2줄 → `& $env:AIGW_PYTHON_EXE -I -m venv C:\workspace_nori\.venvs\aigw-eval` → venv `-I --version` | exit 0, `BASE_OK Python 3.12.10`, `VENV_OK Python 3.12.10`(기존 venv 재사용, 삭제 0) |
| ③ | `C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe -I -m pip install --require-hashes -r eval\py\requirements.txt` | exit 0, 17개 설치 |
| ④ | `.\gradlew.bat :gateway-eval:run --console=plain -q --args='f0 cases --limits eval/f0/arg-limits-v1.json --fixture eval/golden/tc-registry-fixture-v1.json --out eval/f0/output-boundary-cases.jsonl'` | java exit 2 `ARG_LIMITS_UNRESOLVED` → Gradle 실패(exit 1). **차단 기록이며 PASS 아님** |
| 추가 | `…\python.exe -I -m unittest discover -s eval/py/tests -v` | 10 PASS(누락 인자 exit 2·SHA 불일치 exit 3·비 tokenizer exit 4·변조 케이스 거부·덮어쓰기 거부·원문 sentinel 비노출·pdg V1~V4 일치·N1~N3 거부·기대값 변조 exit 1) |
| 추가 | 실제 경로 `count_tokens.py --tokenizer eval/f0/tokenizer/…`(원본 부재) | exit 2 `INPUT_MISSING`, 출력 파일 0 |
| 추가 | `f0 prereg-check --prereg eval/f0/PREREG.md` | `localMismatches=[]`(등록 SHA = 현재 바이트) 출력 후 exit 2 `PREREG_NOT_COMMITTED` — 미커밋 정직 표시 |

- 개발 중 이력(현재 FAIL 아님): 시험 컴파일 오류 1건(`var` 복합 선언) 수정, 개발용 필터 실행 49건 중 1건 FAIL(PREREG 해시 자리표시자) → `prereg-check` 값으로 채운 뒤 전체 ①·①b 를 마지막에 다시 실행.

### 11.5 정제 증거 요약(원문·민감값 없음)

| 필드 | 값 |
|---|---|
| `runId` | `464d687a-c328-4d08-a15f-a7e76328250c`(정제 요약·`run-binding.json` 일치) |
| `recordedAt` | `2026-10-29T05:17:58.220411100Z` |
| `executedTestCount` / `passed` / `failed` / `skipped` / `aborted` | 107 / 107 / 0 / 0 / 0 |
| `specSha` | `14d6cc2e…18ef8`(`FILE_HASHED`, `LOCAL_FILE`) |
| `inputFingerprint` | `6afb0323b9e03ec28897ada46e460bff27bcec61365df6cf6ffb7501ddef33f2` |
| `otrSha256` / `contentSha256` | `c60e04477b0ea620bc464d267308fad3790462af3a8edfa989e37cfccdcbdbc7` / `b3cba14e9e38c56dcc636fe4efd97ec649b94015f1ea276395749217852442f2` |
| `buildIdentity.gitSha` / `runtimeClasspathSha256` | `null` / `null`(미커밋·P04 이전 — 가짜 값 0) |
| leaf 레코드 | 0(시험에 leaf 태그 없음 — F0 권위 component 는 F0R·SPR 이며 JUNIT_OTR 아님) |

### 11.6 시험(P02 신규 14 클래스 / 49건, 모두 `com.nori.ai.gateway.eval.f0`·`f3`)

- Plan 필수: `ClassificationJudgeTest`(5), `MetricsAggregatorTest`(4 — R 3·5·7 분모 불변, 회차1 오답·회차2 정답 = 오답, 회차1 timeout 분모·지연 유지, nearest-rank, 누락·중복·범위 밖 = `DENOMINATOR_DRIFT`), `BoundaryGenJudgeTest`(3 — 완결 pass, length 1회 재시도, 1자 절삭·`\` 손실·필드 생략·129토큰 실패, 결과에 원문 0), `UsageProbeTest`(3 — 축소/비축소 oracle, flush 순서, 허용 필드, 렌더러 미검증 거부), `TokenGoldenTest`(3 — flush 뒤 cold, 결속 필드, digest 불일치 exit 6, 중단 시 `.partial` 만), `ChatTemplateCaptureTest`(2 — `/api/show` sentinel 저장 0, exit 6, 재캡처 거부), `OllamaChatClientContractTest`(3 — 경로 ⊆ 5개·embedding 0, body 키 정확·`num_ctx` 0, 비 loopback 거부, 증표 없는 추론 0, 소스 정적 검사: 관리 API·`System.out`·`printStackTrace` 0), `PreflightSummaryTest`(3 — 허용 필드만, 미상주·생성 ctx≠4096 exit 4, bge-m3 ctx 기록만, 실패 시 추론 0, 측정 중 미상주 → exit 5 부분 보존), `ArgLimitsSchemaTest`(4 — UNRESOLVED 거부·근거 없는 CONFIRMED 거부·형식 불일치·MAX/ESCAPE 무손실·실제 근거 줄·문서 일치·케이스 파일 재생성 일치), `GoldenSetSchemaTest`(3), `F0RejudgeTest`(5 — S0→S1 재판정 새 레코드·원 레코드/원시 바이트 불변, 하네스 1바이트 → `HARNESS_CHANGED`, `measurementAffecting=true` → `REMEASURE_REQUIRED`, 원시 변조 → `RAW_RESULTS_CHANGED`, run-binding 재작성 exit 2, spec-record-check 일치 PASS·1바이트 다름 FAIL, PREREG 이전 측정 레코드 0, P01 `GateEvaluator` 연결).
- 추가: `F0RunTest`(3 — 라운드로빈 순서·결속 선행·정제 행·endpoint 오류 부분 보존 exit 5·timeout 표본 유지·인자/등록 경로/runId 불일치·중복 결속 거부), `PreregCommitGuardTest`(6 — 미커밋·PREREG 미커밋·비 git·dirty·순서·등록 commit 바이트 불일치·입력 변경·하네스 파일 추가 → endpoint 호출 0 거부, C1=C2 거부, harnessDigest 제외 규칙, 실제 `GitReader` 쓰기 명령 거부, **실제 레포 PREREG 로 `EvalCli f0 run` → `PREREG_NOT_COMMITTED`·요청 0·결과 디렉터리 0**, 레포 PREREG 등록 SHA = 현재 바이트), `EmbeddingProfileTest`(2).
- 모든 HTTP 시험은 `127.0.0.1` JDK `HttpServer` stub(LLM_STUB 성격)이며 실제 Ollama·Jetson 호출 0. stub 결과는 실측이 아닙니다.

### 11.7 설계 해석(Plan 문구의 구현 방식 — root 확인 권장)

- run-binding 위치: 명령별 출력 디렉터리의 `run-binding.json`(run A/B·boundary·gguf·chat-template), 파일 출력 명령은 형제 `usage.run-binding.json`·`token-golden.run-binding.json`(Plan 의 `<results>/run-binding.json` 을 명령마다 하나씩·덮어쓰기 금지로 해석). `f0 report` 는 모두 같은 runId·신원·PREREG commit 인지 확인하고 섞이면 거부.
- `--run-id` 가 없는 명령(gguf-count·chat-template·usage-probe·token-golden)은 P03 0단계가 설정하는 `AIGW_PARENT_RUN_ID` 로 회차를 받고, `eval/f0/results/<X>/` 아래 출력이면 X = runId 를 강제.
- 런타임 manifest 의 `templateSha256` 은 모든 측정 명령이 시작 시 `/api/show` 의 `template` 필드 SHA 로 계산(원문은 chat-template 명령만 저장), `tokenizerSha256` 은 pin 파일과 실제 tokenizer.json 바이트가 같을 때만 사용.
- 공식 계수(`official-count.json`, Python)는 run-binding 이 없으므로 tokenizer SHA = run-binding pin, cases SHA = 등록값, 파일 시각 > PREREG commit 시각일 때만 판정 입력.
- `f0 cases` 는 UNRESOLVED 시 NORMAL 만 쓰고 차단 표지 파일과 함께 exit 2(P03 의 NORMAL 측정 입력 보존). `f0 prereg-check`(읽기 전용 보조 명령)를 추가했습니다.
- 추출 규칙(사전등록): `eqp`·`from`·`to` 는 질문 본문 표현에서만 추출하고 Context(`selectedResourceRefs`·`timeWindow`)는 Gateway 가 적용 → AC-38 G08·G20 기대 인자는 null. 시간은 SPEC §22.2 문구, `eqp` 는 같은 원칙의 유추이므로 root 확인을 권장합니다. G08 의 `referenceTime`(2026-10-04T09:00+09:00)은 SPEC 미지정이라 창 이후 값으로 정했습니다.

### 11.8 결정 필요(root 보고 — 해당 부분은 fail-closed 로 두고 나머지 구현 완료)

1. **usage-probe 렌더 문자열 근거**: `/api/generate raw` 에 chat 과 "같은 렌더 문자열" 을 보내려면 모델 chat template 렌더가 필요한데, template 을 아직 캡처하지 않았고 추정은 금지입니다. 구현: 캡처 template SHA 가 `eval/f0/chat-renderers-v1.json` 에 검토 기록(`reviewRef`)과 함께 등록된 경우에만 ChatML 렌더러 사용, 아니면 run-binding·추론 호출 전 `TEMPLATE_RENDERER_UNVERIFIED`(exit 2). 문제: 이 등록 파일은 사전등록 입력(harnessDigest)이라 P03 측정 전에 template 을 알아야 합니다. **권장안**: P03 전 승인된 사람이 읽기 전용 `/api/show` 로 template 만 확인(또는 Ollama 공개 model 페이지의 `qwen3:4b-instruct` template 과 대조)해 렌더 형식 일치를 검토·등록한 뒤 C1 에 포함. 대안: G-USAGE 의 `evalCount` 지표를 SPEC/Plan 소유자가 범위에서 빼는 결정.
2. **G-ARG-LIMITS `from`·`to`·`q`** `[SPEC]`: 시각 형식(초·소수초·offset 허용)과 `q` 상한의 근거 확정 또는 SPEC 소유자 결정. 해소 전 AC-37-OUT-MAX·ESCAPE NOT_RUN(F0 exit 차단).
3. **tokenizer 원본**: HF `Qwen/Qwen3-4B-Instruct-2507` `tokenizer.json` 다운로드·pin 은 외부 다운로드 승인 필요. 미준비 시 모든 측정 명령 `TOKENIZER_PIN_MISSING`.
4. **사전등록 commit**: C1(등록 대상·분석 코드)·C2(PREREG `registeredCommit`=C1) 두 commit 이 P03 전 필수 — 이번 지시상 commit 금지라 미수행. 완료 증거 "PREREG commit SHA" 는 미충족입니다.
5. `requirements.txt` hash 는 Windows x86_64 wheel 기준입니다. Linux CI 에서 쓰려면 같은 절차로 해당 플랫폼 hash 추가가 필요합니다.

### 11.9 실행하지 않은 것·한계

- Jetson·SSH·k8s·실 Ollama 호출·모델 로드·운영 Source/API·서비스 재시작·설정 변경 0. 실측·토큰 계수 결과·AC PASS 생성 0. `eval/f0/results/` 는 실제 레포에 생성되지 않았습니다(가드 시험 확인).
- git commit/checkout/restore/stash/push 0(레포는 git 저장소가 아니며 `gitSha`=null). 외부 전송은 PyPI 공식 index 의 wheel 다운로드·JSON API 해시 조회뿐(모델 아티팩트 다운로드 0).
- `token-golden` 의 ≈2,800 행은 근사 입력이며 실제 토큰 수는 P03 측정값만 유효합니다. stub 시험 PASS 는 Gateway AC/ISO·F0 측정 합격이 아닙니다.
- 시험은 워크스페이스의 TC 소스 근거 파일(`nori-tc-common`·`nori-tc-exec`·`nori-tc-ui-backend`)을 읽어 arg-limits 근거 줄을 대조합니다 — 그 파일이 없는 환경(CI)에서는 그 시험이 실패합니다.
- 생성물 정리: Python 시험의 `eval/py/tests/__pycache__`(pyc 2개)만 경로 경계 확인 후 삭제. wheel 임시 다운로드는 사용자 Temp 의 작업 전용 디렉터리.

### 11.10 변경 파일(P02, build 산출물 제외)

- `gateway-eval/build.gradle.kts`, `gateway-eval/src/main/java/com/nori/ai/gateway/eval/EvalCli.java`(분기만)
- 신규 main: `…/eval/f0/` 20개(`F0RunnerCli`, `OllamaChatClient`, `Preflight`, `RegistryFixture`, `ResourceFixture`, `GoldenSet`, `MessageAssembler`, `ClassificationJudge`, `PlanJudge`, `MetricsAggregator`, `ArgLimits`, `BoundaryCases`, `HarnessDigest`, `GitReader`, `Prereg`, `MeasurementSession`, `ChatRenderers`, `F0Measurements`, `F0Judgement`, `F0ReportWriter`), `…/eval/f3/EmbeddingProfile.java`
- 신규 test: `…/eval/f0/` 시험 13개 + 지원 `OllamaStub`·`F0TestRoot`·`StubAnswers`, `…/eval/f3/EmbeddingProfileTest.java`
- `eval/golden/tc-f0-v1.jsonl`, `eval/golden/tc-registry-fixture-v1.json`
- `eval/f0/PREREG.md`, `eval/f0/prompts/classify-v1.txt`, `eval/f0/prompts/plan-v1.txt`, `eval/f0/arg-limits-v1.json`, `eval/f0/output-boundary-cases.jsonl`, `eval/f0/output-boundary-cases.blocked.json`, `eval/f0/usage-probe-input-v1.jsonl`, `eval/f0/token-golden-input-v1.jsonl`, `eval/f0/chat-renderers-v1.json`, `eval/f0/GGUF-COUNT.md`
- `eval/py/requirements.txt`, `eval/py/count_tokens.py`, `eval/py/pdg_vectors.py`, `eval/py/tests/test_count_tokens.py`, `eval/py/tests/test_pdg_vectors.py`, `eval/py/tests/fixtures/pdg-vectors-plan-p08.json`
- `docs/gates/f0/arg-limits.md`, 이 문서(§11 추가)
- 레포 밖: venv 에 requirements 17개 패키지 설치(`C:/workspace_nori/.venvs/aigw-eval`). SPEC·Plan·기존 TC 레포 변경 0. Plan 진행 표시는 root 판정 뒤 root 가 갱신(이 세션 편집 0).

### 11.11 다음 단계

- root 최종 검증 후 Plan P02 진행 표시 갱신(root 몫).
- P03 착수 전 조건: §11.8 의 1~4(렌더러 근거·G-ARG-LIMITS·tokenizer pin·C1/C2 commit) 해소, 그 뒤 `f0 prereg-check` 가 `PREREG_VERIFIED` 를 내야 측정 명령이 시작됩니다. P03 은 이 세션에서 시작하지 않았습니다.

### 11.12 P02 후속 보완 (2026-10-29, 별도 독립 Claude Code 세션 — claude-opus-5-5)

- 위 §11.1~§11.11(최초 107건·Python 10건·harnessDigest `188d30b0…a0e`)은 이력으로 보존합니다. 현재 수치는 이 절이 정본입니다. P03 은 시작하지 않았습니다.
- 착수 전·종료 후 SPEC `14d6cc2e…18ef8`·Plan `b3d9e53b…26461` 실제 바이트 일치(변경 0). 범위: `gateway-eval/**`, `eval/py/**`, `eval/f0/PREREG.md` 해시 2줄, 이 절. 루트 build·TC 레포·`.claude` 변경 0, git 명령 0.

#### 원인별 재현 → 수정

| # | 재현(수정 전 실제 실행) | 수정 | 회귀 시험 |
|---|---|---|---|
| 1 | `count_tokens.py` 에 JSONL 행 `[]`·`42`·`null`·`"s"` → `COUNT_TOKENS_FAILED` exit 5(`row.get` AttributeError). 추가 점검: 중복 caseId·행/중첩 객체 중복 키·`expectedOutput` 숫자 값·`NaN` → exit 0 으로 계수됨(Java `BoundaryCases.load` 는 중복 caseId·중복 키 거부, `canonical()` = RFC 8785 전제는 문자열·null 평면 객체) | 객체 확인을 필드 접근보다 먼저, `object_pairs_hook` 중복 키 거부, `parse_constant` NaN/Infinity 거부, `expectedOutput` 값 str\|null, caseId 중복 거부 — 모두 exit 2 `CASES_INVALID`. 행 키 집합(question·context) 정확 일치는 Python 이 쓰지 않는 필드라 추가하지 않음 | `test_non_object_rows_exit_2_cases_invalid`, `test_contract_violations_exit_2_cases_invalid` |
| 2 | localhost stub: `usage.completion_tokens=128.9` 를 `chat` 이 128 로 수용, `boundary-gen` exit 0(128.9 가 ≤128 PASS 표본이 됨), `prompt_tokens=-1` 수용, preflight `context_length=4096.9` → `Verified` 발급. 원인: `canConvertToInt()` 만 사용(소수 잘림·부호 미검사) | `OllamaChatClient.isCount` = JSON 정수 리터럴 AND int 표현 가능 AND ≥0. `chat` usage 2필드·`prompt_eval_count`·Preflight `context_length`(있으면 검사, 없거나 null 은 기존대로 null 기록) 공통 적용. 위반 = 고정 `ENDPOINT_RESPONSE_INVALID`(exit 5)·preflight `PREFLIGHT_RESPONSE_INVALID`. `128.0` 도 정수 리터럴이 아니므로 거부 | `malformedUsageNumbersAreRejectedWithFixedErrorAndNoRawText`, `fractionalOrNegativeContextLengthFailsPreflight`, `fractionalCompletionTokensCannotProduceABoundaryPass`(exit 5·passRounds 0·attempt 0·결과에 128.9·sentinel 0) |
| 3 | 실제 `HttpServer`(헤더 즉시·본문 지연) + client timeout 1 s: 고정 길이 본문 정지 → 예외 없이 **8.8 s** 뒤 성공, chunked 미종료 → 8.0 s 뒤 `ENDPOINT_RESPONSE_INVALID`, 300 ms 마다 1바이트 → 9.4 s 뒤 성공, 본문 대기 중 interrupt → `ENDPOINT_UNREACHABLE`, F0 run 에서 chat 본문 정지 → TIMEOUT 표본이 아니라 run 전체 exit 5. 원인: `HttpRequest.timeout` 은 헤더 수신까지만, `ofInputStream().readNBytes` 는 무기한 | `send` 를 `sendAsync` + 상한 `BodySubscriber`(8 MiB 누적 초과 즉시 구독 취소 `ENDPOINT_RESPONSE_TOO_LARGE`, 비 200 은 본문 읽지 않고 `ENDPOINT_HTTP_ERROR`)로 바꾸고 `future.get(남은 기한)` — 송신~헤더~본문 끝 단일 총 기한(=기존 timeout 값, PREREG 120 s). 기한 초과·interrupt·실패 시 구독 cancel + `future.cancel(true)`·버퍼 해제, interrupt 는 플래그 복원 후 `ENDPOINT_INTERRUPTED`. 공용 ForkJoinPool 사용 0(HttpClient 자체 executor), 무기한 대기 0. 기존 `ClassificationJudge` 가 `ENDPOINT_TIMEOUT` 을 TIMEOUT 표본(분모·지연 유지)으로 남기는 경로 그대로 | `stalledFixedLengthBodyTimesOutWithinTotalDeadline`(1.0~4 s, 이후 같은 client 정상), `neverEndingChunkedBody…`, `slowTrickleBodyCannotExtendTheDeadline`, `interruptDuringBodyWaitIsPropagated`, `responseCapStaysAt8MiBIncludingEndlessStreams`(정확히 8 MiB 수용·+1 거부·무한 스트림 거부 — 이 1건은 수정 전에도 PASS), `stalledChatBodyIsRecordedAsTimeoutSampleAndRunContinues`(44 표본·complete) |
| 4 | Plan P02 는 "정수 차원 > 0, digest 형식" 만 요구하나 `EmbeddingProfile.build` 가 `> 65536` 거부 — 65537 시험이 수정 전 `PROFILE_DIMENSION_INVALID` 로 실패 재현. 또 `asLong()` 은 2^64+1024 같은 BigInteger 를 1024 로 감아 수용할 수 있었음 | 상한 제거, `isIntegralNumber && canConvertToLong && ≥1`, 값은 `longValue()` | `dimensionHasNoArbitraryUpperBoundButMustBeASafePositiveInteger`(65537·2^20·Long.MAX 양성 / 0·-1·1024.5·1024.0·2^63·2^64+1024 음성 = `PROFILE_DIMENSION_INVALID`) |

- 시험 지원: `OllamaStub` 에 `stallChatBody`(헤더·본문 절반 후 close 까지 정지)와 close 시 정리되는 executor 추가. 새 시험 클래스 `OllamaChatClientResponseTest`(9건). 모든 HTTP 는 `127.0.0.1` JDK `HttpServer` 이며 실제 Ollama·Jetson 호출 0, stub 결과는 실측 아님.

#### PREREG 갱신

- `prereg-check` 수정 후 첫 실행: `localMismatches=["eval/py/count_tokens.py","harnessDigest"]`(이번 변경과 정확히 일치). 실제 현재 바이트로 2줄 갱신: `count_tokens.py` `83a2cdf7…e624` → `df925293…7b3c`, `harnessDigest` `188d30b0…a0e` → `c35e8194…e38f`(`c35e819439118053b4feda5dff65ee1a86d3ef807e6dade8ac089a2ddf6ae38f`, PREREG 자신 제외 규칙 그대로). 그 밖 9개 입력 SHA 불변. `registrationStatus=UNCOMMITTED`·`registeredCommit=null` 유지.
- 갱신 후 `f0 prereg-check`: `localMismatches=[]` 출력 뒤 java exit 2 `PREREG_NOT_COMMITTED`(Gradle exit 1) — **미커밋에 따른 예상 차단이며 PASS 아님.**

#### 실행 명령과 결과(cwd 레포 루트, Gradle 은 `C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile` + 프로세스 env `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값)

| 순서 | 명령 | 결과 |
|---|---|---|
| a | `.\gradlew.bat :gateway-eval:test --tests "…f0.OllamaChatClientResponseTest"`(수정 전 재현) | 9건 중 8 FAIL(재현 목적). **직후 verify 를 실행하지 않음 — 절차 누락**(아래 b 이후 verify 로 대체 확인했으며 최종 판정은 e·f 기준) |
| b | `--tests …OllamaChatClientResponseTest --tests …f3.EmbeddingProfileTest`(client 수정 후·profile 수정 전) | 12건 중 1 FAIL(65537 재현), 직후 verify exit 4 `STALE_TEST_EVIDENCE`(필터·실패 포함 실행 — 예상 거부) |
| c | `--tests …f3.EmbeddingProfileTest`(profile 수정 후) | 3/3 PASS, 직후 verify `STALE_TEST_EVIDENCE`(필터 실행 — 예상 거부) |
| d | `f0 prereg-check`(갱신 전·후) | 위 PREREG 절 |
| e | `C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe -I -m unittest discover -s eval/py/tests -v` | **12 PASS**(기존 10 + 신규 2), 0 FAIL |
| f | `.\gradlew.bat :gateway-eval:test --console=plain`(필터 없음, PREREG 갱신 이후) | exit 0, BUILD SUCCESSFUL in 2m 20s, **117건 = 117 PASS / 0 FAIL / 0 SKIP / 0 ABORTED**, 27 클래스(기존 107 회귀 + 신규 10) |
| f' | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="test-evidence verify --tasks :gateway-eval:test"`(f 직후 별도) | exit 0 `TEST_EVIDENCE_VERIFIED` |

- f 정제 요약: `runId` `92f1eb1a-3d3e-44c1-8d9a-6823122e9b9f`, `recordedAt` `2026-10-29T05:33:48.494314400Z`, `executedTestCount`/passed/failed/skipped/aborted = 117/117/0/0/0, `specSha` `14d6cc2e…18ef8`(`FILE_HASHED`·`LOCAL_FILE`), `inputFingerprint` `509d661fa765f338b04dc00f08f070a89cad428f3cc02372c238d9e3c5b8596f`, `otrSha256` `04e94ddf…57bd`, `contentSha256` `c9462fed…7b3b`, `gitSha`·`runtimeClasspathSha256` = null(미커밋 — 가짜 값 0).
- 정리: Python 시험이 만든 `eval/py/tests/__pycache__`(pyc 2개)와 레포 밖 사용자 Temp 의 이 세션 재현용 임시 디렉터리만 삭제. 기존 venv 유지, pip 설치 0.

#### 변경 파일(이번 후속)

- main: `gateway-eval/…/eval/f0/OllamaChatClient.java`, `…/eval/f0/Preflight.java`, `…/eval/f3/EmbeddingProfile.java`
- test: 신규 `…/eval/f0/OllamaChatClientResponseTest.java`, 수정 `…/eval/f0/OllamaStub.java`, `…/eval/f3/EmbeddingProfileTest.java`
- `eval/py/count_tokens.py`, `eval/py/tests/test_count_tokens.py`, `eval/f0/PREREG.md`(해시 2줄), 이 절

#### 여전히 미해소(이번 보완으로 바뀌지 않음)

- §11.8 의 결정 사항은 그대로 열려 있습니다: G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED(시각 형식·`q` 상한 — AC-37-OUT-MAX·ESCAPE NOT_RUN, `f0 cases` exit 2 유지), usage-probe 렌더러/template 근거 미등록(`TEMPLATE_RENDERER_UNVERIFIED`), tokenizer HF 원본 pin 없음(다운로드 0), C1/C2 사전등록 commit 없음(`PREREG_NOT_COMMITTED`). 추출 정책·시각 형식은 이번에 바꾸지 않았습니다.
- 권장(결정은 root/SPEC 소유자): ① 본문 총 기한은 PREREG `requestTimeout`(120 s)를 그대로 쓰도록 했으므로, 측정 지연 정의("timeout 은 timeout 시각까지 경과")와 같은 값입니다 — 별도 본문 기한이 필요하면 PREREG v2 로 등록해야 합니다. ② usage `128.0` 같은 소수점 정수 표기를 거부하도록 했습니다. 실제 Ollama 가 정수 리터럴만 내는지는 P03 실측 전 확인 대상입니다(거부 시 exit 5 로 드러나며 측정값으로 섞이지 않음).
- 실제 Ollama·Jetson·SSH·k8s·모델 로드·HF 다운로드·설정 변경 0. 실측·AC 판정 0(AC-37·AC-38 NOT_RUN 유지).

### 11.13 root 최종 대조 및 진행 상태 (2026-10-29)

- 이 절은 root 가 독립적으로 실제 수행한 최종 대조 결과를 옮겨 적은 것입니다. 이 문서 갱신 세션(claude-opus-5-5, 문서 2개 편집만)에서는 시험·Gradle·Python 을 실행하지 않았습니다. §11.1~§11.12(§11.12 표 a 행의 verify 누락 이력 포함)는 그대로 보존합니다.
- Java: 정제 OTR 요약과 XML 모두 117/117 PASS, failed/errors/skipped/aborted 0, 27 클래스, `runId` `92f1eb1a-3d3e-44c1-8d9a-6823122e9b9f`, `inputFingerprint` `509d661fa765f338b04dc00f08f070a89cad428f3cc02372c238d9e3c5b8596f`, `contentSha256` `c9462fed3208f155046cc6035a1f58f93463c6dee5ef5c5c410a12f9687f7b3b`. `records=[]` — unit/stub 시험이며 실제 AC 합격이 아닙니다.
- root 별도 실행 `.\gradlew.bat :gateway-eval:run --console=plain -q --args="test-evidence verify --tasks :gateway-eval:test"` → exit 0 `TEST_EVIDENCE_VERIFIED`.
- root Python `-I -m unittest discover -s eval/py/tests -v` → 12 PASS.
- root 수정 코드 직접 대조: JSON 비객체 행 검증, 수치 integer·nonnegative·overflow 검사, 단일 본문 총 기한 + 8 MiB 상한 + 취소/interrupt 처리, dimension 임의 상한 제거 확인.
- SPEC SHA256 `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` 불변. Plan 진행 표시 수정 전 SHA256 `b3d9e53be4263448987f4bc974d9e0402ea601f73bcd0596f72c299178d26461`.
- 기존 16개 레포 status 가 P02 착수 전과 같음(common `?? package.json`, exec SPEC 미커밋 수정 = 기존 상태, 그 밖 clean). Plan 은 기존 ignore 상태이므로 commit·force add 없음.
- root 검토 확인 사항(이번 작업에서 정책 확정·변경 0): eqp 본문 추출은 F0 비교 표본의 추출 기준이며 화면 Context EQP 는 Gateway 적용 예정 — Gateway 리소스 우선순위 정책은 확정·변경하지 않음. run-binding 을 명령별로 두는 것은 단일 덮어쓰기 금지에 맞는 기술적 배치. 미확정 시각 형식·`q` 정책은 임의로 정하지 않음.
- 진행 상태: **P02 하네스 구현·로컬 검증 완료(`P02_IMPLEMENTED_LOCAL_VERIFIED`) / phase 최종 검증은 완료 증거 대기(`P02_FINAL_EVIDENCE_PENDING`)** — P02 전체 완료가 아닙니다. 미충족 증거: PREREG `registeredCommit=null`·`UNCOMMITTED`(사전등록 commit SHA 없음). P03 측정 전 조건으로 남은 것: G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED(AC-37-OUT-MAX·ESCAPE 차단), 렌더러/template 근거 미등록, HF tokenizer pin 없음. HF 다운로드·commit 은 하지 않았습니다.
- 실제 모델 측정·운영 변경·P03 착수 0. 모든 AC/ISO NOT_RUN 유지. Plan 갱신 범위: `Plan 상태` 행, `실행·증거 상태` 행, P02 체크 줄과 그 아래 `P02 진행 기록` 1줄.

### 11.14 P02 입력 지문 결속 보완 (2026-10-29, 별도 독립 Claude Code 세션 — claude-opus-5-5)

- §11.1~§11.13 은 이력으로 보존합니다(117건 수치 포함). 현재 수치는 이 절이 정본입니다. 이 절의 실행은 모두 이 구현 세션이 한 것이며 **root 독립 검증이 아닙니다** — root 재대조 대기. P03 은 시작하지 않았습니다.
- 착수 시 SPEC `14d6cc2e…18ef8` 실제 바이트 일치(종료 시 동일). 루트 `build.gradle.kts`·TC 레포 코드·`.claude` 변경 0, git 명령 0.

#### 재현 → 원인 → 수정

- 결함(root 최종 대조 지적): `gateway-eval/build.gradle.kts` 의 `inputs.files(eval/**, arg-limits.md)` 는 Gradle 재실행 조건에만 들어가고, 루트 `aigwInputComponents(test)`(test.classpath·SPEC·기준선·catalog·tags·filters·`aigw.*` 시스템 속성)에는 없어 골든셋·프롬프트·PREREG·arg-limits 문서 바이트가 run-binding/verify fingerprint 에 결속되지 않았습니다(Plan §4.2 N17 "선언 입력 = run-binding 입력" 불충족). 같은 원인으로 `ArgLimitsSchemaTest` 가 읽는 워크스페이스 CODE/SCHEMA 근거 파일(`arg-limits-v1.json` 의 `ref` 경로:줄 — 현재 `nori-tc-exec/docs/task/…architecture.md`, `nori-tc-common/…/TcEqpEntity.java`, `nori-tc-exec/db/migration/V1__baseline.sql`, `nori-tc-ui-backend/…/EqpCreateRequest.java`)과 `SpecGuardTest`·`OllamaChatClientContractTest`·`HarnessDigest` 가 스캔하는 `gateway-eval/src/main/java/**` 원문(주석 등 class 바이트에 안 남는 변경)도 어느 쪽에도 선언되지 않았습니다.
- 수정 전 재현: 회귀 시험을 먼저 작성하고, 당시 생산 로직(`inputs.files` 2줄)을 그대로 옮긴 공통 스크립트로 TestKit 실행 → `everyReadInputByteChange…` 가 **프롬프트 1바이트 변경 뒤 verify 가 성공**(`UnexpectedBuildSuccess` — STALE 거부여야 함)으로 FAIL, `productionModuleApplies…` FAIL(2/2 FAIL, 재현 목적).
- 수정(공통 구현 1개): 신규 `gateway-eval/f0-test-inputs.gradle.kts` 가 실제 test 가 읽는 파일 집합 — `eval/**`(기존 제외 `f0/results/**`·`f0/chat-template/**`·`**/__pycache__/**` 그대로), `docs/gates/f0/arg-limits.md`, `gateway-eval/src/main/java/**`, `eval/f0/arg-limits-v1.json` 의 kind CODE·SCHEMA `ref` 파일(워크스페이스 = 루트의 부모 기준) — 을 루트 기준 정렬 상대 경로(`/`, 워크스페이스 파일은 `../…`) + 내용 SHA-256(부재 = `MISSING`) 목록으로 만들고 그 SHA-256 하나(`aigwF0InputsSha256`)를 `inputs.property("aigwF0InputsSha256", …)`(Gradle UP-TO-DATE 판정)와 `systemProperty("aigw.f0InputsSha256", …)`(루트 fingerprint ⑥ 이 값 그대로 해시 → run-binding·finalizer `--current-fingerprint`·verify 가 같은 값) 둘에 같은 문자열로 둡니다. 값은 설정 단계에서 현재 바이트로 매 Gradle 실행마다 다시 계산되며(구성 캐시 명령 없음), Provider 문자열·절대 경로 문자열은 쓰지 않습니다. `gateway-eval/build.gradle.kts` 는 기존 `inputs.files` 블록을 `apply(from = "f0-test-inputs.gradle.kts")` 로 바꿨습니다. 근거 파일이 없는 환경은 지문에 `MISSING` 으로 고정되고 `ArgLimitsSchemaTest` 는 기존대로 실패합니다(시험 변경 0).
- 회귀 시험 신규 `F0InputBindingFunctionalTest`(2건, 루트 `TestKitSupport` 재사용): TestKit fixture(`@TempDir/repo` + 워크스페이스 근거 `@TempDir/code-evidence`)에 **실제 생산 스크립트 파일을 복사해 적용**. 기준 test → 직후 verify 성공 후, 6가지 변경(eval 프롬프트 1바이트, arg-limits 문서 1바이트, eval 새 파일 추가, eval 파일 삭제, 워크스페이스 CODE 근거 1바이트, 근거 파일 삭제 → `MISSING`)마다 ① verify `STALE_TEST_EVIDENCE` 거부 ② `:sub:test --build-cache` 결과 `SUCCESS`(UP-TO-DATE·FROM-CACHE 아님) ③ `runId`·`inputFingerprint` 모두 이전 값과 다름 ④ 직후 verify 성공. 제외 경로(`eval/f0/results/**`) 변경은 UP-TO-DATE·같은 `runId`·verify 성공. run-binding 의 `aigw.f0InputsSha256` 은 64자리 hex, run-binding 에 fixture 절대 경로 문자열 0. 별도 시험은 생산 `gateway-eval/build.gradle.kts` 가 같은 스크립트를 적용하는지 확인. 실제 제품 golden 파일은 수정하지 않았습니다.

#### 실행 명령과 결과(cwd 레포 루트, `C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile` + 프로세스 env `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값)

| 순서 | 명령 | 결과 |
|---|---|---|
| a | `.\gradlew.bat :gateway-eval:test --tests "com.nori.ai.gateway.eval.F0InputBindingFunctionalTest"`(수정 전 재현, 첫 시도) | 2/2 FAIL — 단 첫 시도는 형식 단언(`aigw.f0InputsSha256` 부재)이 먼저 걸려 핵심 결함이 가려졌으므로 단언 순서만 뒤로 옮겨 재실행(a'). 이 첫 시도 직후 verify 는 실행하지 않음 — 절차 누락으로 기록 |
| a' | 같은 명령(수정 전 재현) | 2/2 FAIL: 프롬프트 1바이트 변경 뒤 verify 성공(`UnexpectedBuildSuccess`, 결함 재현)·생산 build 미적용 |
| a'' | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="test-evidence verify --tasks :gateway-eval:test"`(a' 직후 별도) | exit 1 `STALE_TEST_EVIDENCE`(필터·실패 실행 — 예상 거부) |
| b | a 와 같은 필터 명령(수정 후) | 2/2 PASS, BUILD SUCCESSFUL |
| b' | verify(b 직후 별도) | exit 1 `STALE_TEST_EVIDENCE`(필터 실행 — 예상 거부) |
| c | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="f0 prereg-check --prereg eval/f0/PREREG.md"` | `localMismatches=[]`, `currentHarnessDigest` `c35e8194…e38f` 불변 → java exit 2 `PREREG_NOT_COMMITTED`(미커밋 예상 차단, PASS 아님). 변경은 모듈 build 스크립트·시험뿐(harnessDigest 집합 밖)이라 PREREG 해시 재발급 0 |
| d | `.\gradlew.bat :gateway-eval:test --console=plain`(필터 없음) | exit 0, BUILD SUCCESSFUL in 3m 2s, **119건 = 119 PASS / 0 FAIL / 0 SKIP / 0 ABORTED**, 28 클래스(기존 117 회귀 + 신규 2), XML 합계 119 |
| d' | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="test-evidence verify --tasks :gateway-eval:test"`(d 직후 별도) | exit 0 `TEST_EVIDENCE_VERIFIED` |

- d 정제 요약: `runId` `c47dd65c-8d23-4958-a674-4a2d65555bdd`, `recordedAt` `2026-10-29T10:42:37.573165700Z`, executed/passed/failed/skipped/aborted = 119/119/0/0/0, `specSha` `14d6cc2e…18ef8`, `inputFingerprint` `5ffb80c6f7243376ddd2b562f4fc1936e234e26130744a52e2f65956799df7c0`, `aigw.f0InputsSha256` `704b4af0ba97650179e60056b6f869af2f989b9459289583e7c7a15e92f5ff43`, `otrSha256` `78daeac9…e6b3`, `contentSha256` `87188096b4dfe3ebccb85cb514a71013df2e39a201c7024311413c1e0a97fc92`, `records=[]`(unit/stub — AC 합격 아님), `gitSha`·`runtimeClasspathSha256` = null.
- Python 은 이번에 바꾸지 않아 다시 실행하지 않았습니다(§11.12 의 12 PASS 가 마지막 실행). TestKit 임시 디렉터리는 JUnit `@TempDir` 가 정리. localhost 외 호출·실제 Ollama·Jetson·SSH·k8s·모델 로드·HF 다운로드 0.

#### 변경 파일(이번 보완)

- 신규 `gateway-eval/f0-test-inputs.gradle.kts`, 수정 `gateway-eval/build.gradle.kts`(`inputs.files` 2줄 → 스크립트 적용 1줄)
- 신규 시험 `gateway-eval/src/test/java/com/nori/ai/gateway/eval/F0InputBindingFunctionalTest.java`(`TestKitSupport` 변경 0)
- 이 절, Plan `Plan 상태`·`실행·증거 상태` 행·`P02 진행 기록` 줄(prefix `- P02 진행 기록:` 로 변경, 117 이력 보존)

#### 여전히 미해소(변경 없음)

- G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED, usage-probe 렌더러/template 근거 미등록, HF tokenizer pin 없음, PREREG 사전등록 commit 없음(`registeredCommit=null`). 상태는 `P02_IMPLEMENTED_LOCAL_VERIFIED` / `P02_FINAL_EVIDENCE_PENDING` 그대로이며 P02 전체 완료가 아닙니다.

## 12. P03 준비 현황 — tokenizer pin·template 검토·렌더러 등록 (2026-10-30, 별도 독립 Claude Code 세션 — claude-opus-5-5)

- §1~§11(P01·P02 이력, §11.14 의 119건 수치 포함)은 그대로 보존합니다. 이 절은 사용자 명시 승인(공식 HF tokenizer JSON 다운로드·SHA 고정, 실제 template 조회·검토·등록)에 따른 **P03 준비**만 기록하며, P03 측정(추론)·Git 초기화·C1/C2 commit 은 하지 않았습니다(다음 세션 범위). 이 절의 확인은 모두 이 세션이 한 것이며 root 독립 검증이 아닙니다.
- SPEC `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` 착수·종료 시 실제 바이트 일치(변경 0). Plan·SPEC 계약·렌더러 코드(`ChatRenderers.java` 등 Java/Python) 변경 0.
- Jetson 접근: 읽기 조회만(`/api/show`·`/api/version`, 프로세스 인자 플래그 유무, ollama 로그 `template selection` 의 선택값, unit 환경 변수 유무). 출력은 키 이름·판별 값 요약뿐이며 응답 원문·키·접속 정보 저장 0. 저장한 응답 내용은 `/api/show` 의 `template` 필드 하나. 추론·모델 로드/언로드·서비스 재시작·설정 변경 0.

### 12.1 현재 판정

| 항목 | 상태 |
|---|---|
| HF tokenizer 원본 pin | **완료** — `Qwen/Qwen3-4B-Instruct-2507` revision `cdbee75f17c01a7cc42f958dc650907174af0554` `tokenizer.json`, 실제 바이트 11422654 / SHA-256 `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`(메타·LFS SHA 일치). 근거 `docs/gates/f0/tokenizer-provenance.md` |
| template 사전검토 파일 | `docs/gates/f0/preparation-template.template` 4040 B, UTF-8 LF, SHA-256 `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541`(직전 독립 조회 값과 일치). 공식 캡처 위치 `eval/f0/chat-template/` 는 만들지 않음(P03 6단계 명령용) |
| 실제 렌더 경로 | Ollama `v0.31.1`(commit `710292ff…8d4a`) native 모드 → llama-server(llama.cpp `b9840`, commit `8c146a83…8dad`) Jinja 로 GGUF `tokenizer.chat_template` 렌더. Jetson 실측: 생성 llama-server 에 `--no-jinja`·`--chat-template` 없음, 로그 `selected=gguf_chat_template` |
| U1/U2 렌더 일치 | system+user·tools 없음에서 Jinja 출력 = `ChatRenderers.chatMl` 바이트 동일(기본 system 주입 없음, 생성 접두부 `<|im_start|>assistant\n`, `enable_thinking` 미참조, whitespace 제어 확인). 근거 `docs/gates/f0/template-review.md`(공식 source URL·줄) |
| 렌더러 등록 | **등록** — `eval/f0/chat-renderers-v1.json` 에 `templateSha256=40c21f34…b541`, `renderer=CHATML_NO_SYSTEM_DEFAULT`, `reviewRef=docs/gates/f0/template-review.md`(파일 SHA-256 `ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e`) |
| G-ARG-LIMITS `from`·`to`·`q` | **UNRESOLVED 유지**(변경 0) → AC-37-OUT-MAX·ESCAPE NOT_RUN |
| PREREG 입력 SHA·`harnessDigest` | **이번에 갱신하지 않음**(지시 범위). `chat-renderers-v1.json` 바이트 변경(PREREG 등록값 `90414c54…18a5` 와 다름)과 `eval/f0/tokenizer/**` 추가로 `harnessDigest` 도 달라졌으므로, 현재 `f0 prereg-check` 는 `localMismatches` 에 이 둘을 낼 것으로 예상(이번 세션 미실행). C1 전 갱신 필요 |
| PREREG commit(C1/C2) | 없음(`registeredCommit=null`, `UNCOMMITTED`) — 다음 세션 |
| 시험·Gradle·Python·추론 | 실행 0 |

### 12.2 남은 장애·주의

1. G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED(SPEC 소유자 결정 필요) — MAX·ESCAPE NOT_RUN, F0 exit 차단은 그대로.
2. PREREG 입력 SHA(`chat-renderers-v1.json`)·`harnessDigest` 재발급 → C1(등록 대상)·C2(PREREG `registeredCommit`) 두 commit → `f0 prereg-check` `PREREG_VERIFIED` 가 측정 전 조건. `eval/**` 변경으로 `aigwF0InputsSha256`·시험 입력 지문도 바뀌므로 다음 세션에서 시험·verify 재실행 필요.
3. 렌더러 등록 범위는 system 1 + user 1(tools·assistant 이력 없음)으로 한정 — 현재 사용처(usage-probe U1·U2)와 일치. template·모델 digest 가 바뀌면 재검토.
4. Jetson llama-server 바이너리 빌드 번호는 로그에서 직접 관측하지 못함 — 근거는 Ollama 공식 tag 의 `LLAMA_CPP_VERSION=b9840`.
5. tokenizer 원본은 HF BPE 이며 GGUF 내장 vocab 계수와의 동일성은 P03 4ⓐ·4ⓑ 측정으로 확인.

### 12.3 변경 파일(이번 준비)

- 신규 `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json`(SHA `aeb13307…dae4`), `eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256`(SHA `445bc540a9b18b31efa14b81b0a65b56ed0a913c3536ba836b0780a2fd3e2113`)
- 신규 `docs/gates/f0/tokenizer-provenance.md`, `docs/gates/f0/preparation-template.template`, `docs/gates/f0/template-review.md`
- 수정 `eval/f0/chat-renderers-v1.json`(빈 등록 → 1항목, notice 문구), 이 절
- 레포 밖: 사용자 Temp 의 작업 전용 디렉터리에 tokenizer 임시 다운로드(검증 후 삭제)·Ollama `v0.31.1`·llama.cpp `b9840` 얕은 clone(읽기 전용 검토 후 삭제). 그 밖 변경 0.
