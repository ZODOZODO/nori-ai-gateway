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

### 12.4 사전등록 C1/C2·시험 결함 수정·검증 (2026-10-30, 별도 독립 Claude Code 세션 — claude-opus-5-5)

- §12.1~§12.3 은 이력으로 보존합니다(§12.1 의 "PREREG 입력 SHA·`harnessDigest` 이번에 갱신하지 않음", "PREREG commit 없음" 은 그 세션 종료 시점 기록). 현재 상태는 이 절이 정본입니다. 이 절의 실행은 모두 이 구현 세션이 한 것이며 **root 독립 확인 대기**입니다.
- 사용자 승인 범위: Gateway 레포 git 초기화, 로컬 C1/C2 두 사전등록 commit(외부 push 0). 이번 세션은 사전등록·검증까지이며 **실측 0**(SSH·Jetson·HTTP·추론 호출 0, localhost stub 시험만).
- SPEC `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8` 착수·종료 시 실제 바이트 일치(변경 0). Plan 편집 전 SHA-256 `90bc156e53c757b9e562ae0c2aef5892c3deb38fe79b2a73332c6db565152328`. 적용 지침: `C:/workspace_nori`·`nori-ai-gateway`·`nori-tc-exec` 에 CLAUDE.md/AGENTS.md 없음(§11 과 동일) — 사용자 지정 문서 경로 유지.
- production 코드·빌드 구조·`.gitignore`·`.gitattributes`·SPEC/Plan 계약·수치·R·분모·판정 규칙 변경 0. G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED 그대로(이후 측정 가능 kind 는 NORMAL 뿐).

#### 시험 결함 → 수정

- 결함(root 확인): `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/PreregCommitGuardTest.java:73~94`(수정 전)의 두 시험이 실제 레포 ROOT 의 PREREG 가 영원히 `registeredCommit=null`·`UNCOMMITTED` 라고 단언했습니다 — `repositoryRunnerRefusesWhilePreregIsUncommitted`(실제 ROOT 로 `EvalCli f0 run` → `PREREG_NOT_COMMITTED` 기대)와 `repositoryPreregStatesUncommittedAndMatchesCurrentBytes`(`registeredCommit` isNull·`UNCOMMITTED` 단언). C2 정상 등록 후에는 반드시 실패하는 시험 상태 의존 결함입니다(특히 전자는 등록 후 실제 ROOT 에서 stub 호출·결과 디렉터리 생성까지 진행할 수 있는 구조). 수정 전 시험을 C2 이후 실제로 실행해 실패를 재현하지는 않았습니다 — 근거는 단언 내용 자체(정적)입니다.
- 수정(같은 시험 파일 안, production·빌드·`F0TestRoot` 변경 0, 실제 ROOT 수정·조건부 skip 0):
  1. `runnerEntryRefusesUnregisteredPreregWithoutAnyCall`(전자 대체): `F0TestRoot` 임시 루트(실제 공개 입력 복사)에 `prereg(null)` 로 **명시적 미등록 fixture** 를 쓰고, 그 fixture 가 `registeredCommit=null`·`localMismatches=[]` 임을 확인한 뒤 실제 `EvalCli` 진입점(`new EvalCli(env, out, err, 임시 루트).execute("f0","run",…)`, 실제 `GitReader.process()`)으로 실행 → exit 2·stderr 정확히 `PREREG_NOT_COMMITTED`·stub 요청 0·임시 루트 `eval/f0/results` 미생성·실제 ROOT `eval/f0/results/P02GUARDTEST` 미생성.
  2. `repositoryPreregRegistrationStateMatchesLifecycleAndCurrentBytes`(후자 대체): 기존 단언(현재 레포 PREREG 입력 SHA·`harnessDigest` = 현재 바이트(`localMismatches` 빈 목록), R=5, 필수 입력 + `count_tokens.py`·`requirements.txt` 포함, PREREG 가 자기 SHA 미포함)을 그대로 두고, 등록 수명주기 정합을 추가: `registeredCommit=null` 이면 `registrationStatus=UNCOMMITTED` 이고 실제 `Prereg.verify`(실제 git)가 `PREREG_NOT_COMMITTED`; 값이 있으면 `REGISTERED` 이고 실제 `Prereg.verify(ROOT, PREREG, GitReader.process())` 가 통과(C1≠C2·조상·C1 트리 입력 blob·C1 트리 `harnessDigest`·작업 트리 clean)하며 반환 `preregCommit` 이 40자 hex 이고 C1 과 다름. 두 분기 모두 단언이며 skip 경로가 없습니다. C1 전 실행은 UNCOMMITTED 분기, C2 후 실행은 REGISTERED 분기를 실제로 통과했습니다.
- 그 밖 4건(fake git fixture 시험·C1=C2 거부·harnessDigest 제외 규칙·실제 `GitReader` 쓰기 거부) 변경 0. 단언 삭제 0.

#### PREREG 갱신(C1 전)

- `f0 prereg-check` 첫 실행: `localMismatches=["eval/f0/chat-renderers-v1.json","harnessDigest"]`(§12.1 예상과 일치). 실제 현재 바이트로 2값만 갱신: `chat-renderers-v1.json` `90414c54…18a5` → `ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e`, `harnessDigest` `c35e8194…e38f` → `3b3c0a407271d3eac2850a479da584453188d8be2cfbf7380bfa4ec3e8a4bac4`. 그 밖 10개 입력 SHA·`parameters`(R=5·max_tokens 128/160·temperature 0·timeout 120 s·경로 예산·`judgeRulesVersion`) 불변, `parameterManifestSha256` `6afc2755520fdb4fd753bde33f6ad5d5b5d591247c586edd61fd4ff1b73145c6`.
- 문구: §0 계산 시점(2026-10-30)·변경 사유, §1 절차 1(tokenizer)에 완료 표기, §6 tokenizer pin·렌더러 등록을 "준비 완료/검토·등록 완료" 사실로 갱신(불일치 시 기존 fail-closed 코드는 그대로 명시). G-ARG-LIMITS 줄·축별 규칙·분모·판정 규칙 변경 0. 이 시점 상태는 `UNCOMMITTED`·`registeredCommit=null` 유지 → 재실행 `localMismatches=[]` 후 예상 차단 `PREREG_NOT_COMMITTED`.

#### Git 초기화·C1/C2(로컬만)

- `nori-ai-gateway` 에만 `git init`(git 2.53.0.windows.1). 전역 `user.name`·`user.email` 존재만 확인(값 출력 0). 시스템 `core.autocrlf=true` 이나 레포 `.gitattributes`(`* text=auto eol=lf`, `*.bat eol=crlf`)가 우선. TC 레포 git 쓰기 0(`nori-tc-exec`·`nori-tc-common`·`nori-tc-ui-backend` status 는 착수 전과 같음: exec SPEC 미커밋 수정·common `?? package.json` 기존 상태).
- C1 파일 집합: `.gradle`·`build` 를 뺀 작업 트리 일반 파일 120개를 하나씩 목록으로 만들어 `git add --pathspec-from-file` 로 명시 stage(`git add .` 0) — 루트 빌드 입력(`build.gradle.kts`·`settings.gradle.kts`·`gradlew`·`gradlew.bat`·`gradle/**`·`.gitignore`·`.gitattributes`·`README.md`), `gateway-eval/**`(main·test·resources·`f0-test-inputs.gradle.kts`), `eval/golden/**`, `eval/f0/**`(PREREG `registeredCommit=null` 상태 포함, tokenizer 원본·pin, 렌더러 등록), `eval/py/**`(시험·fixture), `docs/**`(gates 검토 문서·tokenizer provenance·template 검토·사전검토 template, 이 보고서의 §12.3 까지 판), `evidence/README.md`. 생성물(`.gradle`·`build`·`__pycache__` 파일)·운영 응답·접속 정보·개인 skill·secret 0(패턴 검사 hit 는 시험의 비 loopback 거부 주소·sentinel 문자열·tiny tokenizer `[UNK]` 뿐). `eval/py/tests/__pycache__` 는 2026-10-29 부터 있던 **빈** 디렉터리라 git 대상이 아니며 그대로 둠(이번 Python 실행은 `-B` 로 pyc 0).
- index 대조: 120개 중 119개 index blob SHA-256 = 작업 트리 바이트 SHA-256. 유일한 차이 `gradlew.bat` 는 작업 트리 CRLF → blob LF(`.gitattributes` `*.bat eol=crlf` 정규화, 등록 입력·harness 아님). 등록 입력·harness 파일은 전부 CR 0·blob 동일(`eval/f0/tokenizer/…tokenizer.json` `i/lf w/lf`).
- **C1 = `ca3b266f8eb8c529ed6bf358712d6060a77b21c5`**(120 files, root commit). **C2 = `bd61f36214d2d665ef270e7f926cf2eb65621ea2`**(부모 C1, `eval/f0/PREREG.md` 1 파일만: `registrationStatus=REGISTERED`, `registeredCommit=ca3b266f…21c5`, §0 등록 상태 문구 — C2 SHA 는 문서에 쓰지 않음). C2 의 PREREG blob = 작업 트리 바이트. 외부 remote·push 0.

#### 실행 명령과 결과(cwd 레포 루트, Gradle 은 `C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -File <레포 밖 임시 스크립트>` — 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값, Java 21(Temurin 21.0.10))

