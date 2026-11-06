package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.JobEventRepository;
import java.util.List;

public final class EventReplayService {
    private final JobEventRepository events;
    public EventReplayService(JobEventRepository events) { this.events=events; }
    public long cursor(VerifiedSystemScope scope,String jobId,String lastEventId) {
        if(lastEventId==null)return 0;
        final long id;try{id=Long.parseLong(lastEventId);}catch(NumberFormatException invalid){throw new UniformNotFound();}
        if(id<=0||events.findAfter(scope,jobId,0).stream().noneMatch(event->event.id()==id))throw new UniformNotFound();return id;
    }
    public List<JobEventRepository.Event> after(VerifiedSystemScope scope,String jobId,long cursor) { return events.findAfter(scope,jobId,cursor); }
}
