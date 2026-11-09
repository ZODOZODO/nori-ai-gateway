@AGENTS.md

# Claude Code SDD 진입 규칙

이 저장소의 SDD 정본은 저장소 안 `docs/` 에 있다. 워크스페이스 공통 규칙(SDD 정본 = 형제 저장소 `../nori-sdd-specs/`)의 예외이며, `../nori-sdd-specs/` 나 `.claude/specs/` 에 이 시스템의 SPEC·PLAN 을 새로 만들지 않는다.

SDD 요청을 받으면 다음 순서로 읽는다.

1. `docs/README.md`
2. 승인 SPEC `docs/ai-rag-gateway-architecture.md`
3. Plan `docs/ai-rag-gateway-architecture-plan.md`(§0 문서 메타·Plan 상태, §6 실행 순서, 대상 phase 또는 정렬 단위 U1~U8)
4. Dev report `docs/ai-rag-gateway-architecture-dev-report.md`
5. 기준선 사슬 `docs/gates/spec-baseline.md`

작업 시작 첫 명령은 `spec-guard` 다(`AGENTS.md` §5). exit 0 이 아니면 그 phase 를 시작하지 않는다.

사용자가 요청한 phase·단계에서 멈춘다. SPEC 개정과 기준선 사슬 추가는 root 단독 절차이며 phase 작업 중에 하지 않는다. 프로젝트 구조·코딩·빌드·시험 규칙은 `AGENTS.md` 를 따른다.