| 순서 | 명령 | 결과 |
|---|---|---|
| a | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="f0 prereg-check --prereg eval/f0/PREREG.md"`(갱신 전, 첫 시도) | Gradle 빌드는 끝났으나(daemon 로그: `:gateway-eval:run` java exit 2) bash 파이프를 Gradle daemon 이 상속해 셸이 반환하지 않아 출력 유실 — 파이프만 종료하고 출력 파일 방식으로 a' 재실행. 판정에 쓰지 않음 |
| a' | 같은 명령(갱신 전) | `localMismatches=["eval/f0/chat-renderers-v1.json","harnessDigest"]`, `currentHarnessDigest` `3b3c0a40…bac4` → java exit 2 `PREREG_NOT_COMMITTED`(Gradle exit 1, 미등록 예상 차단 — PASS 아님) |
| b | 같은 명령(PREREG 2값 갱신 후, C1 전) | `localMismatches=[]` → `PREREG_NOT_COMMITTED`(Gradle exit 1, 예상 차단) |
| c | `.\gradlew.bat :gateway-eval:test --console=plain`(필터 없음, git init·C1 stage 후·commit 전) | exit 0, BUILD SUCCESSFUL in 3m 4s, **119건 = 119 PASS / 0 FAIL / 0 SKIP / 0 ABORTED**, 28 클래스 |
| c' | `.\gradlew.bat :gateway-eval:run --console=plain -q --args="test-evidence verify --tasks :gateway-eval:test"`(c 직후 별도) | exit 0 `TEST_EVIDENCE_VERIFIED` |
| d | `C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe -I -B -m unittest discover -s eval/py/tests -v` | exit 0, **12 PASS**(Ran 12, OK) |
| e | `git diff --quiet`(작업 트리 = index) 확인 후 C1 commit | `ca3b266f…21c5` |
| f | PREREG 등록 표기 후 `git add -- eval/f0/PREREG.md` → C2 commit | `bd61f362…1ea2` |
| g | `f0 prereg-check`(C2 후) | `localMismatches=[]`, `currentHarnessDigest` `3b3c0a40…bac4`, **`PREREG_VERIFIED`**, Gradle exit 0 |
| h | `.\gradlew.bat :gateway-eval:test --console=plain`(필터 없음, C2 후) | exit 0, BUILD SUCCESSFUL in 2m 58s, **119건 = 119 PASS / 0 FAIL / 0 SKIP / 0 ABORTED**, 28 클래스, `PreregCommitGuardTest` XML tests=6 skipped=0 failures=0 errors=0 |
| h' | `test-evidence verify --tasks :gateway-eval:test`(h 직후 별도) | exit 0 `TEST_EVIDENCE_VERIFIED` |

- c 정제 요약(C1 전): `runId` `d01daff1-f8cf-412a-81c7-ccc528b04dc0`, `recordedAt` `2026-10-29T23:31:41.648330200Z`, `inputFingerprint` `44e9953998727dd4338ad33dbf362b7490c2a760d6bf452bf313d9f5a1ef26e0`, `aigw.f0InputsSha256` `879c5a92aea5edf265c79d9451bbbe2a06b0412548009edf1eba14de9b33437e`, `otrSha256` `ae70ebbe011edbeb767785abb2408fa021ee97a60073e03b889b2d9bb1de3849`, `contentSha256` `c883253576466f040f63fd2965c623a04222c9ab8659abc19423f3107d617253`.
- **h 정제 요약(최신, C2 후)**: `runId` `c10c8aab-90fe-4dcc-b3b5-1cf9fca3c7d0`, `recordedAt` `2026-10-29T23:35:56.288796600Z`, executed/passed/failed/skipped/aborted = 119/119/0/0/0, `specSha` `14d6cc2e…18ef8`(`FILE_HASHED`·`LOCAL_FILE`), **`inputFingerprint` `af2d4ffd803bab3bc9c4973166e8133b869d0240549aae3a1245d3691d9b0cf2`**, `aigw.f0InputsSha256` `062546ca166fe955b348bbec31d1a68c74aef79f8ab34d9af4a09948db56f45d`, `otrSha256` `183bb60a2d59c12a500020f8c4637ee8f74bded15141a02e8fbd79e2c2816a2c`, `contentSha256` `98c52678da1ac52831192faeadaa23ab0791ab114aa2f1408e7cdb57069feef2`, `records=[]`(unit/stub — AC 합격 아님).
- 관찰(변경하지 않음): 정제 요약 `buildIdentity.gitSha` 는 여전히 `null` 입니다. `OtrLeafExtractor` 가 env `AIGW_GIT_SHA` 만 읽고(이번 실행 미설정) git 을 직접 읽지 않는 기존 설계이며, P01~P03 레코드는 JUNIT_OTR component 로 쓰이지 않으므로(§4.4) 이번 범위에서 손대지 않았습니다.
- Python 은 C2(PREREG 만 변경) 뒤 다시 실행하지 않았습니다(d 가 마지막 실행). 모든 HTTP 시험은 `127.0.0.1` stub 이며 실측이 아닙니다.

#### 등록 검사 상태·남은 게이트

- 현재 `f0 prereg-check` = `PREREG_VERIFIED`(C1 `ca3b266f…21c5`, C2 `bd61f362…1ea2`, `harnessDigest` `3b3c0a40…bac4`). 이후 등록 입력·harness(`eval/f0/**`·골든셋·fixture·`gateway-eval/.../eval/f0/**`·`eval/py/count_tokens.py`·`requirements.txt`)나 PREREG 를 1바이트라도 바꾸면 측정 명령은 `PREREG_INPUT_CHANGED`/`PREREG_WORKTREE_DIRTY` 로 거부됩니다(수정 필요 시 v2 절차). C2 이후 이 파일들 수정 0.
- 작업 종료 시 git status: 등록 입력·PREREG·harness 경로 clean. 이 보고서(`docs/…dev-report.md`, C1 에 §12.3 판으로 포함)는 이 절 추가로 **작업 트리 수정 상태(미커밋)** 입니다 — 지시상 C2 는 PREREG 만이고 추가 commit 은 하지 않았으며, `docs/**` 는 등록 검사 경로 밖입니다.
- 실측 0: P03 측정(preflight·run A/B·경계 계수·boundary-gen·chat-template·usage-probe·token-golden·report) 미실행 — 다음 세션. 측정 시작 시각은 C2 commit 시각 이후여야 합니다(러너 검사).
- 남은 게이트: ① root 독립 확인(C1/C2·시험·verify 재대조) ② G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED → AC-37-OUT-MAX·ESCAPE NOT_RUN(NORMAL 만 측정 가능) ③ P03 실측(G-F0-ACCESS — Jetson 터널·두 모델 상주·ctx 4096 확인) ④ SPEC §14.5 기록 반영(G-F0-TARGET)·재판정. AC-37·AC-38 은 전부 NOT_RUN 유지.

#### 변경 파일(이번 세션)

- 수정 `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/PreregCommitGuardTest.java`(시험 2건 교체), `eval/f0/PREREG.md`(C1 전: 입력 SHA 1·`harnessDigest`·§0/§1/§6 문구 / C2: 등록 상태·`registeredCommit`·§0 문구), 이 절
- 신규 `nori-ai-gateway/.git`(로컬 commit 2개)
- Plan(`nori-tc-exec/docs/task/ai-rag-gateway-architecture-plan.md`, 기존 ignore 파일): `Plan 상태`·`실행·증거 상태` 행, P02 체크 줄의 괄호 사유와 `P02 진행 기록` 줄만 사실 갱신(P02 검증·P03 체크 변경 0, 계약 변경 0)
- 레포 밖: 사용자 Temp 의 Gradle 실행용 PowerShell 스크립트·로그(레포·입력에 영향 없음). 그 밖 변경 0.

### 12.5 P03 F0 실측 실행 (2026-10-30, 별도 독립 Claude Code 세션 — claude-opus-5-5, root 독립 확인 대기)

- §12.1~§12.4 는 이력으로 보존합니다. 사용자 승인 범위 = Jetson 읽기 전용 조회 + 이미 상주한 모델 추론(서비스·설정 변경·모델 로드 제외). P02 완료 증거는 root 가 독립 확인했다는 사용자 전달을 따랐습니다(C1/C2 blob 11·하네스 33 재계산, `PREREG_VERIFIED`, `test-evidence verify` exit 0, Java 119/119·Python 12) — productAC 합격이 아닙니다.
- 실행 기록 정본(명령·시각·exit·파일 SHA): `docs/gates/f0/p03-run-20261029T234506Z.md`. 측정 판정 정본: `eval/f0/results/20261029T234506Z/report.md`(git 추적 제외 경로), `evidence/F0/**`, `evidence/F0/GATE-REPORT.md`.
- **부모 회차 `20261029T234506Z`**(모든 프로세스에 `AIGW_PARENT_RUN_ID` 동일). SPEC `14d6cc2e…18ef8` 착수·종료 일치, `harnessDigest` `3b3c0a40…bac4`, `runtimeManifestSha256` `b1e6a3af…6f6a`(run-binding 7개 동일), 사후 `prereg-check` `PREREG_VERIFIED`·`localMismatches=[]`.
- 등록 하네스·입력·PREREG·SPEC 바이트 변경 0, TC 코드·설정 변경 0, git commit·push 0. 수치·임계·R5·분모·판정 규칙·프롬프트 변경 0.

#### 명령 exit(전부 별도 실행)

| 0 env | 1 터널 | 2 preflight | 3B run | 3A run | 4ⓐ 공식 계수 | 4ⓑ GGUF | 5 boundary-gen | 6 template | 6 usage | 6 token-golden | 7 터널 종료 | 8 report | 8 gate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (판정 **FAIL**) |

- 측정 실행 실패(endpoint 오류·timeout·exit 4/5/6) 0. 아래 FAIL 은 모두 **표본 판정 FAIL** 입니다.
- 터널: 착수 전 로컬 11434 리스너·ssh 프로세스 0 → 숨김 ssh(PID 7196) 생성 → 종료 시 그 PID 만 `Stop-Process`, 리스너 0 확인. Jetson 모델 상태: 착수 preflight 와 종료 직전 읽기 전용 `/api/ps` 모두 qwen3:4b-instruct(`0edcdef3…8ba0`, ctx 4096)·bge-m3:latest(`79076464…6bab`, ctx 4096) 상주 — 변경 0. 미상주 모델 호출 0(러너는 매 호출 전 상주 재확인).

#### 결과

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 재시도 | timeout | p50/p95 ms | 출력 토큰 max |
|---|---|---|---|---|---|---|---|---|
| A | 18/22 | 12/22 | 19/22(UNS 오탐 1) | 100/110 | 10/110 | 0 | 3248/5800 | 78 |
| B | 18/22 | 9/22 | 20/22(UNS 오탐 0) | 110/110 | 0/110 | 0 | 2860/4764 | 70 |

- 잠정 후보 `KEEP_B`(A 의 UNSUPPORTED 19 < B 20). A 비유효 10 = G05·G18(GQ 양성) 각 5회 `STEPS_INVALID`(재시도 후에도 실패).
- 토큰 계수 NORMAL-01: 공식 HF 57 = GGUF 57(보정 57/53 일치), 종료 여유 71.
- boundary-gen NORMAL-01: 5회 모두 stop·스키마 유효·completion 63·재시도 0 이나 argsExact=false 5/5 → 0/5. 출력 원문 미저장 설계라 불일치 필드는 기록에 없음.
- G-USAGE: `equalOnCacheHit={usage:true, evalCount:true}`(U1 1031/1031, U2 1013/1013) — cache 적중 시 축소 보고 없음.
- token golden 13행(TG12 근접 상한 3952 토큰), template SHA `40c21f34…b541` = 사전검토본 바이트 동일.
- AC-38-F0(G08): B 5/5 INC·workflow 일치, A 5/5 UNS → FAIL.

#### gate F0 = FAIL (통과 아님)

- AC-37-CMP F0R PASS(`KEEP_B`)·SPR NOT_RUN / AC-37-OUT-NORMAL F0R FAIL / AC-37-OUT-MAX·ESCAPE NOT_RUN(G-ARG-LIMITS `from`·`to`·`q` UNRESOLVED) / AC-38-F0 FAIL. AC-37·AC-38 상위 FAIL. 그 밖 AC/ISO NOT_RUN.
- F0 exit 차단 사유: AC-38-F0 FAIL, AC-37-OUT-NORMAL FAIL, G-ARG-LIMITS UNRESOLVED(MAX·ESCAPE NOT_RUN), SPEC_RECORD 부재. **P03 완료 체크하지 않음, P04 미착수.**
- `spec-14.5-proposal.md` 는 `f0 report` 가 생성(`eval/f0/results/20261029T234506Z/`) — SPEC 반영·기준선 사슬·publish·protected 변수 변경 0. 9단계(재판정·spec-record-check) NOT_RUN.

#### 후속 사용자 결정 필요(권장안)

1. **AC-38-F0 FAIL(경로 A 만 G08 을 UNS 로 분류)** — Plan F0 exit 표의 정해진 경로는 "분류 프롬프트 v2 를 새로 사전등록하고 재측정(기존 결과 보존)" 입니다. 이번 실패 경로는 A(`plan-v1`)이고 B(`classify-v1`)는 5/5 정답이므로, 어느 프롬프트를 v2 로 할지(또는 AC-38 판정을 두 경로 모두에 요구하는 현재 규칙 유지 여부)는 SPEC/Plan 소유자 결정입니다. 권장: 규칙 유지, `plan-v1` 의 v2 사전등록 후 새 runId 재측정.
2. **AC-37-OUT-NORMAL 0/5(argsExact)** — 토큰 수(57, 여유 71)·종료(stop)·스키마는 문제없고 인자 값 일치만 실패. 원문 미저장이라 원인 필드 미상. 권장: 필드별 일치 여부(불리언)만 남기는 진단 필드를 v2 하네스로 사전등록한 뒤 재측정(원문 저장 금지 유지). 인자 축(A 12/22·B 9/22)도 같은 진단이 필요합니다.
3. **G-ARG-LIMITS `from`·`to`·`q`** — SPEC 소유자 결정 전까지 MAX·ESCAPE NOT_RUN, F0 exit 차단 유지.
4. **G-F0-TARGET (a)** — 제안서 반영 여부·시점은 SPEC 소유자 결정. 위 1·2 재측정을 할 경우 이번 회차가 아닌 새 회차 제안서를 반영하는 편이 일관적입니다.

#### 변경 파일(이번 세션)

- 신규(git 추적 제외): `eval/f0/results/20261029T234506Z/**`(16 파일).
- 신규(미추적): `eval/f0/chat-template/{qwen3-4b-instruct.template, .template.sha256, model-digest.txt, run-binding.json}`, `evidence/F0/{AC-37-CMP, AC-37-OUT-NORMAL, AC-38-F0, runs}/*.json`, `evidence/F0/GATE-REPORT.md`, `docs/gates/f0/p03-run-20261029T234506Z.md`.
- 수정: 이 절(§12.5), Plan 진행 표시(`Plan 상태`·`실행·증거 상태` 행 앞머리, P02 체크·P03 진행 기록 줄).
- 레포 밖: `%TEMP%\aigw-p03\`(실행 스크립트·Gradle 로그·runId·터널 PID — 접속 정보·원문 없음).

### 12.6 P03 F0 실패 보완 v2 준비 (2026-10-30, GPT-6.1-sol 구현·문서 대조, 정식 재측정 전)

- §12.5의 v1 회차 `20261029T234506Z`·판정·실패 증거와 C1/C2·v1 PREREG는 보존한다. 사용자 승인에 따라 F0 실패의 보완을 별도 v2 입력·하네스 경로로 준비한 기록이다. **P03 완료 아님, F0 FAIL 유지, v2 정식 재측정 0, P04 미착수**다. 여기의 정적 계수·stub 시험은 실제 모델 생성이나 AC 합격 증거가 아니다.
- 승인된 제품 계약은 SPEC §14.5의 `q ≤ 256 CODEPOINT`와 분/선택 초/소수초 1~9자리/선택 `Z`·`±HH:mm` 시각이다. q256은 Source 공식 상한이 아닌 승인된 Gateway 지원 범위다. 실제 입력 q 초과는 분류 전 확인하고 절삭·대체·해당 Source 검색·Composer 없이 기존 `FAILED`/`INPUT_BUDGET_EXCEEDED`와 `clarification{reason, fields:["q"]}`로 거부한다. 모델이 유효 입력에서 생성한 초과·형식 위반은 기존 1회 재시도·`PLAN_INVALID` 정책이다. 시간은 실제 날짜·시각·offset을 엄격 검증하고 Context 시간대로 같은 instant·나노초·원문의 정밀도를 보존한다. 이 계약의 미래 Gateway 적용 책임은 Plan P05/P13/P20이며, 현재 F0 하네스 보완을 그 phase 구현 완료로 표시하지 않는다.

#### 변경 책임과 정적 대조

- SPEC(`nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`)은 §14.5에 승인 q·시간·실패 계약을 반영했고, 기존 Plan(`…/ai-rag-gateway-architecture-plan.md`)은 G-ARG-LIMITS·P03·P05·P13·P20·R-22의 연결을 반영했다. Plan 79행의 사용자 지정 정본 예외를 유지하며 중앙 work item·대체 Spec·audit 문서는 만들지 않았다.
- 문서 `docs/gates/f0/arg-limits.md`는 v1 조사 이력을 남기고 v2 승인 계약을 덧붙였다. 신규 `eval/f0/v2/arg-limits-v2.json`은 eqp64·q256 CODEPOINT와 경계 생성용 최장 35자 시각을 CONFIRMED로 기록한다. 최장 표기는 모든 입력에 초·9자리·offset을 강제하는 문법이 아니다.
- 신규 `eval/f0/v2/prompts/{plan-v2,classify-v2}.txt`는 question 원문 추출·Context 필드 복사 금지·큰따옴표 검색어·실제 날짜·초/나노초 보존·TC/WM 소유권을 명시한다. `eval/golden/tc-registry-fixture-v2.json`은 fixtureId뿐 아니라 TC 통신/실패 로그·TC 자체 LOT 요약·WM 정본 소유 영역과 LIVE_QUERY 근거까지 실제 반영되어 있음을 직접 확인했다. 신규 `eval/golden/tc-f0-v2.jsonl`과 `eval/f0/v2/output-boundary-cases.jsonl`은 v1과 분리한다.
- 하네스 변경: `Prereg`·`HarnessDigest`·`MeasurementSession`·`F0RunnerCli`는 선택한 버전 입력·등록·경로·해시의 결속을 담당한다. `ArgLimits`·`ClassificationJudge`는 v2의 디코드 코드포인트·엄격한 시간 검증을 적용하고 v1 진입 계약은 유지한다. `BoundaryCases`는 v2 최장/이스케이프 경계를 만든다. `F0Measurements`는 인자별 일치 불리언·고정 오류 종류만 진단에 추가한다. `F0Judgement`·`F0ReportWriter`는 등록 케이스 집합과 독립 회차 사실을 대조하여 위조된 합계·누락·중복·다른 등록의 결과를 통과시키지 않는다. 실제 생성 문자열의 정확 일치 기준을 같은 instant 비교로 완화하지 않는다.
- 관련 시험 파일은 기존 `BoundaryGenJudgeTest`·`F0TestRoot`·`PreregCommitGuardTest`와 신규 `F0VersionedInputsTest`·`F0ArgumentContractTest`·`F0BoundaryCasesV2Test`·`F0BoundaryIntegrityTest`다. 이번 문서 대조 담당자는 제품 코드·시험·v1 입력·PREREG·결과·증거·commit을 추가 수정하지 않았다. `git diff HEAD`에서 v1 arg-limits·PREREG·v1 prompt/golden/fixture 변경 경로가 없음을 확인했다. `docs/gates/spec-baseline.md`는 root 단독 갱신 대상이므로 이 대조에서 수정하지 않았다.

#### 정적 계수와 실행 이력의 구분

- v2 cases CLI는 준비 실행 exit 0, 9개 케이스를 생성했다. `eval/f0/results/v2-preparation/official-count.json`의 공식 tokenizer 정적 계수는 아래와 같다. tokenizer SHA-256은 `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`, cases SHA-256은 `7980cb39d1c404be9b0177fb8adcedef96b8bba21684d5ef55c2fb6073224af6`이며 실제 현재 cases 바이트와 일치한다.

| caseId | kind | 공식 기대 JSON 토큰 |
|---|---|---:|
| NORMAL-01 | NORMAL | 57 |
| NORMAL-PRECISE | NORMAL | 95 |
| MAX-MIXED | MAX | 398 |
| MAX-HANGUL | MAX | 398 |
| MAX-DIGIT | MAX | 398 |
| MAX-ASCII | MAX | 154 |
| MAX-SUPPLEMENTARY | MAX | 398 |
| ESCAPE-01 | ESCAPE | 402 |
| ESCAPE-CONTROL | ESCAPE | 1423 |

- 이 파일은 **미등록 v2 준비 정적 계수**다. 보정 57/53은 공식 tokenizer 계수이며 v2 GGUF 대조·실제 완결 생성·반복 지연/재시도 측정은 0이다. 128을 초과하는 기대 출력이 확인됐지만 출력128·계획160과 관련 예산을 이번 승인만으로 바꾸지 않았다. 기존 결과를 v2 PASS로 재표기하지 않는다.
- 구현 세션 전달 시험 이력: 버전/등록 경로 시험 26/26 PASS, runtime 연결 시험 11/11 PASS. 이어진 계약 시험은 XML상 17/17 PASS였으나 프로세스의 SPEC 환경 입력이 빠져 finalizer가 `SPEC_INPUT_MISSING`으로 실패했다. 따라서 그 명령 전체는 FAIL이며 성공한 검증으로 계산하지 않는다. 추가 SCHEMA ref 시험 1건은 당시 미실행이었다. root의 최종 전체 시험·직후 별도 `test-evidence verify` 재실행 결과는 이 기록 작성 시 아직 반영 전이다.
- 한국어 label이 손실된 임시 prefix 계수와 그에 의존한 이전 964 등의 입력 계수는 실제 조립 메시지의 근거가 아니므로 폐기한다. `MessageAssembler`가 조립한 실제 Java 메시지의 공식 tokenizer 입력 계수는 root가 별도 확인 중이며, 이 문서에서 미확정 수치를 확정하지 않는다.

#### SPEC 신원·측정 영향·남은 승인

- 승인 전 SPEC SHA-256은 `14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8`, 승인 q/시간 정책 반영 후 현재 SHA-256은 `550e97ff012725b2380aa862bc6dccf0bca3db8e64b8f4cf406286af03a53ec7`다. 이번 문서 대조 착수 시 Plan SHA-256은 `090f2349c21effe2f2e3cb0dfea7890bf096f2197e07a84593d67b4840c2a908`이며 대조 담당자는 SPEC·Plan 바이트를 변경하지 않았다. 이전 Plan 진행 기록의 SHA는 각 당시 이력이며 현재 파일 신원으로 재사용하지 않는다.
- 직접 측정 영향은 F0의 `AC-37-CMP`·`AC-37-OUT-NORMAL`·`AC-37-OUT-MAX`·`AC-37-OUT-ESCAPE`·`AC-38-F0`와 G-ARG-LIMITS/G-F0-TARGET이다. 제품 적용·회귀 범위는 P05 inputLimits/Manifest, P13 시간창/Source 검색어 경계, P20 분류/재시도/실패 매핑이다. 기존 leaf·phase·임계·R·분모 정책을 추가하거나 완화하지 않았다.
- 남은 사용자 결정은 ①손실 없는 최장 출력에 필요한 출력 상한과 관련 입력/지연/처리량 예산 ②기존 허용 C1/C2와 별개인 신규 로컬 사전등록 commit C3/C4다. 그 결정 전에는 새 출력 상한·정식 v2 사전등록·정식 재측정을 실행하지 않는다. remote publish는 승인되지 않았다. SSH·운영 변경·실제 모델 호출·git commit/push는 이번 보완 준비에서 실행하지 않았다.

#### root 최종 검증 후속 기록 (2026-10-30)

- 위의 전체 시험·입력 계수·기준선 대기 표기는 그 작성 시점 이력으로 보존한다. 아래는 root가 실제 실행하고 전달한 최종 결과이며, 문서 담당자는 현재 정제 요약·run-binding·입력 계수·승인 기준선 파일을 다시 읽어 신원을 대조했다.
- 무필터 `.\gradlew.bat :gateway-eval:test --console=plain`은 exit 0, `BUILD SUCCESSFUL`(3m 48s), **146/146 PASS·32 클래스·FAIL/ERROR/SKIP/ABORTED 모두 0**이다. 직후 별도 `.\gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain`도 exit 0 `TEST_EVIDENCE_VERIFIED`다. 이전 계약 시험 명령의 `SPEC_INPUT_MISSING` 실패 이력은 지우지 않으며, 최종 무필터 시험이 그 뒤 실행된 검증이다.
- 정제 요약 `gateway-eval/build/aigw-evidence/test/leaf-results.json`: `runId=1ba82ce4-8826-40f8-a1ee-ba794dd2a4b8`, `inputFingerprint=3e9d6b97b0c7115aececdd2246dae54aad48ba4b4a23222b90ea7331e1422580`, `specSha=550e97ff012725b2380aa862bc6dccf0bca3db8e64b8f4cf406286af03a53ec7`(`FILE_HASHED`·`LOCAL_FILE`), `contentSha256=494db0dd952eb451ac335ccc7b2f2d35d355da75599be9474c7088950dd88b1b`, `otrSha256=3426b5d8b46f811e791150a2eac8b2cd3b044169f0d400cf81e096364c422ea3`다. `records=[]`이므로 이 결과는 unit/stub·하네스 검증이며 Gateway AC 합격이나 실제 모델 생성 증거가 아니다.
- root는 승인 q/시간 계약을 `docs/gates/spec-baseline.md`의 seq2와 `docs/gates/spec-approvals/2.md`에 반영했다. seq1 내용은 보존했고 seq2는 §14.5·§22.2 변경, `measurementAffecting=true`·`affectedLeaves=ALL`이다. 로컬 승인 기준선 반영이며 추가 commit·publish·보호 변수 변경은 0이다. 앞의 직접 영향 leaf 목록은 이번 수정의 직접 연결 범위이고, 새 기준선의 증거 적격 판정은 보수적인 ALL을 따른다.
- `MessageAssembler`의 실제 Java 메시지와 등록 `ChatRenderers`를 공식 tokenizer로 계수한 최종 `eval/f0/results/v2-preparation/input-count.json`은 미등록 준비 계수이며 **경로 A 입력 최대 2500·경로 B 최대 2340 토큰**이다. 공식 기대 출력 최대는 앞 표의 1423토큰이다. root의 초기 2회 계수는 stdin CP949 디코딩으로 `TypeError`가 발생했으며, stdin binary를 UTF-8로 명시 디코딩한 마지막 성공 결과만 정본이다. 한국어 label 손실이 있었던 초기 prefix 계수 및 그에 따른 964/1023 보고는 부정확했으므로 폐기한다. 모델 API 호출은 없었다.
- root 보존 검증 결과: 등록 입력 11개는 C1 blob과 현재 바이트가 모두 일치한다. 원래 측정 raw bundle SHA-256 `1dedf3bd57eee12491708af9ccf1fb809f6ae44159c11c576a537aa2666b2806`, 원 PREREG·report·proposal·template·gate의 SHA도 보존됐다. 로컬 commit 총수는 기존 C1/C2 두 개이며 새 commit은 없다.
- q 참조 내부 스키마 권장안은 새 사용자 승인 응답을 기다리고 있으므로 종속 구현은 진행하지 않았다. 출력128/계획160 유지, 정식 v2 PREREG 없음, 정식 v2 재측정 0, 새 commit/push·SSH·모델 요청 0, P04 미착수다. **P03 미완료·F0 FAIL 유지**이며 후속 승인 요청은 root가 진행한다.

### 12.7 qRef 원문 보존 결함 수정·v2 예산 profile·호출 전 입력 audit·PREREG-v2 초안 (2026-11-02, 별도 독립 Claude Code 세션 — claude-opus-5-5, effort medium)

- §12.1~§12.6 기록과 v1 회차 `20261029T234506Z`·판정·실패 증거는 이력으로 보존한다.
- **P03 미완료, F0 FAIL 유지, 정식 v2 재측정 0, P04 미착수**다.
- 아래 계수는 모두 **미등록 준비 정적 계수**다. 실제 모델 생성·GGUF 계수·실제 usage의 PASS가 아니다.
- 착수 시 SPEC SHA-256은 `31f9d7ea8a7117a1e45cf151ea162a017d1300f3f3fd684957f2fa25aca040ab`, Plan은 `3cc3afa8ddd19692b4df0ecb4a198e3dae8cf0d2754345acebacd19a60083c54`였고 전달값과 일치했다.
- 승인 기준선은 seq2(`550e97ff…3ec7`) 그대로다. `docs/gates/spec-baseline.md`와 `docs/gates/spec-approvals/`는 편집하지 않았다(root 단독 writer).
- 적용한 지침:
  - 사용자 전역 `~/.claude/CLAUDE.md`
  - Plan 79행의 사용자 지정 정본 예외: 중앙 SDD work item·새 audit 문서를 만들지 않음
  - 레포 안 AGENTS/CLAUDE 파일은 없다(`nori-ai-gateway`·`nori-tc-exec` 루트 확인).

#### 1) 원문 보존 결함(확정) → 근본 수정

- **원인:** `QRefContext.extract`가 명시 `json:` 리터럴을 `String.getBytes(UTF_8)`로 바꾼 뒤 파싱했다. Java는 이 변환에서 짝 없는 UTF-16 surrogate를 `?`로 치환하므로, 손실된 원문이 후보로 통과했다. 요청 SHA(`requireQuestionMatch`)와 메시지 결속(`requireMessagesMatch`)도 같은 변환을 썼다. 그래서 lone surrogate가 든 질문과 `?`가 든 질문을 같은 요청으로 판정할 수 있었다.
- **수정:** `QRefContext.strictUtf8`(CharsetEncoder `REPORT`)는 손실 없는 인코딩만 허용한다.
  - `extract`는 스캔 전에 질문 전체를 검증한다(인용구 밖 lone surrogate 포함).
  - `json:` 리터럴 디코드, 요청 SHA, 메시지 결속 파싱도 같은 엄격 인코딩을 쓴다.
  - 오류에는 고정 코드(`QUERY_LITERAL_INVALID`/`QREF_REQUEST_MISMATCH`)만 담고 원문은 싣지 않는다.
- **Python 동등성:** `eval/py/count_tokens_qref_v2.py`의 `candidates`도 질문 전체에서 surrogate를 먼저 거부한다.
- **회귀 시험:** 외부 요청 JSON을 디코드한 질문으로 확인한다. Java `QRefContextTest`(+3)와 Python `test_decoded_request_surrogates_match_java`는 같은 입력을 쓴다.
  - 거부: `json:` 안의 실제 lone high/low, 인용구 밖 lone low, escaped `\ud800`/`\udc00`
  - 보존: escaped 유효 pair와 raw pair는 `😀`로 복원되고, 일반 quote의 raw 역슬래시(`C:\temp\new\u0041`)는 그대로 남는다.
  - 결속: `?` 치환본과의 SHA 충돌, 다른 요청이나 축소된 catalog의 혼입을 거부한다.

#### 2) 단일 예산 profile 전파와 호출 전 입력 확인(F0 전용)

- **profile 정의:** `Prereg.Budget`에 두 profile을 두고 `Document.budget()`이 schema로 고른다.

| profile | 입력 상한 B/A | 출력 상한 B/A | 경계 출력 상한 |
|---|---:|---:|---:|
| V1 | 1024/2048 | 128/160 | 128 |
| V2 | 3072/3072 | 192/256 | 192 |

- **PREREG 검사:** `Prereg.parse`는 예산 값이 JSON 정수이고 profile과 같을 때만 통과시킨다. 입력+출력 ≤ 4096도 함께 검사한다.
  - 검사 대상: `maxTokens`, `outputLimitTokens`(= 분류 `max_tokens`), v2의 `inputLimitTokens`·`contextTokens=4096`
  - `PREREG_INVALID` 대상: `"192"`, `192.0`, `1.92e2`, 128/160, 3200, 8192, 누락
  - 실제 v1 PREREG는 바이트 그대로 V1으로 읽힌다.
- **값 전파:**
  - `MessageAssembler.maxTokens()`(160/128 고정)를 제거했다.
  - `F0Measurements.run`/`boundaryGen`은 profile 값을 요청 `max_tokens`와 판정 상한으로 쓰고, 결과 헤더 `maxTokens`·`inputLimitTokens`에 기록한다. 기존 boundary의 고정 128은 없앴다.
  - `F0Judgement`의 경계 상한과 종료 여유는 `budget().outputLimit()`을 쓴다. 기존 `asInt(128)` 기본값은 없앴다.
  - `F0ReportWriter`의 보고서와 §14.5 제안서에 profile 줄을 넣었다. 증거 metrics의 `outputLimitTokens`도 같은 값이다.
- **`InputAudit`(신규, F0 v2 전용):** 등록 역할 `inputAudit`의 표(실제 조립 메시지 SHA → 공식 tokenizer 입력 계수)만 사용한다.
  - 측정 명령은 등록 검사 뒤 하네스가 보낼 수 있는 chat 목록 전체를 대조한다. 목록에는 첫 시도, `ClassificationJudge.RETRY_ERROR_CODES` 11종별 재시도, usage·token 행이 들어간다.
  - 세션은 재시도를 포함한 매 호출 직전에 다시 확인한다. 표에 없거나 입력 상한, 또는 입력+`max_tokens` ≤ 4096을 어기면 송신하지 않는다(`INPUT_AUDIT_MISSING`/`INPUT_BUDGET_EXCEEDED`, exit 2).
  - 런타임 template·tokenizer SHA가 표와 다르면 run-binding을 쓰기 전에 `INPUT_AUDIT_RUNTIME_MISMATCH`로 멈춘다.
  - 실제 응답 `usage.prompt_tokens`는 `auditInputTokens`와 별도 열로 남긴다. 보고서에는 사전 계수보다 작은 호출 수만 따로 적는다(판정 규칙 변경 없음).
  - 이 기능은 P18 운영 `PromptTokenCounter`를 당겨오거나 완성한 것이 아니다.
- **준비 도구:**
  - `f0 input-audit-render`: 추론 호출 0. 출력은 레포 밖 임시 JSONL이며 계수 후 삭제했다.
  - `eval/py/count_input_audit_v2.py`: pin이 다르면 exit 3. 원문은 출력하지 않는다.
- **신규 `F0InputBudgetTest`(6건)이 확인하는 내용:**
  - PREREG 유형·값 거부
  - stub 요청 `max_tokens`가 B 192, A 256, boundary 192
  - completion 150은 유효, 192는 통과, 193은 `USAGE_EXCEEDS_LIMIT`
  - 보고서의 profile 줄
  - 재시도 1건 누락, 입력 3073, TG12 4096이면 요청 0·run-binding 0
  - template 불일치면 chat 0
  - 첫 호출 뒤 미등록 재시도 차단
  - judge 오류 코드 집합 = 재시도 계수 집합
- **기존 시험 보정:**
  - `F0BoundaryIntegrityTest`: v2 경계 상한 192(LIMIT±1)
  - `BoundaryGenJudgeTest`: 회차 키에 `promptTokens`(정수) 허용
  - `GoldenSetSchemaTest`: `Budget.V1` 사용
  - `F0VersionedInputsTest.versioned`: v2 profile·audit 등록
  - `QRefFlowTest`: 재생성한 경계 메시지로 audit 재작성
  - `OllamaStub.DEFAULT_TEMPLATE`: 상수로 노출

#### 3) 실제 계수(공식 tokenizer `aeb13307…dae4`, 검토 렌더러 template `40c21f34…b541`)

- `eval/f0/v2/input-audit-v2.json`(SHA `c30c3b60…8ce0`)은 651행이다. 구성은 (22×2 + 9) × 12 + usage 2 + token 13이며 예산 초과는 0이다.

| 그룹 | 첫 시도 최대 | 재시도 최대 |
|---|---:|---:|
| A(계획) | 1248 | 1287 |
| B(분류) | 1088 | 1127 |
| 경계(B) | 2701 | 2740 |
| usage | 1054 | - |
| token | 2793 | - |

- **root의 이전 `v2-qref-preparation/input-count.json`과 대조:**
  - 같은 값: B G22 첫 시도 1064, 경계 첫 시도 최대 2701, A 골든 첫 시도 최대 1248
  - 재시도 최대는 모든 오류 코드를 반영해 1씩 크다.
  - 이전 A 경계 2861/2899는 계획 프롬프트로 경계 케이스를 조립한 정적 계수다. F0 `boundary-gen`은 분류 경로만 쓰므로 이 메시지는 보내지 않는다. 그래도 3072 이하다.
- **출력 wire 공식 계수:** 수정한 스크립트로 새 파일 `eval/f0/results/v2-qref-preparation/official-count-20261102.json`에 다시 셌다. 기존 `official-count.json`과 내용이 같다(NORMAL 58/96, MAX·ESCAPE 145, 보정 57/53, cases SHA `b5a31c95…`).
- 계획 10 step 기대 출력 최악값 227(root 전달값)은 A 256 안이다. `v2-preparation`의 이전 1423 계수 이력과 기존 파일은 그대로 두었다.

#### 4) v2 usage/token-golden 입력(v1과 분리)

- `eval/f0/v2/token-golden-input-v2.jsonl`(SHA `b1e865ce…b233`)은 v1의 13개 ID와 순서를 유지한다.
  - `TG12-NEAR-COMPOSER-LIMIT`만 줄였다. v1 관측 38줄에 39번째 줄의 ", 재시도" 앞부분까지를 남겨, 공식 tokenizer·검토 렌더러 기준 2793토큰(≤ 2816)이 됐다.
  - 참고 계수: 1~38줄은 2745, 1~39줄은 2816.
  - v1 TG12의 공식 계수는 3952로 과거 실제 측정값 3952와 같다. GGUF 계수는 하지 않았다.
- `eval/f0/v2/usage-probe-input-v2.jsonl`은 v1과 같은 조립 행이다(U1=G01, U2=G02, 바이트 동일). 조립은 v2 역할로 한다.

#### 5) PREREG-v2 초안

- **경로·상태:** `eval/f0/PREREG-v2.md`(SHA `bda2c7a7…287f`), `registrationStatus=UNCOMMITTED`, `registeredCommit=null`, `runtimeCaptureDir=eval/f0/chat-template/v2-qref`.
- **입력 15개**(SHA는 실제 바이트로 계산):
  - v2 역할 10개. `chatRenderers`는 v1 렌더러 파일을 재사용하고 `inputAudit`을 더했다.
  - 지원 5개: tokenizer, pin, `count_tokens_qref_v2.py`, `count_input_audit_v2.py`, requirements
- **`harnessDigest`:** `98611f510266172915c825b5efcc75c7aad1ceab943caf0a34ece1c385a1407d`. Python 계산값과 Java `f0 prereg-check`의 `currentHarnessDigest`가 같고 `localMismatches=[]`이다.
- **측정 전 guard:** 아래 두 명령 모두 endpoint를 호출하기 전에 Java exit 2 `PREREG_NOT_COMMITTED`로 거부됐다. 결과 디렉터리는 생기지 않았다.
  - `f0 prereg-check`
  - `f0 run --prereg eval/f0/PREREG-v2.md`(리스너 없는 로컬 포트 지정)
- v1 PREREG는 덮어쓰지 않았다.

#### 6) SPEC·Plan 동기화(현재 profile, 과거 값은 이력)

- **SPEC §14.5 계산 근거**(분류·계획 prefill은 보수값 569.2):

| 호출 유형 | 입력/출력 | 계산 | 추정 | 입력+출력 |
|---|---|---|---|---:|
| 분류 | 3072/192 | 3072/569.2 + 192/15.6 | ≈17.7s(≈18) | 3264 |
| 계획 | 3072/256 | 3072/569.2 + 256/15.6 | ≈21.8s(≈22) | 3328 |
| Composer | 2816/128 | 2816/823 + 128/15.6 | ≈11.6s(≈12) | 2944 |

- **경로 추정:** 기본 경로는 상한 크기 기준 ≈30s, 처리량은 `60/30 ≈ 2.0건/분`이다.
  - 30s 예산 대비 Tool 여유는 0이고 보장하지 않는다.
  - UNSUPPORTED 15s 예산은 상한 크기 분류 추정 ≈18s보다 작다.
  - 경로 예산 15/30/90s와 Job 180s는 완화하지 않았다. 충족 여부는 재시도를 포함한 실측 p95로만 판정한다.
- **이력 보존:** v1 128/160, 1,024/2,048, ≈22s, ≈8s, 2.7건/분, 57/53.
- **그 밖의 SPEC 수정:** §5.1 Agent round의 계획 예산, §5.4 Job 근거, §13.3 ⑤, AC-37·AC-37-OUT-*(등록 출력 상한 v2 192·복원 정확도), AC-23-PERF 여유 문구, AC-43-CLS(3,072). 변경 이력에 1행을 추가했다.
- **바꾸지 않은 값:** Composer 2816/128, num_ctx 4096, 큐/동시성 정책.
- **SCHEMA ref 보존:** SPEC 1~1208행은 줄 수를 바꾸지 않고 줄 안에서만 고쳤다. 그래서 v1·v2 arg-limits SCHEMA ref(1203·1207·1208)가 그대로 같은 근거 줄을 가리킨다. `arg-limits-v2.json`은 바꾸지 않았고, 이는 시험으로 확인했다.
- **Plan 수정 위치:** G-F0-TARGET, P02 목표·v2 oracle, P03 v2 적용(단일 profile·`inputAudit`·PREREG-v2 경로), P18 qRef 계수 경계·`TokenBudgetPolicy`, P19 `LlmPlanPath` 3,072/256, AC-37-OUT-NORMAL 행, R-22, 남은 위험, P03 v2 준비 기록 1줄.
  - P05·P13·P20의 qRef 소비 경계 문구는 profile과 충돌하지 않아 그대로 두었다.
  - `docs/gates/f0/arg-limits.md` 64행에 남아 있던 B128/A160 현재형 문구도 같은 profile로 맞췄다.
- **현재 SHA-256(이 절 작성 시점):** SPEC `0f00921bce3ad642ede4be22ede0e9e00450eeed460a8b6bef063cce4e761e28`, Plan `5c5131a057271000b0f1471b4d2e8a243a9f0e2e20b90ed3cb16fb2ff2465875`.

#### 실행 명령과 실제 결과

실행 조건:

- 모든 명령은 별도로 실행했다.
- Gradle은 `C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -File %TEMP%\aigw-qref\{g,r}.ps1`로 실행했다. 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC, `DEBUG`=빈 값을 지정했다.
- Python은 venv에서 `-I -B`로 실행했다.
- 동시에 실행된 다른 Gradle은 없었다(`--status`: IDLE 1, 나머지 STOPPED).

| 명령 | exit | 결과 |
|---|---|---|
| `:gateway-eval:compileTestJava` | 0 | 컴파일 성공 |
| `:gateway-eval:test --tests com.nori.ai.gateway.eval.f0.*`(1차) | 1 | XML 99건 중 4건 FAIL(이력). 원인은 경계 회차 키 기대값, 경계 fixture ID, 정규 직렬화된 192.0, audit 검사가 PREREG 검사보다 앞선 순서였다. 시험과 검사 순서를 고쳤다 |
| 같은 명령(2차) | 1 | XML **99/99 PASS**. `testLeafEvidence`가 `SPEC_BASELINE_MISMATCH`로 실패했다(기준선 seq2 ≠ 현재 SPEC, 예고된 finalizer 실패, 우회 0) |
| 같은 명령(최종) | 1 | XML **99/99 PASS, 21 클래스, FAIL/ERROR/SKIP 0**. 같은 `SPEC_BASELINE_MISMATCH`가 났다. 단위 결과와 프로세스 실패는 별개이며, 이 명령을 성공한 검증으로 계산하지 않는다 |
| `python -I -B tests/test_count_tokens_qref_v2.py` / `tests/test_count_input_audit_v2.py` | 0 / 0 | 4 OK / 3 OK. 새 `__pycache__` 0 |
| `f0 input-audit-render …` | 0 | `F0_INPUT_AUDIT_RENDERED 651` |
| `count_input_audit_v2.py … --out eval/f0/v2/input-audit-v2.json` | 0 | `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0` |
| `count_tokens_qref_v2.py … --out …/official-count-20261102.json` | 0 | `OFFICIAL_COUNT_WRITTEN 9`. 기존 파일과 내용 동일 |
| `f0 prereg-check --prereg eval/f0/PREREG-v2.md` | Java 2(Gradle 1) | `localMismatches=[]`, digest 일치 후 `PREREG_NOT_COMMITTED` |
| `f0 run --prereg eval/f0/PREREG-v2.md --path B …` | Java 2(Gradle 1) | `PREREG_NOT_COMMITTED`. 요청 0, 결과 디렉터리 0 |
| `git diff --check`(gateway, tc-exec SPEC/Plan) | 0 / 0 | 새 파일도 CR 0, 후행 공백 0 |

- **보존 확인:**
  - v1 등록 입력 11개와 `eval/f0/PREREG.md`는 HEAD와 같고, PREREG에 등록된 SHA와도 같다. tokenizer와 pin도 HEAD와 같다.
  - 원 측정 raw bundle은 `1dedf3bd…2806`로 일치한다.
  - `eval/f0/results/20261029T234506Z/**`, `evidence/F0/**`, `eval/f0/chat-template/**`, `v2-preparation/**`에는 2026-11-01 이후 수정된 파일이 없다.
  - 로컬 commit은 C1/C2 두 개다.
- **실행하지 않은 것:**
  - 무필터 whole test와 `test-evidence verify`. root가 최종 SPEC SHA를 기준선에 추가한 뒤 직접 실행한다.
  - 실제 모델·HTTP·SSH 호출, GGUF 계수, 정식 v2 측정
  - C3/C4 commit·push, 네트워크 다운로드

#### 남은 승인·조건

1. root는 현재 SPEC `0f00921b…1e28`을 승인 기준선 사슬에 `measurementAffecting=true`로 추가하고, whole test와 별도 `test-evidence verify`를 실행한다.
2. 사용자는 신규 로컬 C3/C4의 허용 범위를 정한다. C3 전에 대상이 바뀌면 PREREG-v2의 SHA·digest를 다시 계산해야 한다.
3. 정식 v2 측정은 등록 후에 한다. 순서는 다음과 같다.
   - chat-template: 실제 template SHA가 audit의 `40c21f34…`와 같아야 한다.
   - A/B → boundary-gen → gguf-count → usage/token → report
   - 그 전에는 192/256 안 완결 생성, 지연 p95, UNSUPPORTED 15s·30s Tool 여유를 주장하지 않는다.
4. G-F0-TARGET(정확도·지연 목표 확정)은 SPEC 소유자가 결정한다.

#### 변경 파일(이번 세션)

- **수정(gateway):**
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{QRefContext,Prereg,MessageAssembler,ClassificationJudge,MeasurementSession,F0Measurements,F0Judgement,F0ReportWriter,F0RunnerCli}.java`
  - 시험 `{QRefContextTest,F0VersionedInputsTest,F0BoundaryIntegrityTest,BoundaryGenJudgeTest,GoldenSetSchemaTest,QRefFlowTest,OllamaStub}.java`
  - `eval/py/count_tokens_qref_v2.py`, `eval/py/tests/test_count_tokens_qref_v2.py`
  - `docs/gates/f0/arg-limits.md`(64행), 이 절
- **신규(gateway):**
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/InputAudit.java`
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0InputBudgetTest.java`
  - `eval/py/count_input_audit_v2.py`, `eval/py/tests/test_count_input_audit_v2.py`
  - `eval/f0/v2/{input-audit-v2.json,token-golden-input-v2.jsonl,usage-probe-input-v2.jsonl}`
  - `eval/f0/PREREG-v2.md`
  - `eval/f0/results/v2-qref-preparation/official-count-20261102.json`(git 추적 제외 경로)
- **수정(tc-exec 문서):** `docs/task/ai-rag-gateway-architecture.md`, `docs/task/ai-rag-gateway-architecture-plan.md`
- **레포 밖:** `%TEMP%\aigw-qref\`에 실행 스크립트·Gradle 로그·meta가 있다. 렌더 임시 JSONL은 계수 후 삭제했다. 원문 응답·접속 정보는 없다.

#### root 최종 검증 후속 기록 (2026-11-02)

- 위의 기준선 대기·99개 부분 시험 기록은 작성 시점 이력이다. Claude Code Opus 5.5 medium 세션 종료 후 root가 SPEC SHA `0f00921bce3ad642ede4be22ede0e9e00450eeed460a8b6bef063cce4e761e28`, Plan SHA `5c5131a057271000b0f1471b4d2e8a243a9f0e2e20b90ed3cb16fb2ff2465875`를 독립 재계산했다.
- root 단독 책임인 기준선에 seq3과 `docs/gates/spec-approvals/3.md`를 추가했다. qRef 사용자 승인 및 §14.5 기술 예산 조정 계약에 따른 로컬 반영이다. seq1·seq2는 보존했고 `affectedLeaves=ALL`, `measurementAffecting=true`다. commit·publish·보호 변수 갱신은 실행하지 않았다.
- `$env:AIGW_SPEC_FILE`을 실제 SPEC 경로로 지정한 무필터 `.\gradlew.bat :gateway-eval:test --console=plain`은 **exit 0, BUILD SUCCESSFUL(3m 16s)**다. XML 독립 집계는 **162/162 PASS·35클래스·FAIL/ERROR/SKIP 0**이며 정제 요약의 ABORTED도 0이다.
- 직후 별도 `.\gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain`은 **exit 0, TEST_EVIDENCE_VERIFIED**다. root가 요약 content SHA, run-binding의 runId, OTR XML 해시 목록의 canonical JSON SHA를 별도 계산해 모두 일치함을 확인했다.
- 최신 시험 신원: `runId=cd800e31-cb95-40ce-aaa5-d65bd4fe51f4`, `inputFingerprint=1c7690289491ff960058d8d2209a013bd732686d576c34143d1620c147448112`, `contentSha256=0b52764c5824865992e8f56f5b64e84e8931010177be901bec125ef7ca5be2c8`, `otrSha256=add37545bc53295d4883158582a7f55790cfec59bf69a48be04cfe79a2065f01`, `specShaSource=FILE_HASHED`, `specInputOrigin=LOCAL_FILE`. `records=[]`이므로 실제 모델·Gateway AC 합격 증거가 아니다.
- root가 Python 두 시험 파일을 `python -I -B`로 다시 실행했다. `test_count_tokens_qref_v2.py` 4개와 `test_count_input_audit_v2.py` 3개가 모두 OK다.
- root 독립 보존·초안 검사: 보호 대상 37개 파일의 SHA 불일치 0, 기존 commit C1/C2 두 개 유지, v2 등록 입력 15개 SHA 불일치 0. 실제 소스와 등록 입력으로 계산한 harnessDigest는 `98611f510266172915c825b5efcc75c7aad1ceab943caf0a34ece1c385a1407d`로 초안과 같다. `registeredCommit=null`은 유지했다.
- **현재 상태:** qRef·예산·등록 보호의 로컬 수정 검증 완료. P03은 미완료이며 기존 F0 FAIL을 유지한다. 정식 v2 사전등록 C3/C4, 실제 GGUF 계수·생성·정확도·p95 재측정, SPEC 결과 기록 및 공급 게이트는 미실행이다. P04는 미착수다. 신규 C3/C4는 기존 사용자 허용 C1/C2 범위를 넘어가므로 별도 허용 전에는 만들지 않는다.

### 12.8 P03 v2 사전등록 C3/C4·정식 v2 실측 (2026-11-02, 별도 독립 Claude Code 세션 — claude-opus-5-5, effort medium)

- §12.1~§12.7과 v1 회차 `20261029T234506Z`의 판정·증거는 이력으로 보존한다. 사용자 승인: "로컬 C3/C4 허용 후 계속 진행". push·원격 publish·보호 변수·서비스/설정·TC backend 변경 0. 이 절은 미커밋이다(추가 commit 금지).
- **결론: 정식 v2 측정은 끝까지 실행됐으나(측정 실행 실패 0) F0 gate = FAIL. P03 미완료, P04 미착수.** SPEC 결과 반영·새 기준선·publish·9단계(`--rejudge`·`--spec-record-check`)는 root/SPEC 소유자 후속이며 NOT_RUN이다(SPR component NOT_RUN은 PASS가 아니다).

#### 1) 사전등록 C3/C4

- C3 직전 대조: 등록 입력 15개 SHA 불일치 0, harnessDigest 재계산 `98611f510266172915c825b5efcc75c7aad1ceab943caf0a34ece1c385a1407d`(초안 일치), v1 PREREG·v1 등록 입력 11개·tokenizer/pin의 HEAD 대비 delta 0, `git diff --cached --check` 0.
- **C3 `d44c51ddb275330881bc34fa025c04a6369014f8`**(45파일, 명시 목록 stage): f0 `src/main` 수정 11·신규 2(`InputAudit`·`QRefContext`), f0 `src/test` 수정 6·신규 7, `eval/f0/PREREG-v2.md`(`registeredCommit=null` 초안), `eval/f0/v2/**` 8, `eval/golden/tc-{f0-v2.jsonl,registry-fixture-v2.json}`, `eval/py/count_{tokens_qref,input_audit}_v2.py`와 시험 2, `docs/gates/{spec-baseline.md,spec-approvals/2.md,spec-approvals/3.md,f0/arg-limits.md}`, 이 dev report(§12.7까지).
- **C4 `a861004f170c747d028bf9f7404ab7cb84821574`**: `eval/f0/PREREG-v2.md`만. `registeredCommit`=C3, `registrationStatus=REGISTERED`, 미등록·요청 0 문구를 이력으로 분리, 사용자 승인 기록.
- C3에 넣지 않은 것(원래 dirty 보존): `docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/` 최상위 4개, `evidence/F0/**`, `eval/py/**/__pycache__`.

#### 2) 실행 조건

- 부모 회차 **`20261102T003840Z`**. 레포 밖 새 디렉터리 `%TEMP%\aigw-p03v2\`의 `g.ps1`(그 프로세스에만 `AIGW_SPEC_FILE`=실제 SPEC, `AIGW_PARENT_RUN_ID`=runId, `DEBUG`=빈 값, `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`, 출력은 로그 파일)로 하나씩 실행했다. 옛 `%TEMP%\aigw-p03\` 스크립트는 읽기만 했다.
- 터널: 착수 전 로컬 11434 리스너 0 확인 → 숨김 ssh(`-i k8s-node01`, BatchMode, ConnectTimeout 12, `jetson@192.168.0.52`, PID 7028) → 측정 후 그 PID만 종료, 리스너 0. 키 파일은 존재만 확인했다.
- 모델: 착수 preflight와 종료 직전 읽기 전용 `/api/ps` 모두 qwen3:4b-instruct(`0edcdef3…8ba0`, ctx 4096)·bge-m3:latest(`79076464…6bab`, ctx 4096) 상주. 로드·pull·관리 API·num_ctx·서비스 변경 0.
- SPEC SHA `0f00921b…1e28`(보고서 측정 SPEC SHA 일치), 기준선 seq3. 원문 질문·응답은 파일·화면에 남기지 않았다(하네스 정제 필드·메트릭만).

#### 3) 명령과 exit(UTC)

| 단계 | 명령(`--prereg eval/f0/PREREG-v2.md` 공통, endpoint `http://127.0.0.1:11434`) | 시각 | exit | 결과 |
|---|---|---|---|---|
| 0 | `f0 prereg-check` | 00:38:44~00:38:50 | 0 | `PREREG_VERIFIED`, digest 일치 |
| 1 | `f0 preflight --out eval/f0/results/$runId/preflight.json` | 00:39:12~00:39:22 | 0 | `F0_PREFLIGHT_OK 2` |
| 2 | `f0 chat-template --model qwen3:4b-instruct --out eval/f0/chat-template/v2-qref`(신규 디렉터리) | 00:39:27~00:39:34 | 0 | template 4040 B `40c21f34…b541`(= audit), model-digest `0edcdef3…8ba0` |
| 3 | `f0 run --path B --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --prompt eval/f0/v2/prompts/classify-v2.txt --repeats 5 --run-id $runId --out …/B` | 00:39:39~00:51:08 | 0 | 110/110, complete |
| 4 | `f0 run --path A … --prompt eval/f0/v2/prompts/plan-v2.txt … --out …/A` | 00:51:59~01:03:14 | 0 | 110/110, complete |
| 5 | `python.exe -I -B eval/py/count_tokens_qref_v2.py --tokenizer … --expect-sha256-file … --cases eval/f0/v2/output-boundary-cases.jsonl --out …/official-count.json` | 01:03 | 0 | `OFFICIAL_COUNT_WRITTEN 9`, 준비 파일과 바이트 동일 |
| 6 | `f0 gguf-count --cases eval/f0/v2/output-boundary-cases.jsonl --out …/gguf` | 01:04:13~01:04:44 | 0 | `F0_GGUF_COUNT_COMPLETED 9` |
| 7 | `f0 boundary-gen --cases … --fixture …-v2.json --prompt …/classify-v2.txt --repeats 5 --run-id $runId --out …/boundary` | 01:04:48~01:11:44 | 0 | 45/45 회차, complete |
| 8 | `f0 usage-probe --messages eval/f0/v2/usage-probe-input-v2.jsonl --out …/usage.json` | 01:12:11~01:12:37 | 0 | complete |
| 9 | `f0 token-golden --messages eval/f0/v2/token-golden-input-v2.jsonl --out …/token-golden.jsonl` | 01:12:41~01:13:23 | 0 | 13행 |
| 10 | 터널 종료(본인 PID) | 01:13 | 0 | 리스너 0 |
| 11 | `f0 report --run-id $runId --results eval/f0/results/$runId --out evidence/F0` | 01:13:41~01:13:47 | 0 | `F0_REPORT_WRITTEN 5`, report.md `af26e536…`, proposal `9eaf60f6…`, 신규 hash 파일 6(leaf 5·RUN 1) |
| 12 | gate(레포 밖 `%TEMP%\aigw-p03v2\gatehelper\GateToFile.java`: `SpecGuard`→`LeafCatalog.load(null)`→`EvidenceRepositoryFs`→`GateEvaluator.evaluate`→`GateReportWriter`, EvalCli `gate`와 같은 호출, 출력만 `eval/f0/results/gate-reports/$runId.md`, 6) 후속 보정 참조) | 01:14:56~01:14:58 | 0 | **FAIL**, 기준선 seq3, `a66b2617…` |
| 13 | 사후 `f0 prereg-check` | 01:15:25~01:15:31 | 0 | `PREREG_VERIFIED`, `localMismatches=[]` |

