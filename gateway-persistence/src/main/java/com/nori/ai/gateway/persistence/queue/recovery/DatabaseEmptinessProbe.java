package com.nori.ai.gateway.persistence.queue.recovery;

import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class DatabaseEmptinessProbe {
    private static final Set<String> EXCLUDED=Set.of("gw.queue_control","gw.backup_run","gw.instance_epoch");
    private final JdbcClient jdbc;
    public DatabaseEmptinessProbe(JdbcClient jdbc) { this.jdbc=jdbc; }
    public boolean isEmpty() {
        var tables=jdbc.sql("SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ('gw','kn') AND table_type='BASE TABLE' ORDER BY table_schema,table_name")
            .query((rs,n)->new String[]{rs.getString(1),rs.getString(2)}).list();
        for(var table:tables) {
            if(EXCLUDED.contains(table[0]+"."+table[1]))continue;
            // 식별자는 DB 메타데이터에서만 얻고 따옴표를 이스케이프합니다.
            String qualified="\""+table[0].replace("\"","\"\"")+"\".\""+table[1].replace("\"","\"\"")+"\"";
            if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM "+qualified+")").query(Boolean.class).single())return false;
        }
        return true;
    }
}
