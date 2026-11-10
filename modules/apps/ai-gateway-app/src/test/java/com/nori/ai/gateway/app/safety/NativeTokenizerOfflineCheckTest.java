package com.nori.ai.gateway.app.safety;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 추출·해시 단계만 검사한다. 실제 native 적재는 Linux 이미지 빌드 단계에서만 실행된다. */
class NativeTokenizerOfflineCheckTest {
    @TempDir Path directory;

    private Path root(String name, byte[] content) throws Exception {
        Path root = Files.createDirectories(directory.resolve(name));
        Path file = root.resolve(NativeTokenizerOfflineCheck.RESOURCE);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return root;
    }
    private static URLClassLoader loader(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) urls[i] = roots[i].toUri().toURL();
        return new URLClassLoader(urls, null);
    }

    @Test void extractsTheSingleResourceWithIdenticalBytes() throws Exception {
        byte[] bytes = "pinned-native".getBytes();
        Path target = Files.createDirectory(directory.resolve("out"));
        try (var loader = loader(root("a", bytes))) {
            Path library = NativeTokenizerOfflineCheck.extract(loader, NativeTokenizerOfflineCheck.RESOURCE, target);
            assertThat(library.getFileName().toString()).isEqualTo("libtokenizers.so");
            assertThat(library.isAbsolute()).isTrue();
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(library))))
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
    }

    @Test void missingAmbiguousResourceAndReusedDirectoryFail() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        Path target = Files.createDirectory(directory.resolve("out"));
        try (var loader = loader(empty)) {
            assertThatThrownBy(() -> NativeTokenizerOfflineCheck.extract(loader, NativeTokenizerOfflineCheck.RESOURCE, target)).hasMessage("RESOURCE_MISSING");
        }
        try (var loader = loader(root("a", new byte[]{1}), root("b", new byte[]{2}))) {
            assertThatThrownBy(() -> NativeTokenizerOfflineCheck.extract(loader, NativeTokenizerOfflineCheck.RESOURCE, target)).hasMessage("RESOURCE_AMBIGUOUS");
        }
        // 이전 추출본이 남은 디렉터리는 검사를 대신하지 않는다.
        Files.writeString(target.resolve("libtokenizers.so"), "stale");
        try (var loader = loader(root("c", new byte[]{3}))) {
            assertThatThrownBy(() -> NativeTokenizerOfflineCheck.extract(loader, NativeTokenizerOfflineCheck.RESOURCE, target)).hasMessage("TEMP_DIRECTORY_NOT_EMPTY");
        }
    }
}
