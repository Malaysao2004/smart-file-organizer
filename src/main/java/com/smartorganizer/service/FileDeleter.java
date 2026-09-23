package com.smartorganizer.service;

import com.smartorganizer.model.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FileDeleter {

    public record Result(List<Path> deleted, long freedBytes, int failed) {
    }

    /** Deletes ONLY the given files (permanent delete). */
    public Result delete(List<FileInfo> files, Consumer<String> log) {
        List<Path> deleted = new ArrayList<>();
        long freed = 0;
        int failed = 0;
        for (FileInfo f : files) {
            try {
                Files.delete(f.path());
                deleted.add(f.path());
                freed += f.size();
            } catch (IOException | RuntimeException e) {
                failed++;
                log.accept("ERROR: could not delete " + f.path() + " - " + e.getMessage());
            }
        }
        return new Result(deleted, freed, failed);
    }
}