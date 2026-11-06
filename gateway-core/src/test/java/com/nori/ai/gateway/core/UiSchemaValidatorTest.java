package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.blocks.UiSchemaValidator;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.result.ResultBlock;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UiSchemaValidatorTest {
    @Test void sevenShapesAreClosedAndForeignReferencesMalformedRowsAndNonFiniteNumbersAreRejected() {
        var validator=new UiSchemaValidator();var shapes=Map.of("text","{\"text\":\"문구\"}","table","{\"columns\":[\"v\"],\"rows\":[{\"v\":1}]}","status-card","{\"status\":\"NORMAL\"}","chart","{\"series\":[]}","timeline","{\"items\":[]}","code-block","{\"text\":\"관측 코드\"}","evidence-link","{\"evidenceId\":\"id\"}");
        shapes.forEach((type,json)->validator.validate(new ResultBlock(type,null,SchemaContracts.parse(json)),OwnerSystem.TC));
        assertThatThrownBy(()->validator.validate(new ResultBlock("html",null,SchemaContracts.parse("{}")),OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validator.validate(new ResultBlock("text",new OwnedReference(OwnerSystem.WM,"data://WM/private"),SchemaContracts.parse(shapes.get("text"))),OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
        for(String invalid:List.of("{\"columns\":[\"x\",\"x\"],\"rows\":[]}","{\"columns\":[\"x\"],\"rows\":[{\"extra\":1}]}"))assertThatThrownBy(()->validator.validate(new ResultBlock("table",null,SchemaContracts.parse(invalid)),OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
        for(double number:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {var content=SchemaContracts.JSON.createObjectNode();content.putArray("series").addObject().put("value",number);assertThatThrownBy(()->validator.validate(new ResultBlock("chart",null,content),OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);}
        var content=SchemaContracts.JSON.createObjectNode();content.putArray("series").add(Float.NaN);assertThatThrownBy(()->validator.validate(new ResultBlock("chart",null,content),OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
    }
}
