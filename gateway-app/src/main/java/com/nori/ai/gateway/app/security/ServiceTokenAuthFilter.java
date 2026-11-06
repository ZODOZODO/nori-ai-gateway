package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import static com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.*;

public final class ServiceTokenAuthFilter implements Filter {
    public static final String VERIFIED_SCOPE_ATTRIBUTE = ServiceTokenAuthFilter.class.getName() + ".scope";
    public static final String VERIFIED_DELEGATION_ATTRIBUTE = ServiceTokenAuthFilter.class.getName() + ".delegation";
    private final SecurityCredentialFiles credentials;
    private final ActorAssertionVerifier verifier;
    private final ScopeAssembler scopes;
    private final BodySystemFieldGuard bodies;
    private final AuthRejectionAuditor auditor;
    public ServiceTokenAuthFilter(SecurityCredentialFiles credentials, ActorAssertionVerifier verifier, ScopeAssembler scopes,
            BodySystemFieldGuard bodies, AuthRejectionAuditor auditor) {
        this.credentials = Objects.requireNonNull(credentials); this.verifier = Objects.requireNonNull(verifier);
        this.scopes = Objects.requireNonNull(scopes); this.bodies = Objects.requireNonNull(bodies); this.auditor = Objects.requireNonNull(auditor);
    }
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var http = (HttpServletRequest)request; var output = (HttpServletResponse)response;
        UUID requestId = UUID.randomUUID();
        String authorization = http.getHeader("Authorization");
        VerifiedServiceIdentity service = null;
        VerifiedSystemScope scope = null;
        final BodySystemFieldGuard.ReplayableRequest replay;
        final ActorAssertionVerifier.VerifiedAssertion assertion;
        try {
            if (authorization == null || authorization.isBlank()) throw new ActorAssertionVerifier.Rejected(SERVICE_TOKEN_MISSING);
            if (Collections.list(http.getHeaders("Authorization")).size() != 1 || !authorization.matches("Bearer [^\\s]+"))
                throw new ActorAssertionVerifier.Rejected(SERVICE_TOKEN_INVALID);
            service = credentials.authenticate(authorization.substring(7)).orElseThrow(() -> new ActorAssertionVerifier.Rejected(SERVICE_TOKEN_INVALID));
            replay = bodies.read(http);
            if (Collections.list(http.getHeaders("X-Actor-Assertion")).size() > 1) throw new ActorAssertionVerifier.Rejected(ACTOR_ASSERTION_MISSING);
            assertion = verifier.verify(bodies.assertion(replay),service,replay);
            scope = scopes.forRequest(service,assertion,requestId);
            if (!bodies.check(replay,scope.boundSystem())) throw new ActorAssertionVerifier.Rejected(SYSTEM_SCOPE_MISMATCH);
        } catch (ActorAssertionVerifier.Rejected rejection) {
            // 감사 저장 경계만 변환하며 이후 controller 예외를 인증 거절로 바꾸지 않습니다.
            try {
                if (service == null) auditor.unauthenticated(requestId,rejection.reason());
                else if (scope == null) auditor.serviceVerified(service,requestId,rejection.reason());
                else auditor.actorVerified(service,scope,requestId,rejection.reason());
            } catch (DataAccessException | TransactionException failure) {
                output.setHeader("Retry-After","1");
                respond(output,503,"요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.",requestId); return;
            }
            respond(output,rejection.reason().httpStatus(),"인증 또는 요청 범위를 확인할 수 없습니다.",requestId); return;
        }
        replay.setAttribute(VERIFIED_SCOPE_ATTRIBUTE,scope);
        assertion.delegation().ifPresent(value -> replay.setAttribute(VERIFIED_DELEGATION_ATTRIBUTE,value));
        chain.doFilter(replay,response);
    }
    private static void respond(HttpServletResponse response, int status, String message, UUID requestId) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\",\"requestId\":\"" + requestId + "\"}");
    }
}
