package com.nori.ai.gateway.integration.tokenizer;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 생성 모델과 같은 Qwen3 tokenizer.json 을 DJL HuggingFace tokenizers 엔진으로 읽어 텍스트 토큰 수를 센다(Plan §3.5 엔진 재사용).
 * classpath 의 pin 파일 SHA-256 이 다르면 기동하지 않으며, SDK 의 원격 다운로드 분기는 오프라인 모드로 막는다.
 * special/added token 은 tokenizer.json 정의대로 인식하고, 계수에는 별도 special token 을 덧붙이지 않는다(add_special_tokens=false).
 */
@Component
public class HfTokenizerCounter implements AutoCloseable {
    static final String RESOURCE = "tokenizer/qwen3-4b-thinking-2507.tokenizer.json";
    /** Qwen/Qwen3-4B-Thinking-2507 revision 768f209d 공식 tokenizer.json(eval/f0/tokenizer pin 과 같은 바이트). */
    public static final String TOKENIZER_SHA256 = "aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4";
    static final String OFFLINE_PROPERTY = "ai.djl.offline";
    private static final Map<String, String> OPTIONS = Map.of("addSpecialTokens", "false", "truncation", "false", "padding", "false");

    private final HuggingFaceTokenizer tokenizer;

    public HfTokenizerCounter() { this(HfTokenizerCounter.class.getClassLoader(), RESOURCE, TOKENIZER_SHA256); }

    HfTokenizerCounter(ClassLoader loader, String resource, String expectedSha256) {
        requireOfflineMode();
        byte[] bytes = readSingle(loader, resource);
        if (!sha256(bytes).equals(expectedSha256)) throw new IllegalStateException("TOKENIZER_SHA_MISMATCH");
        tokenizer = load(bytes);
    }

    /** 텍스트 하나의 토큰 수. 짝 없는 surrogate 는 UTF-8 로 정확히 옮길 수 없어 계수하지 않는다. */
    public int countText(String text) {
        Objects.requireNonNull(text, "text");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) i++;
            else if (Character.isSurrogate(c)) throw new IllegalArgumentException("TOKENIZER_INPUT_UNPAIRED_SURROGATE");
        }
        return tokenizer.encode(text, false, false).getIds().length;
    }

    @Override public void close() { tokenizer.close(); }

    /**
     * 운영 진입점은 -Dai.djl.offline=true 를 주지만 시험 JVM·자식 프로세스 같은 다른 진입점에서도 원격 다운로드가 없도록 미설정이면 켠다.
     * 명시적으로 다른 값이면 기동하지 않고, 환경 변수 DJL_OFFLINE 이 system property 보다 우선하므로 SDK 가 실제로 보는 값을 다시 확인한다.
     */
    static void requireOfflineMode() {
        String configured = System.getProperty(OFFLINE_PROPERTY);
        if (configured == null) System.setProperty(OFFLINE_PROPERTY, "true");
        else if (!configured.equals("true")) throw new IllegalStateException("TOKENIZER_OFFLINE_REQUIRED");
        if (!Utils.isOfflineMode()) throw new IllegalStateException("TOKENIZER_OFFLINE_REQUIRED");
    }

    private static byte[] readSingle(ClassLoader loader, String resource) {
        try {
            var urls = Collections.list(loader.getResources(resource));
            if (urls.size() != 1) throw new IllegalStateException(urls.isEmpty() ? "TOKENIZER_RESOURCE_MISSING" : "TOKENIZER_RESOURCE_AMBIGUOUS");
            try (InputStream in = urls.getFirst().openStream()) { return in.readAllBytes(); }
        } catch (IOException failure) { throw new IllegalStateException("TOKENIZER_RESOURCE_UNREADABLE", failure); }
    }

    /** 검증한 바이트만 비공개 임시 디렉터리의 tokenizer.json 으로 쓰고 SDK 의 로컬 파일 로드로 읽은 뒤 지운다. */
    private static HuggingFaceTokenizer load(byte[] bytes) {
        Path directory = null, file = null;
        try {
            directory = Files.createTempDirectory("aigw-tokenizer-");
            file = Files.write(directory.resolve("tokenizer.json"), bytes);
            if (!sha256(Files.readAllBytes(file)).equals(sha256(bytes))) throw new IllegalStateException("TOKENIZER_COPY_MISMATCH");
            return HuggingFaceTokenizer.newInstance(file, OPTIONS);
        } catch (IOException failure) {
            throw new IllegalStateException("TOKENIZER_LOAD_FAILED", failure);
        } finally {
            try {
                if (file != null) Files.deleteIfExists(file);
                if (directory != null) Files.deleteIfExists(directory);
            } catch (IOException failure) { throw new IllegalStateException("TOKENIZER_TEMP_CLEANUP_FAILED", failure); }
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", impossible); }
    }
}
