# P04 G-STACK — 차단 보고 (2026-11-05)

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
- 올바른 파일 입력 지원에는 gateway-eval CLI/추출기와 관련 회귀 시험 수정이 필요하지만 승인 Plan P04의 allowed_writes(438행)는 gateway-eval을 포함하지 않고, 442행은 --runtime-classpath-file 전달을 필수로 요구함. 이는 사용자의 최신 지시에서 새로 명시한 금지가 아니라 승인 Plan의 수정 허용 범위·필수 인터페이스 충돌이며, 기존 사용자 SDD 9.4의 충돌 시 중단 규칙에 따라 구현을 중단함.

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
SPEC/Plan 대비 차이: 현행 eval에 필수 파일 인자 기능이 없고, 승인 Plan P04의 allowed_writes(438행)는 gateway-eval을 포함하지 않으나 442행은 --runtime-classpath-file 전달을 요구하여 수정 허용 범위·필수 인터페이스가 충돌함. 기존 사용자 SDD 9.4의 충돌 시 중단 규칙에 따라 P04 구현을 시작하지 못함. 승인된 계약·정책·버전은 변경하지 않음.
영향 범위: 보고 문서만. P04 완료·G-STACK PASS·P05 착수 근거로 사용할 수 없음.

외부 일차 출처:
- https://github.com/spring-projects/spring-ai/releases/tag/v2.0.2
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.7/spring-boot-dependencies-4.0.7.pom
- https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/2.0.2/spring-ai-bom-2.0.2.pom
- https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.0.2/spring-ai-openai-2.0.2.pom
- https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-bom/2.0.3/testcontainers-bom-2.0.3.pom


## 2026-11-05 P04 재개 — 승인된 범위 보정 후 최신 검증 결과

최신 상태: **구현 및 로컬 기술 검사 통과 / clean git checkout 검증·부모 독립 검증 대기 / P04 최종 완료 선언 없음 / P05 미착수**. 위 BLOCKED 기록은 당시 이력으로 보존한다. 현재 starter 인증 공급 차단은 해소되었다.

- 승인 Plan 변경은 P04 allowed_writes(438행) 한 줄뿐이다. CLI·추출기 2파일과 직접 관련 시험 2파일·TestKitSupport만 추가했다. SPEC·baseline·approval6 및 보호 대상 483개는 불변이다.
- 실제 호출: Args.only → EvalCli leaf-extract → 호환 overload를 가진 OtrLeafExtractor → summary/LEAF buildIdentity. 명시 파일은 일반·읽기 가능 파일인지 확인하고 Json.bytes/sha로 계산한다. 누락·디렉터리·읽기 실패는 환경 SHA로 대체하지 않으며 이전 출력도 제거한다. 기존 무파일/F0 경로와 오류 코드는 보존했다.
- 7모듈, Spring 없는 core, 명시적 @Import, CoreBeansConfiguration(현재 core 빈 0), 분리된 integrationTest, 실제 런타임 artifact 입력/출력 선언 producer와 모든 Test finalizer 연결을 구현했다. build/check에는 integrationTest를 연결하지 않았다.
- writeRuntimeClasspath의 실제 출력 SHA=`29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`. docs 사본은 syncRuntimeClasspathEvidence로만 생성했고 현재 두 파일 바이트가 일치한다. 제품 런타임에 Eval/JUnit/Testcontainers/test-fixtures가 없으며 Mock 산출물의 시험 보조 패키지도 검사한다.

### 인증·고정 스택

기존 `glab config get token --host gitlab.com` 및 비대화형 `git credential fill` 값을 메모리에서만 조회했다. 두 출처 모두 같은 gitlab.com의 사용자 API와 정확한 starter metadata에 Bearer HTTP 200, Private-Token HTTP 401이었다. git credential Basic metadata도 401이었다. redirect를 금지했고 응답 본문·프로필·비밀값은 출력/저장하지 않았다. Gradle은 기존 com.nori 좌표·프로젝트 83704008에 repository 범위를 한정하고 자식 환경의 GITLAB_TOKEN/ORG_GRADLE_PROJECT_gitlabToken, GITLAB_AUTH_HEADER=Authorization을 사용한다. 이후 인증 실행은 --no-daemon으로 수행했다. 새 토큰·권한·전역 설정·대체 publish/composite는 없다.

