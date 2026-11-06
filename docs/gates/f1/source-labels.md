# P12 Source 선택자 로컬 검증

현재 상태: CODE/LOCAL_TEST, 부모 독립 검토 대기입니다. 실제 Source 연결·배포 수용·ISO-15는 NOT_RUN입니다.

| Source | 고정 라벨·선택자 | 로컬 stub 관측 |
|---|---|---|
| Loki | `app=tc-gateway` AND `namespace=tc` | 31일 fixture의 하위 창 124개 |
| Loki | `app=tc-business` AND `namespace=tc` | 31일 fixture의 하위 창 124개 |
| Loki | `app=tc-ui-backend` AND `namespace=tc` | 31일 fixture의 하위 창 124개 |
| Loki | `app=tc-ui-front` AND `namespace=tc` | 31일 fixture의 하위 창 124개 |
| Tempo | 위 TC 서비스 4개 | TC span만 반환, WM 서비스 span 제외 |
| GitLab | 미승인 목록 0개 | 기본 거부, 실제 연결 0건 |

31일 fixture는 최신 6시간 하위 창부터 총 496건의 GET을 보냈습니다. 각 하위 요청의 앱 정확 일치와 namespace AND를 검사했고 namespace 단독 selector는 0건입니다. 총 줄 상한·하위 limit·마감으로 조회하지 못한 구간은 `missingRanges`로 구분했습니다. 보존 미확정·확정·범위 밖과 EMPTY를 별도로 확인했습니다.

양성 제어 helper의 TC 4개·WM 2개 고정 앱은 acceptance 모듈에서만 사용합니다. 로컬 WM 양성 fixture는 1건의 합성 자료에 대한 건수·라벨·시각만 반환하며 운영 Connector의 허용 앱을 늘리지 않습니다.

Source 관측 속성과 meter 태그는 `source_id`, `method`, `status_class` 3개입니다. 검색어·URL·응답 본문·토큰을 이 문서에 보관하지 않습니다. 실제 Loki·Tempo·GitLab 건수와 Service 대상 확인은 수행하지 않았습니다.
