package com.smartorganizer.util;

import com.smartorganizer.model.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UtilTest {

    @Test
    void categorizesExtensions() {
        assertEquals(Category.IMAGES, Category.fromExtension("jpg"));
        assertEquals(Category.IMAGES, Category.fromExtension("PNG"));
        assertEquals(Category.DOCUMENTS, Category.fromExtension("pdf"));
        assertEquals(Category.VIDEOS, Category.fromExtension("mp4"));
        assertEquals(Category.MUSIC, Category.fromExtension("mp3"));
        assertEquals(Category.ARCHIVES, Category.fromExtension("zip"));
        assertEquals(Category.APPLICATIONS, Category.fromExtension("exe"));
        assertEquals(Category.OTHERS, Category.fromExtension("xyz"));
        assertEquals(Category.OTHERS, Category.fromExtension(""));
        assertEquals(Category.OTHERS, Category.fromExtension(null));
    }

    @Test
    void extractsExtension() {
        assertEquals("jpg", FileUtil.extensionOf("photo.JPG"));
        assertEquals("gz", FileUtil.extensionOf("a.tar.gz"));
        assertEquals("", FileUtil.extensionOf(".gitignore"));
        assertEquals("", FileUtil.extensionOf("README"));
    }

    @Test
    void formatsSizes() {
        assertEquals("0 B", FormatUtil.formatSize(0));
        assertEquals("1023 B", FormatUtil.formatSize(1023));
        assertEquals("1.0 KB", FormatUtil.formatSize(1024));
        assertEquals("1.5 KB", FormatUtil.formatSize(1536));
        assertEquals("1.0 MB", FormatUtil.formatSize(1024L * 1024));
        assertEquals("1.0 GB", FormatUtil.formatSize(1024L * 1024 * 1024));
    }

    @Test
    void sha256IsCorrectAndContentBased(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path c = dir.resolve("c.txt");
        Files.writeString(a, "hello");
        Files.writeString(b, "hello");
        Files.writeString(c, "world");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                HashUtil.sha256(a));
        assertEquals(HashUtil.sha256(a), HashUtil.sha256(b));
        assertNotEquals(HashUtil.sha256(a), HashUtil.sha256(c));
    }
}