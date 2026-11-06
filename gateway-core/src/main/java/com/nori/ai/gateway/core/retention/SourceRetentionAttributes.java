package com.nori.ai.gateway.core.retention;

import java.time.*;
import java.util.Objects;

public sealed interface SourceRetentionAttributes {
    record Configured(Duration retention) implements SourceRetentionAttributes {
        public Configured {
            if (retention == null || retention.isZero() || retention.isNegative()) throw new IllegalArgumentException("보존 기간이 잘못되었습니다");
        }
    }
    record VerifiedOldest(Instant verifiedOldestAt) implements SourceRetentionAttributes {
        public VerifiedOldest { Objects.requireNonNull(verifiedOldestAt); }
    }
    record Unverified() implements SourceRetentionAttributes {}
    record TimeAndCountTrim(long retentionHours, long maxEntriesPerKey, boolean appliedValuesVerified) implements SourceRetentionAttributes {
        public TimeAndCountTrim {
            if (retentionHours <= 0 || maxEntriesPerKey <= 0) throw new IllegalArgumentException("보존 설정이 잘못되었습니다");
        }
    }
}
