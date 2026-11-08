package com.nori.ai.gateway.app.safety;

import ai.djl.huggingface.tokenizers.jni.LibUtils;
import ai.djl.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 이미지 빌드 단계에서 한 번 실행하는 DJL tokenizers native 적재 검사 진입점이다(Gateway 기동 경로는 이 클래스를 호출하지 않는다).
 * pin SDK 의 공개 파일 override({@code RUST_LIBRARY_PATH})를 이 프로세스 안에서만 jar 내 pin 바이너리의 새 추출본으로 고정한 뒤
 * {@link LibUtils#checkStatus()} 를 부른다. 유효한 파일 override 는 SDK 의 캐시 복사·원격 다운로드 분기를 건너뛰고,
 * 잘못된 override 는 SDK 가 즉시 실패시키므로 대체 경로로 넘어가지 않는다.
 */
public final class NativeTokenizerOfflineCheck {
    static final String RESOURCE = "native/lib/linux-x86_64/cpu/libtokenizers.so";
    static final String OVERRIDE = "RUST_LIBRARY_PATH";
    static final String LIBRARY_FILE = "libtokenizers.so";

    /** 검사 실패 사유. 메시지에는 경로·환경 값을 담지 않는다. */
    static final class CheckFailure extends RuntimeException {
        CheckFailure(String code) { super(code); }
    }

    private NativeTokenizerOfflineCheck() {}

    public static void main(String[] args) {
        try {
            run();
            System.out.println("NATIVE_TOKENIZER_OFFLINE_CHECK_OK");
        } catch (CheckFailure failure) {
            System.err.println("NATIVE_TOKENIZER_OFFLINE_CHECK_FAILED:" + failure.getMessage());
            System.exit(1);
        } catch (RuntimeException failure) {
            // SDK 예외 메시지에는 파일 경로가 들어 있으므로 유형만 남긴다.
            System.err.println("NATIVE_TOKENIZER_OFFLINE_CHECK_FAILED:NATIVE_LOAD:" + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void run() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT), arch = System.getProperty("os.arch", "");
        if (!os.startsWith("linux") || !(arch.equals("amd64") || arch.equals("x86_64"))) throw new CheckFailure("PLATFORM_UNSUPPORTED");
        if (!"true".equals(System.getProperty("ai.djl.offline"))) throw new CheckFailure("OFFLINE_MODE_REQUIRED");
        // SDK 의 대체 native 적재 경로를 쓰면 override 검사가 실제 적재 경로를 보장하지 못한다.
        if (System.getProperty("ai.djl.huggingface.native_helper") != null) throw new CheckFailure("NATIVE_HELPER_FORBIDDEN");
        if (System.getenv(OVERRIDE) != null) throw new CheckFailure("EXTERNAL_OVERRIDE_FORBIDDEN");
        Path directory;
        try {
            directory = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "aigw-tokenizers-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (IOException | UnsupportedOperationException failure) { throw new CheckFailure("TEMP_DIRECTORY"); }
        boolean checked = false;
        try {
            Path library = extract(NativeTokenizerOfflineCheck.class.getClassLoader(), RESOURCE, directory);
            System.setProperty(OVERRIDE, library.toString());
            // env 가 system property 보다 먼저 읽히므로 SDK 가 실제로 보는 값을 다시 확인한다.
            if (!library.toString().equals(Utils.getEnvOrSystemProperty(OVERRIDE))) throw new CheckFailure("OVERRIDE_NOT_EFFECTIVE");
            LibUtils.checkStatus();
            checked = true;
        } finally {
            boolean removed;
            try { Files.deleteIfExists(directory.resolve(LIBRARY_FILE)); Files.deleteIfExists(directory); removed = true; }
            catch (IOException failure) { removed = false; }
            // 앞선 실패 사유를 정리 실패로 덮지 않는다.
            if (checked && !removed) throw new CheckFailure("CLEANUP");
        }
    }

    /**
     * classpath 의 정확히 한 pin 항목을 비어 있는 {@code directory} 에 {@value #LIBRARY_FILE} 로 추출하고,
     * 추출본 SHA-256 이 원 항목 바이트의 SHA-256 과 같을 때만 정규화한 절대 경로를 돌려준다.
     */
    static Path extract(ClassLoader loader, String resource, Path directory) {
        try {
            var urls = Collections.list(loader.getResources(resource));
            if (urls.size() != 1) throw new CheckFailure(urls.isEmpty() ? "RESOURCE_MISSING" : "RESOURCE_AMBIGUOUS");
            try (var entries = Files.list(directory)) { if (entries.findAny().isPresent()) throw new CheckFailure("TEMP_DIRECTORY_NOT_EMPTY"); }
            Path target = directory.resolve(LIBRARY_FILE).toAbsolutePath().normalize();
            MessageDigest source = sha256();
            try (InputStream in = new DigestInputStream(urls.getFirst().openStream(), source)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(target)) throw new CheckFailure("EXTRACT");
            String expected = HexFormat.of().formatHex(source.digest());
            MessageDigest copied = sha256();
            try (InputStream in = new DigestInputStream(Files.newInputStream(target), copied)) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
            if (!expected.equals(HexFormat.of().formatHex(copied.digest()))) throw new CheckFailure("HASH_MISMATCH");
            return target;
        } catch (IOException failure) { throw new CheckFailure("EXTRACT"); }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 을 사용할 수 없습니다"); }
    }
}
