package com.nori.ai.gateway.app.job.recovery;

import com.nori.ai.gateway.app.config.RecoveryProperties;
import com.nori.ai.gateway.persistence.queue.recovery.*;
import java.time.Clock;
import java.util.Optional;

public final class RecoveryModeRouter implements ReadinessPrecondition {
    public enum Mode{INITIALIZE_NEW,REJECT_EPOCH_MISSING,REJECT_LIVE_DB,RESTORE,NORMAL}
    public static final class RestorePathRequired extends RuntimeException{public RestorePathRequired(){super("RESTORE_PATH_REQUIRED");}}
    private final InstanceEpochRepository epochs;private final DbSystemIdentifierReader identifiers;private final DatabaseEmptinessProbe emptiness;
    private final RecoveryProperties properties;private final Clock clock;private final StartupRecoveryRunner normal;private final Optional<RestoreRecoveryHandler> restore;
    private Mode mode;
    public RecoveryModeRouter(InstanceEpochRepository epochs,DbSystemIdentifierReader identifiers,DatabaseEmptinessProbe emptiness,RecoveryProperties properties,Clock clock,StartupRecoveryRunner normal,Optional<RestoreRecoveryHandler> restore){
        this.epochs=epochs;this.identifiers=identifiers;this.emptiness=emptiness;this.properties=properties;this.clock=clock;this.normal=normal;this.restore=restore;
    }
    @Override public int order(){return Integer.MIN_VALUE;}
    @Override public void run(){
        var epoch=epochs.find();String current=identifiers.read();
        if(epoch.isEmpty())mode=emptiness.isEmpty()&&!properties.restoreMode()?Mode.INITIALIZE_NEW:Mode.REJECT_EPOCH_MISSING;
        else if(!current.equals(epoch.get().dbSystemIdentifier()))mode=Mode.RESTORE;
        else mode=properties.restoreMode()&&epoch.get().restoreCompletedAt()==null?Mode.REJECT_LIVE_DB:Mode.NORMAL;
        switch(mode){
            case REJECT_EPOCH_MISSING->throw new IllegalStateException("RECOVERY_EPOCH_MISSING");
            case REJECT_LIVE_DB->throw new IllegalStateException("RESTORE_TARGET_IS_LIVE_DB");
            case RESTORE->{if(restore.isEmpty()||!restore.get().run(epoch.orElseThrow()).completed())throw new RestorePathRequired();}
            case NORMAL->normal.run();
            case INITIALIZE_NEW->{epochs.insertIfAbsent(current,clock.instant());if(!epochs.find().orElseThrow().dbSystemIdentifier().equals(current)){run();return;}normal.run();}
        }
    }
    public Mode mode(){return mode;}
}
