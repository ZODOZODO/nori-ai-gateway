package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.blocks.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class BlockBuilderTest {
    @Test void tableIsAnExactDefensiveProjectionOfObservedValuesAndTheirOrder() {
        var items=SchemaContracts.JSON.createArrayNode();items.addObject().put("value",new BigInteger("9999999999999999999999999999999999999999999999")).put("unit","ms").put("time","2026-11-05T00:00:00.123456789Z");items.addObject().put("value",new BigDecimal("1E10000")).put("status","NORMAL");
        var content=SchemaContracts.JSON.createObjectNode().set("items",items);var raw=changeResult(result(manifest()),"blocks",List.of(new ResultBlock("table",result(manifest()).dataRef(),content)));
        var builder=new BlockBuilder(new UiSchemaValidator());var blocks=builder.build(manifest(),raw);assertThat(blocks).hasSize(1);var block=blocks.getFirst();assertThat(block.dataRef()).isEqualTo(raw.blocks().getFirst().dataRef());assertThat(block.content().get("rows")).isEqualTo(items);assertThat(block.content().path("columns").valueStream().map(node->node.asString())).containsExactly("value","unit","time","status");
        items.removeAll();assertThat(block.content().get("rows")).hasSize(2);assertThat(block.content().get("rows").get(0).get("unit").asString()).isEqualTo("ms");
    }
    @Test void rawSchemaBoundsAndTypesAreValidatedBeforeAnyUiRowsAreManufactured() {
        var tree=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(manifest());((tools.jackson.databind.node.ObjectNode)tree.get("outputSchema").path("properties").get("items")).put("maxItems",1);tree.put("schemaDigest",SchemaContracts.digest(tree.get("inputSchema"),tree.get("outputSchema")));var bounded=new ManifestLoader().decode(tree);
        var content=SchemaContracts.parse("{\"items\":[{\"unit\":\"ms\"},{\"unit\":\"s\"}]}");var raw=changeResult(result(bounded),"blocks",List.of(new ResultBlock("table",result(bounded).dataRef(),content)));
        assertThatThrownBy(()->new BlockBuilder(new UiSchemaValidator()).build(bounded,raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
