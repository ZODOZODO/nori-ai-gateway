# NORI AI Gateway

독립 AI Gateway의 P01 평가·증거 도구입니다. 제품 런타임과 모델 호출은 후속 phase에서 구현합니다.

Java 21과 Gradle 9.2.1을 사용합니다. 로컬 명령은 승인 SPEC 원본 경로를 `AIGW_SPEC_FILE` 환경변수로 지정한 뒤 실행합니다. SPEC 본문은 이 레포에 복사하지 않습니다.

```powershell
$env:AIGW_SPEC_FILE = 'C:/workspace_nori/nori-tc-exec/docs/task/ai-rag-gateway-architecture.md'
.\gradlew.bat :gateway-eval:test
.\gradlew.bat :gateway-eval:run --args="test-evidence verify --tasks :gateway-eval:test"
.\gradlew.bat :gateway-eval:run --args="spec-guard --baseline docs/gates/spec-baseline.md"
```

실제 원격 패키지 공급 Gate(b)는 로컬 P01 완료와 별개입니다. Python 3.12 준비가 확인되기 전 P02를 시작하지 않습니다.
