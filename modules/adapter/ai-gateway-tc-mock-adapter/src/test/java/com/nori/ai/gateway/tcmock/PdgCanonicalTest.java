package com.nori.ai.gateway.tcmock;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PdgCanonicalTest {
    @Test @SuppressWarnings("unchecked") void matchesFourFixedVectorsAndRejectsThreeInvalidInputs() throws Exception {
        try (var stream = getClass().getResourceAsStream("/pdg-vectors-v1.json")) {
            assertThat(stream).isNotNull();
            var root = JSONObjectUtils.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            var vectors = (List<Map<String,Object>>)root.get("vectors");
            assertThat(vectors).hasSize(4);
            for (var vector : vectors) assertThat(PdgCanonical.compute((String)vector.get("catalogRevision"),(List<String>)vector.get("perms"))).isEqualTo(vector.get("expectedPdg"));
            var negatives = (List<Map<String,Object>>)root.get("negatives");
            assertThat(negatives).hasSize(3);
            for (var vector : negatives) assertThatThrownBy(() -> PdgCanonical.compute((String)vector.get("catalogRevision"),(List<String>)vector.get("perms"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void sortsUnicodeByCodePointAndRejectsNullWhitespaceAndBrokenUtf16() {
        assertThat(PdgCanonical.compute("rev",List.of("\uD800\uDC00","\uE000","\uE000")))
            .isEqualTo("94dc9b484afe4f4af982bfe4afde1fbd20f0e96a8e244f070f09affc33ca1150");
        assertThatThrownBy(() -> PdgCanonical.compute(null,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PdgCanonical.compute("rev",null)).isInstanceOf(IllegalArgumentException.class);
        for (String invalid : Arrays.asList(null,"","A\u0085B","A\u00A0B","A B","\uD800"))
            assertThatThrownBy(() -> PdgCanonical.compute("rev",Arrays.asList(invalid))).isInstanceOf(IllegalArgumentException.class);
    }
}
