package com.nori.ai.gateway.app.hooks;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class ControllableClock extends Clock {
    public enum Mode { SYSTEM, FROZEN }
    private final Clock system=Clock.systemUTC();
    private Instant frozen;
    private boolean fresh=true;
    public ControllableClock(Mode mode,Instant initial) {
        if(mode==Mode.FROZEN && initial==null)throw new IllegalArgumentException("동결 시각이 필요합니다");
        frozen=mode==Mode.FROZEN?initial.truncatedTo(ChronoUnit.MICROS):null;
    }
    @Override public ZoneId getZone(){return ZoneOffset.UTC;}
    @Override public Clock withZone(ZoneId zone){if(!ZoneOffset.UTC.equals(zone))throw new IllegalArgumentException("UTC 시계만 지원합니다");return this;}
    @Override public synchronized Instant instant(){return (frozen==null?system.instant():frozen).truncatedTo(ChronoUnit.MICROS);}
    public synchronized Mode mode(){return frozen==null?Mode.SYSTEM:Mode.FROZEN;}
    public synchronized void freezeAt(Instant at,boolean noActiveJobs){
        if(at==null || ((!fresh || !noActiveJobs) && at.isBefore(instant())))throw new IllegalArgumentException("시계를 뒤로 이동할 수 없습니다");
        frozen=at.truncatedTo(ChronoUnit.MICROS);fresh=false;
    }
    public synchronized void advance(long seconds){if(seconds<=0)throw new IllegalArgumentException("양의 정수 초만 허용합니다");frozen=instant().plusSeconds(seconds);fresh=false;}
    public synchronized void resumeSystem(){frozen=null;fresh=false;}
    public synchronized void reset(){frozen=null;fresh=true;}
    public synchronized void admissionCommitted(){fresh=false;}
}
