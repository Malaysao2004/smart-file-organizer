package com.smartorganizer.service;

import com.smartorganizer.model.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class FileOrganizer {

    public record Result(int moved, int skipped, int failed) {
    }

    /** A file needs moving unless it is already inside root/<CategoryFolder>. */
    public boolean needsMove(Path root, FileInfo file) {
        return !file.path().startsWith(root.resolve(file.category().getDisplayName()));
    }

    public int countToMove(Path root, List<FileInfo> files) {
        int count = 0;
        for (FileInfo f : files) {
            if (needsMove(root, f)) {
                count++;
            }
        }
        return count;
    }

    public Result organize(Path root, List<FileInfo> files, Consumer<String> log,
                           DoubleConsumer progress, BooleanSupplier cancelled) {
        int moved = 0;
        int skipped = 0;
        int failed = 0;
        int done = 0;
        for (FileInfo f : files) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            done++;
            if (!needsMove(root, f)) {
                skipped++;
            } else {
                try {
                    Path targetDir = root.resolve(f.category().getDisplayName());
                    Files.createDirectories(targetDir);
                    Path target = uniqueTarget(targetDir, f.name());
                    Files.move(f.path(), target);
                    moved++;
                    if (!target.getFileName().toString().equals(f.name())) {
                        log.accept("Name conflict, renamed: " + f.name() + " -> " + target.getFileName());
                    }
                } catch (IOException | RuntimeException e) {
                    failed++;
                    log.accept("ERROR: could not move " + f.path() + " - " + e.getMessage());
                }
            }
            progress.accept((double) done / files.size());
        }
        return new Result(moved, skipped, failed);
    }

    /** Returns dir/name, or dir/name (1).ext, (2).ext ... if it already exists. */
    static Path uniqueTarget(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int i = 1;
        do {
            candidate = dir.resolve(base + " (" + i + ")" + ext);
            i++;
        } while (Files.exists(candidate));
        return candidate;
    }
}