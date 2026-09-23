package com.smartorganizer.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtil {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private FormatUtil() {
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int i = -1;
        do {
            value /= 1024;
            i++;
        } while (value >= 1024 && i < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[i]);
    }

    public static String formatDate(LocalDateTime time) {
        return time == null ? "" : DATE_FMT.format(time);
    }
}