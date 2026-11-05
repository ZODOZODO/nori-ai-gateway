package com.nori.ai.gateway.core.registry;

import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class ManifestLoader {
    private final JsonNode manifestSchema = resource("contracts/capability-manifest.schema.json");
    private final JsonNode workflowSchema = resource("contracts/workflow.schema.json");
    public List<CapabilityManifest> load(Path path) {
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                return files.filter(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("index.json"))
                    .sorted().map(this::loadOne).toList();
            } catch (IOException e) { throw new IllegalArgumentException("Manifest를 읽을 수 없습니다"); }
        }
        return List.of(loadOne(path));
    }
    public List<CapabilityManifest> loadClasspath(String index) {
        return indexed(index).stream().map(name -> decode(resource(name))).toList();
    }
    public List<WorkflowDefinition> loadWorkflowsClasspath(String index) {
        return indexed(index).stream().map(name -> decodeWorkflow(resource(name))).toList();
    }
    public WorkflowDefinition loadWorkflow(Path path) { return decodeWorkflow(read(path)); }
    private CapabilityManifest loadOne(Path path) { return decode(read(path)); }
    public CapabilityManifest decode(JsonNode node) {
        if (!SchemaContracts.matches(manifestSchema, node)) throw new IllegalArgumentException("Manifest 계약이 잘못되었습니다");
        SchemaContracts.validateSchema(node.get("inputSchema")); SchemaContracts.validateSchema(node.get("outputSchema"));
        CapabilityManifest manifest;
        try { manifest = SchemaContracts.JSON.treeToValue(node, CapabilityManifest.class); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Manifest 계약이 잘못되었습니다"); }
        if (!SchemaContracts.digest(manifest.inputSchema(), manifest.outputSchema()).equals(manifest.schemaDigest()))
            throw new IllegalArgumentException("Schema digest가 일치하지 않습니다");
        return manifest;
    }
    public WorkflowDefinition decodeWorkflow(JsonNode node) {
        if (!SchemaContracts.matches(workflowSchema, node)) throw new IllegalArgumentException("Workflow 계약이 잘못되었습니다");
        try { return SchemaContracts.JSON.treeToValue(node, WorkflowDefinition.class); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Workflow 계약이 잘못되었습니다"); }
    }
    private List<String> indexed(String name) {
        JsonNode node = resource(name);
        if (!node.isArray() || node.isEmpty()) throw new IllegalArgumentException("계약 목록이 비었습니다");
        List<String> paths = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString() || item.asString().contains("..") || item.asString().contains(":") || item.asString().startsWith("/"))
                throw new IllegalArgumentException("계약 경로가 잘못되었습니다");
            paths.add(item.asString());
        }
        if (new HashSet<>(paths).size() != paths.size()) throw new IllegalArgumentException("계약 경로가 중복되었습니다");
        return List.copyOf(paths);
    }
    private JsonNode read(Path path) {
        try { return SchemaContracts.parse(Files.readString(path)); }
        catch (IOException e) { throw new IllegalArgumentException("계약 파일을 읽을 수 없습니다"); }
    }
    private JsonNode resource(String name) {
        try (InputStream input = ManifestLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalArgumentException("계약 리소스가 없습니다");
            return SchemaContracts.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalArgumentException("계약 리소스를 읽을 수 없습니다"); }
    }
}
