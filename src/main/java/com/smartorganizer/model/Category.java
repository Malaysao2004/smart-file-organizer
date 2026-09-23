package com.smartorganizer.model;

import java.util.Locale;
import java.util.Set;

public enum Category {
    IMAGES("Images", "jpg", "jpeg", "png", "gif", "bmp", "webp"),
    DOCUMENTS("Documents", "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv"),
    VIDEOS("Videos", "mp4", "mkv", "avi", "mov", "webm"),
    MUSIC("Music", "mp3", "wav", "flac", "aac", "ogg"),
    ARCHIVES("Archives", "zip", "rar", "7z", "tar", "gz"),
    APPLICATIONS("Applications", "exe", "msi"),
    OTHERS("Others");

    private final String displayName;
    private final Set<String> extensions;

    Category(String displayName, String... extensions) {
        this.displayName = displayName;
        this.extensions = Set.of(extensions);
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Category fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return OTHERS;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        for (Category c : values()) {
            if (c.extensions.contains(ext)) {
                return c;
            }
        }
        return OTHERS;
    }

    @Override
    public String toString() {
        return displayName;
    }
}