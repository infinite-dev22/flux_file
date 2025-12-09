package io.nomard.flux_file.core.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record FileItem(Path path, String name, boolean isDirectory, long size, Instant modified) {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String getFormattedSize() {
        if (isDirectory) return "--";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public String getFormattedDate() {
        return DATE_FORMATTER.format(modified);
    }

    public String getType() {
        return isDirectory ? "Folder" : "File";
    }

    public String getExtension() {
        if (isDirectory) return "";
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }
}