[GitLab 공식 Maven 인증](https://docs.gitlab.com/user/packages/maven_repository/)은 OAuth에 Authorization Bearer를 명시한다. 이번 401을 package 권한 부재로 해석하지 않았다.

Boot **4.0.7**, Spring AI **2.0.2**, Testcontainers **2.0.3**을 유지했다. Boot BOM이 제안하는 TC 2.0.5는 시험 enforcedPlatform으로 2.0.3에 고정했다. 실제 원격 starter JAR 생성·기동을 확인했고 Kafka는 Boot 4용 starter의 자동구성으로 ConsumerFactory 빈 부재를 해결했다. 기타 pin은 catalog와 scratch source-index/artifact-index/technical-pins에 근거를 기록했다.

### Spring AI 2.0.2 API ⓐ~ⓕ

| 항목 | 확인 결과 |
|---|---|
| ⓐ chat | OpenAiChatModel.builder().options(OpenAiChatOptions.builder()…build()).build(); model, temperature, maxTokens, baseUrl, apiKey, maxRetries, timeout 실재. dummy loopback·dummy key·재시도 0으로 실제 생성. 호출 없음. |
| ⓑ embedding | OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder()…build()).build(); AbstractOpenAiOptions에서 baseUrl/apiKey/model/maxRetries/timeout 상속. 동일 dummy 조건 생성. embed 호출 없음. |
| ⓒ Spring retry | 해당 모델 builder·OpenAiSetup 경로에 RetryTemplate wrapper 없음. SDK maxRetries(0) 구성 API를 검사. |
| ⓓ SDK/HTTP/log | SDK 4.80.0. SpringAiOpenAiHttpClient는 connection recovery=true이고 공개 boolean 해제 setter가 없다. 공식 SDK OkHttpClient는 retryOnConnectionFailure(false). ClientOptions(maxRetries=0, logLevel=OFF)와 stock transport를 OpenAIClientImpl에 넣고 chat sync/async·embedding client에 명시 주입하는 경로를 실제 생성했다. SDK LoggingHttpClient는 logger 이름 없이 System.err를 직접 쓰므로 SLF4J OFF만으로 차단한다고 주장하지 않는다. Spring logger는 org.springframework.ai.openai.OpenAiChatModel/OpenAiEmbeddingModel/setup.OpenAiSetup이며 chat의 빈 choice WARN은 prompt를 포함한다. 운영 로그 차단·실제 요청 횟수 검증은 P14/P19 범위다. |
| ⓔ connect timeout | 단순 timeout(Duration)은 request timeout이다. SDK Timeout.builder().connect(1s).request(2s)를 stock transport와 ClientOptions에 넣는 경로를 생성·assert했다. Spring HTTP customizer도 timeout(Timeout)을 제공한다. |
| ⓕ 기본값/환경 | OpenAiSetup는 미지정 시 OPENAI_BASE_URL, AZURE_OPENAI_BASE_URL 및 OPENAI_API_KEY, MICROSOFT_FOUNDRY_API_KEY, GITHUB_TOKEN을 확인하고 기본 api.openai.com/v1을 선택할 수 있다. SDK fromEnv는 추가 OPENAI_ADMIN_KEY, OPENAI_ORG_ID, OPENAI_PROJECT_ID, OPENAI_WEBHOOK_SECRET, OPENAI_CUSTOM_HEADERS, AZURE_OPENAI_KEY 및 openai.* 속성을 읽는 별도 경로다. 수동 ClientOptions.builder도 OPENAI_LOG를 기본 참조한다. 기본 모델 생성 시험은 endpoint/key를 명시했고, SDK 주입 대안 시험은 sync/async client와 LogLevel.OFF도 명시했다. fromEnv와 실제 API는 호출하지 않았다. |

