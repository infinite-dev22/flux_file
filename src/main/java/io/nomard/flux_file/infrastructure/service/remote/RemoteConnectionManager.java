package io.nomard.flux_file.infrastructure.service.remote;

import io.nomard.flux_file.infrastructure.service.remote.dropbox.DropboxService;
import io.nomard.flux_file.infrastructure.service.remote.googledrive.GoogleDriveService;
import io.nomard.flux_file.infrastructure.service.remote.onedrive.OneDriveService;
import io.nomard.flux_file.infrastructure.service.remote.sftp.SFTPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RemoteConnectionManager {

    @Autowired
    private GoogleDriveService googleDriveService;

    @Autowired
    private OneDriveService oneDriveService;

    @Autowired
    private DropboxService dropboxService;

    @Autowired
    private SFTPService sftpService;

    private final Map<String, RemoteFileSystemService> activeConnections = new HashMap<>();

    public RemoteFileSystemService getService(String serviceType) {
        return switch (serviceType.toLowerCase()) {
            case "googledrive" -> googleDriveService;
            case "onedrive" -> oneDriveService;
            case "dropbox" -> dropboxService;
            case "sftp" -> sftpService;
            default -> throw new IllegalArgumentException("Unknown service type: " + serviceType);
        };
    }

    public void registerConnection(String name, RemoteFileSystemService service) {
        activeConnections.put(name, service);
    }

    public void removeConnection(String name) {
        RemoteFileSystemService service = activeConnections.get(name);
        if (service != null) {
            service.disconnect().subscribe();
            activeConnections.remove(name);
        }
    }

    public Map<String, RemoteFileSystemService> getActiveConnections() {
        return new HashMap<>(activeConnections);
    }

    public boolean hasActiveConnection(String name) {
        return activeConnections.containsKey(name);
    }
}