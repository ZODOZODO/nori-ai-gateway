package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class ManifestDigestTest {
    @Test void canonicalizesRfc8785NumberVectorsAndExponentBoundaries() {
        assertThat(SchemaContracts.canonicalize("[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001]"))
            .isEqualTo("[333333333.3333333,1e+30,4.5,0.002,1e-27]");
        assertThat(SchemaContracts.canonicalize("[-0.0,1e-6,1e-7,1e20,1e21,1.7976931348623157e308,5e-324]"))
            .isEqualTo("[0,0.000001,1e-7,100000000000000000000,1e+21,1.7976931348623157e+308,5e-324]");
    }
    @Test void preservesUnicodeAndEscapingWithUtf16KeyOrdering() {
        assertThat(SchemaContracts.canonicalize("{\"\\ue000\":1,\"\\ud800\\udc00\":2,\"a\":\"\\u000f\\n\\\\\\\"\"}"))
            .isEqualTo("{\"a\":\"\\u000f\\n\\\\\\\"\",\"𐀀\":2,\"\":1}");
        assertThat(SchemaContracts.canonicalize("[\"é\",\"é\"]")).isEqualTo("[\"é\",\"é\"]");
        for (String invalid : new String[]{"[\"\\ud800\"]", "{\"\\udc00\":1}", "{\"a\":1,\"a\":2}", "[1e999]"})
            assertThatThrownBy(() -> SchemaContracts.canonicalize(invalid)).isInstanceOf(IllegalArgumentException.class).hasMessage("parse failed");
    }
    @Test void schemaDigestUsesCanonicalPairAndIsSensitiveToSchemaChange() {
        var a = SchemaContracts.parse("{\"type\":\"number\",\"minimum\":-0.0,\"maximum\":1E30}");
        var b = SchemaContracts.parse("{\"maximum\":1e30,\"minimum\":0,\"type\":\"number\"}");
        var out = SchemaContracts.parse("{\"type\":\"object\"}");
        assertThat(SchemaContracts.digest(a, out)).isEqualTo(SchemaContracts.digest(b, out)).matches("[a-f0-9]{64}");
        assertThat(SchemaContracts.digest(a, out)).isNotEqualTo(SchemaContracts.digest(out, a));
        assertThat(SchemaContracts.digest(a, out)).isNotEqualTo(SchemaContracts.digest(SchemaContracts.parse("{\"type\":\"integer\"}"), out));
    }
    @Test void rejectsMalformedSchemaDigestAndOwnerWithoutResolvingExternalUris() {
        ManifestLoader loader = new ManifestLoader();
        for (Object owner : new Object[]{"ANY", java.util.List.of("TC", "WM")}) {
            ObjectNode tree = SchemaContracts.JSON.valueToTree(manifest()); tree.set("ownerSystem", SchemaContracts.JSON.valueToTree(owner));
            assertThatThrownBy(() -> loader.decode(tree)).isInstanceOf(IllegalArgumentException.class);
        }
        ObjectNode tree = SchemaContracts.JSON.valueToTree(manifest()); tree.putNull("ownerSystem"); final var nullOwner = tree;
        assertThatThrownBy(() -> loader.decode(nullOwner)).isInstanceOf(IllegalArgumentException.class);
        for (String schema : new String[]{"{\"type\":\"nonsense\"}", "{\"$ref\":\"#/missing\"}", "{\"$id\":\"http://127.0.0.1:1/never\"}", "{\"$ref\":\"http://127.0.0.1:1/never\"}", "{\"$schema\":\"http://127.0.0.1:1/never\"}"}) {
            ObjectNode node = SchemaContracts.JSON.valueToTree(manifest()); node.set("inputSchema", SchemaContracts.parse(schema));
            assertThatThrownBy(() -> loader.decode(node)).isInstanceOf(IllegalArgumentException.class);
        }
        tree = SchemaContracts.JSON.valueToTree(manifest()); tree.put("schemaDigest", "0".repeat(64)); final var bad = tree;
        assertThatThrownBy(() -> loader.decode(bad)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pathLoaderReadsAllNineActualContractsAndProtectsMutableSchema() {
        var manifests = new ManifestLoader().load(ROOT.resolve("gateway-app/src/main/resources/manifests/tc"));
        assertThat(manifests).hasSize(10);
        var m = manifests.getFirst(); String before = m.schemaDigest();
        ((ObjectNode)m.inputSchema()).put("type", "null");
        assertThat(SchemaContracts.digest(m.inputSchema(), m.outputSchema())).isEqualTo(before);
        assertThatThrownBy(() -> m.inputLimits().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
