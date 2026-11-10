package com.nori.ai.gateway.integration.source;

import java.util.*;

/** TC Source 조회가 허용하는 TC 앱 목록입니다. Loki app 라벨·Tempo service·GitLab 배포 계보 service 가 같은 목록을 씁니다. */
public final class TcSourcePolicy {
    public static final List<String> APPS = List.of("tc-gateway", "tc-business", "tc-ui-backend", "tc-ui-front");
    private TcSourcePolicy() {}
}
