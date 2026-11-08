package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import com.nori.ai.gateway.eval.spec.SpecGuard;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

final class ArgLimitsSpecEvidence {
    private static final String APPROVAL = "**분류 인자 계약 v2(2026-10-30 사용자 승인, G-ARG-LIMITS 정본):**";
    private ArgLimitsSpecEvidence() {}

    static String current(Path root) {
        // 기존 resolver가 선택한 실제 승인 바이트만 사용한다. sibling의 가변 파일을 정본으로 추정하지 않는다.
        return current(root, System.getenv(), System.getProperty("aigw.specPath"));
    }

    static String current(Path root, Map<String, String> environment, String property) {
        var checked = new SpecGuard(environment).readAndCheck(null, property, root.resolve("docs/gates/spec-baseline.md"));
        return new String(checked.bytes(), StandardCharsets.UTF_8);
    }

    static void historical(Path root, boolean v2) {
        Path input = root.resolve(v2 ? "eval/f0/v2/arg-limits-v2.json" : "eval/f0/arg-limits-v1.json");
        assertThat(Json.sha(Json.bytes(input))).isEqualTo(v2
            ? "cf05c51bd34159ed74a6071ca51bdd281a571088de78c5c9217c2a2e0f76213d"
            : "4fc0c6bbb9c56d2a7ecfabaec2274eb738e4bff28c1af2bfea6602089242315a");
        var chain = SpecBaseline.parseChain(root.resolve("docs/gates/spec-baseline.md"));
        assertThat(chain.entries().get(0).path("sha256").asText()).isEqualTo("14d6cc2efa2c81f699f3073f2590b7272db1b48ffbe2dfb3677bf7127ed18ef8");
        assertThat(chain.entries().get(1).path("sha256").asText()).isEqualTo("550e97ff012725b2380aa862bc6dccf0bca3db8e64b8f4cf406286af03a53ec7");
        String document = new String(Json.bytes(root.resolve("docs/gates/f0/arg-limits.md")), StandardCharsets.UTF_8);
        int split = document.indexOf("## v2 승인 계약 (2026-10-30)");
        assertThat(split).isGreaterThan(0);
        String table = v2 ? document.substring(split) : document.substring(0, split);
        // 당시 SPEC 원문 바이트는 이 자료에 없으므로 고정 줄번호는 역사 메타데이터로만 대조한다.
        // 현재 계약의 의미 검증은 아래 승인 SHA·절·문단 경계에서 별도로 수행한다.
        for (JsonNode field : Json.read(input).path("fields")) {
            String row = uniqueLine(table, "| " + field.path("field").asText() + " |");
            for (JsonNode evidence : field.path("evidence")) assertThat(row).contains(evidence.path("ref").asText());
        }
    }

    static String section(String document) {
        String heading = uniqueLine(document, "### 14.5 ");
        int start = document.indexOf(heading);
        int end = document.indexOf("\n### ", start + heading.length());
        assertThat(end).isGreaterThan(start);
        return document.substring(start, end);
    }

    private static String inputs(String section) {
        String heading = uniqueLine(section, APPROVAL);
        int start = section.indexOf(heading);
        int end = section.indexOf("\n**", start + heading.length());
        assertThat(end).isGreaterThan(start);
        return section.substring(start, end);
    }

    static void classification(String document, ArgLimits.Limit limit) {
        String schema = section(document);
        String paragraph = uniqueLine(schema, "**압축 출력 스키마·검증·재시도(D51, AC-32):**");
        var enumeration = Pattern.compile("`t` ∈ ([A-Z/]+) =").matcher(paragraph);
        assertThat(enumeration.find()).isTrue();
        assertThat(enumeration.group(1)).isEqualTo(limit.format().substring("ENUM:".length()).replace('|', '/'));
        assertThat(enumeration.find()).isFalse();
        assertThat(limit.maxChars()).isEqualTo(3); assertThat(limit.charUnit()).isEqualTo("CODEPOINT");
        assertThat(uniqueLine(inputs(schema), APPROVAL)).contains("`t`는 위 enum");
    }

    static void timeAndQuery(String document, ArgLimits limits) {
        String approved = inputs(section(document));
        String time = uniqueLine(approved, "- **시간:**");
        assertThat(time).contains("`uuuu-MM-dd'T'HH:mm[:ss[.fraction]][offset]`", "소수초는 초가 있을 때만 1~9자리", "`Z` 또는 `±HH:mm`",
            "엄격한 실제 달력", "나노초 값과 같은 instant를 보존", "밀리초·분으로 내리지 않는다");
        for (String field : new String[]{"from", "to"}) {
            assertThat(limits.get(field).format()).isEqualTo("DATETIME:uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx");
            assertThat(limits.get(field).charUnit()).isEqualTo("CODEPOINT");
            assertNumber(time, "최장 ([0-9]+) CODEPOINT", limits.get(field).maxChars());
        }
        String query = uniqueLine(approved, "- **검색어 원문과 참조:**");
        assertThat(limits.get("q").format()).isEqualTo("TEXT"); assertThat(limits.get("q").charUnit()).isEqualTo("CODEPOINT");
        assertNumber(query, "최대 ([0-9]+) Unicode 코드포인트", limits.get("q").maxChars());
        assertThat(query).contains("디코드 후 원문 코드포인트 수", "UTF-16 코드 유닛·UTF-8 바이트·JSON escape 길이가 아님", "공백 trim·Unicode 정규화·대소문자 변환·절삭");
        String failure = uniqueLine(approved, "- **초과·실패:**");
        assertNumber(failure, "후보 검색어가 하나라도 ([0-9]+) CODEPOINT", limits.get("q").maxChars());
        assertThat(failure).contains("참조를 발급하거나 분류를 호출하기 전에 절삭·대체·검색 없이", "`FAILED`/`INPUT_BUDGET_EXCEEDED`", "분류·Source 검색·Composer 호출은 0건", "1회 재시도", "`FAILED`/`PLAN_INVALID`");
    }

    private static void assertNumber(String text, String regex, int expected) {
        var match = Pattern.compile(regex).matcher(text);
        assertThat(match.find()).isTrue(); assertThat(Integer.parseInt(match.group(1))).isEqualTo(expected); assertThat(match.find()).isFalse();
    }

    private static String uniqueLine(String text, String prefix) {
        var lines = text.lines().filter(line -> line.startsWith(prefix)).toList();
        assertThat(lines).as("유일한 계약 anchor: %s", prefix).hasSize(1);
        return lines.getFirst();
    }
}
