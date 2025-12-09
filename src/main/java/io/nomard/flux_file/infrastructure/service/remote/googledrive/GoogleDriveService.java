package io.nomard.flux_file.infrastructure.service.remote.googledrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import io.nomard.flux_file.infrastructure.service.remote.RemoteFileSystemService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService implements RemoteFileSystemService {

    private static final String APPLICATION_NAME = "Reactive File Manager";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens/googledrive";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    
    private Drive driveService;
    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();

    @Override
    public Mono<Boolean> connect(String credentialsJson) {
        return Mono.fromCallable(() -> {
            try {
                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                
                // Load client secrets from credentials JSON
                InputStream in = new ByteArrayInputStream(credentialsJson.getBytes());
                GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
                
                // Build flow and trigger user authorization request
                GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline")
                        .build();
                
                LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
                Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
                
                driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
                
                return true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to Google Drive", e);
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> disconnect() {
        return Mono.fromCallable(() -> {
            driveService = null;
            return true;
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> isConnected() {
        return Mono.just(driveService != null);
    }

    @Override
    public Flux<RemoteFileItem> listFiles(String folderId) {
        return Flux.defer(() -> {
            try {
                if (driveService == null) {
                    return Flux.error(new RuntimeException("Not connected to Google Drive"));
                }
                
                String query = folderId == null || folderId.equals("root") 
                    ? "'root' in parents and trashed = false"
                    : "'" + folderId + "' in parents and trashed = false";
                
                FileList result = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name, mimeType, size, modifiedTime, createdTime)")
                        .execute();
                
                List<File> files = result.getFiles();
                
                return Flux.fromIterable(files)
                        .map(file -> {
                            boolean isFolder = "application/vnd.google-apps.folder".equals(file.getMimeType());
                            long size = file.getSize() != null ? file.getSize() : 0;
                            Instant modified = file.getModifiedTime() != null 
                                ? Instant.ofEpochMilli(file.getModifiedTime().getValue())
                                : Instant.now();
                            
                            return new RemoteFileItem(
                                file.getId(),
                                file.getName(),
                                isFolder,
                                size,
                                modified,
                                "googledrive",
                                folderId != null ? folderId : "root"
                            );
                        });
            } catch (Exception e) {
                return Flux.error(new RuntimeException("Failed to list Google Drive files", e));
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Void> downloadFile(String fileId, Path localDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (driveService == null) {
                    throw new RuntimeException("Not connected to Google Drive");
                }
                
                OutputStream outputStream = Files.newOutputStream(localDestination);
                driveService.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream);
                outputStream.close();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to download file from Google Drive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> uploadFile(Path localFile, String parentFolderId) {
        return Mono.fromRunnable(() -> {
            try {
                if (driveService == null) {
                    throw new RuntimeException("Not connected to Google Drive");
                }
                
                File fileMetadata = new File();
                fileMetadata.setName(localFile.getFileName().toString());
                if (parentFolderId != null && !parentFolderId.equals("root")) {
                    fileMetadata.setParents(Collections.singletonList(parentFolderId));
                }
                
                String mimeType = Files.probeContentType(localFile);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
                
                FileContent mediaContent = new FileContent(mimeType, localFile.toFile());
                
                driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id, name")
                        .execute();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file to Google Drive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> deleteFile(String fileId) {
        return Mono.fromRunnable(() -> {
            try {
                if (driveService == null) {
                    throw new RuntimeException("Not connected to Google Drive");
                }
                driveService.files().delete(fileId).execute();
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete file from Google Drive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> createDirectory(String folderName) {
        return Mono.fromRunnable(() -> {
            try {
                if (driveService == null) {
                    throw new RuntimeException("Not connected to Google Drive");
                }
                
                File fileMetadata = new File();
                fileMetadata.setName(folderName);
                fileMetadata.setMimeType("application/vnd.google-apps.folder");
                
                driveService.files().create(fileMetadata)
                        .setFields("id, name")
                        .execute();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to create folder in Google Drive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Long> getAvailableSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (driveService == null) {
                    return 0L;
                }
                com.google.api.services.drive.model.About about = driveService.about()
                        .get()
                        .setFields("storageQuota")
                        .execute();
                
                Long limit = about.getStorageQuota().getLimit();
                Long usage = about.getStorageQuota().getUsage();
                
                return limit != null && usage != null ? limit - usage : 0L;
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Long> getUsedSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (driveService == null) {
                    return 0L;
                }
                com.google.api.services.drive.model.About about = driveService.about()
                        .get()
                        .setFields("storageQuota")
                        .execute();
                
                return about.getStorageQuota().getUsage();
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public String getServiceName() {
        return "Google Drive";
    }
}