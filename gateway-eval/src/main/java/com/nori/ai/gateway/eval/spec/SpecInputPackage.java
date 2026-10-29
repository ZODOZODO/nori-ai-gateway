package com.nori.ai.gateway.eval.spec;

import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

public final class SpecInputPackage {
    public static final int MAX_COMPRESSED = 2_097_152;
    public static final int MAX_EXPANDED = 4_194_304;
    public record Response(int status, byte[] body) {}
    @FunctionalInterface public interface RegistryTransport {
        Response exchange(String method, URI uri, String header, String token, byte[] body);
    }
    private final Map<String,String> env;
    private final RegistryTransport transport;
    public SpecInputPackage(Map<String,String> env) { this(env, new HttpTransport()); }
    public SpecInputPackage(Map<String,String> env, RegistryTransport transport) { this.env = Map.copyOf(env); this.transport = transport; }
    private static final class HttpTransport implements RegistryTransport {
        private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();
        @Override public Response exchange(String method, URI uri, String header, String token, byte[] body) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header(header, token)
                    .method(method, method.equals("PUT") ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream input = response.body()) {
                    byte[] bytes = input.readNBytes(MAX_COMPRESSED + 1);
                    if (bytes.length > MAX_COMPRESSED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
                    return new Response(response.statusCode(), bytes);
                }
            } catch (IOException | IllegalArgumentException e) { throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE"); }
        }
    }
    private URI endpoint(String api, String projectId, String sha) {
        try {
            URI uri = URI.create(api);
            if (!"https".equals(uri.getScheme()) || !"gitlab.noricloud.org".equals(uri.getHost()) || !(uri.getPort() == -1 || uri.getPort() == 443) || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !"/api/v4".equals(uri.getRawPath()) || !projectId.matches("[0-9]+")) throw new EvalException(2, "SPEC_PACKAGE_HOST_FORBIDDEN");
            return URI.create(api + "/projects/" + projectId + "/packages/generic/aigw-spec-input/" + sha + "/spec.md.gz");
        } catch (IllegalArgumentException e) { throw new EvalException(2, "SPEC_PACKAGE_HOST_FORBIDDEN"); }
    }
    private String token(String name, String file, String header) {
        if (!Set.of("JOB-TOKEN", "PRIVATE-TOKEN", "DEPLOY-TOKEN").contains(header == null ? "" : header) || (name == null) == (file == null)) throw new EvalException(2, "SPEC_PACKAGE_TOKEN_MISSING");
        String value;
        try { value = name != null ? env.get(name) : Files.readString(Path.of(file), StandardCharsets.UTF_8).strip(); }
        catch (IOException | InvalidPathException e) { throw new EvalException(2, "SPEC_PACKAGE_TOKEN_MISSING"); }
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) throw new EvalException(2, "SPEC_PACKAGE_TOKEN_MISSING");
        return value;
    }
    public Path pack(String spec, Path baseline, Path output) {
        clean(output);
        try {
            SpecGuard.CheckedSpec checked = new SpecGuard(env).readAndCheck(spec, null, baseline);
            return writeSnapshot(checked, output);
        } catch (RuntimeException e) { clean(output); throw e; }
    }
    private Path writeSnapshot(SpecGuard.CheckedSpec checked, Path output) {
        try {
            SpecBinding binding = checked.binding(); byte[] original = checked.bytes();
            if (original.length > MAX_EXPANDED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
            byte[] compressed = compress(original);
            byte[] expanded = expand(compressed);
            if (!Arrays.equals(original, expanded)) throw new EvalException(3, "SPEC_PACKAGE_CONTENT_MISMATCH");
            Path directory = output.resolve(binding.specSha());
            Files.createDirectories(directory);
            Files.write(directory.resolve("spec.md.gz"), compressed);
            Json.write(directory.resolve("pack-manifest.json"), manifest(binding.specSha(), compressed, original.length));
            return directory.resolve("spec.md.gz");
        } catch (IOException e) { clean(output); throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
        catch (RuntimeException e) { clean(output); throw e; }
    }
    public Map<String,Object> publish(String spec, Path baseline, Path work, String api, String project, String header, String tokenEnv, String tokenFile) {
        clean(work);
        try {
            endpoint(api, project, "0".repeat(64));
            String token = token(tokenEnv, tokenFile, header);
            SpecGuard.CheckedSpec checked = new SpecGuard(env).readAndCheck(spec, null, baseline);
            String sha = checked.binding().specSha(); URI uri = endpoint(api, project, sha);
            Path packed = writeSnapshot(checked, work);
            byte[] compressed = Json.bytes(packed);
            if (!Json.sha(expand(compressed)).equals(sha)) throw new EvalException(3, "SPEC_PACKAGE_CONTENT_MISMATCH");
            Response response = request("GET", uri, header, token, null);
            boolean reused;
            if (response.status() == 200) {
                if (!Json.sha(expand(response.body())).equals(sha)) throw new EvalException(3, "SPEC_PACKAGE_CONTENT_MISMATCH");
                reused = true;
            } else if (response.status() == 404) {
                Response uploaded = request("PUT", uri, header, token, compressed);
                if (uploaded.status() < 200 || uploaded.status() >= 300) throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE");
                Response verified = request("GET", uri, header, token, null);
                if (verified.status() != 200) throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE");
                if (!Json.sha(expand(verified.body())).equals(sha)) throw new EvalException(3, "SPEC_PACKAGE_CONTENT_MISMATCH");
                reused = false;
            } else throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE");
            return Map.of("package", "aigw-spec-input", "version", sha, "compressedBytes", compressed.length, "state", reused ? "SPEC_PACKAGE_REUSED" : "SPEC_PACKAGE_UPLOADED");
        } catch (RuntimeException e) { clean(work); throw e; }
    }
    public Path fetch(String sha, Path baseline, Path output, String api, String project, String header, String tokenEnv, String tokenFile) {
        clean(output);
        try {
            if (sha == null || !sha.matches("[0-9a-f]{64}")) throw new EvalException(2, "SPEC_SHA_INVALID");
            if (!SpecBaseline.parseChain(baseline).currentSha().equals(sha)) throw new EvalException(3, "SPEC_BASELINE_MISMATCH");
            URI uri = endpoint(api, project, sha);
            String token = token(tokenEnv, tokenFile, header);
            Response response = request("GET", uri, header, token, null);
            if (response.status() != 200) throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE");
            byte[] expanded = expand(response.body());
            if (!Json.sha(expanded).equals(sha)) throw new EvalException(3, "SPEC_PACKAGE_CONTENT_MISMATCH");
            Path target = output.resolve(sha);
            Files.createDirectories(output);
            Path temporary = Files.createTempDirectory(output, ".aigw-spec-");
            try {
                Files.write(temporary.resolve("spec.md"), expanded);
                Map<String,Object> receipt = new LinkedHashMap<>(manifest(sha, response.body(), expanded.length));
                receipt.put("package", "aigw-spec-input"); receipt.put("version", sha); receipt.put("apiHost", uri.getHost());
                Json.write(temporary.resolve("fetch-receipt.json"), receipt);
                if (Files.exists(target)) Files.delete(target);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary.resolve("spec.md")); Files.deleteIfExists(temporary.resolve("fetch-receipt.json")); Files.deleteIfExists(temporary);
            }
            return target.resolve("spec.md");
        } catch (IOException e) { clean(output); throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
        catch (RuntimeException e) { clean(output); throw e; }
    }
    private Response request(String method, URI uri, String header, String token, byte[] body) {
        Response response;
        try { response = transport.exchange(method, uri, header, token, body); }
        catch (EvalException e) { throw e; }
        catch (RuntimeException e) { throw new EvalException(5, "SPEC_PACKAGE_UNAVAILABLE"); }
        if (response.status() >= 300 && response.status() < 400) throw new EvalException(2, "SPEC_PACKAGE_HOST_FORBIDDEN");
        if (response.body().length > MAX_COMPRESSED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
        return response;
    }
    private static Map<String,Object> manifest(String sha, byte[] compressed, int expandedBytes) {
        return Map.of("sha256", sha, "expandedBytes", expandedBytes, "compressedBytes", compressed.length, "compressedSha256", Json.sha(compressed));
    }
    public static byte[] compress(byte[] bytes) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) { gzip.write(bytes); }
            byte[] output = buffer.toByteArray();
            if (output.length > MAX_COMPRESSED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
            return output;
        } catch (IOException e) { throw new EvalException(4, "SPEC_PACKAGE_INVALID_GZIP"); }
    }
    // GZIPInputStream은 연결 member와 후행 바이트를 허용하므로 경계를 별도로 검증합니다.
    public static byte[] expand(byte[] gzip) {
        if (gzip.length > MAX_COMPRESSED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
        Inflater inflater = new Inflater(true);
        try {
            if (gzip.length < 18 || (gzip[0] & 255) != 31 || (gzip[1] & 255) != 139 || gzip[2] != 8 || (gzip[3] & 0xe0) != 0) throw invalid();
            int flags = gzip[3] & 255, offset = 10;
            if ((flags & 4) != 0) { int len = unsigned16(gzip, offset); offset += 2 + len; }
            if ((flags & 8) != 0) { while (offset < gzip.length && gzip[offset++] != 0) { } }
            if ((flags & 16) != 0) { while (offset < gzip.length && gzip[offset++] != 0) { } }
            if ((flags & 2) != 0) {
                CRC32 header = new CRC32(); header.update(gzip, 0, offset);
                if ((header.getValue() & 65535) != unsigned16(gzip, offset)) throw invalid(); offset += 2;
            }
            if (offset > gzip.length - 8) throw invalid();
            inflater.setInput(gzip, offset, gzip.length - offset);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(chunk);
                if (buffer.size() + count > MAX_EXPANDED) throw new EvalException(4, "SPEC_INPUT_OVERSIZED");
                buffer.write(chunk, 0, count);
                if (count == 0 && !inflater.finished()) throw invalid();
            }
            if (inflater.getRemaining() != 8) throw invalid();
            byte[] expanded = buffer.toByteArray(); CRC32 crc = new CRC32(); crc.update(expanded);
            int trailer = gzip.length - 8;
            if (unsigned32(gzip, trailer) != crc.getValue() || unsigned32(gzip, trailer + 4) != expanded.length) throw invalid();
            return expanded;
        } catch (DataFormatException | IndexOutOfBoundsException e) { throw invalid(); }
        finally { inflater.end(); }
    }
    private static int unsigned16(byte[] b, int i) { return (b[i] & 255) | ((b[i + 1] & 255) << 8); }
    private static long unsigned32(byte[] b, int i) { return (b[i] & 255L) | ((b[i + 1] & 255L) << 8) | ((b[i + 2] & 255L) << 16) | ((b[i + 3] & 255L) << 24); }
    private static EvalException invalid() { return new EvalException(4, "SPEC_PACKAGE_INVALID_GZIP"); }
    private static void clean(Path output) {
        if (!Files.isDirectory(output)) return;
        try (var children = Files.list(output)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child) && child.getFileName().toString().matches("[0-9a-f]{64}|\\.aigw-spec-.*")) {
                    for (String name : List.of("spec.md", "spec.md.gz", "pack-manifest.json", "fetch-receipt.json")) Files.deleteIfExists(child.resolve(name));
                    try (var remaining = Files.list(child)) { if (remaining.findAny().isEmpty()) Files.delete(child); }
                }
            }
        } catch (IOException e) { throw new EvalException(5, "OUTPUT_CLEANUP_FAILED"); }
    }
}