- EvalCli `gate --stage F0`는 `evidence/F0/GATE-REPORT.md`를 덮어쓰므로 실행하지 않았다. 옛 GATE-REPORT.md는 불변이다.

#### 4) 결과(report.md·증거 metrics 정제값)

| 경로 | taskType | 인자 | UNSUPPORTED | UNS 오탐 | JSON 유효 | 1차 유효 | 재시도 | timeout | p50/p95 ms | 출력 토큰 p50/p95/max |
|---|---|---|---|---|---|---|---|---|---|---|
| A | 6/22 | 11/22 | 12/22 | 1 | 70/110 | 70/110 | 40/110 | 0 | 3302/13192 | 37/89/90 |
| B | 7/22 | 10/22 | 10/22 | 1 | 60/110 | 55/110 | 55/110 | 0 | 5446/11543 | 30/79/79 |

- 기대 T별 p95 ms — A: UNS 5060·LQ 12353·KQ 5947·GQ 2606·INC 13363 / B: UNS 5477·LQ 11543·KQ 2780·GQ 2454·INC 11589. 경로 예산(UNS 15s·LQ/KQ/GQ 30s·INC 90s) 안이지만, 유효율·정확도가 낮아 지연 결과만으로 합격을 주장하지 않는다.
- 잠정 후보 `KEEP_B`(AC-37-CMP F0R PASS — 후보 선택 규칙의 결과이며 정확도 합격이 아님).
- 비유효 원인(정제 오류 코드): A `QREF_INVALID` 60·`STEPS_INVALID` 20, B `QREF_INVALID` 105. 비유효 문항은 회차 5/5로 결정적이다(A 8문항: G01·G07·G11·G12·G17·G20·G21·G22, B 10문항: G01·G02·G06·G11·G12·G16·G17·G19·G20·G22). 모든 finish=`stop`, 출력 상한 도달 0.
- 입력 audit: 실제 `usage.prompt_tokens`가 사전 계수보다 작은 호출 A 0·B 0·boundary 0. 최대 입력 A 1248, B 1125.
- 출력 경계: 9케이스 모두 공식=GGUF(NORMAL-01 58, NORMAL-PRECISE 96, MAX·ESCAPE 7개 145), 종료 여유 ≥ 47 → 계수 조건 충족. 그러나 boundary-gen pass **0/5 전 케이스**: NORMAL 2케이스는 재시도 후에도 스키마 비유효 5/5, MAX·ESCAPE 7케이스는 스키마 유효 5/5이나 `TASK_TYPE_MISMATCH`·eqp 불일치로 argsExact 0/5. completion 79~99 ≤ 192.
- AC-38-F0: INC 기대 10표본 모두 UNS로 판정(incCount 0, unsCount 10) → FAIL `AC38_MISMATCH`.
- G-USAGE: `equalOnCacheHit` usage·evalCount 모두 true(U1 cold/warm 1054, U2 1036).
- token-golden: 13행 실제 `promptTokens`가 등록 audit 공식 계수와 13/13 일치(TG12 2793).

