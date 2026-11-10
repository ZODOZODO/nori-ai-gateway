package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.*;
import java.time.ZoneId;
import java.util.*;

public final class WorkflowResourceResolverFactory {
    private final TcBusinessReadPort business;private final SourcePermissionMap permissions;private final CapabilityRegistry registry;private final com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiry;
    public WorkflowResourceResolverFactory(TcBusinessReadPort business,SourcePermissionMap permissions,CapabilityRegistry registry,com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiry){this.business=business;this.permissions=permissions;this.registry=registry;this.expiry=expiry;}
    public WorkflowResourceResolver forJob(JobContext job,ZoneId zone) {
        var call=new TcSourceAuthorizationPort.DelegatedCall(job.scope().sub(),job.delegation());
        return new WorkflowResourceResolver() {
            private void verify(VerifiedSystemScope supplied){if(supplied!=job.scope()||!job.jobId().equals(supplied.requestId()))throw new IllegalArgumentException("조회 작업 결속이 다릅니다");if(job.cancellation().reason().isPresent())throw new IllegalStateException("작업이 취소되었습니다");}
            @Override public Lookup lookup(ResourceRef ref,VerifiedSystemScope scope) {
                verify(scope);beforeRead();var result=business.resolveResource(call,ref.type(),ref.ref());
                if(result==null)throw new IllegalStateException("리소스 응답이 없습니다");
                if(result.status()==ResultStatus.DENIED)return new Denied();if(result.status()==ResultStatus.SOURCE_UNAVAILABLE)return new Unavailable();
                if(result.status()!=ResultStatus.SUCCESS&&result.status()!=ResultStatus.EMPTY)throw new IllegalStateException("리소스 응답 계약이 잘못되었습니다");
                if(result.data()==null){if(result.status()==ResultStatus.EMPTY)return new Absent();throw new IllegalStateException("리소스 응답 자료가 없습니다");}
                return result.data().stream().anyMatch(value->ref.type().equals(value.type())&&ref.ref().equals(value.ref()))?new Found(new ResourceState(scope.boundSystem(),ref.type(),true)):new Absent();
            }
            @Override public SourceBinding source(CapabilityManifest manifest,VerifiedSystemScope scope) {
                verify(scope);var canonical=registry.find(manifest.capabilityId()).orElse(null);
                boolean allowed=canonical!=null&&canonical.ownerSystem()==scope.boundSystem()&&canonical.sourceAllowlistRef().equals(manifest.sourceAllowlistRef())
                    &&new OwnedReference(scope.boundSystem(),manifest.sourceAllowlistRef()).belongsTo(scope.boundSystem())&&permissions.find(manifest.sourceAllowlistRef()).isPresent();
                return new SourceBinding(manifest.ownerSystem(),manifest.sourceAllowlistRef(),allowed);
            }
            @Override public ZoneId timezone(VerifiedSystemScope scope){verify(scope);return zone;}
            @Override public TcSourceAuthorizationPort.DelegatedCall delegatedCall(){return call;}
            @Override public void beforeRead(){expiry.assertLive(job.conversationExpiresAt(),job.cancellation());}
        };
    }
}
