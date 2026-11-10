package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class GeneralQueryHelpBuilderTest {
    @Test void helpUsesTheApprovedViewAndRequestPermissionsWithoutExposingPlannedDisabledOrUnpermittedTools() {
        var implemented=manifest();var planned=modify(implemented,"availability","PLANNED");var disabled=modify(implemented,"availability","DISABLED");
        var builder=new GeneralQueryHelpBuilder();var view=new RegistryView(OwnerSystem.TC,List.of(implemented,planned,disabled));
        assertThat(builder.build(view,Set.of("EQP_READ")).getFirst().content().get("rows")).hasSize(1);assertThat(builder.build(view,Set.of()).getFirst().content().get("rows")).isEmpty();
        assertThat(builder.build(view,Set.of("EQP_READ")).getFirst().content().get("rows").get(0).get("description").asString()).isEqualTo(implemented.description());
    }
}