#### 5) 판정(`eval/f0/results/gate-reports/20261102T003840Z.md`)

| leaf | F0R | SPR |
|---|---|---|
| AC-37-CMP | PASS(KEEP_B) | NOT_RUN |
| AC-37-OUT-NORMAL | FAIL | NOT_RUN |
| AC-37-OUT-MAX | FAIL | NOT_RUN |
| AC-37-OUT-ESCAPE | FAIL | NOT_RUN |
| AC-38-F0 | FAIL(AC38_MISMATCH) | — |

- 수치·임계·분모·답·프롬프트·회차·모델 변경 0, 재측정 0.

#### 6) 보존 확인

- 작업 전 스냅샷한 보호 대상 35개(옛 `evidence/F0/**`·GATE-REPORT.md, `eval/f0/results/{20261029T234506Z,v2-preparation,v2-qref-preparation}/**`, `eval/f0/chat-template/` 최상위, 기존 pycache, `p03-run-20261029T234506Z.md`) SHA 불일치 0. 새 파일은 `eval/f0/results/20261102T003840Z/**`, `eval/f0/chat-template/v2-qref/**`, `evidence/F0/` 신규 hash 6개뿐이다. 새 `__pycache__` 0.
- 로컬 commit은 C1·C2·C3·C4 네 개이며 push 0.
- 후속 보정(파생 보고서 위치): root가 gate 파생 보고서 출력 위치를 회차 결과 디렉터리 `eval/f0/results/20261102T003840Z/gate-report.md`로 잘못 지정해, 이 파일이 `F0ReportWriter.rawResultsSha256` 집계(report.md·제안서 외 모든 파일)에 들어갔다(포함 시 `b2b84940…`). 그 한 파일만 PowerShell `Move-Item -LiteralPath`로 `eval/f0/results/gate-reports/20261102T003840Z.md`로 옮겼다(이동 전후 SHA `a66b2617…` 동일). 측정 파일 14개·evidence 바이트 변경 0이며, 같은 정의(정렬 상대 경로+바이트 SHA 쌍 목록의 RFC 8785 SHA-256)로 다시 계산한 raw SHA가 기록값 `1c6d04175ec04a17ed153453609c43361fea536ce9c0eba73e228f62f01df774`와 일치한다. 레포 밖 `%TEMP%\aigw-p03v2\gatehelper\GateToFile.java`·`gate.ps1`은 이번 회차 실행 이력이므로 수정하지 않고 보존했다. 앞으로 실행할 때는 파생 gate 보고서를 `eval/f0/results/gate-reports/$runId.md`에 쓴다.

#### root 최종 독립 확인 (2026-11-02, C3/C4 이후)

- 등록 입력 15개 SHA와 실제 소스의 harnessDigest를 다시 계산해 모두 등록값과 같음을 확인했다. 이전부터 root가 보관한 보호 대상 37개 파일의 SHA 불일치도 0이다. C1/C2 보존, 로컬 commit 총 4개, SPEC SHA `0f00921bce3ad642ede4be22ede0e9e00450eeed460a8b6bef063cce4e761e28` 유지다. Plan은 이번 회차 진행 기록만 추가되어 최종 SHA `7d7b2644a9ff58928c65e15c5739e50123ecfe7adcc5f92932fa1a068f2f54e5`다.
- root가 A/B 각각 110개 고유 문항·회차와 회차 1 정확도 3축, nearest-rank p50/p95, 재시도 수를 원래 정제 결과에서 독립 계산해 위 표와 일치함을 확인했다. 경계 45회도 중복·누락 없이 확인했다. MAX·ESCAPE 35회는 q·from·to가 일치하고 eqp와 taskType이 불일치했으며, NORMAL 10회는 schemaValid=false였다. G08은 A/B 10회 모두 effectiveT=UNS다. QREF_INVALID의 잘못된 참조 값·유형 등 더 세부적인 원인은 보존 자료만으로 단정하지 않는다.
- 원시 결과 14개 파일의 bundle SHA `1c6d04175ec04a17ed153453609c43361fea536ce9c0eba73e228f62f01df774`, 신규 증거 6개의 canonical content SHA와 파일명, leaf 증거의 rawResultsSha256을 독립 계산해 모두 일치함을 확인했다. 이동한 파생 gate 보고서 SHA는 `a66b2617bf13910a2a7cf59df968a88bbb2fc5f46ac3e2ae25bb76823b6aed24`다. 공개 API helper가 SpecGuard→LeafCatalog→EvidenceRepositoryFs→GateEvaluator→GateReportWriter를 호출하며 판정 로직을 추가하지 않았음도 확인했다.
- C4의 PREREG 메타데이터 변경은 선언된 F0 시험 입력에 포함되므로 최종 커밋 상태에서 whole test를 다시 실행했다. 실제 SPEC 경로를 AIGW_SPEC_FILE로 지정한 `.\gradlew.bat :gateway-eval:test --console=plain`은 **exit 0, BUILD SUCCESSFUL(3m 15s), 162/162 PASS·35클래스·FAIL/SKIP/ABORTED 0**이다. 직후 별도 `.\gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain`도 **exit 0, TEST_EVIDENCE_VERIFIED**다.
- 최종 로컬 시험 신원: `runId=45417c25-121a-482c-ae23-3822c25c3d3a`, `inputFingerprint=42e81fb63dfe17d099d023738e55677cc2d711332838b3dc981d9f93a86d74d2`, `contentSha256=85c56582095431cd9d821f1f05cde586f5910ee57237168cc0929ab421f19d26`, `otrSha256=11f275fe6852a75a950e12820af704bc62a240cda241750769cca33c0ee9a9b7`, `records=[]`다. 이 로컬 하네스 시험의 PASS를 실제 모델 F0 합격으로 계산하지 않는다.
- **최종 상태: 승인된 C3/C4와 v2 정식 측정·판정 실행 완료, F0 FAIL, P03 미완료, P04 미착수.** SPEC 결과 반영·새 기준선·publish·보호 변수 변경·추가 commit·push는 미실행이다. 다음 보완 대상은 원문을 노출하지 않는 QREF_INVALID 세부 진단과 taskType·설비 ID 추출 실패이며, 출력 토큰 상한 도달은 이번 측정에서 확인되지 않았다.

### 12.9 P03 실패 근본 보완 — 서버 구조화 출력 조건부 검증·원문 없는 시도별 진단·진단 사전등록 초안 (2026-11-02, 별도 독립 Claude Code 세션 — claude-opus-5-5, effort medium)

- §12.1~§12.8과 v1 회차 `20261029T234506Z`·v2 회차 `20261102T003840Z`의 판정·원시 결과·보고서·증거는 이력으로 보존한다.
- **결론: 로컬 구현·시험만 완료. 실제 모델·SSH 호출 0, C5/C6 commit 0, 진단·정식 재측정 0. F0 FAIL 유지, P03 미완료, P04 미착수.**
- 적용 지침: 사용자 전역 `~/.claude/CLAUDE.md`, Plan 79행 정본 예외(중앙 work item·새 audit 문서 0). 레포 안 AGENTS/CLAUDE 없음. `nori-sdd-specs/AGENTS.md`는 그 레포 SDD 실행기 계약이라 이 작업의 직접 대상이 아니다.
- 착수 시 SPEC SHA `0f00921b…1e28`(기준선 seq3), Plan `7d7b2644…54e5`로 전달값과 같았다. `docs/gates/spec-baseline.md`·`spec-approvals/`는 편집하지 않았다(root 단독 writer).

#### 1) 원문 없는 실패 진단(보존 자료 범위)

- **확인된 구조 결함:**
  - `ClassificationJudge.judge`는 첫 위반(QREF 44행)에서 바로 무효를 반환한다.
  - `F0Measurements.diagnostics`는 무효면 인자 일치를 모두 false로 쓴다.
  - 그래서 QREF 뒤의 `t`·eqp·시간 상태가 결과에 남지 않았다. v2 오류 세부(잘못된 qRef 값 종류 등)는 보존 자료로 단정할 수 없다.
- **v2 프롬프트 정적 불일치(인과는 미측정):**
  - Path A의 "160토큰 안에서 객체를 완결" 문구가 실제 `max_tokens` 256과 다르다.
  - steps 규칙이 "GQ·UNS는 []" 한 방향뿐이다. 판정은 KQ/LQ/INC의 빈 steps도 무효로 본다(A `STEPS_INVALID` 20과 같은 축).
  - "offset은 같은 instant로 timezone 변환" 지시는 경계 NORMAL-PRECISE 기대값(질문의 offset 표기 원문 유지)과 어긋날 수 있다. 다만 경계 파일에 오류 코드가 없으므로 NORMAL 0/10의 직접 원인으로 단정하지 않는다.
  - 출력 예시가 GQ·UNS 전부-null 두 개뿐이다. 라벨 편향 가능성은 있으나 측정으로 분리하지 않았다.
- `RegistryFixture.renderRegistry`는 aliases·ownedAreas·capabilities만 싣는다. 리소스·workflow 목록 누락을 원인으로 가정하지 않았고 모델에 리소스 목록이나 정답을 주입하지 않았다.

#### 2) 구현(gateway, harnessDigest 대상 f0 소스 포함)

- **`ResponseSchema`(신규):** 같은 요청 `QRefContext`로 OpenAI 호환 `response_format{type:"json_schema", json_schema{name, strict:true, schema}}`을 만든다.
  - schema: `required`, `additionalProperties:false`, `t` enum 5개, `eqp`/`from`/`to` `["string","null"]`
  - `qRef`: 후보 0이면 `{"type":"null"}`, 후보가 있으면 발급 ID enum(null 불가)
  - Path A `steps`: `{"type":"array","items":{"type":"string"}}`
  - 정답·기대 출력·리소스·capability·후보 원문은 넣지 않는다. 길이·시간 형식·비활성/미등록 Tool backstop은 기존 Gateway 판정 그대로다.
- **`OllamaChatClient.chat(…, responseFormat)`:** null이면 기존 body(`model, messages, temperature, max_tokens, stream`) 그대로다. 서버가 거부하면(HTTP 오류) `ENDPOINT_HTTP_ERROR` exit 5로 멈추며 JSON 모드·비제약 재송신은 없다.
- **`Prereg` `f0-prereg-v3`:** `purpose`(DIAGNOSTIC|GATE)와 `parameters.responseFormat`(NONE|`JSON_SCHEMA_V1`)이 필수다. v1/v2 문서에 이 키가 있으면 `PREREG_INVALID`. qRef wire·예산 profile V2·`f0-judge-v2`는 v2와 같다.
- **`F0Measurements.Attempts`:** 한 요청의 초회·재시도가 같은 `response_format`을 보낸다. 시도마다 audit 입력 계수와 `AttemptDiagnostics`를 기록하고, 행에 `responseFormatSha256`을 남긴다.
- **`AttemptDiagnostics`(신규):** 판정과 분리된 순수 함수다. 고정 값만 남긴다.
  - `finish` STOP/LENGTH/OTHER, `usageWithinLimit`, `json` OBJECT/NOT_OBJECT/PARSE_FAILED, `keys` EXACT/MISSING/EXTRA/MISSING_AND_EXTRA, `rawQKey`
  - `t.value`(알려진 enum 또는 UNKNOWN_TEXT/NON_TEXT/ABSENT)·`t.match`
  - `qRef.kind` JSON_NULL/ID_IN_RANGE/ID_OUT_OF_RANGE/EMPTY_TEXT/TEXT_OTHER/NON_TEXT/ABSENT, `candidateCount`, `accepted`, `textInQuestion`
  - eqp/from/to: `value` 종류, `match` MATCH/MISMATCH/NOT_EVALUABLE, eqp `inQuestion`·`equalsContext`·`equalsQuoteCandidate`·`registered`, 시간 `formatValid`·`equalsContext`
  - `q.match`(수용된 qRef의 복원값 기준), steps `shape`·`toolConsistency`·`unknownCount`·`inactiveCount`
  - 원문·카탈로그 값·부분 문자열·길이·toString은 내보내지 않는다. 원문 SHA는 기존 `outputSha256`만 쓴다.
