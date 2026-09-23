package com.smartorganizer.service;

import com.smartorganizer.model.DuplicateGroup;
import com.smartorganizer.model.FileInfo;
import com.smartorganizer.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTest {

    private ScanResult scan(Path dir) throws IOException {
        return new FileScanner().scan(dir, n -> { }, m -> { }, () -> false);
    }

    @Test
    void findsOnlyRealDuplicates(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "same");
        Files.writeString(dir.resolve("b.txt"), "same");
        Files.writeString(dir.resolve("c.txt"), "diff");           // same size, different content
        Files.writeString(dir.resolve("d.txt"), "different length");

        ScanResult result = scan(dir);
        assertEquals(4, result.files().size());

        List<DuplicateGroup> groups =
                new DuplicateFinder().find(result.files(), p -> { }, m -> { }, () -> false);
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).files().size());
    }

    @Test
    void organizerMovesFilesAndHandlesNameConflicts(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "1");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub").resolve("a.txt"), "2");
        Files.writeString(root.resolve("photo.jpg"), "3");

        List<FileInfo> files = scan(root).files();
        FileOrganizer organizer = new FileOrganizer();
        assertEquals(3, organizer.countToMove(root, files));

        FileOrganizer.Result r = organizer.organize(root, files, m -> { }, p -> { }, () -> false);
        assertEquals(3, r.moved());
        assertEquals(0, r.failed());
        assertTrue(Files.exists(root.resolve("Documents/a.txt")));
        assertTrue(Files.exists(root.resolve("Documents/a (1).txt")));
        assertTrue(Files.exists(root.resolve("Images/photo.jpg")));
    }

    @Test
    void organizerSkipsFilesAlreadyInCategoryFolder(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("Images"));
        Files.writeString(root.resolve("Images").resolve("x.png"), "img");
        List<FileInfo> files = scan(root).files();
        assertEquals(0, new FileOrganizer().countToMove(root, files));
    }
}