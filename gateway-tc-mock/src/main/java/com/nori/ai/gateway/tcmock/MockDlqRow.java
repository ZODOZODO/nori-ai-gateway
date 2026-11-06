package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.TcBusinessReadPort.DlqEntry;
import java.util.Objects;

/** 원천 설비 귀속은 공개 DTO로 투영하기 전에만 보존합니다. */
record MockDlqRow(String eqpId,DlqEntry projection) {
    MockDlqRow { Objects.requireNonNull(eqpId);Objects.requireNonNull(projection); }
}