- **`F0ReportWriter`:**
  - DIAGNOSTIC 등록은 `diagnosticReport`만 허용한다(`<results>/diagnostic-report.md`, 원시 SHA 집계 제외 파일로 추가).
  - `report`·`rejudge`·`specRecordCheck`는 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`. CLI도 `--out`·`--rejudge`·`--spec-record-check`·`--proposal`을 같은 코드로 거부한다.
  - 진단 보고서에는 경로 지표, 경계 pass, 기대 t×초회 t, 시도별 진단 분포, 실제 usage 대 audit 계수의 작음/큼 호출 수를 쓴다. 큼은 서버가 스키마를 입력에 덧붙이는지 확인하는 용도다.
- **`EvalCli gate --out` + `GateReportWriter.writeNew`:** 회차 파생 보고서를 새 파일로만 쓴다.
  - 거부(`GATE_REPORT_PATH_INVALID`): 레포 밖, `evidence/`, `eval/f0/results/` 아래의 `gate-reports/` 직하 이외 위치, `.md`가 아닌 파일
  - 이미 있으면 `OUTPUT_EXISTS`. `--out`을 생략하면 기존 `evidence/<stage>/GATE-REPORT.md` 동작 그대로다.
  - 지난 회차의 레포 밖 helper·원시 SHA 오염 문제를 정식 CLI로 해소했다.
- `ClassificationJudge`의 엄격 판정·재시도·오류 코드 11종은 바꾸지 않았다.
- `harnessDigest`가 바뀌므로 PREREG-v1/v2로 신규 측정과 `report`는 `PREREG_INPUT_CHANGED`, `--rejudge`는 `HARNESS_CHANGED`(exit 3)로 거부된다. 기존 시험이 확인하는 계약 그대로이며 옛 결과·보고서·증거 바이트는 바뀌지 않는다.

#### 3) v3 입력·진단 사전등록 초안(UNCOMMITTED)

- **프롬프트 c:** `eval/f0/v3/prompts/classify-v3.txt`(`f041e300…cd19`), `plan-v3.txt`(`0c3547d1…2c03`).
  - v1의 명료한 구조(스키마·t 정의·소유 시스템 판단·인자 규칙)를 qRef·시간 계약에 맞게 복구했다.
  - TC 송수신 실패 로그 대 WM 정본 소유권 규칙을 유지했다.
  - qRef 설명을 host와 모델 책임으로 나눴다. 추출 문법은 Gateway 몫이고, 모델에는 []→null, 목록 ID만, 원문·q 키 금지를 남겼다.
  - 시간: 초·소수초·offset이 적힌 절대 시각은 원문 그대로, 그 밖은 Context 기준 `YYYY-MM-DDTHH:mm`.
  - Path A는 256으로 정정하고 steps/t 규칙을 양방향으로 했다. 출력 값 예시는 두지 않았다(복사 오염 방지).
  - 특정 문항 문구·정답 예시는 넣지 않았다.
- **c 입력 audit:** `eval/f0/v3/input-audit-v3-c.json`(`29b3c8f4…91c0`).
  - Java `f0 input-audit-render`(레포 밖 `%TEMP%\aigw-p03v3\render-c.jsonl`, 보존)로 렌더하고, `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`·template `40c21f34…b541`)로 셌다.
  - 결과: `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`. 최대 A 1580/1619, B 1372/1411, 경계 2985/3024(≤3072), usage 1338, token 2793.
- **사전등록 초안** `eval/f0/PREREG-v3-diag-{a,b,c}.md`: `f0-prereg-v3`, `purpose=DIAGNOSTIC`, R=1, 같은 v2 골든 22·경계 9·fixture·arg-limits·예산.

| 등록 | 프롬프트 | responseFormat | 입력 audit | harnessDigest |
|---|---|---|---|---|
| a | v2 원문(160 문구 보존) | NONE | v2 `c30c3b60…8ce0` | `59912312…43ca` |
| b | v2 원문 | JSON_SCHEMA_V1 | v2 | `59912312…43ca` |
| c | v3 | JSON_SCHEMA_V1 | v3-c | `2da9e242…9b44` |

- 15개 입력 SHA 불일치 0이다. Java `f0 prereg-check`가 `localMismatches=[]` 뒤 `PREREG_NOT_COMMITTED`(exit 2, endpoint 호출 0)를 냈고, Python 독립 재계산 digest도 세 파일 모두 일치했다.
- 권장 실행 순서: a → b → c, 각 등록마다 별도 runId(`<id>-a|b|c`)로 B·A·boundary-gen·report(`--out` 없음).
  - b에서 서버가 거부하면(exit 5) c는 실행하지 않고 보고한다.
  - b의 `qRef.kind@candidateCount=0` 분포에 ID_OUT_OF_RANGE가 남으면 스키마가 실제로 적용되지 않은 것이다(서버가 조용히 무시). 이 경우 지원 확정을 주장하지 않는다.
  - 선택 권장: b가 효과 없음으로 나오면 프롬프트 단독 효과를 보려고 v3 프롬프트+NONE 대조(d)를 별도 승인으로 추가한다. 이번에는 준비하지 않았다.
- 표현 변경·미지 ID·소유권 쌍·Context 복사 방지 회귀는 stub 시험 자료로만 두었다. 골든 기대·분모는 바꾸지 않았다.

#### 4) SPEC·Plan 정합 개정

- **SPEC §14.5(1203행, 줄 안에서만 수정):** "서버 구조화 출력은 F2 확인"을 F0(P03) 상주 모델 조건부 검증 게이트로 앞당겼다.
  - 근거: root 확인 `ollama version is 0.31.1`, 같은 tag 공식 `openai/openai.go`가 `json_schema`를 `format`으로 전달.
  - 정한 것: 스키마 표현 범위, 의미 판정 미이관, 자동 대체 0, 진단은 게이트 증거 아님, 채택은 정식 사전등록 판정, 운영 적용은 F2 확정.
  - 지원 확정·PASS로 쓰지 않았다.
- **SPEC §27.5:** 변경 이력 1행을 추가했다. 1~1208행 줄 수는 그대로이고 SCHEMA ref 1207·1208은 같은 근거 줄이다.
- **새 SPEC SHA-256 `60a1e2dae84976c245822a5ae3541b4323c231dc2f002d98605a37ce95c2ecf8`**(2,603줄, CR 0). `measurementAffecting=true` 권장(요청 body·측정 하네스 변경).
- **Plan:**
  - P03에 v3 근본 보완 흐름 1항과 진행 기록 1줄을 추가했다.
  - P03 v2 실측 기록 줄(작업 전 397행)의 helper gate 출력 경로를 현재 위치 `eval/f0/results/gate-reports/20261102T003840Z.md`로 정정했다(이동 사실 명기, 과거 증거 불변).
  - P21 추가 기록 문구를 "운영 경로 적용 여부 확정"으로 맞췄다.
  - §12 권위표·phase 구성·수치는 바꾸지 않았다. 새 Plan SHA `2812106c99e618358e93bcc0ac35fe2b3121b9e1906b4810987a2159bf4842b8`.

#### 5) 실행 명령과 실제 결과

- Gradle은 이 세션 Git Bash에서 `AIGW_SPEC_FILE`=실제 SPEC, `DEBUG`=빈 값으로 실행했다. Python은 venv `-I -B`로 실행했다.

| 명령 | exit | 결과 |
|---|---|---|
| `:gateway-eval:test --tests f0.* --tests GateReportOutputTest`(SPEC 수정 전) | 0 | 신규 `F0StructuredOutputDiagnosticTest` 6·`GateReportOutputTest` 1 포함 PASS |
| 변이 확인: `Attempts.responseFormat=null`로 임시 변경 후 신규 시험 | 1 | 스키마·fallback 시험 2건 FAILED 확인 → 원본 복원(`cmp` 일치) |
| `f0 input-audit-render`(c) / `count_input_audit_v2.py` | 0 / 0 | `F0_INPUT_AUDIT_RENDERED 651` / `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0` |
| `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-{a,b,c}.md` | Java 2 ×3 | `localMismatches=[]`, `PREREG_NOT_COMMITTED` |
| 무필터 `:gateway-eval:test --console=plain`(SPEC 수정 전, 기준선 seq3 일치) | 0 | `BUILD SUCCESSFUL in 3m 12s`. XML 독립 집계 **169/169 PASS·37클래스·failures/errors/skipped 0** |
| 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED` |
| `python -I -B tests/test_count_tokens_qref_v2.py` / `test_count_input_audit_v2.py` | 0 / 0 | `Ran 4 tests … OK` / `Ran 3 tests … OK` |
| SPEC 수정 후 `:gateway-eval:run --args="spec-guard"` | 2 | `SPEC_BASELINE_MISMATCH`(기준선 seq4 전 정상 차단, 우회 0) |

- 최신 시험 신원(SPEC 수정 전): `runId=09894039-72e2-4852-9838-3d4f67d1e76b`, `inputFingerprint=25e0f2692d2ab7260eb0c5a5f6928943914fefde240f8f8ea7387fbe2052cf5d`, `contentSha256=a1b9607f601fbcd670ad0e1bca21dccc3bfae223f2e4fe35d1a7d73109a45b88`, `otrSha256=4f4f85e52d116a4440806079af5df8781343517bc6b91f6aac92f9e3704eaf13`, `specSha=0f00921b…1e28`, `records=[]`. 이 PASS는 stub·로컬 시험이며 실제 모델 F0 합격이 아니다.
- SPEC 바이트가 바뀐 뒤에는 위 시험 PASS를 재사용하지 않는다. root가 seq4를 추가한 뒤 whole test와 verify를 다시 실행해야 한다.

#### 6) 보존 확인

- `git diff HEAD`의 `eval/f0/PREREG.md`·`PREREG-v2.md`·`eval/f0/v2/**`·`eval/f0/prompts/**`·`eval/golden/**` 변경 0.
- v2 원시 bundle 14파일 SHA 재계산 `1c6d04175ec04a17ed153453609c43361fea536ce9c0eba73e228f62f01df774` 일치, 파생 gate 보고서 `a66b2617…ed24` 불변.
- `evidence/F0/**`·`eval/f0/chat-template/**`·`eval/f0/results/**` 기존 파일 수정 0. 새 결과 디렉터리 0, 새 `__pycache__` 0.
- 기존 dirty 파일(`p03-run-20261029T234506Z.md` 등)은 손대지 않았다. 로컬 commit은 C1~C4 그대로이며 push 0.

#### 7) root 인계·남은 승인

1. **root:** 새 SPEC `60a1e2da…ecf8`를 기준선 seq4(`changedSections` §14.5·§27.5, `measurementAffecting=true`)로 추가한다. 그 뒤 무필터 `:gateway-eval:test`와 직후 별도 `test-evidence verify`를 다시 실행한다. 그 전에는 spec-guard 차단으로 시험이 실패한다.
2. **사용자 승인 필요 — C5:** 등록 대상·하네스 코드.
   - main 수정 9: `EvalCli`, `GateReportWriter`, f0 `{F0Measurements, F0ReportWriter, F0RunnerCli, MeasurementSession, OllamaChatClient, Prereg, QRefContext}`
   - main 신규 2: `ResponseSchema`, `AttemptDiagnostics`
   - 시험 수정 1·신규 2: `OllamaStub`, `F0StructuredOutputDiagnosticTest`, `GateReportOutputTest`
   - `eval/f0/v3/**`, `PREREG-v3-diag-{a,b,c}.md`(`registeredCommit=null`), 이 절, 기준선 seq4(root)
   - **C6:** 세 PREREG의 `registeredCommit`=C5와 `registrationStatus=REGISTERED`만 기록한다.
   - C5 직전 입력·digest를 다시 대조한다.
3. **진단 실측(승인 후):** 터널 → a/b/c 각각 `prereg-check`·preflight·B·A·boundary-gen(R=1)·report(`--out` 없음). 모델 로드·관리 API·num_ctx·서비스 변경 0.
4. **확인할 지원 실측 항목:**
   - 서버가 `response_format`을 받는지(HTTP 오류면 exit 5)
   - 실제로 적용되는지(b의 후보 0 qRef 분포, 키·enum 위반 0 여부)
   - 입력 계수 증가(usage 대 audit 큼)
   - `["string","null"]`·`enum`·`{"type":"null"}` 문법의 서버 수용
5. **정식 측정:** 진단 결과로 채택안을 정한 뒤 별도 `purpose=GATE` 등록(R=5)·새 회차로 기존 F0 판정과 `gate --out eval/f0/results/gate-reports/<runId>.md`를 실행한다. G-F0-TARGET(b)·정확도 목표는 기준선 뒤 F2 전에 SPEC 소유자가 확정한다(이번에 새 임계 0).
6. **미실행:** 실제 모델·HTTP·SSH 호출, GGUF 계수, 진단·정식 측정, C5/C6, push·publish·보호 변수·Jetson·k8s 변경, P04.

#### 8) root 최종 검토 보완 (2026-11-02, 별도 독립 Claude Code 세션 — claude-opus-5-5, effort medium)

- 위 1)~7)은 그대로 보존한다. 이 절은 root가 찾은 수정 요구 A~F를 반영한 기록이다.
- **결론: 로컬 구현·시험·등록 초안 대조만 완료. 실제 모델·SSH·k8s 호출 0, commit·push 0(C5/C6 미허용), 진단·회귀·정식 실측 0. F0 FAIL 유지, P03 미완료, P04 미착수.**
- 착수 시 SPEC `60a1e2da…ecf8`, Plan `2812106c…42b8`로 전달값과 같았다. `docs/gates/spec-baseline.md`·`spec-approvals/`는 편집하지 않았다(root가 중간 `SPEC_FROZEN` 출력을 읽고 seq4를 추가).

##### A. SPEC·Plan 최종 정합

- **SPEC §14.5(줄 안에서만 수정, 1~1208행 줄 수 불변 — SCHEMA ref 1203·1207·1208 같은 근거 줄):**
  - 1203행: v2 준비의 "GGUF·완결 생성 아직 미측정" 문구를 등록 전 역사 시점으로 고쳤다. v2 실측 회차 `20261102T003840Z`(§12.8)는 경계 공식·GGUF 계수 일치·종료 여유 충족, 완결 생성 oracle 불통과, F0 FAIL이며 PASS가 아니라고 적었다. 형식 `SPEC_RECORD` 블록은 만들지 않았다.
  - 1203행: 구조화 출력 채택은 정식 GATE 등록 R=5 회차로만 판정하고, 진단·추가 개발 회귀 등록의 문항·결과는 정식 분모·판정 밖이라고 적었다.
  - 1207행: 질문에 명시된 초·소수초·offset 절대 시각은 LLM wire에서 표기 그대로 보존한다(F0 디코드 원문 정확 일치 기준). Context 시간대 변환·같은 instant·나노초 보존은 wire 검증 뒤 Gateway 정규화다. 유효 형식·범위·Source 지원·기대값은 바꾸지 않았다.
  - 1211행: "F0 v2 재측정 전"을 "F0 정식 재측정 PASS 전(v2 회차 FAIL)"으로 고쳤다.
- **SPEC §27.5:** 변경 이력 1행을 추가했다.
- **새 SPEC SHA-256 `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`**(2,604줄, 486,552바이트, CR 0, BOM 없음). changedSections §14.5·§27.5. root가 seq4(`measurementAffecting=true`, affectedLeaves ALL)로 추가한 값과 일치한다.
- **Plan(새 SHA `43cb0cc718294576a8dd389883b0c23b748c4b9ce60ea7023ddba06ab72b451f`):**
  - §0 "Plan 상태"·"실행·증거 상태" 행 맨 앞에 2026-11-02 최신 상태를 적었다. 옛 2026-10-30 문구는 "이하 이력"으로 보존했다.
  - §0 R6: ⑥ 로컬 재판정·재측정·`gate`의 선행은 ③ 사슬 항목과 실제 정본 파일의 G-SPEC-HASH (a)뿐이다. ④ publish·⑤ protected 변수는 (b) 원격 공급 조건으로, 그 공급을 소비하는 CI job·`ci-evidence verify`·배포 수용 전에만 필요하다.
  - P03 9단계와 §6 F0 exit 행도 같은 기준으로 정정했다. 근거: 실제 코드 `SpecGuard`는 비 CI(`CI`≠true)에서 실제 파일 SHA와 기준선 마지막 항목만 대조한다. `F0ReportWriter.report/rejudge/specRecordCheck`·`MeasurementSession`도 같은 `SpecGuard.check`를 쓰며 publish·`AIGW_APPROVED_SPEC_SHA`를 읽지 않는다. 기존 §5 G-SPEC-HASH (a)/(b) 분리를 따르는 문구 정합일 뿐, 업무·보안 정책 변경이 아니다. 원격 공급 게이트는 PASS 처리하지 않았고 publish·설정 변경은 0이다.
  - P03 v3 흐름에 ⑦(아래 B~E)을 추가하고 진행 기록 1줄을 추가했다.

##### B~D. 프롬프트·GATE R5·경계 errorCodes

- **B. c 프롬프트(`classify-v3`·`plan-v3` 같은 줄):** "큰따옴표 안의 검색어는 EQP ID 가 아니다"를 다음으로 바꿨다: "설비로 가리킨 ID 면 큰따옴표 안에 있어도 eqp 다. 코드·클래스·오류 이름처럼 검색어로 쓰인 문구는 eqp 가 아니다."
  - 특정 문항·정답·골든 문장은 넣지 않았다.
  - 새 SHA는 classify `2772aa56…5143`, plan `43da76c3…c982`이다.
  - 예산 상한 3072는 그대로다.
- **C. `Prereg`:** v3 `purpose=GATE`는 `parameters.repeats == 5`(`GATE_REPEATS`)만 허용하고 그 밖은 `PREREG_INVALID`다. 실제 명령 `--repeats`가 등록과 다르면 기존 `ARGUMENT_NOT_REGISTERED`로 endpoint 호출 전에 거부한다. v1/v2(R3/R5/R7 기존 시험)와 DIAGNOSTIC(R≥1, 시험 R2·초안 R1) 규칙은 그대로다. 시험의 v3 GATE fixture는 R5로 맞췄고, 시험 `run` 헬퍼는 등록 회차 수를 그대로 쓴다.
- **D. `F0Measurements.boundaryGen`:** 각 시도 행에 `errorCodes`(골든 행과 같은 고정 오류 코드 배열, 원문 0)를 추가했다. 진단 보고서는 이미 `errorCodes`를 집계하므로 경계 행도 그대로 집계된다. 판정(`F0Judgement`)·분모는 그대로다.

##### E. 등록 초안 5개(모두 `f0-prereg-v3`, `registeredCommit=null`, UNCOMMITTED)

- **c 입력 audit 재생성:** `eval/f0/v3/input-audit-v3-c.json`, `b56e8a11…5d43`, 651행, OVER_BUDGET 0.
  - 최대값: A 1610/1649, B 1402/1441, 경계 3015/3054(≤3072), usage 1368, token 2793.
  - 이전 파일(`29b3c8f4…91c0`)은 삭제하지 않고 레포 밖 `%TEMP%\aigw-p03v3s2\input-audit-v3-c.prev.json`으로 옮겼다.
- **회귀 골든 `eval/f0/v3/golden-regression-v3.jsonl`(`ef1803d6…7337`, 30행):**
  - v2 골든 22행을 바이트 그대로 두고(접두 `cmp` 일치, 시험이 재확인) 신규 8행 R01~R08을 붙였다.
  - 신규 8행: 인용된 정상 EQP, 설비 대 코드명 구분, Context 전용 ID·시간 복사 금지, TC 송수신 실패(INC) 대 WM 정본 자료(UNS) 대조 쌍, 표현 바뀐 분 단위 구간, 인용 후보 2개 중 의도상 두 번째, fixture 기존 64 코드포인트 EQP.
  - 실제 모델 출력을 보기 전에 고정했다. 레포 밖 `%TEMP%\aigw-p03v3s2\make_regression.py`로 생성했으며 모델 호출은 0이다.
  - GoldenSet 범용 리팩터는 0이다. 초·소수초·offset은 기존 경계 NORMAL-PRECISE와 ArgLimits 시험이 계속 맡는다.
- **회귀 audit `eval/f0/v3/input-audit-v3-regression.json`(`f2594893…8e24`):** 843행(= 651 + 8문항 × 2경로 × 12시도), OVER_BUDGET 0, 최대값은 c와 같다.
- 두 audit 모두 Java `f0 input-audit-render`(레포 밖 `%TEMP%\aigw-p03v3s2\render-{c,reg}.jsonl`)와 `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541` — `chat-renderers-v1.json`·PREREG-v2 등록값)로 만들었다.

| 등록 | purpose / R | 골든 | 프롬프트 | responseFormat | audit | runtimeCaptureDir | harnessDigest |
|---|---|---|---|---|---|---|---|
| `PREREG-v3-diag-a.md` | DIAGNOSTIC / 1 | v2 22 | v2 원문 | NONE | v2 `c30c3b60…8ce0` | `v3-diag-a` | `ef72cc42…a7b8` |
| `PREREG-v3-diag-b.md` | DIAGNOSTIC / 1 | v2 22 | v2 원문 | JSON_SCHEMA_V1 | v2 | `v3-diag-b` | `ef72cc42…a7b8` |
| `PREREG-v3-diag-c.md` | DIAGNOSTIC / 1 | v2 22 | v3(정정) | JSON_SCHEMA_V1 | v3-c `b56e8a11…5d43` | `v3-diag-c` | `55d61c55…f408` |
| `PREREG-v3-regression.md` | DIAGNOSTIC / 1 | 회귀 30 | v3(정정) | JSON_SCHEMA_V1 | v3-regression `f2594893…8e24` | `v3-regression` | `5a8f0f0f…daaa` |
| `PREREG-v3-gate.md` | **GATE / 5** | v2 22 | v3(정정) | JSON_SCHEMA_V1 | v3-c | `v3-gate` | `55d61c55…f408` |

- 경계는 모두 v2 9케이스(`eval/f0/v2/output-boundary-cases.jsonl`)다. 전체 digest:
  - a/b `ef72cc4297f2dcac06db23a38158c96ad1f8a6f3d61e439f3d488bfae5bfa7b8`
  - c·gate `55d61c55333b7ecc210d7d45fc5fdd1e7e213c3847511b69343258283b6bf408`
  - 회귀 `5a8f0f0f9d85339df9d3c9d8d6246af7453fe20fbd76765f9f6055f280f7daaa`
- A/B/C 비교 분모(원래 22 + 경계 9, R1)는 바꾸지 않았다. gate는 c와 입력·예산·스키마가 같고 목적·회차·캡처 디렉터리만 다르다(시험 확인).
- **정식 착수 조건(`PREREG-v3-gate.md` 1절):**
  1. 진단 a·b·c와 회귀가 완결되고 서버 거부 0
  2. c 진단에서 기존 oracle로 v2 실패 원인이 해소됨: `QREF_INVALID` 0·후보 0 `ID_OUT_OF_RANGE` 0, G08 A·B `t=INC`, 경계 9케이스 통과
  3. 회귀 검토
  4. C5/C6 뒤 `PREREG_VERIFIED`
  - 하나라도 아니면 blind 정식 측정은 0이고, 보완은 새 등록 파일로 한다. 정확도 새 절대 임계는 두지 않았다.
  - 이 세션 작성 뒤 다른 작업자가 3·4항 문구를 고쳤다(아래 "동시 편집" 참조).
