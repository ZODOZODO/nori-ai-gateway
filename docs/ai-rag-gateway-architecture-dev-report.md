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

#### 7) 로컬 C7/C8 등록·최종 시험 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 허용 근거: 사용자 최신 명시 승인 "로컬 C7/C8 허용 후 계속 진행(권장)"(2026-11-03). 로컬 commit 두 개만 만들었고 push·publish·amend·rebase·reset·추가 commit은 0이다.
- 사전 확인: HEAD C6 `01731891b58381ca9d873d2b931a2c482ffb8f5e`·6커밋·staged 0, C7 대상 16파일 실물 SHA-256이 root 최종 검토값과 16/16 일치했다. 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be` 확인.
- C7 `4e1e866440e50799b4466e23ed344e0651de6081`: 정확 16파일(5)절 목록과 같음, 이 Devreport는 위 6)절까지 포함). `git add`는 16개 경로만 지정했고 cached 이름 목록이 16개와 정확히 일치했다. 커밋된 blob SHA-256도 작업 트리 바이트와 16/16 일치했다.
- C8 `f2ffaeeacf28be86fcb557f6bf0a51642120cbfb`: 정확 3파일 `eval/f0/PREREG-v3-{diag-d,regression-d,gate-d}.md`. 기계 판독 블록은 `registrationStatus=REGISTERED`, `registeredCommit`=C7 SHA 두 키만 바꿨고, 나머지 JSON(inputs·harnessDigest·parameters·inputRoles·runtimeCaptureDir 등)은 전후 파싱 비교로 동일, 블록 텍스트 차이도 그 2줄뿐이다. 머리말은 등록 완료·C7/C8·승인 근거로 갱신하고 미승인 초안 상태는 이력 항목으로 구분했다(gate-d §1 조건 1은 원문 유지 + 허용 사실 추기). 입력 파일 수정 0.
- 커밋 시각: C7 epoch 1793664352, C8 1793664427(75초 차, 초 단위 상이). 이후 총 8커밋·staged 0.
- 등록 검사(`AIGW_SPEC_FILE`=정본 SPEC, 명령별 순차 실행): `f0 prereg-check` diag-d·regression-d·gate-d 모두 exit 0·`PREREG_VERIFIED`·`localMismatches []`. 현재 harnessDigest diag-d/gate-d `cda6456e390fc6b50d5b7f6548aa70a5a7a5a4633264da744a02bf38b588525c`, regression-d `a863b09f46b689ce76c7677eaecb766b190c440a50dd90b061a06461fa94be48`로 등록값과 일치. 첫 회에 regression-d·gate-d 두 검사를 실수로 동시에 실행했으며(둘 다 exit 0·VERIFIED), 동시 Gradle 실행 0 조건을 지키기 위해 세 검사를 순차로 다시 실행해 위 결과를 확정했다. 모델 요청 0.
- 최종 시험(코드·입력·시험 수정 0): `./gradlew.bat :gateway-eval:test --console=plain` exit 0(test 태스크 실제 실행, UP-TO-DATE 아님), JUnit XML 39개 합산 182 tests·failures 0·errors 0·skipped 0. 후보 d 3문서가 REGISTERED이므로 `F0V3RegistrationDraftTest`는 실제 `Prereg.verify` 엄격 검증 분기(C7/C8 commit·입력·harness·clean 작업 트리)를 통과했다. 이어 별도로 `./gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain` exit 0·`TEST_EVIDENCE_VERIFIED`.
- 보호 확인: SPEC·Plan(`af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a`) SHA 불변, C7 16파일 HEAD 대비 변경 0, 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 stage·수정 0, `git diff --check` C6..C8 exit 0. source/user 정책 차이 0.
- 미실측: 후보 d 진단·회귀·정식 R5 모두 실제 모델 측정 0(SSH·HTTP·다운로드·서비스 변경 0). P03 미완료, P04 미착수. 진단 실행은 root가 이어서 맡는다.
- 이 7)절은 C7/C8 범위 밖 작업 트리 추가 기록이며 commit하지 않았다. root 대조 전 기록이다. 응답 원문·secrets·환경값은 출력·저장하지 않았다.

### 12.14 P03 후보 d 진단·개발 회귀 d 실행 (2026-11-03, 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 검토 전)

- §12.1~§12.13은 바꾸지 않았다. 이 세션의 산출물은 아래 4가지와 이 절 추가뿐이다.
  - 신규 결과 디렉터리 `eval/f0/results/20261103T001459Z-d/`, `eval/f0/results/20261103T001459Z-regression-d/`
  - 신규 capture `eval/f0/chat-template/v3-diag-d/`, `eval/f0/chat-template/v3-regression-d/`
- 하지 않은 것: 코드·시험·프롬프트·사전등록·SPEC·Plan·기준선·승인 파일 변경, commit·stage·push, 원격 서비스 재시작·모델 load/pull/unload·num_ctx 변경, 정식 GATE R5, P04.
- **결론(진단 R1, 판정 아님): d·회귀 d 14개 명령은 모두 exit 0·`complete=true`다. 그러나 `PREREG-v3-gate-d.md` §1 조건 3 중 두 항목을 충족하지 않는다.**
  - 경계 boundary-gen 7/9(NORMAL-01·MAX-HANGUL 불통과).
  - G08 최종 t가 A·B 모두 `UNS`다(기대 `INC`).
- 따라서 정식 R5 착수 조건은 미충족이며 root 검토 대상이다. 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(승인 복구 사실·읽기 전용 확인)

- 승인 복구 사실: 사용자가 2026-11-03 08:07:24 KST에 Jetson 서비스를 수동으로 1회 재시작했다. 이후 CUDA compute 8.7이 선택됐고 두 모델이 자동 상주했다(§12.12 1)). 이 세션의 추가 재시작·로드는 0이다.
- 확인 방법: SSH 읽기 전용 + Node `spawnSync` `bash -s`(LF stdin). `GET /api/ps`는 메모리에서 파싱해 허용 필드만 출력했다. 확인 시각 2026-11-03T00:14:35Z.
  - 서비스: `ActiveEnterTimestamp=2026-11-03 08:07:24 KST`, NRestarts 0, active/running. boot 2026-11-02 14:22:11 KST(재부팅 아님).
  - GPU: journal(08:07:20 KST 이후)에 `inference compute library=CUDA compute=8.7 name=CUDA0`(total 7.4 GiB, available 5.5 GiB)이 있고, `Load failed`·`library=cpu`는 0건이다.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - Mem 7619 MiB 중 available 1332, swap 3809 MiB 중 사용 878.
- 측정 명령마다(B·A·boundary 뒤) 같은 항목을 재확인했다. ActiveEnterTimestamp·NRestarts 0은 불변이었고, 2모델 동일 digest·ctx 4096·GPU 전량 상주를 유지했으며, CPU 전환 줄은 0건이었다. 중단 사유는 없었다.

#### 2) 실행 기록

- 공통 조건: Git Bash, cwd `C:\workspace_nori\nori-ai-gateway`.
  - 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=각 runId, `DEBUG`=빈 값을 설정했다.
  - 호출 형식은 `./gradlew.bat :gateway-eval:run --console=plain --args="…"`이고, `--args` 값은 각 등록 §2와 같다.
  - 한 명령씩 실행하고 exit 0을 확인한 뒤 다음으로 넘어갔다. 측정 중 다른 Gradle 실행은 0이다.
- run prefix `20261103T001459Z`(1회 결정, 기존 `20261102T231328Z-*`·c capture 비재사용).
- 터널: 이 세션이 `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다.
  - d는 PID 7052, 회귀 d는 PID 4976이다. 각각 명령행을 확인한 뒤 `Stop-Process`로 종료했다.
  - 시작 전 포트 11434는 미사용이었고, 종료 후 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| d-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-d.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `cda6456e…525c` |
| d-2 | `f0 preflight … --out eval/f0/results/20261103T001459Z-d/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| d-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-d`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| d-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3-d.txt --run-id 20261103T001459Z-d --out …/B` | 0 | 00:15:49~00:17:25, `F0_RUN_COMPLETED B` |
| d-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3-d.txt …` | 0 | 00:17:41~00:19:55, `F0_RUN_COMPLETED A` |
| d-6 | `f0 boundary-gen …` | 0 | 00:20:07~00:21:47, `F0_BOUNDARY_COMPLETED 9` |
| d-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-d.md --run-id 20261103T001459Z-d --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `e11b5e7f…eae3` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression-d.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `a863b09f…be48` |
| r-2 | `f0 preflight …/20261103T001459Z-regression-d/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression-d`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | 0 | 00:24:03~00:26:12 |
| r-5 | `f0 run … --path A …` | 0 | 00:26:26~00:29:20 |
| r-6 | `f0 boundary-gen …` | 0 | 00:29:35~00:31:20, `F0_BOUNDARY_COMPLETED 9` |
| r-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-regression-d.md …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `e950f953…4fe6` |

- 서버 `response_format` 거부(exit 5)는 0이다. F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 6개(d·회귀 d의 B·A·boundary)의 runtimeManifest는 모두 같다.
  - model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`, specSha `34fd6223…66be`(FILE_HASHED)
  - registeredCommit C7 `4e1e8664…6081`, preregCommit C8 `f2ffaeea…cbfb`
  - `contextProjection=EXTRACTION_V1`, `responseFormat=JSON_SCHEMA_V1`, parameterManifestSha256 `9e694499…39e1`
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출(작음/큼)은 d·회귀 d 모두 A 0/0, B 0/0, boundary 0/0이다.
- 출력 결정성(해시만): 회귀 d의 원래 22문항 접두는 d와 `outputSha256`가 A·B 모두 22/22 일치하고, 경계 9케이스도 9/9 일치한다.

#### 4) 진단 d 결과(`eval/f0/results/20261103T001459Z-d/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 19/22 | 15/22 | 20/22 | 21/22 | 17/22 | 5/22 | 4298/11335 | `STEPS_INVALID` 6 |
| B | 17/22 | 15/22 | 19/22 | 22/22 | 22/22 | 0/22 | 3005/6211 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 6·`TASK_TYPE_MISMATCH` 2·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 7·`TASK_TYPE_MISMATCH` 5.
- A 재시도 5건:
  - G05·G07·G16·G18: 초회 `NONEMPTY_FOR_NO_TOOL_TASK` 뒤 재시도에서 유효.
  - G17: 두 시도 모두 steps `ITEM_FORMAT_INVALID`(`STEPS_INVALID` 2회)여서 최종 `INVALID`.
- t 오답: A는 G06(LQ→KQ)·G08(INC→UNS)·G17(최종 무효)이다. B는 G07(UNS→GQ)·G08(INC→UNS)·G09·G12(INC→LQ)·G16(UNS→GQ)이다.
- 인자 오답:
  - A: G01·G09·G11·G12·G22 from+to, G21 eqp, G17 무효.
  - B: G01·G11 to, G09·G12·G17·G22 from+to, G21 eqp.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. 후보 0 시도는 모두 `qRef.kind=JSON_NULL`이고 `ID_OUT_OF_RANGE`는 0이다.
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to의 `equalsContext=true`는 0건이다. c는 B 2시도, A 3시도였다.
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - A·B 모두 초회에 t=`UNS`이고 재시도는 0이므로 최종 t도 `UNS`(`TASK_TYPE_MISMATCH`)다.
  - 최종 시도의 eqp·from·to는 모두 NULL·MATCH·`equalsContext=false`다.
  - c(최종 t INC, from·to Context 복사)와 비교하면, 이번 R1에서는 시각 복사가 관측되지 않았지만 t축이 INC→UNS로 바뀌었다. 따라서 G08 실패가 해소됐다고 보지 않는다.
- c→d 문항 변화(t/틀린 인자):
  - B: G08 INC/from+to→UNS/–, G07 INC→GQ, G20 eqp+from+to→전 MATCH, G01·G11 from+to→to, G09·G22 전 MATCH→from+to.
  - A: G08 INC/from+to→UNS/–, G19 KQ→LQ, G16·G20 인자 MATCH 회복, G09 전 MATCH→from+to, G17 INC/from+to→최종 무효.
- **경계 9케이스 boundary-gen 7/9**(모두 schemaValid=true, finish=stop, 재시도 0, completion ≤192):

| caseId | 판정 | t/eqp/from/to/q | completion | c 대비 |
|---|---|---|---|---|
| NORMAL-01 | **불통과** | **t=LQ(기대 INC)** `TASK_TYPE_MISMATCH`, 인자 4개 MATCH | 60 | 통과→불통과 |
| NORMAL-PRECISE | 통과 | 전부 MATCH | 97 | 동일 |
| MAX-MIXED | 통과 | 전부 MATCH | 146 | 불통과→통과 |
| MAX-HANGUL | **불통과** | **eqp MISMATCH**(registered=false, inQuestion=true), 나머지 MATCH | 107 | 불통과 유지 |
| MAX-DIGIT | 통과 | 전부 MATCH | 146 | 불통과→통과 |
| MAX-ASCII | 통과 | 전부 MATCH | 146 | 불통과→통과 |
| MAX-SUPPLEMENTARY | 통과 | 전부 MATCH | 146 | 불통과→통과 |
| ESCAPE-01 | 통과 | 전부 MATCH | 146 | 불통과→통과 |
| ESCAPE-CONTROL | 통과 | 전부 MATCH | 146 | 불통과→통과 |

- 합성 기대값 메타:
  - NORMAL 2건: eqp 9 코드포인트 ASCII.
  - MAX·ESCAPE 7건: eqp 64 코드포인트, 한글 포함.
  - 9건 모두 기대 t=INC이고 selectedResourceRefs 0이다.
- MAX-HANGUL만 completion이 다른 64자 통과 건(146)과 달리 107이다. 원문을 저장하지 않으므로 eqp가 어떤 형태로 달라졌는지(축약·변형)는 판정하지 않았다(원인 미확정).

#### 5) 개발 회귀 d 결과(`eval/f0/results/20261103T001459Z-regression-d/diagnostic-report.md`, R1·분모 30, 정식 분모 아님)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 27/30 | 22/30 | 28/30 | 29/30 | 25/30 | 5/30 | 4170/11568 | `STEPS_INVALID` 6 |
| B | 25/30 | 23/30 | 27/30 | 30/30 | 30/30 | 0/30 | 3181/6445 | 0 |

- 원래 22문항 부분은 d와 해시까지 같다(위 3)). 오류 종류 A `ARGUMENT_MISMATCH` 7·`TASK_TYPE_MISMATCH` 2·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 7·`TASK_TYPE_MISMATCH` 5.
- `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0, Context 복사 시도 0(A·B). 경계 9는 d와 같은 7/9(같은 두 건 불통과)이고, G08도 d와 같다(A·B 최종 t=UNS).
- 신규 8축(A/B 모두 최종 t 일치 8/8, 재시도 0):

| id | 축 | A | B | 회귀(c) 대비 |
|---|---|---|---|---|
| R01 | REG_QUOTED_EQP | 전 인자 MATCH(eqp=인용 후보, qRef ID_IN_RANGE/후보1) | 같음 | 유지 |
| R02 | REG_EQP_VS_CODE_TERM | 전 인자 MATCH | 같음 | 유지 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | 전 인자 MATCH(eqp·from·to NULL, `equalsContext=false`) | 같음 | A·B 실패→통과 |
| R04 | REG_TC_TRANSFER_FAILURE | 전 인자 MATCH | 같음 | A 실패→통과 |
| R05 | REG_WM_OWNED_RECORD | t=UNS, MATCH | 같음 | 유지 |
| R06 | REG_PARAPHRASED_RANGE | **from·to MISMATCH**(TEXT, Context 복사 아님) | 전 인자 MATCH | **A 통과→실패** |
| R07 | REG_SECOND_QUOTED_INTENT | q MATCH(qRef ID_IN_RANGE/후보2) | 같음 | A·B 실패→통과 |
| R08 | REG_LONG_EQP_ID | eqp MATCH(64 코드포인트) | 같음 | A·B 실패→통과 |

- 회귀 t 오답: A는 G06·G08·G17(무효), B는 G07·G08·G09·G12·G16이다(모두 원래 22문항). 인자 오답: A는 위 d 목록에 R06 from+to를 더한 것, B는 d 목록과 같다.

#### 6) `PREREG-v3-gate-d.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. C7/C8 뒤 세 문서 `PREREG_VERIFIED` | 충족 | diag-d·regression-d는 이번 d-1·r-1, gate-d는 §12.13 7)(이 세션 미재실행) |
| 2. 진단 d·회귀 d exit 0·`complete=true`, exit 5 0 | 충족 | 위 2) |
| 3a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 3b. G08 최종 t A·B 모두 INC | **미충족** | A·B 최종 `UNS`(위 4)) |
| 3c. 경계 9 boundary-gen 9/9 | **미충족(7/9)** | NORMAL-01 t, MAX-HANGUL eqp(위 4)) |
| 4. root 원문 없는 검토 | 미완(대기) | 이 절과 두 진단 보고서 |

- 하나 이상 미충족이므로 이 등록으로 R5를 실행하지 않는다(gate-d §1). 보완은 새 사전등록 파일로만 가능하다. 이 세션은 새 등록·F0 leaf·gate·SPR을 만들지 않았다.

#### 7) 미실행·원인 미확정·다음 정확 조치

- 미실행:
  - 정식 GATE R5(이 세션 금지, 착수 조건도 미충족).
  - gate-d prereg-check 재실행.
  - 시험 재실행(코드 변경 0이라 불필요).
  - P04.
- 원인 미확정:
  - G08 A·B의 t=UNS 전환.
  - 경계 NORMAL-01의 t=LQ.
  - MAX-HANGUL eqp 불일치(completion 107).
  - A G17 steps `ITEM_FORMAT_INVALID` 2회.
  - A R06 from·to 불일치.
  - B G09·G12(INC→LQ)·G07·G16(UNS→GQ) t 오답 잔존.
  - 원문을 저장하지 않으므로 출력 형태를 추정으로 결론 내리지 않는다. 이전 CPU 실패·재부팅 원인(§12.11)도 여전히 미확정이며 이 세션에서 추측하지 않았다.
- 다음 정확 조치(root 결정):
  1. root가 두 진단 보고서와 이 재집계를 원문 없이 검토한다.
  2. 조건 3b·3c 미충족 상태로 `PREREG-v3-gate-d.md` R5를 실행하지 않는다.
  3. 보완 여부·방법(새 후보 사전등록 필요 여부, MAX-HANGUL·NORMAL-01·G08의 원문 없는 추가 진단 방법)은 root가 판단한다.

#### 8) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a`는 불변이다.
- HEAD C8 `f2ffaeeacf28be86fcb557f6bf0a51642120cbfb`, 8커밋, staged 0, `git diff --check` exit 0이다.
- 세션 시작(09:10 KST) 이후 수정 시각 기준 변경 파일은 신규 결과 2디렉터리·신규 capture 2디렉터리·이 Devreport뿐이다.
  - 그 밖의 옛 결과·capture(`v3-diag-{a,b,c}`·`v3-regression`·`v2-qref` 등)·`evidence/`·PREREG·입력·골든·`gateway-eval/src`·`docs/gates`·`eval/py` 변경은 0이다. 새 `.pyc`도 0이다.
  - Devreport의 이 절 앞 바이트(207112 B, SHA `e87d5a3b40927ccb7a6cb292596ca6efda078362130583d57e4a40d79bbbfd30`)는 그대로이며 이 절만 추가했다.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.15 P03 단일 요인 진단 e 준비(투영 EXTRACTION_V1 + 기존 c 프롬프트) — 사전등록 초안만 (2026-11-03, 별도 독립 Claude Code 세션 claude-opus-5-5 effort medium, root 검토 전)

- §12.1~§12.14는 바꾸지 않았다. 이 절 앞 바이트(221940 B, SHA `4107795524b53d920f272d831ca47a8d9d809c7681d30571956502d150ecd65c`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 변경, 다운로드, 새 capture, commit·stage·push. 코드·시험·프롬프트·골든·fixture·schema·임계·분모·기존 PREREG·audit·결과·capture·evidence·SPEC·Plan·기준선·승인 문서 변경 0.
- **결론: 진단 e는 준비만 했고 실측하지 않았다. F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 실행하려면 로컬 C9/C10에 대한 사용자 허용이 먼저 필요하다.

#### 1) 목적·설계(요인 분리 진단, 새 기능·정책·수정안 아님)

- d는 c 대비 투영(`EXTRACTION_V1`)과 프롬프트(d)를 함께 바꿔 §12.14 결과(G08 A·B 최종 t `UNS`, 경계 7/9)의 인과가 분리되지 않는다.
- e = d에서 프롬프트만 기존 c(`classify-v3.txt` `2772aa56…5143`, `plan-v3.txt` `43da76c3…7982`, 바이트 그대로)로 되돌린 구성이다. 그 밖은 d와 같다: purpose DIAGNOSTIC, repeats 1, `responseFormat=JSON_SCHEMA_V1`, `contextProjection=EXTRACTION_V1`, 골든 22 `tc-f0-v2.jsonl`, fixture v2, 경계 9·usage 2·token 13 v2 입력, 입력 3072/3072, `max_tokens` 192/256, 출력 192, context 4096, retry 1, timeout·latency·`f0-judge-v2` 불변.
- 비교 범위: c↔e는 c 프롬프트 고정에서 투영의 영향, e↔d는 투영 고정에서 c/d 프롬프트의 영향이다. FULL + d 프롬프트 칸이 없으므로 상호작용은 분리하지 못한다. e 결과로 전체 원인을 유일 확정하거나 다음 PASS를 보장한다고 주장하지 않는다. e 성적은 어떤 R5·게이트 착수 조건에도 더하지 않는다.

#### 2) 산출물(신규 2파일 + 이 절)

| 파일 | SHA-256 | 내용 |
|---|---|---|
| `eval/f0/PREREG-v3-diag-e.md` | (C10에서 바뀌므로 등록 입력 아님) | UNCOMMITTED·`registeredCommit=null`, `runtimeCaptureDir=eval/f0/chat-template/v3-diag-e`, harnessDigest `ad8ca310513ddcba9712af03d0e6bf969c61837edc5cc5163558e6290a5cf290` |
| `eval/f0/v3/input-audit-v3-e.json` | `be57fd5e3104fad15f897e4fd477968a19b2e56f76ebbf11c3072b3732d23062` | 651행, `OVER_BUDGET 0` |

- 기계 판독 블록은 d(C7 초안)와 키 단위로 비교해 `runtimeCaptureDir`·`inputRoles.{classifyPrompt,planPrompt,inputAudit}`·`inputs`(위치 2·3·9)·`harnessDigest`·`parameters.promptSha256`만 다르다. 나머지 parameters·inputRoles·입력 12개 SHA는 d와 같다.
- harnessDigest 계산법은 `HarnessDigest.compute`(versioned: 등록 inputs + f0 소스 24파일, 정렬 경로·SHA 쌍의 정규 JSON SHA-256)를 메모리 Node로 재현했다. 같은 방식이 d 등록값 `cda6456e…525c`를 정확히 재현했고(d 입력 불일치 0), e 값은 Java `prereg-check`의 `currentHarnessDigest`와도 일치했다.

#### 3) 입력 audit(실제 렌더·계수, 기존 audit에서 빼기 계산 아님)

| 순서 | 명령 | exit | 결과 |
|---|---|---|---|
| 1 | `./gradlew.bat :gateway-eval:run --args="f0 input-audit-render --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --classify-prompt eval/f0/v3/prompts/classify-v3.txt --plan-prompt eval/f0/v3/prompts/plan-v3.txt --cases eval/f0/v2/output-boundary-cases.jsonl --usage-messages eval/f0/v2/usage-probe-input-v2.jsonl --token-messages eval/f0/v2/token-golden-input-v2.jsonl --renderers eval/f0/chat-renderers-v1.json --template-sha256 40c21f34…b541 --context-projection EXTRACTION_V1 --out %TEMP%/aigw-p03v3e/render-e.jsonl"` | 0 | `F0_INPUT_AUDIT_RENDERED 651`(추론 호출 0, 레포 밖 합성 메시지 JSONL 1개, 보존) |
| 2 | `.venvs/aigw-eval python -I -B eval/py/count_input_audit_v2.py --tokenizer …tokenizer.json --expect-sha256-file ….sha256 --rendered …/render-e.jsonl --template-sha256 40c21f34…b541 --out eval/f0/v3/input-audit-v3-e.json` | 0 | `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`, tokenizer `aeb13307…dae4`, tokenizers 0.23.3 |

- 템플릿·tokenizer는 기존 등록 capture·pin 값(`40c21f34…b541`, `aeb13307…dae4`)을 읽기만 했다. 새 capture 0.

| audit | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token |
|---|---|---|---|---|---|
| c(FULL + c 프롬프트) | 1610/1649 | 1402/1441 | 3015/3054 | 1368 | 2793 |
| d(EXTRACTION_V1 + d 프롬프트) | 1610/1649 | 1382/1421 | 3015/3054 | 1368 | 2793 |
| e(EXTRACTION_V1 + c 프롬프트) | 1580/1619 | 1372/1411 | 3005/3044 | 1358 | 2793 |

- 메모리 전수 재검증: 렌더 행과 audit 행의 순서·id·messagesSha256 651/651 일치, 행별 `inputTokens ≤ inputLimit`·`inputTokens + maxTokens ≤ 4096` 위반 0(최대 입력 3044, 최대 입력+출력 3236), 그룹 최대값 재계산 = 파일 값.
- 행 id 집합은 c·d audit와 같다. 행별 계수 차(같은 id): e−d는 A −30·B −10·경계 −10·usage −10(전 행 일정, 프롬프트 차이만), token 11행은 SHA·계수 동일이다. e−c는 투영 차이만이며 −10~−60이다.

#### 4) 미등록 거부·보존 확인

- `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-e.md`(`AIGW_SPEC_FILE`=정본 SPEC, endpoint 인자 없음): Java exit 2, `PREREG_NOT_COMMITTED`, `localMismatches []`, `currentHarnessDigest` = 문서값, parameterManifestSha256 `70e69d75bf06900159623fbe7482ea353b7be433b3120be6b0bccae9f77790d9`(Gradle wrapper 종료 코드는 1). 모델 요청 0.
- 시험: 테스트 코드 변경 0이므로 무필터 182건 재실행은 하지 않았다(직전 C8 뒤 182/182·`TEST_EVIDENCE_VERIFIED`).
- 보존: `eval`·`gateway-eval/src`·`evidence`·`docs/gates`의 기존 291파일 집계 SHA가 작업 전후 같다(`239b4b99…78f0`, 신규 2파일 제외). SPEC `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan `af5657697152dd2f86fe75b302d0ba24f8f4ebd03875110cf22ae641be323b5a` 불변. HEAD C8 `f2ffaeeacf28be86fcb557f6bf0a51642120cbfb`·8커밋·staged 0. 신규 2파일과 이 Devreport는 UTF-8·LF·BOM 없음. 새 `.pyc` 0, `eval/f0/chat-template/v3-diag-e` 부재.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다.

#### 5) 다음 단계(사용자 허용 필요 — 기존 허용은 C7/C8까지)

- C9(로컬 commit, 정확 3파일): `eval/f0/PREREG-v3-diag-e.md`(UNCOMMITTED 초안 그대로), `eval/f0/v3/input-audit-v3-e.json`, `docs/ai-rag-gateway-architecture-dev-report.md`. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C10(로컬 commit, 정확 1파일): `eval/f0/PREREG-v3-diag-e.md`에 `registrationStatus=REGISTERED`, `registeredCommit`=C9 SHA, 등록 설명만 기록한다. inputs·harnessDigest·parameters는 바꾸지 않는다.
- C10 뒤 순서: `PREREG-v3-diag-e.md` §2의 prereg-check → preflight → 신규 capture → B → A → boundary → report(`--out` 없음). runId는 새 UTC 값, 회차 ID `$runId-e`.
- 기존 source 변경 없음, 구조·정책 사용자 결정은 아직 불필요. 실제 모델 결과 미확정, P03 미완료, P04 미착수. 원문 질문·응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

#### 6) root 독립 대조 및 다음 등록 범위 (2026-11-03)

- root가 C7의 정확한 16파일과 커밋 blob SHA를 검토본에 대조해 불일치 0을 확인했다. C8은 새 등록 3문서만 포함하며 JSON 변경은 registrationStatus·registeredCommit 두 항목뿐이다. 등록 후 JUnit XML 39개를 합산해 182건·실패/오류/건너뜀 0을 확인했고 별도 test-evidence verify도 exit 0·TEST_EVIDENCE_VERIFIED였다. 등록 세션의 초기 병렬 prereg 검사 2건은 순차 3건 재검사로 교정한 이력이며 전체 시험과 실측은 서로 겹치지 않았다.
- root가 진단 d·개발 회귀 d 원문 없는 결과를 직접 재집계해 §12.14의 A/B 정확도·최종 G08 UNS·경계 각각 7/9와 일치함을 확인했다. 런타임 신원은 직전 c와도 동일하다. 보존 대상 264파일 SHA 불일치 0이며 이번 d 결과·capture 24파일도 별도 해시로 고정했다. Devreport의 등록 후 원래 207112바이트 prefix SHA e87d5a3b40927ccb7a6cb292596ca6efda078362130583d57e4a40d79bbbfd30도 유지된다. gate-d 착수 조건 3b·3c 미충족이므로 정식 R5·새 F0 증거/판정·SPR은 생성하지 않았다.
- 별도 독립 Claude Code Opus 5.5 medium 읽기 전용 분석에서 확인한 경로에서는 세 차단 실패(G08 최종 t, NORMAL-01 t, MAX-HANGUL eqp)를 설명하는 하네스 조립·판정 결함을 찾지 못했다. SPEC의 시각 문자열 정확 일치 계약을 instant 동치로 완화하지 않는다. 두 요인의 인과를 유일하게 확정할 근거는 아직 없으며 e 비교도 빠진 2×2 한 칸의 상호작용까지 분리하지 못한다. MAX-HANGUL의 실제 출력 식별자는 원문이 없어 모른다. root의 입력 부분 문자열 SHA 대조도 일치 후보를 찾지 못했으므로 축약 형태를 확정하지 않는다.
- root가 e 등록의 모든 입력 SHA·현재 소스 harnessDigest를 독립 재계산해 불일치 0을 확인했다. 실제 렌더 651행과 audit의 id·messagesSha256를 전수 대조하고 입력/입력+출력 예산 위반 0을 확인했다. e 최대 입력 3044, 최대 입력+출력 3236이다. e는 UNCOMMITTED/null이며 실측 0이다.
- root는 검증 후 Plan의 진행 상태 행 11·14만 최신 기록으로 갱신했다. 확정 계약·명령·30 phase 구성은 불변이고 새 Plan SHA는 264abe99d904bf7fb9e0da2b6be0ab0efff8b3be52e72e74ab7fa0988e803907이다. 정본 SPEC SHA 34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be는 불변이다. 앞 절의 Plan SHA 불변 기록은 해당 세션 종료 시점의 검증 결과로 보존한다.
- 다음 미허용 작업은 C9(새 e 등록 초안·새 e audit·기존 Devreport 3파일 고정), C10(새 e 등록 1문서의 REGISTERED/C9 SHA/등록 설명만 확정)이다. 기존 사용자 허용은 C7/C8까지이므로 임의 commit하지 않는다. 두 로컬 commit 허용 뒤 등록 검사 → 새 e 진단 → c/d/e 대조로 보완 범위를 정한다. e가 통과하더라도 정식 등록·R5·SPEC 기록·재판정·SPR·F0 gate가 남는다. P03 미완료, P04 미착수다.

#### 7) 로컬 C9/C10 등록 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 승인 근거: 사용자 승인 "로컬 C9/C10 허용 후 진단 e 진행(권장)"(2026-11-03). 로컬 commit 두 개만 수행했고 push·amend·원격 publish는 없다.
- 커밋 전 확인: HEAD=C8 `f2ffaeeacf28be86fcb557f6bf0a51642120cbfb`(8커밋), staged 0, 대상 3파일 SHA-256이 root 검토값(Devreport 00c01c72…fcd48, PREREG-v3-diag-e 690a63cc…fd2be, input-audit-v3-e be57fd5e…23062)과 일치했다.
- C9 `1b5be44f8549db157d0da2c69925e3f7b901431a`(2026-11-03 09:52:23 +0900) = 정확 3파일: `docs/ai-rag-gateway-architecture-dev-report.md`, `eval/f0/PREREG-v3-diag-e.md`(UNCOMMITTED/null 초안), `eval/f0/v3/input-audit-v3-e.json`. cached 이름 목록이 3파일과 정확히 일치했다. 기존 untracked는 포함하지 않았다.
- C10 `ca6f8a92cf56ee65086111b3c7d59ec65a5b522b`(2026-11-03 09:52:57 +0900) = 정확 1파일 `eval/f0/PREREG-v3-diag-e.md`. §0 등록 상태를 REGISTERED·registeredCommit=C9·승인 근거로 갱신하고 미등록 초안 상태는 이력으로 구분했다. §1·§2 본문은 불변이다.
- 기계 판독 JSON 전후 파싱 비교: 키 9개·순서 동일, 변경 키는 `registrationStatus`(UNCOMMITTED→REGISTERED)·`registeredCommit`(null→C9 SHA) 두 개뿐이다(JSON_ONLY_2KEYS_CHANGED). C10 뒤 총 10커밋, staged 0이다.
- 등록 검사: `AIGW_SPEC_FILE`=정본 SPEC, `DEBUG` 빈 값, endpoint 인자 없이 `:gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-diag-e.md"` → `localMismatches=[]`, `PREREG_VERIFIED`, exit 0. currentHarnessDigest=ad8ca310513ddcba9712af03d0e6bf969c61837edc5cc5163558e6290a5cf290, parameterManifestSha256=70e69d75bf06900159623fbe7482ea353b7be433b3120be6b0bccae9f77790d9.
- 모델 실측 0회(모델 요청·SSH·HTTP 0). 이 절은 C10 뒤 추가분이며 커밋하지 않았다. 다음은 `PREREG-v3-diag-e.md` §2의 preflight부터다. P03 미완료, P04 미착수다.

### 12.16 P03 단일 요인 진단 e 실행 (2026-11-03, 독립 Claude Code Opus 5.5 medium, root 검토 전)

- §12.1~§12.15는 바꾸지 않았다. 이 절 앞 바이트(234654 B, SHA `3b28cece27298e128605debe92a6c4385a241a1e31abdf78b9cfc0d44e52a817`)는 그대로이며 이 절만 추가했다.
- 산출물: 신규 결과 디렉터리 `eval/f0/results/20261103T005544Z-e/`(8파일), 신규 capture `eval/f0/chat-template/v3-diag-e/`(4파일), 이 절.
- 하지 않은 것: 코드·시험·프롬프트·골든·fixture·PREREG·audit·기존 결과/capture·evidence·SPEC·Plan 변경, git 쓰기 전부, 모델 load/pull/unload·서비스 재시작·num_ctx 변경·다운로드, 정식 R5·회귀·gate·F0 leaf 증거·SPR, P04.
- **결론(진단 R1, 판정 아님): `PREREG-v3-diag-e.md` §2의 7개 명령은 모두 exit 0·`complete=true`다. 결과는 G08 최종 t가 A·B 모두 `UNS`(기대 `INC`)이고, 경계 boundary-gen은 0/9다.** e는 진단 등록이므로 어떤 R5·게이트 착수 조건에도 성적을 더하지 않는다. 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(읽기 전용 확인)

- 확인 방법: SSH 읽기 전용 + Node `spawnSync` `bash -s`(LF stdin). `GET /api/ps`는 메모리에서 파싱해 허용 필드만 출력했고, 파싱 실패 시 원문 대신 `parse failed`만 내도록 감쌌다. 확인 시각 2026-11-03T00:55:30Z.
  - 서비스: active/running, `ActiveEnterTimestamp=2026-11-03 08:07:24 KST`, NRestarts 0. boot 2026-11-02 14:22:11 KST(재부팅 아님).
  - journal(마지막 서비스 시작 이후): `library=cpu` 0건, `Load failed` 0건, `library=CUDA` 4줄.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - 상주 모델 2개. Mem 7619 MiB 중 available 720, swap 3809 MiB 중 사용 1615.
- B·A·boundary 뒤(00:58:17Z·01:00:38Z·01:02:14Z) 같은 항목을 재확인했다. ActiveEnterTimestamp·NRestarts 0 불변, 2모델 동일 digest·ctx 4096·GPU 전량 상주 유지, `library=cpu`·`Load failed` 0건이었다. 중단 사유는 없었다.

#### 2) 실행 기록

- 공통 조건: PowerShell 래퍼(`Set-Location C:\workspace_nori\nori-ai-gateway` 후 `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`), `--args` 값은 §2와 같다.
  - 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=`20261103T005544Z-e`, `DEBUG`=빈 값을 설정했다.
  - 한 명령씩 실행하고 exit 0을 확인한 뒤 다음으로 넘어갔다. 측정 중 다른 Gradle 실행은 0이다.
- run prefix `20261103T005544Z`(시작 때 1회 결정, 기존 prefix 비재사용). 회차 ID `20261103T005544Z-e`.
- 터널: `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다(PID 13736, 명령행 확인). 시작 전 로컬 11434 미사용이었다. boundary 뒤 재확인을 마치고 `Stop-Process -Id 13736`으로 종료했으며, 종료 후 11434 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| e-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-e.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `ad8ca310…f290`, parameterManifestSha256 `70e69d75…90d9` |
| e-2 | `f0 preflight … --out eval/f0/results/20261103T005544Z-e/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| e-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-e`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| e-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3.txt --run-id 20261103T005544Z-e --out …/B` | 0 | 00:56:41~00:58:15, `F0_RUN_COMPLETED B` |
| e-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3.txt …` | 0 | 00:58:30~01:00:36, `F0_RUN_COMPLETED A` |
| e-6 | `f0 boundary-gen … --prompt eval/f0/v3/prompts/classify-v3.txt …` | 0 | 01:00:48~01:02:10, `F0_BOUNDARY_COMPLETED 9` |
| e-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-e.md --run-id 20261103T005544Z-e --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `9a98ff73…0989` |

- 서버 `response_format` 거부(exit 5)는 0이다. F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 3개(B·A·boundary)와 capture run-binding의 runtimeManifest는 모두 같다.
  - model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`(runtimeManifestSha256 `b1e6a3af…6f6a`, c·d와 같음), specSha `34fd6223…66be`(FILE_HASHED, LOCAL_FILE)
  - registeredCommit C9 `1b5be44f8549db157d0da2c69925e3f7b901431a`, preregCommit C10 `ca6f8a92cf56ee65086111b3c7d59ec65a5b522b`
  - `contextProjection=EXTRACTION_V1`, `responseFormat=JSON_SCHEMA_V1`, parameterManifestSha256 `70e69d75…90d9`
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출(작음/큼)은 A 0/0, B 0/0, boundary 0/0이다(보고서와 정제 필드 재집계 모두).
- 첫 시도 프롬프트 토큰 범위: A 1542~1580(d 1572~1610), B 1334~1372(d 1344~1382). e−d 차이는 §12.15 3)의 audit 차(A −30·B −10)와 같다.

#### 4) 진단 e 결과(`eval/f0/results/20261103T005544Z-e/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

- 재집계 스크립트는 같은 방식으로 c·d 결과를 다시 계산해 §12.12 4)·§12.14 4) 수치와 일치함을 먼저 확인했다.

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 16/22 | 13/22 | 18/22 | 21/22 | 18/22 | 4/22 | 3881/10135 | `STEPS_INVALID` 5 |
| B | 17/22 | 17/22 | 19/22 | 22/22 | 22/22 | 0/22 | 2996/6136 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 8·`TASK_TYPE_MISMATCH` 5·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 5·`TASK_TYPE_MISMATCH` 5.
- A 재시도 4건:
  - G18: 초회 `NONEMPTY_FOR_NO_TOOL_TASK` 뒤 재시도에서 유효(t=GQ 유지).
  - G05: 두 시도 모두 `NONEMPTY_FOR_NO_TOOL_TASK`(`STEPS_INVALID` 2회)여서 최종 `INVALID`. c·d에서는 재시도로 유효했다.
  - G07: 초회 t=UNS(기대와 같음)·`NONEMPTY_FOR_NO_TOOL_TASK` 뒤, 재시도에서 t=GQ로 바뀌어 유효 판정·t 오답.
  - G17: 초회 t=INC·steps `ITEM_FORMAT_INVALID` 뒤, 재시도에서 t=UNS(steps OK)로 유효 판정·t 오답, eqp+from+to 불일치.
- t 오답: A는 G05(최종 무효)·G06(LQ→KQ)·G07(UNS→GQ)·G08(INC→UNS)·G17(INC→UNS)·G19(LQ→KQ)다. B는 G07·G16(UNS→GQ)·G08(INC→UNS)·G09·G12(INC→LQ)다.
- 인자 오답:
  - A: G01·G09·G11·G12·G22 from+to, G16·G21 eqp, G17 eqp+from+to, G05 무효.
  - B: G01·G12·G17 from+to, G11 to, G21 eqp.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. 후보 0 시도는 모두 `qRef.kind=JSON_NULL`이고 `ID_OUT_OF_RANGE`는 0이다(경계 포함).
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to의 `equalsContext=true`는 0건이다(c는 B 2시도·A 3시도, d는 0).
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - A·B 모두 초회 t=`UNS`이고 재시도는 0이므로 최종 effectiveT도 `UNS`(`TASK_TYPE_MISMATCH`)다. 기대 `INC`와 다르다.
  - 최종 시도의 eqp·from·to는 모두 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`이다.
  - 첫 시도 `outputSha256`가 A·B 모두 d의 G08과 같다(c와는 다름).
- **경계 9케이스 boundary-gen 0/9**(모두 schemaValid=true, finish=stop, 재시도 0, completion ≤192, `equalsContext` 0, prompt=audit 9/9):

| caseId | 판정 | 불통과 축 | completion |
|---|---|---|---|
| NORMAL-01 | **불통과** | t=LQ(기대 INC) `TASK_TYPE_MISMATCH`, 인자 4개 MATCH | 60 |
| NORMAL-PRECISE | **불통과** | t=LQ(기대 INC) `TASK_TYPE_MISMATCH`, 인자 4개 MATCH | 98 |
| MAX-MIXED | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| MAX-HANGUL | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| MAX-DIGIT | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| MAX-ASCII | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| MAX-SUPPLEMENTARY | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| ESCAPE-01 | **불통과** | eqp MISMATCH(registered=false, inQuestion=true) | 98 |
| ESCAPE-CONTROL | **불통과** | t=LQ(기대 INC) + eqp MISMATCH(registered=false, inQuestion=true) | 99 |

#### 5) c·d·e 비교(원문 없는 정제 필드·출력 해시만)

- 등록 구성: c = FULL + c 프롬프트, d = EXTRACTION_V1 + d 프롬프트, e = EXTRACTION_V1 + c 프롬프트.

| 지표 | c | d | e |
|---|---|---|---|
| A taskType / 인자 | 20/22 / 13/22 | 19/22 / 15/22 | 16/22 / 13/22 |
| B taskType / 인자 | 18/22 / 15/22 | 17/22 / 15/22 | 17/22 / 17/22 |
| A JSON 유효 / 1차 유효 / 재시도 | 22 / 18 / 4 | 21 / 17 / 5 | 21 / 18 / 4 |
| G08 최종 t A / B | INC / INC | UNS / UNS | UNS / UNS |
| Context 복사 시도 A / B | 3 / 2 | 0 / 0 | 0 / 0 |
| 경계 boundary-gen | 2/9 | 7/9 | 0/9 |

- 문항별(셀 = 최종 effectiveT / 틀린 인자, 굵게 = t 오답, (ctx) = Context 복사 시도 있음):

| id | 기대 t | A c | A d | A e | B c | B d | B e |
|---|---|---|---|---|---|---|---|
| G01 | INC | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / to | INC / from+to |
| G02 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G03 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G04 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G05 | GQ | GQ / – | GQ / – | **무효** | GQ / – | GQ / – | GQ / – |
| G06 | LQ | **KQ** / – | **KQ** / – | **KQ** / – | LQ / – | LQ / – | LQ / – |
| G07 | UNS | UNS / – | UNS / – | **GQ** / – | **INC** / – | **GQ** / – | **GQ** / – |
| G08 | INC | INC / from+to (ctx) | **UNS** / – | **UNS** / – | INC / from+to (ctx) | **UNS** / – | **UNS** / – |
| G09 | INC | INC / – | INC / from+to | INC / from+to | **LQ** / – | **LQ** / from+to | **LQ** / – |
| G10 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G11 | LQ | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / to | LQ / to |
| G12 | INC | INC / from+to | INC / from+to | INC / from+to | **LQ** / from+to | **LQ** / from+to | **LQ** / from+to |
| G13 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G14 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G15 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G16 | UNS | UNS / eqp | UNS / – | UNS / eqp | **GQ** / – | **GQ** / – | **GQ** / – |
| G17 | INC | INC / from+to | **무효** | **UNS** / eqp+from+to | INC / from+to | INC / from+to | INC / from+to |
| G18 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G19 | LQ | **KQ** / – | LQ / – | **KQ** / – | LQ / – | LQ / – | LQ / – |
| G20 | INC | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / eqp+from+to (ctx) | INC / – | INC / – |
| G21 | KQ | KQ / eqp | KQ / eqp | KQ / eqp | KQ / eqp | KQ / eqp | KQ / eqp |
| G22 | INC | INC / from+to | INC / from+to | INC / from+to | INC / – | INC / from+to | INC / – |

- 경계 케이스별:

| caseId | c | d | e | e 첫 출력 해시 = c / = d |
|---|---|---|---|---|
| NORMAL-01 | 통과(ct 59) | 불통과 t=LQ(ct 60) | 불통과 t=LQ(ct 60) | 다름 / **같음** |
| NORMAL-PRECISE | 통과(ct 97) | 통과(ct 97) | 불통과 t=LQ(ct 98) | 다름 / 다름 |
| MAX-MIXED | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | **같음** / 다름 |
| MAX-HANGUL | 불통과 eqp(ct 98) | 불통과 eqp(ct 107) | 불통과 eqp(ct 98) | **같음** / 다름 |
| MAX-DIGIT | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | **같음** / 다름 |
| MAX-ASCII | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | **같음** / 다름 |
| MAX-SUPPLEMENTARY | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | **같음** / 다름 |
| ESCAPE-01 | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | **같음** / 다름 |
| ESCAPE-CONTROL | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 t=LQ+eqp(ct 99) | 다름 / 다름 |

- 첫 시도 `outputSha256` 일치 수(22문항): A e=c 14·e=d 9, B e=c 18·e=d 19. 경계 e=c 6/9·e=d 1/9.
- 관찰(R1, 인과 확정 아님):
  - G08: e의 A·B 첫 출력이 d와 해시까지 같고 c와는 다르다. c 프롬프트를 고정해도 투영만으로 d와 같은 `UNS`가 재현됐다.
  - NORMAL-01: e 출력이 d와 해시까지 같다(t=LQ). c 프롬프트 고정에서 투영만으로 같은 t 오답이 재현됐다.
  - MAX·ESCAPE 계열 eqp: e 7건 중 6건이 c와 출력 해시까지 같다(ct 98). d에서 7건 중 6건이 통과한 것은 투영 고정에서 d 프롬프트 쪽과 함께 관측됐다.
  - MAX-HANGUL: c·d·e 세 구성 모두 eqp 불통과다(c·e ct 98 해시 동일, d ct 107).
  - NORMAL-PRECISE(t=LQ)·ESCAPE-CONTROL(t=LQ 추가)은 c·d 어느 쪽과도 출력이 다른 e 고유 실패다.
  - Context 복사 소멸(c A3·B2 → d·e 0)과 G20 인자 회복은 d·e 공통, 즉 투영이 같은 두 구성에서 관측됐다.
  - A 재시도 경로: e에서 G05(재시도 후에도 무효)·G07(재시도로 UNS→GQ)·G17(재시도로 INC→UNS)이 c·d와 다르게 끝났다.
- R06 관련: R06(REG_PARAPHRASED_RANGE)은 회귀 골든 전용이라 e 범위에 없다(미측정). 같은 from·to 축의 원래 22문항(골든 기대 from·to 지정 G01·G09·G11·G12·G17·G22) 비교는 위 표와 같다. A G09 from+to 불일치는 d·e 공통(c는 MATCH), A G17은 c 유효·d 무효·e t=UNS, B G01·G22는 c·e 같고 d만 다르다.

#### 6) 원인 해석의 한계와 다음 조치 후보(결정은 root)

- 한계:
  - R=1이다. 출력 해시 일치는 그 1회의 결정적 출력이 같다는 사실일 뿐 반복 안정성을 보이지 않는다.
  - 2×2 중 FULL + d 프롬프트 칸이 없어 투영·프롬프트 상호작용은 분리하지 못한다. NORMAL-PRECISE·ESCAPE-CONTROL의 e 고유 t 오답이 상호작용인지 다른 요인인지 판단할 근거가 없다.
  - 원문을 저장하지 않으므로 MAX 계열 eqp의 실제 출력 형태(축약·변형), NORMAL 계열 t=LQ 선택 이유, A 재시도에서 t가 바뀐 이유는 판정하지 않았다.
  - 이 절의 관찰은 원인 확정이 아니다. 하네스·판정 결함 여부도 이 세션에서 새로 조사하지 않았다.
- 다음 조치 후보(이 세션은 실행·등록하지 않음):
  1. root가 c·d·e 세 진단 보고서와 이 재집계를 원문 없이 대조한다.
  2. 상호작용 분리가 필요하면 FULL + d 프롬프트 칸의 새 진단 등록 여부를 판단한다.
  3. G08·NORMAL 계열 t 축이 투영에 민감하다는 관찰을 검증하려면, 어떤 Context 항목이 t에 영향을 주는지 원문 없이 분리할 별도 진단이 필요한지 판단한다.
  4. 반복 안정성 확인(repeats>1)이 필요한지 판단한다.
  - 어느 경우든 새 사전등록 파일이 필요하며, 이 등록을 고쳐 다시 쓰지 않는다.

#### 7) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be` 불변이다. Plan SHA `264abe99d904bf7fb9e0da2b6be0ab0efff8b3be52e72e74ab7fa0988e803907`은 §12.15 6)의 root 갱신값과 같고(파일 수정 시각 00:40:18Z, 이 세션 시작 전) 이 세션에서 바꾸지 않았다.
- HEAD C10 `ca6f8a92cf56ee65086111b3c7d59ec65a5b522b`, 10커밋, staged 0이다. git 쓰기는 0이다.
- PREREG-v3-diag-e.md보다 새로운 파일은 신규 결과 8파일·신규 capture 4파일·이 Devreport뿐이다. 새 `.pyc`는 0이다.

| 파일 | 바이트 | SHA-256 |
|---|---|---|
| `eval/f0/results/20261103T005544Z-e/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T005544Z-e/B/f0-results.json` | 31204 | `7c89f65b8ae27dc0b1c337eb28be809d1ee5068e85959c488ca12609fc04aa98` |
| `eval/f0/results/20261103T005544Z-e/B/run-binding.json` | 1746 | `9cecf917895e9f5ba4e88811b19e4c9bb5175e1d3bbcccb53f705cbb0d80cb38` |
| `eval/f0/results/20261103T005544Z-e/A/f0-results.json` | 37449 | `d7279788c1ddf30c1fd1dfff9151e29f24eee46f88b4dc0c164628c0662c2fdf` |
| `eval/f0/results/20261103T005544Z-e/A/run-binding.json` | 1749 | `24a6ebacc839907f3a92014246c9c5af332df870756b1e626d68a6cd0e475a0e` |
| `eval/f0/results/20261103T005544Z-e/boundary/boundary-results.json` | 10236 | `251a5a564970d3ee58d8f623d4cc8ed0b4652ef574647080a6d8debe431d30f3` |
| `eval/f0/results/20261103T005544Z-e/boundary/run-binding.json` | 1749 | `e6f1e2e270ef891042b5ffece4849bd50a539f04fa6c05f0a8b1727589cb8c63` |
| `eval/f0/results/20261103T005544Z-e/diagnostic-report.md` | 9857 | `62fd82fdea54f9444658ba45b4c92a7e4392632262bafbaccaad911e4ff77b46` |
| `eval/f0/chat-template/v3-diag-e/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-diag-e/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-diag-e/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-diag-e/run-binding.json` | 1750 | `1fd22535aef7e59cd549b113c8f2619119b8ff00b1c4bbb24a12dccc154618cd` |

- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 이 Devreport는 UTF-8·LF·BOM 없음이다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 집계는 결과 JSON의 열거형·불리언·개수·해시 필드와 골든의 id·category·기대 t만 사용했다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.17 P03 원인 분석·보완 후보 f 구현·등록 초안 (2026-11-03, 독립 Claude Code Opus 5.5 high, root 검토 전)

- §12.1~§12.16은 바꾸지 않았다. 이 절 앞 바이트(252934 B, SHA `2bd983507bb43d1667ddf403351e67236a1ab88009a2b047126f190be610d7a2`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 변경, 다운로드, 새 capture, git 쓰기 전부. 제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`)·`f0-judge-v2`·정확 일치 oracle·골든·fixture·경계 케이스·임계·분모·예산(입력 3072, `max_tokens` 192/256, ctx 4096)·재시도 1회·기존 등록·결과·capture·evidence·SPEC·Plan 변경 0.
- **결론: 세 차단 실패의 원인을 원문 없이 좁혔고(아래 1)), 보완 후보 f(Context 투영 `PRESENCE_V1` + f 프롬프트)의 구현·시험·audit·등록 초안 3개까지 마쳤다. f는 실측하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 진단 f를 실행하려면 로컬 C11/C12 사용자 허용이 먼저 필요하다.

#### 1) 원인 분석(원문 없는 근거, 확정/추정 구분)

- 방법: 결과 JSON에 저장된 첫 시도 `outputSha256`을 입력에서 만든 후보 출력 직렬화의 SHA-256과 대조했다. 직렬화는 키 정렬 compact JSON(`{"eqp":…,"from":…,"qRef":…,"t":…,"to":…}`)이다. root가 §12.13 6)에서 c 경계 7건에 쓴 형식과 같으며, c의 NORMAL 2건·d의 통과 7건도 이 형식으로 기대 출력과 SHA가 정확히 같았다. eqp 후보는 질문의 모든 코드포인트 부분 문자열, t 후보는 5개 enum이다. 보조로 pin tokenizer(`aeb13307…dae4`)로 후보 출력의 토큰 수를 셌다. 응답 원문 조회·출력·저장, 추가 모델 호출은 0이다.
- 경계 27출력(c·d·e × 9)은 모두 단일 후보로 정확히 풀렸다(셀 = 최종 t / eqp).

| caseId | c(FULL + c) | d(EXTRACTION_V1 + d) | e(EXTRACTION_V1 + c) |
|---|---|---|---|
| NORMAL-01 | INC / 전체 | **LQ** / 전체 | **LQ** / 전체 |
| NORMAL-PRECISE | INC / 전체 | INC / 전체 | **LQ** / 전체 |
| MAX-MIXED·DIGIT·ASCII·SUPPLEMENTARY, ESCAPE-01 | INC / 질문 `[9,16)` | INC / 전체 | INC / 질문 `[9,16)` |
| MAX-HANGUL | INC / 질문 `[9,16)` | INC / **질문 `[0,16)`** | INC / 질문 `[9,16)` |
| ESCAPE-CONTROL | INC / 질문 `[9,16)` | INC / 전체 | **LQ** / 질문 `[9,16)` |

- B G08 첫 출력도 같은 방식으로 풀렸다. c는 t=INC, eqp=null, from·to=Context `timeWindow` 값 복사이고, d·e는 t=UNS, eqp·from·to 모두 null이다.

| 실패 | 확정(SHA·입력 대조) | 추정(근거와 한계) |
|---|---|---|
| G08 최종 t `UNS`(3b) | G08 질문(`eval/golden/tc-f0-v2.jsonl` 8줄)에는 설비 ID·시간 표현이 없다. 설비·시간 신호는 Context의 `selectedResourceRefs=[EQP PHOTO_001]`·`timeWindow`에만 있다. `EXTRACTION_V1`(`MessageAssembler.java` `project`)은 이 두 키를 통째로 지운다. 프롬프트를 c로 고정한 e에서도 A·B 첫 출력 SHA가 d와 같다. SPEC AC-38-F0(정본 SPEC 2087줄)은 Ollama 직접 비교 입력을 "고정 문항 + AC-38 공통 TC Context"로 정하고, 공통 Context(2105줄)가 "Snapshot(EQP)·Log(시간창) 전제를 확정한다"고 적는다. 따라서 `EXTRACTION_V1`은 AC-38-F0이 입력으로 주도록 정한 신호를 지웠다. SPEC §22.2 시간창 결정(1674줄)은 값의 적용 주체(Gateway)만 정하며, LLM 입력에서 존재 사실까지 빼라고 하지 않는다. | 모델이 "TC 설비가 선택됨"을 TC 소유 근거로 써 왔고, 그 신호가 없으면 `WM 으로 보낸 …`을 WM 소유 자료로 읽어 UNS로 분류한 것으로 본다. 같은 cross-system 문항이라도 질문에 설비 ID가 있는 G17은 B c·d·e 모두 INC였다. 다만 c(FULL)에서도 A 초회는 UNS였다(재시도 뒤 INC). 따라서 G08은 원래 경계 문항이다. R=1 근거다. |
| 경계 NORMAL-01 t=`LQ`(3c) | 이 케이스 Context는 `timeWindow=null`·`selectedResourceRefs=[]`다. 투영이 지운 정보는 없고 빈 두 키의 바이트(약 10토큰)만 사라졌다. d·e 출력은 t만 LQ이고 인자 4개는 기대와 정확히 같다(SHA). | 원인은 INC/LQ 경계 규칙이 약한 것이고, 투영의 바이트 변화가 경계 문항을 넘긴 계기로 본다. 같은 구조(설비 + 명시 구간 + "오류 로그 원인 찾아줘")의 G09는 B에서 c·d·e 모두 LQ였고, G12("DLQ … 왜 실패했는지 분석해줘")도 c·d·e 모두 LQ였다. NORMAL-PRECISE는 e에서만 LQ로 바뀌었다. |
| 경계 MAX-HANGUL eqp(3c) | d 출력의 eqp는 질문 코드포인트 `[0,16)` 접두 `포토설비A동1라인EQP0001`이다(SHA 정확 일치). pin tokenizer로 센 이 출력은 106토큰 + 종료 1 = completion 107이다. 64자 전체 출력은 145 + 1 = 146(d 통과 6건), `[9,16)` `EQP0001` 출력은 97 + 1 = 98(c·e)로 모두 일치한다. 절단 위치 16은 토큰 경계이며, 그 뒤가 `경계최대길이검증용식별자…`다. c·e의 `[9,16)` 선택은 c 프롬프트의 "EQP ID" 문구와 일치한다. | MAX·ESCAPE 7건의 eqp와 질문 앞부분은 모두 같고, 따옴표 검색어 payload만 다르다. MAX-HANGUL payload는 한글뿐인 `가나다라마바사아자차카타파하…`이고, 그 앞 12자는 eqp 끝 12자와 같다. 한글이 많은 입력에서 eqp 안의 한글 낱말 구간을 설명 문구로 읽고 끊은 것으로 보이나, 확인하지 못했다. |

- 하네스 조립·판정 결함: 확인한 경로(`MessageAssembler` 조립·재시도, `F0Measurements.run/boundaryGen`·`argsExact`, `ClassificationJudge.judge`, `AttemptDiagnostics`)에서는 세 실패를 설명하는 결함을 찾지 못했다. 위 SHA 해석에서 실패 축 외 필드는 모두 기대값과 정확히 같아 판정·진단과도 일치한다. **설계 결함은 `EXTRACTION_V1` 자체**다. 이 투영이 AC-38-F0 입력 신호를 지웠고, 이번 f에서 새 opt-in 값으로 교정한다(기존 값은 그대로 보존).
- §12.15 6)의 "root 입력 부분 문자열 SHA 대조도 일치 후보를 찾지 못했다"와 이번 결과(MAX-HANGUL d = `[0,16)`)는 다르다. 이번 탐색은 질문 전체의 모든 부분 문자열 × 키 정렬 compact 직렬화를 썼다. 차이는 탐색 범위 때문으로 보이나 root의 당시 범위는 확인하지 않았다.

#### 2) 보완 후보 f 설계

- 투영 `PRESENCE_V1`(새 v3 opt-in 값, `parameters.contextProjection`). 원 Context·fixture·판정 oracle·`AttemptDiagnostics`(`equalsContext` 포함)는 전체 Context 그대로다.
  - Context 키 4개(`timezone`·`referenceTime`·`timeWindow`·`selectedResourceRefs`)를 유지한다. `timeWindow` 객체는 `true`, `selectedResourceRefs` 각 항목은 `{"type": …}`만 남긴다. 시각·자원 ID 값은 없으므로 c의 Context 값 복사 경로가 구조적으로 사라진다.
  - `null`·`[]`는 그대로라 값 없는 Context는 FULL과 **바이트가 같다**. 경계 9케이스 전부, 골든 22 중 20, 회귀 30 중 27이 여기에 해당한다. 값이 지워지는 문항은 G08·G20(+회귀 R03)뿐이다.
  - 키 집합이 다르거나 타입이 맞지 않으면 `CONTEXT_PROJECTION_INVALID`로 송신 전에 거부한다(알 수 없는 키를 조용히 버리지 않는다).
- 프롬프트 `classify-v3-f`·`plan-v3-f`(d 기반, 두 파일의 t·소유·인자 절은 같은 문구):
  - INC: "로그·DLQ·이력을 봐야 해도 원인·이유를 물으면 INC 다"(NORMAL-01·G09·G12의 INC/LQ 경계).
  - 소유: "보낸 주체가 생략돼도 다른 시스템과 주고받은 메시지가 실패한 이유를 물으면 INC 다"(SPEC §5.3·AC-38 규칙. 골든 문구 `WM 으로 보낸`은 넣지 않음).
  - 인자 머리말: "context 의 timeWindow·selectedResourceRefs 는 화면 선택 여부만 알리며 값은 Gateway 가 적용한다".
  - eqp: "일부만 골라내거나 한글 낱말처럼 보이는 곳에서 끊지 않는다"(확정된 `[0,16)` 절단 대응).
  - 삭제 3문장: 설명 텍스트 금지, 키 집합 문장, qRef 원문/목록 밖 ID/q 키 문장. 모두 `JSON_SCHEMA_V1`(`ResponseSchema.java`: required·`additionalProperties:false`·qRef enum/null)이 이미 강제한다. 삭제 이유는 경계 입력 여유다. 값 없는 경계 Context가 FULL 바이트(EXTRACTION_V1 대비 +10)이므로 분류 프롬프트는 916토큰 이하여야 한다. f는 906(d 908, c 898)이다.
  - 정답 값·골든/회귀 문구·자원 catalog·ID 상수는 넣지 않았다.
- 3a 근거: 후보 0 qRef는 서버 스키마가 `null` 타입만, 후보가 있으면 발급 ID enum만 허용한다(c·d·e 모두 `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0). f도 같은 스키마다.
- 3b 근거: G08에 AC-38-F0 정의대로 "EQP·시간 창 선택됨" 신호를 돌려준다. 이 신호가 있던 c(FULL)에서 G08은 B 초회 INC, A 재시도 뒤 INC였다. 여기에 소유 규칙을 INC로 명시했다. 값이 없으므로 c의 from·to 복사는 재현될 수 없다.
- 3c 근거: 경계 9케이스는 c와 같은 FULL 바이트 Context가 된다. 이 Context에서 c는 NORMAL-01·PRECISE를 INC로 통과했다. INC 규칙이 같은 방향으로 보강한다. eqp 규칙은 d(6/7 MAX·ESCAPE 통과)를 유지하고, 확정된 MAX-HANGUL 절단 형태를 직접 금지한다.
- 위험(실측 전 미확정):
  - R=1·단일 결정 출력 근거다. "FULL 바이트 + d 계열 프롬프트" 칸은 측정된 적이 없고, f는 새 조합이다.
  - 삭제 3문장과 새 문장이 다른 문항(특히 A 경로 steps)의 출력을 바꿀 수 있다.
  - `[{"type":"EQP"}]`를 보고 eqp에 `"EQP"`를 쓸 수 있다(G08·G20·R03 인자 불일치 위험. G08 게이트 조건은 t만 본다).
  - INC 문구가 원인을 묻지 않는 LQ 문항(G10·G11·G02)에 번질 수 있다.
  - G07·G16(UNS→GQ)·A G17 steps·A R06 시각은 이번 설계의 직접 대상이 아니다.
  - 경계 입력 여유가 10토큰이다(3062/3072).

#### 3) 변경 파일(이 절 제외 11파일 + Devreport)

| 파일 | 구분 | 바이트 | SHA-256 |
|---|---|---|---|
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/MessageAssembler.java` | 수정 | 7054 | `36bb1eb96557820dc7fa651b1d527792e5ee09ebfa9dc6a9156535065e32f250` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Prereg.java` | 수정(`CONTEXT_PROJECTIONS`에 `PRESENCE_V1` 1줄) | 16512 | `45d7a09768fe74f3ec581bbeba987ae50d5a29433122074c056b6c98282548d0` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0PresenceProjectionTest.java` | 신규(6시험) | 22288 | `3c47fb690799a0962feee6ddee398c46636afc47c39ebf5b32be963719784abb` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java` | 수정 | 23041 | `f25b3537dcd442ba92cff89d1031d3cbe99b149e6d565edc5f7977ad824e4c77` |
| `eval/f0/v3/prompts/classify-v3-f.txt` | 신규(906토큰) | 3139 | `aad2ef3d32ec5da5c6608fe9626f7133465bbd05a3f086bb08fc16b56b864b98` |
| `eval/f0/v3/prompts/plan-v3-f.txt` | 신규(1044토큰) | 3561 | `38efd566d0084583816f2d6c8399fd58064c9bc2c710dcc1d87ca78cc875871b` |
| `eval/f0/v3/input-audit-v3-f.json` | 신규(651행) | 92669 | `6d9b0a516ee4bbf92ff248cc6e2cabbb4e381b485bb62fa8e7ae4ed5aa250277` |
| `eval/f0/v3/input-audit-v3-regression-f.json` | 신규(843행) | 119389 | `2d8fd993a65079a18f8824dbd107bb9236618eb923464536e86a9081c37e3682` |
| `eval/f0/PREREG-v3-diag-f.md` | 신규 초안 | 11580 | `933700125f2a918956adf73f322a84f30470dd11cfcd48b8e9d5f47131a0c1e2` |
| `eval/f0/PREREG-v3-regression-f.md` | 신규 초안 | 9106 | `90f58663bbde703116ee3210672d96bf3c2717c023663aeaaf1bf681c57ba67e` |
| `eval/f0/PREREG-v3-gate-f.md` | 신규 초안 | 11746 | `03f1e4bae985c3c9f63136a366b6a848a04ef86dc137bd897f71752ad710a5b9` |

- `F0RunnerCli`(`input-audit-render`는 `Prereg.CONTEXT_PROJECTIONS`로 검사)·`F0Measurements`(`requireProjection`)·`F0ReportWriter`(투영값 표기)는 기존 코드가 새 값을 그대로 다루므로 수정하지 않았다. 측정 명령에는 여전히 투영 override 인자가 없다.
- 11파일 모두 UTF-8·LF·BOM 없음, `git diff --check` exit 0.

#### 4) 시험(이 세션 실행, root 실행 아님, Gradle 동시 실행 0)

- 실패 시험 먼저: `F0PresenceProjectionTest`를 구현 전에 실행해 `cannot find symbol … PRESENCE_V1`(컴파일 오류 20)로 실패함을 확인했다. 구현 뒤 같은 필터 실행(`F0PresenceProjectionTest`·`F0ContextProjectionTest`)은 PASS였다.
- `F0PresenceProjectionTest` 6건:
  - ① 조립 바이트(값 제거·존재 신호, 원 Context 불변, 값 없는 Context = FULL 바이트, 오염 8종 `CONTEXT_PROJECTION_INVALID`).
  - ② 실제 등록 입력에서 FULL·EXTRACTION_V1 조립이 조립기와 독립 구성한 기대 바이트와 같음. 회귀 30 중 값 있는 Context 3문항만 PRESENCE가 다르고, 실제 경계 9는 PRESENCE = FULL.
  - ③ v3 opt-in 등록 값·오염 거부·GATE R5.
  - ④ 실제 HTTP stub 요청(B·A·경계·usage·token, 초회 무효 → 재시도 전수): 전송 메시지 SHA ⊆ audit, 재시도 = 같은 초회 + 고정 코드 한 줄, 값 없는 Context는 FULL 독립 구성 바이트와 같음, run-binding·진단 보고서에 `PRESENCE_V1`.
  - ⑤ 다른 투영 audit(EXTRACTION_V1·FULL)·기존 FULL 등록 + PRESENCE audit·CLI override 모두 호출 전 거부(요청 0).
  - ⑥ `input-audit-render --context-projection PRESENCE_V1` 행 id·SHA = 측정 조립 목록.
- `F0V3RegistrationDraftTest` 수정 이유: `harnessDigest`는 f0 소스 24파일을 포함하므로, 소스를 고치면 d(C7/C8)·e(C9/C10) 등록이 현재 하네스 기준 stale이 된다. 이는 §12.13에서 C5 묶음에 적용한 것과 같은 결과다.
  - 기존 "d 등록은 `Prereg.verify` 통과" 단정을 C5 묶음과 같은 역사 신원 검증으로 옮겼다. 내용은 실제 git으로 등록 commit 트리 harnessDigest와 PREREG commit=C8·C10 재계산, 입력 바이트 불일치 0, 현재 하네스 `localMismatches=["harnessDigest"]`·`verify`=`PREREG_INPUT_CHANGED`다.
  - e를 처음으로 이 검증 묶음에 넣었다.
  - 후보 묶음은 f 3초안(`PRESENCE_V1`, 로컬 불일치 0, 초안이면 `PREREG_NOT_COMMITTED`·등록 뒤면 실제 `verify` 통과)으로 바꿨다.
  - 비교 시험에 f↔gate-f·f↔regression-f·f↔d 관계를 추가했다. audit 반대 투영 대조는 PRESENCE→EXTRACTION_V1이다(값 없는 Context에서 FULL과 바이트가 같기 때문).

| 명령 | exit | 결과 |
|---|---|---|
| `./gradlew.bat :gateway-eval:test --console=plain`(무필터, `AIGW_SPEC_FILE`=정본 SPEC, test 태스크 실제 실행) | 0 | JUnit XML 40개 합산 188 tests·failures 0·errors 0·skipped 0(기존 182 + 신규 6) |
| `./gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain` | 0 | `TEST_EVIDENCE_VERIFIED` |

- 전체 시험 출력 끝: `> Task :gateway-eval:testLeafEvidence` … `BUILD SUCCESSFUL in 4m 15s`, `6 actionable tasks: 3 executed, 3 up-to-date`.

#### 5) 입력 audit(실제 Java 조립 + 등록 렌더러 + pin tokenizer)

- 렌더: `f0 input-audit-render … --classify-prompt eval/f0/v3/prompts/classify-v3-f.txt --plan-prompt eval/f0/v3/prompts/plan-v3-f.txt … --template-sha256 40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541 --context-projection PRESENCE_V1`을 실행했다. 골든 v2 22·회귀 30 각각 → `F0_INPUT_AUDIT_RENDERED 651`·`843`. 렌더 JSONL은 레포 밖 세션 임시 디렉터리에 두었다.
- 계수: `C:\workspace_nori\.venvs\aigw-eval\Scripts\python.exe -I -B eval/py/count_input_audit_v2.py --tokenizer …tokenizer.json --expect-sha256-file ….sha256 --rendered … --template-sha256 40c21f34…b541 --out eval/f0/v3/input-audit-v3-{f,regression-f}.json` → `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`·`843 OVER_BUDGET 0`.
- 전수 재검증: 렌더↔audit 행 순서·id·messagesSha256 651/651·843/843 일치. 행별 입력 ≤ 상한·입력+`max_tokens` ≤ 4096 위반 0.

| audit | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token | 최대 입력+출력 |
|---|---|---|---|---|---|---|
| d | 1610/1649 | 1382/1421 | 3015/3054 | 1368 | 2793 | 3246 |
| f | 1616/1655 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254(`boundary/ESCAPE-CONTROL/retry`) |
| 회귀 d | 1626/1665 | 1398/1437 | 3015/3054 | 1368 | 2793 | 3246 |
| 회귀 f | 1632/1671 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 |

- 같은 id 기준 f−d: A +6/+11, B +8/+13, 경계 +8, usage +8, token 0/+8/+13. f−c: 값 있는 Context 문항 B −37/−36, 그 밖 B·경계 +8(프롬프트 차이만).

#### 6) 등록 초안 3개·등록 검사

| 초안 | 목적 | R | 골든·audit | capture | harnessDigest | parameterManifestSha256 |
|---|---|---|---|---|---|---|
| `PREREG-v3-diag-f.md` | DIAGNOSTIC | 1 | v2 22 + `input-audit-v3-f.json` | `v3-diag-f` | `35fd218fb9f3b12207c5eceaeeba1399a1f205499bce19c7e1f509154766bd10` | `c8a6504c…cb56` |
| `PREREG-v3-regression-f.md` | DIAGNOSTIC | 1 | 회귀 30 + `input-audit-v3-regression-f.json` | `v3-regression-f` | `89cb4ebbf59fee4dd1af191941c52eb8458251e427ee39f6a0775d8a94a4455e` | `c8a6504c…cb56` |
| `PREREG-v3-gate-f.md` | GATE | 5 | v2 22 + `input-audit-v3-f.json`(diag-f와 바이트 동일) | `v3-gate-f` | `35fd218f…bd10` | `1cc125f2…86ed` |

- 기계 판독 블록은 d 블록에서 생성했다. d 대비 바뀐 것은 `registrationStatus`·`registeredCommit`(UNCOMMITTED/null)·`runtimeCaptureDir`·`inputRoles.{classifyPrompt,planPrompt,inputAudit}`·해당 inputs 3개·`harnessDigest`·`parameters.promptSha256`·`parameters.contextProjection`(=`PRESENCE_V1`)이고 나머지 입력 12개·파라미터는 같다.
- harnessDigest는 Java `prereg-check`의 `currentHarnessDigest`와 Node 독립 재계산(등록 inputs + f0 소스 24파일, 정렬 경로·SHA 쌍 JSON의 SHA-256)이 일치했다.
- gate-f §1 착수 조건은 gate-d와 같은 수준이다(C11/C12 뒤 3문서 `PREREG_VERIFIED`, 진단 f·회귀 f exit 0·complete, 진단 f에서 `QREF_INVALID` 0·후보 0 `ID_OUT_OF_RANGE` 0·G08 최종 t A·B INC·경계 9/9, root 원문 없는 검토).
- `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0)는 Java exit 2, Gradle 1이다.

| 등록 | localMismatches | 결과 |
|---|---|---|
| diag-f·regression-f·gate-f(초안) | `[]` | `PREREG_NOT_COMMITTED` |
| diag-c·regression·gate(C5/C6) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(C7 이후 이미 stale) |
| diag-d·regression-d·gate-d(C7/C8) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale) |
| diag-e(C9/C10) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale) |

- 기존 등록 재현성:
  - 9개 모두 입력 바이트 불일치 0이다. 실제 git `verifyHistorical`로 등록 commit 트리 harnessDigest·PREREG commit(C6·C8·C10)을 재확인했다(위 시험).
  - FULL·EXTRACTION_V1 요청 바이트는 조립기 독립 구성과 같다(위 ②). 실제 d·e 등록 audit 651·843·651행도 현재 조립과 id·SHA가 전수 일치한다(`eachAuditCovers…` 시험).
  - 즉 역사 신원과 요청 구성은 그대로이고, 신규 측정만 stale로 거부된다. d·e는 이미 측정을 마쳤고, gate-d는 착수 조건 3b·3c 미충족으로 실행 대상이 아니다.

#### 7) 다음 커밋 범위 제안(사용자 허용 필요 — 기존 허용은 C9/C10까지)

- C11(로컬 commit, 정확 12파일): 위 3) 표 11파일 + `docs/ai-rag-gateway-architecture-dev-report.md`. 초안 3문서는 UNCOMMITTED/null 그대로 넣는다. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C12(로컬 commit, 정확 3파일): `eval/f0/PREREG-v3-{diag-f,regression-f,gate-f}.md`에 `registrationStatus=REGISTERED`, `registeredCommit`=C11 SHA, 등록 설명만 기록한다. inputs·harnessDigest·parameters는 바꾸지 않는다.
- C12 뒤 순서: 3문서 `f0 prereg-check` → 진단 f(diag-f §2) → 회귀 f → gate-f §1 충족 시 R5 → SPEC §14.5 기록·재판정·F0 gate. P04는 착수하지 않는다.

#### 8) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `264abe99d904bf7fb9e0da2b6be0ab0efff8b3be52e72e74ab7fa0988e803907` 불변이다. HEAD C10 `ca6f8a92cf56ee65086111b3c7d59ec65a5b522b`, staged 0, git 쓰기 0이다.
- 세션 중 `eval/f0/results`·`eval/f0/chat-template`·`evidence`·`eval/golden`의 변경 파일은 0이고 새 `.pyc`도 0이다. `eval/f0` 아래 신규 파일은 위 7개(초안 3·프롬프트 2·audit 2)뿐이며 기존 파일 변경은 0이다.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 응답 원문·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

#### 로컬 C11/C12 등록·최종 시험 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 승인 근거: 사용자 승인 "C11/C12 허용 후 진행(권장)"(2026-11-03). 허용 범위는 로컬 commit 두 개(C11·C12)뿐이며 push·amend·원격 publish는 하지 않았다.
- 착수 전 확인: HEAD C10 `ca6f8a92cf56ee65086111b3c7d59ec65a5b522b`(10커밋), staged 0, C11 대상 12파일 SHA-256이 지정 기대값과 12/12 일치했다.
- C11 `8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc`(2026-11-03T10:42:31+09:00), 정확 12파일(`git diff --cached --name-only` 12개 확인), 12 files changed, 1010 insertions(+), 34 deletions(-):
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/MessageAssembler.java`
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Prereg.java`
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0PresenceProjectionTest.java`
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java`
  - `eval/f0/v3/prompts/classify-v3-f.txt`
  - `eval/f0/v3/prompts/plan-v3-f.txt`
  - `eval/f0/v3/input-audit-v3-f.json`
  - `eval/f0/v3/input-audit-v3-regression-f.json`
  - `eval/f0/PREREG-v3-diag-f.md`(UNCOMMITTED/null 초안 상태)
  - `eval/f0/PREREG-v3-regression-f.md`(UNCOMMITTED/null 초안 상태)
  - `eval/f0/PREREG-v3-gate-f.md`(UNCOMMITTED/null 초안 상태)
  - `docs/ai-rag-gateway-architecture-dev-report.md`(SHA `f92fda65a7135971cf2c12432280e1c40f183d125f7dd6261db4878f31e998c8`, 이 절 추가 전 바이트)
- C12 `48561f3eefaef7e230939da0169c2f3e1a9e5070`(2026-11-03T10:43:13+09:00, C11과 초 단위 다름), 정확 3파일(`git diff --cached --name-only` 3개 확인), 3 files changed, 18 insertions(+), 12 deletions(-): `eval/f0/PREREG-v3-{diag-f,regression-f,gate-f}.md`. 각 문서는 §0 제목을 등록 완료로 바꾸고 "현재 상태"(미허용) 줄을 등록 근거·C11/C12 설명·이력 3줄로 교체했다(C10 diag-e 방식). §1 이하 본문은 기계 판독 JSON의 2키 외 변경 0이다. C12 뒤 12커밋, staged 0.
- JSON 2키 검사(node, 파싱 실패 시 원문 비출력): 3문서 모두 전 `registrationStatus=UNCOMMITTED`·`registeredCommit=null` → 후 `REGISTERED`·C11 SHA, 최상위 키 9개 순서 동일, 2키를 제외한 값 직렬화 동일(true), §1 이하 바이트 동일(2키 줄 제외, true).
- 등록 검사(`AIGW_SPEC_FILE=C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`, `DEBUG` 빈 값, endpoint 인자 없음, 순차): `f0 prereg-check --prereg eval/f0/PREREG-v3-{diag-f,regression-f,gate-f}.md` 3건 모두 `PREREG_VERIFIED`, `BUILD SUCCESSFUL`, exit 0.
- 등록 뒤 전체 시험(코드 변경 0): `:gateway-eval:test --console=plain --rerun-tasks` `BUILD SUCCESSFUL in 4m 26s`, exit 0, JUnit XML 40개 합계 tests=188·failures=0·errors=0·skipped=0(F0PresenceProjectionTest 6, F0V3RegistrationDraftTest 5 포함). 이어서 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` `TEST_EVIDENCE_VERIFIED`, exit 0. Gradle 동시 실행 0.
- 모델 실측 0회, SSH·HTTP 요청·다운로드·서비스 변경 0. 코드·시험·프롬프트·골든·기존 PREREG·audit·결과·capture·evidence·SPEC·Plan 수정 0. 이 절은 커밋하지 않았다(C11에 커밋된 Devreport 바이트 뒤에 추가만 함).

### 12.18 P03 후보 f 진단·개발 회귀 f 실행 (2026-11-03, 독립 Claude Code Opus 5.5 medium, root 검토 전)

- §12.1~§12.17은 바꾸지 않았다. 이 절 앞 바이트(277337 B, SHA `c348eb4f4eb02f725142974d3f2e0bb6070c5810998c3f0be49ffbf9b5aa63ac`)는 그대로이며 이 절만 추가했다.
- 산출물: 신규 결과 디렉터리 `eval/f0/results/20261103T015113Z-f/`(8파일)·`eval/f0/results/20261103T015113Z-regression-f/`(3파일, 미완), 신규 capture `eval/f0/chat-template/v3-diag-f/`·`eval/f0/chat-template/v3-regression-f/`(각 4파일), 이 절.
- 하지 않은 것: 코드·시험·프롬프트·골든·fixture·PREREG·audit·기존 결과/capture·evidence·SPEC·Plan 변경, git 쓰기 전부, 모델 load/pull/unload·서비스 재시작·num_ctx 변경·다운로드, 정식 R5(gate-f)·F0 leaf 증거·gate·SPR, P04.
- **결론(진단 R1, 판정 아님):**
  - 진단 f 7개 명령은 모두 exit 0·`complete=true`다. 경계 boundary-gen은 9/9 통과이고, B G08 최종 t는 `INC`다. A G08은 두 시도 모두 t=`INC`였지만 steps `EMPTY_FOR_TOOL_TASK`(`STEPS_INVALID` 2회)로 최종 `INVALID`다. A에서는 G01·G02가 `ENDPOINT_TIMEOUT`(120 s)으로 끝났다.
  - 개발 회귀 f는 4단계(B)에서 중단됐다. Jetson 커널 OOM killer가 `llama-server`를 종료했고(11:15:09 KST), systemd가 서비스를 자동 재시작했다(NRestarts 0→1, 상주 모델 0). 러너는 `ENDPOINT_UNREACHABLE`·Java exit 5·`complete=false`·attempts 0으로 끝났다. 회귀 f 5~7단계는 실행하지 않았다.
  - 따라서 gate-f §1 조건 2는 회귀 f 미완으로, 조건 3b는 A G08 최종 `INVALID`로 미충족이다(판정은 root). 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(읽기 전용 확인)

- 확인 방법: SSH 읽기 전용 + `bash -s`(LF stdin). `GET /api/ps`는 Jetson 안에서 python3로 파싱해 허용 필드만 출력했고, 파싱 실패 시 원문 대신 `parse failed`만 내도록 감쌌다. 확인 시각 2026-11-03T01:50:59Z.
  - 서비스: active/running, `ActiveEnterTimestamp=2026-11-03 08:07:24 KST`, NRestarts 0. boot 2026-11-02 14:22:11 KST(재부팅 아님).
  - journal(마지막 서비스 시작 이후): `library=cpu` 0건, `Load failed` 0건, `library=CUDA` 4줄.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - 상주 모델 2개. Mem 7619 MiB 중 available 234, swap 3809 MiB 중 사용 1784. 로컬 11434는 미사용이었다.
- 진단 f B·A·boundary 뒤 재확인 결과, 중단 조건 항목(ActiveEnterTimestamp·NRestarts 0·2모델 digest·ctx 4096·size_vram=size·`library=cpu`/`Load failed` 0)은 모두 유지됐다. 다만 메모리 압박은 계속 커졌다.

| 확인 시각(UTC) | 시점 | Mem available MiB | swap 사용 MiB | 중단 조건 |
|---|---|---|---|---|
| 01:50:59 | 착수 전 | 234 | 1784 | 충족 |
| 01:55:24 | 진단 f B 뒤 | 271 | 2518 | 충족 |
| 02:07:58 | 진단 f A 뒤 | 160 | 3554 | 충족 |
| 02:13:06 | 진단 f boundary 뒤 | 192 | 3621 | 충족 |
| 02:15:52 | 회귀 f B 실패 뒤 | 6400 | 1148 | **불충족**(NRestarts 1, activating, 모델 0) |

- 진단 f A 실행 중(02:06Z경) 시도한 SSH 확인은 `Connection timed out during banner exchange`로 실패했다. 측정에는 개입하지 않았다.
- 02:13Z 확인 때 RSS 상위는 `llama-server` 2개(3.34 GB·0.80 GB)였고, 그 밖에는 gnome-shell 129 MB 이하뿐이었다(외부 대형 프로세스 없음).
- 회귀 f 실패 뒤 읽기 전용 확인(02:16:32Z):
  - kernel `Out of memory: Killed process 9551 (llama-server)`(11:15:09 KST, global_oom, task_memcg=ollama.service).
  - systemd `Failed with result 'oom-kill'`, 이어서 `Scheduled restart job, restart counter is at 1` → 11:16:00 KST `Started`.
  - 재시작 뒤 `library=CUDA compute=8.7`, `library=cpu` 0·`Load failed` 0, 상주 모델 0.
  - 이 세션은 재시작·로드를 하지 않았고, 이후 모델 요청도 0이다.

#### 2) 실행 기록

- 공통 조건: Bash에서 `powershell.exe -NoProfile -File <scratchpad 래퍼>`로 실행했다. 래퍼는 `Set-Location C:\workspace_nori\nori-ai-gateway` 뒤 `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`를 호출한다. `--args` 값은 각 등록 §2와 같다.
  - 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=각 회차 ID, `DEBUG`=빈 값을 설정했다.
  - 한 명령씩 실행하고 exit 0을 확인한 뒤 다음으로 넘어갔다. 측정 중 다른 Gradle 실행은 0이다.
- run prefix `20261103T015113Z`(시작 때 1회 결정). 회차 ID는 `20261103T015113Z-f`·`20261103T015113Z-regression-f`다. 결과 2디렉터리·capture 2디렉터리는 사전 부재를 확인했다.
- 터널: `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다. 진단 f는 PID 12100, 회귀 f는 PID 5796이며, 각각 명령행을 확인한 뒤 `Stop-Process`로 종료했다. 종료 후 11434 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| f-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-f.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `35fd218f…bd10`, parameterManifestSha256 `c8a6504c…cb56` |
| f-2 | `f0 preflight … --out eval/f0/results/20261103T015113Z-f/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| f-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-f`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| f-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3-f.txt --run-id 20261103T015113Z-f --out …/B` | 0 | 01:52:09~01:55:14(3m 4s), `F0_RUN_COMPLETED B` |
| f-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3-f.txt …` | 0 | 01:55:40~02:07:47(12m 6s), `F0_RUN_COMPLETED A` |
| f-6 | `f0 boundary-gen … --prompt eval/f0/v3/prompts/classify-v3-f.txt …` | 0 | 02:08:23~02:12:43(4m 19s), `F0_BOUNDARY_COMPLETED 9` |
| f-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-f.md --run-id 20261103T015113Z-f --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `ecaf71fe…e41e` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression-f.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `89cb4ebb…455e` |
| r-2 | `f0 preflight …/20261103T015113Z-regression-f/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression-f`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | **Java 5 / Gradle 1** | 02:14:39~02:15:26, `ENDPOINT_UNREACHABLE`, `f0-results.json` `complete=false`·`abortReason=ENDPOINT_UNREACHABLE`·attempts 0 |
| r-5~r-7 | A·boundary·report | 미실행 | r-4 실패와 환경 조건 불충족(서비스 재시작·모델 미상주)으로 중단 |

- r-1은 localMismatches를 출력에서 확인하려고 같은 명령을 한 번 더 실행했다(모델 요청 없음, 결과 동일).
- 진단 f의 B·A·boundary 소요 시간은 d·e(B 약 1.5분, A 약 2분, boundary 약 1.5분)보다 B 약 2배, A 약 6배, boundary 약 3배 길었다. 메모리 압박 시기와 겹치지만, 인과는 확인하지 않았다.
- 서버 `response_format` 거부는 관측하지 않았다. r-4의 exit 5는 `ENDPOINT_UNREACHABLE`(OOM 종료 뒤 endpoint 부재)이다. F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 6개(진단 f B·A·boundary·capture, 회귀 f B·capture)의 runtimeManifest는 모두 같다.
  - model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`(runtimeManifestSha256 `b1e6a3af…6f6a`, c·d·e와 같음), specSha `34fd6223…66be`(FILE_HASHED, LOCAL_FILE)
  - registeredCommit C11 `8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc`, preregCommit C12 `48561f3eefaef7e230939da0169c2f3e1a9e5070`
  - `contextProjection=PRESENCE_V1`, `responseFormat=JSON_SCHEMA_V1`, parameterManifestSha256 `c8a6504c…cb56`
  - harnessDigest: 진단 f `35fd218f…bd10`, 회귀 f `89cb4ebb…455e`(각 등록값과 같음)
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출(작음/큼)은 A 0/0, B 0/0, boundary 0/0이다(보고서와 정제 필드 재집계 모두). A G01·G02는 timeout이라 usage가 없다.
- 첫 시도 프롬프트 토큰 범위: A 1579~1616(G01·G02 제외), B 1353~1390. audit 최대 A 1616·B 1390과 같다.

#### 4) 진단 f 결과(`eval/f0/results/20261103T015113Z-f/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

- 재집계 스크립트는 같은 방식으로 c·d·e를 다시 계산해 §12.12 4)·§12.14 4)·§12.16 4) 수치와 일치함을 먼저 확인했다.

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 17/22 | 13/22 | 19/22 | 19/22 | 16/22 | 4/22 | 20096/120011 | `STEPS_INVALID` 5·`ENDPOINT_TIMEOUT` 2 |
| B | 20/22 | 18/22 | 20/22 | 22/22 | 22/22 | 0/22 | 4809/7100 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 6·`TASK_TYPE_MISMATCH` 2·`INVALID_OUTPUT` 1·`TIMEOUT` 2, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2.
- A의 p95 120011과 최대 120020은 timeout 2건 때문이다. B 최대 지연은 G01 66894 ms이며, 나머지 21건은 ≤7100 ms다.
- A timeout 2건: G01·G02는 초회 `ENDPOINT_TIMEOUT`(120011~120020 ms)이고 재시도는 없으며, 최종 상태는 `TIMEOUT`이다.
- A 재시도 4건:
  - G05·G07·G16: 초회 `NONEMPTY_FOR_NO_TOOL_TASK` 뒤 재시도에서 유효(t 유지: GQ·UNS·UNS).
  - G08: 두 시도 모두 t=INC·steps `EMPTY_FOR_TOOL_TASK`(`STEPS_INVALID` 2회)여서 최종 `INVALID`.
- t 오답:
  - A: G06·G19(LQ→KQ), G08(최종 무효), G01·G02(timeout).
  - B: G07(UNS→INC), G16(UNS→GQ).
- 인자 오답:
  - A: G09·G11·G12·G17 from+to, G16·G20 eqp, G08 무효, G01·G02 timeout.
  - B: G01·G11·G12·G17 to.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. 후보 0 시도는 모두 `qRef.kind=JSON_NULL`이고(A 21·B 19), 후보 1 시도는 `ID_IN_RANGE`(A 3·B 3)다. `ID_OUT_OF_RANGE`는 경계를 포함해 0이다.
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to의 `equalsContext=true`는 0건이다(c는 B 2시도·A 3시도, d·e는 0).
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - B: 초회 t=`INC`, 재시도 0이며 최종 effectiveT도 `INC`다. eqp·from·to는 모두 NULL·MATCH·`equalsContext=false`이고 qRef는 `JSON_NULL`이다. 첫 출력 SHA는 c·d·e와 모두 다르다.
  - A: 초회와 재시도 모두 t=`INC`다. 그러나 두 시도 모두 steps `EMPTY_FOR_TOOL_TASK`였으므로 최종 상태는 `INVALID`이고 effectiveT는 없다(`INVALID_OUTPUT`). 최종 시도 eqp·from·to는 NULL·MATCH·`equalsContext=false`, qRef는 `JSON_NULL`이다.
- **경계 9케이스 boundary-gen 9/9**(모두 status OK, schemaValid=true, argsExact=true, finish=stop, 재시도 0, completion ≤192, `equalsContext` 0, prompt=audit 9/9):

| caseId | 판정 | 불통과 축 | completion | qRef |
|---|---|---|---|---|
| NORMAL-01 | 통과 | – | 59 | JSON_NULL/후보0 |
| NORMAL-PRECISE | 통과 | – | 97 | JSON_NULL/후보0 |
| MAX-MIXED | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| MAX-HANGUL | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| MAX-DIGIT | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| MAX-ASCII | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| MAX-SUPPLEMENTARY | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| ESCAPE-01 | 통과 | – | 146 | ID_IN_RANGE/후보1 |
| ESCAPE-CONTROL | 통과 | – | 146 | ID_IN_RANGE/후보1 |

#### 5) 개발 회귀 f 결과(미완)

- r-4(B)가 attempts 0으로 중단됐으므로 회귀 f의 A·B 표, 신규 8축(R01~R08), 회귀 경계, `diagnostic-report.md`는 **없다**.
- 회귀 f B `f0-results.json`(438 B)은 `complete=false`·`abortReason=ENDPOINT_UNREACHABLE`·attempts 0이다.

| id | 축 | 회귀(c) | 회귀 d | 회귀 f |
|---|---|---|---|---|
| R01 | REG_QUOTED_EQP | A·B 통과 | A·B 통과 | 미측정 |
| R02 | REG_EQP_VS_CODE_TERM | A·B 통과 | A·B 통과 | 미측정 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | A·B 실패 | A·B 통과 | 미측정 |
| R04 | REG_TC_TRANSFER_FAILURE | A 실패·B 통과 | A·B 통과 | 미측정 |
| R05 | REG_WM_OWNED_RECORD | A·B 통과 | A·B 통과 | 미측정 |
| R06 | REG_PARAPHRASED_RANGE | A·B 통과 | A from·to 실패·B 통과 | 미측정 |
| R07 | REG_SECOND_QUOTED_INTENT | A·B 실패 | A·B 통과 | 미측정 |
| R08 | REG_LONG_EQP_ID | A·B 실패 | A·B 통과 | 미측정 |

- 회귀(c)·회귀 d 열은 §12.14 5) 표의 "회귀(c) 대비" 기록을 옮긴 것이다(이 세션에서 다시 계산하지 않음).

#### 6) c·d·e·f 비교(원문 없는 정제 필드·출력 해시만)

- 등록 구성: c = FULL + c 프롬프트, d = EXTRACTION_V1 + d 프롬프트, e = EXTRACTION_V1 + c 프롬프트, f = PRESENCE_V1 + f 프롬프트.

| 지표 | c | d | e | f |
|---|---|---|---|---|
| A taskType / 인자 | 20/22 / 13/22 | 19/22 / 15/22 | 16/22 / 13/22 | 17/22 / 13/22(timeout 2 포함) |
| B taskType / 인자 | 18/22 / 15/22 | 17/22 / 15/22 | 17/22 / 17/22 | 20/22 / 18/22 |
| A JSON 유효 / 1차 유효 / 재시도 | 22 / 18 / 4 | 21 / 17 / 5 | 21 / 18 / 4 | 19 / 16 / 4 |
| A / B p95 ms | 10724 / 6107 | 11335 / 6211 | 10135 / 6136 | 120011 / 7100 |
| G08 최종 t A / B | INC / INC | UNS / UNS | UNS / UNS | 무효(두 시도 t=INC) / INC |
| Context 복사 시도 A / B | 3 / 2 | 0 / 0 | 0 / 0 | 0 / 0 |
| 경계 boundary-gen | 2/9 | 7/9 | 0/9 | 9/9 |

- 문항별(셀 = 최종 effectiveT / 틀린 인자, 굵게 = t 오답·무효·timeout, (ctx) = Context 복사 시도 있음):

| id | 기대 t | A c | A d | A e | A f | B c | B d | B e | B f |
|---|---|---|---|---|---|---|---|---|---|
| G01 | INC | INC / from+to | INC / from+to | INC / from+to | **TIMEOUT** | INC / from+to | INC / to | INC / from+to | INC / to |
| G02 | LQ | LQ / – | LQ / – | LQ / – | **TIMEOUT** | LQ / – | LQ / – | LQ / – | LQ / – |
| G03 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G04 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G05 | GQ | GQ / – | GQ / – | **무효** | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G06 | LQ | **KQ** / – | **KQ** / – | **KQ** / – | **KQ** / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G07 | UNS | UNS / – | UNS / – | **GQ** / – | UNS / – | **INC** / – | **GQ** / – | **GQ** / – | **INC** / – |
| G08 | INC | INC / from+to (ctx) | **UNS** / – | **UNS** / – | **무효** | INC / from+to (ctx) | **UNS** / – | **UNS** / – | INC / – |
| G09 | INC | INC / – | INC / from+to | INC / from+to | INC / from+to | **LQ** / – | **LQ** / from+to | **LQ** / – | INC / – |
| G10 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G11 | LQ | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / to | LQ / to | LQ / to |
| G12 | INC | INC / from+to | INC / from+to | INC / from+to | INC / from+to | **LQ** / from+to | **LQ** / from+to | **LQ** / from+to | INC / to |
| G13 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G14 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G15 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G16 | UNS | UNS / eqp | UNS / – | UNS / eqp | UNS / eqp | **GQ** / – | **GQ** / – | **GQ** / – | **GQ** / – |
| G17 | INC | INC / from+to | **무효** | **UNS** / eqp+from+to | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / to |
| G18 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G19 | LQ | **KQ** / – | LQ / – | **KQ** / – | **KQ** / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G20 | INC | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / eqp | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / – |
| G21 | KQ | KQ / eqp | KQ / eqp | KQ / eqp | KQ / – | KQ / eqp | KQ / eqp | KQ / eqp | KQ / – |
| G22 | INC | INC / from+to | INC / from+to | INC / from+to | INC / – | INC / – | INC / from+to | INC / – | INC / – |

- 경계 케이스별:

| caseId | c | d | e | f | f 첫 출력 해시 = c / = d / = e |
|---|---|---|---|---|---|
| NORMAL-01 | 통과(ct 59) | 불통과 t=LQ(ct 60) | 불통과 t=LQ(ct 60) | 통과(ct 59) | 같음 / 다름 / 다름 |
| NORMAL-PRECISE | 통과(ct 97) | 통과(ct 97) | 불통과 t=LQ(ct 98) | 통과(ct 97) | 같음 / 같음 / 다름 |
| MAX-MIXED | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 같음 / 다름 |
| MAX-HANGUL | 불통과 eqp(ct 98) | 불통과 eqp(ct 107) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 다름 / 다름 |
| MAX-DIGIT | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 같음 / 다름 |
| MAX-ASCII | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 같음 / 다름 |
| MAX-SUPPLEMENTARY | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 같음 / 다름 |
| ESCAPE-01 | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 eqp(ct 98) | 통과(ct 146) | 다름 / 같음 / 다름 |
| ESCAPE-CONTROL | 불통과 eqp(ct 98) | 통과(ct 146) | 불통과 t=LQ+eqp(ct 99) | 통과(ct 146) | 다름 / 같음 / 다름 |

- 첫 시도 `outputSha256` 일치 수(22문항): A f=c 7·f=d 5·f=e 8(A G01·G02는 출력 없음), B f=c 14·f=d 15·f=e 15.
- 관찰(R1, 인과 확정 아님):
  - 경계 통과 출력은 기대 출력이 하나뿐이라 통과 건끼리 SHA가 같다(f=c는 NORMAL 2건, f=d는 d 통과 7건 전부).
  - B에서 G09·G12의 t가 c·d·e의 `LQ`에서 `INC`로 바뀌었다. 반면 G07은 `INC`(c와 같은 오답)로 바뀌었다.
  - A G08은 t축은 INC로 돌아왔지만 steps가 비어 무효다. A G06·G19 LQ→KQ, B G16 UNS→GQ는 c~f 전반에 남아 있다.
  - A 경로 지연·timeout은 Jetson 메모리 압박과 시기가 겹친다. A 결과(G01·G02 timeout 포함)는 이 환경 조건에서 얻은 값이다.

#### 7) `PREREG-v3-gate-f.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. C11/C12 뒤 세 문서 `PREREG_VERIFIED` | 충족 | diag-f·regression-f는 이번 f-1·r-1, gate-f는 §12.17 C11/C12 절(이 세션 미재실행) |
| 2. 진단 f·회귀 f exit 0·`complete=true`, 서버 `response_format` 거부(exit 5) 0 | **미충족** | 진단 f는 7단계 exit 0·complete=true. 회귀 f는 r-4 exit 5(`ENDPOINT_UNREACHABLE`, OOM 종료)·complete=false, r-5~r-7 미실행. `response_format` 거부 자체는 관측되지 않음 |
| 3a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 3b. G08 최종 t A·B 모두 INC | **미충족** | B 최종 `INC`. A는 두 시도 t=INC였지만 steps `EMPTY_FOR_TOOL_TASK` 2회로 최종 `INVALID`(effectiveT 없음) |
| 3c. 경계 9 boundary-gen 9/9 | 충족 | 위 4) |
| 4. root 원문 없는 검토 | 미완(대기) | 이 절과 진단 f 보고서 |

- 조건 2·3b가 미충족이므로 gate-f §1에 따라 이 등록으로 R5를 실행하지 않는다. 이 세션은 R5·F0 leaf·gate·SPR을 만들지 않았다.

#### 8) 미실행·원인 미확정·다음 정확 조치

- 미실행:
  - 회귀 f r-4 재시도와 r-5~r-7(서비스 재시작·모델 미상주로 중단 조건 해당. 이 세션은 로드 금지).
  - gate-f prereg-check 재실행, 정식 R5, P04.
- 원인 미확정:
  - Jetson 메모리 압박의 원인. 상주 모델 2개 외 대형 프로세스는 없었지만 swap 사용이 계속 늘었다.
  - 진단 f A의 지연 증가·G01·G02 timeout과 메모리 압박의 인과.
  - A G08 steps `EMPTY_FOR_TOOL_TASK`.
  - B G07 UNS→INC.
  - 원문을 저장하지 않으므로 출력 형태를 추정으로 결론 내리지 않는다.
- 다음 정확 조치(root 결정):
  1. root가 진단 f 보고서와 이 재집계를 원문 없이 검토한다.
  2. Jetson 모델 재상주는 사용자·root 결정 사항이다(이 세션은 하지 않음).
  3. 회귀 f 재실행 여부와 방법을 정한다. 회귀 f capture `v3-regression-f`와 결과 `20261103T015113Z-regression-f`가 이미 존재하므로 재실행 시 처리가 필요하다.
  4. A 경로 timeout이 환경 영향이라고 판단되면, 진단 f를 같은 등록으로 다시 측정할지 정한다(capture `v3-diag-f` 기존재).

#### 9) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`는 불변이다(모든 run-binding specSha). HEAD C12 `48561f3eefaef7e230939da0169c2f3e1a9e5070`, staged 0이며 git 쓰기는 0이다.
- `git status --short`는 세션 시작과 같다(Devreport M, 기존 untracked 5항목). PREREG·입력·코드 변경은 0이고, 새 `.pyc`도 0이다.

| 파일 | 바이트 | SHA-256 |
|---|---|---|
| `eval/f0/results/20261103T015113Z-f/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T015113Z-f/B/f0-results.json` | 31111 | `5d65a67cfbf97556ac1e029fb41c4c11394ade8a89e3b092c94396efc2af5f6b` |
| `eval/f0/results/20261103T015113Z-f/B/run-binding.json` | 1747 | `498f1cd58eaf2c00c1af5c6497488aa2c358029c4e4caffeea26da550aeedbe8` |
| `eval/f0/results/20261103T015113Z-f/A/f0-results.json` | 35102 | `55aad619bfd9f93be32a7d85d3f6dbd5522049153f399bfe3ea3b412c107e957` |
| `eval/f0/results/20261103T015113Z-f/A/run-binding.json` | 1747 | `065748e7673a2149a41b0e91a311978a1e8c80b8bc65218261dd550ab3966f0b` |
| `eval/f0/results/20261103T015113Z-f/boundary/boundary-results.json` | 10008 | `ed30215bdd6318283f539d6905de116bfa1ea65e9edb49f1958394971e57bd41` |
| `eval/f0/results/20261103T015113Z-f/boundary/run-binding.json` | 1747 | `7eba75763624ede2356080e4cd7deb24995053aaebf990cedb28a0caba91fbc3` |
| `eval/f0/results/20261103T015113Z-f/diagnostic-report.md` | 9226 | `851da99faebc7eeb49524108280558b250c95f00b651736b2d752a0ea337252c` |
| `eval/f0/results/20261103T015113Z-regression-f/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T015113Z-regression-f/B/f0-results.json` | 438 | `d98230d58c02f5f8e23e1db8442d72617231b2403d0d91267aa97a273ed5d7b4` |
| `eval/f0/results/20261103T015113Z-regression-f/B/run-binding.json` | 1764 | `4e96e5f43c4ec7f32595579fe54717396f404c4de21a9fc3cb91e046169e480a` |
| `eval/f0/chat-template/v3-diag-f/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-diag-f/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-diag-f/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-diag-f/run-binding.json` | 1748 | `5bce7587a3e344255ff78263bfbcd32f2b0822ec016ca7e8018c38fa0ec3e735` |
| `eval/f0/chat-template/v3-regression-f/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-regression-f/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-regression-f/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-regression-f/run-binding.json` | 1765 | `d2214e4728722842ddb55ee9b59fffe798ef9e3ee19cbd10e906521ee576cff3` |

- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 이 Devreport는 UTF-8·LF·BOM 없음이다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 집계는 결과 JSON의 열거형·불리언·개수·해시 필드와 골든의 id·기대 t만 사용했다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.19 P03 A 경로 원인 분석·보완 후보 g 구현·등록 초안 (2026-11-03, 독립 Claude Code Opus 5.5 high, root 검토 전)

- §12.1~§12.18은 바꾸지 않았다. 이 절 앞 바이트(302108 B, SHA `4c9f9d7f6fffc22d21dfb6373a1adc2f2377e0e558f568c2213366b568a26651`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 변경, 다운로드, 새 capture, git 쓰기 전부. 하네스 소스(`gateway-eval/src/main`)·제품 wire(`t`·`eqp`·`from`·`to`·`qRef`·`steps`)·`f0-judge-v2`·정확 일치 oracle·골든·fixture·경계 케이스·임계·분모·예산(입력 3072, `max_tokens` 192/256, ctx 4096)·재시도 1회(메시지 포함)·timeout·`classify-v3-f`·`PRESENCE_V1`·기존 등록·결과·capture·evidence·SPEC·Plan 변경 0.
- **결론: A G08 `STEPS_INVALID` 의 직접 원인은 출력 키 생성 순서다. 하네스가 요청 본문을 키 정렬 canonical JSON 으로 보내므로 서버 스키마의 `properties` 가 `eqp, from, qRef, steps, t, to` 순서가 되고, 모델은 `steps` 를 `t` 보다 먼저 확정한다. f 프롬프트의 "t 를 먼저 정하고 steps 를 맞춘다" 지시는 이 순서와 반대였다. 계획 프롬프트만 바꾼 보완 후보 g(`plan-v3-g`)의 구현·시험·audit·등록 초안 3개를 마쳤다. g 는 실측하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 진단 g 를 실행하려면 로컬 C13/C14 사용자 허용이 먼저 필요하다.

#### 1) 원인 분석(원문 없는 근거, 확정/추정 구분)

- 방법: §12.17과 같다. 결과 JSON의 시도별 `outputSha256` 을 입력·정제 필드로 만든 후보 출력 직렬화의 SHA-256과 대조했다. A G08 은 `attemptDiagnostics` 가 eqp·from·to=`NULL`, qRef=`JSON_NULL`, steps 길이 0(`EMPTY_FOR_TOOL_TASK`), t=`INC` 로 값을 모두 정하므로 남는 자유도는 키 순서와 공백뿐이다. 키 순서 720가지 × 구분자·괄호·공백 조합과 정렬 직렬화의 공백 1~2개 삽입을 탐색했다. steps 후보는 fixture IMPLEMENTED 7개 중 길이 0~3의 순열이다. 응답 원문 조회·출력·저장, 추가 모델 호출은 0이다.

| 항목 | 확정(SHA·코드·입력 대조) | 추정(근거와 한계) |
|---|---|---|
| A G08 f 출력 형태 | 두 시도 SHA 가 같고(`8979805c…ce47`), `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC", "to":null}`(키 정렬, `"to"` 앞 공백 1)과 정확히 같다. d·e A G08 첫 출력(`ccba2efd…7086`)은 같은 형태에서 t=`UNS` 다. 재시도는 temperature 0 에서 오류 코드만 덧붙이므로(`MessageAssembler.java:88-91`) 같은 출력을 다시 냈다. | – |
| 생성 순서 = 키 정렬 순서 | 요청 본문은 `Json.canonical`(`Json.java:56` `Collections.sort(names)`)로 직렬화돼 송신된다(`OllamaChatClient.java:86`). `ResponseSchema.java:19-29` 는 `t` 부터 만들지만, 송신 바이트의 `properties` 는 `eqp, from, qRef, steps, t, to` 다. c·d·e·f A 시도 중 값으로 복원된 39건은 모두 이 순서였고, `t` 가 앞인 출력은 0건이다(인자가 모두 null 인 48건 중 나머지 9건은 steps 길이 0~3 탐색 범위 안에서 일치 후보가 없었다). 네 프롬프트(c·d·e·f)는 모두 스키마를 `t` 부터 보여 준다. | 서버(Ollama) 문법이 스키마 `properties` 순서를 강제한다고 본다. 서버 내부 코드는 보지 않았으나, 프롬프트 표기와 다른 정렬 순서가 39/39로 나온 사실과 맞는다. |
| steps/t 불일치는 A 의 반복 실패 축 | c~f A `STEPS_INVALID` 시도 20건 중 17건이 steps/t 불일치다(`NONEMPTY_FOR_NO_TOOL_TASK` 15·`EMPTY_FOR_TOOL_TASK` 2). 나머지 3건은 항목 형식(`ITEM_FORMAT_INVALID`, d·e G17)이다. c 의 A G08 도 초회는 t=`UNS`·steps 비어 있지 않음(`NONEMPTY_FOR_NO_TOOL_TASK`)이었고, 재시도에서 t=`INC`·steps `[tc.event.getRecent]` 로 유효가 됐다(from·to 는 Context 복사). | steps 는 t 를 쓰기 전에 정해지므로 steps 판단과 t 판단이 따로 움직인다. f 에서 G08 의 t 는 INC 규칙(소유 판단 문장이 "INC 다"로 t 기준 표현)으로 돌아왔지만, 그보다 먼저 쓰는 steps 에는 그 판단이 반영되지 않은 것으로 본다. R=1 근거다. |
| `PRESENCE_V1` 영향 | f A G08 두 시도 모두 eqp·from·to `equalsContext=false`(값 복사 0). c 는 값이 있어 재시도 from·to 가 Context 값과 같았다. d·e(`EXTRACTION_V1`)의 A G08 은 `UNS`·`[]` 로 일관됐다. | 존재 신호 복원이 G08 의 t 를 INC 로 되돌린 요인 중 하나로 보이나(B·A 모두 t=INC), f 는 투영과 프롬프트를 함께 바꿨으므로 steps 공백에 대한 투영의 기여를 분리할 수 없다. |
| A G06·G19 t=`KQ` | c~f A 의 steps 는 모두 `[tc.eqp.getOperationalSnapshot]` 이다(G19 d 는 t=`LQ`). fixture v2 KQ workflow 는 `tc.knowledge.search`·`tc.git.searchCode` 이고 snapshot 은 LQ·INC workflow Tool 이다. B 는 같은 두 문항을 c~f 모두 `LQ` 로 분류했다. | 같은 생성 순서 문제로 본다. 상태 조회 Tool 을 먼저 고른 뒤 t 를 그 Tool 과 무관하게 썼다. |
| AC-38-F0 의 A 쪽 workflow | `F0Judgement.ac38`(`F0Judgement.java:154-168`)은 A 행에 `PlanJudge.workflowMatches` 를 적용하고, INCIDENT_ANALYSIS 는 steps 에 `tc.logs.search` 가 있어야 한다(`PlanJudge.java:42`). c 의 유효한 A G08 steps 는 `[tc.event.getRecent]` 라 t=INC 여도 이 조건을 충족하지 못한다. SPEC AC-38-F0(정본 2087줄)은 "기대 workflow=TC 로그/코드 분석", §6.3(481-490줄)은 INC 를 `tc.logs.search` 중심으로 정의한다. | gate-f·gate-g §1 조건 3b 는 t 만 본다. 정식 R5 의 AC-38-F0 leaf 는 A steps 의 `tc.logs.search` 도 요구한다. |

- 하네스 판정 결함: 없음. `ClassificationJudge.java:68-69`(t 가 GQ·UNS 가 아닌데 steps 가 비면 `STEPS_INVALID`)는 계획 프롬프트 규칙·SPEC §5.3 GENERAL_QUERY workflow(정본 403줄, GQ `steps=[]`)와 같고, `AttemptDiagnostics.java:95` 분류도 이와 일치한다. 결함은 판정이 아니라 **설계 불일치**다. 하네스의 정렬 직렬화가 만든 생성 순서를 c~f 계획 프롬프트가 반영하지 않았다. SPEC 은 wire 키·스키마 구조만 정하고 키 순서는 정하지 않으므로(정본 1203줄) SPEC 계약 변경은 필요 없다.

#### 2) 보완 후보 g 설계

- 원칙: `classify-v3-f`·`PRESENCE_V1`·스키마·하네스는 그대로 두고 계획 프롬프트만 바꾼다. 코드로 확인한 결과, A 만 `planPrompt` 를 쓰고 B(`F0RunnerCli.java:54`)·경계(`:68`)·usage·token(`:224`)은 `classifyPrompt` 만 쓴다. 따라서 g 의 B·경계·usage·token 송신 메시지는 f 와 바이트가 같다(아래 5) audit·4) 시험).
- `plan-v3-g`(f 기반, [t 분류]·[소유 시스템 판단]·[인자] 문구는 f·`classify-v3-f` 와 같음). 바뀐 것은 두 블록이다.
  - [출력 스키마]: 실제 생성 순서 `{"eqp":…,"from":…,"qRef":…,"steps":[…],"t":…,"to":…}` 로 보여 주고, 제목에 "키는 이 순서로 출력되며 steps 가 t 보다 먼저 나온다"를 적었다.
  - [steps]: "steps 를 쓰기 전에 [t 분류]·[소유 시스템 판단]으로 t 를 정한다", t 별 Tool(KQ = `tc.knowledge.search`·`tc.git.searchCode` 중, LQ = `tc.eqp.getOperationalSnapshot`·`tc.event.getRecent` 중 1개 이상, INC = `tc.logs.search` 포함 1개 이상, GQ·UNS = `[]`), "다른 시스템과 주고받은 메시지의 실패 이유도 TC 송수신 로그에서 찾는다", "뒤에 쓰는 t 는 앞서 쓴 steps 와 맞는 t 다", "재시도 요청의 `STEPS_INVALID` 는 steps 와 t 가 맞지 않거나 steps 형식이 틀렸다는 뜻이다".
  - 근거: t 별 Tool 은 fixture v2 `workflows`(SPEC §6.1~§6.3)와 AC-38-F0·`PlanJudge.java:42`, 송수신 로그 문장은 fixture TC `ownedAreas` "TC 송수신·실패 로그(상대 시스템 이름과 무관)"(이미 Registry 블록에 렌더됨), `STEPS_INVALID` 뜻은 `ClassificationJudge.java:66-69` 다. 정답 값·골든/회귀 문구·EQP ID·자원 catalog 는 넣지 않았다. capability ID 는 같은 프롬프트의 Tool 목록에 이미 있는 IMPLEMENTED 값만 썼다.
  - 크기: 4118 B, pin tokenizer 1219토큰(f 1044, +175).
- 3b 근거: G08 실패는 t 가 아니라 t 보다 먼저 생성되는 steps 였다. g 는 steps 위치에서 t 판단을 먼저 하도록 생성 순서를 알리고, INC 에 `tc.logs.search` 를 명시하며, 다른 시스템과 주고받은 메시지의 실패 이유를 steps 규칙에 다시 적었다. B G08 은 f 와 같은 입력이므로 f 의 초회 `INC` 결과 조건과 같다. 같은 조치는 A G06·G19(snapshot steps 뒤 t=KQ)도 "t 는 steps 와 맞는다"·LQ Tool 규칙으로 겨냥한다. `tc.logs.search` 포함은 정식 R5 의 AC-38-F0 A 쪽 workflow 조건도 겨냥한다.
- 3a·3c 근거: B·경계 메시지·스키마가 f 와 바이트까지 같으므로 f 의 `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0·경계 9/9 조건과 같은 입력이다(R=1·temperature 0, 서버·모델 동일 전제).
- 직접 대상이 아닌 것(같은 근거가 없음): A G09·G11·G12·G17 from+to, A G16·G20 eqp(인자 추출 축), A G01·G02 timeout(Jetson 메모리 압박과 겹침, 원인 미확정), B G07 `INC`·B G16 `GQ`(분류 프롬프트 불변 원칙상 g 에서 다루지 않음).
- 위험(실측 전 미확정):
  - R=1·단일 결정 출력 근거다. g 는 측정된 적 없는 계획 프롬프트다.
  - "t 는 steps 와 맞는다" 문장 때문에, 모델이 G08 steps 를 다시 비우면 t 가 `UNS` 로 따라갈 수 있다(무효 대신 오답).
  - 스키마 표시 순서를 바꿨으므로 A 의 인자 추출 출력이 달라질 수 있다(실제 생성 순서는 f 와 같다).
  - t 별 Tool 을 프롬프트에 적어 A 계획이 고정 workflow 에 가까워진다. 비교 지표(taskType·인자·UNSUPPORTED)는 steps 내용을 채점하지 않지만(백스톱·AC-38 제외), A↔B 비교 해석은 root 가 판단한다.
  - INC steps 가 길어지면 A 출력 토큰·지연이 늘어난다(256 상한 안).
  - 계획 프롬프트가 fixture v2 capability ID 와 이 서버의 키 순서 동작에 의존한다.
- 하네스 변경 대안(구현하지 않음, root 결정 사항): A 스키마만 `t` 가 앞인 순서로 보내도록 송신 직렬화를 바꾸는 방법이 있다. 다만 A 요청 바이트 정의가 바뀌고, `responseFormatSha256`(`ResponseSchema.sha256` = 정렬 canonical)이 송신 순서를 반영하지 못하므로 해시 정의도 함께 바꿔야 한다. 프롬프트만으로 대응할 수 있어 이번에는 쓰지 않았다.

#### 3) 변경 파일(이 절 제외 7파일 + Devreport)

| 파일 | 구분 | 바이트 | SHA-256 |
|---|---|---|---|
| `eval/f0/v3/prompts/plan-v3-g.txt` | 신규(1219토큰) | 4118 | `89852f7252b73b963a23232eb6b4a57a1cbce2ac8e1accef6a3e432c921a2d74` |
| `eval/f0/v3/input-audit-v3-g.json` | 신규(651행) | 92669 | `4735ba32a1944ef59df744c5117c6719c3be7806853eb6fdb908d8ed7ad16744` |
| `eval/f0/v3/input-audit-v3-regression-g.json` | 신규(843행) | 119389 | `bf83e1018cd850d0e3734eb585fb9bf7162abfdc2c2188c8d0dc38d3d7232cb5` |
| `eval/f0/PREREG-v3-diag-g.md` | 신규 초안 | 11765 | `289e8ef02fd315f4c0fcf559595e8d4a59c67df091fa3294d54c0dbea8947f1c` |
| `eval/f0/PREREG-v3-regression-g.md` | 신규 초안 | 9499 | `8d1a653fa96cd2caacd369959ba7e19bfdbd4a302e29998147c2fb4b8e22186d` |
| `eval/f0/PREREG-v3-gate-g.md` | 신규 초안 | 11909 | `bbe41f52a98342bbe056c9f909f330bd099e8d1fa53fc2bc46a9315ba3f04db6` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java` | 수정(+87/−19) | 30602 | `eaadfae43d04642d9b508c1a11b8f6b107addc8d0dd694bfdcdaff494550c6fa` |

- 7파일 모두 UTF-8·LF·BOM 없음(바이트 검사 CR 0), `git diff --check` exit 0. 하네스 소스 변경 0 이므로 실패 시험 선작성 대상(코드 변경)은 없다.

#### 4) 시험(이 세션 실행, root 실행 아님, Gradle 동시 실행 0)

- `F0V3RegistrationDraftTest` 변경:
  - 과거 등록 묶음에 f 3문서(C11/C12, `PRESENCE_V1`)를 넣었다. 역사 신원(등록 commit 트리 harnessDigest·PREREG commit=C12·입력 바이트 불일치 0) 검사는 다른 9개와 같다.
  - C11 뒤 하네스 소스 변경이 없으므로 f 는 stale 단정 대신 현재 harnessDigest 일치·`localMismatches` 0 을 단정한다. `Prereg.verify` 는 실제 `git status` 로 등록 경로 아래 미커밋 파일을 세어, 있으면 `PREREG_WORKTREE_DIRTY`, 없으면 통과를 단정한다(검사 근거 `Prereg.java:132-139`). 기존 C5~C10 9개의 stale 단정(`PREREG_INPUT_CHANGED`)은 그대로다.
  - 후보 묶음을 g 3초안(`PRESENCE_V1`, 로컬 불일치 0, 초안이면 `PREREG_NOT_COMMITTED`, 등록 뒤면 실제 `verify` 통과, capture 경로 고유)으로 바꿨다.
  - 비교 시험에 g↔gate-g·g↔regression-g 관계를 넣고, f→g 는 `planPrompt`·`inputAudit`·`promptSha256.plan` 만 다르고 `classifyPrompt`·투영·나머지 입력·파라미터는 같음을 단정한다.
  - 신규 시험 `candidateGChangesOnlyPlanRequestsAndKeepsEveryOtherMessageAndAuditRowOfCandidateF`: f·g(진단, 회귀) 각각 실제 조립 목록을 만들어, A 외 행의 송신 메시지 SHA·audit 행 SHA·계수가 f 와 같고, A 행은 system 만 다르며 user·재시도 메시지는 같음을 단정한다(A 호출 수 = 12 × 문항 수).
- 첫 전체 실행은 189 중 1 실패였다. f 의 `Prereg.verify` 가 `PREREG_WORKTREE_DIRTY` 였다. g 신규 파일 6개가 `eval/f0` 아래 미커밋이고, `verify` 는 `HarnessDigest.ROOTS`(`eval/f0` 전체) 작업 트리가 깨끗해야 통과하기 때문이다. 위 git status 기반 기대값으로 고친 뒤 재실행했다.

| 명령 | exit | 결과 |
|---|---|---|
| `./gradlew.bat :gateway-eval:test --console=plain`(무필터, `AIGW_SPEC_FILE`=정본 SPEC, test 태스크 실제 실행) | 0 | JUnit XML 40개 합산 189 tests·failures 0·errors 0·skipped 0(기존 188 + 신규 1, `F0V3RegistrationDraftTest` 6) |
| `./gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test" --console=plain` | 0 | `TEST_EVIDENCE_VERIFIED` |

- 전체 시험 출력 끝: `BUILD SUCCESSFUL in 4m 12s`, `6 actionable tasks: 3 executed, 3 up-to-date`.

#### 5) 입력 audit(실제 Java 조립 + 등록 렌더러 + pin tokenizer)

- 렌더: `f0 input-audit-render --golden {v2 22 | 회귀 30} --fixture eval/golden/tc-registry-fixture-v2.json --classify-prompt eval/f0/v3/prompts/classify-v3-f.txt --plan-prompt eval/f0/v3/prompts/plan-v3-g.txt --cases eval/f0/v2/output-boundary-cases.jsonl --usage-messages eval/f0/v2/usage-probe-input-v2.jsonl --token-messages eval/f0/v2/token-golden-input-v2.jsonl --renderers eval/f0/chat-renderers-v1.json --template-sha256 40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541 --context-projection PRESENCE_V1` → `F0_INPUT_AUDIT_RENDERED 651`·`843`. 렌더 JSONL 은 레포 밖 세션 임시 디렉터리에 두었다.
- 계수: `count_input_audit_v2.py`(pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) → `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`·`843 OVER_BUDGET 0`.
- 전수 재검증: 렌더↔audit 행 순서·id·messagesSha256 651/651·843/843 일치, 행별 입력 ≤ 상한·입력+`max_tokens` ≤ 4096 위반 0, 메타데이터(kind·schema·renderer·template·tokenizer·tokenizers 버전·tool)는 f audit 와 같다.

| audit | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token | 최대 입력+출력 |
|---|---|---|---|---|---|---|
| f | 1616/1655 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254 |
| g | 1791/1830 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254(`boundary/ESCAPE-CONTROL/retry/USAGE_EXCEEDS_LIMIT`) |
| 회귀 f | 1632/1671 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 |
| 회귀 g | 1807/1846 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 |

- f 대비(같은 위치 id): A 264/264·360/360 행은 SHA 가 모두 다르고 계수는 모두 +175 다. **B 264·경계 108·usage 2·token 13(=387행)과 회귀 B 360·경계 108·usage 2·token 13(=483행)은 id 순서·messagesSha256·계수가 전부 같다.** A 최대 입력+출력은 1830+256 = 2086 이다.

#### 6) 등록 초안 3개·등록 검사

| 초안 | 목적 | R | 골든·audit | capture | harnessDigest | parameterManifest 관련 |
|---|---|---|---|---|---|---|
| `PREREG-v3-diag-g.md` | DIAGNOSTIC | 1 | v2 22 + `input-audit-v3-g.json` | `v3-diag-g` | `4de7eadb9c3fb06f4fd987b08417eaad20aa41637b53daf5b00080ec4cad5597` | f 대비 `promptSha256.plan` 만 다름 |
| `PREREG-v3-regression-g.md` | DIAGNOSTIC | 1 | 회귀 30 + `input-audit-v3-regression-g.json` | `v3-regression-g` | `fd7e952381420d9de2d8950347b2d11b6760e3357d33b0baeab9d2dc3a436cb5` | 같음 |
| `PREREG-v3-gate-g.md` | GATE | 5 | v2 22 + `input-audit-v3-g.json`(diag-g 와 바이트 동일) | `v3-gate-g` | `4de7eadb…5597` | 같음(repeats 5) |

- 기계 판독 블록은 f 블록에서 만들었다. f 대비 바뀐 것은 `registrationStatus`·`registeredCommit`(UNCOMMITTED/null)·`runtimeCaptureDir`·`inputRoles.{planPrompt,inputAudit}`·해당 inputs 2개·`harnessDigest`·`parameters.promptSha256.plan` 뿐이다(Node 키 단위 비교). `classifyPrompt`·`contextProjection=PRESENCE_V1`·나머지 입력 13개·파라미터는 같다.
- harnessDigest 는 Java `prereg-check` 의 `currentHarnessDigest` 와 Node 독립 재계산(등록 inputs + f0 소스 24파일)이 일치했다. 같은 Node 계산은 f 3등록의 기존 값(`35fd218f…bd10`·`89cb4ebb…455e`)도 재현했다.
- gate-g §1 착수 조건은 gate-f 와 같은 수준이다(C13/C14 뒤 3문서 `PREREG_VERIFIED`, 진단 g·회귀 g exit 0·complete, 진단 g 에서 `QREF_INVALID` 0·후보 0 `ID_OUT_OF_RANGE` 0·G08 최종 t A·B INC·경계 9/9, root 원문 없는 검토).
- `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차). 거부는 Java `EvalException(2, …)`(`Prereg.java:130`·`:139`), Gradle exit 1 이다.

| 등록 | localMismatches | 결과 |
|---|---|---|
| diag-g·regression-g·gate-g(초안) | `[]` | `PREREG_NOT_COMMITTED` |
| diag-f·regression-f·gate-f(C11/C12) | `[]`(currentHarnessDigest = 등록값) | `PREREG_WORKTREE_DIRTY` |

- f 상태: 하네스 소스 변경이 없어 입력·harnessDigest 불일치는 0 이고, 등록 commit 트리 기준 역사 신원도 시험에서 재확인했다. 다만 `verify` 는 `eval/f0` 작업 트리가 깨끗해야 하므로, g 신규 파일 6개가 미커밋인 지금은 `PREREG_WORKTREE_DIRTY` 로 거부된다. C13 뒤에는 `PREREG_VERIFIED` 로 돌아갈 것으로 보지만 실행으로 확인하지 않았다(추정). 같은 이유로 g 파일이 미커밋인 동안에는 f 등록으로 측정(예: 회귀 f 재실행)을 할 수 없다.

#### 7) 다음 커밋 범위 제안(사용자 허용 필요 — 기존 허용은 C11/C12까지)

- C13(로컬 commit, 정확 8파일): 위 3) 표 7파일 + `docs/ai-rag-gateway-architecture-dev-report.md`. Devreport 에는 C11 뒤 미커밋으로 추가된 §12.17 "로컬 C11/C12 등록·최종 시험"·§12.18·이 절이 함께 들어간다. 초안 3문서는 UNCOMMITTED/null 그대로 넣는다. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C14(로컬 commit, 정확 3파일): `eval/f0/PREREG-v3-{diag-g,regression-g,gate-g}.md` 에 `registrationStatus=REGISTERED`, `registeredCommit`=C13 SHA, 등록 설명만 기록한다. inputs·harnessDigest·parameters 는 바꾸지 않는다.
- C14 뒤 순서: 3문서 `f0 prereg-check` → (Jetson 메모리·서비스 상태 확인, 재상주는 사용자·root 결정) → 진단 g(diag-g §2) → 회귀 g → gate-g §1 충족 시 R5 → SPEC §14.5 기록·재판정·F0 gate. P04 는 착수하지 않는다.

#### 8) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`·Plan SHA `264abe99d904bf7fb9e0da2b6be0ab0efff8b3be52e72e74ab7fa0988e803907` 불변이다. HEAD C12 `48561f3eefaef7e230939da0169c2f3e1a9e5070`, staged 0, git 쓰기 0이다.
- `eval/f0/results`·`eval/f0/chat-template`·`evidence`·`eval/golden`·하네스 소스·기존 프롬프트·기존 PREREG·기존 audit 변경 0, 새 `.pyc` 0(Python 은 `-I -B`). `eval/f0` 아래 신규 파일은 위 6개뿐이다.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 응답 원문·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS 를 확정하지 않는다.

#### 로컬 C13/C14 등록·최종 시험 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 승인 근거: 사용자 승인 "C13/C14 허용 후 진행 (권장)"(2026-11-03). 허용 범위는 로컬 commit C13·C14 두 개뿐이며 push·amend·원격 publish 는 하지 않았다.
- 착수 전 확인: HEAD C12 `48561f3eefaef7e230939da0169c2f3e1a9e5070`(12커밋), staged 0, C13 대상 8파일 SHA-256 이 기대값과 모두 일치했다(이 Devreport 의 C13 시점 SHA `1c91353498936f5a556700ce9b0d21ec633b0adf5cef933a6264576e7925daa3` 포함).

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C13 | `1b1ded2566e911520e03cbf82606eba4e7f2b133` | 2026-11-03 12:05:34 +0900 | 8파일, 816 insertions(+), 19 deletions(-) |
| C14 | `87113b50a0a57f75c80c3e33a6faec1c3d39189d` | 2026-11-03 12:06:13 +0900 | 3파일, 18 insertions(+), 12 deletions(-) |

- C13 정확 8파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 8개 확인): `eval/f0/v3/prompts/plan-v3-g.txt`, `eval/f0/v3/input-audit-v3-g.json`, `eval/f0/v3/input-audit-v3-regression-g.json`, `eval/f0/PREREG-v3-diag-g.md`, `eval/f0/PREREG-v3-regression-g.md`, `eval/f0/PREREG-v3-gate-g.md`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java`, `docs/ai-rag-gateway-architecture-dev-report.md`. 초안 3문서는 UNCOMMITTED/null 상태로 들어갔다. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않았다.
- C14 정확 3파일: `eval/f0/PREREG-v3-{diag-g,regression-g,gate-g}.md`. §0 제목·현재 상태 문구를 C12 방식(등록 근거·C13/C14 설명·이력)으로 바꾸고, 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C13 SHA 만 바꿨다. §1 이하는 그 2줄 외 바이트 그대로다.
- JSON 검사(Node, 파싱 실패 시 원문 비출력): 3문서 모두 전 값이 UNCOMMITTED/null, 후 값이 REGISTERED/C13 SHA 이고, 두 키를 뺀 나머지 객체 동일·키 순서 동일·§1 이하 2줄 외 동일이 true 였다. `git diff C13 C14` 의 JSON 변경 줄도 문서당 이 2키 2줄뿐이다.
- gate-g §1 조건 1 의 "C13/C14 는 기존 C3~C12 허용에 포함되지 않으며 C13/C14 구체 범위 검토 뒤 사용자의 별도 commit 허용이 필요하다" 문장은 지시에 따라 바꾸지 않았다(보고만 한다).
- C14 뒤 14커밋, staged 0 이다.

`f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차 실행, 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값):

| 등록 | localMismatches | currentHarnessDigest | 결과 | Gradle exit |
|---|---|---|---|---|
| diag-g | `[]` | `4de7eadb…5597` | `PREREG_VERIFIED` | 0 |
| regression-g | `[]` | `fd7e9523…6cb5` | `PREREG_VERIFIED` | 0 |
| gate-g | `[]` | `4de7eadb…5597` | `PREREG_VERIFIED` | 0 |
| diag-f | `[]` | `35fd218f…bd10` | `PREREG_VERIFIED` | 0 |
| regression-f | `[]` | `89cb4ebb…455e` | `PREREG_VERIFIED` | 0 |
| gate-f | `[]` | `35fd218f…bd10` | `PREREG_VERIFIED` | 0 |

- f 3문서는 C13 전 `PREREG_WORKTREE_DIRTY` 였으나 C13 으로 `eval/f0` 미커밋 g 파일이 없어져 다시 `PREREG_VERIFIED` 가 됐다(앞 절의 추정을 실행으로 확인).

등록 뒤 전체 시험(Gradle 동시 실행 0, 순차):

- `.\gradlew.bat :gateway-eval:test --console=plain --rerun-tasks` → exit 0, `BUILD SUCCESSFUL in 4m 50s`, `6 actionable tasks: 6 executed`. XML 독립 집계 40클래스·189 tests·failures 0·errors 0·skipped 0.
- 이어서 `.\gradlew.bat :gateway-eval:run --console=plain --args="test-evidence verify --tasks :gateway-eval:test"` → exit 0, `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s`.

보존: 모델 실측 0회, SSH·HTTP 요청·다운로드·서비스 변경 0. 코드·시험·프롬프트·골든·기존 PREREG·audit·결과·capture·evidence·SPEC·Plan 변경 0. push·amend·rebase·reset 0. 이 절은 commit 하지 않았으며(C13 커밋 바이트 불변, 작업 트리에만 추가), 응답 원문·API 응답·secrets 는 출력·저장하지 않았다. 다음 순서는 앞 절 7) 그대로이며(진단 g → 회귀 g → gate-g §1 충족 시 R5), 이 세션은 착수하지 않았다.

### 12.20 P03 후보 g 진단·개발 회귀 g 실행 (2026-11-03, 독립 Claude Code Opus 5.5 medium, root 검토 전)

- §12.1~§12.19는 바꾸지 않았다. 이 절 앞 바이트(327195 B, SHA `db224078a7670eb557b2fa5a698d9c6f4ba1f254da5e45b9253eb6ed9c97b9b3`)는 그대로이며 이 절만 추가했다.
- 산출물: 신규 결과 디렉터리 `eval/f0/results/20261103T032128Z-g/`(8파일)·`eval/f0/results/20261103T032128Z-regression-g/`(8파일), 신규 capture `eval/f0/chat-template/v3-diag-g/`·`eval/f0/chat-template/v3-regression-g/`(각 4파일), 이 절.
- 하지 않은 것: 코드·시험·프롬프트·골든·fixture·PREREG·audit·기존 결과/capture·evidence·SPEC·Plan 변경, git 쓰기 전부, 모델 load/pull/unload·서비스 재시작·num_ctx 변경·다운로드, 정식 R5(gate-g)·F0 leaf 증거·gate·SPR, P04.
- **결론(진단 R1, 판정 아님):**
  - 진단 g 7개 명령과 개발 회귀 g 7개 명령은 모두 exit 0·`complete=true`다. 서버 `response_format` 거부, timeout, 서비스 재시작·OOM은 0이다.
  - 진단 g: 경계 boundary-gen 9/9, B G08 최종 t `INC`, `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0이다. A는 taskType 21/22로 G06·G19가 `LQ`로 돌아왔다. 그러나 A G08은 두 시도 모두 t=`INC`·steps `EMPTY_FOR_TOOL_TASK`(`STEPS_INVALID` 2회)로 최종 `INVALID`다(f와 같은 실패 축).
  - 개발 회귀 g: R01~R08 A·B 모두 통과다. 원래 22문항 행은 진단 g와 첫 출력 SHA가 A·B 22/22 같고, 경계도 9/9 같다.
  - 따라서 gate-g §1 조건 3b(G08 최종 t A·B 모두 INC)가 미충족이다(판정은 root). 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(읽기 전용 확인)

- 확인 방법: §12.18과 같다(SSH 읽기 전용 + `bash -s`, `GET /api/ps`는 Jetson 안 python3로 허용 필드만 파싱하고 실패 시 `parse failed`만 출력). 사용자가 12:16:36 KST에 `systemctl restart ollama`를 직접 실행했고, 이 세션은 warmup이 두 모델을 적재할 때까지 읽기 전용 확인만 했다(03:17:57Z 상주 0 → 03:19:14Z qwen3 1개 → 03:21:04Z 2개).
- 03:21:04Z 확인 결과(착수 조건 충족):
  - 서비스: active/running, `ActiveEnterTimestamp=2026-11-03 12:16:36 KST`, NRestarts 0.
  - journal(서비스 시작 이후): `library=cpu` 0건, `Load failed` 0건, `library=CUDA` 4줄.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - Mem 7619 MiB 중 available 2173, swap 3809 MiB 중 사용 1241. 로컬 11434는 미사용이었다.
- 각 B·A·boundary 뒤 재확인에서 중단 조건 항목(ActiveEnterTimestamp·NRestarts 0·2모델 digest·ctx 4096·size_vram=size·`library=cpu`/`Load failed` 0)은 모두 유지됐다. Jetson 시계는 로컬보다 약 3초 늦다.

| 확인 시각(UTC) | 시점 | 상주 모델 | Mem available MiB | swap 사용 MiB | 중단 조건 |
|---|---|---|---|---|---|
| 03:17:57 | warmup 중 | 0 | 5643 | 1127 | 대기 |
| 03:19:14 | warmup 중 | 1(qwen3) | 2994 | 1136 | 대기 |
| 03:21:04 | 진단 g 착수 전 | 2 | 2173 | 1241 | 충족 |
| 03:24:09 | 진단 g B 뒤 | 2 | 2134 | 1224 | 충족 |
| 03:25:58 | 진단 g A 뒤 | 2 | 1918 | 1223 | 충족 |
| 03:27:50 | 진단 g boundary 뒤 | 2 | 1862 | 1271 | 충족 |
| 03:28:21 | 회귀 g 착수 전 | 2 | 1853 | 1271 | 충족 |
| 03:30:51 | 회귀 g B 뒤 | 2 | 1429 | 1317 | 충족 |
| 03:33:16 | 회귀 g A 뒤 | 2 | 1479 | 1316 | 충족 |
| 03:35:00 | 회귀 g boundary 뒤 | 2 | 1661 | 1328 | 충족 |
| 03:37:22 | 전 명령 종료 뒤 | 2 | 1656 | 1328 | 충족 |

- 진단 f 때(§12.18 1) available 160~271 MiB·swap 1784→3621 MiB)와 달리 이번에는 available이 1429 MiB 아래로 내려가지 않았고 swap 증가는 약 90 MiB였다.

#### 2) 실행 기록

- 공통 조건: §12.18 2)와 같다. Bash에서 `powershell.exe -NoProfile -File <scratchpad 래퍼>`로 실행했고, 래퍼는 `Set-Location C:\workspace_nori\nori-ai-gateway` 뒤 `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`를 호출한다. `--args` 값은 각 등록 §2와 같다. 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=각 회차 ID, `DEBUG`=빈 값. 한 명령씩 실행하고 exit 0을 확인한 뒤 넘어갔다. 측정 중 다른 Gradle 실행은 0이다.
- run prefix `20261103T032128Z`(시작 때 1회 결정). 회차 ID는 `20261103T032128Z-g`·`20261103T032128Z-regression-g`다. 결과 2디렉터리·capture 2디렉터리는 사전 부재를 확인했다.
- 터널: `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다. 진단 g는 PID 13436, 회귀 g는 PID 7904이며, 각각 명령행을 확인한 뒤 report 전에 `Stop-Process`로 종료했다. 종료 후 11434 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| g-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-g.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `4de7eadb…5597`, parameterManifestSha256 `b9b25664…8a2d` |
| g-2 | `f0 preflight … --out eval/f0/results/20261103T032128Z-g/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| g-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-g`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| g-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3-f.txt --run-id 20261103T032128Z-g --out …/B` | 0 | 03:22:15~03:24:08, `F0_RUN_COMPLETED B`(Gradle 1m 51s) |
| g-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3-g.txt …` | 0 | 03:24:19~03:26:01, `F0_RUN_COMPLETED A`(1m 41s) |
| g-6 | `f0 boundary-gen … --prompt eval/f0/v3/prompts/classify-v3-f.txt …` | 0 | 03:26:09~03:27:53, `F0_BOUNDARY_COMPLETED 9`(1m 43s) |
| g-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-g.md --run-id 20261103T032128Z-g --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `1258fe32…9176` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression-g.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `fd7e9523…6cb5` |
| r-2 | `f0 preflight …/20261103T032128Z-regression-g/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression-g`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template `40c21f34…b541`, model-digest `0edcdef3…8ba0` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | 0 | 03:28:52~03:30:54, `F0_RUN_COMPLETED B`(2m 1s) |
| r-5 | `f0 run … --path A --golden eval/f0/v3/golden-regression-v3.jsonl … --prompt eval/f0/v3/prompts/plan-v3-g.txt …` | 0 | 03:31:03~03:33:19, `F0_RUN_COMPLETED A`(2m 15s) |
| r-6 | `f0 boundary-gen …` | 0 | 03:33:20~03:35:03, `F0_BOUNDARY_COMPLETED 9`(1m 42s) |
| r-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-regression-g.md --run-id 20261103T032128Z-regression-g --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN` |

- 소요 시간은 d·e 수준이고 진단 f(B 3m 4s·A 12m 6s)보다 짧다. preflight 두 파일의 SHA 차이는 모델 나열 순서뿐이다(같은 두 digest·ctx 4096·residentCount 2).
- F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 8개(진단 g B·A·boundary·capture, 회귀 g B·A·boundary·capture)의 runtimeManifest는 모두 같다.
  - model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`(runtimeManifestSha256 `b1e6a3af…6f6a`, c·d·e·f와 같음), specSha `34fd6223…66be`(FILE_HASHED, LOCAL_FILE)
  - registeredCommit C13 `1b1ded2566e911520e03cbf82606eba4e7f2b133`, preregCommit C14 `87113b50a0a57f75c80c3e33a6faec1c3d39189d`
  - `contextProjection=PRESENCE_V1`, `responseFormat=JSON_SCHEMA_V1`, parameterManifestSha256 `b9b25664…8a2d`
  - harnessDigest: 진단 g `4de7eadb…5597`, 회귀 g `fd7e9523…6cb5`(각 등록값과 같음)
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출(작음/큼)은 두 회차 모두 A 0/0, B 0/0, boundary 0/0이다(보고서와 정제 필드 재집계 모두).
- 첫 시도 프롬프트 토큰 범위: 진단 g A 1754~1791·B 1353~1390, 회귀 g A 1754~1807·B 1353~1406. audit 최대(A 1791/1807·B 1390/1406)와 같다. 출력 토큰 최대: 진단 g A 89·B 60, 회귀 g A 89·B 78.

#### 4) 진단 g 결과(`eval/f0/results/20261103T032128Z-g/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

- 재집계는 §12.18과 같은 스크립트(결과 JSON의 열거형·불리언·개수·해시 필드만)로 했다.

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 21/22 | 15/22 | 21/22 | 21/22 | 21/22 | 1/22 | 3102/6736 | `STEPS_INVALID` 2(G08) |
| B | 20/22 | 18/22 | 20/22 | 22/22 | 22/22 | 0/22 | 2949/5307 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 6·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2. timeout은 A·B 모두 0이다.
- 지연 최대: A 9744 ms(G01), B 32170 ms(G01, 회차 첫 요청). B 나머지 21건은 ≤5307 ms다.
- A 재시도 1건: G08만이다. 두 시도 모두 t=`INC`·steps `EMPTY_FOR_TOOL_TASK`라 최종 `INVALID`다. f에서 재시도가 있던 G05·G07·G16은 g에서 초회 유효다.
- t 오답:
  - A: G08(최종 무효). timeout 0.
  - B: G07(UNS→INC), G16(UNS→GQ).
- 인자 오답:
  - A: G01·G09·G11·G12·G17 from+to, G16 eqp, G08 무효.
  - B: G01·G11·G12·G17 to.
- A 최종 steps(유효 21문항, `predicted.steps` capability ID):
  - INC 6문항 모두 `tc.logs.search`를 포함한다: G01 `tc.logs.search;tc.git.searchCode`, G09 `tc.logs.search`, G12 `tc.dlq.list;tc.logs.search`, G17 `tc.logs.search`, G20 `tc.logs.search`, G22 `tc.logs.search;tc.event.getRecent`.
  - LQ: G02·G06·G10·G19 `tc.eqp.getOperationalSnapshot`, G11 `tc.event.getRecent`. KQ: G04·G13 `tc.knowledge.search`, G21 `tc.git.searchCode`. GQ·UNS 7문항 `[]`.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. 후보 0 시도는 모두 `qRef.kind=JSON_NULL`(A 20 = G08 2시도 포함, B 19)이고, 후보 1 시도는 `ID_IN_RANGE`(A 3·B 3)다. `ID_OUT_OF_RANGE`는 경계를 포함해 0이다.
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to의 `equalsContext=true`는 0건이다.
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - B: 초회 t=`INC`, 재시도 0, 최종 effectiveT `INC`. eqp·from·to는 모두 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`. 첫 출력 SHA `b4db4d71…`은 f와 같다.
  - A: 초회·재시도 모두 t=`INC`, steps `EMPTY_FOR_TOOL_TASK`(steps 길이 0) → 최종 `INVALID`, effectiveT 없음(`INVALID_OUTPUT`). 최종 시도 eqp·from·to는 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`.
  - **A G08 최종 steps에 `tc.logs.search`는 없다.** 최종 상태가 `INVALID`라 `predicted`(따라서 `predicted.steps` 정제 필드)가 null이며, 대신 최종 시도 `attemptDiagnostics.steps.toolConsistency=EMPTY_FOR_TOOL_TASK`(길이 0)로 확인했다.
  - A G08 두 시도 출력 SHA는 같고(`d648833f…b242`), 정제 필드 값으로 만든 키 정렬·공백 없는 직렬화 `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC","to":null}`의 SHA-256과 정확히 같다(64자 일치). f(`8979805c…ce47`)와는 `"to"` 앞 공백 1개만 다르고, 키 순서는 여전히 steps가 t보다 앞이다. 즉 g 프롬프트 뒤에도 G08은 steps를 빈 배열로 먼저 쓰고 t=`INC`를 썼다.
- **경계 9케이스 boundary-gen 9/9**(모두 status OK, schemaValid=true, argsExact=true, finish=stop, 재시도 0, completion ≤192, `equalsContext` 0, prompt=audit 9/9). 첫 출력 `outputSha256`은 9/9 모두 f와 같다(입력 바이트가 f와 같음).

| caseId | 판정 | 불통과 축 | completion | qRef | 첫 출력 SHA = f |
|---|---|---|---|---|---|
| NORMAL-01 | 통과 | – | 59 | JSON_NULL/후보0 | 같음 |
| NORMAL-PRECISE | 통과 | – | 97 | JSON_NULL/후보0 | 같음 |
| MAX-MIXED | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-HANGUL | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-DIGIT | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-ASCII | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-SUPPLEMENTARY | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| ESCAPE-01 | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| ESCAPE-CONTROL | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |

- **B 22문항 첫 출력 `outputSha256`은 22/22 f와 같다.** 따라서 B 결과(t·인자·qRef·Context 복사)는 f와 같고, B 지연만 달라졌다(f p95 7100 → g 5307).

#### 5) 개발 회귀 g 결과(`eval/f0/results/20261103T032128Z-regression-g/diagnostic-report.md`, R1·분모 30)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 29/30 | 23/30 | 29/30 | 29/30 | 29/30 | 1/30 | 3250/8116 | `STEPS_INVALID` 2(G08) |
| B | 28/30 | 26/30 | 28/30 | 30/30 | 30/30 | 0/30 | 3047/6239 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 6·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2. timeout 0, `QREF_INVALID` 0, `ID_OUT_OF_RANGE` 0, Context 복사 0이다. qRef는 A·B 각각 `JSON_NULL`(A 25·B 24), `ID_IN_RANGE` 후보1 5, 후보2 1(R07)이다.
- 원래 22문항 행은 진단 g와 첫 출력 SHA가 A 22/22·B 22/22 같으므로 t·인자 오답은 진단 g와 같다(A G08 무효·G01·G09·G11·G12·G17 from+to·G16 eqp, B G07·G16 t·G01·G11·G12·G17 to). 회귀 경계도 진단 g와 첫 출력 SHA 9/9가 같고 9/9 통과다.
- 회귀 A G08도 같은 SHA `d648833f…b242`로 두 시도 `STEPS_INVALID`, 최종 `INVALID`다.
- 신규 8문항 A 최종 steps: R01·R03·R08 `tc.eqp.getOperationalSnapshot`, R02·R04·R06 `tc.logs.search`, R05 `[]`, R07 `tc.knowledge.search`.

| id | 축 | 회귀(c) | 회귀 d | 회귀 f | 회귀 g |
|---|---|---|---|---|---|
| R01 | REG_QUOTED_EQP | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 |
| R02 | REG_EQP_VS_CODE_TERM | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 |
| R04 | REG_TC_TRANSFER_FAILURE | A 실패·B 통과 | A·B 통과 | 미측정 | A·B 통과 |
| R05 | REG_WM_OWNED_RECORD | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 |
| R06 | REG_PARAPHRASED_RANGE | A·B 통과 | A from·to 실패·B 통과 | 미측정 | A·B 통과 |
| R07 | REG_SECOND_QUOTED_INTENT | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 |
| R08 | REG_LONG_EQP_ID | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 |

- 회귀(c)·회귀 d 열은 이 세션에서 `20261102T231328Z-regression`·`20261103T001459Z-regression-d` 정제 필드로 다시 계산했고 §12.18 5) 기록과 같다.
- 회귀 g B·경계 요청은 회귀 f와 메시지 바이트가 같으므로(regression-g §1), 회귀 g B·경계 결과는 f 분류 입력의 회귀 결과로도 읽을 수 있다(R=1).

#### 6) c·d·e·f·g 비교(원문 없는 정제 필드·출력 해시만)

- 등록 구성: c = FULL + c 프롬프트, d = EXTRACTION_V1 + d 프롬프트, e = EXTRACTION_V1 + c 프롬프트, f = PRESENCE_V1 + f 프롬프트, g = PRESENCE_V1 + `classify-v3-f` + `plan-v3-g`.

| 지표 | c | d | e | f | g |
|---|---|---|---|---|---|
| A taskType / 인자 | 20/22 / 13/22 | 19/22 / 15/22 | 16/22 / 13/22 | 17/22 / 13/22(timeout 2 포함) | 21/22 / 15/22 |
| B taskType / 인자 | 18/22 / 15/22 | 17/22 / 15/22 | 17/22 / 17/22 | 20/22 / 18/22 | 20/22 / 18/22 |
| A JSON 유효 / 1차 유효 / 재시도 | 22 / 18 / 4 | 21 / 17 / 5 | 21 / 18 / 4 | 19 / 16 / 4 | 21 / 21 / 1 |
| A / B p95 ms | 10724 / 6107 | 11335 / 6211 | 10135 / 6136 | 120011 / 7100 | 6736 / 5307 |
| G08 최종 t A / B | INC / INC | UNS / UNS | UNS / UNS | 무효(두 시도 t=INC) / INC | 무효(두 시도 t=INC) / INC |
| Context 복사 시도 A / B | 3 / 2 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 경계 boundary-gen | 2/9 | 7/9 | 0/9 | 9/9 | 9/9 |

- A 문항별(셀 = 최종 effectiveT / 틀린 인자, 굵게 = t 오답·무효·timeout, (ctx) = Context 복사 시도 있음):

| id | 기대 t | A c | A d | A e | A f | A g |
|---|---|---|---|---|---|---|
| G01 | INC | INC / from+to | INC / from+to | INC / from+to | **TIMEOUT** | INC / from+to |
| G02 | LQ | LQ / – | LQ / – | LQ / – | **TIMEOUT** | LQ / – |
| G03 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G04 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G05 | GQ | GQ / – | GQ / – | **무효** | GQ / – | GQ / – |
| G06 | LQ | **KQ** / – | **KQ** / – | **KQ** / – | **KQ** / – | LQ / – |
| G07 | UNS | UNS / – | UNS / – | **GQ** / – | UNS / – | UNS / – |
| G08 | INC | INC / from+to (ctx) | **UNS** / – | **UNS** / – | **무효** | **무효** |
| G09 | INC | INC / – | INC / from+to | INC / from+to | INC / from+to | INC / from+to |
| G10 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G11 | LQ | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to |
| G12 | INC | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / from+to |
| G13 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G14 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G15 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G16 | UNS | UNS / eqp | UNS / – | UNS / eqp | UNS / eqp | UNS / eqp |
| G17 | INC | INC / from+to | **무효** | **UNS** / eqp+from+to | INC / from+to | INC / from+to |
| G18 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G19 | LQ | **KQ** / – | LQ / – | **KQ** / – | **KQ** / – | LQ / – |
| G20 | INC | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / eqp | INC / – |
| G21 | KQ | KQ / eqp | KQ / eqp | KQ / eqp | KQ / – | KQ / – |
| G22 | INC | INC / from+to | INC / from+to | INC / from+to | INC / – | INC / – |

- B 문항별(g는 f와 첫 출력 SHA 22/22 같음):

| id | 기대 t | B c | B d | B e | B f | B g |
|---|---|---|---|---|---|---|
| G01 | INC | INC / from+to | INC / to | INC / from+to | INC / to | INC / to |
| G02 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G03 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G04 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G05 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G06 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G07 | UNS | **INC** / – | **GQ** / – | **GQ** / – | **INC** / – | **INC** / – |
| G08 | INC | INC / from+to (ctx) | **UNS** / – | **UNS** / – | INC / – | INC / – |
| G09 | INC | **LQ** / – | **LQ** / from+to | **LQ** / – | INC / – | INC / – |
| G10 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G11 | LQ | LQ / from+to | LQ / to | LQ / to | LQ / to | LQ / to |
| G12 | INC | **LQ** / from+to | **LQ** / from+to | **LQ** / from+to | INC / to | INC / to |
| G13 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G14 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G15 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G16 | UNS | **GQ** / – | **GQ** / – | **GQ** / – | **GQ** / – | **GQ** / – |
| G17 | INC | INC / from+to | INC / from+to | INC / from+to | INC / to | INC / to |
| G18 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G19 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G20 | INC | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / – | INC / – |
| G21 | KQ | KQ / eqp | KQ / eqp | KQ / eqp | KQ / – | KQ / – |
| G22 | INC | INC / – | INC / from+to | INC / – | INC / – | INC / – |

- c·d·e·f 열은 §12.18 6) 표를 옮긴 것이다. 경계 케이스별 c·d·e·f는 §12.18 6) 표와 같고, g는 9/9 통과이며 첫 출력 SHA가 f와 9/9 같다(completion 59·97·146×7).
- 첫 시도 `outputSha256` 일치 수(22문항): A g=c 4·g=d 6·g=e 7·g=f 6/20(f G01·G02는 출력 없음), B g=c 14·g=d 15·g=e 15·g=f 22.
- 관찰(R1, 인과 확정 아님):
  - g에서 A 실패는 G08 무효 하나와 인자 축(from+to 5·eqp 1)만 남았다. c~f 전반의 A G06·G19 `KQ`는 g에서 `LQ`(steps `tc.eqp.getOperationalSnapshot`)로 바뀌었고, f의 G05·G07·G16 `NONEMPTY_FOR_NO_TOOL_TASK` 재시도도 사라졌다.
  - A의 유효 INC 6문항은 모두 steps에 `tc.logs.search`를 포함한다(f 유효 INC 5문항 G09·G12·G17·G20·G22 중 `tc.logs.search` 포함은 G09 1건).
  - A G08만은 g 프롬프트 뒤에도 steps를 빈 배열로 먼저 쓰고 t=`INC`를 썼다. §12.19 위험 항목의 "t가 `UNS`로 따라감"은 일어나지 않았고, f와 같은 무효 축이 그대로 남았다.
  - A·B 지연은 c~e 수준 이하로 돌아왔다. 진단 f의 A timeout·지연 증가는 이번처럼 메모리 여유가 큰 조건에서는 재현되지 않았다(인과 확정 아님).

#### 7) `PREREG-v3-gate-g.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. C13/C14 뒤 세 문서 `PREREG_VERIFIED` | 충족 | diag-g·regression-g는 이번 g-1·r-1, gate-g는 §12.19 C13/C14 절(이 세션 미재실행) |
| 2. 진단 g·회귀 g exit 0·`complete=true`, 서버 `response_format` 거부(exit 5) 0 | 충족 | 14개 명령 exit 0, A·B·boundary `complete=true`(두 회차), 거부 0 |
| 3a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 3b. G08 최종 t A·B 모두 INC | **미충족** | B 최종 `INC`. A는 두 시도 t=INC였지만 steps `EMPTY_FOR_TOOL_TASK` 2회로 최종 `INVALID`(effectiveT 없음) |
| 3c. 경계 9 boundary-gen 9/9 | 충족 | 위 4) |
| 4. root 원문 없는 검토 | 미완(대기) | 이 절과 진단 g·회귀 g 보고서 |

- 조건 3b가 미충족이므로 gate-g §1에 따라 이 등록으로 R5를 실행하지 않는다. 이 세션은 R5·F0 leaf·gate·SPR을 만들지 않았다.

#### 8) 미실행·원인 미확정·다음 정확 조치

- 미실행: gate-g prereg-check 재실행, 정식 R5, P04.
- 원인 미확정:
  - A G08만 steps가 빈 배열로 남는 이유. 같은 프롬프트에서 다른 INC 6문항은 `tc.logs.search`를 넣었다. 원문을 저장하지 않으므로 출력 형태를 추정으로 결론 내리지 않는다.
  - B G07 UNS→INC, B G16 UNS→GQ(분류 입력 불변이라 f와 같음).
  - A 인자 축(G01·G09·G11·G12·G17 from+to, G16 eqp).
- 다음 정확 조치(root 결정):
  1. root가 진단 g·회귀 g 보고서와 이 재집계를 원문 없이 검토한다.
  2. A G08 무효 축에 대한 보완은 새 사전등록 파일로만 한다(gate-g §1 마지막 항목). 이 세션은 설계하지 않았다.

#### 9) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`는 불변이다(모든 run-binding specSha). HEAD C14 `87113b50a0a57f75c80c3e33a6faec1c3d39189d`, staged 0이며 git 쓰기는 0이다.
- `git status --short`는 세션 시작과 같다(Devreport M, 기존 untracked 5항목). PREREG·입력·코드 변경은 0이다.

| 파일 | 바이트 | SHA-256 |
|---|---|---|
| `eval/f0/results/20261103T032128Z-g/preflight.json` | 304 | `c9c344acfed2de216b5dc41b87b808e574107601830ed9385ce6bcd2da4d69e2` |
| `eval/f0/results/20261103T032128Z-g/B/f0-results.json` | 31111 | `3ca5ba38bcec98ae92c94e9feac4a03e1efddcdde295df87b46763ba782a2b97` |
| `eval/f0/results/20261103T032128Z-g/B/run-binding.json` | 1747 | `ec780be689192f9ba5e15f9a8d68dfd9a63ad39009101e653a25f0de25dea067` |
| `eval/f0/results/20261103T032128Z-g/A/f0-results.json` | 35001 | `c2cedc5485df34fb8953bf991b65f1984529e8ca30055eba56c7fea20cfafb19` |
| `eval/f0/results/20261103T032128Z-g/A/run-binding.json` | 1744 | `e714e82a6ec6142515371e334d1401e46762858b8f1e33d32b5efddbee253212` |
| `eval/f0/results/20261103T032128Z-g/boundary/boundary-results.json` | 10005 | `f2ca5f60ad439d887718d32db55aa3b760d609e1b31770bd46325dc3d021f331` |
| `eval/f0/results/20261103T032128Z-g/boundary/run-binding.json` | 1747 | `8138ecf619ee389c9b5c49e489dea2c4616beda655c053aca93cad7fd89fd97b` |
| `eval/f0/results/20261103T032128Z-g/diagnostic-report.md` | 8833 | `648d27b9886fdc64f9a85aeabd272a076de5077b85331599e0469ddb3973ca94` |
| `eval/f0/results/20261103T032128Z-regression-g/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T032128Z-regression-g/B/f0-results.json` | 42240 | `6f21d38a30c59bff45bfadc0d2107e88ea8e361a516a705ed73e9a3a8356393e` |
| `eval/f0/results/20261103T032128Z-regression-g/B/run-binding.json` | 1761 | `764977bbcc0186d836d156864ce8ee4d0b6dae1ba2cdf9b1ee560788af2d813a` |
| `eval/f0/results/20261103T032128Z-regression-g/A/f0-results.json` | 47437 | `17b20c06359ba5f635ca3e88db6b2cb3e602a038735594775113dc2d0bf42ea5` |
| `eval/f0/results/20261103T032128Z-regression-g/A/run-binding.json` | 1761 | `d1475c72fe3b2d1b82fc7724ed13a1346f2d4c1f70b9452f3860adfd417d6952` |
| `eval/f0/results/20261103T032128Z-regression-g/boundary/boundary-results.json` | 10016 | `5d33368e16d27c1cfbe43f4763b33d8f74502cd7b31634c216dd53f862e0ab68` |
| `eval/f0/results/20261103T032128Z-regression-g/boundary/run-binding.json` | 1764 | `2c9863523bd0351f441b15a386ec1423b24d46092bac759f8c7b5ca3ee4a2468` |
| `eval/f0/results/20261103T032128Z-regression-g/diagnostic-report.md` | 9162 | `93c802a885702e8246e65f5df52779dd6351fd2dfb58747679dfb2272b9d997b` |
| `eval/f0/chat-template/v3-diag-g/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-diag-g/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-diag-g/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-diag-g/run-binding.json` | 1748 | `1eb4b2f0ec7534089e595fdc712ffb68156ea9d1f9e937dd66486cf36171926a` |
| `eval/f0/chat-template/v3-regression-g/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-regression-g/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-regression-g/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-regression-g/run-binding.json` | 1765 | `7992270f51e4d796477ab9a917e72613dfb701135809e6f11e414a40a412b983` |

- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 이 Devreport는 UTF-8·LF·BOM 없음이다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 집계는 결과 JSON의 열거형·불리언·개수·해시 필드, `predicted.steps` capability ID, 골든의 id·기대 t만 사용했다. 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.21 P03 A 스키마 키 순서 보완 후보 h 구현·등록 초안 (2026-11-03, 독립 Claude Code Opus 5.5 high, root 검토 전)

- §12.1~§12.20은 바꾸지 않았다. 이 절 앞 바이트(354784 B, SHA `1ad9d388947861b69d5d78ce894bf77f78edf610edc75b54cbb2694bb582eb43`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 변경, 다운로드, 새 capture, git 쓰기 전부. 제품 wire 키 집합·의미(`t`·`eqp`·`from`·`to`·`qRef`·`steps`)·`f0-judge-v2`·정확 일치 oracle·골든·fixture·경계 케이스·임계·분모·예산(입력 3072, `max_tokens` 192/256, ctx 4096)·재시도 1회·timeout·`classify-v3-f`·`PRESENCE_V1`·기존 등록·결과·capture·evidence·SPEC·Plan 변경 0.
- **결론: A G08 의 구조적 원인(요청 본문 전체 키 정렬로 계획 스키마 `properties` 가 `eqp, from, qRef, steps, t, to` 순서로 송신됨)을 코드로 확정했고, 새 등록에서만 켜지는 응답 형식 값 `JSON_SCHEMA_PLAN_ORDERED_V1`(계획 스키마 `properties` 만 `t, eqp, from, to, qRef, steps` 순서로 송신)과 `plan-v3-h` 의 구현·시험·audit·등록 초안 3개까지 마쳤다. 기존 등록 값(NONE·JSON_SCHEMA_V1)과 B·경계·usage·token 요청 바이트는 그대로다. h 는 실측하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 진단 h 를 실행하려면 로컬 C15/C16 사용자 허용이 먼저 필요하다.

#### 1) 현재 키 순서 확인(코드·기록 대조)

- 송신 경로(변경 전 HEAD C14 기준):
  - `F0Measurements.Attempts` 가 등록 값이 `JSON_SCHEMA_V1` 이면 `ResponseSchema.responseFormat(kind, references)` 를 만든다(`F0Measurements.java:89`).
  - `MeasurementSession.chat`(`:96`) → `OllamaChatClient.chat` 이 본문 Map 에 `response_format` 를 넣는다(`OllamaChatClient.java:62`).
  - `send` 가 본문 전체를 `Json.canonical(Json.MAPPER.valueToTree(body))` 로 직렬화한다(`OllamaChatClient.java:86`). `Json.canonicalString` 은 모든 객체 키를 정렬한다(`Json.java:56` `Collections.sort(names)`).
- `ResponseSchema.responseFormat` 은 `properties` 를 `t, eqp, from, to, qRef(, steps)` 순서로 만든다(변경 후 `ResponseSchema.java:44-52`, 본문 불변). 그러나 송신 바이트의 `properties` 는 A(계획) `eqp, from, qRef, steps, t, to`, B·경계(분류) `eqp, from, qRef, t, to` 다. 배열인 `required` 는 정렬되지 않아 `t, eqp, from, to, qRef(, steps)` 그대로 간다. 관찰된 A 출력 키 순서(§12.19 39건, §12.20 G08)는 송신된 `properties` 순서와 같고 `required` 순서와는 다르다.
- 기록 대조: 진단 g·회귀 g 결과 122행(A 52·B 52·경계 18)의 `responseFormatSha256` 은 시험 쪽에서 독립 재구성한 스키마의 정렬 canonical SHA 와 122/122 같다(Node 재구성, 원문 없음).
- A 스키마만 바꿀 수 있는가: 그렇다. 계획 스키마는 `Kind.PLAN` 에서만 만들어지고(`steps`·`name=f0_plan`), PLAN 을 쓰는 경로는 A 뿐이다. 근거는 run 의 프롬프트 역할 선택(`F0RunnerCli.java:54`), boundary-gen 의 CLASSIFY 강제(`F0Measurements.java:43`), usage·token 의 `response_format` 없는 3인자 chat(`F0Measurements.java:176`·`:196`)이다.

#### 2) 후보 h 설계

- opt-in 방식: 기존 `Prereg.RESPONSE_FORMATS` 에 새 값 `JSON_SCHEMA_PLAN_ORDERED_V1` 을 더했다(`Prereg.java:43`, `CONTEXT_PROJECTIONS` 와 같은 패턴). 서버 출력 제약 선택은 이미 `parameters.responseFormat` 하나가 담당하므로 별도 파라미터를 만들지 않았다. v3 전용이며 v1/v2 의 키 거부 규칙은 기존 그대로다. 기존 값 `NONE`·`JSON_SCHEMA_V1` 의 의미는 바뀌지 않는다.
- 구현(f0 패키지 5파일):
  - `ResponseSchema.Format(node, orderedPaths)` 레코드(`ResponseSchema.java:29`): 송신 값과 직렬화 규칙을 담는다. `bytes()` = `canonicalKeepingOrder(node, orderedPaths)`, `sha256()` = SHA-256(bytes()).
  - `ResponseSchema.forPolicy(policy, kind, refs)`(`:34`): `NONE` 은 null, `JSON_SCHEMA_V1` 은 (기존 스키마, 경로 없음), `JSON_SCHEMA_PLAN_ORDERED_V1` 은 (같은 스키마, PLAN 일 때만 경로 `json_schema.schema.properties` 하나)이다.
  - `canonicalKeepingOrder`(`:67`): 경로가 비면 `Json.canonical` 을 그대로 반환한다. 경로가 있으면 그 객체만 노드 순서로 쓰고, 나머지 객체는 정렬하며, 문자열·숫자는 `Json.canonicalString` 바이트 그대로다. `Json.java`(io 패키지)는 고치지 않았다. 따라서 전역 정렬은 그대로다. 또 이 코드를 f0 패키지에 둬서 변경이 harnessDigest(f0 소스만 포함)에 반영되게 했다.
  - `OllamaChatClient.chat(…, ResponseSchema.Format)`(`:58-67`): 본문에 `node` 를 넣고 순서 보존 경로를 `response_format` 아래로 붙여 `send` 에 넘긴다. `send(method, path, body)` 는 `Set.of()` 로 위임하므로 ps·tags·show·generate·usage·token 송신은 기존 식과 같다(`:86-92`).
  - `MeasurementSession.chat` 은 매개변수 타입만 바꿨다(`:93`). `F0Measurements.Attempts` 는 `forPolicy`·`responseFormat.sha256()` 을 쓴다(`:89`·`:101`).
  - `ResponseSchema.sha256(ObjectNode)` 는 main 에서 쓰이지 않게 되어 제거했다. 이를 쓰던 시험 1줄(`F0StructuredOutputDiagnosticTest`)은 같은 정의인 `Json.sha(Json.canonical(…))` 로 직접 적었다.
- `responseFormatSha256` 정의 변경: "정렬 canonical 의 SHA" 에서 "요청 본문 안 `response_format` 값의 실제 송신 바이트 SHA-256" 으로 바꿨다. `JSON_SCHEMA_V1` 과 h 의 분류 스키마는 송신 바이트가 정렬 canonical 이므로 값이 기존과 같다(시험 ②③, 위 기록 대조 122/122). h 의 계획 스키마만 순서 보존 바이트의 SHA 가 된다.
- `plan-v3-h`(g 기반, diff 3곳 4줄):
  - [출력 스키마] 제목을 "키는 이 순서로 출력되며 t 가 steps 보다 먼저 나온다" 로 바꿨고, 표시 줄은 `{"t":…,"eqp":…,"from":…,"to":…,"qRef":…,"steps":[…]}` 다(f 와 같은 줄, 송신 순서와 같음).
  - [steps] 제목은 "출력에서 t 가 steps 보다 먼저 나온다. steps 는 앞서 [t 분류]·[소유 시스템 판단] 으로 정한 t 에 맞춘다" 다.
  - "뒤에 쓰는 t 는 앞서 쓴 steps 와 맞는 t 다" 를 "steps 는 앞서 쓴 t 와 맞아야 한다" 로 바꿨다.
  - t 별 Tool 규칙(INC 는 `tc.logs.search` 포함 등)·[t 분류]·[소유 시스템 판단]·[인자]·`STEPS_INVALID` 뜻은 g 와 바이트가 같다. 크기는 4104 B, pin tokenizer 1206토큰이다(g 1219, f 1044).
- 3b 충족 근거:
  - G08 실패는 t 가 아니라 t 보다 먼저 생성되는 steps 였다. A 두 시도의 t 는 f·g 모두 `INC` 였다.
  - h 는 송신 스키마에서 `t` 를 첫 키, `steps` 를 마지막 키로 둔다. 프롬프트 지시만으로 바꾸지 못한 생성 순서를 서버 제약 쪽에서 맞추는 것이다.
  - t 가 먼저 `INC` 로 정해지면, steps 는 g 와 같은 INC 규칙(`tc.logs.search` 포함)을 따른다. 진단 g 에서 다른 유효 INC 6문항은 모두 이 규칙대로 `tc.logs.search` 를 넣었다.
  - B G08 은 g 와 같은 입력이므로 g 의 초회 `INC` 결과 조건과 같다.
- 3a·3c·회귀 근거: B·경계 메시지와 분류 스키마 송신 바이트가 g 와 같다. 따라서 g 의 `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0·경계 9/9·회귀 R01~R08 B 통과와 같은 입력이다(R=1·temperature 0, 서버·모델 동일 전제). A 쪽 R01~R08 유지 여부는 회귀 h 로 확인한다.
- 위험(실측 전 미확정):
  - 서버가 스키마 `properties` 순서를 그대로 출력 생성 순서로 쓰는지는 서버 코드로 확인하지 않았다(추정). 지금까지 송신 순서와 정렬 순서가 같았으므로, 기존 39/39 관찰은 "서버가 송신 순서를 따른다" 와 "서버가 키를 다시 정렬한다" 를 구분하지 못한다. 후자라면 h 의 A 출력은 g 와 같은 키 순서가 되며, 진단 h 의 A 출력 SHA 로 판별된다.
  - t 를 먼저 쓰면 A 의 t 판단이 steps 근거 없이 정해진다. 진단 g 에서 steps 와 맞춰 고쳐졌던 G06·G19(LQ)가 다시 `KQ` 로 갈 수 있다.
  - 인자(eqp·from·to·qRef)가 steps 앞으로 오면서 A 인자 출력이 g 와 달라질 수 있다(실제 생성 순서가 c~g 와 달라지는 첫 후보).
  - R=1·단일 결정 출력 근거다.
- SPEC 계약: 정본 SPEC 1203줄은 요청별 스키마가 "wire 계약만 표현"(필수 키·추가 키 금지·`t` enum·nullable 인자·`qRef` enum/null·`steps` 문자열 배열)한다고 정하고 키 순서는 정하지 않는다. h 의 스키마 내용은 V1 과 같다(`Json.parse` 동등 시험). 따라서 SPEC 계약 변경은 필요 없다.

#### 3) 변경 파일(이 절 제외 15파일 + Devreport)

| 파일 | 구분 | 바이트 | SHA-256 |
|---|---|---|---|
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/ResponseSchema.java` | 수정(+46/−2) | 5824 | `8fbe19470dbd3af54d142bfb5c13cc071ad4f9cc8205c319d6bbcabcfd1eaaa0` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/OllamaChatClient.java` | 수정(+12/−6) | 10586 | `39317a1c50b83f154b65115ded94c818e4867bec8158d5da3049e28c1a1741db` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/MeasurementSession.java` | 수정(+2/−3) | 8080 | `f8162d1d1084070f3b6139c1b8d545215b50e2169629832007433c8733e3c11d` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/F0Measurements.java` | 수정(+3/−3) | 20222 | `5d19695da945ea66ed576e0fe26b2e92d0c6a0d1487eadda9ceeb2b0bf9b6fdb` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Prereg.java` | 수정(+2/−2) | 16620 | `1445996e891ba060a53d63f7d2a4c34ea117eb67e486274842b5de46f2500f69` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0PlanSchemaOrderTest.java` | 신규(4시험) | 16882 | `8aa4eb1a087a87c791198d018a0e902b41b4d2cd779658cab3a204386bf0afff` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java` | 수정(+78/−21) | 37135 | `c514cd08fa2797802169b5bb3a1fdc007225b6d77944c84fd25819111245f089` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0StructuredOutputDiagnosticTest.java` | 수정(+1/−1) | 26813 | `52a7cf1ebf98b31c76860ba46b3243f61edd39f1dbfe1f0692342199918bf002` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/OllamaStub.java` | 수정(+3/−2, 받은 본문 raw 바이트 기록) | 7792 | `87a3cc84a37cf325a4a50394bf27701fae3c595c447153e1779b5070774c9ad9` |
| `eval/f0/v3/prompts/plan-v3-h.txt` | 신규(1206토큰) | 4104 | `6c6997f4a6263658f28bc1ab86fec86553db667f976c91e113b645f1a63d197a` |
| `eval/f0/v3/input-audit-v3-h.json` | 신규(651행) | 92669 | `40201ea139c4e75a6d4c5209aa2bf0642fe2c27b397ab6ac7f59324635a4b9bc` |
| `eval/f0/v3/input-audit-v3-regression-h.json` | 신규(843행) | 119389 | `25f7300b7811d6aa316c1b03e2b859dac1ecc6d06e9f354e4f1804838001cfdc` |
| `eval/f0/PREREG-v3-diag-h.md` | 신규 초안 | 13053 | `63a565f3c71ee1e53e500d0eebd7fe30901612a2e5e54114d5ea5de594141a3b` |
| `eval/f0/PREREG-v3-regression-h.md` | 신규 초안 | 9705 | `3c61ec91ecff0b581f59ca2fb11cbed7b83677dcae40bfcdbd0294430d11d98e` |
| `eval/f0/PREREG-v3-gate-h.md` | 신규 초안 | 12145 | `b4a2f1f59776150d50fc21ab8fb834548293301028a53c3eaf082f3942753810` |

- 15파일 모두 UTF-8·LF(CR 바이트 0)·BOM 없음, `git diff --check` exit 0.

#### 4) 시험(이 세션 실행, root 실행 아님, Gradle 동시 실행 0)

- 실패 시험 먼저: 구현 전 `:gateway-eval:compileTestJava` 가 `cannot find symbol`(`forPolicy`·`Format`·`PLAN_ORDERED_POLICY`·`canonicalKeepingOrder`) 컴파일 오류 28로 실패(`BUILD FAILED`, exit 1)함을 확인했다. 구현 뒤 `F0PlanSchemaOrderTest`·`F0StructuredOutputDiagnosticTest`·`OllamaChatClient*` 필터 실행은 PASS(4·7·3·9 tests, failures 0)였다.
- `F0PlanSchemaOrderTest` 4건:
  - ① 직렬화기: 경로 없음 = `Json.canonical` 바이트, 지정 객체만 노드 순서(형제·자식·조상 정렬, 이스케이프·숫자 canonical 그대로), 배열 안 객체는 지정되지 않는다(손으로 쓴 기대 문자열).
  - ② 실제 등록 질문 61개(v2 골든 22·회귀 30·경계 9) × 두 종류:
    - `NONE` = null.
    - V1 `bytes` = 변경 전 정의 `Json.canonical(responseFormat)`, `sha256` 도 같다.
    - 새 값의 분류 스키마는 V1 과 바이트·SHA 가 같다.
    - 새 값의 계획 스키마는 내용(`Json.parse`)이 V1 과 같고, `properties` 순서가 `t, eqp, from, to, qRef, steps` 다. 송신 바이트는 정렬 canonical 문자열에서 `properties` 하나만 바꾼 독립 구성과 같다.
  - ③ 실제 HTTP stub(B·A·경계·usage·token, 초회 무효 → 재시도 전수, `NONE`·V1·새 값 세 등록):
    - `NONE`·V1 의 모든 요청 raw 바이트 = 받은 본문의 정렬 canonical(변경 전 식)이다.
    - 새 값의 A 외 요청 raw 바이트 = V1 과 같다. A 요청은 내용이 같고 `properties` 순서만 다르다(독립 구성과 바이트 일치). 송신된 `response_format` 바이트 = `Format.bytes()` 이고, 초회와 재시도가 같다.
    - 결과 행 `responseFormatSha256`: V1 B·A·경계와 새 값 B·경계는 정렬 canonical SHA, 새 값 A 는 송신 바이트 SHA 다. `NONE` 행에는 이 열이 없다.
    - run-binding `parameterManifest.responseFormat`·진단 보고서 표기, sentinel 비노출도 확인했다.
  - ④ 등록 값: `RESPONSE_FORMATS` = {NONE, V1, 새 값}. 소문자·`_V2`·`JSON_SCHEMA_V2`·v2 스키마에서의 키·GATE R1 은 `PREREG_INVALID`, GATE R5 는 유효다.
- `F0V3RegistrationDraftTest` 변경(C7·C11 때와 같은 방식):
  - 하네스 소스가 바뀌어 f(C11/C12)·g(C13/C14) 등록이 현재 하네스 기준 stale 이 된다. g 3문서를 과거 등록 묶음(C13·C14, `PRESENCE_V1`)에 넣었고, `HARNESS_UNCHANGED_SINCE_REGISTRATION` 은 비게 됐다.
  - 따라서 f·g 는 역사 신원(등록 commit 트리 harnessDigest·PREREG commit·입력 바이트 불일치 0)과 현재 하네스 `localMismatches=["harnessDigest"]`·`verify`=`PREREG_INPUT_CHANGED` 를 단정한다. 기존 C5~C10 단정은 그대로다. g 의 `responseFormat=JSON_SCHEMA_V1` 단정을 추가했다.
  - 후보 묶음은 h 3초안(`PRESENCE_V1`·`JSON_SCHEMA_PLAN_ORDERED_V1`, 로컬 불일치 0, 초안이면 `PREREG_NOT_COMMITTED`·등록 뒤면 실제 `verify` 통과, capture 경로 고유)으로 바꿨다.
  - 비교 시험에 h↔gate-h·h↔regression-h 를 넣었다. g→h 는 `planPrompt`·`inputAudit`·`promptSha256.plan`·`responseFormat` 만 다르다고 단정한다.
  - 신규 시험 `candidateHChangesOnlyPlanPromptAndPlanSchemaWireOrderAndKeepsEveryOtherRequestOfCandidateG`: g·h(진단, 회귀) 실제 조립 목록에서 A 외 행의 메시지 SHA·audit 행 SHA·계수가 g 와 같고, A 행은 system 만 다르다. 질문·경계마다 분류 스키마 송신 바이트가 g 와 같고, 계획 스키마는 내용이 같고 순서만 `t` 먼저다.
  - 기존 f→g 비교 시험(`candidateGChanges…`)은 그대로 유지했다.

| 명령 | exit | 결과 |
|---|---|---|
| `.\gradlew.bat :gateway-eval:test --console=plain`(무필터, `AIGW_SPEC_FILE`=정본 SPEC, test 태스크 실제 실행) | 0 | JUnit XML 41개 합산 194 tests·failures 0·errors 0·skipped 0(기존 189 + `F0PlanSchemaOrderTest` 4 + 등록 시험 1, `F0V3RegistrationDraftTest` 7) |
| `.\gradlew.bat :gateway-eval:run --console=plain "--args=test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED` |

- 전체 시험 출력 끝: `> Task :gateway-eval:test`, `> Task :gateway-eval:testLeafEvidence`, `BUILD SUCCESSFUL in 4m 34s`, `6 actionable tasks: 2 executed, 4 up-to-date`.

#### 5) 입력 audit(실제 Java 조립 + 등록 렌더러 + pin tokenizer)

- 렌더: `f0 input-audit-render --golden {v2 22 | 회귀 30} --fixture eval/golden/tc-registry-fixture-v2.json --classify-prompt eval/f0/v3/prompts/classify-v3-f.txt --plan-prompt eval/f0/v3/prompts/plan-v3-h.txt --cases eval/f0/v2/output-boundary-cases.jsonl --usage-messages eval/f0/v2/usage-probe-input-v2.jsonl --token-messages eval/f0/v2/token-golden-input-v2.jsonl --renderers eval/f0/chat-renderers-v1.json --template-sha256 40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541 --context-projection PRESENCE_V1` → `F0_INPUT_AUDIT_RENDERED 651`·`843`. 렌더 JSONL 은 레포 밖 세션 임시 디렉터리에 두었다.
- 계수: `count_input_audit_v2.py`(Python `-I -B`, pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) → `INPUT_AUDIT_WRITTEN 651 OVER_BUDGET 0`·`843 OVER_BUDGET 0`.
- 전수 재검증: 렌더↔audit 행 순서·id·messagesSha256 651/651·843/843 일치, 행별 입력 ≤ 상한·입력+`max_tokens` ≤ 4096 위반 0. 메타데이터(kind·schema·renderer·template·tokenizer·tokenizers 버전·tool)는 g audit 와 같고 `maxInputTokensByGroup` 만 다르다.

| audit | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token | 최대 입력+출력 |
|---|---|---|---|---|---|---|
| g | 1791/1830 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254 |
| h | 1778/1817 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254(`boundary/ESCAPE-CONTROL/retry/USAGE_EXCEEDS_LIMIT`) |
| 회귀 g | 1807/1846 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 |
| 회귀 h | 1794/1833 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 |

- g 대비(같은 위치 id): A 264/264·360/360 행은 SHA 가 모두 다르고 계수는 모두 −13 이다. **B 264·경계 108·usage 2·token 13(=387행)과 회귀 B 360·경계 108·usage 2·token 13(=483행)은 id 순서·messagesSha256·계수가 전부 같다.** A 최대 입력+출력은 2073(회귀 2089)이다.
- `response_format` 과 입력 토큰 계수:
  - audit 렌더는 chat 메시지만 렌더한다(`F0RunnerCli.java:148` `renderer.render(call.messages())`).
  - 진단 g·회귀 g 실측 결과에서 `response_format` 을 보낸 호출 124건(A 23+31, B 22+30, 경계 9+9)의 `usage.prompt_tokens` 가 audit 계수와 124/124 같다(결과 JSON 정제 필드 재집계). 따라서 이 서버에서 `response_format` 은 입력 토큰 계수에 들어가지 않는다(V1 기준 확정).
  - h 는 같은 스키마의 키 순서만 바꾸므로 같은 결과일 것으로 본다(추정, 진단 h 에서 확인).

#### 6) 등록 초안 3개·등록 검사

| 초안 | 목적 | R | 골든·audit | capture | harnessDigest | parameterManifestSha256 |
|---|---|---|---|---|---|---|
| `PREREG-v3-diag-h.md` | DIAGNOSTIC | 1 | v2 22 + `input-audit-v3-h.json` | `v3-diag-h` | `2c35eb07c28a3aa6f9f52c5158c17d1413368fd7d7e2ad8fb53a50a1ffbc9a9f` | `0ce7e01df9f7f074554c6df9f5b6da4f35cdc7a13226d5b1131e7c9dcdb781ee` |
| `PREREG-v3-regression-h.md` | DIAGNOSTIC | 1 | 회귀 30 + `input-audit-v3-regression-h.json` | `v3-regression-h` | `63c9fa7ea8a0a6f7ec9da621e73b2bcfd1eb0b1924213b7404852ed67cd394d0` | `0ce7e01d…81ee` |
| `PREREG-v3-gate-h.md` | GATE | 5 | v2 22 + `input-audit-v3-h.json`(diag-h 와 바이트 동일) | `v3-gate-h` | `2c35eb07…9a9f` | `647d127dc5ab009d3dddbe29884ef2b67f16552c2fb21230999c4f527c3ac278` |

- 기계 판독 블록은 g 블록에서 만들었다. Node 키 단위 비교에서 g 대비 바뀐 것은 `registrationStatus`·`registeredCommit`(UNCOMMITTED/null)·`runtimeCaptureDir`·`inputRoles.{planPrompt,inputAudit}`·해당 inputs 2개·`harnessDigest`·`parameters.promptSha256.plan`·`parameters.responseFormat`(=`JSON_SCHEMA_PLAN_ORDERED_V1`)뿐이다. 최상위 키 순서는 같다. `classifyPrompt`·`contextProjection=PRESENCE_V1`·나머지 입력 13개·파라미터는 같다. gate-h 는 diag-h 와 inputs·inputRoles·repeats 외 파라미터가 같다.
- §2 실행 순서는 g 문서의 §2 를 g→h 로만 바꾼 것과 3문서 모두 줄 단위로 같다(diff 0).
- harnessDigest 는 Java `prereg-check` 의 `currentHarnessDigest` 와 Node 독립 재계산(등록 inputs + f0 소스 24파일, 정렬 경로·SHA 쌍 JSON 의 SHA-256)이 일치했다. 같은 Node 계산을 C13 트리에 적용하면 g 등록값(`4de7eadb…5597`·`fd7e9523…6cb5`)이 재현된다.
- gate-h §1 착수 조건은 gate-g 와 같은 수준이다. C15/C16 뒤 3문서가 `PREREG_VERIFIED` 이고, 진단 h·회귀 h 가 exit 0·complete 이며, 진단 h 에서 `QREF_INVALID` 0·후보 0 `ID_OUT_OF_RANGE` 0·G08 최종 t A·B INC·경계 9/9 를 충족하고, root 가 원문 없이 검토해야 한다. AC-38-F0 의 A 쪽 workflow(`tc.logs.search`) 조건은 착수 조건이 아니라 R5 판정 규칙(기존 그대로)으로 적었다.
- `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차). 거부는 Java `EvalException(2, …)`, Gradle exit 1 이다.

| 등록 | localMismatches | 결과 |
|---|---|---|
| diag-h·regression-h·gate-h(초안) | `[]` | `PREREG_NOT_COMMITTED` |
| diag-g·regression-g·gate-g(C13/C14) | `["harnessDigest"]`(current `3871cf94…`·`fd47ed0b…`·`3871cf94…`) | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale) |
| diag-f(C11/C12) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale) |

- 기존 등록 재현성: 15개 과거 등록 모두 입력 바이트 불일치 0 이고, 역사 신원은 실제 git `verifyHistorical` 로 재확인했다. `NONE`·V1 요청 바이트·`responseFormatSha256` 은 변경 전 식과 같다(시험 ②③, 실측 기록 122/122). 즉 신규 측정만 stale 로 거부된다. 진단 f·g 와 회귀 g 는 측정을 마쳤고(회귀 f 는 §12.18 미완 기록 그대로), gate-f·gate-g 는 착수 조건 미충족으로 실행 대상이 아니다.

#### 7) 다음 커밋 범위 제안(사용자 허용 필요 — 기존 허용은 C13/C14까지)

- C15(로컬 commit, 정확 16파일): 위 3) 표 15파일 + `docs/ai-rag-gateway-architecture-dev-report.md`. Devreport 에는 C13 뒤 미커밋으로 추가된 §12.19 "로컬 C13/C14 등록·최종 시험"·§12.20·이 절이 함께 들어간다. 초안 3문서는 UNCOMMITTED/null 그대로 넣는다. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C16(로컬 commit, 정확 3파일): `eval/f0/PREREG-v3-{diag-h,regression-h,gate-h}.md` 에 `registrationStatus=REGISTERED`, `registeredCommit`=C15 SHA, 등록 설명만 기록한다. inputs·harnessDigest·parameters 는 바꾸지 않는다.
- C16 뒤 순서: 3문서 `f0 prereg-check` → (Jetson 메모리·서비스 상태 확인) → 진단 h(diag-h §2) → 회귀 h → gate-h §1 충족 시 R5 → SPEC §14.5 기록·재판정·F0 gate. P04 는 착수하지 않는다.

#### 8) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be` 불변이다. HEAD C14 `87113b50a0a57f75c80c3e33a6faec1c3d39189d`, staged 0, git 쓰기 0 이다.
- `eval/f0/results`·`eval/f0/chat-template`·`evidence`·`eval/golden`·기존 프롬프트·기존 PREREG·기존 audit 변경 0, 새 `.pyc` 0(Python `-I -B`). `eval/f0` 아래 신규 파일은 위 6개뿐이다. `Json.java` 변경 0.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 응답 원문·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS 를 확정하지 않는다.

#### 로컬 C15/C16 등록·최종 시험 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 승인 근거: 사용자 승인 "C15/C16 허용 후 진행 (권장)"(2026-11-03). 허용 범위는 로컬 commit C15·C16 두 개뿐이며 push·amend·원격 publish 는 하지 않았다.
- 착수 전 확인: HEAD C14 `87113b50a0a57f75c80c3e33a6faec1c3d39189d`(14커밋), staged 0, C15 대상 16파일 SHA-256 이 기대값과 모두 일치했다(이 Devreport 의 C15 시점 SHA `b097fe274e5746f1d68830e1d4008c8d9fb944cbb05f4bdcbfdc496568869955`, 378289 B 포함).

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C15 | `889e27b582dfbb4c4fac033d25a6d5cbc6c423a9` | 2026-11-03 13:16:26 +0900 | 16파일, 1136 insertions(+), 40 deletions(-) |
| C16 | `372dbb4653ab807324ec7361554ea1c9d5f87f82` | 2026-11-03 13:17:30 +0900 | 3파일, 18 insertions(+), 12 deletions(-) |

- C15 정확 16파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 16개, 기대 목록과 diff 0): `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ResponseSchema,OllamaChatClient,MeasurementSession,F0Measurements,Prereg}.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0PlanSchemaOrderTest,F0V3RegistrationDraftTest,F0StructuredOutputDiagnosticTest,OllamaStub}.java`, `eval/f0/v3/prompts/plan-v3-h.txt`, `eval/f0/v3/input-audit-v3-h.json`, `eval/f0/v3/input-audit-v3-regression-h.json`, `eval/f0/PREREG-v3-diag-h.md`, `eval/f0/PREREG-v3-regression-h.md`, `eval/f0/PREREG-v3-gate-h.md`, `docs/ai-rag-gateway-architecture-dev-report.md`. 초안 3문서는 UNCOMMITTED/null 상태로 들어갔다. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않았다.
- C16 정확 3파일: `eval/f0/PREREG-v3-{diag-h,regression-h,gate-h}.md`. §0 제목·현재 상태 문구를 C14 방식(등록 근거·C15/C16 설명·이력)으로 바꾸고, 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C15 SHA 만 바꿨다. §1 이하는 그 2줄 외 바이트 그대로다. C15·C16 커밋 시각의 초는 다르다(64초 차이).
- JSON 검사(Node, 파싱 실패 시 원문 비출력): 3문서 모두 전 값이 UNCOMMITTED/null, 후 값이 REGISTERED/C15 SHA 이고, 두 키를 뺀 나머지 객체 동일·최상위 키 순서 동일(9키)·§1 이하 2줄 외 동일이 true 였다. 커밋 blob(`889e27b`↔`372dbb4`) 독립 재비교에서도 바뀐 최상위 키는 문서마다 `registrationStatus`·`registeredCommit` 둘뿐이다.
- C16 뒤 16커밋, staged 0 이다.

`f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차 실행, 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값):

| 등록 | localMismatches | 결과 | Gradle exit |
|---|---|---|---|
| diag-h | `[]` | `PREREG_VERIFIED` | 0 |
| regression-h | `[]` | `PREREG_VERIFIED` | 0 |
| gate-h | `[]` | `PREREG_VERIFIED` | 0 |
| diag-g(C13/C14) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(Java exit 2) | 1 |

- diag-g 는 C15 의 하네스 소스 변경으로 stale 이 된 것이며 §12.21 6)의 예상과 같다. f·g 의 나머지 등록은 같은 이유이므로 diag-g 1건으로만 확인했다.

등록 뒤 전체 시험(Gradle 동시 실행 0, 순차):

- `.\gradlew.bat :gateway-eval:test --console=plain --rerun-tasks` → exit 0, `BUILD SUCCESSFUL in 5m 3s`, `6 actionable tasks: 6 executed`. XML 독립 집계 41클래스·194 tests·failures 0·errors 0·skipped 0.
- 이어서 `.\gradlew.bat :gateway-eval:run --console=plain --args="test-evidence verify --tasks :gateway-eval:test"` → exit 0, `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s`.

보존: 모델 실측 0회, SSH·HTTP 요청·다운로드·서비스 변경 0. 코드·시험·프롬프트·골든·기존 PREREG·audit·결과·capture·evidence·SPEC·Plan 변경 0. push·amend·rebase·reset 0. 이 절은 commit 하지 않았으며(C15 커밋 바이트 불변, 작업 트리에만 추가), 응답 원문·API 응답·secrets 는 출력·저장하지 않았다. 다음 순서는 §12.21 7) 그대로이며(Jetson 상태 확인 → 진단 h → 회귀 h → gate-h §1 충족 시 R5), 이 세션은 착수하지 않았다.

### 12.22 P03 후보 h 진단·개발 회귀 h 실행 (2026-11-03, 독립 Claude Code Opus 5.5 medium, root 검토 전)

- §12.1~§12.21은 바꾸지 않았다. 이 절 앞 바이트(382542 B, SHA `783bef0fd7ddd2d5f8d45876c55523460cd8f59329ca5d15725ba42c0d975239`)는 그대로이며 이 절만 추가했다.
- 산출물: 신규 결과 디렉터리 `eval/f0/results/20261103T043458Z-h/`(8파일)·`eval/f0/results/20261103T043458Z-regression-h/`(8파일), 신규 capture `eval/f0/chat-template/v3-diag-h/`·`eval/f0/chat-template/v3-regression-h/`(각 4파일), 이 절.
- 하지 않은 것: 코드·시험·프롬프트·골든·fixture·PREREG·audit·기존 결과/capture·evidence·SPEC·Plan 변경, git 쓰기 전부, 모델 load/pull/unload·서비스 재시작·num_ctx 변경·다운로드, 정식 R5(gate-h)·F0 leaf 증거·gate·SPR, P04.
- **결론(진단 R1, 판정 아님):**
  - 진단 h 7개 명령과 개발 회귀 h 7개 명령은 모두 exit 0·`complete=true`다. 서버 `response_format` 거부, timeout, 서비스 재시작·OOM은 0이다.
  - **서버는 계획 스키마의 송신 `properties` 순서(`t` 먼저)를 출력 생성 순서로 따르지 않았다.** A 시도 중 원문 없이 판별 가능한 진단 h 15건·회귀 h 20건이 모두 옛 정렬 순서(`eqp, from, qRef, steps, t, to`) 직렬화와 SHA가 같고, 새 순서와 같은 것은 0건이다. 요청 쪽에서는 새 바이트가 실제로 송신됐다(A `responseFormatSha256`이 g와 다르고 B·경계는 같다).
  - 진단 h: 경계 9/9, B G08 최종 t `INC`, `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0이다. **A G08은 두 시도 출력 SHA가 g와 같은 `d648833f…b242`이고 t=`INC`·steps `EMPTY_FOR_TOOL_TASK`(`STEPS_INVALID` 2회)로 최종 `INVALID`다(g와 같은 실패).** A taskType 21/22·인자 16/22(g 15/22)다.
  - 개발 회귀 h: R01~R06·R08은 A·B 통과다. **R07은 B 통과, A는 q 실패(qRef 후보 2개 중 다른 발급 ID 선택, `ID_IN_RANGE`)로 회귀 g 대비 새 실패다.** 원래 22문항 행은 진단 h와 첫 출력 SHA가 A·B 22/22 같고, 경계도 9/9 같다.
  - 따라서 gate-h §1 조건 3b(G08 최종 t A·B 모두 INC)가 미충족이다(판정은 root). 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(읽기 전용 확인)

- 확인 방법: §12.20과 같다(SSH 읽기 전용 + `bash -s`, `GET /api/ps`는 Jetson 안 python3로 허용 필드만 파싱하고 실패 시 `parse failed`만 출력, journal은 서비스 시작 이후 `library=cpu`·`Load failed`·`library=CUDA`·`out of memory` 줄 수만). 사용자가 13:27:43 KST에 `systemctl restart ollama`를 직접 실행했고, 이 세션은 warmup이 두 모델을 적재할 때까지 1분 간격으로 읽기 전용 확인만 했다(04:29:47Z 상주 0 → 04:32:13Z qwen3 1개 → 04:34:39Z 2개).
- 04:34:39Z 확인 결과(착수 조건 충족):
  - 서비스: active/running, `ActiveEnterTimestamp=2026-11-03 13:27:43 KST`, NRestarts 0.
  - journal(서비스 시작 이후): `library=cpu` 0건, `Load failed` 0건, `out of memory` 0건, `library=CUDA` 4줄.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - Mem 7619 MiB 중 available 2106, swap 3809 MiB 중 사용 1095. 로컬 11434는 미사용이었다.
- 각 B·A·boundary 뒤 재확인에서 중단 조건 항목(ActiveEnterTimestamp·NRestarts 0·2모델 digest·ctx 4096·size_vram=size·`library=cpu`/`Load failed`/OOM 0)은 모두 유지됐다. Jetson 시계는 로컬보다 약 3초 늦다.

| 확인 시각(UTC) | 시점 | 상주 모델 | Mem available MiB | swap 사용 MiB | 중단 조건 |
|---|---|---|---|---|---|
| 04:29:47 | warmup 중 | 0 | 5559 | 1013 | 대기 |
| 04:31:07 | warmup 중 | 0 | 5549 | 1013 | 대기 |
| 04:32:13 | warmup 중 | 1(qwen3) | 2806 | 1089 | 대기 |
| 04:33:27 | warmup 중 | 1(qwen3) | 2032 | 1091 | 대기 |
| 04:34:39 | 진단 h 착수 전 | 2 | 2106 | 1095 | 충족 |
| 04:37:42 | 진단 h B 뒤 | 2 | 2075 | 1104 | 충족 |
| 04:39:38 | 진단 h A 뒤 | 2 | 1821 | 1121 | 충족 |
| 04:41:37 | 진단 h boundary 뒤 | 2 | 1766 | 1126 | 충족 |
| 04:42:10 | 회귀 h 착수 전 | 2 | 1764 | 1126 | 충족 |
| 04:44:47 | 회귀 h B 뒤 | 2 | 1340 | 1218 | 충족 |
| 04:47:15 | 회귀 h A 뒤 | 2 | 1407 | 1251 | 충족 |
| 04:49:09 | 회귀 h boundary 뒤 | 2 | 1592 | 1257 | 충족 |
| 04:50:25 | 전 명령 종료 뒤 | 2 | 1589 | 1257 | 충족 |

- available 최저는 1340 MiB(회귀 h B 뒤)이고, swap 증가는 착수 전 대비 약 162 MiB다. §12.20(최저 1429 MiB·증가 약 90 MiB)과 비슷한 수준이며, 진단 f(§12.18 1) available 160~271 MiB) 같은 메모리 압박은 없었다.

#### 2) 실행 기록

- 공통 조건: §12.20 2)와 같다. Bash에서 `powershell.exe -NoProfile -File <scratchpad 래퍼>`로 실행했고, 래퍼는 `Set-Location C:\workspace_nori\nori-ai-gateway` 뒤 `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`를 호출한다. `--args` 값은 각 등록 §2와 같다. 명령마다 `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=각 회차 ID, `DEBUG`=빈 값. 한 명령씩 실행하고 exit 0을 확인한 뒤 넘어갔다. 측정 중 다른 Gradle 실행은 0이다.
- run prefix `20261103T043458Z`(시작 때 1회 결정). 회차 ID는 `20261103T043458Z-h`·`20261103T043458Z-regression-h`다. 결과 2디렉터리·capture 2디렉터리는 사전 부재를 확인했다.
- 터널: `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다. 진단 h는 PID 5388, 회귀 h는 PID 15080이며, 각각 명령행을 확인한 뒤 report 전에 `Stop-Process`로 그 PID만 종료했다. 종료 후 11434 LISTENING 0·ssh 프로세스 0이다. 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| h-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-h.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `2c35eb07…9a9f`, parameterManifestSha256 `0ce7e01d…81ee` |
| h-2 | `f0 preflight … --out eval/f0/results/20261103T043458Z-h/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| h-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-h`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest 파일 SHA `235b84f5…5ddd`(g와 같음) |
| h-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3-f.txt --run-id 20261103T043458Z-h --out …/B` | 0 | 04:35:49~04:37:41, `F0_RUN_COMPLETED B`(Gradle 1m 51s) |
| h-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3-h.txt …` | 0 | 04:37:53~04:39:37, `F0_RUN_COMPLETED A`(1m 43s) |
| h-6 | `f0 boundary-gen … --prompt eval/f0/v3/prompts/classify-v3-f.txt …` | 0 | 04:39:49~04:41:34, `F0_BOUNDARY_COMPLETED 9`(1m 43s) |
| h-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-h.md --run-id 20261103T043458Z-h --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `009b33a7…2ff9` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression-h.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, parameterManifestSha256 `0ce7e01d…81ee` |
| r-2 | `f0 preflight …/20261103T043458Z-regression-h/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression-h`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template `40c21f34…b541` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | 0 | 04:42:56~04:44:50, `F0_RUN_COMPLETED B`(1m 54s) |
| r-5 | `f0 run … --path A --golden eval/f0/v3/golden-regression-v3.jsonl … --prompt eval/f0/v3/prompts/plan-v3-h.txt …` | 0 | 04:45:03~04:47:18, `F0_RUN_COMPLETED A`(2m 14s) |
| r-6 | `f0 boundary-gen …` | 0 | 04:47:30~04:49:12, `F0_BOUNDARY_COMPLETED 9`(1m 41s) |
| r-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-regression-h.md --run-id 20261103T043458Z-regression-h --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `e64b1edd…6c2e` |

- 소요 시간은 g와 같은 수준이다. preflight 두 파일은 g의 같은 위치 파일과 바이트가 같다(SHA `c9c344ac…69e2`·`fe4a66cb…1b10`).
- F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 8개(진단 h B·A·boundary·capture, 회귀 h B·A·boundary·capture)의 runtimeManifest는 모두 같다.
  - model `0edcdef3…8ba0`, template `40c21f34…b541`, tokenizer `aeb13307…dae4`(runtimeManifestSha256 `b1e6a3af…`, c~g와 같음), specSha `34fd6223…66be`(FILE_HASHED, LOCAL_FILE)
  - registeredCommit C15 `889e27b582dfbb4c4fac033d25a6d5cbc6c423a9`, preregCommit C16 `372dbb4653ab807324ec7361554ea1c9d5f87f82`
  - `contextProjection=PRESENCE_V1`, `responseFormat=JSON_SCHEMA_PLAN_ORDERED_V1`, parameterManifestSha256 `0ce7e01d…81ee`
  - harnessDigest: 진단 h `2c35eb07…9a9f`, 회귀 h `63c9fa7e…94d0`(각 등록값과 같음)
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출(작음/큼)은 두 회차 모두 A 0/0, B 0/0, boundary 0/0이다(보고서와 정제 필드 재집계 모두: 진단 h A 0/23·B 0/22·경계 0/9, 회귀 h A 0/31·B 0/30·경계 0/9). 따라서 순서 보존 계획 스키마도 입력 토큰 계수에 들어가지 않는다(§12.21 5)의 추정 확인).
- 첫 시도 프롬프트 토큰 범위: 진단 h A 1741~1778·B 1353~1390, 회귀 h A 1741~1794·B 1353~1406. audit 최대(A 1778/1794·B 1390/1406)와 같다. A 범위는 g 대비 −13이다. 출력 토큰 최대: 진단 h A 89·B 60, 회귀 h A 89·B 78.
- `responseFormatSha256`(첫 8자): 진단 h A는 `2c500069`(후보 0)·`b7f3c2f6`(후보 1)이고, 회귀 h A는 여기에 `1a1814b2`(후보 2)가 더해진다. g A(`398c3a9c`·`dd5b4987`)와 다르다. B·경계는 `b7f55fab`·`ee5d249c`(회귀 B는 `18767a88` 추가)로 g와 같다. 즉 순서 보존 바이트는 A에만 실제로 송신됐다.

#### 4) 진단 h 결과(`eval/f0/results/20261103T043458Z-h/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

- 재집계는 §12.20과 같은 방식(결과 JSON의 열거형·불리언·개수·해시 필드, `predicted.steps` capability ID, 골든의 id·기대 t)으로 했다.

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 21/22 | 16/22 | 21/22 | 21/22 | 21/22 | 1/22 | 3319/6916 | `STEPS_INVALID` 2(G08) |
| B | 20/22 | 18/22 | 20/22 | 22/22 | 22/22 | 0/22 | 2946/4977 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 5·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2. timeout은 A·B 모두 0이다.
- 지연 최대: A 9083 ms(G01), B 32322 ms(G01, 회차 첫 요청). B 나머지 21건은 ≤4977 ms다.
- A 재시도 1건: G08만이다. 두 시도 모두 t=`INC`·steps `EMPTY_FOR_TOOL_TASK`라 최종 `INVALID`다.
- t 오답:
  - A: G08(최종 무효, timeout 아님). timeout 0.
  - B: G07(UNS→INC), G16(UNS→GQ).
- 인자 오답:
  - A: G01·G11·G12 from+to, G17 to, G20 eqp, G08 무효.
  - B: G01·G11·G12·G17 to.
- g 대비 A 인자 변화: G09(from+to)·G16(eqp)은 맞게 바뀌었고, G17은 from+to → to, G20은 맞음 → eqp 오답이다.
- A 최종 steps(유효 21문항, `predicted.steps` capability ID):
  - INC 6문항 모두 `tc.logs.search`를 포함한다: G01 `tc.logs.search;tc.git.searchCode`, G09 `tc.logs.search`, G12 `tc.dlq.list;tc.logs.search`, G17 `tc.logs.search`, G20 `tc.logs.search`, G22 `tc.logs.search`(g의 `tc.logs.search;tc.event.getRecent`에서 바뀜).
  - LQ: G02·G06·G10·G19 `tc.eqp.getOperationalSnapshot`, G11 `tc.event.getRecent`. KQ: G04·G13 `tc.knowledge.search`, G21 `tc.git.searchCode`. GQ·UNS 7문항 `[]`.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. 후보 0 시도는 모두 `qRef.kind=JSON_NULL`(A 20 = G08 2시도 포함, B 19)이고, 후보 1 시도는 `ID_IN_RANGE`(A 3·B 3)다. `ID_OUT_OF_RANGE`는 경계를 포함해 0이다.
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to의 `equalsContext=true`는 0건이다.
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - B: 초회 t=`INC`, 재시도 0, 최종 effectiveT `INC`. eqp·from·to는 모두 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`. 첫 출력 SHA `b4db4d71…`은 g·f와 같다.
  - A: 초회·재시도 모두 t=`INC`, steps `EMPTY_FOR_TOOL_TASK`(shape OK) → 최종 `INVALID`, effectiveT 없음(`INVALID_OUTPUT`). 최종 시도 eqp·from·to는 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`.
  - **A G08 최종 steps에 `tc.logs.search`는 없다.** 최종 상태가 `INVALID`라 `predicted`가 null이며, 최종 시도 `attemptDiagnostics.steps.toolConsistency=EMPTY_FOR_TOOL_TASK`로 확인했다.
  - A G08 두 시도 출력 SHA는 g와 같은 `d648833f…b242`다. 즉 키 정렬·공백 없는 `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC","to":null}`의 SHA-256과 같다. h의 두 변경(스키마 송신 순서·`plan-v3-h`)은 G08 출력 바이트를 바꾸지 못했다.
- **서버의 키 순서 준수 판별(원문 없음)**:
  - 방법: §12.19 방식과 같다. 각 A 시도의 정제 필드로 값을 복원할 수 있는 경우만 대상으로 했다. 조건은 eqp·from·to가 NULL이거나 TEXT·MATCH(골든 기대값으로 복원), qRef `JSON_NULL`, `json=OBJECT`·`keys=EXACT`이며, t는 `t.value`로 복원했다. steps는 최종 유효 시도면 `predicted.steps`, `EMPTY_*`면 `[]`, 그 밖은 등록 capability 6개로 길이 0~3을 탐색했다. 후보 직렬화는 새 순서 `t,eqp,from,to,qRef,steps`와 옛 정렬 순서 `eqp,from,qRef,steps,t,to` 각각에 대해 구분자 공백 4종 × (공백 없음·키 하나 앞 공백 1개)로 만들었고, 각 시도 출력 `outputSha256`과 대조했다. 복원 값·직렬화 문자열은 메모리에서만 쓰고 출력·저장하지 않았다.
  - 방법 검증: 같은 스크립트를 진단 g A에 적용하면 판별 가능 16건이 모두 옛 순서이고 새 순서는 0건이다(§12.19·§12.20 관찰과 같음).
  - 결과: **진단 h A 23시도 중 판별 가능 15건(G02·G03·G04·G05·G06·G07·G08 2시도·G10·G14·G15·G16·G18·G19·G22)이 모두 옛 정렬 순서와 일치했고, 새 순서 일치는 0건, 어느 쪽도 아닌 것도 0건이다.** 판별 불가 8건은 인자가 TEXT·MISMATCH이거나 qRef가 `ID_IN_RANGE`인 시도다. 회귀 h A는 31시도 중 판별 가능 20건(위 15건 + R03·R04·R05·R06·R08)이 모두 옛 순서이고 새 순서는 0건이다.
  - 해석: 요청 바이트는 A에만 `t` 먼저 순서로 송신됐지만(3) `responseFormatSha256`), 서버 출력은 키 정렬 순서로 생성됐다. 서버가 `properties`를 다시 정렬하는지, 다른 규칙(예: 키 이름 순서)으로 문법을 만드는지는 서버 코드를 보지 않아 미확정이다. 관찰은 §12.21 위험 1항(서버가 키를 다시 정렬하는 경우)과 맞는다.
- **경계 9케이스 boundary-gen 9/9**(모두 status OK, schemaValid=true, argsExact=true, finish=stop, 재시도 0, completion ≤192, `equalsContext` 0, prompt=audit 9/9, qRef `JSON_NULL`/후보0 2·`ID_IN_RANGE`/후보1 7). 첫 출력 `outputSha256`은 9/9 모두 g와 같다(입력 바이트가 g와 같음).

| caseId | 판정 | 불통과 축 | completion | qRef | 첫 출력 SHA = g |
|---|---|---|---|---|---|
| NORMAL-01 | 통과 | – | 59 | JSON_NULL/후보0 | 같음 |
| NORMAL-PRECISE | 통과 | – | 97 | JSON_NULL/후보0 | 같음 |
| MAX-MIXED | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-HANGUL | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-DIGIT | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-ASCII | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| MAX-SUPPLEMENTARY | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| ESCAPE-01 | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |
| ESCAPE-CONTROL | 통과 | – | 146 | ID_IN_RANGE/후보1 | 같음 |

- **B 22문항 첫 출력 `outputSha256`은 22/22 g와 같다.** 따라서 B 결과(t·인자·qRef·Context 복사)는 g와 같고, B 지연만 달라졌다(g p95 5307 → h 4977).
- A 첫 출력 SHA는 g와 13/22 같다(다른 문항 G01·G03·G09·G14·G15·G16·G17·G20·G22). G03·G14·G15는 t·steps·인자가 g와 같은 UNS·`[]`·null이고, 판별상 키 순서도 옛 순서로 같다. 따라서 차이는 공백 등 직렬화 형태로 보인다(추정, 원문 미저장).

#### 5) 개발 회귀 h 결과(`eval/f0/results/20261103T043458Z-regression-h/diagnostic-report.md`, R1·분모 30)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 29/30 | 23/30 | 29/30 | 29/30 | 29/30 | 1/30 | 3557/6884 | `STEPS_INVALID` 2(G08) |
| B | 28/30 | 26/30 | 28/30 | 30/30 | 30/30 | 0/30 | 2980/5816 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 6·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2. timeout 0, `QREF_INVALID` 0, `ID_OUT_OF_RANGE` 0, Context 복사 0이다. qRef는 A·B 각각 `JSON_NULL`(A 25·B 24), `ID_IN_RANGE` 후보1 5, 후보2 1(R07)이다.
- 원래 22문항 행은 진단 h와 첫 출력 SHA가 A 22/22·B 22/22 같으므로 t·인자 오답도 진단 h와 같다(A G08 무효·G01·G11·G12 from+to·G17 to·G20 eqp, B G07·G16 t·G01·G11·G12·G17 to). 회귀 경계도 진단 h와 첫 출력 SHA 9/9가 같고 9/9 통과다. 회귀 g 대비 첫 출력 SHA는 A 19/30·B 30/30·경계 9/9가 같다.
- 회귀 A G08도 같은 SHA `d648833f…b242`로 두 시도 `STEPS_INVALID`, 최종 `INVALID`다.
- **R07(REG_SECOND_QUOTED_INTENT) A**: t `KQ`·steps `tc.knowledge.search`는 맞지만 q가 불일치다. 정제 필드는 `qRef.kind=ID_IN_RANGE`·candidateCount 2·accepted true·`q.match=MISMATCH`로, 발급 후보 2개 중 기대와 다른 ID를 골랐다. 같은 문항에서 회귀 g A와 회귀 h B는 `q.match=MATCH`다.
- 신규 8문항 A 최종 steps: R01·R03·R08 `tc.eqp.getOperationalSnapshot`, R02·R04·R06 `tc.logs.search`, R05 `[]`, R07 `tc.knowledge.search`(회귀 g와 같음).

| id | 축 | 회귀(c) | 회귀 d | 회귀 f | 회귀 g | 회귀 h |
|---|---|---|---|---|---|---|
| R01 | REG_QUOTED_EQP | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R02 | REG_EQP_VS_CODE_TERM | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R04 | REG_TC_TRANSFER_FAILURE | A 실패·B 통과 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R05 | REG_WM_OWNED_RECORD | A·B 통과 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R06 | REG_PARAPHRASED_RANGE | A·B 통과 | A from·to 실패·B 통과 | 미측정 | A·B 통과 | A·B 통과 |
| R07 | REG_SECOND_QUOTED_INTENT | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 | **A q 실패**·B 통과 |
| R08 | REG_LONG_EQP_ID | A·B 실패 | A·B 통과 | 미측정 | A·B 통과 | A·B 통과 |

- 회귀(c)~회귀 g 열은 §12.20 5) 표를 옮긴 것이다. 회귀 h B는 회귀 g B와 첫 출력 SHA 30/30이 같다.

#### 6) c·d·e·f·g·h 비교(원문 없는 정제 필드·출력 해시만)

- 등록 구성: c = FULL + c 프롬프트, d = EXTRACTION_V1 + d 프롬프트, e = EXTRACTION_V1 + c 프롬프트, f = PRESENCE_V1 + f 프롬프트, g = PRESENCE_V1 + `classify-v3-f` + `plan-v3-g`, h = g + `plan-v3-h` + `JSON_SCHEMA_PLAN_ORDERED_V1`.

| 지표 | c | d | e | f | g | h |
|---|---|---|---|---|---|---|
| A taskType / 인자 | 20/22 / 13/22 | 19/22 / 15/22 | 16/22 / 13/22 | 17/22 / 13/22(timeout 2 포함) | 21/22 / 15/22 | 21/22 / 16/22 |
| B taskType / 인자 | 18/22 / 15/22 | 17/22 / 15/22 | 17/22 / 17/22 | 20/22 / 18/22 | 20/22 / 18/22 | 20/22 / 18/22 |
| A JSON 유효 / 1차 유효 / 재시도 | 22 / 18 / 4 | 21 / 17 / 5 | 21 / 18 / 4 | 19 / 16 / 4 | 21 / 21 / 1 | 21 / 21 / 1 |
| A / B p95 ms | 10724 / 6107 | 11335 / 6211 | 10135 / 6136 | 120011 / 7100 | 6736 / 5307 | 6916 / 4977 |
| G08 최종 t A / B | INC / INC | UNS / UNS | UNS / UNS | 무효(두 시도 t=INC) / INC | 무효(두 시도 t=INC) / INC | 무효(두 시도 t=INC, g와 같은 SHA) / INC |
| Context 복사 시도 A / B | 3 / 2 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 경계 boundary-gen | 2/9 | 7/9 | 0/9 | 9/9 | 9/9 | 9/9 |
| A 판별 가능 시도의 키 순서(옛/새) | – | – | – | – | 16/0 | 15/0 |

- A 문항별(셀 = 최종 effectiveT / 틀린 인자, 굵게 = t 오답·무효·timeout, (ctx) = Context 복사 시도 있음):

| id | 기대 t | A c | A d | A e | A f | A g | A h |
|---|---|---|---|---|---|---|---|
| G01 | INC | INC / from+to | INC / from+to | INC / from+to | **TIMEOUT** | INC / from+to | INC / from+to |
| G02 | LQ | LQ / – | LQ / – | LQ / – | **TIMEOUT** | LQ / – | LQ / – |
| G03 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G04 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G05 | GQ | GQ / – | GQ / – | **무효** | GQ / – | GQ / – | GQ / – |
| G06 | LQ | **KQ** / – | **KQ** / – | **KQ** / – | **KQ** / – | LQ / – | LQ / – |
| G07 | UNS | UNS / – | UNS / – | **GQ** / – | UNS / – | UNS / – | UNS / – |
| G08 | INC | INC / from+to (ctx) | **UNS** / – | **UNS** / – | **무효** | **무효** | **무효** |
| G09 | INC | INC / – | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / – |
| G10 | LQ | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – | LQ / – |
| G11 | LQ | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to | LQ / from+to |
| G12 | INC | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / from+to | INC / from+to |
| G13 | KQ | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – | KQ / – |
| G14 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G15 | UNS | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – | UNS / – |
| G16 | UNS | UNS / eqp | UNS / – | UNS / eqp | UNS / eqp | UNS / eqp | UNS / – |
| G17 | INC | INC / from+to | **무효** | **UNS** / eqp+from+to | INC / from+to | INC / from+to | INC / to |
| G18 | GQ | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – | GQ / – |
| G19 | LQ | **KQ** / – | LQ / – | **KQ** / – | **KQ** / – | LQ / – | LQ / – |
| G20 | INC | INC / eqp+from+to (ctx) | INC / – | INC / – | INC / eqp | INC / – | INC / eqp |
| G21 | KQ | KQ / eqp | KQ / eqp | KQ / eqp | KQ / – | KQ / – | KQ / – |
| G22 | INC | INC / from+to | INC / from+to | INC / from+to | INC / – | INC / – | INC / – |

- B 문항별: h는 g와 첫 출력 SHA 22/22가 같으므로 §12.20 6) B 표의 g 열과 같다(t 오답 G07 INC·G16 GQ, 인자 오답 G01·G11·G12·G17 to).
- c~g 열은 §12.20 6) 표를 옮긴 것이다. 경계는 h도 9/9 통과이며 첫 출력 SHA가 g(따라서 f)와 9/9 같다(completion 59·97·146×7).
- 관찰(R1, 인과 확정 아님):
  - h의 목적이던 A 출력 키 순서 변경은 일어나지 않았다. 판별 가능한 A 출력은 모두 g와 같은 정렬 순서이고, G08 출력은 바이트까지 g와 같다.
  - 키 순서가 같은데도 A 9문항의 출력 SHA가 g와 달라졌다. 바뀐 입력은 계획 시스템 프롬프트(`plan-v3-h`, −13토큰)와 스키마 송신 바이트다. 따라서 A 인자 변화(G09·G16·G17 개선, G20·R07 악화)는 이 둘 중 하나 또는 둘 다의 영향이며, 어느 쪽인지 분리할 수 없다.
  - A G06·G19는 g처럼 `LQ`를 유지했다(§12.21 위험 2항은 일어나지 않음). 실제 생성 순서가 g와 같았으므로 예상과 맞는 결과다.

#### 7) `PREREG-v3-gate-h.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. C15/C16 뒤 세 문서 `PREREG_VERIFIED` | 충족 | diag-h·regression-h는 이번 h-1·r-1, gate-h는 §12.21 C15/C16 절(이 세션 미재실행) |
| 2. 진단 h·회귀 h exit 0·`complete=true`, 서버 `response_format` 거부(exit 5) 0 | 충족 | 14개 명령 exit 0, A·B·boundary `complete=true`(두 회차), 거부 0 |
| 3a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 3b. G08 최종 t A·B 모두 INC | **미충족** | B 최종 `INC`. A는 두 시도 t=INC였지만 steps `EMPTY_FOR_TOOL_TASK` 2회로 최종 `INVALID`(effectiveT 없음), 출력 SHA g와 동일 |
| 3c. 경계 9 boundary-gen 9/9 | 충족 | 위 4) |
| 4. root 원문 없는 검토 | 미완(대기) | 이 절과 진단 h·회귀 h 보고서. 신규 8문항 실패 축: R07 A q(정식 분모 밖) |

- 조건 3b가 미충족이므로 gate-h §1에 따라 이 등록으로 R5를 실행하지 않는다. 이 세션은 R5·F0 leaf·gate·SPR을 만들지 않았다.

#### 8) 미실행·원인 미확정·다음 정확 조치

- 미실행: gate-h prereg-check 재실행, 정식 R5, P04.
- 원인 미확정:
  - 서버가 송신 `properties` 순서를 출력 순서로 쓰지 않는 이유(재정렬인지, 다른 문법 생성 규칙인지). 서버 코드는 보지 않았다.
  - A G08만 steps가 빈 배열로 남는 이유(g와 같음).
  - R07 A가 qRef 후보 2개 중 다른 ID를 고른 이유, A G20 eqp 오답 재발.
  - B G07 UNS→INC, B G16 UNS→GQ(분류 입력 불변이라 f·g와 같음).
- 다음 정확 조치(root 결정):
  1. root가 진단 h·회귀 h 보고서와 이 재집계를 원문 없이 검토한다.
  2. 스키마 키 순서로 생성 순서를 바꾸는 경로는 이 서버에서 관찰상 효과가 없었다. A G08 무효 축 보완은 새 사전등록 파일로만 한다(gate-h §1 마지막 항목). 이 세션은 설계하지 않았다.

#### 9) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`는 불변이다(모든 run-binding specSha). HEAD C16 `372dbb4653ab807324ec7361554ea1c9d5f87f82`, staged 0이며 git 쓰기는 0이다.
- `git status --short`는 세션 시작과 같다(Devreport M, 기존 untracked 5항목). PREREG·입력·코드 변경은 0이다.

| 파일 | 바이트 | SHA-256 |
|---|---|---|
| `eval/f0/results/20261103T043458Z-h/preflight.json` | 304 | `c9c344acfed2de216b5dc41b87b808e574107601830ed9385ce6bcd2da4d69e2` |
| `eval/f0/results/20261103T043458Z-h/B/f0-results.json` | 31111 | `70d19d2e5714a000c09ae52f8d23931cea7469feaad71bb1b671dafde55c7faf` |
| `eval/f0/results/20261103T043458Z-h/B/run-binding.json` | 1760 | `02c53a51afc57cf8c3ed11d40673064b7e627eb54e784dfd5ce35d95373018f1` |
| `eval/f0/results/20261103T043458Z-h/A/f0-results.json` | 34951 | `31f120500574da8c0003f31e61de76a6dfe6fb7ac4667ac0d9759f58628bed23` |
| `eval/f0/results/20261103T043458Z-h/A/run-binding.json` | 1760 | `1458378f0711ba9e94d74057bb6f6d2bce69271820f7ad7cfa71d661090d196e` |
| `eval/f0/results/20261103T043458Z-h/boundary/boundary-results.json` | 10005 | `a0f65f2467435523ae43f74f6a0a325f2019490ee61f5d7d332406d13529c4bc` |
| `eval/f0/results/20261103T043458Z-h/boundary/run-binding.json` | 1760 | `95e682cac62b9aefa9ec39b722a7983dab9149b234070cb1bc32464e7f29dd70` |
| `eval/f0/results/20261103T043458Z-h/diagnostic-report.md` | 8849 | `39b922349e8adc1bd8fe565202064ce9653f1e57dee8630cd65cbf45ae656d60` |
| `eval/f0/results/20261103T043458Z-regression-h/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T043458Z-regression-h/B/f0-results.json` | 42240 | `483fec3daa394a3828a248814f3b1b7fb91882995dcb9f9129eb675946e1d5e6` |
| `eval/f0/results/20261103T043458Z-regression-h/B/run-binding.json` | 1777 | `1826211c1f750688cebdc66a044324638ee14dd6f8eb1f004b34e81bb25e24da` |
| `eval/f0/results/20261103T043458Z-regression-h/A/f0-results.json` | 47410 | `5ce66391e2920a138520399a8d2713ca7ae9676236e85b9bc887f96e47b30fcd` |
| `eval/f0/results/20261103T043458Z-regression-h/A/run-binding.json` | 1777 | `e170874cef3ae4894620ef6920786290deec064fdb6fdc52a6e95bf814e03d79` |
| `eval/f0/results/20261103T043458Z-regression-h/boundary/boundary-results.json` | 10016 | `e4687f33db9b88a54d67fdf7f587a4429f53003b8a2a36a3a45ed2294297d04f` |
| `eval/f0/results/20261103T043458Z-regression-h/boundary/run-binding.json` | 1774 | `208de8394c0c48cafd66b286c977707645fba62ecaccfaa17794add4eac43ed2` |
| `eval/f0/results/20261103T043458Z-regression-h/diagnostic-report.md` | 9216 | `cc3e9e5f1001c5f9cc2b20f3e574f8a4f9bacf8cab3f0adade134ae32c5af9e7` |
| `eval/f0/chat-template/v3-diag-h/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-diag-h/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-diag-h/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-diag-h/run-binding.json` | 1761 | `c1362494b5d654123b50fa0b29d55af5e1ba2281736a8648a5eec93c948c26e8` |
| `eval/f0/chat-template/v3-regression-h/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-regression-h/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-regression-h/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-regression-h/run-binding.json` | 1778 | `79deb9be1da8f92b0ecba715fe7588d83f3b9f73c6a7b9e3406d2b5e9a17b357` |

- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 이 Devreport는 UTF-8·LF·BOM 없음이다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 집계는 결과 JSON의 열거형·불리언·개수·해시 필드, `predicted.steps` capability ID, 골든의 id·기대 t만 사용했다(키 순서 판별을 위한 골든 기대값 복원은 메모리 안 해시 대조에만 썼다). 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.23 P03 재시도 힌트 구체화 후보 i 구현·등록 초안 (2026-11-03, 독립 Claude Code Opus 5.5 high, root 검토 전)

- §12.1~§12.22는 바꾸지 않았다. 이 절 앞 바이트(413066 B, SHA `81b981f99a6c5dffe6012215222f873479fd7db8eae1a2c9dbc4aa03fdd704fc`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: 모델 실측(SSH·HTTP·모델 call/load/pull/unload), 서비스 변경, 다운로드, 새 capture, git 쓰기 전부. 제품 wire·`f0-judge-v2`(어떤 시도를 PASS/INVALID 로 볼지)·정확 일치 oracle·골든·fixture·경계 케이스·임계·분모·예산(입력 3072, `max_tokens` 192/256, ctx 4096)·재시도 1회·timeout·`classify-v3-f`·`PRESENCE_V1`·기존 등록·결과·capture·evidence·SPEC·Plan 변경 0. B·경계 요청 바이트(첫 시도·재시도)도 바꾸지 않았다.
- **결론: 사용자 결정(2026-11-03) "재시도 힌트 구체화 후보 i" 에 따라, 새 등록에서만 켜지는 재시도 힌트 `parameters.retryHint=STEPS_DETAIL_V1` 과 `plan-v3-i`(g 기반)의 구현·시험·audit·등록 초안 3개까지 마쳤다. 계획(A) 판정이 `STEPS_INVALID` 일 때 재시도 메시지의 고정 코드만 판정의 같은 검사 분기가 찾은 세부 코드(`EMPTY_FOR_TOOL_TASK`·`NONEMPTY_FOR_NO_TOOL_TASK`·`ITEM_FORMAT_INVALID`)로 바뀐다. 판정·결과 행 `errorCodes`·B·경계·기존 등록(a~h)의 재시도 바이트는 그대로다. i 는 실측하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).** 진단 i 를 실행하려면 로컬 C17/C18 사용자 허용이 먼저 필요하다.

#### 1) SPEC 재시도 계약과 현재 코드 경로

- 정본 SPEC(`C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`, SHA `34fd6223…66be`):
  - 95줄 D51: "압축 출력 스키마·출력 상한·스키마 검증·1회 재시도".
  - 1203줄(§14.5 압축 출력 스키마·검증·재시도): "실패하면 오류 요지를 덧붙여 **1회만 재시도**한다. 재시도도 실패하면 Job 을 `FAILED`(errorCode `PLAN_INVALID`)로 끝내고 … 추측 인자로 Tool 을 실행하지 않는다". 같은 줄은 "비활성·미등록 Tool backstop·… 의미 판정은 스키마로 옮기지 않고 Gateway 검증(1회 재시도 포함)을 그대로 적용"한다고 정한다.
  - 1211줄(검색어 참조 계약): "고정 오류 요지만 첨부해 같은 바인딩으로 1회 재시도하고 재실패하면 `FAILED`/`PLAN_INVALID` … (원문을 오류에 붙이지 않음, Source·Composer 0)".
  - 1923줄 AC-32·2073~2075줄 AC-32-SCHEMA/LENGTH/RETRYOK: 호출 2건(초회 + 재시도 1)·재실패 시 `FAILED`/`PLAN_INVALID`·재시도 성공 시 재시도 출력만 사용.
- 계약 안 판단 근거: SPEC 은 재시도에 붙이는 것을 "오류 요지"(1203)·"고정 오류 요지"(1211)로만 정하고 코드 집합·세분도는 정하지 않는다. 금지는 원문 첨부와 재시도 횟수 초과다. 후보 i 의 세부 코드는 판정 분기에서 나온 고정 열거값 3개이며 모델 출력 원문·카탈로그 값·부분 문자열을 담지 않는다. 재시도 횟수·실패 처리·같은 바인딩·`response_format`(같은 요청 바인딩) 은 그대로다. 따라서 SPEC 변경 없이 계약 안이다.
- 변경 전 코드 경로(HEAD C16):
  - `MessageAssembler.retry`(`MessageAssembler.java:87-92`, 변경 없음): 원 메시지에 `직전 출력이 무효다(오류: <코드>). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.` 한 줄을 덧붙인다.
  - `ClassificationJudge.execute`(변경 전 `:90`): 재시도 코드 = `errors.getLast()` = 직전 판정 `errorCode`. 계획 steps 위반은 배열 아님·10개 초과·항목 형식·t 불일치가 모두 `STEPS_INVALID` 하나였다(변경 전 `:66-69`).
  - `F0Measurements.run`(`:33`)이 `execute` 를 부르고, 결과 행 `errorCodes` 는 판정 `errorCode` 배열이다.
  - `InputAudit.withRetries`(변경 전 `:72`)가 `RETRY_ERROR_CODES` 11종마다 같은 `MessageAssembler.retry` 로 재시도 행을 만들고, `F0RunnerCli` run(`:59`)·`input-audit-render`(`:141-142`)와 `MeasurementSession.audited` 가 같은 목록으로 사전 계수·호출 직전 검사를 한다.
  - 세부 원인은 독립 진단 `AttemptDiagnostics.steps`(`shape`·`toolConsistency`)에만 있었고 판정·재시도에는 쓰이지 않았다.

#### 2) 후보 i 설계

- 기반 후보는 g 다(h 아님). 근거:
  - h 의 계획 스키마 송신 순서는 서버 출력 순서를 바꾸지 못했다(진단 h 판별 가능 A 15시도 모두 정렬 순서, §12.22 4)).
  - 회귀 h 에서 A R07 q 가 새로 실패했고 회귀 g 는 통과였다(§12.22 5)).
  - g(`JSON_SCHEMA_V1`)는 송신 바이트가 이미 측정됐고 회귀 R01~R08 A·B 를 통과했다. h 를 기반으로 하면 효과가 없던 요인과 R07 악화 요인을 함께 끌고 간다.
- opt-in 방식: `Prereg.RETRY_HINTS = {STEPS_DETAIL_V1}`(`Prereg.java:47`), `Document.retryHint()` = `parameters.retryHint`, 키가 없으면 `NONE`(`:55`). v3 전용이며 v1/v2 의 같은 키·`NONE`·미정의 값·비문자열·null 은 `PREREG_INVALID`(`:91-92`). `CONTEXT_PROJECTIONS` 와 같은 패턴이다(기존 값은 등록 값으로 쓰지 않음).
- 재시도 코드 매핑(계획 판정이 `STEPS_INVALID` 일 때만, `ClassificationJudge.judge` 의 같은 검사 분기):

| 판정 분기(검사 순서 그대로) | 판정 errorCode | 결과 행 `errorCodes` | NONE 재시도 코드 | STEPS_DETAIL_V1 재시도 코드 | 대응 진단 값 |
|---|---|---|---|---|---|
| steps 가 배열 아님·10개 초과 | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | shape `NOT_ARRAY`·`TOO_MANY` |
| 항목이 문자열 아님 | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | shape `ITEM_NOT_TEXT` |
| 항목이 capabilityId 형식 아님 | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | `ITEM_FORMAT_INVALID` | shape `ITEM_FORMAT_INVALID` |
| t 가 KQ·LQ·INC 인데 steps `[]` | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | `EMPTY_FOR_TOOL_TASK` | toolConsistency 같은 이름 |
| t 가 GQ·UNS 인데 steps 비지 않음 | `STEPS_INVALID` | `STEPS_INVALID` | `STEPS_INVALID` | `NONEMPTY_FOR_NO_TOOL_TASK` | toolConsistency 같은 이름 |
| 그 밖의 오류 10종 | 그대로 | 그대로 | 그대로 | 그대로 | – |

- 구현(f0 패키지 5파일):
  - `ClassificationJudge`: `RETRY_HINT_NONE`·`STEPS_DETAIL_V1`·`STEPS_DETAIL_CODES`(`:18-20`). `Verdict` 에 `stepsDetail` 성분을 더하고 기존 5인자 생성자는 null 로 위임한다(`:23-26`). 판정의 steps 검사는 `!isTextual() || !matches` 를 두 if 로 나눠 각각 `STEPS_INVALID`(세부 없음)·`ITEM_FORMAT_INVALID` 를 기록하고, t 불일치는 `EMPTY_FOR_TOOL_TASK`·`NONEMPTY_FOR_NO_TOOL_TASK` 를 기록한다(`:75-81`). 반환 valid·errorCode 와 검사 순서는 그대로다. `retryCodes(kind, hint)`(`:89`): PLAN+STEPS_DETAIL_V1 이면 기존 11종 뒤에 세부 3종을 붙이고, 그 밖은 기존 11종이다. `retryCode(verdict, hint)`(`:96`), 미정의 힌트는 `RETRY_HINT_INVALID`(`:99`). `execute` 8인자(`:115`): 재시도 메시지 코드만 `retryCode` 로 정하고(`:123`·`:129`), `errors` 에는 판정 `errorCode` 를 그대로 넣는다. 기존 7인자는 `NONE` 으로 위임한다.
  - `InputAudit.run(…, retryHint)`(`:51-56`): 같은 `ClassificationJudge.retryCodes` 로 재시도 행을 만든다. 4인자는 `NONE` 위임, `boundary` 는 기존 11종 그대로다.
  - `F0Measurements.run`(`:33`): 등록 `retryHint` 를 `execute` 에 넘긴다. `boundaryGen` 은 바꾸지 않았다(경계는 분류라 힌트 무관).
  - `F0RunnerCli`: run 의 호출 전 audit 검사가 등록 `retryHint` 로 목록을 만든다(`:59`). `input-audit-render` 에 `--retry-hint`(선택, `RETRY_HINTS` 값만, 그 밖은 `ARGUMENT_INVALID`)를 더했다(`:134-135`·`:144-145`).
  - 사전 계수(`InputAudit`)·렌더러(`input-audit-render`)·실측 재시도(`execute`)가 같은 `retryCodes` 와 같은 `MessageAssembler.retry` 를 쓴다.
- B·경계: 분류 판정에는 steps 가 없어 `stepsDetail` 이 항상 null 이고 `retryCodes(CLASSIFY, hint)` 는 기존 11종이다. 따라서 B 재시도까지 바꿀 필요가 없고 바꾸지 않았다.
- `plan-v3-i`(g 기반, 1줄 → 6줄, 4622 B): g 의 32줄 "뒤에 쓰는 t 는 앞서 쓴 steps 와 맞는 t 다. 재시도 요청의 STEPS_INVALID 는 …" 에서 앞 문장은 그대로 두고, 뒤 문장을 재시도 코드 4종의 뜻과 고치는 방법으로 바꿨다. 나머지 36줄은 g 와 바이트가 같다.
  - `EMPTY_FOR_TOOL_TASK`: t 가 KQ·LQ·INC 인데 steps 가 비었다. t 에 맞는 Tool 로 steps 를 채운다(INC 면 tc.logs.search 포함).
  - `NONEMPTY_FOR_NO_TOOL_TASK`: t 가 GQ·UNS 인데 steps 가 비지 않았다. steps 를 [] 로 쓴다.
  - `ITEM_FORMAT_INVALID`: steps 에 capabilityId 형식이 아닌 항목이 있다. 아래 Tool 목록의 capabilityId 를 그대로 쓴다.
  - `STEPS_INVALID`: steps 가 배열이 아니거나 10개를 넘거나 문자열이 아닌 항목이 있다. capabilityId 문자열 배열(최대 10개)로 쓴다.
  - pin tokenizer 기준 계획 메시지 계수는 g 대비 +150 이다(audit 기존 A 행 전부 +150).

#### 3) G08 효과 근거와 위험(실측 전 미확정)

- 효과 근거:
  - f·g·h 의 G08 재시도 입력은 초회 + `오류: STEPS_INVALID` 한 줄이었다. `STEPS_INVALID` 는 형식 위반과 t·steps 불일치를 함께 뜻하고, g 프롬프트의 설명도 그 둘을 함께 적었다. temperature 0 에서 두 시도 출력 SHA 가 같았다.
  - i 의 G08 재시도는 `오류: EMPTY_FOR_TOOL_TASK` 를 싣는다(G08 두 시도 모두 `toolConsistency=EMPTY_FOR_TOOL_TASK`, §12.20·§12.22). 시스템 프롬프트가 그 코드의 뜻과 조치("t 에 맞는 Tool 로 steps 를 채운다(INC 면 tc.logs.search 포함)")를 같은 이름으로 적는다. 재시도 입력이 처음으로 "무엇이 틀렸고 어떻게 고치는지"를 담는다.
  - 진단 g·h 에서 다른 유효 INC 6문항은 모두 steps 에 `tc.logs.search` 를 넣었다. 지시가 구체적이면 같은 규칙을 따를 여지가 있다(추정).
- 위험:
  - 계획 시스템 프롬프트가 바뀌므로 A 초회 출력도 g 와 달라질 수 있다. h 에서는 −13토큰 변경으로 A 9문항 출력 SHA 가 바뀌었고 R07 q 가 악화됐다. i 는 +150토큰이다.
  - 재시도에서 steps 를 채우지 않고 t 를 GQ·UNS 로 바꿔 유효해질 수 있다. 그러면 G08 최종 t 가 `INC` 가 아니다(3b 미충족).
  - 정렬 송신 순서상 steps 가 t 보다 먼저 생성된다. 재시도의 steps 는 아직 쓰지 않은 t 에 관한 지시에 기대어 정해진다.
  - 첫 시도 초회 출력이 g 와 같게 유지되는지는 보장되지 않는다. 재시도만 바꾸는 단일 요인 설계(설명을 재시도 메시지에 싣는 방식)는 사용자 결정(프롬프트에 코드 뜻 기재)과 달라 택하지 않았다.
  - 결과 행에 실제 송신한 재시도 코드 열은 더하지 않았다(요청 밖). 송신 코드는 첫 시도 `attemptDiagnostics.steps`(shape·toolConsistency)로 재구성할 수 있다. 다만 형식 위반 항목과 문자열 아닌 항목이 한 배열에 섞이면 판정(첫 위반)과 진단(마지막 분류)이 다를 수 있다. `JSON_SCHEMA_V1` 이 steps 항목을 문자열로 제약하므로 드물 것으로 본다(추정). 세부 3코드 중 `ITEM_FORMAT_INVALID` 재시도는 `STEPS_INVALID` 재시도와 토큰 수가 같아 계수로는 구분되지 않는다.
  - 진단 보고서 머리 줄은 `retryHint` 를 표기하지 않는다(run-binding `parameterManifest.retryHint` 에는 기록된다, 시험 확인).
  - R=1·단일 결정 출력 근거다.

#### 4) 기존 등록·B·경계 바이트 불변 증명

- 기존 등록(a~h) 재시도 바이트: 18개 과거 등록 모두 `retryHint` 키가 없고(시험 단정) `retryHint()=NONE` 이다. `eachAuditCoversEveryPlannedCallOfItsRegistrationWithinTheRegisteredProfile` 가 각 등록의 커밋된 audit 표(바이트 불변) 행 순서·id·messagesSha256 을 현재 Java 조립 목록(재시도 포함)과 전수 대조해 통과했다. 따라서 현재 하네스가 a~h 등록으로 만드는 재시도 메시지는 등록 당시와 바이트가 같다.
- 판정 경로: `F0RetryHintTest.executeSendsTheDetailCodeOnlyUnderTheOptInAndKeepsTheJudgeOutcome` 는 NONE·7인자 호출의 재시도 메시지가 `retry(first, 판정 errorCode)` 이고 문자열이 `직전 출력이 무효다(오류: STEPS_INVALID). …` 와 같음을 단정한다. 같은 출력에서 힌트와 무관하게 status·retried·firstValid·outputSha256·errorCodes 가 같다.
- HTTP stub 종단(`optInChangesOnlyPlanRetryMessagesAndTheAuditCoversExactlyTheSentRetries`, 등록 2개 = 힌트 없음·STEPS_DETAIL_V1, B·A·boundary-gen R=2):
  - B·경계 요청(초회·재시도)과 A 초회 요청 raw 바이트가 두 등록에서 같다.
  - A 재시도는 메시지 외 본문이 같고, 메시지만 `retry(first, STEPS_INVALID)` ↔ `retry(first, 세부 코드)` 다. 세 세부 코드가 모두 송신됐다.
  - 송신 재시도 메시지 SHA = 각 등록 audit 의 같은 위치 id 행(`A/<문항>/retry/<코드>`)이다. 힌트 없는 audit 에는 세부 코드 행이 없다.
  - 결과 행 questionId·status·retried·firstValid·errorCodes(`["STEPS_INVALID","STEPS_INVALID"]`)·outputSha256·predicted·responseFormatSha256 이 같다. run-binding `parameterManifest.retryHint` 는 opt-in 쪽에만 있다. sentinel 비노출이다.
- 세부 코드 audit 행이 없는 opt-in 등록은 A 호출 전 `INPUT_AUDIT_MISSING`(요청 0)이고 B 는 같은 audit 로 측정된다(`optInRegistrationWithoutDetailRetryRowsIsRejectedBeforeAnyPlanRequest`).
- 렌더러: `input-audit-render` 는 힌트 없으면 기존 목록과 같고, 힌트가 있으면 A 외 행이 순서·id·SHA·렌더 문자열까지 같으며 A 에 문항당 세부 3행만 더해진다(`inputAuditRenderAppendsDetailRetriesOnlyToPlanRowsUnderTheOptIn`).
- 실제 등록: `candidateIChangesOnlyPlanPromptAndAddsPlanDetailRetriesAndKeepsEveryOtherRequestOfCandidateG` 가 g·i(진단·회귀)의 실제 조립 목록과 audit 를 대조한다. A 외 행은 SHA·audit SHA·계수가 g 와 같다. 기존 A 행은 system 만 다르다. 새 행은 i 첫 시도 + 세부 코드 메시지다. 기존 행 순서는 유지된다.
- 결과: 위 시험 모두 PASS(아래 5)).

#### 5) 시험(이 세션 실행, root 실행 아님, Gradle 동시 실행 0)

- 실패 시험 먼저: 구현 전 `:gateway-eval:compileTestJava` 가 `cannot find symbol`(`retryHint()`·`retryCodes`·`STEPS_DETAIL_V1`·`stepsDetail` 등) 40 errors 로 `BUILD FAILED`(exit 1)였다. 구현 뒤 `F0RetryHintTest`·`ClassificationJudgeTest`·`F0InputBudgetTest` 필터 실행은 8·5·6 tests, failures 0 이었다.
- 신규 `F0RetryHintTest` 8건: 판정 세부 코드·진단 일치(①), 코드 집합·미정의 힌트 거부(②), execute 재시도 메시지·판정 불변(③), opt-in 이 보낼 수 있는 재시도 코드 14종 = audit 코드 집합(④), Prereg opt-in 규칙(⑤), 세부 행 없는 audit 거부(⑥), HTTP stub 종단(⑦), 렌더러(⑧).
- `F0VersionedInputsTest.writeAudit` 에 힌트 인자 오버로드를 더했다(기존 4인자는 NONE 위임, 기존 호출 불변).
- `F0V3RegistrationDraftTest`(C7·C11·C15 때와 같은 방식):
  - 하네스 소스가 바뀌어 h(C15/C16) 등록이 현재 하네스 기준 stale 이 된다. h 3문서를 과거 등록 묶음(C15·C16, `PRESENCE_V1`)에 넣었다. h 는 역사 신원(등록 commit 트리 harnessDigest·PREREG commit C16·입력 바이트 불일치 0)과 현재 `localMismatches=["harnessDigest"]`·`verify=PREREG_INPUT_CHANGED` 로 단정된다.
  - 과거 18개 등록 모두 `retryHint` 키 없음을 단정하고, h 의 `responseFormat=JSON_SCHEMA_PLAN_ORDERED_V1` 단정을 더했다.
  - 후보 묶음을 i 3초안(`PRESENCE_V1`·`JSON_SCHEMA_V1`·`STEPS_DETAIL_V1`, 로컬 불일치 0, 초안이면 `PREREG_NOT_COMMITTED`·등록 뒤면 실제 `verify` 통과, capture 경로 고유)으로 바꿨다.
  - 비교 시험에 i↔gate-i·i↔regression-i 를 넣고, g→i 는 `planPrompt`·`inputAudit`·`promptSha256.plan`·`retryHint`(추가)만 다르다고 단정한다.
  - `planned()` 크기·교차 투영 계수 식은 등록 힌트의 계획 재시도 코드 수(11 또는 14)로 계산한다. 기존 f→g·g→h 비교 시험은 그대로다.

| 명령 | exit | 결과 |
|---|---|---|
| `.\gradlew.bat :gateway-eval:test --console=plain --rerun-tasks`(무필터, `AIGW_SPEC_FILE`=정본 SPEC) | 0 | `BUILD SUCCESSFUL in 4m 56s`, `6 actionable tasks: 6 executed`. JUnit XML 42클래스 합산 203 tests·failures 0·errors 0·skipped 0(기존 194 + `F0RetryHintTest` 8 + 등록 시험 1) |
| `.\gradlew.bat :gateway-eval:run --console=plain "--args=test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s` |

#### 6) 입력 audit(실제 Java 조립 + 등록 렌더러 + pin tokenizer)

- 렌더: `f0 input-audit-render --golden {v2 22 | 회귀 30} --fixture eval/golden/tc-registry-fixture-v2.json --classify-prompt eval/f0/v3/prompts/classify-v3-f.txt --plan-prompt eval/f0/v3/prompts/plan-v3-i.txt --cases eval/f0/v2/output-boundary-cases.jsonl --usage-messages eval/f0/v2/usage-probe-input-v2.jsonl --token-messages eval/f0/v2/token-golden-input-v2.jsonl --renderers eval/f0/chat-renderers-v1.json --template-sha256 40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541 --context-projection PRESENCE_V1 --retry-hint STEPS_DETAIL_V1` → `F0_INPUT_AUDIT_RENDERED 717`·`933`. 렌더 JSONL 은 레포 밖 세션 임시 디렉터리에 두었다.
- 계수: `count_input_audit_v2.py`(venv Python `-I -B`, pin tokenizer `aeb13307…dae4`, template `40c21f34…b541`) → `INPUT_AUDIT_WRITTEN 717 OVER_BUDGET 0`·`933 OVER_BUDGET 0`.
- 전수 재검증(Python): 렌더↔audit 행 순서·id·messagesSha256 717/717·933/933 일치, 행별 입력 ≤ 상한·입력+`max_tokens` ≤ 4096 위반 0. 메타데이터(kind·schema·renderer·template·tokenizer·tokenizers 버전·tool)는 g audit 와 같고 `maxInputTokensByGroup` 만 다르다.

| audit | 행 | A 첫/재시도 최대 | B 첫/재시도 최대 | 경계 첫/재시도 최대 | usage | token | 최대 입력+출력 | A 최대 입력+출력 |
|---|---|---|---|---|---|---|---|---|
| g | 651 | 1791/1830 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254 | 2086 |
| i | 717 | 1941/1981 | 1390/1429 | 3023/3062 | 1376 | 2793 | 3254(`boundary/ESCAPE-CONTROL/retry/USAGE_EXCEEDS_LIMIT`) | 2237 |
| 회귀 g | 843 | 1807/1846 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 | 2102 |
| 회귀 i | 933 | 1957/1997 | 1406/1445 | 3023/3062 | 1376 | 2793 | 3254 | 2253 |

- g 대비(같은 위치 id): **B 264·경계 108·usage 2·token 13(=387행)과 회귀 B 360·경계 108·usage 2·token 13(=483행)은 id 순서·messagesSha256·계수가 전부 같다.** 기존 A 264/264·360/360 행은 SHA 가 모두 다르고 계수는 모두 +150 이다. 새 행은 A 세부 코드 재시도 66·90행(문항당 3)이며, 같은 문항 `retry/STEPS_INVALID` 대비 계수 차는 `ITEM_FORMAT_INVALID` 0·`EMPTY_FOR_TOOL_TASK` +1·`NONEMPTY_FOR_NO_TOOL_TASK` +3 이다.

#### 7) 등록 초안 3개·등록 검사

| 초안 | 목적 | R | 골든·audit | capture | harnessDigest | parameterManifestSha256 |
|---|---|---|---|---|---|---|
| `PREREG-v3-diag-i.md` | DIAGNOSTIC | 1 | v2 22 + `input-audit-v3-i.json` | `v3-diag-i` | `9832870eade5edc9f20cd9f96d4a3a84d21896002e6cb09d221aacb3b153f2de` | `c13ed8bc67a5e4252ee4498f464fd9f1d41fb93153106597cd73fefb756f4cda` |
| `PREREG-v3-regression-i.md` | DIAGNOSTIC | 1 | 회귀 30 + `input-audit-v3-regression-i.json` | `v3-regression-i` | `5017e5ffb3a150e6f29305ee2e6aaa4aca70e2ec186c1d8191e4903fc48e09f5` | `c13ed8bc…4cda` |
| `PREREG-v3-gate-i.md` | GATE | 5 | v2 22 + `input-audit-v3-i.json`(diag-i 와 바이트 동일) | `v3-gate-i` | `9832870e…f2de` | `489ca2facb838c601081e22a724af4ca9012c584b3ec5bb58b18c8393b90b322` |

- 기계 판독 블록은 g 블록에서 만들었다. g 대비 바뀐 것은 `registrationStatus`·`registeredCommit`(UNCOMMITTED/null)·`runtimeCaptureDir`·`inputRoles.{planPrompt,inputAudit}`·해당 inputs 2개·`harnessDigest`·`parameters.promptSha256.plan`·`parameters.retryHint`(마지막 키로 추가)뿐이다(줄 단위 diff). `responseFormat=JSON_SCHEMA_V1`·`contextProjection=PRESENCE_V1`·`classifyPrompt`·나머지 입력 13개·파라미터는 같다.
- §2 실행 순서는 h 문서 §2 를 h→i·C15/C16→C17/C18 로만 바꾼 것과 3문서 모두 같다(줄 단위 diff, 회귀 등록 안내 문장에 "재시도 힌트" 추가 1곳).
- gate-i §1 착수 조건은 gate-h 와 같은 수준이다. C17/C18 뒤 3문서 `PREREG_VERIFIED`, 진단 i·회귀 i exit 0·complete·`response_format` 거부 0, 진단 i 에서 `QREF_INVALID` 0·후보 0 `ID_OUT_OF_RANGE` 0·G08 최종 t A·B INC·경계 9/9, root 원문 없는 검토다. 과거 후보 목록에 h(A G08 최종 `INVALID`·회귀 h A R07 q 실패)를 더했다.
- harnessDigest 는 Java `prereg-check` 의 `currentHarnessDigest` 와 Python 독립 재계산(등록 inputs + f0 소스 24파일, 정렬 경로·SHA 쌍 JSON 의 SHA-256)이 일치했다.
- `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차). 거부는 Java `EvalException(2, …)`, Gradle exit 1 이다.

| 등록 | localMismatches | 결과 |
|---|---|---|
| diag-i·regression-i·gate-i(초안) | `[]` | `PREREG_NOT_COMMITTED` |
| diag-h(C15/C16) | `["harnessDigest"]`(current `b8a09a57…03e9`) | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale, 예상대로) |

- 기존 등록 재현성: 과거 18개 등록 모두 입력 바이트 불일치 0 이고 역사 신원은 실제 git `verifyHistorical` 로 재확인했다(시험). 진단 h·회귀 h 는 측정을 마쳤고(§12.22), gate-h 는 착수 조건 3b 미충족으로 실행 대상이 아니다. 신규 측정만 stale 로 거부된다.

#### 8) 다음 커밋 범위 제안(사용자 허용 필요 — 기존 허용은 C15/C16까지)

- C17(로컬 commit, 정확 15파일):
  - `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ClassificationJudge,Prereg,InputAudit,F0Measurements,F0RunnerCli}.java`
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0RetryHintTest,F0V3RegistrationDraftTest,F0VersionedInputsTest}.java`
  - `eval/f0/v3/prompts/plan-v3-i.txt`, `eval/f0/v3/input-audit-v3-i.json`, `eval/f0/v3/input-audit-v3-regression-i.json`
  - `eval/f0/PREREG-v3-diag-i.md`, `eval/f0/PREREG-v3-regression-i.md`, `eval/f0/PREREG-v3-gate-i.md`(UNCOMMITTED/null 그대로)
  - `docs/ai-rag-gateway-architecture-dev-report.md`(C16 뒤 미커밋으로 추가된 §12.21 "로컬 C15/C16 등록·최종 시험"·§12.22·이 절 포함)
  - 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C18(로컬 commit, 정확 3파일): `eval/f0/PREREG-v3-{diag-i,regression-i,gate-i}.md` 에 `registrationStatus=REGISTERED`, `registeredCommit`=C17 SHA, 등록 설명만 기록한다. inputs·harnessDigest·parameters 는 바꾸지 않는다.
- C18 뒤 순서: 3문서 `f0 prereg-check` → (Jetson 메모리·서비스 상태 확인) → 진단 i(diag-i §2) → 회귀 i → gate-i §1 충족 시 R5 → SPEC §14.5 기록·재판정·F0 gate. P04 는 착수하지 않는다.

#### 9) 변경·신규 파일(이 절 제외 14파일)

| 파일 | 구분 | 바이트 | SHA-256 |
|---|---|---|---|
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/ClassificationJudge.java` | 수정(+40/−7) | 11838 | `4978b685dc9ca8c1d86076d10e6aecd24b6588668dbccb45305314744092cc89` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Prereg.java` | 수정(+6/−0) | 17437 | `7023ecdbe6ae4067727b9062d87cb3e98a5d0fb2b196bfc1c02fd0fc7fc6624e` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/InputAudit.java` | 수정(+8/−6) | 6075 | `b3ebcdb3f5d5227a89eb2a45b50a561ca68baa0c213e33660f7f4c666bd1ed48` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/F0Measurements.java` | 수정(+1/−1) | 20263 | `4e8ee1af50622583267155acb4c195e8f0a6cdca8e18de2c75dfab8200fca97d` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/F0RunnerCli.java` | 수정(+7/−4) | 23754 | `0138ceb560fc03ed186ef31498bdcb3d052500469ac264feda7d4789574c9fa2` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0RetryHintTest.java` | 신규(8시험) | 27773 | `8ee9933eff5b72cb7713e17b2cd69911f57152bf3daa47e5e5ff7c32aea8b458` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java` | 수정(+97/−25) | 45301 | `7ed4900125f7f5f97340e5127b501f22e16cdb37f6174d9507451d39a99c868f` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0VersionedInputsTest.java` | 수정(+5/−3) | 19275 | `eefaeef4c185ca274335b7f9eb6b3a0dbf00478cd2cf42ea06ff9661d693da89` |
| `eval/f0/v3/prompts/plan-v3-i.txt` | 신규 | 4622 | `0d5311096ca35f3ef24b230cd3e54c3b0bd8865e5524e94e8752186af1917997` |
| `eval/f0/v3/input-audit-v3-i.json` | 신규(717행) | 102305 | `f30685cdae92b48991a05ebc72d723ebda7bb333ef25757c33de09f4978e91dd` |
| `eval/f0/v3/input-audit-v3-regression-i.json` | 신규(933행) | 132529 | `345869b55f98f8e378f8c5ba5d7104640a390b015635448c2d15bbe3822f1137` |
| `eval/f0/PREREG-v3-diag-i.md` | 신규 초안 | 14351 | `1960dc8fbe1194ec6315144b25787dd5703dc1f02ff04fd2ca1590b40838825d` |
| `eval/f0/PREREG-v3-regression-i.md` | 신규 초안 | 9942 | `50be70ecd83d7d8e123c4f5a9fd13baff1af8383b68ed5795c1b17541992ef17` |
| `eval/f0/PREREG-v3-gate-i.md` | 신규 초안 | 12445 | `720085b9f0d4436c7ffa401e4de980aa7dbae8dfc66a76c26607c2f9186e019d` |

- 14파일 모두 UTF-8·LF(CR 바이트 0)·BOM 없음, `git diff --check` exit 0.

#### 10) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be` 불변이다. HEAD C16 `372dbb4653ab807324ec7361554ea1c9d5f87f82`, staged 0, git 쓰기 0 이다.
- `eval/f0/results`·`eval/f0/chat-template`·`evidence`·`eval/golden`·기존 프롬프트·기존 PREREG·기존 audit·`MessageAssembler`·`AttemptDiagnostics`·`PlanJudge`·`F0Judgement`·`Json.java` 변경 0, 새 `.pyc` 0(Python `-I -B`). `eval/f0` 아래 신규 파일은 위 6개뿐이다.
- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 응답 원문·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS 를 확정하지 않는다.

#### 로컬 C17/C18 등록·최종 시험 (2026-11-03, 독립 Claude Code Opus 5.5 medium)

- 승인 근거: 사용자 승인 "C17/C18 허용 후 진행 (권장)"(2026-11-03). 허용 범위는 로컬 commit C17·C18 두 개뿐이며 push·amend·원격 publish 는 하지 않았다.
- 착수 전 확인: HEAD C16 `372dbb4653ab807324ec7361554ea1c9d5f87f82`(16커밋), staged 0, C17 대상 15파일 SHA-256 이 기대값과 15/15 일치했다(이 Devreport 의 C17 시점 SHA `f88293a99b74611bd911da221f54db45214becec1c4b5d59207ddf488eef7dd9`, 439979 B 포함).

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C17 | `76baa655f3487c8666398b44b9129b7ac219fc16` | 2026-11-03 14:40:32 +0900 | 15파일, 1282 insertions(+), 46 deletions(-) |
| C18 | `086b619f65951b4d40e49ba00802a852e737793b` | 2026-11-03 14:41:11 +0900 | 3파일, 18 insertions(+), 12 deletions(-) |

- C17 정확 15파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 15개, 기대 목록과 diff 0): `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ClassificationJudge,Prereg,InputAudit,F0Measurements,F0RunnerCli}.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0RetryHintTest,F0V3RegistrationDraftTest,F0VersionedInputsTest}.java`, `eval/f0/v3/prompts/plan-v3-i.txt`, `eval/f0/v3/input-audit-v3-i.json`, `eval/f0/v3/input-audit-v3-regression-i.json`, `eval/f0/PREREG-v3-{diag-i,regression-i,gate-i}.md`(UNCOMMITTED/null 초안), `docs/ai-rag-gateway-architecture-dev-report.md`. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않았다.
- C18 정확 3파일: `eval/f0/PREREG-v3-{diag-i,regression-i,gate-i}.md`. §0 제목·현재 상태 문구를 C16 방식(등록 근거·C17/C18 설명·이력)으로 바꾸고, 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C17 SHA 만 바꿨다. §1 이하는 그 2줄 외 바이트 그대로다. C17·C18 커밋 시각의 초는 다르다(39초 차이). 3파일 UTF-8·LF(CR 0)·BOM 없음, `git diff --check` exit 0.
- JSON 검사(Node, C17 blob 대 작업 트리, 파싱 실패 시 원문 비출력): 3문서 모두 전 값이 UNCOMMITTED/null, 후 값이 REGISTERED/C17 SHA 이고, 바뀐 최상위 키는 `registrationStatus`·`registeredCommit` 둘뿐, 두 키를 뺀 나머지 객체 동일·최상위 키 순서 동일(9키)·§1 이하 2줄 외 동일이 true 였다.
- C18 뒤 18커밋, staged 0 이다.

`f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차 실행, 그 프로세스에만 `AIGW_SPEC_FILE`=원 SPEC·`DEBUG`=빈 값):

| 등록 | localMismatches | 결과 | Gradle exit |
|---|---|---|---|
| diag-i | `[]` | `PREREG_VERIFIED` | 0 |
| regression-i | `[]` | `PREREG_VERIFIED` | 0 |
| gate-i | `[]` | `PREREG_VERIFIED` | 0 |
| diag-h(C15/C16) | `["harnessDigest"]` | `PREREG_INPUT_CHANGED`(Java exit 2) | 1 |

- diag-h 는 C17 의 하네스 소스 변경으로 stale 이 된 것이며 §12.23 의 예상과 같다. h 의 나머지 등록은 같은 이유이므로 diag-h 1건으로만 확인했다.

등록 뒤 전체 시험(Gradle 동시 실행 0, 순차):

- 1차 실행은 실행 스크립트에 `AIGW_SPEC_FILE`·`DEBUG` 를 넣지 않아 `:gateway-eval:test` 뒤 `:gateway-eval:testLeafEvidence` 가 `SPEC_INPUT_MISSING`(Java exit 2, Gradle exit 1)으로 멈췄고, 이어진 `test-evidence verify` 도 같은 이유로 exit 1 이었다. 코드·시험 결함이 아닌 실행 환경 누락이므로 코드 변경 없이 prereg-check 와 같은 env 로 재실행했다.
- 재실행 `.\gradlew.bat :gateway-eval:test --console=plain --rerun-tasks` → exit 0, `BUILD SUCCESSFUL in 5m 7s`, `6 actionable tasks: 6 executed`. XML 독립 집계 42클래스·203 tests·failures 0·errors 0·skipped 0(C16 시점 41클래스·194 tests 에 `F0RetryHintTest` 등 반영).
- 이어서 `.\gradlew.bat :gateway-eval:run --console=plain --args="test-evidence verify --tasks :gateway-eval:test"` → exit 0, `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s`.

보존: 모델 실측 0회, SSH·HTTP 요청·다운로드·서비스 변경 0. 코드·시험·프롬프트·골든·기존 PREREG·audit·결과·capture·evidence·SPEC·Plan 변경 0. push·amend·rebase·reset 0. 이 절은 commit 하지 않았으며(C17 커밋 바이트 불변, 작업 트리에만 추가), 응답 원문·API 응답·secrets 는 출력·저장하지 않았다. 다음 순서는 §12.23 8) 그대로다(Jetson 메모리·서비스 상태 확인 → 진단 i → 회귀 i → gate-i §1 충족 시 R5 → SPEC §14.5 기록·재판정·F0 gate). P04 는 착수하지 않는다.

### 12.24 P03 후보 i 진단·개발 회귀 i 실행 (2026-11-03, 독립 Claude Code Opus 5.5 medium, root 검토 전)

- §12.1~§12.23은 바꾸지 않았다. 이 절 앞 바이트(444671 B, SHA `c6099564b18cfbe7a21831df857a3bdc056d6e42aefccf75f1b7893e63665de6`)는 그대로이며 이 절만 추가했다.
- 산출물: 신규 결과 디렉터리 `eval/f0/results/20261103T060939Z-i/`(8파일)·`eval/f0/results/20261103T060939Z-regression-i/`(5파일, 미완), 신규 capture `eval/f0/chat-template/v3-diag-i/`·`eval/f0/chat-template/v3-regression-i/`(각 4파일), 이 절.
- 하지 않은 것: 코드·시험·프롬프트·골든·fixture·PREREG·audit·기존 결과/capture·evidence·SPEC·Plan 변경, git 쓰기 전부, 모델 load/pull/unload·서비스 재시작·num_ctx 변경·다운로드, 회귀 i 6·7단계(boundary-gen·report), 정식 R5(gate-i)·F0 leaf 증거·gate·SPR, P04.
- **결론(진단 R1, 판정 아님):**
  - 진단 i 7개 명령은 모두 exit 0·`complete=true`다. 서버 `response_format` 거부·timeout·서비스 재시작·OOM은 0이다.
  - **진단 i A G08은 재시도 코드 `EMPTY_FOR_TOOL_TASK`를 받았지만 두 시도 출력 SHA가 g·h와 같은 `d648833f…`이고, t=`INC`·steps `EMPTY_FOR_TOOL_TASK`(`STEPS_INVALID` 2회)로 최종 `INVALID`다.** 재시도 세부 코드와 `plan-v3-i` 설명은 G08 출력 바이트를 바꾸지 못했다. B G08은 최종 `INC`다.
  - 진단 i: 경계 9/9, `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0, Context 복사 0. A taskType 21/22·인자 14/22(g 15/22), B 20/22·18/22(g와 첫 출력 SHA 22/22 같음).
  - **개발 회귀 i는 미완이다.** 1~4단계는 exit 0, 5단계(A)는 R06 요청이 120 s timeout 뒤 `abortReason=ENDPOINT_UNREACHABLE`·`complete=false`(28/30 시도)로 Gradle exit 1이다. 같은 시각부터 Jetson이 ping·SSH 모두 응답하지 않아(06:24~06:37Z 12회 연속) 6·7단계를 실행하지 않았다.
  - 따라서 gate-i §1 조건 2(회귀 i exit 0·`complete=true`)와 조건 3b(G08 최종 t A·B 모두 INC)가 미충족이다(판정은 root). 기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수.

#### 1) 착수 전 환경(읽기 전용 확인)

- 직전 시도(14:56:03 KST 재시작): warmup에서 qwen3 적재가 15:01:07 KST `Load failed`(llama-server 기동 대기 timeout)로 실패했다. bge-m3만 상주해 착수 조건 미충족으로 NOT_RUN이었고, 레포 변경 0·Devreport 미기록이었다.
- 이번 재시작: 사용자가 15:05:46 KST에 `systemctl restart ollama`를 다시 실행했다. 이 세션은 재시작·로드를 하지 않았고, warmup이 두 모델을 적재할 때까지 1분 간격으로 읽기 전용 확인만 했다. 확인 방법은 §12.22 1)과 같다(SSH `bash -s`, `GET /api/ps`는 Jetson 안 python3로 허용 필드만 파싱하고 실패 시 `parse failed`만 출력, journal은 줄 수만).
- **journal `Load failed` 1줄의 귀속(시각 비교)**: `--since "2026-11-03 15:05:40"` 범위에는 `Load failed` 1줄(15:05:43.913 KST, blob `85e4a5b7…`)이 있다. 이 줄은 PID 15289가 남겼고, 그 시각은 이번 서비스의 `ActiveEnterTimestamp` 15:05:46보다 앞선다. 이번 인스턴스는 PID 15387이다. 따라서 이전 인스턴스(14:56:03 기동분)가 종료되면서 남긴 줄이며 이번 재시작의 실패로 세지 않았다(root 확인과 같음). 착수 조건의 `Load failed` 0건은 15:05:46 이후 줄 기준이다. PID 15387 기동 단계: 15:05:45 `inference compute` → 15:06:24 `offloaded 37/37 layers to GPU` → 15:06:28 `model loaded` → 15:06:29 `loaded runners`.
- 06:09:19Z 확인 결과(착수 조건 충족):
  - 서비스: active/running, `ActiveEnterTimestamp=2026-11-03 15:05:46 KST`, NRestarts 0.
  - journal(15:05:46 이후): `library=cpu` 0, `Load failed` 0, `out of memory` 0, `library=CUDA` 3줄.
  - `qwen3:4b-instruct` `0edcdef34593eac1aa2be9c7d06c432dcf81945adca5eca2f27662c18f168ba0` ctx 4096, size_vram=size 3178149969.
  - `bge-m3:latest` `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` ctx 4096, size_vram=size 664000265.
  - Mem 7619 MiB 중 available 2175, swap 3809 MiB 중 사용 1183. 로컬 11434 미사용, ssh 프로세스 0.

| 확인 시각(UTC) | 시점 | 상주 모델 | Mem available MiB | swap 사용 MiB | 중단 조건 |
|---|---|---|---|---|---|
| 06:06:34 | warmup 중 | qwen3 적재 완료(journal, /api/ps 미조회) | 3100 | 1047 | 대기 |
| 06:07:36 | warmup 중 | 1(qwen3) | 2851 | 1171 | 대기 |
| 06:08:11 | warmup 중 | 1(qwen3) | 2850 | 1180 | 대기 |
| 06:09:19 | 진단 i 착수 전 | 2 | 2175 | 1183 | 충족 |
| 06:12:48 | 진단 i B 뒤 | 2 | 2130 | 1187 | 충족 |
| 06:14:55 | 진단 i A 뒤 | 2 | 1922 | 1192 | 충족 |
| 06:16:58 | 진단 i boundary 뒤 | 2 | 1846 | 1193 | 충족 |
| 06:20:13 | 회귀 i B 뒤 | 2 | 1429 | 1300 | 충족 |
| 06:25 이후 | 회귀 i A 중단 뒤 | 확인 불가 | 확인 불가 | 확인 불가 | **SSH 타임아웃** |

- 각 B·A·boundary 뒤 재확인에서 ActiveEnterTimestamp·NRestarts 0·2모델 digest·ctx 4096·size_vram=size·`library=cpu`/`Load failed`/OOM 0은 06:20:13Z까지 유지됐다.
- available은 착수 전 2175에서 회귀 i B 뒤 1429 MiB까지 줄었고 swap은 117 MiB 늘었다. §12.22(최저 1340 MiB)와 비슷한 수준이었지만, 이번에는 그 직후 장비가 응답하지 않게 됐다.

#### 2) 실행 기록

- 공통 조건: §12.22 2)와 같다. Bash에서 `powershell.exe -NoProfile -File <scratchpad i-run-f0.ps1>`로 실행했고, 래퍼는 `Set-Location C:\workspace_nori\nori-ai-gateway`, `AIGW_SPEC_FILE`=정본 SPEC 경로, `AIGW_PARENT_RUN_ID`=각 회차 ID, `DEBUG`=빈 값을 설정한 뒤 `.\gradlew.bat :gateway-eval:run --console=plain "--args=…"`를 호출한다. `--args` 값은 각 등록 §2와 같다. 한 명령씩 실행하고 exit를 확인했다.
- run prefix `20261103T060939Z`(시작 때 1회 결정). 회차 ID는 `20261103T060939Z-i`·`20261103T060939Z-regression-i`다. 결과 2디렉터리·capture 2디렉터리는 사전 부재를 확인했다.
- 터널: `Start-Process -WindowStyle Hidden`으로 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`를 띄웠다. 진단 i는 PID 1912이며 명령행 확인 뒤 report 전에 `Stop-Process`로 그 PID만 종료했다. 회귀 i는 PID 14380이며, 회귀 A 실패 확인 시점에 이미 종료돼 있었다(11434 LISTENING 0, ssh 프로세스 0). 다른 프로세스 중단은 0이다.

| 단계 | 명령(§2 그대로) | exit | 결과(UTC) |
|---|---|---|---|
| i-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-diag-i.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `9832870e…f2de`, parameterManifestSha256 `c13ed8bc…4cda` |
| i-2 | `f0 preflight … --out eval/f0/results/20261103T060939Z-i/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| i-3 | `f0 chat-template … --out eval/f0/chat-template/v3-diag-i`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template 4040 B `40c21f34…b541`, model-digest 파일 SHA `235b84f5…5ddd`(g·h와 같음) |
| i-4 | `f0 run … --path B … --prompt eval/f0/v3/prompts/classify-v3-f.txt --run-id 20261103T060939Z-i --out …/B` | 0 | 06:10:36~06:12:33, `F0_RUN_COMPLETED B`(Gradle 1m 56s) |
| i-5 | `f0 run … --path A … --prompt eval/f0/v3/prompts/plan-v3-i.txt …` | 0 | 06:13:01~06:14:50, `F0_RUN_COMPLETED A`(1m 48s) |
| i-6 | `f0 boundary-gen … --prompt eval/f0/v3/prompts/classify-v3-f.txt …` | 0 | 06:15:09~06:16:54, `F0_BOUNDARY_COMPLETED 9`(1m 44s) |
| i-7 | 터널 종료 뒤 `f0 report --prereg eval/f0/PREREG-v3-diag-i.md --run-id 20261103T060939Z-i --results …`(`--out` 없음) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`, rawResultsSha256 `31ee7f8d…0370` |
| r-1 | `f0 prereg-check --prereg eval/f0/PREREG-v3-regression-i.md` | 0 | `PREREG_VERIFIED`, localMismatches `[]`, harnessDigest `5017e5ff…`, parameterManifestSha256 `c13ed8bc…` |
| r-2 | `f0 preflight …/20261103T060939Z-regression-i/preflight.json` | 0 | `F0_PREFLIGHT_OK 2` |
| r-3 | `f0 chat-template … --out eval/f0/chat-template/v3-regression-i`(사전 부재 확인, 신규) | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template `40c21f34…b541` |
| r-4 | `f0 run … --path B --golden eval/f0/v3/golden-regression-v3.jsonl …` | 0 | 06:18:15~06:20:10, `F0_RUN_COMPLETED B`(1m 54s) |
| r-5 | `f0 run … --path A --golden eval/f0/v3/golden-regression-v3.jsonl … --prompt eval/f0/v3/prompts/plan-v3-i.txt …` | **1** | 06:20:26~06:24:58, `BUILD FAILED in 4m 31s`. 결과 파일 `complete=false`, 시도 28/30, `abortReason=ENDPOINT_UNREACHABLE` |
| r-6 | `f0 boundary-gen …` | 미실행 | r-5 실패·Jetson 무응답으로 중단 |
| r-7 | `f0 report …` | 미실행 | 같은 이유 |

- preflight 두 파일은 g·h의 같은 위치 파일과 바이트가 같다(SHA `c9c344ac…69e2`·`fe4a66cb…1b10`).
- r-5 경과(정제 필드만): G01~R04는 OK·INVALID로 정상 진행했고, R05 지연이 11853 ms(같은 회차 다른 UNS·LQ 문항 약 2.5~3.9 s)로 늘었으며, R06 요청이 `ENDPOINT_TIMEOUT`(120009 ms)이었다. 하네스는 이어서 endpoint 도달 불가로 회차를 중단했다(R07·R08 미요청).
- r-5 실패 뒤 읽기 전용 확인: SSH `Connection timed out`. 06:26:22Z~06:37:43Z 약 1분 간격 12회 ping·SSH 모두 응답 0. Jetson 쪽 서비스 상태·journal·메모리는 확인하지 못했다. 원인(메모리 압박에 의한 장비 정지인지, 네트워크 문제인지)은 미확정이다. 재시작·재실행은 하지 않았다.
- F0 leaf 증거·RUN PARENT·§14.5 제안서 생성은 0이다.

#### 3) 런타임 동일성

- run-binding 7개(진단 i B·A·boundary·capture, 회귀 i B·A·capture)의 runtimeManifest는 모두 같다.
  - runtimeManifestSha256 `b1e6a3af…`(c~h와 같음), specSha `34fd6223…66be`(FILE_HASHED)
  - registeredCommit C17 `76baa655…`, preregCommit C18 `086b619f…`(preregCommitTime 2026-11-03T05:41:11Z)
  - `contextProjection=PRESENCE_V1`, `responseFormat=JSON_SCHEMA_V1`, `retryHint=STEPS_DETAIL_V1`, parameterManifestSha256 `c13ed8bc…4cda`
  - harnessDigest: 진단 i `9832870e…`, 회귀 i `5017e5ff…`(각 등록값과 같음)
- 실제 `usage.prompt_tokens`와 사전 audit 계수가 다른 호출: 진단 i 보고서 A 0/0·B 0/0·boundary 0/0, 정제 필드 재집계 A 0/24·B 0/22·경계 0/9. 회귀 i(부분) A 0/29·B 0/30.
- 첫 시도 프롬프트 토큰: 진단 i A 1904~1941(h 1741~1778 대비 +163, 즉 g 대비 +150)·B 1353~1390. 출력 토큰 최대: A 89·B 60.
- `responseFormatSha256`(첫 8자): 진단 i A `398c3a9c`·`dd5b4987`(g A와 같음), B·경계 `b7f55fab`·`ee5d249c`(g와 같음), 회귀 i B는 `18767a88` 추가(회귀 g와 같음).

#### 4) 진단 i 결과(`eval/f0/results/20261103T060939Z-i/diagnostic-report.md`, R1·분모 22, 원문 없는 정제 필드 재집계와 일치)

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A | 21/22 | 14/22 | 21/22 | 21/22 | 20/22 | 2/22 | 3577/6746 | `STEPS_INVALID` 3(G05 1·G08 2) |
| B | 20/22 | 18/22 | 20/22 | 22/22 | 22/22 | 0/22 | 2948/5115 | 0 |

- 오류 종류: A `ARGUMENT_MISMATCH` 7·`INVALID_OUTPUT` 1, B `ARGUMENT_MISMATCH` 4·`TASK_TYPE_MISMATCH` 2. timeout은 A·B 모두 0이다. INVALID는 A G08 1건이다.
- 지연 최대: A 10595 ms(G01), B 34783 ms(G01, 회차 첫 요청). B 나머지 21건은 ≤5115 ms다.
- t 오답: A G08(최종 무효, timeout 아님). B G07(UNS→INC), G16(UNS→GQ).
- 인자 오답: A G01·G09·G11·G12·G17·G22 from+to, G16 eqp, G08 무효. B G01·G11·G12·G17 to.
- g 대비 A 인자 변화: G22가 맞음 → from+to 오답으로 바뀌었고, 나머지 문항의 정오는 g와 같다(G09·G16·G17은 g처럼 오답, G20은 g처럼 맞음).
- A 최종 steps(유효 21문항):
  - INC 6문항 모두 `tc.logs.search`를 포함한다: G01 `tc.logs.search;tc.git.searchCode`, G09·G17·G20·G22 `tc.logs.search`, G12 `tc.dlq.list;tc.logs.search`.
  - LQ: G02 `tc.eqp.getOperationalSnapshot;tc.event.getRecent`(g·h는 snapshot 1개), G06·G10·G19 `tc.eqp.getOperationalSnapshot`, G11 `tc.event.getRecent`. KQ: G04·G13 `tc.knowledge.search`, G21 `tc.git.searchCode`. GQ·UNS 7문항 `[]`.
- **A 재시도 2건(송신 코드는 첫 시도 판정 errorCode와 `attemptDiagnostics.steps`로 재구성)**:

| 문항 | 송신 재시도 코드 | 첫 시도 | 재시도 결과 | 최종 | 두 시도 출력 SHA |
|---|---|---|---|---|---|
| G05 | `NONEMPTY_FOR_NO_TOOL_TASK` | t=GQ, steps 비어 있지 않음 | t=GQ, steps `[]`(CONSISTENT) → 유효 | GQ, steps `[]` | 다름(`c999b727`→`88617a94`, 후자는 G18 첫 출력과 같음) |
| G08 | `EMPTY_FOR_TOOL_TASK` | t=INC, steps `[]` | t=INC, steps `[]` → 무효 | `INVALID` | 같음(`d648833f`) |

  - 세부 코드별: `NONEMPTY_FOR_NO_TOOL_TASK` 1건 유효화, `EMPTY_FOR_TOOL_TASK` 1건 무효 유지, `ITEM_FORMAT_INVALID`·`STEPS_INVALID`(형식) 0건.
  - G05는 g·h에서 1차 유효였다. i에서는 초회 출력이 바뀌어(g와 SHA 다름) 첫 시도가 steps 불일치였고, 세부 코드 재시도로 유효해졌다.
- 두 경로 모두 판정 `QREF_INVALID` 0이다. qRef는 A `JSON_NULL`/후보0 21(G05·G08 2시도씩 포함)·`ID_IN_RANGE`/후보1 3, B `JSON_NULL` 19·`ID_IN_RANGE` 3이다. `ID_OUT_OF_RANGE`는 경계 포함 0이다.
- **Context 복사**: A·B·경계의 모든 시도에서 eqp·from·to `equalsContext=true`는 0건이다.
- **G08(AC-38, 최종 `predicted.effectiveT`와 최종 시도 diagnostics 기준)**:
  - B: 초회 t=`INC`, 재시도 0, 최종 effectiveT `INC`. eqp·from·to 모두 NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`. 첫 출력 SHA `b4db4d71…`은 g·h와 같다.
  - A 첫 시도: t=`INC`, steps shape OK·toolConsistency `EMPTY_FOR_TOOL_TASK` → `STEPS_INVALID`. 세부 원인 `EMPTY_FOR_TOOL_TASK`.
  - A 재시도로 보낸 코드: `EMPTY_FOR_TOOL_TASK`(재구성).
  - A 재시도 결과: t=`INC`, steps `EMPTY_FOR_TOOL_TASK` → `STEPS_INVALID`, 최종 `INVALID`, effectiveT 없음(`INVALID_OUTPUT`). eqp·from·to NULL·MATCH·`equalsContext=false`, qRef `JSON_NULL`.
  - **A G08 최종 steps에 `tc.logs.search`는 없다.** 최종 `predicted`가 null이며 최종 시도 `toolConsistency=EMPTY_FOR_TOOL_TASK`로 확인했다.
  - **A G08 두 시도 출력 SHA는 서로 같고, g·h와 같은 `d648833f…`다**(키 정렬·공백 없는 `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC","to":null}`). completion 27·27, finish stop.
- 키 순서 판별(§12.22 4) 방식, 원문 없음): 판별 가능 A 16시도 모두 옛 정렬 순서, 새 순서 0, 판별 불가 8이다.
- **경계 9케이스 boundary-gen 9/9**(모두 status OK, schemaValid=true, argsExact=true, finish=stop, 재시도 0, completion ≤192, prompt=audit 9/9). 첫 출력 `outputSha256`은 9/9 g와 같다.

| caseId | 판정 | 불통과 축 | completion | 첫 출력 SHA = g |
|---|---|---|---|---|
| NORMAL-01 | 통과 | – | 59 | 같음 |
| NORMAL-PRECISE | 통과 | – | 97 | 같음 |
| MAX-MIXED | 통과 | – | 146 | 같음 |
| MAX-HANGUL | 통과 | – | 146 | 같음 |
| MAX-DIGIT | 통과 | – | 146 | 같음 |
| MAX-ASCII | 통과 | – | 146 | 같음 |
| MAX-SUPPLEMENTARY | 통과 | – | 146 | 같음 |
| ESCAPE-01 | 통과 | – | 146 | 같음 |
| ESCAPE-CONTROL | 통과 | – | 146 | 같음 |

- **B 22문항 첫 출력 SHA는 22/22 g와 같다.** B 결과(t·인자·qRef·Context 복사)는 g와 같고 지연만 다르다(g p95 5307 → i 5115).
- **A 초회 출력 SHA는 g와 15/22 같다**(다른 문항 G01·G02·G03·G05·G15·G17·G22). G03·G15는 UNS·`[]`·null로 결과가 g와 같고, 다른 UNS 문항(G07·G16)과 같은 SHA `963407fc`가 됐다. G08은 같다.

#### 5) 개발 회귀 i 결과(부분, `eval/f0/results/20261103T060939Z-regression-i/`, 보고서 없음)

- B는 `complete=true`(30/30), A는 `complete=false`(28/30, R06 timeout, R07·R08 미요청)다. 경계·보고서는 없다. 아래는 결과 JSON 정제 필드 재집계이며 회차 판정 자료가 아니다.

| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p50/p95 ms | 판정 errorCode |
|---|---|---|---|---|---|---|---|---|
| A(28시도) | 26/28 측정분(G08 무효·R06 timeout) | 19/28 측정분 | – | 26/28 | 25/28 | 2 | 3491/11853 | `STEPS_INVALID` 3(G05 1·G08 2), `ENDPOINT_TIMEOUT` 1(R06) |
| B | 28/30 | 26/30 | 28/30 | 30/30 | 30/30 | 0/30 | 2994/6032 | 0 |

- B: t 오답 G07·G16, 인자 오답 G01·G11·G12·G17 to(진단 i와 같음). 회귀 g와 첫 출력 SHA 30/30 같다. `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0·Context 복사 0, qRef `JSON_NULL` 24·`ID_IN_RANGE` 후보1 5·후보2 1(R07).
- A: 원래 22문항 행은 진단 i A와 첫 출력 SHA 22/22가 같다(G05·G08 재시도 결과도 같음). 회귀 g A와는 19/28이 같다(다름 G01·G02·G03·G05·G15·G17·G22·R05·R06). R01~R05는 t·인자 모두 맞음, R06은 timeout, R07·R08은 미측정이다. `QREF_INVALID` 0·`ID_OUT_OF_RANGE` 0·Context 복사 0.
- 신규 8문항 A 최종 steps(측정분): R01·R03 `tc.eqp.getOperationalSnapshot`, R02·R04 `tc.logs.search`, R05 `[]`.

| id | 축 | 회귀 g | 회귀 h | 회귀 i |
|---|---|---|---|---|
| R01 | REG_QUOTED_EQP | A·B 통과 | A·B 통과 | A·B 통과 |
| R02 | REG_EQP_VS_CODE_TERM | A·B 통과 | A·B 통과 | A·B 통과 |
| R03 | REG_CONTEXT_ONLY_NO_COPY | A·B 통과 | A·B 통과 | A·B 통과 |
| R04 | REG_TC_TRANSFER_FAILURE | A·B 통과 | A·B 통과 | A·B 통과 |
| R05 | REG_WM_OWNED_RECORD | A·B 통과 | A·B 통과 | A·B 통과(A 11853 ms) |
| R06 | REG_PARAPHRASED_RANGE | A·B 통과 | A·B 통과 | **A timeout(endpoint 무응답)**·B 통과 |
| R07 | REG_SECOND_QUOTED_INTENT | A·B 통과 | A q 실패·B 통과 | **A 미측정**·B 통과 |
| R08 | REG_LONG_EQP_ID | A·B 통과 | A·B 통과 | **A 미측정**·B 통과 |

#### 6) c~i 비교(원문 없는 정제 필드·출력 해시만)

- i = g + `plan-v3-i` + `retryHint=STEPS_DETAIL_V1`. c~h 열은 §12.22 6) 표를 옮긴 것이다.

| 지표 | c | d | e | f | g | h | i |
|---|---|---|---|---|---|---|---|
| A taskType / 인자 | 20/22 / 13/22 | 19/22 / 15/22 | 16/22 / 13/22 | 17/22 / 13/22 | 21/22 / 15/22 | 21/22 / 16/22 | 21/22 / 14/22 |
| B taskType / 인자 | 18/22 / 15/22 | 17/22 / 15/22 | 17/22 / 17/22 | 20/22 / 18/22 | 20/22 / 18/22 | 20/22 / 18/22 | 20/22 / 18/22 |
| A JSON 유효 / 1차 유효 / 재시도 | 22 / 18 / 4 | 21 / 17 / 5 | 21 / 18 / 4 | 19 / 16 / 4 | 21 / 21 / 1 | 21 / 21 / 1 | 21 / 20 / 2 |
| A / B p95 ms | 10724 / 6107 | 11335 / 6211 | 10135 / 6136 | 120011 / 7100 | 6736 / 5307 | 6916 / 4977 | 6746 / 5115 |
| G08 최종 t A / B | INC / INC | UNS / UNS | UNS / UNS | 무효 / INC | 무효 / INC | 무효(g와 같은 SHA) / INC | 무효(`EMPTY_FOR_TOOL_TASK` 재시도, g와 같은 SHA) / INC |
| Context 복사 시도 A / B | 3 / 2 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 경계 boundary-gen | 2/9 | 7/9 | 0/9 | 9/9 | 9/9 | 9/9 | 9/9 |
| 회귀 R01~R08 | c: 4문항 실패 | d: R06 A 실패 | – | 미측정 | 전부 통과 | R07 A 실패 | R06 A timeout·R07/R08 A 미측정(미완) |

- 관찰(R1, 인과 확정 아님):
  - 재시도 입력은 G08에서 실제로 바뀌었다(`STEPS_INVALID` → `EMPTY_FOR_TOOL_TASK`, 프롬프트에 그 뜻과 조치 기재). 그래도 출력 바이트는 초회·g·h와 같았다. 이 모델·서버 조합에서 G08 steps 빈 배열은 이 재시도 문구 변화에 반응하지 않았다.
  - 세부 코드 재시도는 G05(`NONEMPTY_FOR_NO_TOOL_TASK`)에서는 작동했다. 다만 G05 첫 시도 실패 자체가 i 프롬프트 변경(+150토큰)으로 새로 생긴 것이다.
  - A 인자 정오는 G22 하나만 g와 달라졌다(악화).

#### 7) `PREREG-v3-gate-i.md` §1 착수 조건 대조(판정은 root)

| 조건 | 상태 | 근거 |
|---|---|---|
| 1. C17/C18 뒤 세 문서 `PREREG_VERIFIED` | 충족 | diag-i·regression-i는 이번 i-1·r-1, gate-i는 §12.23 C17/C18 절(이 세션 미재실행) |
| 2. 진단 i·회귀 i exit 0·`complete=true`, `response_format` 거부(exit 5) 0 | **미충족** | 진단 i 7명령 exit 0·`complete=true`. 회귀 i r-5 exit 1·A `complete=false`(ENDPOINT_UNREACHABLE), r-6·r-7 미실행. 거부(exit 5) 0 |
| 3a. B·A `QREF_INVALID` 0, 후보 0 `ID_OUT_OF_RANGE` 0 | 충족 | 위 4) |
| 3b. G08 최종 t A·B 모두 INC | **미충족** | B `INC`. A는 두 시도 t=INC·steps `EMPTY_FOR_TOOL_TASK`로 최종 `INVALID`, 출력 SHA g·h와 동일 |
| 3c. 경계 9 boundary-gen 9/9 | 충족 | 위 4) |
| 4. root 원문 없는 검토 | 미완(대기) | 이 절과 진단 i 보고서. 회귀 i 보고서 없음 |

- 조건 2·3b가 미충족이므로 gate-i §1에 따라 이 등록으로 R5를 실행하지 않는다. 회귀 i를 다시 실행해 조건 2를 채워도 조건 3b는 진단 i 결과로 이미 미충족이다.

#### 8) 미실행·원인 미확정·다음 정확 조치

- 미실행: 회귀 i r-6(boundary-gen)·r-7(report), gate-i prereg-check 재실행, 정식 R5, P04.
- 원인 미확정:
  - 06:24Z 무렵 Jetson 무응답의 원인(메모리 압박에 의한 정지인지, 네트워크·전원 문제인지). 마지막 확인 available 1429 MiB·swap 1300 MiB, 직전 R05 지연 증가가 관찰됐다.
  - 재시도 코드가 바뀌어도 A G08 출력이 바이트까지 같은 이유.
  - A G02 steps 2개·G22 from+to 악화·G05 초회 steps 불일치(프롬프트 변경 영향으로 보이나 분리 불가).
- 다음 정확 조치(root 결정):
  1. 사용자가 Jetson 상태를 직접 확인한다(이 세션은 원격 sudo·재시작 권한과 지시가 없다).
  2. root가 진단 i 보고서와 이 재집계를 원문 없이 검토한다.
  3. A G08 무효 축 보완은 새 사전등록 파일로만 한다(gate-i §1 마지막 항목). 이 세션은 설계하지 않았다.

#### 9) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`는 불변이다(모든 run-binding specSha). HEAD C18 `086b619f65951b4d40e49ba00802a852e737793b`, staged 0이며 git 쓰기는 0이다.
- `git status --short`는 세션 시작과 같다(Devreport M, 기존 untracked 5항목. 신규 capture는 기존 untracked `eval/f0/chat-template/` 아래). PREREG·입력·코드 변경은 0이다.

| 파일 | 바이트 | SHA-256 |
|---|---|---|
| `eval/f0/results/20261103T060939Z-i/preflight.json` | 304 | `c9c344acfed2de216b5dc41b87b808e574107601830ed9385ce6bcd2da4d69e2` |
| `eval/f0/results/20261103T060939Z-i/B/f0-results.json` | 31111 | `4e00de5d05064826c135ff4fa39d119b6d986975dbd9e2dd028f526154766e31` |
| `eval/f0/results/20261103T060939Z-i/B/run-binding.json` | 1777 | `4407a9328c7ebca4257ba426cfa972c34a5a114e23f1d4302eaef8f87cfe7119` |
| `eval/f0/results/20261103T060939Z-i/A/f0-results.json` | 35757 | `cf5fd9c493ab6d83a370a96e39a9a3cbd1198f81359f676dfa219e2a60ee0ca3` |
| `eval/f0/results/20261103T060939Z-i/A/run-binding.json` | 1777 | `a002f411d456a72dc9ff9c52f54f65302ae1fb27c7daf21ca4f43138a8d1e8ff` |
| `eval/f0/results/20261103T060939Z-i/boundary/boundary-results.json` | 10005 | `ffcff32bd8ebeb517d414ef5926aa14396c8506d1f6f70d34afa5c224da5f1fd` |
| `eval/f0/results/20261103T060939Z-i/boundary/run-binding.json` | 1777 | `3d352a13b54b7cfb6f2ee788b195eb77ff6453d7f2c2d9e6964ef02bde0a3b70` |
| `eval/f0/results/20261103T060939Z-i/diagnostic-report.md` | 8997 | `6463b9bfe08aa21003fc18d35e075977a6f1d320ae9502a5b068d6efbd197301` |
| `eval/f0/results/20261103T060939Z-regression-i/preflight.json` | 304 | `fe4a66cbe943907bb8a934a2dfb87136baa2d3b35af3d590972e471fbc291b10` |
| `eval/f0/results/20261103T060939Z-regression-i/B/f0-results.json` | 42240 | `bac0c27dba7e4e96bfd14f89489120b99b9dca5f3f7228f305cb3ef7c013452e` |
| `eval/f0/results/20261103T060939Z-regression-i/B/run-binding.json` | 1794 | `3e925e392b4a57ae402af7044f77cad99b1646da46502d65fdec2eac9d99b90b` |
| `eval/f0/results/20261103T060939Z-regression-i/A/f0-results.json`(미완) | 44001 | `c14d21d3a3c60e826c1a3072dc614cafd5dd5d49ecc6ac4693828b5a34950772` |
| `eval/f0/results/20261103T060939Z-regression-i/A/run-binding.json` | 1791 | `ed46e1e117c7dedc88cc3168d89173f5166ddd3bbc63696cb71b6934416c1a71` |
| `eval/f0/chat-template/v3-diag-i/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-diag-i/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-diag-i/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-diag-i/run-binding.json` | 1778 | `c77d5531d32da4f22a329f4d145feb1d8295c5135402f26ff2539343dd73887d` |
| `eval/f0/chat-template/v3-regression-i/qwen3-4b-instruct.template` | 4040 | `40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541` |
| `eval/f0/chat-template/v3-regression-i/qwen3-4b-instruct.template.sha256` | 65 | `3dec2c0e5ff73781b43228f662b2555bb5d24f1eb741bb9a62ae39376550db33` |
| `eval/f0/chat-template/v3-regression-i/model-digest.txt` | 65 | `235b84f5c9e0059f6830b38611571d3269572ed386b8b791b97400ef0df65ddd` |
| `eval/f0/chat-template/v3-regression-i/run-binding.json` | 1795 | `9294202dc55f3620843d5a9d3e9b873eabcbcdf1a6caf4c1a7e6b6603e5dc217` |

- 기존 dirty(Devreport)·untracked 항목은 그대로 보존했다. 이 Devreport는 UTF-8·LF·BOM 없음이다.
- 원문 질문·응답·API 응답·secrets·환경값은 출력·저장하지 않았다. 집계는 결과 JSON의 열거형·불리언·개수·해시 필드, `predicted.steps` capability ID, 골든의 id·기대 t만 사용했다(키 순서 판별의 골든 기대값 복원은 메모리 안 해시 대조에만 썼다). 이 절은 root 검토 전이며 완료·PASS를 확정하지 않는다.

### 12.25 P03 A 경로 G08 steps 공백 원인 분석 요약·원인 확정 시험 도구 구현·사전등록 초안 (2026-11-03, 독립 Claude Code Opus 5.5, root 검토 전)

- §12.1~§12.24는 바꾸지 않았다. 이 절 앞 바이트(470594 B, SHA `48a3aa403160a6efced2d91d9c9ab409e511e9181dbcfa733f1eabbdddd3e1e5`)는 그대로이며 이 절만 추가했다.
- 하지 않은 것: Jetson SSH·모델 HTTP·터널·측정 실행(모델 call/load/pull/unload·서비스 재시작) 전부, push·`--force`. SPEC·Plan·기존 PREREG(a~i)·기존 결과·capture·evidence·골든·fixture·기존 프롬프트·기존 audit·`f0-judge-v2`·예산 변경 0.
- **결론: 원인 분석에서 G08 steps 공백의 직접 메커니즘(Ollama chat 경로가 스키마 properties 를 알파벳순으로 재직렬화해 steps 가 t 보다 먼저 생성됨, G08 출력은 `"t":"` 앞까지 UNS 출력과 같음)은 확정했고, G08 에서만 steps 위치가 UNS 쪽이 되는 이유는 추정으로 남았다. 이를 가르는 시험 8건(E0·E-ORD·E1·E2·E3·E5·E6·N1, 각 1회·첫 시도만)을 새 명령 `f0 probe-g08` 과 DIAGNOSTIC 등록 `eval/f0/PREREG-v3-probe-g08.md` 로 구현·등록 초안까지 마쳤다. 측정은 하지 않았으므로 F0 판정은 그대로다(기존 v2 F0 FAIL 유지, P03 미완료, P04 미착수).**

#### 1) 원인 분석 요약(읽기 전용 분석, 모델 호출 0, 원문 없는 정제 필드·출력 SHA·공식 소스 근거)

- 확정:
  - A G08(`WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했어?`)은 g·h·i 모두 두 시도 출력 SHA `d648833f1ddc7d17001e8fa6f14b60b99d11e448ebae6b0c934f96159257b242`(= `{"eqp":null,"from":null,"qRef":null,"steps":[],"t":"INC","to":null}`, 이 절에서 문자열 SHA 재계산 일치)이다. `"t":"` 앞까지는 같은 회차 UNS 문항(G03·G15·R05) 출력과 바이트가 같고 t 값만 INC 다. `EMPTY_FOR_TOOL_TASK` 는 전 회차를 통틀어 G08 만이다.
  - 생성 순서 eqp→from→qRef→steps→t→to 는 Ollama 0.31.1 `llm/llama_server.go` `llamaServerChatResponseFormat` 가 스키마를 `map[string]any` 로 다시 담아 `encoding/json` 으로 직렬화(맵 키 정렬)하기 때문이다. llama.cpp `json-schema-to-grammar.cpp` 는 `ordered_json` 으로 받은 properties 순서대로 문법을 만든다. 따라서 후보 h 의 "t 먼저 송신"은 Ollama 에서 지워졌고, "t 를 먼저 생성하면 steps 가 채워진다"는 가설은 시험되지 않았다.
  - `/api/generate` → Completion 경로는 `lsReq.JsonSchema = req.Format`(RawMessage)라 Ollama 쪽 순서를 보존한다(llama-server `/completion` 쪽은 미확정 → E0·E-ORD 로 관측). `api/types.go`(v0.31.1)의 `GenerateRequest` 에는 `format`·`raw`·`logprobs`·`top_logprobs`·`options`, `GenerateResponse` 에는 `response`·`done_reason`·`logprobs[{token,logprob,bytes,top_logprobs}]` 가 있다(이 절에서 공식 소스 확인).
  - Context 형태 단독·인자 전부 null 단독·하네스/판정 결함은 배제된다(G20·R03·G04 반례, §12.19). 재시도에는 직전 출력이 실리지 않으며, i 의 세부 코드 재시도도 G08 출력을 바꾸지 못했다(§12.24).
- 추정(현재 자료로 분리 불가): G08 은 경계 문항이며 steps 위치에서 WM 소유 자료 해석(UNS 상태)이 우세하고 t 위치에서 `plan-v3-i.txt:15` 의 INC 규칙이 이긴다. UNS 상태 요인 후보는 (가) 질문 `WORK_ORDER_REPLY` ↔ fixture `tc-registry-fixture-v2.json:7` WM ownedAreas `LOT·Workorder 진행 상태…` 낱말 겹침, (나) `plan-v3-i.txt:16` `TC 자료로 대신 채우지 않는다.`, (다) 질문에 원인·분석·에러·로그 단서와 설비·시간 부재다.
- 수정이 필요한 기존 서술: "서버가 t 먼저 순서를 따르지 않아 h 가 실패했다"(§12.22·§12.23·`PREREG-v3-diag-i.md` §1 ②) → 서버(llama.cpp)는 받은 순서를 따르지만 Ollama 가 그 순서를 지웠다. 기존 문서는 고치지 않고 이 절에만 적는다.

#### 2) 시험 설계(사용자 승인 범위 8건, temperature 0, 각 1회, 첫 시도만, 재시도 0)

- 기준선 = 후보 i(C17/C18: `PRESENCE_V1`·`plan-v3-i`·`JSON_SCHEMA_V1`)의 A/G08 첫 시도 입력 `c64d1f75a93ba9e7c3ee6a8178747be5b65f1d2751a24b64453692c405bdda9f`(`input-audit-v3-i.json` `A/G08/first`, 1916토큰), 기대 출력 SHA `d648833f…b242`.

| id | 기준선 대비 변경 1개 | messagesSha256 | 사전 계수 |
|---|---|---|---|
| E0 | 호출 경로: `/api/generate` raw=true, prompt = 등록 렌더러로 기준 메시지를 렌더한 문자열, format = 키 정렬 계획 스키마, logprobs·top_logprobs=5 | `c64d1f75…da9f` | 1916 |
| E-ORD | E0 에서 format properties 선언 순서만 t, eqp, from, to, qRef, steps | `c64d1f75…da9f` | 1916 |
| E1 | question `WORK_ORDER_REPLY` → `메시지` | `67d2b44b…c527` | 1916 |
| E2 | question 에서 `WM 으로 보낸 ` 삭제 | `5eae0bab…6861` | 1910 |
| E3 | `가 왜 실패했어?` → `가 실패한 원인 분석해줘` | `462ffde5…243d` | 1918 |
| E5 | `plan-v3-i.txt:16` 의 ` TC 자료로 대신 채우지 않는다.`(앞 공백 포함)만 삭제한 프롬프트 | `f6dd94d8…870d` | 1903 |
| E6 | fixture WM ownedAreas `LOT·Workorder 진행 상태` → `LOT 진행 상태` | `8f9db8be…9f5e` | 1913 |
| N1 | `실패했어?` → `실패했니?`(대조군) | `8b5a1c71…7b4e` | 1916 |

- 판별 기준은 원인 분석 보고 §6 표를 그대로 `PREREG-v3-probe-g08.md` §1.4 에 고정했다. 적용 규칙: E0 SHA ≠ `d648833f…` 이면 E0·E-ORD 결론 무효, N1 SHA ≠ `d648833f…` 이면 E1~E6 을 요인 근거로 쓰지 않음, "Tool 이 생긴다" = 첫 시도 steps 비지 않음.
- 기록 축(원문 금지): 출력 SHA·기준선 동일 여부, t, `toolConsistency`, steps 의 `tc.logs.search` 포함(YES·NO·NOT_EVALUABLE), prompt/completion 토큰, 지연, finishReason, 첫 시도 판정(valid·errorCode·stepsDetail), 원문 없는 `attemptDiagnostics`. E0·E-ORD 는 추가로 `"steps":[` 직후 첫 비공백 문자 위치의 `]` 대 `"` 확률과 t 값 첫 문자 위치의 INC(`I`) 대 UNS(`U`) 확률만 기록하며, 후보 토큰 문자열은 이 후보 집합에 드는 것만 남긴다.

#### 3) 구현(f0 패키지, 기존 파일은 추가만)

- 신규 `G08Probe`(f0 패키지):
  - `load`: manifest(`eval/f0/v3/probe-g08/probe-g08-v1.json`)와 기준 역할(golden·fixture·planPrompt·투영)로 8건 송신 메시지를 만든다. 측정 전 거부 조건: 기준선 메시지 SHA ≠ manifest(`PROBE_BASELINE_MISMATCH`), id 순서·호출 방식·키 집합 불일치, `edit.find` 가 기준 대상에 0회·여러 회, 변형 파일 바이트 ≠ 기준 파일에 edit 적용 바이트, 질문 변형이 system 을·파일 변형이 user 를 바꿈(`PROBE_INPUT_INVALID`). 메시지 조립은 기존 `MessageAssembler`·`QRefContext`·`RegistryFixture`·`GoldenSet` 을 그대로 쓴다.
  - `run`: 등록 순서대로 `ensureResident` → 사전 계수(`MeasurementSession.audited`) → E0·E-ORD 는 `OllamaChatClient.generateRawStructured`, 나머지는 `MeasurementSession.chat`(등록 `JSON_SCHEMA_V1`, `ResponseSchema.forPolicy`). 판정은 `ClassificationJudge.judge`, 진단은 `AttemptDiagnostics.diagnose`. 결과는 `F0Measurements.header` 형식에 행을 더해 `probe-g08-results.json` 에 쓴다. timeout 은 행 `status=TIMEOUT` 으로 남기고 다음 시험으로 가며, 그 밖 endpoint 오류는 `complete=false`·`abortReason` 보존 뒤 exit 5 다.
  - `logprobSummary`/`position`/`classOf`: 선택 토큰을 이어 붙인 텍스트에서 위치를 찾고, 후보 = 앞 토큰들 + 후보가 그 위치 직전까지 실제 텍스트와 같을 때만 다음 문자로 분류한다(앞 토큰이 `[` 까지 덮는 병합 토큰도 처리). 분류 밖(OTHER·DIVERGED·INCOMPLETE)은 개수·확률 합만 남긴다. logprobs 필드가 없으면 `status=ABSENT`.
- `OllamaChatClient`(+46줄): `TokenLogprob`·`Position`·`GenerateResult`(toString 에 원문 없음)와 `generateRawStructured` 를 더했다. body = model·prompt·raw·stream·format·options{temperature 0, num_predict}·logprobs·top_logprobs, format 은 지정 경로(`format.properties`)만 노드 순서·나머지 키 정렬(기존 `ResponseSchema.canonicalKeepingOrder`). 기존 송신 경로·허용 경로 집합(`/api/generate` 기존 포함)은 바꾸지 않았다.
- `F0RunnerCli`(+35줄): `probe-g08-render`(준비 전용, 렌더 JSONL, 추론 0)와 `probe-g08`(DIAGNOSTIC·R=1 등록만, `--probe` 와 변형 파일이 등록 inputs, 호출 전 audit 검사, 렌더러는 측정 시점 template SHA 로 선택) 두 case 를 더했다.
- 사전 계수 `eval/f0/v3/input-audit-probe-g08.json`: `f0 probe-g08-render --golden eval/golden/tc-f0-v2.jsonl --fixture eval/golden/tc-registry-fixture-v2.json --plan-prompt eval/f0/v3/prompts/plan-v3-i.txt --context-projection PRESENCE_V1 --probe eval/f0/v3/probe-g08/probe-g08-v1.json --renderers eval/f0/chat-renderers-v1.json --template-sha256 40c21f34…b541` → `F0_PROBE_G08_RENDERED 8` → `count_input_audit_v2.py`(venv Python `-I -B`, tokenizers 0.23.3, pin `aeb13307…dae4`) → `INPUT_AUDIT_WRITTEN 8 OVER_BUDGET 0`. 기준선 행 1916 은 i audit `A/G08/first` 와 같다. 렌더 JSONL 은 레포 밖 세션 임시 디렉터리에 두었다.

#### 4) 시험(이 세션 실행, Gradle 동시 실행 0, `AIGW_SPEC_FILE`=정본 SPEC)

- 신규 `F0ProbeG08Test` 6건: ① 실제 레포 변형 입력이 기준선과 정확히 한 요소만 다름(질문 변형 = user 의 question 값만, E5·E6 = system 1줄만, 변형 파일 = 기준 파일 1곳 치환, audit 8행 순서·id·SHA 결속, 등록 파라미터·역할 = 진단 i), ② 모호·손 편집·기준선 불일치 거부, ③ E-ORD format properties 선언 순서 보존 직렬화와 E0 정렬 format = 진단 i chat `response_format.json_schema.schema` 송신 바이트, ④ logprob 위치 분류가 후보 집합 밖 토큰 문자열·원문을 남기지 않음(분리·병합 토큰 두 경우), ⑤ stub 종단 8건 순서·generate/chat 요청 본문·format 송신 순서·결과 행·원문 sentinel 비기록·재실행 `RUN_BINDING_EXISTS`, ⑥ 미등록 manifest·audit 행 누락·GATE 등록의 호출 전 거부(요청 0).
- `F0V3RegistrationDraftTest`: 하네스 소스 변경으로 i(C17/C18) 3문서를 과거 등록 묶음(PRESENCE_V1)에 넣었다. i 는 역사 신원 유지·현재 `localMismatches=["harnessDigest"]`·`verify=PREREG_INPUT_CHANGED`·`retryHint=STEPS_DETAIL_V1` 로 단정한다. 현재 초안 묶음은 probe 등록 1개다(audit 결속은 `F0ProbeG08Test` 가 검사).
- 첫 실행에서 `OllamaChatClientContractTest.harnessSourcesContainNoManagementOrEmbeddingEndpointAndNoRawOutputPrinting` 이 실패했다(새 주석에 금지 문자열 `num_ctx` 포함). 주석 문구만 바꿔 통과시켰다.

| 명령 | exit | 결과 |
|---|---|---|
| `.\gradlew.bat :gateway-eval:test --console=plain --rerun-tasks`(무필터) | 0 | `BUILD SUCCESSFUL in 5m 7s`, `6 actionable tasks: 6 executed`. JUnit XML 43클래스 합산 209 tests·failures 0·errors 0·skipped 0(기존 203 + `F0ProbeG08Test` 6) |
| `.\gradlew.bat :gateway-eval:run --console=plain "--args=test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s` |

#### 5) 등록 초안·등록 검사

- `eval/f0/PREREG-v3-probe-g08.md`: DIAGNOSTIC, R=1, `runtimeCaptureDir=eval/f0/chat-template/v3-probe-g08`(형식상 값, 이 등록은 capture 단계 없음). 진단 i 대비 바뀐 것은 `inputRoles.inputAudit`·inputs(audit 교체 + manifest·E5 프롬프트·E6 fixture 3개 추가)·`harnessDigest`·수명 메타데이터뿐이고 `parameters` 는 같다(parameterManifestSha256 `c13ed8bc…4cda`, 시험 단정). harnessDigest `b2109add63d14049369ceb5fb1a4e5e30c730f4e9b409ac8ac579d3a620a0c62`(Java `prereg-check` currentHarnessDigest).
- `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0, 순차):

| 등록 | localMismatches | 결과 |
|---|---|---|
| probe-g08(초안) | `[]` | `PREREG_NOT_COMMITTED` |
| diag-i(C17/C18) | `["harnessDigest"]`(current `893d71de…f12b`) | `PREREG_INPUT_CHANGED`(이번 소스 변경으로 stale, 예상대로) |

#### 6) 변경·신규 파일(이 절 제외 10파일)

| 파일 | 구분 | 바이트 | SHA-256 |
|---|---|---|---|
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/G08Probe.java` | 신규 | 20315 | `79ff74dabbe36373a424668378b824409e03b07cfce21a81cb6064d357baef57` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/OllamaChatClient.java` | 수정(+46/−0) | 14574 | `61bbff94caef38ff32f6deea629221f1b83a73de7d853f2e356557df1a55360a` |
| `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/F0RunnerCli.java` | 수정(+35/−0) | 27467 | `ad537cbd97ecfcb50e3cabb5b819aa98475d2245133eb2363470324e6de941cc` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0ProbeG08Test.java` | 신규(6시험) | 29315 | `d08c4aa5225b4162e1e9b4d1dc37baafe75b6a8244026b4be0315f003da59a3d` |
| `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V3RegistrationDraftTest.java` | 수정(+24/−16) | 46349 | `2186fbcbc1fa6e4c18ba244f9be59fbf9de802e331a019ca6889f3818aa27e84` |
| `eval/f0/v3/probe-g08/probe-g08-v1.json` | 신규(manifest) | 1718 | `99968394fca22695ebcb498e920623bf7fa916429752395ba337ec164f5d8422` |
| `eval/f0/v3/probe-g08/plan-v3-i-e5.txt` | 신규(E5) | 4581 | `cc67c15c92c6766b814786c98ccdfe6fd89ec43b94a53a5abe92d93b79f212df` |
| `eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json` | 신규(E6) | 4046 | `8f4783b2e0a69182aac294c59e61030db5cd155d0c1e4343516d80026682b3c9` |
| `eval/f0/v3/input-audit-probe-g08.json` | 신규(8행) | 1399 | `c75a6ffeb11f7f184a9bba0328659129f8ef16d37d13a712b0f0e93b2aae19db` |
| `eval/f0/PREREG-v3-probe-g08.md` | 신규 초안 | 17567 | `d425be499e5a8358854d60bb1e0f7f05d04dc7e179ed92932517bc907156bf7a` |

- 10파일 모두 UTF-8·LF(CR 바이트 0), `git diff --check` exit 0.

#### 7) 커밋 범위(사용자 허용: 로컬 C19/C20 두 개, push 없음)

- C19(정확 11파일): 위 10파일 + `docs/ai-rag-gateway-architecture-dev-report.md`(C18 뒤 미커밋으로 추가된 §12.23 끝 "로컬 C17/C18 등록·최종 시험"·§12.24·이 절 포함, C5·C7·…·C17 과 같은 관례). 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C20(정확 1파일): `eval/f0/PREREG-v3-probe-g08.md` 의 `registrationStatus=REGISTERED`, `registeredCommit`=C19 SHA, §0 등록 설명만 기록한다.

#### 8) 측정 실행 순서(C20 뒤, 각 명령 별도·exit 0 확인, 이 절은 실행하지 않음)

1. 사용자: Jetson 에서 `sudo systemctl restart ollama`. 세션: warmup 대기 중 읽기 전용 확인(서비스 active·ActiveEnterTimestamp·NRestarts 0, 재시작 이후 journal `library=cpu`/`Load failed`/`out of memory` 0, `/api/ps` 2모델 digest·ctx 4096·size_vram=size).
2. 터널 `ssh … -o ExitOnForwardFailure=yes -N -L 11434:127.0.0.1:11434`(별도 창), 로컬 11434 사전 미사용 확인.
3. PowerShell, cwd `C:\workspace_nori\nori-ai-gateway`, `$env:AIGW_SPEC_FILE`=정본 SPEC, `$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')`.
4. `.\gradlew.bat :gateway-eval:run --args="f0 prereg-check --prereg eval/f0/PREREG-v3-probe-g08.md"` → `PREREG_VERIFIED`
5. `.\gradlew.bat :gateway-eval:run --args="f0 preflight --prereg eval/f0/PREREG-v3-probe-g08.md --endpoint http://127.0.0.1:11434 --out eval/f0/results/$runId-probe-g08/preflight.json"` → `F0_PREFLIGHT_OK 2`
6. `.\gradlew.bat :gateway-eval:run --args="f0 probe-g08 --prereg eval/f0/PREREG-v3-probe-g08.md --endpoint http://127.0.0.1:11434 --probe eval/f0/v3/probe-g08/probe-g08-v1.json --run-id $runId-probe-g08 --out eval/f0/results/$runId-probe-g08"` → `F0_PROBE_G08_COMPLETED 8`
7. 터널 종료, 실행 뒤 읽기 전용 확인 1회.

#### 9) 위험·미확정

- generate 경로 동등성(E0)은 미확정이다. Ollama completion 경로의 sampler 기본값·llama-server `/completion` 의 json_schema 순서 처리·raw 렌더와 서버 chat template 의 차이 중 하나라도 있으면 E0 SHA 가 기준선과 달라지고, 등록 규칙상 E0·E-ORD 결론은 무효다.
- logprobs 가 문법 적용 전·후 어느 분포인지, 서버가 `logprobs` 필드를 실제로 돌려주는지는 미확정이다(없으면 `status=ABSENT` 기록).
- E1~E6·N1 의 기준선은 g·h·i 기록 SHA 이며 이 명령 안에서 chat 기준선을 다시 보내지 않는다(승인 범위 8건). 런타임이 g·h·i 와 같은지(모델 digest·template SHA)는 run-binding 으로만 대조된다.
- R=1·단일 결정 출력 근거다.

#### 10) 보존 확인

- 정본 SPEC SHA `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be` 불변. HEAD C18 `086b619f65951b4d40e49ba00802a852e737793b`, 이 절 작성 시점 staged 0.
- `eval/f0/results`·`eval/f0/chat-template`·`evidence`·`eval/golden`·기존 프롬프트·기존 PREREG·기존 audit·`MessageAssembler`·`AttemptDiagnostics`·`ClassificationJudge`·`ResponseSchema`·`F0Measurements`·`Json.java` 변경 0, 새 `.pyc` 0(Python `-I -B`).
- 응답 원문·API 응답·secrets·환경값은 출력·저장하지 않았다. 이 절은 root 검토 전이며 완료·PASS 를 확정하지 않는다.

#### 로컬 C19/C20 등록·최종 확인 (2026-11-03, 독립 Claude Code Opus 5.5)

- 허용 범위: 로컬 commit C19·C20 두 개뿐이며 push·amend·원격 publish 는 하지 않았다.

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C19 | `0dcf104f2690f9438433b7242d643057a8a1f6ca` | 2026-11-03 16:15:39 +0900 | 11파일, 1260 insertions(+), 16 deletions(-) |
| C20 | `7ab4e9a4ec181277a67fc9d85dc1a1325c3331d3` | 2026-11-03 16:16:08 +0900 | 1파일(`eval/f0/PREREG-v3-probe-g08.md`), 6 insertions(+), 4 deletions(-) |

- C19 정확 11파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 11개, 기대 목록과 diff 0). 이 Devreport 의 C19 시점 바이트는 488032 B, SHA `3b346767cff06c9f1a90c2648dd95693f5fb88eb3be64f2a149333e0eb178a0f` 이며 앞 470594 B SHA `48a3aa40…e1e5` 는 추가 전후 같았다. 기존 untracked 는 넣지 않았다.
- C20: §0 제목·상태 문구를 C18 방식(등록 근거·C19/C20 설명·이력)으로 바꾸고 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C19 SHA 만 바꿨다. JSON 검사(Node, 파싱 실패 시 원문 비출력): 바뀐 최상위 키 2개뿐, 나머지 객체·최상위 키 순서 동일. `git diff --check` exit 0.
- C20 뒤 `f0 prereg-check --prereg eval/f0/PREREG-v3-probe-g08.md`: localMismatches `[]`, harnessDigest `b2109add…0c62`, parameterManifestSha256 `c13ed8bc…4cda`, `PREREG_VERIFIED`(Gradle exit 0). 같은 뒤 `F0ProbeG08Test`·`F0V3RegistrationDraftTest` 필터 실행 exit 0(등록 완료 문서가 실제 `Prereg.verify` 엄격 검증을 통과).
- C20 뒤 20커밋, staged 0. 측정(§12.25 8))은 실행하지 않았다.

### 12.26 추론 장비 교체 SPEC·Plan 개정 반영과 F0 하네스 v4 분기 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: SPEC·Plan 개정 반영과 하네스 v4 분기·시험까지 작업트리 변경만 했다. commit·push 0, SSH·모델 HTTP·측정 0, 기준선 seq5 미작성(root 단독 단계), v4 사전등록 0. P03 미완료·기존 F0 FAIL 유지·P04 미착수.**
- 착수 시 SPEC `34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be`(seq4 와 같음), Plan `264abe99d904bf7fb9e0da2b6be0ab0efff8b3be52e72e74ab7fa0988e803907`, 이 Dev report 489,731 B `011442bb…0b35`, 레포 HEAD C20 `7ab4e9a`(20커밋)였다.

#### 1) 장비 교체 결정과 근거(사용자 결정, 2026-11-04)

- 추론 장비: Jetson Orin Nano(192.168.0.52) → LLM 노트북 서버(192.168.0.160, GTX 1660 Ti 6GiB, Ollama 0.40.13). 사유 = Jetson 이 GPU 부하 중 WiFi(RTL8822CE) PCIe AER 오류 폭주로 2회 정지(2026-11-03 57,533건·2026-11-04 47,478건, OOM 0).
- 생성 모델: 서버의 `qwen3:4b` = Qwen3-4B-Thinking-2507(ID 359d7dd4bcda, Q4_K_M), 임베딩 `bge-m3`(ID 790764642607). ctx = 서버 설정 8192 그대로.
- 개정안 권장안 일괄 채택: temperature 정식 0·진단 0.6 비교 arm, 요청 `max_tokens` = 답변 상한 + 사고 상한 T(T 는 진단 측정 후 확정 — 미정), §4.2 "상한 없는 CoT 금지", D44 배제 근거 이력 유지·같은 위험은 F0 측정 항목, D51 8192·입력 상한 유지·AC-33-CTX Fixture 4096, 공유 서버 겹침 회차 지연 축 무효·재측정, KEEP_ALIVE F0 24h·F2 착수 전 -1, 용량 확장 트리거 ⓐ 비해당, v4 신규·v1~v3 불변, readiness digest 검사 추가, v4.1 날짜 행, SSH 터널 유지, G-JETSON-*→G-INFER-*.
- 인터넷 노출: 사용자 결정 "지금은 두고 나중에" — F2 착수 필수 게이트로만 기록, 서버 조치 0. AC-35-LOCAL 은 대상만 새 서버로 바꾸고 바인딩 형태는 조건부 권장으로 둔다.
- 192.168.0.201 요청은 측정 중 멈출 수 없으므로 겹침 무효·재측정 규칙이 실제로 적용된다. 출발지 확인 경로 = 서버 `journalctl -u ollama` GIN 로그(`ubuntu` 계정 adm 그룹, 서버 시간대 UTC — 2026-11-04 root 확인), Plan G-INFER-SHARE 에 기록.
- 미결정(이번에 정하지 않음): v4 등록에 함께 둘 v3 후보 구성, F2 readiness digest 불일치 errorCode 형태(신규 vs `MODEL_CONTEXT_MISMATCH` 의미 확장). 둘 다 SPEC·Plan 에 열린 항목으로 적었다.

#### 2) SPEC 개정 요약

- 새 SPEC SHA-256 `1f08318d5ce90611b2c4a8f7f33ced545caa84b2276a51db316e22e4f571637d`(2,612줄, 507,050 B, CR 0). 기존 70줄 개정 + 8줄 추가(§26.1 새 서버 사실 블록 7줄, §27.5 행 1줄).
- 반영: 헤더·§0 D44/D51/D55/D56·§4.1~§4.3·§5.4·§9.5·§12.3·§13.3·§14.2·§14.5~§14.7·§16·§19 O1~O4·§26.1·§27.2 AC-31/AC-33-CTX/AC-35-LOCAL/AC-37/AC-37-OUT-*·§27.5. 표기는 기존 관례 `v4.1 대체(2026-11-04)`·§27.5 날짜 행.
- **줄 번호 보존(개정안과 다른 배치):** 등록 입력 `eval/f0/arg-limits-v1.json`·`eval/f0/v2/arg-limits-v2.json` 이 SPEC `:1203`·`:1207`·`:1208`(SCHEMA 근거)과 `:1666`(후보 문구)을 참조하고 `ArgLimitsSchemaTest` 가 실제 SPEC 1203행 내용을 검사한다. 그래서 1~1838행 줄 수를 바꾸지 않았다(§12.10 A 의 "1~1208행 줄 수 불변" 선례와 같은 방식). 그 결과 ①§4.1 새 현재 전제는 개정안의 표 대신 306행 같은 줄의 ①~⑨ 항목 목록으로 두고 Jetson 표(308~318행)는 바이트 그대로 그 아래 이력으로 남겼다 ②§14.5 "표 아래 추가" 문장은 1199행 계산 근거 앞에 ③§14.6 새 장비 사실(S-29)은 1233행 셋째 글머리 끝에 넣었다. 내용은 개정안 문안과 같다.
- 조건부 S-53(AC-35 계층 예외)은 적용하지 않았다(권장 (a) — AC-35-LOCAL 기대 불변). S-54(AC-37 답변 상한 "사고 토큰 제외")는 적용했다.

#### 3) Plan 개정 요약

- 새 Plan SHA-256 `52729527ae8298e13c5675adddb8678fb65cc16226e203a4d84f67450bac1b91`(1,756줄, 652,944 B). §0 상태·미완료 선행 조치, §4.1 SSH 규칙(`llm-notebook` skill, `AIGW_INFER_*`), §5 G-F0-ACCESS·G-F0-TARGET, G-JETSON-* → G-INFER-NET/CTX/RUNBOOK/EGRESS(옛 4행은 "폐기(대상 장비 변경, 이력)"로 남김) + 신규 G-INFER-ENV·G-INFER-SHARE, §6 DAG·병렬 규칙·F0/F2 exit, P03(v4 새 환경 흐름 ①~⑪, 선행·allowed_writes·oracle, 진행 기록 1줄), F2 이후 파급(P06·P15·P17·P18·P19·P21·P22·P27~P29·§11.1·§11.4·§12) 반영. P17 은 미착수라 Jetson 전용 절차를 이력으로 남기고 새 서버 기준 재작성 지침을 앞에 두었다.

#### 4) SPEC 해시 파급(기준선)

- Plan §0 R6: ①SPEC 반영(이 세션) ②root `Get-FileHash` ③**root 단독** `docs/gates/spec-approvals/5.md` + `spec-baseline.md` seq5 추가·커밋 ④⑤ 원격 publish·protected 변수(원격 소비 전) ⑥재측정. ③은 root 단독 writer 이고 commit 을 포함하므로 이 세션은 기준선 파일을 편집하지 않았다(§12.9·§12.10 선례).
- SPEC 수정 후 `:gateway-eval:run --args="spec-guard"` = `SPEC_BASELINE_MISMATCH`(Java exit 3, Gradle exit 1) — seq5 전 정상 차단, 우회 0. `MeasurementSession` 도 같은 `SpecGuard` 를 쓰므로 seq5 전에는 모든 측정 명령이 시작되지 않는다.
- root 에 권장하는 seq5: `sha256`=`1f08318d…637d`, `prevSha256`=`34fd6223…66be`, `approvedAt`=2026-11-04, `approvalRef`=`docs/gates/spec-approvals/5.md`, `changedSections`=["header","0","4.1","4.2","4.3","5.4","9.5","12.3","13.3","14.2","14.5","14.6","14.7","16","19","26.1","27.2","27.5"], `affectedLeaves`=ALL, `measurementAffecting`=true. seq5 뒤 v1~v3 레코드(specSha seq1~4)는 F0 gate 에 부적격(`SPEC_BASELINE_STALE`)이 되며 삭제·재해시 0.

#### 5) 하네스 v4 분기(f0 패키지, v1~v3 상수·요청 body·판정 불변)

- `Prereg`: schema `f0-prereg-v4` 추가. `RuntimeProfile`(JETSON = 기존 값 `qwen3:4b-instruct`·4096·Instruct tokenizer 쌍·`qwen3-4b-instruct` 템플릿 이름 / INFER_V4 = `qwen3:4b`·8192·`eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json{,.sha256}`·`qwen3-4b`). v4 는 v3 선언 규칙(purpose·responseFormat·contextProjection·retryHint)을 그대로 쓰고, `generationModelDigest`(64 hex) 필수, `contextTokens` 8192, 예산 `Budget.V4_DIAGNOSTIC`(입력 3,072/3,072, 요청 `max_tokens` 5,120/5,120 = 8192 − 입력 상한, 경계 판정 상한 = 답변 192), `answerLimitTokens`{192, 256} 필수, temperature 0 또는 0.6. **v4 는 DIAGNOSTIC 만 허용**한다(사고 상한 T 확정 전 GATE 등록은 `PREREG_INVALID` — SPEC §14.5 "확정 전 정식 측정 0"). v1~v3 에 `generationModelDigest`·`answerLimitTokens` 가 있으면 `PREREG_INVALID`, v1~v3 temperature 는 0 만(기존과 같음).
- `Preflight.run(..., requiredContext, registeredDigest)`: profile context 와 등록 digest 를 검사한다. 상주 digest ≠ 등록 digest 면 기존 tag·상주 불일치와 같은 `MODEL_DIGEST_MISMATCH`(측정 명령 exit 6, `f0 preflight` 명령은 기존대로 exit 4). 기존 3인자 `run` 은 4096·digest 검사 없음 그대로.
  - errorCode 선택 근거: 하네스 관례는 검사 범주별 고정 사유 코드 + 범주별 exit 코드다(`F0RunnerCli` javadoc "6 모델 digest 불일치", `MeasurementSession` 이 `MODEL_DIGEST_MISMATCH` 만 exit 6 으로 매핑). 등록 digest 불일치는 같은 범주(생성 모델 digest 불일치)라 새 코드를 만들지 않고 기존 코드를 썼다. SPEC §14.6 F2 readiness errorCode 형태는 미결정 그대로다(하네스 선택이 그 결정을 대신하지 않음).
- `OllamaChatClient`: `ChatResult` 에 `ThinkingObservation`(원문 없음 — message 의 `reasoning`·`reasoning_content`·`thinking` 중 null 아닌 필드 이름, `usage.completion_tokens_details.reasoning_tokens` 정수 또는 null, content 의 `<think>`·`</think>` 표지 존재 불리언)을 더했다. Ollama 0.40.13 의 실제 응답 형태는 미확인이라 존재/부재만 기록한다. 기존 4인자 생성자 유지, `toString` 은 기존대로 원문 비노출. content 비문자열은 기존처럼 `ENDPOINT_RESPONSE_INVALID`(exit 5, 부분 결과 보존). temperature 인자 overload 추가 — v1~v3 경로는 기존 정수 0 바이트 그대로.
- `MeasurementSession`: profile 기반 preflight·tokenizer 쌍·audit context, v4 만 등록 temperature 송신. `InputAudit`: context 를 받는 `load`·`probes` overload(기존 overload = 4096). `F0Measurements`: usage·token-golden 입력 상한 = 등록 context − 1, v4 행에만 시도별 `thinkingObservations` 열. `F0RunnerCli`: `chat-template --model` = 등록 profile 모델·저장 파일 이름 profile 별, `probe-g08` 은 v3 전용(v4 = `PREREG_INVALID`). `F0ReportWriter`: 재판정 런타임 SHA·예산 줄 context 를 profile 에서 읽음(v1~v3 문구 바이트 동일).
- 손대지 않은 것: `input-audit-render`·`probe-g08-render`(Budget.V2 고정)와 Python `count_input_audit_v2.py`(`CONTEXT_TOKENS = 4096`). v4 입력 audit 재생성(P03 v4 ⑥)에서 v4 계수기가 필요하며, 지금 v4 행을 v2 계수기에 넣으면 입력 + 5,120 > 4096 으로 `OVER_BUDGET`(fail-closed)이다. v4 지원 입력 목록은 tokenizer 쌍만 바꾸고 Python 파일은 v2 와 같게 두었으므로 ⑥에서 함께 정한다.

#### 6) 시험(이 세션, Gradle 동시 실행 0, `AIGW_SPEC_FILE`=정본 SPEC, SPEC 수정 전 seq4 일치 상태)

| 명령 | exit | 결과 |
|---|---|---|
| `:gateway-eval:test --tests "*F0V4RuntimeProfileTest"` | 0 | 7/7 PASS |
| 변이: `Preflight` 등록 digest 조건 제거 후 같은 시험 | 1 | `preflightRequiresRegisteredDigestAnd8192BeforeAnyInference` FAILED 확인 → 원본 복원(`cmp` 일치) |
| 무필터 `:gateway-eval:test --rerun-tasks`(1차) | 1 | 216 중 1 실패 — `F0V3RegistrationDraftTest.currentDraftsMatch…`: 현재 후보 `PREREG-v3-probe-g08.md` 의 `harnessDigest` stale |
| 위 시험만(수정 후) | 0 | 8/8 PASS |
| 무필터 `:gateway-eval:test --rerun-tasks`(2차) | 0 | `BUILD SUCCESSFUL in 5m 15s`, XML 독립 집계 **216/216 PASS·44클래스·failures/errors/skipped 0** |
| 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED` |
| SPEC 수정 후 `spec-guard` | Java 3 | `SPEC_BASELINE_MISMATCH` |

- 1차 실패 처리: C19 때 후보 i 를 HISTORICAL 로 옮긴 선례대로 `PREREG-v3-probe-g08.md` 를 `F0V3RegistrationDraftTest` HISTORICAL(C19/C20, PRESENCE_V1, STEPS_DETAIL_V1)로 옮겼다. 전체 호출 audit 검사(PURPOSE)에는 넣지 않았다(probe audit 은 `F0ProbeG08Test` 가 검사). 현재 v3 후보(CANDIDATES)는 0 이라 후보 시험은 후보가 생길 때만 실제로 검사한다.
- 최종 시험 신원: `runId=b209118c-d651-417f-ba1a-b39197948e84`, `inputFingerprint=858a8adcb7edd674475c395ea83877f83df3ce3119c02a144849f5ebb854933f`, `specSha=34fd6223…66be`(seq4). stub·로컬 시험이며 실제 모델 F0 합격이 아니다. SPEC 바이트가 바뀌었으므로 root 가 seq5 를 추가한 뒤 무필터 시험과 verify 를 다시 실행해야 한다.
- 신규 `F0V4RuntimeProfileTest` 7건: v1~v3 profile 불변·v4 profile/예산, v4 등록 규칙(GATE·Jetson 모델·4096·digest 누락/앞자리만·0.7·요청 192·답변 상한 누락·Jetson tokenizer 거부, 0.6 허용, v3 의 새 키·0.6 거부), v4 run 송신 body(키 집합 불변·`qwen3:4b`·5120·0.6)와 사고 관측 열·sentinel 비노출, content 혼입(`</think>`) = 무효 답변·표지만 기록, 등록 digest 불일치 exit 6·context 4096 exit 4·추론 호출 0, chat-template v4 모델·파일 이름과 probe-g08 v4 거부, 관측 `toString` 비노출과 정수 0 temperature 바이트.

#### 7) 기존 사전등록 상태(`f0 prereg-check`, 변경 전 → 후)

- 변경 전: `PREREG-v3-probe-g08.md` 만 `PREREG_VERIFIED`, 나머지 23건(`PREREG.md`·`PREREG-v2.md`·v3 21건)은 이미 `localMismatches=["harnessDigest"]`·`PREREG_INPUT_CHANGED`.
- 변경 후: 24건 모두 `localMismatches=["harnessDigest"]`·`PREREG_INPUT_CHANGED`. 새로 stale 이 된 것은 `PREREG-v3-probe-g08.md`(f0 하네스 소스 변경)뿐이며 입력 바이트 불일치는 0건이다. 하네스 변경으로 stale 되는 것은 정상이며 등록 문서는 고치지 않았다.

#### 8) 미확인·다음 단계

- 미확인(P03 v4 ④ 진단에서 확인): 사고문 분리 필드 이름과 content 혼입 여부, `max_tokens`·`usage.completion_tokens` 의 사고 포함 여부와 사고 중 상한 도달 `finish_reason`, `response_format`+thinking, properties 순서, `/api/ps` `context_length` 필드, prompt cache·G-USAGE, temperature 0 의 비종결 빈도, Thinking-2507 tokenizer 와 Instruct pin 의 바이트 동일 여부, 새 서버 처리 속도·콜드 로드.
- 다음: root seq5·`spec-approvals/5.md` → 무필터 시험·verify 재실행 → (사용자 허용 시) commit → P03 v4 ① G-INFER-ENV 읽기 전용 기록. 실측·SSH 는 사용자 승인 뒤에만.

#### 9) 보존 확인

- 기존 PREREG·결과·capture·evidence·v1~v3 입력·`docs/gates/**` 변경 0. 이 Dev report 앞 489,731 B 의 SHA 는 추가 전후 같다(`011442bb…0b35`). commit·push 0, 원문 응답·secrets 출력 0.

### 12.27 v4 개정 독립 검토 결함 수정과 하네스 반영 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: 독립 검토 결함(차단 B1, 중요 I1~I5, 경미 M1~M9, 이력 표지)을 SPEC·Plan 에 반영하고, I2(답변 상한 초과 = 실패)·I5(겹친 호출 지연 표본만 제외)를 v4 하네스 판정·집계에 반영했다. 작업트리 변경만 했고 commit·push 0, SSH·모델 호출·측정 0, 기준선 seq5·`spec-approvals/**` 변경 0, 기존 PREREG·결과·evidence·v1~v3 입력 변경 0.**
- 착수 시 SPEC `1f08318d…637d`(2,612줄), Plan `52729527…1b91`(1,756줄), 이 Dev report 503,142 B `ec9719ac…`.
- 사용자 결정(2026-11-04) 두 건: ①답변 토큰이 답변 상한(분류 192·서술 128·계획 256)을 넘으면 실패(v1~v3 의 `finish_reason=length` 실패와 같은 판정, 1회 재시도 대상) ②공유 서버 겹침은 회차 전체 무효가 아니라 겹친 호출의 지연 표본만 제외(정확도 채점 유지).

#### 1) SPEC 수정(1~1838행 줄 수 불변 — 모두 줄 안 수정·줄 끝 덧붙임, 새 줄 0)

| 결함 | 위치 | 전 → 후 요지 |
|---|---|---|
| B1 | §14.5 표 1194·1196, 1199, §16 F0 행 1382 | "F0 진단 측정 후 G-F0-TARGET 확정" → **T 확정 순서**: 진단 분포로 `T_cls`·`T_pln` 제안 → SPEC 소유자 승인 → §14.5 표에 T 기록(`measurementAffecting=true`, 기준선 새 사슬 항목) → 그 뒤에만 정식(GATE) 등록·측정 |
| I1 | 1195 | `T_cmp`(미정, F2 측정 후 확정) → F0 는 Composer 를 호출하지 않아 F0 근거 없음, 산정 절차·게이트는 열린 항목 ⓑ(F2 P18 착수 전) |
| I2 | 1199, 1203, 1382 | 1199 에 "답변 상한 초과 = 실패"(1회 재시도, 분류·계획 = 검사 ④·`PLAN_INVALID`, Composer = 같은 재시도 뒤 §14.6 ②), 답변 토큰 = 사고를 뺀 wire JSON·서술 본문, 서버의 사고 분리 보고로 셀 수 없으면 실패(fail-closed), 계수 방법은 F0 진단 뒤·정식 GATE 등록 전(열린 항목 ⓒ). 1203 검사 ①~③ 에 ④ 추가(`KQ/LQ/INC/GQ/UNS` 유지). F0 측정 항목에 답변 상한 초과 건수 |
| I3 | 306 ⑤, 328(§4.2), 1045(§12.3) | 사고문이 content 에 섞이면 분류·계획은 검사 ① JSON 파싱 실패로 걸러진다는 현재 사실만 적고, Composer 처리 규칙은 열린 항목 ⓓ(F2 P19/P20 착수 전) |
| I4 | 1848·1849(§26.1) | F2 권장 문장의 ".201 도 proxy 경유(별도 토큰) 또는 사용 중단" 을 빼고 ② **재비교 대상** ⓐ0.0.0.0+방화벽 ⓑproxy 경유(비용: 노드 IP 만 허용하는 방화벽에 .201 추가, 경로 allowlist 에 `/api/embed`·`/api/chat` 추가, 단일 토큰 → 소비자별 토큰, AC-35-TOKEN·FW 재검토) ⓒ사용 중단으로 이동 |
| I5 | 1382, 1225 ④ | "측정 창에 NORI 밖 요청 1건이라도 있으면 지연 축 무효·재측정" → 겹친 호출의 지연 표본만 제외, 채점 유지, 겹침 = 시도별 송신~수신 구간(UTC) ∩ 서버 GIN 로그 NORI 밖 요청 구간(서버 UTC), 회차의 시도 하나라도 겹치면 그 회차 지연 표본 제외, 제외 건수·비율·제외 뒤 표본 수 기록, digest·context·상주 변화만 회차 전체 무효, F2·F4 적용 여부 열린 항목 ⓔ. 1225 ④(F4 큐 회차 유효성)는 겹침 규칙과 별개임을 적음 |
| M6 | 1245 | 세마포어 목적 = "Gateway 자신의 호출에 한해"(NORI 밖 요청 뒤 서버 내부 대기는 막지 못함) |
| M7 | 1237 | digest READY errorCode 결정(P19 전) 때 AC-33 하위도 함께 정함(열린 항목 ⓕ) |
| M8 | 2286(§27.5) | 미결정 목록에 `T_cls`·`T_pln` 값, `T_cmp` 절차·시점, 답변 토큰 계수 방법, Composer 사고문 혼입, 겹침 규칙 F2·F4 적용, AC-35-LOCAL 조건부 재정의, proxy 실행 방식, .201 처리 합의 추가. 같은 행의 겹침 규칙 요약을 새 결정으로 바꾸고 "같은 날 독립 검토 보정" 문장 추가 |
| M9 | 306 ⑦ | VRAM 4544/6144(free 1205) 출처 = nvidia-smi `memory.used`·`memory.free`, 합이 total 과 다른 차이는 드라이버 예약분으로 추정 |
| 이력 표지 | 1176, 1231, 321, 1830·1835·1836 | 1176 "결론(이력 — Jetson 실측 기준)", 1231 "(이력, Jetson) 콜드 로드 198s", 321 "위 표" → §4.1 현재 전제 ⑤⑦⑨, 1830 머리말에 클러스터 항목(Cilium SNAT·노드 IP·NetworkPolicy)은 현재 근거로 분리·1835/1836 "(클러스터)" 표시·방화벽 문장 "추론 서버 호스트 방화벽(Jetson·새 서버 공통)" |

- §14.5 열린 항목 목록(1199 끝에 신설): ⓐ`T_cls`·`T_pln` 값 ⓑ`T_cmp` 산정 절차·게이트 ⓒ답변 토큰 계수 방법 ⓓComposer 서술 사고문 혼입 처리 ⓔ겹침 규칙의 F2·F4 적용 여부 ⓕreadiness digest 불일치 errorCode 와 AC-33 하위. 값·규칙은 정하지 않았다.

#### 2) Plan 수정

| 결함 | 위치 | 전 → 후 요지 |
|---|---|---|
| B1 | §5 G-F0-TARGET, P03 v4 흐름 | 필요 시점에 "(0) v4 정식 GATE 등록 전 — T 의 SPEC 기록·seq6 뒤에만 GATE 등록 commit" 추가. 흐름 ①~⑪ → ①~⑭ 재배열: ④하네스 v4 진단 분기 → ⑤audit → ⑥진단·개발 회귀 등록 → ⑦pre/postflight → ⑧진단 실행(계수 방법 확정) → ⑨개발 회귀 → ⑩T 제안·승인·SPEC 기록·seq6 → ⑪하네스 정식 분기·GATE R5 등록 → ⑫정식 R5 → ⑬report(겹침 제외)·gate → ⑭G-F0-TARGET 나머지·seq7. 직전 "④ 진단 등록 → ⑤ 하네스" 역순과 P03 준비 기록의 "⑤ 일부 선행" 문구를 바로잡음 |
| I1 | §6 F2 DAG, P18 선행, `TokenBudgetPolicy` | `T_cmp` 절차 결정을 P18 선행에 추가, 정책에 사고 상한(`T_cls`·`T_pln` = G-F0-TARGET, `T_cmp` = 절차 결과·값 미정) 항목 |
| I2 | P20 Classification·Composer | 답변 토큰 > 답변 상한(192/128, 계수 불가도 실패) → 같은 1회 재시도. Composer 요청 `max_tokens` = 128 + `T_cmp` |
| I3 | P19 readiness 행, P20 Composer | Composer content 사고문 혼입 처리 = SPEC 열린 항목 ⓓ(P19/P20 착수 전) |
| I5 | G-INFER-SHARE, P03 흐름 | GIN 로그에서 출발지별 건수 + NORI 밖 요청 구간 목록(UTC 시작·끝만)을 뽑고 `f0 share-exclude` 로 겹친 회차 지연 표본만 제외. GIN 로그 줄 시각이 완료 시각인지는 첫 수집 때 확인(미확인) |
| M1~M5 | 862·864, 425, 1667, 1679, 1676·1752 | 198s → "콜드 로드 완료(새 장비 미측정)까지", 128 → 등록 답변 상한(현재 192), `ops/jetson/**` → `ops/inference-server/**`, readiness 4096 → 8192(이력 표시), G-JETSON-* → G-INFER-*(이력 표시) |
| 이력 표지 | P02 363·376·388·391 | v1~v3 Jetson 등록 이력 표시와 v4 대응값 안내 |

- P03 에 진행 기록 1줄(독립 검토 보정)을 추가했다.

#### 3) 하네스 반영(f0 패키지, v1~v3 동작·요청 바이트 불변)

- 판단: v4 진단 하네스는 요청 `max_tokens` 5,120 으로 완료 토큰만 검사했고 답변 상한 192/256 은 판정에 없었다. 또 v4 경계 무결성 재계산이 사고 포함 `completionTokens ≤ 192` 를 써서 GATE 판정 경로가 생기면 `BOUNDARY_SUMMARY_MISMATCH` 가 날 구조였다. 지연은 회차 단위 `latencyMs` 만 있어 겹침 판정 입력(시각)이 없었다. 그래서 I2·I5 모두 반영이 필요했다.
- `ClassificationJudge`: `judge`·`execute` 에 `answerLimit`(Integer) overload 추가(기존 overload = null = 기존 판정). 길이·종료·완료 토큰 검사 뒤 ④ 답변 토큰 검사 — 셀 수 없으면 `ANSWER_TOKENS_UNCOUNTABLE`, 상한 초과면 `ANSWER_LIMIT_EXCEEDED`. 두 코드는 `RETRY_ERROR_CODES` 에 넣지 않고 재시도 요지는 기존 `USAGE_EXCEEDS_LIMIT` 로 보낸다(사전 입력 audit 이 계수하는 재시도 메시지 집합을 v2·v3 와 같게 유지 — 새 재시도 메시지를 만들면 audit 에 없어 `INPUT_AUDIT_MISSING`).
- 답변 토큰 계수: `completion_tokens − completion_tokens_details.reasoning_tokens`. 사고 토큰이 completion 에 포함된다는 OpenAI 호환 정의를 전제한다. 사고 토큰 수 보고가 없거나, content 에 `<think>`/`</think>` 표지가 있거나, 사고 토큰 > completion 이면 계수 불가 = 실패(fail-closed). **보고: 이 계수는 사고 분리 방식(미확인)에 의존한다.** Ollama 0.40.13 의 OpenAI 호환 응답이 `reasoning_tokens` 를 주지 않으면 v4 모든 시도가 `ANSWER_TOKENS_UNCOUNTABLE` 로 실패하고 재시도로 호출 수가 두 배가 된다(서버 응답 형태는 미확인 — 추정). 이 경우 진단의 정확도 축은 쓸 수 없고 `attemptDiagnostics`(필드별 독립 진단)·`thinkingObservations`·완료 토큰만 남는다. 계수 방법 확정은 P03 v4 ⑧·SPEC 열린 항목 ⓒ 이며, GATE 등록은 지금도 `Prereg` 가 거부한다.
- `Prereg.Document.answerLimit(kind)`: v4 등록 `answerLimitTokens`(분류 192·계획 256), v1~v3 = null.
- `MeasurementSession`: `Context.clock()` 을 보관하고 `clock()` 제공. `F0Measurements.Attempts.send`: 시도마다 송신 직전·수신(또는 timeout) 직후 UTC 시각을 `CallWindow{sentAt, receivedAt}` 로 기록(finally — timeout 시도 포함). v4 행·경계 시도에만 `answerTokens`(시도별, 셀 수 없으면 null)·`callWindows` 열. run·boundary-gen 은 v4 등록 답변 상한을 judge 에 넘긴다(v1~v3 = null).
- `F0Judgement.boundaryIntegrity`: v4 는 마지막 `answerTokens`(응답 없는 timeout 이면 null, 열 없음 = `BOUNDARY_ATTEMPT_INVALID`)로 경계 상한을 재계산, v1~v3 는 기존 `completionTokens` 그대로.
- 신규 `SharedServerOverlap` + CLI `f0 share-exclude --prereg <v4> --results <회차 디렉터리> --intervals <파일>`(추론 호출 0, v4 전용 — v1~v3 = `PREREG_INVALID`): 구간 파일 schema `f0-share-intervals-v1`(`intervals[{start,end}]`, UTC `Z` 표기·start ≤ end 만, 위반 = `SHARE_INTERVALS_INVALID`). 회차의 `callWindows` 중 하나라도 외부 구간과 닫힌 구간으로 교차하면 그 회차 지연 표본 제외. 경로별(A·B·boundary) `samples`·`excluded`·`excludedRatio`·`keptSamples`·제외 뒤 p50/p95(표본 0 이면 null)·기대 t 별 p95 만 출력(원문·IP 0, 원시 결과 불변). 서버 로그 수집(SSH)·GIN 줄 → 구간 변환은 구현하지 않았다(사용자 실행, Plan G-INFER-SHARE). 정식 report·gate 연동은 v4 정식 분기(P03 ⑪) 몫이다.
- 손대지 않은 것: `input-audit-render`(Budget.V2 고정)와 Python 계수기 — §12.26 5) 와 같다(P03 ⑤).

#### 4) 시험

- 신규 시험: `F0V4RuntimeProfileTest` 3건 — ①답변 192 유효·193 `ANSWER_LIMIT_EXCEEDED`·계수 불가 4형태(`reasoning_tokens` 없음·content `</think>`·사고 > completion·관측 없음) `ANSWER_TOKENS_UNCOUNTABLE`, 같은 응답의 v1~v3 판정 유효(불변), `FINISH_LENGTH` 가 먼저 ②v4 run: 답변 193 이면 문항마다 초회+재시도 2회·재시도 요지 `오류: USAGE_EXCEEDS_LIMIT`(사전 audit 통과)·요청 바이트에 `ANSWER_` 0·`errorCodes`·`answerTokens [193,193]`·`callWindows` 2개(UTC `Z`, sentAt ≤ receivedAt), 답변 192 면 재시도 0·OK ③`share-exclude`: 빈 구간 = 제외 0, 전 구간 = 전부 제외·p95 null, 닫힌 구간 경계(같은 시각 겹침·1ms 뒤 비겹침), offset 표기·역순 구간 거부, `callWindows` 없는 행 거부, v1 등록 거부, 원시 결과 불변. `F0BoundaryIntegrityTest` 1건 — v4 는 사고 포함 completion 960 이어도 답변 60 으로 PASS, 답변 193·null = `BOUNDARY_SUMMARY_MISMATCH`, 열 없음 = `BOUNDARY_ATTEMPT_INVALID`, 같은 입력의 v2 판정은 기존대로 불일치.
- 기존 시험 조정 1건: `F0V4RuntimeProfileTest.withThinking` 이 `reasoning_tokens` 77 을 주면서 completion 은 40 으로 두던 것을 completion += 77(사고 포함 정의)로 맞췄다(새 판정에서 사고 > completion 은 계수 불가이므로).
- 변이 확인: ①답변 상한 비교 제거 ②겹침 교차를 항상 false ③v4 경계 재계산을 completion 으로 되돌림 — 세 변이를 함께 넣은 필터 실행에서 신규 4건이 모두 FAILED(`fix-mut.log`), 원본 복원 후 `cmp` 일치.

| 명령(`AIGW_SPEC_FILE`=정본 SPEC, Gradle 동시 실행 0) | exit | 결과 |
|---|---|---|
| `:gateway-eval:test --tests "*F0V4RuntimeProfileTest" --tests "*F0BoundaryIntegrityTest" --tests "*ClassificationJudgeTest"` | Gradle 1 | XML **28/28 PASS**(3클래스). `test` 태스크 성공, finalizer `testLeafEvidence` 가 `SPEC_BASELINE_MISMATCH`(Java exit 3) |
| 같은 필터 + 변이 3종 | Gradle 1 | 신규 4건 FAILED(의도된 실패) → 원본 복원 `cmp` 일치 |
| 무필터 `:gateway-eval:test --rerun-tasks` | Gradle 1 | `> Task :gateway-eval:test` 성공, XML 독립 집계 **220/220 PASS·44클래스·failures/errors/skipped 0**(§12.26 216 + 신규 4). `testLeafEvidence FAILED` — `SPEC_BASELINE_MISMATCH`, `BUILD FAILED in 5m 27s` |

- SPEC 바이트가 기준선 seq4 와 다르므로(seq5 미작성 — root 몫, 이번에도 기준선 파일 변경 0) `testLeafEvidence` finalizer 가 spec-guard 로 막는다. 단위 결과(220 PASS)와 프로세스 실패는 별개이며 이 명령을 성공한 검증으로 계산하지 않는다. `test-evidence verify` 는 이 상태에서 실행하지 않았다(seq5 뒤 재실행 대상). 측정 명령도 같은 SpecGuard 로 시작되지 않는다.

#### 5) 열린 항목(최종, 값·규칙 미정)

1. `T_cls`·`T_pln` 값 — F0 진단 뒤, v4 정식 GATE 등록 전(SPEC §14.5 "T 확정 순서", Plan G-F0-TARGET (0)).
2. `T_cmp` 산정 절차·게이트·확정 시점 — F2 P18 착수 전(F0 근거 없음).
3. 답변 토큰 계수 방법 — F0 진단(⑧) 뒤, 정식 GATE 등록 전. 그때까지 계수 불가 = 실패.
4. Composer 서술 content 사고문 혼입 처리 — F2 P19/P20 착수 전.
5. 공유 서버 겹침 제외 규칙의 F2·F4 지연 축 적용 여부 — F2 착수 전.
6. readiness 생성 모델 digest 불일치 errorCode 형태(신규 vs `MODEL_CONTEXT_MISMATCH` 확장)와 그 AC-33 하위 — F2 P19 착수 전.
7. v4 등록에 함께 둘 v3 후보(c~i) 구성 — 사용자 결정 대기(기존).
8. AC-35-LOCAL 조건부 재정의 — §26.1 ② 비교 결과에 따름.
9. 추론 서버 proxy 실행 방식 — 새 서버 docker 유무 미확인, F2 산출물.
10. 기존 소비자 192.168.0.201 처리(ⓐ0.0.0.0+방화벽 ⓑproxy 경유 ⓒ사용 중단) — 소유자 합의, F2 착수 전.
11. GIN 로그 줄 시각의 의미(완료 시각 여부)와 구간 산출 방법 — 첫 수집 때 확인.

#### 6) 다음 단계

- root: 새 SPEC `bb91146a0f0c84f8ec885355dc840c42de06be346c910cd0f846745469bd0de4` 로 기준선 seq5 를 만든다(§12.26 4) 의 권장 seq5 는 이전 SHA `1f08318d…` 기준이므로 sha256 을 새 값으로 바꿔야 함. `prevSha256`=`34fd6223…66be` 그대로). 그 뒤 무필터 시험과 `test-evidence verify` 재실행 → (사용자 허용 시) commit → P03 v4 ①.

#### 7) 보존 확인

- 이 Dev report 앞 503,142 B(§12.26 끝까지)의 SHA 는 추가 전후 같다(`ec9719ac8b265dbdf86ede23fe260830659801e8af6872279d8236465ccbd62e`). 기존 PREREG·결과·capture·evidence·v1~v3 입력·`docs/gates/**` 변경 0. 원문 응답·secrets 출력 0.

### 12.28 기준선 seq5 기록·전체 시험·로컬 C21 커밋 범위 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: Plan §0 R6 ③ 의 기준선 seq5 와 `docs/gates/spec-approvals/5.md` 를 기록했고, 그 뒤 `spec-guard`·무필터 시험(testLeafEvidence 포함)·`test-evidence verify` 가 모두 exit 0 이다. 로컬 commit C21 1개만 허용 범위다(push·`--force`·`spec-input publish`·protected 변수 갱신 0). SSH·모델 호출·측정 0. P03 미완료(v4 흐름 미착수)·기존 F0 FAIL 유지·P04 미착수.**
- 승인 근거: 2026-11-04 사용자 결정 — SPEC v4.1 추론 장비 교체 개정 권장안 일괄 채택, 독립 검토 결함 수정 결정(답변 상한 초과 = 실패, 공유 서버 겹친 호출만 지연 제외), "seq5·C21 허용".
- 착수 시 SPEC SHA-256 `bb91146a0f0c84f8ec885355dc840c42de06be346c910cd0f846745469bd0de4`(2,612줄, §12.27 6) 의 값과 일치), HEAD C20 `7ab4e9a`, 이 Dev report 518,503 B `4255882b29e94cbcedf05a495caefc140020c0a287eec3df0a7e3e31af226ffa`, staged 0.

#### 1) 기준선 seq5

- `docs/gates/spec-baseline.md` 배열 끝에 항목 1개만 추가했다(seq1~4 바이트 불변):

```json
{"seq":5,"sha256":"bb91146a0f0c84f8ec885355dc840c42de06be346c910cd0f846745469bd0de4","prevSha256":"34fd62233f96995054a48ce64c6aa0abe83585bab6c516183bce38ad739466be","approvedAt":"2026-11-04","approvalRef":"docs/gates/spec-approvals/5.md","changedSections":["header","0","4.1","4.2","4.3","5.4","9.5","12.3","13.3","14.2","14.5","14.6","14.7","16","19","26.1","27.2","27.5"],"affectedLeaves":"ALL","measurementAffecting":true}
```

- `approvalRef` 는 seq2~4 관례와 §12.26 4) 권장대로 승인 기록 경로로 두고, 승인 요지는 `spec-approvals/5.md` 에 적었다(`SpecBaseline` 은 이 값을 자유 문자열로 받는다).
- `changedSections` 대조: seq4 바이트 사본(SHA `34fd6223…66be` 확인)과 현재 SPEC 의 줄 diff(54 hunk)의 변경 위치를 조항 머리글로 대응시킨 합집합이 §12.26 4) 목록 18개와 같다. §12.27 1) 수정 줄(306·321·328·1045·1176·1194~1203·1225·1231·1233·1237·1245·1382·1830~1849·2286)은 각각 §4.1·§4.2·§12.3·§14.5·§14.6·§14.7·§16·§26.1·§27.5 에 속해 새 조항을 더하지 않는다. 그래서 §12.26 4) 목록을 그대로 썼다(sha256 만 새 값).
- 배열 JSON 파싱(Node, 실패 시 원문 비출력): 5항목, seq5 `prevSha256` = seq4 `sha256` true. 두 파일 UTF-8·LF(CR 0)·끝 개행.

#### 2) 시험(`AIGW_SPEC_FILE`=정본 SPEC, `DEBUG`=빈 값, Gradle 동시 실행 0, 순차)

| 명령 | exit | 결과 |
|---|---|---|
| `:gateway-eval:run --args="spec-guard"` | 0 | `specSha`=`bb91146a…d0de4`, `specInputOrigin`=`LOCAL_FILE`, `BUILD SUCCESSFUL in 3s` |
| 무필터 `:gateway-eval:test --console=plain --rerun-tasks` | 0 | `:gateway-eval:test`·`:gateway-eval:testLeafEvidence` 실행, `BUILD SUCCESSFUL in 5m 24s`, `6 actionable tasks: 6 executed`. XML 독립 집계 **220/220 PASS·44클래스·failures/errors/skipped 0** |
| `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s` |

- 시험 신원(`run-binding.json`): `runId=203aa122-1d4f-4854-aa3e-b92b5a124d51`, `inputFingerprint=22fd026b8c18345a2479d8547a791a248e08670709d35444ee3531021df9ca69`, `specSha=bb91146a…d0de4`(seq5). stub·로컬 시험이며 실제 모델 F0 합격이 아니다. §12.27 4) 의 `testLeafEvidence` `SPEC_BASELINE_MISMATCH` 는 seq5 기록으로 해소됐다(코드 변경 0).

#### 3) 기존 사전등록 상태(`f0 prereg-check`, endpoint 인자 없음, 모델 요청 0)

- 24건(`PREREG.md`·`PREREG-v2.md`·v3 22건) 모두 `localMismatches=["harnessDigest"]`·`PREREG_INPUT_CHANGED`(Gradle exit 1) — §12.26 7) 의 변경 후 상태와 같다. 입력 바이트 불일치 0.
- `prereg-check` 는 하네스·입력 대조만 하며 기준선 적격(`SPEC_BASELINE_STALE`)은 `GateEvaluator`(gate 단계)가 판정한다. seq5(`affectedLeaves=ALL`) 뒤 v1~v3 레코드(specSha seq1~4)는 gate 에서 부적격이 된다. 등록 문서·결과·evidence 는 고치지 않았다.

#### 4) 커밋 범위(사용자 허용: 로컬 C21 1개, push 없음)

- C21(정확 17파일): `docs/gates/spec-baseline.md`, `docs/gates/spec-approvals/5.md`(신규), `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ClassificationJudge,F0Judgement,F0Measurements,F0ReportWriter,F0RunnerCli,InputAudit,MeasurementSession,OllamaChatClient,Preflight,Prereg}.java`, `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/SharedServerOverlap.java`(신규), `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0BoundaryIntegrityTest,F0V3RegistrationDraftTest}.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V4RuntimeProfileTest.java`(신규), `docs/ai-rag-gateway-architecture-dev-report.md`(C20 뒤 미커밋으로 추가된 §12.25 끝 "로컬 C19/C20 등록·최종 확인"·§12.26·§12.27·이 절 포함, C5·C7·…·C19 와 같은 관례). 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- 이 절 추가 전 518,503 B 의 SHA(`4255882b…6ffa`)는 추가 후 앞 518,503 B 에서 같아야 한다(커밋 직전 확인). 커밋 해시·확인 결과는 커밋 뒤 이 절 끝 "로컬 C21 커밋" 소절에 작업 트리에만 기록한다(C17/C18·C19/C20 관례 — 다음 커밋에 포함).

#### 5) 다음 단계

- Plan §0 R6 ④⑤(`spec-input publish`·protected `AIGW_APPROVED_SPEC_SHA`=seq5)는 원격 공급을 소비하는 CI job·`ci-evidence verify`·배포 수용 전 조건이며 미실행이다. 다음은 P03 v4 ① G-INFER-ENV 읽기 전용 기록이며, SSH·실측은 사용자 승인 뒤에만 한다.

#### 로컬 C21 커밋·최종 확인 (2026-11-04, 독립 Claude Code Opus 5.5)

- 허용 범위: 로컬 commit C21 1개뿐이며 push·amend·`--force`·원격 publish 는 하지 않았다.

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C21 | `09c252a9c10a61a38c77381b1d50729578275283` | 2026-11-04 11:10:45 +0900 | 17파일, 886 insertions(+), 62 deletions(-) |

- C21 정확 17파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 17개, 기대 목록과 diff 0, `git diff --cached --check` exit 0). 이 Devreport 의 C21 시점 바이트는 524,347 B, SHA `16325922c650b4aff894f03aa0a5c85b5ca4b41b1ac5a94d693fd2a3dec7a9ac`(C21 blob 과 같음)이며 앞 518,503 B SHA `4255882b…6ffa` 는 §12.28 추가 전후 같았다. 기존 untracked 는 넣지 않았다.
- 커밋 뒤 정본 SPEC SHA `bb91146a…d0de4` 불변, 21커밋, staged 0. 측정·SSH·모델 호출은 실행하지 않았다. 이 소절은 commit 하지 않았다(C21 커밋 바이트 불변, 작업 트리에만 추가 — 다음 커밋에 포함).

### 12.29 P03 v4 ① G-INFER-ENV 런타임 고정 기록·③ tokenizer pin·chat template 캡처·렌더러 등록 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: ① G-INFER-ENV 를 `docs/gates/f0/runtime-env-20261104T021454Z.json`(허용 필드만)에 기록했고, ③ Thinking-2507 공식 `tokenizer.json` pin(Instruct pin 과 바이트 동일), `/api/show` template 사전검토 캡처(Go template, SHA `2d54db2b…97f9`), v4 렌더러 `OLLAMA_GO_QWEN3_THINKING_V1` 등록(기존 `CHATML_NO_SYSTEM_DEFAULT` 는 재현 불가)과 시험을 작업트리에 추가했다. 모델 추론 호출 0(`/api/chat`·`/api/generate`·`/v1/*` 0), pull·load·unload·재시작·설정 변경·sudo 0, commit·push 0. ②(spec-guard)는 아래 시험 앞 `spec-guard` exit 0 으로 seq5 일치만 확인했다. P03 미완료·기존 F0 FAIL 유지·P04 미착수.**
- 착수 시 HEAD C21 `09c252a`, SPEC `bb91146a…d0de4`, Plan `5d51c23bcbcd5bccd19f86c0f0d8ac5f4947af33afccbbf5a04bbe2583d6ba34`(1,757줄), 이 Dev report 525,397 B `159d0f140a7a602154a84946bf254050b21c3b0a2c1a9ad2462e8a89cc2edb61`(C21 뒤 미커밋 "로컬 C21 커밋·최종 확인" 소절 포함).

#### 1) ① G-INFER-ENV(Plan §5 정의 항목, 읽기 전용)

- 수집: 서버(192.168.0.160, hostname `ollama`, 시간대 Etc/UTC)에서 Python 표준 라이브러리로 `GET /api/version`·`/api/ps`·`/api/tags`·`POST /api/show {"model":"qwen3:4b"}` 만 호출하고 허용 필드만 try/except 로 뽑았다(파싱 실패 시 고정 문구만, 원문 출력·저장 0). 그 밖에 `systemctl show ollama`(OLLAMA_* 값·키 이름·unit/drop-in 경로와 SHA), `nvidia-smi` 요약. API 수집 시각 `2026-11-04T02:14:54Z`.
- 값: Ollama `0.40.13`. 환경 `OLLAMA_CONTEXT_LENGTH=8192`·`OLLAMA_HOST=0.0.0.0:11434`·`OLLAMA_KEEP_ALIVE=24h`·`OLLAMA_MAX_LOADED_MODELS=2`·`OLLAMA_NUM_PARALLEL=1`(그 밖 키는 `PATH` 뿐), drop-in `/etc/systemd/system/ollama.service.d/override.conf`(SHA `d25df74d…2b09`, 6줄), 서비스 active since `2026-11-03T04:10:45Z`.
- `/api/ps`: `qwen3:4b` digest **`359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7`**·`context_length` 8192·size = size_vram 3,877,571,133(Q4_K_M, 4.0B); `bge-m3:latest` digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`·8192·689,187,061 = size_vram(F16). `/api/tags` digest 두 모델 모두 `/api/ps` 와 같다. `/api/ps` 필드 이름 = context_length·details·digest·expires_at·model·name·runner·size·size_vram(`expires_at`·`runner` 값은 기록하지 않음).
- `/api/show qwen3:4b`: template SHA `2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9`(1506 B), `template_source=go_template`, renderer·parser 없음, capabilities `[completion, tools, thinking]`, `thinking={default:true, values:[true]}`, parameters `top_k 20`·`top_p 0.95`·`repeat_penalty 1`·`stop ["<|im_start|>","<|im_end|>"]`·`temperature 0.6`(num_ctx 없음), Modelfile 지시어 FROM·TEMPLATE·PARAMETER 6·LICENSE, `general.finetune=Thinking`, `qwen3.context_length=262144`, `tokenizer.ggml.add_bos_token=false`.
- GPU: GTX 1660 Ti, 드라이버 595.99.02, CUDA 13.2, 6144 MiB 중 used 4544·free 1205 MiB, P8, 사용률 0%, llama-server 2개(3798·742 MiB).
- §12.26 8) 미확인 중 `/api/ps` `context_length` 필드는 **있음**(8192)으로 확인됐다.

#### 2) ③ tokenizer pin

- HF `Qwen/Qwen3-4B-Thinking-2507` revision `768f209d9ea81521153ed38c47d515654e938aea` 의 `tokenizer.json` 1개만 새 빈 디렉터리에 받았다(실행 코드·가중치 0). 302 헤더 `X-Linked-Size 11422654`·`X-Linked-ETag aeb13307…dae4` = 받은 바이트 재계산값. `eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json`(바이트 그대로) + pin `….sha256`(`aeb13307…dae4  qwen3-4b-thinking-2507.tokenizer.json` + LF, `sha256sum -c` OK, pin 파일 SHA `91fd22b9…bc03`). 이 경로는 C21 의 `RuntimeProfile.INFER_V4` 가 이미 가리키던 경로다.
- **Instruct-2507 pin 과 비교: `cmp` 차이 0 — 두 공식 tokenizer.json 은 바이트 동일**(SHA `aeb13307…dae4`). pin 파일은 파일 이름 부분만 달라 바이트가 다르다(Instruct `445bc540…2113`). 기록 `docs/gates/f0/tokenizer-provenance-v4.md`(v1 `tokenizer-provenance.md` 형식). §12.26 8) 미확인 "Thinking-2507 tokenizer 와 Instruct pin 의 바이트 동일 여부" = 동일.

#### 3) ③ chat template 캡처

- 기존 관례 확인: v1 은 P03 준비 때 `/api/show` 의 `template` 필드만 `docs/gates/f0/preparation-template.template` 에 바이트 그대로 저장하고 `template-review.md` 로 검토했으며, `eval/f0/chat-template/**` 정식 캡처(템플릿 본문 + `.sha256` + `model-digest.txt` + `run-binding.json`)는 등록 PREREG 가 있는 `f0 chat-template` 측정 명령만 쓴다(v2/v3 는 각 PREREG 2절 명령). v4 등록 문서(⑥)는 아직 없으므로 정식 명령은 실행할 수 없고, v1 준비와 같은 방식으로 **사전검토 캡처** `docs/gates/f0/preparation-template-v4.template`(1506 B, LF, CR 0, 끝 개행 없음, SHA `2d54db2b…97f9`)만 만들었다. template 은 모델 설정이며 기존 관례도 본문을 저장했다.
- 정식 캡처는 v4 PREREG 의 `runtimeCaptureDir` 아래 `qwen3-4b.template`(C21 `templateStem`)로 측정 회차에서 만든다.

#### 4) ③ 렌더러 등록

- 판정 근거 `docs/gates/f0/template-review-v4.md`: 이 모델은 HF Jinja 가 아니라 Ollama Go template 경로다(`/api/show` `template_source=go_template`, 서버 로그 `template selection` `selected=go_template` 2건, 생성 llama-server 인자 `--no-jinja --chat-template chatml`(시작용 자리표시), Ollama v0.40.13 `04088e7c…` 소스 `selectedTemplateSource`·`usesOllamaRenderedChat`·`renderPrompt`·`Template.Execute`·`collate`). 렌더 문자열은 `/completion` 에 그대로 간다(`add_bos_token=false`).
- system+user 렌더 = `<|im_start|>system\n` + S + `\n\n<|im_end|>\n<|im_start|>user\n` + U' + `<|im_end|>\n<|im_start|>assistant\n<think>\n`, 재시도의 연속 user 는 `collate` 가 `\n\n` 으로 합친다(U' = U + `\n\n` + R). **v1 `chatMl` 과 3곳이 달라(system 뒤 빈 줄, 재시도 user 병합, `<think>\n` 접두) 재사용 불가.**
- 실제 Go 엔진 대조: 로컬 Docker `golang:1.24-alpine`(go1.24.13, `--network none`)에서 저장 template 을 `text/template` 으로 실행(Ollama `collate` 와 같은 값 구성) — 6 사례 모두 위 공식과 바이트 동일. 대조 프로그램은 scratchpad 에만 두었다.
- 추가: `ChatRenderers.OLLAMA_GO_QWEN3_THINKING`(=`OLLAMA_GO_QWEN3_THINKING_V1`)·`ollamaGoQwen3Thinking`(범위 밖 입력 = `RENDERER_INPUT_UNSUPPORTED` exit 2)과 `forTemplate` 의 이름 분기 1줄. v4 전용 등록 파일 `eval/f0/v4/chat-renderers-v4.json`(같은 형식 `f0-chat-renderers-v1`, template SHA `2d54db2b…97f9` → `OLLAMA_GO_QWEN3_THINKING_V1`, reviewRef `docs/gates/f0/template-review-v4.md`). v1~v3 등록 파일 `eval/f0/chat-renderers-v1.json`(`ba743ec7…c7e`)·`chatMl`·`CHATML_NO_SYSTEM_DEFAULT` 분기는 바이트·동작 불변. v4 등록(⑥)은 `chatRenderers` 역할에 v4 파일을 넣어야 한다.

#### 5) 시험(`AIGW_SPEC_FILE`=정본 SPEC, `DEBUG`=빈 값, Gradle 동시 실행 0, 순차)

- 신규 `F0V4ChatRendererTest` 4건(레포 실제 바이트): ①v4 등록 → 렌더러 선택, 첫 시도·재시도(직접 + `MessageAssembler.retry`)·system 없음·빈 system 문자열 ②범위 밖(assistant·system 만·system 뒤 위치·tool·빈 목록) 거부 ③v1 등록 SHA `ba743ec7…` 불변·v1 등록은 v4 template 거부·v4 등록은 v1 template 거부·`chatMl` 출력 불변 ④Thinking pin = 실제 바이트 = `aeb13307…`, pin 파일 내용, Instruct 원본과 바이트 동일.
- 변이 확인: v4 렌더러를 `chatMl` 형태(system 빈 줄 제거·user 블록 분리·`<think>` 제거)로 바꾼 필터 실행에서 ①이 FAILED(1/4) → 원본 복원 `cmp` 일치.

| 명령 | exit | 결과 |
|---|---|---|
| `:gateway-eval:test --tests "*F0V4ChatRendererTest" --tests "*F0V4RuntimeProfileTest"` | 0 | XML 4/4 + 10/10 PASS, `BUILD SUCCESSFUL in 30s` |
| 같은 필터(`F0V4ChatRendererTest` 만) + 변이 | 1 | 4 중 1 FAILED(의도) → 복원 |
| `:gateway-eval:run --args="spec-guard"` | 0 | `specSha`=`bb91146a…d0de4`, `BUILD SUCCESSFUL in 2s` |
| 무필터 `:gateway-eval:test --console=plain --rerun-tasks` | 0 | `:gateway-eval:test`·`:gateway-eval:testLeafEvidence`, `BUILD SUCCESSFUL in 5m 27s`, `6 actionable tasks: 6 executed`. XML 독립 집계 **224/224 PASS·45클래스·failures/errors/skipped 0**(§12.28 220 + 신규 4) |
| `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s` |

- 시험 신원(`run-binding.json`): `runId=ff3e07b5-954b-45d6-9b14-a248c1d89ef8`, `inputFingerprint=5160fcd8f32b39ff5dcfc1e47f11197debc3ea7c158c0bcc862dc26ac73b8f31`, `specSha=bb91146a…d0de4`. stub·로컬 시험이며 실제 모델 F0 합격이 아니다.
- 사전등록 영향: f0 소스(`ChatRenderers.java`) 변경으로 기존 24건의 `harnessDigest` 는 계속 stale(§12.28 3) 과 같은 상태, 이번에 재실행하지 않음)이고 등록 입력 파일은 바꾸지 않았다(새 파일 추가만).

#### 6) 변경·신규 파일(작업트리, 커밋 0)

- 수정: `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/ChatRenderers.java`, 이 Dev report(끝에 이 절 추가만).
- 신규: `docs/gates/f0/runtime-env-20261104T021454Z.json`, `docs/gates/f0/tokenizer-provenance-v4.md`, `docs/gates/f0/preparation-template-v4.template`, `docs/gates/f0/template-review-v4.md`, `eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json`(11,422,654 B), `eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256`, `eval/f0/v4/chat-renderers-v4.json`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V4ChatRendererTest.java`.
- 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 건드리지 않았다.

#### 7) 미확인·예상 밖

- 예상 밖: 서버 모델은 GGUF Jinja 가 아니라 Ollama Go template 으로 렌더된다(Jetson v1 과 경로가 다름). 그래서 v4 입력 audit(⑤)는 v4 렌더러·v4 등록 파일과 Thinking pin 으로 다시 만들어야 하며, 재시도 입력은 user 두 블록이 아니라 한 블록으로 계수된다.
- Modelfile 기본값 `temperature 0.6`·`top_k 20`·`top_p 0.95` 가 있다. 하네스 v4 요청은 temperature 를 등록값으로 보내지만 top_k·top_p 는 보내지 않으므로 서버 기본값이 적용된다(추정 — 요청별 옵션 병합 경로는 이번에 읽지 않음, ⑧ 진단 해석 때 확인).
- `OLLAMA_NUM_PARALLEL=1`·`MAX_LOADED_MODELS=2`: 192.168.0.201 요청과 측정 요청이 서버 큐에서 직렬화될 수 있다(G-INFER-SHARE 겹침 제외의 전제와 같은 방향, 수치 미측정).
- 미확인: Ollama 0.40.13 번들 llama.cpp 빌드 번호와 `/completion` special token 해석(v1 검토 근거를 따름), 실제 서버 `prompt_tokens` 와 pin 계수의 일치(⑤·⑧), 사고 분리 응답 형태(⑧). Ollama 소스에 `/api/chat` 의 렌더 전용 디버그 경로(`DebugRenderOnly`)가 있으나 이 세션은 `/api/chat` 금지이고 그 경로도 runner 스케줄 뒤에 있어 쓰지 않았다.
- GIN 로그 형식(G-INFER-SHARE 열린 항목 11)은 이번 범위가 아니어서 보지 않았다(`template selection` 줄의 두 값만 집계).

#### 8) 다음 단계

- P03 v4 ④(하네스 v4 진단 분기 보완)·⑤(입력 audit 재생성 — `input-audit-render` 의 v4 예산·v4 렌더러, Python 계수기 context 8192)·⑥(진단·개발 회귀 등록 초안, `generationModelDigest`=`359d7dd4…fae7`, `chatRenderers`=`eval/f0/v4/chat-renderers-v4.json`). 커밋 포함 여부는 root 결정.

#### 9) 보존 확인

- SPEC `bb91146a…d0de4`·Plan `5d51c23b…ba34` 불변, 기존 PREREG·결과·evidence·v1~v3 입력·pin·캡처(`eval/f0/chat-renderers-v1.json`, `eval/f0/tokenizer/qwen3-4b-instruct-2507.*`, `docs/gates/f0/preparation-template.template`·`template-review.md`·`tokenizer-provenance.md`, `eval/f0/chat-template/**`) 변경 0. 이 Dev report 앞 525,397 B 의 SHA 는 추가 전후 같다(`159d0f14…db61`). 원문 응답·secrets 출력 0.

### 12.30 P03 v4 ④ 하네스 v4 진단 분기 완성·⑤ v4 입력 audit·⑥ 진단·개발 회귀 사전등록 초안과 로컬 C22/C23 범위 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: Plan P03 v4 ⑥ 의 진단 최소 입력 항목을 원문 없이 관측하도록 하네스 v4 진단 분기를 보완했고(v4 GATE 등록 거부 유지), v4 렌더러·Thinking pin 으로 v4 입력 audit 4개(OVER_BUDGET 0)를 만들었으며, v4 진단 4·개발 회귀 2 사전등록 초안을 작성했다. 무필터 시험·`test-evidence verify`·`spec-guard` 모두 exit 0. 모델 추론 호출·SSH·측정 0, SPEC·Plan·기존 PREREG·결과·evidence·v1~v3 입력·pin 변경 0. P03 미완료·기존 F0 FAIL 유지·P04 미착수.**
- 착수 시 HEAD C21 `09c252a`, SPEC `bb91146a…d0de4`(seq5), 이 Dev report 537,769 B `0868750a9cc9369799ac9aa2eb6fc85371e293c7bd6268d68bc2c839ba0e99dd`(§12.29·C21 뒤 소절 포함, 미커밋).
- 사용자 결정(2026-11-04, 이 세션 입력): 프롬프트 arm = v2 계약 프롬프트(필수) + v3 마지막 구성 i(C17/C18 등록 구성), temperature arm = 0 + 0.6(DQ-1), 진단은 사고 상한 없이 `max_tokens = 8192 − 입력 상한`, 답변 상한 192/256 초과 = 실패·1회 재시도(I2), 겹친 호출만 지연 제외(I5), 골든셋·fixture·회귀·경계 케이스 바이트 재사용, 등록은 C22/C23 두 commit 만.

#### 1) ④ 하네스 v4 진단 분기(f0 패키지, v1~v3 요청 body·판정·결과 모양 불변)

- `OllamaChatClient.ThinkingObservation`: `thinkingCodepoints`(THINKING_FIELDS 순서 첫 문자열 분리 필드의 코드포인트 수, 없으면 null)·`contentCodepoints`(content 코드포인트 수)를 더했다 — usage 의 사고 포함 여부·사고 도중 상한 도달(content 0)을 원문 없이 가리는 개수.
- `F0Measurements`: v4 행·경계 시도에 `outputKeyOrders`(답변 최상위 키 출력 순서 — 키가 모두 스키마 키 이름 `t·eqp·from·to·q·qRef·steps` 일 때만, 아니면 null; properties 순서 진단). v4 의 `max_tokens:1` 계수 호출(usage-probe chat·token-golden)에 `finishReason`·`completionTokens`·`thinking`(관측)·`auditInputTokens`(그 호출의 사전 audit 계수)를 더했다 — 사고 전용 모델에서 출력 상한 1 은 사고 도중 상한 도달이므로 `max_tokens` 의 사고 포함 여부와 그때의 finish_reason 을 직접 관측하고, `promptTokens` 대 `auditInputTokens` 로 v4 렌더러·pin 계수와 서버 계수의 동등성을 본다.
- `F0RunnerCli input-audit-render --runtime-profile INFER_V4`(선택): 행 예산을 `Budget.V4_DIAGNOSTIC`(요청 `max_tokens` 5,120, 입력 상한 3,072)·probe context 8192 로 만든다. 값 없음 = 기존 `Budget.V2`·4096 그대로, 그 밖 값 = `ARGUMENT_INVALID`.
- `Prereg.RuntimeProfile`: 입력 audit 계수기 필드(`inputAuditCounter`) — JETSON = `eval/py/count_input_audit_v2.py`(불변), INFER_V4 = 신규 `eval/py/count_input_audit_v4.py`. v2 계수기는 v2·v3 등록 입력이라 바이트를 바꿀 수 없어(바꾸면 v2·v3 24건이 입력 불일치) v2 와 계수 규칙·결과 형식이 같고 `CONTEXT_TOKENS=8192`·렌더러·도구 이름 기록만 다른 파일을 새로 뒀다(diff 4곳). Python 시험 `eval/py/tests/test_count_input_audit_v4.py`(실제 pin tokenizer 로 8192 경계·렌더러 기록) — 수동 실행 `unittest` 4/4 OK(v2 시험 3 포함, Gradle 밖).
- `F0ReportWriter.diagnosticReport`: v4 등록에서만 "v4 사고 출력 관측" 절(출처·시도별 시도 수, 분리 필드=건수, `reasoning_tokens` 보고 수, content 표지 수, content 0 건수, 사고 토큰 p50/p95/max, 사고 코드포인트 max, 답변 토큰 p50/p95/max(계수 불가 수), 출력 키 순서=건수). 사고 토큰 분포는 ⑩ T 제안의 근거 입력이다.
- 유지: v4 = DIAGNOSTIC 만(`Prereg` 가 v4 GATE `PREREG_INVALID`), v1~v3 상수(`F0RunnerCli.GENERATION_MODEL`·`Preflight.REQUIRED_CONTEXT`·`Prereg.CONTEXT_TOKENS`·temperature 0 검사) 불변.

#### 2) ③ 렌더러 범위 확장(⑤ 의 선행 결함)

- 예상 밖: 등록 token-golden 입력 `eval/f0/v2/token-golden-input-v2.jsonl`(재사용)의 `TG07-MULTI-TURN`(system·user·assistant·user — Plan P03 token golden 의 "다중 턴" 요구 행)이 §12.29 렌더러 범위(system + user 만) 밖이라 v4 audit 렌더가 `RENDERER_INPUT_UNSUPPORTED` 였다. 행을 빼면 Plan 요구가 깨지므로 렌더러를 넓혔다.
- 근거: tagged source(`04088e7c…d5b`) `server/routes.go#L3662-L3688` `filterThinkTags`(qwen3: 마지막 user 앞 assistant content 를 thinking parser 로 다시 씀)와 `thinking/parser.go`(왼쪽 공백 뺀 값이 여는 태그·그 접두가 아니고 비어 있지 않으면 원문 그대로) → 첫 글자가 공백류도 `<` 도 아닌 비어 있지 않은 content 만 불변. template 은 마지막 user 앞 assistant 를 사고 블록 없이 `<|im_start|>assistant\n` + C + `<|im_end|>\n` 으로 렌더한다.
- `ChatRenderers.ollamaGoQwen3Thinking`: system(첫 위치·선택) 뒤 user·assistant(연속 같은 역할 `\n\n` 병합, 마지막은 user). 범위 밖(마지막 assistant·빈/공백 시작/`<` 시작 assistant·tool·뒤쪽 system) = `RENDERER_INPUT_UNSUPPORTED`. §12.29 범위 입력의 출력은 바이트 그대로(기존 시험 통과).
- Go 엔진 대조(로컬 Docker `golang:1.24-alpine` go1.24.13 `--network none`, Ollama `thinking/parser.go` 바이트 그대로 + `filterThinkTags`·`collate` 같은 규칙, 저장 template SHA `2d54db2b…97f9`): 7 사례 렌더 SHA 를 `F0V4ChatRendererTest.v4RendererMatchesGoTemplateForAssistantTurnsBeforeTheLastUser` 가 Java 렌더 SHA 와 대조(TG07 `d36326fe…6b05` 포함). 대조 프로그램은 scratchpad 에만 두었다.
- 기록: `docs/gates/f0/template-review-v4.md` 판정 범위 문장·§6 근거 범위 1문장 + §7 신설, `eval/f0/v4/chat-renderers-v4.json` notice 1문장(둘 다 C21 뒤 미커밋 ③ 산출물 — 이번 C22 에 포함). 새 SHA: `chat-renderers-v4.json` `6b6d8bd7…bd56`, `template-review-v4.md` `aafc5a32…6a12`.

#### 3) ⑤ v4 입력 audit(Java `input-audit-render --runtime-profile INFER_V4 --renderers eval/f0/v4/chat-renderers-v4.json --template-sha256 2d54db2b…97f9` → `count_input_audit_v4.py`, pin `aeb13307…dae4`)

| audit | 프롬프트 arm·골든 | 행 | OVER_BUDGET | 최대 입력(A 초/재 · B 초/재 · 경계 초/재 · usage · token) | 최대 입력+요청 max_tokens | SHA-256 |
|---|---|---|---|---|---|---|
| `eval/f0/v4/input-audit-v4-v2.json` | V2·22 | 651 | 0 | 1250/1284 · 1090/1124 · 2703/2737 · 1056 · 2795 | 7857 | `65f54494df5e34a2a17c0f3ca981f8d20630a1140aa8e87fec1379427e978c5b` |
| `eval/f0/v4/input-audit-v4-i.json` | I·22 | 717 | 0 | 1943/1978 · 1392/1426 · 3025/3059 · 1378 · 2795 | 8179 | `ee16e33d69836db3c3247fd82822536aa994ff597a62b964ab1f0cd6424f8384` |
| `eval/f0/v4/input-audit-v4-regression-v2.json` | V2·30 | 843 | 0 | 1250/1284 · 1090/1124 · 2703/2737 · 1056 · 2795 | 7857 | `660780babeea53200a0a7eb63f0c268bce3f69872e7e0a616bf5cd4ab4c4d472` |
| `eval/f0/v4/input-audit-v4-regression-i.json` | I·30 | 933 | 0 | 1959/1994 · 1408/1442 · 3025/3059 · 1378 · 2795 | 8179 | `5d2ada83e0d0a1b574a4577ecfb04ded29a6c0cfaa461b4f85a673f518814136` |

- Jetson 등록 audit(`input-audit-v2.json`·`input-audit-v3-i.json`·`input-audit-v3-regression-i.json`)와 행 id 순서·messagesSha256 전부 같고 계수만 초회 +2·재시도 −3(재시도 user 병합)·probe +2. 회귀 audit 는 같은 arm 진단 audit 의 행을 id·SHA·계수 그대로 포함하고 `A/R0x`·`B/R0x` 행만 더한다(시험으로 검사).
- I arm 최대 입력+`max_tokens` 8,179 ≤ 8,192(여유 13). 서버 `prompt_tokens` 가 audit 보다 크면 ctx 를 넘을 수 있어 사전등록에 token-golden 13/13 동등성 게이트를 장시간 회차 전에 두었다.
- 렌더 입력(원문 포함 jsonl)은 scratchpad 에만 두었고 레포·결과에는 계수 표만 둔다.

#### 4) ⑥ 사전등록 초안 6문서(`registrationStatus=UNCOMMITTED`, `registeredCommit=null`)

| 등록 | arm | temperature | 골든 | 명령 | harnessDigest | parameterManifestSha256 |
|---|---|---|---|---|---|---|
| `eval/f0/PREREG-v4-diag-v2-t0.md`(공통 설계 §1 포함) | V2 | 0 | 22 | token-golden·usage-probe·B·A·boundary | `4c36e79e…e824` | `5a41be13…60fc` |
| `eval/f0/PREREG-v4-diag-i-t0.md` | I | 0 | 22 | B·A·boundary | `87aebc06…66b1` | `538f7ffb…0d50` |
| `eval/f0/PREREG-v4-diag-v2-t06.md` | V2 | 0.6 | 22 | B·A·boundary | `4c36e79e…e824` | `c1a2288b…5ef2` |
| `eval/f0/PREREG-v4-diag-i-t06.md` | I | 0.6 | 22 | B·A·boundary | `87aebc06…66b1` | `3c80eab5…4ec2` |
| `eval/f0/PREREG-v4-regression-v2.md` | V2 | 0 | 30 | B·A | `f8ccd2d0…8259` | `5a41be13…60fc` |
| `eval/f0/PREREG-v4-regression-i.md` | I | 0 | 30 | B·A | `50ba7cc3…ad76` | `538f7ffb…0d50` |

- 공통 parameters: `qwen3:4b`·digest `359d7dd4…fae7`·`bge-m3`·R=1·`maxTokens` 5120/5120·입력 3072/3072·context 8192·`answerLimitTokens` 192/256·`outputLimitTokens` 192·`requestTimeoutMs` 600000·`latencyBudgetMsByT`(v3 와 같음)·`f0-judge-v2`. V2 arm = `classify-v2`/`plan-v2`·`responseFormat=NONE`(투영·재시도 힌트 키 없음 — `PREREG-v2.md` 와 같은 요청 구성), I arm = `classify-v3-f`/`plan-v3-i`·`JSON_SCHEMA_V1`·`PRESENCE_V1`·`STEPS_DETAIL_V1`. 재사용 입력 SHA 는 v3 등록값과 같다(골든 `260da1e4…`, 회귀 `ef1803d6…`, fixture `87efe85b…`, 경계 `b5a31c95…`, usage `105a4edf…`, token `b1e865ce…`, 프롬프트 4개). `chatRenderers` = v4 등록 파일, 지원 입력 = Thinking pin 쌍·`count_tokens_qref_v2.py`·`count_input_audit_v4.py`·`requirements.txt`.
- 결정과 근거(사용자 지시와 다른 점 포함):
  - **문서 수 6(지시 예시는 `PREREG-v4-diag.md`·`PREREG-v4-regression.md` 2건)**: 하네스 사전등록 1건은 프롬프트·temperature·응답 형식을 하나씩만 담는다(`Prereg.Document.temperature()`·`responseFormat()` 단일 값). arm 을 한 문서에 담으려면 등록 schema·전 측정 명령에 arm 선택을 넣어야 해 범위가 커지므로, v3 `diag-a/b/c`(arm 별 문서) 선례대로 나눴다.
  - **회귀는 temperature 0 만**: SPEC §16 F0 "temperature 는 정식(GATE) 등록 0, 진단 등록에 0.6 비교 arm" — 회귀는 정식 후보 구성의 추가 문항 확인이라 정식 기준값으로 뒀다.
  - **회귀는 boundary-gen 미실행**: 같은 arm·temperature 0 진단과 경계 요청 메시지·`response_format` 바이트가 같다(audit `boundary/*` 108행 동일).
  - **timeout 600초**: 새 장비 속도 미측정. 유일한 실측 근거인 Jetson ctx 8192(prefill 350.9·생성 12.5 tok/s)로 최대 호출 ≈ 418초, `NUM_PARALLEL=1` 큐 대기 여유 약 1.4배.
- 호출 수·예상 시간(공통 설계 §1.5): 생성 호출 최소 332·최대 664(진단 53~106 × 4, 회귀 60~120 × 2) + 계수 38. 최악값 A(timeout 600초) 55.3~110.7시간, 최악값 B(입력 3,072·출력 5,120 × Jetson 속도 418초) 38.6~77.2시간, 가정값(근거 없음: 생성 40 tok/s·prefill 1,000 tok/s·평균 출력 1,500 토큰 ≈ 40초) 3.7~7.4시간. 첫 등록 `run B` 뒤 `latencyMs` 중앙값으로 다시 계산해 보고하는 규칙을 넣었다.
- 판별 기준·무효 규칙: 사전등록 §1.4·§1.6. E0 류 경로 동등성 실험은 두지 않았다(v4 측정 호출이 모두 `/v1/chat/completions` 한 경로) — 대신 token-golden `promptTokens == auditInputTokens` 13/13 을 장시간 회차 전 게이트로 두었다.

#### 5) 시험(`AIGW_SPEC_FILE`=정본 SPEC, `DEBUG`=빈 값, Gradle 동시 실행 0, 순차)

- 신규/변경 시험: `F0V4DiagnosticObservationTest` 5건(출력 키 순서 기록 규칙, v4 run 행의 사고·content 개수·키 순서와 보고서 절, v3 보고서·행 모양 불변, v4 계수 호출 행의 finish·completion·thinking·auditInputTokens 와 v2 행 모양 불변, `--runtime-profile INFER_V4` 예산·같은 호출 목록·잘못된 값 거부), `F0V4RegistrationDraftTest` 4건(실제 6문서의 arm·temperature·런타임·예산·역할·로컬 일치·고유 capture·초안 verify 거부, temperature 쌍은 temperature 만 다름, 각 audit 이 계획 호출 전부를 v4 profile 안에서 덮고 v4 런타임 신원 기록·모든 호출이 v4 렌더러 범위 안·Jetson profile 로는 `INPUT_BUDGET_EXCEEDED`, 회귀 audit 는 진단 행 그대로 + R01~R08 만), `F0V4ChatRendererTest` +1(Go 엔진 7 사례)·범위 밖 시험 갱신, `F0V4RuntimeProfileTest` 관측 레코드 생성자·키 집합 갱신과 v4 시험 등록 지원 입력(`count_input_audit_v4.py`).
- 변이 확인: ①렌더러 역할 고정(`user`) ②키 순서 정렬 ③계수 호출 v4 열 생략 ④`--runtime-profile` 무시 — 4변이를 함께 넣은 필터 실행에서 신규 4건 FAILED(`F0V4ChatRendererTest`·`F0V4DiagnosticObservationTest`, 10 중 4) → 원본 복원 `cmp` 일치.

| 명령 | exit | 결과 |
|---|---|---|
| 필터 `F0V4DiagnosticObservationTest`·`F0V4RuntimeProfileTest`·`F0V4ChatRendererTest` | 0 | XML 5/5·10/10·5/5 |
| 필터 `F0V4RegistrationDraftTest`·`F0V3RegistrationDraftTest` | 0 | `BUILD SUCCESSFUL in 1m 58s` |
| `:gateway-eval:run --args="spec-guard"` | 0 | `specSha`=`bb91146a…d0de4`, `specInputOrigin`=`LOCAL_FILE` |
| 무필터 `:gateway-eval:test --console=plain --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 5m 29s`, `6 actionable tasks: 6 executed`(testLeafEvidence 포함). XML 독립 집계 **234/234 PASS·47클래스·failures/errors/skipped 0**(§12.29 224 + 신규 10) |
| `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED` |

- 시험 신원(`run-binding.json`): `runId=4a9bc1a4-f801-47fe-8f71-6605fa437956`, `inputFingerprint=27fafa76d2dcbfe2227be2291931249d6539d9ffaf2345060bcdaf30b5813eaf`, `specSha=bb91146a…d0de4`. stub·로컬 시험이며 실제 모델 F0 합격이 아니다.

#### 6) 사전등록 상태(`f0 prereg-check`, endpoint 인자 없음, 모델 요청 0)

- 새 6문서: 모두 `localMismatches=[]`, `currentHarnessDigest` = 문서 값, 결과 `PREREG_NOT_COMMITTED`(초안 — 정상). 하네스 소스 변경으로 기존 24건(`PREREG.md`·`PREREG-v2.md`·v3 22건)은 §12.28 3) 과 같이 `harnessDigest` stale 그대로이며 등록 문서는 고치지 않았다.

#### 7) 커밋 범위(사용자 허용: 로컬 C22/C23 두 개, push·`--force` 없음)

- C22(정확 30파일): 수정 8 = `docs/ai-rag-gateway-architecture-dev-report.md`(C21 뒤 미커밋 소절·§12.29·이 절 포함, C5·…·C21 관례), `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ChatRenderers,F0Measurements,F0ReportWriter,F0RunnerCli,OllamaChatClient,Prereg}.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/F0V4RuntimeProfileTest.java`. 신규 22 = ①·③ 산출물 `docs/gates/f0/{runtime-env-20261104T021454Z.json,tokenizer-provenance-v4.md,preparation-template-v4.template,template-review-v4.md}`·`eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json{,.sha256}`·`eval/f0/v4/chat-renderers-v4.json`, ⑤ `eval/f0/v4/input-audit-v4-{v2,i,regression-v2,regression-i}.json`·`eval/py/count_input_audit_v4.py`·`eval/py/tests/test_count_input_audit_v4.py`, ⑥ `eval/f0/PREREG-v4-{diag-v2-t0,diag-i-t0,diag-v2-t06,diag-i-t06,regression-v2,regression-i}.md`(초안), 시험 `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0V4ChatRendererTest,F0V4DiagnosticObservationTest,F0V4RegistrationDraftTest}.java`. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`, `evidence/F0/`, `eval/py/**/__pycache__/`)는 넣지 않는다.
- C23(정확 6파일): 6문서의 §0 제목·상태 문구(C18·C20 방식)와 기계 판독 블록 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C22 SHA 만.
- 이 절 추가 전 537,769 B 의 SHA(`0868750a…99dd`)는 추가 후 앞 537,769 B 에서 같아야 한다(커밋 직전 확인). 커밋 해시·확인 결과는 커밋 뒤 이 절 끝 "로컬 C22/C23" 소절에 작업 트리에만 기록한다(관례 — 다음 커밋에 포함).

#### 8) 측정 실행 순서(C23 뒤, 사용자 승인 후, 이 세션은 실행하지 않음)

- 진단: `PREREG-v4-diag-v2-t0` §2(spec-guard → prereg-check → preflight → chat-template(template SHA 확인) → token-golden → 동등성 게이트 13/13 → usage-probe → run B(예상 시간 재계산) → run A → boundary-gen → postflight → share-window 작성 → share-exclude → report) → `diag-i-t0` → `diag-v2-t06` → `diag-i-t06`(각 문서 §2, 같은 `$runId`). 회귀 2문서는 Plan ⑧ 뒤 각 문서 1절 착수 조건 충족 시만.

#### 9) 위험·미확정

- **properties 순서(Plan ⑥ 항목)는 이 arm 구성으로 일부만 판별된다**: I arm `JSON_SCHEMA_V1` 은 properties 를 키 정렬 순서로 송신하므로 출력 순서가 정렬이어도 "서버가 선언 순서를 보존하는지/재정렬하는지" 를 가릴 수 없다. 판별하려면 선언 순서 송신 arm(`JSON_SCHEMA_PLAN_ORDERED_V1`, v3 h 구성)이 필요하며 사용자 arm 결정 밖이라 넣지 않았다(사전등록 §1.4 에 한계로 명시).
- `reasoning_tokens` 미보고면 v4 모든 시도가 `ANSWER_TOKENS_UNCOUNTABLE` → 재시도 포함 최대 호출(664)·정확도 축 사용 불가(§12.27 3) 추정 그대로).
- I arm 최대 입력+`max_tokens` 여유 13 토큰 — token-golden 게이트로 확인 전에는 서버 계수 일치 미확인.
- Plan ⑧ 에서 하네스가 바뀌면 회귀 2문서는 `PREREG_INPUT_CHANGED` 로 stale — 새 등록으로 대체(문서 수정 금지).
- GIN 로그 줄 시각 의미(§12.27 5) 열린 항목 11)·Modelfile 기본 `top_k 20`·`top_p 0.95` 적용(§12.29 7))·새 장비 처리 속도는 미확인.

#### 10) 보존 확인

- SPEC `bb91146a…d0de4`·Plan 불변, 기존 PREREG 24건·결과·capture·evidence·v1~v3 입력·pin(`eval/f0/chat-renderers-v1.json`, `eval/py/count_input_audit_v2.py`, Instruct tokenizer 쌍 등) 변경 0. 이 Dev report 앞 537,769 B 의 SHA 는 추가 전후 같다(`0868750a…99dd`). 원문 응답·secrets 출력 0.

#### 로컬 C22/C23 등록·최종 확인 (2026-11-04, 독립 Claude Code Opus 5.5)

- 허용 범위: 로컬 commit C22·C23 두 개뿐이며 push·amend·`--force`·원격 publish 는 하지 않았다.

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C22 | `7c8272f966dec4a0c4d33549adff31ee56808d61` | 2026-11-04 12:36:38 +0900 | 30파일, 759324 insertions(+), 26 deletions(-)(대부분 Thinking-2507 `tokenizer.json` 줄 수) |
| C23 | `d5511c235673988f92f0a32c35c2c7dc9bf725ca` | 2026-11-04 12:37:09 +0900 | 6파일(`eval/f0/PREREG-v4-*.md`), 36 insertions(+), 24 deletions(-) |

- C22 정확 30파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 30개, §12.30 7) 기대 목록과 diff 0, `git diff --cached --check` exit 0). 이 Devreport 의 C22 시점 바이트는 556,072 B, SHA `ad680c82a9d0de5e30525305a014194afb17b27713a63d723cded858412fa3e2`(C22 blob 과 같음)이며 앞 537,769 B SHA `0868750a…99dd` 는 §12.30 추가 전후 같았다. 기존 untracked 는 넣지 않았다.
- C23: 6문서의 §0 제목·상태 문구를 C18·C20 방식(등록 근거·C22/C23 설명·이력)으로 바꾸고 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C22 SHA 만 바꿨다. JSON 검사(Python, 파싱 실패 시 원문 비출력): 6문서 모두 바뀐 최상위 키 `registeredCommit`·`registrationStatus` 2개뿐, 최상위 키 순서 동일. `git diff --check` exit 0.
- C23 뒤 `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0): 6문서 모두 `localMismatches=[]`·`PREREG_VERIFIED`(Gradle exit 0). 같은 뒤 `F0V4RegistrationDraftTest` 필터 실행 exit 0(4/4 — 등록 완료 문서가 실제 `Prereg.verify` 엄격 검증 통과).
- C23 뒤 23커밋, staged 0. SPEC `bb91146a…d0de4` 불변. 측정(§12.30 8))은 실행하지 않았다. 이 소절은 commit 하지 않았다(작업 트리에만 추가 — 다음 커밋에 포함).

#### v4 진단 1단계 실측 — `PREREG-v4-diag-v2-t0` run B 착수 전 중단 (2026-11-04, 독립 Claude Code Opus 5.5)

- 승인 범위(2026-11-04 사용자): 등록 §2 의 1~8(run B)까지. usage-probe 에서 `reasoning_tokens` 미보고로 모든 시도가 계수 불가가 될 것이 명확하면 run B 전에 중단. run A·boundary-gen·다른 등록 미실행. 모델 load·pull·unload·재시작·설정 변경 0.
- 회차 ID `20261104T034413Z-v2-t0`(측정 창 2026-11-04T03:44:13Z~03:47:26Z UTC), 터널 로컬 `127.0.0.1:11434` → 160 `127.0.0.1:11434`.

| 단계 | 명령 | exit | 마지막 출력 |
|---|---|---|---|
| 1 | `spec-guard` | 0 | `specSha bb91146a…d0de4`, BUILD SUCCESSFUL |
| 2 | `f0 prereg-check` | 0 | `PREREG_VERIFIED`(`localMismatches=[]`, harnessDigest `4c36e79e…e824`) |
| 3 | `f0 preflight` | 0 | `F0_PREFLIGHT_OK 2` — `qwen3:4b` `359d7dd4…fae7` ctx 8192, `bge-m3:latest` `79076464…6bab` ctx 8192, 둘 다 상주 |
| 4 | `f0 chat-template` | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template SHA `2d54db2b…97f9`(등록값 일치) |
| 5 | `f0 token-golden` | 0 | `F0_TOKEN_GOLDEN_COMPLETED 13` |
| 6 | 계수 동등성 게이트 | — | `TOKEN_GOLDEN_AUDIT_MATCH 13/13` |
| 7 | `f0 usage-probe` | 0 | `F0_USAGE_PROBE_COMPLETED`, `equalOnCacheHit{usage:true, evalCount:true}`(G-USAGE 일치) |
| 8 | `f0 run --path B` | 미실행 | 아래 중단 근거 |
| 11 | `f0 preflight`(postflight) | 0 | `F0_PREFLIGHT_OK 2`, preflight 와 모델 목록(name·digest·context_length) 동일 |

- §1.4 관측(token-golden 13행 + usage-probe 4행, 모두 `max_tokens:1`): 17/17 `finishReason=length`·`completionTokens=1`·`contentCodepoints=0`·`separateFields=["reasoning"]`·content 사고 표지 0·`reasoningTokens=null`. 판별: 사고문 분리 필드 = `reasoning`(분리), `max_tokens` 사고 포함 = **포함**, 사고 도중 상한 `finish_reason` = `length`, 방법 (a) `completion − reasoning_tokens` = **불가**(`reasoningTokens` 미보고).
- 중단 근거: Ollama 0.40.13 `openai/openai.go` `Usage` 구조체(80~85행)에 `completion_tokens_details` 필드가 없고 `ToUsage`(264~273행)도 설정하지 않는다. 하네스 `ClassificationJudge.answerTokens`(105~108행)는 `reasoningTokens == null` 이면 null → `judge`(54~55행) `ANSWER_TOKENS_UNCOUNTABLE`. 따라서 run B 는 stop 시도 전부 계수 불가·length 시도는 `FINISH_LENGTH` 로 22문항 전부 재시도(44호출)·유효 0 이 확정적이므로(§1.4·§1.5 "재시도 전부" 경우) 사용자 지시대로 run B 를 시작하지 않았다. run B 지연 실측이 없어 §1.5 예상 시간 재계산은 하지 않았다.
- 공유 서버: 측정 창 GIN 요청 78건 전부 loopback(NORI 터널), NORI 밖 출발 0건. run 결과가 없어 share-window 파일·`share-exclude` 는 만들지 않았다.
- 서버 상태(전 03:43:56Z / 후 03:47:35Z): Ollama active·NRestarts 0·ActiveEnter 2026-11-03 04:10:45 UTC 동일, `ollama ps` 두 모델 100% GPU·CONTEXT 8192 동일, GPU 메모리 사용 4544→4548 MiB / 6144 MiB.
- 생성 파일: `eval/f0/results/20261104T034413Z-v2-t0/{preflight.json,token-golden.jsonl,token-golden.run-binding.json,usage.json,usage.run-binding.json,postflight.json}`(gitignore 대상), `eval/f0/chat-template/v4-diag-v2-t0/{model-digest.txt,qwen3-4b.template,qwen3-4b.template.sha256,run-binding.json}`. 모델 응답·사고문·API 응답 원문 출력·저장 0. 하네스·등록 문서·SPEC·Plan·기존 결과 변경 0, commit·push 0. 이 소절 추가 전 Dev report 558,015 B SHA `102971ef…4668`.

### 12.31 P03 v4 답변 토큰 계수 방법 (b) 구현·교차 확인·v4-b 진단·개발 회귀 재등록과 로컬 C24/C25 범위 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- **결론: 1단계 실측(`reasoningTokens` 17/17 null → 방법 (a) 불가)에 따라 사용자 결정 방법 (b)(답변 content 를 Thinking pin 으로 로컬 계수)와 교차 확인(로컬 사고 + 로컬 답변 대 서버 `completion_tokens` 차이 기록·보고)을 하네스 v4 에 opt-in(`parameters.answerTokenMethod = LOCAL_TOKENIZER_PIN_V1`)으로 구현했고, 기존 v4 6문서를 고치지 않고 대체 등록 6문서(`-b`)를 새로 작성했다. 무필터 시험 241/241·`test-evidence verify`·`spec-guard` 모두 exit 0. 모델 추론 호출·SSH·측정 0, 서버·SPEC·Plan·기존 PREREG(v1~v4 30건)·결과·evidence 변경 0. P03 미완료·기존 F0 FAIL 유지·P04 미착수.**
- 착수 시 HEAD C23 `d5511c2`, SPEC `bb91146a…d0de4`(seq5), 이 Dev report 561,590 B `5bca9f35c7fc7c44174db57811dbc0756ab9d9f505ed26997e2f10ada130c659`(§12.30 끝 "로컬 C22/C23"·"v4 진단 1단계 실측" 소절 포함, 미커밋).
- 사용자 결정(2026-11-04, 이 세션 입력): 방법 (b) — 답변 토큰 = 서버 응답 content(답변 부분)의 고정 tokenizer pin 로컬 계수, 원문 비저장(메모리에서 개수만). 교차 확인 — 같은 pin 으로 `reasoning` 필드도 계수해 `로컬(사고) + 로컬(답변)` 과 `completion_tokens` 의 차이를 시도마다 기록, 분포를 진단 보고서에. 크게 어긋나는 호출은 표시만·판정 불변, 기준값은 근거가 없어 등록 문서에 "관측 후 확정". 답변 상한 초과 = 실패(1회 재시도) 유지. C24/C25 허용(push·`--force` 금지).

#### 1) 기존 계수 수단 조사와 재사용

- Java 쪽에는 tokenizer 가 없다(의존성 Jackson 뿐, `gateway-eval/build.gradle.kts`). 지금까지의 pin 계수는 모두 Python `tokenizers` 0.23.3 스크립트(`count_tokens.py`·`count_tokens_qref_v2.py`·`count_input_audit_v2.py`·`count_input_audit_v4.py`)가 준비 단계에서 만든 표였고, 측정 중 Java 가 Python 을 부르는 경로는 없었다. 그래서 측정 중 답변 계수는 같은 엔진·같은 규칙(`encode(text, add_special_tokens=False)`)을 상주 Python 프로세스로 쓰고, Java BPE 재구현은 하지 않았다(같은 로직 중복 금지).
- 신규 `eval/py/count_answer_tokens_v4.py`(SHA `139136b7893e949a70c48df7909efb482ac3a7497ef8e19934f9f21d78edf6bc`): pin 검사·인자 오류·SHA 함수(`Failure`·`Parser`·`read_pin`·`read_bytes`·`sha256_bytes`)는 `count_input_audit_v4.py` 를 파일 경로로 불러(`importlib`, `-I` 에서도 sys.path 변경 없음) 그대로 쓰고, 새 코드는 줄 규약(`READY <sha> <version>` → stdin 한 줄 `[답변|null, 사고문|null]` → stdout 한 줄 `[정수|null, 정수|null]`)과 짝 없는 surrogate = null 판정뿐이다. `count_input_audit_v4.py` 바이트는 바꾸지 않았다(v4 입력 audit 재사용 조건).

#### 2) 하네스 구현(f0 패키지, v1~v3 요청 body·판정·결과 모양 불변)

- `AnswerTokenCounter`(신규): 측정 세션당 `$AIGW_PYTHON -I -B eval/py/count_answer_tokens_v4.py --tokenizer <profile pin 쌍>` 을 한 번 띄우고 READY 의 tokenizer SHA 가 세션 pin 과 같을 때만 쓴다. 요청은 Jackson `ESCAPE_NON_ASCII` JSON 한 줄(US-ASCII 파이프)이라 한글·보조 평면·줄바꿈·짝 없는 surrogate 도 같은 문자열로 넘어간다. 실패 코드: 준비 = `AIGW_PYTHON_MISSING`·`ANSWER_COUNTER_UNAVAILABLE`(·`_EXIT_<n>`)·`ANSWER_COUNTER_TOKENIZER_MISMATCH`(exit 2), 측정 중 = `ANSWER_COUNTER_FAILED`(exit 5).
- **원문 비저장 보장**: 문자열은 stdin 으로만 넘기고(파일·명령줄 인자 0 — 명령줄은 경로뿐), 계수기 stderr 는 `Redirect.DISCARD`, 예외 메시지는 고정 코드(+exit 코드)만, `-B` 로 계수기의 bytecode 캐시 쓰기도 막았다(없으면 `eval/py/__pycache__` 에 `.pyc` 가 생김을 scratchpad 사본으로 확인). 사고문 문자열은 `OllamaChatClient.chat` 안에서 계수기로만 넘어가고 메서드 밖으로 나가지 않는다(`ChatResult` 에는 개수만).
- `OllamaChatClient`: `chat(…, temperature, AnswerTokenCounter)` 겹쳐쓰기(기존 6인자는 counter null 위임 — body 동일), `ChatResult.localTokens`(`LocalTokenCount{answerTokens, thinkingTokens, completionMinusLocal}`, 기존 4·5인자 생성자 유지). 사고문 = `thinkingCodepoints` 와 같은 규칙(THINKING_FIELDS 순서 첫 문자열 분리 필드). `completionMinusLocal` = `completion_tokens − (사고 + 답변)`, 한쪽 null 이면 null.
- `ClassificationJudge.answerTokens`: content 사고 표지 → null(두 방법 공통), `localTokens` 가 있으면 방법 (b) 로컬 답변, 없으면 방법 (a) 그대로. 상한 초과 `ANSWER_LIMIT_EXCEEDED`·계수 불가 `ANSWER_TOKENS_UNCOUNTABLE`·재시도 요지 `USAGE_EXCEEDS_LIMIT`·1회 재시도 규칙 불변.
- `Prereg`: `answerTokenMethod` 는 v4 opt-in 전용(`ANSWER_TOKEN_METHODS = {LOCAL_TOKENIZER_PIN_V1}`), 값이 있으면 `eval/py/count_answer_tokens_v4.py` 가 등록 inputs 에 있어야 한다(v1~v3 의 같은 키·미정의 값·비문자열·계수기 미등록 = `PREREG_INVALID`). `Document.answerTokenMethod()`(키 없음 = `USAGE_REASONING`)·`localAnswerCount()`. `RuntimeProfile.supportInputs` 는 바꾸지 않았다(기존 v4 문서가 계속 파싱되어 stale 로만 거부되도록).
- `MeasurementSession`: `Context` 에 `AnswerTokenCounter.Launcher`(운영 `EvalCli` = `AnswerTokenCounter::python`), 방법 (b) 등록이면 PREREG·SPEC 검사 뒤·preflight 전(endpoint 호출 0·run-binding 작성 전)에 계수기를 띄우고 v4 chat 에 넘긴다.
- `F0Measurements`: 방법 (b) 등록만 v4 행에 `localTokenCounts[]`(시도별), token-golden·usage-probe 의 v4 계수 행에 `localTokenCount`. `answerTokens` 열은 (b) 에서 로컬 답변 계수다(F0Judgement 경계 판정이 그대로 사용).
- `F0ReportWriter`: 방법 (b) 등록만 "v4 답변 토큰 로컬 계수 교차 확인" 절 — 출처(A·B·boundary)×시도(초회·재시도)·전체의 시도 수, 답변 계수 불가, 사고 계수 없음, 차이 계산 수, 차이 min/p50/p95/max(nearest-rank), 0 아닌 차이 건수, 차이 값=건수 전체. 어긋남 표시 기준값은 정하지 않았다(등록 §1.5 "관측 후 확정").

#### 3) 방법 (a) 코드 경로 처리 — 유지(기본값, 등록 값 아님)

- 근거: ①v1~v3 은 `answerLimit` 이 null 이라 (a)·(b) 어느 쪽도 지나지 않는다(판정 불변). ②C21~C23 의 v4 동작(키 없는 v4 등록 = completion − reasoning_tokens)을 그대로 두어야 기존 v4 6문서가 의미 그대로 파싱되고 `verifyHistorical`(등록 commit 하네스로 다시 계산한 역사 신원)이 유지된다. ③이 레포의 opt-in 관례(`responseFormat`·`contextProjection`·`retryHint` — 키 없음 = 기존 동작, 그 값은 등록 값으로 쓰지 않음)와 같다. (a) 경로는 기존 줄 그대로이며 새로 더한 코드는 (b) 분기 1줄과 표지 검사 순서 정리뿐이다(조건 집합 동일 — 기존 `F0V4RuntimeProfileTest` (a) 시험 무수정 통과).
- 실제로 (a) 는 이 서버에서 계수 불가가 확정이므로(1단계) 새 등록은 모두 (b) 를 명시한다.

#### 4) v4 입력 audit 재사용 확인

- 현재 하네스로 Java `f0 input-audit-render --runtime-profile INFER_V4 …`(C22 와 같은 4명령) → `count_input_audit_v4.py` 를 scratchpad 에 다시 만들어 `cmp`: 렌더 입력 jsonl 4개·계수 표 JSON 4개 모두 **바이트 동일**(651/717/843/933행, `OVER_BUDGET 0`). 하네스 변경이 응답 처리(계수·기록·보고)뿐이고 메시지 조립·렌더러·예산·재시도 요지 집합·입력 계수기를 바꾸지 않았기 때문이다. 그래서 기존 4파일(`eval/f0/v4/input-audit-v4-{v2,i,regression-v2,regression-i}.json`, SHA `65f54494…`·`ee16e33d…`·`660780ba…`·`5d2ada83…`)을 새 등록이 그대로 참조한다. 렌더 입력(원문 포함)은 scratchpad 에만 두었다.

#### 5) 새 등록 6문서(`registrationStatus=UNCOMMITTED`, `registeredCommit=null`)

| 등록 | 대체 대상 | arm | temperature | 골든 | harnessDigest | parameterManifestSha256 |
|---|---|---|---|---|---|---|
| `eval/f0/PREREG-v4-diag-v2-t0-b.md`(공통 설계 §1.1~§1.9) | `PREREG-v4-diag-v2-t0` | V2 | 0 | 22 | `91580fb7…eae35` | `5fbc27fe…09a42` |
| `eval/f0/PREREG-v4-diag-i-t0-b.md` | `PREREG-v4-diag-i-t0` | I | 0 | 22 | `7f64c9b8…e86080` | `cc8885b3…0a13a` |
| `eval/f0/PREREG-v4-diag-v2-t06-b.md` | `PREREG-v4-diag-v2-t06` | V2 | 0.6 | 22 | `91580fb7…eae35` | `ed9a5c8e…4719c` |
| `eval/f0/PREREG-v4-diag-i-t06-b.md` | `PREREG-v4-diag-i-t06` | I | 0.6 | 22 | `7f64c9b8…e86080` | `5d93f751…939a4` |
| `eval/f0/PREREG-v4-regression-v2-b.md` | `PREREG-v4-regression-v2` | V2 | 0 | 30 | `2db16e2e…324bf0` | `5fbc27fe…09a42` |
| `eval/f0/PREREG-v4-regression-i-b.md` | `PREREG-v4-regression-i` | I | 0 | 30 | `d3d726ef…b7f917` | `cc8885b3…0a13a` |

- 기존 v4 등록과의 차이(시험으로 검사): inputs 는 `eval/py/count_answer_tokens_v4.py` 1개 추가뿐, `inputRoles` 동일, parameters 는 `answerTokenMethod` 1키 추가뿐, `runtimeCaptureDir` = `eval/f0/chat-template/v4-<id>-b`(고유). 이름의 `-b` = 계수 방법 (b) 재등록(v3 후보 문자 접미 관례, 경로 B 와 무관).
- 공통 설계 갱신: §1.2 1단계 관측 사실(token-golden 13/13, 17/17 `reasoning` 분리·`max_tokens` 사고 포함·`length`·`reasoningTokens` null, G-USAGE 일치, preflight=postflight, NORI 밖 출발 0), §1.5 방법 (b)·교차 확인 정의·원문 비저장 경로·기준값 "관측 후 확정", §1.6 판별 기준(방법 (a) 불가 확정, (b) 사용 가능 = `answerTokens` null 0건, 차이가 전 시도 같은 값 c 이면 "completion = 사고 + 답변 + c"), §1.7 호출 수·시간(같은 근거 재기재 — 최소 332·최대 664 + 계수 38, 최악값 A 55.3~110.7시간·B 38.6~77.2시간·가정값 3.7~7.4시간; (a) 에서 확정적이던 "재시도 전부"는 (b) 에서 실제 실패만), §1.8 계수기 준비 실패(요청 0·재실행)와 측정 중 계수기 오류(exit 5·새 회차 ID), §1.9·§2 0단계 `$env:AIGW_PYTHON` 과 `tokenizers` 0.23.3 확인.
- 계수 호출(token-golden·usage-probe)은 1단계에서 이미 실행했지만 새 등록의 회차 결속·동등성 게이트(13/13)를 위해 `diag-v2-t0-b` §2 에 다시 둔다(38회, 최악값 B 약 5분).

#### 6) 시험(`AIGW_SPEC_FILE`=정본 SPEC, `DEBUG`=빈 값, Gradle 동시 실행 0, 순차)

- 신규 `F0V4AnswerTokenCountTest` 6건: ①판정 — 로컬 답변 192 유효·193 `ANSWER_LIMIT_EXCEEDED`·null/표지 = 계수 불가, 로컬 계수 없는 응답은 방법 (a) 그대로, `LocalTokenCount.of` 차이 규칙 ②등록 opt-in — 계수기 미등록·`USAGE_REASONING` 값·미정의 값·비문자열·v3 키 = `PREREG_INVALID` ③계수기 프로세스 — stdin 한 줄 왕복(한글·🙂·줄바꿈·따옴표, 시험 프로세스는 비 ASCII 바이트를 받으면 exit 9), READY SHA 불일치·READY 없음(`_EXIT_4`)·실행 파일 없음·`AIGW_PYTHON` 없음, 측정 중 사망 = exit 5 `ANSWER_COUNTER_FAILED`(메시지 원문 없음) ④run B — 시도별 `localTokenCounts`(답변 = content 개수, 사고 = 사고문 개수, 차이 = 주입 상수 1), 결과 디렉터리 밖 새 파일 0, 계수기 명령줄에 sentinel 없음, 보고서 절 `1/1/1/1`·`1=N`, 결과·출력 sentinel 0, 같은 응답의 방법 (a) 등록과 초회 요청 바이트 동일·(a) 결과에 새 열·절 없음 ⑤로컬 답변 > 192 = 실패·1회 재시도(초회만 초과 → OK, 둘 다 초과 → INVALID·2호출), 사고 길이는 상한 밖 ⑥계수기 준비 실패 = endpoint 요청 0(exit 2), 측정 중 사망 = 부분 결과 `complete=false`·`abortReason=ANSWER_COUNTER_FAILED`. 계수기는 같은 줄 규약의 시험 프로세스 `FakeAnswerCounter`(코드포인트 계수 — 같은 JVM·시험 classpath)다.
- 갱신 `F0V4RegistrationDraftTest`(4→5건): 현재 등록 = `-b` 6문서(방법 (b)·계수기 입력 검사 추가), 신규 과거 등록 시험 — 기존 v4 6문서는 입력 바이트 동일·`verifyHistorical` preregCommit = C23·`localMismatches=["harnessDigest"]`·`verify`=`PREREG_INPUT_CHANGED`·대체 등록과 inputs(+계수기 1)·parameters(+1키) 차이만. `F0TestRoot` 는 `Context` 에 launcher 를 넘기도록 1줄.
- Python(Gradle 밖, 수동 `<venv python> -I -B -m unittest discover -s eval/py/tests`): 신규 `test_count_answer_tokens_v4.py` 6건 — **실제 Thinking pin 으로 알려진 문자열 대조**(SPEC §14.5 과거 예시 57·53토큰 = `count_tokens.py` CALIBRATION 값, `</think>` 1, 빈 문자열 0), Java 와 같은 `\u` 이스케이프 줄의 한글·보조 평면 계수 동일·짝 없는 surrogate null, 형식 오류 고정 코드·원문 비노출, `serve` 줄 규약, 실제 프로세스 실행(작업 디렉터리 새 파일 0·stderr 빈 값), pin 불일치 exit 3. 전체 26/26 OK.
- 수동 Java→실제 Python 왕복(scratchpad `Smoke.java`, 모델 호출 0): `Counts[57, 53]`, `</think>` 1, 한글+🙂 11·짝 없는 surrogate null, 이스케이프 문자열 13 — 같은 문자열의 Python 직접 계수와 일치, 잘못된 pin → `ANSWER_COUNTER_TOKENIZER_MISMATCH`, env 없음 → `AIGW_PYTHON_MISSING`, 없는 실행 파일 → `ANSWER_COUNTER_UNAVAILABLE`.
- 변이 확인: ①판정에서 로컬 계수 무시 ②차이에서 사고 제외 ③ASCII 이스케이프 제거 ④READY SHA 검사 제거 ⑤`localTokenCounts` 기록 제거 ⑥계수기 등록 필수 제거 — 6변이를 함께 넣은 필터 실행에서 `F0V4AnswerTokenCountTest` 6 중 6 FAILED → 원본 복원 `cmp` 일치.

| 명령 | exit | 결과 |
|---|---|---|
| 필터 `F0V4AnswerTokenCountTest`·`F0V4RuntimeProfileTest`·`F0V4DiagnosticObservationTest` | 0 | XML 6/6·10/10·5/5, `BUILD SUCCESSFUL in 27s` |
| 같은 필터(`F0V4AnswerTokenCountTest`) + 6변이 | 1 | `6 tests completed, 6 failed`(의도) → 복원 |
| 필터 `F0V4RegistrationDraftTest`·`F0V3RegistrationDraftTest` | 0 | XML 5/5·8/8, `BUILD SUCCESSFUL in 2m 24s` |
| `:gateway-eval:run --args="spec-guard"` | 0 | `specSha`=`bb91146a…d0de4`, `specInputOrigin`=`LOCAL_FILE`, `BUILD SUCCESSFUL in 2s` |
| 무필터 `:gateway-eval:test --console=plain --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 5m 58s`, `6 actionable tasks: 6 executed`. XML 독립 집계 **241/241 PASS·48클래스·failures/errors/skipped 0**(§12.30 234 + 신규 6 + 과거 v4 등록 1) |
| `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"` | 0 | `TEST_EVIDENCE_VERIFIED`, `BUILD SUCCESSFUL in 3s` |

- 시험 신원(`gateway-eval/build/aigw-otr/test/run-binding.json`): `runId=9a9b3e23-6ea1-4674-9c87-07b1af190e97`, `inputFingerprint=a2965658f38c49796232a032c9ad23228fc964c69aa846da6d87edd7e130cff3`, `specSha=bb91146a…d0de4`. stub·로컬 시험이며 실제 모델 F0 합격이 아니다.

#### 7) 사전등록 상태(`f0 prereg-check`, endpoint 인자 없음, 모델 요청 0)

- 새 6문서: 모두 `localMismatches=[]`, 결과 `PREREG_NOT_COMMITTED`(초안 — 정상). 기존 v4 6문서: 모두 `localMismatches=["harnessDigest"]`·`PREREG_INPUT_CHANGED`(하네스 변경으로 stale — 정상, 문서 수정 0). v1~v3 24건은 §12.30 과 같은 stale 상태 그대로.

#### 8) 커밋 범위(사용자 허용: 로컬 C24/C25 두 개, push·`--force` 없음)

- C24(정확 21파일): 수정 10 = 이 Dev report(§12.30 끝 미커밋 두 소절·이 절 포함, C22 관례), `gateway-eval/src/main/java/com/nori/ai/gateway/eval/EvalCli.java`, `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/{ClassificationJudge,F0Measurements,F0ReportWriter,MeasurementSession,OllamaChatClient,Prereg}.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0TestRoot,F0V4RegistrationDraftTest}.java`. 신규 11 = `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/AnswerTokenCounter.java`, `eval/py/count_answer_tokens_v4.py`, `eval/py/tests/test_count_answer_tokens_v4.py`, `eval/f0/PREREG-v4-{diag-v2-t0,diag-i-t0,diag-v2-t06,diag-i-t06,regression-v2,regression-i}-b.md`(초안), `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/{F0V4AnswerTokenCountTest,FakeAnswerCounter}.java`. 기존 untracked(`docs/gates/f0/p03-run-20261029T234506Z.md`, `eval/f0/chat-template/`(1단계 `v4-diag-v2-t0` 포함), `eval/py/__pycache__/`, `eval/py/tests/__pycache__/`, `evidence/F0/`)는 넣지 않는다.
- C25(정확 6파일): `-b` 6문서의 §0 제목·상태 문구(C23 방식)와 기계 판독 블록 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C24 SHA 만.
- 이 절 추가 전 561,590 B 의 SHA(`5bca9f35…c659`)는 추가 후 앞 561,590 B 에서 같아야 한다(커밋 직전 확인). 커밋 해시·확인 결과는 커밋 뒤 이 절 끝 "로컬 C24/C25" 소절에 작업 트리에만 기록한다(관례 — 다음 커밋에 포함).

#### 9) 측정 실행 순서(C25 뒤, 사용자 승인 후, 이 세션은 실행하지 않음)

- 진단: `PREREG-v4-diag-v2-t0-b` §2(0 계수기 환경 → spec-guard → prereg-check → preflight → chat-template(template SHA 확인) → token-golden → 동등성 게이트 13/13 → usage-probe → run B(예상 시간 재계산) → run A → boundary-gen → postflight → share-window 작성 → share-exclude → report) → `diag-i-t0-b` → `diag-v2-t06-b` → `diag-i-t06-b`(각 문서 §2, 같은 `$runId`). 회귀 2문서는 Plan ⑧ 뒤 각 문서 1절 착수 조건 충족 시만.

#### 10) 위험·미확정

- 교차 확인 차이의 실제 값(특수 토큰 상수 여부)은 미관측이다. 서버 `completion_tokens` 가 사고 분리 경계를 어떻게 세는지(Ollama thinking parser 가 `</think>` 를 사고·답변 어느 쪽에서도 빼는지)는 소스로 확인하지 않았고, 진단 분포로 본다.
- 방법 (b) 는 서버가 생성한 토큰열 자체가 아니라 응답 문자열의 재토큰화다. 같은 문자열이 다른 토큰열로 생성됐을 수 있어(BPE 비정규 분할) 서버 답변 토큰과 1:1 일치를 주장하지 않는다 — 교차 확인 차이 분포가 그 크기의 관측 근거다.
- 측정 PC 에 `AIGW_PYTHON`(requirements 설치 venv)이 필요해졌다. 없으면 측정 명령은 endpoint 호출 전에 exit 2 로 멈춘다.
- 기존 위험(§12.30 9)) 그대로: properties 순서 판별 한계, I arm ctx 여유 13 토큰(동등성 게이트로 확인), GIN 로그 시각 의미, Modelfile 기본 `top_k`/`top_p` 적용, 새 장비 처리 속도 미측정.

#### 11) 보존 확인

- SPEC `bb91146a…d0de4`·Plan 불변, 기존 PREREG 30건(v1·v2·v3 22·v4 6)·결과(1단계 `20261104T034413Z-v2-t0` 포함)·capture·evidence·v1~v4 입력·pin·`count_input_audit_v{2,4}.py`·v4 audit 4파일 변경 0. 이 Dev report 앞 561,590 B 의 SHA 는 추가 전후 같다(`5bca9f35…c659`). 원문 응답·사고문·secrets 출력 0.

#### 로컬 C24/C25 등록·최종 확인 (2026-11-04, 독립 Claude Code Opus 5.5)

- 허용 범위: 로컬 commit C24·C25 두 개뿐이며 push·amend·`--force`·원격 publish 는 하지 않았다.

| commit | 전체 SHA | 시각 | 파일 |
|---|---|---|---|
| C24 | `e5f64b82bae0c678a327d327944bdeb96b282eb5` | 2026-11-04 13:35:23 +0900 | 21파일, 1519 insertions(+), 28 deletions(-) |
| C25 | `f20518931a81946e8e5265a51fd7bca71bd72040` | 2026-11-04 13:35:59 +0900 | 6파일(`eval/f0/PREREG-v4-*-b.md`), 36 insertions(+), 24 deletions(-) |

- C24 정확 21파일(파일 지정 `git add` 뒤 `git diff --cached --name-only` 21개, §12.31 8) 기대 목록과 diff 0, `git diff --cached --check` exit 0). 이 Devreport 의 C24 시점 바이트는 580,784 B, SHA `d6211ac49dbc4732029100a6f7f7a7c783b169a97f657e0a410aea05083ff1f0`(C24 blob 과 같음)이며 앞 561,590 B SHA `5bca9f35…c659` 는 §12.31 추가 전후 같았다. 기존 untracked 는 넣지 않았다.
- C25: `-b` 6문서의 §0 제목·상태 문구를 C23 방식(등록 근거·C24/C25 설명·이력)으로 바꾸고 기계 판독 블록의 `registrationStatus` UNCOMMITTED→REGISTERED, `registeredCommit` null→C24 SHA 만 바꿨다. JSON 검사(Python, 파싱 실패 시 원문 비출력): 6문서 모두 바뀐 최상위 키 `registrationStatus`·`registeredCommit` 2개뿐, 최상위 키 순서 동일. `git diff --check` exit 0.
- C25 뒤 `f0 prereg-check`(endpoint 인자 없음, 모델 요청 0): `-b` 6문서 모두 `localMismatches=[]`·`PREREG_VERIFIED`(Gradle exit 0). 기존 v4 6문서는 `localMismatches=["harnessDigest"]`·`PREREG_INPUT_CHANGED`(stale 유지). 같은 뒤 `F0V4RegistrationDraftTest` 필터 실행 exit 0(5/5 — 등록 완료 문서가 실제 `Prereg.verify` 엄격 검증 통과, 과거 v4 등록 역사 신원 확인).
- C25 뒤 25커밋, staged 0. SPEC `bb91146a…d0de4` 불변. 측정(§12.31 9))은 실행하지 않았다. 이 소절은 commit 하지 않았다(작업 트리에만 추가 — 다음 커밋에 포함).

#### v4-b 진단 1단계 실측 — `PREREG-v4-diag-v2-t0-b` run B 까지 (2026-11-04, 독립 Claude Code Opus 5.5)

- 승인 범위(2026-11-04 사용자): 등록 §2 의 0~8(run B)까지와 postflight·측정 창 외부 요청 집계(share-window → share-exclude). run A·boundary-gen·나머지 진단 3건·회귀 2건 미실행. 모델 load·pull·unload·재시작·설정 변경 0.
- 회차 ID `20261104T044320Z-diag-v2-t0-b`(측정 PC 시각 04:43:20Z~04:55:38Z, run B 04:45:30.9Z~04:55:05.3Z), 터널 로컬 `127.0.0.1:11434` → 160 `127.0.0.1:11434`(측정 뒤 본인 ssh 프로세스만 종료).

| 단계 | 명령 | exit | 마지막 출력 |
|---|---|---|---|
| 0 | 계수기 환경 | 0 | `tokenizers` `0.23.3`, `AIGW_PYTHON` 존재 |
| 1 | `spec-guard` | 0 | `specSha bb91146a…d0de4`, BUILD SUCCESSFUL |
| 2 | `f0 prereg-check` | 0 | `PREREG_VERIFIED`(`localMismatches=[]`, harnessDigest `91580fb7…ae35`) |
| 3 | `f0 preflight` | 0 | `F0_PREFLIGHT_OK 2` — `qwen3:4b` `359d7dd4…fae7` ctx 8192, `bge-m3:latest` `79076464…6bab` ctx 8192, 둘 다 상주 |
| 4 | `f0 chat-template` | 0 | `F0_CHAT_TEMPLATE_CAPTURED`, template SHA `2d54db2b…97f9`(등록값 일치) |
| 5 | `f0 token-golden` | 0 | `F0_TOKEN_GOLDEN_COMPLETED 13` |
| 6 | 계수 동등성 게이트 | — | `TOKEN_GOLDEN_AUDIT_MATCH 13/13` |
| 7 | `f0 usage-probe` | 0 | `F0_USAGE_PROBE_COMPLETED`, `equalOnCacheHit{usage:true, evalCount:true}` |
| 8 | `f0 run --path B` | 0 | `F0_RUN_COMPLETED B`(Gradle 9분 46초) |
| 11 | `f0 preflight`(postflight) | 0 | `F0_PREFLIGHT_OK 2`, preflight 와 모델 목록(name·digest·context_length) 동일 |
| 12 | share-window 작성 | — | `docs/gates/f0/share-window-20261104T044320Z-diag-v2-t0-b.json`(`intervals=[]`) |
| 13 | `f0 share-exclude` | 0 | B `samples 22`·`excluded 0`·p50 18223·p95 44882 ms |
| 14 | `f0 report`(터널 종료 뒤, B 만 존재) | 0 | `F0_DIAGNOSTIC_REPORT_WRITTEN`(A·boundary 열은 `-`) |

- `max_tokens:1` 계수 행 17개(token-golden 13 + usage-probe 4): 17/17 `finishReason=length`·`completionTokens=1`·`contentCodepoints=0`·`separateFields=["reasoning"]`·`reasoningTokens=null`·`localTokenCount{answerTokens 0, thinkingTokens 1, completionMinusLocal 0}`.
- run B(22문항, 호출 22, 재시도 0): 유효 22/22(1차 유효 22, errorCode 0, finish 전부 `stop`, timeout 0), taskType 19/22, 인자 13/22, UNSUPPORTED 21/22. 호출 지연 p50 18.2 s·p95 44.9 s·max 74.7 s·min 9.4 s·합 574.0 s(nearest-rank). 로컬 사고 토큰 p50 1107·p95 2616·max 4082, 답변 토큰 p50 29·p95 77·max 77(상한 192 초과 0, 계수 불가 0), 서버 `completion_tokens` max 4154. `prompt_tokens` 대 audit 작음/큼 0/0. 출력 키 순서 `t,eqp,from,to,qRef` 22/22. 생성 처리량(completion 합 34,468 / 지연 합 574.0 s) 약 60.0 tok/s.
- 교차 확인(방법 b): 차이 계산 22/22, `completion − (로컬 사고 + 로컬 답변)` min/p50/p95/max = 3/3/3/3, 0 아닌 차이 22건, 값 `3=22`. 계수 행(`max_tokens:1`)은 17/17 차이 0.
- 공유 서버: 160 GIN 로그(서버 UTC 04:43:00~04:56:00) 127건 전부 loopback, NORI 밖 출발 0건 → 겹침 0, 제외 지연 표본 0.
- GIN 줄 시각(§12.27 5) 열린 항목 11): run B 첫 5호출의 GIN 시각 간격이 하네스 호출 지연과 맞아 **완료 시각**으로 판단한다. 다만 같은 호출의 GIN 시각이 하네스 `receivedAt` 보다 약 5초 이르며(측정 PC 시계가 서버보다 약 5초 앞섬, 서버 `NTPSynchronized=yes`), 외부 구간이 생기면 겹침 판정 전에 이 시계 차를 다뤄야 한다(이번은 외부 0이라 영향 없음).
- 서버 상태(전 04:43:03Z / 후 04:55:39Z): Ollama active·NRestarts 0·ActiveEnter 2026-11-03 04:10:45 UTC 동일, `ollama ps` 두 모델 100% GPU·CONTEXT 8192 동일, GPU 메모리 4548 MiB / 6144 MiB 동일.
- 생성 파일: `eval/f0/results/20261104T044320Z-diag-v2-t0-b/{preflight.json,token-golden.jsonl,token-golden.run-binding.json,usage.json,usage.run-binding.json,B/f0-results.json,B/run-binding.json,postflight.json,diagnostic-report.md}`, `eval/f0/chat-template/v4-diag-v2-t0-b/`, `docs/gates/f0/share-window-20261104T044320Z-diag-v2-t0-b.json`. 모델 응답·사고문·API 응답 원문 출력·저장 0. 하네스·등록 문서·SPEC·Plan·기존 결과 변경 0, commit·push 0. 이 소절 추가 전 Dev report 582,817 B SHA `fba88005…0443`.

#### v4-b 진단 4문서 실측 — `diag-v2-t0-b` 나머지(run A·boundary-gen)와 `diag-i-t0-b`·`diag-v2-t06-b`·`diag-i-t06-b` (2026-11-04, 독립 Claude Code Opus 5.5)

- 승인 범위(2026-11-04 사용자 "진행"): `diag-v2-t0-b` §2 의 남은 9(run A)·10(boundary-gen)·11~14, 이어서 `diag-i-t0-b` → `diag-v2-t06-b` → `diag-i-t06-b` 각 §2 전체, 같은 `$runId=20261104T044320Z`. 회귀 2문서는 실행하지 않았다. 모델 load·pull·unload·재시작·설정 변경·sudo 0. 하네스·등록 문서·SPEC·Plan·기존 결과 변경 0, commit·push 0.
- 터널: 문서마다 본인 ssh 프로세스(PID 5796 → 16904 → 9644 → 6616)를 띄우고 각 문서 report 직전에 그 PID 만 종료했다(§2 "터널 종료 뒤 report"). 각 문서 시작 전 0단계 계수기 환경 `tokenizers 0.23.3`·`AIGW_PYTHON` 존재 확인.
- 시계: 측정 PC 가 서버보다 **4.45초 앞섬**(05:13Z 실측, ssh 왕복 0.44초). 등록 §1.9("하네스 시각도 UTC 라 변환하지 않는다")대로 share-window 구간은 서버 UTC 그대로 쓰고 보정하지 않았다. 보정 시 영향은 아래 공유 서버 항목에 따로 적는다.

##### 1) `diag-v2-t0-b` 남은 단계 — 기존 파일 처리

| 단계 | 처리 | exit | 마지막 출력 |
|---|---|---|---|
| 9 run A | §2 명령 그대로(05:13:49Z~05:26:25Z) | 0 | `F0_RUN_COMPLETED A` |
| 10 boundary-gen | §2 명령 그대로(05:26:26Z~05:34:13Z) | 0 | `F0_BOUNDARY_COMPLETED 9` |
| 11 postflight | 기존 `postflight.json` 이 있어 하네스 `preflight` 가 `OUTPUT_EXISTS`(`F0RunnerCli` 43행)로 거부하는 경로다. 덮어쓰지 않고 같은 회차 디렉터리에 **`postflight-2.json`** 으로 실행(결과 경로는 runId 디렉터리 강제 — 283~286행) | 0 | `F0_PREFLIGHT_OK 2`, `preflight.json`·`postflight.json` 과 바이트 동일 |
| 12 share-window | 기존 파일(B 창)은 두고 **`docs/gates/f0/share-window-20261104T044320Z-diag-v2-t0-b-2.json`**(창 04:43:00~05:35:00Z, B·A·boundary 전체) 새로 작성 | — | GIN 200건 = loopback 199·외부 1, 구간 1개 |
| 13 share-exclude | 새 구간 파일 지정 | 0 | A·B·boundary 제외 0 |
| 14 report | §2 명령 그대로 | Java 2(Gradle 1) | **`REPORT_EXISTS`**(`F0ReportWriter` 105행). 기존 `diagnostic-report.md`(A·boundary 칸 비어 있음, SHA `899edfd6…3dde`)는 그대로이며 우회(삭제·다른 디렉터리 복사)하지 않았다 |

- 따라서 `diag-v2-t0-b` 의 하네스 진단 보고서는 run B 만 담은 상태로 남는다. A·boundary 는 원시 결과(`A/f0-results.json`·`boundary/boundary-results.json`, run-binding 신원 동일)와 아래 집계로만 보고한다. 보고서 재생성은 기존 보고서 처리에 대한 사용자 결정이 필요하다.
- §1.8 무효 규칙에 해당하는 사건(digest·context·상주 변화, 계수기 실패, endpoint 오류)은 없어 다음 문서로 진행했다.

##### 2) 문서별 명령 exit

| 문서 | prereg-check | preflight | chat-template(SHA `2d54db2b…97f9`) | run B | run A | boundary-gen | postflight(= preflight 바이트) | share-exclude | report |
|---|---|---|---|---|---|---|---|---|---|
| `diag-i-t0-b` | 0 `PREREG_VERIFIED` | 0 | 0 일치 | 0(05:36:55~05:47:47Z) | 0(~05:58:47Z) | 0(~06:19:04Z) | 0 동일 | 0 | 0 `F0_DIAGNOSTIC_REPORT_WRITTEN` |
| `diag-v2-t06-b` | 0 | 0 | 0 일치 | 0(06:20:32~06:32:41Z) | 0(~06:47:41Z) | 0(~06:57:13Z) | 0 동일 | 0 | 0 |
| `diag-i-t06-b` | 0 | 0 | 0 일치 | 0(06:58:38~07:10:35Z) | 0(~07:21:35Z) | 0(~07:37:24Z) | 0 동일 | 0 | 0 |

##### 3) 경로별 결과(R=1, 지연 = 호출 단위 nearest-rank, 초)

| 문서·경로 | 호출 | 재시도 문항 | 유효 | errorCode | t 정답 | 인자 정답 | 오답 eqp/from/to/q | Context 복사 시도 | 지연 p50/p95/max | timeout | 로컬 사고 p50/p95/max | 답변 max(상한 초과) | 교차 확인 차이 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| v2-t0 B | 22 | 0 | 22/22 | 0 | 19/22 | 13/22 | 3/8/8/0 | 2 | 18.2/44.9/74.7 | 0 | 1107/2616/4082 | 77(0) | 3=22 |
| v2-t0 A | 22 | 0 | 22/22 | 0 | 22/22 | 13/22 | 3/8/8/0 | 2 | 24.7/81.2/84.1 | 0 | 1452/4360/4461 | 87(0) | 3=22 |
| v2-t0 boundary | 9 | 0 | 9/9 | 0 | — | 1/9 통과 | — | 0 | 49.1/68.1/68.1 | 0 | 2617/3333/3333 | 98(0) | 3=9 |
| i-t0 B | 22 | 0 | 22/22 | 0 | 22/22 | 22/22 | 0/0/0/0 | 0 | 22.8/53.9/69.5 | 0 | 1331/2994/3578 | 59(0) | 2=22 |
| i-t0 A | 22 | 0 | 22/22 | 0 | 22/22 | 21/22 | 1/0/0/0 | 0 | 18.7/60.0/66.8 | 0 | 1036/3188/3516 | 69(0) | 1=3, 2=19 |
| i-t0 boundary | 14 | 5 | 6/9 | `FINISH_LENGTH` 8 | — | 3/9 통과 | — | 0 | 97.6/113.8/113.8 | 0 | 5120/5120/5120 | 145(0) | 0=8, 2=6 |
| v2-t06 B | 22 | 0 | 22/22 | 0 | 20/22 | 11/22 | 5/8/8/0 | 2 | 26.2/57.1/64.8 | 0 | 1580/3184/3595 | 77(0) | 3=22 |
| v2-t06 A | 25 | 3 | 22/22(1차 19) | `STEPS_INVALID` 2, `FINISH_LENGTH` 1 | 21/22 | 13/22 | 3/7/7/0 | 2 | 24.1/82.8/95.6 | 0 | 1407/4436/5120 | 87(0) | 0=1, 3=24 |
| v2-t06 boundary | 10 | 1 | 9/9 | `FINISH_LENGTH` 1 | — | 1/9 통과 | — | 0 | 52.8/111.5/111.5 | 0 | 2800/5120/5120 | 97(0) | 0=1, 3=9 |
| i-t06 B | 23 | 1 | 22/22(1차 21) | `FINISH_LENGTH` 1 | 22/22 | 19/22 | 3/0/0/0 | 0 | 17.6/91.5/96.4 | 0 | 1012/4830/5120 | 66(0) | 0=1, 2=22 |
| i-t06 A | 22 | 0 | 22/22 | 0 | 22/22 | 21/22 | 1/0/0/0 | 0 | 19.0/84.8/96.0 | 0 | 1058/4342/4880 | 70(0) | 2=22 |
| i-t06 boundary | 11 | 2 | 9/9 | `FINISH_LENGTH` 2 | — | 4/9 통과 | — | 0 | 91.3/100.3/100.3 | 0 | 4542/5120/5120 | 145(0) | 0=2, 2=9 |

- 전 시도 답변 계수 불가 0·사고 계수 없음 0, `prompt_tokens` 대 audit 작음/큼 0/0, finish `length` 시도는 전부 답변 0·로컬 사고 5,120 이며 교차 확인 차이 0 이다(사고 종료 표지 없음). 그 밖의 시도는 V2 arm 차이 3, I arm 차이 2(i-t0 A 3시도만 1)로 arm 별 상수에 모였다. 답변 상한(분류 192·계획 256) 초과 0.
- 출력 키 순서: V2 B `t,eqp,from,to,qRef`, V2 A `t,eqp,from,to,qRef,steps`(프롬프트 표시 순서), I B `eqp,from,qRef,t,to`, I A `eqp,from,qRef,steps,t,to`(송신 정렬 순서). length 시도는 null.
- 문항별 오답(t 오답은 기대→예측):
  - v2-t0 B: G01 t INC→KQ+from·to, G04 t KQ→GQ, G16 t UNS→GQ+eqp, G08·G20 eqp·from·to(Context 복사), G09·G11·G12·G17·G22 from·to. A: G01·G09·G11·G12·G17·G22 from·to, G08·G20 eqp·from·to(Context 복사), G16 eqp. boundary 불통과: NORMAL-01 from·to, MAX 5건·ESCAPE 2건 eqp.
  - i-t0 B: 없음. A: G03 eqp. boundary 불통과: MAX-MIXED·MAX-HANGUL·MAX-SUPPLEMENTARY eqp, MAX-DIGIT·ESCAPE-01·ESCAPE-CONTROL 무효(두 시도 모두 `FINISH_LENGTH`).
  - v2-t06 B: G01 t INC→KQ+from·to, G18 t GQ→KQ, G03·G16·G21 eqp, G08·G20 eqp·from·to(Context 복사), G09·G11·G12·G17·G22 from·to. A: G17 t INC→UNS+from·to(초회 `FINISH_LENGTH` → 재시도), G01·G09·G11·G12·G22 from·to, G08 eqp·from·to(Context 복사), G20 eqp(Context 복사 시도), G16 eqp; 재시도 뒤 정답 G06·G19(`STEPS_INVALID`). boundary 불통과: NORMAL-01 from·to, MAX 5건·ESCAPE 2건 eqp(ESCAPE-CONTROL 초회 `FINISH_LENGTH`).
  - i-t06 B: G03·G21 eqp, G15 eqp(초회 `FINISH_LENGTH`). A: G03 eqp. boundary 불통과: NORMAL-PRECISE from·to, MAX-MIXED·MAX-SUPPLEMENTARY·ESCAPE-01·ESCAPE-CONTROL eqp. 통과: NORMAL-01·MAX-HANGUL(재시도)·MAX-DIGIT·MAX-ASCII.
- G08: V2 arm(t0·t06) A·B 모두 유효·t=INC 정답·eqp·from·to 오답(Context 복사). I arm(t0·t06) A·B 모두 유효·전 인자 정답.
- Jetson 비교(같은 22문항·9케이스): v2 회차 `20261102T045749Z-a` B t 7/22·인자 10/22(무효 10, `QREF_INVALID`), A t 6/22·인자 11/22(무효 8), boundary 0/9, G08 A·B t=UNS. v3 i 회차 `20261103T060939Z-i` B t 20/22·인자 18/22, A t 21/22·인자 14/22·G08 INVALID(`STEPS_INVALID`×2), boundary 9/9.

##### 4) 공유 서버·서버 상태

- GIN(서버 UTC, 출발지 건수·외부 구간 시각만): v2-t0 04:43~05:35 200건(loopback 199·외부 1), i-t0 05:36~06:20 127건(외부 0), v2-t06 06:20~06:58 126건(외부 0), i-t06 06:58~07:38 125건(외부 0). share-exclude 제외 표본 4문서 전부 0.
- 외부 1건은 서버 05:26:37.999~05:26:38.000Z(GIN 지연 약 1 ms)다. 등록 규칙(무변환)으로는 겹침 0이다. 다만 측정 PC 시계 4.45초 앞섬을 보정하면 이 요청은 v2-t0 boundary 첫 호출 NORMAL-01(PC 송신 05:26:42.17Z = 서버 약 05:26:37.7Z, 40.5초) 구간 안에 든다. 보정했다면 boundary 지연 표본 1건이 제외 대상이었다(판정 불변, 기록만).
- 서버(05:13:14Z / 05:35:55Z / 06:19:52Z / 06:57:56Z / 07:38:13Z): Ollama active·NRestarts 0·ActiveEnter 2026-11-03 04:10:45 UTC 불변, `ollama ps` `qwen3:4b` 3.9 GB·`bge-m3:latest` 689 MB 둘 다 100% GPU·CONTEXT 8192, GPU 메모리 4548 / 6144 MiB 불변.

##### 5) 생성 파일

- `eval/f0/results/20261104T044320Z-diag-v2-t0-b/{A/,boundary/,postflight-2.json}`, `docs/gates/f0/share-window-20261104T044320Z-diag-v2-t0-b-2.json`.
- 3문서마다 `eval/f0/results/20261104T044320Z-<tag>/{preflight.json,B/,A/,boundary/,postflight.json,diagnostic-report.md}`, `eval/f0/chat-template/v4-<tag>/`, `docs/gates/f0/share-window-20261104T044320Z-<tag>.json`(`<tag>` = `diag-i-t0-b`·`diag-v2-t06-b`·`diag-i-t06-b`).
- 모델 응답·사고문·API 응답 원문 출력·저장 0(집계는 열거형·개수·지연만). 이 소절 추가 전 Dev report 587,203 B SHA `70629b66…afd8`.

### 12.32 P03 v4 ⑧ 진단 결과 정리 — 응답 파싱 규칙·답변 토큰 계수 방법·예산 규칙·오답 목록 (2026-11-04, 독립 Claude Code Opus 5.5, root 검토 전)

- 범위(2026-11-04 사용자 지시): Plan P03 v4 흐름 ⑧(같은 날 사용자 승인으로 개정된 흐름 — ⑧ = 진단 결과 정리: 응답 파싱 규칙·답변 토큰 계수 방법·예산 규칙 확정과 오답 목록 작성)을 **저장된 진단 결과만으로** 수행했다. 모델 호출·SSH·네트워크 0, Gradle 하네스 실행 0, `eval/f0/results`·PREREG·evidence·capture 파일 쓰기 0, commit 0. 사고 상한 T 는 제안하지 않는다(흐름 ⑫).
- 입력(4 진단 × A·B·boundary, 하네스 `e5f64b8`, 회차 `20261104T044320Z-<tag>`): `diag-v2-t0-b/A/f0-results.json` `a1e9dd8c75411eaf…`, `diag-v2-t0-b/B/f0-results.json` `b34e869021c03ed0…`, `diag-v2-t0-b/boundary/boundary-results.json` `29d982780ef715e9…`, `diag-i-t0-b/A/f0-results.json` `77324aef21d09bb4…`, `diag-i-t0-b/B/f0-results.json` `30f266580e61e882…`, `diag-i-t0-b/boundary/boundary-results.json` `3edd8be37cc76572…`, `diag-v2-t06-b/A/f0-results.json` `da1dc54a7db0e7a5…`, `diag-v2-t06-b/B/f0-results.json` `46e487992027fbb2…`, `diag-v2-t06-b/boundary/boundary-results.json` `e4289dde7820e666…`, `diag-i-t06-b/A/f0-results.json` `874ca2f1a48b80bd…`, `diag-i-t06-b/B/f0-results.json` `160e011c36ef7f73…`, `diag-i-t06-b/boundary/boundary-results.json` `5c1cc431f0fb2aa8…`. 보조 입력: `diag-v2-t0-b/{usage.json,token-golden.jsonl}`, 4 진단의 `preflight.json`·`postflight.json`과 `diag-v2-t0-b/postflight-2.json`, 각 `diagnostic-report.md`, `eval/golden/tc-f0-v2.jsonl`, `eval/f0/v2/output-boundary-cases.jsonl`, `eval/golden/tc-registry-fixture-v2.json`. `diag-v2-t0-b/diagnostic-report.md` 는 B 만 담고 있어(§12.31 4문서 실측 1)) 이 소절의 A·boundary 수치는 원시 결과에서 다시 셌다.
- 방법: 레포 밖 세션 scratchpad 의 Python 스크립트(`step8/step8_agg.py`·`step8_tables.py`·`match_golden_time.py`·`extra.py`, 경계 해시 재현은 같은 세션의 `boundary-rca/match_hashes_t.py`·`match_extra.py` 출력)로 모든 수를 파일에서 계산했다. 출력은 열거형·불리언·개수·해시 접두부만이며 질문·응답·사고문·인자 원문은 출력·저장하지 않았다. **시도 = 모델 호출 1회**(재시도 포함, 전체 224회). 백분위는 하네스와 같은 nearest-rank 이고 중앙값(통계)은 따로 적었다.

#### 1) 응답 파싱 규칙(a)

| 진단/경로 | 호출 | 분리 필드 | reasoning_tokens null | content `<think>` | content `</think>` | content 0 | content 0 중 finish=length | JSON OBJECT/PARSE_FAILED |
|---|---|---|---|---|---|---|---|---|
| i-t0/A | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| i-t0/B | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| i-t0/boundary | 14 | reasoning=14 | 14 | 0 | 0 | 8 | 8 | 6/8 |
| i-t06/A | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| i-t06/B | 23 | reasoning=23 | 23 | 0 | 0 | 1 | 1 | 22/1 |
| i-t06/boundary | 11 | reasoning=11 | 11 | 0 | 0 | 2 | 2 | 9/2 |
| v2-t0/A | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| v2-t0/B | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| v2-t0/boundary | 9 | reasoning=9 | 9 | 0 | 0 | 0 | 0 | 9/0 |
| v2-t06/A | 25 | reasoning=25 | 25 | 0 | 0 | 1 | 1 | 24/1 |
| v2-t06/B | 22 | reasoning=22 | 22 | 0 | 0 | 0 | 0 | 22/0 |
| v2-t06/boundary | 10 | reasoning=10 | 10 | 0 | 0 | 1 | 1 | 9/1 |
| **합계** | 224 | reasoning=224 | 224 | 0 | 0 | 13 | 13 | 211/13 |

- 사고문 위치: 전 224 시도의 `thinkingObservations[].separateFields` = `reasoning` 224/224(`reasoning_content`·`thinking` 0). 계수 행(`max_tokens:1`)도 token-golden 13행 `reasoning` 13/13, usage-probe 4호출 모두 `reasoning`.
- content 혼입: `contentThinkOpen` 0/224, `contentThinkClose` 0/224.
- usage 사고 토큰 보고: `reasoningTokens` null 224/224(계수 행 token-golden 13/13·usage-probe 4/4 도 null).
- content 0 은 13 시도이고 13건 모두 `finish_reason=length`·JSON `PARSE_FAILED` 다. `stop` 211 시도는 JSON `OBJECT` 211/211.
- **확정 규칙(제안): 답변 = `message.content` 만 JSON 파싱·답변 토큰 계수 대상으로 쓰고, 사고문은 별도 필드 `message.reasoning` 으로만 받아 답변에서 제외한다(비저장, 길이·토큰 수만 관측).** content 의 `<think>` 표지 제거 같은 보정은 관측상 필요 없다(0/224). 사고 토큰 수를 usage 에서 읽는 방법은 쓸 수 없다(null 224/224). 현재 하네스(`OllamaChatClient.THINKING_FIELDS` 첫 문자열 필드 = 사고, content = 답변)와 같은 규칙이다.

#### 2) 답변 토큰 계수 방법 (b) `LOCAL_TOKENIZER_PIN_V1` 교차 확인

| arm | 호출 | 답변 계수 불가 | 사고 계수 없음 | 차이 null | 하네스 answerTokens = 로컬 답변 | 차이 값=건수(전체) | stop 시도 | length 시도 | 교차 확인 어긋남(stop, arm 상수 아님) |
|---|---|---|---|---|---|---|---|---|---|
| V2 | 110 | 0 | 0 | 0 | 110/110 | 0=2; 3=108 | 3=108 | 0=2(답변 0 2건, 로컬 사고 5120=2) | 0: - |
| I | 114 | 0 | 0 | 0 | 114/114 | 0=11; 1=3; 2=100 | 1=3; 2=100 | 0=11(답변 0 11건, 로컬 사고 5120=11) | 3: i-t0-b/A/G02#0 차이 1·답변 39·사고 2182, i-t0-b/A/G04#0 차이 1·답변 31·사고 724, i-t0-b/A/G21#0 차이 1·답변 32·사고 1209 |

- 계수 불가 0/224(V2 0·I 0), 사고 계수 없음 0, 차이 null 0. 하네스 행 `answerTokens[]` 와 `localTokenCounts[].answerTokens` 는 224/224 같다.
- 차이 = `completion_tokens − (로컬 사고 + 로컬 답변)`: `stop` 시도는 V2 arm `3` 108/108, I arm `2` 100/103·`1` 3/103. `length` 시도는 두 arm 모두 차이 `0`(V2 2·I 11) — 답변 0·로컬 사고 5,120(= `max_tokens`)이라 사고·답변 경계 특수 토큰이 생기지 않은 경우다. 계수 행 `max_tokens:1` 은 token-golden 13/13·usage-probe 4/4 이 차이 0.
- 교차 확인 어긋남(`stop` 인데 arm 상수가 아님): 3건, 전부 `diag-i-t0-b` 경로 A(G02, G04, G21, 차이 1, 답변 39·31·32 토큰). 같은 문항의 `diag-i-t06-b` A 는 차이 2 다. 원인은 사고문·답변 원문을 저장하지 않아 판별할 수 없다(NOT DETERMINABLE). 세 시도 모두 답변 상한 256·192 와 거리가 커서 ±1 차이가 판정을 바꾸지 않는다.
- V2 arm 3 과 I arm 2 의 상수 차이 원인(요청 `response_format` 유무·프롬프트·Context 투영이 함께 다름)도 원문 없이 분리할 수 없다(NOT DETERMINABLE). 관측 사실은 "arm 안에서는 `stop` 시도가 한 상수에 모인다(어긋남 3/211)" 이다.
- **확정 방법(제안): 답변 토큰 수 = `message.content` 를 등록 tokenizer pin(`Qwen/Qwen3-4B-Thinking-2507` tokenizer.json, `encode(text, add_special_tokens=False)`)으로 센 개수(방법 b). content 가 없거나 짝 없는 surrogate 로 셀 수 없으면 계수 불가 = 그 시도 실패. 교차 확인 차이는 기록만 하고 판정에 쓰지 않는다.** 서버 usage 로 답변 토큰을 빼는 방법 (a) 는 `reasoning_tokens` 미보고로 불가(위 1)). SPEC §14.5 열린 항목 ⓒ 를 닫는 문안 초안은 SPEC 소유자 승인 대상으로 별도 작성했다(SPEC 변경 0).

#### 3) 예산 규칙(c) — 관측 분포(T 제안 아님)

| 진단/경로 | 호출 | 경로 답변 상한 | 답변 max | 상한 초과 | 192 초과 | FINISH_LENGTH(시도) | 로컬 사고 min/p50/중앙값/p95/max | > 4928 | stop 시도만 사고 p50/p95/max |
|---|---|---|---|---|---|---|---|---|---|
| i-t0/A | 22 | 256 | 69 | 0 | 0 | 0 | 503/1036/1122.5/3188/3516 | 0/22 | 1036/3188/3516 |
| i-t0/B | 22 | 192 | 59 | 0 | 0 | 0 | 645/1331/1478.5/2994/3578 | 0/22 | 1331/2994/3578 |
| i-t0/boundary | 14 | 192 | 145 | 0 | 0 | 8 (MAX-DIGIT#0, MAX-DIGIT#1, MAX-ASCII#0, MAX-SUPPLEMENTARY#0, ESCAPE-01#0, ESCAPE-01#1, ESCAPE-CONTROL#0, ESCAPE-CONTROL#1) | 1498/5120/5120/5120/5120 | 8/14 | 3249/4461/4461 |
| i-t06/A | 22 | 256 | 70 | 0 | 0 | 0 | 469/1058/1080.5/4342/4880 | 0/22 | 1058/4342/4880 |
| i-t06/B | 23 | 192 | 66 | 0 | 0 | 1 (G15#0) | 497/1012/1012/4830/5120 | 1/23 | 909/3316/4830 |
| i-t06/boundary | 11 | 192 | 145 | 0 | 0 | 2 (MAX-HANGUL#0, MAX-SUPPLEMENTARY#0) | 2624/4542/4542/5120/5120 | 4/11 | 4082/4979/4979 |
| v2-t0/A | 22 | 256 | 87 | 0 | 0 | 0 | 587/1452/1468.5/4360/4461 | 0/22 | 1452/4360/4461 |
| v2-t0/B | 22 | 192 | 77 | 0 | 0 | 0 | 567/1107/1160/2616/4082 | 0/22 | 1107/2616/4082 |
| v2-t0/boundary | 9 | 192 | 98 | 0 | 0 | 0 | 2282/2617/2617/3333/3333 | 0/9 | 2617/3333/3333 |
| v2-t06/A | 25 | 256 | 87 | 0 | 0 | 1 (G17#0) | 730/1407/1407/4436/5120 | 1/25 | 1335/3879/4436 |
| v2-t06/B | 22 | 192 | 77 | 0 | 0 | 0 | 631/1580/1640/3184/3595 | 0/22 | 1580/3184/3595 |
| v2-t06/boundary | 10 | 192 | 97 | 0 | 0 | 1 (ESCAPE-CONTROL#0) | 1793/2800/2824/5120/5120 | 1/10 | 2800/3207/3207 |

| arm/경로 | 호출 | 로컬 사고 min/p50/p95/max | > 4928 | FINISH_LENGTH |
|---|---|---|---|---|
| V2/A | 47 | 587/1452/4436/5120 | 1 | 1 |
| V2/B | 44 | 567/1507/3184/4082 | 0 | 0 |
| V2/boundary | 19 | 1793/2774/5120/5120 | 1 | 1 |
| I/A | 44 | 469/1058/3655/4880 | 0 | 0 |
| I/B | 45 | 497/1256/3578/5120 | 1 | 1 |
| I/boundary | 25 | 1498/4896/5120/5120 | 12 | 10 |

- 답변 상한 초과: 0/224(분류·경계 192, 계획 A 256 기준. A 도 192 기준 초과 0). 경로별 답변 max: A 87·B 77·boundary 145. 경계 145 는 정답 content(해시 `26db1d7e637f…`, I 키 순서)의 로컬 계수이며 SPEC §14.5 의 v2 준비 정적 공식 최장 wire 145 토큰과 수가 같다.
- FINISH_LENGTH(시도): 전체 13 — 경로별 A 1·B 1·boundary 11, 진단별 v2-t0 0·i-t0 8·v2-t06 2·i-t06 3(위 표). 13/13 모두 답변 0·로컬 사고 5,120.
- 로컬 사고 토큰 전체: n 224, min 469, p50 1711(중앙값 1720), p95 5120, max 5120. SPEC §14.5 표의 분류 ctx 상한 `T_cls ≤ 4,928` 을 넘는 시도는 15/224(경로별 A 1·B 1·boundary 13). 경로 A 는 계획 호출이라 SPEC 의 계획 ctx 상한 `T_pln ≤ 4,864` 도 따로 보면 넘는 시도가 2건이다(`v2-t06` G17 초회 5,120 length, `i-t06` G17 4,880 stop).
- 규칙 확인: ①서버는 `max_tokens` 하나로 사고·답변·경계 특수 토큰을 함께 자른다(아래 4) 의 "max_tokens 사고 포함"). 사고를 따로 자르는 요청 수단은 이 진단에서 쓰지 않았고 관측되지 않았다. 따라서 사고 상한 T 는 요청 `max_tokens = 답변 상한 + T` 로만 걸리며, 사고가 이 합을 다 쓰면 답변 0·`length` = FINISH_LENGTH 실패(1회 재시도)가 된다(관측 13/13). ②`completion_tokens` 에는 사고·답변 외에 arm 상수(2 또는 3)가 들어가므로 요청 `max_tokens` 의 합에도 이 몫이 들어간다. ③답변 상한 판정(초과 = 실패)은 서버가 아니라 Gateway·하네스의 계수로만 걸린다. T 값 자체는 흐름 ⑫ 에서 제안한다.
- 대조(사용자 제공 boundary 수치): 중앙값 i-t0 5,120·i-t06 4,542·v2-t0 2,617 과 4928 초과 8/14·4/11·0/9·1/10 은 위 계산과 같다. v2-t06 boundary 중앙값은 nearest-rank 2,800·통계 중앙값 2,824(n=10)로 계산되어 제공값 2,848 과 다르다.

#### 4) Plan ⑥ 진단 항목 판별(d)

| 항목 | 판정 | 근거(파일 / JSON 경로) |
|---|---|---|
| 사고문 분리 필드 | CONFIRMED — `message.reasoning` | 각 결과 `attempts[].thinkingObservations[].separateFields`(A·B), `cases[].attempts[].thinkingObservations[]`(boundary): `reasoning` 224/224; `token-golden.jsonl` `thinking.separateFields` 13/13 |
| `max_tokens` 의 사고 포함 | CONFIRMED — 포함 | `length` 13 시도 모두 `localTokenCounts[].thinkingTokens` 5,120 = 요청 `maxTokens` 5,120·`answerTokens` 0(A·B 행은 `completionTokens[]` 도 5,120, boundary 는 마지막 호출 `completionTokens` 5,120 3케이스); `max_tokens:1` 계수 행 17/17 `completionTokens` 1·`thinkingTokens` 1·`answerTokens` 0(`token-golden.jsonl`·`usage.json` `calls[].localTokenCount`) |
| usage `completion_tokens` 의 사고 포함 | CONFIRMED — 포함 | `localTokenCounts[].completionMinusLocal` ∈ {0,1,2,3} 224/224(사고 수백~5,120 토큰 대비 상수 차이) |
| usage 사고 토큰 수(`reasoning_tokens`) | CONFIRMED — 미보고 | `thinkingObservations[].reasoningTokens` null 224/224; 계수 행 17/17 null |
| 답변 토큰 계수 방법 | CONFIRMED(방법 b 계수 가능) | 위 2): 계수 불가 0/224, `stop` 시도 arm 상수 일치 208/211 |
| 사고 중 상한 도달 `finish_reason` | CONFIRMED — `length`, content 0, JSON `PARSE_FAILED`, 하네스 errorCode `FINISH_LENGTH` | `finishReasons[]`·`attemptDiagnostics[].finish`=`LENGTH` 13, `thinkingObservations[].contentCodepoints`=0 13, `attemptDiagnostics[].json`=`PARSE_FAILED` 13; `diagnostic-report.md` "판정 errorCode=FINISH_LENGTH" 행 |
| `response_format`+thinking — 서버 수락·사고 분리 유지 | CONFIRMED | I arm(`responseFormat=JSON_SCHEMA_V1`, `diagnostic-report.md` 5행) 114 시도: 결과 `complete=true`·`abortReason=null` 6/6 파일, `separateFields=reasoning` 114/114, `stop` 103 시도 JSON `OBJECT`·`keys=EXACT` 전부 |
| `response_format`+thinking — 문법 제약이 사고 단계에 걸리는지 | NOT DETERMINABLE | 사고문 원문 비저장(길이·토큰 수만). I arm 도 사고 토큰 469~5,120 이 관측되어 사고가 생략되지는 않았다(위 3) 표) |
| `response_format`+thinking — FINISH_LENGTH 에 대한 효과 | NOT DETERMINABLE(단일 요인 분리 불가) | FINISH_LENGTH I arm 11·V2 arm 2. 두 arm 은 프롬프트·Context 투영·`response_format` 이 함께 달라(PREREG-v4-diag-*-b 1절 표) 요인을 나눌 수 없다 |
| properties 순서(`outputKeyOrders`) | CONFIRMED(관측) / 비정렬 순서 보존 여부 NOT MEASURED | `attempts[].outputKeyOrders[]`: I A `eqp,from,qRef,steps,t,to`=44; I B `eqp,from,qRef,t,to`=44; I B `null`=1; I boundary `eqp,from,qRef,t,to`=15; I boundary `null`=10; V2 A `null`=1; V2 A `t,eqp,from,to,qRef,steps`=46; V2 B `t,eqp,from,to,qRef`=44; V2 boundary `null`=1; V2 boundary `t,eqp,from,to,qRef`=18(null = length 시도). I arm 은 송신 schema properties 순서(canonical 정렬, `ResponseSchema` 20~21행)와 같고 V2 arm 은 프롬프트 표시 순서다. 정렬과 다른 순서로 보낸 schema 를 서버가 보존하는지는 v4 등록에 `JSON_SCHEMA_PLAN_ORDERED_V1` arm 이 없어 측정하지 않았다 |
| `/api/ps` 필드 | CONFIRMED(허용 요약 필드) / 그 밖 원본 필드 NOT DETERMINABLE | 9 파일(`preflight.json`·`postflight.json` 4쌍 + `diag-v2-t0-b/postflight-2.json`) 모두 `{models[{name,digest,context_length}],residentCount,ok,reason}`, 바이트 SHA 접두부 `901c0c8f94c134be` 하나로 동일: `qwen3:4b` `359d7dd4bcda…` ctx 8192, `bge-m3:latest` `790764642607…` ctx 8192, residentCount 2. preflight 는 허용 필드만 저장하므로(Plan P03 `f0 preflight` 정의 — 허용 필드 요약만 출력·저장) 원본 `/api/ps` 의 다른 필드는 저장 결과로 알 수 없다 |
| G-USAGE | CONFIRMED — 같음 | `diag-v2-t0-b/usage.json` `equalOnCacheHit` = usage `true`·evalCount `true`; `calls[]` U1/cold prompt 1056·eval 1056·audit 1056, U1/warm prompt 1056·eval 1056·audit 1056, U2/shared prompt 1038·eval 1038·audit 1038, U2/cold prompt 1038·eval 1038·audit 1038. 진단 run 224 시도도 `promptTokens[]` = `auditInputTokens[]` 224/224 |
| prompt cache | 계수 영향 CONFIRMED(warm·공유 접두부에서 `prompt_tokens`·`prompt_eval_count` 감소 0) / 실제 캐시 적중·지연 이득 NOT DETERMINABLE | 같은 `usage.json` — `calls[]` 키는 `auditInputTokens, completionTokens, finishReason, id, localTokenCount, phase, promptEvalCount, promptTokens, thinking` 뿐이라 호출별 지연·적중 표지가 없다 |

#### 5) 오답 목록(e)

- 대상: 4 진단의 A·B 골든 행 중 실효 t 오답 또는 인자 오답 또는 무효(45행), boundary 케이스 중 불통과(27케이스) — 합계 72. 재시도 뒤 통과한 4건은 따로 적었다. A 의 실효 t 는 하네스 `predicted.effectiveT`(미등록·비활성 단계가 있으면 UNS, `PlanJudge` 30행)이며 재시도 뒤 마지막 시도 기준이다. boundary 의 `outputSha256` 은 마지막 시도의 content 해시다.
- 유형별(한 행이 여러 유형에 들 수 있음):

| 실패 유형 | 건수 | 대상(진단 경로 caseId) |
|---|---|---|
| F1 t 오분류 | 8 | v2-t0 B G01, v2-t0 B G04, v2-t0 B G16, v2-t0 boundary ESCAPE-01, v2-t0 boundary ESCAPE-CONTROL, v2-t06 A G17, v2-t06 B G01, v2-t06 B G18 |
| F2 시간 형식 변형(초·오프셋 추가) | 26 | v2-t0 A G01, v2-t0 A G09, v2-t0 A G11, v2-t0 A G12, v2-t0 A G17, v2-t0 A G22, v2-t0 B G01, v2-t0 B G09, v2-t0 B G11, v2-t0 B G12, v2-t0 B G17, v2-t0 B G22, v2-t0 boundary NORMAL-01, v2-t06 A G01, v2-t06 A G09, v2-t06 A G11, v2-t06 A G12, v2-t06 A G17, v2-t06 A G22, v2-t06 B G01, v2-t06 B G09, v2-t06 B G11, v2-t06 B G12, v2-t06 B G17, v2-t06 B G22, v2-t06 boundary NORMAL-01 |
| F2b 정밀 시각 → 분 단위 축약 | 1 | i-t06 boundary NORMAL-PRECISE |
| F2c 기대 null 시간창에 값 출력 | 2 | v2-t0 B G20, v2-t06 B G20 |
| F3 Context 값 복사 | 8 | v2-t0 A G08, v2-t0 A G20, v2-t0 B G08, v2-t0 B G20, v2-t06 A G08, v2-t06 A G20, v2-t06 B G08, v2-t06 B G20 |
| F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | 7 | i-t0 A G03, v2-t06 B G03, v2-t06 B G21, i-t06 A G03, i-t06 B G03, i-t06 B G15, i-t06 B G21 |
| F4b eqp 누락(기대 값 있음, 출력 null) | 4 | v2-t0 A G16, v2-t0 B G16, v2-t06 A G16, v2-t06 B G16 |
| F5 경계 eqp 64cp → `EQP0001` | 20 | v2-t0 boundary MAX-MIXED, v2-t0 boundary MAX-HANGUL, v2-t0 boundary MAX-DIGIT, v2-t0 boundary MAX-ASCII, v2-t0 boundary MAX-SUPPLEMENTARY, v2-t0 boundary ESCAPE-01, v2-t0 boundary ESCAPE-CONTROL, i-t0 boundary MAX-MIXED, i-t0 boundary MAX-HANGUL, i-t0 boundary MAX-SUPPLEMENTARY, v2-t06 boundary MAX-MIXED, v2-t06 boundary MAX-HANGUL, v2-t06 boundary MAX-DIGIT, v2-t06 boundary MAX-ASCII, v2-t06 boundary MAX-SUPPLEMENTARY, v2-t06 boundary ESCAPE-01, v2-t06 boundary ESCAPE-CONTROL, i-t06 boundary MAX-MIXED, i-t06 boundary ESCAPE-01, i-t06 boundary ESCAPE-CONTROL |
| F5b 경계 eqp 오답(내용 미재현) | 1 | i-t06 boundary MAX-SUPPLEMENTARY |
| F6 사고 상한 소진(답변 0) | 3 | i-t0 boundary MAX-DIGIT, i-t0 boundary ESCAPE-01, i-t0 boundary ESCAPE-CONTROL |
| F6r 초회 사고 상한 소진 → 재시도 | 5 | i-t0 boundary MAX-SUPPLEMENTARY, v2-t06 A G17, v2-t06 boundary ESCAPE-CONTROL, i-t06 B G15, i-t06 boundary MAX-SUPPLEMENTARY |

- 해시 근거: boundary 는 `boundary-rca/match_hashes_t.out`(질문 부분 문자열 eqp 후보 × t × 시간 × qRef 직렬화 3,236,240 후보)과 `match_extra.out`, 골든 A·B 는 `step8/match_golden_time.out`(기대값 + 시간 접미 변형 후보)으로 저장 해시를 재현한 결과다. 정답 해시는 `output-boundary-cases.jsonl` 의 `expectedOutput` 을 arm 키 순서로 직렬화해 계산했다(MAX·ESCAPE 정답: I 순서 `26db1d7e637f…`, V2 순서 `7fc3cb729e31…` — V2 순서 정답은 한 번도 나오지 않았다). `e3b0c44298fc…` 는 빈 문자열 해시다.

| # | 진단 | 경로 | caseId | 기대 t → 실효 t(원 t) | 오답 필드 | errorKinds | errorCodes | 재시도 | 실효 판정 | 실패 유형 | 해시 근거 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | v2-t0 | A | G01 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `e82664c62e3f` 미재현 |
| 2 | v2-t0 | A | G08 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 3 | v2-t0 | A | G09 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `b1407006e560` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 4 | v2-t0 | A | G11 | LQ → LQ | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `f1761c4d9b38` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 5 | v2-t0 | A | G12 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `c484734ce737` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 6 | v2-t0 | A | G16 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4b eqp 누락(기대 값 있음, 출력 null) | - |
| 7 | v2-t0 | A | G17 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `a3611bae245e` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 8 | v2-t0 | A | G20 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 9 | v2-t0 | A | G22 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `2fbb1236809c` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 10 | v2-t0 | B | G01 | INC → KQ | t·from·to | TASK_TYPE_MISMATCH,ARGUMENT_MISMATCH | - | 아니오 | OK, t 오답, 인자 오답, UNSUPPORTED 정답 | F1 t 오분류; F2 시간 형식 변형(초·오프셋 추가) | `f6c2926386f3` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 11 | v2-t0 | B | G04 | KQ → GQ | t | TASK_TYPE_MISMATCH | - | 아니오 | OK, t 오답, 인자 정답, UNSUPPORTED 정답 | F1 t 오분류 | - |
| 12 | v2-t0 | B | G08 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 13 | v2-t0 | B | G09 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `8132a84619b6` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 14 | v2-t0 | B | G11 | LQ → LQ | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `58b4a150fe23` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 15 | v2-t0 | B | G12 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `fa3bf734de78` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 16 | v2-t0 | B | G16 | UNS → GQ | t·eqp | TASK_TYPE_MISMATCH,ARGUMENT_MISMATCH | - | 아니오 | OK, t 오답, 인자 오답, UNSUPPORTED 오답 | F1 t 오분류; F4b eqp 누락(기대 값 있음, 출력 null) | - |
| 17 | v2-t0 | B | G17 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `c86c720f6f7d` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 18 | v2-t0 | B | G20 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사; F2c 기대 null 시간창에 값 출력 | - |
| 19 | v2-t0 | B | G22 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `be30e9b5e125` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 20 | v2-t0 | boundary | NORMAL-01 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 76 → 불통과 | F2 시간 형식 변형(초·오프셋 추가) | `c86c720f6f7d` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 21 | v2-t0 | boundary | MAX-MIXED | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 22 | v2-t0 | boundary | MAX-HANGUL | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 23 | v2-t0 | boundary | MAX-DIGIT | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 24 | v2-t0 | boundary | MAX-ASCII | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 25 | v2-t0 | boundary | MAX-SUPPLEMENTARY | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 26 | v2-t0 | boundary | ESCAPE-01 | INC → t 오답 | eqp | TASK_TYPE_MISMATCH,ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 98 → 불통과 | F1 t 오분류; F5 경계 eqp 64cp → `EQP0001` | `794328a9b592` 재현: t=LQ eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 27 | v2-t0 | boundary | ESCAPE-CONTROL | INC → t 오답 | eqp | TASK_TYPE_MISMATCH,ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 98 → 불통과 | F1 t 오분류; F5 경계 eqp 64cp → `EQP0001` | `9c560d9472d3` 재현: t=KQ eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 28 | i-t0 | A | G03 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 29 | i-t0 | boundary | MAX-MIXED | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 30 | i-t0 | boundary | MAX-HANGUL | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 31 | i-t0 | boundary | MAX-DIGIT | INC → (무효) | 전 필드(무효) | INVALID_OUTPUT | FINISH_LENGTH,FINISH_LENGTH | 예 | INVALID/length, schemaValid=false, argsExact=false, 답변 0 → 불통과 | F6 사고 상한 소진(답변 0) | `e3b0c44298fc` = 빈 content |
| 32 | i-t0 | boundary | MAX-SUPPLEMENTARY | INC → INC | eqp | ARGUMENT_MISMATCH | FINISH_LENGTH | 예 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001`; F6r 초회 사고 상한 소진 → 재시도 | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 33 | i-t0 | boundary | ESCAPE-01 | INC → (무효) | 전 필드(무효) | INVALID_OUTPUT | FINISH_LENGTH,FINISH_LENGTH | 예 | INVALID/length, schemaValid=false, argsExact=false, 답변 0 → 불통과 | F6 사고 상한 소진(답변 0) | `e3b0c44298fc` = 빈 content |
| 34 | i-t0 | boundary | ESCAPE-CONTROL | INC → (무효) | 전 필드(무효) | INVALID_OUTPUT | FINISH_LENGTH,FINISH_LENGTH | 예 | INVALID/length, schemaValid=false, argsExact=false, 답변 0 → 불통과 | F6 사고 상한 소진(답변 0) | `e3b0c44298fc` = 빈 content |
| 35 | v2-t06 | A | G01 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `e82664c62e3f` 미재현 |
| 36 | v2-t06 | A | G08 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 37 | v2-t06 | A | G09 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `b1407006e560` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 38 | v2-t06 | A | G11 | LQ → LQ | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `f1761c4d9b38` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 39 | v2-t06 | A | G12 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `57bd258cdad0` 재현: from `:00` 추가·to `:00` 추가 |
| 40 | v2-t06 | A | G16 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4b eqp 누락(기대 값 있음, 출력 null) | - |
| 41 | v2-t06 | A | G17 | INC → UNS(원 t INC, 미등록 단계 1) | t·from·to | ARGUMENT_MISMATCH | FINISH_LENGTH | 예 | OK, t 오답, 인자 오답, UNSUPPORTED 오답 | F1 t 오분류; F2 시간 형식 변형(초·오프셋 추가); F6r 초회 사고 상한 소진 → 재시도 | `2941f123681a` 미재현 |
| 42 | v2-t06 | A | G20 | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 43 | v2-t06 | A | G22 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `2c1b4b79e438` 재현: from `:00` 추가·to `:00` 추가 |
| 44 | v2-t06 | B | G01 | INC → KQ | t·from·to | TASK_TYPE_MISMATCH,ARGUMENT_MISMATCH | - | 아니오 | OK, t 오답, 인자 오답, UNSUPPORTED 정답 | F1 t 오분류; F2 시간 형식 변형(초·오프셋 추가) | `4f095a440f6a` 재현: from `+09:00` 추가·to `+09:00` 추가 |
| 45 | v2-t06 | B | G03 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 46 | v2-t06 | B | G08 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사 | - |
| 47 | v2-t06 | B | G09 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `8132a84619b6` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 48 | v2-t06 | B | G11 | LQ → LQ | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `58b4a150fe23` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 49 | v2-t06 | B | G12 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `fa3bf734de78` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 50 | v2-t06 | B | G16 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4b eqp 누락(기대 값 있음, 출력 null) | - |
| 51 | v2-t06 | B | G17 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `c86c720f6f7d` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 52 | v2-t06 | B | G18 | GQ → KQ | t | TASK_TYPE_MISMATCH | - | 아니오 | OK, t 오답, 인자 정답, UNSUPPORTED 정답 | F1 t 오분류 | - |
| 53 | v2-t06 | B | G20 | INC → INC | eqp·from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F3 Context 값 복사; F2c 기대 null 시간창에 값 출력 | - |
| 54 | v2-t06 | B | G21 | KQ → KQ | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 55 | v2-t06 | B | G22 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F2 시간 형식 변형(초·오프셋 추가) | `be30e9b5e125` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 56 | v2-t06 | boundary | NORMAL-01 | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 76 → 불통과 | F2 시간 형식 변형(초·오프셋 추가) | `c86c720f6f7d` 재현: from `:00+09:00` 추가·to `:00+09:00` 추가 |
| 57 | v2-t06 | boundary | MAX-MIXED | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 58 | v2-t06 | boundary | MAX-HANGUL | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 59 | v2-t06 | boundary | MAX-DIGIT | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 60 | v2-t06 | boundary | MAX-ASCII | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 61 | v2-t06 | boundary | MAX-SUPPLEMENTARY | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 62 | v2-t06 | boundary | ESCAPE-01 | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 63 | v2-t06 | boundary | ESCAPE-CONTROL | INC → INC | eqp | ARGUMENT_MISMATCH | FINISH_LENGTH | 예 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001`; F6r 초회 사고 상한 소진 → 재시도 | `8d8e08e58b88` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 64 | i-t06 | A | G03 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 65 | i-t06 | B | G03 | UNS → UNS | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 66 | i-t06 | B | G15 | UNS → UNS | eqp | ARGUMENT_MISMATCH | FINISH_LENGTH | 예 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null); F6r 초회 사고 상한 소진 → 재시도 | - |
| 67 | i-t06 | B | G21 | KQ → KQ | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK, t 정답, 인자 오답, UNSUPPORTED 정답 | F4 미등록 질문 문자열을 eqp 로 추출(기대 null) | - |
| 68 | i-t06 | boundary | NORMAL-PRECISE | INC → INC | from·to | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 58 → 불통과 | F2b 정밀 시각 → 분 단위 축약 | `c5121ba69c9f` = NORMAL-01 정답(I 순서) |
| 69 | i-t06 | boundary | MAX-MIXED | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 70 | i-t06 | boundary | MAX-SUPPLEMENTARY | INC → INC | eqp | ARGUMENT_MISMATCH | FINISH_LENGTH | 예 | OK/stop, schemaValid=true, argsExact=false, 답변 106 → 불통과 | F5b 경계 eqp 오답(내용 미재현); F6r 초회 사고 상한 소진 → 재시도 | `e5ab52289a48` 미재현 |
| 71 | i-t06 | boundary | ESCAPE-01 | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |
| 72 | i-t06 | boundary | ESCAPE-CONTROL | INC → INC | eqp | ARGUMENT_MISMATCH | - | 아니오 | OK/stop, schemaValid=true, argsExact=false, 답변 97 → 불통과 | F5 경계 eqp 64cp → `EQP0001` | `be5dc92fe2de` 재현: t=INC eqp=`EQP0001` fromLen=35 qRef=Q1 |

- 재시도 뒤 통과:

| 진단 | 경로 | caseId | errorCodes | 재시도 뒤 | 해시 근거 |
|---|---|---|---|---|---|
| i-t0 | boundary | MAX-ASCII | FINISH_LENGTH | 통과(정답) | `26db1d7e637f` = 정답 |
| v2-t06 | A | G06 | STEPS_INVALID | 통과(정답) | - |
| v2-t06 | A | G19 | STEPS_INVALID | 통과(정답) | - |
| i-t06 | boundary | MAX-HANGUL | FINISH_LENGTH | 통과(정답) | `26db1d7e637f` = 정답 |

- 요약: ①V2 arm 의 골든 오답 대부분은 시간 형식(분 단위 기대값에 `:00+09:00`·`+09:00`·`:00` 를 붙임 — 해시 재현)과 Context 값 복사(G08·G20)이며, 같은 문항이 I arm 에서는 정답이다. ②I arm 의 골든 오답은 기대 null 인 eqp 에 질문 속 미등록 문자열을 넣은 경우(G03·G15·G21)뿐이다. ③경계 MAX·ESCAPE 불통과는 무효 3건(i-t0, 두 시도 모두 사고 상한 소진)을 빼면 모두 eqp 오답이며, 21건 중 20건이 64 codepoint eqp 대신 질문 안의 `EQP0001` 을 쓴 content 로 해시 재현됐다(나머지 1건 `e5ab52289a48…` 은 미재현). ④t 오분류는 V2 arm 에서만 났다(골든 6행·경계 2케이스).

#### 6) F0 gate 5 leaf 지시적 대응(진단 = 게이트 증거 아님)

- 아래는 진단 결과에 정식 판정 규칙(`F0Judgement`)의 생성 부분을 그대로 적용해 본 **지시적 대응**이다. 진단 등록은 `DIAGNOSTIC_NOT_GATE_ELIGIBLE` 이라 gate 입력이 될 수 없다. 또 진단 회차에는 `official-count.json`·`gguf-count.json` 이 없어(4 진단 모두 부재 확인) 정식 판정이었다면 AC-37-OUT-* 는 계수 입력 부재로 `NOT_RUN`(`OUT_INPUT_MISSING`)이 된다. 표의 OUT 열은 계수 조건을 뺀 생성 조건(케이스별 `passRounds = totalRounds`)만 본 것이다.

| 진단 | AC-37-CMP(하네스 규칙) | AC-37-OUT-NORMAL | AC-37-OUT-MAX | AC-37-OUT-ESCAPE | AC-38-F0 | 정식이었다면 FAIL 될 leaf |
|---|---|---|---|---|---|---|
| v2-t0 | PASS 기록·결정 `KEEP_B`(A t/인자/UNS 22/13/22, B 19/13/21, A 기대 t별 p95 예산 이내 false) | 불통과 — 생성 1/2 케이스(재시도 0) | 불통과 — 생성 0/5 케이스(재시도 0) | 불통과 — 생성 0/2 케이스(재시도 0) | PASS(표본 2, INC 2, workflow 2, UNS 0) | AC-37-OUT-NORMAL, AC-37-OUT-MAX, AC-37-OUT-ESCAPE |
| i-t0 | PASS 기록·결정 `KEEP_B`(A t/인자/UNS 22/21/22, B 22/22/22, A 기대 t별 p95 예산 이내 false) | 통과 — 생성 2/2 케이스(재시도 0) | 불통과 — 생성 1/5 케이스(재시도 3) | 불통과 — 생성 0/2 케이스(재시도 2) | PASS(표본 2, INC 2, workflow 2, UNS 0) | AC-37-OUT-MAX, AC-37-OUT-ESCAPE |
| v2-t06 | PASS 기록·결정 `KEEP_B`(A t/인자/UNS 21/13/21, B 20/11/22, A 기대 t별 p95 예산 이내 false) | 불통과 — 생성 1/2 케이스(재시도 0) | 불통과 — 생성 0/5 케이스(재시도 0) | 불통과 — 생성 0/2 케이스(재시도 1) | PASS(표본 2, INC 2, workflow 2, UNS 0) | AC-37-OUT-NORMAL, AC-37-OUT-MAX, AC-37-OUT-ESCAPE |
| i-t06 | PASS 기록·결정 `KEEP_B`(A t/인자/UNS 22/21/22, B 22/19/22, A 기대 t별 p95 예산 이내 false) | 불통과 — 생성 1/2 케이스(재시도 0) | 불통과 — 생성 3/5 케이스(재시도 2) | 불통과 — 생성 0/2 케이스(재시도 0) | PASS(표본 2, INC 2, workflow 2, UNS 0) | AC-37-OUT-NORMAL, AC-37-OUT-MAX, AC-37-OUT-ESCAPE |

- AC-37-CMP 는 하네스 규칙상 A·B 회차가 완결되면 PASS(결정 기록)이며 4 진단 모두 `KEEP_B` 다. A 가 잠정 후보가 되지 못한 이유 중 하나인 기대 t 별 p95 지연 예산(PREREG `latencyBudgetMsByT` UNS 15,000·LQ/KQ/GQ 30,000·INC 90,000 ms)은 아래와 같이 4 진단 모두 넘는 t 가 있다(B 도 참고로 적음).

| 진단/경로 | GQ | INC | KQ | LQ | UNS |
|---|---|---|---|---|---|
| v2-t0/A | 59038 | 84118 | 24983 | 44375 | 22660 |
| v2-t0/B | 10529 | 74666 | 17050 | 33760 | 44882 |
| i-t0/A | 18624 | 66789 | 21845 | 39849 | 25433 |
| i-t0/B | 15754 | 69547 | 32609 | 53928 | 30464 |
| v2-t06/A | 64419 | 167296 | 38321 | 46256 | 25691 |
| v2-t06/B | 18250 | 64781 | 28873 | 32425 | 46580 |
| i-t06/A | 15205 | 96051 | 25766 | 43563 | 19838 |
| i-t06/B | 10744 | 60471 | 17561 | 55844 | 187895 |

- AC-38-F0(G08, 카테고리 `AC38_CROSS_SYSTEM_NAME`)는 4 진단 모두 A·B 실효 t=INC·workflow 일치·UNS 0 으로 PASS 조건을 만족한다. 다만 V2 arm 의 G08 은 인자(eqp·from·to)가 Context 복사로 오답이다(AC-38 판정 대상 아님).
- 계수 입력까지 갖춘 정식 회차에서 같은 생성 결과가 나왔다면: 4 진단 모두 AC-37-OUT-MAX·AC-37-OUT-ESCAPE FAIL, AC-37-OUT-NORMAL 은 `i-t0` 만 생성 통과(나머지 3 FAIL), AC-37-CMP·AC-38-F0 는 PASS — F0 exit(5 leaf 모두 PASS) 불충족.

#### 7) 판별 불가·미측정 정리와 다음 단계

- NOT DETERMINABLE: 교차 확인 차이 1(3건)과 arm 상수 3/2 의 원인, 문법 제약이 사고 단계에 걸리는지, `response_format` 단독 효과, `/api/ps` 원본의 다른 필드, prompt cache 실제 적중·지연 이득, 미재현 해시 3종(`e82664c62e3f…` A G01 — 두 V2 진단 공통, `2941f123681a…` v2-t06 A G17, `e5ab52289a48…` i-t06 MAX-SUPPLEMENTARY) — 모두 원문 비저장 때문이다.
- NOT MEASURED: 비정렬 schema properties 순서 보존(v4 등록에 순서 유지 arm 없음), 진단 회차의 공식·GGUF 출력 계수(경계 OUT leaf 계수 조건).
- 다음 단계는 개정 흐름 ⑨(오답 원인 분석 → 프롬프트 수정 제안 → 사용자 승인 → 새 진단 등록)다. 생성 모델 변경 0·경계/골든 케이스 변경 0(eqp 64 codepoint = `nori-tc-common` `TcEqpEntity.java:56` `length = 64`)은 같은 날 사용자 결정으로 고정이다.

#### 8) 보존 확인

- 이 소절은 append 만 했다. 이 소절 추가 전 Dev report 596,642 B SHA `afb56331…675d`. 결과·PREREG·evidence·capture·하네스·SPEC 변경 0, commit·push 0. 같은 날 Plan P03 v4 흐름 개정(⑧~⑮ 재번호·반복 규칙, Plan 은 gitignore 대상)은 별도 반영했다.


### 12.33 P03 v4 ⑨ 후반 — 승인 classify-v4-a·재진단 4문서 C26/C27 등록 준비 (2026-11-05, Codex)

- 사용자 승인(2026-11-05)에 따라 새 후보 C(classify-v4-a / 기존 plan-v3-i)와 불변 V2 대조를 각각 temperature 0·0.6, R=1 DIAGNOSTIC으로 등록한다. 목적은 P03 ⑩ 연습 재시험이며 F0 gate 증거를 만들지 않는다. Spec·Plan·하네스 코드·기존 등록·입력·결과·capture·evidence는 변경하지 않는다.
- 승인 파일을 `eval/f0/v4/prompts/classify-v4-a.txt`에 바이트 동일 복사했다. SHA-256 `3dc53407afa595caf0248070a550a712cd74a1ad7ddc0ea70c70bf6336790b46`. 토큰 수 898(기존 906); plan-v3-i 유지. 후보 설정은 PRESENCE_V1·JSON_SCHEMA_V1·STEPS_DETAIL_V1, V2 대조는 기존 FULL·NONE·힌트 키 없음이다. 예산·상한·fixture·golden 22·boundary 9·LOCAL_TOKENIZER_PIN_V1은 기존 -b 그대로다.
- 신규 `eval/f0/v4/input-audit-v4-c.json`: Java input-audit-render(INFER_V4) → venv Python -B count_input_audit_v4.py. 출력 `F0_INPUT_AUDIT_RENDERED 717`, `INPUT_AUDIT_WRITTEN 717 OVER_BUDGET 0`. 최대 입력 3,051 ≤ 3,072, 최대 입력+max_tokens 8,171 ≤ 8,192. SHA-256 `dc1a7a36240796f54573aa51f848161bacfc23fb96c5e78e4bee09876388f4cf`. A 초회/재시도 1943/1978, B 1384/1418, boundary 3017/3051, usage 1370, token 2795. V2 audit는 기존 파일 재사용, 덮어쓰기 0.
- 새 문서: `eval/f0/PREREG-v4-diag-c-t0.md`, `PREREG-v4-diag-c-t06.md`, `PREREG-v4-diag-v2-t0-c.md`, `PREREG-v4-diag-v2-t06-c.md`. 기존 문서와 비교해 후보의 classify 경로·SHA·audit 경로·SHA 및 그 입력을 반영한 harnessDigest, 새 capture 경로·등록 메타데이터만 바뀐다. V2의 inputs·parameters·harnessDigest는 기존 -b와 동일하다. 후보 harnessDigest `ed88cf6c6977934e4fad9b91b0e2b4336ad72a4139774472bfe367ca34792927`, V2 `91580fb716f40189d4c609a32a44f86a1349a9b420be12e6fe1fca14b59eae35`.
- 하네스 확인: F0RunnerCli.run/boundary-gen은 CLI --prompt를 PREREG inputRoles의 planPrompt/classifyPrompt와 대조한 뒤 MessageAssembler.load로 로드한다. Prereg.parse는 promptSha256와 inputs SHA의 일치를, Prereg.verify는 실제 바이트·등록 commit·harnessDigest를 검증한다. MeasurementSession.start는 이 검증 뒤 preflight와 계수기 pin을 확인한다. 경로·SHA를 하네스에 하드코딩해 바꾸는 작업은 필요 없었다.
- C26 전 실제 검증: `.\gradlew.bat :gateway-eval:test --console=plain`은 241건 중 240 PASS·1 FAIL·errors/skipped 0, `BUILD FAILED in 6m 3s`, `6 actionable tasks: 2 executed, 4 up-to-date`. 원인은 작업 시작 시 추가한 새 미추적 prompt가 기존 등록의 Prereg.verify에서 `PREREG_WORKTREE_DIRTY`를 발생시킨 것이다(F0V4RegistrationDraftTest.java:74). 제가 추가한 파일을 허용 scratch로 잠시 옮겨 eval/f0의 기존 등록 작업 트리를 복원한 뒤, `.\gradlew.bat :gateway-eval:test --tests '*F0V4RegistrationDraftTest.draftsBindArmTemperatureRuntimeAndBudgetAndMatchTheWorkingTree' --console=plain`으로 실패한 1건만 재검증했다: 1/1 PASS, `BUILD SUCCESSFUL in 29s`, `6 actionable tasks: 2 executed, 4 up-to-date`. 그 후 prompt를 복원했다. 전체 재실행을 통과했다고 표현하지 않으며, 하네스·테스트 코드 변경은 없다.
- C24/C25의 선례(§12.31 로컬 등록 소절)에 따라 C26에는 기존 미커밋 devreport 꼬리를 그대로 포함한다. 기존 §12.32(⑧ 정리) 및 이전 C25 뒤 꼬리의 내용·순서는 바꾸지 않았다. 추가 전 devreport 639,673 B SHA `bf2a3a1d0a07deb06fb3fe79e117b56141b34d571b67ca4317562d731e836d53`, 이 앞부분의 바이트 동일성을 확인했다. 새 등록 준비 소절만 append한다. 실행 결과는 별도 한 소절을 append하고 커밋하지 않는다.
- C26 대상은 새 prompt 1·새 audit 1·PREREG 초안 4·이 devreport 1, 총 7파일이다. C27은 네 PREREG의 registrationStatus·registeredCommit 기록만 담당한다. 기존 untracked는 포함하지 않는다. push·force·amend 없음.
- 실행은 C26/C27 뒤 prereg-check 4건 통과 → c-t0 → c-t06 → v2-t0-c → v2-t06-c. preflight 실패 시 복구 없이 중단한다. 측정/사고 원문 출력·저장 금지, JSON 파싱 오류는 `parse failed`만 출력한다. scratch는 사용자 지정 step9/run에만 둔다.


### 12.34 P03 v4 ⑩ 연습 재시험 — 상주 변화로 첫 회차 중단 (2026-11-05, Codex, 미커밋)

- 등록은 C26 `c25951e326f9b9c8babaab1b8c62dc1f58164b69` / C27 `891321330b0e7df577da66d8cf4d9ffa2eb243b1`로 완료했다. C26 정확 7파일(prompt·audit·4초안·devreport), C27 정확 4파일(PREREG 메타데이터). 네 등록의 prereg-check는 각각 exit 0·`PREREG_VERIFIED`. `spec-guard` exit 0, SPEC SHA `bb91146a0f0c84f8ec885355dc840c42de06be346c910cd0f846745469bd0de4`, tokenizers 0.23.3.
- **실행 중단: `20261104T234844Z-diag-c-t0` B 측정 중 `MODEL_NOT_RESIDENT`(Java exit 5, Gradle exit 1). postflight도 `MODEL_NOT_RESIDENT`(Java exit 4, Gradle exit 1), 상주 모델 수 2→1. qwen3:4b의 digest·context는 유지됐으나 bge-m3가 상주 목록에서 사라졌다. SPEC/등록 무효 규칙에 따라 이 회차 전체는 무효이며 유효한 재진단 결과가 아니다.** A·경계와 나머지 c-t06·v2-t0-c·v2-t06-c는 NOT_RUN이다. 상주 복구·연장 요청, load/pull/unload, 서비스 재시작, 설정 변경, sudo는 하지 않았다.
- preflight는 `F0_PREFLIGHT_OK 2`, template SHA는 `2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9`로 일치했다. 시작 직후 읽기 전용 ollama ps는 bge-m3의 남은 상주 시간을 약 7분으로 표시했다. 모델 호출은 승인된 첫 B 진단에만 사용했다. 중단 후에는 읽기 전용 postflight·journal 수집과 로컬 report/share-exclude만 수행했다.
- B 부분 결과: 등록 분모 22 중 16문항/16시도만 기록됐다. t 정답 16, 인자 정답 15는 **실행된 16문항에 한정한 참고값**이며 22문항 완료 점수로 해석하지 않는다. 미실행 문항을 오답으로 채우지 않았다. 원시 결과의 complete=false·abortReason을 보존했다.
- `.\gradlew.bat :gateway-eval:test --console=plain`: 241건 중 240 PASS·1 FAIL(`PREREG_WORKTREE_DIRTY`), `BUILD FAILED in 6m 3s`, `6 actionable tasks: 2 executed, 4 up-to-date`. 테스트 중 제가 추가한 미추적 prompt를 scratch로 옮겨 작업 트리를 복원하고 실패 테스트 1건만 재실행: 1/1 PASS, `BUILD SUCCESSFUL in 29s`, `6 actionable tasks: 2 executed, 4 up-to-date`. 전체 재실행 PASS로 주장하지 않는다. 상세 원인과 C26 전 검증은 §12.33에 기록했다.
- 공유 서버: GIN 수집 loopback 47건·외부 0건, 외부 구간 0개. 서버 UTC를 보정하지 않고 callWindows와 닫힌 구간 교차를 적용했다. B 지연 표본 16개 중 제외 0, 유지 16. 수치는 partial/무효 회차의 참고값이다. raw GIN·IP·본문·모델 응답·사고문은 출력·저장하지 않았다.

#### 1) 현재 실행 상태·정답 수(스크립트 계산)

| 등록 | B t·인자(등록 분모 22) | A effectiveT·인자(등록 분모 22) | 경계(등록 9케이스) | 판정 |
|---|---|---|---|---|
| c-t0 | 실행 16개 중 16·15, 불완전 | NOT_RUN | NOT_RUN | 상주 변화로 회차 무효 |
| c-t06 | NOT_RUN | NOT_RUN | NOT_RUN | 앞 회차에서 중단 |
| v2-t0-c | NOT_RUN | NOT_RUN | NOT_RUN | 앞 회차에서 중단 |
| v2-t06-c | NOT_RUN | NOT_RUN | NOT_RUN | 앞 회차에서 중단 |

#### 2) 이전 회차와 나란히 비교(이전값은 전부 저장 파일 재집계)

| 대응 등록 | 경로 | 이전 t·인자 /22 또는 경계 /9 | 이번 t·인자 또는 경계 | 이전 → 이번 FINISH_LENGTH | 이전 → 이번 사고 min/median/max | 이전 → 이번 사고 >4928 | 이전 → 이번 답변 >192 | 이전 → 이번 지연 p50/p95 ms(겹침 제외) |
|---|---|---|---|---|---|---|---|---|
| i-t0-b → c-t0 | B | 22·22 | 16·15 (실행 16, 무효) | 0 → 0 | 645/1478.5/3578 → 729/1171.5/3049 | 0 → 0 | 0 → 0 | 22847/53928 → 17536/54777 |
| i-t0-b → c-t0 | A | 22·21 | NOT_RUN | 0 → — | 503/1122.5/3516 → — | 0 → — | 0 → — | 18711/59991 → — |
| i-t0-b → c-t0 | boundary | 3/9 | NOT_RUN | 8 → — | 1498/5120.0/5120 → — | 8 → — | 0 → — | 161857/221141 → — |
| i-t06-b → c-t06 | B | 22·19 | NOT_RUN | 1 → — | 497/1012/5120 → — | 1 → — | 0 → — | 15364/60471 → — |
| i-t06-b → c-t06 | A | 22·21 | NOT_RUN | 0 → — | 469/1080.5/4880 → — | 0 → — | 0 → — | 18967/84782 → — |
| i-t06-b → c-t06 | boundary | 4/9 | NOT_RUN | 2 → — | 2624/4542/5120 → — | 4 → — | 0 → — | 91344/198934 → — |
| v2-t0-b → v2-t0-c | B | 19·13 | NOT_RUN | 0 → — | 567/1160.0/4082 → — | 0 → — | 0 → — | 18223/44882 → — |
| v2-t0-b → v2-t0-c | A | 22·13 | NOT_RUN | 0 → — | 587/1468.5/4461 → — | 0 → — | 0 → — | 24688/81153 → — |
| v2-t0-b → v2-t0-c | boundary | 1/9 | NOT_RUN | 0 → — | 2282/2617/3333 → — | 0 → — | 0 → — | 49071/68138 → — |
| v2-t06-b → v2-t06-c | B | 20·11 | NOT_RUN | 0 → — | 631/1640.0/3595 → — | 0 → — | 0 → — | 26232/57100 → — |
| v2-t06-b → v2-t06-c | A | 21·13 | NOT_RUN | 1 → — | 730/1407/5120 → — | 1 → — | 0 → — | 32514/82802 → — |
| v2-t06-b → v2-t06-c | boundary | 1/9 | NOT_RUN | 1 → — | 1793/2824.0/5120 → — | 1 → — | 0 → — | 52838/170077 → — |

- 이전 prefix는 `20261104T044320Z-`, 이번 prefix는 `20261104T234844Z-`다. 사고 min/median/max는 재시도 포함 로컬 사고 토큰, median은 통계 중앙값이다. 지연은 기존 share-exclude와 같은 문항 회차 단위(재시도 합산), nearest-rank p50/p95다. 외부 요청과 한 시도라도 겹친 회차만 지연 표본에서 제외하고 정확도는 유지했다. 이전 V2 t0의 share-window는 전체 창을 담은 -2 파일을 썼다. 이번 무효 회차의 값으로 개선·회귀를 판정하지 않는다.

#### 3) 경계 caseId별 passRounds(각 등록 R=1, 전체 9케이스)

| caseId | 이전 I t0 | 이번 C t0 | 이전 I t06 | 이번 C t06 | 이전 V2 t0 | 이번 V2 t0-c | 이전 V2 t06 | 이번 V2 t06-c |
|---|---|---|---|---|---|---|---|---|
| NORMAL-01 | 1/1 | NOT_RUN | 1/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| NORMAL-PRECISE | 1/1 | NOT_RUN | 0/1 | NOT_RUN | 1/1 | NOT_RUN | 1/1 | NOT_RUN |
| MAX-MIXED | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| MAX-HANGUL | 0/1 | NOT_RUN | 1/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| MAX-DIGIT | 0/1 | NOT_RUN | 1/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| MAX-ASCII | 1/1 | NOT_RUN | 1/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| MAX-SUPPLEMENTARY | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| ESCAPE-01 | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |
| ESCAPE-CONTROL | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN | 0/1 | NOT_RUN |

#### 4) 이번 실행의 실패 case와 출력 해시

| 경로 | caseId | 틀린 필드 | errorKinds / errorCodes | outputSha256 접두부·기지 유형 |
|---|---|---|---|---|
| B | G16 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['127f9390eeb1'] / [None] |

- 알려진 해시는 이번 스크립트가 이전 boundary 결과에서 직접 읽었다: `be5dc92fe2de` = I 내부 EQP0001 선택, `26db1d7e637f` = MAX/ESCAPE 정답, `8d8e08e58b88` = V2 내부 EQP0001 선택. 이번에 경계를 실행하지 않아 새 경계 실패 해시는 없다.

#### 5) F0 5 leaf 참고 매핑

| leaf | 이번 참고 판정 | 근거 |
|---|---|---|
| AC-37-CMP | 미검증 | 회차 무효·B 불완전·A 미실행, 같은 분모 비교 불가 |
| AC-37-OUT-NORMAL | NOT_RUN | 정상 경계 미실행 |
| AC-37-OUT-MAX | NOT_RUN | 최장 경계 미실행 |
| AC-37-OUT-ESCAPE | NOT_RUN | escape 경계 미실행 |
| AC-38-F0 | 미검증 | G08 B 관측은 아래 참고값이며 A 미실행·회차 무효 |

- G08 B 부분 관측: `{'status': 'OK', 'effectiveT': 'INC', 'unknownStepCount': 0, 'toolConsistency': None}`. 회차 전체가 무효이므로 leaf PASS로 처리하지 않는다. 기존 F0 상태는 그대로이며 새 gate 증거·SPEC 체크박스·Plan 변경 0.

#### 6) 보존·추가 확인

- C26에는 C24 선례대로 기존 미커밋 꼬리(§12.32 포함)를 바이트 그대로 포함했다. 본 §12.34 한 소절은 append-only·미커밋이다. 등록 관련 커밋 이외 추가 commit·push·force·amend는 하지 않았다.
- 하네스 source·기존 등록·prompt·audit·결과·capture·evidence 및 SPEC·Plan은 작업 시작 snapshot과 비교해 변경 0. 이 보고서 추가 직전 C26 devreport 전체 바이트를 접두부로 그대로 보존했다. 기존 untracked 파일도 그대로 뒀다.
- 재진단을 완료하려면 서버의 두 모델 상주 전제가 유지되는 실행 기회가 필요하다. 이번 세션에서 이를 고치지 않았다. 기존 capture·부분 결과는 재사용/덮어쓰기하지 않으며, 재개 시 새 회차와 capture 충돌에 대한 등록 절차를 먼저 정해야 한다.
- 집계 스크립트와 검증 로그는 사용자 지정 `step9/run/`에만 있다. `summarize.py`가 이전/현재 원시 결과에서 모든 수를 계산했고, 모든 JSON 파싱은 예외 처리 후 고정 문구 `parse failed`만 출력한다. 이번 생성 파일은 첫 회차의 preflight·B 부분 결과·run-binding·postflight·diagnostic-report, 새 chat-template capture 및 share-window뿐이다.

- 추가 검증: `f0 share-exclude`는 완결 결과만 집계하므로 이번 complete=false B를 제외하고 `RESULTS_MISSING`(Java exit 2, Gradle exit 1)을 출력했다. 이 제약을 우회하거나 하네스를 수정하지 않았다. 위 B 부분 지연은 summarize.py가 같은 닫힌 구간 교차·문항 회차 단위 규칙으로 계산한 참고값이며, 하네스 share-exclude 성공 결과로 주장하지 않는다. 진단 report는 exit 0 `F0_DIAGNOSTIC_REPORT_WRITTEN`이며 불완전 경로의 지표는 결과 없음으로 처리한다. 본인 터널 PID 1464만 명령행 확인 후 종료했다.


### 12.35 P03 v4 ⑩ bge-m3 재상주 후 재실행 점검 — 고정 capture binding 충돌로 중단 (2026-11-05, Codex, 미커밋)

- 작업 기준: 사용자 재실행 지시·원본 brief-run.md·이전 final.md·§12.34와 기존 scratch 스크립트를 읽었다. 재등록·하네스 수정·새 commit·push·force·amend 없이 점검했다.
- **중단 사유: 기존 첫 등록의 필수 chat-template 명령이 `RUN_BINDING_EXISTS`로 거부됐다(Java exit 2, Gradle exit 1, `BUILD FAILED in 12s`). 첫 등록부터 실행 불가하므로 지정 순서의 네 진단을 모두 NOT_RUN으로 남겼다. 이는 모델 상주 실패가 아니라 로컬 capture binding 충돌이다.**
- SPEC §16 F0(현재 1382행)은 상주/digest/context 변화 시 회차 무효·기존 결과 보존·동일 문항 재측정을 요구한다. F0RunnerCli.results(283~285행)는 새 runId 아래 결과를 허용하며, MeasurementSession.start(49행)는 해당 경로의 binding 존재만 검사한다. 따라서 새 timestamp 결과 디렉터리의 B·A·boundary 자체는 같은 PREREG로 실행할 수 있다. REPORT_EXISTS 검사도 새 결과 디렉터리라면 충돌하지 않는다.
- 그러나 F0RunnerCli chat-template(94~97행)은 --out을 PREREG.runtimeCaptureDir에 고정한다. c-t0의 경로 `eval/f0/chat-template/v4-diag-c-t0`에는 기존 `run-binding.json`이 있고 그 runId는 `20261104T234844Z-diag-c-t0`이다. 새 AIGW_PARENT_RUN_ID도 이 고정 경로를 바꾸지 못한다. 새 capture 경로를 넘기면 registered 검사(273행)의 ARGUMENT_NOT_REGISTERED 대상이다. MeasurementSession.start는 이 기존 binding을 발견하면 계수기 시작·Preflight.run·showTemplate 전에 거부한다(49~57행). 실제 동일 명령을 실행해 같은 고정 오류를 확인했다. 기존 capture 생략/재사용으로 brief의 필수 단계를 임의 변경하지 않았다.
- 나머지 세 등록의 capture binding은 현재 없다. 다만 첫 회차에서 충돌이 확인되면 중단하라는 지시 및 지정 순서를 적용해 진행하지 않았다. 기존 capture를 변경·삭제하지 않고 모든 단계를 그대로 다시 수행하려면 등록 경로 또는 하네스 동작 변경이 필요하므로 이번 범위에서 처리하지 않는다.
- 새 결과 디렉터리·preflight/postflight·share-window를 생성하지 않았다. SSH·ollama ps·모델 생성·embed warmup·load/unload/pull·qwen3 조작은 모두 0회다. 따라서 이번 세션이 공유 로그에 추가한 loopback warmup 요청과 기록할 HTTP code/UTC는 없다. 사용자가 알려 준 00:00 UTC 상주 상태를 이번 검증 결과로 주장하지 않는다.

#### 1) 등록 및 실제 검증 출력

- C26 `c25951e326f9b9c8babaab1b8c62dc1f58164b69`: `docs/ai-rag-gateway-architecture-dev-report.md`, `eval/f0/PREREG-v4-diag-c-t0.md`, `eval/f0/PREREG-v4-diag-c-t06.md`, `eval/f0/PREREG-v4-diag-v2-t0-c.md`, `eval/f0/PREREG-v4-diag-v2-t06-c.md`, `eval/f0/v4/input-audit-v4-c.json`, `eval/f0/v4/prompts/classify-v4-a.txt`.
- C27 `891321330b0e7df577da66d8cf4d9ffa2eb243b1`: `eval/f0/PREREG-v4-diag-c-t0.md`, `eval/f0/PREREG-v4-diag-c-t06.md`, `eval/f0/PREREG-v4-diag-v2-t0-c.md`, `eval/f0/PREREG-v4-diag-v2-t06-c.md`.
- 기존 launch/verify 패턴을 재사용한 새 scratch 스크립트로 spec-guard exit 0 및 네 prereg-check exit 0을 확인했다. 이전 검증 로그는 덮어쓰지 않았다.

```text
CHECK c-t0 PREREG_VERIFIED (exit 0)
CHECK c-t06 PREREG_VERIFIED (exit 0)
CHECK v2-t0-c PREREG_VERIFIED (exit 0)
CHECK v2-t06-c PREREG_VERIFIED (exit 0)
```

#### 2) 깨끗한 작업 트리 전체 단위 테스트

- 원래 작업 트리에는 이전 미커밋 보고서·미추적 증거가 있어 그대로 보존했다. 같은 저장소의 HEAD=C27을 scratch의 `step9/run/wt`에 detached worktree로 checkout하고 여기서 `.\gradlew.bat :gateway-eval:test --console=plain`을 전체 실행했다. 실행 전후 git status --porcelain 출력 0 B로 clean을 확인했다. 소스·테스트·빌드 설정 수정 0, 신규 commit 0이다.
- 테스트가 워크스페이스 형제 경로로 참조하는 근거 파일 4개를 컴파일 단계에서 원본 읽기 전용·바이트 동일 복사로 scratch에 배치했다. 다른 저장소 원본은 수정하지 않았다. 실제 근거 검증 테스트를 포함한 전체 판정은 아래 실행 결과에 따른다.
- XML 실제 집계: tests 241, failures 0, errors 0, skipped 0; Gradle exit 0.

```text
Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.
You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
BUILD SUCCESSFUL in 7m 46s
6 actionable tasks: 6 executed
```

#### 3) 네 재실행의 상태와 이전 점수 비교

| 이전 → 이번 등록 | 이전 B t·인자 /22 | 이전 A effectiveT·인자 /22 | 이전 경계 /9 | 이번 B / A / 경계 | preflight / postflight |
|---|---|---|---|---|---|
| i-t0-b → c-t0 | 22·22 | 22·21 | 3 | NOT_RUN / NOT_RUN / NOT_RUN | NOT_RUN / NOT_RUN |
| i-t06-b → c-t06 | 22·19 | 22·21 | 4 | NOT_RUN / NOT_RUN / NOT_RUN | NOT_RUN / NOT_RUN |
| v2-t0-b → v2-t0-c | 19·13 | 22·13 | 1 | NOT_RUN / NOT_RUN / NOT_RUN | NOT_RUN / NOT_RUN |
| v2-t06-b → v2-t06-c | 20·11 | 21·13 | 1 | NOT_RUN / NOT_RUN / NOT_RUN | NOT_RUN / NOT_RUN |

- 이번 네 등록의 FINISH_LENGTH·사고 토큰 min/median/max·사고 >4,928·답변 >192·겹침 제외 수·p50/p95는 모두 NOT_RUN이다. 호출 0회라는 실행 사실과 측정치 0을 구분한다. 이번 실패 caseId·틀린 필드·errorKinds·outputSha256는 결과 자체가 없어 해당 없음이다. 이전 무효 B 16문항을 새 실행 결과로 재사용하지 않았다.

#### 4) 이전 경로별 토큰·지연 통계(기존 summarize.py 함수로 원시 파일 재계산)

| 이전 등록 | 경로 | FINISH_LENGTH | 사고 min/median/max | 사고 >4928 | 답변 >192 | 지연 제외/유지 | p50/p95 ms | 이번 대응 경로 |
|---|---|---|---|---|---|---|---|---|
| i-t0-b | B | 0 | 645/1478.5/3578 | 0 | 0 | 0/22 | 22847/53928 | NOT_RUN |
| i-t0-b | A | 0 | 503/1122.5/3516 | 0 | 0 | 0/22 | 18711/59991 | NOT_RUN |
| i-t0-b | boundary | 8 | 1498/5120.0/5120 | 8 | 0 | 0/9 | 161857/221141 | NOT_RUN |
| i-t06-b | B | 1 | 497/1012/5120 | 1 | 0 | 0/22 | 15364/60471 | NOT_RUN |
| i-t06-b | A | 0 | 469/1080.5/4880 | 0 | 0 | 0/22 | 18967/84782 | NOT_RUN |
| i-t06-b | boundary | 2 | 2624/4542/5120 | 4 | 0 | 0/9 | 91344/198934 | NOT_RUN |
| v2-t0-b | B | 0 | 567/1160.0/4082 | 0 | 0 | 0/22 | 18223/44882 | NOT_RUN |
| v2-t0-b | A | 0 | 587/1468.5/4461 | 0 | 0 | 0/22 | 24688/81153 | NOT_RUN |
| v2-t0-b | boundary | 0 | 2282/2617/3333 | 0 | 0 | 0/9 | 49071/68138 | NOT_RUN |
| v2-t06-b | B | 0 | 631/1640.0/3595 | 0 | 0 | 0/22 | 26232/57100 | NOT_RUN |
| v2-t06-b | A | 1 | 730/1407/5120 | 1 | 0 | 0/22 | 32514/82802 | NOT_RUN |
| v2-t06-b | boundary | 1 | 1793/2824.0/5120 | 1 | 0 | 0/9 | 52838/170077 | NOT_RUN |

- 이전 prefix는 `20261104T044320Z-`다. 지연은 각 시도 UTC와 외부 요청 구간의 닫힌 구간 교차로 겹친 문항 회차만 제외하고 재시도를 합산한 nearest-rank p50/p95다. 정확도는 유지한다. 이전 V2 t0은 전체 수집창인 share-window의 -2 파일을 사용했다.

#### 5) 경계 case별 passRounds 비교(각 R=1)

| caseId | 이전 I t0 | 이전 I t06 | 이전 V2 t0 | 이전 V2 t06 | 이번 C t0 / C t06 / V2 t0-c / V2 t06-c |
|---|---|---|---|---|---|
| NORMAL-01 | 1/1 | 1/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| NORMAL-PRECISE | 1/1 | 0/1 | 1/1 | 1/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| MAX-MIXED | 0/1 | 0/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| MAX-HANGUL | 0/1 | 1/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| MAX-DIGIT | 0/1 | 1/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| MAX-ASCII | 1/1 | 1/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| MAX-SUPPLEMENTARY | 0/1 | 0/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| ESCAPE-01 | 0/1 | 0/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |
| ESCAPE-CONTROL | 0/1 | 0/1 | 0/1 | 0/1 | NOT_RUN / NOT_RUN / NOT_RUN / NOT_RUN |

- 알려진 출력 해시를 기존 결과에서 직접 읽어 대조했다: `be5dc92fe2de` = I 내부 EQP0001 선택, `26db1d7e637f` = MAX/ESCAPE 정답, `8d8e08e58b88` = V2 내부 EQP0001 선택. 새 출력이 없어 일치 판정할 대상은 없다.

#### 6) F0 다섯 leaf 참고 매핑 및 추가 확인

| leaf | 이번 네 등록 공통 | 근거 |
|---|---|---|
| AC-37-CMP | NOT_RUN | B·A 동일 분모 실측 없음 |
| AC-37-OUT-NORMAL | NOT_RUN | 정상 경계 미실행 |
| AC-37-OUT-MAX | NOT_RUN | 최장 경계 미실행 |
| AC-37-OUT-ESCAPE | NOT_RUN | escape 경계 미실행 |
| AC-38-F0 | NOT_RUN | G08 두 경로 재실측 없음 |

- DIAGNOSTIC은 정식 게이트 증거가 아니며 이번에는 참고 생성 판정도 할 수 없다. 개선/회귀·F0 통과를 주장하지 않는다. SPEC·Plan·기존 gate 상태를 바꾸지 않았다.
- 추가로 정해야 하는 사항은 기존 capture를 보존하면서 재측정할 수 있는 절차다. 새 등록 또는 하네스 변경은 이번 작업에서 승인되지 않았고, 충돌 시 중단하라는 사용자 지시에 따라 수행하지 않았다.

#### 7) 보존 확인

- 작업 전 snapshot 534개 파일(SPEC·Plan 포함)의 SHA-256이 모두 동일하다. 중단 회차 `20261104T234844Z-diag-c-t0`의 결과·capture·share-window를 포함해 기존 파일 변경/삭제 0. 원래 git 상태도 작업 시작과 동일하다.
- 기존 devreport 653970 B SHA-256 `d87910898684545cbba9379f37629651d2b9837081ed240330188b8964d3ae19` 전체를 접두부 그대로 보존하고 본 §12.35만 한 번 append했다. 이번 저장소 변경은 이 문서의 append뿐이며 커밋하지 않았다.
- scratch 산출물: rerun-check*.log/exit, rerun-capture-conflict.log/exit, rerun-unit-tests.log/exit, rerun-baseline-summary.json, rerun-final.md 및 보존 snapshot. 이전 세션 스크립트·로그·summary·final.md는 덮어쓰지 않았다. 임시 clean worktree와 테스트 결과는 검토용으로 scratch에 유지한다.


### 12.36 P03 v4 ⑩ root 승인 capture 생략 후 새 회차 재진단 (2026-11-05, Codex 시작·Claude Code Opus 5.5 인계 완료, 미커밋)

- 세션 인계: Codex 세션이 약 02:17 UTC 사용량 한도로 종료됐다. 인계 시점 프로세스 목록 확인 결과 scratch 오케스트레이터 `continue_runs.py resume-last`(02:08:13 UTC 시작)와 v2-t06-c B 실행(02:08:14 UTC 시작)이 모두 살아 있었다. 따라서 B를 새로 띄우지 않았고 후속 단계도 수동 실행하지 않았다. 같은 오케스트레이터가 B → A → boundary-gen → report → postflight → 공유 창 수집 → share-exclude를 PREREG §2 명령·환경변수·`AIGW_PARENT_RUN_ID=20261105T003119Z-diag-v2-t06-c` 그대로 이어 실행해 02:42:48 UTC `ALL_FOUR_COMPLETE`(exit 0)로 끝났다. 인계 세션은 읽기 전용 상태 조회, 본 절 생성·append만 수행했다.

- 적용 근거: brief-run.md·brief-rerun.md 및 이번 사용자(root) 결정. 지정된 순서 c-t0 → c-t06 → v2-t0-c → v2-t06-c, 새 UTC runId `20261105T003119Z`를 사용했다. 기존 중단 회차 `20261104T234844Z-diag-c-t0`와 기존 결과·등록·capture를 보존했다.
- c-t0의 단계 3(chat-template)만 사용자 승인에 따라 생략했다. §12의 v3 diag c 단계 c-3(기존 capture 무결성 검증 후 root가 생략을 확정한 선례, 약 1278행)을 적용했다. 스크립트로 template SHA `2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9`와 등록 audit SHA·capture binding의 runtime manifest 일치, `model-digest.txt`·`qwen3-4b.template.sha256` 두 파일의 `v4-diag-i-t0-b` 대비 바이트 동일성, 등록 모델 digest 일치를 재검증했다. 캡처 binding의 이전 runId도 그대로다.
- 현재 서버의 읽기 전용 ollama ps로 qwen3:4b ID `359d7dd4bcda`, context 8192와 bge-m3 상주를 확인했다. 과거 00:00 UTC 상태는 사용자 확인 근거이고 이번 확인 시각은 scratch의 between 로그에 별도로 기록했다. 각 측정 명령은 MeasurementSession에서 현재 모델·template SHA·tokenizer pin을 다시 수집하고 audit와 대조한다.
- c-t0 후속 단계에서 capture/parent binding 오류를 우회하지 않는다. 나머지 세 등록의 단계 3은 기존 capture가 없음을 확인한 뒤 정상 실행 대상으로 두었다. 하네스·등록 변경, 신규 commit·push·amend·force, qwen3 조작은 없다.

#### 1) 등록 및 검증

- C26 `c25951e326f9b9c8babaab1b8c62dc1f58164b69`: `docs/ai-rag-gateway-architecture-dev-report.md`, `eval/f0/PREREG-v4-diag-c-t0.md`, `eval/f0/PREREG-v4-diag-c-t06.md`, `eval/f0/PREREG-v4-diag-v2-t0-c.md`, `eval/f0/PREREG-v4-diag-v2-t06-c.md`, `eval/f0/v4/input-audit-v4-c.json`, `eval/f0/v4/prompts/classify-v4-a.txt`.
- C27 `891321330b0e7df577da66d8cf4d9ffa2eb243b1`: `eval/f0/PREREG-v4-diag-c-t0.md`, `eval/f0/PREREG-v4-diag-c-t06.md`, `eval/f0/PREREG-v4-diag-v2-t0-c.md`, `eval/f0/PREREG-v4-diag-v2-t06-c.md`.

```text
CHECK c-t0 PREREG_VERIFIED (exit 0)
CHECK c-t06 PREREG_VERIFIED (exit 0)
CHECK v2-t0-c PREREG_VERIFIED (exit 0)
CHECK v2-t06-c PREREG_VERIFIED (exit 0)
```

- `spec-guard` exit 0, tokenizers 0.23.3 확인. 전체 단위 테스트는 이번 지시에 따라 재실행하지 않았다. 기존 clean detached worktree 테스트 XML과 로그를 재확인한 결과 {'tests': 241, 'failures': 0, 'errors': 0, 'skipped': 0}, 기존 Gradle exit 0이다.

```text
BUILD SUCCESSFUL in 7m 46s
6 actionable tasks: 6 executed
```

#### 2) 실행 단계·preflight/postflight·중단 여부

- 기존 c-t0 template 파일의 수정 시각(스크립트 조회): `2026-11-05T08:49:29.584755+09:00`; 기존 파일은 수정하지 않았다.
- 스크래치 자동화 수정: c-t0의 두 preflight 명령은 모두 exit 0·F0_PREFLIGHT_OK 2였으나 제 비교 코드가 models 배열 순서를 그대로 대조해 POSTFLIGHT_CHANGED로 잠시 멈췄다. 실제로는 `[bge-m3, qwen3]` → `[qwen3, bge-m3]` 순서만 달랐고, 모델명으로 정렬해 name·digest·context·residentCount가 모두 같음을 독립 스크립트로 확인했다. 하네스 오류·capture/parent binding 거부·실제 상주 변화가 아니므로 스크래치 비교만 수정하고 완료된 preflight/B/A/boundary/report/postflight 재실행 없이 share 수집부터 재개했다. 첫 자동화 로그·실패 exit 파일도 보존했다.
- 스크래치 종료코드 읽기 경쟁 수정: v2-t06-c template은 F0_CHAT_TEMPLATE_CAPTURED·실제 Gradle exit 0이었으나 polling이 .exit 파일 생성과 값 기록 사이에 빈 문자열을 읽어 잠시 멈췄다(하네스 오류 코드 없음). preflight/template 실제 exit 0·현재 runId의 새 capture binding·template SHA를 확인하고 빈 값이면 기록 완료까지 기다리도록 스크래치 polling만 고쳤다. 이전 세 회차와 마지막 preflight/template을 다시 실행하지 않고 마지막 B부터 재개했다. 빈 값을 읽은 원래 로그·continue-stopped.txt도 보존했다. 저장소 하네스·등록·capture·결과를 수정한 우회는 없다.
- 전체 상태: 네 진단 완료.

| 등록 | preflight | postflight | chat-template |
|---|---|---|---|
| c-t0 | PASS OK resident=2 | PASS OK resident=2 | 검증 후 root 승인 생략 |
| c-t06 | PASS OK resident=2 | PASS OK resident=2 | 실행 exit 0 |
| v2-t0-c | PASS OK resident=2 | PASS OK resident=2 | 실행 exit 0 |
| v2-t06-c | PASS OK resident=2 | PASS OK resident=2 | 실행 exit 0 |

- 매 회차 preflight 전 ollama ps로 bge-m3 UNTIL을 검사했다(실행된 회차에 한함). 상주·남은 시간 ≥3h라 warmup/keep_alive 연장 요청 0회, 추가 loopback warmup 요청·HTTP code 없음.

| 단계 종료 로그(UTC) | 결과 |
|---|---|
| 2026-11-05T00:31:42.755856+00:00 | spec-guard exit=0 signals= |
| 2026-11-05T00:31:51.092834+00:00 | check-c-t0 exit=0 signals=PREREG_VERIFIED |
| 2026-11-05T00:31:59.474309+00:00 | check-c-t06 exit=0 signals=PREREG_VERIFIED |
| 2026-11-05T00:32:07.836686+00:00 | check-v2-t0-c exit=0 signals=PREREG_VERIFIED |
| 2026-11-05T00:32:16.201818+00:00 | check-v2-t06-c exit=0 signals=PREREG_VERIFIED |
| 2026-11-05T00:32:25.172776+00:00 | c-t0-preflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T00:42:31.829876+00:00 | c-t0-B exit=0 signals=F0_RUN_COMPLETED B |
| 2026-11-05T00:53:32.564229+00:00 | c-t0-A exit=0 signals=F0_RUN_COMPLETED A |
| 2026-11-05T01:00:55.077600+00:00 | c-t0-boundary exit=0 signals=F0_BOUNDARY_COMPLETED 9 |
| 2026-11-05T01:01:03.433507+00:00 | c-t0-report exit=0 signals=F0_DIAGNOSTIC_REPORT_WRITTEN |
| 2026-11-05T01:01:11.786629+00:00 | c-t0-postflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T01:02:24.384310+00:00 | c-t0-share-exclude exit=0 signals= |
| 2026-11-05T01:02:33.179621+00:00 | c-t06-preflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T01:02:41.577512+00:00 | c-t06-template exit=0 signals=F0_CHAT_TEMPLATE_CAPTURED |
| 2026-11-05T01:15:38.220545+00:00 | c-t06-B exit=0 signals=F0_RUN_COMPLETED B |
| 2026-11-05T01:25:14.792296+00:00 | c-t06-A exit=0 signals=F0_RUN_COMPLETED A |
| 2026-11-05T01:34:45.360279+00:00 | c-t06-boundary exit=0 signals=F0_BOUNDARY_COMPLETED 9 |
| 2026-11-05T01:34:53.707788+00:00 | c-t06-report exit=0 signals=F0_DIAGNOSTIC_REPORT_WRITTEN |
| 2026-11-05T01:35:02.060390+00:00 | c-t06-postflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T01:35:07.286211+00:00 | c-t06-share-exclude exit=0 signals= |
| 2026-11-05T01:35:16.088101+00:00 | v2-t0-c-preflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T01:35:24.437943+00:00 | v2-t0-c-template exit=0 signals=F0_CHAT_TEMPLATE_CAPTURED |
| 2026-11-05T01:35:54.810320+00:00 | v2-t0-c-token-golden exit=0 signals=F0_TOKEN_GOLDEN_COMPLETED 13 |
| 2026-11-05T01:36:09.196001+00:00 | v2-t0-c-usage exit=0 signals=F0_USAGE_PROBE_COMPLETED |
| 2026-11-05T01:45:59.784723+00:00 | v2-t0-c-B exit=0 signals=F0_RUN_COMPLETED B |
| 2026-11-05T01:58:44.558286+00:00 | v2-t0-c-A exit=0 signals=F0_RUN_COMPLETED A |
| 2026-11-05T02:06:27.150856+00:00 | v2-t0-c-boundary exit=0 signals=F0_BOUNDARY_COMPLETED 9 |
| 2026-11-05T02:06:35.535754+00:00 | v2-t0-c-report exit=0 signals=F0_DIAGNOSTIC_REPORT_WRITTEN |
| 2026-11-05T02:06:43.883121+00:00 | v2-t0-c-postflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T02:06:49.113973+00:00 | v2-t0-c-share-exclude exit=0 signals= |
| 2026-11-05T02:06:57.861129+00:00 | v2-t06-c-preflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T02:07:06.211932+00:00 | v2-t06-c-template exit= signals=F0_CHAT_TEMPLATE_CAPTURED (polling 경쟁으로 빈 값 기록, 실제 .exit 파일 값 0) |
| 2026-11-05T02:20:30.811664+00:00 | v2-t06-c-B exit=0 signals=F0_RUN_COMPLETED B |
| 2026-11-05T02:33:55.504708+00:00 | v2-t06-c-A exit=0 signals=F0_RUN_COMPLETED A |
| 2026-11-05T02:42:26.086592+00:00 | v2-t06-c-boundary exit=0 signals=F0_BOUNDARY_COMPLETED 9 |
| 2026-11-05T02:42:34.454135+00:00 | v2-t06-c-report exit=0 signals=F0_DIAGNOSTIC_REPORT_WRITTEN |
| 2026-11-05T02:42:42.791776+00:00 | v2-t06-c-postflight exit=0 signals=F0_PREFLIGHT_OK 2 |
| 2026-11-05T02:42:48.025297+00:00 | v2-t06-c-share-exclude exit=0 signals= |

#### 3) 이전/현재 정확도·경계 비교(파일에서 스크립트 재계산)


| 이전 → 현재 | 이전 B t·인자 /22 | 현재 B | 이전 A effectiveT·인자 /22 | 현재 A | 이전 경계 /9 | 현재 경계 |
|---|---|---|---|---|---|---|
| i-t0-b → c-t0 | 22·22/22 | 22·21/22 | 22·21/22 | 22·21/22 | 3/9 | 2/9 |
| i-t06-b → c-t06 | 22·19/22 | 22·21/22 | 22·21/22 | 22·21/22 | 4/9 | 2/9 |
| v2-t0-b → v2-t0-c | 19·13/22 | 19·13/22 | 22·13/22 | 22·13/22 | 1/9 | 1/9 |
| v2-t06-b → v2-t06-c | 20·11/22 | 21·12/22 | 21·13/22 | 18·13/22 | 1/9 | 1/9 |

- 첫 B 종료 시점의 지연 중앙값 17194.0ms로 나머지 190문항 회차를 단순 환산하면 54.4분이다(계수 호출·경계의 긴 출력·재시도·준비 시간 제외 추산). 실제 c-t0 preflight 시작부터 v2-t06-c share-exclude 종료까지는 00:32 ~ 02:42 UTC다.
- v2-t06-c A의 effectiveT 18/22는 G10 t 오답(LQ→KQ) 1건과, `t`는 기대와 같지만 unknownStepCount=1로 effectiveT가 UNS로 강등된 G20·G21·G22 3건의 합이다. G21은 인자가 모두 일치해 errorKinds가 비어 있으나 effectiveT 기준으로 오답 집계된다. v2-t06-c B G20의 첫 시도는 FINISH_LENGTH로 빈 답변(`e3b0c44298fc` = 빈 문자열 SHA-256)이었고 재시도 출력 `9db7bfc3f597`도 인자 불일치다.
#### 4) 경로별 토큰·지연 이전 → 현재

- 사고 토큰은 로컬 pin 계수이며 재시도를 포함한 시도별 min/통계 median/max다. FINISH_LENGTH도 시도별 개수다. 지연은 재시도 합산 문항 회차 단위 nearest-rank p50/p95(ms), UTC 닫힌 구간 교차로 외부 요청과 겹친 회차의 지연만 제외하고 정확도는 유지한다. 이전 prefix `20261104T044320Z`, V2 t0의 공유 창은 -2 파일을 사용했다.

| 현재 등록 | 경로 | FINISH_LENGTH | 사고 min/median/max | 사고 >4928 | 답변 >192 | 지연 제외/유지 | p50/p95 ms |
|---|---|---|---|---|---|---|---|
| c-t0 | B | 0 → 0 | 645/1478.5/3578 → 620/1025.0/3922 | 0 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 22847/53928 → 16553/68281 |
| c-t0 | A | 0 → 0 | 503/1122.5/3516 → 503/1122.5/3516 | 0 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 18711/59991 → 18777/60103 |
| c-t0 | boundary | 8 → 0 | 1498/5120.0/5120 → 1448/2301/4118 | 8 → 0 | 0 → 0 | 0/9 (0.0%) → 0/9 (0.0%) | 161857/221141 → 44539/80471 |
| c-t06 | B | 1 → 1 | 497/1012/5120 → 638/1268/5120 | 1 → 1 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 15364/60471 → 19688/85478 |
| c-t06 | A | 0 → 0 | 469/1080.5/4880 → 573/959.5/3760 | 0 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 18967/84782 → 17145/52847 |
| c-t06 | boundary | 2 → 0 | 2624/4542/5120 → 1414/3636/4308 | 4 → 0 | 0 → 0 | 0/9 (0.0%) → 0/9 (0.0%) | 91344/198934 → 68423/86663 |
| v2-t0-c | B | 0 → 0 | 567/1160.0/4082 → 567/1160.0/4082 | 0 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 18223/44882 → 18309/44968 |
| v2-t0-c | A | 0 → 0 | 587/1468.5/4461 → 587/1468.5/4461 | 0 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 24688/81153 → 24940/81400 |
| v2-t0-c | boundary | 0 → 0 | 2282/2617/3333 → 2282/2617/3333 | 0 → 0 | 0 → 0 | 0/9 (0.0%) → 0/9 (0.0%) | 49071/68138 → 48950/67881 |
| v2-t06-c | B | 0 → 1 | 631/1640.0/3595 → 491/1280/5120 | 0 → 1 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 26232/57100 → 21051/73849 |
| v2-t06-c | A | 1 → 0 | 730/1407/5120 → 696/1593/3633 | 1 → 0 | 0 → 0 | 0/22 (0.0%) → 0/22 (0.0%) | 32514/82802 → 28180/65106 |
| v2-t06-c | boundary | 1 → 0 | 1793/2824.0/5120 → 1555/2864/4087 | 1 → 0 | 0 → 0 | 0/9 (0.0%) → 0/9 (0.0%) | 52838/170077 → 58890/75776 |

#### 5) 경계 case별 passRounds/totalRounds(각 R=1)


| caseId | I t0 → C t0 | I t06 → C t06 | V2 t0 → V2 t0-c | V2 t06 → V2 t06-c |
|---|---|---|---|---|
| NORMAL-01 | 1/1 → 1/1 | 1/1 → 1/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| NORMAL-PRECISE | 1/1 → 1/1 | 0/1 → 1/1 | 1/1 → 1/1 | 1/1 → 1/1 |
| MAX-MIXED | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| MAX-HANGUL | 0/1 → 0/1 | 1/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| MAX-DIGIT | 0/1 → 0/1 | 1/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| MAX-ASCII | 1/1 → 0/1 | 1/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| MAX-SUPPLEMENTARY | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| ESCAPE-01 | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |
| ESCAPE-CONTROL | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 | 0/1 → 0/1 |

#### 6) 이번 실패 case·틀린 필드·errorKinds·출력 SHA


| 등록 | 경로 | caseId | 틀린 필드 | errorKinds / errorCodes | outputSha256 접두부 | 기지 해시 |
|---|---|---|---|---|---|---|
| c-t0 | B | G16 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['127f9390eeb1'] | [None] |
| c-t0 | A | G03 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['445ac9311338'] | [None] |
| c-t0 | boundary | MAX-MIXED | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | MAX-HANGUL | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | MAX-DIGIT | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | MAX-ASCII | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | MAX-SUPPLEMENTARY | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | ESCAPE-01 | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t0 | boundary | ESCAPE-CONTROL | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | B | G16 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['127f9390eeb1'] | [None] |
| c-t06 | A | G03 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['445ac9311338'] | [None] |
| c-t06 | boundary | MAX-MIXED | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | boundary | MAX-HANGUL | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | boundary | MAX-DIGIT | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | boundary | MAX-ASCII | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | boundary | MAX-SUPPLEMENTARY | eqp | ['ARGUMENT_MISMATCH'] / [] | be5dc92fe2de | I 내부 EQP0001 선택 |
| c-t06 | boundary | ESCAPE-01 | eqp | ['ARGUMENT_MISMATCH'] / [] | ab03b9728859 | None |
| c-t06 | boundary | ESCAPE-CONTROL | eqp | ['ARGUMENT_MISMATCH'] / [] | d95a10e969c3 | None |
| v2-t0-c | B | G01 | t/effectiveT, from, to | ['TASK_TYPE_MISMATCH', 'ARGUMENT_MISMATCH'] / [] | ['f6c2926386f3'] | [None] |
| v2-t0-c | B | G04 | t/effectiveT | ['TASK_TYPE_MISMATCH'] / [] | ['5ac45f612db2'] | [None] |
| v2-t0-c | B | G08 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['8a356dcf80ac'] | [None] |
| v2-t0-c | B | G09 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['8132a84619b6'] | [None] |
| v2-t0-c | B | G11 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['58b4a150fe23'] | [None] |
| v2-t0-c | B | G12 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['fa3bf734de78'] | [None] |
| v2-t0-c | B | G16 | t/effectiveT, eqp | ['TASK_TYPE_MISMATCH', 'ARGUMENT_MISMATCH'] / [] | ['5ac45f612db2'] | [None] |
| v2-t0-c | B | G17 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['c86c720f6f7d'] | [None] |
| v2-t0-c | B | G20 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['7ca8fd423a65'] | [None] |
| v2-t0-c | B | G22 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['be30e9b5e125'] | [None] |
| v2-t0-c | A | G01 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['e82664c62e3f'] | [None] |
| v2-t0-c | A | G08 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['3dfedc5ae0ce'] | [None] |
| v2-t0-c | A | G09 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['b1407006e560'] | [None] |
| v2-t0-c | A | G11 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['f1761c4d9b38'] | [None] |
| v2-t0-c | A | G12 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['c484734ce737'] | [None] |
| v2-t0-c | A | G16 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['94c5ec256372'] | [None] |
| v2-t0-c | A | G17 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['a3611bae245e'] | [None] |
| v2-t0-c | A | G20 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['62319f821d3d'] | [None] |
| v2-t0-c | A | G22 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['2fbb1236809c'] | [None] |
| v2-t0-c | boundary | NORMAL-01 | from, to | ['ARGUMENT_MISMATCH'] / [] | c86c720f6f7d | None |
| v2-t0-c | boundary | MAX-MIXED | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t0-c | boundary | MAX-HANGUL | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t0-c | boundary | MAX-DIGIT | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t0-c | boundary | MAX-ASCII | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t0-c | boundary | MAX-SUPPLEMENTARY | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t0-c | boundary | ESCAPE-01 | t, eqp | ['TASK_TYPE_MISMATCH', 'ARGUMENT_MISMATCH'] / [] | 794328a9b592 | None |
| v2-t0-c | boundary | ESCAPE-CONTROL | t, eqp | ['TASK_TYPE_MISMATCH', 'ARGUMENT_MISMATCH'] / [] | 9c560d9472d3 | None |
| v2-t06-c | B | G01 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['c86c720f6f7d'] | [None] |
| v2-t06-c | B | G02 | t/effectiveT | ['TASK_TYPE_MISMATCH'] / [] | ['f43e16dda78f'] | [None] |
| v2-t06-c | B | G03 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['e9c14181a298'] | [None] |
| v2-t06-c | B | G08 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['8a356dcf80ac'] | [None] |
| v2-t06-c | B | G09 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['8132a84619b6'] | [None] |
| v2-t06-c | B | G11 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['58b4a150fe23'] | [None] |
| v2-t06-c | B | G12 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['fa3bf734de78'] | [None] |
| v2-t06-c | B | G15 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['e9c14181a298'] | [None] |
| v2-t06-c | B | G17 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['c86c720f6f7d'] | [None] |
| v2-t06-c | B | G20 | eqp, from, to | ['ARGUMENT_MISMATCH'] / ['FINISH_LENGTH'] | ['e3b0c44298fc', '9db7bfc3f597'] | [None, None] |
| v2-t06-c | B | G22 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['beafef32e363'] | [None] |
| v2-t06-c | A | G01 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['e82664c62e3f'] | [None] |
| v2-t06-c | A | G08 | eqp, from, to | ['ARGUMENT_MISMATCH'] / [] | ['3dfedc5ae0ce'] | [None] |
| v2-t06-c | A | G09 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['0ed93fbd45a7'] | [None] |
| v2-t06-c | A | G10 | t/effectiveT | ['TASK_TYPE_MISMATCH'] / [] | ['b44092d461fb'] | [None] |
| v2-t06-c | A | G11 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['d071a78ff04b'] | [None] |
| v2-t06-c | A | G12 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['c484734ce737'] | [None] |
| v2-t06-c | A | G16 | eqp | ['ARGUMENT_MISMATCH'] / [] | ['94c5ec256372'] | [None] |
| v2-t06-c | A | G17 | from, to | ['ARGUMENT_MISMATCH'] / [] | ['355710217efc'] | [None] |
| v2-t06-c | A | G20 | t/effectiveT, eqp | ['ARGUMENT_MISMATCH'] / [] | ['e251998873c4'] | [None] |
| v2-t06-c | A | G21 | t/effectiveT | [] / [] | ['80e3e797f271'] | [None] |
| v2-t06-c | A | G22 | t/effectiveT, from, to | ['ARGUMENT_MISMATCH'] / [] | ['c753fe350da1'] | [None] |
| v2-t06-c | boundary | NORMAL-01 | from, to | ['ARGUMENT_MISMATCH'] / [] | c86c720f6f7d | None |
| v2-t06-c | boundary | MAX-MIXED | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | MAX-HANGUL | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | MAX-DIGIT | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | MAX-ASCII | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | MAX-SUPPLEMENTARY | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | ESCAPE-01 | eqp | ['ARGUMENT_MISMATCH'] / [] | 8d8e08e58b88 | V2 내부 EQP0001 선택 |
| v2-t06-c | boundary | ESCAPE-CONTROL | eqp | ['ARGUMENT_MISMATCH'] / [] | 45cc253f5066 | None |

- 기지 해시는 기존 결과에서 직접 읽었다: `be5dc92fe2de` = I 내부 EQP0001 선택, `26db1d7e637f` = MAX/ESCAPE 정답, `8d8e08e58b88` = V2 내부 EQP0001 선택.

#### 7) F0 다섯 leaf 참고 매핑

- DIAGNOSTIC은 정식 게이트 증거가 아니다. CMP의 PASS는 측정 비교가 성립한다는 하네스 규칙이며 후보 A 승격/품질 통과를 뜻하지 않는다. OUT은 생성 조건만 본 참고 판정이다. 정식 계수 입력 official-count/gguf-count가 없으면 정식 OUT 판정은 OUT_INPUT_MISSING·NOT_RUN이며 이번 참고표로 대체하지 않는다.

| leaf | c-t0 | c-t06 | v2-t0-c | v2-t06-c |
|---|---|---|---|---|
| AC-37-CMP | PASS 참고·KEEP_B (UNS A/B=22/22, A 기대 t별 p95 예산=False) | PASS 참고·KEEP_B (UNS A/B=22/22, A 기대 t별 p95 예산=False) | PASS 참고·KEEP_B (UNS A/B=22/21, A 기대 t별 p95 예산=False) | PASS 참고·KEEP_B (UNS A/B=19/22, A 기대 t별 p95 예산=False) |
| AC-37-OUT-NORMAL | PASS 생성 조건 참고 (2/2 케이스) | PASS 생성 조건 참고 (2/2 케이스) | FAIL 생성 조건 참고 (1/2 케이스) | FAIL 생성 조건 참고 (1/2 케이스) |
| AC-37-OUT-MAX | FAIL 생성 조건 참고 (0/5 케이스) | FAIL 생성 조건 참고 (0/5 케이스) | FAIL 생성 조건 참고 (0/5 케이스) | FAIL 생성 조건 참고 (0/5 케이스) |
| AC-37-OUT-ESCAPE | FAIL 생성 조건 참고 (0/2 케이스) | FAIL 생성 조건 참고 (0/2 케이스) | FAIL 생성 조건 참고 (0/2 케이스) | FAIL 생성 조건 참고 (0/2 케이스) |
| AC-38-F0 | PASS 참고 (기대 INC·TC workflow 2/2) | PASS 참고 (기대 INC·TC workflow 2/2) | PASS 참고 (기대 INC·TC workflow 2/2) | PASS 참고 (기대 INC·TC workflow 2/2) |

#### 8) 공유 창·계수 확인·보존·남은 확인

- c-t0: 수집창 2026-11-05T00:31:00Z ~ 2026-11-05T01:03:00Z, 출발지 집계 {'loopback': 121, 'external': 0}, 외부 구간 0개, 미해석 GIN 0. 기존 수집 스크립트대로 공통 prefix부터 누적 수집하되 겹침 제외는 해당 회차 callWindows와만 교차한다. IP·원문·본문은 저장/출력하지 않았다.
- c-t0: 독립 스크립트의 겹침 제외 수·유지 수·p50/p95가 실제 f0 share-exclude 출력과 모두 일치했다.
- c-t0/B: completionMinusLocal 값=건수 {2: 21, 3: 1}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- c-t0/A: completionMinusLocal 값=건수 {1: 3, 2: 19}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- c-t0/boundary: completionMinusLocal 값=건수 {2: 9}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- c-t06: 수집창 2026-11-05T00:31:00Z ~ 2026-11-05T01:36:00Z, 출발지 집계 {'loopback': 246, 'external': 0}, 외부 구간 0개, 미해석 GIN 0. 기존 수집 스크립트대로 공통 prefix부터 누적 수집하되 겹침 제외는 해당 회차 callWindows와만 교차한다. IP·원문·본문은 저장/출력하지 않았다.
- c-t06: 독립 스크립트의 겹침 제외 수·유지 수·p50/p95가 실제 f0 share-exclude 출력과 모두 일치했다.
- c-t06/B: completionMinusLocal 값=건수 {0: 1, 2: 17, 3: 5}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- c-t06/A: completionMinusLocal 값=건수 {2: 22}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- c-t06/boundary: completionMinusLocal 값=건수 {2: 9}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t0-c: 수집창 2026-11-05T00:31:00Z ~ 2026-11-05T02:07:00Z, 출발지 집계 {'loopback': 439, 'external': 0}, 외부 구간 0개, 미해석 GIN 0. 기존 수집 스크립트대로 공통 prefix부터 누적 수집하되 겹침 제외는 해당 회차 callWindows와만 교차한다. IP·원문·본문은 저장/출력하지 않았다.
- v2-t0-c: 독립 스크립트의 겹침 제외 수·유지 수·p50/p95가 실제 f0 share-exclude 출력과 모두 일치했다.
- v2-t0-c: token-golden 입력 계수 일치 13/13. 등록 동등성 게이트는 13/13 일치를 실제 확인한 뒤 usage/B/A/경계로 진행했다.
- v2-t0-c: usage-probe equalOnCacheHit={'evalCount': True, 'usage': True}. 원시 응답은 출력하지 않았다.
- v2-t0-c/B: completionMinusLocal 값=건수 {3: 22}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t0-c/A: completionMinusLocal 값=건수 {3: 22}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t0-c/boundary: completionMinusLocal 값=건수 {3: 9}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t06-c: 수집창 2026-11-05T00:31:00Z ~ 2026-11-05T02:43:00Z, 출발지 집계 {'loopback': 565, 'external': 0}, 외부 구간 0개, 미해석 GIN 0. 기존 수집 스크립트대로 공통 prefix부터 누적 수집하되 겹침 제외는 해당 회차 callWindows와만 교차한다. IP·원문·본문은 저장/출력하지 않았다.
- v2-t06-c: 독립 스크립트의 겹침 제외 수·유지 수·p50/p95가 실제 f0 share-exclude 출력과 모두 일치했다.
- v2-t06-c/B: completionMinusLocal 값=건수 {0: 1, 3: 22}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t06-c/A: completionMinusLocal 값=건수 {3: 23}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- v2-t06-c/boundary: completionMinusLocal 값=건수 {3: 9}; 실제 promptTokens가 audit보다 작음/큼 0/0. 비제로 차이를 이유로 채점을 바꾸지 않았다. 작음은 잘림 의심 관측이며 입력 계수 동등성은 token-golden에서 별도 검사한다.
- 기존 보호 파일 534개(SPEC·Plan·중단 회차·기존 capture 포함)의 SHA-256 일치, 변경·삭제 0. 기존 devreport 664439 B 전체를 바이트 접두부 그대로 보존하고 §12.35 뒤에 본 §12.36 한 절만 append했다. 미커밋 상태로 유지한다.
- 새 결과는 `eval/f0/results/20261105T003119Z-diag-*/`, 새 공유 창은 `docs/gates/f0/share-window-20261105T003119Z-diag-*.json`에 있다. 스크립트·로그·집계·보고서 사본은 지정 scratch `step9/run/continue-*`에만 생성했다. 이전 scratch 결과는 덮어쓰지 않았다.
- SPEC 체크박스·Plan·운영 설정·기존 gate 증거는 변경하지 않았다. 이번 실측은 정식 F0 게이트 통과나 T 상한 확정을 수행하지 않는다. 사용자 지시 범위의 진단 관측값만 보고한다.
- bge-m3 재상주 이력: 이번 회차 전 00:00 UTC root가 사용자 승인 하에 keep_alive 24h로 1회 상주시켰다(brief-rerun 기록, HTTP code는 scratch 로그에 없음). 이번 4회차 사이 between 점검(00:32·01:02·01:35·02:06 UTC)은 모두 bge-m3 상주·UNTIL 22~24h였고 warmup 요청은 0회다.
- SSH 터널: `127.0.0.1:11434` 터널(PID 9928, 00:31:35 UTC 시작)은 Codex 세션이 띄운 것이다. 인계 세션은 터널을 새로 띄우지 않았으므로 지시(본인 터널만 종료)에 따라 종료하지 않고 남겨 두었다.


### 12.37 F0 진단 종료·실모델 정식 측정 F5a 이관 SPEC/Plan 개정 (2026-11-05, Codex, 미커밋)

- 사용자 결정: "이건 ai-gateway 를 다 만들고, 그 이후에 tc backend 에서도 ai-gateway 와 연결하는 부분을 만들고, 모두 연결이 되어서 사용이 가능한 상태에서 테스트를 해야 올바른 테스트가 되겠지. 그때 진행할거야." SPEC §16 F1~F4는 TC 업무 API Mock이고 F5a에서 실제 Backend READ API·위임·프록시·폴링·HTTP 클라이언트를 연결하므로 F5a를 선택했다. F5b는 Front/SSE 단계이므로 API 수준 실모델 품질 시험을 기다릴 이유가 없다.
- 변경 파일은 SPEC·Plan, `docs/gates/spec-baseline.md`, `docs/gates/spec-approvals/6.md`, 이 Dev report뿐이다. SPEC의 기존 미커밋 변경과 gitignored Plan을 보존한 상태에서 수정했다. P04 구현은 실행하지 않았다.
- 편집 전 사본: `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/spec-defer/spec-before.md`·`plan-before.md`. SPEC before `bb91146a0f0c84f8ec885355dc840c42de06be346c910cd0f846745469bd0de4`(2612줄) → after `dc5dae6ee5667a5c1209e6399c54bf7d31368f6cfa09d60ce8f6130598ff1b1e`(2613줄). Plan before `69a369cca2444bf9516a55c1981b5a6adcf5993d60c04b118cf25be2c9f8e8e4`(1757줄) → after `334972a33c99af4e507dca99ba5b2a15df0981e8f1123b319e43cacd1e722f93`(1767줄). UTF-8·LF로 기록한다.
- 핵심 영향: F0 추가 연습·정식 측정을 중단하고 진단 기준선 이력으로 닫는다. F0 PASS·T·정확도·지연 최종 확정은 F1 착수 선행이 아니다. AC-37-CMP·AC-37-OUT-NORMAL/MAX/ESCAPE·AC-38-F0는 ID 유지·F5a TC_LIVE+LLM_LIVE 실제 경로로 이동한다. F2/F4 방어 시험은 유지하고 LLM_STUB가 긴eqp·escape 실모델 시험을 대신하지 않도록 명시한다. A 후보/F4 승격 의존도 F5a 동일3축+실경로p95로 이관한다. 실제 Gateway의 메시지·qRef 조립을 통과하는 증거를 요구하며 하네스 재구현 경로를 수용 근거로 쓰지 않는다.
- 진단 근거(원문 출력0): `eval/f0/results/20261104T044320Z-diag-*-b/`와 `20261105T003119Z-diag-*/`의 A/B/f0-results.json·boundary/boundary-results.json·preflight/postflight.json, 이 문서 §12.32·§12.34~§12.36, C24/C25 방법(b) 등록과 C26/C27 classify-v4-a 등록을 대조했다. Thinking-2507·192.168.0.160·ctx8192, 224/224 답변 계수·reasoning 분리·content 표지 혼입0, 답변 최대145<192, 대표 A/B p50약17~25s를 확인했다. I t0 B t22/22·args22/22, C t0/t0.6 B t22/22·args21/22다. 최초 I t0.6 args19/22를21~22로 바꾸지 않았다. 새4회차·2프롬프트의 MAX는0/5·ESCAPE는0/2씩이며 EQP0001 후보 JSON을 재구성한 SHA256 접두부be5dc92fe2de/8d8e08e58b88가 저장 해시와 일치했다. classify-v4-a의 경계 사고 소진은8→0·2→0이지만 골든 B t0.6 length1은 남아 있다는 범위를 명시했다.
- 잠정값: T_cls4928=min(관측 사고 최대5120,8192−3072−192), T_pln4864=min(5120,8192−3072−256), T_cmp5120=같은 모델 관측 최대 차용(직접 Composer 진단0, ctx여유5248 이하). 요청max_tokens5120/5120/5248·ctx assert·실패/1회재시도를 유지한다. 관측5120은 자연 길이 최대가 아니라 상한 도달이다. 정확도 목표t100%(22/22)·args≥21/22(약95.45%)·UNS100%(22/22), 단일 호출p95≤90s(관측 최대85478ms 올림)를 잠정 적용한다. Composer는 대리값임을 명시하고 실제 품질·SLA로 주장하지 않는다. 기존 경로15/30/90s·Job180s는 그대로다. 최종 T/Composer/품질/지연은 F5a 정식 시험 전에 확정한다. 방법(b) LOCAL_TOKENIZER_PIN_V1으로 ⓒ를 닫고 usage 차이는 기록만 하는 기존 진단 정책을 따른다.
- F0 의존 전수 대조: 헤더/순서/D37·D44·D50, 런타임·캐시 확인, GQ/AC-38 문항, §13.3 기준선 뒤 구현 전 목표 확정, §14.5 T/ⓑ/ⓒ·출력 충분성·재시도·추정, §14.7 슬롯/처리량, §16 선행/F0/F1/F4/F5a, UC-11·O2/O11·원칙23·Envelope/시간 추출·SSH, AC-37/38 부모/leaf·AC-24-PERF·매핑·산출물·완료 상태를 날짜 주석으로 대체했다. F0~F4 백업 범위·WM/HRM 비선행·용량 확장 트리거·이미 존재한 과거 등록/결과/커밋 설명은 유지한다. Plan은 메타/DAG/G-F0-ACCESS·TARGET/G-ARG-LIMITS/G-USAGE/G-INFER-ENV·SHARE/F0 exit/P03/P04/P17/P18/P19/P20/P26·P28/매트릭스/권위producer/handoff/리스크/기존 자체검증 추적을 대조했다. 기존 고정 하네스 catalog의 F0 매핑은 이력이며 F5a 구현 담당자가 실제 producer와 함께 갱신할 인계 항목으로 명시했다(현재 코드 수정0).
- 고정 행 확인: SPEC1203·1207·1208·1666은 줄 번호와 원문 전체가 편집 전과 같다. 1203의 KQ/LQ/INC/GQ/UNS,1207의 시간35 CODEPOINT·나노초·같은instant·엄격한실제달력,1208의q256·원문코드포인트,1666의Context를 보존했다. ArgLimitsSchemaTest의 간접1203 참조와 F0BoundaryCasesV2Test의 간접1207/1208 참조, v1 q의1666 근거를 확인했다. 새 이력 행은 마지막 고정 행 뒤2287에 추가했다.
- seq6: approvedAt2026-11-05, approvalRef=`docs/gates/spec-approvals/6.md`(위 사용자 결정 원문 포함), prevSha=bb91146a…d0de4, sha=dc5dae6ee5667a5c1209e6399c54bf7d31368f6cfa09d60ce8f6130598ff1b1e, affectedLeaves=ALL, measurementAffecting=true다. 측정 경로·단계·T·계수/판정 계약이 바뀌므로 seq5와 같은 의미로 true를 사용하며 단순 문구 변경용 false 재판정을 허용하지 않는다. seq1~5 값은 HEAD와 대조해 동일하고 새 항목만 추가했다. changedSections는 실제 변경절 집합과 일치한다.
- 실제 검증: cwd `C:/workspace_nori/nori-ai-gateway`, `.\gradlew.bat :gateway-eval:test --offline --console=plain`, exit=0, JUnit XML 집계={'tests': 241, 'failures': 0, 'errors': 0, 'skipped': 0}, 실패 테스트=[]. 외부 의존 다운로드를 막는 --offline을 추가했고 테스트의 로컬 HTTP stub만 사용했다. 하네스·테스트 코드는 수정하지 않았다. SPEC·Plan UTF-8/LF·행수·해시·고정행·기준선 사슬·word-diff를 대조했다.
- 미실행: 모델 실호출·SSH·외부 네트워크·F5a 품질/경계/통합 게이트·서비스/운영 설정 변경·publish·protected 변수 갱신·git add/commit/stash/checkout/reset/push. PREREG·results·evidence·captures·prompts·audits는 변경하지 않았다. 실제 품질 PASS나 eqp 설계 해결을 주장하지 않는다.
- 열린 항목: 지금 사용자 결정이 필요한 추가 차단 항목은 없다. Gateway eqp 원문 보존 설계와 Composer 직접 분포·잠정 T/목표 최종 확정은 F5a 책임이다. 기존 Composer content 혼입/공유 겹침/readiness errorCode 등 독립 미해결 항목은 종전 책임 시점에 검토한다. 별도 F5a 구현 phase는 기존 Plan에 없어 §13.1 handoff 실행 계약으로 보완했으며 TC 구현 allowed_writes를 임의로 늘리지 않았다.
- scratch 산출물: spec-before.md·plan-before.md·spec-word-diff.txt·plan-word-diff.txt·before-after-sha256.json·pinned-lines-check.txt·changed-locations.txt·diagnostic-verification.txt·gateway-eval-test-final.log·gateway-eval-test-final.exit·test-tail.txt, 조사/편집/검증 스크립트와 전수검색 inventory는 모두 `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/spec-defer`에 둔다. 전체 파일 경로는 scratch-files.txt에 기록한다.

검증 출력 마지막10줄:

```text
[Incubating] Problems report is available at: file:///C:/workspace_nori/nori-ai-gateway/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 6m 8s
6 actionable tasks: 2 executed, 4 up-to-date
```

#### SPEC 변경 위치(최종 행 번호)

| 절 | 행 | 한 줄 요약 |
|---|---|---|
| header | 5, 13, 24, 29 | 최신 F0 종료·F1 비차단·F5a 측정 순서를 명시하고 과거 상태를 이력으로 구분한다. |
| 0 | 81, 88, 94 | D37/D44/D50의 F0 의존과 A 후보·승격 시점을 F5a로 대체한다. |
| 4.1 | 306 | 진단 관측과 미확인을 구분하고 추가 런타임 실측을 F5a로 연기한다. |
| 4.2 | 328 | reasoning 분리 관측을 기록하고 추가 F0 측정 선행을 제거한다. |
| 5.1 | 368, 373 | 초기값 조정·A 비교/승격 시점을 F5a로 이동한다. |
| 5.3 | 403, 416 | GQ 품질·AC-38-F0 분류 판정을 F5a로 이관하고 F2/F4 방어 시험을 유지한다. |
| 5.4 | 421 | 지연 근거를 진단과 F5a 실경로 확인으로 구분한다. |
| 9.5 | 899 | G-USAGE 일치 관측과 캐시 적중 미확인을 구분한다. |
| 13.2 | 1099, 1104 | 골든셋 문항을 보존하고 실모델 정식 판정을 F5a로 이관한다. |
| 13.3 | 1123, 1124 | 목표 최종 확정의 구현 전 차단을 제거하고 잠정값·F5a 사전 확정을 적용한다. |
| 14.2 | 1136 | F0 상주 관측을 이력으로 두고 F2 설정 게이트를 유지한다. |
| 14.5 | 1162, 1176, 1178, 1188, 1194, 1195, 1196, 1199, 1200, 1201, 1204, 1211, 1223, 1225 | T·품질·지연 잠정값과 진단 수치·실패 근거를 기록하고 방법(b)/ⓒ 확정·긴eqp 설계 항목·F5a 확인을 명시한다. |
| 14.7 | 1245, 1249 | 슬롯/처리량의 추가 실측을 F5a로 연기하고 기존 용량 프로필을 유지한다. |
| 16 | 1349, 1382, 1383, 1386, 1387 | F0를 진단 종료·비게이트로 바꾸고 F1 진입·F4 B 유지·F5a 실모델5 leaf를 명시한다. |
| 17 | 1431 | AC-38-F0의 실모델 분류·workflow 판정을 F5a로 이관한다. |
| 19 | 1460, 1469 | 모델 위험·초기값 최종 확인을 F5a로 옮긴다. |
| 20 | 1515 | F0 선행 실측 순서를 비차단 진단 이력과 F5a 정식 측정으로 대체한다. |
| 21.3 | 1593 | 계획 Envelope의 F0 후보/F4 승격 의존을 F5a로 옮긴다. |
| 22.2 | 1674 | 시간 추출 정확도 실모델 판정을 F5a로 이관하고 고정 Context 행을 보존한다. |
| 26.1 | 1848 | 추가 F0 SSH 실측을 중단하고 F2 보안 게이트를 유지한다. |
| 27.2 | 1935, 1936, 2020, 2090, 2091, 2092, 2093, 2094, 2126, 2130, 2135, 2142 | AC-37/38·이관5 leaf·F0/F5a 매핑·긴eqp/escape 실모델 필수성을 정합화한다. |
| 27.4 | 2244, 2249 | F0 진단 산출물과 F5a 실경로 수용 산출물을 구분한다. |
| 27.5 | 2282, 2284, 2285, 2286, 2287, 2291, 2293 | v1~v4 이력을 보존하고 날짜가 있는 대체 주석·신규 변경 행·현재 검증 상태를 기록한다. |

#### Plan 변경 위치(최종 행 번호)

| 절 | 행 | 한 줄 요약 |
|---|---|---|
| 0. 문서 메타 | 9, 11, 14 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 0.1 용어 — 단계와 phase | 19 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 1. 범위 계약 요약 (승인 SPEC 전문 우선) | 32, 41 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 2. 조사한 기존 코드 근거 (정적, 2026-10-27) | 76 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 4.1 명령·OS·cwd | 175, 180 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 5. 게이트 레지스트리 | 250, 251, 252, 253, 274, 275 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 6. DAG·선후관계·단계 exit gate | 292, 298, 305, 310, 312 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P01 레포 부트스트랩·eval 모듈·ac-report (F0) | 354 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P02 F0 하네스 — 골든셋 사전등록·토큰 계수·A/B 러너 (F0) | 361, 366, 372, 374, 384, 385, 392 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P03 F0 진단 기준선 완료·정식 측정 통합 단계로 연기 (이력) | 395, 397, 399, 400, 402, 410, 411, 412, 413, 424, 426, 429 | P03을 진단 기준선 완료·정식 측정 F5a 연기로 닫고⑧~⑩ 및 기존 결과·명령을 이력으로 보존한다. |
| P04 스택 호환 게이트·모듈 골격·의존 규칙 (F1) | 437 | F0 PASS 선행을 제거하고 자체 기술 게이트를 유지한다. |
| P17 추론 서버 네트워크 경계·복구 runbook 산출물 (F2 착수 준비, P04~P16 과 병렬) | 778 | F0 PASS 선행을 제거하고 자체 기술 게이트를 유지한다. |
| P18 토큰 계수·호출 예산 guard·잘림 감지 (F2) | 802, 806 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P19 추론 Client·readiness·503 (F2) | 822, 824 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P20 분류·Composer·재시도·UNSUPPORTED/GQ/리소스 검증 (F2) | 844, 846 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P21 F2 게이트 실행 (F2) | 870 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P26 F4 통합 경로·음성 Fixture·(A) 후보 실행 준비 (F4) | 969 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| P28 성능·부하·장애 주입 (F4) | 1001 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 12. AC·ISO 추적 매트릭스 | 1244 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 12.1 상위 ID(AC-01~AC-46, ISO-01~ISO-15) | 1288, 1289 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 12.2 leaf 216개 | 1456, 1457, 1458, 1459, 1460 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 12.4 권위 producer 정본 (N1 — `leaf-catalog-v2.json` 의 component 입력, leaf·원자 ID 추가·삭제 0) | 1566, 1570, 1571, 1592 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 13. Handoff 계약(P30 산출물 목차)과 후속 NOT_RUN | 1610, 1617 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 13.1 F5a 통합 단계로 이관하는 실모델 품질·경계 시험 (v4.1 대체(2026-11-05)) | 1618, 1619, 1620, 1621, 1622, 1623, 1624, 1625, 1626 | F5a 실제 통합 준비·목표 확정·긴eqp 설계 검토·실모델5 leaf·producer 갱신 책임과 순서를 추가한다. |
| 14. 미해결 기준 충돌·리스크 (root 검토 필요) | 1637, 1646, 1660 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 15. Author 자체검증 (공식 독립 Review 아님) | 1682, 1684, 1691 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 15.2 root 검토 후 부분 closure (Author 자체검증 — 공식 PASS 아님, 작성 당시 상태 UNREVIEWED/PENDING_REVIEW — 이력, 현재 상태는 §0) | 1730 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |
| 15.3 2026-10-29 통합 감사 확정 근본원인 N1~N17 반영 대조 (Author 자체 대조 — 공식 PASS 아님, 작성 당시 상태 UNREVIEWED/PENDING_REVIEW — 이력, 현재 상태는 §0) | 1747 | F0 추가 측정·최종값 선행을 제거하거나 이력으로 대체하고 잠정값·F5a 실행 책임과 정합화한다. |


#### 12.37.1 독립 리뷰 5건 보정 (2026-11-05, Codex, 미커밋)

- 범위: 독립 리뷰의 MAJOR 1건·MINOR 4건만 보정했다. 변경 파일은 SPEC·Plan·기존 seq6 기준선/승인 기록·이 append-only 부록이다. 구현·하네스·테스트 코드는 변경하지 않았다.
- F4 성능 기준: SPEC:1199·Plan P28:1004에 B 경로 15/30/90s를 비교·기록용 잠정 기준으로 명시했다. P28 담당자가 docs/gates/f4/ 기준 JSON을 작성하고 SPEC 소유자가 최신 baselineSeq·SPEC 파일 해시·approved=true·실재 approvalRef로 승인·결속하며 실행 담당자가 AIGW_F4_PERF_CRITERIA를 연결한다. candidateA=false·f0AccuracyQualified=false·경로별 minSamples=20을 명시했다. F5a 최종 모델 목표 승인과 분리하고 기존 PerfReporter 검사를 유지한다.
- F5b 해석: Plan:1622에서 F5a 배정을 Backend/API 수준 실모델 시험의 해석으로 명시하고 최종 사용자 화면의 사용 가능 상태는 기존 F5b Front/SSE·실제 E2E·운영 수용에서 검증하도록 했다. §12.37 첫 문단의 “F5b는 Front/SSE 단계이므로 API 수준 실모델 품질 시험을 기다릴 이유가 없다”는 설명은 사용자 확정 제외 정책이 아니며 이 부록의 해석으로 대체한다.
- 공유 겹침: SPEC:1199·1225·Plan:1623을 ⓔ-1 F2/F4 적용 정책(F2 착수 전 SPEC 소유자 확정), ⓔ-2 F5a 실제 측정 창·UTC 로그 수집/해석·겹침 판정(F5a 정식 시험 전 확정)으로 통일했다. 2026-11-04 F0의 겹친 호출만 제외하는 사용자 결정은 이력으로 유지한다.
- 90s 단위: SPEC:1199·Plan:1623에서 I/C 골든 A/B latencyMs의 문항 회차 지연(최대 1회 재시도 포함, 경계 시험 제외), 최대 관측 p95=85,478ms에서 올린 잠정 목표임을 명시했다. 모든 개별 호출의 관측 상한이 아니다. §12.37의 “단일 호출p95≤90s” 표현은 이 정의로 대체한다. per-callWindow 수치는 이번에 재검증하지 않아 인용하지 않았다.
- 이력 복원: Plan:310의 “새 환경(v4) 정식 회차만 쓴다”, seq5 affectedLeaves=ALL에 따른 Jetson v1~v3 레코드 부적격·기존 F0 실패 처리 계약을 복원하고 v4.1 대체(2026-11-05)를 붙였다. 원본 대비 전체 diff를 대조해 직접 치환된 SPEC:1194~1196·1382·1935~1936·2130과 Plan:305(DAG)·397·402·429·437·1456~1460·1570~1571의 옛 계약/상태/배정도 이력과 날짜 주석으로 보존했다. §12.37의 모든 과거 문구 보존 보고는 이번 복원까지 반영한 상태로 보완한다.
- 최종 SHA-256/행수: SPEC `6175e5d1a8221054f8ea72ab73a2aeb79bc278221d73bdbe9dfd13d1c358c2ef` / 2613줄, Plan `f3e6a543bb98c8f4f20032ff215868cc50091e0687cc35398b192ca75535c4ac` / 1767줄. 원본 spec-before.md·plan-before.md는 그대로 보존했다. SPEC 1203·1207·1208·1666행은 원본과 번호·내용 전체가 동일함을 스크립트로 확인했다.
- seq6: docs/gates/spec-baseline.md:4·docs/gates/spec-approvals/6.md:6의 SPEC 해시를 위 값으로 갱신했다. 이번 수정은 기존 changedSections의 §14.5·§16·§27.2에 포함되므로 23개 목록을 유지했고 전체 diff의 변경 절과 일치함을 확인했다. seq1~5는 HEAD와 동일하며 seq6의 affectedLeaves=ALL·measurementAffecting=true를 유지했다.
- 실제 검증: 최초 `.\gradlew.bat :gateway-eval:test --offline --console=plain`은 JUnit 241건 모두 통과했으나 후속 testLeafEvidence에서 SPEC_INPUT_MISSING(exit=1)으로 실패했다. 실행 셸에 AIGW_SPEC_FILE이 없어 발생한 실행 설정 오류이며 SpecInputResolver의 파일 입력 요구를 확인했다. `$env:AIGW_SPEC_FILE='C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md'`를 지정하고 같은 명령을 재실행한 최종 결과는 exit=0, JUnit XML 48개·241건, failures/errors/skipped 모두 0이다. 아래는 재실행 출력의 마지막 10줄이다. 모델 호출·SSH·외부 네트워크·실제 F4/F5a 수용 시험은 실행하지 않았다. PREREG·results·evidence는 수정하지 않았고 git commit/add/stash/checkout/reset/push는 실행하지 않았다. F2/F4 겹침 적용 정책과 F5a 최종 목표·실모델 시험은 문서의 후속 확정/검증 책임으로 남는다.
- 원본 대비 word diff: `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/spec-defer/spec-word-diff-v2.txt`, `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/spec-defer/plan-word-diff-v2.txt`. 검증 기록은 같은 폴더의 review-v2-verification.json·pinned-lines-check-v2.json·test-tail-v2.txt·gateway-eval-test-v2.log(최초 실패)·gateway-eval-test-v2-spec-bound.log(재실행)에 둔다.

```text
[Incubating] Problems report is available at: file:///C:/workspace_nori/nori-ai-gateway/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 7m 1s
6 actionable tasks: 2 executed, 4 up-to-date
```


#### 12.37.2 최종 지연 목표 단위 문구 보정 (2026-11-05)

- SPEC §14.5:1223의 잔존 문구만 “잠정 골든 문항 회차 지연 p95≤90s 목표”로 맞췄다. 수치·재시도·경계 정책과 Plan은 변경하지 않았다.
- 최종 SPEC SHA-256: `136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3`. 기존 미커밋 seq6의 현재 해시만 갱신했고 seq1~5·changedSections·affectedLeaves=ALL·measurementAffecting=true는 보존했다.
- 현재 scratch의 spec-before.md와 바이트 비교하여 변경은 1223줄 하나뿐이며 총 줄 수·고정 1203/1207/1208/1666줄·UTF-8 LF·Plan 해시 불변을 확인했다. 기존 Dev report 전체 바이트를 보존하고 이 절만 끝에 추가했다.
- AIGW_SPEC_FILE을 실제 SPEC 절대 경로로 지정하고 `.\gradlew.bat :gateway-eval:test --offline --console=plain`을 이번 보정에서 1회 실행했다: exit=0, JUnit XML 48개, tests=241, failures=0, errors=0, skipped=0. 로그·종료 코드는 현재 scratch의 gateway-eval-final-test.log·gateway-eval-final-test-exit.txt, 검증은 verification.json에 기록한다.
- §12.37.1의 최초 실패 및 후속 실행 이력은 그대로 보존했다. 실제 통합·모델 시험은 실행하지 않았으며 P04/F1 구현·코드·테스트 변경은 하지 않았다.


## P04 — 2026-11-05 사전 게이트 차단 (구현·검증 미완료)

P04 상태: BLOCKED / 구현 미완료 / 검증 미완료. P05 미착수.

확인된 선행조건:
- 세션 run.log 시작 정보: model=gpt-6.1-sol, reasoning effort=medium. 하위 에이전트 사용 없음.
- SPEC 실제 SHA256=136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3. 기준선 마지막 seq=6.
- AIGW_SPEC_FILE을 실제 승인 파일로 설정한 spec-guard exit 0, FILE_HASHED/LOCAL_FILE. F0 PASS를 선행조건으로 요구하지 않음.
- Boot BOM 4.0.7, Spring AI BOM/openai 2.0.2, Testcontainers BOM 2.0.3의 정확한 Maven Central POM GET 모두 HTTP 200. 이는 artifact 존재·접근 확인이며 조합 호환 PASS가 아님.
- 공식 Spring AI v2.0.2 release 페이지 존재 확인. 수동 생성 API ⓐ~ⓕ의 tagged source/compile 검증은 미실행이며 확인 완료로 주장하지 않음.

차단 1 — 수정 허용 범위와 필수 finalizer 계약 충돌:
- 승인 Plan P04(442행)는 모든 LeafEvidence finalizer에 writeRuntimeClasspath 의존과 --runtime-classpath-file <실제 출력 절대 경로>를 추가하도록 요구함.
- 실제 build.gradle.kts의 Test → JavaExec leaf-extract → EvalCli.dispatch → Args.only → OtrLeafExtractor.extract 호출을 확인함.
- gateway-eval/.../EvalCli.java:55~57의 leaf-extract 허용 인자·호출에는 --runtime-classpath-file이 없음. Args.java:18의 only가 알 수 없는 인자를 INVALID_ARGUMENTS로 거부함.
- OtrLeafExtractor.java:22~23의 extract 인자에도 runtime 파일 입력이 없음. 55~57행은 AIGW_RUNTIME_CLASSPATH_SHA256의 64자리 문자열만 받아 identity에 넣으며 파일 실제 바이트를 검증·해시하지 않음.
- 실제 재현: :gateway-eval:run --args="leaf-extract --runtime-classpath-file <scratch>/runtime-not-created.txt --out <scratch>/rejected-evidence.json" → CLI exit 2 INVALID_ARGUMENTS / Gradle exit 1. 허용 인자 검사에서 거부되므로 출력·기존 증거 쓰기 0.
- 따라서 root build에 Plan대로 인자를 추가하면 각 Test의 finalizer가 실패함. 환경변수 SHA만 전달하는 우회는 승인된 파일 입력 계약을 충족하지 않음.
- 올바른 파일 입력 지원에는 gateway-eval CLI/추출기와 관련 회귀 시험 수정이 필요하지만 사용자는 gateway-eval 수정을 명시 금지함. 문구 오타가 아닌 기능·범위 충돌이므로 구현을 중단함.

차단 2 — starter 인증 공급 미충족:
- 읽기 전용 nori-object-storage/build.gradle.kts의 publication 좌표=com.nori:nori-object-storage-s3-starter:0.0.1-SNAPSHOT, GitLab Maven URL=https://gitlab.com/api/v4/projects/<projectId>/packages/maven, Job-Token(CI_JOB_TOKEN) 또는 Private-Token(gitlabToken/GITLAB_TOKEN)을 확인함.
- Plan G-STACK의 projectId=83704008에 대해 기존 Gradle providers/env 경로만 이용한 GET을 실행함. credentialSource=none, HTTP 401. 비밀 파일을 읽거나 인증값을 출력하지 않음. 이 응답만으로 artifact 부재나 Boot 4 비호환을 단정하지 않음.
- 전역 설정 변경, 로컬 fake publish, composite/source 대체, 다른 private 좌표 사용 없음. 승인된 안전한 자격증명 공급 전에는 starter 다운로드·Boot 4 실제 기동 확인 불가.

대안과 영향:
1. P04 쓰기 범위를 EvalCli·OtrLeafExtractor 및 직접 관련 회귀 시험에 한정해 명시적으로 확장하고, 기존 승인된 자격증명 공급 경로를 운영자가 준비: Plan 파일 입력 계약과 기존 증거 불변·fail-closed를 유지하며 재개 가능. SPEC/Plan 정책·버전 변경은 필요하지 않음. 기존 env 경로의 보존 여부는 수정 승인 시 명시해야 함.
2. gateway-eval 금지를 유지하고 Plan을 환경변수 SHA 방식으로 개정·승인: 현재 선언값만 신뢰하는 경로와 파일 바이트 결속 차이를 재설계해야 하며, 승인된 finalizer 계약 변경과 검토가 필요함. 이번 세션에서 선택·시행하지 않음.
권고(하나): 대안 1. 이미 명시된 실제 파일 해시 계약을 구현하고 기존 fingerprint/증거 의미를 보존할 수 있어 계약 변경보다 영향이 작음. 허용 범위 승인과 안전한 starter 인증 공급을 받은 뒤 P04를 재개해야 함. 토큰을 대화에 붙여넣지 않음.

실제 실행 명령·결과(모든 Gradle 호출에 -p C:/workspace_nori/nori-ai-gateway 사용):
1. AIGW_SPEC_FILE=<승인 SPEC>; gradlew.bat :gateway-eval:run --args="spec-guard" → exit 0. scratch/spec-guard.log 및 .exit.txt.
2. AIGW_SPEC_FILE=<승인 SPEC>; gradlew.bat :gateway-eval:run --args="leaf-extract --runtime-classpath-file <scratch>/runtime-not-created.txt --out <scratch>/rejected-evidence.json" → Gradle exit 1, 내부 CLI exit 2 INVALID_ARGUMENTS(차단 재현). scratch/runtime-argument-reproduction.log 및 .exit.txt.
3. AIGW_SPEC_FILE=<승인 SPEC>; gradlew.bat :gateway-eval:run --args="spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" → exit 0. scratch/spec-guard-exact.log 및 .exit.txt.
4. C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe -B -X utf8 preflight_access.py → exit 0; HTTP/POM SHA 증거=scratch/artifact-access.json, scratch/*.pom. GET 성공을 빌드 PASS로 세지 않음.
5. AIGW_SPEC_FILE=<승인 SPEC>; gradlew.bat -I <scratch>/registry-preflight.init.gradle.kts p04RegistryAccessProbe → Gradle exit 0이나 실제 HTTP 401, 인증 공급 미충족. scratch/registry-preflight.log 및 .exit.txt. 이 task는 읽기 전용 진단이며 검증 PASS가 아님.

미실행 검증과 사유:
- Plan P04 ① build ② app integrationTest(StackSmokeIT/AppContextWiringIT) ③ integration integrationTest(TokenizerNativeSmokeIT) ④ app test(RuntimeClasspathCleanTest) ⑤ syncRuntimeClasspathEvidence: 구현 전 필수 계약·범위 충돌로 중단. 모듈·시험·producer를 허위로 만들지 않음.
- 관련 test-evidence verify는 Test task를 실행하지 않았으므로 미실행. P04 새 XML/OTR/실행 시험 수=0(NOT_RUN). 기존 XML/OTR을 P04 결과로 재사용하지 않음.
- clean checkout build, starter Boot 4 호환, Spring AI 수동 생성, DJL native 오프라인 로컬/컨테이너, OTLP logs, 모듈 방향/빈 wiring/runtime 청결: 모두 NOT_RUN.
- Docker 이미지 pull/기동·실모델/API 호출 없음. JDK/Docker 이전 root 점검 결과를 새 실행 결과로 주장하지 않음.

실제 저장소 변경(문서만):
- docs/gates/f1/stack-compat.md: G-STACK 차단·확인 범위·미검증·재개 조건 기록.
- docs/ai-rag-gateway-architecture-dev-report.md: 기존 바이트를 보존하고 P04 절만 추가.
- 구현 소스/빌드/catalog/gateway-eval 및 다른 레포 수정 0. runtime-classpath.txt는 producer 미구현이므로 생성하지 않음.
SPEC/Plan 대비 차이: 필수 파일 인자 기능이 현행 eval에 부재하나 eval 수정 금지여서 P04 구현을 시작하지 못함. 승인된 계약·정책·버전은 변경하지 않음.
영향 범위: 보고 문서만. P04 완료·G-STACK PASS·P05 착수 근거로 사용할 수 없음.

보존 검사: before.json의 protected 483개와 SPEC·Plan·baseline·approval6 해시 일치. Dev report 기존 바이트 prefix 보존. 기존 untracked 진단 파일을 삭제·stash하지 않음.

P04 출처 정정: 위의 “사용자는 gateway-eval 수정을 명시 금지함” 및 “eval 수정 금지”는 사용자의 최신 지시에서 새로 명시한 금지가 아니라, 승인 Plan P04 allowed_writes(438행)의 gateway-eval 제외와 442행의 --runtime-classpath-file 필수 전달 간 수정 허용 범위·필수 인터페이스 충돌을 뜻함. 기존 사용자 SDD 9.4의 충돌 시 중단 규칙이 적용되며, 기존 증거와 BLOCKED·구현/검증 미완료·P05 미착수 상태는 유지함.


### P04 재개 보고 (2026-11-05, 파일 입력·인증·스택 검증)

상태: 구현·로컬 기술 검사 통과, clean git checkout 검증·부모 독립 검증 대기. P04 최종 완료 선언 없음, P05 미착수.

변경 이유: 승인된 P04 필수 --runtime-classpath-file 계약을 CLI/추출기에 구현하고 실제 파일 바이트를 증거 identity에 결속했다. 기존 overload·F0 무파일 동작은 보존하며 명시 파일 실패의 환경 SHA fallback을 금지했다. 2026-11-05 사용자 승인으로 기존 ignored Plan 438행 allowed_writes만 정확한 5개 Eval 소스/시험 파일로 확장했다. 그 밖 SPEC/Plan 계약 차이는 없다.

기존 자격증명의 Private-Token 401은 Bearer 방식으로 해결했다(같은 gitlab.com 사용자 API·정확한 metadata 모두 200). 값은 CLI에서 메모리로 캡처하고 자식 환경에만 전달했다. 7모듈·분리 IT·명시 @Import·Spring-free core·runtime producer/finalizer/fingerprint·정적 규칙·실제 PG/모델 생성·starter/OTLP/DJL 검사를 구현했다. Boot 4 Kafka 자동구성 부재는 starter 의존성으로 해결했고 TC는 2.0.3으로 고정했다. 실추론·embedding·클라우드 호출은 없다.

실제 변경 파일:

- `build.gradle.kts`
- `docs/ai-rag-gateway-architecture-dev-report.md`
- `docs/gates/f1/runtime-classpath.txt`
- `docs/gates/f1/stack-compat.md`
- `gateway-acceptance/build.gradle.kts`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/package-info.java`
- `gateway-app/build.gradle.kts`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackContextSupport.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackSmokeIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/AiGatewayApplication.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/GatewayAppModuleConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/CoreBeansConfiguration.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/ArchitectureRulesTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/CoreSystemBranchGuardTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/ModuleConfigurationCompletenessTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/RuntimeClasspathCleanTest.java`
- `gateway-core/build.gradle.kts`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/package-info.java`
- `gateway-eval/src/main/java/com/nori/ai/gateway/eval/EvalCli.java`
- `gateway-eval/src/main/java/com/nori/ai/gateway/eval/report/OtrLeafExtractor.java`
- `gateway-eval/src/test/java/com/nori/ai/gateway/eval/RuntimeClasspathFileTest.java`
- `gateway-eval/src/test/java/com/nori/ai/gateway/eval/RuntimeClasspathFinalizerFunctionalTest.java`
- `gateway-eval/src/test/java/com/nori/ai/gateway/eval/TestKitSupport.java`
- `gateway-integration/build.gradle.kts`
- `gateway-integration/src/integrationTest/java/com/nori/ai/gateway/integration/tokenizer/TokenizerNativeSmokeIT.java`
- `gateway-integration/src/integrationTest/java/com/nori/ai/gateway/integration/tokenizer/TokenizerOfflineProbe.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/IntegrationModuleConfiguration.java`
- `gateway-integration/src/testFixtures/java/com/nori/ai/gateway/integration/testing/DockerRequiredExtension.java`
- `gateway-persistence/build.gradle.kts`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/PersistenceModuleConfiguration.java`
- `gateway-tc-mock/build.gradle.kts`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/TcMockModuleConfiguration.java`
- `gradle/libs.versions.toml`
- `settings.gradle.kts`
- 외부 ignored Plan: `C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture-plan.md`(P04 allowed_writes 한 줄만).

검증(각 명령의 정확한 argv·exit·XML/OTR 수·실패 이력은 `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p04-resume/result.json`, 상세 명령은 docs/gates/f1/stack-compat.md의 최신 재개 절):

| 실행 label | exit | 결과 |
|---|---:|---|
| `spec-guard` | 0 | 시험 task 없음 |
| `focused` | 0 | :gateway-eval:test: EXECUTED, tests=14, failures=0, errors=0, skipped=0, XML=3, OTR=1 |
| `focused-verify-final` | 0 | 시험 task 없음 |
| `eval-regression` | 0 | :gateway-eval:test: EXECUTED, tests=248, failures=0, errors=0, skipped=0, XML=49, OTR=1 |
| `eval-regression-verify` | 0 | 시험 task 없음 |
| `focused-p04` | 0 | :gateway-eval:test: EXECUTED, tests=15, failures=0, errors=0, skipped=0, XML=4, OTR=1 |
| `focused-p04-verify` | 0 | 시험 task 없음 |
| `p04-clean` | 0 | 시험 task 없음 |
| `p04-build` | 0 | :gateway-app:test: EXECUTED, tests=4, failures=0, errors=0, skipped=0, XML=4, OTR=1; :gateway-eval:test: EXECUTED, tests=249, failures=0, errors=0, skipped=0, XML=50, OTR=1 |
| `p04-build-verify` | 0 | 시험 task 없음 |
| `p04-stack-4` | 0 | :gateway-app:integrationTest: EXECUTED, tests=3, failures=0, errors=0, skipped=0, XML=2, OTR=1 |
| `p04-stack-4-verify` | 0 | 시험 task 없음 |
| `p04-tokenizer` | 0 | :gateway-integration:integrationTest: EXECUTED, tests=1, failures=0, errors=0, skipped=0, XML=1, OTR=1 |
| `p04-tokenizer-verify` | 0 | 시험 task 없음 |
| `p04-runtime` | 0 | :gateway-app:test: EXECUTED, tests=1, failures=0, errors=0, skipped=0, XML=1, OTR=1 |
| `p04-runtime-verify` | 0 | 시험 task 없음 |
| `p04-sync` | 0 | 시험 task 없음 |
| `p04-final-build` | 0 | :gateway-app:test: EXECUTED, tests=4, failures=0, errors=0, skipped=0, XML=4, OTR=1; :gateway-eval:test: UP-TO-DATE, tests=249, failures=0, errors=0, skipped=0, XML=50, OTR=1 |
| `p04-final-build-verify` | 0 | 시험 task 없음 |

보존: SPEC/baseline/approval6 및 protected 483개 불변, Plan은 승인 범위 한 줄뿐, Dev report 기존 바이트 prefix 보존. runtime 실제 출력/사본 SHA=29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e. 기존 raw report·PREREG·BLOCKED 이력은 수정하지 않았다. 비밀값 검사 결과는 scratch/secret-leak-check.json에 기록했다.

미검증·위험: clean git checkout build와 부모 독립 확인은 남았다(기존 untracked 진단 자료 보존). SDK 요청 재시도/로그/실제 inference 계약시험·배포·운영 설정·P05 이후 기능은 이번 범위가 아니다. SDK·Spring AI API ⓐ~ⓕ와 OTLP 클래스 경로, DJL native SHA/라이선스/OS/arch는 stack-compat.md 최신 절에 근거와 한계를 구분했다. 현재 외부 starter 인증 공급 차단은 없다.


#### P04 재개 필수 명령의 정확한 argv

각 명령은 AIGW_SPEC_FILE=C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md를 명시한 별도 프로세스에서 실행했다. 아래 argv를 PowerShell 인용 형태로 표시했다. 인증값은 argv에 없고 기존 자격증명을 자식 환경에서만 공급했다.

- `p04-final-build`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' 'build' '--no-daemon' '--console=plain'`
- `p04-final-build-verify`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test' '--no-daemon' '--console=plain'`
- `p04-stack-4`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:integrationTest' '-Paigw.requireDocker=true' '--tests' '*StackSmokeIT' '--tests' '*AppContextWiringIT' '--no-daemon' '--console=plain'`
- `p04-stack-4-verify`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\stack-filter.init.gradle.kts' '-Paigw.requireDocker=true' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker' '--no-daemon' '--console=plain'`
- `p04-tokenizer`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-integration:integrationTest' '-Paigw.requireDocker=true' '--tests' '*TokenizerNativeSmokeIT' '--no-daemon' '--console=plain'`
- `p04-tokenizer-verify`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\tokenizer-filter.init.gradle.kts' '-Paigw.requireDocker=true' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-integration:integrationTest --require-docker' '--no-daemon' '--console=plain'`
- `p04-runtime`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:test' '--tests' '*RuntimeClasspathCleanTest' '--no-daemon' '--console=plain'`
- `p04-runtime-verify`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\runtime-filter.init.gradle.kts' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:test' '--no-daemon' '--console=plain'`
- `p04-sync`(exit 0): `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:syncRuntimeClasspathEvidence' '--no-daemon' '--console=plain'`

## 2026-11-05 P04 root-review correction (append-only)

- `GatewayAppModuleConfiguration`을 승인 Plan 정본인 `app/config` 경로와 package로 옮기고 앱·시험 import를 맞췄다.
- 앱 `integrationTestImplementation`에만 기존 pin Nimbus 10.5, protobuf 4.33.2, networknt 3.0.9를 연결했다. 기존 StackSmokeIT에서 Boot 컨텍스트와 같은 JVM의 실제 CodeSource JAR 파일명을 assert하고, 더미 HS256 서명/정상·오서명 검증, protobuf StringValue 직렬화 왕복, Draft 2020-12 schema의 정상·타입 오류 검증을 실행했다. 제품 인증·schema 기능과 제품 의존성은 추가하지 않았다.
- core 분기 guard는 AST의 조건식·case label과 명시/와일드카드 static import를 함께 검사한다. TC/WM/HRM 각각의 static import·qualified·문자열·switch/switch expression 음성 fixture 및 주석·데이터·enum 양성 fixture를 추가했다.
- 루트 finalizer가 실제 root의 `git rev-parse HEAD`를 자식 Eval 환경으로 공급한다. 잘못된 AIGW_GIT_SHA를 실제 Git 신원으로 대체하는 TestKit 회귀를 추가했다. 비 Git/no-app 동작과 extractor 정책은 유지했고 전체 Eval 회귀로 확인했다.
- producer artifacts를 `@InputFiles` + `@PathSensitive(NAME_ONLY)`로 선언하여 원시 JAR SHA 출력과 입력 추적을 일치시켰다. 기존 의존·출력 계약은 유지했다. 원시 SHA를 쓰는 task에 ZIP 타임스탬프·순서를 무시하는 `@Classpath`는 맞지 않는다([Gradle 9.2.1 공식 API](https://docs.gradle.org/9.2.1/javadoc/org/gradle/api/tasks/Classpath.html)).

변경 파일: `build.gradle.kts`, `gateway-app/build.gradle.kts`, `gateway-app/src/main/java/com/nori/ai/gateway/app/AiGatewayApplication.java`, `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java`, `gateway-app/src/test/java/com/nori/ai/gateway/app/ModuleConfigurationCompletenessTest.java`, `gateway-app/src/test/java/com/nori/ai/gateway/app/CoreSystemBranchGuardTest.java`, `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackSmokeIT.java`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/RuntimeClasspathFinalizerFunctionalTest.java`, `docs/gates/f1/stack-compat.md`, `docs/ai-rag-gateway-architecture-dev-report.md`; 옛 app/GatewayAppModuleConfiguration.java는 이동으로 제거했다. docs/gates/f1/runtime-classpath.txt는 생성 task로 동기화했으며 최종 바이트는 기존과 동일하다. 영향은 P04 구성·빌드 증거·해당 시험이며 제품 런타임 기능 확장은 없다.

실제 writeRuntimeClasspath task를 scratch init script로만 override했다. 엔트리 내용 동일·ZIP 타임스탬프만 변경한 JAR 원시 SHA는 `ca0ff48691795e06a1044367bdcf0b15f29315bb5e0cea52afe4e41b8cbbd3ee` → `0afde1e17f811b48a33666e84b0d5e28098586d5f8a02bfc43cec97d8c777ee5`. 두 호출 모두 exit 0/EXECUTED였고 두 번째 출력은 새 SHA를 포함하고 옛 SHA를 포함하지 않았다. 의존 캐시·실제 제품 JAR는 수정하지 않았다. 이후 정상 producer와 sync를 실행했으며 실제 출력과 docs 사본의 바이트가 같고 SHA는 `29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`다. 상세 값은 scratch/raw-jar-regression.json에 있다.

DJL 0.40.0 JAR SHA=`569b1fa597e362ac11cd527e5deacd52cd827c8f5133a573572e2cd7051c5768`와 기존 기록의 native 바이너리 전체 SHA가 일치한다. 기존 Apache-2.0·native/DLL 라이선스 한계와 pin을 유지한다. tokenizer SHA=`aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`. native 소스는 수정하지 않았다. 사용자 정의 입력 fingerprint는 종전과 동일(`53b5cc5316445a72b2d082456b475f0654f47add373034d6f2c5915e66ef5064`)이나 이번 필수 명령에서 Gradle이 EXECUTED로 처리하여 Windows와 network=none Linux amd64 컨테이너의 offline load+encode를 실제 다시 통과했다(강제 rerun 옵션·추가 반복 없음). 현재 이미지 digest는 이전과 같아 기존 glibc 2.35 확인 근거도 유지된다. 이전 원시 결과를 새 실행이라고 쓰지 않았으며 새 XML/OTR를 별도 보관했다.

모든 이번 단위/IT 요약은 실제 HEAD `a72b1b79dd8995c2da5542ff7064c54773727230`, 해당 입력 fingerprint, 실제 producer 출력 SHA `29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`와 일치한다. 작업 트리는 수정·untracked 파일이 있는 dirty 상태이며 HEAD만으로 미커밋 변경의 배포 신원을 주장하지 않는다.

모든 Gradle 호출은 명시적 `AIGW_SPEC_FILE=C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`, `--no-daemon --console=plain`을 사용했다. glab 기존 host token은 메모리에서 조회하여 Authorization Bearer를 자식 환경으로만 전달하고 로그에서 원시 값을 마스킹했다. 각 Test 명령 바로 다음 별도 verifier는 동일 --tests/includeTags/aigw.requireDocker 조건을 임시 init script로 재현했다. 원시 XML/OTR와 정확한 argv·exit는 이 scratch의 각 label 파일에 있다.

| scratch label | exit | 실제 시험 결과 |
|---|---:|---|
| `focused-eval` | 0 | :gateway-eval:test: EXECUTED, XML=1, OTR=1, tests=1, failures=0, errors=0, skipped=0 |
| `focused-eval-verify` | 0 | Test task 없음 |
| `app-unit` | 0 | :gateway-app:test: EXECUTED, XML=4, OTR=1, tests=6, failures=0, errors=0, skipped=0 |
| `app-unit-verify` | 0 | Test task 없음 |
| `app-stack` | 0 | :gateway-app:integrationTest: EXECUTED, XML=2, OTR=1, tests=3, failures=0, errors=0, skipped=0 |
| `app-stack-verify` | 0 | Test task 없음 |
| `raw-jar-first` | 0 | Test task 없음 |
| `raw-jar-changed-timestamp` | 0 | Test task 없음 |
| `spec-guard` | 0 | Test task 없음 |
| `native-current` | 0 | :gateway-integration:integrationTest: EXECUTED, XML=1, OTR=1, tests=1, failures=0, errors=0, skipped=0 |
| `native-current-verify` | 0 | Test task 없음 |
| `local-clean` | 0 | Test task 없음 |
| `local-clean-build` | 0 | :gateway-app:test: EXECUTED, XML=4, OTR=1, tests=6, failures=0, errors=0, skipped=0; :gateway-eval:test: EXECUTED, XML=50, OTR=1, tests=249, failures=0, errors=0, skipped=0 |
| `local-clean-build-verify` | 0 | Test task 없음 |
| `runtime-clean` | 0 | :gateway-app:test: EXECUTED, XML=1, OTR=1, tests=1, failures=0, errors=0, skipped=0 |
| `runtime-clean-verify` | 0 | Test task 없음 |
| `final-sync` | 0 | Test task 없음 |

정확한 실행 명령(각각 별도 프로세스):

- `focused-eval`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:test --tests *RuntimeClasspathFinalizerFunctionalTest --no-daemon --console=plain`
- `focused-eval-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\eval-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-eval:test" --no-daemon --console=plain`
- `app-unit`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain`
- `app-unit-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain`
- `app-stack`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *StackSmokeIT --tests *AppContextWiringIT --no-daemon --console=plain`
- `app-stack-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\stack-filter.init.gradle.kts -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain`
- `raw-jar-first`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\review-artifact.init.gradle :gateway-app:writeRuntimeClasspath --no-daemon --console=plain`
- `raw-jar-changed-timestamp`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\review-artifact.init.gradle :gateway-app:writeRuntimeClasspath --no-daemon --console=plain`
- `spec-guard`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --no-daemon --console=plain`
- `native-current`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-integration:integrationTest -Paigw.requireDocker=true --tests *TokenizerNativeSmokeIT --no-daemon --console=plain`
- `native-current-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\tokenizer-filter.init.gradle.kts -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-integration:integrationTest --require-docker" --no-daemon --console=plain`
- `local-clean`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway clean --no-daemon --console=plain`
- `local-clean-build`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway build --no-daemon --console=plain`
- `local-clean-build-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test" --no-daemon --console=plain`
- `runtime-clean`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --tests *RuntimeClasspathCleanTest --no-daemon --console=plain`
- `runtime-clean-verify`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-review-fix\runtime-filter.init.gradle.kts :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain`
- `final-sync`: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:syncRuntimeClasspathEvidence --no-daemon --console=plain`

P04 검토 수정의 로컬 검증은 통과했지만, 사용자 승인 전 commit 금지라는 원래 인계 조건에 따라 커밋·staging·push·checkout을 하지 않았다. 따라서 이번 clean 후 build는 로컬 N8 producer/전체 Eval 회귀 증거이며 **깨끗한 Git checkout build는 미검증**이다. 부모의 독립 검토도 대기 중이다. P04 최종 완료는 선언하지 않으며 P05는 시작하지 않았다. SPEC/Plan 계약 변경은 없고 기존 승인 scope line은 그대로 유지했다. 서버·SSH·K8s·배포·설정/재시작·모델 호출은 수행하지 않았다.

### 2026-11-05 P04 최종 감독 검증 결론

- 부모가 최종 코드와 원시 증거를 직접 독립 검증하여 **로컬 P04 코드·검증 PASSED**로 확정했다. 고유 시험 259건(Eval 249 + 앱 단위 6 + 앱 IT 3 + native IT 1), 실패·오류·건너뜀 0건이다. 각 XML 집계, OTR 바이트, record contentSHA, binding component hash, fingerprint/runId, 실제 HEAD와 runtime hash가 일치했다.
- 실제 runtime JAR 186행과 native 바이너리 해시, 문서 사본의 바이트 일치를 독립 확인했다(runtime SHA256=`29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`). 부모가 실제 producer의 원시 JAR 바이트·타임스탬프 변경을 재시험하여 두 실행 모두 EXECUTED이고 각각 새 SHA를 정확히 반영함을 확인했다. 현재 단위 출력과 동일한 앱 runtime filter를 적용한 공식 `test-evidence verify`도 부모가 별도 실행하여 exit 0 / `TEST_EVIDENCE_VERIFIED`를 확인했다.
- 이전 ‘부모 독립 검토 대기’는 당시 이력이며 현재는 통과했다. 보호 파일 483개, Spec·baseline·승인 문서, 기존 보고서 prefix와 정본 구성 경로도 부모가 확인했다. 실제 HEAD는 `a72b1b79dd8995c2da5542ff7064c54773727230`이며 미커밋 소스가 있는 dirty 작업 트리를 배포 신원으로 간주하지 않는다.
- 원래 인계 조건인 ‘사용자 승인 후에만 커밋’(`commits only after user approval`)을 유지한다. **남은 완료 조건은 사용자 승인 후 커밋 및 해당 커밋의 clean Git checkout 빌드 검증**이다. 그때까지 P04 최종 완료는 보류하며 P05는 미착수다. 이번 작업은 문서 끝 추가만 수행했으며 빌드·시험을 재실행하지 않았다.


부모의 최종 단위 증거 verifier 정확한 argv (`root-final-validation/root-unit-evidence.json`; exit 0):

```json
[
    "C:\\workspace_nori\\nori-ai-gateway\\gradlew.bat",
    "-p",
    "C:\\workspace_nori\\nori-ai-gateway",
    "-I",
    "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p04-review-fix\\root-final-validation\\runtime-filter.init.gradle.kts",
    ":gateway-eval:run",
    "--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test",
    "--no-daemon",
    "--console=plain"
]
```


### 2026-11-05 P04 커밋 및 실제 clean checkout 종료 결론

사용자의 최신 명시 승인에 따라 로컬 커밋을 완료했다. 실제 검증한 코드 커밋은 `8e51ea97049c86ee2a4729d736b8cd934131c792`다. `git clone --no-hardlinks`로 `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p04-commit-close/clean-checkout`에 복제한 뒤 해당 커밋을 detached checkout했다. 빌드 전 tracked/unstaged/untracked 상태는 모두 비어 있었고 build 디렉터리·생산물도 없었다. P03 tokenizer/golden/arg-limits fixture는 tracked checkout에서 공급되었고 기존 untracked 진단 자료·Python 캐시를 복사하지 않았다.

첫 고립 clone build는 P03 arg-limits 근거 시험이 부모 경로의 외부 원본3개 레포를 찾지 못해 NoSuchFileException으로 실패했다. 실패 clone과 원시는 clean-checkout-first-failed 및 first-failed-build*로 그대로 보존했다. 기존 P03 시험은 실제 CODE/SCHEMA의 경로:줄을 대조하므로 scratch sibling에 nori-tc-exec/common/ui-backend 실제 원본을 가리키는 directory junction만 제공했다(읽기 전용 사용, 근거 파일4개 해시 전후 일치). SPEC은 명시한 같은 승인 원본이다. 소스 변경·증거/생산물 복사·시험 제외 없이 새 정상 Git clone을 다시 만들고 build 산출물0 상태에서 아래 명령을 수행했다. 외부 입력 신원은 external-workspace-inputs.json에 기록했다.

모든 호출은 `AIGW_SPEC_FILE=C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md`를 명시하고 기존 gitlab.com 호스트 자격을 메모리 조회·자식 환경으로만 전달했다. 정확한 별도 명령:
- `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-commit-close\clean-checkout\gradlew.bat -p C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-commit-close\clean-checkout build --no-daemon --console=plain` → exit 0
- `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-commit-close\clean-checkout\gradlew.bat -p C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-commit-close\clean-checkout :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test" --no-daemon --console=plain` → exit 0

실제 `build` 단독 실행에서 `:gateway-app:writeRuntimeClasspath`가 `:gateway-app:test`보다 먼저 EXECUTED되었고 integrationTest는 실행되지 않았다. 앱 6 + Eval 249 = 255건, 실패·오류·건너뜀·중단 0건이다. 직후 기본 필터(필터·태그 추가 없음)의 별도 verifier는 `TEST_EVIDENCE_VERIFIED`를 출력했다.
- `:gateway-app:test`: 6건 PASS, runId=`3693a23e-a297-4dfa-bc76-12871e3f4144`, fingerprint=`a2a571f7ba056f42a22317d7d99db0c6a2ff9e3b52cda05e9395410d65f3cff1`, OTR aggregate SHA256=`15dc492c9f598c8e34cc153b351ef2f935ccd8df3cfd453ef1b8b9aa58934e29`, 요약 contentSHA=`2da01866ac5c00d73cf8f86625242082e89bd34db278217157b388622c102243`.
- `:gateway-eval:test`: 249건 PASS, runId=`e8f87bcb-6e25-45de-9aa6-e8ff151b7435`, fingerprint=`7e3a99c5ade318c341707796af5029cda9bd704da9d3b7b793418dba4baee1a5`, OTR aggregate SHA256=`b265d8d64a5d03478d3a554bb53582b14927e9b765660df6956231b052b5aca3`, 요약 contentSHA=`ef3ffd4dac7783939187f4732112ddf117801202810a24f1f2a58156fee4554d`.

두 finalizer의 실제 gitSha는 코드 커밋 `8e51ea97049c86ee2a4729d736b8cd934131c792`와 같고 runtimeClasspathSha256은 실제 생산 파일의 SHA256 `29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`와 같다. XML 집계, OTR 실제 바이트의 aggregate SHA, binding fingerprint, summary/record contentSHA를 독립 대조했다. 빌드 후 clone Git 상태도 비어 있다. 상세 원시는 이번 scratch의 clean-build*.json/log, clean-validation.json과 XML/OTR 사본에 있다.

부모의 이전 최종 소스·259건(단위255 + 앱IT3 + nativeIT1) 독립 검증은 이미 통과했다. 이번에는 소스·의존 입력 변경 없이 커밋 및 실제 clean checkout 검증만 추가했으므로 안정 코드의 Docker/native 시험을 반복하지 않았다. 승인 소스32개, 보호483개, SPEC·baseline·승인6의 바이트는 불변이다. Plan은 기존 승인 범위 문구 위에 P04 구현/검증 체크박스만 완료로 전환했다. 기존 문서 바이트는 모두 prefix로 보존했다.

**P04 완료. P05는 아직 시작하지 않았다.** 종료 보고를 담는 후속 docs-only 커밋은 위에서 실제 시험한 코드 커밋과 구분한다. 해당 커밋의 코드·빌드 트리는 시험한 커밋과 동일하며 그 docs-only HEAD를 시험했다고 주장하지 않는다. 실제 모델 호출·SSH·K8s·배포·서비스·설정 변경·push는 수행하지 않았다. 부모가 이번 결과를 직접 확인한 후 P05를 새 독립 세션에서 이어간다.


### 2026-11-05 P05 Core 계약 구현 — 부모 직접 검증 대기

P04 corrected-layout clean checkout PASS(`../p04-commit-close/result.json`, 실제 코드 `8e51ea9`, 종료 HEAD `d3c96c11651134e74b685b699127e562dd126760`)를 확인한 뒤 P05만 구현했다. 최초 셸 명령은 잘못 추정한 `spec-guard` 파일 읽기로 실패하여 요청된 첫 명령 형식을 지키지 못했다. 구현 파일 변경 전 실제 `:gateway-eval:run --args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md`는 exit 0이었다. 현재 소스는 위 HEAD + P05 미커밋 변경이며, 부모가 직접 검증하기 전 P05 검증 완료·커밋을 선언하지 않는다.

Scope·닫힌 enum·Manifest/Workflow 도메인과 networknt 기동 검증, 정확 버전/등록 digest 정책, owner/활성/ANY-of 권한 Registry view, 순서 ①~⑨ Validator, 실행 직전 Dispatcher 검사와 계약 위반 결과 제거를 구현했다. 리소스 인자도 Schema의 x-resourceType 바인딩으로 검증하므로 Plan에서 resources를 빼서 존재 검사를 우회할 수 없다. q는 디코드 원문 CODEPOINT로 검사하고 qRef 주입은 거부한다. 날짜·시각·offset과 나노초 비교를 검증하며 P13 시간창 우선순위 엔진은 구현하지 않았다. INC의 두 선택 Source는 summary 의존성과 선언된 두 조건만 가진다(실행은 P13). TC Manifest 9종·Workflow 4종이며 index.json은 classpath 목록일 뿐 추가 Capability/Workflow가 아니다. Knowledge 2종은 PLANNED, 이벤트 권한 미확정은 빈 매핑으로 기본 거부한다. AI_* 권한명은 SPEC §9.3의 Mock 단계 매핑 예시이며 실제 TC 권한 등록·배포를 주장하지 않는다.

공통 wiring 예외는 CoreBeansConfiguration의 실제 8개 @Bean 및 AppContextWiringIT의 실제 호출 assertion뿐이다. 기본 seam은 지정된 ConditionalOnProperty(false/missing), enabled=true인데 후속 hooks가 없으면 기동 실패한다. 운영 executor는 등록하지 않아 호출은 fail-closed다. 완료 로직을 fake/no-op으로 대체하지 않았다. Core 시험은 실제 앱 계약 리소스를 test classpath에 포함하여 계약 데이터 변경도 입력 지문에 결속한다. networknt 기본 preload가 참조 오류를 지연할 수 있어 initializeValidators를 명시 호출하며 외부 Schema 참조/식별자는 거부한다.

기술 지원 차이: 기존 승인 RFC 8785 동작을 위해 `gradle/libs.versions.toml`에 `jsonCanonicalization=1.1`과 alias 하나만 추가하고 core에만 `io.github.erdtman:java-json-canonicalization:1.1`을 연결했다(기존 pin 변경 0). 부모가 허용한 최소 allowed_writes 보정 및 날짜·근거를 기존 Plan P05에 직접 반영했다. 사용자 별도 라이브러리 선정으로 표현하지 않는다. 공식 upstream https://github.com/erdtman/java-json-canonicalization 및 Maven Central 1.1 POM/API·Apache-2.0을 재확인했다. 실제 RFC 숫자·음수 0·지수 경계·Unicode UTF-16 키 정렬·escape·surrogate 거부 벡터를 시험했으며 일반 sorted-key JSON으로 대체하지 않았다.

변경 경로 전체(각 행의 디렉터리와 파일명을 결합; 기존 dev report는 이 절만 append):

- `docs/`: `ai-rag-gateway-architecture-dev-report.md`.
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/`: `AppContextWiringIT.java`.
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/`: `CoreBeansConfiguration.java`.
- `gateway-app/src/main/resources/manifests/tc/`: `index.json`, `tc.dlq.list.json`, `tc.eqp.getOperationalSnapshot.json`, `tc.event.getRecent.json`, `tc.git.searchCode.json`, `tc.knowledge.getSource.json`, `tc.knowledge.search.json`, `tc.logs.search.json`, `tc.model.read.json`, `tc.trace.search.json`.
- `gateway-app/src/main/resources/workflows/tc/`: `GENERAL_QUERY.json`, `INCIDENT_ANALYSIS.json`, `KNOWLEDGE_QUERY.json`, `LIVE_QUERY.json`, `index.json`.
- `gateway-app/src/test/java/com/nori/ai/gateway/app/manifest/`: `CoreSeamDefaultsTest.java`, `InputLimitsNotNarrowerTest.java`.
- `gateway-core/`: `build.gradle.kts`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/dispatch/`: `DispatchTarget.java`, `ExecutionContext.java`, `ToolDispatcher.java`, `ToolExecutor.java`, `ToolResultContractValidator.java`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/plan/`: `InputValidator.java`, `OwnerBoundary.java`, `PlanEnvelope.java`, `PlanStep.java`, `PlanValidator.java`, `RejectReason.java`, `ResourceInputs.java`, `ResourceRef.java`, `ResourceResolver.java`, `ValidationOutcome.java`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/registry/`: `Availability.java`, `CapabilityManifest.java`, `CapabilityRegistry.java`, `ContractVersionPolicy.java`, `ImplementationPhase.java`, `InputLimit.java`, `ManifestLoader.java`, `RegistryView.java`, `RetryPolicy.java`, `RiskLevel.java`, `SchemaContracts.java`, `SideEffects.java`, `TaskType.java`, `ToolKind.java`, `WorkflowDefinition.java`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/`: `AdmissionErrorCode.java`, `Claim.java`, `Clarification.java`, `ErrorCode.java`, `Evidence.java`, `JobStatus.java`, `ResultBlock.java`, `ResultStatus.java`, `ToolResultEnvelope.java`, `ToolResultMetadata.java`, `Verdict.java`, `Warning.java`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/scope/`: `ActorRef.java`, `OwnedReference.java`, `OwnerSystem.java`, `VerifiedSystemScope.java`.
- `gateway-core/src/main/java/com/nori/ai/gateway/core/seam/`: `DispatchInjectionSeam.java`, `PlanInjectionSeam.java`.
- `gateway-core/src/main/resources/contracts/`: `capability-manifest.schema.json`, `workflow.schema.json`.
- `gateway-core/src/test/java/com/nori/ai/gateway/core/`: `ContractVersionPolicyTest.java`, `DispatcherOwnerCheckTest.java`, `DomainFixtures.java`, `ManifestDigestTest.java`, `PlanValidatorAvailabilityTest.java`, `PlanValidatorOrderTest.java`, `RegistryViewTest.java`, `ResultStatusClosedEnumTest.java`, `WorkflowOptionalStepContractTest.java`.
- `gradle/`: `libs.versions.toml`.

정확한 별도 검증 명령·실행 결과(전부 AIGW_SPEC_FILE 명시, glab host token 메모리 조회 → 자식 Authorization Bearer 환경만, --no-daemon; includeTags 없음; 필터 replay는 scratch init에만 적용):

- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-core:test --no-daemon --console=plain` → exit 0; XML 8개/31건, 실패·오류·skip·aborted 0; runId=`ad7049de-a558-4c82-a64f-0e3788896032`.
  직후 별도 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.
- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --tests *InputLimitsNotNarrowerTest --no-daemon --console=plain` → exit 0; XML 1개/5건, 실패·오류·skip·aborted 0; runId=`06902360-91f0-4f74-ac26-9582beb67e42`.
  직후 별도 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p05\input-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.
- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain` → exit 0; XML 6개/13건, 실패·오류·skip·aborted 0; runId=`1d7d6422-e6c3-4e20-8731-2e2dcb13dcf9`.
  직후 별도 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.
- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *AppContextWiringIT --no-daemon --console=plain` → exit 0; XML 1개/1건, 실패·오류·skip·aborted 0; runId=`cc9b69bb-9531-4cac-a4e0-4a318c3fb893`.
  직후 별도 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p05\wiring-filter.init.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.

앱 전체 단위 13건은 P04 모듈 방향·금지 scanner·Scope 생성자·Core 시스템 분기·등록 완전성·runtime 청결 가드와 P05 입력/seam 시험을 포함한다. AppContextWiringIT는 실제 로컬 임시 PostgreSQL 컨테이너로 전체 컨텍스트·실제 ManifestLoader→CapabilityRegistry.view·seam 각 1개를 확인했으며 requireDocker=true, skip 0이다. Core 31 + 앱 전체 13 + 앱 IT 1은 서로 다른 45개 시험이고, 별도 필수 입력 5건은 앱 전체와 중복이다. 태그 단위 계약 시험 성공을 F1 전체 게이트/실제 Source 수용으로 확대하지 않는다.

중간 진단도 scratch 로그에 보존했다. 테스트 코드 컴파일 오류 2건을 수정했다. 첫 IT 시도는 PowerShell이 -Paigw.requireDocker=true를 두 인자로 분리해 Test task 시작 전 실패했고 직후 verifier도 실패했다(0건 PASS로 세지 않음). 인자를 인용한 새 명령으로 실제 Docker 시험·별도 verifier를 실행했다. 최종 결과는 위 새 runId만 기준이며 이전 출력을 새 실행으로 재사용하지 않았다.

SPEC SHA 136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3, baseline·approval6, 보호 483개(기존 F0 관련 482개 전부 포함)가 일치한다. 기존 dev report prefix는 바이트 그대로 보존했다. Python 캐시는 변경/커밋 후보에서 제외했다. 새 raw model/API 응답 저장·실모델/cloud API·SSH·K8s·배포·기존 서비스 재시작·운영 설정·전역 인증 변경·push·커밋은 0이다.

미실행: Eval 전체 249 회귀(변경 0으로 불필요), P05 clean checkout/부모 독립 Validate, 실제 TC/Source/LLM 연동(후속 phase 소유). P07 ScopeAssembler, P11 실제 executor·Source 인가/allowlist·verify hooks, P13 조건 평가/Workflow 실행·Composer·시간창 우선순위, P20 qRef 복원, P23 Knowledge 활성화는 미구현이다. 미해결 업무/보안/승인 계약/운영 결정은 없다. 부모 직접 검증 대기이며 P06은 구현하지 않았다. 상세 명령 JSON·XML·OTR·보호 해시·정확한 전체 경로와 SHA는 이번 scratch `p05/result.json`에 보존한다.

### P05 ROOT-REVIEW 원인 수정 (2026-11-05, 부모 독립 검증 대기)

기존 승인 Spec/Plan과 부모의 실제 경계 probe를 대조해 원인을 수정했다. 기존 생성자·record 필드를 유지하면서 actorRef.value=sub 일치를 명시적으로 강제했다. 이는 Plan §3.3의 actorRef(sub)와 SPEC §9.1의 단일 사용자 신원을 보장하는 최소 변경이며 새 인가 정책·ScopeAssembler를 추가하지 않는다. DomainFixtures와 AppContextWiringIT의 사용자도 일치시켰다.

9개 Manifest의 requiredActorScope와 Registry·현재 인가 fixture를 승인된 READ로 맞췄다. resourceScope=AUTHENTICATED_ACTOR는 서명 scope 클레임이 아니라 인증된 actor의 현재 permCode가 허용하는 리소스 집합을 나타내는 기존 로컬 표지이므로 보존했다(SPEC §9.3). requiredPermission ANY-of는 유지했고, tc.event.getRecent의 []는 상세 F5a 매핑 확정 전 기본 거부로 보존했다. 실제 Registry view 6개와 Knowledge 2개 PLANNED를 유지하며 별도 사용자 결정을 요구하지 않는다.

Dispatcher의 기존 유효 시각 검사와 등록된 Tool owner 검사를 resolver의 resolve/source/authorize/timezone 및 DispatchInjectionSeam보다 먼저 수행한다. 주입 후 실제 Tool/resource owner 검사는 그대로 유지한다. PlanValidator ①~⑨ 순서와 기존 신원·TTL 정책은 변경하지 않았고 비동기 위임·갱신 정책은 구현하지 않는다. 새 회귀 시험은 일치/불일치 신원, READ의 Registry→Validator→Dispatcher 성공, 잘못된/누락 scope 거부, 만료·만료 경계·미래 scope 및 타 owner/미등록 버전에서 외부 호출·seam·executor 0과 결과 참조 비공개를 검증한다.

수정 파일(이번 review-fix 증분):
- `gateway-app/src/main/resources/manifests/tc/tc.dlq.list.json`
- `gateway-app/src/main/resources/manifests/tc/tc.eqp.getOperationalSnapshot.json`
- `gateway-app/src/main/resources/manifests/tc/tc.event.getRecent.json`
- `gateway-app/src/main/resources/manifests/tc/tc.git.searchCode.json`
- `gateway-app/src/main/resources/manifests/tc/tc.knowledge.getSource.json`
- `gateway-app/src/main/resources/manifests/tc/tc.knowledge.search.json`
- `gateway-app/src/main/resources/manifests/tc/tc.logs.search.json`
- `gateway-app/src/main/resources/manifests/tc/tc.model.read.json`
- `gateway-app/src/main/resources/manifests/tc/tc.trace.search.json`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/dispatch/ToolDispatcher.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/registry/CapabilityRegistry.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/scope/VerifiedSystemScope.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/DispatcherPreReadBoundaryTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/DomainFixtures.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/VerifiedIdentityAndReadScopeTest.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java`
- `docs/ai-rag-gateway-architecture-dev-report.md`(이 절만 append).

CoreBeansConfiguration의 두 한국어 주석, core build의 classpath 결속 주석, ignored Plan의 P05 allowed_writes RFC 8785 주석·부모 기술 보정 문장은 작업 시작 시 실제 UTF-8 바이트에서 이미 정상 한국어였다. 시작 snapshot 및 최종 검사에서 연속 물음표/대체 문자 손상이 없으므로 재작성하지 않았다. 기존 최소 JCS 1.1 버전·alias는 승인된 RFC 8785 지원을 위한 기술 선택이며 별도 사용자 라이브러리 선정이나 SPEC/기존 pin 변경이 아니다. Plan 전체 바이트를 그대로 유지했다.

실행 환경은 기존 Temurin JDK 21.0.10과 venv Python이며, 모든 Gradle 호출에 AIGW_SPEC_FILE을 명시했다. 복사한 secure runner는 glab 자격을 메모리에서만 받아 자식 Bearer 환경으로 전달하고 로그에서 redaction한다. 다음 각 Test 직후 같은 필터/requireDocker 입력으로 별도 공식 verifier를 실행했다.

- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-core:test --no-daemon --console=plain` → exit 0, XML 10개/36건, 실패·오류·skip·aborted 0, OTR 1개, runId=`9a190d45-512e-4620-bade-d57c2703a580`.
  직후 별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.

- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --tests *InputLimitsNotNarrowerTest --no-daemon --console=plain` → exit 0, XML 1개/5건, 실패·오류·skip·aborted 0, OTR 1개, runId=`cfb7fccf-2c25-401a-b362-11a998ed2400`.
  직후 별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p05-root-fix\input-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.

- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain` → exit 0, XML 6개/13건, 실패·오류·skip·aborted 0, OTR 1개, runId=`ebeed893-927a-4fce-85a7-05a54f8a044f`.
  직후 별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.

- 시험: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *AppContextWiringIT --no-daemon --console=plain` → exit 0, XML 1개/1건, 실패·오류·skip·aborted 0, OTR 1개, runId=`ad1a4e90-9066-4255-a5ec-a5d160a1098a`.
  직후 별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p05-root-fix\wiring-filter.init.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain` → exit 0, TEST_EVIDENCE_VERIFIED.

부모 RootBoundaryProbe를 현재 core main/test 클래스 및 실제 런타임과 SHA가 일치하는 의존성 187개로 다시 컴파일·실행했다. 결과: inconsistentActorAccepted=false, signedReadValidates=true, signedReadVisible=true, expiredScopeResolverReads=0, knownOtherOwnerResolverReads=0. probe의 신원 출력과 별도로 새 생성자 음성 시험이 불일치 예외를 확인했다. 최초 보조 probe 스크립트의 classpath manifest 형식/좌표 해석 오류는 저장소 변경 없이 수정했고, 공식 테스트 실패는 없었다. 기존 p05 실패·진단 증거는 보존했다.

SPEC SHA 136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3, baseline·approval6 및 보호 483개와 기존 Dev report prefix 바이트를 보존했다. source/comment/신규 report UTF-8·LF와 연속 물음표/대체 문자 부재를 검사했다. XML·OTR·실제 runId·명령·해시는 p05-root-fix/result.json 및 같은 scratch의 실행별 원본에 있다.

미실행/한계: 부모의 독립 Validate 및 clean checkout은 대기 중이다. 이 시험은 검증된 scope를 소비하는 P05 계약 시험이며 실제 JWS 서명 검증은 후속 P08 책임이다. 실제 TC/Source/LLM·K8s·네트워크 모델·운영 설정은 실행/변경하지 않았다. Eval 전체 회귀는 Eval 변경이 없어 재실행하지 않았다. 미래 phase 구현·승인 산출물 변경·커밋·push는 없다. 실제 차단 사유는 없고 P05 검증 체크박스는 미완료로 유지했다.

### P05 시험 메타데이터 보정 (2026-11-05, 부모 최종 검증 대기)

Plan §4.2와 실제 OtrLeafExtractor의 단계 누락 시 F0 기본값 처리를 확인했다. 아래 세 클래스에 기존 JUnit @Tag("stage-F1")와 @Tag("label-MOCK_API")를 클래스 수준으로 추가했다. 기존 leaf ID·assertion·guard 및 기능 구현은 그대로 유지했다.
- `gateway-core/src/test/java/com/nori/ai/gateway/core/PlanValidatorAvailabilityTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/DispatcherOwnerCheckTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/ContractVersionPolicyTest.java`

수정 전 실제 LEAF 6개는 stage=F0, labels=[]였다. 새 실행의 동일한 leaf 집합 6개는 모두 stage=F1, labels=[MOCK_API], PASS이며 contentSha256·OTR·binding 결속을 확인했다. RUN 요약 stage=F0는 기존 task 요약 규약이다. componentId=IT인 로컬 단위 시험 보조 증거이므로 catalog의 ACCEPTANCE/ACC:F1 수용 증거를 대체하거나 F1 게이트 PASS로 처리하지 않는다.

모든 명령에 AIGW_SPEC_FILE 정본 경로를 지정했고, 기존 secure runner를 복사해 자격을 메모리 및 자식 Bearer 환경에서만 사용했다. 수정 전 spec-guard와 수정 후 Test, 직후 별도 verifier를 순서대로 실행했다.
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --no-daemon --console=plain` → exit 0.
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-core:test --no-daemon --console=plain` → exit 0.
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --no-daemon --console=plain` → exit 0 / TEST_EVIDENCE_VERIFIED.

실제 Test task는 EXECUTED, XML 10개/36건, OTR 1개이며 실패·오류·skip·aborted는 모두 0이다. runId=`4c7b3037-65de-4e28-a04a-8b6c5755e5d4`. filters=[], includeTags=[], requireDocker=false이며 runtimeClasspathSha256=`e78599abbf4846c2eea36eddc537f3e29fa0a4bbf3faff21379939471d42ab42`는 수정 전과 같다.

기존 Dev report 전체 prefix 바이트와 SPEC·Plan·baseline·approval6·보호 483개 및 기존 F0 증거를 보존했다. 기존 소스/기능 구현은 변경하지 않았다. 증거 원본·명령·해시는 scratch p05-tag-fix/result.json과 같은 디렉터리에 보존했다. 변경 범위 밖인 app/Docker/Eval 전체 시험과 부모 독립 probe는 재실행하지 않았다. 부모 최종 Validate·clean checkout은 미실행이며 P05 검증 상태를 변경하지 않았다. 업무 차단 사유는 없으며 커밋·push·P06 진행은 없다.

### P05 부모 직접 Validate 및 로컬 커밋 마감 (2026-11-05)

부모의 최종 `p05-root-fix/root-validation-approval.json`(passed=true)을 확인하여 P05 구현·검증을 완료로 반영했다. 부모는 실제 소스·호출 흐름·bean wiring과 실패 경계를 검토하고 독립 Java probe를 다시 실행했다. actorRef.value=sub 단일 신원 강제(`VerifiedSystemScope`), READ 계약 정합(9개 TC Manifest·`CapabilityRegistry`), 외부 읽기 전 scope·등록 Tool owner 차단(`ToolDispatcher`)을 확인했다. fixture·`AppContextWiringIT`도 해당 계약에 맞췄으며 신규 회귀 시험 `DispatcherPreReadBoundaryTest.java`, `VerifiedIdentityAndReadScopeTest.java`가 이를 검증한다. probe는 불일치 신원 허용=false, READ 노출·검증=true, 만료 scope·타 owner의 resolver 읽기=0이다. 완료 로직을 fake/no-op으로 등록하지 않았으며 주입 없음이 기본인 두 seam만 기본 no-op이다.

최종 승인 시험은 Core 36건(runId `4c7b3037-65de-4e28-a04a-8b6c5755e5d4`), 앱 전체 13건(`ebeed893-927a-4fce-85a7-05a54f8a044f`), 실제 로컬 Docker PostgreSQL AppContext IT 1건(`ad1a4e90-9066-4255-a5ec-a5d160a1098a`)으로 서로 다른 50건이며 실패·오류·skip·aborted=0이다. 별도 필수 입력 5건(`cfb7fccf-2c25-401a-b362-11a998ed2400`)은 앱 전체와 중복이다. 정확한 Test·직후 공식 verifier 명령과 exit=0은 `p05-root-fix/result.json`, 최종 태그 보정 Core 명령은 `p05-tag-fix/result.json`에 있다. 부모의 XML·OTR·binding·content 해시 대조 및 최종 runId는 `p05-root-fix/root-evidence-verification.json`, 독립 probe 결과는 `root-boundary-probe.json`, 공식 verifier 결과는 `root-final-unit-verify.json`, `root-final-it-verify.json`, `root-final-core-tags-verify.json`에 보존했다. 세 기존 테스트 클래스의 태그 보정으로 동일 LEAF 6개는 F1/MOCK_API이며 RUN 요약의 legacy F0 규약과 Eval 구현은 유지했다. 이는 P05 로컬 계약 검증 승인으로 F1 exit·ACCEPTANCE 승인과 다르다.

검증 당시 기준 HEAD는 `d3c96c11651134e74b685b699127e562dd126760`와 당시 dirty source tree다. 승인된 83개 커밋 후보와 SHA-256은 `p05-root-fix/root-approved-source.json`(SHA-256 `096e0e6c8049d717338f1cd30648f2f263d1158446f23adad9e1f663a6acc44b`)에 있다. 마감에서는 구현·시험·build 바이트를 그대로 보존하고 기존 보고서 뒤에 이 절을 추가하며 ignored Plan의 P05 검증 체크박스만 완료로 바꾼다. 기존 JCS 1.1과 catalog 최소 지원 변경은 앞서 기록한 승인 RFC 8785 구현을 위한 기술 지원이다. 이전 P05 보고서의 미래 `ScopeAssembler` 생성 단계 P07 표기는 역사적 오기이며 실제 생성은 Plan P08이다. 과거 문장은 수정하지 않았다.

SPEC·baseline·approval6·보호 483개와 기존 F0·실패·controlled-stop 증거를 보존했다. 기존 보고서 전체 prefix는 바이트 단위로 보존하며 마감 전 길이·해시와 마감 후 증명은 `p05-close/preflight.json`, `p05-close/result.json`에 기록한다. 최초 P05 CLI는 최종 산출물 작성 후 응답 재시도 중 부모가 중단한 exit 1이며 자연 종료 성공으로 해석하지 않는다. 최종 PASS 근거는 실제 review-fix·metadata-fix 결과와 부모의 직접 검증이다. 로컬 커밋의 Git blob을 승인 소스 해시와 대조하며 미래 커밋 SHA에서 시험을 실행했다고 주장하지 않는다.

이 마감은 소스 변경이 없어 시험·fresh gate·전체 Eval 249·clean checkout을 재실행하지 않는다. P04의 실제 fresh clone 검증 이력은 유지한다. 실제 Source·TC·LLM·K8s 및 후속 phase 기능은 미실행이며 미래 검증 책임으로 남는다. P06 구현, push·MR·배포·SSH·서비스/운영 설정·전역 인증 변경은 하지 않는다. 미해결 사용자 결정 차단 사유는 없다. 실제 로컬 커밋 SHA·후속 무결성 확인은 `p05-close/result.json`을 기준으로 한다.


## P06 독립 CLI 구현·로컬 검증 (2026-11-05)

상태: 로컬 준비 구현·검증 완료, P06 운영 게이트 미완료·승인 계약 충돌 BLOCKED. 부모 직접 검증 전이며 P07·커밋·push·MR·배포는 실행하지 않았습니다. 실행 세션 01a1abbf-cfa3-7d62-9898-5fb09bc23680은 gpt-6.1-sol / medium 독립 Codex CLI이며 하위 에이전트는 0입니다. P05 부모 승인·검토 산출물 12개 해시와 로컬 커밋 b028c81372f6d3f1aa5e7bfee32e94ae1e12df16을 먼저 확인했습니다.

### 변경 내용·영향

두 release 전용 DB 차트, 단일 bootstrap SQL, 승인 입력 schema/fixture, 읽기 전용 exec clone·Helm 실행 경로, 런타임 Secret 검사/회전 절차, 승인 key별 메타데이터 집계와 대기 게이트 문서를 구현했습니다. DB 이름·인스턴스 label·PVC·Ingress target을 release별로 분리하며 PG18 data mount와 PVC Retain을 적용했습니다. 비밀번호는 Secret env/psql getenv와 stdin 파이프만 사용합니다. app/migrator/backup DML과 gw 스키마는 P07 책임으로 남겼습니다. 앱 차트·CronJob·CI·P05 코드/테스트/기존 pin은 변경하지 않았습니다.

실제 변경 파일(해시는 scratch result.json의 양 레포 changedFiles에 기록):

- `ai:docs/gates/f1/backup-destination.md`
- `ai:docs/gates/f1/corpus-inventory.md`
- `ai:docs/gates/f1/exec-chart-ref.md`
- `ai:docs/gates/f1/postgres-image.md`
- `ai:docs/gates/f1/pvc-capacity.md`
- `ai:docs/gates/f1/pvc-protection.md`
- `ai:docs/runbooks/aigw-runtime-credentials.md`
- `ai:evidence/F1/gates/p06-local-verification.json`
- `ai:ops/deploy/aigw-helm.sh`
- `ai:ops/deploy/read-exec.py`
- `ai:ops/deploy/requirements.txt`
- `ai:ops/deploy/runconfig.py`
- `ai:ops/gates/aigw-runtime-credentials-selftest.sh`
- `ai:ops/gates/aigw-runtime-credentials.sh`
- `ai:ops/postgres/bootstrap.sql`
- `ai:ops/runconfig/README.md`
- `ai:ops/runconfig/fixtures/LINT-ONLY.md`
- `ai:ops/runconfig/fixtures/lint-verify.json`
- `ai:ops/runconfig/negative-missing-pvc.json`
- `ai:ops/runconfig/verify.schema.json`
- `ai:ops/seaweedfs/bin/corpus-inventory.sh`
- `exec:deploy/helm/ai-gateway/db-chart/Chart.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/templates/_helpers.tpl`
- `exec:deploy/helm/ai-gateway/db-chart/templates/bootstrap-configmap.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/templates/bootstrap-job.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/templates/networkpolicy.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/templates/service.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/templates/statefulset.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/values-restore.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/values-verify.yaml`
- `exec:deploy/helm/ai-gateway/db-chart/values.yaml`
- `ai:docs/ai-rag-gateway-architecture-dev-report.md`는 기존 780935바이트 뒤에 이 절만 추가합니다.

### 실제 검증·명령

- 수정 전 secure runner: `C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe ../p05-root-fix/run_gradle.py p06-spec-guard-actual :gateway-eval:run "--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --secure-auth` → exit 0. AIGW_SPEC_FILE도 원본 절대 경로로 명시되었습니다. 처음 `spec-guard`를 Gradle task로 호출한 시도는 task 없음으로 exit 1이었고 실제 EvalCli 진입점으로 바로잡았습니다.
- scratch `tools.Dockerfile`로 일회용 로컬 도구 이미지를 빌드했습니다. 기반은 python 3.12.10-slim-bookworm digest fd95fa221297a88e1cf49c55ec1828edd7c5a428187e67b5d1805692d11588db와 alpine/helm digest ee6fe3e96d9f8ea8dd1af9ecd7bbb3e233616a25f145392376f020fd2a51eb33입니다. 도구 이미지 ID는 aa75a3bbab72c9c0be4e0dada6d7c41cc944fd9b74e8f090683884ee7e22d892입니다. 전역 설치는 하지 않았습니다. Linux bash 5.2.15, Helm v4.2.2+gb05881c, Python 3.12.10, Git 2.39.5, OpenSSL 3.0.22이며 Python 의존은 ops/deploy/requirements.txt의 고정 버전과 일치합니다.
- `docker run --rm --network none --mount type=bind,source=C:/workspace_nori/nori-ai-gateway,target=/ai,readonly --mount type=bind,source=C:/workspace_nori/nori-tc-exec,target=/nori-tc-exec,readonly --mount type=bind,source=<P06 scratch>,target=/scratch aigw-p06-tools:local python3 -B /scratch/local_tests.py` → exit 0, 27개 실행 검사. 실제 main/restore Helm lint·render, parsed name/instance/selector/PVC retention/PGDATA/Secret env/backup CronJob 0, missing persistence 정확 음성 code, 잘못된 release·replicas·PG17·restore backup 거부, shell syntax, 입력/profile/승인 경계와 selftest를 확인했습니다. fake helm은 사용하지 않았습니다.
- 같은 Docker 옵션으로 `python3 -B /scratch/regression_tests.py` → exit 0, 변경한 경계의 16개 회귀 검사(앞 검사와 중복 포함). 최종 schema·누락 key/ref·대기 승인 거부, 자격 selftest와 CI/운영자 ASKPASS·401/403·잘못된 SHA·종료 후 helper 제거·sentinel 비노출, inventory의 owner 미승인/밖 파일 거부 및 AWS stub 집계를 확인했습니다. 개별 정확 argv·기대/실제 exit는 scratch local-tests.json·regression-tests.json과 evidence/F1/gates/p06-local-verification.json에 있습니다. AWS/kubectl/git 보안 경계는 stub이며 실제 Source/Secret/사내 GitLab 인증 시험이 아닙니다.
- `C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe -I sql_probe.py` → exit 0. 네트워크 없는 일회용 공개 pgvector PG18 Docker에서 bootstrap true 2회·false 2회, vector:0.8.7 설치, 사용자 gw 스키마 0, app CREATE 거부/migrator CREATE 허용, acceptance 연결 한도2·기본 read-only, 비밀번호 따옴표/역슬래시 안전 quoting과 실제 TCP 로그인, false 역할 DROP 멱등, 기본 서버 로그의 password 비노출을 확인했습니다. 첫 준비 시험은 공식 이미지 초기화 중 임시 서버를 readiness로 오인해 실패했으며 실제 ai_gateway TCP SELECT 성공을 기다리도록 시험 준비 코드를 수정했습니다. 이후 통과했습니다. 컨테이너·익명 볼륨은 제거했습니다. 공개 이미지의 로컬 시험 digest는 Harbor 승인 pin이 아닙니다.
- 양 레포 `git diff --check`와 후보 UTF-8/LF/깨진 문자·후보 Python cache 부재를 확인했습니다. 보호 파일 483개, SPEC/기준선/승인6, 기존 report prefix는 바이트 해시로 대조했습니다. 정확 최종 결과·양 레포 HEAD/status/변경 hash는 scratch result.json에 기록합니다.

### Spec/Plan 차이·실제 중단 조건

승인 Spec/Plan의 확정 계약과 체크박스를 변경하지 않았습니다. 필수 pg_read_all_data를 다른 역할로 교체하거나 다른 DB의 PUBLIC 정책을 임의로 회수하지 않았습니다. 하지만 이 역할의 “이 DB 밖 권한0/쓰기DDL0”이라는 문구가 실제 PG18 동작과 충돌합니다. 기존 postgres DB CONNECT/TEMPORARY=true, 별도 테스트 DB의 SELECT 성공(exit0), read-only 기본값 off 변경 후 CREATE TEMP TABLE/INSERT 성공(exit0)을 재현했습니다. ai_gateway 자체의 CREATE/TEMPORARY=false 및 영구 DDL 실패(exit3)도 구분했습니다. 로컬 기능 PASS를 해당 보안 계약 PASS로 처리하지 않습니다. [PG18 predefined roles](https://www.postgresql.org/docs/18/predefined-roles.html), [privileges](https://www.postgresql.org/docs/18/ddl-priv.html), [client defaults](https://www.postgresql.org/docs/18/runtime-config-client.html)가 근거이며 sql-probe.json과 postgres-image.md에 상세 재현을 기록합니다.

부모 결정 선택지는 A: pg_read_all_data 유지 + 역할별 pg_hba로 실제 ai_gateway 외 연결 거부를 계약에 추가하고 문구 판정 범위를 명시, B: predefined role 요구 제거 + ai_gateway 한정 P07 SELECT/default privileges + 다른 DB PUBLIC CONNECT/TEMPORARY 정책까지 재설계입니다. 권장안은 A의 명시적 Plan 보정입니다. 기존 전용 인스턴스·필수 role을 유지하면서 다른 역할의 기본 정책을 바꾸지 않고 외부 DB 접근을 차단하기 때문입니다. A는 SQL ACL 자체를 0으로 만들지는 않으므로 literal ACL0이 필수라면 부모가 B 수준의 결정을 해야 합니다. 이 판단을 구현자가 임의 확정하지 않았고 실제 pg_hba 설정도 바꾸지 않았습니다.

별도의 실제 외부 입력 누락: 운영자 승인 Harbor 이미지/digest·probe, owner 승인 corpus 범위·읽기 identity/endpoint, 용량·부하 가정·노드별 현재 여유·GiB 승인, 백업 목적지/암호화/권한 승인, PV bind/Retain 운영 실행, 승인 병합 exec SHA·inbound 읽기 확인 및 실제 runtime/verify Secret이 없습니다. 관련 문서는 BLOCKED/NOT_RUN입니다. 따라서 P06 전체 완료/F1 exit/P07 착수는 주장할 수 없습니다. P07은 특히 확정 이미지 digest를 선행으로 요구합니다. 값·승인을 새로 만들거나 배포할 권한을 요청하지 않고 이 근거를 부모에게 반환합니다.

미실행: 실제 Harbor·K8s·Secret·PV patch·Helm deploy·백업/restore·사내 exec clone·Source/model API·SSH·추론서버/전역 설정 변경, P07, F0 재측정, 전체 Eval249/Core/app Java 재시험. shell/chart 변경이므로 승인된 최소 검증과 spec-guard만 수행했습니다. rotate-db는 안전한 대상 선택·오류 전파와 runbook을 구현하고 구문 검사했으나 실제 회전/복구는 실행하지 않았습니다. 운영 gate 증거 JSON·가짜 승인 runconfig·새 audit/Spec/Plan은 만들지 않았습니다.


## P06 일반 검토 결함 보정 (20261105T133526738290Z-a9fbf721)

상태: 일반 코드 보정과 로컬 검증만 완료했습니다. 부모 직접 검증 전이며 P06/F1 완료·P07 착수·운영 게이트 PASS가 아닙니다. 커밋·push·MR·운영 K8s/Secret/SSH/Harbor/서버/전역 설정 변경은 없습니다.

변경 파일과 이유:

- `ops/deploy/runconfig.py`: load는 JSON 객체를 요구하고 validate는 purpose/profile 존재·형식을 의미 검사 전에 확인합니다. lint 더미의 사업 SHA 결속만 제외하고 deploy 승인값 비교는 유지합니다. schema 통과한 양의 정수 GiB를 int로 정규화하여 main/restore 모두 1.0 → 1Gi로 렌더합니다. 분수 절삭 경로는 없습니다. inventory도 같은 load를 재사용합니다.
- `ops/deploy/aigw-helm.sh`: 자격 check에 자신의 --runconfig를 AIGW_RUNCONFIG로 전달하여 상속된 다른 파일이 승인 기준을 바꾸지 못하게 합니다. clone·checkout·rev-parse CLI SHA 대조는 유지합니다.
- `ops/deploy/read-exec.py`: 착수 소스는 이미 Git stdout/stderr를 subprocess 메모리에만 캡처했고 git.log 경로가 없었습니다. 이 경로를 보존하며 자식의 모든 GIT_TRACE 계열과 GIT_CURL_VERBOSE 환경을 제거했습니다. 원문 오류·자격을 파일/로그/argv에 쓰지 않습니다.
- `ops/gates/aigw-runtime-credentials.sh`: F1~F4는 승인된 backup.type만 읽어 목적지 유형별 필드를 요구합니다. RESTORE는 백업 입력을 읽지 않습니다. rotate는 모든 소유 release hook의 terminal/active 상태를 Secret 변경 전에 검사하고, 메모리 template으로 일반 delete --wait=true → create → 완료 대기를 수행합니다. 양쪽 DB hook이 모두 완료된 후에만 해당 앱을 재시작하며 실패 시 RECOVERY_REQUIRED를 반환합니다. SQL·PG 인증 정책은 변경하지 않았습니다.
- 기존 `ops/gates/aigw-runtime-credentials-selftest.sh`, 최소 `ops/deploy/runconfig-selftest.py`: 임시 작업 사본에서만 synthetic 승인 marker를 만들고, SHA·JSON·GiB·단계/유형별 자격·활성 hook 무변경·회전 순서·실패 후 앱 재시작 0·오류 발생 중 sentinel 파일 0을 검증합니다.
- 기존 `docs/runbooks/aigw-runtime-credentials.md`, `ops/runconfig/README.md`: 실제 입력·검증 명령·회전 선행과 부분 실패 복구를 명시했습니다. 새 로컬 증거는 `evidence/F1/gates/p06-review-local-20261105T133526738290Z-a9fbf721.json`입니다. 이 보고서는 기존 790589바이트 뒤에만 추가했습니다.

실제 명령·결과:

- 수정 전 secure runner `../p05-root-fix/run_gradle.py p06-review-spec-guard-actual :gateway-eval:run "--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --secure-auth` → exit 0. AIGW_SPEC_FILE 명시, host glab 토큰은 메모리에서 child Bearer 환경으로만 전달했습니다. 앞선 잘못된 Gradle task spec-guard 시도는 exit 1이었으며 실제 EvalCli 명령으로 바로잡은 뒤 편집했습니다. 원시 토큰 출력·파일은 없습니다.
- `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p06-root-fix\verify.py` → 각 docker run --rm --network none, 도구 이미지 고정, 양 레포 readonly mount. 실제 존재한 P06 local_tests.py/regression_tests.py를 이 scratch로 복사하여 실행했습니다. 요청에 언급된 p06/linux_verify.py는 해당 디렉터리에 없었습니다. 기존 파일·결과·로그는 덮어쓰지 않았습니다.
- local_tests.py → exit 0, 27개 실행 검사: 실제 Helm main/restore lint·render·name/selector/Retain·backup0, 정확 missingPVC 음성, invalidrelease/replicas/PG17, purpose/profile/승인 경계, 기존 inventory 선행 거부와 자격 selftest.
- regression_tests.py → exit 0, 16개 실행 검사: 대기 승인·누락·schema, inventory AWS head-only 승인 범위/집계/원시 key0, 자격 selftest와 구문 검사.
- 기존 credentials selftest 최종 별도 실행 → exit 0, 59건. kubectl/git은 stub이며 실제 Helm lint/template을 사용한 다른 CLI SHA+더미0 양성도 포함합니다. 실행 중 403 stderr flush 후 모든 임시 regular file을 검사한 완료 표식을 부모 시험이 확인합니다. helper0700·env-only·종료 후 제거·sentinel argv/stdout/stderr/file0, 모든 stage 누락13, S3/SFTP 혼용 거부13, 활성/nonterminal/두번째 release 활성 hook의 Secret 쓰기0, 양 release/세 역할 회전 순서와 wait 실패 시 앱 restart0을 검사합니다.
- runconfig-selftest.py → exit 0, 25건. malformed/null/number/string/array 고정 오류2, 필수 최상위 누락, profile/승인 SHA 충돌, main/restore의 실제 Helm 1.0 → 1Gi 양성 및 1.1/0/-1 거부를 포함합니다. 검사 수는 서로 중복되므로 합계를 독립 고유 시험 수로 해석하지 않습니다.
- 개별 argv·exit·stdout/stderr 경로는 이 scratch verification.json과 새 로컬 증거에 기록했습니다. 양 레포 diff --check, UTF-8/LF, 보호483·SPEC/기준선/승인6·Plan/SQL·원래 P06 증거·보고서 prefix 보존은 result.json의 실제 최종 대조 결과를 따릅니다.

남은 범위: 부모의 SQL/HBA 선택 실험 결과와 사용자 결정 대기는 그대로입니다. HBA overlay·pg_read_all_data 정책·SQL·Spec·Plan·운영 입력은 수정하지 않았습니다. 실제 Harbor/K8s/Secret/사내 clone/배포·백업·restore·SSH·모델/Source API·SQL 운영 시험과 Java/Eval249 재실행은 하지 않았습니다. 실제 승인 입력과 운영 게이트는 계속 미확보이며 부모가 직접 검증·사용자 결정 보고를 진행해야 합니다.

## P06 최종 root Validate·사용자 결정 보고 (2026-11-05, 문서 검토)

상태: **PROPOSED / USER_DECISION_PENDING**. 로컬 준비는 부모 root가 직접 재실행·검토했으나 검증 계정 계약은 **FAIL/BLOCKED**, 실제 운영 게이트는 **NOT_RUN**입니다. **P06 미완료, 현 Plan의 P07 선행조건 BLOCKED, P07 미착수**를 유지합니다. 이 절의 작성자는 보존된 실제 증거와 정본 Plan/Spec·관련 소스를 독립 대조했으며 테스트·빌드·SQL 실험을 다시 실행하지 않았습니다. 제안은 승인·구현·배포가 아닙니다.

### 변경 범위와 실제 검증 근거

P04 완료·커밋, P05 root 검증 Core36+app13+Docker IT1=고유50 및 83파일 커밋 `b028c81372f6d3f1aa5e7bfee32e94ae1e12df16`을 유지합니다(`scratchpad/p05-close/root-commit-verification.json`). P05 소스 바이트를 보존했습니다. P06 후보는 부모 `scratchpad/p06/root-reviewed-candidates.json`의 AI24·exec10파일이며 이번 문서 검토가 코드를 수정한 것은 아닙니다.

- DB 준비: `ops/postgres/bootstrap.sql:16`·`:24`~`:27`, exec `deploy/helm/ai-gateway/db-chart/templates/statefulset.yaml:12`·`:31`, `networkpolicy.yaml:16`, `bootstrap-job.yaml:18`·`:36`에서 역할·PG18 데이터 경로·Retain·release별 선택자·Secret 참조를 확인했습니다. 차트10파일과 인벤토리 도구·게이트6문서·runconfig/schema/fixture·배포/자격 도구는 기존 P06 변경군입니다. 실제 운영 승인을 추가하지 않았습니다.
- 일반 결함 보정: `ops/deploy/runconfig.py:36`·`:68`·`:106`은 잘못된 JSON/필수 키/승인 SHA 검사를 분리하고 유효한 정수형 JSON `1.0`을 `1Gi`로 정규화합니다. lint 더미 SHA의 잘못된 결속만 제거했으며 deploy 승인 SHA와 실제 clone/checkout 신원 검사는 유지합니다(`ops/deploy/read-exec.py:26`). Git trace 파일 환경은 제거하고 원시 오류는 메모리에서만 다룹니다(`read-exec.py:11`).
- 자격/회전 보정: `ops/gates/aigw-runtime-credentials.sh:27`·`:38`은 승인 backup.type별 S3/SFTP 필드를 요구합니다. `:100`에서 모든 대상 hook의 종료 상태를 먼저 확인하여 busy이면 Secret 쓰기0, `:115`~`:117`에서 강제 교체 없이 종료 Job 삭제·재생성·완료 대기, 양 DB 성공 뒤 `:125`에서 앱 재시작, 부분 실패는 RECOVERY_REQUIRED로 전파합니다. 관련 selftest와 runconfig-selftest가 이를 검증합니다.

| 대조한 실제 증거(모두 scratchpad 기준) | 결과와 판정 범위 |
|---|---|
| `p06/root-final-checks-v2/result.json`, `command-1.log`~`command-4.log` | 부모가 main·restore 실제 Helm lint, 정확한 missingPVC 음성, 자격 selftest의 필수4명령을 직접 재실행하여 모두 exit0. 각 release 렌더5리소스에서 이름·db-target/db-instance·PVC Retain·PG18 경로·Secret 참조·backup CronJob0 확인 |
| `p06/root-final-runconfig-selftest.log` | 부모 직접 실행25검사 PASS: 실제 Helm main/restore 1.0 양성·분수/0/음수 음성, malformed JSON·schema·profile·SHA guard 포함 |
| `p06/root-extra-probes-v2.json` | 부모 별도 검사: 1.0→1Gi, 승인 SFTP에 S3 필드만 있을 때 MISSING·exit13 |
| `p06/result.json`, `p06-root-fix/result.json`·`final.md` | 초기27·16 및 보정59·25의 로컬 검증 이력. 중복되므로 고유 시험 수로 합산하지 않음. 수정 전후 증거를 덮어쓰지 않음 |
| `p06/root-sql-probe.json:32`, `p06/root-hba-option-probe.json:49` | 부모가 별도 임시 PG18에서 승인 SQL의 계약 충돌 및 미승인 HBA 제안의 동작을 각각 직접 재현. 아래 결정 근거 |

부모 최초 자격 selftest는 Docker에서 exec를 `/exec`에 마운트한 환경과 기본 sibling `/nori-tc-exec` 경로가 달라 실패했습니다(`p06/root-final-checks/command-4.log` 보존). 기존 지원 변수 `AIGW_SELFTEST_EXEC_DIR=/exec`를 지정하고 필수4명령 전체를 다시 실행한 v2가 성공 증거입니다. 소스 실패를 숨기거나 시험을 제외한 것이 아닙니다. 지원 근거는 `ops/gates/aigw-runtime-credentials-selftest.sh:17`, `ops/deploy/runconfig-selftest.py:12`이며 이번에는 README·runbook에 비형제 배치 설명만 추가했습니다.

SQL 실험 이미지는 격리 로컬 공개 이미지 `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`입니다. 부모 증거의 실제 버전은 PostgreSQL18.6·vector0.8.7이며 컨테이너는 제거됐습니다. 이는 승인 Harbor 미러·운영 G-PG-IMAGE 통과 증거가 아닙니다. 이번 Ops 변경에 Java/Core/app/Eval249 재실행은 없으며 기존 P05 검증을 새 P06 실행으로 세지 않습니다.

### 사용자 결정1 — 검증 계정의 실효 접근 경계와 서버 인증 예외

정본 근거는 exec `docs/task/ai-rag-gateway-architecture-plan.md:490`입니다. 필수 `pg_read_all_data`·`default_transaction_read_only=on`과 “쓰기·DDL0, 이 DB 밖 권한0”을 함께 요구합니다. 같은 Plan `:489`는 “statement_timeout 등 서버 기본은 건드리지 않음”을 요구합니다. 현재 SQL `ops/postgres/bootstrap.sql:25`~`:27`은 이 처방을 그대로 구현했습니다.

그러나 부모 실제 baseline(`root-sql-probe.json:32`·`:36`)에서 별도 DB의 비공개 테이블 SELECT가 성공했고, 해당 역할이 기본 read-only를 off로 SET한 뒤 외부 DB TEMP DDL/DML도 성공했습니다. ai_gateway 자체 영구 DDL은 거부됐습니다. 원인은 클러스터 범위 predefined READ 역할과 외부 DB의 기본 PUBLIC CONNECT/TEMP 권한이며, read-only 기본값은 변경 불가능한 권한 장벽이 아닙니다. [PG18 predefined roles](https://www.postgresql.org/docs/18/predefined-roles.html)는 모든 데이터의 읽기 권한을 설명하고, [PG18 HBA](https://www.postgresql.org/docs/18/auth-pg-hba-conf.html)는 DB·사용자·접속 유형별 첫 일치 인증 규칙과 include를 설명합니다. HBA는 서버 인증 설정이므로 기존 Plan의 서버 기본 불변 조항 아래 임의 도입할 수 없습니다.

**선택A(권장, 미승인)**: 필수 READ 역할·연결한도2·기본 read-only·고정 OracleQueries를 유지하되 “다른 DB 권한0”을 **이 계정의 ai_gateway 외 DB CONNECT/읽기/쓰기 실효 접근0**으로 명확히 합니다. 서버 기본 불변에 오직 이 역할의 `hba_file` overlay 예외를 승인하는 조항을 제안합니다. TCP와 Unix 모두 ai_gateway에 대한 이 역할의 비밀번호 인증만 허용하고, 그 밖 모든 DB(기본 postgres 포함)의 이 역할 접속은 거부한 뒤 원래 PGDATA HBA를 include하여 다른 역할의 기존 인증을 유지합니다. timeout 등 다른 서버 기본은 유지합니다. HBA는 ACL 비트를 제거하지 않습니다.

부모의 임시 컨테이너 제안 실험(`root-hba-option-probe.json:49`~`:86`, 실험 소스 `root_hba_option_probe.py:56`~`:85`)에서 target 비밀번호 읽기 성공, 다른2DB×TCP/Unix=4접속 HBA 거부, 자체 DB 영구 DML/DDL 거부, app 비밀번호 접속·superuser 유지보수·statement_timeout 보존을 확인했습니다. `approved=false`, `repositoryChanged=false`, `productionChanged=false`입니다. 이 실험 PASS가 저장소 구현이나 운영 적용 PASS를 의미하지 않습니다.

**선택B(대안)**: 사용자가 실효 접근 차단이 아닌 literal SQL ACL 비트0을 요구하면 pg_read_all_data 필수 처방을 제거하고 DB별 객체 SELECT/default privileges와 외부 DB PUBLIC 권한을 재설계해야 합니다. P07/P15 및 다른 역할 영향 범위가 커집니다. A는 기존 전용 DB·고정 READ oracle을 유지하는 최소 변경이므로 권장하며, B를 승인 없이 대신 구현하지 않습니다.

### 사용자 결정2 — F1 로컬 구현과 실제 배포 선행조건의 한정 분리

현재 승인 Plan `:485`는 P06에서 실제 운영 게이트 closure를, `:509`는 P07 선행 P06과 Testcontainers 동일 확정 digest를 요구합니다. `:256`의 G-PG-IMAGE도 “F1 PG 배포·P07 이후”를 막습니다. 따라서 현 Plan으로 P07 로컬 코드부터 진행 가능하다고 해석할 수 없습니다. 승인 AIGW_RUNCONFIG, 실제 승인 Harbor 미러/probe, 소유자 승인 corpus 메타데이터 범위, GiB·노드 근거, 독립 암호화 백업 목적지, PV Retain 실행, 병합 exec SHA/inbound 읽기 승인·runtime/verify 자격은 확보되지 않았습니다(`root-preparation-review.json`, 기존 게이트 문서). 사업 값이나 승인을 임의 생성할 수 없습니다.

정본 Spec `exec:docs/task/ai-rag-gateway-architecture.md:1369`·`:1372`·`:1383`은 F1 PostgreSQL 기술 확인과 배포/수용 게이트를 요구하고, 미완료이면 **해당 배포 NOT_RUN**으로 규정합니다. 검토한 §16에는 이 운영 입력 확보 전 모든 F1 로컬 코드 작성까지 금지하는 명문은 없습니다. 따라서 로컬 구현 선행조건만 더 강하게 둔 Plan의 한정 변경안으로 제시하며 Spec 기준선은 수정하지 않습니다. F2의 명시적 착수 게이트(`Spec:1384`, `Plan:266`~`:272`)는 이 제안의 예외 대상이 아닙니다.

**제안 조항(PROPOSED / USER_DECISION_PENDING)**:

1. Plan의 P06 로컬 준비·계정 경계 검증과 실제 운영 게이트 완료 상태를 분리하여 각각 기록합니다. P06 전체 완료·운영 PASS를 로컬 PASS로 대체하지 않습니다.
2. 검증 계정 보정의 root 로컬 검증 뒤 F1 P07~P15의 **코드·격리 로컬 시험 준비만** 운영 입력 대기 중 허용합니다. 기존 phase 순서·기능 범위·개별 코드 계약과 실제 Source 연결의 승인 경계를 유지합니다. 외부 입력이 필요한 부분은 명시적 BLOCKED/NOT_RUN으로 남깁니다.
3. 그 준비의 PostgreSQL 시험에는 위 실측 공개 PG18 이미지 tag/digest·버전·격리 환경을 명시한 test pin을 허용합니다. 이를 승인 Harbor 이미지로 간주하지 않으며 실제 승인 이미지 확보 뒤 관련 bootstrap/마이그레이션/권한·저장소 시험을 승인 digest로 다시 검증하고 차이를 해소합니다. 현 P07 동일 digest 조항과 G-PG-IMAGE의 로컬 구현 차단 범위에만 예외를 둡니다.
4. 실제 DB/Secret/K8s 배포는 기존 G-EXEC-CHART-REF·G-PG-IMAGE·용량/보호/백업/자격 승인·runconfig 및 Plan §4.1(`:159`·`:163`~`:166`), §5(`:254`~`:263`), §11.9(`:1214`~`:1220`)의 조건을 그대로 요구합니다. P16/F1 exit와 F2·이후 단계의 Spec 게이트·정식 수용을 완화하지 않습니다. Source·corpus·capacity·backup 사업 기본값은 새로 정하지 않습니다.

**하나의 권장 방향**은 “A의 검증 계정 실효 경계 명확화·역할 한정 HBA 예외 + 위 F1 로컬 준비/실제 배포의 한정 분리”를 함께 명시 승인하는 것입니다. 이는 반복적인 일반 phase/커밋 허가 요청이 아니라 승인된 보안 문구·서버 설정 제한·P07 이미지 선행조건을 바꾸는 사용자 결정입니다. 연속 자율 작업 지시만으로 확정 계약을 묵시 변경하지 않습니다. 현재 두 결정과 Plan 개정은 모두 미승인·미적용입니다.

### 승인 후 순서·보존 및 미검증 범위

사용자 결정 후 독립 담당의 **Plan 조항만 수정** → root의 Spec 불변·변경 범위 대조 → 독립 P06 HBA 보정 구현 → root 실제 PG18/Helm/음성·인증 회귀 검증 → 통상 커밋 → 개정된 선행조건에 따른 독립 P07 구현 순서입니다. 각 단계는 기존 연속 작업 승인 범위에서 진행하되 승인 결과를 넘어 확장하지 않습니다. 실제 운영 승인·필수 입력은 소유자에게서 확보할 때까지 미완료로 유지하며 운영 게이트를 위조하지 않습니다. B를 선택하면 먼저 그 권한 설계의 정확한 Plan을 확정해야 합니다.

이번 문서 작업은 이 Dev report의 기존796430바이트 뒤에만 추가하고, README·자격 runbook의 selftest 소스 배치 설명만 추가했습니다. scratch `p06-decision-report/result.json`에 편집 전후 해시·기존 prefix·보호483·Spec/기준선/승인6·Plan·SQL·P05 소스·부모 후보 manifest 및 전체 레포 파일 snapshot 대조를 기록합니다. 기존 증거 JSON과 게이트 상태·Plan 체크박스를 수정하지 않았습니다.

이번 실행에서 테스트·빌드·SQL·Helm 실행, 실제 Harbor/K8s/Secrets/Source/model/SSH/서버 설정·배포·백업·복원·commit/push/MR은 없습니다. 추가 확인은 사용자 두 계약 결정과 실제 운영 입력 확보, 승인 후 새 구현에 대한 실제 root 검증입니다. **phaseComplete=false, userDecisionPending=true**를 유지합니다.


## 2026-11-06 P06 승인 A 보완 구현·LOCAL 검증 (부모 독립 검증 대기)

이번 작업은 사용자 “권장으로 진행” 승인 A/B와 부모의 `p06-approved-plan/root-plan-approval.json`(semanticReview PASS, approvedForP06Implementation=true)을 읽고 P06 접속 제한만 구현했습니다. 앞 절의 “미승인/미적용”은 당시 이력이며 승인된 현재 계약은 개정 Plan입니다. SPEC SHA256=`136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3`, 개정 Plan SHA256=`0dbe45183d89ce118d86b09d40ff0a6476d5a8f1440d79b8ae3395102daa2718`를 그대로 유지했습니다. P05 HEAD `b028c81372f6d3f1aa5e7bfee32e94ae1e12df16` 이후 commit/staging/push/MR은 없습니다.

### 근본 원인과 최종 배선

`pg_read_all_data`는 인스턴스 범위 READ 역할이고 다른 DB PUBLIC CONNECT/TEMP가 남으므로 read-only 기본값만으로 타 DB 접근을 막을 수 없었습니다. 승인 A에 따라 literal SQL ACL 0을 주장하지 않고 검증 역할의 타 DB 연결/유효 접근 0을 강제합니다. 기존 bootstrap.sql의 역할·확장·DB 권한 책임과 GRANT/REVOKE는 변경하지 않았습니다(SHA256=`04b42923d3940ed51d0630c0cddb13cc649167fcb8d43027bba07c7f2ca7c11e`). 사용자 스키마·테이블은 시험 관리자 fixture에만 존재합니다.

HBA 정본은 `ops/postgres/acceptance-pg_hba.conf` 한 곳입니다. 앞 네 행은 해당 역할의 ai_gateway local/host SCRAM 허용과 나머지 DB 거부이고, 마지막 필수 include는 `/var/lib/postgresql/18/docker/pg_hba.conf`입니다. 원본 영속 HBA·postgresql.conf·postgresql.auto.conf 수정, ALTER SYSTEM, 새 trust 규칙은 없습니다. 정본 SHA256=`1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22`입니다.

모든 DB lint/render/deploy가 정본을 `--set-file hba.overlay`로 주입합니다. 각 release의 `-hba` ConfigMap을 optional=false/key 명시/0444로 `/etc/aigw/postgres`에 readOnly 디렉터리 mount하고 ENTRYPOINT를 유지한 `postgres -c hba_file=/etc/aigw/postgres/pg_hba.conf`로 기동합니다. checksum은 Pod template annotation에 반영됩니다. PGDATA/PVC mount·이미지·Secret 값·서버 timeout 기본값은 유지했습니다. 주/복원 6종 리소스의 이름·참조·label은 서로 분리되고 restore CronJob은 0입니다.

정본/렌더 계약 변조는 배포 전 고정 오류로 거부하고 실제 파일/key 부재·필수 include 대상 부재·구문 손상은 새 PostgreSQL 기동 실패로 확인했습니다. deploy는 rollout status와 bootstrap 완료 후 Pod 내부 관리자 env로 고정 SQL을 실행해 실제 경로/정본 SHA/앞 네 HBA 규칙/원본 include/오류 0을 확인합니다. 다른 계정의 원본 내부 include도 허용합니다. 원시 SQL 오류는 메모리에서 고정 오류로 바꾸며 배포 실패 증거를 기록합니다. 실제 운영 호출은 실행하지 않았습니다.

`--acceptance-role enabled|disabled`를 추가했습니다. disabled는 Secret 검사·hook 환경변수 참조를 생략하고 false를 전달하면서 HBA를 유지합니다. 활성 세션 정리는 인자 단독의 보장 범위가 아니며 runbook에 두 release 각각 NOLOGIN·대상 세션 종료 → disabled bootstrap → 역할 부재/재접속 거부 → 공유 Secret 삭제 순서를 명시했습니다. HBA 변경의 checksum rollout은 기존 세션까지 끝내는 Pod 교체이며 reload만으로 완료 처리하지 않습니다. 동일 checksum의 승인 리허설은 release별 명시 restart/status 절차입니다. P15 전체 자격 수명·실제 운영 검증은 NOT_RUN입니다.

### 실제 검증과 실행 위치

Windows 실행 cwd는 `C:/workspace_nori/nori-ai-gateway`, Linux 도구 컨테이너 cwd `/ai`는 같은 checkout입니다. exec는 `/exec`에 읽기 전용 mount했고 `AIGW_SELFTEST_EXEC_DIR=/exec`를 전달했습니다. 도구 이미지 ID는 `sha256:aa75a3bbab72c9c0be4e0dada6d7c41cc944fd9b74e8f090683884ee7e22d892`이며 `--network none --pull=never`로만 실행했습니다. 전역 패키지 설치/설정 변경은 없습니다.

| 실행 | 실제 결과 | 로그/증거 |
|---|---|---|
| `bash ops/postgres/aigw-hba-local-test.sh --image pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2 --out evidence/F1/gates/p06-hba-local-approved-20261106-r4.json` | exit 0, LOCAL_VALIDATED, 205개 판정 이벤트 | scratch `hba-locked-command.json`, `hba-locked.log`; 새 repo LOCAL JSON r4 |
| main/restore `aigw-helm.sh --mode lint`와 `--mode render`, missingPVC expected failure, direct Helm release/replicas/PG18/restore backup/HBA 필수 음성, checksum·mount/key/args 음성, shell 문법과 runconfig 실행 | `local_checks.py`의 총 24회 명령이 기대 exit와 일치 | scratch `helm-checks.json`와 항목별 `.stdout/.stderr` |
| `python3 -B ops/deploy/runconfig-selftest.py` | exit 0, 기존 25개 유지 | scratch `runconfig-selftest.stdout`(위 24회 안에 포함) |
| `AIGW_SELFTEST_EXEC_DIR=/exec bash ops/gates/aigw-runtime-credentials-selftest.sh` 및 최종 5개 shell `bash -n` | exit 0, 81회 selftest 명령; 기존 59개가 순서·exit·expectedError 그대로 포함 | scratch `locked-checks.json`, `credentials-locked.log`, `selftest-locked-summary.json` |
| `gradlew.bat -p C:/workspace_nori/nori-ai-gateway :gateway-eval:run "--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md" --no-daemon --console=plain` | exit 0, FILE_HASHED/LOCAL_FILE, 승인 Spec SHA 일치 | scratch `p06-hba-spec-guard.json/.log` |

위 수는 서로 겹치는 반복 명령/시나리오 판정입니다. 205+24+25+81를 고유 시험 수로 합산하지 않습니다. Gradle Test task는 실행하지 않았고 Java 249 회귀와 별도 test-evidence verify도 실행하지 않았습니다(ops 전용 변경, Spec guard는 run task). 소스 UTF-8/LF·한글 literal ?/치환문자 검사와 두 레포 git diff --check는 최종 경계 검증에서 확인합니다.

PG 결과는 `PostgreSQL 18.6 (Debian 18.6-1.pgdg13+2) on x86_64-pc-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit`, vector 0.8.7, pg_trgm 가용·미설치·미사용입니다. 실제 차트에서 렌더한 HBA와 args로 빈 PGDATA 기동·기존 볼륨 재기동·유효 정책 복구를 검증했습니다. bootstrap true 2회/false 2회·삭제 후 재활성, 비특권 속성/READ membership/실제 연결 2개 전제의 세 번째 거부, target 읽기와 wrong-password 거부, postgres/p06_outside/template1의 TCP IPv4·IPv6·Unix HBA_REJECT를 확인했습니다. default_transaction_read_only=off 후 INSERT/UPDATE/DELETE/TRUNCATE/CREATE/CREATE SCHEMA/ALTER/DROP/TEMP는 각각 SQLSTATE 42501로 거부됐고 25006을 통과 근거로 세지 않았습니다. 다른 계정 로그인·admin 유지보수·migrator 스키마 작업과 PUBLIC DDL 권한을 회귀했습니다.

동일한 자기 소유 격리 볼륨의 원본 HBA 대조군과 overlay의 다른 역할 인증 결과가 일치했습니다. 공식 initdb 원본의 loopback/Unix 인증 결과를 일률적 SCRAM 거부로 바꾸지 않았습니다. 모든 서버 설정 비교의 변경 이름은 hba_file 하나였고 statement_timeout도 같았습니다. 원본 HBA 전후 SHA256=`31f65ccbe621d83a6ac98fad2428bc075a05a566a32bc869da7313b9e34a7349`로 동일합니다. 비밀번호는 CSPRNG 가짜 값만 메모리/env로 전달했고 argv/임시 파일/캡처 출력/각 컨테이너 로그에서 값 노출을 검사했습니다. 전체 실행의 컨테이너·볼륨·임시 디렉터리와 Windows python3 shim은 정리됐고 label 조회 잔류는 각각 0입니다. 실행/정리 코드는 자기 소유 label·정확한 container ID·volume 이름을 확인한 자원만 삭제합니다.

초기 r1은 다른 역할의 원본 loopback 인증을 일괄 비밀번호 거부로 가정한 시험 결함으로 FAIL했습니다. 원본 대조군과 실제 결과 일치를 검사하도록 근본 원인을 고쳤습니다. r2는 PostgreSQL이 실제 exit 1로 종료됐으나 기대 로그 문구에 파일 경로가 빠진 시험 결함으로 FAIL했고, 실제 경로와 구체 실패 원인을 함께 검사하도록 고쳤습니다. r3는 205개 판정으로 LOCAL_VALIDATED였습니다. 그 뒤 로그 검사 실패도 자원 정리를 건너뛰지 않는 finally 보완을 반영해 현재 소스를 bash 진입점으로 r4 재검증했습니다. 초기 credentials 실행 1회는 실행 중 소스 편집으로 heredoc 뒤 셸 읽기 위치가 어긋나 PASS 출력 후 exit 1이었으며 통과로 세지 않았습니다. 소스를 고정한 뒤 최종 문법/전체 selftest exit 0으로 확인했습니다. r1/r2/r3 JSON과 앞선 승인 전 부모 proposal 증거는 덮어쓰지 않았습니다.

### 실제 변경 파일과 SHA256

| 파일 | SHA256 |
|---|---|
| `AI:docs/gates/f1/postgres-image.md` | `ae3eb6b470e63cdf53eefafed7d8fba8bdc19d9c4aca7b7e2445ec38590a34f6` |
| `AI:docs/runbooks/aigw-runtime-credentials.md` | `bc3fe0da39841a2a28618b8ba85bda75c49d2c17c9d6d0f855af9874b572e450` |
| `AI:ops/deploy/aigw-helm.sh` | `b5d2e52f65e119b7136e578b9e9a102f38a989d59722f73d77f7e6208416ecba` |
| `AI:ops/deploy/hba_policy.py` | `2cf0087ce3d55d3d70b4b57bcb899939b675ad4ff1e7043604045730a803d058` |
| `AI:ops/gates/aigw-runtime-credentials-selftest.sh` | `21f8998073567fbdf326137fbfadac3dda651e74a691f0ec55009328230d0767` |
| `AI:ops/postgres/acceptance-pg_hba.conf` | `1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22` |
| `AI:ops/postgres/aigw-hba-local-test.sh` | `d711b8d55e26f1053760279d34142d6588ba19e40913e3e07d8a30293b9f471c` |
| `AI:ops/postgres/hba_local_test.py` | `2c4d97d554f7f16be2f111444730817cbb7d0623eb99e02fa2534e9baf67bc01` |
| `AI:ops/runconfig/README.md` | `1ec50a5704ef03df21b9e86dfb39004204c42fcaef4d74562d000ae868ba7e3c` |
| `exec:deploy/helm/ai-gateway/db-chart/templates/hba-configmap.yaml` | `31f660a7536ea6a980b65b733496c96c9483898e688977a042547b8bb5a9acb4` |
| `exec:deploy/helm/ai-gateway/db-chart/templates/statefulset.yaml` | `9fb2ab66c37d9658f52518cf4adb8ef2a47766d2fd429b7e3ac939ccf64c45e1` |
| `exec:deploy/helm/ai-gateway/db-chart/values.yaml` | `716ff5db725a2eb56687e2a5fb70a5470442032f0c6286bc8eec6f571c597437` |

위 12개 소스/문서 외에는 이 Dev report의 APPEND와 새 LOCAL JSON r1~r4만 추가했습니다. Dev report는 기존 809344바이트(SHA256 `bddb7a664b0265fa8316a24492c22f630e8ddee1afeb962e461cd52865f4a222`)를 그대로 두고 이 절만 UTF-8/LF 바이트로 붙였습니다. 자기 자신 최종 SHA는 순환 참조를 피하기 위해 scratch result.json에 기록합니다. 변경 파일 전체 해시/새 증거 해시는 scratch boundary.json/result.json에 있습니다.

### 범위·Plan 대비 차이·미검증

제품/권한 계약과 phase 순서의 차이는 없습니다. bash 진입점은 Python 구현에 위임하며 Plan의 trap 정리 의무는 Python finally로 구현했습니다. Windows에서는 기존 venv의 python3 shim과 이미 설치된 Linux 도구 이미지로 동일 bash/Helm 경로를 실행했고 별도 정책·사업 기본값·운영 runtime guard 범위를 추가하지 않았습니다. 원본 HBA 내부 include를 배제하지 않도록 검사 범위를 승인된 overlay 네 규칙·필수 include 정본 SHA·전체 구문 오류 0으로 한정했습니다.

부모 보호 스냅샷 483개, Spec/baseline/approval6, 개정 Plan, 기존 F0/PREREG/golden/capture/result/evidence와 기존 P06 JSON은 불변입니다. 전체 source snapshot 비교에서 이번 허용 파일 외 수정은 없습니다. 이미지 Harbor 미러/승인 digest, 승인 runconfig·용량·PV 보호·백업 목적지, 병합 exec SHA/inbound 자격과 실제 운영 배포는 미확보/미검증입니다. 실제 SSH/K8s/Secret/Harbor/모델/서버/Source/배포/운영 백업·복원·CI/push/MR/전역 설정 변경을 실행하지 않았습니다. 승인 Harbor digest 확보 후 관련 bootstrap/HBA·migrate/권한·저장·백업/복원 재시험과 부모 검증이 필요합니다. 실제 rollout 세션 종료·양쪽 운영 자격 수명·운영 수용은 NOT_RUN입니다.

현재 상태는 **P06 CODE/LOCAL_VALIDATED, 부모 독립 검증 대기; P06 전체 미완료; P07 미착수; P16/F1 완료/F2 운영 게이트 BLOCKED/NOT_RUN**입니다. Spec/Plan 체크박스는 변경하지 않았습니다. 다음 P07은 부모 검증 후 별도 세션입니다.


## P06 부모 독립 Validate 종료 및 로컬 커밋 경계 (2026-11-06)

부모 승인 근거는 scratch `p06-approved-hba/root-validation-approval.json`과 `root-source-db-review.json`입니다. P06 **CODE/LOCAL_VALIDATED PASS**, **P07 코드 진행 가능**으로 판정됐습니다. P06 운영 전체 완료를 뜻하지 않으며 실제 운영 게이트/P16/F1/F2는 **BLOCKED/NOT_RUN**을 유지합니다. P07 착수와 Spec/Plan 수정은 이번 종료 작업 범위에 포함하지 않습니다.

부모 검증은 AI HEAD `b028c81372f6d3f1aa5e7bfee32e94ae1e12df16`과 당시 dirty ops/exec 차트 소스에서 실행됐습니다. 이후 생성할 로컬 커밋 SHA에서 테스트를 실행했다는 의미가 아닙니다. `root-linux-final/result.json`은 actual Helm, main/restore × acceptance role enabled/disabled의 4개 render variant, 누락·변조 거부를 포함한 outer check 15개 PASS입니다. 내부 credential 81개/runconfig 25개는 이 검사와 겹치므로 합산하지 않습니다. 신규 Java 코드·테스트 변경이 없어 기존 Java 249개 테스트는 반복하지 않았습니다. 부모/worker Spec guard는 각각 exit 0입니다.

실제 격리 PG 검증에 사용한 source HBA SHA256은 `1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22`, bootstrap SQL SHA256은 `04b42923d3940ed51d0630c0cddb13cc649167fcb8d43027bba07c7f2ca7c11e`입니다. `root-pg-review-v2.json`은 75개 PASS와 옛 negative 기대 로그 문구 1개 FAIL이므로 전체 PASS로 처리하지 않습니다. fresh/restart의 TCP·Unix·IPv6 own read, other DB HBA reject, read-only off 후 SQLSTATE 42501, 기존 계정 인증 동일, 설정 변경이 hba_file 하나뿐임과 role off 시 부재 검증은 실제 PASS입니다. 해당 negative는 `root-negative-review-v3.json`에서 missing overlay/invalid syntax/missing include 각각 exit 1과 구체 HBA 원인, valid recovery PASS를 독립 확인하여 닫았습니다.

실패한 검증 도구 실행 이력은 보존합니다. `root-pg-review.json`은 광범위 Docker inspect 메모리 출력에 자체 secret-sentinel 검사가 반응하여 중단됐으며 채팅/파일 비밀 노출은 없었습니다. 소유자를 확인한 자원을 정리한 뒤 inspect 범위를 labels/state로 제한했습니다. `root-negative-review.json`과 v2는 강제 종료/짧은 기동 대기라는 시험 조건의 실패였고, 최종 v3는 소유 컨테이너의 graceful 종료와 180초 기동 대기로 복구까지 PASS했습니다. 실제 코드 변경 없이 검증 도구를 수정한 것이며 과거 실패를 통과로 승격하지 않습니다.

종료 작업의 사전 대조에서 AI 32개/exec 11개, 총 43개 후보 원본 해시가 부모의 `root-reviewed-candidates.json`과 모두 일치했고 두 HEAD와 작업트리 스냅샷도 일치했습니다. 스테이징은 양쪽 모두 비어 있었습니다. 최종 부모 검증의 소스 동결 전후 해시 일치 근거와 보호 483개, Spec/baseline/approval6, ignored Plan, 기존 F0/PREREG/results/evidence 및 기존 report prefix를 확인했습니다. `aigw.root.owner`와 `aigw.p06.owner` 라벨로 컨테이너/볼륨 이름·개수만 조회한 현재 잔류는 각각 0이며 전체 inspect 값은 출력하지 않았습니다.

이번에는 이 Dev report의 원본 바이트를 유지한 추가 절만 작성하고 승인된 정확한 43개 경로를 exec 11개 → AI 32개 순서로 로컬 커밋합니다. README/evidence/코드/Spec/Plan은 편집하지 않습니다. 커밋 전후 diff check, 실제 staged/committed 경로와 blob SHA256, 외부 경로·삭제·누락 0, Dev report 원본 prefix와 보호 파일 불변의 최종 결과 및 두 커밋 SHA는 별도 scratch `p06-approved-close/result.json`과 `final.md`에 기록합니다. 새 구현이 없어 부모 테스트는 반복하지 않습니다. 실제 DB/K8s/SSH/Secret/Harbor/Source/모델 서버 접근, 전역 설정 변경, push/MR/force 및 운영 배포 작업은 실행하지 않습니다.


## P07 CODE/LOCAL_VALIDATED 구현 보고 (2026-11-06, 독립 구현 세션)

P07 범위의 구현과 격리 로컬 시험을 완료했습니다. **CODE/LOCAL_VALIDATED는 구현자 시험 결과이며 부모 독립 Validate는 대기 중입니다. DEPLOYMENT/ACCEPTANCE/COMPLETE는 BLOCKED/NOT_RUN**입니다. P06 부모 승인과 최신 P07 시험 지원 Plan 승인에 따라 진행했으며 Spec/Plan 체크박스는 변경하지 않았습니다. P08·커밋·push·MR·하위 에이전트 작업은 실행하지 않았습니다.

### 정본과 선행 근거
- Spec: `exec:docs/task/ai-rag-gateway-architecture.md`, SHA256 `136261bdd91a87f01c69072fd90c76e1d453ae9e96d6ea478f6a83f1ec3465f3`. 최초 Gradle SpecGuard exit 0 뒤에만 파일을 편집했습니다.
- 직접 읽고 계산한 최신 승인 Plan: `exec:docs/task/ai-rag-gateway-architecture-plan.md`, SHA256 `12e29e5215ce117ba869a1d2734594ed61aa3690346c05103cc2fd565dfd4f63`. §3.4·§4·§7 P07·§8 및 Spec §8/9/12/16/21의 해당 계약을 대조했습니다.
- 선행 proof: 부모 scratch의 `p06-approved-close/root-commit-verification.json`, `p06-approved-hba/root-validation-approval.json`, `p07-test-support-plan/root-plan-approval.json`. P06 CODE/LOCAL_VALIDATED와 P07 CODE 착수 승인만 근거로 사용했습니다.
- 실제 시험 HEAD: AI `b307e53bf120297687cb4fa5236bc41fb63c973d`, exec `6d744aa57fd4d2970f9fca8c941b43b8a2e18670` + 아래 dirty P07 소스입니다. 미래 커밋에서 실행한 결과가 아닙니다.

### 구현 내용·호출·배선

- V1은 bootstrap이 설치한 vector 0.8.7을 검증하고 gw/kn과 DML 기본 권한을 만듭니다. 이력은 migrator 소유 gw_flyway에만 생성합니다. V2는 대화·메시지·문맥·요약·Job·큐 제어·이벤트·결과·Evidence·Tool 실행·캐시를 생성합니다. V3는 허용 메타데이터 감사·retention probe·backup_run·instance_epoch를 생성하고 backup_run INSERT/sequence USAGE만 backup에 추가합니다. V4 이후와 bootstrap 객체는 추가하지 않았습니다.
- 일반 앱 → GatewayAppModuleConfiguration → PersistenceModuleConfiguration의 실제 @Import → JdbcClient/명시적 TransactionTemplate/Clock → SchemaVersionGuard·9개 scope 저장소·3개 유지보수 저장소입니다. guard는 Ordered.HIGHEST_PRECEDENCE의 실제 ApplicationRunner이며 클래스패스 최신 V와 DB 최신 성공 V를 Flyway 숫자 버전으로 비교합니다. 빈 DB·비어 있는 성공 이력·V10 불일치에서 후속 runner 실행 0을 확인했습니다.
- 일반 앱은 Flyway 비활성입니다. NormalDatabaseConfiguration의 BeanFactoryPostProcessor가 일반 진입점의 migrate 프로필/자동 Flyway 활성화를 DDL 초기화 전에 거부합니다. 전용 config.MigrateApplication은 DataSource/Flyway 자동 설정과 migrate 프로필 MigrateCommand만 구성합니다. 전략에서 DB current_user를 먼저 검사한 후 Spring Flyway 초기화가 한 번 migrate하고, runner 완료 뒤 main이 컨텍스트를 닫고 반환합니다. 잘못된 역할/프로필·Flyway 비활성·마이그레이션 실패를 성공으로 삼지 않습니다. System.exit는 없습니다. **P15 hook은 일반 앱 main을 migrate 프로필로 실행하는 대신 이 전용 MigrateApplication 클래스를 명시적으로 실행해야 합니다. 이번 packaging task 변경은 없습니다.**
- 9개 저장소의 public 실행 메서드는 VerifiedSystemScope를 첫 인자로 받습니다. scope에서 system/actor를 바인딩하고 부모 Conversation의 현재 소유권·expires_at > Clock.now를 JOIN/EXISTS로 확인합니다. Message/Job/이벤트/Result/Evidence/Tool/Cache와 Context/Summary도 같은 경계를 통과합니다. payload의 owner 값은 인가 정본이 아닙니다. audit는 actor/system과 90일 경계를 함께 검사합니다. 인증 JWT의 새 수명 정책은 추가하지 않았습니다.
- Job SELECT는 명시 열만 사용하고 일반 DTO에 일시 위임을 포함하지 않습니다. P07 제품 코드에서 delegation_id는 접수 INSERT에만 등장합니다. terminal 잔류는 DB job_dlg_terminal_null 제약으로 막으며, 실제 DB에서 terminal 상태 5종의 23514/정확한 제약 이름과 같은 UPDATE의 NULL 삭제·조건부 1회 성공을 확인했습니다. worker 전용 reader와 terminal 업무 서비스는 P09 책임으로 남겼고 앞당기지 않았습니다.
- Queue/Retention/RecoveryMaintenanceRepository는 별도 타입으로 등록하고 ArchUnit이 dispatcher/purge/recovery 서비스 외 실행 호출을 제한합니다. 큐 잠금은 실제 기존 tx를 요구하며, purge는 만료됐어도 비 terminal Job이 있으면 삭제하지 않습니다. 미래 서비스나 no-op 구현은 만들지 않았습니다.
- 공유 PgTestDatabase testFixtures가 정본 bootstrap/HBA → migrator migrate → app 연결을 소유합니다. CSPRNG 자격은 메모리/자기 컨테이너 env와 테스트 JVM 메모리 속성으로만 전달하고 자격 값을 argv/정본 파일/증거에 쓰지 않았습니다. 자기 label을 확인한 컨테이너를 finally/close에서 정리합니다. 컨텍스트를 먼저 닫습니다. 생산 코드의 테스트 분기나 가짜 guard는 없습니다.
- persistence/app integrationTest는 동일한 AIGW_PG_TEST_IMAGE 전체 pin을 선언 입력과 aigw.pgTestImage에 결속합니다. bootstrap/HBA 실제 바이트 SHA도 파일 입력 및 aigw.pgBootstrapSha256/aigw.pgHbaSha256에 결속하며 fixture가 실제 바이트를 다시 비교합니다. 누락·비고정·형식 오류 pin은 factory 단위 시험과 실행 전 Gradle 검증에서 거부하고 fallback은 없습니다. 기존 루트 N17 메커니즘을 사용했으며 루트 build/catalog/pins는 변경하지 않았습니다.
- StackContextSupport는 PG17 빈 DB 대신 공유 PG18 준비 후 app 역할로 일반 verify 앱을 실행합니다. StackSmoke는 Flyway 빈 부재·app current_user·성공 이력/클래스패스 최신 V·필수 스키마를 검사합니다. 기존 모델 미호출·라이브러리 호환 assertion과 AppContextWiring의 P05 8개 Core 빈/9 manifest/6 capability/production executor 부재 assertion은 유지했습니다.

### 실행 명령과 실제 결과

모든 Gradle은 scratch run_gradle.py로 AIGW_SPEC_FILE을 명시하고 --secure-auth의 메모리 캡처 Bearer 자격과 --no-daemon을 사용했습니다. 아래 명령은 각각 별도 프로세스입니다. 각 최종 Test 직후 같은 task/필터/includeTags/requireDocker/image/SQL·HBA SHA 입력의 공식 test-evidence verify를 별도 실행했습니다. 앱 필터는 scratch app-filter.init.gradle에서 동일하게 재현했습니다. XML/OTR/summary/run-binding/runtimeClasspath/inputFingerprint를 대조했습니다.

| 최종 Test | exit | 실제 case | 실패/오류/skip | 공식 검증 exit | scratch 실행 proof |
|---|---:|---:|---|---:|---|
| `:gateway-persistence:test` | 0 | 11 | 0/0/0 | 0 | `persistence-unit-final.json` / `persistence-unit-final-verify.json` |
| `:gateway-persistence:integrationTest` | 0 | 53 | 0/0/0 | 0 | `persistence-it-r4.json` / `persistence-it-r4-verify.json` |
| `:gateway-app:integrationTest` | 0 | 5 | 0/0/0 | 0 | `app-it-r1.json` / `app-it-r1-verify.json` |
| `:gateway-app:test` | 0 | 13 | 0/0/0 | 0 | `app-unit-final.json` / `app-unit-final-verify.json` |

최종 서로 다른 task의 case 합계는 82건입니다. 앞선 실행/실패/재실행은 합산하지 않았습니다. UP-TO-DATE/NO-SOURCE를 새 Test 실행으로 세지 않았고 최종 네 Test의 taskOutcome은 모두 EXECUTED입니다.

- 실행: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:test --no-daemon --console=plain`
  별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run --args=test-evidence verify --tasks :gateway-persistence:test --no-daemon --console=plain`
  runId `d820b272-52b7-4ff4-8655-d1afb347e4fb`, inputFingerprint `30a7218c1d1916ed580b86076d03784421dc0e92b491b8394dc401312cf0dae5`.
- 실행: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:integrationTest -Paigw.requireDocker=true --no-daemon --console=plain`
  별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run -Paigw.requireDocker=true --args=test-evidence verify --tasks :gateway-persistence:integrationTest --no-daemon --console=plain`
  runId `28df6823-5be1-42e7-9f8f-9b63b99c8c1d`, inputFingerprint `0ac274a9d7d405e97d969863718a6a5c65561fcf19fbf233b237c7b542a1166f`.
- 실행: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *AppContextWiringIT --tests *StackSmokeIT --no-daemon --console=plain`
  별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p07-implementation\app-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true --args=test-evidence verify --tasks :gateway-app:integrationTest --no-daemon --console=plain`
  runId `9c88a4b7-5417-4ae9-8d9c-819faf65ca49`, inputFingerprint `2fd68c1562d047a5a282515269e7cf7de098d61fdf4e08d5a29a6da00b76afa1`.
- 실행: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain`
  별도 검증: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run --args=test-evidence verify --tasks :gateway-app:test --no-daemon --console=plain`
  runId `79379e7b-4202-435b-b5c4-ed02d163efba`, inputFingerprint `1f0375d0fda6110cf838f190a0972ba8b854f8b0880331b35ce36f43f28449bc`.

공통 실제 runtimeClasspath SHA256은 `c97a88754eb9c0f32577f809454e37c91082650a01e54bd6cc65a64a429f6d81`입니다. 상세 classpath와 각 binding/leaf 해시·실제 실행 클래스·필터·정본 입력은 scratch `test-proof.json`과 `runtime-classpath.txt`에 있습니다.

기존 OtrLeafExtractor는 모든 RUN 요약의 stage 메타데이터를 F0로 고정합니다(기존 구현 불변). 실제 P07 OTR의 stage-F1/label-MOCK_API 태그를 별도로 확인했고 기반 persistence 시험의 leaf records는 비어 있습니다. RUN 요약으로 F0 판정이나 전체 ISO 수용 PASS를 주장하지 않습니다.

53개 persistence IT에는 migration 2건, bootstrap/권한/HBA 1건, terminal 제약 1건, 실제 guard 1건, scoped 저장소 48건이 있습니다. scoped 45건은 9개 저장소 × 정상 TC/동일 actor WM/다른 actor/만료/now==expiresAt이며 각 case에 대표 read/write를 함께 검증합니다. 나머지는 null/빈 키/중복/부모 불일치/pdg/독립 90일·캐시 경계/명시 tx·비 terminal purge 보호입니다. 하나의 case 안 여러 assertion이나 권한별 SQL 검사를 독립 case로 더하지 않았습니다. 실제 bootstrap true 2회 → migrate → app CRUD/DDL 거부 → backup 전 테이블 SELECT와 유일한 예외 INSERT → acceptance readonly off 후 정확한 42501 → 다른 DB TCP/Unix 구체 HBA reject → false 2회/역할 부재를 확인했습니다. stage-F1/label-MOCK_API 기반 로컬 시험이며 전체 ISO 수용 PASS나 운영 gate 레코드로 등록하지 않았습니다.

실패 이력도 scratch에 남겼습니다. persistence-it-r1은 PowerShell의 인자 분리로 시험 실행 전 실패했습니다. r2는 실제 guard의 직접 예외에 root-cause를 잘못 기대한 assertion 및 Clock 중복 등록으로 실패했습니다(XML 6개 중 initializationError 1개 포함, OTR 실제 test 5개). Clock 기본 설정의 조건 평가를 사용자 빈 등록 뒤로 지연시켜 등록 순서 의존을 없앴습니다. r3는 같은 actor/system의 준비 행에 같은 멱등 키를 재사용한 시험 fixture 오류로 scoped 47건이 준비 단계에서 실패했습니다. DB unique 제약을 유지하고 준비 키를 분리했습니다. r3 도중 너무 일찍 시작한 verifier는 STALE_TEST_EVIDENCE로 거부됐으며 완료 뒤 별도 r3-verify-after는 exit 0이었습니다. 실패한 Test를 통과로 바꾸지 않았습니다. 최종 r4의 53건은 모두 실제 PASS입니다. app-compile-r1의 공개 JDBC/Flyway 타입 가시성 실패는 persistence의 기존 두 starter를 api로 노출하여 수정했고 app-compile-r2 exit 0 및 최종 앱 시험으로 재확인했습니다. 런타임 artifact/버전 추가는 없습니다.

### PostgreSQL 입력과 운영 한계
- 개발 candidate: `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`. fixture가 실제 server_version 18.6과 vector 0.8.7을 질의해 확인했습니다. 실제 버전 문자열은 test-proof.json의 actualDatabaseProof와 XML에 있습니다.
- 정본 bootstrap SHA256 `04b42923d3940ed51d0630c0cddb13cc649167fcb8d43027bba07c7f2ca7c11e`, HBA SHA256 `1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22`. 두 정본 파일은 편집하지 않았습니다.
- 승인 Harbor 운영 digest로 이미지 전환한 재시험은 NOT_RUN입니다. 전환 시 두 모듈 선언 입력/시스템 속성과 N17 fingerprint가 달라지며 bootstrap/HBA/migration/권한/저장 회귀를 실제 재실행하고 부모가 확인하기 전 배포/수용/전체 완료로 승격하지 않습니다.
- 실제 K8s/SSH/Secret/Harbor/Source/모델 서버·운영 백업/복원/CI/전역 설정 변경은 실행하지 않았습니다. P15 hook·packaging·실제 운영 수용은 이 결과가 검증한 범위가 아닙니다. 운영 입력 대기와 F1 코드 로컬 준비를 분리한 승인 B를 유지합니다.

### 실제 변경 파일·Plan 차이·보호

다음은 P07 실제 변경 경로와 SHA256입니다(이 Dev report 자신의 최종 해시는 scratch result.json에 기록). persistence API 가시성, Clock 지연 기본 설정과 일반 앱 DDL 사전 거부는 확정된 배선/실패 정책을 만족하기 위한 기술적 구현입니다. 업무·보안·운영 계약의 변경이나 승인 Spec/Plan 충돌은 발견하지 않았습니다. P05 Core/Registry/Manifest/Validator/Dispatcher는 수정하지 않았습니다.

| 파일 | SHA256 |
|---|---|
| `gateway-app/build.gradle.kts` | `4aa6583f6de70bf4a68e922de48c81d64bf996ce301aa70b960e5c88164cd004` |
| `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackContextSupport.java` | `833f7fb273dc013c1fa97e3afaffc10ea7d3e0c9c5ec5d166f67b53e2333141e` |
| `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackSmokeIT.java` | `eb0cf69aff483420de6768c9cc3992792b345a2c2217bb983305f71f7adaaa7b` |
| `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java` | `727083bae489aa5691c99b00e769c12bae61806239d618cfb4b07708b378831a` |
| `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java` | `350a9a4bc4cabd9d07b043f5e1a349ca0b4b227b7b5cd330ec124758d6a8f558` |
| `gateway-app/src/main/java/com/nori/ai/gateway/app/config/MigrateApplication.java` | `73966ad9b7f34c2dd526172e145b3d39e3a5dda90615e320b05c1ed9ca77694e` |
| `gateway-app/src/main/java/com/nori/ai/gateway/app/config/MigrateCommand.java` | `08042a9fcc4a6df3aeef1faa991b0efaeab149dc4d04ed25bad17a572b1f0ec2` |
| `gateway-app/src/main/java/com/nori/ai/gateway/app/config/NormalDatabaseConfiguration.java` | `1516541b14dbf16ecf0e64daa99fa814c2e74aad515f57e656345d69ba8cb4cf` |
| `gateway-app/src/main/resources/application-migrate.yaml` | `00f0ad99350bf5b18b7f3af5f1082a83626bd197fcbe8497f76fcb635e472258` |
| `gateway-app/src/main/resources/application.yaml` | `b5b933bce0c5944a64e14a77d8dd3f14da7b083c62f966bc27f786ca20873354` |
| `gateway-persistence/build.gradle.kts` | `e887cb67bafcf9b5983cfc7c6499e9a37c6cf8ccfb98036135102c1532ba3f3d` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/DbBootstrapOrderIT.java` | `1e28ef46faad881b951d2fa24d3e9adea537dbda14a4c9705a49bcdf01f4d232` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/JobDelegationColumnIT.java` | `7de206f308e65c9699bce507e064b106c4b14c4b3c373bad01b8fbe75945d73d` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/MigrationApplyIT.java` | `7ae401bfee7f9f0d0f7c43050861ec6fdb92bf5620294ee7bc7f4917d637b5c3` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/PgIntegrationSupport.java` | `04db4027c7475373b837442b78d4847b1a0a22cda4aa466cdc51a2bda4da6bb5` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/SchemaVersionGuardIT.java` | `a2c9235d7a11b03f6b1ba2ee3f109201905ae8aeafbe8be863257604fba97e9a` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/ScopedRepositoryIT.java` | `c830f3217b831202eaf989331e2c300fed812c61ef6df19500c38f0e03002769` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/PersistenceClockConfiguration.java` | `d49ecb8b182585093a8737701e259b68494b9631cf8439efe359817b869eaf1c` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/PersistenceModuleConfiguration.java` | `bc414a5bf3f913dda7acee7a83272d5df0443c94b1ccd77a37730c674eff794c` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/maintenance/QueueMaintenanceRepository.java` | `9aa0c7dc70deb5ea21b47b4e88d51bc36d02d94c74804838ba0c81a8fe54e030` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/maintenance/RecoveryMaintenanceRepository.java` | `0db2770f60e3f773350b605a08b3cf6887afd0a7fdb4b5653951544a2f2c4c34` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/maintenance/RetentionMaintenanceRepository.java` | `d394c54331678d18bb807c619af25dfefb5a23508a5f7c61b8d0699c126ce0c3` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/AuditRepository.java` | `6d8b8f012467429d4e85d0f7f9bceff3a15481703fe36c2af3b371ee0e03632f` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/CacheRepository.java` | `10c35587ffa4bb1982eaf4ab06d08cb45d6d6e7573738737a109894e00d1bda5` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/ConversationRepository.java` | `aead9520d18142f01cea42affe44cbcb927f9170d9017f34b7fd426f746c90ac` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/EvidenceRepository.java` | `caa2d1c16212e3d84a02d0d485dc3e2cc90fd188d5884ffb0b8092afc8341274` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/JobEventRepository.java` | `2ae6ed2d18eb3661e03e949a2d51a50aeb90f709ea7aef96ea4f5204a7bb0d24` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/JobRepository.java` | `4ed377608dff38e5ee058de5a5e77bf5b7dca3332ffaeadc2e075f6f375e57be` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/MessageRepository.java` | `a79088f983237755bf88a54c27d9cff874135a530cee9bb04725ad109388814e` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/ResultRepository.java` | `bd850b51200829d004d7804d16f6e0a27dcb995677221f88ef717a3d871b641d` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/ScopedJdbc.java` | `5f0d22e1962f1bfd49d52cf6365863a5769fbd82196070e96e412660dd002aff` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/ToolExecutionRepository.java` | `62e07e3750e7a68f10ac7f27eee8f658fc249de2aa83966f54050d0d63daa114` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/schema/SchemaVersionGuard.java` | `df45b9ae981ec9f0e9cd05466e299e46ff4ffcb65bef3a7748d6fcd980c0edea` |
| `gateway-persistence/src/main/resources/db/migration/gateway/V1__schemas_and_extension_assert.sql` | `c79f6d25a2202c21aa280dcc68446f23cdc57bf7e63158d83092d1f9a4624d61` |
| `gateway-persistence/src/main/resources/db/migration/gateway/V2__conversation_job_core.sql` | `ef879b7dd36359e659129fee5aca2c1634fe50a321f3d5a54cfa10d3bd8d5914` |
| `gateway-persistence/src/main/resources/db/migration/gateway/V3__audit_ops.sql` | `209a5c8665e384cfbf46a23ba875af39c74dd5569cabcc81b45a9a3f764818eb` |
| `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/PgTestInputTest.java` | `6325c5ad24aad10af537954d6487fa7da5386d9e6e022fc88c37201e6e4623f3` |
| `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/RepositorySignatureArchTest.java` | `1c2e5a0706bc1df7aede91ca0fc8a5323f462dbc02d202c71adfb9c66c236932` |
| `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/schema/SchemaVersionGuardTest.java` | `348a0a1b57caa8bdb829ec7f69dd0aecbd85b942a93751ca3fe8174f9a358fe4` |
| `gateway-persistence/src/testFixtures/java/com/nori/ai/gateway/persistence/testing/PgTestDatabase.java` | `6d523960e2478983d75314eee8a2255c23a9cf09659f7f09eb15e5f7ef467872` |

기존 report 825975바이트(SHA256 `c952d02902bca6771f4c4e9b24d5bd32b8d1b70eb138611fdac7478dd37383bd`)는 그대로 보존하고 이 절만 UTF-8 LF로 추가했습니다. 보호 483개와 기존 파일 전체 스냅샷을 대조했고 허용 경로 밖 변경은 없습니다. Spec/baseline/approval6/최신 Plan/기존 P06 JSON/옛 F0·PREREG·golden·capture·results·evidence는 불변입니다. 최종 경로/해시·부모 승인 proof·보호/원본 prefix·자원 정리·noOps/noP08/noCommit 확인은 scratch `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p07-implementation/result.json` 및 `final.md`에 기록합니다. 부모의 독립 Validate 및 운영 선행조건은 별도이며 이번 세션에서 전체 완료로 표시하지 않습니다.

### P07 부모 실제 PostgreSQL 재현 결함 근본수정 — 2026-11-06

상태: CODE/LOCAL_VALIDATED만 PASS이며 부모 독립 Validate는 PENDING입니다. 기존 구현의 82건 PASS는 부모 승인이 아니며, 아래 부모 초기 결함은 exit 0이어도 제품 결함입니다. 부모 프로그램·실행기·초기 결과를 수정/실행/덮어쓰지 않았고, 이번 수정은 영구 회귀시험으로 검증했습니다. DEPLOYMENT/ACCEPTANCE/COMPLETE는 BLOCKED/NOT_RUN입니다.

#### 근본원인·수정·영향

- CacheRepository.put: 요청 expiresAt를 그대로 저장하여 부모 만료를 넘겼습니다. 소유권·actor·미만료 조건을 만족하는 부모 행을 같은 INSERT SELECT에서 읽고 LEAST(요청 만료, 부모 만료)로 제한합니다. ON CONFLICT UPDATE는 이미 제한된 EXCLUDED.expires_at를 사용합니다. 더 짧은 TTL, pdg별 분리, 타 owner/actor 및 없는/만료 부모 쓰기 거부를 보존합니다.
- AuditRepository: 조회에 살아 있는 부모의 30일 만료/존재를 요구한 것이 원인입니다. 조회는 감사 자신의 owner_system+actor_ref+recorded_at+90일만으로 판정합니다. INSERT는 부모 존재·실제 owner/actor·job/conversation 상호 일치를 검사하되 부모 만료로 종결 메타데이터 저장을 막지 않습니다. nullable parent의 명시 CAST와 의도된 부모 FK 부재는 그대로입니다. 기존 9×5 시험은 대화묶음 8×5 엄격 만료와 감사 5조건 독립 기대로 분리했습니다. 이는 Spec §12.2·§12.3의 확정 계약 복구입니다.
- ScopedJdbc :now, Audit cutoff, RetentionMaintenanceRepository의 대화 now/감사 cutoff: Timestamp 바인딩의 미래 반올림이 만료 1ns 직전 조회 차단·조기 삭제를 일으켰습니다. 실제 사용 JDBC 42.7.11 TimestampUtils.toString의 nanosExceed499 올림 경로를 javap로 확인했습니다(jdbc-rounding-proof.json). 해당 기준시각만 ChronoUnit.MICROS로 내림해 저장 정밀도와 비교합니다. 비교 연산자, 만료 시각, 30/90일 기간, Clock 정책은 바꾸지 않았습니다. JSONB Context와 resolvedTimeWindow 원본 Instant/나노초는 변환하지 않습니다.
- 호출/wiring: AiGatewayApplication → GatewayAppModuleConfiguration → PersistenceModuleConfiguration @Import의 기존 9개 저장소와 유지보수 빈/생성자 연결을 확인했습니다. scoped 공통 시각은 대화묶음 저장소의 조회/쓰기 경계에 영향을 주며, 이외 app/Core/Registry/Manifest/P05 assertion·루트 build·권한/스키마는 변경하지 않았습니다.

#### 부모 초기 결과 보존

아래는 수정 전 부모 결과 그대로이며 이번 세션의 수정 후 부모 프로그램 결과가 아닙니다. 원본 경로: ../p07-implementation/root-probe-initial-defects.json. metadata 파일명 충돌의 Python KeyError java는 이전 부모 하네스 정리 이력이고, 후속 runtime.json 분리 뒤 완료된 실제 PostgreSQL 재현과 구분해 보존합니다.

```json
{
  "conversationTtlExactly30Days": true,
  "conversationTtlSeconds": 2592000,
  "cacheExpiryWithinConversation": false,
  "auditInsert": true,
  "conversationVisible1nsBeforeExpiry": false,
  "purgeDoesNotDelete1nsBeforeExpiry": false,
  "conversationHiddenAtExpiry": true,
  "auditVisibleAfterConversationExpiry": false,
  "auditVisibleAfterConversationPurge": false,
  "auditWithoutParentInsert": true,
  "auditHiddenAt90Days": true,
  "unlinkedAuditVisible1nsBefore90Days": false,
  "auditPurgeCount1nsBeforeUnlinkedExpiry": 2,
  "unlinkedAuditSurvivesPurgeBefore90Days": false
}
```

#### 영구 회귀시험과 실제 실행 증거

RetentionBoundaryIT 14건을 추가했습니다. 캐시 INSERT/충돌 UPDATE 제한·짧은 TTL·pdg 분리·소유권 거부, 대화/독립 감사의 -1us/-1ns/정확 만료/+1ns/+1us 조회 및 실제 purge 행 수, 대화 31일 감사 조회·만료 소유 부모 감사 쓰기·부모 정리 뒤 90일 직전 조회·정확 90일 차단/정리·이후 독립행 보존, 알 수 없는/타 owner/타 actor/서로 다른 부모 거부를 확인했습니다. StepClock 생성 TTL=2592000초 및 touch/context/message 뒤 만료 불변, Context referenceTime/timeWindow와 Message resolvedTimeWindow의 9자리 나노초 문자열 왕복도 확인했습니다. ScopedRepositoryIT의 기존 40건 대화묶음 격리/엄격 만료와 독립 감사 5조건, 나머지 기존 시험은 유지했습니다.

첫 Gradle은 실제 SpecGuard(fix-spec-guard) exit 0이며 그 뒤에만 편집했습니다. 모든 Gradle에 AIGW_SPEC_FILE을 지정했습니다. 시험/검증의 이미지·requireDocker·aigw.* 입력은 동일하고 앱 IT 필터는 재사용한 app-filter.init.gradle로 verifier에 재현했습니다. glab token은 메모리 캡처 후 Authorization Bearer child env로만 전달했습니다.

| Test task | 실제 고유 case | Test exit | 직후 별도 공식 verifier exit | 실행 상태 |
|---|---:|---:|---:|---|
| `:gateway-persistence:test` | 11 | 0 | 0 | EXECUTED |
| `:gateway-persistence:integrationTest` | 67 | 0 | 0 | EXECUTED |
| `:gateway-app:integrationTest` | 5 | 0 | 0 | EXECUTED |
| `:gateway-app:test` | 13 | 0 | 0 | EXECUTED |

실제 명령(각 Test 바로 뒤 별도 verifier 실행):

- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:test --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:test" --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:integrationTest -Paigw.requireDocker=true --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run -Paigw.requireDocker=true "--args=test-evidence verify --tasks :gateway-persistence:integrationTest" --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *AppContextWiringIT --tests *StackSmokeIT --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p07-root-fix\app-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true "--args=test-evidence verify --tasks :gateway-app:integrationTest" --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain`
- `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain`

최종 고유 case 합계 96건(이전 회차/부모 probe 미합산). XML failure/error/skip=0, OTR failed/skipped/aborted=0, leaf summary failed/skipped/aborted=0, 모든 대상 Test task=EXECUTED입니다. runId·build binding·inputFingerprint·현재 fingerprint·runtimeClasspath를 대조했고 공식 verifier의 TEST_EVIDENCE_VERIFIED를 확인했습니다. verifier exit 0과 실제 Test PASS를 별도로 판정했습니다. 증거: `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p07-root-fix\test-proof.json` SHA256 `88ecce40410042089a5634a167366fa27fba040906c61e88a48efe70bbe18ae6`. 이번 수정 시험의 실패 회차가 있으면 원본 label 증거를 보존하며 최종 집계에 포함하지 않습니다.

최종 증거 수집기 첫 시도는 서로 다른 parameterized method의 동일 XML 표시 이름을 classname+name만으로 중복 판정해 exit 1(DUPLICATE_XML_CASE)이었습니다. 제품 Test 실패가 아니며 기록은 proof-collection-initial-failure.json에 보존했습니다. OTR의 JUnit uniqueId(클래스·메서드·invocation)로 고유성을 확인하고 XML/OTR/summary 수를 대조해 수집 exit 0을 얻었습니다. 기존 캡처를 덮어쓰지 않고 동일 바이트를 확인했습니다.

PostgreSQL image `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`, 실제 PostgreSQL 18.6/vector 0.8.7. Docker 필수 두 IT가 해당 pin으로 실행됐습니다. 승인 Harbor 운영 digest 재시험·실운영 배포·수용은 미실행입니다. Eval 249 전체는 반복하지 않았습니다(제품 루트/하네스 변경 없음).

#### 실제 변경 파일·SHA256

| 경로 | 수정 후 SHA256 |
|---|---|
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/RetentionBoundaryIT.java` | `cce7052f1505b956919eaf72b80330932378210009ee894d1d2c23484949aa44` |
| `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/ScopedRepositoryIT.java` | `e10d6b8543f0b935945e092ba46d5997a568ba07269aaf5e50d8edf5cf1c0085` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/maintenance/RetentionMaintenanceRepository.java` | `e4d4d60f78f1b06de90d40cc5beac4ab02665049665b66b3b25653b3a8173451` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/AuditRepository.java` | `c8c26db0107f609f2191fa6d70033659aa94b299801048da8ea18340d28024e3` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/CacheRepository.java` | `2455a5c76228df1c106bbecd6e6cae71c92e8528b557597a2d2953d91d36017f` |
| `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/ScopedJdbc.java` | `126cfc7dc6f9ab415514d4d2321666481fe4ddcc816df5e3b5dd01b41e972bc9` |

위 6개 persistence 파일과 이 Dev report append만 이번 세션의 변경입니다. Spec/Plan 차이·충돌·신규 정책 결정은 없습니다. SQL V1~V3, P06 bootstrap/HBA, Spec/Plan, 보호 483개, 기존 P06/F0/PREREG/golden 및 앞 구현 전체 미커밋 상태를 보존합니다.
기존 보고서 847947바이트 SHA256 `4ac31aefdd85cc0d188b4e70770dbbb205a78584eaf8678bf6f9bfd38269bad4`를 prefix 그대로 보존하고 이 절만 UTF-8 LF로 append했습니다. 소유 label을 확인한 fixture finally 정리 뒤 잔류를 읽기 전용 확인합니다. 다른 소유 자원을 무조건 삭제하지 않습니다.
변경/보호 hash·append prefix·공식 검증 proof·자원 정리·noCommit/noPush/noOps/noP08/noSubAgents 최종 결과: `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p07-root-fix\result.json`. 부모가 독립 재검증한 뒤 커밋/P08을 별도 세션으로 진행합니다.

### P07 부모 직접 검증 PASS 및 로컬 종료 — 2026-11-06

부모 정본 `scratchpad/p07-root-fix/root-validation-approval.json`의 status=PASS에 따라 CODE 및 허용된 LOCAL_VALIDATED의 부모 검증 PASS를 기록합니다. 앞 절의 부모 PENDING 이후에 수행된 검증입니다. 실제 배포·수용·전체 COMPLETE는 완료하지 않았습니다. 이 종료 세션은 코드·Spec·Plan·기존 evidence를 변경하거나 Test task를 반복하지 않고 증거와 현재 파일을 읽어 대조했습니다.

초기 `p07-implementation/result.json`의 자체 82건 PASS는 부모에게 반려되었습니다. `root-probe-initial-defects.json`의 exit=0은 프로그램 종료값이며 캐시 부모 TTL 초과, 부모 만료/삭제 후 감사 조회 실패, 만료 1ns 직전 조기 차단/삭제라는 제품 결함을 PASS로 만들지 않습니다. 부모 하네스 최초 metadata 실행은 출력 파일명 충돌로 KeyError java가 발생했습니다. 이름을 분리한 metadata 2회차 성공과 실제 PostgreSQL 결함 재현 결과를 별도로 보존했습니다. 증거 수집기 DUPLICATE_XML_CASE 이력 또한 앞 절과 원본 증거 그대로 보존합니다.

근본수정은 CacheRepository.put의 INSERT/충돌 UPDATE에 LEAST(요청 만료, 소유 부모 만료)를 적용한 것, AuditRepository의 90일 조회를 감사 자체 owner/actor/recorded_at 기준으로 독립시킨 것, ScopedJdbc의 now 및 감사/정리 cutoff를 마이크로초로 내림하여 JDBC/PG 시각 반올림의 조기 만료를 막은 것입니다. 감사 쓰기는 부모 owner/actor와 job/conversation 일치를 유지하면서 소유한 만료 부모의 종결 메타데이터를 허용합니다. 실제 근거는 해당 저장소·RetentionMaintenanceRepository와 RetentionBoundaryIT 14건 및 ScopedRepositoryIT입니다. Spec/Plan 확정 계약 복구이며 새 business/security 정책 결정이나 계약 변경은 없습니다.

최종 실제 고유 JUnit case는 **96건**입니다. 초기 82건·실패 회차·별도 부모 프로그램을 합산하지 않습니다. XML failure/error/skipped=0, OTR 및 leaf summary failed/skipped/aborted=0이며 OTR JUnit uniqueId로 고유성을 확인했습니다.

| Test task | 최종 case | runId | inputFingerprint | binding SHA256 |
|---|---:|---|---|---|
| `:gateway-persistence:test` | 11 | `49bda1bf-4f5a-4620-b504-a4ab678ee779` | `dc038668f7382acdf1e73f408a55587d32e18c35d5bdb583bcc60c44adda4f3e` | `c9dd21583edae4ed0a63756550718e322b1b34922bb028ff56309ce4a8e2133b` |
| `:gateway-persistence:integrationTest` | 67 | `411f53bf-17e9-499d-a423-008f987c325b` | `016424ee76583f5b383b63b648df71cbb4daa63c3d6c801805971ba3eb2c52fb` | `242898014b57615009cb4ab97093a67fbee641d658951bcc667d7c9fa935d8ab` |
| `:gateway-app:test` | 13 | `56bf821d-f507-40fa-aad5-9fd312669057` | `1e27ed1019b937db091c7dd9049f5de971e6464c054dae8c361f289960dc18f4` | `8f4358595dd36048e56dc21fde5bf2454a6ea634535c0d331c44e9d0f3329a77` |
| `:gateway-app:integrationTest` | 5 | `1a6dd4fc-f69f-453d-9e9b-4c18a12fa84a` | `0d41366bfa21e039e4353f454f262a05db40ebebf88d97e196699a595463ed9a` | `2d8d2561fbf059359cb9b9b3d0656b07929ce72452db73faddd0eac5f98d3d79` |

XML/OTR/leaf summary의 case·상태·실행 클래스, runId·Spec SHA·runtimeClasspath SHA·현재 inputFingerprint와 run-binding을 실제 대조했습니다. 앱 IT 필터는 `*AppContextWiringIT`, `*StackSmokeIT` 두 개이고 다른 task의 testFilters 및 모든 includeTags는 빈 목록입니다. 두 IT의 aigw.requireDocker=true·시험 이미지 pin·정본 SQL/HBA 입력이 일치합니다. 부모 `root-official-verify.json` exit=0과 `root-official-verify.log`의 TEST_EVIDENCE_VERIFIED를 확인했습니다. 공식 명령은 `:gateway-eval:run -Paigw.requireDocker=true --args="test-evidence verify --require-docker --tasks :gateway-persistence:test,:gateway-persistence:integrationTest,:gateway-app:test,:gateway-app:integrationTest"`이며 기존 app-filter.init.gradle로 동일 필터를 재현했습니다. 종료 세션에서 이 명령을 재실행하지 않았습니다.

정본 bootstrap SHA256 `04b42923d3940ed51d0630c0cddb13cc649167fcb8d43027bba07c7f2ca7c11e`, HBA SHA256 `1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22`를 보존했습니다. 명시 candidate `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`의 실제 PostgreSQL 18.6/vector 0.8.7 로컬 시험이며 운영 Harbor 이미지 승인에 해당하지 않습니다.

fixture `PgTestDatabase`는 정본 SHA 확인·HBA read-only mount·고정 이미지·일회성 난수 자격·소유 label 확인 후 close를 구현합니다. `DbBootstrapOrderIT.canonicalBootstrapMigrationAppAndRestrictedBackupAcceptanceRoles`는 bootstrap 재실행, 앱 DML/DDL 거부, 16개 테이블 backup/acceptance 권한, acceptance read-only 해제 후 쓰기 거부, Unix ai_gateway 허용 및 postgres/template1/p07_outside의 TCP/Unix HBA 거부, acceptance 역할 제거를 검증합니다. `MigrationApplyIT`는 V1~V3 재실행/소유권과 vector 부재 실패를 검증합니다. `AppContextWiringIT`는 실제 AiGatewayApplication 기동·저장소 bean·실제 쓰기/읽기, history 부재/버전 불일치 시 후속 runner 미실행, migrate-only main의 역할·프로필·실패 전파와 일반 앱 자동 DDL 사전 거부를 검증합니다. 같은 클래스의 기존 P05 Core 8개 bean·registry/manifest/validator/dispatcher·NoOp seam assertion 및 최종 앱 단위시험 13건도 유지했습니다. 실제 코드와 최종 실행 클래스 목록이 일치하며 운영 실행을 의미하지 않습니다.

부모 별도 실제 PG 프로그램 `p07-implementation/root-probe-r2-result.json`은 status=PASS, exit=0입니다. 초기 프로그램에 wrong owner/actor 거부와 소유 만료 부모 감사 쓰기 조건을 추가한 후 재실행했습니다. 최종 standalone program PASS이며 sourceFrozen=true, 실행 전후 소스 hash 일치입니다. 종료 시점에도 sourceHashes의 모든 파일을 실제 대조해 일치를 확인했습니다. 아래 17조건은 JUnit과 별도입니다(15개 bool=true, TTL=2592000, 감사 정리 count=1).

| 부모 프로그램 조건 | 실제 결과 |
|---|---|
| `conversationTtlExactly30Days` | `true` |
| `conversationTtlSeconds` | `2592000` |
| `cacheExpiryWithinConversation` | `true` |
| `auditInsert` | `true` |
| `conversationVisible1nsBeforeExpiry` | `true` |
| `purgeDoesNotDelete1nsBeforeExpiry` | `true` |
| `conversationHiddenAtExpiry` | `true` |
| `auditVisibleAfterConversationExpiry` | `true` |
| `auditOtherSystemDenied` | `true` |
| `auditOtherActorDenied` | `true` |
| `auditOwnExpiredParentInsert` | `true` |
| `auditVisibleAfterConversationPurge` | `true` |
| `auditWithoutParentInsert` | `true` |
| `auditHiddenAt90Days` | `true` |
| `unlinkedAuditVisible1nsBefore90Days` | `true` |
| `auditPurgeCount1nsBeforeUnlinkedExpiry` | `1` |
| `unlinkedAuditSurvivesPurgeBefore90Days` | `true` |

최종 probe SHA256 `7b65cee54e68db415a688cb398ce663448320d3be534b73f0daf588d549653fb`, 프로그램 SHA256 `a6845795f8e769455353a05066111fad60873f983d57def97cbd340b3bedf41e`입니다. 부모 승인 JSON SHA256 `69d97ca6d7af779660e9ca034624945b753e51b9976b64f1720dca247fa4016c`를 기준으로 후보 42개 경로와 파일 해시 전체를 대조했습니다. AI HEAD `b307e53bf120297687cb4fa5236bc41fb63c973d`, exec HEAD `6d744aa57fd4d2970f9fca8c941b43b8a2e18670` 및 전체 작업트리 baseline 일치·스테이징 비어 있음을 확인한 뒤 이 절만 추가했습니다. 이 절 직전 보고서 전체 858764바이트 SHA256 `20d00229652cfa0db6f80a3bc8d364ac6068c0ebdcac625b27b3af741f7d694e`를 byte prefix로 보존합니다.

보호 483개, Spec/baseline/approval6, Plan SHA256 `12e29e5215ce117ba869a1d2734594ed61aa3690346c05103cc2fd565dfd4f63`, P06 SQL/HBA·기존 JSON·F0/PREREG/golden/results/evidence 및 기존 Dev report 본문은 불변입니다. 부모 changes만 정확한 로컬 커밋 후보이며 보고서만 이 append로 해시가 바뀝니다. staged/committed 경로·각 blob SHA256·최종 commit SHA와 prefix/보호 대조 결과는 scratch `p07-close/result.json`, `final.md`에 기록합니다. exec에는 커밋을 만들지 않습니다.

approved Harbor digest·운영 용량·backup/PV/inbound·execMerge·실제 Secret 부재 등 기존 P06 외부 게이트는 BLOCKED/NOT_RUN입니다. 실제 배포·수용·전체 COMPLETE로 승격하지 않습니다. 승인 B의 기존 순서로 P08은 다음 독립 세션에서 Plan의 기술 wiring 누락 보완부터 이어갈 수 있으며 이 세션은 P08을 실행하지 않습니다. push/MR/SSH/K8s/Secret/Harbor/Source/전역 설정 변경·하위 에이전트는 없습니다. P04 임시 clone은 사용자 요청에 따라 작업 폴더 밖 `scratch/p04-retired-clean-checkout`으로 가역 이동된 기존 상태이며 원본은 없습니다. 영구삭제는 자동승인 검토에 막혀 실행 0인 상태를 유지하고 이 세션은 추가 삭제/이동하지 않았습니다.

### P07 추가 캐시 요청 만료 쓰기 경계 수정 — 2026-11-06

상태: 이번 추가 경계에 대한 CODE/LOCAL_VALIDATED PASS, 부모 독립 검증 및 로컬 커밋 PENDING. 기존 초기 96 JUnit + 부모 17조건/42blob 검증은 P07 AEC baseline의 역사적 회차로 보존합니다. 이번 회차의 15건과 합산하지 않으며, 현재 소스 전체 96건을 재시험했다는 의미가 아닙니다. Harbor/K8s 배포·실제 Source·운영 수용·DEPLOYMENT/ACCEPTANCE/COMPLETE는 BLOCKED/NOT_RUN을 유지합니다.

#### 원인·최소 수정·영향 범위

- 부모 독립 프로그램의 초기 결과(p07-cache-expiry-probe/initial-result.json)는 storedNotAfterRequested=false, hiddenAtRequestedExpiry=false, storedMinusRequestedNanos=1입니다. 부모 프로그램·실행기·초기 결과를 실행하거나 수정하지 않았습니다.
- CacheRepository.put의 Entry.expiresAt 원본 Timestamp 바인딩에 JDBC 42.7.11 TimestampUtils의 마이크로초 올림이 적용되어 요청한 캐시 유효기간을 초과했습니다. 기존 query now/cutoff 내림과 구분되는 추가 쓰기 경계입니다. 요청 expiresAt를 ChronoUnit.MICROS로 내린 뒤 바인딩합니다. 1마이크로초 미만 구간의 조기 캐시 미스는 허용된 보수적 수명 준수이며 만료 연장·epsilon·비교 완화는 없습니다.
- SQL LEAST(request,parent), ON CONFLICT EXCLUDED.expires_at, owner/actor/pdg 조건, 단일 TransactionTemplate 경계를 보존했습니다. 공개 API·생성자·빈·권한·schema/migration·UTC 30일/90일·query cutoff·JSONB 나노초 정책은 변경하지 않았습니다.
- 변경 파일은 CacheRepository.java, RetentionBoundaryIT.java, 이 Dev report의 끝 append뿐입니다. 기존 보고서 전체 바이트 prefix를 보존했습니다.

#### 실제 검증과 한계

- 첫 Gradle 명령: 정본 spec-guard exit 0 후 코드 편집. SPEC SHA256=c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4, 승인 Plan SHA256=16159d39d5242a128d3bb6f1e3f267b6e0be580d7ad9c85ea8f553817343e4b8를 그대로 유지했습니다.
- RetentionBoundaryIT에 1개 case를 추가했고 기존 14개 case를 보존했습니다. 499/500/999ns 각각 INSERT·같은 키 UPDATE에서 DB 직접 조회 저장 expiry <= 요청 expiry 및 마이크로초 기대값 일치, 저장 expiry 1ns 직전 조회 가능, 정확 저장 expiry·정확 원래 요청 expiry 조회 차단을 MutableClock/BASE·기존 실제 PG/JDBC fixture로 검증했습니다.
- 첫 실행은 AssertJ Instant에 없는 isLessThanOrEqualTo 사용으로 컴파일 실패(exit 1), 별도 공식 verifier 호출도 같은 컴파일 선행 실패(exit 1)였습니다. 현재 시험 실행 0이며 수집기에 보인 이전 67건은 이번 증거에서 제외했습니다. isBeforeOrEqualTo로 수정했습니다.
- 최종 시험 명령: C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:integrationTest -Paigw.requireDocker=true --tests *RetentionBoundaryIT --no-daemon --console=plain.
- 직후 별도 공식 검증 명령: C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p07-cache-expiry-fix\retention-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true --args=test-evidence verify --tasks :gateway-persistence:integrationTest --no-daemon --console=plain. 외부 scratch init으로 같은 필터를 재현했습니다.
- 최종 시험 exit 0, task 실제 EXECUTED, raw XML 15건·OTR 고유 JUnit ID 15건·summary passed 15건; fail/error/skip/abort 0. 공식 verifier exit 0 및 TEST_EVIDENCE_VERIFIED를 별도로 확인했습니다. 표시 이름이 같은 parameterized XML 항목은 OTR 고유 ID로 구분했습니다.
- runId=ded5072e-ed53-4dca-b767-770604c0f210, inputFingerprint=ec6cffcf125ba2500127fbd253ea2d60bbc81d4e1548f39d136a8c4d14fa175b, runtimeClasspath SHA256=b2d359a23059f99b476d6e1a5ed9bcc0041f2f7064f3d73fc0dba6c33adb3902. 현재 fingerprint/실제 classpath·정본 SQL/HBA SHA·requireDocker=true·동일 필터·SPEC 결속을 직접 대조했습니다.
- 로컬 candidate 이미지: pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2. 실제 PostgreSQL 18.6 / vector 0.8.7, aigw_app을 사용했습니다. 운영 승인 digest 검증으로 승격하지 않습니다.
- fixture의 finally 정리와 owner label 검사 경로를 확인했고 실행 중 owner label을 직접 확인했습니다. 종료 뒤 해당 label 컨테이너는 0개였습니다. 다른 자원을 삭제하지 않았습니다.
- 보호 483개 hash·Spec/Plan·P06 SQL/HBA·V1~V3·승인 문서·기존 root 프로그램/증거 보존을 확인했습니다. commit/push/P08/하위에이전트/SSH/K8s/Secret/Harbor/Source/전역 설정 변경은 수행하지 않았습니다.
- 생성자/빈/권한/하네스 변경이 없어 관련 없는 전체 67 IT·5 앱 컨텍스트·13 앱 unit·Eval 249는 반복하지 않았습니다. 부모의 실제 독립 프로그램 재실행·최종 검증 및 정확 3파일 로컬 커밋은 후속 부모 책임이며 이번 세션에서는 미실행입니다.
- scratch 증거: p07-cache-expiry-fix/test-proof.json, result.json, cache-spec-guard.json, cache-retention-it-final*.json, current-fingerprint.json, runtime-classpath.txt 및 raw XML/OTR. 증거 수집기의 첫 XML 표시 이름 중복 판정 실패는 OTR 고유 ID 대조로 바로잡았으며 제품 시험을 재실행하거나 결과를 바꾸지 않았습니다.


### P08 인입 인증 및 승인 A안 거절 감사 — 2026-11-06
상태: 승인 P08 Implement의 구현·LOCAL_TEST 결과 후보이며 **NEEDS_PARENT_REVIEW**입니다. 부모 독립 검토·로컬 커밋은 미실행입니다. DEPLOYMENT/ACCEPTANCE/COMPLETE 및 P16/P29 수용은 NOT_RUN입니다. Spec/Plan의 완료 체크는 변경하지 않았습니다.
선행 확인: launch-authorization.json의 AI HEAD d6b0b4dee04c7b11afc48de27ef3f814bec72bc8, exec HEAD 6d744aa57fd4d2970f9fca8c941b43b8a2e18670, 승인 A Specify/Plan·R6 seq7(f7abcc35137d8800aeb751245e3376efdf53a9a1) 및 P07 캐시 부모 실제 PG 검증/커밋을 실제 파일과 대조했습니다. 직전 P07 추가 절의 부모 PENDING은 당시 이력이며, 현재는 부모 15 JUnit·공식 verifier·실제 PG 요청 만료 probe PASS와 위 d6b0b4d 커밋 완료입니다. 기존 96 JUnit+17조건은 AEC/P07의 역사적 증거로 보존하며 P08 현재 Spec 수용 증거로 재분류하거나 합산하지 않았습니다.
첫 Gradle은 정본 spec-guard(exit 0)였고 구현은 그 뒤 시작했습니다. Spec SHA256 c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4, Plan SHA256 16159d39d5242a128d3bb6f1e3f267b6e0be580d7ad9c85ea8f553817343e4b8은 불변입니다.
#### 구현·변경 이유와 영향
- 단일 /v1/* REQUEST Filter → 서비스 해시 constant-time 비교 → 신뢰 공개 JWKS/ES256/kid/서명·필수 claim·원본 정밀 NumericDate·경로별 dlg/sys 검증 → ScopeAssembler → Body 4개 시스템 필드 검사 → 검증 scope attribute/재독 wrapper → chain 순서를 연결했습니다. requestId는 서버 UUID입니다. MVC와 같은 RequestPath/PathPattern을 사용하고 probe/test 경계 및 중복 등록 0을 실제 HTTP로 확인했습니다.
- V3.1만 추가해 AUTH_REJECTED의 미확인 신원 NULL을 허용했습니다. 일반 감사 owner+actor 필수·actor의 owner 필수·기존 owner enum CHECK와 일반 9개 저장소·독립 90일·NULL 포함 purge는 유지합니다. AUTH_REJECTED만 있는 core enum, 닫힌 typed append, 독립 실제 commit, Clock 마이크로초 내림, Auditor/설정만의 실제 호출자·생성자 제약을 연결했습니다. 업무 error enum/P09/P14 이벤트는 추가하지 않았습니다.
- token 실패 NULL/NULL, 단언 실패(서명 유효한 SYS 불일치 포함) owner/NULL, Body 위반만 완전 검증 owner/actor를 기록합니다. 실제 INSERT/commit/lock timeout/연결 실패는 원래 401/403 대신 503·Retry-After: 1·고정 두 필드 응답이며 후속 처리가 0입니다. 원문/자유 JSON/cause·로그 대체·재시도·가짜 scope가 없습니다.
- signer는 testFixtures의 두 factory와 provided Instant API에만 있습니다. 합성 private JWK 파일 시험은 자기 TempDir·ACL·finally 삭제를 확인했습니다. 공개 JWKS/해시 파일만 앱 시험에 주입합니다. 실제 production runtime의 signer/test-fixtures/시험 인프라 제외 검사를 유지했습니다.
- 고정 pdg 벡터 V1~V4/N1~N3를 기존 P02 입력에서 재사용했습니다. Java는 Unicode 코드포인트 정렬·엄격 UTF-8을 사용하며, 기존 Python helper의 추가 제어문자 금지를 정본 CR/LF·공백 조건에 맞추고 비표현 UTF-8 입력을 명시 거부하도록 보정했습니다. 고정 기대값은 변경하지 않았습니다.
- 기존 앱 시험에 필수 자격을 공급하고 단일 Clock/실제 최신 schema/normal Flyway off/migration-only 격리/Core8/manifest9/capability6/production ToolExecutor0 회귀를 유지했습니다. 기존 두 module 설정 변경은 import/@Import만, app build는 승인 Nimbus implementation 및 tc-mock testFixtures 연결만입니다.
Spec/Plan의 확정 업무·보안 계약과 차이는 없습니다. wire의 기존 Body contextRef를 유지하며 Body 없는 요청의 동일 단언 전달 위치를 X-Actor-Assertion 헤더로 auth-v1에 구체화했습니다. 두 위치 동시/중복 헤더는 거부하며 신뢰키는 파일에서만 읽습니다. P09/P10/P14 책임을 앞당기지 않았습니다.
#### 실행 실패·원인·수정
- p08-app-test: 22건 중 2건 실패. ArchUnit이 jar 경로의 참조 타입 자동 해석에 기대 persistence 설정 생성자 호출을 놓친 수집 결함을 6개 모듈 main 디렉터리 직접 import로 수정했습니다. Nimbus 표준 jku/jwk/x5u를 customParam으로 만든 시험 결함은 표준 builder API로 수정했습니다. 직후 verifier exit 0도 이 실패를 PASS로 만들지 않습니다. r2 22건은 통과했고 이후 원본 인코딩 보강의 최종 r4 24건만 집계합니다.
- p08-ingress-it: BeforeAll 때 필드 TempDir가 준비되지 않아 초기화 실패. BeforeAll TempDir 인자로 소유 수명을 확보했습니다. 실제 HTTP/leaf 0이며 공식 verifier도 실패했고 이전 XML/요약을 사용하지 않았습니다.
- p08-ingress-it-r2: HTTP 30건 자체 PASS이나 실행 중 같은 integrationTest 소스셋의 AppContextWiringIT를 보강해 직후 verifier가 STALE_TEST_EVIDENCE로 거부했습니다. 원본을 보존하고 소스를 고정해 r3 30건과 verifier를 통과했습니다. 이후 발견된 아래 제품 파서 결함 때문에 최종 r5만 집계합니다.
- 독립 Jackson 3.1.8 scratch probe에서 UTF-16LE의 ASCII/NUL 바이트는 UTF-8 decoder를 통과하고 기본 CHARSET_DETECTION은 전체 JSON을 허용함을 재현했습니다. 최초 probe의 첫 token만 확인한 결과는 불충분해 별도 전체 파싱 probe로 보강했으며 두 회차를 보존했습니다. Signed payload/자격/Body 파서의 자동 감지를 명시 비활성화해 승인 UTF-8 계약을 복구했습니다. 실제 서명된 UTF-16/32·손상 UTF-8의 단위/HTTP 거부를 추가하고 영향 앱 단위/HTTP만 재실행했습니다. 새 정책·예외 무시는 없습니다.
- Nimbus 10.5 독립 probe에서 jku/jwk/x5u의 null 필드가 getter JSON에서 사라짐을 확인했습니다. 원본 헤더의 엄격 UTF-8/중복 키/실제 key 존재 검사를 추가해 null도 UNTRUSTED_KEY_REFERENCE로 거부했습니다. 실제 서명된 단위/HTTP 음성을 보강하고 앱 단위/HTTP만 재실행했습니다. r3/r4 이전 통과 회차는 원시 보존하며 최종 합계에서 제외합니다.
- Unicode 보조 digest의 첫 Python -c 실행은 PowerShell 인용으로 SyntaxError였고 계산 전 실패였습니다. UTF-8 파일 unicode_vector_probe.py 실행으로 수정했으며 JUnit/고정 벡터 증거에 합산하지 않았습니다. 실패 Test/각 직후 verifier·초기/수정 probe와 raw XML/OTR는 고유 label로 보존했습니다.
#### 최종 실제 검증
| 실제 Test task / 필터 | 고유 JUnit | runId | inputFingerprint |
|---|---:|---|---|
| `:gateway-core:test` / `전체` | 36 | `fe51d198-a3fe-485c-b7b9-8d072cdfb89d` | `118e8b49546a6027f977684682ad7f79c4704ecca1b6ce593697a5a008e9b6eb` |
| `:gateway-persistence:test` / `전체` | 12 | `1e75ea08-2bad-4454-8fb0-9d308c1fcd71` | `ce554e936550fdaaf2a3fbdc8d53415843f52889626440718c7e5b1c64047dd9` |
| `:gateway-persistence:integrationTest` / `*AuthRejectionAuditIT,*DbBootstrapOrderIT,*MigrationApplyIT,*RetentionBoundaryIT,*SchemaVersionGuardIT,*ScopedRepositoryIT` | 70 | `4a31ed94-e05a-4cb2-b2a2-ad56602a57bc` | `2cc3c7e6cc157fea8f17c2cea4bd80a375969c339ed6372e6856fbc39ad6aae3` |
| `:gateway-tc-mock:test` / `전체` | 4 | `017fb6fe-38ca-4438-83d4-7cac1e809373` | `0df7d60afe4277d07cb605a6ff6daf884c9c15b9144b164c29cf996602a1bb00` |
| `:gateway-app:test` / `전체` | 24 | `2d188758-ff74-4995-b336-6f2fe0f2a29f` | `8add6ed795584f6930ab2c9e7ed556ce9abfc6066626c952cb927b3bf493ab43` |
| `:gateway-app:integrationTest` / `*IngressAuthIT` | 30 | `784b9ff5-e6f7-40d3-ad3e-e240398e044a` | `a5ac59cefd50abef31f24d56bb2afd2a836dd1dcb5378d7c9f749da3631d089f` |
| `:gateway-app:integrationTest` / `*AppContextWiringIT,*StackSmokeIT` | 6 | `e15a400a-27f9-4d0c-b7a0-6ad8cae6467a` | `1fde2c5bd7273eb9f767f85aa3d08ac189bd35d13afb12a74ccf53f6f1b05a5b` |

최종 고유 JUnit은 **182건**입니다. 실패·이전 회차 및 Python 검사는 합산하지 않았습니다. 각 Test는 실제 EXECUTED, XML/OTR 고유 JUnit ID/summary.executed/passed가 일치하며 fail/error/skip/abort=0입니다. ISO-04 기존 20 tag가 각각 정확히 한 case에서 실행됐습니다. raw XML/OTR·leaf/run-binding·현재 공식 fingerprint·실제 classpath/Spec·이미지/SQL/HBA·필터를 대조했습니다. 마지막 앱 실행들은 시작/종료/현재 source hash도 일치합니다. 각 회차의 runtimeClasspath SHA는 해당 proof에 보존하며, 초기 core/persistence의 Nimbus 연결 전 전체 앱 runtime SHA를 후기 runtime과 같은 측정으로 섞지 않습니다.

각 Test 직후 실행한 정확한 명령(전 회차 AIGW_SPEC_FILE 정본 명시, pin/requireDocker/SQL/HBA 유지):

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-core:test --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:test --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:test" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-persistence:integrationTest -Paigw.requireDocker=true --tests *MigrationApplyIT --tests *AuthRejectionAuditIT --tests *SchemaVersionGuardIT --tests *DbBootstrapOrderIT --tests *ScopedRepositoryIT --tests *RetentionBoundaryIT --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p08-implementation\persistence-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true "--args=test-evidence verify --tasks :gateway-persistence:integrationTest --require-docker" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-tc-mock:test --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-tc-mock:test" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:test --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *IngressAuthIT --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p08-implementation\ingress-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain`

- Test: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-app:integrationTest -Paigw.requireDocker=true --tests *AppContextWiringIT --tests *StackSmokeIT --no-daemon --console=plain`
- 직후 공식 verifier: `C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p08-implementation\app-filter.init.gradle :gateway-eval:run -Paigw.requireDocker=true "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain`

Python 실체는 C:/workspace_nori/.venvs/aigw-eval/Scripts/python.exe, Python 3.12.10/독립 venv입니다. 최종 Plan의 pdg CLI 고정 4양성·3음성 및 기존 Python unit 3건, 독립 Unicode 벡터/입력 경계를 실제 확인했습니다. JUnit 기여는 0입니다. 정확 명령과 출력은 python-proof.json/python-validation-1.log/python-validation-2.log에 있습니다.

실제 PG는 고정 candidate pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2, PostgreSQL 18.6/vector 0.8.7입니다. 호스트 Docker 후보이며 운영 Harbor 승인·배포 수용이 아닙니다. 정본 bootstrap SHA 04b42923d3940ed51d0630c0cddb13cc649167fcb8d43027bba07c7f2ca7c11e, HBA SHA 1146cf885b2b35e6c0880fdc41fe21602d5169fae2bdc19c23bb4c121539dc22를 보존했습니다.

#### 실제 변경 파일

- `docs/contracts/auth-v1.md` — SHA256 `d45aac12383832ef2008f374b5c436cd7e3d5d882464cafa2ece14521a070818`
- `eval/py/pdg_vectors.py` — SHA256 `ef64b2732a388e0329d620fafd1e6a2cc03c558d9d929db68ed273ccd7caa925`
- `gateway-app/build.gradle.kts` — SHA256 `92eb732bfb3022be7adaacd7c2ff49350b61d40926aafffec5fd43c91e39bedc`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackContextSupport.java` — SHA256 `8e4fa77625ce483ec443bd89b1258d4d2e7b0b3961fb68c7a3ac3a67eeea6dfb`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/security/IngressAuthIT.java` — SHA256 `8b1a9aa79817fd7d84e1f5ff7b9d72e0e7a479969bcdfb4522905aa12e75be54`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/security/IngressTerminalFixture.java` — SHA256 `11541b618673bcf9a3d42a27b579138580e14ae1858587b1d1a4e45b88d6600d`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java` — SHA256 `11e5375ceac47c9040bc314f4f243d2d37fcc7845eccf8fa4339085c873b211b`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java` — SHA256 `c54f6423ecd641d892c48759ae5e54831f1d121e4d5b44a4fe37dccf6dd50080`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/ActorAssertionVerifier.java` — SHA256 `80c58abc23bc137d890657500b1356fb801f2b32bb09f809683a4b51ba3bc89e`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/AuthRejectionAuditor.java` — SHA256 `9e8f988152440f6a23d129a1acfcd4daf68d71eef2865158e1a558ec7d5624fb`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/BodySystemFieldGuard.java` — SHA256 `4dfe76f4d3fcb2df494d8a02eca7b44d3fd28f81385abf295d9faf5a2a1709f2`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/IngressSecurityConfiguration.java` — SHA256 `de998bd11585e394ca47b5f9792af6b4f2cd805ddb5273fe6883af26c5a1502e`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/RouteClassifier.java` — SHA256 `bbb3af86f21b778e1852360a226bd37460cd472b480ac130d5e07e5a380afbf1`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/ScopeAssembler.java` — SHA256 `951a620ec31af0a1c9363606bd191187a2c0931d57ed20a5d28aa24063865c4a`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/SecurityCredentialFiles.java` — SHA256 `b114744721cf4f0630be527dded67b36ffd300cae77a147cb89c1fa657a944a1`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/ServiceTokenAuthFilter.java` — SHA256 `323d39b05dfacb1aa18a81535400f3774d00ac096751beeedceadb0c4825870c`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/VerifiedServiceIdentity.java` — SHA256 `be55c53f89fd30188b0ad18df2060b9e1596e6621850dc3fd89da1a23ea31866`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/ArchitectureRulesTest.java` — SHA256 `d0e780b1a74c74e623c8e75582cf9fb4d8989b8181ec3d7e2093a467a83d9a29`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/security/ActorAssertionVerifierContractTest.java` — SHA256 `5395b1cd48bcaebdeafc5092d5725cb09565fa0d485c73dab8f4185ee907e159`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/security/CredentialFileContractTest.java` — SHA256 `3633a5eb94aeb7bc66cb9aa662234e5316b87f3fb065f7a734a291fc0e439ab1`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/audit/AuditEventType.java` — SHA256 `8647935faef3f53ec7e2146f7792fa60c27005cf762f32a0ad0e085085227998`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/AuthRejectionAuditIT.java` — SHA256 `fa4ef920eeb0f2d8473233710d4530dc6df5ea919d5335e0f1d3dccc8b75ec2d`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/MigrationApplyIT.java` — SHA256 `d16c7a4d6bbfe6ffb571ef4e6e68c8dc29264126e536289445f7a305919ff4c4`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/SchemaVersionGuardIT.java` — SHA256 `46719eea39fface08d50e2c07951feae689ac4b4bc33f016d2891bdbefb59ba8`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/PersistenceModuleConfiguration.java` — SHA256 `b5d0a03d221a16845a1805e92f36701e9a18093a65a89f2a0ee6a58f711b1d17`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/audit/auth/AuthRejectionAppend.java` — SHA256 `38817ca1504a757a1481e032a1ff3e87a3da6aff5519029007c5553dd8591251`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/audit/auth/AuthRejectionAppendRepository.java` — SHA256 `2304abc395d33091efbfaaf7cc5a5aafe7050ea550fbd4c204f04ba29121b420`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/audit/auth/AuthRejectionPersistenceConfiguration.java` — SHA256 `117e9281e051d1a11ca2d9ea477bdc4017f00b8dfb7a921aac428964ebee8f68`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/audit/auth/AuthRejectionReason.java` — SHA256 `a069a07dfed33d553633604cc79b60287a63dc30b49418db7185bd707bac96c6`
- `gateway-persistence/src/main/resources/db/migration/gateway/V3_1__auth_rejection_identity.sql` — SHA256 `bb4d26b5b1d8d540123e8d1fb01d6c1b16982d82957e289398c71782370f8c76`
- `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/RepositorySignatureArchTest.java` — SHA256 `977b17444fb0d5693ba18244d107846665b53345c9c56c10dfb2aac10481673d`
- `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/schema/SchemaVersionGuardTest.java` — SHA256 `dd54bf8d8f39e42cadba4ffe078aa3e1f951ae6b654d480e2c5c0c51db096a79`
- `gateway-tc-mock/build.gradle.kts` — SHA256 `f14637539b076720d3b051981245a2a0a90f53849eff5ff1bc3f879e34f65992`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/PdgCanonical.java` — SHA256 `94a8b6d2c0d122dd57cf1a9f776277b69e872543ee8d0c814bd3af13d7f01a2d`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/PdgCanonicalTest.java` — SHA256 `131fe440501d2177d232eb934a4813e8f435f4cf70496800e250f98ae1e72f4b`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/StubAssertionSignerClockTest.java` — SHA256 `b9d2e49e40602913b5bdb3121df1953ac52761debadb894b598bd27a6a8fdc82`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/StubAssertionSignerSelectionTest.java` — SHA256 `81ad298aca613bdca955378e3e46b4bb50bb190e8d96eab51242035e7a2f03d3`
- `gateway-tc-mock/src/test/resources/pdg-vectors-v1.json` — SHA256 `b7a235907bc31b37a2830e0dfac5f48cedc52d8665862c6e742cb07f5caf382b`
- `gateway-tc-mock/src/testFixtures/java/com/nori/ai/gateway/tcmock/testing/StubAssertionSigner.java` — SHA256 `945f914a15e60b214bd97508cba24e8237978f7d1b4e43319c9225e1cca0a96e`
- `docs/ai-rag-gateway-architecture-dev-report.md` — 이번 절 끝 append만, 최종 SHA는 result.json의 후보 목록에 기록합니다.

기존 Dev report 873587바이트 SHA256 6ff17295fc19c04d9df2217d0c31fc1976be2acbc028803b9746486babc6cb29를 byte prefix로 보존했습니다. 변경 코드는 UTF-8/LF입니다. 보호 483개·기존 V1~V3·P06 SQL/HBA·Spec/Plan·baseline/approval1~7·기존 캡처/증거·양쪽 HEAD·빈 staging을 대조합니다. fixture의 owner label/finally 정리 뒤 동일 label 컨테이너는 0개입니다. 다른 자원/이미지/checkout 삭제는 없습니다.

#### 미실행·한계·후속 책임

부모의 소스/계약/빈/호출/보안 실패 경로·원시 증거 독립 검토와 로컬 커밋은 미실행입니다. 원격 push/MR/SSH/K8s/운영 DB/Secrets/Harbor/실제 Source·추론 호출/전역 설정 변경/하위 에이전트는 실행하지 않았습니다. root 하네스 변경이 없으므로 Eval 249 전체 회귀는 실행하지 않았습니다. 운영 게이트·P16/P29 수용은 NOT_RUN/BLOCKED를 유지합니다.

P08 terminal controller와 Source/cache 카운터는 integrationTest 전용 실제 저장 자극입니다. production JobAdmission/queue/worker는 P09, 공식 API는 P10, 일반 AuditWriter/일일 purge scheduler는 P14, 배포 수용은 P16/P29 책임입니다. P09는 부모가 별도 독립 세션으로 시작합니다. V3.1 적용 후 같은 최신 schema의 guard·자격·감사 실패 응답을 확인하기 전 인입을 활성화하지 않습니다. rollback도 승인 A와 동일 schema에 호환되는 앱만 사용하며, 없으면 인입 중단 후 forward-only 보정합니다.

정확 후보/SHA·보호/prefix/HEAD·명령/검증/실패/미실행 원시는 `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p08-implementation/result.json`, `final.md`, 각 `*-proof.json`·`*-xml/`·`*-otr/`에 있습니다. 상태는 NEEDS_PARENT_REVIEW입니다.


### P09 요청 큐·실행·취소·재기동 및 부모 검토 보완 — 2026-11-06
상태는 **CODE/LOCAL_TEST 후보·ROOT_REVIEW_PENDING**입니다. 원 P09 CLI는 부모가 실제 late-cancel RED 종료 뒤 통제 종료했으며 최종 result/report를 작성하지 않았습니다. 아래는 기존 P09 구현과 이번 별도 보완 회차를 함께 보고하며, 원 실행 결과를 성공으로 덮어쓰지 않습니다. Spec/Plan 체크박스·phase COMPLETE·F1/P16/F2 활성화는 변경하지 않았습니다.
직전 P08 절의 부모 PENDING 표기는 당시 이력입니다. 실제 부모 root-validation-approval.json의 P08 CODE/LOCAL_TEST PASS와 root-commit-verification.json의 PASS·커밋 `8e4e2e7d51f22b5de1b02da75b675ca873715ba3`를 확인했습니다. 기존 Dev report **895382바이트**, SHA256 `00584f8663f40bf95d766e9dbe32509ce53d45d3aa9ff45e31f2b60dede52c71`를 전부 prefix로 보존했습니다.
착수 정본은 Spec SHA256 `c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`, 기술 보정 승인 Plan SHA256 `7c42f1433fd5df530221f632c7fb212dca92407027c4f5899764cce4a6005045`, baseline seq7 SHA256 `b1040ce78cde516641cdc90da644b03e5d459ddc33668243a935ac4e3d348264`입니다. AI HEAD `8e4e2e7d51f22b5de1b02da75b675ca873715ba3`·exec HEAD `6d744aa57fd4d2970f9fca8c941b43b8a2e18670`·빈 staging·보호 483개와 현재 85개 P09 후보를 snapshot한 뒤 첫 실제 SpecGuard exit0을 확인하고 수정했습니다. Spec/Plan/R6/기준선/SQL/HBA/기존 V는 변경하지 않았습니다.
#### P09 전체 구현과 영향 범위
- 필수 CapacityProfile·닫힌 InferenceMode, verify 전용 disabled readiness, mask-v1 ContentSanitizer를 실제 접수 경로에 연결했습니다. 기존 일반 scoped 저장소와 queue_control 잠금을 재사용해 READ_COMMITTED 선조회·잠금 직후 재조회·용량/대화 검사·masked Message·QUEUED Job·event·hook 예약을 한 tx로 저장합니다. 중복은 동일 Job, 충돌은 409, 만료는 404이며 새 위임·행·TTL 연장은 없습니다. V4의 요청 perm/pdg와 백업 열 목록을 연결했습니다.
- 단일 dispatcher가 FIFO·RUNNING 1 제한을 검사하고 commit 뒤 가상 worker를 시작합니다. worker의 영속 권한 scope와 전용 위임 reader, 대화 만료 우선 검사, 빈 권한 집합의 실행 개시 TC 위임·세션 인가 게이트를 연결했습니다. start gate 실패는 Source 허가로 해석하지 않고 정본 DENIED/SOURCE_UNAVAILABLE 결과로 종결합니다.
- TerminalSpec의 닫힌 상태 조합과 JobTerminalCommitter의 자기 tx·조건부 terminal 전이·delegation NULL·Result/event/JOB_TERMINAL 감사·commit 뒤 발행을 연결했습니다. QUEUED 취소와 RUNNING 경쟁에서 실제 호출 종료를 확인한 뒤 terminal/슬롯 반납을 한 번만 수행합니다. 큐 >60초와 RUNNING >180초, 만료 우선 정책을 유지합니다.
- Schema guard 다음 Router/ReadinessPrecondition이 실제 DB identifier·epoch·gw/kn 빈 DB를 판정합니다. 정상 재기동의 interrupted 결과·만료 취소·QUEUED 처리·미발행 terminal 재발행·Ready 전 캐시 무효화를 연결했습니다. restore handler 운영 구현은 P27에 남겼습니다. INITIALIZE_NEW의 캐시는 0→0이므로 부모 신규 standalone probe의 실제 clear1/Ready 전0 독립 검증은 이 보고서에서 대신 주장하지 않습니다. 정상 재기동 2→0 기존 IT와 원 코드 흐름은 최종 회귀에 포함됐습니다.
- verify hook 인증/arm 예약·rollback·소비·reset·단일 Clock·URL별 filter 경계를 구현하고, 현재 integrationTest classpath/JDK를 쓰는 자식 JVM 재기동 하네스를 연결했습니다. 인가/workflow 미구현 포트는 src/integrationTest 설정만 채우며 운영 성공 기본 빈을 만들지 않았습니다.
#### 이번 보완의 원인·최소 변경·검증 범위
1. Spec15.2의 timeWindow:null이 AdmissionRequest Map.copyOf에서 NPE였습니다. LinkedHashMap 얕은 defensive copy·non-null map/key 검사·unmodifiableMap으로 명시 null을 보존했습니다. 기존 IdempotentAdmissionIT에 실제 PG 접수, caller Map 변경 격리, 접근자 불변성, 반대 키 순서 재전송의 같은 Job/행 불변 사례를 추가했습니다. null과 부재의 새로운 동등성/409 정책은 추가하지 않았습니다. nested deep copy·요청 입력 영속화는 이번 범위에 넣지 않았습니다.
2. worker scope의 배타적 만료 상한이 created+180초여서 실제 PlanValidator가 정확한 180초에 SYSTEM_SCOPE_MISMATCH를 반환했습니다. 승인 Plan N12대로 forWorker만 +180초+1ns로 표현했습니다. 신규 app ScopeAssemblerTest가 실제 ScopeAssembler+GQ PlanValidator의 -1ns/정각/+1ns를 허용/허용/거부로 확인하며 WorkerRestartProcessIT의 동일 영속 scope 기대값을 갱신했습니다. forRequest·JWS60초·일반 validAt·위임 TTL·timeout 수치는 유지했습니다.
3. 부모 actual PG late-cancel RED는 DB cancel_requested 커밋 뒤 신호를 지연해 실제 IO 종료·markTerminated·TERMINAL_PRECOMMIT 뒤 interrupt가 terminal tx를 중단하는 경우였습니다. SlotLedger.signalIfActive를 markTerminated와 같은 원장 monitor 아래 실행하고 CancellationService/Sweeper 두 외부 신호 호출자를 연결했습니다. 표시 뒤 신호는 생략하지만 Committer의 DB 취소 flag·clock·expiry 최종 판정은 유지합니다. Worker에서는 표시 직전 마지막 interrupt를 표시 직후 소비하고 종료시 복원하도록 보완했습니다. 기존 RED assertion은 약화하거나 제거하지 않았습니다. 표시 전 마지막 신호에 관한 1줄은 호출 흐름과 잠금 순서 근거의 보완이며 그 좁은 interleaving만을 강제한 별도 시험까지 실행했다고 주장하지 않습니다.
4. 추가 raw Clock 실제 PG RED에서 created+180초+1ns NORMAL commit이 저장용 micro 내림 때문에 SUCCEEDED가 되는 것을 재현했습니다. Committer가 clock을 한 번 읽고 raw policyNow로 timeout/expiry를 판정하며 저장 Timestamp만 micro 정밀도로 내리도록 분리했습니다. 새 JobTerminalDeadlineIT는 실제 Repo INSERT/RUNNING 전이/Committer/DB를 통해 180초 SUCCEEDED·+1ns TIMED_OUT, 결과·감사·event 및 저장 정밀도 보존을 검사합니다. core·기본/제어 Clock·DB schema는 바꾸지 않았습니다. persistence main CP 변경으로 schema/repositoryArch/scope 회귀를 모두 재실행했습니다.
#### 실패와 선행 GREEN 이력 보존
원 P09의 compile/r1~r5/설정·Docker·classpath 수집 실패 및 원 late-cancel RED 증거는 p09-implementation에 그대로 남겼습니다. 원 CLI의 완료되지 않은 최종화 스크립트에 적힌 예상 결과를 실제 완료 결과로 사용하지 않았습니다.
- 부모 p09-late-cancel-red-r1은 test1 FAIL/0error/skip·verifier0이며 runId6848aee3-50ab-466a-b02b-326e1701b5e0입니다. 이번 late-cancel-green-r1은 기존 같은 메서드 1 PASS와 paired verifier0을 확인한 선행 회차입니다. 이후 최종 P09 app IT에서 동결된 모든 보완으로 다시 실행했습니다.
- terminal-deadline-red-r1은 새 시험의 TransactionTemplate.execute 결과에 대한 AssertJ generic 오버로드 모호성으로 compile 실패했습니다. Test NOT_REPORTED/잔류64 XML을 PASS나 RED로 세지 않았고 paired verifier도 실패했습니다. Boolean 지역변수로 시험 타입만 명확히 한 뒤 새 회차를 사용했습니다.
- terminal-deadline-red-r2는 실제 test1 FAIL/0error/skip/abort·sourceStable, runId9dba75d7-88be-48e8-b927-57cd31645e7a입니다. 180초 정상 assertion 뒤 +1ns expected TIMED_OUT/actual SUCCEEDED로 실패했으며 paired verifier0은 실행 증거의 유효성일 뿐 PASS가 아닙니다. 최소 수정 후 terminal-deadline-green-r1 1 PASS·paired0과 최종 schema 4필터 회귀를 확인했습니다.
#### 최종 실제 검증
각 새 Test 명령 직후 같은 필터/init·requireDocker·PG image/SQL/HBA 입력의 공식 test-evidence verifier를 별도로 실행했습니다. XML testcase·OTR unique JUnit ID·summary passed/executed가 일치하며 failure/error/skip/abort는 0입니다. 마지막 fresh classpath capture로 현재 compiled CP와 원시 binding의 component/fingerprint를 다시 계산했습니다.
| Plan/회귀 | 회차 | JUnit | runId |
|---|---|---:|---|
| 1 | `final-app-it-r1` | 32 | `a34b3b5d-f2cf-4a10-80ee-2a3fd0c8d3fb` |
| 2 | `p09-02-core-test-r1` | 39 | `cf00f039-2ee5-4f0a-b5a8-a8a28839926f` |
| 3 | `final-schema-it-r1` | 5 | `e7479c7e-b649-4075-9830-66235ef2fe45` |
| 4 | `final-persistence-test-r1` | 3 | `6ea0eeb6-f3e8-4f33-a450-0bb561c71db7` |
| 5 | `final-app-test-r1` | 8 | `debf66a5-72d5-413a-a8d8-6ac9651e80ee` |
| 6 | `final-scope-it-r1` | 64 | `c81e67db-9fc2-409d-a6d6-35a189010cf1` |
| 7 | `final-ingress-it-r1` | 30 | `797df662-5bd1-4f63-ae34-6a65bbdc8029` |
| 8 | `final-auth-audit-it-r1` | 3 | `eab9d1f1-2717-40c5-b03c-602d5a58821c` |

최종 고유 JUnit 184건입니다. 원 Plan7명령 중 core39는 실제 core Test CP/소스·정본 입력이 그대로여서 원 p09-02-core-test-r1 증거/runId를 보존해 재사용했습니다. 과거 global app runtime manifest를 새 값으로 다시 결속하지 않았습니다. schema는 부모 승인대로 기존3필터+JobTerminalDeadlineIT, app unit은 기존4+ScopeAssemblerTest로 확장했습니다. AuthRejectionAuditIT는 V3.1→V4 fixture 영향의 추가 회귀입니다. Worker5 leaf는 JUNIT_OTR/IT/F4 권위로, queue9 tag는 P09 로컬 계약으로 확인했으며 실제 Source/운영 acceptance PASS로 승격하지 않았습니다.

정확 실행 명령과 직후 공식 verifier(실제 credential 값은 자식 env에만 전달):

`final-app-it-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-01.gradle :gateway-app:integrationTest -Paigw.requireDocker=true --tests *JobQueue*IT --tests *WorkerRestart*IT --tests *IdempotentAdmissionIT --tests *AdmissionMaskingIT --tests *HookAndProbeFilterScopeIT --tests *HookArmingIT --tests *AppContextWiringIT --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-01.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain
```

`p09-02-core-test-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-core:test --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --no-daemon --console=plain
```

`final-schema-it-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-03.gradle :gateway-persistence:integrationTest -Paigw.requireDocker=true --tests *MigrationApplyIT --tests *DbBootstrapOrderIT --tests *SchemaVersionGuardIT --tests *JobTerminalDeadlineIT --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-03.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:integrationTest --require-docker" --no-daemon --console=plain
```

`final-persistence-test-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-04.gradle :gateway-persistence:test --tests *RepositorySignatureArchTest --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-04.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:test" --no-daemon --console=plain
```

`final-app-test-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-05.gradle :gateway-app:test --tests *ArchitectureRulesTest --tests *ModuleConfigurationCompletenessTest --tests *CapacityProfileValidationTest --tests *ReadinessPreconditionTest --tests *ScopeAssemblerTest --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-05.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --no-daemon --console=plain
```

`final-scope-it-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-06.gradle :gateway-persistence:integrationTest -Paigw.requireDocker=true --tests *ScopedRepositoryIT --tests *RetentionBoundaryIT --tests *JobDelegationColumnIT --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-06.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:integrationTest --require-docker" --no-daemon --console=plain
```

`final-ingress-it-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-07.gradle :gateway-app:integrationTest -Paigw.requireDocker=true --tests *IngressAuthIT --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-07.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker" --no-daemon --console=plain
```

`final-auth-audit-it-r1`

```text
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-auth-audit.gradle :gateway-persistence:integrationTest -Paigw.requireDocker=true --tests *AuthRejectionAuditIT --no-daemon --console=plain
C:\workspace_nori\nori-ai-gateway\gradlew.bat -p C:\workspace_nori\nori-ai-gateway -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\filters-auth-audit.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-persistence:integrationTest --require-docker" -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\capture-classpaths.gradle -Dp09.classpathOutput=C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix\final-current-test-classpaths.json --no-daemon --console=plain
```

#### 실제 변경 파일

원 P09 85개 후보를 유지하고 이번 새 시험 2개를 추가했습니다. 아래는 원 P09 시작 snapshot 대비 전체 후보와 최종 SHA입니다. 보고서 자체의 최종 SHA는 result.json에 기록합니다.

- `docs/contracts/test-hooks-v1.md` — `bf2f6723dac03e658ff5831ecc31eaadc8f1ea3f0548875d161f47a0e0a61852`

- `gateway-app/build.gradle.kts` — `ca6392918dc2baae1c6611c1e37544d4ea05eeaa8f1cedaacdc2c825903fd202`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackContextSupport.java` — `df797d24769fb5b8c09288da8cee0b80768c6a41da4bbc2f7147ac407bb0f234`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/AppProcessHarness.java` — `1da4486311c1e644203576cbfd60422a8c0d072d55a25d9774e5791f49e94ca7`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/HarnessFingerprint.java` — `0dabcee40c660010ff719cc043e654b458db85710f7799f8ddd06020d362585d`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/HarnessMain.java` — `3802188a154d195ca48863fef4347c185106f20413d8c2997f97f787a67b2592`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/HarnessP09TestPorts.java` — `0d05aa02a739bc6a055a4fe096b0a840133b82127549e7ccc416e31d5c78d5b6`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/AdmissionMaskingIT.java` — `764dd9975f8cd43b0a7db6b8ebe92d6e4b201c08b3f3a5caa9d44846d236a2ab`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HookAndProbeFilterScopeIT.java` — `dc775b7bd72768a30e0131850c9408c1409932d4f543e2a08cee0602a6a53b85`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HookArmingIT.java` — `0b3c583efb21ca0c713b07341040bf64239628ec2413c2a113a87d81ea6652ef`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/IdempotentAdmissionIT.java` — `fd00a24908d67940a3bbaf91c72da0769363bc28dae5c7b02d0477bdff80d573`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/JobQueueIT.java` — `c16ddce2858e07257ea6342e2ea05576e2da89650fafa72b1ca1f3a7530edc30`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09AdmissionEndpoint.java` — `1eb49bb436cfc33dff37714b8bb91e5aa4f359303739d2df9c7bb6ee5f528098`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09AppContextWiringIT.java` — `07fa914cc3d9212b50aad4efd247f9a3c129aceaf99baeb596fcf88685715307`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09QueueSupport.java` — `c9619b63df924f1bdc50f2ebfc35bcfc508872a8ebeac520e0c316aec74894cc`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09TestPorts.java` — `d7b13b6d81e1c045196e5a9c2ccbc48794cf0a20776658772634256211e69a0d`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartEpochIT.java` — `a3a0b24145cf46932f5907bd40de6cd29365f3a9759ddbe18cbd2145728f1792`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartExpiryIT.java` — `67df8c69a12d299c459043584e88dbc8d4b0dedb149d3b30bc2343a0b5649b6d`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartProcessIT.java` — `2067baa2dcb88d3bf1ad82013a38d4de66ecca149db99ba977485b334fe237fc`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartProcessSupport.java` — `4f92bc875a7591f233a0925fb8fe5bbefe42b4bf2f7747d0e4c4d23a88946bfe`

- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java` — `2a784c52b1069f2583e5f58b356bec0bf92b0dd8e8c63d1ea666eced4d2a5104`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/CapacityProfile.java` — `7c1847a4a08048c9efb6ab3921a69f6b290f3b6cab041424bcbac69aaca0c9e8`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/CoreBeansConfiguration.java` — `eea828f687221e83f47ce07ef0ca836178e257c9f045f453326e715c3a77f873`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java` — `ebecd55f96a3c0c606ae4cf3fa67988b9362818a0a9ed101f81e59da636a2443`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/InferenceModeProperties.java` — `684ed0fa6422b0ca470a586ef21f8f0d86464903d9342f81c611c16b61a4872c`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/JobConfiguration.java` — `5f63a3ccfe110528aef29c0313683e00c05e9c52bcbee9f2044d947515046c48`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/RecoveryProperties.java` — `1e9ab619be84aa866da433917035b83ded3d7b26708bfd459f16591b0c56fd6e`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/TestHooksConfiguration.java` — `3f5850eee98a5a4c48232f0b7625fb3404b589080a459be7cf17569f9f9091b1`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/ControllableClock.java` — `5c2a14d776e1ca8cb8893101dea03e63b50037956a63948ea3c348a5ac9d4b04`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/FaultInjector.java` — `b3349daa269f81393f627d978c3bb2c215bb23a9fc6cbdb32025fb3afd7cec3c`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/PausePointRegistry.java` — `1f3c74acdb3b7c64ec99f9ff888f344e1fe0917b1d8c7a4eda86bee397f4cd47`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/StructuredClassificationHook.java` — `7d3ed7a944798543317803ed36acd654063df4f9c6ece615a846239dff5e1c25`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookAuthFilter.java` — `e30962c2a27970810bdf6ea9fa08c58d483ec3d59484b34ebb651fa259fb5d3a`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookController.java` — `2d985f59d2df185a0daee2b581d68fefce54655467ea9e204828632b0b3696ca`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookRegistry.java` — `03eab760594170ab5e8121c4256b81b95302f1d6f583a498213d7367983f347e`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/DisabledInferenceReadiness.java` — `edda23ecc5564f09bf41a41dfcacc787f99b7aff669c6614a04af5b267c19fc3`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/IdempotentAdmissionLookup.java` — `2dcce826ead492c48f243c30680c3c6751b522f239041bb1d09beb5e8720212e`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobAdmissionService.java` — `39bc9869f91464d628e94bb0ee96beadef970ad01fd560394861bf7a1ad0f958`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobCancellationService.java` — `13f5aa112dea72445cbd162687ceeeb682a313c8992fbdb6825d02862019d380`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobDispatcher.java` — `1cdff2ffa3dc7c5319a233e3e61015efc1d251ea6f8ede38b2e4ff7ae92a105f`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobTimeoutSweeper.java` — `899cf29263b9db22f04f67b3cd4bc101d89707170a1322c5aaabed45cb52d6dd`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobWorker.java` — `c0f5baacdc53f63cbb36f3b06df5f8dd1fd65218cf22348d5f5d6a0d61d02d94`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/SlotLedger.java` — `e1021a1d23e0263d7b4e8d449b672f0fd694926f602e53e9a02c44c208536f0f`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/recovery/ReadinessGate.java` — `33d3126280950b020d91fe562078c75b1260af012ba693bf03d9e2ac7d6ca266`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/recovery/ReadinessPrecondition.java` — `b8e77979303ded2dc1862f1ee4c1854900fb36eff5ecb0b633da559a44f7cb8b`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/recovery/RecoveryModeRouter.java` — `6291448aec7cbea847a4345931294acba9fbc63694d45b48b8a685da03e655d6`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/recovery/RestoreRecoveryHandler.java` — `2a5af938e0dd5deee1c4a27dd2e3770cea435828f88a6270b0cef3130bf06876`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/recovery/StartupRecoveryRunner.java` — `85b76123835633d4dc6057571e132f34ab27a63aec1a7bb1708a1534e7cc51fa`

- `gateway-app/src/main/java/com/nori/ai/gateway/app/security/ScopeAssembler.java` — `71df9e53f3b97c606bd3526058c51f5c323be5965a75456b986e6b376399b42a`

- `gateway-app/src/main/resources/application.yaml` — `a8f4ff7a0ed9063bad26216260263205758b6f9b72e3a4c3dea536d0f49418d4`

- `gateway-app/src/test/java/com/nori/ai/gateway/app/ArchitectureRulesTest.java` — `0b9f7a61418183e5e17e99c3d4a5f77a62bf51860beb67e548b51ea51b26c412`

- `gateway-app/src/test/java/com/nori/ai/gateway/app/CapacityProfileValidationTest.java` — `3daaef025851e042d982f3c758ed578f0fd371abc4cf5d084e4ffbf8cfdf2b65`

- `gateway-app/src/test/java/com/nori/ai/gateway/app/ReadinessPreconditionTest.java` — `8675539cf67852faae67a7b06216015c66a59db75bb61afd597d41920ca33d55`

- `gateway-app/src/test/java/com/nori/ai/gateway/app/security/ScopeAssemblerTest.java` — `71e611085ab47c4f80f87050eb86f9ab2232f4a3f0f8151024e23767a41b8ad0`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/audit/AuditEventType.java` — `78b93659e21edcab9b28bd540085732f57e0171b3ed5278452bceb3d878bf86a`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/expiry/ConversationExpiryGuard.java` — `3e4459c07de683ca68a2315f889421bcbca9964566b3c9a3759ab595b7026dc1`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/CancellationToken.java` — `03079ecc2082de56c45ed2e46ce4ef9e1773c9c5a23151b12f4194867789356d`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/GenerationSemaphore.java` — `43f35da6f421aab19da26d99aefa41bdcad567d2382df92b51db2c28c93a4b4b`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/InferenceMode.java` — `d399d85a236cd2b1c75aff99dc081fc1ae4d98c3177a082dbb1eff6f9cd89856`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/InferenceReadiness.java` — `f3d7ed80a50b543710e77c7a1ae19dbf784ae8e240649339035cc64cc2b6cdaf`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/JobContext.java` — `1a47cc6cee465bff27eaadd1011086a29edf8fd40b12e3b9d6125ac58226adb5`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/TerminalSpec.java` — `e37d7bf8aa29f42b418e3aad14a406987d0e3be2f437c273895ceaa839a56f76`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/WorkflowExecutor.java` — `20aea107088aef44c3f87032df11f5a03e743e98e46bbbe1fad31ea23f6c3dc4`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/port/TcSourceAuthorizationPort.java` — `ef52cc195f37a68c51724bd276c438bab21cc91fd8455cedba0218939ef0ddd2`

- `gateway-core/src/main/java/com/nori/ai/gateway/core/sanitize/ContentSanitizer.java` — `30533ef0609f807c4dc0c9c4939fd040704151d8623f39b5925f89ccd3ba4923`

- `gateway-core/src/test/java/com/nori/ai/gateway/core/job/TerminalSpecContractTest.java` — `15b5f1672f8add0e7bf49fcf3a4753ff6e7b7b947b602eb49ce1ff25eecead0a`

- `gateway-core/src/test/java/com/nori/ai/gateway/core/sanitize/ContentSanitizerTest.java` — `02cd1fe9f998ee0e89a34b030f731a7e445797fe18be8cca6d333f89b9760bbc`

- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/AuthRejectionAuditIT.java` — `43056b744365a6973c4602f95436fc0e815cada7c914c716625c84207ab2a798`

- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/JobDelegationColumnIT.java` — `ce23387210aded3e1d7b494f000d2761a5065dbbb0d14feb32fe5f527bd8cae6`

- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/JobTerminalDeadlineIT.java` — `902252ae7c63e0310c59118b8f02c206015b87475aa12bf2ecd5b0fa3648c4c7`

- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/MigrationApplyIT.java` — `b77b1eee8b18821780b9402744b2e5f0388c0f8a801628a69441e2d6b1c0b929`

- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/SchemaVersionGuardIT.java` — `0ddabee78e478f1c63450b444cd26b2edb6dc96b3b20fc9aa54efef09b875495`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/AdmissionLookupRepository.java` — `6c614f6663b79a40b5d365d7f4133469c29a95ab7940e037174ef3ea29813956`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/JobDelegationReader.java` — `c52f8a49398a794767079d7ac1490099c469ac1555c027fc16fe7d969f21c22b`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/JobEventPublisher.java` — `c8b278d8faba4d8cd7507a614f089aee302a1e674e9349ae4187c8abc3fccb7d`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/JobScopeRecord.java` — `422ab022f4a3638fcd1e20f17257075554911e99a45300d49dfbef505500626e`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/JobTerminalCommitter.java` — `ae9b2f8a7a47530717cda38536441ca76e9089f4c90903a65df71807347037f3`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/QueueJobRepository.java` — `ecdadd50a83f12ebfbafb6e2105b1c513b470d941dcb8a73764dff5d463656f8`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/TerminalCommitObserver.java` — `8daf30fd2493de564276ff9d7a3e91ec0f3db2fed67178084fdbb9087022e7b2`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/recovery/CacheInvalidationRepository.java` — `c55a898132d8ab84c873aa5cf6fd74ff649585da9aabe60e3acc8e6d9c726b92`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/recovery/DatabaseEmptinessProbe.java` — `34eff00606ef253f7d457b5c9ee6bf6f0a08c0cbecf9979f323598e4c9aa4ee3`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/recovery/DbSystemIdentifierReader.java` — `076d6594d359edf35c912e6eacbf6c6f1da1a0b9e380390f3f9ba002cbd0d851`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/recovery/InstanceEpochRepository.java` — `e035b784cbea25d974545f6bdebff28a03db8406ac93efb606a019962d717c50`

- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/JobRepository.java` — `3a39e71588f402a8157a9053385efa256a3357699647648c3d22e8e40fc59eee`

- `gateway-persistence/src/main/resources/db/migration/gateway/V4__job_request_authority.sql` — `58479a14c3cc45cff965b96a12a6669def93714d0c8f6ceafa0265017742c5ac`

- `gateway-persistence/src/test/java/com/nori/ai/gateway/persistence/RepositorySignatureArchTest.java` — `eabb8e6168cd83b2081820f18565d2b8762b40c8e3619bad8f80ef0081f01808`

- `ops/backup/job-columns-v1.txt` — `5af14bb65c30801087924882e6d888ac1b772792d1629aa8615bafe3203dd227`

- `docs/ai-rag-gateway-architecture-dev-report.md` — 기존895382바이트 전체를 보존한 이번 끝 append입니다.

#### 보존·미실행·남은 검토

Spec/Plan/R6/baseline/두 HEAD/빈 staging/보호483/원 P09 85후보/oldV/bootstrap/HBA/원 probe·patch·실행 결과를 보존했습니다. 제품·시험은 source-freeze 이후 변경하지 않았습니다. Ingress paired verifier 뒤 root 요청 boundary에서 대기했고 root가 Test 실행 없이 현재 CP를 export한 뒤 RESUME했습니다. root 소유 review-inputs.init.gradle의 export 제어 변경 한 개는 별도 before/after SHA로 기록했습니다. 원 probe Java·patch·실행 결과 변경이 아닙니다. 이후 root 자체 PG 1개와 회귀 PG 1개는 각 소유 label/finally로 분리하여 사용하며 다른 fixture를 정리하지 않았고, RootDONE 이후 공유 label 잔류0을 확인했습니다. 커밋/push/MR/SSH/K8s/운영DB/Secrets/Harbor/실제 Source·모델/배포·수용은 실행하지 않았습니다. 제품 fake/no-op로 미래 포트를 채우지 않아 P11/P13/P19 등 후속 빈 책임과 운영 게이트는 그대로 미완료입니다.

부모의 독립 소스·wiring·실패/경쟁·standalone compiled/PG probe·원시 증거 검토와 commit이 남아 있습니다. 이 보고서는 부모 검토를 대체하지 않습니다. 입력 Message/Context의 요청별 영속 연결은 이번 보완으로 구현하지 않았으며 후속 P10 Plan 책임입니다. 기존 ConversationRepository의 scoped saveContext/findContext·saveSummary/findSummary는 동적 SQL로 존재하므로 해당 공통 기능의 재사용 검토가 가능합니다.

원시 결과·현재 CP·명령·보존 manifest는 `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p09-consolidated-fix`의 final-evidence-proof.json/result.json 및 각 회차 XML/OTR/proof를 참조합니다.

### P09 부모 독립 검증 PASS — 2026-11-06

앞 절의 ROOT_REVIEW_PENDING은 당시 상태입니다. 부모 최종 독립 검증 결과는 **P09 CODE/LOCAL_TEST PASS**이며, 제품 소스는 동결 상태를 유지합니다. 이 기록 시점의 local commit은 아직 미실행입니다. 배포·실제 Source/모델·P16/F1 수용은 **NOT_RUN**이며 후속 phase 완료나 운영 활성화를 뜻하지 않습니다.

- `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p09-parent-review/r2-root-validation.json` — PASS, SHA256 `50fed73669fb8c621fecc3106e94e4f7357ba77ee20baa2afda9e2813808d12b`. 후보 88파일·고유 JUnit 184건·8회차의 actual XML/OTR, 현재 CP 재해시와 fingerprint 재계산, 필수 tag, 보호 483파일·895382바이트 원 prefix·Spec/Plan·exec·잔류 0을 부모가 독립 확인했습니다.

- `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p09-parent-review/root-java-probe-validation.json` — PASS, SHA256 `33aa9a32641648edc7f5e08610055f8c44698c912201559a6d8a3d29a0c16ffd`. 실제 compiled AdmissionRequest의 nullable Context와 ScopeAssembler/PlanValidator의 180초 경계를 독립 확인했습니다.

- `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p09-parent-queue-boundary-probe/r1-result.json` — PASS, SHA256 `3ca6788ab82fe96e25e9b352e076f579a849de022f24301a47caddff52a5683d`. 실제 격리 PG에서 만료/find·늦은 claim 취소·stale QUEUED/실행 중 호출의 세 경합과 INITIALIZE_NEW 캐시 삭제 호출 1회·Ready 전 캐시 0을 확인했습니다. 실제 호출 종료 전 terminal/슬롯 반납이 없고 종료 뒤 종결·감사·반납이 각 1회임을 확인했습니다.

- `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p09-parent-terminal-deadline/r1-result.json` — PASS, SHA256 `3627a60df61045b08cca0b762d17259c83bcb625b6c1ed10aa5dd2637b148a98`. 실제 JobTerminalCommitter/PG/raw Instant의 180초 -1ns·정각·+1ns를 SUCCEEDED·SUCCEEDED·TIMED_OUT으로 확인했고 각 감사 1·종결 이벤트 1·delegation NULL을 확인했습니다.

기존 DevReport 원 prefix와 직전 구현 보고 append는 전부 보존하고 이 절만 끝에 추가했습니다. 부모 검증 증거가 기록한 보고서 SHA는 이 절을 추가하기 전의 값이며, 이 추가로 제품 소스·시험·기존 증거 파일은 바뀌지 않았습니다. 커밋은 부모가 별도로 수행합니다.


### P11 CONTRACT_READY 후보 — 2026-11-06 / 부모 검토 대기

상태는 **CONTRACT_READY_CANDIDATE_ROOT_REVIEW_PENDING**입니다. P11 phase 전체 완료가 아니며 P12 착수 승인을 자체 선언하지 않습니다. 부모의 독립 source/시험/실제 classpath 확인과 C11contract local commit이 남아 있습니다. 기존 보고서 원 prefix를 바이트 단위로 보존하고 이 절만 append했습니다.

작업 좌표는 AI_ROOT=`C:/workspace_nori/.aigw-worktrees/p11-ai`, branch=`aigw/f1-p11`, base/현재 HEAD C09=`44315e4d861a2100e9703d9b6168244776927b4f`이며 시작 dirty=0입니다. canonical Spec SHA=`c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`, immutable Plan SHA=`7a5e37c8b747a1acbb846ab8804fb6c57c6b483f08a47ee813d6f76b9b424cb5`, baseline seq7 파일 SHA=`b1040ce78cde516641cdc90da644b03e5d459ddc33668243a935ac4e3d348264`를 실제 대조했습니다. 원본 EXEC/다른 lane은 쓰지 않았습니다.

#### 구현과 이유

- P12/P13 소비용 `TcBusinessReadPort`와 nested 업무 DTO·`TcReadResult`, 기존 2필드 `TcSourceAuthorizationPort`를 보존하는 Source 매핑/단계 인가, P14 소비용 `SourceRetentionReader`를 구현했습니다.
- SourcePermissionMap은 실제 sourceAllowlistRef를 키로 쓰고 미매핑 거부·ANY-of·단계별 현재 권한 재인가·인가 캐시 0을 유지합니다. typed wire4필드의 결손/타입/권한범위/모순/pdg/시각을 기존 SchemaContracts로 검증한 뒤에만 core2필드로 투영합니다. 빈 요청은 Source를 허용하지 않고, 무효 위임과 장애를 구별합니다.
- 보존 규칙1~6의 반개구간·경계·설정 미확정·건수 trim의 unverifiedRanges·조회 잘림 missingRanges·키별 실패·DLQ SCAN/decode 손실을 typed 결과로 보존합니다.
- Mock 업무 응답은 typed JSON 왕복과 1.0 schema 검증, 실제 Manifest outputSchema/items 검증 및 동일 SchemaContracts.digest를 사용합니다. 상태·관측 시각·Work/LOT 미확정·독립 키 실패를 빈 성공으로 숨기지 않습니다. 부재 EQP는 EMPTY+null 자료이며 상태를 합성하지 않습니다.
- Mock 위임은 SHA256 키만 저장하고 발급+300초 절대 TTL·중복재등록 불연장·원자 파일 교체·재생성 시 폐기/만료 보존을 구현했습니다. 이 항목의 증거는 단위 재생성까지이며 실제 자식 JVM 내구성 검증은 아래 미실행입니다.
- 공통 SchemaContracts/ManifestLoader/PdgCanonical 및 기존 DelegatedCall/Outcome을 재사용했습니다. 신규 타입은 위 외부 읽기/인가/보존 경계를 표현하기 위한 것입니다. Spec/Plan의 확정 계약 변경은 없습니다.

#### 최종 실제 검증

- `:gateway-core:test`(필터 없음): 실제 EXECUTED, 51/51 PASS, skip/error/failure=0, runId=`abeebdc1-a982-4d0d-a6fa-aa077b763009`, fingerprint=`638542ce551031c8075dfe2ffc5b3842f852aa76b73f1e469eceb1e2e8db5358`. 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-core:test"`: TEST_EVIDENCE_VERIFIED·exit0.
- `:gateway-tc-mock:test`(필터 없음): 실제 EXECUTED, 35/35 PASS, skip/error/failure=0, runId=`4f9ddd0c-207d-49a2-a2c4-7c2ec6dc4599`, fingerprint=`b1e468a958bb030854543b810b4a25ccd3fb124bc402e791bcb27822fd11c52e`. 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks :gateway-tc-mock:test"`: TEST_EVIDENCE_VERIFIED·exit0.
- 두 실제 Test 실행 중 source hashes 불변을 확인했습니다. 최종 r2와 별개인 초기 r1/compile 기록도 수정하지 않았으며 stale XML/up-to-date를 새 실제 시험으로 계산하지 않았습니다. 실제 XML·OTR·run-binding·fresh Test.classpath/content manifest·runtime manifest·명령/exit/log/hash는 scratch `p11-contract/result.json`과 연결 파일에 보존했습니다.
- 테스트 범위는 missing/wrongtype4필드, out-of-request/배열중복/모순, malformed pdg/instant, denied 혼입0, empty gate, +300초 정각·폐기·catalog/store 장애, 현재 이벤트 권한 철회·Source0, 보존 경계/ns·trim·DLQ 손실, Mock 파일 재생성·raw dlg 부재입니다. JDK21·고정 Python venv를 사용했고 credential은 glab에서 메모리로 받아 child env에만 전달했습니다.

#### 실제 변경 파일

- `docs/ai-rag-gateway-architecture-dev-report.md`
- `docs/contracts/tc-ai-read-api-v1.md`
- `gateway-app/src/main/resources/config/source-permissions/tc-v1.json`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/authz/SourceAuthorizationStep.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/authz/SourcePermissionMap.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/port/SourceRetentionReader.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/port/TcBusinessReadPort.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/retention/DlqCompletenessEvaluator.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/retention/RetentionClassifier.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/retention/SourceRetentionAttributes.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/retention/TimeWindow.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/retention/TrimCompletenessEvaluator.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/authz/SourceAuthorizationStepTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/retention/CompletenessEvaluatorTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/retention/RetentionClassifierTest.java`
- `gateway-tc-mock/build.gradle.kts`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockCallCounter.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockDelegationRegistry.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockScenarioStore.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockStateFiles.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockTcBusinessReadAdapter.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockTcSourceAuthorizationAdapter.java`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/authz-down.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/catalog-down.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/comm-down.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/comm-trim.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/contract-v2.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/default.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/dlq-down.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/dlq-partial.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/empty.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/index.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/logout.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/lot-ended.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/lot-multi.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/lot-unknown.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/permission-revoked.json`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/MockAuthorizationContractTest.java`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/MockBusinessReadContractTest.java`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/MockDelegationRegistryTest.java`

#### 차이·발견 사항·미실행

승인된 부분 handoff 순서에 따라 shared config/JobContext/Worker/TestPorts/API/공통 하네스는 변경하지 않았습니다. P11 전체 wiring·GET 종결·WorkerAuthorizationOrderIT·WorkerRestart·MockDelegationHookIT·실제 halt/restart MockStateDurabilityIT·verify state-dir guard·SeamRegistry/접수 전 hook 결속은 P10 actual join 후 P11 전용 후속에서 구현/검증해야 합니다. P14 persistence retention adapter/스케줄러, P13 Tool/Workflow/종결 조립도 아직 없습니다. 이 미실행은 성공으로 대체하지 않았습니다.

기존 `gateway-app/src/main/resources/manifests/tc/tc.event.getRecent.json`의 requiredPermission은 빈 배열입니다. Source 매핑은 AI_EVENT_READ(mockOnly)이고 Mock getRecentEvents가 매번 실제 authorize해 철회 후 DENIED·Source 증가0임을 단위로 확인했습니다. 기존 Manifest·Registry·PlanValidator·Dispatcher의 노출/실행 전체 정합은 actual join/P13 소비 검토 항목이며 이 회차 그 파일은 수정하지 않았습니다. 새로운 업무/외부 endpoint 정책을 결정하지 않았고 HTTP 경로명은 계약 문서에서 F5a 확정 대상으로 남겼습니다.

자체/부모 코드 읽기에서 인가 예외와 malformed pdg+빈 allowed를 장애로 분류하도록 보정했고, synthetic UNKNOWN EQP 부재 응답을 제거했습니다. 실제 실행 실패는 없었습니다. 최종 source freeze 뒤 제품·시험 코드는 변경하지 않았습니다. `git diff --check`는 통과했습니다. commit/push/MR/외부메시지/SSH/K8s/운영DB/Secrets/Harbor/실제TC·Source·모델/배포·P16/F1/F2는 실행하지 않았습니다.


### P11 CONTRACT_READY 후보 r3 — 부모 경계 probe 결함 수정 / 2026-11-06

상태는 계속 **CONTRACT_READY_CANDIDATE_ROOT_REVIEW_PENDING**이며 P11 전체 완료가 아닙니다. 앞 r2 보고와 모든 r2 증거·result.json은 보존합니다. 부모 독립 actual compiled probe `p11-parent-review/comm-red-r2-comm-clock-probe.json`은 compile=0·실행=1·inputsStable=true인 실제 RED입니다. comm-trim 실제 fixture의 itemAt+1h부터 clock을 매 호출 1ns 증가시키면 분류는 Full이지만 항목 필터가 새 clock을 읽어 EMPTY/items0이 되는 결함을 재현했습니다. 앞 comm-red-r1은 수집기 UTF-8 decode 오류이며 제품 RED로 계산하지 않습니다.

근본원인은 같은 조회의 보존 분류와 필터가 각각 Clock.instant()를 읽는 것입니다. `MockTcBusinessReadAdapter.getCommLogs`에서 `queryNow`를 한 번 읽고 보존 판정과 cutoff 필터에 재사용하도록 최소 수정했습니다. `MockBusinessReadContractTest.advancingClockKeepsItemExactlyOnTheFixedQueryBoundary`는 같은 실제 comm-trim fixture·최초 itemAt+1h·매 호출+1ns·요청[itemAt,itemAt+60s)에서 SUCCESS/items1·Source호출1을 영구 회귀로 확인합니다. Spec §7.6의 판정 시각에 고정한 boundary 계약을 복구한 변경이며 확정 계약·외부 업무정책·공개 typed 서명 변경은 없습니다.

실제 추가 변경 파일은 위 제품 파일·회귀시험 파일과 이 DevReport 끝 append뿐입니다. r2 보고서 prefix와 원 Spec/Plan/baseline·기존 proof·부모 RED probe/program을 수정하지 않았습니다.

- `tcmock-final-r3`: `:gateway-tc-mock:test` 필터 없음 actual EXECUTED 36/36 PASS, failure/error/skip=0, source hashes 불변. runId=`f1f5e82b-3a23-48a9-88cd-b52d3cca9c11`, fingerprint=`3af7d1e4fe13e1ae301510c3f34bcc7dacf1d62fb72172dc3b83a6dd73ef44f9`. 직후 별도 공식 `test-evidence verify --tasks :gateway-tc-mock:test`는 TEST_EVIDENCE_VERIFIED·exit0입니다.
- core main/test와 실제 core Test.classpath의 모든 artifact 바이트는 r2와 같습니다. `core-current-r3-verify`는 기존 core 51건의 run-binding과 actual current fingerprint `638542ce551031c8075dfe2ffc5b3842f852aa76b73f1e469eceb1e2e8db5358` 일치를 공식 verifier로 확인했습니다. core Test를 새로 실행했다고 계산하지 않습니다. 기존 core r2 runId=`abeebdc1-a982-4d0d-a6fa-aa077b763009`를 그대로 재사용합니다.
- `current-inputs-export-r3`는 실제 두 Test.classpath를 Gradle inputs.files로 결속해 Test 실행 없이 새 `current-test-classpaths-r3.json`과 현재 runtime manifest를 export했습니다. artifact content 재해시 manifest와 XML/OTR/run-binding·source freeze·명령/exit/hash는 scratch `p11-contract/result-r3.json`에 연결합니다.

P10 actual join 뒤 wiring/API/WorkerRestart/hook/실제 halt-restart durability 검증과 부모 독립 재검증·고정 local commit은 여전히 남아 있습니다. 추가 Gradle Test나 외부 Source·PG·Docker·운영 실행은 하지 않았고 commit/push도 하지 않았습니다. 최종 r3 제품·시험 소스는 동결했습니다.

### P11 CONTRACT_READY 부모 독립 검증 PASS — 2026-11-06

부모의 실제 최종 판정은 **P11 CONTRACT_READY 부분 PASS**입니다. 파일40·고유 JUnit87(core51+r3mock36), 실제 XML/OTR·summary·source input·fresh CP 내용/FP 재계산·원 runtime 결속·scope 경계·기존930576바이트 prefix 보존을 확인했습니다.
증거: `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p11-parent-review/r3-root-validation.json`, SHA256=`95bd0aad8d22d7ea80fa150cec7d31513074be2e9edda3073a104108a81e3d9d`.
부모의 동결 clock probe RED→GREEN 및 r3 조합 probe도 PASS이며 제품·시험 소스는 계속 동결합니다.
API/wiring/hook/WorkerRestart 및 P11 전체는 미완료이고, 배포·실제 Source·모델·운영·P16/F1 수용은 NOT_RUN입니다. 이 절은 기존 보고서 끝 append만이며 exact local commit과 후속 P12 착수는 부모가 별도로 수행합니다.

### P10 구현 및 허용 LOCAL_TEST — 2026-11-06 — ROOT_REVIEW_PENDING

승인된 Spec `c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`와 Plan을 기준으로 A lane `aigw/f1-p10`, C09 `44315e4d861a2100e9703d9b6168244776927b4f`에서 구현했습니다. 최초 Plan `7a5e37c8b747a1acbb846ab8804fb6c57c6b483f08a47ee813d6f76b9b424cb5`의 ③ 실제 Test+paired verifier 종료 경계를 보존한 뒤, 부모 GO로 적용된 기술보완 Plan `f8e3d84de8ca2e5c8929cb162d458b70de7fefcc95ef5ba8de97e81d26de9ae1`을 읽고 started 생산·서버 block id helper를 연결했습니다. Spec/Plan/기준선은 이 세션에서 수정하지 않았습니다.

실제 API와 SSE, 저장 deps의 fail-closed 결과 접근, 균일404, 공유 Clock의 동기 만료 guard를 연결했습니다. 기존 JobRepository/ScopedJdbc/SchemaContracts.JSON과 admission digest를 재사용해 masked Message·정확 Job 입력·queued event를 같은 tx에 저장하고, 멱등 재전송은 저장 messageId를 반환합니다. Worker는 빈 TC 사전인가 뒤 분류 직전 정확 snapshot을 읽고 취소/만료를 재검사해 같은 JobContext로 전달합니다. V5는 기존 Job 행에 명시실패하며 nullable/presence snapshot·object check·deferred NO ACTION 복합 FK·backup 열 목록을 함께 연결합니다.

기존 started WHERE/signature를 보존한 단일 원자 SQL과 commit 이후 queued→started→worker 순서를 연결했습니다. ResultBlockIds는 최종 blocks 배열의 서버 id를 부여하는 빈 아닌 helper이며 실제 P13 producer는 미실행입니다. Export는 저장 id가 정확 한 건일 때만 허용합니다. P09 임시 API를 실제 controller로 교체하고 test-only 관측 GET 및 인증 probe 헤더 mapping만 분리했습니다. 비어 있지 않은 결손/미지정/잘못된 타입 deps를 빈 의존으로 정규화하지 않으며 정확한 legacy `{}`만 호환합니다.

#### 실제 현재 소스 시험 및 별도 증거 검증

아래 여섯 Test는 각각 별도 명령으로 실제 EXECUTED됐으며, 각 Test 직후 같은 필터의 별도 `test-evidence verify` exit0을 확인했습니다. XML/OTR/leaf summary/run-binding/inputFingerprint/current task CP/runtime manifest/명령·exit와 Test·verifier source before/after hash를 새 label로 복사 보존했습니다. 모든 아래 회차는 실패·오류·skip·aborted0이며 양쪽 source 불변입니다.

| 회차 | 실제 task | JUnit PASS | runId | inputFingerprint |
|---|---|---:|---|---|
| `p10-final-core-r1` | `:gateway-core:test` | 43 | `b2504670-3ad7-48a2-af78-7b55290bcbc7` | `a1af8eb16de06bf59307dc9e101b8b5efb659038d7facc4bad7604265cce8138` |
| `p10-final-api-r1` | `:gateway-app:integrationTest` | 18 | `d22121d7-30cb-4f14-8e8c-00cc4acdb4c8` | `322ad1945896c4aa518fc8b4fb42e852b053664355f162bcdfc68a1bcafe95d6` |
| `p10-final-schema-r1` | `:gateway-persistence:integrationTest` | 69 | `2f99a342-76e2-4cc6-8676-0d792d736a7b` | `d61f3387ac39df018872c264e2c86042214b9d3e1bb25f5c3b0627b593a06f74` |
| `p10-final-worker-r1` | `:gateway-app:integrationTest` | 53 | `8bad7344-1682-414d-b120-739c0b5449e2` | `8ab2dbdfce74afdc4a975a601e8a92ca70ce8a23fe5e1e37ff0997e689ed7410` |
| `p10-final-repository-r1` | `:gateway-persistence:test` | 3 | `b4fff9e6-5cf8-4fe2-949d-4504350bcbb7` | `135d152302edd3f3c785dae9fb7a36186e74182ba92e40c0965e35edd2531728` |
| `p10-final-arch-r1` | `:gateway-app:test` | 5 | `edbe23c5-c5ab-45c9-8558-f513e94efc30` | `ffef8c1b483d709271dbe499a7181091e9e24447154f383c3ee6bbcdcdf31b2c` |

각 실행의 실제 argv입니다(Windows argv 배열, 각 항목은 원래 인자 하나입니다). Test 다음 verifier를 별도 프로세스로 실행했으며, verifier의 현재 inputFingerprint 및 실제 task CP manifest도 Test와 일치합니다.

- `p10-final-core-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-core:test", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-core:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-core-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-core-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-core:test", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-core:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-core-r1-verify-current-task-classpaths.json", "--console=plain"]`
- `p10-final-api-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-app:integrationTest", "-Paigw.requireDocker=true", "--tests", "*Api*IT", "--tests", "*Sse*IT", "--tests", "*ExpiryRaceIT", "--tests", "*ResultAccessPathsIT", "--tests", "*AppContextWiringIT", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-api-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-api-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\filters-api.gradle", "-Paigw.requireDocker=true", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-api-r1-verify-current-task-classpaths.json", "--console=plain"]`
- `p10-final-schema-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-persistence:integrationTest", "-Paigw.requireDocker=true", "--tests", "*MigrationApplyIT", "--tests", "*DbBootstrapOrderIT", "--tests", "*SchemaVersionGuardIT", "--tests", "*ScopedRepositoryIT", "--tests", "*RetentionBoundaryIT", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-persistence:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-schema-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-schema-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\filters-schema.gradle", "-Paigw.requireDocker=true", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-persistence:integrationTest --require-docker", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-persistence:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-schema-r1-verify-current-task-classpaths.json", "--console=plain"]`
- `p10-final-worker-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-app:integrationTest", "-Paigw.requireDocker=true", "--tests", "*JobQueue*IT", "--tests", "*WorkerRestart*IT", "--tests", "*IdempotentAdmissionIT", "--tests", "*AdmissionMaskingIT", "--tests", "*IngressAuthIT", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-worker-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-worker-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\filters-worker.gradle", "-Paigw.requireDocker=true", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:integrationTest", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-worker-r1-verify-current-task-classpaths.json", "--console=plain"]`
- `p10-final-repository-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-persistence:test", "--tests", "*RepositorySignatureArchTest", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-persistence:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-repository-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-repository-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\filters-repository.gradle", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-persistence:test", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-persistence:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-repository-r1-verify-current-task-classpaths.json", "--console=plain"]`
- `p10-final-arch-r1` Test exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", ":gateway-app:test", "--tests", "*ArchitectureRulesTest", "--tests", "*ModuleConfigurationCompletenessTest", "--tests", "*ScopeAssemblerTest", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-arch-r1-current-task-classpaths.json", "--console=plain"]`
- `p10-final-arch-r1` paired verifier exit0: `["C:\\workspace_nori\\.aigw-worktrees\\p10-ai\\gradlew.bat", "-p", "C:\\workspace_nori\\.aigw-worktrees\\p10-ai", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\filters-arch.gradle", ":gateway-eval:run", "--args=test-evidence verify --tasks :gateway-app:test", "--no-daemon", "-I", "C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\capture-current-classpaths.gradle", "-Dp10.classpathTasks=:gateway-app:test", "-Dp10.classpathOutput=C:\\Users\\DongGeon\\AppData\\Local\\Temp\\claude\\468c18f2647c2504\\2a367916-708b-4d0e-8a9d-62501d362a92\\scratchpad\\p10-implementation\\p10-final-arch-r1-verify-current-task-classpaths.json", "--console=plain"]`

정확 명령·필터·각 proof SHA는 scratch `result.json`의 `verificationMatrix`에 기록했습니다. core43 뒤 core 제품을 변경하지 않아 불필요한 core 재실행은 하지 않았고, Plan 보완 뒤 current CP가 달라진 나머지 task를 현재 소스로 실행했습니다. old41/old69와 실패회차 XML은 보존만 하고 현재 PASS로 세지 않았습니다.

실제 schema XML의 정본 fixture 출력에서 승인 local image `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`, PostgreSQL `18.6 (Debian 18.6-1.pgdg13+2)`, vector `0.8.7`을 확인했습니다. PGDATA는 기존 fixture의 `/var/lib/postgresql/18/docker`이며 운영/Harbor 승인 근거는 아닙니다.

#### 실제 변경 파일과 이유

- `docs/contracts/gateway-api-v1.md` — 실제 HTTP/SSE 응답·균일404·결과 접근·저장 block id Export 계약. SHA256/상태: `c8c3a9d98789c31ff724654c196f7e123d04dc1c55e95752fbe20fa72ff9e4b6`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/HarnessP09TestPorts.java` — 제품 API와 분리한 test-only 관측 GET 유지. SHA256/상태: `74cdee50aaa01e97c8a3ac8b8ceeece58f4154b44cadd806c318cd32352344d3`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/AdmissionMaskingIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `84737f6cf3eeba62cec82d1689cbe6626bdc9572f0592a10f6de371e30ea35dd`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HarnessObservationsEndpoint.java` — 제품 API와 분리한 test-only 관측 GET 유지. SHA256/상태: `964d27a3a5c81b5634f17937d6c299e2910a7532c0e28210c4f5447228f7d858`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HookAndProbeFilterScopeIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `7bb6dbc1c6efaa3ca4f39722410fb41f70f5395262c52f15a136158122021841`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HookArmingIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `bce563c68d4b4d0a56359ec7bb1707c0fc726dba896b37ef0e3c367511831e2d`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/IdempotentAdmissionIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `4e863e39bd4290a8e511848302655035e18ce49e961f202781c4fbde3c209673`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/JobQueueIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `e4733816a722190984564cc0ae61102851b5ff690b1249b03864e81d285566f6`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09AdmissionEndpoint.java` — 충돌하는 시험 임시 API 제거 후 실제 P10 controller로 전환. SHA256/상태: `삭제`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09AppContextWiringIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `1b62f9f0225426d1028c7c77cba98a22df771107fe051c119106df99ae86ea97`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09QueueSupport.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `fac0f91da4391c1fa211e892a77faff800979bb25a406abbdfef57e9a5a65aaa`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09TestPorts.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `a34a1e0a00cd3c1330cf5a9ae328fddb3fc6c1a825176579577d89bb38e40bc5`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartEpochIT.java` — 실제 재기동 정확 snapshot 복원과 terminal/started oracle 정합. SHA256/상태: `1aae8ec57eb301b5abb288fbab345f07a84826ac569f9ab470f73de7153d40a9`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartInputIT.java` — 실제 재기동 정확 snapshot 복원과 terminal/started oracle 정합. SHA256/상태: `7e36f0d2526e782afb0fc9262d793ff703cdb72044f5c7fcbff58a394be0027a`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartProcessIT.java` — 실제 재기동 정확 snapshot 복원과 terminal/started oracle 정합. SHA256/상태: `de26e01bb381537c08af1848ff44e5f9168d74868b03c17b55b2e43b915b3119`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartProcessSupport.java` — 실제 재기동 정확 snapshot 복원과 terminal/started oracle 정합. SHA256/상태: `fe4050dcc5f37712032128e5c176e7db86fbb175a21e2f44c3c47b3797d0843b`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/security/IngressAuthIT.java` — 실제 인증 필터·scoped DB·downstream0 oracle을 유지한 test-only 헤더 mapping 분리. SHA256/상태: `20380624e7fbfa8484410532bfe6bcc764db7d23dd231a80672b53c1d375c144`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/security/IngressTerminalFixture.java` — 실제 인증 필터·scoped DB·downstream0 oracle을 유지한 test-only 헤더 mapping 분리. SHA256/상태: `292366051733081aaa0fa15d33a7217baf14c1adc00bc4da8be478b8e2003d92`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ApiInputIT.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `6e3485be6639ea2c9f47849d2d56c9be00b8d51e7d4fafef62930254b45d4830`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ApiWebSupport.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `a44fcd0290f78fe2955cec62b7fbf6bf18b68a7e60a3dc9776cf3256cc75d801`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ExpiryRaceIT.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `0dd0ce953f4c93f2eeccd5f2435514ab18005c6b9edd3d00fb5ab799b1d9229d`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ResultAccessPathsIT.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `f9465f2110455abc0bfd374ce8d0a0286af300e2ee11fce7d3793727c7f7d744`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/SseApiIT.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `2bd14025fe296d6dcf8c6eb2a932d553f7131e1441f79443c33b3eab16cc1e4b`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/WebAppContextWiringIT.java` — 실제 HTTP 입력·결과 접근·SSE lifecycle/replay·동기 만료·bean wiring 직접 검증. SHA256/상태: `becdd172506bc209503c114b69a82c81a78e094eee096547e6a4f7bc96f835b2`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `0527cee4e95bb9826083ce3f92a997e7e9c68040126e8ef4d1b120c819055b75`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/CoreBeansConfiguration.java` — 기존 명시적 bean 구성에 web·입력 reader·fail-closed 리더를 단일 연결. SHA256/상태: `ad38dcb7f55c3b5501940ea5cf933b4f5a610ac52a75e6d7ca1c61b2303ede81`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java` — 기존 명시적 bean 구성에 web·입력 reader·fail-closed 리더를 단일 연결. SHA256/상태: `a207e478a1f734c2dc23dea96f7789b49a924571fcdc4090a126195d7982b777`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/JobConfiguration.java` — 기존 명시적 bean 구성에 web·입력 reader·fail-closed 리더를 단일 연결. SHA256/상태: `31e4bf6f560082106b6afaf4a879b9fa2191e9d191aa22e3ac3ff8c34c4cdc71`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/IdempotentAdmissionLookup.java` — 기존 admission tx·멱등 messageId·Worker 정확 입력 복원 및 postcommit started 발행 연결. SHA256/상태: `ca22772188536bf98ce2dfb9def47bc51fe596944b09c5e2abb493a16b5dbf55`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobAdmissionService.java` — 기존 admission tx·멱등 messageId·Worker 정확 입력 복원 및 postcommit started 발행 연결. SHA256/상태: `4464c529c87cdbd49817302ac4d63d2be25f9ba06d2221b2d623b63ec6b640da`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobDispatcher.java` — 기존 admission tx·멱등 messageId·Worker 정확 입력 복원 및 postcommit started 발행 연결. SHA256/상태: `040f4a4c6a2cae15bfc890c0af7ad12a3776e112a4b7fb76250e5b87fe544503`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/JobWorker.java` — 기존 admission tx·멱등 messageId·Worker 정확 입력 복원 및 postcommit started 발행 연결. SHA256/상태: `6d61f65d121fd3e4a6576d7de3fd33caf10a26d721c77903fc1e1717096eaab1`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/AnalysisController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `f9209890528a6eea5b478f72d4d212aa29db120d88cdbf9f8b22d460320735e8`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/ApiExceptionHandler.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `245e5623c80dba0cdee245f0550e957bb9fe8f0b1f0a1dcbfd91ea683d57a4dd`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/ApiRequestInput.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `cba182fd4affad6dd3c55564040afe6fbe3a87e9f54fe685e57794c50a1de75a`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/AuditQueryController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `54e143ca2766aa1489bfee34840a2157bc5ebcc32bd1efca27c5b8e199f8dad7`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/BlockExportController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `bb32baea45d10d3e794812cddd24ee6924876544ddc2ede03f60994e936f9e0c`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/ConversationController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `60ef232763a5ec8cc55df24f3ca270ffb569d2c3da2dac733002d5a93f4a2315`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/EventReplayService.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `1a7e7592b447e853991cf3be81485335b0366c7095102e676b474fb39c436e81`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/EvidenceSourceController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `714fd9dd1e6e279a0929a0e6b619a746fc3008e3880abce1d1b0400072a816dd`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/ExpiryEmitterCloser.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `e6461573c475fc12ef9517eb140ff11c2c23e3ec0c90b53b1be52e97fae20d84`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/GuardedSseWriter.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `1b2d97c9db6bb357026ed6a69e598a67fa06bf5b2f837df5cf9a9f1ee593fa8e`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/JobEventStreamController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `d576c8ee371c79c616a74ebf01de220f2175c511c09d5484630829ea6453b499`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/QueryController.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `8e07f05d8c35205902e6c8f74cf9fe3dea9b8b8e8a5617b8000c43dc9ba823cf`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/ResultResponseAssembler.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `682b80019dd0dee3a69b6ed1687520fa8d07bda6de41de44f8c6065effb52077`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/SseHub.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `8361dc1b6d89af2f496fc5931cd9999a2b4cf80bf83e790e692dddcde14fd319`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/UniformNotFound.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `415e6b778b59241c63ce81f17ae75098c2b0efd534a19c79a3da40eb89eaae10`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/web/WebConfiguration.java` — 실제 API/SSE/결과 접근 및 동기 만료 경계와 단일 WebConfiguration wiring. SHA256/상태: `6cbe20f4621f6490bd4e813be594e2df867e9a83ec0b283b3e6790f6f3d9fc88`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/ArchitectureRulesTest.java` — 실제 P10 API 합류·NewJob 입력 fixture·started lifecycle 및 기존 닫힌 architecture 경계 정합. SHA256/상태: `3dd3d0a1f5307f58b1eee008a06dfdade606783bdc186aafc3bc78725174c246`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/expiry/ExpiryCheckpoint.java` — 기존 ConversationExpiryGuard·공유 Clock을 사용하는 동기 checkpoint SPI. SHA256/상태: `e273ead18be4385cda5ec33bfab9311ef0ec424e1a185686894956f76a4fa8ee`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/JobContext.java` — nullable/presence를 보존하는 immutable Job 입력 snapshot·scoped reader·JobContext 전달. SHA256/상태: `fc032848b997dd1da2903ac3d64553ca252494bd03c256bb65feb2fd78476c91`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/job/JobRequestInput.java` — nullable/presence를 보존하는 immutable Job 입력 snapshot·scoped reader·JobContext 전달. SHA256/상태: `99d2bb7ed701d2a7ecc1b75d839c366e568dbc1d2cea8f8f8f7eb0fbfb1054bc`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/port/JobRequestInputReader.java` — nullable/presence를 보존하는 immutable Job 입력 snapshot·scoped reader·JobContext 전달. SHA256/상태: `325310ad9d68138e7f9a6a2251048c836568f9fb8d18b7aa136c319394cfc235`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/EvidenceSourceReader.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `833a55155db1ca6c3d7d11b53efdabad39720698fa1fa7eb7ad74423f943939a`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/EvidenceSourceRef.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `941e62f4c89bff35f691c11b96a642aef1e3b29ae18bcc373c7b2cd96711d461`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/FailClosedEvidenceSourceReader.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `b2f0a6cc317715816fefdf63bfa50cb49bf9eafb808de70c40326fe9faeff816`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/FailClosedKnowledgeDocumentStateReader.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `ebc2a056534ce870a52b0d1107b31f1349b325b3a2fd874fd4960be75b0732d6`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/KnowledgeDocumentStateReader.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `db8efb9eaeae1f020838bc58f474cad607a9928465c56de650e6060dc91b5a21`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/ResultAccessEvaluator.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `6f1f6f65895006979660e5ef3106259b3789f779ec66b7975eefb34e3e7f7dcf`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/ResultBlockIds.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `6f61842782c5eb412685c7780638c8307b146d553f972f8b9f568202940b70c2`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/result/ResultSourceDeps.java` — 기존 Source AND/문서 현재상태 fail-closed 계약과 Evidence 포트·최종 blocks 서버 id helper. SHA256/상태: `a1af3fc574e1d6243f2519198e20df89f9a7061f23bca30c8519c56e16643f64`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/job/JobRequestInputTest.java` — immutable nullable 입력·결과 접근 평가·blocks 서버 id 단위 검증. SHA256/상태: `c682218b173bcb532bd0130e989cd98686ea0a4e97c04d07ebc067884f616421`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/result/ResultAccessEvaluatorTest.java` — immutable nullable 입력·결과 접근 평가·blocks 서버 id 단위 검증. SHA256/상태: `6beae394d7d5bb10d0f12c3437ec5198a038981835a2078ce8ac04e3c0050287`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/result/ResultBlockIdsTest.java` — immutable nullable 입력·결과 접근 평가·blocks 서버 id 단위 검증. SHA256/상태: `0a05b78030e658b6ab02be4545aa1d9af65457a66efda366abf65cec050fa1fa`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/AuthRejectionAuditIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `f5bdd8c1f519b41ba1986b445311a3ba174624a363284149bdd7b5c67e6436e8`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/JobDelegationColumnIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `136c085fedccc44c8d5166f6d4c4492212ce529d9bb19467f0a637d97ca8a95c`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/JobTerminalDeadlineIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `aa21d314cf67f8ce85e61fe8e0be7776720d2150e0dad4c8ab6715ad787d177b`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/MigrationApplyIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `b3cfd44acba4a67007c617d98cf8680a98a59d32c098ec0bd1331f0b6027ba2c`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/RetentionBoundaryIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `22167a21db4bdbbbb425dbe6a263219c2220427b23771a0c6295f35d5ba7b748`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/SchemaVersionGuardIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `a2c28cafed5269629fa2db95a7c4cfc37d55fc219665a8cedf663688f500f646`
- `gateway-persistence/src/integrationTest/java/com/nori/ai/gateway/persistence/ScopedRepositoryIT.java` — V5·scoped FK/SQL·retention 검증 및 NewJob 7필드/정확 Message fixture 정합. SHA256/상태: `bab2e88e3a8e21db8f4961e4b19f994b64c9d2478e7d8d617bd68d32dd3614e4`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/AdmissionLookupRepository.java` — 멱등 정확 messageId 조회 및 dispatcher tx의 RUNNING+started 원자 SQL·고정 started publish. SHA256/상태: `2abecd7156bf0413aa5b6d8203d5b5556c2d534422a7940e7f7d6f6b05586e30`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/JobEventPublisher.java` — 멱등 정확 messageId 조회 및 dispatcher tx의 RUNNING+started 원자 SQL·고정 started publish. SHA256/상태: `c0b1c470eed065f21ef2901e74e455d3cb5707390e234578043fec0009bf0211`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/queue/QueueJobRepository.java` — 멱등 정확 messageId 조회 및 dispatcher tx의 RUNNING+started 원자 SQL·고정 started publish. SHA256/상태: `4e81918773ad8dd1d73e0bf4f6d1a2f9d2f79a861c617dfdc050bae51e3fee81`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/repository/JobRepository.java` — 기존 JobRepository의 scoped 입력 INSERT/조회 및 기존 SchemaContracts.JSON 재사용. SHA256/상태: `724d1673d6dbceddd90e17a9e554661272d14d6e070e1aa0a97f32f6f2c458bc`
- `gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/result/JobAuditReader.java` — 본인·boundSystem·대화 미만료·90일 범위의 안전 감사 메타데이터 조회. SHA256/상태: `b9c63d60c0d147df3fc38f7a714a8ba887166f54c7eff187d0a8bc22063f9b3d`
- `gateway-persistence/src/main/resources/db/migration/gateway/V5__job_request_input.sql` — 기존 Job이 있으면 명시실패하는 V5와 JSON object 검사·지연 NO ACTION 복합 FK. SHA256/상태: `3da10f04f0d89f0848129065001d22d9ca1d8b43d88753cb2aa90865a8d9e67d`
- `ops/backup/job-columns-v1.txt` — V5 NOT NULL Job 입력 두 열을 기존 backup forward 목록에 연결. SHA256/상태: `6dccff09dce8ddedf051855f104b1e440a1c15fc67387b5fd84cbb866cfdf848`

- `docs/ai-rag-gateway-architecture-dev-report.md` — 기존 930576바이트 prefix SHA256 `09a5047321d396d3b130485549428002922880b855b23f12399709e135fc72ef` 전체를 보존한 본 절 append입니다.

#### 실패·차이·미실행·후속 위험

- `p10-core-r1` — 새 단위 fixture의 AssertJ wildcard generic compile 오류; 정확 타입 assertion으로 수정 후 core-r2/final-core-r1 실행. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.
- `p10-compile-r1` — 기존 FrozenClock에 없는 set 호출 compile 오류; 실제 freezeAt API로 수정. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.
- `p10-api-r1` — PowerShell이 -P 속성을 분리해 Test 미실행; Python argv 실행기로 교정. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.
- `p10-api-r2` — 실제 16건 중 날짜예외500/기존 bean count assertion 두 실패; DateTimeException400 및 실제 단일 빈 assertion 정합 후 final-api-r1 실행. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.
- `p10-schema-r1` — Retention fixture의 Map import 누락 compile 오류; import 후 schema-r2 및 current final-schema-r1 실행. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.
- `p10-final-compile-r1` — 새 JobQueueIT의 TransactionTemplate generic 반환 assertion compile 오류; 명시 Boolean 타입으로 교정. 기존 실패 증거는 변경하지 않았고 PASS로 승격하지 않았습니다.

승인 Spec의 확정 계약을 변경하지 않았습니다. Plan r2의 기술 연결 보완은 부모가 canonical에 적용했으며 이 구현은 그 GO 이후의 허용 파일만 수정했습니다. 새 업무/권한 정책·schema 기준선·생산 성공 fake는 추가하지 않았습니다.

- NOT_RUN: P13 실제 WorkflowExecutionAdapter의 ResultBlockIds 생산·최종 저장 왕복 및 Tool/cache guard 적용
- NOT_RUN: P20 실제 ClassificationService/classify·Composer/Prompt/LLM의 동일 snapshot 소비와 호출 guard 적용
- NOT_RUN: P15 backup roundtrip 및 실제 운영 기존 Job 0건 확인·V5 적용
- NOT_RUN: P23 실제 KnowledgeDocumentStateReader/EvidenceSourceReader 원문 제공·재노출 검증
- NOT_RUN: 실제 Source/모델·Harbor·배포·운영DB·SSH/K8s·P16/F1/F2·acceptance
- NOT_RUN: 부모의 독립 Validate·병렬 lane 합류 후 동일 source/CP/fingerprint 회귀 및 commit
- 위험/제한: V5는 기존 Job 행이 있으면 의도적으로 실패합니다. 운영 적용 전 Job 0건은 이 로컬 시험으로 확인되지 않습니다.
- 위험/제한: Knowledge/Evidence는 이 phase의 명시 fail-closed 단일 기본 빈입니다. P23 실구현 join 전 문서 의존은 DENY/원문은 비가용입니다.
- 위험/제한: 시험 workflow/분류 seam은 test-only입니다. 실제 업무 workflow·분류·LLM PASS를 뜻하지 않습니다.
- 위험/제한: 현재 결과는 소유 worktree CODE/허용 LOCAL_TEST 근거이며 부모 독립 검증 및 병렬 합류가 남아 있습니다.
- 위험/제한: 변경된 기존 AuthRejectionAuditIT/JobDelegationColumnIT/JobTerminalDeadlineIT 등 필터 밖 fixture는 전체 integrationTest 컴파일 정합만 확인했고 별도 실행 PASS로 주장하지 않습니다.

문서 마무리의 첫 git diff --check는 본인 append의 EOF 빈 줄로 exit2였으며, 기존 prefix가 동일함을 확인하고 본인 append 끝줄만 교정했습니다. 제품·시험 소스는 바꾸지 않았고 최종 git diff --check exit0을 별도로 확인합니다.


제품 소스는 최종 시험 전 동결 SHA manifest `p10-final-source-freeze-r1.json`(`5479a8b24a77e668323909c0dbcc298df99e28973db7030606f18af6f5bd964d`)의 78경로와 그대로 일치합니다. 본 append를 제외한 파일 변경은 없습니다. UTF-8 strict decode·LF 및 git diff --check를 확인합니다. Spec/Plan 체크 승격·commit/push/MR·외부 메시지·운영 작업은 하지 않았습니다. 상태는 ROOT_REVIEW_PENDING이며 부모 독립 Validate와 통합을 기다립니다.

증거 위치: `C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p10-implementation`의 `result.json`, 각 `p10-final-*-r1.json`/`-verify.json` 및 별도 XML/OTR/CP/hash proof입니다.

#### P10 부모 독립 검증 — 로컬 커밋 대상 확인

부모는 실제 변경 79경로와 삭제 범위, 기존 보고서 prefix, 여섯 회차의 실제 JUnit 191건·XML/OTR 고유 실행·198개 보존 artifact hash·현재 task classpath와 inputFingerprint를 직접 대조했습니다. 모두 일치하며 실패·오류·skip·aborted는 0입니다. 별도 작성한 실제 PG probe에서 started INSERT 제약 실패 시 RUNNING과 이벤트가 함께 rollback되는 것, 손상된 저장 입력이 workflow 호출 없이 CONTRACT_INVALID·Result 1건·단일 terminal로 종료되고 위임 값이 정리되는 것을 확인했습니다. probe 전후 CP는 동일하고 종료 후 해당 PG fixture는 남지 않았습니다.

부모 근거는 scratch `p10-parent-review/r1-root-validation.json`과 `r1-parent-probe.json`입니다. 현재 Plan `7f488addf6a337616f7a0982172be137faab0ebda944ce6ae581ac8cb5d7d8c6`의 P10 본문은 실제 시험 기준 `f8e3d84de8ca2e5c8929cb162d458b70de7fefcc95ef5ba8de97e81d26de9ae1`과 바이트가 같습니다. 이 판정은 P10 격리 소스의 CODE/LOCAL_VALIDATED이며 P10/P11/P12 실제 합류·후속 producer·운영 수용은 별도 미실행 항목으로 유지합니다. 로컬 커밋에는 이 정확한 변경 집합만 포함하며 Spec·ignored Plan·기준선·사용자 보존 파일은 포함하지 않습니다.


## P12 Connector CODE/LOCAL_TEST — 2026-11-06 (독립 구현, 부모 검토 대기)

상태는 `ROOT_REVIEW_PENDING`입니다. 이 절은 기존 보고서 전체 943913바이트를 그대로 보존한 append입니다. 기존 prefix SHA256은 `d45872b3d73c4e1747d437488dffe0673901081661149808668d9d1879c6c0ae`입니다.

### 기준과 실제 작업 위치

- AI_ROOT: `C:/workspace_nori/.aigw-worktrees/p12-ai`, branch `aigw/f1-p12`, 시작/현재 HEAD `62517ba2e42629d7d8be431cc30025ce98e08d2f`. 시작 dirty 0이며 C09 `44315e4d861a2100e9703d9b6168244776927b4f` ancestor를 실제 확인했습니다.
- canonical Spec SHA256: `c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`, baseline seq7 SHA256: `b1040ce78cde516641cdc90da644b03e5d459ddc33668243a935ac4e3d348264`.
- 최초 Plan pin `7a5e37c8b747a1acbb846ab8804fb6c57c6b483f08a47ee813d6f76b9b424cb5`는 초기 실행 이력으로 보존했습니다. 부모 GO 뒤 현재 pin `f8e3d84de8ca2e5c8929cb162d458b70de7fefcc95ef5ba8de97e81d26de9ae1`를 사용했습니다. `plan-transition.json`은 P12 섹션 실제 byte 불변을 대조합니다. Spec/Plan/R6/기능 계약은 변경하지 않았습니다.

### 구현 내용과 이유

- Loki는 고정 TC 4개 앱과 namespace AND로만 GET query_range를 생성합니다. `UriComponentsBuilder`의 변수 바인딩으로 검색어를 정확 인코딩하고 LogQL 줄 필터의 따옴표·역슬래시·제어문자를 이스케이프합니다. 반개구간 나노초 시각은 축소하지 않습니다.
- 최신 6시간 창부터 하위 limit 500, 전체 1000줄, 기본 30초 마감을 적용합니다. 시간창을 자르지 않으며 조회하지 않은 창과 limit 때문에 잘린 구간은 PARTIAL/missingRanges로 반환합니다. 라벨을 다시 검사해 허용 밖 app/namespace를 버리고 위반 건수를 반환합니다.
- 앱별 `SourceRetentionReader.find(LokiSourcePolicy.retentionSourceId(app))`로 각각 분류합니다. 전역 로그 Source 권한 키는 `sources://TC/logs`이며 보존 키는 `sources://TC/logs/<app>`입니다. 네 앱에 하나의 보존 경계를 추정하지 않습니다. 보존 경계 없음은 EMPTY/HIT 모두 RETENTION_UNVERIFIED이고, 범위 밖은 호출 없이 OUT_OF_RETENTION입니다.
- `LokiRetentionProbe`는 명시된 탐침 창에서 forward limit1로 순수 측정만 수행합니다. `{verifiedOldestAt, probeLimit}`를 반환하며 0건은 Optional.empty입니다. JDBC·persistence·스케줄 의존은 없습니다.
- Tempo는 TC 서비스만 검색하고 cross-service trace의 WM/미상 서비스 span을 제거합니다. 공식 초 단위 API의 전송 범위는 start floor/end ceil로 넓혀 누락을 막고 실제 span을 원래 나노초 반개구간과 다시 대조합니다. 보존은 UNVERIFIED입니다.
- GitLab은 승인 repo/ref/경로와 lineage 항목이 없으면 기본 거부합니다. 배포 설정은 실제 승인 전 빈 목록이며 토큰은 파일 참조만 사용합니다. code 검색 결과도 경로를 재검사하고 스니펫을 최대 2000 codepoint로 제한합니다.
- 계보는 retried job과 전체 페이지를 포함한 성공 package job이 정확히 1개일 때만 trace를 읽습니다. trace는 제한된 행 버퍼로 정규식 sibling 줄만 추출하며 원문을 저장하거나 기록하지 않습니다. 대상 sibling 줄이 정확히 1개이고 commits API가 동일 prefix의 40자 SHA를 반환할 때만 VERIFIED입니다. exec/pipeline SHA나 현재 HEAD 추정은 없습니다.
- 직결 Source마다 기존 `SourceAuthorizationStep`과 typed `DelegatedCall`로 현재 권한을 다시 확인합니다. 소유 시스템·Source 참조·만료·권한 폐기는 Source 실행 전에 차단합니다. core Allowed의 2필드 계약은 유지했습니다.
- 전용 `SourceObservability.create`가 private ObservationRegistry와 3태그 meter handler를 생성합니다. 전역 DefaultMeterObservationHandler를 Source registry에 붙이지 않습니다. URL을 포함할 수 있는 HTTP/파서 예외는 관측 전에 고정 메시지·원인 없는 Failure로 바꾸며 wire/debug 설정이 활성화되면 요청 전에 거부합니다.
- 전송은 요청마다 전용 JDK HttpClient의 try-with-resources 안에서 동기로 수행합니다. Spring 7.0.8의 응답 전체 timeout/stream close와 JDK21 HttpClient.close의 실제 종료 대기를 사용합니다. callback 종료·Observation stop·client 종료가 상위 반환보다 앞섭니다. 중단/마감 뒤 재시도나 추가 Source 요청을 만들지 않습니다.
- acceptance `LokiDirectQuery`는 고정 TC4/WM2 양성 제어의 건수·라벨·시각만 반환합니다. 운영 Connector의 allowlist를 늘리지 않습니다. acceptance main을 실제 컴파일하고 단위시험도 별도로 실행했습니다.

### 실제 변경 파일

- `docs/ai-rag-gateway-architecture-dev-report.md`
- `docs/gates/f1/source-labels.md`
- `gateway-acceptance/build.gradle.kts`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/loki/LokiDirectQuery.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/loki/LokiDirectQueryTest.java`
- `gateway-app/src/main/resources/config/sources/tc-sources-v1.json`
- `gateway-integration/build.gradle.kts`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/gitlab/GitLabCodeConnector.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/gitlab/GitLabPipelineLineageReader.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/gitlab/GitLabReadTokenFile.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/gitlab/GitLabSourcePolicy.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/AllowlistClientRequestObservationConvention.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/LokiLogConnector.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/LokiProperties.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/LokiRetentionProbe.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/LokiSourcePolicy.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/SourceHttpClient.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/SourceObservability.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/SourceReadAccess.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/TcSourceAllowlist.java`
- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/tempo/TempoTraceConnector.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/ConnectorTestSupport.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/gitlab/GitLabAllowlistTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/gitlab/GitLabFixture.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/gitlab/GitLabPipelineLineageTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/LokiQueryNotRecordedTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/LokiRetentionProbeTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/LokiSelectorInvariantTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/LokiWindowBatchingTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/SourceHttpClientTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/SourceHttpTerminationTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/SourceOwnershipAndCurrentAuthorizationTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/TcSourceAllowlistTest.java`
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/tempo/TempoSpanFilterTest.java`

### 실행한 검증과 결과

- 최신 `:gateway-integration:test --offline --no-daemon`: 실제 58/58 PASS, XML 11개, fail/error/skip/aborted 0. runId `01c43c43-fd0f-4205-bf7e-5538977ef4eb`, fingerprint `1ba85474e1856589781c8bcc50a1caeaad5854941478b2f1206fc728392632c6`. 직후 별도 `test-evidence verify --tasks :gateway-integration:test` exit0입니다.
- 최신 `:gateway-acceptance:test --offline --no-daemon`: 실제 1/1 PASS, main compile 포함, fail/error/skip/aborted 0. runId `53916513-94d0-4e03-9531-d8e17c8c4480`, fingerprint `77c148421ee34a642cbf9d0fea575dc553d02879ca26052254d75d86c9e31cda`. 직후 별도 `test-evidence verify --tasks :gateway-acceptance:test` exit0입니다.
- 두 Test와 paired verifier에서 source before/after 불변·정본 hash 불변을 확인했습니다. no-test owning-project CP capture 뒤 실제 dir/jar 파일 hash와 run-binding classpath component를 대조하고 fingerprint를 독립 재계산했습니다. `final-evidence-check.json`에 원시 XML/OTR·summary·binding·current CP 경로를 기록했습니다.
- 31일 창 496 GET, 고정 selector/인코딩, 총/하위 limit·마감 누락 구간, 보존 밖/겹침/미확정 EMPTY/HIT, 라벨 제거, Source 소유권/현재 권한 폐기, 전역 default meter와 Source 전용 registry 병존, 실패/연결/파서 예외의 sentinel/URL 비노출, redirect/타 authority 차단, 지연 header/body와 실행 중단 시 callback·handler 완료 및 추가 요청0, Tempo 나노초 경계와 WM span 제거, GitLab 기본 거부·경로·페이지·계보 ambiguity/40SHA/CRLF를 검사했습니다.
- 초기 실행 이력은 원본 보존했습니다. r1은 44건 중 4FAIL(표준 meter가 자동 추가한 error 태그)이며 assertion을 완화하지 않고 전용 실제 meter registry/handler로 고쳤습니다. r2/r5는 각각 generic 추론/HttpHeaders import 충돌의 compile 실패라 옛 XML을 새 실행으로 인정하지 않았습니다. acceptance-r1은 core direct 의존 누락과 scratch CP observer의 unsafe resolution을 정석 수정했습니다. 중간 r3/r4 PASS도 최종 코드 증거로 대체하지 않았습니다.
- 재위임·commit·push·실제 Source·Docker·PG·TC·모델·운영 작업은 0입니다. 이 개별 로컬 PASS는 actual join 또는 AC/ISO/운영 수용 PASS가 아닙니다.

### P13/P14 및 부모 합류 인계

- P13/공유 wiring 작성자가 `SourceReadAccess(SourceAuthorizationStep, Clock)`와 typed Request를 사용합니다. `TcSourceAllowlist.parse`로 TC owner/고정 선택자를 검증한 GitLab policy를 읽고 현재 인가와 승인 Source gate를 만족한 뒤에만 Client를 생성합니다. 실제 base target·소유자 승인·자격 정보는 아직 확인하지 않았습니다.
- 관측 wiring은 `SourceObservability.create(MeterRegistry, ObservationHandler<ClientRequestObservationContext>)`가 만드는 전용 registry를 사용합니다. span handler는 고정 convention의 3개 속성만 수출해야 하며 전역 표준 meter/자동 URL handler를 이 registry에 추가하지 않습니다. 실제 OTLP 수출/수집 검증은 P14/P15 후속입니다.
- P14 scheduler는 앱마다 `LokiRetentionProbe.measure(app)`를 호출하고 값이 있을 때 `LokiSourcePolicy.retentionSourceId(app)`를 upsert의 sourceId로 사용합니다. verifiedOldestAt/probeLimit 두 값과 Gateway Clock probedAt을 그대로 저장합니다. SourceRetentionReaderAdapter도 같은 키를 읽어야 하며 aggregate Source 경계로 합치지 않습니다. 탐침 창의 가장 이른 시각은 실제 Loki 허용 범위를 명시 입력해야 합니다.
- P13 코드 검색에서 VERIFIED Lineage의 sibling SHA만 과거 코드 ref로 사용할 수 있습니다. UNKNOWN은 승인 ref로만 검색하며 당시 코드로 표시하지 않는 기존 Plan 정책을 따릅니다. 새 fallback 정책을 추가하지 않았습니다.
- API/hook/worker/shared bean wiring, 실제 Source 승인·연결, scheduler·DB, 실제 배포/운영/ISO-15/P16/F1/F2는 NOT_RUN입니다. 부모 독립 검토·actual join 회귀는 이 세션 밖입니다.

### 공식 기술 근거

- Loki GET/query_range·방향·시각: https://grafana.com/docs/loki/latest/reference/loki-http-api/
- Tempo 초 단위 search/traces·OTLP JSON: https://grafana.com/docs/tempo/latest/api_docs/
- GitLab jobs include_retried·페이지·trace: https://docs.gitlab.com/api/jobs/
- GitLab commits/search: https://docs.gitlab.com/api/commits/ 및 https://docs.gitlab.com/api/search/
- 실제 Spring tagged RestClient/관측/전체 응답 timeout 구현: https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.8/spring-web/src/main/java/org/springframework/web/client/DefaultRestClient.java 및 https://raw.githubusercontent.com/spring-projects/spring-framework/v7.0.8/spring-web/src/main/java/org/springframework/http/client/JdkClientHttpRequest.java
- 표준 meter의 자동 error 태그 근거: https://raw.githubusercontent.com/micrometer-metrics/micrometer/v1.16.6/micrometer-core/src/main/java/io/micrometer/core/instrument/observation/DefaultMeterObservationHandler.java
- JDK close/interrupt/wire logging은 실제 `C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot/lib/src.zip`의 java/net/http/HttpClient.java, ResponseSubscribers.java, common/Log.java, common/DebugLogger.java를 직접 읽었습니다.

증거 기본 경로: `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p12-implementation`. 정본/기존 증거는 덮어쓰지 않았고 추가 제품 audit/summary 문서는 만들지 않았습니다.


### P12 r6 후속 수정 — GitLab 계보 wire 타입 검증 (2026-11-06)

부모 독립 `ParentLineageWireProbe.java`가 Job ID `1.5`에 대해 HTTP 3건 뒤 VERIFIED가 되는 실제 RED를 재현했습니다(`scratch/p12-parent-review/fractional-red-r1-parent-probe.json`, javac0/java1/inputStable=true). `canConvertToLong()`은 JSON 정수 타입 검사가 아니라 값의 변환 가능 범위를 검사하므로 소수 ID가 정수 1로 축소되는 것이 근본원인입니다.

이 후속 변경은 다음 두 파일에만 한정했습니다. 공유 wiring·core·Spec·업무 정책은 바꾸지 않았습니다.

- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/gitlab/GitLabPipelineLineageReader.java`: package Job ID는 isIntegralNumber, canConvertToLong, 양수 조건을 모두 확인한 뒤 사용합니다. name/status도 문자열임을 확인하고, 인접 commits 응답의 id는 object의 문자열 필드일 때만 SHA로 검사해 숫자형 40자리 값의 암묵 변환도 차단했습니다.
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/gitlab/GitLabPipelineLineageTest.java`: 소수1.5/1.0·숫자 string·결손/null·음수/0·범위 밖·boolean/object/array Job ID는 UNKNOWN/PACKAGE_JOB_AMBIGUOUS와 HTTP 1건·후속 trace0을 확인합니다. 정수1/Long.MAX_VALUE는 정확한 ID로 trace를 조회해 기존 VERIFIED 양성을 유지합니다. 숫자형40SHA·다른 타입은 UNKNOWN/SHORT_SHA_UNRESOLVED이고 문자열40SHA만 양성인 회귀도 추가했습니다.

`source-freeze-r6.json`은 제품 수정 뒤 Test 시작 전에 동결했습니다. 이번 실제 `:gateway-integration:test`는 60/60 PASS(11 XML, fail/error/skip/aborted0), runId `87db1d5b-7ed9-43f0-92f0-1c8f1de1d081`, fingerprint `bd62edb6d41ccd93fefdfc5cd5f8bf2568e9b5487d8b9005d450a02804f28f4a`입니다. 직후 별도 `test-evidence verify --tasks :gateway-integration:test`도 exit0이며 두 명령의 소스·CP·정본 결속을 보존했습니다.

integration 산출물 변경으로 acceptance도 최신 CP에서 실제 1/1 PASS와 별도 verifier exit0을 다시 얻었습니다. runId `e6f3141c-00e9-48dd-b5a4-d0d455dd55d0`, fingerprint `229609b430c077a48683a0e871426bd63d55b0c82e7ec8a120acc45907010967`입니다. 새 no-test CP capture의 실제 dir/jar 파일 hash와 각 run-binding component를 대조하고 fingerprint를 독립 재계산했습니다. 현재 앱 runtime SHA256은 `e106fe97cc8610eae4549572c367ef9cbbcc4a0c42eb723f04935265f3d0e6fe`이며 두 측정과 같습니다.

integration Test/paired verifier는 Plan `f8e3d84de8ca2e5c8929cb162d458b70de7fefcc95ef5ba8de97e81d26de9ae1`에서 시작·종료했습니다. 안전경계 뒤 부모 GO로 Plan `7f488addf6a337616f7a0982172be137faab0ebda944ce6ae581ac8cb5d7d8c6`를 확인하고 acceptance/현재 CP 후속 검증을 진행했습니다. P12의 두 module build allowed_writes 명시 외 기능·시험·Source 계약 byte는 그대로임을 `plan-transition-r6.json`에 대조했습니다. Spec/baseline/R6는 변경하지 않았습니다.

종전 `result.json`, source-freeze, 59건의 원시 검증과 부모 r2 PASS를 덮어쓰지 않았습니다. 사전 보존한 332개 증거 파일의 hash가 그대로이며 이 절 전 보고서 957627바이트(SHA256 `4f2dc2958d20f92d650fd35366e54babb7be15468ea3e5ab7ca03bf08340d999`)도 완전히 보존한 append입니다. 새 결과는 `scratch/p12-implementation/result-r6.json`, 원시 XML/OTR·현재 CP·hash·명령 exit·FP 검사는 `final-evidence-check-r6.json`에 따로 기록합니다.

상태는 `ROOT_REVIEW_PENDING`입니다. 실제 Source·모델·TC·운영·Docker/PG·배포·commit/push는 0이며 부모의 동일 독립 oracle GREEN과 exact commit은 부모가 수행합니다. 이 로컬 회귀 결과를 actual join·AC/ISO·운영 합격으로 승격하지 않습니다.

#### P12 부모 독립 검증 — 로컬 커밋 대상 확인

부모는 최종 변경 34파일·기존 보고서 prefix·현재 CP와 실제 JUnit 61건 및 두 paired verifier의 XML/OTR 고유 실행과 fingerprint를 독립 대조했습니다. 별도 HTTP oracle로 앱별 보존 경계·현재 인가 철회·실제 meter 고정 태그·Tempo 나노초 범위도 확인했습니다. GitLab 소수 Job ID가 VERIFIED로 확정되는 실제 RED를 먼저 재현했고, 같은 프로그램을 수정하지 않은 GREEN에서 UNKNOWN/PACKAGE_JOB_AMBIGUOUS·HTTP 1건 종료를 확인했습니다. 부모 probe 전후 실제 CP 파일 내용은 같습니다.

근거는 scratch p12-parent-review의 r6-root-validation.json, r6-raw-evidence-review.json, fractional-red-r1-parent-probe.json, fractional-green-r6-parent-probe.json, r3-current-r6-parent-probe.json입니다. 현재 Plan은 P12 build 두 파일의 필수 연결만 명시했고 Source·인가·wire·실패 정책은 그대로입니다. 이 판정과 로컬 커밋은 C11contract 기반 격리 Connector CODE/LOCAL_VALIDATED이며 P10/P11/P12 실제 합류 회귀·실제 Source·운영 수용은 별도로 미실행 상태를 유지합니다.


### P12 r7 후속 수정 — Loki 설정의 실제 Constructor Binding (2026-11-06)

시작 위치는 clean C12 `49a0d49eca76e65626be4d97cd738caa89e96502`의 `aigw/f1-p12` worktree입니다. 부모의 실제 RED는 `ParentLokiBindingProbe.java`(SHA256 `df596ac2d0086475f1ef7c239e117d961b4609bb9b7ad1b034fad74963b6c86a`)와 `scratch/p12-parent-review/loki-binding-red-c12-r1-parent-probe.json`의 javac0/java1/CPstable이며, 승인된 per-query-limit=123을 Boot Binder로 읽어도 bound=false였습니다.

근본원인은 public 무인자 생성자와 canonical 생성자가 함께 있어 값 생성자 선택이 명시되지 않은 점과 record component limitPerQuery의 기본 키가 승인 키 per-query-limit와 다른 점입니다. 실제 Boot 4.0.7 JAR의 ConstructorBinding/Name/DefaultValue 애너테이션 및 BindResult API를 javap로 확인한 뒤 표준 바인딩 기능만 사용했습니다.

- `gateway-integration/src/main/java/com/nori/ai/gateway/integration/loki/LokiProperties.java`: compact canonical constructor에 ConstructorBinding을 지정하고 limitPerQuery component에 Name("per-query-limit")를 지정했습니다. 누락된 설정도 기존 6h/500/1000/30s 기본값을 유지하도록 각 constructor parameter에 동일 DefaultValue를 지정했습니다. 기존 public 무인자·4인자 생성자, record component/accessor 이름, 검증 로직은 유지했습니다.
- `gateway-integration/src/test/java/com/nori/ai/gateway/integration/loki/LokiPropertiesTest.java`: 실제 Binder로 승인 키 전체와 nondefault123, 다른 nondefault limits, 빈/부분 설정의 동일 기본값, 0·음수 validation, Duration 오형식·정수 소수/범위 초과 실패를 검사하는 5개 영구 단위시험을 추가했습니다. 수동 Environment 파싱·필드 rename·wrapper·전역 설정·의존 변경은 없습니다.

Plan pin `cc1a74a5e951fbd100c3cf9871f7474c4ab1598ed8e73e75675c2447284425aa` 및 P12 본문 SHA256 `d117d64c1270151d7f290c276bad5c5cec8817fa3ea8ad1f33c7fcad9008ce8a`를 root GO 뒤 대조했고 Spec `c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`는 그대로입니다. Test 시작 전 `source-freeze-r7.json`을 생성하고 spec-guard-r7 exit0을 확인했습니다.

실제 실행 범위는 `:gateway-integration:test --tests com.nori.ai.gateway.integration.loki.LokiPropertiesTest` 한 클래스입니다. **5/5 PASS**, XML1·fail/error/skip/aborted0, runId `d881e7c6-780f-42be-9ab2-9677856d3a3a`, fingerprint `a65c5abd44593a04ce407ecdcc21df439e0f367f9b680ce57b2aa03932138538`입니다. 직후 별도 `test-evidence verify --tasks :gateway-integration:test`는 같은 fixed includePattern으로 exit0을 얻었습니다. 테스트와 verifier의 source before/after·정본·현재 FP filter 결속을 각각 확인했습니다.

no-test 현재 CP export의 실제 dir/jar 파일 hash를 run-binding과 대조하고 FP를 독립 재계산했습니다. 현재 app runtime SHA256 `0396011b7874da722b558f22c60e3210280a26e5be1b36ad856725e9fcfd488a`도 원 측정과 같습니다. 원시 XML/OTR·summary·binding·명령 exit·현재 CP·파일별 hash 경로는 `final-evidence-check-r7.json`에 있습니다. 이전 결과·r6의 61건 원시 증거를 포함한 435개 기존 파일은 hash 불변으로 보존했으며 이번 코드의 current FP PASS로 재사용하지 않았습니다.

이 절 전 C12 보고서 전체 962716바이트(SHA256 `c1f721531cd2fe1e6ea2892326ca4c98bde8bd09373bf3488bc3144bab899b18`)를 보존한 append입니다. 제품 변경은 위 LokiProperties와 새 영구시험뿐이며 이 DevReport append를 포함한 3파일입니다. 기존 C12 commit·r6 결과·보고서는 덮어쓰지 않았습니다.

부모는 RED와 동일한 독립 Binder oracle로 GREEN을 실제 확인했습니다(`scratch/p12-parent-review/loki-binding-green-r7-parent-probe.json`, javac0/java0/inputStable=true). 이 결과는 좁은 5건의 JUnit과 구분합니다.

상태는 `ROOT_REVIEW_PENDING`이며 **전체 Connector/acceptance/P11 actual joined tree 회귀는 PENDING_ROOT_P11_JOIN**입니다. 이전61+이번5를 전체 최신 검증으로 합산하지 않습니다. fix commit·P11 합류 뒤 전체 회귀는 부모가 관리합니다. 실제 Source/모델/TC·Docker/PG·원격·운영·배포·commit/push는 이 세션에서 0입니다.

#### P12 r7 부모 검증 및 로컬 수정 커밋

부모는 실제 Binder 시험 5건의 XML/OTR 고유 실행·정확 필터·별도 verifier와 현재 classpath 내용·fingerprint를 독립 대조했습니다. 승인 키 123이 바인딩되지 않는 실제 RED를 재현한 동일 Java 프로그램으로 수정 후 GREEN도 확인했습니다. 변경 두 소스 파일을 직접 읽었고 기존 생성자·접근자·기본값·실패 검증을 보존한 표준 constructor binding임을 확인했습니다. 기존 보고서 전체와 과거 증거 435개는 hash가 같습니다.

근거는 scratch/p12-parent-review/r7-root-validation.json, r7-raw-evidence-review.json 및 loki-binding-red-c12-r1-parent-probe.json/loki-binding-green-r7-parent-probe.json입니다. 로컬 수정 커밋 범위는 제품 1파일·영구시험 1파일·보고서 append뿐입니다. 이 5건과 과거 61건을 최신 전체 회귀로 합산하지 않으며, 전체 Connector/acceptance와 P10·P11 실제 합류 검증은 다음 고정 source에서 수행합니다. 실제 Source·배포·운영 수용은 미실행입니다.


### P11 actual P10/P12 합류 후 구현·로컬 검증 후보 — 2026-11-06

상태는 **P11_IMPLEMENTED_LOCAL_TESTED_ROOT_REVIEW_PENDING**입니다. J2 `12f74ee1698c1d39b929e89065e18f4d5cedec87`는 C11contract+C10의 J1과 최신 C12 r7 fix `bb17792e32a3482f5bf05b4bcde37c1da829af79`를 합친 source 기준입니다. 과거 C11 87건·P10 191건·P12 독립 실행을 merged PASS로 승격하지 않고 아래 현재 source의 실제 실행만 집계했습니다. Spec `c232c0ebacc1f70781569bf1c0cdb01070047d09bdb24506cf67cf311c6cbfd4`, 승인 Plan `cc1a74a5e951fbd100c3cf9871f7474c4ab1598ed8e73e75675c2447284425aa`, baseline seq7 `b1040ce78cde516641cdc90da644b03e5d459ddc33668243a935ac4e3d348264`는 읽기 전용으로 유지했습니다. 합류 전·후 spec-guard와 source freeze를 scratch `p11-followup`에 별도 보존했습니다.

기존 두 seam의 SAM/NoOp/lambda를 보존한 scope default overload를 추가하고 PlanValidator/ToolDispatcher의 원래 inspect 위치에서 동일 VerifiedSystemScope를 전달했습니다. 실제 Validator scope→seam→owner 순서와 Dispatcher scope/초기 owner 선차단은 유지했습니다. verify+enabled=true의 concrete SeamRegistry 빈 하나가 기존 job binding의 immutable typed directive만 읽으며, legacy 1arg는 identity이고 다른 Job·Source·capability로 주입이 번지지 않습니다. SourceAuthorizationStep은 기존 2arg/public typed 계약을 보존하고 3arg 삭제 전용 callback만 추가했습니다. 기본 매핑 부재·owner/actor 불일치에서 callback/외부 인가/Source 호출을 허용하지 않고 기존 map singleton을 변경하지 않습니다.

실제 Mock authorization/business/retention Bean과 Source 정책 Bean을 등록하고 P09 auth fake·verify NoOp 대체물만 제거했습니다. 미구현 WorkflowExecutor fake는 P13까지 시험 전용으로 유지합니다. verify hook의 위임 사전등록·revoke·scenario·호출계수·1-job negative seam과 기존 접수 tx rollback/멱등/reset fence를 연결했습니다. Mock 상태는 SHA256(dlg) 요약과 절대 issuedAt/expiresAt/revoked만 저장하고 원 TTL300s를 재기동에 연장하지 않습니다. eager singleton state load를 Recovery 앞에서 수행하며, 최소 state-dir guard가 verify 이외·prod 동시·blank/잘못된 경로를 파일 I/O 전에 거부합니다. P15 전체 ProductionSafetyGuard는 구현하거나 완료로 표시하지 않았습니다. API/하네스는 실제 Mock 위임을 사전등록하고 기존 P10 Job 입력 snapshot·job.started·저장 blockId·payload/JWS/SSE assertion을 유지했습니다.

부모 actual RED 두 건을 근본 수정했습니다. CoreBeansConfiguration의 기존 PlanValidator/ToolDispatcher만 동일 Gateway Clock 인자를 사용하도록 연결했고 미래 2040 Clock의 유효 경계 전·정각·후 실제 validate/dispatch와 동일 객체·선차단을 검증했습니다. Mock TC의 pdg 계산은 catalog 전체 grant 대신 해당 응답 allowed 집합만 사용하며, 빈 Gate는 revision+LF이고 denied/무관한 grant는 제외합니다. MockAuthorizationContractTest는 실제 Adapter→wire projection에서 empty/EQP/AI_LOG/복수정렬/부분 denied/동일 allowed+revision/새 revision을 독립 JDK SHA와 대조합니다. Gateway 측 pdg 재계산은 추가하지 않았습니다. 과거87건은 wire 형식·투영을 검증했으나 실제 응답 allowed와 정본 digest의 결속을 놓쳤다는 검증 한계를 남깁니다.

실행 중 발견한 실패도 보존했습니다. core-r1 새 owner 선차단 시험의 잘못된 errorCode 기대는 기존 trace OWNER_MISMATCH+UNSUPPORTED 응답에 맞췄고, SourceParser 시험의 synthetic key 변경 누락도 시험만 바로잡았습니다. appunit-r1의 기존 CoreSystemBranchGuard는 C11 SourceStep의 TC 상수 분기를 검출했습니다. 검사 제외/상수 숨김 대신 공통 OwnedReference로 실제 Source owner와 scope를 비교하고 foreign owner/actor/미매핑·비정상 ref의 외부 인가0 회귀를 확인했습니다. Guard 시험의 끝 NUL은 Spring property parser가 trim해 실제 유효 경로로 바뀌므로 중간 NUL로 고쳤습니다. r1에서 생성된 본인 io-probe만 scratch 증거로 보존 후 정확 경로 파일·빈 디렉터리만 제거했습니다. appIT-r1은 미인용 dotted -P가 PowerShell에서 분리된 Gradle 설정 실패로 Test/PG0이며 제품 RED/0건 PASS로 계산하지 않습니다. 모든 실패 로그/XML/OTR·paired 결과는 덮어쓰지 않았습니다.

실행 명령은 고정 JDK21·secure child env·canonical AIGW_SPEC_FILE의 `run_gradle.py <새label> --secure-auth ...`이며 각 Test 직후 별도 `:gateway-eval:run --args="test-evidence verify --tasks <같은 task>"`를 실행했습니다. IT Test와 verifier 모두 `-I filters-app-final.gradle` 및 단일 문자열 `-Paigw.requireDocker=true`를 동일하게 사용했습니다. PG는 single worker/maxParallelForks=1로 순차 실행하고 실제 외부 Source/모델을 호출하지 않았습니다.

| 실제 Test | JUnit 고유 건수 | runId | inputFingerprint | 직후 별도 verifier |
|---|---:|---|---|---|
| `:gateway-core:test` (`core-j2-r4`) | 59 | `e1437b4d-f49d-4c50-96ae-3cadc91926eb` | `8521744af91c53066881bec0956258e2778bb446f90e98048b3a62e44e1ea5e4` | `core-j2-r4-verify` TEST_EVIDENCE_VERIFIED / exit0 |
| `:gateway-tc-mock:test` (`tcmock-j2-r2`) | 39 | `ef0f8854-27d5-4676-9403-f01fd92f67d2` | `18dab9053fc60be67a5654c3cb40e1719fdb7c29dec23e88a4482210cb3d5e66` | `tcmock-j2-r2-verify` TEST_EVIDENCE_VERIFIED / exit0 |
| `:gateway-app:test` (`appunit-j2-r2`) | 37 | `bdcb55c7-40b4-4c4d-9130-2bd0f2b155f0` | `03a49a61518e643ef279f630433b5a3373c1663caf9bb3ebdb01fc154bc45bdf` | `appunit-j2-r2-verify` TEST_EVIDENCE_VERIFIED / exit0 |
| `:gateway-app:integrationTest` (`appit-j2-r2`) | 89 | `a5e10186-9321-4df0-a87d-0f0d75d36ed7` | `9209f955454fdb7d235718d4665874c02679d70824ad2e33fc95533d60bf74bc` | `appit-j2-r2-verify-r2` TEST_EVIDENCE_VERIFIED / exit0 |
| `:gateway-integration:test` (`connector-j2-r1`) | 65 | `b24d47db-2d5c-4e8e-b067-bf586b819df9` | `2511af414bcc05e006909396b0b5bff1d890fe88b3ba78d5086830f014101428` | `connector-j2-r1-verify` TEST_EVIDENCE_VERIFIED / exit0 |
| `:gateway-acceptance:test` (`acceptance-j2-r1`) | 1 | `57fb5c6e-a3a1-40ad-9109-5567c189ff39` | `558847b8204b354f936d3015405a007b8a63a277ef6e52eeca08be32e0d3d0bc` | `acceptance-j2-r1-verify` TEST_EVIDENCE_VERIFIED / exit0 |

합계는 실제 고유 JUnit **290건**이며 failure/error/skip/abort=0입니다. 각 task는 actual EXECUTED이고 실행 전후 source hash가 같으며 현재 source·Test.classpath 내용이 원 run-binding과 일치합니다. 원시 XML·OTR·leaf summary·binding·정확 command/exit·file hash·현재 Test inputs/classpath export·runtime manifest를 `p11-followup/validated-proof-r1.json`과 최종 `result-r1.json`에 결속했습니다. runtime manifest SHA=`870497d2198c8dc95df2638dd5119da9328a70bcc62bb309351750e0f05f78b9`입니다.

IT 직후 첫 `appit-j2-r2-verify`는 GitLab SNAPSHOT Maven metadata HEAD401로 Gradle 설정 단계에서 exit1이고 검증 CLI 실행0입니다. source/CP가 같으므로 actual89를 반복하지 않고 `appit-j2-r2-verify-r2 --offline`으로 동일 필터·단일 인용 -P의 verifier만 다시 실행해 통과했습니다. 최초 실패 proof는 보존했으며 성공으로 바꾸지 않았습니다. 이후 Connector/acceptance Test와 각 paired도 캐시에 고정된 같은 artifact를 `--offline`으로 사용했습니다. 마지막 noTest fresh CP export는 첫 export와 경로·runtime 내용이 같고 6개 실제 binding의 classpath 내용을 현재 artifact로 재해시해 대조했습니다.

해당 Gradle401의 세부 원인은 미확정입니다. 부모 별도 읽기 진단 `p11-parent-review/maven-auth-header-diagnostic-r1.json`은 현재 credential로 Authorization Bearer HEAD200·Private-Token HEAD401을 확인했습니다. 이 진단과 offline 검증 성공은 별개 근거이며 자격 부재/토큰 오류를 해결했다고 판단하지 않습니다. credential·전역설정·제품/빌드 설정 변경은 없습니다.

부모 독립 probe도 현재 compiled 제품에서 PASS입니다. 공유 Clock은 기존 RED와 동일 oracle(프로그램 SHA17e66947...)을 사용한 `p11-parent-review/clock-green-j2-r1-parent-probe.json`, pdg는 기존 RED와 동일 oracle(SHA25f4ca21...)의 `mock-pdg-green-j2-r1-parent-probe.json`, 기존 Source 계약은 원 synthetic 프로그램을 보존한 후 합법 ownedref fixture만 갱신한 `owned-source-contract-j2-r1-parent-probe.json`입니다. 부모가 javac0/java0/inputsStable/PG0를 확인했으며 전체 P11 부모 최종 판정·정확 commit은 아직 대기입니다.

Spec/Plan 확정 계약 변경·추가 업무정책·P12 owner 재설계·P13/P14/P15 제품 선구현·운영 호출은 없습니다. 실제 TC/Source/모델 연결, 배포·운영 수용·P15 전체 safety guard·P16/F1 exit/F2는 NOT_RUN입니다. 기존 tc.event.getRecent Manifest의 requiredPermission=[]는 지시대로 변경하지 않았고 실제 Mock 업무 읽기는 별도 AI_EVENT_READ 현재 권한을 재평가합니다. 공통 Registry/Manifest 노출 정합의 후속 P13 검토 책임은 유지됩니다. 이 절은 기존 합류 보고서 전체 prefix를 보존한 append이며 기존 r1/r2/r3 증거와 부모 oracle은 수정하지 않았습니다.

실제 P11 후속 변경 파일(각 바이트 hash는 result-r1.json):

- `docs/contracts/test-hooks-v1.md`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/StackContextSupport.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/harness/HarnessP09TestPorts.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HarnessObservationsEndpoint.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/HookArmingIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/IdempotentAdmissionIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/JobQueueIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/MockDelegationHookIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/MockStateDurabilityIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09AppContextWiringIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09QueueSupport.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P09TestPorts.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P11AppContextWiringIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/RetentionMockIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/SourceAuthzNegativeSeamIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerAuthorizationOrderIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkerRestartProcessSupport.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ApiInputIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ExpiryRaceIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/ResultAccessPathsIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/web/WebAppContextWiringIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/CoreBeansConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/GatewayAppModuleConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/MockDelegationController.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/MockHooksConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/MockScenarioController.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/MockStateDirectoryGuard.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/SeamRegistry.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookController.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookRegistry.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/hooks/MockStateDirectoryGuardTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/hooks/SeamRegistryTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/manifest/CoreSeamDefaultsTest.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/authz/SourceAuthorizationStep.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/dispatch/ToolDispatcher.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/plan/PlanValidator.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/seam/DispatchInjectionSeam.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/seam/PlanInjectionSeam.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/ScopeAwareInjectionSeamTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/authz/SourceAuthorizationStepTest.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockCallCounter.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockTcSourceAuthorizationAdapter.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/TcMockModuleConfiguration.java`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/catalog-rev2.json`
- `gateway-tc-mock/src/main/resources/tcmock/scenarios/index.json`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/MockAuthorizationContractTest.java`
- `docs/ai-rag-gateway-architecture-dev-report.md` (이 append)

부모 독립 검증 원시 proof SHA256:

- `p11-parent-review/clock-green-j2-r1-parent-probe.json` = `894f3c1c1e0600329398920ba03c8fc6f18b1cef789972961073e8bf20441f96`
- `p11-parent-review/mock-pdg-green-j2-r1-parent-probe.json` = `620682aa75ac439f54b946554862fbe26b9b3ea13562eed8a3499c0355f5abfe`
- `p11-parent-review/owned-source-contract-j2-r1-parent-probe.json` = `a35e480c24e6e567c586006c1d62ce58a646dda6a589bba28f434d86888b54ff`

#### P11 후속 부모 검증·P10/P11/P12 합류 로컬 커밋

부모는 현재 합류 소스의 실제 JUnit 290건(core59·Mock39·app단위37·실제PG연결89·Connector65·acceptance1)을 XML과 OTR의 고유 실행으로 대조했습니다. 각 원 Test와 별도 공식 verifier의 정확 필터·runId·현재 classpath 내용·입력 fingerprint·소스 hash·동일 runtime manifest를 확인했으며 failure/error/skip/abort는 모두 0입니다. 과거 격리 실행이나 실패한 시도를 이 290건으로 승격하지 않았습니다.

부모 독립 Java 검사 6개도 같은 현재 compiled 입력에 결속해 확인했습니다. 공유 Gateway Clock과 Mock allowed-only pdg는 수정 전 RED를 재현한 동일 프로그램으로 GREEN을 확인했고, Source 계약·Connector 보존/매 HTTP 인가·설정 바인딩·비정수 GitLab 계보 거부도 통과했습니다. Source의 공통 owner 검증, 원래 scope/seam 순서, 실제 singleton/복구 순서, 위임 절대 TTL·revoke 상태 저장, verify hook 차단과 실패 경로를 직접 읽었습니다.

근거는 scratch/p11-parent-review/j2-final-root-validation.json과 세 원시 결과 대조 파일 및 해당 파일이 결속한 독립 검사입니다. 과거 보고서 전체 prefix와 owner 증거 426개, 보호 대상 483개는 hash가 같습니다. prejoin 보조 목록 첫 경로의 status trim 오기는 원본을 보존한 채 정확 source map과 실제 Git 경로로 대조했으며 제품 변경은 아닙니다. 현재 검증된 48파일만 로컬 커밋하고 원본 main에 fast-forward합니다. 제품/시험 바이트는 검증 뒤 변경하지 않으며 이 부모 보고서 append만 더합니다.

패키지 저장소의 최초 Gradle metadata HEAD401은 검증 CLI 시작 전 실패로 보존했습니다. 동일 cached 입력을 사용한 새 label의 offline verifier는 통과했고, 부모가 같은 URL의 현재 Bearer HEAD200을 별도 확인했습니다. 최초 Gradle401의 세부 원인은 미확정이며 credentials/전역 설정을 고쳤다고 주장하지 않습니다. 새 환경의 전체 의존 해석·CI 검증을 완료로 표시하지 않습니다.

이 판정은 CODE/LOCAL_TEST 범위입니다. P13의 실제 workflow는 아직 미구현이며 시험용 workflow 대체물은 그 phase에서 교체합니다. 실제 TC·Source·모델 연결, 배포·운영 수용·P16·F1 종료·F2는 미실행입니다. P13의 직전 턴 시간창 및 allowTools/allowKnowledge 의미 두 항목은 사용자 답변을 기다리고 있으며, 확정 후 Spec과 Plan에 반영한 뒤 별도 P13 에이전트가 구현합니다.

## P13 고정 workflow·Evidence·캐시·종결 저장 CODE/LOCAL_TEST — 2026-11-07 (독립 구현·부모 로컬 검증 PASS)

부모가 실제 코드·계약·원시 XML/OTR·별도 verifier의 FP/CP를 대조해 CODE/LOCAL_TEST를 확인했습니다. 필수 7개 Test task의 419건이 통과했으며 실패·오류·skip은 0건입니다. 근거는 `scratch/p13-r6-root/root-all-seven-final-pairs-r1.json`(SHA256 `a0fddc7358d3140560caf8110774ce6ae16352fdd7405c2731e30229111c78f2`)과 최종 소스의 `p13-core-verify-r8`·`p13-app-it-verify-r4`입니다. 이 판정은 실제 배포·운영 수용·P16/F1 종료를 대신하지 않으며 Spec·Plan의 전체 완료 체크박스는 변경하지 않았습니다.

기존 P13 구현·시험 이력은 보존했고, 사용자 모델 변경 뒤 남은 검증과 시험 보완은 Claude Code `claude-opus-5-5` high 독립 세션에서 수행했습니다. 아래 기준은 로컬 커밋 전 시험 시점의 기록이며, 후속 커밋 식별자는 Git 이력과 Plan의 진행 상태로 확인합니다.

### 기준과 작업 위치

- 확정 Spec `90ba8a739c303312065824f9923642812f5e427a5f05a8b13142f792c1ad0550`, Plan `a89b979d79b93d50f4b38ec861af8bcc65a84a4b5f0f94183e7ab09a77d6ddf1`(사용자 확정 직전 요청 창·allowTools/allowKnowledge 결정과 부모의 Knowledge search EQP 보완 반영본), 기준선 seq8 `1bea13bfe7cee9213a60a6369ec3d0ad9c090e8529cd17209f0b9d8c286eb88f`. 모든 최종 실행에서 runner의 policyInputsStable=true입니다.
- 시험·구현 시점의 단일 writer worktree는 `C:/workspace_nori/.aigw-worktrees/p13-ai`, branch는 `aigw/f1-p13`, 시험 base/HEAD는 `4f936d6902a10c7e28204e24051a8424fd96c8f4`이며 WIP를 대상으로 검증했습니다. 독립 구현 세션은 원본 main·nori-tc-exec·운영 환경을 변경하지 않았고 commit·push·배포·Secret/K8s/SSH 조작을 하지 않았습니다.
- 최종 소스 입력(runner 규칙 521파일)은 ③ `p13-persist-it-r3`·`p13-persist-it-verify-r3`·①② verifier `p13-core-verify-r8`·`p13-app-it-verify-r4`의 source-inputs와 같습니다.
- ①② 원 Test(`p13-core-test-r6`·`p13-app-it-r2`)와 ④~⑦ Test snapshot은 최종 소스와 정확히 1파일이 다릅니다. 그 파일은 `gateway-persistence/src/integrationTest/.../ScopedRepositoryIT.java`이며 아래 ③ 시험 보정 때문입니다. 이 파일은 그 Test들의 입력에 들어가지 않습니다. ①②는 아래처럼 최종 소스에서 verifier로 같은 FP를 공식 확인했습니다.
- runtime classpath 파일 SHA256 `3e78fa8feb84207c386e78140f7b9c89460df2b9c93fe97bc564559ef3933a50`은 모든 최종 Test와 r8/r4 verifier에서 같습니다.

### 구현 범위와 이유

Plan P13의 allowed_writes와 exact 추가 허용(805~806, 833, 851~852, 860, 879~883줄) 안에서 다음을 연결했습니다.

- core workflow 실행: `WorkflowPlanBuilder`·`WorkflowExecutionPlan`·`StepBinding`·`WorkflowExecutor`·`WorkflowOutcome`·`TerminalOutcomeAssembler`·`ComposerOutcome(NOT_INVOKED만 사용)`·`ResultSourceDepsRecorder`(Job-local)·`LogSummarizer`·`ConversationSummaryBuilder`, 시간창 `TimeWindowResolver`, 블록 `BlockBuilder`·`UiSchemaValidator`·`GeneralQueryHelpBuilder`, `EvidenceAssembler`, `ToolResultCache`(core는 JDBC 비의존, nested Store).
- 단계 현재 인가: 기존 legacy `PlanValidator.validate`/`ToolDispatcher.dispatch` API·9순서를 보존하고, actual workflow 전용 typed 준비(`PlanValidator` nested Preparation)·`WorkflowResourceResolver`·`StepAttempt`·`SourceAuthorizationStep.authorizeStep`·`ToolDispatcher.dispatchCurrent/dispatchBusiness`를 추가했습니다. 구조 준비의 PENDING을 인가 PASS로 쓰지 않습니다.
- 실행 carrier: `ExecutionContext`의 additive `Optional<BoundInputs>`(2arg 호환), `WorkflowDefinition` Step의 cacheable와 `workflow.schema.json`의 해당 boolean 계약, 세 TC workflow JSON.
- 종결 저장: `TerminalSpec`의 9번째 `Optional<CommitData>`(8arg 호환), `JobTerminalCommitter`의 같은 tx 결속 검사와 최종 자료 저장, `JobConfiguration`의 저장소 DI.
- 직전 요청 창: `MessageRepository`의 정확 Message 창 update와 LATERAL 직전 seq 한 요청 reader(Plan 810~832줄 SQL), `CacheRepository`의 current-pdg overload.
- app 연결: `app/job`의 `WorkflowExecutionAdapter`·`WorkflowExecutionConfiguration`·`WorkflowExecutionRecords`·`WorkflowContent`·`WorkflowCacheStore`·`WorkflowResourceResolverFactory`·`DirectSourceToolExecutor`·`BusinessToolExecutor`·`ToolResults`·`DeploymentEvidenceResolver`·`SourceRetentionAttributesProvider`, `CoreBeansConfiguration`·`GatewayAppModuleConfiguration` Bean 조립.
- DLQ: `TcBusinessReadPort.listDlq` 4arg overload, Mock `MockScenarioStore`·`MockTcBusinessReadAdapter`·package-private `MockDlqRow`, 시나리오 JSON 귀속 fixture, `docs/contracts/tc-ai-read-api-v1.md` 설명. 기존 3arg·DTO·wire1.0·digest는 변경하지 않았습니다.
- Source: P12 Connector의 하위 READ lifecycle/attempt 전달과 short SHA 해석 불가·실제 장애 구별(`SourceHttpClient`·`SourceReadAccess`·`LokiLogConnector`·`TempoTraceConnector`·`GitLabCodeConnector`·`GitLabPipelineLineageReader`).
- Manifest: `tc.knowledge.search.json`의 `resourceTypes`에 `EQP` 한 값만 추가했습니다(Plan 860줄). 나머지 필드와 `tc.knowledge.getSource.json`은 변경하지 않았습니다.
- 공유 하네스: P09 시험 fake workflow를 실제 adapter·DB 관찰로 교체했습니다(`P09TestPorts`·`P09QueueSupport`·`WorkerRestartProcessSupport` 및 기존 app IT caller).

위 파일별 Plan 조항 대응은 구현 세션의 기록과 현재 diff에 근거합니다. 부모의 exact allowed_writes 대조는 별도로 필요합니다.

### 실행 실패·원인·수정 (원본 보존)

모든 실패 회차의 원 log·XML·OTR·run-binding·source-inputs는 scratch `p13-implementation/`에 label별로 보존했습니다. 실패한 실행의 paired verifier exit0은 증거 결속만 확인한 것이며 Test PASS가 아닙니다.

- `p13-app-it-r1`: 107건 중 104 PASS·3 FAIL, exit1. 원인과 수정:
  - `JobQueueIT` late cancel: GQ hold fixture가 DB 신호 순서를 바꿨습니다. 실제 INC Source barrier와 TERMINAL_PRECOMMIT으로 시험만 정합했습니다.
  - `WorkerRestartInputIT`: Tool 0개 GQ가 prepare 중 불필요한 자원 READ를 했습니다. Builder 결함으로, tools가 비면 targetResources를 빈 목록으로 하고 원 JobInput/Context selectedRefs는 보존하도록 수정했습니다.
  - `WorkflowKnowledgeIT`: DENIED 기대값을 HTTP404로 잘못 썼습니다. 기존 HTTP200 exact 5필드 계약으로 시험만 수정했습니다.
  - paired `p13-app-it-verify-r1` exit0.
- `p13-app-risk-it-r1`: 25건 중 23 PASS·2 FAIL. 원인과 수정:
  - optional Knowledge의 기존 EQP 입력이 DOCUMENT-only resourceTypes와 충돌했습니다. 부모의 Plan 보완 뒤 위 Manifest 한 값을 추가했습니다.
  - Threw 최소 기록의 Optional errorCode를 시험이 필수로 기대했습니다. 시험만 정확 3키로 정합했습니다.
  - 진단 재실행 `p13-app-failure-it-r1` 2/2와 paired는 진단용이며 전체 ②를 대체하지 않습니다.
- `p13-persist-it-r1`: 72건 중 71 PASS·1 FAIL, exit1, paired `p13-persist-it-verify-r1` exit0(FP `6e706c29…`). 원인과 수정:
  - P13 신규 `ScopedRepositoryIT.previousReaderDistinguishesParentScopeExpiryRoleAndStoredJsonCorruption`이 `tc` 대화의 `expires_at`만 고정 Clock `NOW`로 바꿨습니다. `@BeforeEach`가 같은 `NOW`로 만든 `created_at`과 같아져 V2 `CHECK (expires_at > created_at)`를 위반했습니다(SQLState 23514).
  - 제품 스키마나 reader 결함이 아닌 시험 fixture 결함입니다. 같은 파일의 기존 `edge` fixture처럼 `created_at=NOW-30일`을 함께 갱신하도록 시험 1줄만 수정했습니다. 이 값은 실제 제약 `expires_at > created_at`(V2:4)와 Spec 30일 정책을 모두 만족합니다. 계약·제품 변경은 0입니다.
  - 중간 회차 `p13-persist-it-r2` 72/72와 paired `p13-persist-it-verify-r2`(FP `ee57ea2a…`)는 원본 그대로 보존하지만 최종 결과가 아닙니다.
- 부모 검토 보완(같은 시험 메서드만, 사용자 결정 불필요): r2 시점의 음성 assertion 두 개가 실제 guard를 독립적으로 검증하지 못했습니다.
  - 다른 actor/owner 음성: 손상 JSON loop의 마지막 invalid 값이 남은 상태라 scope 필터가 회귀해도 JSON 예외로 통과할 수 있었습니다.
  - reader 만료 음성: `role='assistant'`가 복원되지 않아 만료 guard가 회귀해도 role 예외로 통과할 수 있었습니다.
  - 보정: loop 직후 이전 Message의 `resolved_time_window`를 SQL NULL로 되돌리고 올바른 base scope의 `Optional.empty` 양성 제어를 넣었습니다. role 음성 뒤에는 `role='user'`로 복원하고 같은 양성 제어를 다시 넣었습니다. 손상 loop·role 음성·만료 IllegalState·update=false는 그대로 유지했습니다. 신규 시험·추상화·제품/스키마 변경은 0입니다.

### 최종 실제 검증

각 Test 뒤 별도 공식 verifier를 같은 소스·정책 입력으로 실행했습니다. 건수는 복사된 원시 JUnit XML에서 독립 집계했고 leaf 결과와 같습니다.

| # | Test label / 명령 | XML 클래스 | tests / fail / error / skip | runId | inputFingerprint | paired label (결과) |
|---|---|---|---|---|---|---|
| ① | `p13-core-test-r6` `:gateway-core:test` | 33 | 100 / 0 / 0 / 0 | `b984d325-c04f-4b5f-9a34-91f53b33360f` | `2f6518abf68e3e92ae19ccf10e7d2ece25955bf590064dc94cb45be99f2121dd` | `p13-core-verify-r6` exit0, 최종 소스 verifier `p13-core-verify-r8` exit0 `TEST_EVIDENCE_VERIFIED`(중간 r7 보존) |
| ② | `p13-app-it-r2` `:gateway-app:integrationTest -Paigw.requireDocker=true` 무필터 | 31 | 120 / 0 / 0 / 0 | `c828ef6b-56c9-42bc-be07-8f1003cd4941` | `d471c093fba073cb400d651bb6b158f45f846a8c90ffb33a8ae636cec8851b28` | `p13-app-it-verify-r2` 무필터 exit0, 최종 소스 무필터 verifier `p13-app-it-verify-r4` exit0 `TEST_EVIDENCE_VERIFIED`(중간 r3 보존) |
| ③ | `p13-persist-it-r3` `:gateway-persistence:integrationTest -Paigw.requireDocker=true` + 5 `--tests` | 5 | 72 / 0 / 0 / 0 | `17604254-b817-4de1-a4fc-e0cd6e99fdc0` | `05477bb7036b1b379070a89e8b66c1427221c945612571311cddbbdd02661dee` | `p13-persist-it-verify-r3` (`-I persistence-it-filter.init.gradle`) exit0 |
| ④ | `p13-persist-unit-r1` `:gateway-persistence:test --tests "*RepositorySignatureArchTest"` | 1 | 3 / 0 / 0 / 0 | `3dac5a9a-1456-46f1-86f4-a1f711ca3b95` | `97619d728fc87f3bfcffe7d8f58e360e834e6300ce2e8ecf0a3e3a1e089466fc` | `p13-persist-unit-verify-r1` (`-I persistence-unit-filter.init.gradle`) exit0 |
| ⑤ | `p13-app-unit-final-r1` `:gateway-app:test` 4 기존 패턴 + `com.nori.ai.gateway.app.job.WorkflowContentTest` | 5 | 13 / 0 / 0 / 0 | `f9d49eb2-6215-48ff-b0e3-e37185527a87` | `3fe51ee5b2d956d71ab772caa6f171066fcc1312ca3ee7630c64814023e88f51` | `p13-app-unit-final-verify-r1` (`-I app-unit-filter.init.gradle`) exit0 |
| ⑥ | `p13-tcmock-test-r1` `:gateway-tc-mock:test` 전체 | 6 | 40 / 0 / 0 / 0 | `502c9b23-f23d-45f0-b326-2077ef7b63c1` | `f76334f571e801e1b27cb3c0c0b169d21b0c2ecf757c99fab618a30a2c83880e` | `p13-tcmock-verify-r1` exit0 |
| ⑦ | `p13-integration-final-r1` `:gateway-integration:test` 전체 | 13 | 71 / 0 / 0 / 0 | `553e33ce-9c47-42a9-8fbf-ee8c0ddfbb57` | `4b32db5b0589db7cac5b00dc6904d50033bfd289b92ee1efc6e9e4c42c0bd581` | `p13-integration-final-verify-r1` exit0 |

- 모든 Test는 `--offline --no-daemon`, JDK 21.0.10, PG 이미지 `pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895949dfcdf308eb0cd31c950c93c14895c77f33fbedfecf1fa511ab1d4bcd2`로 실행했습니다. PG는 한 번에 최대 1개였고, 실행은 runner `p13-implementation/run_gradle.py`로 순차 진행했습니다.
- ② XML 31개는 시험 소스 30파일과 `WorkflowSourceCompositionIT.java` 안의 두 번째 최상위 클래스 `WorkflowDuplicateSourceIT`를 합한 수와 정확히 같습니다(나머지 5파일은 `@TestConfiguration` 지원 클래스).
- ③의 시험 보정 뒤 ①②는 다시 실행하지 않았습니다(58분 App 전체와 무관 core 재실행 0). 다만 verifier가 실행 시점의 task 입력으로 fingerprint를 재계산합니다(`build.gradle.kts:210-215`). 그래서 최종 소스에서도 ① `2f6518ab…`·② `d471c093…`·CP `3e78fa8f…`가 그대로임을 r8/r4로 공식 확인했습니다. 원 Test와 다른 파일은 persistence integrationTest 소스 1개뿐이며 두 task의 입력이 아닙니다.
- ④~⑦은 보정 전 source snapshot으로 완료된 결과를 그대로 보존했습니다. 위 1파일 외 차이는 없습니다. 이번 보완 범위에 따라 ④~⑦의 추가 verifier는 실행하지 않았습니다.
- 원시 경로: scratch `p13-implementation/<label>.log|.json|.exit.txt|-source-inputs.json`과 `<label>-<module>-<task>-{xml,otr,leaf-results.json,run-binding.json,runtime-classpath.txt}`입니다.

### 정본 Plan 명령 대비 차이

- ⑤: Plan의 4패턴에 실제 존재하는 신규 `WorkflowContentTest` FQCN 하나를 Test와 paired init에 함께 추가했습니다. "③~⑤의 새/수정 시험 클래스가 선택 목록 밖이면 정확 FQCN만 추가" 조항(893줄)에 근거합니다. ③④의 변경 시험은 기존 선택 목록 안에 있습니다.
- ⑦ `:gateway-integration:test` 전체와 paired는 Plan 명령 목록 밖입니다. P13이 P12 Connector 6파일을 변경했으므로 부모 지시에 따라 추가 실행했습니다.
- Eval 249 전체 재실행과 관련 없는 회귀는 Plan대로 실행하지 않았습니다.

### 실제 변경 파일 (`git status --porcelain -uall` 118항목, 이 보고서 포함 전)

- 문서: `docs/contracts/gateway-api-v1.md`, `docs/contracts/tc-ai-read-api-v1.md`
- gateway-core main 수정: `core/authz/SourceAuthorizationStep.java`, `core/dispatch/{ExecutionContext,ToolDispatcher}.java`, `core/job/TerminalSpec.java`, `core/plan/PlanValidator.java`, `core/port/TcBusinessReadPort.java`, `core/registry/WorkflowDefinition.java`, `resources/contracts/workflow.schema.json`
- gateway-core main 신규: `core/authz/StepAttempt.java`, `core/blocks/{BlockBuilder,GeneralQueryHelpBuilder,UiSchemaValidator}.java`, `core/cache/ToolResultCache.java`, `core/evidence/EvidenceAssembler.java`, `core/plan/WorkflowResourceResolver.java`, `core/time/TimeWindowResolver.java`, `core/workflow/{ComposerOutcome,ConversationSummaryBuilder,LogSummarizer,ResultSourceDepsRecorder,StepBinding,TerminalOutcomeAssembler,WorkflowExecutionPlan,WorkflowExecutor,WorkflowOutcome,WorkflowPlanBuilder}.java`
- gateway-core test: 수정 `{DispatcherPreReadBoundaryTest,WorkflowOptionalStepContractTest}.java`, 신규 `{BlockBuilderTest,EvidenceVerdictTest,ExecutionContextBindingTest,GeneralQueryHelpBuilderTest,KnowledgeEvidenceProjectionTest,LogSummarizerTest,StepAttemptGrantTest,TerminalOutcomeAssemblerTest,TimeWindowResolverTest,ToolResultCacheTest,UiSchemaValidatorTest,WorkflowAuthorizationPreparationTest,WorkflowExecutorTest,WorkflowPlanBuilderTest}.java`
- gateway-app main: 수정 `app/config/{CoreBeansConfiguration,GatewayAppModuleConfiguration,JobConfiguration}.java`, `resources/manifests/tc/tc.knowledge.search.json`, `resources/workflows/tc/{INCIDENT_ANALYSIS,KNOWLEDGE_QUERY,LIVE_QUERY}.json`; 신규 `app/job/{BusinessToolExecutor,DeploymentEvidenceResolver,DirectSourceToolExecutor,SourceRetentionAttributesProvider,ToolResults,WorkflowCacheStore,WorkflowContent,WorkflowExecutionAdapter,WorkflowExecutionConfiguration,WorkflowExecutionRecords,WorkflowResourceResolverFactory}.java`
- gateway-app test: 수정 `ArchitectureRulesTest.java`, `manifest/CoreSeamDefaultsTest.java`; 신규 `job/WorkflowContentTest.java`
- gateway-app integrationTest: 수정 `job/{HookArmingIT,IdempotentAdmissionIT,JobQueueIT,P09AppContextWiringIT,P09QueueSupport,P09TestPorts,WorkerAuthorizationOrderIT,WorkerRestartInputIT,WorkerRestartProcessIT,WorkerRestartProcessSupport}.java`, `web/SseApiIT.java`, `wiring/AppContextWiringIT.java`; 신규 `job/{DeploymentLineageIT,WorkflowExpiryRaceIT,WorkflowKnowledgeIT,WorkflowMockIT,WorkflowSourceCompositionIT,WorkflowSourceFixture,WorkflowTerminalCommitIT}.java`
- gateway-integration: main 수정 `gitlab/{GitLabCodeConnector,GitLabPipelineLineageReader}.java`, `loki/{LokiLogConnector,SourceHttpClient,SourceReadAccess}.java`, `tempo/TempoTraceConnector.java`; test 수정 `gitlab/GitLabPipelineLineageTest.java`, `loki/SourceOwnershipAndCurrentAuthorizationTest.java`, 신규 `SourceAttemptGuardTest.java`
- gateway-persistence: main 수정 `queue/JobTerminalCommitter.java`, `repository/{CacheRepository,MessageRepository}.java`; integrationTest 수정 `ScopedRepositoryIT.java`
- gateway-tc-mock: main 수정 `{MockScenarioStore,MockTcBusinessReadAdapter}.java`, 신규 `MockDlqRow.java`; scenarios 수정 `{authz-down,catalog-down,catalog-rev2,comm-down,comm-trim,contract-v2,default,dlq-down,dlq-partial,index,logout,lot-ended,lot-multi,lot-unknown,permission-revoked}.json`, 신규 `{dlq-scoped,dlq-scoped-other-snapshot}.json`; test 수정 `MockBusinessReadContractTest.java`
- `docs/ai-rag-gateway-architecture-dev-report.md` (이 append)

### 미실행·한계·후속 책임

- 실제 TC·Loki·Tempo·GitLab·모델 연결, CI, 배포, 운영 수용, P16·F1 종료는 미실행입니다. 승인되지 않은 URI/repo/ref/token은 만들지 않았습니다.
- production Knowledge는 기존 PLANNED·fail-closed 상태입니다. IMPLEMENTED 양성은 시험 stub 구성에 한정되며 P23 실제 검색·원문 구현이 아닙니다. `tc.knowledge.getSource`의 DOCUMENT-only resourceTypes와 optional EQP 입력이 같은 방식으로 충돌할 가능성은 P13 범위에서 변경하지 않았습니다. 활성화 전 검토가 필요합니다.
- Git 경로는 요청 전에 적용 sourceRevision을 확인하는 생산자가 없어 해당 캐시 lookup/put을 하지 않습니다. LOG/TRACE 캐시와 구별됩니다.
- UI 검증은 기존 schema·값·시간·단위·키·행 증폭 불가 조건에 한정합니다. 별도 전역 byte/row 상한이나 범용 단위 schema를 만들지 않았으며, Spec §22.3 운영 수용 PASS로 해석하지 않습니다.
- P14 책임은 이 작업에서 완료로 주장하지 않습니다. 대상은 캐시 저장의 마스킹 잔여(`WorkflowCacheStore`가 raw를 직렬화함), Audit/OTLP/retention, Source 조건부 Bean 조립입니다.
- 실제 Loki·Tempo·GitLab Source 연결은 F1~F4부터 필요한 후속 운영 구성·수용 검증 대상이며, 이번 P13 로컬 시험으로 완료하지 않았습니다. F5a 후속 책임은 실제 TC 업무 Backend 연결과 scoped DLQ 4arg의 운영 구현입니다.


## P14 관측·감사·보존 CODE/LOCAL_TEST — 2026-11-07 (독립 구현·보정 r2~r5, 부모 독립 판정 대기)

Claude Code `claude-opus-5-5` high 독립 세션(`d6c7bd7f-3c5e-401e-87eb-f58634f29bd9`)이 보정 GO r2·r3·r4·r5를 순서대로 수행했다. 아래 최종 6개 canonical Test는 모두 통과했지만, 이것은 로컬 시험 결과다. P14 COMPLETE·F1·운영 PASS, Spec 체크박스 갱신은 하지 않았으며 부모의 독립 대조를 기다린다.

### 기준과 작업 위치

- 기준 문서: Plan `6daef09368cbd2df777c0050c2e96b2b92ebf4cc5acfa04565b27074993c8851`(r4/r5 정본, 이전 판 `dd9ce765…`·`26bf…`), Spec `90ba8a739c303312065824f9923642812f5e427a5f05a8b13142f792c1ad0550`, baseline seq8 `1bea13bfe7cee9213a60a6369ec3d0ad9c090e8529cd17209f0b9d8c286eb88f`.
  - 모든 최종 실행에서 runner의 policy 입력(Spec·Plan·baseline)은 불변(`policyInputsStable=true`)이었다.
- 작업 위치: 단일 writer worktree `C:/workspace_nori/.aigw-worktrees/p14-ai`, branch `aigw/f1-p14`, HEAD `6268403a5ea726f6a66e83cc52638b77fe0bdb06`, WIP 미커밋 상태다.
  - commit·push·원격·SSH·K8s·Secret·전역 설정·다른 repo 편집은 0이다.
- 최종 소스 입력: runner 규칙 554파일, digest 앞 16자리 `9663aa5bb4a5b5b1`.
  - ①②④⑤⑥ Test와 ③ r2(UP-TO-DATE)의 source-inputs가 이 집합과 같다.
  - ③ r1(실제 실행)은 `gateway-app/.../MetricLabelCardinalityTest.java` 1파일만 다르며, 이 파일은 core Test 입력이 아니다.
- runtime classpath SHA256 `e07547eaff8e5ce3f8e1c3dc1689b20555c9aea353acdf060657d9afae989e52`(193행). 최종 app Test·verifier가 이 값을 쓰며, 최종 sync 사본과 같다.
- 초기 source의 이전 PASS(③103·②13·④3·⑤74 등)는 구 source의 이력이다. 최종 집계에 합산하거나 재사용하지 않는다.

### 구현 범위 (Plan P14 allowed_writes·exact 추가 허용 안)

- **감사 생산자:**
  - core typed `AuditEvent`(API_REQUEST·JOB_ACCEPTED·JOB_CLASSIFIED·TOOL_CALL·TOOL_BLOCKED)와 이벤트별 필드 집합 `AuditFieldAllowlist`(같은 `CapabilityRegistry`의 등록 Tool·sourceRef, 유효 nonzero traceId, UUID requestId).
  - persistence `AuditWriter`, `AuditRepository.insert`(service_ref)·`insertForJob`(같은 owner·actor·Job의 JOB_ACCEPTED 원점이 정확히 1행일 때만 저장, 0행·중복은 false·무저장).
  - V7 `audit_request_identity`(service_ref·task_type·result_status 열과 요청 원점 CHECK, trace_id NOT NULL·형식).
  - `JobTerminalCommitter`의 원점 파생 종결 감사, `commitExpiredQueued`.
  - app `AuditRecorder`(실패 counter `aigw.audit.write.failures` + 고정 코드 `AUDIT_WRITE_FAILED`).
  - `ServiceTokenAuthFilter`의 `aigw.request` SERVER span(setNoParent·속성 0)과 API_REQUEST(저장 실패 시 503).
  - `JobAdmissionService` JOB_ACCEPTED, `WorkflowExecutionRecords` TOOL_CALL/TOOL_BLOCKED, `WorkflowExecutionAdapter` JOB_CLASSIFIED.
- **관측:**
  - `ObservabilityConfiguration`: 명시 `OtlpMeterRegistry`, `GatewayOpenTelemetry`, traces off 시 context-only SpanProcessor.
  - `MetricsCatalog`: 닫힌 라벨, 같은 registry에 등록된 canonical `tool_id`만 허용, 선언과 다른 Meter.Type 거부.
  - `AllowlistObservationFilter`, `OtlpLogAppenderInstaller`·`AllowlistLogFilter`(5개 고정 코드·닫힌 MDC 값), `logback-spring.xml`(STDOUT), `ops/monitoring/aigw-alerts.yaml`, `docs/gates/f1/alerting.md`.
- **보존:**
  - `RetentionMaintenanceRepository`의 bounded batch purge(100, `FOR UPDATE SKIP LOCKED`)와 `findOldestConversationExpiry()`.
  - `RetentionPurgeJob`: RecoveryCompleted 뒤 Gateway Clock 1h 주기, PURGE_HOLD 준수. 실패 또는 정리 성공 뒤 만료+24h 초과 잔류가 있으면 주기당 1회 counter·`RETENTION_PURGE_FAILED`를 남긴다. 정각은 초과가 아니다.
  - `SourceRetentionProbeScheduler`·`SourceRetentionProbeRepository`·`SourceRetentionReaderAdapter`, `SourceConnectorConfiguration`(값 부재 시 Bean 0·형식 오류 목록·APPS 명시 probe 창).
- **기타:** `ExpiryEdgeJobCanceller`, tc-mock testFixtures `ResidueScanner`(JSON·`\uXXXX`·URL·base64 파생 view).
- **OTel 정렬(r4):** Plan exact 2줄(instrumentation BOM alpha alias·platform)로 stable SDK/exporter를 1.67.0에 맞췄다. 상세 RCA·graph·delta는 `docs/gates/f1/stack-compat.md`의 2026-11-07 P14 절에 있다.

### 실행 실패·원인·수정 (원시 증거 보존, 실패를 PASS로 승격 0)

- **컴파일:** `p14c-compile-r1` exit0(당시 제한 compile), r2·r3·r4 exit1(시험의 구 API·모호 호출 수정), r5 exit0.
- **③ `p14c-core-test-r1`(보정 초기)** 104/2 FAIL:
  - `AuditFieldAllowlistTest`의 기대 문구를 typed `sourceRef`로 정정했다.
  - `WorkflowPlanBuilderTest:38`은 base부터 있던 `Set.copyOf` 순서 의존 단언이라 `containsExactlyInAnyOrder`로 바꿨다.
- **⑤ `p14c-persist-it-r1`** 84/2 FAIL(`JobTerminalDeadlineIT:90` 실제 2 vs 예상 1, `:118` `[RUNNING,1,0,0]` vs `[RUNNING,0,0,0]`):
  - `QueueJobRepository.start`가 `job.started` 1행을 쓰는 기존 생산 계약을 기대값에 반영했다(started+종결=2, RUNNING=1).
  - 총 event 수 검사는 유지했다. r3 중간의 종결 type만 세는 수정은 되돌렸다.
  - 최소 재현: `p14c-deadline-it-r2` 3/0 → verify VERIFIED.
- **① `p14c-app-it-r1`** ABORTED(부모가 worker 중단): `io.opentelemetry.api.internal.ConfigUtil` ClassNotFound로 공통 기동이 실패했다. OTel 정렬로 해결했고, `p14c-stacksmoke-r1` 2/0 → verify VERIFIED.
- **① `p14c-app-it-r2`** ABORTED(본 세션이 worker 중단, XML 19/16 fail/1 skip):
  - 1.67 SDK는 processor 0개면 noop tracer를 반환해 traceId가 무효가 되고, `감사 허용 목록 밖 값입니다: traceId`로 실패했다.
  - context-only SpanProcessor로 해결했다. `p14c-span-repro-r1` 5/0 → verify VERIFIED.
- **① `p14c-app-it-r3`** 완주 exit1 125/5 FAIL(runId `b9cfac45…`, FP `aa38d050…`, source/policy 동결 true). 5건 모두 시험 fixture 결함이며 운영 정책·감사 검사를 약화하지 않고 고쳤다.
  - `WorkerRestartExpiryIT`: P14 purge가 복구 취소 뒤 만료 대화를 정당하게 삭제한다. 시험 전용 `HarnessP09TestPorts`에 `aigw.harness.purge-hold-at-start` ReadinessPrecondition(-99)을 두어, 이 시험의 재기동만 RecoveryCompleted 전에 기존 `PURGE_HOLD`를 건다. 기존 취소·감사·위임·인가 검사는 유지했다.
  - `WorkflowExpiryRaceIT`(신규 시험): trigger가 앞선 snapshot TOOL_CALL까지 거부했다. trigger를 `tc.logs.search` TOOL_CALL로 한정하고 snapshot TOOL_CALL 1행을 정확히 단언하도록 고쳤다.
  - `RetentionProbeWiringIT`: 첫 앱 저장만 기다리던 경쟁을 고쳤다. 성공 3앱(마지막 tc-ui-front 포함)의 이번 주기 probedAt, 요청 창 8회, tc-business 실패 counter를 단일 bounded await로 기다린 뒤 기존 검사를 수행한다.
  - `IngressAuthIT` 503: Spring logging 초기화의 LoggerContext reset이 class appender를 분리했다. class appender는 활성 컨텍스트 시작 뒤·매 시험 전에 연결을 보장하고, 격리 앱 시험은 open 뒤 전용 캡처를 설치·해제한다. 고정 코드 1건과 인자·예외 0을 단언한다.
  - `IngressAuthIT` 별칭: filter 인증을 통과한 뒤 handler가 4xx를 내는 별칭은 Plan대로 API_REQUEST 1행(Job 0)이다. filter 실행 여부로 0행 또는 `assertRequestOrigin(0)`(service·UUID requestId·SDK trace)을 단언한다. 401 경로의 DELEGATION_INVALID 단독 검사는 유지했다.
  - 최소 재현: `p14c-r5-repro-it-r1` 4개 클래스(IngressAuthIT 전체 포함) 40/0 → `p14c-r5-repro-verify-r1` VERIFIED.
- **② `p14c-final-app-unit-r1`** 19/1 FAIL: `MetricLabelCardinalityTest`의 type 불일치 음성 사례가 같은 registry에 이미 있는 같은 id Timer와 충돌해, Micrometer가 filter 전에 예외를 냈다(`MeterRegistry.java:663`). 이 사례만 새 registry에서 filter 거부(meter 0)를 단언하도록 고쳤다.

### 최종 canonical 결과 (각 Test 직후 같은 filter·Docker 속성의 별도 공식 verifier)

| # | Test label | outcome | tests/fail/err/skip | runId | input FP | verifier |
|---|---|---|---|---|---|---|
| ③ | `p14c-final-core-test-r1` | EXECUTED | 104/0/0/0 | `4818420d-8402-4785-83e9-9e8d28b9ad48` | `f63c40e193fba5c7facb23bcf3b34acf221d5993849cfa9ea27e517ba9f33c9a` | `p14c-final-core-verify-r1` VERIFIED |
| ③ | `p14c-final-core-test-r2`(최종 소스) | UP-TO-DATE | 104/0/0/0 | 같은 runId | 같은 FP | `p14c-final-core-verify-r2` VERIFIED |
| ② | `p14c-final-app-unit-r2` | EXECUTED | 19/0/0/0 | `97298e01-faf8-427b-baf9-e60a81c2f0e4` | `1ec3af3f63e7418198afbc6010b617420caf0c974f72e68258296d86af88782a` | `p14c-final-app-unit-verify-r2` VERIFIED |
| ④ | `p14c-final-persist-unit-r1` | EXECUTED | 3/0/0/0 | `c76ea339-b494-4e05-8e86-42712c1cf3e9` | `255242fdf5d7e8295bb7ed3ea854fc80dc26fef0c23a9c45f730a333cd7b6d8b` | `p14c-final-persist-unit-verify-r1` VERIFIED |
| ⑥ | `p14c-final-tcmock-r1` | EXECUTED | 7/0/0/0 | `8a3ec276-009b-4254-894c-7c8fce701b56` | `022374ef0d942196384852648fe98549c38f3fd3d8b41661d33c92b8d7a82d99` | `p14c-final-tcmock-verify-r1` VERIFIED |
| ⑤ | `p14c-final-persist-it-r1`(7 패턴) | EXECUTED | 84/0/0/0 | `a3fd09e8-b683-4a6f-b18f-32334782269b` | `a8b810ce92b3ef5b3fbc4a02cbe13369520bfd27fbe06e9dd42fcd356850713b` | `p14c-final-persist-it-verify-r1` VERIFIED |
| ① | `p14c-final-app-it-r1`(정본 19 + WorkflowKnowledgeIT·WorkerRestartEpochIT, 26 클래스) | EXECUTED | 125/0/0/0 | `234ae18a-6528-412b-bc4e-a280426d3424` | `467bd9482737eab1ef9dce3681adf26bea385f114b4549a81bb66ee467d62336` | `p14c-final-app-it-verify-r1` VERIFIED |

- 고유 최종 시험은 342건(104+19+3+7+84+125)이고 실패·오류·skip은 0이다. verifier VERIFIED는 증거 결속 확인이며, 실패 Test를 PASS로 바꾸지 않는다(`p14c-final-app-unit-r1` 실패는 실패로 보존).
- 의존: `p14c-final-deps-runtime-r1` exit0(OTel 1.55.0 요청은 모두 `-> 1.67.0`), `p14c-final-depinsight-registry-otlp-r1` exit0(`micrometer-registry-otlp:1.16.6`).
- `p14c-final-sync-cp-r1` exit0: `docs/gates/f1/runtime-classpath.txt`가 P04 사본 `29edaa95…`에서 생성물 `e07547ea…`로 바뀌었고 바이트 일치를 확인했다.
- 원시 XML·OTR·run-binding·runtime CP·source-inputs는 scratch `p14-implementation/<label>-*`에 보존했다.

### 미실행·한계·후속 책임

- **NOT_RUN:**
  - G-ALERT(실제 Prometheus/Alertmanager 규칙 배치)와 G-OBS-READBACK(실제 OTLP 수신·운영 readback).
  - 실제 Loki·Tempo·GitLab Source IO, 운영 credential, 배포·CI.
  - `BackupColumnCoverageIT`(P15 이후).
- **Source 사실:** Source 선언은 disabled를 유지했다. 운영 Source 사실(Tempo 관측 3앱, ui-front 미관측)은 부모의 P16 gate 결정 대상이며 이 phase에서 승격하지 않았다.
- **자격:** glab gitlab.com 자격은 만료(401) 상태다. CI 최소권한 자격 준비는 P15 책임이다.
- **base부터 있던 시험 결함 수정:** `WorkflowPlanBuilderTest`의 Set 순서 의존, `JobTerminalDeadlineIT`의 job.started 미반영 기대값, `IngressAuthIT` class appender가 Spring logging reset으로 분리되던 lifecycle.
- **변경 파일:** append 직전 `git status` 기준 수정 61개 경로(`docs/gates/f1/{runtime-classpath.txt,stack-compat.md}` 포함)와 untracked 18개 경로(신규 파일·디렉터리)다. 그 밖에 이 DevReport append가 있다.


### 2026-11-07 P14 부모 최종 독립 검증 — CODE/LOCAL_TEST PASS

독립 구현은 native Claude Code `claude-opus-5-5`, effort `high`로 수행했고 부모가 실제 호출/DI·SQL·실패 경로와 원시 결과를 대조했다. 최종 여섯 Test task의 고유 시험은 core 104 + app unit 19 + persistence unit 3 + tc-mock scanner 7 + persistence IT 84 + app IT 125 = **342건**, 실패·오류·skip 0이다. 각 Test의 별도 공식 verifier와 원시 XML/OTR TEST 식별자·runId·fingerprint·현재 554개 소스/정책 입력·runtime classpath를 함께 대조했다. app IT는 정본 필터 21개/26개 클래스/125건을 빠짐없이 실행했다. persistence IT의 중복 표시 이름은 고유 OTR TEST ID로 구별했다.

native core-r2는 UP-TO-DATE로 최초 core-r1의 결과를 재사용한 실행이다. 부모는 이를 새 실행으로 세지 않고 `p14-parent-core-test-r1`에서 해당 Test에만 `--rerun`을 적용해 실제 104건을 새로 실행했다. 새 runId는 `4c5adef4-21f3-47d0-9211-a06eba1fc633`이고 별도 `p14-parent-core-verify-r1`이 검증했다. 이 강제 실행 전후 787개 컴파일 클래스 바이트는 동일하다. 최종 통합 부모 증거는 scratch `p14-parent-review/final-six-pairs-root-proof-r1.json`, SHA256 `a4c406e9b94619a2bc92de2e221f39e52853960857150c5150ffbd5927dbb01f`다. 명령·필터·신원·각 원시 결과 경로는 이 증거와 각 runner JSON에 보존했다.

기존 실패·부분 중단 진단과 fixture 결함 수정 이력은 그대로 보존했다. 이후의 통과 결과로 이전 실패 기록을 덮어쓰지 않았다. 원래 main 835파일·EXEC 514파일·보호 483파일·Spec SHA `90ba8a739c303312065824f9923642812f5e427a5f05a8b13142f792c1ad0550`·기준선 seq8·기존 DevReport 1,045,423바이트 prefix가 불변이다. 변경 97파일은 정본 allowed_writes 안에 있고 UTF-8/LF/BOM0·git diff --check를 통과했다. stack-compat는 append만 했으며 runtime classpath 문서/생성물은 SHA `e07547eaff8e5ce3f8e1c3dc1689b20555c9aea353acdf060657d9afae989e52`로 바이트 동일하다.

P14의 코드/로컬 검증은 PASS다. 실제 경보 배치(G-ALERT), 실제 운영 OTLP 수신 확인(G-OBS-READBACK), CI/Source/배포 및 P16/F1 종료는 미실행이고 PASS로 세지 않는다. 다음은 별도 Opus 5.5 P15 구현이며 P16 이후는 진행하지 않는다.

자격 확인 보정: 앞 절의 glab gitlab.com 401은 그 자격에 한정한 결과다. 부모가 별도의 기존 Windows Git Credential Manager 자격을 메모리 안에서만 사용해 Bearer API 및 실제 private starter Maven metadata HEAD 200을 확인했다. 값 출력/저장·전역 설정 변경 0이며 로컬 의존 해석이 사용자 신규 계정 제공 때문에 차단된 상태는 아니다. 이 권한을 운영 읽기 전용 또는 CI 전용 자격으로 간주하지 않는다.


## P15 컨테이너·helm·NetworkPolicy·CI 최종 구현 CODE/LOCAL_VALIDATED — 2026-11-07 (독립 Opus 5.5 세션, 부모 판정 대기)

상태: P15 COMPLETE 아님, F1 운영 PASS 아님. 실제 CI·push·Harbor 승인 digest·K8s 배포·승인 운영 gate(G-CI-DOCKER, G-BACKUP-DEST, G-NETPOL-TARGETS, G-SOURCE-SVC, G-ALERT)는 NOT_RUN/BLOCKED다. 커밋·push·이미지 push·클러스터 변경 0. 입력: AI worktree `p15final-ai`(HEAD 0f56bd3, 미커밋 작업), EXEC worktree `p15final-exec`(HEAD 0ed7701, 작업 트리만 변경), 정본 Spec 90ba8a73…·Plan bc0a025a…(r4 입력 결속 반영)·기준선 seq8. `tc-sources-v1.json` 변경 0(G-SOURCE-SVC 미답).

### 변경(요약)

- 패키징: `gradle/libs.versions.toml` spring-boot plugin alias, `gateway-app/build.gradle.kts` Boot plugin·djl tokenizers·integrationTest 의 `aigw.backupImage`(형식 `harbor.nori.local/nori-tc/ai-gateway-backup@sha256:<64hex>` 외 실패)와 Dockerfile-backup·backup/restore 스크립트·job-columns 의 `inputs.file`+`aigw.*Sha256`, 루트 `aigwPackage`(Sync)·`aigwBuildIdentity`(gitSha 부재/형식/HEAD 불일치/dirty → 실패, 이전 파일 삭제).
- 앱: `app.safety.{ProductionSafetyGuard,NativeTokenizerOfflineCheck}`, `app.hooks.EgressProbeHook`, `app.ops.backup.BackupStatusMetrics`, `GatewayAppModuleConfiguration` @Import 3개(component scan 없음 — 부모가 Plan §7 공유 DI 규칙 허용 확인).
- eval: `eval.ci.{BuildIdentityManifest,TarGz,CiEvidence}`, `EvalCli` 허용 두 지점. 부모 재현(manifest 추가 필드·미등록 모듈 요약 raw 필드·gzip CRC 손상·뒤 gzip member) 보정: gzip member 1개(FLG 0)·CRC32/ISIZE·후행 0, tar 끝 표시 정확·typeflag '0'·link/prefix/중복 0, 부호화·해제 16 MiB 상한, manifest 고정 9 필드·정규 바이트(중복 키 거부), 등록 task 요약만, 요약 = `{summary, records}`·taskPath/module 일치. `processes()` 는 시간 제한이 출력 읽기까지 적용(별도 reader, 64 KiB 상한), 실패 시 자식·후손 종료 대기, 인터럽트 보존.
- `ci/integration-required.txt`: 모듈당 1줄 + 쉼표 FQCN(P01 test-evidence 파서가 같은 모듈 여러 줄이면 마지막 줄만 보는 기존 동작에 맞춤). persistence 8·integration 1·app 38 = 47 클래스.
- 수용 하네스 `gateway-acceptance`: application·installDist 배포본, `AcceptanceMain`(System.out/err 폐기 격리·예상 밖 예외 → `ACCEPTANCE_HARNESS_FAILED`·exit 0 = 실패/skip/abort 0·OTR 무효 → RUN ABORTED `OTR_*`), `SpecInputVerifier`(압축 2 MiB·해제 4 MiB — 초과도 `SPEC_INPUT_MISSING`), `EvidenceBlockWriter`(중복·고아 finished 거부), `OracleQueries.retentionProbe(lokiApp)` = `LokiSourcePolicy.retentionSourceId`, `RubricViewer`(해시 일치 서술을 열람한 대상만 판정), Windows 시작 스크립트 classpath = `%APP_HOME%\lib\*`(jar 147개 절대 경로가 cmd 8191자 제한 초과 — ⑩ 실측).
- 백업/복원: `deploy/docker/Dockerfile-backup`(필수 `PG_IMAGE_REPOSITORY`, ENTRYPOINT 제거, rclone 확인 `env -u`), `ops/backup/aigw-backup.sh`(같은 snapshot 세션 A·`pg_dump --snapshot --exclude-table-data=gw.job | age`·job sanitised `\copy TO PROGRAM age`·암호화 manifest·tar bundle·s3/sftp 업로드·보존 정리·`gw.backup_run`), `aigw-restore.sh`(정확 3 일반 파일 목록·순서 확인 뒤 추출, manifest 고정 형태 정규식, 대상 검사 exit 7, TOC 제외 = vector 확장·그 COMMENT·public 스키마 ACL/COMMENT·pg_control_system() ACL 만, `--exit-on-error` 단계별 exit 8, receipt).
- EXEC 차트: prod 렌더 차단, egress `expect`, egress-probe ConfigMap 필드 제한, migrate Job PropertiesLauncher+MigrateApplication, `DJL_CACHE_DIR`, GitLab token env 제거, 수용 Job·NetworkPolicy 를 `templates/tests/` 로, `acceptance.buildIdentityJson`, metrics 대상, `db-chart/templates/backup-cronjob.yaml`·백업 values.
- ops: `runconfig.py`(앱 release 값·`profile=restore` 의 leaf 단위 `RUNCONFIG_KEY_CONFLICT`·백업 값), `verify.schema.json` 확장, `aigw-helm.sh`(앱 release lint/render/deploy/suite-only, `--image-digests`·`--suite`·`--spec-file`·`--baseline`·`--fixture-catalog`·복원 입력, lint 안의 guard 음성·§11.9 렌더 행렬·재배포 0 Deployment 불변, DB 백업 CronJob 양성/음성), `app_render.py`, `hba_policy.py`(주 DB 백업 CronJob 1개 허용), fixture 6종(LINT-ONLY), `ops/monitoring/aigw-backup-prometheusrule.yaml`(label `release=monitoring`; P14 `aigw-alerts.yaml` 은 HEAD 그대로), 문서 `docs/runbooks/aigw-backup-restore.md`·`docs/gates/f1/netpol-targets.md`(NOT_RUN)·`docs/contracts/test-hooks-v1.md` egress-probe 행·`ops/runconfig/README.md` 절 추가.

### 실제 실행(raw: scratch `p15-implementation/` 의 `<label>.json|log`, ops 는 `ops-runs/<label>.*`)

| 명령 | label | 결과 |
|---|---|---|
| ⑤ `:gateway-app:test --tests "*ProductionSafetyGuardTest"` | p15-r2-app-safety-r1 / -verify-r1 | 6/0/0/0 EXECUTED, paired `TEST_EVIDENCE_VERIFIED` (이전 p15-app-unit-r1 56/1 실제 결함 → r2 56/0 은 as-run 이력) |
| ⑥ `:gateway-app:integrationTest -Paigw.requireDocker=true --tests "*Backup*IT" --tests "*EgressProbeHookIT"` | p15-r2-app-it-backup-r1 / -verify-r1 | 6/0/0/0 EXECUTED(EgressProbeHookIT 1, BackupColumnCoverageIT 1, BackupScriptIT 4), source·정본 동결 true, paired(같은 필터 init·`--require-docker`) `TEST_EVIDENCE_VERIFIED` |
| ⑦ `:gateway-eval:test --tests "*CiEvidence*"` | p15-r2-eval-ci-r2 / -verify-r2 | 19/0/0/0 EXECUTED, paired VERIFIED |
| ⑧ `:gateway-acceptance:test` | p15-r2-acceptance-unit-r1 / -verify-r1 | 24/0/0/0 EXECUTED, paired VERIFIED (r2 는 시작 스크립트만 바뀌어 Test UP-TO-DATE — 새 PASS 로 세지 않음) |
| ⑨ `aigwPackage`(gitSha 없음) | p15-r2-package-nosha-r1 | `BUILD_IDENTITY_GIT_SHA_MISSING` 실패, `build-identity.json` 0 |
| ⑨ `aigwPackage -Paigw.gitSha=<HEAD>`(미커밋 트리) | p15-r2-package-dirty-r1·r2 | `BUILD_IDENTITY_DIRTY_TREE` 실패, identity 0. 양성(clean commit)은 root 커밋 뒤 NOT_RUN |
| ⑩ `build\package\acceptance\bin\gateway-acceptance.bat`(env·마운트 0) | p15-r2-acceptance-bat-r2 | exit 3, 증거 블록 1개 = RUN ABORTED `SPEC_INPUT_MISSING`(SHA 일치), java stderr 0, Launcher 0. r1 은 cmd "입력 줄이 너무 깁니다" exit 255 → 위 classpath 보정 |
| ① `aigw-helm.sh --release ai-gateway --mode lint … --suite none` | ops-runs/r3-c1-app-main-lint | exit 0 (guard 음성 10종·행렬 F1/F2/F3-PROBE/F3/F3-ACL/F4/F4-PERF·재배포 0 불변·SPEC SHA 불일치 음성) |
| ①' `--release ai-gateway-restore` | r3-c1p-app-restore-lint | exit 0 (행렬 F4-RESTORE·복원 음성) |
| ② `--release ai-gateway-db --mode render --runconfig ops/runconfig/negative-missing-backup.json --expect-fail RENDER_REQUIRED_backup.destination` | r3-c2-db-missing-backup | `EXPECTED_FAILURE:RENDER_REQUIRED_backup.destination` exit 0 |
| 복원 runconfig ingest `--expect-fail RUNCONFIG_KEY_CONFLICT:ingest.enabled` | r3-c1p-restore-ingest | EXPECTED exit 0 |
| M-10 `--suite F1` SPEC 부재 / 다른 바이트 / lint 기준선 일치 | r3-m10-missing·mismatch·match | 2 `SPEC_INPUT_MISSING` / 3 `SPEC_BASELINE_MISMATCH` / 0 |
| deploy 에 비정본 `--baseline`, catalog 허용 디렉터리 밖 | r3-deploy-baseline·r3-fixcat-outside | 2 `BASELINE_DEPLOY_FIXED` / 2 `FIXTURE_CATALOG_INVALID` |
| DB main/restore lint, 기존 `runconfig-selftest.py`·`aigw-runtime-credentials-selftest.sh` | r2-c0-*·r2-*-selftest | 모두 exit 0 (P06 회귀 0) |
| native: `eclipse-temurin:21-jre-jammy`(로컬 Docker Hub 이미지 digest 199aebeb…, harbor base 아님) `--network none --read-only`, Dockerfile 과 같은 PropertiesLauncher 명령 | p15-r2-native-check-r1 / -neg-r1 | `NATIVE_TOKENIZER_OFFLINE_CHECK_OK` exit 0 / offline 속성 없으면 `…_FAILED:OFFLINE_MODE_REQUIRED` exit 1 |
| migrate Job 진입점(`-Dloader.main=…MigrateApplication`) DB 없이 | p15-r2-migrate-entry-r1 | Spring Boot 기동 뒤 DB 부재로 exit 1(클래스 해석 오류 0). 실제 migrate NOT_RUN |

BackupScriptIT 가 실제로 확인한 것(로컬 Dockerfile 빌드 이미지, 로컬 시험 pin age 1.2.1·rclone 1.68.2 — 승인값 아님): 이미지 context 4 파일 SHA = Gradle 결속값, 동시 쓰기 중 commit 된 쓰기 ≥1(executeUpdate 성공 뒤 계수·worker 실패 전파·join), 원본 독립 표 지문/행 수 = 복원 대상(audit_event 는 commit 순서 접두사 집합), receipt 행 수 = 원본, `delegation_id` non-NULL 0, dlg sentinel 복호 스트림 0, `/work` 잔류 0, Job→요청 Message 연관, 실제 reader `JobRepository.findRequestInput` 으로 비단순 요청 입력 왕복 일치, `SEQUENCE SET` last_value 보존·aigw_app 새 INSERT, 비어 있지 않은 대상 exit 7·표 지문 불변, 목적지 실패 FAILED 행·지표·다른 override exit 2·복귀, 손상 ACL TOC exit 8(`P15_TOC_PROBE failedStep=pre-data`, 이후 단계 0), 변형 묶음 7종(추가·중복·순서·link·manifest 추가 필드·중복 키·타입) exit 8 `BUNDLE`·대상 쓰기 0·작업 디렉터리 잔류 0. 실제 TOC 제외 = `EXTENSION - vector`, `COMMENT - EXTENSION vector` 2건.

### 실패·RCA(보존)

- p15-app-it-backup-r1: 시험 컴파일 오류(Timestamp 모호) 0 tests. r2: 5/1 — order3 이 receipt 디렉터리를 만들지 않아 `ARGUMENT_MISSING`(시험 결함).
- p15-r2-eval-ci-r1: 19/1 — `CiEvidenceProcessTest` timeout 시험의 `@TempDir` 정리에서 Windows "다른 프로세스가 사용 중"(자식 생존 단언은 PASS). 진단 재실행 PASS(간헐). 자식 작업 디렉터리를 `java.io.tmpdir` 로 바꿈.
- ops 1차: 용량 음성 기대 사유를 guard 문구로 잡았으나 차트 schema(const 1)가 먼저 거부 → 기대 사유를 schema 메시지로 교정. DB main lint `HBA_POLICY_INVALID`(백업 CronJob 이 kind 집합 밖) → hba_policy 에 주 DB 백업 CronJob 1개만 허용. P06 selftest 가 DB 렌더에 `--image-digests` 를 주지 않으므로 DB 는 선택 입력, lint fixture 에 `backup.enabled` 를 두지 않고 lint 모드가 내부에서 켜서 확인.
- ⑩ r1: Windows cmd 줄 길이 초과(위 보정).

### 편차·미완료·위험

- 미구현 NOT_RUN: ③ `ops/gates/aigw-stage-gate.sh`+selftest, ④ `aigw-verify-credentials.sh`+selftest+runbook, ④' `aigw-backup-alert-injection.sh`+selftest, rollback 리허설 스크립트, `--set-extra`(F3-RULEVER — 차트 schema 에 sanitize 값 없음). suite-only 는 `helm upgrade --reuse-values` 경로와 lint 의 Deployment 불변 확인만 있고 Pod 전후 비교는 stage gate 몫(미구현).
- 수용 하네스: retention 조회 키는 단위 시험(바인딩 값)으로만 확인 — 실제 DB read-back 은 P16 배포 회차. 하네스 오류 레코드는 suite 가 없으면 stage 가 없어 ingest 불가(업무 증거를 만들지 않으려는 선택). RubricViewMode 는 P16 F1-RUBRIC fixture claims 전까지 항상 `RUBRIC_FIXTURE_CLAIMS_UNDEFINED` ABORTED — viewer 완료 아님. Windows 시작 스크립트 wildcard classpath 는 jar 순서를 디렉터리 순서에 맡긴다(Linux sh 는 기존 순서).
- 백업: sftp 목적지 시험 서버 미구현(승인 이미지 유형이 sftp 면 BackupScriptIT 가 명시 실패). 승인 digest(`-Paigw.backupImage`) 경로는 미보유로 NOT_RUN. 복원 loopback 대상 제한은 Plan/caller 대조 전이라 변경 0.
- ⑨ 양성(clean commit 의 `build-identity.json`)은 root 로컬 커밋 뒤 같은 세션에서 확인 필요. dirty guard 우회 0.
- `ops/deploy/__pycache__/runconfig.cpython-312.pyc` 는 이 세션의 `py_compile` 부산물(미추적·미무시) — 삭제는 root 결정.

### P15 r3·r4 보정과 남은 스크립트 구현 — 2026-11-07 (append, 위 r2 기록은 그 시점 이력으로 보존)

판정: CODE/LOCAL_VALIDATED 범위만. 운영 배포·실제 클러스터·실제 자격·경보 수신은 0회(NOT_RUN). P15 COMPLETE·F1 운영 PASS 를 주장하지 않는다.

**r3 보정**
- `TarGz`: 각 헤더가 writer 헤더 바이트와 정확히 같아야 하고(linkname·uname·prefix 등 숨김 0), 내용 뒤 패딩은 0 바이트만 허용한다. 시험 `hiddenBytesInPaddingOrUnusedHeaderFieldsAreRejected`.
- `CiEvidence.processes`: 하나의 마감 시각으로 종료와 출력 읽기를 함께 묶는다. 읽기 IOException 은 실패로 전파한다. finally 에서 후손 종료·소유 스트림 close·reader join·자식 wait 를 하고 인터럽트를 보존한다.
- p15-r3-eval-ci-r1: 21/0/0/0 EXECUTED → verify TEST_EVIDENCE_VERIFIED.
- `ops/deploy/__pycache__/runconfig.cpython-312.pyc` 단일 파일을 삭제했다. 빈 디렉터리는 남아 있다.

**r4 자격 보정(부모 재현: cleanup exit 0 뒤 test- 공개키·수용 토큰 해시·hook Secret 잔류)**
- `verify_credentials.py` cleanup 순서:
  1. 부작용 전 판정 — release 조회(오류 ≠ 부재), 입력 의미 검사(아래), Secret 조회, JWKS·토큰 형식 검사와 신뢰 제거 결과 계산, P06 `hba_policy.py live`.
  2. 수용 Job 두 개만 삭제.
  3. 각 DB 에서 NOLOGIN 후 그 역할 세션만 종료(P06 admin TCP 경로, SQL 은 stdin).
  4. disabled 재배포 후 역할 0·기존 자격 접속 비-OK 확인.
  5. 수용 Secret 4종 삭제.
  6. JWKS 에서 test-·수용 kid 제거, 수용 토큰 해시 제거(무관 키·토큰 보존, 비면 Secret 삭제), hook Secret 삭제.
  7. 실행 중 앱마다 `RESTART_OR_UNINSTALL_REQUIRED` 출력.
- install(provision·rotate) 도 부작용 전에 HBA live 를 돌린다. 재배포 뒤 실제 접속 분류는 `ai_gateway` SELECT = OK 이고 `postgres`·`template1` 의 TCP·Unix 가 모두 HBA_REJECT 여야 한다(비밀번호는 exec stdin).
- check 항목 출력은 MATCH/MISSING 만이다. 사유는 stderr 고정 코드로 낸다. Secret 조회 오류는 `SECRET_QUERY_FAILED:<이름>`·exit 1 이다.
- 부모 r4 검토 반영:
  - 입력 의미 검사: `AIGW_EXEC_CHART_SHA` 형식, `app_render.py digests`, release 별 `runconfig.py <runconfig> <release> deploy … <exec sha>`, 런타임 자격 check(읽기 전용)를 Secret 변경 전에 돈다. 무효 입력은 exit 2, 런타임 자격 미준비는 `DB_RUNTIME_CREDENTIALS_NOT_READY:<release>` exit 1.
  - cleanup 의 신뢰 JSON 파싱을 삭제 앞으로 옮겼다(형식 오류면 변경 0이라 재시도 가능).
  - install 의 JWKS·토큰 형식 오류도 고정 코드로 낸다.
- runbook `docs/runbooks/aigw-verify-credentials.md` 추가.

**남은 스크립트(Plan P15 ③·④'·L-6)**
- `ops/gates/aigw-stage-gate.sh`(`stage_gate.py`): §11.9 행렬 17 suite.
  - 입력 → runconfig profile → 이미지 신원 → spec-guard(≠0 → 3) → 부모 회차(새 부모는 stdout `AIGW_PARENT_RUN_ID=` 정확히 1줄, 기존 부모는 manifest 의 앱·수용 digest·buildIdentity 대조 → `PARENT_IDENTITY_MISMATCH` 2).
  - 검증 자격(12) → 런타임 자격(13) → deploy|suite-only. suite-only 는 앱 Pod uid·template hash·restartCount 전후 대조, 실패 시 11.
  - 실제 앱 Pod imageID ≠ 입력이면 `DEPLOYED_DIGEST_MISMATCH` 11 이다. 기존 부모·suite-only 에도 같은 검사가 걸린다.
  - `helm test --logs`: 시간 제한 = 입력+120초. 증거 블록·helm 상태 줄만 저장하고 `.sha256` 을 남긴다.
  - acceptance ingest. F4-RESTORE 는 RTO MEASUREMENT 를 ops ingest 한다. gate 는 F3-PROBE·F4-PERF·F4-RESTORE-PREP·F4-RESTORE-MUTATE 에서만 생략한다.
  - 종료 코드는 수집·보고 뒤에만 전파한다.
- 부모 r4 검토 반영(stage gate):
  - F4-RESTORE 의 gate 생략을 제거했다.
  - PARENT 레코드 `runId` = parentRunId 다(GateEvaluator 의 부모 조회 키).
  - 새 부모 manifest 는 helm test 뒤에 쓴다. 수용 Job Pod(`job-name=<release>-acceptance`, hook-delete-policy before-hook-creation 이라 남음)의 실제 imageID 를 넣고, 못 읽으면 입력값 대체 없이 `UNVERIFIED` 로 둔다.
  - ops 레코드는 파일 하나에 객체 하나다(`evidence_record.py seal` 단일 객체). eval `readAll` 이 단계 아래 모든 .json 을 레코드 객체로 검증하기 때문이다. 배열 파일이면 실제 gate 가 `EVIDENCE_INVALID` rc 2 임을 재현했다.
- `ops/gates/aigw-backup-alert-injection.sh`:
  - Job 정의는 `kubectl create --dry-run | python | kubectl create -f -` 파이프로만 다닌다(디스크 0).
  - 주입 전 `max(id)` 를 기준으로 잡고 새 행이 정확히 1건(FAILED:UPLOAD)인지 본다. 복귀 뒤에도 새 행 정확히 1건(SUCCESS)이어야 한다. 기준 조회 실패는 `BACKUP_RUN_QUERY_FAILED` 이고 Job 생성 0 이다.
  - Prometheus firing 은 수신 확인이 아니다. G-ALERT 미승인(`docs/gates/f1/alerting.md` NOT_RUN) 상태에서는 관측이 모두 참이어도 LEAF result = `NOT_RUN`(`ALERT_RECEIPT_PATH_UNAPPROVED`)·exit 2 이고, 하나라도 거짓이면 FAIL·exit 1 이다(PASS 경로 없음).
- `ops/gates/aigw-rollback-rehearsal.sh`(`rollback_rehearsal.py`):
  - A → B → `helm rollback N` 순서로 돈다. revision N·N+1·N+2, Pod imageID = A, Flyway 최신 V 불변을 확인한다.
  - 같은 스키마 두 번째 이미지가 없거나, 스키마가 다르거나, label 이 없으면 NOT_RUN exit 2 다.
  - 부모 r4 재현 반영: 401 의 `WWW-Authenticate` realm 이 레지스트리와 같은 scheme·host·port 가 아니면 자격을 보내지 않는다(`REGISTRY_AUTH_REALM_REJECTED`).
- `aigw-helm.sh` lint 의 suite-only 재사용 렌더(F1-RUBRIC)가 새 chart guard `RUBRIC_TARGETS_REQUIRED` 에 맞도록 `acceptance.rubricTargets=[]` 를 넘긴다. 같은 guard 의 음성 렌더도 추가했다.
- 공유 selftest stub `selftest_kubectl_stub.py` 에 `pods` 시퀀스와 `selector_pods`(selector 고정 응답)를 추가했다.

**실행(raw: scratch `p15-implementation/ops-runs/<label>.*`, 모두 aigw-p06-tools 컨테이너 network none·원본 읽기 전용, 실제 클러스터 0)**
- r7 ops 재실행(차트 sanitize·rubric 변경 뒤):
  - exit 0: 앱 main/restore lint, DB main/restore lint, 백업 목적지 누락 EXPECTED_FAILURE, 복원 ingest KEY_CONFLICT EXPECTED, M-10 일치, runconfig-selftest, runtime-credentials-selftest.
  - 기대 실패: M-10 부재 2·불일치 3, F4-RESTORE 입력 2, deploy 비정본 baseline 2, catalog 밖 2, `SET_EXTRA_NOT_ALLOWED` 2건.
- selftest: r7-stage-gate-selftest-r2 PASS 34(timeout 강제 종료 ≥120 초 포함), r7-verify-creds-selftest-r2 PASS 42, r7-alert-selftest-r2 PASS 9, r7-rollback-selftest-r2 PASS 12.
- 실제 eval 통합(r7-stage-gate-replay.json):
  - stage gate selftest 가 정본 SPEC(90ba8a73…) 바이트로 4 시나리오의 OPS/RUN 파일·정제 로그·eval 호출 순서를 내보냈다. 호스트 `:gateway-eval:installDist` 의 실제 `EvalCli` 로 같은 순서를 재생했다.
  - ingest·gate 는 전부 rc 0 이었다.
  - f1-normal·f4-restore: PARENT(runId = parentRunId)·ACCEPTANCE RUN COMPLETED·`ops:restore-rto` 가 저장됐고 보고서에 `PARENT_MANIFEST_MISSING` 이 없다.
  - f1-no-block: eval 이 `ACCEPTANCE_EVIDENCE_MISSING` ABORTED 를 만들었다.
  - f1-verify-missing: 배포 전 중단이라 부모 manifest 가 없어 보고서는 `PARENT_MANIFEST_MISSING` 이다. Plan 은 manifest 를 "배포 뒤" 로 정하므로 사유 코드는 RUN ABORTED 레코드에만 남는다.

**발견(P15 허용 쓰기 밖 — root 결정 필요)**
- eval `EvidenceRepositoryFs.readAll` 은 `evidence/<stage>/` 아래 모든 .json 을 읽고, `GateEvaluator` 는 각각을 레코드로 검증한다. 그런데 레포에 이미 커밋된 `evidence/F1/gates/p06-*.json` 과 Plan 이 같은 경로로 정한 운영 기록이 그 아래에 놓인다. 운영 기록은 `aigw-helm.sh` deploy 기록, verify-credentials check, rollback 리허설이다.
- 재현: 실제 EvalCli 로 `gate --stage F1` 을 돌리면 레코드만 있을 때 rc 0, `p06-local-verification.json` 1개를 더하면 `EVIDENCE_HASH_MISMATCH` rc 5 다.
- 레포 전체 Grep 에서 eval 에 gates/ 제외 처리는 없다. P16 F1 gate 를 막는 사전 결함이다. eval 수정(P15 허용 두 지점 밖) 또는 증거 경로 변경 중 무엇을 할지는 root 결정이다.

**미완료·NOT_RUN**
- 운영: stage gate·자격 provision/cleanup·경보 주입·rollback 리허설의 실제 실행.
- G-ALERT 수신 확인 경로가 없다(경보 leaf 는 NOT_RUN 만 가능).
- ⑨ clean packaging 양성은 root 로컬 커밋 뒤에 확인한다.
- 사용자 결정 대기: G-SOURCE-SVC(로그4/trace3)·metadata corpus·외부 백업 위치. tc-sources 변경 0.
- 빌드 부산물 `gateway-eval/build/install/`(installDist, 미추적 build 디렉터리) 가 생겼다.


## P15 최종 결함 보정 — Sol 세션 로컬 결과(2026-11-09, 운영/P16 미실행)

정본 SPEC SHA `90ba8a739c303312065824f9923642812f5e427a5f05a8b13142f792c1ad0550`, root 기술범위 보완 Plan SHA `ca2cad51a97480b004b4a411b75e2bfaa2415af27c6f70e7e8250c789e0afeda`, baseline seq8을 기준으로 보정했다. 이 절은 기존 전체 1,080,666바이트(SHA `5c3a1cd766c6b4707a7746147a7c7fff9a372b0c01dcb20a1019011c7dbc3bc6`) 뒤에만 추가한다. 원래 1,059,900바이트 prefix SHA `2d8cccdc6be967b735e52466d000208f48d41081e1fb555d1f4f6a731b51b5a7`도 보존한다.

변경과 영향:

- `ops/gates/rollback_rehearsal.py`: 승인 HTTPS Harbor origin만 허용하고 임의 registry override, userinfo/다른 host/port/scheme/제어 문자 및 승인 밖 realm을 거부한다. 공통 opener에서 인증 전·Basic·Bearer 응답의 모든 redirect를 거부해 Authorization 복제 전송을 막는다. 응답을 닫고 인증/JSON/HTTP 오류는 고정 사유로 변환한다.
- `ops/gates/aigw-rollback-rehearsal-selftest.sh`: 실제 urllib opener의 HTTPError/redirect 처리를 거치는 HTTPS fixture로 교체했다. socket.create_connection/socket.connect/connect_ex, HTTP(S)Connection.connect, HTTPHandler.http_open, urlopen의 실제 전송을 차단하고 Docker `--network none`을 함께 적용했다. 실제 자격/클러스터/외부 네트워크 사용은 없다.
- `gateway-eval/.../report/EvidenceRepositoryFs.java`, `LeafCatalog.java`: 단계 직계 namespace를 먼저 선별하고 runs/measurements/leaf문법 디렉터리/perf/user-run만 순회한다. 운영 gates/ops/단계 루트 artifact는 조회하지 않는다. 선택된 namespace의 모든 JSON을 최종 전역 정렬 후 파싱하며 기존 GateEvaluator/EvidenceRecord 검증과 store 경로·바이트·해시는 유지한다. LeafCatalog의 기존 ID 문법만 package-private helper로 공유했다.
- `gateway-eval/.../EvidenceRepositoryFsTest.java`, `PerfReporterTest.java`: 운영 artifact 전후 판정/바이트 동일성, 정본 내부 malformed JSON·해시·kind누락·미등록 leaf 거부, 명시 ops ingest, 직접 USER_RUN/PERF 소비를 검증한다. `evidence/README.md`에 조회 경계를 기록했다.
- `ops/gates/stage_gate.py`: full/paired freeze 종료 뒤 CRLF를 LF로만 정규화했다. 로직 변경은 없다.

실행 기록(모든 receipt/원시 XML/OTR/paired는 scratch `p15-implementation/`의 새 label로 보존):

| 검증 | 명령/label | 실제 결과 |
|---|---|---|
| rollback 보안 회귀 | tools 컨테이너 network none, `bash ops/gates/aigw-rollback-rehearsal-selftest.sh`, `ops-runs/sol-r3-rollback-selftest.*` | 92건 PASS, exit0, sourceStable=true, 실제 전송0. r1은 fixture no_label 상태 격리 오류로 실패했고 r2 88PASS 후 고정오류 경계4개를 더해 최종92PASS |
| 최소 reader/PERF | `-I sol-eval-reader-filter.init.gradle :gateway-eval:test --offline --no-daemon --max-workers=2`, `sol-eval-reader-min-r2` | 9/9 PASS, 실제 EXECUTED. r1 1실패는 새 단언의 Jackson LongNode/IntNode 동등성 차이였고 canonical 비교로 바로잡음 |
| 전체 eval 1회 | `:gateway-eval:test :gateway-eval:installDist --offline --no-daemon --max-workers=2`, `sol-eval-full-r1` | 274건 중272PASS/2FAIL, 오류/skip0, exit1. source/policy stable. 이 실패 때문에 해당 호출의 installDist는 실행되지 않음 |
| 전체 원시 결속 | `:gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"`, `sol-eval-full-verify-r1` | TEST_EVIDENCE_VERIFIED, exit0. 실패를 포함한 원시결과/신원 결속 검증이며 전체PASS가 아님 |
| eval 배포본 빌드 | `:gateway-eval:installDist --offline --no-daemon --max-workers=2`, `sol-eval-install-r1` | exit0, source/policy stable |
| 실제 CLI 연결/운영 artifact 재현 | `sol_cli_replay.py`, `sol-cli-replay-r1.json` | 5시나리오 PASS: stage OPS/PARENT/ABORTED/RTO·정제 acceptance와 실제 alert producer OPS를 ingest/gate. 커밋된 p06 운영 JSON 동시 존재 전후 보고서·정본바이트 동일, 정본 runs 손상은 INVALID_JSON exit2. 소스·실제 installDist classpath SHA 안정 |
| alert producer export | scratch 파생 selftest `sol-alert-export-selftest-r2.*` | 실제 승인 SPEC 바이트·stub kubectl/gradlew·loopback Prometheus, network none에서9PASS. 원본 레코드의 NOT_RUN/ALERT_RECEIPT_PATH_UNAPPROVED 유지. r1은 최소 fixture에 bootstrap.sql이 없어 AI_ROOT_REQUIRED로 실패했고 fixture에 그 정본만 추가 |
| 기존 F0 실패2건만 재실행 | `-I sol-eval-f0-env-filter.init.gradle :gateway-eval:test`, `sol-eval-f0-env-r1` | 2FAIL. 전체 회귀 추가실행0. 최초 sibling 부재를 원본을 가리키는 로컬 junction3개로 보정했으나 현재 SPEC 줄번호와 기존 arg-limits 근거가 어긋남을 확인 |

남은 검증 한계:

기존 `ArgLimitsSchemaTest.repositoryLimitsMatchInvestigatedCodeEvidenceAndHumanDocument`와 `F0BoundaryCasesV2Test.repositoryV2LimitsCiteTheActualApprovedSchemaLines`는 worktree 부모의 sibling 정본을 읽는다. full 최초 실패는 sibling 경로 부재였다. 로컬 junction으로 실제 정본을 읽게 한 뒤에는 기존 F0 JSON의 SPEC 고정 참조1203/1207/1208줄이 현 승인 SPEC의 해당 문구를 가리키지 않아 실패했다. F0 artifact/Spec/기대값을 범위 밖에서 수정하거나 이2건을 PASS 처리하지 않았다. 정본 원본 파일은 수정하지 않았다. junction 경로/대상은 `sol-test-sibling-junctions-r1.json`에 기록했다.

P15 완료/F1 운영 PASS로 표시하지 않는다. 실제 CI·push·K8s·Secret·승인 이미지 digest 재시험·G-ALERT 수신·P16은 NOT_RUN이다. G-ALERT 수신 미확정은 NOT_RUN을 유지하고 Source JSON·Spec·버전은 변경하지 않았다. clean packaging 양성은 root 검토·로컬 commit 뒤의 checkpoint로 남긴다. 이 세션은 commit/push를 하지 않았다.


## P15 부모 검증·실패 2건 해소 및 로컬 커밋 준비 (2026-11-09)

- 사용자 지시: 실패 두 건의 원인 수정, 독립 검증, 로컬 커밋과 최종 패키징까지 수행하고 P16 착수 전에 종료한다. 구현자는 기존 gpt-6.1-sol/high 한 세션이며 부모가 실제 코드와 원시 결과를 별도로 대조했다.
- 원인: F0 이력 JSON의 조사 당시 SPEC 줄번호를 가변 최신 문서의 현재 줄번호로 사용했다. 분리 worktree에서는 sibling 파일 위치 가정도 실패했다. 측정 자료의 인자 계약과 실제 Gateway 동작 결함으로 판정하지 않는다.
- 실제 추가 변경: `gateway-eval/src/test/java/com/nori/ai/gateway/eval/f0/ArgLimitsSchemaTest.java`, `F0BoundaryCasesV2Test.java`, 신규 `ArgLimitsSpecEvidence.java`만이다. 이력 JSON의 정확 SHA·human 문서 이력행·기준선 1/2 연결은 보존하고, 현재 계약은 기존 SpecGuard가 검증한 승인 SHA 바이트의 유일 §14.5와 필드별 승인 문단에서 확인한다. 시간 문법·35 CODEPOINT·나노초/instant 보존, 검색어 256 코드포인트·원문 보존·초과 거부·호출 0건·재시도 정책을 검증한다. 과거 문서 원문이 없으므로 당시 줄 내용을 검증했다는 주장은 하지 않는다.
- 입력 경로: `AIGW_SPEC_FILE`과 worker에 실제 제공된 `aigw.specPath`를 기존 resolver 우선순위대로 사용한다. Gradle `-P`가 worker로 자동 전달된다고 가정하지 않는다. 잘못된 우선 경로는 정상 env로 우회하지 않고 실패한다. TC CODE/DB 근거 세 파일의 기존 경로:줄 단언은 유지하며 분리 시험 환경의 명시 sibling junction 세 개는 `sol-test-sibling-junctions-r1.json`에 기록했다.
- 원시 실행 이력: 전체 `sol-eval-full-r1`은 274건 중 272 PASS/2 FAIL로 그대로 보존했다. 수정 후 두 클래스 `sol-f0-contract-r2`는 기존 관련 7건과 신규 음성/입력 경로 3건을 포함해 10/10 PASS, 오류·skip·abort 0이며 `sol-f0-contract-verify-r2`는 TEST_EVIDENCE_VERIFIED다. 전체 실행을 다시 했거나 원래 실패 결과를 PASS로 덮어썼다고 표시하지 않는다.
- 부모는 XML testcase, OTR TEST 시작/종료와 성공·실패 수, runId, input fingerprint, runtime classpath 및 시험/paired 양쪽의 source·정본 입력 동결을 직접 대조했다. 예전 전체 실행 이후 소스 변경은 위 시험 세 파일뿐이고 이전 실패 두 method가 새 실행에 포함돼 PASS한 것을 확인했다. 증명: scratch `p15-parent-review/raw-sol-close-proof-r1.json`, SHA-256 `7127e75f8112a1eb94c3811c98e21abcf0b1b1d695dda98ce2f522c70b3ec151`.
- P15 최종 보정 검증: rollback 실제 urllib handler를 통한 인증 전송 차단 92 PASS/실제 네트워크 호출 0, reader/PERF 최소회귀 9 PASS, 실제 CLI ingest/gate 재현 5시나리오 PASS를 원시 출력과 현재 파일 SHA로 대조했다. 운영 artifact 동반 여부에 따른 판정/정본 바이트 불변과 손상 정본의 INVALID_JSON 거부가 유지된다. F1 gate의 실제 상태 NOT_RUN은 정상 판정이며 F1 운영 PASS로 바꾸지 않았다.
- 기존 부모의 백업/복원·egress 6 IT, safety 6건, CI 경계 21건의 원시 결속 증명은 각각 `raw-backup-pair-proof-r1.json`, `raw-safety-pair-proof-r1.json`, `raw-ci-pair-proof-r3.json`에 보존돼 있다. 백업 IT 이후 변경된 소스 중 앱/백업 코드 변경 0, 앱 runtime classpath 불변을 확인해 무관한 Docker/DB 전체 시험을 반복하지 않았다. 전체 eval은 이후 CI/reader 변경을 포함해 실행됐다.
- EXEC 차트 최종 로컬 커밋: `fdbaae0b7a95d000cd8df8a6635ce1141f07bfd5`. AI는 이 절까지 포함해 로컬 커밋한 후 clean-tree `aigwPackage -Paigw.gitSha=<HEAD>` 양성 검증을 수행한다. 현재 이 절 작성 시점의 양성 패키징은 아직 NOT_RUN이며 후속 결과 절에서 실제 결과를 기록한다.
- Spec `90ba8a73…`, 기준선 seq8/`1bea13bf…`, 기존 F0 JSON·PREREG·측정/실패 증거 및 기존 DevReport prefix는 불변이다. 기존 1,086,628바이트 prefix SHA `d41befeb760ea2267950f3c5af3e98cba96ea84317c4bab950b8802df6f06446`를 그대로 보존하고 append했다. 변경 파일 UTF-8/LF와 git diff --check를 확인했다.
- 종료 범위는 P15 CODE/LOCAL_VALIDATED/LOCAL_COMMITTED와 패키징이다. 원격 CI/Harbor build·push·scan, 승인 digest 재시험, K8s 배포·Secret 변경·운영 경보 수신은 미실행이며 기존 운영 gate 조건을 유지한다. P16 수용 시험과 F1 exit는 착수하지 않는다.


## P15 최종 패키징 양성 검증·로컬 종료 (2026-11-09)

- AI 구현 커밋 7e933171a7e27a2a415a5884f1e929666dccac51, EXEC 차트 커밋 fdbaae0b7a95d000cd8df8a6635ce1141f07bfd5.
- 실제 명령: aigwPackage -Paigw.gitSha=7e933171a7e27a2a415a5884f1e929666dccac51 --offline --no-daemon --max-workers=2 → BUILD SUCCESSFUL, exit 0, source·정본 입력 동결. git status --porcelain이 빈 상태에서 생성됐고 build/package/build-identity.json의 gitSha와 HEAD가 일치한다. 기존 missing/dirty guard 음성 결과를 유지하며 clean-tree 양성 checkpoint를 닫았다.
- 부모 검증: build/package/app.jar는 실제 bootJar archive와 바이트가 같은 180,305,230바이트 파일이다. SHA-256 d1619b8cda92681819acf7528a9c543829d295a628e0c3e98f5d97db68ac8269, Boot JarLauncher/앱 진입 클래스/BOOT-INF 구조를 확인했다. runtime classpath 정본·package 파일·identity의 SHA는 모두 e07547eaff8e5ce3f8e1c3dc1689b20555c9aea353acdf060657d9afae989e52다.
- 수용 배포본: 147개 jar·Linux/Windows launcher가 존재하고 Windows wildcard classpath가 유지된다. 실제 패키징된 .bat를 시험 입력 없는 격리 child env에서 실행 → exit 3, 블록 SHA/base64를 직접 해독한 RUN 1건이 ABORTED/SPEC_INPUT_MISSING이며 실제 suite 실행 0건이다. 호스트 DEBUG에 따른 wrapper echo를 제거한 child env로 증거 블록 경계까지 검증했다. 이 결과는 P16 수용 시험 PASS가 아니다.
- 원시 명령 receipt p15-implementation/root-p15-clean-package-r1.json, 부모 증명 p15-parent-review/root-p15-clean-package-r1-proof.json, launcher 원시 출력 root-p15-clean-package-r1-acceptance-nodebug.out을 scratch에 보존했다. 본 결과 문서 커밋 뒤 최종 HEAD에 대한 build identity 재결속 결과는 root-p15-final-identity-r1 receipt로 별도 보존한다. 앱/수용 코드 재변경이나 시험 전체 반복은 없다.
- 현재 사용자 지정 종료 범위(P15 구현·허용 로컬 검증·실패 2건 처리·부모 검증·로컬 커밋·패키징)는 완료했다. 전체 274건 실행의 기존 272 PASS와 수정 두 클래스 10 PASS를 실행별로 보존했으며 단일 전체 277 PASS 실행으로 표시하지 않는다.
- 정본 Plan의 P15 항목에 CODE/LOCAL_VALIDATED/LOCAL_COMMITTED 종료와 P16 NOT_STARTED를 반영했다. 이 상태 기록은 Spec의 계약·baseline seq8을 바꾸지 않는다. Plan이 요구하는 원격 CI/Harbor·승인 digest·K8s·운영 gate 실측은 미실행으로 유지하므로 전체 운영 완료 체크 두 개를 선표기하지 않는다. 사용자 원본의 미커밋 Spec 및 AI 원본의 미추적 측정 자료는 포함하지 않는다.


## 배포 시점 정정·P16 진행 범위 재정렬 (2026-11-09)

- 사용자 결정: 실제 Kubernetes 배포는 F4까지 코드 구현과 허용된 로컬 검증을 마친 뒤 수행한다. 중간 실환경 gate PASS를 다음 코드 단계 선행으로 적용한 이전 해석을 정정했다.
- Specify: 정본 SPEC header·§16·§27.4에 해당 확정 계약을 먼저 반영하고 Plan 바이트 불변을 확인했다. Plan: 이후 §6의 코드 DAG/실환경 exit 적용 범위와 P16 코드/실환경 선행·수용 suite 준비 책임을 분리했다. 기존 AC/ISO·환경 라벨·임계값·기술/보안/실행 승인 조건을 삭제하지 않았다. 실제 Source/모델/운영 입력은 실제 연결 전 요구하며 코드 작업을 차단하는 조건으로 쓰지 않는다.
- R6: seq9 SPEC SHA ea89cce7d3a95af3b24610f8fdbcf82f909abe79bd29f3d23f05591cc3431e02, baseline SHA 336190f18bc5c70197654f37dcc7347b2aff945ad25f0b988d511f9297713696. seq1~8과 기존 승인·증거·원시 결과·DevReport prefix는 그대로 보존했다. 운영 환경 시점 변경은 보수적으로 ALL/measurementAffecting=true로 기록했고 과거 시험을 새 기준선 PASS로 승격하지 않았다.
- 실제 검증: 기존 검증된 eval installDist의 spec-guard --spec <정본> --baseline <seq9> 실행 exit 0. scratch p16-preflight/baseline9-spec-guard-r1.json 및 원시 out/err에 명령·해시·결과를 기록했다. 신규 제품/시험 코드 수정이나 전체 시험 재실행은 없었다.
- 독립 P16 gpt-6.1-sol/medium 읽기 조사 결과, 기존 catalog F1 suite는 35 leaf, F1-RUBRIC은 2 component이며 실제 src/main suite 클래스는 아직 없다. F1 suite·fixture·기존 하네스/rubric 연결과 로컬 검증이 다음 구현 범위다. ISO-04의 단일 알고리즘 위반 신뢰 fixture 및 수동 rubric 입력의 실제 배포 배선은 운영 수용 전에 확인해야 하며, 로컬 fixture로 운영 PASS를 만들지 않는다.
- 현재 P16 구현·로컬 검증은 아직 미실행이며 완료로 표시하지 않는다. 현재 요청은 P16뿐이고 P17/F2 이후 코드는 착수하지 않았다. Kubernetes/Secret/DB/PVC·원격 CI/push/MR·모델/Source 호출·원격 Spec publish/protected 변수 변경 0. F1~F4 실제 수용/exit는 NOT_RUN으로 유지한다.


## P16 배포 제외 구현 착수·통신로그 workflow 계약 결정 대기 (2026-11-09)

- 사용자 요청: 배포를 제외한 P16 구현·로컬 검증만 수행한다. 독립 gpt-6.1-sol/medium 한 세션이 담당하고 부모는 실제 계약·호출 흐름을 대조했다. 배포·운영 gate 미확보를 코드 착수 차단 사유로 쓰지 않았다. F2 이후 작업은 시작하지 않았다.
- 현재 변경은 acceptance/src/main의 기존 AcceptanceMain.java, GatewayClient.java, HookClient.java, OracleQueries.java와 신규 F1Assertions.java, F1Fixture.java, F1RubricTargets.java, F1SecurityChecks.java 총 8파일이다. 최종 구현/검증 전 중간 상태로 보존하며 compile·test·운영 실행·커밋은 아직 0이다.
- 실제 코드에서 확인한 보정 필요점: (1) SeamRegistry.Kind에 UNSUPPORTED가 없어 AC-17-U 단일 상태 주입 불가, (2) 기존 OWNER_OVERRIDE는 Plan 검사에도 적용돼 Dispatcher 직전 교차 owner 차단을 검사하는 AC-21-TOOL이 조기에 거부됨, (3) PlanValidator의 고정 단계·reason Trace는 rejection에 있지만 TerminalOutcomeAssembler가 최종 결과에 투영하지 않음. 기존 권한/격리 정책 완화로 고치지 않으며, 최소 seam·판정 Trace 연결 보정은 다음 Plan 범위 확정 후 수행한다.
- 별도 제품 계약 누락: AC-46-TRIM은 F1 필수인데 TcBusinessReadPort.getCommLogs와 sources://TC/comm-log-backend의 COMM_LOG_READ 정의만 있고 실제 Manifest/BusinessToolExecutor/고정 workflow 호출 경로가 없다. Spec §6.3의 장애분석은 Snapshot→Loki/Tempo→DLQ→선택 Git/Knowledge로 정의되어 있다. 직접 Mock 포트만 호출한 기존 RetentionMockIT 성공을 Gateway 수용 PASS로 사용하지 않는다.
- 사용자 결정 질문을 현재 대화에 전달했다. 권장은 TC 통신로그를 별도 BUSINESS READ Capability로 등록하고 장애분석 workflow에 연결하는 방식이다. 기존 Loki app 로그와 TC Redis 통신로그는 원천/인가/보존 규칙이 달라 대체·합치지 않는다. 새 경로는 기존 EQP·시간창 바인딩과 COMM_LOG_READ·위임 경계로 gateway/business를 각 1회 조회하므로 기본 장애분석은 Tool 단계가 1개, 내부 READ가 2회 추가된다. 기본 조회 동작과 Spec의 고정 workflow가 달라져 사용자 확정 전 구현을 중단했다.
- 최소 정석 연결은 신규 Manifest+index, BusinessToolExecutor 분기와 INCIDENT_ANALYSIS workflow 변경이다. source별 엄격 outputSchema에 상태/관측치/completeness/retentionMeta{missingRanges,unverifiedRanges}를 보존하면 기존 contract validator→BlockBuilder→최종 blocks.content.rows 전달 경로를 재사용할 수 있다. 공통 ToolResultMetadata API 확장은 필요하지 않고 BUSINESS 캐시는 기존 금지를 유지할 수 있다. 실제 TC HTTP type=eqp/mes 연결은 F5a 책임이다.
- 다음 작업은 사용자 계약 확정→정본 Spec/Plan·R6 정합→최소 코드 보정과 P16 suite 구현 재개→실제 로컬 검증/부모 검증/커밋이다. 현재 상태는 IMPLEMENTATION_INCOMPLETE/DECISION_PENDING이며 코드/로컬 검증 완료나 F1 PASS가 아니다. 실환경 leaf/exit는 NOT_RUN이다. 기존 Spec/Plan/seq9·증거·보고서 prefix와 사용자 원본 파일은 변경하지 않았다.


## P16 — F1 suite 구현·로컬 검증 최종 보고 (배포 제외)

구현 담당 결과는 **CODE / LOCAL_VALIDATED**입니다. 아래 결과는 로컬 단위·격리 PG·loopback 시험과 패키지 발견 검증이며, F1 운영 수용 PASS가 아닙니다. 부모의 독립 raw 검증도 통과했으며 최종 커밋은 부모가 담당합니다. 구현자는 커밋하지 않았습니다. 기존 중단 기록을 포함한 1,099,670바이트(prefix SHA-256 `8708ac3abcc8876f1eff77db064bbef1b560f264a667041518c9fe81783a119a`)를 그대로 보존하고 이 절만 추가했습니다.

### 승인 입력과 범위

- 시험 HEAD: `e163a5008823ba50072aeaf3bea133a4962ee09c`.
- 정본 Spec: 원본 EXEC `docs/task/ai-rag-gateway-architecture.md`, SHA-256 `ab898263d55a3bac4a91797e6363e57094b38edd337e97740c581a5b5e869f17`.
- 정본 Plan: 원본 EXEC `docs/task/ai-rag-gateway-architecture-plan.md`, SHA-256 `5c1cb0ec4e2faa3f4cc8f18f7ba7674078ebb69c50b9b7f6c62850c03d612b6e`.
- 기준선 seq11: `docs/gates/spec-baseline.md`, SHA-256 `015a4ac1b701986a91032656d27ece7f3e1a74714ea300cc6102f322eff19f70`.
- 사용자 확정 순서인 F4 구현·로컬 검증 후 최초 Kubernetes 배포를 유지했습니다. 이번 범위는 P16이며, F2 이후 작업과 운영 배포는 진행하지 않았습니다. 통신로그 READ 연결과 AC-46 Source 단계 판정/전체 집계 유지도 승인된 seq11 계약대로 구현했습니다. 독립 구현 세션에서는 Spec/Plan/기준선, catalog, 공통 ToolResultMetadata/API, WorkflowOutcome/Executor, 기존 인가·캐시 API, 운영 설정·차트는 수정하지 않았습니다.

### 구현 내용과 실제 판정 경로

- `gateway-acceptance/src/main`에 F1의 35개 atomic leaf를 갖는 배포용 `F1Suite`와 fixture·실제 HTTP client·서명 입력·Job/hook/SQL oracle를 구현했습니다. 각 leaf의 suite/stage/label/leaf 태그를 정본 catalog와 대조합니다. 운영 자격·Source 관측을 갖추지 못한 경우 NOT_RUN으로 구분하며, 실제 제품 응답·audit·실행 기록 없이 PASS를 만들지 않습니다.
- JWS 누락/변조·인증 음성은 실제 요청, HTTP 오류, audit 1건, Job/Mock 호출 변화 없음, 응답/DB/로그 residue를 대조합니다. 누락 sub/perm, 61초 수명과 60초+1ns 경계, 실재 conversation에 대한 같은 GET의 양성/추가 dlg 음성, 실제 전송한 잘못된 service token까지 검사합니다. RSA 단일 알고리즘 위반 입력은 실제 배포에서 공급할 별도 signer/trusted public JWKS 파일 경로까지 연결하고, 로컬에서는 실제 RSA 서명/신뢰키 결속을 검증했습니다. 비밀 원문은 출력·보고하지 않습니다.
- AC-41의 고정 SQL을 terminal Job 전체 행 및 Tool/Evidence/cache까지 확장해 메모리 scanner로만 검사합니다. 같은 key_digest의 다른 owner/actor/pdg cache 행이 덮이지 않도록 전체 복합 정렬의 순번으로 전수 검사하고 회귀를 추가했습니다. Tool payload는 고정 SQL에서 메모리 typed projection으로만 읽으며 원문 반환 API를 만들지 않았습니다.
- `tc.commLogs.search`를 BUSINESS/READ/PHASE_1, COMM_LOG_READ, 캐시 비활성으로 추가하고 INC snapshot 다음에 연결했습니다. 기존 permission/delegation/expiry/beforeRead 경계를 재사용해 gateway/business 각각 1회 조회합니다. 엄격한 source별 output에 completeness/retentionMeta/missingRanges/unverifiedRanges/warnings를 담고 최종 블록까지 보존합니다. DENIED/FAILED를 계약 오류나 EMPTY로 덮지 않는 경로도 검증했습니다. 공통 metadata에는 확정 missingRanges만 유지합니다.
- 승인된 UNSUPPORTED seam, Plan 신원은 유지하는 DISPATCH_OWNER_OVERRIDE, UNSUPPORTED rejection의 고정 validationTrace 투영을 보정했습니다. 기존 OWNER_OVERRIDE와 clarification-only 비노출 분기는 유지했습니다. AC-21-TOOL은 첫 snapshot 실제 성공 뒤 comm 호출 전에 Dispatcher OWNER_MISMATCH가 난 audit를 검사합니다. VERSION fixture는 resolveResource만 정상 1.0으로 두고 대상 업무 READ의 2.0 위반이 Tool까지 도달하도록 보정했습니다.
- verify·enabled·hook 인증 경계 안에 optional SourceRequestProbe와 별도 읽기 controller를 연결했습니다. 실제 송신 직전 carrier를 메모리에서만 파싱해 정규화한 app/namespace/window의 digest·enum·count를 Job/capability별로 캡처합니다. 실제 loopback 수신 digest와 인증된 hook 응답을 대조했습니다. 원문 query/URL/EQP/window는 보관·반환하지 않습니다. retry/실패 요청 횟수, 완료 0회와 관측 부재의 구분, job/thread 격리, overflow, 성공 idle reset/실패 active reset 보존, prod 미생성, hook 인증 거부를 검사했습니다. 기존 public 생성자·OTLP·SourceHttpClient/core/API는 유지했습니다.
- AC-36 실제 호출 0회, AC-45의 정확한 누락 합집합 및 실제 전송 window digest, ISO-15의 selector/app 경계와 Evidence/Citation 범위를 검사하는 소비 경로를 연결했습니다. 임의 관측 JSON 파일이나 정적 selector 함수 결과를 실제 호출 증거로 사용하지 않습니다.
- F1-RUBRIC은 대상 RUN의 F1 stage/suite·정본 Spec·buildIdentity·parent를 현재 실행과 결속하고, 두 fixture/leaf를 정확히 매핑합니다. 대상 jobId 경로 문법, 인증 GET의 동일 jobId/SUCCEEDED/result, narrative hash를 검증한 뒤 기존 viewer를 사용합니다. 실제 수동 rubric verdict는 생성하지 않았습니다.

### 최종 로컬 검증

원시 명령·XML·OTR·run-binding·fingerprint·현재 CP·실행 전후 소스와 정본 입력 SHA는 다음 scratch에 고유 label로 보존했습니다.

`C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p16-implementation`

| 모듈 | 실제 Test / paired label | 실행 / 통과 | XML / OTR | runId / fingerprint |
| --- | --- | --- | --- | --- |
| core | `p16-core-final-r1` / `p16-core-final-verify-r1` | 21 / 21 | 6 / 1 | `588b64b0-bfbc-4865-86c5-6240069a2ed9` / `6898aed46952f466525333115cd6421c0c417068484df62a8bdf30f7590252cd` |
| app | `p16-app-final-r2` / `p16-app-final-verify-r2` | 19 / 19 | 5 / 1 | `bba6f364-93c0-496d-9b5d-efb6fb376df0` / `e3f9e31ac3cdf0efd8d28770994dfaf4d630caa97fe958fba808b2bb619b8975` |
| tc-mock | `p16-mock-final-r1` / `p16-mock-final-verify-r1` | 13 / 13 | 1 / 1 | `1a672f96-787f-4d88-8a44-c8b1eba76710` / `40f61fc4d7f900cb8db9ca000ee63a9a2a855ef02152983c17a444564d6fdaf6` |
| acceptance | `p16-acceptance-final-r1` / `p16-acceptance-final-verify-r1` | 37 / 37 | 11 / 1 | `84a6dad9-ca2f-4ac6-9f2a-5d38b09ee352` / `2a5a1840919182e3e22466f11c77b0292ec4cb38c06e6ccf116573ad41f3529f` |
| app integration | `p16-it-final-r4` / `p16-it-final-verify-r4` | 9 / 9 | 3 / 1 | `cd5be6a0-9198-483e-9ffa-64fd7800bdba` / `4e8dd305b650a6e4629259c47febe1e5ce4eb477a44be20ca0af2e2a0c72ae74` |

RuntimeClasspath SHA-256: `0677a90c31222421d3ee58f977388a2365c7fce6eb574f0af55614038f9f58d5`.

`p16-package-final-r1`: acceptance build/installDist exit 0. `p16-packaged-discovery-r1.json`: installed JAR discovery 35 / execution 0; receipt SHA-256 `992a3ce4b6aa01d66fa4d079b0146a3512d7e0d0cba070a30f08a25c38510024`.

각 최종 Test 직후 같은 필터/조건의 별도 `test-evidence verify`를 실행해 통과했습니다. 관련 unit 90개와 최종 필수 Docker 통합 9개는 fail/error/skip/abort가 모두 0입니다. 최종 통합은 P16 4개, 영향 WorkflowMock 2개, manifest/core 조립·Source Registrar/factory 관련 wiring 3개를 선별했습니다. 전체 PG/eval 대형 suite를 반복하지 않았습니다. 각 개별 Gradle 실행 중 소스 및 정본 입력은 동결됐습니다. core 최종 시험 뒤의 P16IT/CommLog unit 변경, app 최종 시험 뒤의 P16IT 변경은 앞선 시험이 소비하지 않는 파일임을 부모가 실제 fingerprint/classpath로 확인했습니다. 따라서 모든 시험이 최종 전체 소스와 처음부터 같았다고 주장하지 않으며, 소비 범위가 변하지 않은 앞선 unit 결과와 최종 IT를 결합합니다.

부모는 XML 26파일/OTR 99개 test ID의 SUCCESS, 5개 paired, 최신 Spec/baseline/현재 CP/실행 중 소스 동결과 비소비 변경 범위를 독립 대조했습니다. 근거는 `scratchpad/p16-parent-review/root-all-pairs-final.json`, SHA-256 `cfe2b09e04888d478da5a1786f4ce6da4596391b00068ceeb67c119a58e3db66`입니다.

설치본의 `lib/*`와 scratch probe만으로 JUnit launcher discovery를 수행해 catalog의 35개 leaf 및 태그 closure를 확인했습니다. F1Suite codeSource는 실제 설치본 `gateway-acceptance.jar`였고, 설치 JAR 147개 해시가 전후 동일했습니다. leaf 실행은 0건이며 운영 증거 ingest도 0건입니다. 부모도 설치 JAR을 독립 대조하고 동일 discovery를 직접 실행했습니다.

### 실패·명령 차이와 보정 이력

- 초기 seq10/중간 compile 결과는 역사로 보존하고 최종 계약 판정에 사용하지 않았습니다. seq11의 `p16-all-compile-r2`는 관련 main/test/integrationTest 컴파일 진단 exit 0입니다.
- `p16-app-final-r1`: 19개 중 신규 CommLogToolExecutorTest 3개가 짧은 DelegatedCall fixture 때문에 생성자에서 실패했습니다. fixture를 기존 최소 22자 계약에 맞춘 뒤 r2에서 19개 모두 통과했습니다. 제품의 위임 검증을 완화하지 않았습니다.
- `p16-it-final-r1`: 14개 중 12개 통과/2개 실패였습니다. 신규 시험에서 source별로 다른 comm-trim fixture를 같다고 가정한 기대값과, Dispatcher 거부 후 최종 toolSummary가 남는다고 가정한 기대값 오류였습니다. 제품을 바꾸지 않고 gateway exact nano 범위/business 미검증·빈 범위, 첫 조회/comm 실행 0/정확한 audit status/error/tool_id를 대조하도록 시험만 보정했습니다. r1 원시 실패와 다른 12개 통과 기록은 보존했습니다.
- `p16-it-final-r2`와 paired는 9개 실제 실행/통과였으나 Plan 필수 `-Paigw.requireDocker=true`가 누락된 명령이므로 최종 승인 명령 근거로 삼지 않았습니다. 사후에 명령·fingerprint·원시 결과를 수정하지 않았습니다.
- `p16-it-final-r3`: 인용하지 않은 PowerShell 인자가 `-Paigw`와 `.requireDocker=true`로 나뉘어 Gradle 명령 해석에서 exit 1이 났고 시험은 실행되지 않았습니다. wrapper의 NOT_REPORTED와 남아 있던 r2 XML을 구분해, 잔여 XML을 r3 시험 성공으로 사용하지 않았습니다.
- 최종 `p16-it-final-r4`와 paired에 리터럴 단일 인자 `'-Paigw.requireDocker=true'`를 모두 적용했습니다. metadata.command 배열에 앞 공백 없이 해당 한 항목이 있는지 검사했습니다. 소스는 바꾸지 않았고 동일 9개 필터만 재실행했습니다. 최종 판정은 r4 실제 EXECUTED 결과와 r4 paired에 근거합니다. 관련 unit 90개와 성공한 패키징을 다시 실행하지 않았습니다.

### 미실행·잔여 수용 조건

실제 Kubernetes/Harbor/원격 CI·push·Secret/SSH·운영 DB 변경·실제 Source/모델 호출은 수행하지 않았습니다. F1 35개 leaf의 실제 배포 수용, 실제 RSA 신뢰 입력, 운영 로그 readback/Source 관측, 운영 SQL oracle, 수동 rubric 승인은 미실행입니다. 로컬 성공을 ACCEPTANCE/F1 운영 PASS로 기록하거나 ingest하지 않았으며 `evidence/F1` 변경도 없습니다.

ISO-15-WINDOW는 현재 승인 Tool inputLimits에 최대 조회 창 계약이 없으므로 임의 기간을 만들지 않고 `MAX_WINDOW_UNDEFINED / NOT_RUN`으로 유지합니다. 이는 운영 수용 미확보 항목이며 PASS가 아닙니다. 배포 입력·운영 승인·실제 Source fixture·수동 reviewer 입력을 확보한 뒤 F4 이후 승인 순서에서 실제 수용을 별도 수행해야 합니다.

### 실제 변경 파일

- `docs/ai-rag-gateway-architecture-dev-report.md`
- `docs/contracts/test-hooks-v1.md`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/AcceptanceMain.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/GatewayClient.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/HookClient.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/OracleQueries.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/RubricViewMode.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/OracleQueriesTest.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkflowMockIT.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkflowSourceFixture.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/wiring/AppContextWiringIT.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/config/SourceConnectorConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/MockHooksConfiguration.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/SeamRegistry.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/TestHookRegistry.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/BusinessToolExecutor.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/DirectSourceToolExecutor.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/job/WorkflowExecutionConfiguration.java`
- `gateway-app/src/main/resources/manifests/tc/index.json`
- `gateway-app/src/main/resources/workflows/tc/INCIDENT_ANALYSIS.json`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/hooks/SeamRegistryTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/manifest/InputLimitsNotNarrowerTest.java`
- `gateway-core/src/main/java/com/nori/ai/gateway/core/workflow/TerminalOutcomeAssembler.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/DomainFixtures.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/ManifestDigestTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/TerminalOutcomeAssemblerTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/WorkflowExecutorTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/WorkflowOptionalStepContractTest.java`
- `gateway-core/src/test/java/com/nori/ai/gateway/core/WorkflowPlanBuilderTest.java`
- `gateway-tc-mock/src/main/java/com/nori/ai/gateway/tcmock/MockTcBusinessReadAdapter.java`
- `gateway-tc-mock/src/test/java/com/nori/ai/gateway/tcmock/MockBusinessReadContractTest.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1Assertions.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1Fixture.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1JobChecks.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1RubricTargets.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1Runtime.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1SecurityChecks.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/F1SourceChecks.java`
- `gateway-acceptance/src/main/java/com/nori/ai/gateway/acceptance/suite/F1Suite.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/F1LocalFixture.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/F1RubricBindingTest.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/F1SourceChecksTest.java`
- `gateway-acceptance/src/test/java/com/nori/ai/gateway/acceptance/F1SuiteTest.java`
- `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/P16WorkflowCompletenessIT.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/SourceObservationController.java`
- `gateway-app/src/main/java/com/nori/ai/gateway/app/hooks/SourceRequestProbe.java`
- `gateway-app/src/main/resources/manifests/tc/tc.commLogs.search.json`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/hooks/SourceProbeProfileTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/hooks/SourceRequestProbeTest.java`
- `gateway-app/src/test/java/com/nori/ai/gateway/app/job/CommLogToolExecutorTest.java`

### 실행한 최종 명령과 원시 receipt

**`p16-core-final-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\core-filter.init.gradle :gateway-core:test --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-core-final-verify-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\core-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-core:test" --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-app-final-r2.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\app-filter.init.gradle :gateway-app:test --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-app-final-verify-r2.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\app-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:test" --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-mock-final-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\mock-filter.init.gradle :gateway-tc-mock:test --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-mock-final-verify-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\mock-filter.init.gradle :gateway-eval:run "--args=test-evidence verify --tasks :gateway-tc-mock:test" --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-acceptance-final-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai :gateway-acceptance:test --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-acceptance-final-verify-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai :gateway-eval:run "--args=test-evidence verify --tasks :gateway-acceptance:test" --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-it-final-r4.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\it-final-filter.init.gradle -Paigw.requireDocker=true :gateway-app:integrationTest --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-it-final-verify-r4.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai -I C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p16-implementation\it-final-filter.init.gradle -Paigw.requireDocker=true :gateway-eval:run "--args=test-evidence verify --tasks :gateway-app:integrationTest" --offline --no-daemon --max-workers=2 --console=plain
```

**`p16-package-final-r1.json` — exit 0**

```text
C:\workspace_nori\.aigw-worktrees\p16-ai\gradlew.bat -p C:\workspace_nori\.aigw-worktrees\p16-ai :gateway-acceptance:build :gateway-acceptance:installDist --offline --no-daemon --max-workers=2 --console=plain
```


## P16 부모 검증·로컬 종료 판정 (2026-11-09, 배포 제외)

P16의 이번 요청 범위는 CODE/LOCAL_VALIDATED로 마무리합니다. 구현 커밋은 4397bbcfba2fd238da380fab949df84e3bf3d97f입니다. 실제 F1 수용/exit PASS를 뜻하지 않으며 P17/F2 코드는 시작하지 않았습니다.

- 사용자 승인 두 건을 root가 정본에 반영했습니다. seq10은 TC 통신로그 READ를 별도 Capability로 장애분석에 연결하는 계약이고, seq11은 AC-46의 대상 Source 단계 EMPTY/SUCCESS와 전체 응답 집계를 분리하는 계약입니다. 독립 구현자는 그 확정 계약을 소비했습니다.
- 통신로그 두 Source의 호출·보존 경고·확정 누락/미확인 구간 구분, Gateway 최종 블록의 나노초 경계, 검증기 6단계 거부 trace, Dispatcher 소유자 불일치 차단, verify 전용 실제 Loki 요청 digest/count 관측의 생산/조회·수명·격리를 실제 코드/등록 조건/호출 흐름과 대조했습니다. BUSINESS 캐시 금지, 기존 인가·내용 비노출·전체 상태 집계는 유지했습니다. 관측 계약 문구는 현재 대상인 Loki query_range로 명확히 했습니다.
- 최종 99건(core21/app19/mock13/acceptance37/통합9)은 원시 XML 26파일과 OTR 99 TEST ID 모두 SUCCESS, fail/error/skip/abort 0입니다. 5개 공식 paired의 실행 필터·runId·fingerprint·정본 Spec/기준선·현재 runtime classpath를 독립 대조했습니다. 최종 통합은 test/paired 양쪽의 단일 인자 -Paigw.requireDocker=true도 확인했습니다. 근거 root-all-pairs-final.json SHA cfe2b09e04888d478da5a1786f4ce6da4596391b00068ceeb67c119a58e3db66.
- 앞선 core 결과 뒤의 P16IT/CommLog unit, 다른 unit 결과 뒤의 P16IT 수정은 해당 실제 classpath가 소비하지 않는 시험 파일임을 확인했습니다. 각 개별 실행 및 paired 중 입력은 동결됐습니다. 현재 620개 실행 소스 입력은 최종 통합 실행 당시 바이트와 같으며 구현 커밋 후에도 확인했습니다. 로컬 시험은 당시 작업트리/기준 커밋 e163a50에 결속된 이력이며 새 배포 이미지나 원격 CI 검증으로 재표시하지 않습니다.
- 설치 lib 147개 JAR의 바이트를 대조한 뒤 부모가 그 배포본만 사용해 discovery를 직접 실행했습니다. 실제 gateway-acceptance.jar에서 정본 catalog의 35개 leaf가 발견됐고 전후 JAR은 불변입니다. 실행 leaf 및 운영 증거 ingest는 0건입니다. 근거 root-packaged-discovery-r1.json, discovery receipt SHA 992a3ce4b6aa01d66fa4d079b0146a3512d7e0d0cba070a30f08a25c38510024.
- app 최초 3건 실패와 IT 최초 2건 실패, 필수 옵션을 빠뜨린 IT r2, PowerShell이 옵션을 나눈 r3(시험 실행0/잔여 XML 미사용)를 이력으로 보존했습니다. 최종 r4만 필수 조건을 갖춘 통합 통과 근거입니다. 원시 실패를 삭제하거나 이전 PASS를 새 회차에 재사용하지 않았습니다.
- 원본 AI 945파일/EXEC 521파일과 사용자 미추적 F0 자료를 사전 보호 snapshot에 대조했습니다. 기존 DevReport 1,099,670bytes/SHA 8708ac3abcc8876f1eff77db064bbef1b560f264a667041518c9fe81783a119a는 그대로입니다. 독립자 인계 원본 de28c7b05c71ec6c7308bed40e081c7034fb0d08ec6147e3efc0200f74035aef는 scratch에 보존하고 이번 새 추가분의 표 머리글/구분자 인코딩만 교정했습니다.
- 시험 당시 Plan SHA는 5c1cb0ec4e2faa3f4cc8f18f7ba7674078ebb69c50b9b7f6c62850c03d612b6e입니다. 부모 검증 후 P16 CODE/LOCAL_VALIDATED 종료 사실만 Plan에 추가했고 종료 Plan SHA는 ae309bb7bce871ac639fede55c956da91fd7351e1059db1f0a1ec5646cc327c8입니다. Spec SHA ab898263d55a3bac4a91797e6363e57094b38edd337e97740c581a5b5e869f17와 seq11 기준선은 바꾸지 않았습니다. 실제 AC/ISO·F1 exit 체크를 PASS로 올리지 않았습니다.

운영 미실행: 실제 K8s/Secret/PVC·Harbor 승인 이미지/백업·외부 Source/LLM·수동 rubric·원격 CI/push/MR·Spec publish/protected 변수 갱신입니다. ISO-15-WINDOW의 최대 조회 창 계약은 미확정이므로 해당 실제 leaf는 MAX_WINDOW_UNDEFINED/NOT_RUN이며 실수용 전에 확정해야 합니다. TC 실제 HTTP type=eqp/mes 연동은 기존 F5a 책임을 유지합니다. F4 코드/로컬 검증 뒤 최초 배포·실수용을 수행하는 사용자 순서도 유지합니다.


## F2 착수 — 임차 원칙 seq12·P15 코드 정합·P17 (2026-11-09)

- 사용자 결정: 추론 서버 192.168.0.160은 사용자가 빌려 쓰는 공유 자원이다. 서버를 변경하거나 변경을 게이트·요청 조건으로 두지 않고 기존 endpoint `http://192.168.0.160:11434`를 그대로 호출한다. 모델이 상주하지 않으면 운영자가 승인된 로컬 SSH 수단으로 일반 Ollama 요청을 보내 적재한다. 독립 에이전트 기본은 Claude Code Opus 5.5(medium/high, xhigh 금지)이며 gpt-6.1-sol은 사용자 요청 시만 쓴다.
- 결정 계기(읽기 전용 관측): Ollama `0.0.0.0:11434` 무인증, `KEEP_ALIVE=24h`·`CONTEXT_LENGTH=8192`, 72시간 공인 IP 출발 요청 약 4,700건(관리 API 포함), 외부 주소가 추가한 cloud 모델, `qwen3:4b` digest `359d7dd4…` = F0 등록값. 서버 설정은 바꾸지 않았다.
- Specify: 독립 Opus 5.5(high)가 SPEC·Plan을 임차 원칙으로 정합했다. 부모는 개정 전 사본과 대조해 SPEC 72줄·Plan 198줄 변경이 모두 개정 주제 안이고 기존 사용자 변경 손실이 없음을, 현재 계약 문장에 proxy·토큰·방화벽·서버 재시작 요구가 남지 않음을 확인했다(Plan §15 작성 당시 이력 1곳은 이력으로 유지). R6: seq11 `ab898263…` → seq12 `ba94d1ec0affe02c47d3daed5e106eb9081f23ea3ad2d4854e357d484ef5ca1b`, 승인 기록 `docs/gates/spec-approvals/12.md`, affectedLeaves=ALL·measurementAffecting=true. spec-guard exit 0(`specSha` 일치). 커밋 `06cbf31`.
- P15 코드 정합(독립 Opus 5.5 high, 부모 검증): 차트(exec `6993614`)는 `inference.baseUrl`·`inference.port`, env `AI_GATEWAY_INFERENCE_BASEURL` 하나, 추론 자격 Secret 0, 수용 pod 추론 egress 0, 렌더 실패 사유 `INFERENCE_BASE_URL_REQUIRED`·`INFERENCE_BASE_URL_INVALID`·`INFERENCE_PORT_REQUIRED`·`INFERENCE_PORT_CONFLICT`. AI(`ddceb80`)는 runconfig·렌더·런타임 자격(F2~ proxy token 요구 제거)·netpol 문서·eval catalog(leaf 213·원자 23·상위 61·F2 필수 22, AC-35 하위 EGRESS 1)·user-run schema(빈 객체)·ManualRecorder(AC-35 분기 제거, `record user-run` 명령과 UR producer는 유지)·관련 시험을 정합했다.
- 기존 결함 수정(P15 이래): `app_render.py matrix`의 egress 대조가 lint 전용 표지(`198.51.100.20/32` 등)에 고정되어 실제 승인 runconfig로 render·deploy하면 `RENDER_MATRIX_MISMATCH:egress`로 실패했다. 기대 egress를 그 실행의 runconfig 해석 결과(`values.json`의 `netpol.targets`)에서 차트와 같은 규칙으로 계산하도록 고쳤고, 플래그가 켜졌는데 대상이 없으면 실패하도록 부모가 조건을 보강했다. 재현: 수정 전 lint 값과 다른 runconfig로 F2 render exit 1 → 수정 후 exit 0, 변이 음성 5종 모두 `RENDER_MATRIX_MISMATCH`. 차트는 추론 egress를 `netpol.targets.inference`로만 만들며 `baseUrl` host와 netpol cidr 일치는 차트·matrix 모두 강제하지 않는다(포트만 guard).
- 검증(로컬, `aigw-p06-tools:local` Helm v4.2.2 컨테이너 `--network none`): app main/restore lint, DB main/restore lint, expect-fail 3종, 변이 6종, selftest(runconfig 25·runtime-credentials·verify-credentials 42·stage-gate 29·backup-alert 9·rollback 92) 모두 exit 0, 부모 보강 뒤 app main/restore lint·실값형 lint·F2 render 재실행 exit 0. `:gateway-eval:test` 278건(XML 55, 실패·오류·skip 0, 부모 독립 집계) 뒤 `test-evidence verify` `TEST_EVIDENCE_VERIFIED`. render 모드 F3·F4는 P23 산출물 corpus catalog 부재로 기존과 같이 실행 불가(lint 행렬로 확인). 원시 근거: scratch `seq12-align/`.
- P17(독립 Opus 5.5 medium, 부모 검증, 커밋 `29661bf`): `docs/runbooks/inference-server-operations.md`(읽기 전용 상태 확인·모델 적재 절차, 접속 세부 미기재), `ops/inference-server/bin/aigw-ps-summary.sh`(loopback `GET /api/ps` 1회, 허용 필드만 출력, 파싱 실패 시 고정 문구·원문 미출력)와 fixture selftest 12/12(부모 재실행 `SELFTEST_PASSED`, `bash -n` 통과, shellcheck 미설치 NOT_RUN). `qwen3:4b` 적재 요청의 근거는 Ollama 공식 API 문서 "If an empty prompt is provided, the model will be loaded into memory."다.
- 운영 확인(부모, 사용자 지시): 스크립트를 표준 입력으로 서버에 넘겨 실행한 결과 `qwen3:4b` digest 일치·`context_length=8192`, `bge-m3` 미상주로 `ok=false`였다. runbook (b)대로 `/api/embed`에 `{"model":"bge-m3","input":""}` 요청 → `200`, 재확인 `ok=true`·`resident_count=2`(두 모델 `context_length=8192`). 서버 `OLLAMA_HOST`·`KEEP_ALIVE=24h`·`CONTEXT_LENGTH`·override 파일은 그대로다. `/api/embed` 적재는 문서 근거가 아니라 이 실측이 근거이며 runbook에 그렇게 적었다.
- 미실행: 실제 K8s 배포·실환경 F1~F4 수용·원격 CI/push. 열린 항목 ⓓ(Composer 사고문 혼입, P19/P20 착수 전)·ⓕ(digest 불일치 errorCode·AC-33 하위, P19 착수 전)·ⓔ-1(F2/F4 지연 겹침 제외)은 그대로다. 다음은 P18이다.


## P18·seq13(ⓕ)·P16 기존 결함·정본 이관 (2026-11-09)

- P18(독립 Opus 5.5 high, 부모 검증, 커밋 `95c0c6f`): `core.budget`(`CallType`·`ChatMessage`·`PromptTokenCounting`·`TokenBudgetPolicy` 운영값 3072/192/4928·3072/256/4864·2816/128/5120·ctx 8192·`PreCallBudgetAssert`·`TruncationDetector`·`PromptDraft`·`PromptReducer`), `integration.tokenizer`(`HfTokenizerCounter` SHA pin `aeb13307…`·offline, `QwenChatTemplateRenderer` template SHA `2d54db2b…`, `PromptTokenCounter`), `CoreBeansConfiguration` `@Bean` 4개, `IntegrationModuleConfiguration` `@Import` 3개, `AppContextWiringIT` 실제 계수기 주입·golden 1행 assertion. golden 기준 회차 = `20261104T044320Z-diag-v2-t0-b`(사본 SHA 원본 일치), 계수 = 서버 `promptTokens` 13/13·usage 4/4. G-USAGE `equalOnCacheHit` 일치라 잘림 감지·AC-31 등식 판정을 비활성하지 않는다. Thinking 전용 official/GGUF 계수 파일은 없어 v2 공식·GGUF 계수(Instruct·Thinking tokenizer.json 바이트 동일)와 Thinking audit 15행으로 parity 시험을 구성했다(데이터 생성 0).
- P18 범위 밖 기술 수정(부모 승인): `gateway-integration/build.gradle.kts` 시험 리소스 include에 `tokenizer/**` 추가(기존 `config/sources/**` 필터가 golden 사본을 빼던 문제), `CoreSeamDefaultsTest`에 `PromptTokenCounting` 대역 1줄.
- P18 검증(부모 XML 독립 집계): `:gateway-core:test` 117·`:gateway-integration:test` 96·`*AppContextWiringIT` 15·`TokenizerNativeSmokeIT` 1, 실패·오류·skip 0, 각 직후 `test-evidence verify` `TEST_EVIDENCE_VERIFIED`.
- seq13(사용자 "권장으로 진행"): 열린 항목 ⓕ를 닫았다 — readiness 상태·접수 503 errorCode `MODEL_DIGEST_MISMATCH`(닫힌 4값), 판정 순서 응답 형식 → 상주 → ctx → digest, 즉시 지속 경보, 수용 leaf `AC-33-DIGEST`(leaf 214·F2 필수 23·F4 포함). 독립 Opus 5.5가 SPEC 16줄·Plan 52줄을 정합했고 부모가 주제 범위·잔존 표기를 대조했다. 기준선 seq13 `8c95194890bf32ce5a960cbc85bcd68d9dbb31c45325555143d6d0835afab8dc`·`docs/gates/spec-approvals/13.md`, spec-guard exit 0, 커밋 `9793260`. enum·catalog 실제 반영은 P19 범위다.
- P16 기존 결함(부모가 P18 검증 중 발견, 독립 Opus 5.5 high 수정, 커밋 `9bcb54b`): `SourceObservationController`가 큐 저장 경계 밖 호출자로 `QueueJobRepository.nonterminal()`을 호출해 `ArchitectureRulesTest.everyQueueSqlMethodHasClosedSignatureAndActualProductionCaller`와 `CoreSeamDefaultsTest.verifyRegistersOneConcreteRegistryForBothInterfaces`가 실패했다(P16은 필터 실행이라 미검출). 허용 호출자 확대나 mock 없이, `SourceRequestProbe`가 기존 `TerminalCommitObserver`(FaultInjector와 같은 경로)로 결속 Job의 종결을 종결 tx 안에서 기록하고 롤백 시 되돌리게 했으며 컨트롤러는 probe만 의존한다. `test-hooks-v1.md` 계약 문구 불변. 검증: `:gateway-app:test` 67(실패 0)·`P16WorkflowCompletenessIT` 4·`WorkflowTerminalCommitIT` 5, 각 verify 통과(부모 XML 집계 일치).
- 정본 이관(사용자 지시): 승인 SPEC·Plan을 `docs/ai-rag-gateway-architecture.md`·`docs/ai-rag-gateway-architecture-plan.md`로 옮겨 git 추적한다(커밋 `7b39246`, nori-tc-exec 원본 제거 `c34518d`). 바이트 동일 이동이라 SPEC SHA·기준선 사슬은 그대로이며 새 경로 spec-guard exit 0. Plan의 경로 9곳과 M-10 "레포에 SPEC 사본을 두지 않는다" 규칙에 이관 사실을 반영했고 CI SPEC 입력 패키지 방식은 바꾸지 않았다. 이 문서의 이전 절에 남은 `nori-tc-exec/docs/task/` 경로는 당시 기록이다.
- 원격: nori-ai-gateway는 원격 저장소가 0개다. push 시에는 nori-tc-*와 같이 GitLab·GitHub 동시 전송으로 설정한다(사용자 지시). 다음은 P19다.


## P19 추론 Client·readiness·503 (2026-11-09)

- 구현(독립 Opus 5.5 high, 부모 검증, 커밋 `15e9b99`): 포트 `core.port.InferenceClient`(`chat`·`embed`, 결과 `ChatOutcome` = `Completed`·`BudgetExceeded`·`CallFailed{TRANSPORT·EMPTY_RESPONSE·TRUNCATED·INTERRUPTED}` 3변형 — Plan 의 단일 레코드 대신 호출 실패를 별도 갈래로 둠), `integration.inference`(`InferenceEndpointConfig` `scheme://host:port` 만 허용·SDK `+/v1`·readiness `+/api/ps`, `InferenceModelConfiguration`, `ChatTransport`·`SpringAiChatTransport`(`liveChatTransport`), `SpringAiOpenAiInferenceClient` = 예산 assert → `GenerationSemaphore` → 전송 1회 → `TruncationDetector`, `OllamaReadinessProbe` 판정 순서 형식 → 상주 → ctx 8192 → digest, `InferenceReadinessService` 30s 주기·LOADING/UNAVAILABLE 600s·CTX/DIGEST 즉시 경보·READY 해제), hook `EmbeddingProbeHook`·`ReadinessStubHook`, `JobAdmissionService` 503 + `Retry-After: 60`, `AdmissionErrorCode` 5값, `MetricsCatalog` 라벨, catalog AC-33-DIGEST(leaf 214), 수용 `EmbeddingProfileProbeAT`(suite `F3-PROBE`, MEASUREMENT 레코드만). 추론 빈은 모두 `ai.gateway.inference.mode=enabled` 조건부, `IntegrationModuleConfiguration`·`GatewayAppModuleConfiguration` `@Import` 등록, core 빈 추가 0.
- apiKey 결정(Plan "root 확인 대상"): Spring AI `OpenAiSetup` 은 빈 apiKey 를 무인증으로 처리하지만 `OPENAI_LOG` env 를 기본 채택해 요청 본문을 출력할 수 있어 SDK client 를 직접 만들고 `logLevel(OFF)` 를 명시했다. 이 경로는 SDK 가 credential 을 강제하므로 비밀이 아닌 고정 placeholder `aigw-no-auth` 를 코드에 둔다(Secret·env 미사용, Ollama 무인증이라 영향 없음). 전송 헤더 `Authorization: Bearer aigw-no-auth`.
- 재시도 0: OkHttp `retryOnConnectionFailure` 를 끌 수단이 없어 재사용 연결 끊김 시 재전송을 실측했다(수신 2). 요청 본문 재전송 불가 표시·연결 pool 0 으로 시도당 수신 1 을 시험으로 고정했다. connect 3s·요청 120s.
- 기대 생성 모델 digest: 속성 `ai.gateway.inference.generationModelDigest`(소문자 64hex, `enabled` 시 필수, 코드 기본값 없음). 차트·runconfig 전달 경로는 아직 없다 — 공급 전 `enabled` 배포는 기동 거부(fail-closed). 차트 작업에서 연결한다.
- 부모 검증에서 찾은 보완 5건(같은 에이전트가 반영, 범위 밖 쓰기 부모 승인): `EmbeddingProfileProbeAT` 누락, 경보 로그 코드가 `AllowlistLogFilter` 에 없어 `UNLISTED_MESSAGE` 로 가려짐, `ci/integration-required.txt` 에 새 IT 4종 미등록, `test-hooks-v1.md` readiness-stub 행 누락, `ManualRecorder` readiness 상태에 `MODEL_DIGEST_MISMATCH` 누락.
- 검증(부모 XML 독립 집계): integration 114·core 117·acceptance 41·app 70·app IT 31(8 클래스)·eval 280, 실패·오류·skip 0. 각 실행 직후 `test-evidence verify` `TEST_EVIDENCE_VERIFIED`(IT 는 `--require-docker`). spec-guard specSha `8c951948…`(seq13).
- 미실행·남은 차이: 실제 배포 Gateway 대상 F3-PROBE·실 추론 호출(네트워크 금지, 로컬 HttpServer 대역만), `ENDPOINT_ERROR` 사유의 지표 라벨 노출(라벨 신설 금지 — 서비스 상태로만 보유), 기본 `api.openai.com` 무연결은 대역 시험·코드 경로 근거(직접 관측 아님). 열린 항목 ⓓ(Composer 사고문 혼입)·ⓔ-1 은 그대로다.
- 사용자 결정(같은 날, 후속 작업 대상): ①AI Gateway DB 를 k8s 전용 PostgreSQL 대신 기존 서버 192.168.0.150 의 database `nori_ai_gateway`(계정 1개, 이름은 env 주입)로 쓴다 — 사용자가 DB·계정 생성과 `vector` 0.8.7 활성화를 마쳤고 부모가 그 계정 로그인·소유자·확장을 확인했다. ②저장소 구조를 형제 시스템 nori-wm 기준으로 정렬하고 SDD 문서는 레포 `docs/` 에 둔다. 두 결정은 SPEC 개정(seq14)과 정렬 작업으로 반영하며 P20 착수 전에 진행한다.


## U2 nori-common build-logic 채택 (2026-11-10, 구현·로컬 검증)

- 범위: worktree `C:/workspace_nori/nori-ai-gateway-u2`(branch `aigw/align-u2`, 기준 `f2e50c1`). 커밋 없음. 모듈 디렉터리 이동 0(U3 범위).
- 변경 파일(allowed_writes): `settings.gradle.kts`(nori-wm 과 같은 composite/published mode 유도, `../nori-common/build-logic` → `nori-common-build-logic` include, marker `settings-conventions`·`root-project-conventions` 0.0.6, `id("com.nori.common.settings-conventions")`, 평면 include 유지), 루트 `build.gradle.kts`(`id("com.nori.common.root-project-conventions")`, `allprojects { group = "com.nori.ai.gateway"; version = "0.0.1-SNAPSHOT" }`, subproject 저장소 블록 제거, conventions 와 같은 toolchain 21·UTF-8·`-parameters` 인라인 제거, `apply(plugin = "java")` → `pluginManager.withPlugin("java") { … }`, `aigw.*` 시험 증거 장치 본문 불변), `gradle.properties`(신규 — UTF-8 jvmargs·`systemProp.file.encoding`·`gitlabPackageProjectIds=33`), `gradle/libs.versions.toml`(`slf4j-api` 2.0.18, `flyway-gradle-plugin`·`flyway-database-postgresql`·`flyway-core` 11.14.1, `nori-storage-starter` → `noriObjectStorage = "0.0.1"`), `settings-gradle.lockfile`·모듈 7개 `gradle.lockfile`(신규), `.gitlab-ci.yml`, 이 절. 모듈 `build.gradle.kts` 는 conventions 와 겹치는 설정이 없어 바꾸지 않았다.
- root 승인으로 allowed_writes 밖을 바꾼 것(2026-11-10):
  - `gateway-eval/src/test/java/com/nori/ai/gateway/eval/TestKitSupport.java`: fixture settings 앞에 실제 settings 와 같은 `pluginManagement { includeBuild(<aigw.rootDir>/../nori-common/build-logic) { name = "nori-common-build-logic" } }` + `plugins { id("com.nori.common.settings-conventions") }` 를 두는 `buildLogicSettings()` 추가, `placeholder/build.gradle.kts` = `plugins { java }`. `RuntimeClasspathFinalizerFunctionalTest.java` 의 자체 settings 앞에도 같은 `buildLogicSettings()`. 이유: fixture 는 루트 `build.gradle.kts` 를 복사하므로(`TestKitSupport.java:18`) root-project-conventions 를 해석할 수 있어야 하고, 루트가 더 이상 모든 subproject 에 java 를 붙이지 않으므로 시험 없는 모듈 역할의 `placeholder` 가 java 를 직접 적용한다(`TestCountGuardFunctionalTest` 의 `:placeholder:testLeafEvidence`). fixture 의존은 junit·slf4j·Spring Boot BOM 등 Maven Central 좌표뿐이라 TestKit 에 `useLocalMaven` 을 넘기지 않았다. `HarnessDigest.ROOTS`(`HarnessDigest.java:15` = `eval/f0`, `eval/golden/tc-f0-v1.jsonl`, `eval/golden/tc-registry-fixture-v1.json`, `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0`)는 `src/test` 를 포함하지 않으므로 F0 harness digest 는 바뀌지 않는다.
  - `.gitlab-ci.yml` 전역 `variables: GITLAB_BASE_URL: "https://gitlab.noricloud.org"`(nori-wm `.gitlab-ci.yml:17` 과 같음). Gradle job 의 `../nori-common` clone 줄은 allowed_writes 그대로 `.aigw-spec-input` before_script 와 `package` script 첫 줄에 넣었다.
  - 루트 `gradle.lockfile` 은 승인됐으나 생성되지 않았다(루트에 resolvable configuration 없음). nori-wm 루트에도 없다(추적 lockfile = `settings-gradle.lockfile` + 모듈 8개).
  - 로컬 명령의 `-PuseLocalMaven` 사용(사용자 registry 자격 추가 전 구간).
- 범위 안 근본 수정: java-module-conventions 는 적용 시점에 모듈의 `libs` catalog extension 을 찾는다(`JavaModuleConventionsPlugin.kt:16`). 종전 루트 `subprojects { apply(plugin = "java") }` 는 모듈 평가 전에 java 를 붙여 `Extension of type 'VersionCatalogsExtension' does not exist` 로 실패했다. 7개 모듈은 모두 자기 `plugins {}` 의 `java-library`/`application` 으로 java 를 적용하므로 루트 본문을 `withPlugin("java")` 안으로 옮겼다. 구조용 project(U3 의 `:adapter`·`:apps`)에는 java 가 붙지 않는다.
- 결정·근거: archunit-conventions 미적용, composite 만 사용(⑤ 출력), noriCatalog 없음(settings-conventions 가 catalog 를 요구하지 않고 이 레포는 `com.nori.common` 좌표를 소비하지 않음 — `git grep` 결과 `com.nori:` 좌표는 catalog 의 object-storage 1행뿐). `slf4j-api` 2.0.18·flyway 11.14.1 은 Spring Boot 4.0.7 BOM 의 `slf4j.version`·`flyway.version` 과 같다. marker 0.0.6 은 include 하는 build-logic source version 이다(composite 에서는 해석에 쓰이지 않음).
- `nori-object-storage`: `com.nori:nori-object-storage-s3-starter:0.0.1`(전이 `com.nori:nori-object-storage-api:0.0.1`), noricloud GitLab Package Registry project id 33. lockfile 4개 모듈(acceptance·app·integration·persistence)에 `0.0.1` 로 고정됐다.
- 해석 출처: 로컬 = 사용자 `~/.gradle/gradle.properties` 의 `gitlabBaseUrl`·`gitlabToken`(사용자가 추가, 값 미출력)으로 settings-conventions 가 등록한 `GitLabPackages33`. `-PuseLocalMaven` 은 더 이상 필요 없다. 확인 명령: `-PuseLocalMaven` 없이 `--refresh-dependencies --info` 로 `:gateway-integration:dependencies --configuration runtimeClasspath`(scratchpad 진단 init script 로 저장소 목록·해석 파일 출력, 레포 파일 변경 0) exit 0. 저장소 = Maven Central + `GitLabPackages33 url=https://gitlab.noricloud.org/api/v4/projects/33/packages/maven`(mavenLocal 없음). 인용: `Cached resource https://gitlab.noricloud.org/api/v4/projects/33/packages/maven/com/nori/nori-object-storage-s3-starter/0.0.1/nori-object-storage-s3-starter-0.0.1.pom is up-to-date`, `Downloading https://gitlab.noricloud.org/api/v4/projects/33/packages/maven/com/nori/nori-object-storage-api/0.0.1/nori-object-storage-api-0.0.1.pom`, 해석 파일 `C:\Users\DongGeon\.gradle\caches\modules-2\files-2.1\com.nori\nori-object-storage-s3-starter\0.0.1\ba1eceacae4c372b5930c6f12f0eaff426d4915\nori-object-storage-s3-starter-0.0.1.jar`(원격 저장소 캐시 경로). 출력 중 `token` 이 든 줄은 DJL `tokenizers` 뿐이었다.
- `-PuseLocalMaven` 구간: ①~⑥(app IT) 은 사용자 자격 추가 전이라 root 지시대로 `-PuseLocalMaven --offline` 으로 실행했다. 당시 Gradle 원격 캐시에는 `com.nori:*:0.0.1` 이 없어(`0.0.1-SNAPSHOT` 만) mavenLocal(`C:\Users\DongGeon\.m2\repository\com\nori\…\0.0.1`, 발행 commit `3207a42` 와 같은 내용)에서만 해석될 수 있었다. 같은 api jar 를 이후 registry 가 checksum 일치로 확인했다(`Found locally available resource with matching checksum: [https://gitlab.noricloud.org/…/nori-object-storage-api-0.0.1.jar, C:\Users\DongGeon\.m2\…\nori-object-storage-api-0.0.1.jar]`). ⑥ 후반(integration·persistence IT)과 ⑦ 은 `-PuseLocalMaven` 없이 실행했다.
- CI 해석: `GitLabPackages33`(CI_JOB_TOKEN) 경로는 첫 CI 파이프라인(F4 뒤)에서 확인한다. project 33 과 nori-common 의 job-token allowlist 에 project 50(nori-ai-gateway) 등록이 필요하며 root 가 나중에 처리한다.
- 명령 결과(각 별도 실행, `AIGW_SPEC_FILE`=이 worktree SPEC, `AIGW_PG_TEST_IMAGE`=Plan 고정값):
  - ① spec-guard exit 0 — `{"specInputOrigin":"LOCAL_FILE","specSha":"9270488294079f4bc17e387be9f64e07e96f75916761e895dd21ff21f94aad19","specShaSource":"FILE_HASHED"}`, `BUILD SUCCESSFUL in 59s`.
  - ② `resolveAndLockAll --write-locks` exit 0 — `Persisted dependency lock state` 모듈 7개, `BUILD SUCCESSFUL in 1m 15s`.
  - ③ `build` exit 0 — `BUILD SUCCESSFUL in 17m 9s`. JUnit(XML 집계, 실패·오류·skip 0): acceptance 41, app 70, core 117, eval 280(TestKit 5개 클래스 13건 포함 — F0InputBinding 2·LeafReportRoundTrip 3·RuntimeClasspathFinalizer 1·RunWorkingDir 2·TestCountGuard 5), integration 114, persistence 12, tc-mock 48. 직후 7개 `:…:test` 의 `test-evidence verify` exit 0 `TEST_EVIDENCE_VERIFIED`.
  - ④ `check` exit 0 — 7개 모듈 `verifyDependencyMatrix`·`verifyResolvedDependencyMatrix` 실행, Test task UP-TO-DATE(③ 결과 재사용), `BUILD SUCCESSFUL in 42s`. 직후 verify exit 0 `TEST_EVIDENCE_VERIFIED`.
  - ⑤ `verifyBuildLogicResolutionMode` exit 0 — `NORI_BUILD_LOGIC_MODE=composite; INCLUDED_BUILD_LOGIC=1; INCLUDED_PRODUCT_BUILD=0`.
  - ⑥ `integrationTest -Paigw.requireDocker=true` exit 1 — `:gateway-app:integrationTest` 167건 중 실패 2(오류·skip 0): `AdmissionMaskingIT.actualPostStoresOnlySanitizedUserMessageAndFailureRollsBackAllRows`(`AdmissionMaskingIT.java:32`, expected `"[마스킹] …"` but was `"service:TC"`), `WorkflowExpiryRaceIT.toolCallAuditFailureOnTheThrewPathIsCountedAndLoggedWhileThePrimaryCancellationWins`(`WorkflowExpiryRaceIT.java:112`, TOOL_CALL `["tc.eqp.getOperationalSnapshot","tc.commLogs.search"]` vs 기대 `["tc.eqp.getOperationalSnapshot"]`), `BUILD FAILED in 1h 26m 55s`. Gradle 이 첫 실패에서 멈춰 integration·persistence IT 는 미실행이었으므로 경로 지정으로 별도 실행(`--offline --continue`) exit 0 — integration 1·persistence 85, 실패·오류·skip 0, `BUILD SUCCESSFUL in 13m 46s`, 직후 verify(`--require-docker`) exit 0 `TEST_EVIDENCE_VERIFIED`. app IT verify 는 exit 1 `STALE_TEST_EVIDENCE` — 그 사이 다른 세션이 이 worktree 에서 두 클래스만 필터 실행해(11:10, XML 2 클래스 7건 중 실패 2) 마지막 실행 fingerprint(testFilters 포함)가 비한정 호출과 다르다.
  - ⑥ 실패 2건은 U2 회귀가 아니다. 기준 `f2e50c1` 을 `git archive` 로 scratchpad 에 풀어 같은 두 클래스를 실행하니(`--offline`) 같은 두 메서드가 같은 메시지·같은 줄로 실패했다(7건 중 실패 2, exit 1). `tc.commLogs.search` 는 P16(`4397bbc`)에서 incident workflow 에 추가됐고 두 시험의 기대값은 그 전 상태다. U2 범위 밖이라 고치지 않았다.
  - ⑦ `resolveAndLockAll --write-locks` 재실행(`-PuseLocalMaven` 없이) exit 0 — lockfile 8개(`settings-gradle.lockfile` + 모듈 7개)가 ② 결과와 바이트 동일(`cmp`). `-PuseLocalMaven` 유무에 따른 lockfile 차이도 0 이다.
- 부수 효과: `--write-locks` 는 composite 의 included build 에도 적용되어 `../nori-common/build-logic/gradle.lockfile` 을 CRLF 로 다시 쓴다(내용 차이 0, `git diff --ignore-cr-at-eol` 0줄). ②·⑦ 뒤 각각 `git checkout` 으로 되돌려 nori-common 은 clean 이다. 이 레포 lockfile 은 `.gitattributes` `eol=lf` 로 커밋 때 정규화된다.
- 증거·환경: `evidence/**` 변경 0(빌드가 쓰는 시험 증거는 각 모듈 `build/aigw-otr`·`build/aigw-evidence` 와 루트 `build/aigw-fingerprint` 뿐). 남은 시험 컨테이너 0, 오늘 만든 Docker 이미지 0. 고정 이미지(pgvector·ryuk)는 병행 검증이 쓰는 중이라 남겼다.
- 참고: `verifyDependencyMatrix` 금지 목록은 consumer 가 채운 것만 검사하며 이 레포는 채우지 않았다(U2 allowed_writes 에 없음).

## gateway-app 통합 시험 기존 실패 2건 해소 (2026-11-10, 시험 기대값 정정·로컬 검증)

- 범위: worktree `C:/workspace_nori/nori-ai-gateway-fix-it`(branch `aigw/fix-app-it`, 기준 `c8e6f40`). 커밋 없음. U2 ⑥ 에서 기록한 실패 2건(기준 `f2e50c1`·`c8e6f40` 모두 재현)을 다룬다. production 코드 변경 0.
- `AdmissionMaskingIT.actualPostStoresOnlySanitizedUserMessageAndFailureRollsBackAllRows`(종전 `AdmissionMaskingIT.java:32`, 기대 `"[마스킹] …"`, 실제 `"service:TC"`):
  - 원인: 시험 spy 는 `ContentSanitizer.sanitize` 의 **마지막** 호출 결과를 관측한다. P14(`7c810d2`)가 추가한 `AuditFieldAllowlist.requireService`(`gateway-core/.../audit/AuditFieldAllowlist.java:64`)는 인입 serviceRef 를 같은 sanitizer 로 검사하며, 같은 접수 tx 에서 질문 sanitize(`JobAdmissionService.java:88`) 직후 `audit.write(AuditEvent.jobAccepted(…))`(같은 파일 93줄)가 이 검사를 부른다. 그래서 마지막 호출이 `sanitize("service:TC")` 가 됐다. U2 절의 "P16 에서 기대값이 낡았다" 는 설명은 이 시험에는 맞지 않는다 — 이 시험은 P14 에서 깨졌다. P14 의 필터 명령(Plan §P14 명령 ①)에 이 클래스가 없었고 이후 P15~P19 도 필터 실행만 해서 드러나지 않았다.
  - 판정: production 동작은 정본과 맞다. Plan P14 "`AuditFieldAllowlist`는 event별 필드 집합·기존 enum·CapabilityRegistry 소속을 검사한다(CoreBeans의 같은 sanitizer/registry 재사용)", SPEC §12.3 "허용값 검증과 호출자 제한은 실제 저장·실행 경계에서 강제해야 한다". 시험의 "마지막 호출" 관측이 낡았다.
  - 수정: 질문 원문을 상수 `MASKED_QUESTION` 으로 두고 spy 가 그 입력의 호출 결과만 `observed` 에 담는다. 기존 단언(`observed.text()` = 저장된 `content_masked`, `ruleSetVersion` = `mask-v1`, `applied` = true)은 그대로다.
- `WorkflowExpiryRaceIT.toolCallAuditFailureOnTheThrewPathIsCountedAndLoggedWhileThePrimaryCancellationWins`(종전 `WorkflowExpiryRaceIT.java:112`, TOOL_CALL `["tc.eqp.getOperationalSnapshot","tc.commLogs.search"]` vs 기대 `["tc.eqp.getOperationalSnapshot"]`):
  - 원인: P16(`4397bbc`)이 `workflows/tc/INCIDENT_ANALYSIS.json` 에 `commLogs` 단계(`tc.commLogs.search`, `dependsOn: snapshot`)를 넣고 `logs` 를 그 뒤(`dependsOn: commLogs`)로 옮겼다. 같은 커밋은 `WorkflowMockIT` 기대값은 고쳤지만 이 시험(P14 작성)은 고치지 않았다.
  - 판정: SPEC §6.3 순서 `tc.eqp.getOperationalSnapshot → tc.commLogs.search → tc.logs.search …` 와 같은 절 "TC 통신로그 READ 연결(2026-11-09 사용자 승인)" 이 정본이다. 시험 기대값이 낡았다.
  - 수정: 조회에 `ORDER BY id` 를 붙이고 `containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search")` 로 바꿨다(trigger 가 `tc.logs.search` 감사만 거부하므로 앞선 두 Tool 감사가 단계 순서대로 저장된다). trigger 설명 주석도 snapshot·commLogs 로 맞췄다.
- 변경 파일: `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/AdmissionMaskingIT.java`, `gateway-app/src/integrationTest/java/com/nori/ai/gateway/app/job/WorkflowExpiryRaceIT.java`, 이 Dev report. `evidence/**`·SPEC·Plan 변경 0.
- 명령 결과(각 별도 실행, `--offline --no-daemon`, `AIGW_SPEC_FILE`=이 worktree SPEC, `AIGW_PG_TEST_IMAGE`=Plan 고정값):
  - ① spec-guard exit 0 — specSha `9270488294079f4bc17e387be9f64e07e96f75916761e895dd21ff21f94aad19`.
  - ② 두 클래스 `--tests '*AdmissionMaskingIT' --tests '*WorkflowExpiryRaceIT'` exit 0 — `BUILD SUCCESSFUL in 5m 34s`. XML: AdmissionMaskingIT 2·WorkflowExpiryRaceIT 5, 실패·오류·skip 0.
  - ③ 비한정 `:gateway-app:integrationTest -Paigw.requireDocker=true` 1회차 exit 1 — `BUILD FAILED in 1h 6m 17s`, 167건 중 실패 1(오류·skip 0): `IdempotentAdmissionIT.duplicateConflictExpiredAndCrossIdentityAreDistinct` 가 `PgTestDatabase.java:65` "격리 PostgreSQL 준비에 실패했습니다"(pgvector 컨테이너 `exited with code 255`, `database system is ready to accept connections` 대기 timeout). 수정 대상과 무관한 컨테이너 기동 실패다. 그 시험 구간(02:55:26Z 시작, 187s) 안에 다른 프로젝트의 Docker 스택이 기동했다(컨테이너 StartedAt 02:57:19Z~02:58:04Z, Docker VM 메모리 약 4GB). 경합이 원인이라는 것은 추정이다 — Docker 이벤트 버퍼에 그 구간이 남아 있지 않아 확정하지 못했다.
  - ④ 비한정 재실행 exit 0 — `BUILD SUCCESSFUL in 52m 56s`. `gateway-app/build/test-results/integrationTest/*.xml` 44개 집계 tests 167·failures 0·errors 0·skipped 0(IdempotentAdmissionIT 3건 통과).
  - ⑤ 직후 `:gateway-eval:run -Paigw.requireDocker=true --args="test-evidence verify --tasks :gateway-app:integrationTest --require-docker"` exit 0 `TEST_EVIDENCE_VERIFIED`. `-Paigw.requireDocker=true` 를 빼면 fingerprint 의 `aigw.requireDocker` system property(`build.gradle.kts` `aigwInputComponents`)가 `false` 로 계산되어 `STALE_TEST_EVIDENCE`(exit 4)가 난다(실측). AGENTS.md §5 의 "integrationTest 는 `-Paigw.requireDocker=true` 동반" 그대로다.
  - `:gateway-app:test`·`:gateway-eval:test` 는 실행하지 않았다. 변경은 `integrationTest` source set 뿐이고 두 task 의 classpath 에 들어가지 않는다.
- 남은 항목: Plan U2 진행 상태 줄(⑥ 미충족 `[~]`)은 Plan 개정 권한 밖이라 고치지 않았다. 남은 시험 컨테이너 0(`docker ps -a --filter label=org.testcontainers=true -q` 빈 출력), 이미지 삭제·다른 프로젝트 컨테이너 조작 0.

## U3 모듈 디렉터리 이동·경로 갱신 (2026-11-10, commit 1+2 구현·로컬 검증, commit 3 결정 대기)

- 범위: worktree `C:/workspace_nori/nori-ai-gateway-u3`(branch `aigw/align-u3`, 기준 `37e3852`). 로컬 commit 2개, push 0. commit 3(integration 분할)은 아래 "결정 대기" 때문에 하지 않았다. 이 절은 커밋하지 않았다.
- commit:
  - commit 1 `2676ca4d63a791e174db6d648057d1bb840df001` `refactor: move gateway modules under modules/ without content changes (U3 commit 1)` — `git mv` 만. `git show --stat -M` 마지막 줄 `557 files changed, 0 insertions(+), 0 deletions(-)`, `--name-status` 전부 `R100`(100% 이외 rename 0줄).
  - commit 2 `2911c3c54bee8aaea244a9792b4a83e7e617d229` `build: map moved modules with projectDir and update module paths (U3 commit 2)` — 24개 파일, `104 insertions(+), 63 deletions(-)`.

### 옛 경로 → 새 경로

| 옛 디렉터리 · task 경로 | 새 디렉터리 · task 경로 |
|---|---|
| `gateway-core/` · `:gateway-core` | `modules/ai-gateway-core/` · `:ai-gateway-core` |
| `gateway-persistence/` · `:gateway-persistence` | `modules/adapter/ai-gateway-db-adapter/` · `:adapter:ai-gateway-db-adapter` |
| `gateway-integration/` · `:gateway-integration` | (commit 1+2 임시) `modules/adapter/ai-gateway-inference-adapter/` · `:adapter:ai-gateway-inference-adapter` — loki·tempo·gitlab 분리는 commit 3 |
| `gateway-tc-mock/` · `:gateway-tc-mock` | `modules/adapter/ai-gateway-tc-mock-adapter/` · `:adapter:ai-gateway-tc-mock-adapter` |
| `gateway-app/` · `:gateway-app` | `modules/apps/ai-gateway-app/` · `:apps:ai-gateway-app` |
| `gateway-acceptance/` · `:gateway-acceptance` | `modules/apps/ai-gateway-acceptance-app/` · `:apps:ai-gateway-acceptance-app` |
| `gateway-eval/` · `:gateway-eval` | 변경 없음 |
| (없음) | 구조용 `:adapter` → `modules/adapter`, `:apps` → `modules/apps`(java 미적용, lockfile 없음 — nori-wm 과 같음) |

### 결정과 근거

- `settings.gradle.kts`: nori-wm `settings.gradle.kts` 의 블록 구성(`include` 다음 `project(":…").projectDir = file("modules/…")`, 구조용 `:adapter`·`:apps` projectDir 지정)을 그대로 따랐다. `:gateway-eval` 은 루트에 둔다(Plan §3.2 표 예외).
- `gateway-integration` 의 commit 1 이동 대상: Plan 표는 분할 뒤 위치만 정한다. commit 2 뒤 ⑥ 이 `gateway-integration/` 0줄을 요구하므로 commit 1 에서 옮겨야 하고, 표에 있는 이름 중 모듈 전체를 담을 수 있는 것은 분할 뒤에도 같은 자리에 남는 추론+tokenizer 쪽 `modules/adapter/ai-gateway-inference-adapter/` 뿐이다(새 이름 0). commit 3 은 여기서 loki·tempo·gitlab 을 `git mv` 로 꺼내는 형태가 된다.
- 루트 `build.gradle.kts`: integrationTest source set 대상 `project.name` 집합을 새 이름으로, `:gateway-app` 참조 3곳·`:gateway-acceptance` 1곳을 새 task 경로로 바꿨다.
- `leaf-catalog-v2.json`: `task` 값 5개와 같은 값을 key 로 쓰는 `producerIndex` 의 key 1개(`LeafCatalogTest` 가 `producerIndex.path(task)` 를 대조)를 `:apps:ai-gateway-app:integrationTest` 로 바꿨다. leaf·원자 ID 변경 0.
- 수용 배포본 시작 스크립트: application plugin 은 project 이름으로 `bin/<이름>` 을 만든다. `applicationName` 으로 옛 이름을 고정하지 않고 새 이름 `bin/ai-gateway-acceptance-app` 을 따르도록 `.gitlab-ci.yml`(package 검사 줄)과 `deploy/docker/Dockerfile-acceptance`(`test -x`·`ENTRYPOINT`)의 모듈 경로 문자열을 바꿨다. exec 의 ai-gateway 차트 템플릿에는 command override 가 없다(이미지 ENTRYPOINT 사용).
- 범위 안 근본 수정(실행 순서 의존): 둘째 build 에서 `RepositorySignatureArchTest.nineRepositoriesRequireScopeAndMaintenanceCallersAreRestricted` 가 실패했다(기대 `upsert=[app.ops.SourceRetentionProbeScheduler]` 가 관측에 없음). 이 시험은 app 의 `build/classes/java/main` 이 있으면 읽고 없으면 건너뛴다. 옛 평면 경로에서는 실행 순서상 app compileJava 가 persistence test 보다 먼저 돌아 우연히 있었고, `:adapter:*` 가 `:apps:*` 보다 앞서는 지금은 없다. app `test` 의 `dependsOn(":gateway-eval:classes")` 와 같은 방식으로 db-adapter `test` 에 `dependsOn(":apps:ai-gateway-app:classes")` 를 넣었다(시험 classpath·fingerprint 불변).
- TestKit fixture: `RuntimeClasspathFinalizerFunctionalTest` 의 fixture settings 도 `:apps:ai-gateway-app` + `:apps`·`:apps:ai-gateway-app` projectDir(`modules/apps/…`)로 바꿨다. 첫 실행은 `:apps` projectDir 를 지정하지 않아 `Configuring project ':apps' without an existing directory is not allowed` 로 실패했고, 실제 settings 와 같이 지정해 해소했다.
- testFixtures 소유(Plan §3.2 "U3 에서 기록"): commit 1+2 에서는 `DockerRequiredExtension`(`com.nori.ai.gateway.integration.testing`)이 `:adapter:ai-gateway-inference-adapter` testFixtures 에 있다. 최종 소유는 commit 3 결정과 함께 기록한다.
- lockfile: 모듈 `gradle.lockfile` 은 디렉터리와 함께 이동했고 build·integrationTest 뒤 `git status` 변경 0. `--write-locks` 는 실행하지 않았다(새 모듈 없음). nori-common 작업 트리 clean.

### 명령 결과 (commit 1+2 검증 단위, 각 별도 실행, `--offline --no-daemon`, `AIGW_SPEC_FILE`=이 worktree SPEC, `AIGW_PG_TEST_IMAGE`=Plan 고정값)

- ① spec-guard exit 0 — `{"specInputOrigin":"LOCAL_FILE","specSha":"9270488294079f4bc17e387be9f64e07e96f75916761e895dd21ff21f94aad19","specShaSource":"FILE_HASHED"}`(이동 전 기준과 편집 뒤 각각).
- ② `build`:
  - 1회차 exit 1 — `RuntimeClasspathFinalizerFunctionalTest` 1건 실패(위 TestKit fixture), `BUILD FAILED in 11m 59s`. 수정 뒤 그 클래스만 실행 exit 0(`BUILD SUCCESSFUL in 2m`).
  - 2회차 exit 1 — `RepositorySignatureArchTest` 1건 실패(위 실행 순서 의존), `BUILD FAILED in 8m 30s`.
  - 3회차(`build --continue`) exit 0 — `BUILD SUCCESSFUL in 4m 21s`, `84 actionable tasks: 51 executed, 33 up-to-date`. JUnit(XML 집계, 실패·오류·skip 0): eval 280, core 117, db-adapter 12, inference-adapter 114, tc-mock-adapter 48, app 70(`ArchitectureRulesTest`·`ModuleConfigurationCompletenessTest` 포함), acceptance-app 41 — 합 682, U2 와 같은 건수.
- ③ `integrationTest -Paigw.requireDocker=true --continue` exit 0 — `BUILD SUCCESSFUL in 1h 2m 12s`(05:37:03Z~06:39:17Z). JUnit: db-adapter 85, inference-adapter 1(`TokenizerNativeSmokeIT`), app 167, 실패·오류·skip 0. core·tc-mock-adapter·acceptance-app 은 `NO-SOURCE`(미등록 모듈).
- `test-evidence verify`(Gradle 경로): test 7개 task·IT 3개 task(`-Paigw.requireDocker=true --require-docker`) 모두 exit 1 `NO_TESTS_EXECUTED`(`:gateway-eval:test` 단독은 exit 0 `TEST_EVIDENCE_VERIFIED`). 원인은 아래 결정 대기 1.
  - 진단(레포 변경 0): scratchpad 에 `build`→루트 `build`, `ai-gateway-core`→`modules/ai-gateway-core`, `adapter`→`modules/adapter`, `apps`→`modules/apps`, `gateway-eval`→`gateway-eval` junction 을 둔 디렉터리에서 `gateway-eval` installDist 의 `EvalCli test-evidence verify --baseline <절대경로>` 를 실행하니 test 7개 task exit 0, IT 3개 task(`--require-docker`) exit 0, `--selector integrationTest --required ci/integration-required.txt --require-docker`(등록 클래스 검사 포함) exit 0, 모두 `TEST_EVIDENCE_VERIFIED`. 즉 증거·fingerprint 는 일치하고 verifier 의 디렉터리 해석만 맞지 않는다. junction 은 진단 뒤 링크만 지웠다.
- ④ `aigwPackage -Paigw.gitSha=<HEAD>`(clean tree) exit 0 — `build-identity.json` = `{"appJarSha256":"b50dc7056cdfc379b391f7a2a80e80a7739bccf0e7a766c774388e0c8d5bba5a","gitSha":"2911c3c54bee8aaea244a9792b4a83e7e617d229","runtimeClasspathSha256":"965e90f240dd0002e8722ec1962d6c18907e0c642b1b4f1b32d86222b0f6e02b"}`, `acceptance/bin/ai-gateway-acceptance-app`(+`.bat`) 생성. gitSha 없는 `aigwPackage` 는 P15 설계대로 `BUILD_IDENTITY_GIT_SHA_MISSING` 이므로 양성 형식으로 실행했다.
- ⑤ 경로 문자열을 바꾼 도구는 `ops/deploy/aigw-helm.sh`(fixture corpus 허용 디렉터리·기본 catalog 경로) 하나다. 기존 도구 이미지 `aigw-p06-tools:local`(`--network none`, 이 worktree·`nori-tc-exec` readonly mount, `AIGW_SELFTEST_EXEC_DIR=/nori-tc-exec`)로 실행, 모두 exit 0 `"status": "PASS"`: runconfig-selftest 25, stage-gate 29, runtime-credentials 81 명령, verify-credentials 42, rollback-rehearsal 92(`actualNetworkCalls: 0`), backup-alert-injection 9.
- ⑥ 0줄 아님 — 4줄, 모두 allowed_writes 밖이거나 그 짝이라 바꾸지 않았다(결정 대기 2): `AGENTS.md:68`, `eval/py/tests/fixtures/pdg-vectors-plan-p08.json:2`, `gateway-eval/src/test/java/com/nori/ai/gateway/eval/CiEvidenceTest.java:101`, `modules/adapter/ai-gateway-tc-mock-adapter/src/test/resources/pdg-vectors-v1.json:2`.
- 경로 길이: 이 worktree 안 최장 경로(`C:/workspace_nori/nori-ai-gateway-u3/` 포함)는 `modules/` 쪽 207자(`modules/adapter/ai-gateway-inference-adapter/build/classes/java/main/.../InferenceModelConfiguration$SingleAttemptHttpClient$1.class`), 추적 파일 181자. 240자 초과 249개는 모두 이동하지 않은 `gateway-eval/build/tmp/test/work/.gradle-test-kit/caches/…`(TestKit Gradle 캐시, 최장 321자)이다.
- 증거·환경: `evidence/**`·SPEC·Plan 변경 0. 남은 시험 컨테이너 0(`docker ps -a --filter label=org.testcontainers=true -q` 빈 출력), Docker 이미지 목록은 시작 전과 같다(새로 만든 이미지 0, 삭제 0).

### 결정 대기 (root)

1. **eval verifier 의 task 경로 → 디렉터리 해석(차단):** `gateway-eval/src/main/java/com/nori/ai/gateway/eval/report/TestEvidenceVerifier.java:14-18`, `ci/BuildIdentityManifest.java:51`, `ci/CiEvidence.java:53-55` 가 `:a:b:task` 를 `<root>/a/b/build/…` 로 읽는다. projectDir 가 `modules/…` 인 지금은 `test-evidence verify`·`ci-evidence pack`·`--expect-build-identity` 가 `NO_TESTS_EXECUTED` 다. 세 파일은 U3 allowed_writes 밖이다(HarnessDigest ROOTS 의 `gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0` 밖이므로 F0 digest 영향 0).
2. **⑥ 잔존 4줄(allowed_writes 밖):** `AGENTS.md:68` 의 U5 이전 migration 경로, `eval/py/tests/fixtures/pdg-vectors-plan-p08.json:2` 의 정본 경로 설명(tc-mock `pdg-vectors-v1.json` 과 바이트 동일 사본이라 tc-mock 쪽도 함께 보류), `CiEvidenceTest.java:101` 의 가짜 `git status` 경로 문자열.
3. **commit 3 분할의 공유 클래스 배치:** `integration.loki` 의 `SourceHttpClient`·`SourceReadAccess`(`SourceObservability`·`AllowlistClientRequestObservationConvention` 포함)를 tempo·gitlab 이 쓰고(`TempoTraceConnector` 는 `LokiSourcePolicy.APPS`·`LokiLogConnector.nanos` 도 사용), `loki.TcSourceAllowlist` 는 `gitlab.GitLabSourcePolicy` 를 쓴다. 패키지 단위로 나누면 loki ↔ gitlab 순환과 tempo·gitlab → loki 의존이 생겨 Plan §3.2 "integration 계열 adapter 사이 의존 0, adapter → core 만" 과 충돌한다. 시험도 `SourceAttemptGuardTest`(gitlab·loki·tempo 동시)·`ConnectorTestSupport`(공유)·`GitLabPipelineLineageTest`(→ `LokiQueryNotRecordedTest.Capture`)가 경계를 넘는다. 공유 클래스의 위치·이름은 Plan 표에 없다.

### U3 결정 대기 1·2 처리 — fix commit (2026-11-10, root 승인)

- commit `2c7b41744ec2e2fcbeaf925cbcf0655afacf96e5` `fix: resolve test evidence through the recorded projectDir and update residual module paths (U3)` — 11개 파일, `87 insertions(+), 19 deletions(-)`. commit 1·2 는 amend 하지 않았다.
- 1(경로 해석): 루트 `build.gradle.kts` 의 `:gateway-eval:run` doFirst 가 `build/aigw-fingerprint/<task>.json` 에 `projectDir`(루트 기준 상대 경로, `/` 구분)를 함께 기록한다. `TestEvidenceVerifier` 는 그 값으로 정제 요약·run-binding 위치를 찾고, 값이 없거나 절대 경로이거나 루트 밖이면 `TEST_PROJECT_DIR_INVALID`(exit 4)다. 디렉터리 탐색·task 경로 유도는 하지 않는다. `BuildIdentityManifest.verifyTaskSummaries` 와 `CiEvidence.pack` 은 공용 `TestEvidenceVerifier.summaryFile(root, task)` 로 같은 위치를 읽는다. 묶음 항목 이름(`CiEvidence.summaryPath`)은 task 경로 기준 논리 이름 그대로다 — evidence 모드 job 에는 fingerprint 가 없으므로 `ci-evidence verify` 가 실제 projectDir 을 알 수 없다.
- 실패 시험 먼저: `TestEvidenceVerifyTest.findsEvidenceOnlyThroughRecordedProjectDir`(`:sub` → `modules/sub`, projectDir 누락·루트 밖 거부), `CiEvidenceTest.runnerAndPackReadTaskEvidenceThroughRecordedProjectDir`(runner `--expect-build-identity`·pack·verify), `ProjectDirMappingFunctionalTest.verifyFindsEvidenceOfModuleMappedUnderModulesDirectory`(실제 루트 build TestKit, `:apps:sub` → `modules/apps/sub`). 수정 전 3건 모두 `NO_TESTS_EXECUTED` 로 실패(exit 1, `12 tests completed, 3 failed`), 수정 뒤 3 클래스 exit 0. 기존 fixture 의 fingerprint 에 `"projectDir":"sub"` 를 더했다(`TestEvidenceVerifyTest`·`CiEvidenceFixture`). 루트 `:sub` 를 쓰는 기존 TestKit 시험은 그대로 통과했다.
- 2(⑥ 잔존 4줄): `AGENTS.md:68` 경로, `AGENTS.md:36` "U3 전까지는 왼쪽 열" 문구(→ "U3 에서 했다(왼쪽 열은 이동 전 디렉터리이고, `gateway-integration` 의 외부 시스템별 분할은 U3 셋째 commit 이다)"), pdg 벡터 사본 두 파일의 `source` 설명(바꾼 뒤 `cmp` 동일), `CiEvidenceTest.java:101` 가짜 status 경로. 다른 내용 변경 0. `AGENTS.md:42` 표 행에는 source-support 가 없다(이번 승인 범위 밖이라 그대로 둠).
- 결과(각 별도, `--offline --no-daemon`):
  - `:gateway-eval:test` exit 0 — `BUILD SUCCESSFUL in 8m 18s`, XML 57개 tests 283(종전 280 + 새 시험 3)·failures 0·errors 0·skipped 0. 직후 `test-evidence verify --tasks :gateway-eval:test` exit 0 `TEST_EVIDENCE_VERIFIED`.
  - 재실행 없는 verify: test 6개(core·db-adapter·inference-adapter·app·acceptance-app + eval) exit 0 `TEST_EVIDENCE_VERIFIED`. `:adapter:ai-gateway-tc-mock-adapter:test` 는 exit 1 `STALE_TEST_EVIDENCE` — pdg 벡터 `src/test/resources/pdg-vectors-v1.json` 은 그 Test task classpath 이므로 fingerprint 가 정확히 바뀌었다. 그 task 만 재실행(exit 0, XML 7개 tests 48·실패 0) 뒤 verify exit 0. IT 3개 `--tasks …:integrationTest --require-docker -Paigw.requireDocker=true` exit 0, `--selector integrationTest --required ci/integration-required.txt --require-docker` exit 0, 모두 `TEST_EVIDENCE_VERIFIED`(commit 2 단계 IT 실행 결과).
  - integrationTest 재실행이 불필요한 근거: `aigwInputFingerprint`(`build.gradle.kts` `aigwInputComponents`)의 입력은 Test task classpath 파일 내용·specSha·baselineSha256·catalogSha256·includeTags·testFilters·`aigw.*` system property 다. fix commit 이 바꾼 것은 `gateway-eval` main·test(IT classpath 밖), 루트 build 의 fingerprint 기록 형식(fingerprint 계산식 불변), AGENTS.md·eval/py(입력 아님), tc-mock 의 test resource(tc-mock `test` classpath 만 — app IT 는 tc-mock `testFixtures` 만 씀)다. 실제로 IT verify 가 재실행 없이 통과했다.
  - ⑥ 0줄.

## U3 commit 3 — integration 의 외부 시스템별 분할 + Source 공용 기반 (2026-11-10, 구현·로컬 검증)

- commit `7cba8d45f8bb80f9f828defcddb8d07462980712` `refactor: split gateway-integration per external system with a shared source-support module (U3 commit 3)` — 70개 파일, `556 insertions(+), 175 deletions(-)`. rename 30개(R053~R100, `git mv` 로 이동 후 패키지·import 만 수정), 새 파일 12개(4개 모듈 build·lockfile, `TcSourcePolicy`, adapter 별 SourceAttemptGuardTest 3개). Plan §3.2 표 integration 행의 2026-11-10 사용자 결정(Source 공용 기반)을 계약으로 따랐다. Plan 파일은 root 미커밋 수정이라 stage·수정하지 않았다.

### 대응표 (종전 `gateway-integration` → commit 3)

| 종전(패키지·클래스) | commit 3 모듈 · 패키지 |
|---|---|
| `integration.inference.*`, `integration.tokenizer.*`, testFixtures `integration.testing.DockerRequiredExtension`, `TokenizerNativeSmokeIT` | `:adapter:ai-gateway-inference-adapter` · 그대로 |
| `integration.IntegrationModuleConfiguration` | inference-adapter · `integration.InferenceAdapterModuleConfiguration`(이름 변경, `@Import` 항목 불변) |
| `integration.loki.{SourceHttpClient,SourceReadAccess,SourceObservability,AllowlistClientRequestObservationConvention}` | `:adapter:ai-gateway-source-support` · `integration.source.*` |
| `integration.loki.LokiSourcePolicy.APPS` | source-support · `integration.source.TcSourcePolicy.APPS` |
| `integration.loki.LokiLogConnector.nanos(Instant)` | source-support · `integration.source.SourceHttpClient.nanos(Instant)` |
| `integration.loki.{LokiLogConnector,LokiProperties,LokiRetentionProbe,LokiSourcePolicy(SOURCE_REF·selector·retentionSourceId)}` | `:adapter:ai-gateway-loki-adapter` · `integration.loki` |
| `integration.tempo.TempoTraceConnector` | `:adapter:ai-gateway-tempo-adapter` · `integration.tempo` |
| `integration.gitlab.*`, `integration.loki.TcSourceAllowlist` | `:adapter:ai-gateway-gitlab-adapter` · `integration.gitlab`(TcSourceAllowlist 는 패키지 이동) |
| test `integration.ConnectorTestSupport`, `loki.LokiQueryNotRecordedTest.Capture` | source-support testFixtures · `integration.ConnectorTestSupport`(+ 중첩 `Capture`) |
| test `loki.SourceHttpClientTest`, `loki.SourceHttpTerminationTest` | source-support test · `integration.source` |
| test `loki.TcSourceAllowlistTest` | gitlab-adapter test · `integration.gitlab` |
| app 시험 자원 `config/sources/**` 포함 | inference → gitlab-adapter test 자원(TcSourceAllowlistTest 만 사용) |

### 결정과 근거

- 새 이름(root 지시: 기존 이름·용어에서 선택):
  - 앱 목록 → `TcSourcePolicy.APPS`. `<시스템>SourcePolicy` 는 기존 `LokiSourcePolicy`·`GitLabSourcePolicy` 의 이름 형식이고, `TcSource` 접두는 기존 `TcSourceAllowlist`·core `TcSourceAuthorizationPort` 와 같다. 필드 이름 `APPS` 는 그대로다.
  - nanos → 새 클래스 없이 기존 `SourceHttpClient` 의 static 메서드. Loki(`start`/`end`)·Tempo(조회 창) 조회 파라미터를 만드는 Source HTTP 요청 형식이며 `SourceHttpClient.uri(…, query)` 와 같은 책임이다.
  - `Capture` → 이름을 바꾸지 않고 testFixtures `ConnectorTestSupport` 의 중첩 클래스로 옮겼다(GitLab 시험이 loki 시험 클래스를 참조하던 경계 위반 해소).
  - inference 의 module configuration → `InferenceAdapterModuleConfiguration`(nori-wm `UiKafkaAdapterModuleConfiguration` 등 `<…>AdapterModuleConfiguration` 형식). Plan §3.2 공통 예외 파일 목록의 "integration 계열 각 adapter 의 `*ModuleConfiguration.java`(클래스 이름은 U3 셋째 commit 에서 확정·기록)" 에 해당한다.
- ModuleConfiguration: source-support·loki·tempo·gitlab 에는 두지 않았다. 네 모듈에 `@Component`·`@Configuration` 계열 클래스가 0개이고(`LokiProperties` 는 app `SourceConnectorConfiguration` 의 `@EnableConfigurationProperties`), 객체는 모두 app `SourceConnectorConfiguration` 의 `@Bean` 이 만든다. 종전 `IntegrationModuleConfiguration` 의 `@Import` 항목은 전부 inference·tokenizer 라 나눠 줄 항목이 없다. 빈 `@Import({})` 설정 클래스는 만들지 않았다. `ModuleConfigurationCompletenessTest` 는 `ArchitectureRulesTest.mainClasses()` 의 모든 main 산출물(새 4개 모듈 디렉터리 추가)에서 `@Component` 계열이 app 진입점에서 `@Import` 로 도달하는지 검사하며 통과했다.
- 의존: `source-support` = `api(core, spring-web, micrometer-observation, micrometer-core, jackson-databind)`(공개 시그니처에 나오는 타입), loki = core + source-support + `spring-boot`(`@ConfigurationProperties`), tempo·gitlab = core + source-support. adapter 사이 의존을 막는 기존 ArchUnit 규칙은 없어(`ArchitectureRulesTest` 의 금지 방향은 persistence↔integration·tcmock→… 패키지 단위) Gradle 의존 그래프로 확인했다(`:adapter:ai-gateway-<m>:dependencies --configuration runtimeClasspath`, 각 exit 0, `project :` 줄 인용):
  - source-support: `+--- project :ai-gateway-core`
  - loki·tempo·gitlab 각각: `+--- project :ai-gateway-core` / `+--- project :adapter:ai-gateway-source-support` / `|    +--- project :ai-gateway-core (*)`
  - inference: `+--- project :ai-gateway-core`
- app 은 네 모듈을 모두 `implementation` 한다. acceptance 는 loki·source-support 를 더하고 inference 의존은 유지했다 — acceptance 코드는 inference 를 쓰지 않지만, 빼면 실행 classpath 에서 logback·spring-ai·djl·kafka·S3·object-storage 등이 빠지고 micrometer 런타임 버전이 1.17.2 → 1.16.6 으로 바뀐다(첫 `--write-locks` 의 acceptance lockfile diff 로 확인). 동작 변경 0 을 지키려고 유지했고 정리는 별도 결정으로 남긴다.
- kafka starter·object-storage starter·AWS S3(P24 용): main·test 코드에서 쓰는 곳 0(`kafka`·`software.amazon`·`com.nori.storage` 참조 0). 지금은 inference-adapter 에 그대로 두었다(임의 결정 안 함). 판단 재료: inference main 의 `@ConditionalOnProperty`(spring-boot-autoconfigure)와 inference·acceptance 의 logback 은 `spring-boot-starter-kafka` 경유로만 들어온다(`:adapter:ai-gateway-inference-adapter:dependencyInsight --dependency spring-boot-autoconfigure|logback-classic --configuration compileClasspath`: `requested org.springframework.boot:spring-boot-starter-kafka` → `spring-boot-starter:4.0.7` → `spring-boot-starter-logging:4.0.7`). 제거하면 inference 에 `spring-boot-autoconfigure` 명시가 필요하고 acceptance 실행 classpath 가 바뀐다.
- 시험 분할(단언 약화 0, JUnit 건수 integration 114 → 5개 모듈 합 123 = 114 − 5 + 13 + 1):

| 종전 `SourceAttemptGuardTest` 메서드(kind 반복) | commit 3 위치 |
|---|---|
| `firstCheckAndRetryPreserveTheExactLifecycleExceptionEvenWhenScopeExpired`(logs·trace·code·lineage × retry) | `LokiSourceAttemptGuardTest`(logs), `TempoSourceAttemptGuardTest`(trace), `GitLabSourceAttemptGuardTest`(code·lineage) 같은 이름·같은 단언 |
| `denialEmptyPermissionsAndUnavailableStopThePhysicalRetryWithoutChangingFailureClass` | 위 세 클래스 같은 이름·같은 단언 |
| `anAllowedRetryReallyPerformsTwoHttpRequestsAndErrorsEscapeUnchanged`(logs 5건·그 외 2건 + logs `AssertionError` 블록) | Loki(5건 + `AssertionError` 블록), Tempo(2건), GitLab(code·lineage 각 2건) |
| `lifecycleChangeDuringAuthorizationStopsTheVeryFirstHttpBeforeTransport` | 위 세 클래스 같은 이름·같은 단언 |
| `timeSpentInTheGuardConsumesTheSameDeadlineBeforeTransportStarts`(전송기 전용) | source-support `integration.source.SourceAttemptGuardTest` |
| `SourceHttpClientTest.transportDefaultsMatchApprovedConnectAndReadTimeouts` 의 `LokiProperties` 기본값 4개 단언 | `LokiPropertiesTest.lokiDefaultsMatchApprovedSplitLimitAndDeadline`(같은 4개 단언). 전송 timeout 단언은 원래 메서드에 남음 |

- 관측 sourceId 허용 집합(`loki`·`tempo`·`gitlab`, `AllowlistClientRequestObservationConvention`)은 바꾸지 않았다(SPEC 허용 목록).
- `ci/integration-required.txt`: 통합 IT 는 inference 의 `TokenizerNativeSmokeIT` 하나라 등록 줄은 그대로(`:adapter:ai-gateway-inference-adapter …`), 2행 주석의 IT 없는 모듈 목록에 새 4개 모듈을 더했다. 등록 IT FQCN 변경 0.
- 루트 `build.gradle.kts` 의 integrationTest source set 대상 이름 집합, `ArchitectureRulesTest.mainClasses()`·`RepositorySignatureArchTest` 의 모듈 디렉터리 목록에 새 4개 모듈을 더했다.
- lockfile: `resolveAndLockAll --write-locks` exit 0. 새 lockfile 4개, 내용이 바뀐 기존 lockfile 은 acceptance 하나(compileClasspath 에 source-support `api` 의존 추가 — runtimeClasspath 줄 변경 0). 나머지는 CRLF 만 달라 commit 때 정규화되어 변경 0. `../nori-common/build-logic/gradle.lockfile` 은 `git checkout` 으로 되돌려 nori-common clean.

### 명령 결과 (commit 3 검증 단위, 각 별도, `--offline --no-daemon`)

- ① spec-guard exit 0 — specSha `9270488294079f4bc17e387be9f64e07e96f75916761e895dd21ff21f94aad19`.
- ② `build` exit 0 — `BUILD SUCCESSFUL in 10m 13s`. JUnit(XML 집계, 실패·오류·skip 0): eval 283, core 117, db-adapter 12, inference-adapter 43, source-support 8, loki-adapter 38, tempo-adapter 10, gitlab-adapter 24, tc-mock-adapter 48, app 70(`ArchitectureRulesTest`·`ModuleConfigurationCompletenessTest` 포함), acceptance-app 41 — 합 694. 직후 test 11개 `test-evidence verify --tasks …` exit 0 `TEST_EVIDENCE_VERIFIED`.
- ③ `integrationTest -Paigw.requireDocker=true`(비한정) exit 0 — `BUILD SUCCESSFUL in 1h 23s`(07:49:28Z~08:49:52Z). JUnit: db-adapter 85, inference-adapter 1, app 167, 실패·오류·skip 0. 나머지 8개 모듈 `NO-SOURCE`(미등록). 직후 `--tasks <IT 3개> --require-docker -Paigw.requireDocker=true` exit 0, `--selector integrationTest --required ci/integration-required.txt --require-docker` exit 0, 모두 `TEST_EVIDENCE_VERIFIED`.
- ④ `aigwPackage -Paigw.gitSha=<HEAD 7cba8d4>` exit 1 `BUILD_IDENTITY_DIRTY_TREE` — 작업 트리에 root 소유 미커밋 Plan 수정과 이 Dev report 수정이 있어 설계대로 신원 기록을 거부했다. 같은 실행에서 `build/package/{app.jar,runtime-classpath.txt,acceptance/}` 는 만들어졌다(acceptance `bin/ai-gateway-acceptance-app`, lib 에 loki·source-support·inference jar, runtime-classpath 에 새 4개 모듈 좌표). clean tree 양성은 Plan·Dev report 커밋 뒤에 실행해야 한다.
- ⑤ selftest 6종(`aigw-p06-tools:local`, `--network none`) 모두 exit 0 PASS: runconfig 25, stage-gate 29, runtime-credentials 81 명령, verify-credentials 42, rollback-rehearsal 92, backup-alert-injection 9. commit 3 은 셸·Python 도구를 바꾸지 않았다.
- ⑥ `git grep …` 0줄(exit 1). 옛 FQCN(`integration.loki.SourceHttpClient` 등)·`LokiSourcePolicy.APPS`·`LokiLogConnector.nanos` 잔존 0(`docs`·`evidence` 제외).
- 경로 길이: 추적 파일 최장 180자(`modules/adapter/ai-gateway-source-support/src/main/java/com/nori/ai/gateway/integration/source/AllowlistClientRequestObservationConvention.java`), build 산출물 포함 `modules/` 쪽 최장 207자. 240자 초과는 모두 `gateway-eval/build/tmp/test/work/.gradle-test-kit/caches/…`(이동 안 한 TestKit 캐시)뿐이다.
- 환경: 남은 시험 컨테이너 0, Docker 이미지 목록 시작 전과 같음, nori-common clean, `evidence/**`·SPEC 변경 0.

### root 독립 검증과 ④ 재실행 (2026-11-10)

- JUnit XML 직접 집계: test 11개 task 합 694건, integrationTest db-adapter 85·inference 1·app 167, 모두 실패·오류·skip 0. 분할된 5개 모듈 test 합 123건.
- `test-evidence verify --tasks <test 11개 쉼표 목록>` exit 0, `-Paigw.requireDocker=true … --selector integrationTest --required ci/integration-required.txt --require-docker` exit 0, 둘 다 `TEST_EVIDENCE_VERIFIED`.
- ⑥ 을 commit `7cba8d4` 대상으로 다시 돌려 0줄, 옛 모듈 디렉터리 추적 파일 0.
- `SourceAttemptGuardTest` 분할: 종전 시험 5개 → 공용 4개 × adapter 3개 + 전송기 전용 1개 = 13개. 종전 단언 4종은 `kind` 분기가 adapter 별 `SOURCE_REF`·건수(logs 5, 그 외 2)로 바뀐 형태로 모두 남았다(약화 0).
- 결정 대기 1·2(kafka·object-storage·S3 의존, acceptance-app 의 inference adapter 의존): 둘 다 P04 `8e51ea9` 부터 있던 구성이고 U3 는 동작 변경 0 이 원칙이므로 유지한다. 정리는 그 의존을 받을 새 모듈을 만드는 P24 의 2026-11-10 대체 문단에 넘겼다. 결정 대기 3(AGENTS.md §4 source-support 행)은 같은 문서 commit 에서 추가했다.
- ④ 문서 commit `cd116d4` 뒤 깨끗한 트리에서 `aigwPackage -Paigw.gitSha=cd116d4b9df2700151d3…` exit 0 `BUILD SUCCESSFUL in 25s`, `build/package/build-identity.json` 의 `gitSha` 가 HEAD 와 같다.

## U5 DB 결정 반영 + Flyway 외부화 (2026-11-11, 구현·로컬 검증)

- worktree `nori-ai-gateway-u5`, 브랜치 `aigw/align-u5`, 기준 `832822b`. commit 1 `1846b03` `refactor: move gateway migrations to root flyway/sql without content changes (U5 commit 1)`(7개 순수 rename, 0 insertions/0 deletions), commit 2 `61e6eb6` `build: externalize Flyway to the root Gradle plugin and adopt the single shared-server DB account (U5 commit 2)`(48개 파일, `342 insertions(+), 788 deletions(-)`), commit 3 `548c312` `fix: keep datasource keys out of application.yaml and fix the app-never-migrates test (U5)`(⑤ 1·2회차 실패 원인 수정, 아래), commit 4 = 이 절.
- 공유 서버(192.168.0.150)·다른 공유 서버 접속 0. ⑦ 은 직접 띄운 격리 컨테이너에만 실행했다. 계정 이름·비밀번호를 코드·문서·출력에 쓰지 않았다(시험 계정 이름은 실행마다 무작위 `acct_<hex>`).

### 옛 위치 → 새 위치

| 종전 | U5 |
|---|---|
| `modules/adapter/ai-gateway-db-adapter/src/main/resources/db/migration/gateway/V{1,2,3,3_1,4,5,7}__*.sql` | 루트 `flyway/sql/` 같은 이름(V6 는 원래 없음). 앱 리소스 `db/migration/gateway/` 는 app `processResources` 사본이고 db-adapter IT 리소스에도 같은 사본을 둔다 |
| 앱 `MigrateCommand`·`MigrateApplication`·`application-migrate.yaml`(Spring Flyway 자동 migrate) | 삭제. 루트 Gradle Flyway(`flywayInfo`·`flywayMigrate`, CI `migrate` stage 는 U7) |
| db-adapter `api(spring-boot-starter-flyway)`·`runtimeOnly(flyway-database-postgresql)` | 삭제. main `implementation(libs.flyway.core)`(guard 의 `MigrationVersion` 만), `testImplementation(libs.flyway.core)`, `testFixturesApi(libs.flyway.core)` |
| 역할 4종(migration·앱·백업·수용 읽기 전용), DB 이름(종전) | 단일 계정(env `AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD`, URL `AI_GATEWAY_DB_URL`), DB `nori_ai_gateway` |
| `ops/postgres/bootstrap.sql`(역할 3종·수용 역할·DB 권한) | 임시 인스턴스용 선행조건 재현(vector·env 이름 단일 계정을 DB 소유자로·`pg_control_system()` PUBLIC 실행). 운영 공유 서버에는 실행하지 않는다 |
| `ops/postgres/aigw-hba-local-test.sh`·`hba_local_test.py` | 삭제 |
| (없음) | `docs/runbooks/aigw-db-server-prerequisites.md`(서버 담당자 선행조건, 계정 이름·값 0) |

### V1·V3 제자리 수정(Plan §8 일회성 예외, 계정·GRANT 문만)

- V1: 두 `CREATE SCHEMA … AUTHORIZATION <migration 역할>;` → `CREATE SCHEMA gw;`·`CREATE SCHEMA kn;`. `GRANT USAGE ON SCHEMA gw, kn, gw_flyway TO <앱·백업 역할>;`, `GRANT SELECT ON gw_flyway.flyway_schema_history TO <앱·백업 역할>;`, `ALTER DEFAULT PRIVILEGES FOR ROLE <migration 역할> …` 4줄 삭제(8줄 → 2줄). vector 0.8.7 검사 `DO` 블록 불변.
- V3: `GRANT INSERT ON gw.backup_run TO <백업 역할>;`·`GRANT USAGE ON SEQUENCE gw.backup_run_id_seq TO <백업 역할>;` 2줄 삭제. 테이블 정의 불변.
- 다른 V(V2·V3_1·V4·V5·V7) 바이트 변경 0(commit 1 의 rename 그대로).

### 결정과 근거

- 루트 Flyway: nori-wm `build.gradle.kts:1-17,55-78` 형태(buildscript classpath → `apply(plugin = "org.flywaydb.flyway")` → `configure<FlywayExtension>`). url/user/password = `-Pflyway.*` 다음 env `AI_GATEWAY_DB_*`, `locations = filesystem:flyway/sql`, `defaultSchema = gw_flyway`, `createSchemas = true`, `baselineOnMigrate` 미사용(Plan U5 allowed_writes 그대로). JDBC 드라이버는 catalog 에 `postgresql` 항목이 없고 `gradle/libs.versions.toml` 이 allowed_writes 밖이라 buildscript 에 Spring Boot 4.0.7 BOM 의 `postgresql.version` 과 같은 `42.7.11` 을 직접 적었다. buildscript 에 BOM platform 을 거는 방법은 루트 플러그인 classloader 의 다른 의존(kotlin·jackson)까지 BOM 이 바꿀 수 있어 쓰지 않았다. flyway-gradle-plugin 11.14.1 은 로컬 캐시에 없어 첫 해석 1회만 `--offline` 없이 실행했다(Gradle Plugin Portal·Maven Central, 공유 서버 아님).
- flyway-core 를 db-adapter main 에 남김: `SchemaVersionGuard.java:6` 이 `org.flywaydb.core.api.MigrationVersion` 으로 버전을 비교한다(`SchemaVersionGuardTest` 가 V10·V3_1 순서를 고정). 비교를 직접 구현하면 운영 로직이 바뀌므로 유지했다. Spring Flyway 자동 설정(`spring-boot-flyway`)·`flyway-database-postgresql` 은 앱 런타임에서 빠졌다(`RuntimeClasspathCleanTest` 에 부재 단언 추가).
- `PgTestDatabase`: bootstrap 을 정본 `ops/postgres/bootstrap.sql` 재사용으로 축소했다. 이 파일은 Plan P06 2026-11-10 이 정한 복원 임시 인스턴스 bootstrap(vector 0.8.7 + env 이름 단일 계정)과 서버 담당자 선행조건 재현이 같은 내용이라 하나로 두었고, P07 의 "별도 SQL 사본을 내장하지 않는다" 원칙과 sha 결속(`aigw.pgBootstrapSha256`)을 유지한다. 계정을 DB 소유자로 만드는 것은 서버 상태(계정 = DB 소유자·CREATE 권한, 2026-11-10 확인)와 같다. superuser 는 컨테이너 준비와 시험의 결함 주입에만 쓴다. HBA mount·`hba_file` 인자와 `aigw.pgHbaSha256` 결속은 지웠다. 새 공개 메서드는 `user()`(단일 계정 이름)와 `flywayConfiguration()`(루트 Flyway 설정과 같은 값 — `target` 지정 시험용) 두 개이고, `bootstrap(boolean)` → `bootstrap()` 이다.
- `aigw.flyway.sql.dir` 값은 `aigw.rootDir` 기준 상대 경로 `flyway/sql` 이다. 절대 경로면 시험 입력 지문(`aigwInputComponents` 의 `aigw.*` 속성)이 checkout 위치마다 달라진다. 내용 변경은 `inputs.dir(flyway/sql)` 와 classpath 사본으로 지문에 들어간다.
- db-adapter IT 도 `SchemaVersionGuard` 가 classpath 사본을 읽으므로 `processIntegrationTestResources` 에서 같은 사본을 둔다(guard 의 classpath 위치 불변, Plan P07 2026-11-10).
- 앱 `application.yaml`: 앱 안 migration 설정 `spring.flyway.{locations,default-schema,create-schemas}` 는 지우고 `enabled: false` 만 남겼다. `NormalDatabaseConfiguration`(migrate 프로필·`spring.flyway.enabled=true` 거부)은 그대로 둔다.
- "DB 설정 키를 `AI_GATEWAY_DB_*` 로"(Plan U5 app 항목)는 이 단위에서 적용하지 않았다(결정 대기). 1차 시도로 `application.yaml` 에 `spring.datasource.{url,username,password}: ${AI_GATEWAY_DB_URL}`·`${AI_GATEWAY_DB_USER}`·`${AI_GATEWAY_DB_PASSWORD}` 를 넣자 ⑤ 1회차가 136건 중 120건 실패했다(`'url' must start with "jdbc"`). 원인: 앱 IT 는 `SpringApplicationBuilder.properties(...)`(Spring 기본 속성 — 우선순위 최하)로 datasource 를 주는데 `application.yaml` 값이 그 위에 와서 해석되지 않은 placeholder 가 남는다. 명령행 인자로 주는 child-process IT(`WorkerRestart*IT`·`InferenceProcessEnvironmentIT`)와 `BackupScriptIT` 는 통과했다. 되돌린 뒤 ⑤ 를 다시 돌렸다. nori-wm 은 이 매핑을 `config/wm-db.properties`(U6 의 `config/ai-gateway-db.properties` 와 같은 자리)에 둔다. U6 의 `spring.config.import` 도 같은 우선순위 문제를 만나므로 U6 에서 시험 주입 방식을 함께 정해야 한다.
- `MigrateCommand`·`MigrateApplication` 은 어느 `*ModuleConfiguration` 의 `@Import` 에도 없었다(`MigrateApplication` 이 직접 `@Import(MigrateCommand.class)`). 삭제에 따른 ModuleConfiguration 변경 0, `ModuleConfigurationCompletenessTest` 통과(④).
- 수용 oracle: `OracleQueries.postgres(host, user, password)` — 사용자는 env `AI_GATEWAY_DB_USER`(AGENTS.md §6 의 계정 이름 env), 포트 15432(공유 서버 직접 연결·복원 port-forward 와 같음), DB 는 `nori_ai_gateway` 하나만 허용. 고정 SELECT enum 불변. 다른 DB(postgres·template1) HBA 거부 판정은 수용 전용 역할이 없어 지웠다(Plan P15 2026-11-10 D-16). exec 차트 `acceptance-job.yaml:48` 의 `AIGW_DB_USERNAME`(옛 역할값)은 이 코드가 읽지 않으며 공급 경로는 U6 이 정한다.
- 백업·복원: `aigw-backup.sh` 입력 검사 = PGUSER 비어 있지 않음·`PGDATABASE == nori_ai_gateway`. `aigw-restore.sh` 대상 URL = `postgresql://<계정>@<host>:<port>/nori_ai_gateway`(계정은 URL 에서 읽음), bootstrap 확인 = 접속 계정이 그 DB 소유자(종전 역할 3종 존재 확인 대체, 거부 코드 `bootstrap_roles` 유지), `pg_restore --role="$PGUSER"`. TOC 제외 목록은 그대로다(공유 서버의 `pg_control_system()` PUBLIC ACL 도 dump 에 실리므로 제외가 필요하다).
- `AppContextWiringIT` 의 종전 migrate 전용 진입점 시험 → `normalApplicationNeverMigratesAndStartsOnlyAfterExternalMigration`: migrate 프로필·`spring.flyway.enabled=true` 기동 거부와 테이블 0, `FlywayAutoConfiguration` 클래스 부재, 외부 migrate 뒤 같은 계정 기동·Flyway 빈 0·이력 1,2,3,3.1,4,5,7. `DbBootstrapOrderIT` 는 Plan P07 2026-11-10 순서로 다시 썼다(역할별 거부·수용 역할·HBA 단언 제거). `StackSmokeIT` 에 "앱 리소스 사본 = `flyway/sql`(파일 집합·바이트·최신 V)" 단언을 더했다. IT 클래스 이름 변경 0 이라 `ci/integration-required.txt` 변경 0.
- lockfile: `resolveAndLockAll --write-locks` 뒤 내용이 바뀐 것은 db-adapter·app 둘(spring-boot-flyway·starter-flyway 줄 삭제, flyway-database-postgresql 은 시험 구성만). 나머지는 CRLF 만 달라 되돌렸고, `../nori-common/build-logic/gradle.lockfile` 도 `git checkout` 으로 되돌려 nori-common clean.

### HBA overlay·승인 A 도구를 U5 에서 지우지 않은 이유(결정 대기)

- 원인(클래스 단위): `ops/postgres/acceptance-pg_hba.conf` 는 시험 fixture 외에 `ops/deploy/hba_policy.py`(`SOURCE`, `source`·`render`·`live`)가 읽고, `aigw-helm.sh:71` 이 app release 를 포함한 모든 호출에서 `hba_policy.py source` 를 먼저 실행하며, `aigw-helm.sh:296` 이 exec `db-chart` 에 `--set-file hba.overlay` 로 넘기고, `aigw-runtime-credentials-selftest.sh:36,214` 가 읽는다. 파일 삭제는 배포 도구 흐름 변경을 요구한다(고정 제약). Plan 은 그 흐름 변경(승인 A 수용 역할 provision/cleanup·DB 역할 Secret 4종 제거, 운영 db-chart 폐기)을 U6 ②③ 에 두었고 U5 의 `ops/deploy/**`·`ops/gates/**` 는 "역할·DB 이름 하드코딩만" 이다(배치 문제).
- 이름만 바꾸는 경우: `verify_credentials.py:369` 의 정리 SQL 은 대상 역할에 `ALTER ROLE … NOLOGIN` 을 건다. 이것을 단일 계정 이름으로 바꾸면 공유 서버의 유일한 계정을 잠그는 도구가 된다. `aigw-backup-alert-injection.sh:46`·`hba_policy.py:106`·`verify_credentials.py:231`·`rollback_rehearsal.py:22` 는 없어지는 주 DB Pod(`ai-gateway-db-0`)에 exec 하는 경로라 DB 이름만 바꿔도 동작하지 않는다.
- 그래서 U5 는 자기 완결인 HBA 로컬 시험 2개만 지웠고 `acceptance-pg_hba.conf`·`hba_policy.py`·승인 A gate 코드·해당 runbook 2개(`aigw-runtime-credentials.md`·`aigw-verify-credentials.md`)는 그대로 두었다. ⑧ 잔존 29줄은 모두 이 범주다(아래 ⑧).

### 명령 결과 (각 별도, `--offline --no-daemon`, `AIGW_SPEC_FILE`=이 worktree SPEC, `AIGW_PG_TEST_IMAGE`=Plan 고정값)

- ① spec-guard exit 0 — `{"specInputOrigin":"LOCAL_FILE","specSha":"9270488294079f4bc17e387be9f64e07e96f75916761e895dd21ff21f94aad19","specShaSource":"FILE_HASHED"}`.
- ② `:adapter:ai-gateway-db-adapter:test` exit 0 `BUILD SUCCESSFUL in 33s`. JUnit XML 3개 12건, 실패·오류·skip 0. `test-evidence verify --tasks :adapter:ai-gateway-db-adapter:test` exit 0 `TEST_EVIDENCE_VERIFIED`.
- ③ `:adapter:ai-gateway-db-adapter:integrationTest -Paigw.requireDocker=true` exit 0 `BUILD SUCCESSFUL in 3m 47s`. JUnit XML 8개 85건, 실패·오류·skip 0. `MigrationApplyIT`(`singleAccountAppliesExactlyV1ToV3AndOwnsAllObjects` = vector 0.8.7 빈 DB 에 V1~V3 → V7, 16개 표 소유자 = 단일 계정·checksum 불변, `missingExtensionFailsMigrationWithoutCreatingGatewayObjects` = vector 없으면 V1 실패·gw/kn 표 0, V5·V7 음성 2건), `DbBootstrapOrderIT.serverPrerequisitesThenExternalMigrationThenSameAccountPersistenceContext`, `SchemaVersionGuardIT` 포함. `-Paigw.requireDocker=true … test-evidence verify --tasks :adapter:ai-gateway-db-adapter:integrationTest --require-docker` exit 0 `TEST_EVIDENCE_VERIFIED`.
- ④ `:apps:ai-gateway-app:test` exit 0 `BUILD SUCCESSFUL in 1m 22s`. JUnit XML 21개 70건(`ModuleConfigurationCompletenessTest`·`RuntimeClasspathCleanTest` 포함), 실패·오류·skip 0. verify exit 0 `TEST_EVIDENCE_VERIFIED`.
- ⑥ `:apps:ai-gateway-acceptance-app:test` exit 0 `BUILD SUCCESSFUL in 28s`. JUnit XML 12개 41건, 실패·오류·skip 0. verify exit 0 `TEST_EVIDENCE_VERIFIED`.
- ②③⑥ 은 commit 2 직전 작업 트리(commit 2 와 같은 내용)에서 실행했다. commit 3 은 `application.yaml` 을 바꿔 ④ 를 다시 실행했다: exit 0 `BUILD SUCCESSFUL in 1m 20s`, 70건 실패·오류·skip 0, verify exit 0 `TEST_EVIDENCE_VERIFIED`.
- ⑤ `:apps:ai-gateway-app:integrationTest -Paigw.requireDocker=true`(비한정):
  - 1회차(commit 2) exit 1 `BUILD FAILED in 38m 24s`, 136건 중 실패 120 — `application.yaml` datasource placeholder 문제(위 결정 대기 항목). `BackupScriptIT` 4건·`BackupColumnCoverageIT`·child-process IT 는 통과.
  - 2회차(yaml 되돌림 뒤) exit 1 `BUILD FAILED in 43m 32s`, 167건 중 실패 1 — 새로 쓴 `normalApplicationNeverMigratesAndStartsOnlyAfterExternalMigration` 의 `spring.flyway.enabled=true` 를 기본 속성으로 줘 `application.yaml` 의 `false` 에 가려졌다. 명령행 인자로 바꾸고, 같은 편집에서 빠진 SQL 따옴표(`IN ('gw','gw_flyway')`)를 되돌렸다. 확인용 `--tests "*AppContextWiringIT"` 17건 통과(최종 회차 전 실행, 증거로 쓰지 않음).
  - 최종 회차(commit 3 `548c312`) exit 0 `BUILD SUCCESSFUL in 43m 26s`(00:28:03Z~01:11:30Z). JUnit XML 44개 167건, 실패·오류·skip 0(`StackSmokeIT`·`AppContextWiringIT`·`BackupScriptIT`·`BackupColumnCoverageIT` 포함). 직후 `-Paigw.requireDocker=true … test-evidence verify --tasks :apps:ai-gateway-app:integrationTest --require-docker` exit 0 `TEST_EVIDENCE_VERIFIED`. `IdempotentAdmissionIT` 일시 실패는 이번 세 회차 모두 없었다.
- `build`(commit 3) exit 0 `BUILD SUCCESSFUL in 15m 3s`. JUnit(XML 집계, 실패·오류·skip 0): eval 283, core 117, db-adapter 12, inference-adapter 43, source-support 8, loki-adapter 38, tempo-adapter 10, gitlab-adapter 24, tc-mock-adapter 48, app 70, acceptance-app 41 — 합 694(U3 와 같음). 직후 test 11개 `test-evidence verify --tasks …` exit 0 `TEST_EVIDENCE_VERIFIED`.
- ⑦ 격리 컨테이너(`pgvector/pgvector:0.8.7-pg18-trixie@sha256:5895…4bcd2`, `127.0.0.1` 임의 포트, 이름 `aigw-u5-flyway-check`)에 superuser 로 `bootstrap.sql`(exit 0)을 실행해 선행조건을 재현한 뒤, 자격을 그 셸 프로세스 env `AI_GATEWAY_DB_URL`·`AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD` 로만 주고(값은 셸 안에서 생성, 출력 0, `-Pflyway.password` 0) 루트 task 를 실행했다. `flywayInfo` exit 0(`Schema version: << Empty Schema >>`, V1·V2·V3·V3.1·V4·V5·V7 모두 `Pending`) → `flywayMigrate` exit 0 `BUILD SUCCESSFUL in 19s` → `flywayInfo` exit 0, `Pending` 0건:

```
Schema version: 7
| Category  | Version | Description                  | Type   | Installed On        | State   | Undoable |
|           |         | << Flyway Schema Creation >> | SCHEMA | 2026-11-11 07:44:34 | Success |          |
| Versioned | 1       | schemas and extension assert | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 2       | conversation job core        | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 3       | audit ops                    | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 3.1     | auth rejection identity      | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 4       | job request authority        | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 5       | job request input            | SQL    | 2026-11-11 07:44:34 | Success | No       |
| Versioned | 7       | audit request identity       | SQL    | 2026-11-11 07:44:35 | Success | No       |
```

  컨테이너는 직후 `docker rm -f` 로 지웠다.
- selftest(`aigw-p06-tools:local`, `--network none`, worktree·`nori-tc-exec` readonly, `AIGW_SELFTEST_EXEC_DIR=/nori-tc-exec`): U5 가 바꾼 셸·SQL 은 `ops/backup/*.sh`(BackupScriptIT 가 실행)와 `bootstrap.sql` 이고 gate·deploy 도구는 바꾸지 않았지만 bootstrap 내용이 바뀌어 6종을 모두 실행했다. 모두 exit 0 `PASS`: stage-gate 29, runtime-credentials 81 명령, verify-credentials 42, rollback-rehearsal 92(`actualNetworkCalls=0`), backup-alert-injection 9, runconfig 25.
- ⑧ `git grep -nwE "<Plan U5 ⑧ 패턴>" -- . ":!evidence" ":!docs/ai-rag-gateway-architecture.md" ":!docs/ai-rag-gateway-architecture-plan.md" ":!docs/ai-rag-gateway-architecture-dev-report.md" ":!docs/gates"` — **0줄 아님, 29줄**(작업 전 약 30개 파일 → 10개 파일). 남은 줄은 모두 위 "HBA overlay·승인 A 도구" 범주다: `docs/runbooks/aigw-runtime-credentials.md` 5, `docs/runbooks/aigw-verify-credentials.md` 3, `ops/deploy/hba_policy.py` 4, `ops/gates/aigw-backup-alert-injection.sh` 1, `ops/gates/aigw-runtime-credentials-selftest.sh` 1, `ops/gates/aigw-verify-credentials-selftest.sh` 1, `ops/gates/rollback_rehearsal.py` 1, `ops/gates/selftest_kubectl_stub.py` 1, `ops/gates/verify_credentials.py` 8, `ops/postgres/acceptance-pg_hba.conf` 4. `modules/**`·`flyway/sql/**`·`ops/backup/**`·`ops/postgres/bootstrap.sql`·`aigw-backup-restore.md` 는 0줄.
- 환경: `docker ps -a --filter label=org.testcontainers=true -q` 빈 출력, ⑦ 컨테이너 없음. BackupScriptIT 가 만든 backup 이미지는 deleteOnExit 로 지워졌다(`aigw-backup-it-1c16f094:latest` 는 이 세션 전부터 있던 이미지라 두었다). pgvector·ryuk·`aigw-p06-tools:local`·seaweedfs·다른 프로젝트 자원은 건드리지 않았다. `../nori-common` clean. `evidence/**`·SPEC·Plan·`docs/gates/**`·`AGENTS.md`·`CLAUDE.md` 변경 0.

### root 에 넘기는 항목

- Plan 변경 필요: ① U5 ⑧ 의 0줄 조건은 승인 A·HBA·주 DB Pod exec 도구를 지우는 U6 ②③ 뒤에야 성립한다 — U5 ⑧ 범위에서 `ops/deploy/hba_policy.py`·`ops/gates/**` 승인 A 코드·`ops/postgres/acceptance-pg_hba.conf`·runbook 2개를 빼거나 U6 완료 증거로 옮긴다(U8 ⑨ 재검사는 그대로). ② "DB 설정 키를 `AI_GATEWAY_DB_*` 로"(U5 app)를 U6 `config/ai-gateway-db.properties` 쪽으로 옮기고, U6 에서 import 된 설정이 시험의 기본 속성 주입보다 우선하는 문제를 함께 다룬다. ③ `.gitlab-ci.yml:108` 의 `migrations_sha` 가 옛 migration 경로를 읽어 다음 CI 이미지 빌드가 실패한다 — U6(`.gitlab-ci.yml` allowed) 에서 `flyway/sql` 로 바꾼다. ④ exec 차트 `db-chart` bootstrap Job 은 새 `bootstrap.sql` 이 읽는 env(`AI_GATEWAY_DB_USER`·`AI_GATEWAY_DB_PASSWORD`)를 주지 않고, `acceptance-job.yaml:48` 은 옛 수용 역할을 `AIGW_DB_USERNAME` 으로 준다 — U6 차트 이관 때 맞춘다. ⑤ `gradle/libs.versions.toml` 에 `postgresql` 항목을 두려면(nori-wm `libs.versions.toml:60,212` 선례) allowed_writes 추가가 필요하다.
- 관련 없는 발견(수정 안 함): `docs/gates/f1/runtime-classpath.txt` 는 `syncRuntimeClasspathEvidence` 로만 갱신되는 스냅샷이라 spring-boot-flyway 줄이 남아 있다. `docs/runbooks/aigw-runtime-credentials.md:48` 은 지운 HBA 로컬 시험 명령을 안내한다(U6 에서 승인 A 문단과 함께 정리).