1차 근거: [chat tagged source](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java), [embedding tagged source](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiEmbeddingModel.java), [setup](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/setup/OpenAiSetup.java), [Spring HTTP transport](https://github.com/spring-projects/spring-ai/blob/v2.0.2/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/http/okhttp/SpringAiOpenAiHttpClient.java), [SDK stock transport](https://github.com/openai/openai-java/blob/v4.80.0/openai-java-client-okhttp/src/main/kotlin/com/openai/client/okhttp/OkHttpClient.kt), [SDK logging](https://github.com/openai/openai-java/blob/v4.80.0/openai-java-core/src/main/kotlin/com/openai/core/http/LoggingHttpClient.kt).

### OTLP·DJL

Boot 4.0.7의 별도 spring-boot-opentelemetry 모듈은 OtlpLoggingAutoConfiguration을 제공하며 조건 클래스는 OpenTelemetry/SdkLoggerProvider, properties prefix는 management.opentelemetry.logging.export.otlp이다([tagged source](https://github.com/spring-projects/spring-boot/blob/v4.0.7/module/spring-boot-opentelemetry/src/main/java/org/springframework/boot/opentelemetry/autoconfigure/logging/otlp/OtlpLoggingAutoConfiguration.java)). 현재 제품 classpath는 그 모듈을 추가하지 않고 Plan의 명시 구성 요소 경로인 SdkLoggerProvider·OtlpHttpLogRecordExporter·OpenTelemetryAppender 클래스 로드를 실제 검사했다. OTel SDK/exporter=1.55.0(Boot BOM), logback appender=2.33.1-alpha. 실제 collector 전송·운영 로깅 정책은 구현하지 않았다.

DJL tokenizers=0.40.0, JAR SHA=`569b1fa597e362ac11cd527e5deacd52cd827c8f5133a573572e2cd7051c5768`. 배포 POM 선언 라이선스는 Apache-2.0([POM](https://repo.maven.apache.org/maven2/ai/djl/huggingface/tokenizers/0.40.0/tokenizers-0.40.0.pom)). 아래는 그 JAR에 포함된 native 및 보조 DLL의 실제 SHA이며 개별 제3자 runtime DLL의 라이선스를 DJL POM 라이선스로 새로 정의하지 않는다.

| 리소스 | SHA-256 |
|---|---|
| `native/lib/linux-aarch64/cpu/libtokenizers.so` | `f78a889b3305953a5048c5809bb477d4feb3655456a26acb49a0326699672e39` |
| `native/lib/linux-x86_64/cpu/libtokenizers.so` | `edf58c92155e0fe4fae5231e782a1f02deeb5881de7bbcd1195f6e25009da90a` |
| `native/lib/osx-aarch64/cpu/libtokenizers.dylib` | `cca1c15bcbfa47b1c942e9d395ea8353161e5ed709f74826ad5faebcd05544ab` |
| `native/lib/win-x86_64/cpu/libgcc_s_seh-1.dll` | `32ce10abc802edc83c36d62e0e9f9389bab0c4bfeacf01cc98726da81094b80a` |
| `native/lib/win-x86_64/cpu/libstdc++-6.dll` | `a0b8f623f0da655a30873ef7d6b6be0e6942c34535abcaecce142fa0ab1cef4f` |
| `native/lib/win-x86_64/cpu/libwinpthread-1.dll` | `567ca7ae7ecdfb8799b28c41ac3d1a2cc46f4ee2e72c511f027c44250498e893` |
| `native/lib/win-x86_64/cpu/tokenizers.dll` | `7a13309eaeea059aa7a796ed4ab4727cfe0affcafb7b8171cf887cf7b07fea57` |

기존 P02 pin tokenizer.json의 실제 SHA를 확인하고 사본을 사용했다. ai.djl.offline=true, 새 임시 DJL cache, CPU flavor에서 Windows x86_64 자식 JVM과 eclipse-temurin:21-jre-jammy Linux amd64 컨테이너가 각각 load+encode 1회 성공했다. 컨테이너 networkMode=none 및 exit 0을 assert했다. native는 빌드 의존 JAR 내부에서 추출했으며 offline 시험 중 다운로드는 없다. 이미지 digest·OS/arch·glibc 2.35 실행 증거는 scratch/docker-evidence.json에 있다.

### 실행 명령·실제 결과

모든 아래 Gradle 호출의 환경은 AIGW_SPEC_FILE=C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md이다. `EXECUTED`와 `UP-TO-DATE`를 구분한다. 재사용은 원래 runId를 유지하며 새 실행으로 세지 않는다. 실패 시도도 scratch/result.json의 commands 및 각 log/exit에 보존했다. 첫 실패 스택 시험(3개 중 1개 실패)은 ConsumerFactory 부재였고, 그 직후 verifier exit 0은 증거 결속 확인일 뿐 시험 PASS가 아니었다. 수정 후 3/3을 다시 실행해 통과했다.

| scratch label | exit | XML/OTR·시험 수 |
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

정확한 실행 인자(각각 별도 프로세스, scratch JSON에는 argv 배열도 보존):

- `spec-guard`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:run' '--args=spec-guard --spec C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md --baseline docs/gates/spec-baseline.md' '--console=plain'`
- `focused`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:test' '--tests' '*RuntimeClasspathFileTest' '--tests' '*LeafReportRoundTripFunctionalTest' '--tests' '*TestEvidenceVerifyTest' '--console=plain'`
- `focused-verify-final`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\focused-filter.init.gradle.kts' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-eval:test' '--console=plain'`
- `eval-regression`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:test' '--console=plain'`
- `eval-regression-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-eval:test' '--console=plain'`
- `focused-p04`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:test' '--tests' '*RuntimeClasspathFileTest' '--tests' '*LeafReportRoundTripFunctionalTest' '--tests' '*TestEvidenceVerifyTest' '--tests' '*RuntimeClasspathFinalizerFunctionalTest' '--no-daemon' '--console=plain'`
- `focused-p04-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\focused-filter.init.gradle.kts' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-eval:test' '--no-daemon' '--console=plain'`
- `p04-clean`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' 'clean' '--no-daemon' '--console=plain'`
- `p04-build`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' 'build' '--no-daemon' '--console=plain'`
- `p04-build-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test' '--no-daemon' '--console=plain'`
- `p04-stack-4`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:integrationTest' '-Paigw.requireDocker=true' '--tests' '*StackSmokeIT' '--tests' '*AppContextWiringIT' '--no-daemon' '--console=plain'`
- `p04-stack-4-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\stack-filter.init.gradle.kts' '-Paigw.requireDocker=true' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:integrationTest --require-docker' '--no-daemon' '--console=plain'`
- `p04-tokenizer`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-integration:integrationTest' '-Paigw.requireDocker=true' '--tests' '*TokenizerNativeSmokeIT' '--no-daemon' '--console=plain'`
- `p04-tokenizer-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\tokenizer-filter.init.gradle.kts' '-Paigw.requireDocker=true' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-integration:integrationTest --require-docker' '--no-daemon' '--console=plain'`
- `p04-runtime`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:test' '--tests' '*RuntimeClasspathCleanTest' '--no-daemon' '--console=plain'`
- `p04-runtime-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' '-I' 'C:\Users\DongGeon\AppData\Local\Temp\claude\468c18f2647c2504\2a367916-708b-4d0e-8a9d-62501d362a92\scratchpad\p04-resume\runtime-filter.init.gradle.kts' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:test' '--no-daemon' '--console=plain'`
- `p04-sync`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-app:syncRuntimeClasspathEvidence' '--no-daemon' '--console=plain'`
- `p04-final-build`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' 'build' '--no-daemon' '--console=plain'`
- `p04-final-build-verify`: `& 'C:\workspace_nori\nori-ai-gateway\gradlew.bat' '-p' 'C:\workspace_nori\nori-ai-gateway' ':gateway-eval:run' '--args=test-evidence verify --tasks :gateway-app:test,:gateway-eval:test' '--no-daemon' '--console=plain'`

최초 clean 후 build는 253개(앱 4+Eval 249) 실제 통과했고 producer가 앱 test보다 먼저 실행됐다. 이후 Kafka 자동구성·runtime 시험 보강을 반영한 최종 build도 재검증했다. 기존 untracked 진단 파일이 있으므로 clean git checkout이라고 주장하지 않는다. clean checkout build 및 부모의 독립 대조가 남아 있어 P04 최종 완료·P05 착수로 처리하지 않는다. 외부 인증 공급 필요사항은 현재 없다.

## 2026-11-05 P04 root-review correction — 현재 로컬 결론

- `GatewayAppModuleConfiguration`을 승인 Plan 정본인 `app/config` 경로와 package로 옮기고 앱·시험 import를 맞췄다.
- 앱 `integrationTestImplementation`에만 기존 pin Nimbus 10.5, protobuf 4.33.2, networknt 3.0.9를 연결했다. 기존 StackSmokeIT에서 Boot 컨텍스트와 같은 JVM의 실제 CodeSource JAR 파일명을 assert하고, 더미 HS256 서명/정상·오서명 검증, protobuf StringValue 직렬화 왕복, Draft 2020-12 schema의 정상·타입 오류 검증을 실행했다. 제품 인증·schema 기능과 제품 의존성은 추가하지 않았다.
- core 분기 guard는 AST의 조건식·case label과 명시/와일드카드 static import를 함께 검사한다. TC/WM/HRM 각각의 static import·qualified·문자열·switch/switch expression 음성 fixture 및 주석·데이터·enum 양성 fixture를 추가했다.
- 루트 finalizer가 실제 root의 `git rev-parse HEAD`를 자식 Eval 환경으로 공급한다. 잘못된 AIGW_GIT_SHA를 실제 Git 신원으로 대체하는 TestKit 회귀를 추가했다. 비 Git/no-app 동작과 extractor 정책은 유지했고 전체 Eval 회귀로 확인했다.
- producer artifacts를 `@InputFiles` + `@PathSensitive(NAME_ONLY)`로 선언하여 원시 JAR SHA 출력과 입력 추적을 일치시켰다. 기존 의존·출력 계약은 유지했다. 원시 SHA를 쓰는 task에 ZIP 타임스탬프·순서를 무시하는 `@Classpath`는 맞지 않는다([Gradle 9.2.1 공식 API](https://docs.gradle.org/9.2.1/javadoc/org/gradle/api/tasks/Classpath.html)).

추가 API는 Maven Central의 해당 버전 source JAR에서 확인했다: Nimbus SignedJWT/JWTClaimsSet, JWSObject sign/verify, MACSigner/MACVerifier; protobuf StringValue builder/toByteArray/parseFrom; networknt SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema와 Schema.validate(String, InputFormat.JSON). 실제 로드 JAR는 `nimbus-jose-jwt-10.5.jar`, `protobuf-java-4.33.2.jar`, `json-schema-validator-3.0.9.jar`로 XML의 P04_STACK_LOADED 출력에 남았다. Nimbus와 networknt의 배포 POM 라이선스는 Apache-2.0([Nimbus POM](https://repo.maven.apache.org/maven2/com/nimbusds/nimbus-jose-jwt/10.5/nimbus-jose-jwt-10.5.pom), [networknt POM](https://repo.maven.apache.org/maven2/com/networknt/json-schema-validator/3.0.9/json-schema-validator-3.0.9.pom)); protobuf는 상위 POM의 BSD-3-Clause다([parent POM](https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-parent/4.33.2/protobuf-parent-4.33.2.pom)). Boot 4.0.7/Spring AI 2.0.2/Testcontainers 2.0.3 및 앞 절의 Spring AI API ⓐ~ⓕ·OTLP 확인 결과는 변경하지 않았다.

DJL 0.40.0 JAR SHA=`569b1fa597e362ac11cd527e5deacd52cd827c8f5133a573572e2cd7051c5768`와 기존 기록의 native 바이너리 전체 SHA가 일치한다. 기존 Apache-2.0·native/DLL 라이선스 한계와 pin을 유지한다. tokenizer SHA=`aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`. native 소스는 수정하지 않았다. 사용자 정의 입력 fingerprint는 종전과 동일(`53b5cc5316445a72b2d082456b475f0654f47add373034d6f2c5915e66ef5064`)이나 이번 필수 명령에서 Gradle이 EXECUTED로 처리하여 Windows와 network=none Linux amd64 컨테이너의 offline load+encode를 실제 다시 통과했다(강제 rerun 옵션·추가 반복 없음). 현재 이미지 digest는 이전과 같아 기존 glibc 2.35 확인 근거도 유지된다. 이전 원시 결과를 새 실행이라고 쓰지 않았으며 새 XML/OTR를 별도 보관했다.

실제 writeRuntimeClasspath task를 scratch init script로만 override했다. 엔트리 내용 동일·ZIP 타임스탬프만 변경한 JAR 원시 SHA는 `ca0ff48691795e06a1044367bdcf0b15f29315bb5e0cea52afe4e41b8cbbd3ee` → `0afde1e17f811b48a33666e84b0d5e28098586d5f8a02bfc43cec97d8c777ee5`. 두 호출 모두 exit 0/EXECUTED였고 두 번째 출력은 새 SHA를 포함하고 옛 SHA를 포함하지 않았다. 의존 캐시·실제 제품 JAR는 수정하지 않았다. 이후 정상 producer와 sync를 실행했으며 실제 출력과 docs 사본의 바이트가 같고 SHA는 `29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`다. 상세 값은 scratch/raw-jar-regression.json에 있다.

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

P04 검토 수정의 로컬 검증은 통과했지만, 사용자 승인 전 commit 금지라는 원래 인계 조건에 따라 커밋·staging·push·checkout을 하지 않았다. 따라서 이번 clean 후 build는 로컬 N8 producer/전체 Eval 회귀 증거이며 **깨끗한 Git checkout build는 미검증**이다. 부모의 독립 검토도 대기 중이다. P04 최종 완료는 선언하지 않으며 P05는 시작하지 않았다. SPEC/Plan 계약 변경은 없고 기존 승인 scope line은 그대로 유지했다. 서버·SSH·K8s·배포·설정/재시작·모델 호출은 수행하지 않았다.

### 2026-11-05 P04 최종 감독 검증 결론

- 부모가 최종 코드와 원시 증거를 직접 독립 검증하여 **로컬 P04 코드·검증 PASSED**로 확정했다. 고유 시험 259건(Eval 249 + 앱 단위 6 + 앱 IT 3 + native IT 1), 실패·오류·건너뜀 0건이다. 각 XML 집계, OTR 바이트, record contentSHA, binding component hash, fingerprint/runId, 실제 HEAD와 runtime hash가 일치했다.
- 실제 runtime JAR 186행과 native 바이너리 해시, 문서 사본의 바이트 일치를 독립 확인했다(runtime SHA256=`29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`). 부모가 실제 producer의 원시 JAR 바이트·타임스탬프 변경을 재시험하여 두 실행 모두 EXECUTED이고 각각 새 SHA를 정확히 반영함을 확인했다. 현재 단위 출력과 동일한 앱 runtime filter를 적용한 공식 `test-evidence verify`도 부모가 별도 실행하여 exit 0 / `TEST_EVIDENCE_VERIFIED`를 확인했다.
- 이전 ‘부모 독립 검토 대기’는 당시 이력이며 현재는 통과했다. 보호 파일 483개, Spec·baseline·승인 문서, 기존 보고서 prefix와 정본 구성 경로도 부모가 확인했다. 실제 HEAD는 `a72b1b79dd8995c2da5542ff7064c54773727230`이며 미커밋 소스가 있는 dirty 작업 트리를 배포 신원으로 간주하지 않는다.
- 원래 인계 조건인 ‘사용자 승인 후에만 커밋’(`commits only after user approval`)을 유지한다. **남은 완료 조건은 사용자 승인 후 커밋 및 해당 커밋의 clean Git checkout 빌드 검증**이다. 그때까지 P04 최종 완료는 보류하며 P05는 미착수다. 이번 작업은 문서 끝 추가만 수행했으며 빌드·시험을 재실행하지 않았다.


### 2026-11-05 P04 최신 종료 결론 — 실제 clean Git checkout PASS

P04는 사용자 승인에 따른 로컬 커밋과 실제 clean checkout 검증까지 완료했다. 실제 시험 코드 커밋은 `8e51ea97049c86ee2a4729d736b8cd934131c792`, clone은 `C:/Users/DongGeon/AppData/Local/Temp/claude/468c18f2647c2504/2a367916-708b-4d0e-8a9d-62501d362a92/scratchpad/p04-commit-close/clean-checkout`이다. clone 최초 Git 상태는 비어 있고 build 산출물도 없었다. `gradlew.bat build --no-daemon --console=plain` 단독 exit 0, 직후 별도 `gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-app:test,:gateway-eval:test" --no-daemon --console=plain` exit 0 / TEST_EVIDENCE_VERIFIED다(각 호출 AIGW_SPEC_FILE 명시, 기본 필터). 앱6 + Eval249 = 255건, 실패·오류·건너뜀·중단0. producer가 앱 test 전에 실제 실행되었으며 integrationTest는 build에 포함되지 않았다.

실제 finalizer gitSha=`8e51ea97049c86ee2a4729d736b8cd934131c792`, 실제 생산 runtime SHA256=`29edaa9568651ebc588658f45fba789e35c79f02e2a0950f97bcec8f23384a6e`이며 OTR·binding·요약 신원을 대조했다. 빌드 후 clone도 Git clean이다. 부모가 기존 안정 소스의 259건·runtime/native·원시 증거를 이미 독립 검증했고, 이번 소스 변경은0이라 Docker/native는 반복하지 않았다. SPEC·baseline·승인6·보호483·승인 소스32는 불변이다. 기존 역사와 문서 prefix를 보존하며 이 절이 최신 결론이다. 후속 종료보고 docs-only 커밋의 코드/빌드 트리는 시험한 코드 커밋과 동일하다. P04 완료, P05 미착수, 실제 모델 호출0. 정확한 argv·runId·OTR/summary hash는 dev-report 최신 종료 절과 이번 scratch clean-validation.json에 있다.


## 2026-11-07 P14 OTel API/SDK 정렬 — 실제 RCA·공식 BOM·current graph·기동 검증 (P04 기록 불변)

P04 이후 resolved runtime에는 `opentelemetry-api`·`context`·`common` 1.67.0과 `api-incubator` 1.67.0-alpha(계측 `opentelemetry-logback-appender-1.0` 2.33.1-alpha·`instrumentation-api` 2.33.1이 요구)가 Boot 4.0.7 BOM의 SDK/exporter 1.55.0과 섞여 있었다(위 P04 runtime SHA `29edaa95…` 사본의 53·60·64행). P04~P13의 main 코드는 SDK provider를 만들지 않아 잠재 결함이었다. P14가 `SdkTracerProvider`/`SdkLoggerProvider`를 처음 생성하면서 드러났다.

- **원인:** `opentelemetry-sdk-common-1.55.0.jar`의 `ExceptionAttributeResolver.getDefault()`(line 36)가 `io.opentelemetry.api.internal.ConfigUtil`을 호출한다. 그런데 `opentelemetry-api-1.67.0.jar`(SHA `538d1dff…`)에는 이 클래스가 없다. 1.67에서는 `opentelemetry-common-1.67.0.jar`의 `io/opentelemetry/common/impl/ConfigUtil.class`로 옮겨졌다. 그 결과 P14 app IT 기동이 `NoClassDefFoundError`로 실패했다(scratch `p14c-app-it-r1`, 부모 중단 ABORTED 진단 근거).
- **공식 정렬:** 계측 v2.33.1 릴리스의 대상 SDK는 1.67.0이다. `opentelemetry-instrumentation-bom-alpha:2.33.1-alpha`(POM SHA `192bfb99…`)는 `opentelemetry-bom:1.67.0`·`opentelemetry-bom-alpha:1.67.0-alpha`·`opentelemetry-instrumentation-bom:2.33.1`을 import한다(부모 공개 POM 근거 `p14-parent-review/otel-bom-metadata-root-r1/receipt.json`).
- **Plan 허용 변경:** 정확히 2줄만 바꿨다. `gradle/libs.versions.toml`의 `[libraries]`에 `otel-instrumentation-bom-alpha` alias를, `gateway-app/build.gradle.kts`에 `implementation(platform(libs.otel.instrumentation.bom.alpha))`를 추가했다. `[versions]`·Boot 4.0.7·Spring AI 2.0.2·appender 선택은 그대로다. force/enforcedPlatform/exclude/새 숫자 pin은 0이다.
- **최초 실제 해석:**
  - 기존 runner `--secure-auth`(glab gitlab.com 자격)는 `/api/v4/user` 401로 만료 상태였다(`p14c-deps-runtime-bom-r1` exit1, 값 출력 0). 전역 credential은 변경하지 않았다.
  - scratch init `cached-snapshot-online.init.gradle`(`cacheChangingModulesFor`만 설정)로 이미 캐시된 com.nori SNAPSHOT의 재조회만 생략하고, Maven Central은 `--offline` 없이 해석했다.
  - `p14c-deps-runtime-bom-r2` exit0, `p14c-depinsight-sdkcommon-r1` exit0(`sdk-common:1.67.0`, By constraint·conflict 1.67.0 vs 1.55.0), `p14c-writecp-bom-r1` exit0(artifact 다운로드).
- **current graph:**
  - 최종 `p14c-final-deps-runtime-r1` exit0: OTel 1.55.0 요청 노드는 모두 `-> 1.67.0`이고 미정렬 0이다.
  - runtime의 stable OTel은 api/context/common/sdk-common/sdk-trace/sdk-logs/sdk-metrics/exporter-common/exporter-otlp/exporter-otlp-common/exporter-sender-okhttp 모두 1.67.0이다. alpha `api-incubator`는 1.67.0-alpha, 계측은 2.33.1(-alpha), `opentelemetry-proto`는 1.8.0-alpha다.
  - `p14c-final-depinsight-registry-otlp-r1`: `micrometer-registry-otlp:1.16.6`(Boot BOM constraint).
- **BOM만으로 인한 정확 delta**(BOM 직전 app runtime `f2a986c1…` → 직후 `e07547ea…`):
  - non-OTel은 OkHttp 5.2.1→5.5.0, Okio 3.16.1→3.18.1 두 개뿐이다.
  - OTel은 sdk/exporter 계열이 1.55.0→1.67.0으로 올라갔다.
  - `opentelemetry-sdk`(집계)·`opentelemetry-sdk-extension-autoconfigure-spi` 1.55.0은 제거되었다.
  - P04 사본과 비교할 때 보이는 jackson·slf4j·nimbus 등의 차이는 이전 phase 의존 변화이며 BOM 변화가 아니다.
- **1.67 동작 차이와 보정:**
  - 1.67 `SdkTracerProvider.tracerBuilder`는 `TracerSharedState.hasNoSpanProcessor()`가 true면 `TracerProvider.noop()`을 반환한다. 그러면 export off에서 traceId가 무효가 된다(scratch probe `otel-probe-r4` valid=false, `p14c-app-it-r2` ABORTED 진단).
  - Plan "span export off여도 SDK context를 생성"에 맞춰 `ObservabilityConfiguration`이 traces off일 때 export하지 않는 context-only `SpanProcessor` 1개를 둔다(probe valid=true).
- **기동·clean 검증:**
  - `p14c-stacksmoke-r1` 2/0/0/0 → `p14c-stacksmoke-verify-r1` TEST_EVIDENCE_VERIFIED.
  - 최종 `p14c-final-app-it-r1` 125/0/0/0(FP `467bd948…`, CP `e07547ea…`) → `p14c-final-app-it-verify-r1` VERIFIED.
  - `RuntimeClasspathCleanTest`를 포함한 `p14c-final-app-unit-r2` 19/0/0/0 → VERIFIED.
  - 최종 `syncRuntimeClasspathEvidence`로 `docs/gates/f1/runtime-classpath.txt`가 생성물과 바이트 일치한다(SHA256 `e07547eaff8e5ce3f8e1c3dc1689b20555c9aea353acdf060657d9afae989e52`, 193행).
- **판정 범위:** 위 결과는 로컬 graph·기동 근거다. 실제 OTLP 수신기·운영 export 판정(G-OBS-READBACK)과 경보 배치(G-ALERT)는 NOT_RUN이다. P14 완료·F1·운영 PASS를 선언하지 않는다.