- **실행 명령(생략 없는 정확한 명령은 각 PREREG 2절, PowerShell, cwd 레포 루트, 각 명령 별도·exit 0 확인):**
  - 공통 endpoint `http://127.0.0.1:11434`, fixture `eval/golden/tc-registry-fixture-v2.json`, cases `eval/f0/v2/output-boundary-cases.jsonl`, `$env:AIGW_SPEC_FILE`=정본 SPEC.
  - 진단 묶음: `$runId`를 한 번 정하고 a → b → c → 회귀 순서다. 각 등록은 `prereg-check` → `preflight --out eval/f0/results/<회차>/preflight.json` → `chat-template --model qwen3:4b-instruct --out eval/f0/chat-template/<캡처>` → `run --path B`·`run --path A`(`--repeats 1 --run-id <회차> --out eval/f0/results/<회차>/{B,A}`) → `boundary-gen --repeats 1 --out eval/f0/results/<회차>/boundary` → 터널 종료 → `report --run-id <회차> --results eval/f0/results/<회차>`(`--out` 없음)를 실행한다.
  - 회차는 a `$runId-a`, b `$runId-b`, c `$runId-c`, 회귀 `$runId-regression`이다. 각 명령에 `--prereg eval/f0/PREREG-v3-<id>.md`를 붙이고 `$env:AIGW_PARENT_RUN_ID`=그 회차로 둔다.
  - 프롬프트: a·b는 `eval/f0/v2/prompts/{classify,plan}-v2.txt`, c·회귀는 `eval/f0/v3/prompts/{classify,plan}-v3.txt`다. 골든은 회귀만 `eval/f0/v3/golden-regression-v3.jsonl`이고 나머지는 `eval/golden/tc-f0-v2.jsonl`이다.
  - b가 exit 5면 c 이후는 중단한다.
  - 정식(조건 충족 후, 새 `$runId`=부모 회차, `--prereg eval/f0/PREREG-v3-gate.md`): 환경 검사 → 터널 → `prereg-check` → `preflight` → `chat-template --out eval/f0/chat-template/v3-gate` → `run --path B/A --repeats 5`(v3 프롬프트·v2 골든) → `count_tokens_qref_v2.py … --out eval\f0\results\$runId\official-count.json` → `gguf-count` → `boundary-gen --repeats 5` → `usage-probe --messages eval/f0/v2/usage-probe-input-v2.jsonl` → `token-golden --messages eval/f0/v2/token-golden-input-v2.jsonl` → 터널 종료 → `report --run-id $runId --results eval/f0/results/$runId --out evidence/F0` → `gate --stage F0 --parent-run-id $runId --spec $env:AIGW_SPEC_FILE --out eval/f0/results/gate-reports/$runId.md`
  - 새 audit 문서·임시 호출 스크립트는 0이다.

##### F. 실행 결과(Git Bash, `AIGW_SPEC_FILE`=실제 SPEC, `DEBUG`=빈 값, Python venv `-I -B`)

