package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.node.TextNode;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonCanonicalTest {
    @Test void matchesEveryFiniteRfc8785AppendixBNumberVector() {
        String[][] vectors = {
            {"0000000000000000","0"},{"8000000000000000","0"},{"0000000000000001","5e-324"},{"8000000000000001","-5e-324"},
            {"7fefffffffffffff","1.7976931348623157e+308"},{"ffefffffffffffff","-1.7976931348623157e+308"},{"4340000000000000","9007199254740992"},{"c340000000000000","-9007199254740992"},
            {"4430000000000000","295147905179352830000"},{"44b52d02c7e14af5","9.999999999999997e+22"},{"44b52d02c7e14af6","1e+23"},{"44b52d02c7e14af7","1.0000000000000001e+23"},
            {"444b1ae4d6e2ef4e","999999999999999700000"},{"444b1ae4d6e2ef4f","999999999999999900000"},{"444b1ae4d6e2ef50","1e+21"},{"3eb0c6f7a0b5ed8c","9.999999999999997e-7"},{"3eb0c6f7a0b5ed8d","0.000001"},
            {"41b3de4355555553","333333333.3333332"},{"41b3de4355555554","333333333.33333325"},{"41b3de4355555555","333333333.3333333"},{"41b3de4355555556","333333333.3333334"},{"41b3de4355555557","333333333.33333343"},
            {"becbf647612f3696","-0.0000033333333333333333"},{"43143ff3c1cb0959","1424953923781206.2"}
        };
        for (String[] vector : vectors) assertThat(Json.canonicalString(com.fasterxml.jackson.databind.node.DoubleNode.valueOf(Double.longBitsToDouble(Long.parseUnsignedLong(vector[0],16))))).as(vector[0]).isEqualTo(vector[1]);
        for (double invalid : new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) assertThatThrownBy(()->Json.canonicalString(com.fasterxml.jackson.databind.node.DoubleNode.valueOf(invalid))).isInstanceOf(EvalException.class);
    }
    @Test void canonicalizesObjectsNumbersAndEscapes() {
        assertThat(Json.canonicalString(Json.parse("{\"z\":1e23,\"a\":0.000001,\"b\":-0.0}".getBytes(StandardCharsets.UTF_8))))
            .isEqualTo("{\"a\":0.000001,\"b\":0,\"z\":1e+23}");
        assertThat(Json.canonicalString(TextNode.valueOf("\u000f\\u000F"))).isEqualTo("\"\\u000f\\\\u000F\"");
    }
    @Test void rejectsDuplicateKeysTrailingDocumentsAndLoneSurrogates() {
        assertThatThrownBy(() -> Json.parse("{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8))).isInstanceOf(EvalException.class);
        assertThatThrownBy(() -> Json.parse("{} {\"token\":\"sentinel\"}".getBytes(StandardCharsets.UTF_8))).isInstanceOf(EvalException.class);
        assertThatThrownBy(() -> Json.canonicalString(TextNode.valueOf("\ud800"))).isInstanceOf(EvalException.class);
    }
    @Test void serializesEveryControlCharacterUsingRfc8785Escapes() {
        for (int c = 0; c < 32; c++) {
            String expected = switch (c) { case 8 -> "\\b"; case 9 -> "\\t"; case 10 -> "\\n"; case 12 -> "\\f"; case 13 -> "\\r"; default -> String.format("\\u%04x", c); };
            assertThat(Json.canonicalString(TextNode.valueOf(Character.toString(c)))).isEqualTo("\"" + expected + "\"");
        }
    }
}
