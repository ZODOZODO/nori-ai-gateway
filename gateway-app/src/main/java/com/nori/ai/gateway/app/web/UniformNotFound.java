package com.nori.ai.gateway.app.web;

public final class UniformNotFound extends RuntimeException {
    public UniformNotFound() { super("요청 대상을 찾을 수 없습니다"); }
}
