package io.nomard.flux_file.core.domain.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RemoteFileItem {
    private final String id;
    private final String name;
    private final boolean isDirectory;
    private final long size;
    private final Instant modified;
    private final String service;
    private final String parentPath;
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public RemoteFileItem(String id, String name, boolean isDirectory, long size, 
                         Instant modified, String service, String parentPath) {
        this.id = id;
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.modified = modified;
        this.service = service;
        this.parentPath = parentPath;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }

    public String getFormattedSize() {
        if (isDirectory) return "--";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public Instant getModified() {
        return modified;
    }

    public String getFormattedDate() {
        return DATE_FORMATTER.format(modified);
    }

    public String getType() {
        return isDirectory ? "Folder" : "File";
    }

    public String getService() {
        return service;
    }

    public String getParentPath() {
        return parentPath;
    }

    public String getExtension() {
        if (isDirectory) return "";
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }
}