| 명령 | exit | 결과 |
|---|---|---|
| `f0 input-audit-render`(c·회귀) / `count_input_audit_v2.py` ×2 | 0 / 0 | `F0_INPUT_AUDIT_RENDERED 651`·`843` / `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`·`843 OVER_BUDGET 0` |
| 관련 시험 `--tests F0StructuredOutputDiagnosticTest --tests BoundaryGenJudgeTest --tests F0VersionedInputsTest`(seq4 뒤) | 0 | 16/16 PASS(7·3·6) |
| `f0 prereg-check --prereg eval/f0/PREREG-v3-{diag-a,diag-b,diag-c,regression,gate}.md` | Gradle 1(Java 2) ×5 | 모두 `localMismatches=[]`, `currentHarnessDigest`=등록값, `PREREG_NOT_COMMITTED`(미커밋 거부, endpoint 호출 0). 레포 밖 Python 독립 재계산(`%TEMP%\aigw-p03v3s2\digest.py`)도 inputs 불일치 0·digest 일치(최종 시험 뒤 재확인) |
| 1차 무필터 `:gateway-eval:test`(12:10) / 직후 verify | 0 / 0 | 173/173·38클래스 / `TEST_EVIDENCE_VERIFIED` — 아래 동시 편집 전 트리 |
| 2차 무필터 시험 / verify | 0 / 1 | 174/174 PASS이나 실행 중 외부 편집(`PREREG-v3-gate.md` 12:14:59)으로 `STALE_TEST_EVIDENCE` — 정상 차단, 재사용 0 |
| **최종** 무필터 `:gateway-eval:test --console=plain`(90초 정착 확인 뒤, 실행 중 레포 파일 변경 0) | 0 | `BUILD SUCCESSFUL in 3m 18s`. XML 독립 집계 **174/174 PASS·38클래스·failures/errors/skipped 0** |
| 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED` |

- **최종 시험 신원:** `runId=c38a7ab0-6925-4b9d-b319-39794d0f265a`, `inputFingerprint=5db577841870b4a69bfaafef2945b3673e5284e92966cc46cccc2561b694e771`, `contentSha256=e8cdc9089f08eb6f28dffe7332c52cb80fcc990ea53ca148d625fc5ae413ca24`, `otrSha256=086ea8b5e9a3146451452285f74e8cbccdcb2213e4ef21f21b8a6d851b1e1508`, `specSha=34fd6223…66be`(seq4), `executedTestCount=174`, `records=[]`. stub·로컬 시험이며 실제 모델 F0 합격이 아니다.
- **신규·변경 시험(169 → 174):**
  - `F0StructuredOutputDiagnosticTest`:
    - GATE R5 강제: R1·R2·R7 변조 `PREREG_INVALID`, `--repeats 2` 명령은 `ARGUMENT_NOT_REGISTERED`·chat 0, R5 실행 110행
    - DIAGNOSTIC R1·R2 유효
    - 신규 경계 시험: 후보 0 케이스 범위 밖 qRef → 시도 행 `errorCodes=[QREF_INVALID,QREF_INVALID]`, 이후 t·eqp·from·to 진단 MATCH, 질문·eqp 원문 저장 0
  - `BoundaryGenJudgeTest`: 키 집합에 `errorCodes`, 정상 `[]`, 잘림 재시도 `[FINISH_LENGTH]`, 무효 2건
  - 신규 `F0V3RegistrationDraftTest`(실제 레포 파일, Test 선언 입력 `eval/**`로 fingerprint 결속):
    - 이 세션 3건: 5개 초안의 목적·R·responseFormat·로컬 일치·캡처 디렉터리 고유, a/b/c/gate v2 골든·경계와 gate=c 입력·예산, 회귀=c와 골든·audit만 다름·접두 22행 바이트 일치·30문항, PREREG-v2 등록 입력 바이트 불변, 각 audit이 실제 하네스 호출 전부를 profile 안에서 덮음(651/843행)
    - 외부 추가 1건: 등록 수명 메타데이터 UNCOMMITTED/REGISTERED 일관성
- 추가 변이 실험은 하지 않았다. 신규 단언이 기존 코드(R≥1 허용, 경계 행 키 집합)에서 실패하는 형태임을 코드 대조로 확인했다.

##### 동시 편집(이 세션 밖 작업자, 되돌리지 않음)

- `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java`(최종 `14fe6945…ee1b`): 다른 작업자가 `registeredCommit=null` 단정을 수명 일관성 검사(UNCOMMITTED=null, REGISTERED=40hex)로 바꿨고, 시험 1건(`lifecycleCheckAccepts…`)과 helper를 추가했다. C6 뒤에도 시험이 유지되도록 한 변경으로 보인다.
- `eval/f0/PREREG-v3-gate.md`(최종 `11318d36…12eb`): 다른 작업자가 1절 3·4항을 고쳤다.
  - 3항: 조건 1·2 충족과 새 업무 결정이 없으면 root 검토로 진행하며 "정식 측정 자체의 별도 사용자 재승인은 요구하지 않음".
  - 4항: C5/C6는 아직 승인이 필요함.
  - 기계 판독 블록·digest는 그대로이고 로컬 일치다. 승인 범위 문구는 이 세션이 결정하지 않았으며 root·사용자 확인 대상이다.

##### 보존 확인

- `git diff HEAD`에서 `eval/f0/PREREG.md`·`PREREG-v2.md`·`eval/f0/v2/**`·`eval/f0/prompts/**`·`eval/golden/**`·`eval/py/**` 변경은 0이다. `docs/gates/**` 변경은 root의 seq4·`spec-approvals/4.md`뿐이다.
- 이전 세션 종료 시점 이후 `evidence/**`·`eval/f0/results/**`·`eval/f0/chat-template/**`·`eval/py/**`의 새·수정 파일은 0이다(`find -newer`). 새 `__pycache__`도 0이다.
- 기존 dirty(`p03-run-20261029T234506Z.md` 등)는 손대지 않았다. 로컬 commit은 C1~C4 그대로이며 push는 0이다.

##### 이번 세션 변경 파일

- 정본(exec 레포): `nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`(§14.5·§27.5), `ai-rag-gateway-architecture-plan.md`(§0 상태·R6, §6 F0 exit, P03 v3 흐름·9단계·진행 기록)
- gateway main: `f0/Prereg.java`, `f0/F0Measurements.java`
- gateway test: `f0/F0StructuredOutputDiagnosticTest.java`, `f0/BoundaryGenJudgeTest.java`, `f0/F0VersionedInputsTest.java`, 신규 `f0/F0V3RegistrationDraftTest.java`(이후 외부 편집)
- 데이터: `eval/f0/v3/prompts/{classify,plan}-v3.txt`, `eval/f0/v3/input-audit-v3-c.json`(재생성), 신규 `eval/f0/v3/golden-regression-v3.jsonl`·`eval/f0/v3/input-audit-v3-regression.json`
- 등록: `eval/f0/PREREG-v3-diag-{a,b,c}.md`(2절 정확한 명령·digest, c 입력 SHA), 신규 `eval/f0/PREREG-v3-regression.md`·`eval/f0/PREREG-v3-gate.md`(이후 외부 편집)
- 문서: 이 절

##### C5/C6 인계(사용자 승인 필요, 이번에 실행 0)

- **C5 대상(선별 stage):**
  - main 수정 9: `EvalCli`, `report/GateReportWriter`, f0 `{F0Measurements, F0ReportWriter, F0RunnerCli, MeasurementSession, OllamaChatClient, Prereg, QRefContext}`
  - main 신규 2: `ResponseSchema`, `AttemptDiagnostics`
  - test 수정 3: `OllamaStub`, `BoundaryGenJudgeTest`, `F0VersionedInputsTest`
  - test 신규 3: `F0StructuredOutputDiagnosticTest`, `GateReportOutputTest`, `F0V3RegistrationDraftTest`
  - `eval/f0/v3/**` 5파일
  - `eval/f0/PREREG-v3-{diag-a,diag-b,diag-c,regression,gate}.md`(null 상태)
  - 이 Dev report
  - root의 `docs/gates/spec-baseline.md`(seq4)·`spec-approvals/4.md`
- **C5 제외:** `docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/**`, `evidence/F0/**`, `__pycache__/`
- `Prereg.verify`는 `eval/f0` 전체 worktree clean(results·chat-template 제외)을 요구하므로 `eval/f0` 아래 새 파일은 모두 C5에 들어가야 한다.
- **C6:** 위 5개 PREREG의 `registeredCommit`=C5 SHA와 `registrationStatus=REGISTERED`만 기록한다.
- C5 직전 `prereg-check`로 입력·digest를 다시 대조한다.

##### root 독립 최종 확인 (2026-11-02)

- 위 "동시 편집" 2건은 root가 별도의 독립 Claude Code Opus 5.5 medium 세션에 범위를 나눠 위임한 작업이다. 사전등록 수명 시험 보완과 불필요한 정식 측정 재승인 조건 제거이며, 사용자 작업을 덮어쓴 변경이나 새 업무 정책 결정이 아니다. 현재 사용자의 P03 실행 승인 범위에서 진단·회귀를 검토한 뒤 조건이 충족되면 정식 측정을 계속하며, 별도로 제한된 C5/C6 commit 허용은 아직 받지 않았다.
- root가 실제 호출 경로를 대조했다: 요청별 스키마는 질문의 현재 참조 목록과 구조 제약만 포함하고 정답·설비 목록을 주입하지 않는다. Gateway의 엄격 판정·재시도 1회는 유지하며, 독립 진단은 실행 인자를 바꾸지 않는다. 진단 등록은 F0 증거 생성 경로에서 거부되고 v3 GATE는 R=5만 허용된다.
- root가 XML 38개를 직접 합산해 174건·failures/errors/skipped 0을 확인했다. 정제 요약도 passed=174·failed/skipped/aborted=0·records=[]이며 위 최종 시험 runId·fingerprint·SPEC SHA와 일치한다.
- root가 별도 프로세스로 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain`을 실행해 exit 0·`TEST_EVIDENCE_VERIFIED`·`BUILD SUCCESSFUL in 3s`를 확인했다. 이는 로컬/stub 시험 증거이며 실제 모델 F0 PASS가 아니다.
- root Node 재계산: 등록 초안 5개 모두 inputs SHA 불일치 0·harnessDigest 일치. c/gate audit 651행, 개발 회귀 843행, 최대 입력 3054≤3072·입력+출력≤4096 위반 0. 회귀 파일의 앞 22행은 원래 v2 골든의 바이트 접두부와 일치하고 총 30행이다.
- 보호 스냅샷 75파일 중 변경은 root가 seq4를 추가한 baseline 1파일뿐이다. 기존 baseline 항목 3개는 git HEAD의 원 항목과 값·순서가 같다. 기존 v2 원시 결과는 14파일·SHA `1c6d04175ec04a17ed153453609c43361fea536ce9c0eba73e228f62f01df774`로 그대로다. `git diff --check` exit 0, git commit 수 4(C1~C4) 유지.
- C5 검토 대상은 위 선별 목록 30파일이며, C6 대상은 신규 사전등록 5문서의 등록 수명 메타데이터와 그 설명이다. 기존 v1/v2 증거·등록 입력·untracked 이력은 stage하지 않는다.
- 실제 v3 모델 호출·진단·회귀·정식 측정, C5/C6, push·publish·보호 변수·서비스 변경은 미실행이다. P03 미완료·기존 F0 FAIL·P04 미착수 상태를 유지한다.

### 12.10 P03 v3 사전등록 C5/C6·진단 a/b/c 실행 — c 중단 (2026-11-02, 진단 실행: 별도 native Claude Code 세션 claude-opus-5-5 effort medium / 이 절 문서 정리: 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 최종 문서 대조 전)

- §12.1~§12.9와 v1 `20261029T234506Z`·v2 `20261102T003840Z` 회차의 판정·증거는 이력으로 보존한다. 이 절은 문서 정리만이며 코드·시험·프롬프트·사전등록·SPEC·기준선·승인 문서 변경, 추가 commit·push, SSH/HTTP·모델 로드·서비스 변경은 0이다.
- **결론: C5/C6 등록 완료, 진단 a/b 완료(R1 complete), 진단 c는 B만 완료·A 시간 초과 뒤 장비 재부팅·상주 모델 0으로 중단. 개발 회귀·정식 GATE R5는 미실행. 이번 진단 결과로 만든 F0 leaf·GATE·SPR은 0이며 새 정식 판정이 없다. 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.**

#### 1) 사전등록 C5/C6

- **C5 `05c5facab7daa5d9d46bad2caa5ec26e13760e77`**: §12.9 인계 목록의 정확히 30파일(v3 진단·회귀·정식 등록 대상, 하네스, 관련 시험, 이 Dev report, root seq4 기준선·`spec-approvals/4.md`).
- **C6 `01731891b58381ca9d873d2b931a2c482ffb8f5e`**: `eval/f0/PREREG-v3-{diag-a,diag-b,diag-c,regression,gate}.md` 5파일만. 등록 메타데이터(`registeredCommit`=C5, `registrationStatus=REGISTERED`)와 사람이 읽는 상태 문구만 바뀌었다. root도 기계 판독 JSON이 이 2키 외 동일함을 독립 확인했다.
- C6 뒤 무필터 `:gateway-eval:test` 174/174 PASS·38클래스·failed/errors/skipped 0, 직후 `test-evidence verify` 통과. root가 XML 합계와 별도 verify를 모두 확인했고, 측정 후 2026-11-02T05:23 무렵 root 재verify도 exit 0 `TEST_EVIDENCE_VERIFIED`다. 이는 로컬/stub 시험 증거이며 실제 모델 F0 PASS가 아니다.

#### 2) 진단 실행 조건

- native 진단 세션: model `claude-opus-5-5`, effort medium. run 묶음 `20261102T045749Z`(`-a`·`-b`·`-c`), 측정 SPEC SHA `34fd6223…66be`, 등록 commit C5.
- 결과: `eval/f0/results/20261102T045749Z-{a,b,c}/`(각 `diagnostic-report.md`), template capture: `eval/f0/chat-template/v3-diag-{a,b,c}/`(각 template 4040 B). 원문 질문·응답은 저장되지 않았다(정제 필드·메트릭만). 기존 v1/v2 결과는 바뀌지 않았다.
- 착수 preflight(a·b·c 모두): qwen3:4b-instruct(`0edcdef3…8ba0`, ctx 4096)·bge-m3:latest(`79076464…6bab`, ctx 4096) 상주 2.

| run | 사전등록 | responseFormat | harnessDigest | A | B | boundary 9 |
|---|---|---|---|---|---|---|
| `-a` | `PREREG-v3-diag-a.md` | `NONE` | `ef72cc42…a7b8` | 22/22 complete=true | 22/22 complete=true | complete=true |
| `-b` | `PREREG-v3-diag-b.md` | `JSON_SCHEMA_V1` | `ef72cc42…a7b8` | 22/22 complete=true | 22/22 complete=true | complete=true |
| `-c` | `PREREG-v3-diag-c.md` | `JSON_SCHEMA_V1` | `55d61c55…f408` | G01·G02 각 `TIMEOUT`(120020/120008 ms, `ENDPOINT_TIMEOUT`), complete=false, abortReason `ENDPOINT_UNREACHABLE` | 22/22 complete=true | 미실행(preflight 실패, 추론 표본·출력 0) |

- c는 A 실패 뒤에도 불완전 `diagnostic-report.md`가 생성됐다. 이 c 보고서는 **부분 보고서**이며 AC/F0 증거 0이다.

#### 3) 진단 결과(R1, 분모 22 — 진단이며 정식 정확도 합격 아님)

이 문서 정리 세션이 각 `diagnostic-report.md`와 `f0-results.json`·`boundary-results.json`을 읽어 아래 수치를 다시 대조했다(불일치 0).

| run·경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p95 ms |
|---|---|---|---|---|---|---|---|
| a·A | 6/22 | 11/22 | 12/22 | 14/22 | 14/22 | 8/22 | 13305 |
| a·B | 7/22 | 10/22 | 10/22 | 12/22 | 11/22 | 11/22 | 11696 |
| b·A | 14/22 | 10/22 | 18/22 | 20/22 | 20/22 | 2/22 | 7897 |
| b·B | 13/22 | 11/22 | 19/22 | 22/22 | 22/22 | 0/22 | 6182 |
| c·B | 18/22 | 15/22 | 20/22 | 22/22 | 22/22 | 0/22 | 7787 |

- 판정 오류 코드(초회+재시도 합계): a·A `QREF_INVALID` 12·`STEPS_INVALID` 4, a·B `QREF_INVALID` 21, b·A `STEPS_INVALID` 4(2문항×2시도)·`QREF_INVALID` 0, b·B 0, c·B 0. a 경계는 `QREF_INVALID` 4.
- 경계 생성: a·b 모두 9케이스 **0/9**. c 경계 미실행.
- 실제 `usage.prompt_tokens`가 사전 audit 계수보다 작거나 큰 호출: a·b의 A/B/boundary, c의 A/B 모두 0.
- G08: a·A·a·B·b·A·b·B 모두 최종 t=`UNS`. c·B는 t=`INC`(기대 일치), eqp MATCH, from·to 둘 다 MISMATCH(`equalsContext=true`, Context 값 복사). c·A의 G08은 미실행.
- 개발 회귀 신규 8문항(회귀 30문항 중 v2 접두 22행 외)은 전부 미실행이다.

#### 4) c 중단 경위(root 수동 확인 포함)

- c·A G01·G02가 각각 120초 시간 초과했다. root가 읽기 전용 SSH로 `ollama ps`·`hostname`을 두 번 시도했으나 둘 다 banner timeout이었다.
- 더 이상의 요청 진행을 막기 위해 **root가 native 세션이 생성한 것임을 PID·명령행으로 확인한 로컬 ssh 터널 PID 8984만 `Stop-Process`로 종료했다. 그 직후 진행 중 호출이 `ENDPOINT_UNREACHABLE`로 기록됐다.** native 세션 최종 서술의 "터널이 재부팅으로 자동 종료"는 사실과 다르다.
- 이후 host 접속이 복구됐다. native 세션은 boot 시각 05:22:12 UTC를 보고했고, root가 05:24:39 UTC에 조회한 uptime은 2분, `ollama ps`는 헤더만(상주 0)이었다. **재부팅 사실은 확인됐으나 재부팅 주체·원인은 미확정이다.** journal 지속 보관이 없어 이전 boot 원인을 판정할 수 없다.
- root·native 모두 원격 상태 변경 0, 서비스 재시작·모델 로드 0. 접속은 복구됐지만 상주 모델 0이므로 실측을 진행할 수 없고, 기존 모델 로드 금지 지시가 적용된다.

#### 5) native 실행상의 문제(숨기지 않음)

- b: 임시 러너의 경로 이스케이프 실수가 있었다. native 세션은 자신이 띄운 터널(PID 14116)을 식별해 재사용했고 b 끝에 종료했다. 등록 입력·결과에 미친 영향은 없다.
- c: 명령을 묶어 실행해, A 실패 뒤에도 boundary preflight와 불완전 진단 보고서 생성까지 진행됐다.
- 파일 덮어쓰기·숨긴 best-of는 0이다. 이 문제는 새 SOURCE 수정 대상으로 범위를 넓히지 않고, 아래 재개 조건의 실행 규칙으로 막는다.

#### 6) 재개 권장 조건(현재 준비 실행 금지)

- 사용자의 모델 로드 금지 지시 때문에, 추가 로드 승인 없이는 진행 0이다.
- 장비 안정 상태, 모델 동일 digest, 4096 context, 2모델 상주 복구를 확인한 뒤에만 재개한다.
- **새 고유 runId로 c 전체(B, A, 경계 9)를 다시 실행**한다. 옛 부분 c(`20261102T045749Z-c`)는 불변으로 두며, 재부팅 전 B와 재부팅 후 A를 섞지 않는다. 기존 capture 원본도 보존하고 다시 capture할 때 덮어쓰지 않는다.
- 동일 입력·판정을 그대로 재사용하고, 시도 이력은 모두 보존한다. snapshot·capture 재사용의 정확한 검증 방식은 재개 시 현재 `Prereg`/`MeasurementSession` 규칙으로 확인하며, 미확정 동작을 단정하지 않는다.
- **후속 실행은 한 명령씩 실행해 exit 0을 확인한 뒤 다음 명령으로 넘어가고, 실패하면 즉시 중단한다.**
- 그 뒤 개발 회귀(30문항+경계 9), 착수 조건을 충족할 때만 정식 GATE R5를 실행한다.

#### 7) 상태

- 이번 진단 3개 보고서로 만든 F0 leaf·GATE·SPR은 0이다. 기존 v2 F0 FAIL을 유지하며, 이번에 새 정식 판정은 없다. **P03 미완료, P04 미착수.**
- 이 절의 내용은 root 최종 문서 대조 전이다.

#### 8) root 독립 최종 문서 대조 (2026-11-02)

- 실제 정제 결과를 독립 재집계해 a/b/c-B 수치·G08·오류 분포와 §12.10이 일치함을 확인했다. c-A의 2건 TIMEOUT과 complete=false는 실패 표본으로 보존됐고, 이번 진단 runId의 F0 증거 레코드는 0개다.
- Dev report는 C6 본문 전체를 접두부로 보존한 추가 기록뿐이다. Plan 변경 줄은 11·14 두 행뿐이며, 두 행을 검토 전 내용으로 치환한 전체 SHA가 종전 43cb0cc7…451f와 일치한다(파일 복원 실행 0). SPEC SHA 34fd6223…66be는 불변이다.
- 최종 별도 test-evidence verify도 exit 0·TEST_EVIDENCE_VERIFIED다. 기존 174/174 로컬 시험 결과는 유효하며 실제 F0 합격은 미검증이다. commit은 승인된 C1~C6 총 6개, staged 파일 0, push 0이다.
- 복구 후 추가 읽기 전용 조회: 설치 목록에 qwen3:4b-instruct(0edcdef34593)·bge-m3:latest(790764642607)가 남아 있었다. 메모리 available 6588 MiB·swap 3809 MiB 중 사용 0은 재부팅 후 조회값이며, 재부팅 전 메모리 부족의 증거로 쓰지 않는다. 재부팅 주체·원인은 여전히 미확정이다. 모델 로드 금지에 따라 복구 추론은 실행하지 않았다.

### 12.11 P03 생성 모델 단일 재적재 실패·차단 원인 읽기 전용 진단 (2026-11-03, 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 최종 대조 전)

- **배경**: 사용자가 예외로 '생성 모델 1회 재적재 후 계속 진행'을 승인했다. root가 이 1회를 소비했다. 요청은 `qwen3:4b-instruct` `/api/generate` 1회(max 1 token, ctx 4096, keep_alive -1, 바깥 timeout 1800초)였고, 2026-11-02T23:00:28Z HTTP 500으로 실패했다. 추가 요청은 0건이며, 이후 추가 load 권한은 없다. 이 세션도 원격 변경, 서비스 재시작, load/pull/unload, POST, 추론을 하지 않았다. 원격 임시 파일도 만들지 않았다(SSH stdin Python으로 읽기만 함).
- **원격 정적 사실(읽기 전용)**: 설치 상태는 다음과 같다.
  - unit `/etc/systemd/system/ollama.service`, User/Group `jetson`, ExecStart `/home/jetson/ollama/bin/ollama`(Listening version `0.31.1`, 바이너리 mtime 2026-06-30)
  - drop-in `10-preload.conf`(TimeoutStartSec, `ExecStartPost=-/home/jetson/ollama/warmup.sh`)와 `20-listen.conf`(내용은 범위 밖이라 보지 않음)
  - Restart=always, NRestarts 0, TimeoutStartUSec 10min
  - 허용 목록 설정: unit `OLLAMA_KEEP_ALIVE=-1`·`OLLAMA_MAX_LOADED_MODELS=2`. server config 기준으로는 `OLLAMA_LOAD_TIMEOUT=5m0s`(기본값), `OLLAMA_LLM_LIBRARY`·`CUDA_VISIBLE_DEVICES`는 빈 값, NUM_PARALLEL 1, FLASH_ATTENTION false
  - lib `cuda_jetpack6`·`cuda_v12`·`cuda_v13`, L4T R36 REVISION 5.0, 현재 journal boot 1개, cron·별도 unit의 warmup 0
- **journal 사실(현 boot, 정제 요약)**:
  - 05:23:26.88Z에 GPU discovery를 시작했고, 약 29초 뒤인 05:23:56.21Z에 `llama-server GPU discovery watchdog timed out`(context deadline exceeded)가 났다. 이어 `inference compute library=cpu`(total 7.4 GiB, available 6.8 GiB)가 선택됐다.
  - **부팅 warmup의 qwen 적재도 이미 실패했다.** 05:23:58Z에 시작해 05:29:00Z에 `Load failed: timed out waiting for llama-server to start`, `POST /api/generate` 500(5m3s)이 기록됐다. `~/ollama/warmup.log`의 현 boot 줄도 `qwen3:4b-instruct load FAILED`, `bge-m3 loaded`이다. 앞서 "loaded 3줄, error 0"으로 정리된 사실은 이전(2026-10-19) 회차 줄과 섞인 것이며, 그 회차의 qwen 적재는 3초였다. bge-m3는 CPU에서 106.07초 만에 ctx 4096으로 적재됐다.
  - root 1회 시도: 22:55:21.53Z에 llama-server를 시작했다(system memory free 4.3 GiB, free_swap 3.7 GiB). 22:55:24.76Z에 `load_tensors` CPU 305.03 MiB + CPU_REPACK 2375.16 MiB가 찍힌 뒤 loader 진행 로그 없이, 약 5분 6초 뒤인 23:00:27.93Z에 `Load failed`(같은 오류), 23:00:28.60Z에 500(5m8s)이 났다. 이 구간에 `llama_context` 생성 로그는 0건이다.
  - kernel 현 boot에서 OOM 0, nvgpu 4줄 중 error/fail/timeout 동반 0이다(로그가 없다는 것이 원인 부재의 증명은 아님).
- **인과 판정**:
  - 확정: 두 qwen 적재 모두 CPU backend에서 서버 측 `OLLAMA_LOAD_TIMEOUT` 5분을 넘겨 실패했다. 바깥 1800초 timeout은 결과에 영향이 없었다. CPU 선택은 부팅 시 GPU discovery timeout 직후에 일어났다.
  - 미확정: GPU discovery가 timeout된 원인(드라이버/런타임 초기화 지연인지, 부팅 경합인지 등)과 재부팅 원인. GPU 경로였으면 성공했을지도 이번 boot 자료로는 검증하지 않았다(이전 3초 적재는 다른 boot의 사실일 뿐이다).
  - 메모리 압박(swap 사용 증가, IOwait 표본)은 CPU 적재 지연에 기여했을 수 있지만 OOM으로 확정되지 않았다.
- **측정 상태**: 새 c runId, 회귀 30+9, 정식 R5는 모두 미착수(0)다. 기존 결과, 등록 입력, PREREG, spec(SHA `34fd6223…66be` 재확인), Plan, HEAD C6 `01731891…` 6커밋은 불변이며 staged 0이다. 기존 174/174 로컬 시험 결과는 유효하다. **P03 미완료, P04 미착수.**
- **필요 조치(사용자 직접 수행, 별도 명시 승인 필요. 이 세션은 실행하지 않음)**:
  1. GPU 재탐색을 위한 `sudo systemctl restart ollama` 또는 장비 재부팅. 이것으로 GPU discovery가 다시 실행되지만 **CUDA 선택이 보장되지는 않는다.**
  2. 재시작하면 ExecStartPost warmup이 qwen·bge 적재를 자동으로 시도하므로, 이 적재까지 포함한 승인이어야 한다.
  3. 재시작 직후 journal의 `inference compute` library가 CUDA인지 읽기 전용으로 먼저 확인한다. 여전히 cpu면 추가 적재·측정을 중단하고, GPU discovery timeout 원인(JetPack/CUDA 런타임) 조사를 별도 단계로 승인받는다.
  - 단순 `OLLAMA_LOAD_TIMEOUT` 증가, CPU 정식 측정 강행, 게이트 완화는 권장하지 않는다.
- **완료 경로(불변)**: CUDA 선택과 2모델(동일 digest, ctx 4096) 상주를 확인한 뒤 진행한다. 순서는 새 runId c(B22, A22, 경계 9, 기존 `v3-diag-c` capture 재호출·덮어쓰기 금지) → 런타임 동일성 검사 → 회귀 30+9 → root 검토 후 조건부 R5 → SPEC §14.5, 기준선, 재판정, SPR, F0 gate이다.

#### root 독립 대조·재시작 승인 후 실행 결과 (2026-11-03)

- root가 warmup 로그를 날짜별 허용 필드로 다시 파싱해 2026-11-02 부팅의 qwen 적재 실패·bge 적재 성공을 확인했다. 이전의 단순 누적 집계는 2026-10-19 성공 줄을 섞었으므로 이번 부팅의 적재 성공 근거로 쓰지 않는다.
- root가 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a` 불변, C6 Dev report 접두부 보존, UTF-8 LF, 6커밋·staged 0을 확인했다. `git diff --check` exit 0이다.
- root 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain`은 exit 0·`TEST_EVIDENCE_VERIFIED`(15초)였다. 기존 로컬 시험 증거의 검증이며 174개 시험을 새로 실행하거나 실제 F0를 통과한 것은 아니다.
- 사용자가 '서비스 1회 재시작·자동 적재 허용(권장)'을 승인했다. root가 SSH로 `sudo -n systemctl restart ollama`를 1회 시도했으나 `sudo: a password is required`·exit 1로 거부됐다. 실제 서비스 재시작·그에 따른 자동 적재는 수행되지 않았다. 추가 재시작 시도·비밀번호 우회는 0이다.
- 다음 선행 조치는 사용자가 Jetson 터미널에서 `sudo systemctl restart ollama`를 직접 1회 실행하는 것이다. 그 뒤 root가 GPU 선택·두 모델의 동일 digest·상주·context를 읽기 전용으로 확인하고, 준비된 경우 기존 승인 범위의 c 전체 진단부터 이어간다. 실제 재시작·모델 준비·측정 완료를 확인 전까지 선언하지 않는다.

### 12.12 P03 v3 재개 — 진단 c 전체·개발 회귀 실행 (2026-11-03, 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 검토 전)

- §12.1~§12.11은 바꾸지 않는다. 이 세션의 변경은 신규 결과 디렉터리 2개·신규 capture `eval/f0/chat-template/v3-regression/`·이 절 추가뿐이다. 코드·시험·프롬프트·사전등록·SPEC·Plan·기준선·승인 파일 변경 0, commit·stage·push 0, 원격 서비스 재시작·모델 load/pull/unload·num_ctx 변경 0이다.
- **결론(진단 R1, 판정 아님): c 전체와 회귀가 모두 exit 0·`complete=true`다. 그러나 경계 9케이스 boundary-gen이 2/9만 통과해 `PREREG-v3-gate.md` §1 착수 조건 2를 충족하지 않는다. G08 from·to의 Context 복사도 남아 있다. 정식 GATE R5는 이 세션에서 실행하지 않았고 root 검토 대상이다. 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.**

#### 1) 사용자 행동·복구 결과(읽기 전용 확인)

- §12.11 이후 사용자가 Jetson에서 서비스 1회 재시작을 직접 수행했고, ExecStartPost warmup의 2모델 자동 적재를 별도로 승인했다. 이에 앞서 root의 생성 모델 1회 복구 시도는 CPU에서 HTTP 500으로 실패했다(§12.11).
- 이 세션의 확인값(SSH 읽기 전용, `GET /api/ps` 허용 필드만 메모리에서 파싱):
  - `ActiveEnterTimestamp=2026-11-03 08:07:24 KST`, NRestarts 0, active/running. 장비 boot 시각은 여전히 2026-11-02 14:22:12 KST(05:22:12Z)라 재부팅은 아니다.
  - journal: 08:07:24 KST 새 프로세스가 `inference compute library=CUDA compute=8.7 name=CUDA0`(total 7.4 GiB, available 5.5 GiB)를 선택했다. 08:07:13 KST의 `GPU discovery watchdog timed out`·`library=cpu` 줄은 재시작 직전 프로세스의 것이다. 08:07:20 KST 이후 `Load failed`·`library=cpu` 0건(측정 중 매 명령 뒤 재확인해도 0)이다.
  - warmup.log(현 일자 줄만): 08:07:25 시작, `qwen3:4b-instruct loaded` 08:10:24, `bge-m3 loaded` 08:12:46 KST.
  - 대기: 23:09:07Z 상주 0 → 23:10:26Z qwen만 → 23:12:59Z 2모델(약 6분, 8분 한도 이내). 조회 1건은 SSH 응답이 약 44초 지연됐다(원인 미확인).
  - 착수 시 `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096 size_vram=size 3178149969. `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096 size_vram=size 664000265. 두 모델 모두 GPU 전량 적재다.
  - 메모리 Mem 7619 MiB 중 available 1739 MiB, swap 3809 MiB 중 사용 581 MiB, loadavg 1.75.
- 측정 중 매 명령 뒤 재확인: ActiveEnterTimestamp 불변, 2모델 동일 digest·VRAM 상주 유지, CPU 재전환 0. 측정 중단 사유는 없었다.

#### 2) 측정 환경 비교의 한계

- a·b(`20261102T045749Z`)는 재부팅 전 boot(2026-10-19 warmup 줄은 qwen 적재 3초)에서 측정됐다. 이번 측정은 재부팅 뒤 같은 boot에서 서비스 재시작 1회를 거친 CUDA 환경이다(qwen warmup 적재 약 3분, swap 사용 581 MiB). 따라서 지연 수치는 boot 간에 직접 비교하지 않는다.
- 출력 결정성 대조(해시만): 옛 c·B(재부팅 전)와 새 c·B의 `outputSha256`가 22/22 일치하고, `predicted.t`·인자 일치 패턴도 22/22 같다. p50/p95는 옛 4041/7787 ms, 새 3075/6107 ms다. 옛 c·A는 G01·G02 TIMEOUT 뒤 중단돼 출력 대조가 불가능하다.
- 회귀의 원래 22문항 접두는 새 c와 `outputSha256`가 A·B 모두 22/22 일치한다. 경계 9케이스도 c와 회귀가 9/9 일치한다.

#### 3) 실행 기록(Git Bash, cwd `C:\workspace_nori\nori-ai-gateway`, 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로·`AIGW_PARENT_RUN_ID`=각 runId·`DEBUG`=빈 값, `./gradlew :gateway-eval:run --console=plain --args="…"`, 한 명령씩 exit 확인)

- run prefix `20261102T231328Z`(옛 `20261102T045749Z-*`와 섞지 않음). 터널은 이 세션이 `Start-Process -WindowStyle Hidden`으로 띄운 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`만 썼다. c는 PID 11856, 회귀는 PID 17368이며, 둘 다 명령행을 확인한 뒤 이 세션이 `Stop-Process`로 종료했다. 시작 전 포트 11434 미사용, 종료 후 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| c-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-c.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `55d61c55…f408` |
| c-2 | `f0 preflight … --out eval/f0/results/20261102T231328Z-c/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| c-3 | `f0 chat-template`(c) | **미실행(의도)** | 기존 `v3-diag-c` capture·run-binding이 있어 재호출하면 `RUN_BINDING_EXISTS`다. root가 기존 capture 무결성(template `40c21f34…b541`, tokenizer `aeb13307…dae4`, model digest 동일)을 검증하고 생략을 확정했으며, 사용자 재질문은 불필요했다. 각 측정 명령이 `MeasurementSession`에서 runtime(`/api/show` template SHA·model digest·tokenizer pin)을 다시 수집하고 input audit와 대조한다. |
| c-4 | `f0 run … --path B … --run-id 20261102T231328Z-c --out …/B` | 0 | 23:14:05~23:15:48, `F0_RUN_COMPLETED B` |
| c-5 | `f0 run … --path A …` | 0 | 23:16:02~23:18:20, `F0_RUN_COMPLETED A` |
| c-6 | `f0 boundary-gen …` | 0 | 23:18:35~23:19:59, `F0_BOUNDARY_COMPLETED 9` |
| c-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-c.md --run-id 20261102T231328Z-c --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `da166f40…2611` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]` |
| r-2 | `f0 preflight …/20261102T231328Z-regression/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression`(신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B SHA `40c21f34…b541`(=`v3-diag-c`), model-digest `0edcdef3…8ba0` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | 0 | 23:22:00~23:24:10 |
| r-5 | `f0 run … --path A …` | 0 | 23:24:25~23:27:14 |
| r-6 | `f0 boundary-gen …` | 0 | 23:27:28~23:28:48, `F0_BOUNDARY_COMPLETED 9` |
| r-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-regression.md …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `aa6cfd60…fdd` |

- 6개 run-binding(c·회귀의 B·A·boundary)의 runtimeManifest는 모두 model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`이고 specSha `34fd6223…`다. 별도 `spec-guard`는 exit 0이었다. 이 1회는 c·B 측정 도중 동시에 실행됐다(클래스 재빌드 없음, 측정 run-binding은 그 전에 작성됨). 이후 측정과 겹치는 Gradle 실행은 0이다.
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 달랐던 호출(작음/큼)은 c·회귀 모두 A 0/0, B 0/0, boundary 0/0이다.

#### 4) 진단 c 결과(`eval/f0/results/20261102T231328Z-c/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 20/22 | 13/22 | 22/22 | 22/22 | 18/22 | 4/22 | 4293/10724 | `STEPS_INVALID` 4(G05·G07·G08·G18, 재시도 후 유효) |
| B | 18/22 | 15/22 | 20/22 | 22/22 | 22/22 | 0/22 | 3075/6107 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 9·`TASK_TYPE_MISMATCH` 2, B `ARGUMENT_MISMATCH` 7·`TASK_TYPE_MISMATCH` 4.
- t 오답: A는 G06·G19(LQ→KQ). B는 G07(UNS→INC)·G16(UNS→GQ)·G09·G12(INC→LQ).
- 두 경로 모두 `QREF_INVALID` 0, 후보 0 문항의 `qRef.kind=ID_OUT_OF_RANGE` 0(후보 0에서 모두 `JSON_NULL`)이다.
- **G08**: B는 t=INC(초회), eqp MATCH. A는 초회 t=UNS(`STEPS_INVALID`) 뒤 재시도에서 t=INC, eqp MATCH다. 그러나 **A·B 모든 시도에서 from·to MISMATCH·`equalsContext=true`(Context 시각 복사)다. t 축만 기대와 일치하며, G08 실패가 해소됐다고 보지 않는다.**
- **경계 9케이스 boundary-gen 2/9**: NORMAL-01·NORMAL-PRECISE만 통과했다. MAX-MIXED·MAX-HANGUL·MAX-DIGIT·MAX-ASCII·MAX-SUPPLEMENTARY·ESCAPE-01·ESCAPE-CONTROL 7건은 schemaValid=true·finish=stop·재시도 0이지만 eqp만 불일치(`ARGUMENT_MISMATCH`)이고 t·q·from·to는 MATCH다. 7건 모두 completionTokens가 98로 같다. 서버 스키마(`ResponseSchema.java:21`)에는 eqp 길이 제약이 없다. **원인 미확정**: 원문 없이 eqp 축약·변형 여부를 판정할 수 없고, 추정으로 결론 내리지 않는다.

#### 5) 개발 회귀 결과(`eval/f0/results/20261102T231328Z-regression/diagnostic-report.md`, R1·분모 30, 정식 분모 아님)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 28/30 | 17/30 | 30/30 | 30/30 | 26/30 | 4/30 | 4123/8639 | `STEPS_INVALID` 4 |
| B | 26/30 | 20/30 | 28/30 | 30/30 | 30/30 | 0/30 | 3198/6512 | 0 |

- `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0(A·B). 경계 9는 c와 같은 2/9이고 같은 7건이 eqp 불일치다. G08은 c와 같다(from·to Context 복사).
- 신규 8축(A/B 모두 t 일치 8/8):

| id | 축 | A | B |
|---|---|---|---|
| R01 | REG_QUOTED_EQP | 전 인자 MATCH(qRef ID_IN_RANGE/후보1, eqp=인용 후보) | 같음 |
| R02 | REG_EQP_VS_CODE_TERM | 전 인자 MATCH | 같음 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | **실패**: eqp·from·to MISMATCH, 모두 `equalsContext=true`(Context 복사) | **실패**: 같음 |
| R04 | REG_TC_TRANSFER_FAILURE | t=INC, **from·to MISMATCH**(Context 복사 아님) | 전 인자 MATCH |
| R05 | REG_WM_OWNED_RECORD | t=UNS, MATCH | 같음 |
| R06 | REG_PARAPHRASED_RANGE | 전 인자 MATCH | 같음 |
| R07 | REG_SECOND_QUOTED_INTENT | **q MISMATCH**(qRef ID_IN_RANGE/후보2, 의도 아닌 후보 선택으로 보이나 원문 없이 단정하지 않음) | 같음 |
| R08 | REG_LONG_EQP_ID | **eqp MISMATCH**(64 코드포인트 ID, 경계 MAX 계열과 같은 축) | 같음 |

#### 6) `PREREG-v3-gate.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. a·b·c·회귀 exit 0·complete=true, b·c `response_format` 거부(exit 5) 0 | 충족 | a·b(§12.10), 새 c·회귀 전 명령 exit 0·complete=true·exit 5 0. 옛 부분 c(`20261102T045749Z-c`)는 불변 보존·비사용 |
| 2a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 2b. G08 A·B 모두 t=INC(UNS 0) | 최종 t 기준 충족(A 초회 UNS → 재시도 INC). from·to Context 복사 실패는 잔존 | 위 4) |
| 2c. 경계 9 boundary-gen 기존 oracle 모두 통과 | **미충족(2/9)** | 위 4) |
| 3. 회귀 30문항 보고서 root 검토 | 미완(root 검토 대기) | 위 5) |
| 4. C5/C6 범위·`PREREG_VERIFIED` | 이번 c·회귀 prereg-check는 충족. gate 등록의 prereg-check는 미실행 | — |

- 따라서 이번 결과로는 정식 R5 착수 조건이 충족되지 않는다. 프롬프트·스키마 보완은 새 사전등록 파일로만 해야 한다(gate §1). 이 세션은 새 등록·F0 leaf·gate·SPR을 만들지 않았다.

#### 7) 미실행·원인 미확정·다음 정확 조치

- 미실행: 정식 GATE R5(이 세션 금지), c chat-template 재호출(의도), 시험 재실행(코드 변경 0이라 불필요), P04.
- 원인 미확정: 경계 MAX·ESCAPE 7건과 R08의 eqp 불일치(7건 completion 98 동일). G08·R03의 Context 시각·ID 복사. R07 q 후보 선택. A의 `STEPS_INVALID` 4건(무도구 task 비어 있지 않은 steps, `NONEMPTY_FOR_NO_TOOL_TASK` 초회 4). SSH 조회 1건 지연. 이전 GPU discovery timeout·재부팅 원인(§12.11)도 여전히 미확정이다.
- 다음 정확 조치(root 결정): ① root가 이 두 진단 보고서·재집계를 원문 없이 검토한다. ② 경계 eqp 불일치 원인 진단 방법(원문 미저장 원칙 안의 추가 정제 필드, 예: eqp 길이·접두 일치 여부)을 새 진단 사전등록이 필요한지 판단한다. ③ 착수 조건 2c 미충족 상태로 R5를 실행하지 않는다.

#### 8) 보존 확인

- SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a` 불변이다. HEAD C6 `01731891b58381ca9d873d2b931a2c482ffb8f5e`, 6커밋, staged 0, `git diff --check` exit 0이다.
- 측정 시작 이후 수정 시각 기준으로 옛 결과(`20261029T234506Z`·`20261102T003840Z`·`20261102T045749Z-{a,b,c}`), capture `v2-qref`·`v3-diag-{a,b,c}`, `evidence/`, `eval/py`(새 `__pycache__` 0), PREREG·v2/v3 입력·golden, `gateway-eval/src`, `docs/gates`의 변경 파일은 0이다. `v3-diag-c` template SHA·model digest도 재확인했다.
- 기존 dirty(Dev report)·untracked 항목은 그대로 보존했다. 원문 질문·응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.13 P03 근본 보완 — LLM 추출용 Context 투영(opt-in)·d 정리 프롬프트·후보 d 재등록 초안 (2026-11-03, 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 검토 전)

- §12.1~§12.12는 바꾸지 않았다. 이 절의 범위는 로컬 수정·시험·재등록 초안뿐이다.
  - 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 재시작, 원격 설정 변경, commit·stage·push.
  - 바꾸지 않은 것: SPEC·Plan·기준선·승인 문서, 기존 등록 문서·입력·audit·골든·fixture·tokenizer·capture·측정 결과·evidence.
- **결론: 코드·시험·audit·초안 준비까지 완료했다. 후보 d는 아직 실측하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 진단 d·회귀 d·정식 d를 실행하려면 C7/C8 로컬 commit에 대한 사용자 허용이 먼저 필요하다.

#### 1) 근본 원인과 정석 수정(§12.12·root 원문 비노출 증명 근거)

| 실패 | 원인 | 수정 |
|---|---|---|
| G08 A/B from·to, R03 eqp·from·to의 Context 복사 | `MessageAssembler` qRef 오버로드가 전체 Context(`timeWindow`·`selectedResourceRefs` 포함)를 LLM user 메시지에 넣었다. SPEC §14.5/§22.2는 Context의 선택 리소스·시간 창 적용을 LLM 출력 뒤 Gateway 책임으로 확정한다. | 새 v3 opt-in 키 `parameters.contextProjection=EXTRACTION_V1`을 추가했다. 이 값이면 LLM에는 `context.timezone`·`referenceTime`만 보낸다. 원 Context·fixture·판정 oracle·시도별 진단(`equalsContext` 등)은 전체 Context를 그대로 쓴다. |
| 경계 7건·R08 eqp | 64 코드포인트 Unicode ID 중 ASCII로 보이는 일부(codepoint [9,16))만 설비 ID로 골랐다. 스키마 길이 절삭 때문이 아니다. | d 프롬프트: eqp는 질문이 설비로 지칭한 식별자 전체다. 한글·영문·숫자·기호가 섞인 최대 64자일 수 있으며 영문·숫자 부분만 골라내지 않는다. 정답 ID·패턴 상수·catalog는 넣지 않았다. |
| R07 두 번째 q | 제외·대조로 언급한 검색어를 선택했다. | qRef는 질문이 요구하는 검색어를 고른다. 제외·대조로만 언급한 검색어(`말고`·`빼고` 등)는 고르지 않는다. |
| R04 A 정밀 시각, G08 A STEPS_INVALID | 명시 절대 시각의 원문 보존 규칙과 steps↔최종 t 규칙이 모호했다. | 초·소수초·offset 절대 시각은 원문 그대로 쓴다(시간대 변환은 Gateway). 질문에 시간이 없으면 null이고 referenceTime을 옮기지 않는다. 상대·분 단위 표현만 timezone·referenceTime 기준이다. steps는 최종 t에 맞춘다. |

- 계약 차이 0.
  - 제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`)는 그대로다. `eqpRef`·`timeRef` 같은 새 wire는 도입하지 않았다.
  - 질문·qRef 카탈로그·`JSON_SCHEMA_V1`·q 256·Unicode 64 eqp·시간 나노초 원문 보존·인가도 그대로다.
  - 예산(3072/3072, 192/256, 경계 192, context 4096)·분모(정식 22+9, 개발 30+9)·기준·판정 규칙(`f0-judge-v2`)도 그대로다.
  - 소유 시스템·미지원·Source 계약도 그대로다.
  - 새 사용자 정책이 필요한 충돌은 발견하지 않았다.

#### 2) 구현(변경 파일 — 코드 5·시험 3·입력 4·초안 3)

- `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/`
  - `Prereg.java`: `CONTEXT_PROJECTIONS={EXTRACTION_V1}`와 `Document.contextProjection()`을 추가했다. 키가 없으면 `FULL`이다. v1/v2에 키가 있거나 v3 값이 미정의·`FULL`·비문자열·null이면 `PREREG_INVALID`다. GATE R=5 규칙은 그대로다.
  - `MessageAssembler.java`: 투영값 생성자·`load`를 추가했다. 기존 2인자 생성자는 `FULL`이다.
    - `FULL`: 받은 Context 노드를 그대로 쓴다(기존 바이트 동일).
    - `EXTRACTION_V1`: 새 노드에 두 필드만 복사한다(원 Context 변경 0). 두 필드가 문자열이 아니면 `CONTEXT_PROJECTION_INVALID`다.
  - `F0RunnerCli.java`: `run`·`boundary-gen`·usage/token `assemble` 조립에 등록 `contextProjection`을 결속했다.
    - `input-audit-render`에 선택 인자 `--context-projection`을 추가했다. 없으면 `FULL`이고 `EXTRACTION_V1`만 받는다. 그 밖 값이나 명시 `FULL`은 `ARGUMENT_INVALID`다.
    - 측정 명령에는 override 인자가 없다(`INVALID_ARGUMENTS`).
  - `F0Measurements.java`: `run`·`boundaryGen`에서 조립기 투영과 등록 투영이 다르면 송신 전에 `CONTEXT_PROJECTION_MISMATCH`로 멈춘다.
  - `F0ReportWriter.java`: 진단 보고서 머리줄에 `contextProjection`을 표기한다. `parameterManifestSha256`이 이미 이 키를 결속한다.
- 시험(`gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/`)
  - 신규 `F0ContextProjectionTest.java`(7개, 실제 HTTP stub 요청):
    - 투영 요청(B·A·경계·usage·token): `context` 키는 timezone·referenceTime 두 개뿐이고 값은 원 Context와 같다. `timeWindow`·`selectedResourceRefs` 0, 질문·`qRefCatalog`는 원문 바인딩과 같다.
    - 재시도: 같은 투영의 초회 메시지 + 고정 코드 한 줄이다. 전송 메시지 SHA는 모두 audit 표에 있다. 골든·경계 파일 바이트와 메모리 Context는 불변이다.
    - FULL 요청(키 없음): 파일 Context로 독립 구성한 기존 user 메시지와 바이트가 정확히 같다.
    - 등록 키 오염 9종 거부(v2 키·미정의 값·GATE R1).
    - audit 투영과 등록 투영이 다르면 run A/B·boundary-gen·usage-probe가 모두 `INPUT_AUDIT_MISSING`(요청 0)이다. CLI override도 거부한다.
    - `input-audit-render` 행 id·SHA는 측정 조립 목록과 같다. 조립기는 원 Context를 바꾸지 않고 잘못된 값에서 fail-closed다.
    - AC38 Context 복사 답: 진단은 원 Context 기준 `equalsContext=true`, 판정은 불일치다. 진단 등록은 여전히 `DIAGNOSTIC_NOT_GATE_ELIGIBLE`이다.
  - `F0V3RegistrationDraftTest.java`: 기존 5등록과 후보 d 3초안을 분리했다.
    - 기존 5등록: 입력 바이트 일치. 실제 git `verifyHistorical`로 C5 트리 harnessDigest와 PREREG commit=C6을 확인한다. 현재 하네스 기준으로는 `localMismatches=["harnessDigest"]`, `Prereg.verify`는 `PREREG_INPUT_CHANGED`다. 즉 역사 신원은 유지되고 신규 측정은 stale로 거부된다. 기존 등록은 수정하지 않았고 FULL 조립 audit 전수 결속도 유지한다.
    - 후보 d 3초안: `localMismatches=[]`, 투영·스키마·R1/R5·고유 capture 확인. 초안이면 `PREREG_NOT_COMMITTED`, 등록 뒤에는 실제 `Prereg.verify` 통과를 요구한다(root 반영).
    - audit: 행 순서·id·SHA가 현재 조립과 같다. 반대 투영의 조립 메시지(22문항 등록 640건·30문항 등록 832건)는 각 표에서 `INPUT_AUDIT_MISSING`이다.
  - `F0VersionedInputsTest.java`: `writeAudit`에 투영 인자 오버로드만 추가했다(기존 호출 = FULL).
- 신규 입력(`eval/f0/v3/`)
  - `prompts/classify-v3-d.txt` `9f52c9c0…80c7`(908토큰, c 898)
  - `prompts/plan-v3-d.txt` `b3297926…3c86`(1048토큰, c 1018)
  - `input-audit-v3-d.json` `f77adec8…e24f`
  - `input-audit-v3-regression-d.json` `799cf300…4a62`
- 신규 초안(`eval/f0/`): `f0-prereg-v3`, UNCOMMITTED/`registeredCommit=null`, `responseFormat=JSON_SCHEMA_V1`, `contextProjection=EXTRACTION_V1`

| 초안 | 목적 | R | 골든·audit | capture | harnessDigest |
|---|---|---|---|---|---|
| `PREREG-v3-diag-d.md` | DIAGNOSTIC | 1 | v2 22문항 + `input-audit-v3-d.json` | `v3-diag-d` | `cda6456e…525c` |
| `PREREG-v3-regression-d.md` | DIAGNOSTIC | 1 | 회귀 30문항 + `input-audit-v3-regression-d.json` | `v3-regression-d` | `a863b09f…be48` |
| `PREREG-v3-gate-d.md` | GATE | 5 | v2 22문항 + `input-audit-v3-d.json`(diag-d와 바이트 동일) | `v3-gate-d` | `cda6456e…525c` |

- 골든 22·회귀 30·fixture v2·경계 9·usage/token v2 메시지 입력·tokenizer·pin·Python 3종은 원본 바이트 SHA를 그대로 참조한다.
- 세 초안의 Java `f0 prereg-check`: `currentHarnessDigest`=문서값, `localMismatches []`, `PREREG_NOT_COMMITTED`(exit 2, endpoint 호출 0).
- `PREREG-v3-gate-d.md` §1 착수 조건(모두 충족해야 R5 실행):
  1. C7/C8 뒤 `PREREG_VERIFIED`
  2. 진단 d·회귀 d exit 0·complete
  3. 진단 d에서 `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0, G08 최종 t A·B INC, 경계 boundary-gen 9/9
  4. root의 원문 없는 검토
- 과거 c의 2/9는 조건이 아니다. a/b는 이력일 뿐 R5를 대체하지 않는다. 정식 측정용 별도 재승인 벽은 두지 않았다. 기존 `PREREG-v3-gate.md` §1은 과거 문서로 보존했다.

#### 3) 공식 입력 audit(실제 Java 조립 + 등록 렌더러 `chat-renderers-v1.json` + 로컬 pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`)

- 렌더: Java `f0 input-audit-render … --context-projection EXTRACTION_V1`, 레포 밖 `%TEMP%\aigw-p03v3d\render-{d,regression-d}.jsonl`. 결과 `F0_INPUT_AUDIT_RENDERED 651`·`843`.
- 계수: `count_input_audit_v2.py`(`-I -B`). 결과 `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`·`843 OVER_BUDGET 0`.
- 행 구성: A/B 각 문항 × (첫 시도 + 고정 오류 코드 11종 재시도), 경계 9 × 12, usage 2, token 13. 모든 retry 오류 코드를 포함하며 유효 입력 절삭은 0이다.

| audit | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token |
|---|---|---|---|---|---|
| c(기존) | 1610/1649 | 1402/1441 | 3015/3054 | 1368 | 2793 |
| d | 1610/1649 | 1382/1421 | 3015/3054 | 1368 | 2793 |
| 회귀(기존) | 1610/1649 | 1402/1441 | 3015/3054 | 1368 | 2793 |
| 회귀 d | 1626/1665 | 1398/1437 | 3015/3054 | 1368 | 2793 |

- 행별 변화(c→d, 같은 id 집합):
  - B·경계: 프롬프트 +10, 투영 −10이 상쇄돼 대부분 0이다. Context에 선택 자원·시간 창이 있는 문항(G08 등)은 −50이다.
  - A: +20(프롬프트 +30, 투영 −10)에서 −30 사이다.
- 최대 입력은 경계 `ESCAPE-CONTROL/retry` 3054로 상한 3072까지 18토큰 여유다. 3054+192=3246≤4096, A 최대 1665+256=1921≤4096이다.
- 경계 여유가 작아 d 프롬프트는 중복 문구를 정리했고, 분류 프롬프트 순증을 +10토큰으로 제한했다.

#### 4) 시험 결과(이 세션 실행, root 실행 아님, Gradle 동시 실행 0)

| 명령 | exit | 결과 |
|---|---|---|
| `./gradlew :gateway-eval:test --console=plain`(무필터, `AIGW_SPEC_FILE`=정본 SPEC) | 0 | 182 tests, failures 0·errors 0·skipped 0(기존 174 + 신규 8) |
| `./gradlew :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain` | 0 | `TEST_EVIDENCE_VERIFIED` |
| `python -I -B tests/test_count_input_audit_v2.py` / `tests/test_count_tokens_qref_v2.py` | 0 / 0 | OK / OK, 새 `__pycache__` 0 |
| `f0 prereg-check` × 3 초안 | 2 | `localMismatches []`, `PREREG_NOT_COMMITTED` |

- 중간 필터 실행 첫 회에 시험 자체 오류 3건(색인·키 판별)이 나와 시험 코드만 고쳤다. 제품·하네스 동작 결함은 아니었다. 최종 무필터 실행은 위와 같이 전부 PASS다.

#### 5) 미실측·다음 단계(C7/C8 선별 경로)

- 미실측: 진단 d·회귀 d·정식 d R5. 후보 d의 정확도·경계 통과 여부는 아직 모른다.
- C7(사용자 허용 필요): 등록 대상·하네스·시험·이 Devreport. C5와 같이 초안 3문서는 UNCOMMITTED 상태로 포함한다. 총 16파일:
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{Prereg,MessageAssembler,F0RunnerCli,F0Measurements,F0ReportWriter}.java`
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0ContextProjectionTest,F0V3RegistrationDraftTest,F0VersionedInputsTest}.java`
  - `eval/f0/v3/prompts/{classify-v3-d,plan-v3-d}.txt`, `eval/f0/v3/{input-audit-v3-d,input-audit-v3-regression-d}.json`
  - `eval/f0/PREREG-v3-{diag-d,regression-d,gate-d}.md`
  - `docs/ai-rag-gateway-architecture-dev-report.md`
  - 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C8(사용자 허용 필요): `eval/f0/PREREG-v3-{diag-d,regression-d,gate-d}.md` 3파일에만 `registrationStatus=REGISTERED`와 `registeredCommit`=C7 SHA를 기록한다. inputs·harnessDigest는 바꾸지 않는다.
- C8 뒤 순서: 각 `f0 prereg-check` → 진단 d(§2) → 회귀 d → gate-d §1을 충족하면 R5.
- 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다. 원문 질문·응답·secrets·환경값은 출력·저장하지 않았다.

#### 6) root 독립 최종 대조 (2026-11-03)

- root가 진단 c·개발 회귀의 원문 없는 결과를 직접 재집계해 §12.12의 정확도·경계 2/9·신규 회귀 실패 유형과 일치함을 확인했다. G08은 초회와 최종 판정을 구분했다(A 초회 UNS, 재시도 뒤 최종 INC; 최종 시간 인자 불일치는 잔존).
- 경계 7건의 eqp 불일치는 원문을 재조회하지 않고 저장된 `outputSha256`을 알려진 입력 식별자의 부분 문자열 후보와 기존 wire 직렬화의 SHA-256에 대조했다. 7건 모두 Unicode 64 코드포인트 식별자의 `[9,16)` 위치 7자 ASCII 부분만 선택한 값과 정확히 일치했다. 응답 원문 출력·저장과 추가 모델 호출은 0이다. 이는 서버의 문자열 길이 제한이 아니라 전체 식별자를 부분 식별자로 해석한 실패의 구체 근거다.
- root가 새 등록 3개의 입력 SHA·현재 f0 소스 digest를 Node로 독립 재계산했다. 입력 불일치 0, 진단 d·정식 d의 harnessDigest `cda6456e390fc6b50d5b7f6548aa70a5a7a5a4633264da744a02bf38b588525c`, 회귀 d `a863b09f46b689ce76c7677eaecb766b190c440a50dd90b061a06461fa94be48`와 일치했다. audit 651·843행 전수에서 입력 상한·입력+출력 예산 위반 0이었다.
- root가 최종 JUnit XML 39개를 직접 합산해 182건·failures/errors/skipped 0을 확인했다. 별도 `test-evidence verify --tasks :gateway-eval:test`도 exit 0·`TEST_EVIDENCE_VERIFIED`였다(6초). 이는 로컬/stub 시험 증거이며 후보 d의 실제 모델 측정은 아직 실행하지 않았다.
- 등록 수명 시험의 초안·등록 완료 양쪽 지원은 root가 별도 독립 Claude Code Opus 5.5 medium 세션에 한 메서드만 맡겨 보완한 내용이다. 구현 주세션의 최종 전체 시험은 이 변경을 포함한다. 현재 실제 등록 상태는 여전히 UNCOMMITTED/null이며 C8 뒤에는 실제 Git commit·입력·하네스 검증을 다시 통과해야 한다.
- root가 기존 보존 대상에서 허용한 소스·시험 수정 7파일을 제외하고 이번 c·회귀 측정 결과를 추가한 총 264파일의 SHA를 대조해 변경 0을 확인했다. SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a` 불변, C7 검토 대상 16파일 UTF-8 LF·BOM 없음, `git diff --check` exit 0, HEAD C6·6커밋·staged 0이다.
- 다음 실행을 위한 미충족 선행은 검토한 16파일을 고정할 로컬 C7과 새 등록 3문서의 등록 메타데이터·설명을 확정할 로컬 C8의 사용자 허용이다. 기존 허용은 C5/C6까지이며 임의로 추가 commit하지 않는다. 허용 뒤 등록 검사·최종 시험 → 진단 d → 회귀 d → 조건 충족 시 R5 → SPEC 기록·재판정·SPR·F0 gate 순서로 이어간다. P04는 착수하지 않는다.
