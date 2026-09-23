package com.smartorganizer.service;

import com.smartorganizer.model.FileInfo;
import com.smartorganizer.model.ScanResult;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class FileScanner {

    public ScanResult scan(Path root, IntConsumer onFileCount, Consumer<String> log,
                           BooleanSupplier cancelled) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        int[] folders = {0};
        int[] skipped = {0};

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (cancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                folders[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (cancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                if (attrs.isRegularFile()) {
                    files.add(FileInfo.of(file, attrs.size(), attrs.lastModifiedTime()));
                    onFileCount.accept(files.size());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                skipped[0]++;
                log.accept("Skipped (no access or removed): " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        return new ScanResult(files, folders[0], skipped[0]);
    }
}