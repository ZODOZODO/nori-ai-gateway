package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.registry.SchemaContracts;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultBlockIdsTest {
    @Test void finalIdsFollowServerArrayOrderAndDoNotUseDataRefsOrNestedBusinessIds() {
        var original=SchemaContracts.parse("{\"blocks\":[{\"type\":\"table\",\"dataRef\":null,\"content\":{\"id\":\"business-id\"}},{\"type\":\"text\",\"dataRef\":\"result://TC/job/same\"},{\"type\":\"text\",\"dataRef\":\"result://TC/job/same\"}]}");
        var assigned=ResultBlockIds.assign(original);
        assertThat(assigned.get("blocks").get(0).get("id").asString()).isEqualTo("b0");
        assertThat(assigned.get("blocks").get(1).get("id").asString()).isEqualTo("b1");assertThat(assigned.get("blocks").get(2).get("id").asString()).isEqualTo("b2");
        assertThat(assigned.get("blocks").get(0).get("content").get("id").asString()).isEqualTo("business-id");assertThat(original.get("blocks").get(0).has("id")).isFalse();
        assertThat(ResultBlockIds.assign(SchemaContracts.parse("{}"))).isEqualTo(SchemaContracts.parse("{}"));
        assertThat(ResultBlockIds.assign(SchemaContracts.parse("{\"blocks\":[]}"))).isEqualTo(SchemaContracts.parse("{\"blocks\":[]}"));
    }
}
