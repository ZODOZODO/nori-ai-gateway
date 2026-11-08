package com.nori.ai.gateway.eval.ci;

import com.nori.ai.gateway.eval.EvalException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/**
 * CI 증거 묶음 전용 최소 ustar+gzip. 일반 파일 항목만, 결정론 헤더(mtime·uid·gid 0, mode 0644).
 * 읽기는 이 writer 가 만드는 정확한 형태만 받는다: gzip member 1개(FLG 0)·CRC32/ISIZE 일치·뒤따르는 바이트 0,
 * tar 는 끝 표시(0 블록 2개)로 정확히 끝나야 한다. 부호화·해제 크기는 할당 전에 상한으로 막는다.
 */
public final class TarGz {
    static final int MAX_ENTRIES = 64;
    static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    static final int MAX_EXPANDED_BYTES = 16 * 1024 * 1024;
    static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;
    private static final int GZIP_HEADER = 10, GZIP_TRAILER = 8, BLOCK = 512;
    private TarGz() {}

    public static void write(Path out, SortedMap<String,byte[]> entries) {
        Path temporary = out.resolveSibling(out.getFileName() + ".tmp");
        try (OutputStream file = Files.newOutputStream(temporary); GZIPOutputStream gzip = new GZIPOutputStream(file)) {
            for (var entry : entries.entrySet()) {
                requireName(entry.getKey());
                gzip.write(header(entry.getKey(), entry.getValue().length));
                gzip.write(entry.getValue());
                gzip.write(new byte[(BLOCK - entry.getValue().length % BLOCK) % BLOCK]);
            }
            gzip.write(new byte[2 * BLOCK]);
        } catch (IOException e) { throw new EvalException(4, "CI_EVIDENCE_WRITE_FAILED"); }
        try { Files.move(temporary, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException e) { throw new EvalException(4, "CI_EVIDENCE_WRITE_FAILED"); }
    }

    public static LinkedHashMap<String,byte[]> read(Path in) {
        if (!Files.isRegularFile(in, LinkOption.NOFOLLOW_LINKS)) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_MISSING");
        try {
            if (Files.size(in) > MAX_ENCODED_BYTES) throw invalid();
            return entries(gunzip(Files.readAllBytes(in)));
        } catch (IOException e) { throw invalid(); }
    }

    /** gzip member 정확히 1개. 이어 붙인 member·후행 바이트·CRC/ISIZE 불일치·상한 초과는 모두 거부. */
    private static byte[] gunzip(byte[] encoded) {
        if (encoded.length < GZIP_HEADER + GZIP_TRAILER || (encoded[0] & 0xff) != 0x1f || (encoded[1] & 0xff) != 0x8b || encoded[2] != 8 || encoded[3] != 0) throw invalid();
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(encoded, GZIP_HEADER, encoded.length - GZIP_HEADER);
            var expanded = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw invalid();
                if (expanded.size() + count > MAX_EXPANDED_BYTES) throw invalid();
                expanded.write(buffer, 0, count);
            }
            if (inflater.getRemaining() != GZIP_TRAILER) throw invalid();
            byte[] body = expanded.toByteArray();
            ByteBuffer trailer = ByteBuffer.wrap(encoded, encoded.length - GZIP_TRAILER, GZIP_TRAILER).order(ByteOrder.LITTLE_ENDIAN);
            CRC32 crc = new CRC32(); crc.update(body);
            if ((trailer.getInt() & 0xffffffffL) != crc.getValue() || (trailer.getInt() & 0xffffffffL) != (body.length & 0xffffffffL)) throw invalid();
            return body;
        } catch (DataFormatException e) { throw invalid(); }
        finally { inflater.end(); }
    }

    private static LinkedHashMap<String,byte[]> entries(byte[] tar) {
        var entries = new LinkedHashMap<String,byte[]>();
        if (tar.length % BLOCK != 0) throw invalid();
        int offset = 0;
        while (true) {
            if (offset + BLOCK > tar.length) throw invalid();
            byte[] header = Arrays.copyOfRange(tar, offset, offset + BLOCK);
            if (Arrays.equals(header, new byte[BLOCK])) {
                // 끝 표시 = 0 블록 정확히 2개이고 그 뒤 바이트 0.
                if (offset + 2 * BLOCK != tar.length || !Arrays.equals(Arrays.copyOfRange(tar, offset + BLOCK, tar.length), new byte[BLOCK])) throw invalid();
                return entries;
            }
            String name = text(header, 0, 100);
            long size = octal(header, 124, 12);
            requireName(name);
            if (size < 0 || size > MAX_ENTRY_BYTES || entries.size() >= MAX_ENTRIES || entries.containsKey(name)) throw invalid();
            // 헤더는 writer 가 만드는 바이트 그대로여야 한다(linkname·uname·prefix 등 다른 영역에 데이터를 숨길 수 없게).
            if (!Arrays.equals(header, header(name, size))) throw invalid();
            int start = offset + BLOCK, padded = (int) ((size + BLOCK - 1) / BLOCK * BLOCK);
            if (start + padded > tar.length) throw invalid();
            // 내용 뒤 블록 패딩은 0 바이트뿐이어야 한다.
            for (int i = start + (int) size; i < start + padded; i++) if (tar[i] != 0) throw invalid();
            entries.put(name, Arrays.copyOfRange(tar, start, start + (int) size));
            offset = start + padded;
        }
    }

    private static EvalException invalid() { return new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID"); }
    private static void requireName(String name) {
        if (name.isEmpty() || name.length() >= 100 || name.startsWith("/") || name.contains("\\") || name.contains("//")
            || Arrays.asList(name.split("/")).contains("..") || !name.matches("[A-Za-z0-9._/-]+"))
            throw invalid();
    }
    private static byte[] header(String name, long size) {
        byte[] header = new byte[BLOCK];
        put(header, 0, name.getBytes(StandardCharsets.US_ASCII));
        put(header, 100, "0000644\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 108, "0000000\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 116, "0000000\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 124, String.format("%011o\0", size).getBytes(StandardCharsets.US_ASCII));
        put(header, 136, "00000000000\0".getBytes(StandardCharsets.US_ASCII));
        header[156] = '0';
        put(header, 257, "ustar\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 263, "00".getBytes(StandardCharsets.US_ASCII));
        put(header, 148, String.format("%06o\0 ", checksum(header)).getBytes(StandardCharsets.US_ASCII));
        return header;
    }
    private static long checksum(byte[] header) {
        long sum = 0;
        for (int i = 0; i < BLOCK; i++) sum += (i >= 148 && i < 156) ? ' ' : (header[i] & 0xff);
        return sum;
    }
    private static void put(byte[] target, int offset, byte[] value) { System.arraycopy(value, 0, target, offset, value.length); }
    private static String text(byte[] header, int offset, int length) {
        int end = offset; while (end < offset + length && header[end] != 0) end++;
        return new String(header, offset, end - offset, StandardCharsets.US_ASCII);
    }
    private static long octal(byte[] header, int offset, int length) {
        String value = text(header, offset, length).trim();
        if (!value.matches("[0-7]{1,11}")) return -1;
        return Long.parseLong(value, 8);
    }
}
