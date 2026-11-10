package com.nori.ai.gateway.app;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CoreSystemBranchGuardTest {
    private static final Set<String> SYSTEMS = Set.of("TC", "WM", "HRM");

    @Test void coreConditionsDoNotBranchByOwnerSystem() throws Exception {
        Path source = Path.of(System.getProperty("aigw.rootDir"), "gateway-core/src/main/java");
        assertThat(source).isDirectory();
        try (var paths = Files.walk(source)) {
            List<Path> files = paths.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("OwnerSystem.java")).toList();
            assertThat(files).isNotEmpty();
            for (Path file : files) assertThat(violations(Files.readString(file))).as("시스템별 분기: %s", file).isEmpty();
        }
    }

    @Test void rejectsStaticImportsQualifiedConstantsStringsAndSwitchLabels() throws Exception {
        for (String system : SYSTEMS) {
            assertThat(violations("import static probe.OwnerSystem." + system + "; class Probe { void check(Object owner) { if (owner == " + system + ") {} } }" )).isNotEmpty();
            assertThat(violations("import static probe.OwnerSystem.*; class Probe { void check(Object owner) { if (owner == " + system + ") {} } }" )).isNotEmpty();
            assertThat(violations("class Probe { void check(Object owner) { if (owner == OwnerSystem." + system + ") {} } }" )).isNotEmpty();
            assertThat(violations("class Probe { void check(String owner) { if (\"" + system + "\".equals(owner)) {} } }" )).isNotEmpty();
            assertThat(violations("class Probe { void check(OwnerSystem owner) { switch (owner) { case " + system + " -> {} } } }" )).isNotEmpty();
            assertThat(violations("class Probe { int check(OwnerSystem owner) { return switch (owner) { case " + system + " -> 1; default -> 0; }; } }" )).isNotEmpty();
        }
    }

    @Test void permitsCommentsDataAndEnumDefinitions() throws Exception {
        assertThat(violations("import static probe.OwnerSystem.*; class Probe { /* if (owner == TC) */ String data = \"TC\"; Object value = TC; void check(boolean enabled) { if (enabled) { String data = \"WM\"; } } }" )).isEmpty();
        assertThat(violations("enum OwnerSystem { TC, WM, HRM }" )).isEmpty();
    }

    private List<String> violations(String source) throws Exception {
        List<String> found = new ArrayList<>();
        var input = new SimpleJavaFileObject(URI.create("string:///Probe.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, manager, null, List.of("-proc:none"), null, List.of(input));
            for (var unit : task.parse()) {
                Set<String> imported = new HashSet<>();
                for (ImportTree item : unit.getImports()) {
                    if (!item.isStatic()) continue;
                    String name = item.getQualifiedIdentifier().toString();
                    for (String system : SYSTEMS) {
                        if (name.endsWith(".OwnerSystem." + system) || name.endsWith(".OwnerSystem.*")) imported.add(system);
                    }
                }
                new TreeScanner<Void,Void>() {
                    private void condition(Tree tree, boolean caseLabel) {
                        new TreeScanner<Void,Void>() {
                            @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                                String name = node.getName().toString();
                                if (SYSTEMS.contains(name) && (caseLabel || imported.contains(name))) found.add(name);
                                return super.visitIdentifier(node, unused);
                            }
                            @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                                String owner = node.getExpression().toString();
                                if ((owner.equals("OwnerSystem") || owner.endsWith(".OwnerSystem")) && SYSTEMS.contains(node.getIdentifier().toString())) found.add(node.toString());
                                return super.visitMemberSelect(node, unused);
                            }
                            @Override public Void visitLiteral(LiteralTree node, Void unused) {
                                if (node.getValue() instanceof String text && SYSTEMS.contains(text)) found.add(text);
                                return super.visitLiteral(node, unused);
                            }
                        }.scan(tree, null);
                    }
                    @Override public Void visitIf(IfTree tree, Void unused) { condition(tree.getCondition(), false); return super.visitIf(tree, unused); }
                    @Override public Void visitConditionalExpression(ConditionalExpressionTree tree, Void unused) { condition(tree.getCondition(), false); return super.visitConditionalExpression(tree, unused); }
                    @Override public Void visitSwitch(SwitchTree tree, Void unused) { condition(tree.getExpression(), false); return super.visitSwitch(tree, unused); }
                    @Override public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) { condition(tree.getExpression(), false); return super.visitSwitchExpression(tree, unused); }
                    @Override public Void visitCase(CaseTree tree, Void unused) { tree.getExpressions().forEach(expression -> condition(expression, true)); return super.visitCase(tree, unused); }
                    @Override public Void visitWhileLoop(WhileLoopTree tree, Void unused) { condition(tree.getCondition(), false); return super.visitWhileLoop(tree, unused); }
                    @Override public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) { condition(tree.getCondition(), false); return super.visitDoWhileLoop(tree, unused); }
                    @Override public Void visitForLoop(ForLoopTree tree, Void unused) { if (tree.getCondition() != null) condition(tree.getCondition(), false); return super.visitForLoop(tree, unused); }
                }.scan(unit, null);
            }
        }
        return found;
    }
}
