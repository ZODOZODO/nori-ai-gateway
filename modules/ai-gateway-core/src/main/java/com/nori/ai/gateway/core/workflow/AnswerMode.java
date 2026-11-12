package com.nori.ai.gateway.core.workflow;

/** 분류 wire v3 {@code a}: 표현 길이 선택(SPEC §28.3). 권한·시스템·도구 호출 범위를 넓히지 않고 Composer 128토큰도 늘리지 않는다. */
public enum AnswerMode {
    /** 짧은 설명(기본). */ B,
    /** 보고서·비교·상세 요청 — 구조화 근거 블록으로 제공한다. */ D
}
