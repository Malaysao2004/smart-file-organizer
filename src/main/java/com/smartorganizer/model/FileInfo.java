package com.smartorganizer.model;

import com.smartorganizer.util.FileUtil;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record FileInfo(Path path, String name, String extension, long size,
                       LocalDateTime lastModified, Category category) {

    public static FileInfo of(Path path, long size, FileTime modified) {
        String name = path.getFileName().toString();
        String ext = FileUtil.extensionOf(name);
        LocalDateTime time = LocalDateTime.ofInstant(modified.toInstant(), ZoneId.systemDefault());
        return new FileInfo(path, name, ext, size, time, Category.fromExtension(ext));
    }
}