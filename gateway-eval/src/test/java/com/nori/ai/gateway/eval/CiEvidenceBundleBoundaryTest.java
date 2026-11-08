package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.ci.BuildIdentityManifest;
import com.nori.ai.gateway.eval.ci.TarGz;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static com.nori.ai.gateway.eval.TestSupport.error;
import static org.assertj.core.api.Assertions.*;

/**
 * 묶음 부호화·내용 경계(부모 재현 4건 포함): pack 을 우회해 독립적으로 만든 변조 묶음이 모두 거부되는지 본다.
 * 변조 tar 는 이 시험의 별도 writer({@link #tar})로 만든다 — 검증 대상 TarGz 의 writer 를 거치면 만들 수 없는 형태를 포함한다.
 */
class CiEvidenceBundleBoundaryTest {
    static final String SUMMARY = "sub/build/aigw-evidence/integrationTest/leaf-results.json";
    @TempDir Path directory;
    CiEvidenceFixture x;
    Path bundle;
    @BeforeEach void fixture() throws Exception { x = new CiEvidenceFixture(directory); bundle = x.pack(); }

    private void verify(Path file) { x.evidence().verify(file, CiEvidenceFixture.SHA, x.required, x.f.binding(), BuildIdentityManifest.load(x.identity)); }
    private Path file(byte[] bytes) {
        Path out = directory.resolve(UUID.randomUUID() + ".tgz");
        try { Files.write(out, bytes); } catch (IOException e) { throw new UncheckedIOException(e); }
        return out;
    }
    private static byte[] gzip(byte[] body) {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) { gzip.write(body); } catch (IOException e) { throw new UncheckedIOException(e); }
        return out.toByteArray();
    }
    private static byte[] gunzip(Path file) throws IOException { try (var in = new GZIPInputStream(Files.newInputStream(file))) { return in.readAllBytes(); } }
    /** 시험 전용 ustar writer: typeflag 와 끝 표시 뒤 바이트를 마음대로 정한다. */
    private static byte[] tar(List<Map.Entry<String,byte[]>> entries, char type, byte[] afterEnd) {
        var out = new ByteArrayOutputStream();
        for (var entry : entries) {
            byte[] header = new byte[512];
            byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, header, 0, name.length);
            for (var field : List.of(Map.entry(100, "0000644\0"), Map.entry(108, "0000000\0"), Map.entry(116, "0000000\0"),
                    Map.entry(124, String.format("%011o\0", entry.getValue().length)), Map.entry(136, "00000000000\0"), Map.entry(257, "ustar\0"), Map.entry(263, "00"))) {
                byte[] value = field.getValue().getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(value, 0, header, field.getKey(), value.length);
            }
            header[156] = (byte) type;
            long sum = 0; for (int i = 0; i < 512; i++) sum += (i >= 148 && i < 156) ? ' ' : (header[i] & 0xff);
            byte[] checksum = String.format("%06o\0 ", sum).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(checksum, 0, header, 148, checksum.length);
            out.writeBytes(header); out.writeBytes(entry.getValue()); out.writeBytes(new byte[(512 - entry.getValue().length % 512) % 512]);
        }
        out.writeBytes(new byte[1024]); out.writeBytes(afterEnd);
        return out.toByteArray();
    }
    private List<Map.Entry<String,byte[]>> original() { return new ArrayList<>(TarGz.read(bundle).entrySet()); }
    private Path withManifest(ObjectNode manifest, Map<String,byte[]> extra) {
        var entries = new TreeMap<>(TarGz.read(bundle)); entries.putAll(extra);
        entries.put("bundle.json", Json.canonical(manifest));
        Path out = directory.resolve(UUID.randomUUID() + ".tgz"); TarGz.write(out, entries); return out;
    }
    private ObjectNode manifest() { return (ObjectNode) Json.parse(TarGz.read(bundle).get("bundle.json")); }

    @Test void independentlyBuiltLegitimateArchiveIsAccepted() throws Exception {
        verify(file(gzip(tar(original(), '0', new byte[0]))));
    }

    @Test void manifestAcceptsOnlyFixedFieldsInCanonicalBytes() throws Exception {
        ObjectNode unknown = manifest(); unknown.put("rawLog", "sentinel");
        error(() -> verify(withManifest(unknown, Map.of())), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        ObjectNode wrongType = manifest(); wrongType.put("worktreeClean", "true");
        error(() -> verify(withManifest(wrongType, Map.of())), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        // 중복 키(뒤 값이 이기는 파서)를 정규 바이트 비교로 거부한다.
        String canonical = new String(TarGz.read(bundle).get("bundle.json"), StandardCharsets.UTF_8);
        byte[] duplicated = canonical.replaceFirst("^\\{", "{\"gitSha\":\"" + "0".repeat(40) + "\",").getBytes(StandardCharsets.UTF_8);
        var entries = new TreeMap<>(TarGz.read(bundle)); entries.put("bundle.json", duplicated);
        Path out = directory.resolve("duplicate.tgz"); TarGz.write(out, entries);
        error(() -> verify(out), 4, "CI_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void summaryOfUnregisteredModuleIsRejectedEvenWhenListed() throws Exception {
        String extra = "other/build/aigw-evidence/integrationTest/leaf-results.json";
        byte[] raw = "{\"summary\":{\"raw\":\"sentinel\"},\"records\":[]}".getBytes(StandardCharsets.UTF_8);
        ObjectNode listed = manifest(); ((ObjectNode) listed.get("files")).put(extra, Json.sha(raw));
        error(() -> verify(withManifest(listed, Map.of(extra, raw))), 4, "CI_EVIDENCE_UNEXPECTED_ENTRY");
    }

    @Test void registeredSummaryWithRawFieldOrWrongTaskIsRejected() throws Exception {
        for (var change : List.of(Map.<String,Object>of("raw", "sentinel"), Map.<String,Object>of("taskPath", ":other:integrationTest"))) {
            ObjectNode summary = x.summary.deepCopy();
            change.forEach((key, value) -> summary.set(key, Json.MAPPER.valueToTree(value)));
            EvidenceRecord.seal(summary);
            byte[] bytes = Json.canonical(Json.MAPPER.valueToTree(Map.of("summary", summary, "records", x.records)));
            ObjectNode listed = manifest(); ((ObjectNode) listed.get("files")).put(SUMMARY, Json.sha(bytes));
            error(() -> verify(withManifest(listed, Map.of(SUMMARY, bytes))), 4, change.containsKey("raw") ? "CI_EVIDENCE_RECORD_INVALID" : "CI_EVIDENCE_TASK_MISMATCH");
        }
        byte[] extraTop = Json.canonical(Json.MAPPER.valueToTree(Map.of("summary", x.summary, "records", x.records, "rawXml", "<testcase/>")));
        ObjectNode listed = manifest(); ((ObjectNode) listed.get("files")).put(SUMMARY, Json.sha(extraTop));
        error(() -> verify(withManifest(listed, Map.of(SUMMARY, extraTop))), 4, "CI_EVIDENCE_RECORD_INVALID");
    }

    @Test void corruptedOrConcatenatedGzipIsRejected() throws Exception {
        byte[] good = Files.readAllBytes(bundle);
        byte[] crc = good.clone(); crc[crc.length - 8] ^= 0x01;
        error(() -> verify(file(crc)), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        byte[] size = good.clone(); size[size.length - 1] ^= 0x01;
        error(() -> verify(file(size)), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        byte[] hidden = gzip("hidden member".getBytes(StandardCharsets.UTF_8));
        byte[] concatenated = Arrays.copyOf(good, good.length + hidden.length); System.arraycopy(hidden, 0, concatenated, good.length, hidden.length);
        error(() -> verify(file(concatenated)), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        error(() -> verify(file(trailing)), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        // 정상 gzip 안에서도 tar 끝 표시 뒤 데이터는 숨긴 항목이다.
        byte[] afterEnd = new byte[512]; afterEnd[0] = 'x';
        error(() -> verify(file(gzip(tar(original(), '0', afterEnd)))), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        assertThat(gunzip(bundle).length % 512).isZero();
    }

    @Test void linksDuplicatesAndOversizeAreRejected() throws Exception {
        error(() -> verify(file(gzip(tar(original(), '2', new byte[0])))), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        var duplicate = original(); duplicate.add(Map.entry(SUMMARY, TarGz.read(bundle).get(SUMMARY)));
        error(() -> verify(file(gzip(tar(duplicate, '0', new byte[0])))), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        // 해제 크기 상한: 압축은 작아도 해제 결과가 상한을 넘으면 할당 전에 거부한다.
        var huge = original(); huge.add(Map.entry("sub/padding.bin", new byte[17 * 1024 * 1024]));
        byte[] bomb = gzip(tar(huge, '0', new byte[0]));
        assertThat(bomb.length).isLessThan(1024 * 1024);
        error(() -> verify(file(bomb)), 4, "CI_EVIDENCE_BUNDLE_INVALID");
    }

    /** root 재현(ci-boundary-probe-r2 nonzeroTarPadding): 내용·해시는 그대로 두고 패딩·헤더 빈 영역에 숨긴 데이터도 거부한다. */
    @Test void hiddenBytesInPaddingOrUnusedHeaderFieldsAreRejected() throws Exception {
        var entries = original();
        int firstSize = entries.getFirst().getValue().length;
        assertThat(firstSize % 512).as("fixture 첫 항목에 패딩이 있어야 한다").isNotZero();
        byte[] padded = tar(entries, '0', new byte[0]);
        byte[] payload = "SYNTHETIC_PADDING_PAYLOAD".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(payload, 0, padded, 512 + firstSize, Math.min(payload.length, 512 - firstSize % 512));
        error(() -> verify(file(gzip(padded))), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        for (int field : List.of(157, 265, 297, 345)) {
            byte[] header = tar(entries, '0', new byte[0]);
            byte[] value = "hidden".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(value, 0, header, field, value.length);
            long sum = 0; for (int i = 0; i < 512; i++) sum += (i >= 148 && i < 156) ? ' ' : (header[i] & 0xff);
            byte[] checksum = String.format("%06o\0 ", sum).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(checksum, 0, header, 148, checksum.length);
            error(() -> verify(file(gzip(header))), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        }
        // 같은 내용을 정상 형태로 다시 만들면 통과한다(거부가 내용 때문이 아님).
        verify(file(gzip(tar(entries, '0', new byte[0]))));
    }
}
