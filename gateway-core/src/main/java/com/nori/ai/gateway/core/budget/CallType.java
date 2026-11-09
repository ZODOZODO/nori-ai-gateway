package com.nori.ai.gateway.core.budget;

/** 추론 호출 유형. 유형마다 SPEC §14.5 의 입력·답변·사고 상한이 따로 적용된다. */
public enum CallType { CLASSIFY, PLAN, COMPOSER }
