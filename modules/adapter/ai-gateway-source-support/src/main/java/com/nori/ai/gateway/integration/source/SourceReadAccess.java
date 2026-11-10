package com.nori.ai.gateway.integration.source;

import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.*;
import java.time.Clock;
import java.util.Objects;

/** Source의 소유권과 실행 시점 인가는 기존 중앙 계약으로 확인합니다. */
public final class SourceReadAccess {
    public record Request(VerifiedSystemScope scope, DelegatedCall delegatedCall, String sourceAllowlistRef,Runnable beforeRead) {
        public Request(VerifiedSystemScope scope,DelegatedCall delegatedCall,String sourceAllowlistRef){this(scope,delegatedCall,sourceAllowlistRef,()->{});}
        public Request { Objects.requireNonNull(scope); Objects.requireNonNull(delegatedCall); Objects.requireNonNull(sourceAllowlistRef);Objects.requireNonNull(beforeRead); }
        @Override public String toString() { return "SourceReadRequest[보호됨]"; }
    }
    public static final class GuardStopped extends RuntimeException {
        private final RuntimeException original;
        private GuardStopped(RuntimeException original){super("작업 조회가 중단되었습니다",null,false,false);this.original=original;}
        public RuntimeException rethrow(){throw original;}
        @Override public String toString(){return "GuardStopped[보호됨]";}
    }
    public static final class Rejected extends RuntimeException {
        private final ResultStatus status;
        private Rejected(ResultStatus status){super("현재 Source 조회가 거부되었습니다",null,false,false);if(status!=ResultStatus.DENIED&&status!=ResultStatus.SOURCE_UNAVAILABLE)throw new IllegalArgumentException("조회 상태가 잘못되었습니다");this.status=status;}
        public ResultStatus status(){return status;}
    }
    private final SourceAuthorizationStep authorization;
    private final Clock clock;
    public SourceReadAccess(SourceAuthorizationStep authorization, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization); this.clock = Objects.requireNonNull(clock);
    }
    public ResultStatus check(Request request, String expectedRef) {
        Objects.requireNonNull(request);
        if (request.scope().boundSystem() != OwnerSystem.TC || !expectedRef.equals(request.sourceAllowlistRef())) return ResultStatus.DENIED;
        guard(request);
        if(!request.scope().validAt(clock.instant()))return ResultStatus.DENIED;
        var decision = authorization.authorizeBefore(expectedRef, request.scope(), request.delegatedCall());
        guard(request);
        if(!request.scope().validAt(clock.instant()))return ResultStatus.DENIED;
        if (decision instanceof SourceAuthorizationStep.Granted) return ResultStatus.SUCCESS;
        return decision instanceof SourceAuthorizationStep.SourceUnavailable ? ResultStatus.SOURCE_UNAVAILABLE : ResultStatus.DENIED;
    }
    private void guard(Request request){try{request.beforeRead().run();}catch(RuntimeException stopped){throw new GuardStopped(stopped);}}
    public void require(Request request,String expectedRef){ResultStatus status=check(request,expectedRef);if(status!=ResultStatus.SUCCESS)throw new Rejected(status);}
}
