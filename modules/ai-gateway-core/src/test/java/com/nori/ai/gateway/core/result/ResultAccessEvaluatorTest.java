package com.nori.ai.gateway.core.result;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.result.ResultAccessEvaluator.Decision.*;
import static com.nori.ai.gateway.core.result.KnowledgeDocumentStateReader.State.*;

class ResultAccessEvaluatorTest {
    private final ResultAccessEvaluator evaluator=new ResultAccessEvaluator();
    @Test void sourceAnyAndBetweenSourcesAndCurrentDocumentAvailabilityAreRequired() {
        var a=new ResultSourceDeps.DocKey("TC","doc-a","old");var b=new ResultSourceDeps.DocKey("TC","doc-b","old");
        var deps=new ResultSourceDeps(List.of(new ResultSourceDeps.Source("a",Set.of("A","B")),new ResultSourceDeps.Source("b",Set.of("C"))),List.of(a,b));
        assertThat(evaluator.evaluate(Set.of("B","C"),deps,Map.of(a,AVAILABLE,b,AVAILABLE))).isEqualTo(ALLOW);
        assertThat(evaluator.evaluate(Set.of("B"),deps,Map.of(a,AVAILABLE,b,AVAILABLE))).isEqualTo(DENY);
        for(var state:List.of(UNAVAILABLE,UNKNOWN))assertThat(evaluator.evaluate(Set.of("B","C"),deps,Map.of(a,AVAILABLE,b,state))).isEqualTo(DENY);
        assertThat(evaluator.evaluate(Set.of("B","C"),deps,Map.of(a,AVAILABLE))).isEqualTo(DENY);
        assertThat(evaluator.evaluate(Set.of(),new ResultSourceDeps(List.of(),List.of()),Map.of())).isEqualTo(ALLOW);
    }
}
