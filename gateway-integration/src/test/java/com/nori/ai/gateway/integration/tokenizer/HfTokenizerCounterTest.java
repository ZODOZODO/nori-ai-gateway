package com.nori.ai.gateway.integration.tokenizer;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HfTokenizerCounterTest {
    @TempDir Path directory;
    private static HfTokenizerCounter counter;
    @BeforeAll static void load() { counter = new HfTokenizerCounter(); }
    @AfterAll static void close() { counter.close(); }

    private Path root(String name, byte[] content) throws Exception {
        Path file = directory.resolve(name).resolve(HfTokenizerCounter.RESOURCE);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return directory.resolve(name);
    }
    private static URLClassLoader loader(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) urls[i] = roots[i].toUri().toURL();
        return new URLClassLoader(urls, null);
    }

    @Test void bundledResourceIsThePinnedOfficialTokenizer() {
        assertThat(GoldenResources.sha256(HfTokenizerCounter.RESOURCE)).isEqualTo(HfTokenizerCounter.TOKENIZER_SHA256);
    }

    @Test void pinMismatchMissingAndAmbiguousResourcesRefuseToStart() throws Exception {
        byte[] pinned = GoldenResources.bytes(HfTokenizerCounter.RESOURCE);
        byte[] tampered = pinned.clone();
        tampered[tampered.length - 2] ^= 1;
        try (var single = loader(root("tampered", tampered))) {
            assertThatThrownBy(() -> new HfTokenizerCounter(single, HfTokenizerCounter.RESOURCE, HfTokenizerCounter.TOKENIZER_SHA256)).hasMessage("TOKENIZER_SHA_MISMATCH");
        }
        try (var empty = loader(Files.createDirectory(directory.resolve("empty")))) {
            assertThatThrownBy(() -> new HfTokenizerCounter(empty, HfTokenizerCounter.RESOURCE, HfTokenizerCounter.TOKENIZER_SHA256)).hasMessage("TOKENIZER_RESOURCE_MISSING");
        }
        try (var twice = loader(root("a", pinned), root("b", pinned))) {
            assertThatThrownBy(() -> new HfTokenizerCounter(twice, HfTokenizerCounter.RESOURCE, HfTokenizerCounter.TOKENIZER_SHA256)).hasMessage("TOKENIZER_RESOURCE_AMBIGUOUS");
        }
        try (var exact = loader(root("exact", pinned)); var loaded = new HfTokenizerCounter(exact, HfTokenizerCounter.RESOURCE, HfTokenizerCounter.TOKENIZER_SHA256)) {
            assertThat(loaded.countText("<|im_start|>user\n질문<|im_end|>\n")).isEqualTo(counter.countText("<|im_start|>user\n질문<|im_end|>\n"));
        }
    }

    @Test void longInputIsCountedWithoutTruncation() {
        // " token" 은 한 토큰이다. num_ctx 를 넘는 길이도 잘림 없이 그대로 센다.
        int base = counter.countText("x:");
        assertThat(counter.countText("x:" + " token".repeat(10_000))).isEqualTo(base + 10_000);
    }

    @Test void addedSpecialTokensAreRecognizedAsSingleTokens() {
        assertThat(counter.countText("<|im_start|>")).isEqualTo(1);
        assertThat(counter.countText("<|im_end|>")).isEqualTo(1);
        assertThat(counter.countText("<think>")).isEqualTo(1);
    }

    @Test void unpairedSurrogatesAreRejectedAndPairedOnesCounted() {
        assertThatThrownBy(() -> counter.countText("a\uD83D")).hasMessage("TOKENIZER_INPUT_UNPAIRED_SURROGATE");
        assertThatThrownBy(() -> counter.countText("\uDD27b")).hasMessage("TOKENIZER_INPUT_UNPAIRED_SURROGATE");
        assertThat(counter.countText("🔧")).isPositive();
        assertThatThrownBy(() -> counter.countText(null)).isInstanceOf(NullPointerException.class);
    }
}
