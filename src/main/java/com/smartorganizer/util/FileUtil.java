package com.smartorganizer.util;

import java.util.Locale;

public final class FileUtil {
    private FileUtil() {
    }

    /** Returns lowercase extension without dot; "" if none (hidden files like .gitignore have none). */
    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}