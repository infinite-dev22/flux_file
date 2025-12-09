package io.nomard.flux_file.infrastructure.service.remote.dropbox;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.SpaceUsage;
import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import io.nomard.flux_file.infrastructure.service.remote.RemoteFileSystemService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class DropboxService implements RemoteFileSystemService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();
    private DbxClientV2 client;

    @Override
    public Mono<Boolean> connect(String accessToken) {
        return Mono.fromCallable(() -> {
            try {
                DbxRequestConfig config = DbxRequestConfig.newBuilder("reactive-file-manager").build();
                client = new DbxClientV2(config, accessToken);

                // Test connection
                client.users().getCurrentAccount();

                return true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to Dropbox", e);
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> disconnect() {
        return Mono.fromCallable(() -> {
            client = null;
            return true;
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> isConnected() {
        return Mono.just(client != null);
    }

    @Override
    public Flux<RemoteFileItem> listFiles(String remotePath) {
        return Flux.defer(() -> {
            try {
                if (client == null) {
                    return Flux.error(new RuntimeException("Not connected to Dropbox"));
                }
                String path = remotePath;
                if (path == null || path.isEmpty()) {
                    path = "";
                }

                ListFolderResult result = client.files().listFolder(path);

                String finalRemotePath = path;
                return Flux.fromIterable(result.getEntries())
                        .map(entry -> {
                            boolean isDirectory = entry instanceof FolderMetadata;
                            long size = 0;
                            Instant modified = Instant.now();

                            if (entry instanceof FileMetadata fileMetadata) {
                                size = fileMetadata.getSize();
                                modified = fileMetadata.getClientModified().toInstant();
                            }

                            return new RemoteFileItem(
                                    entry.getPathDisplay(),
                                    entry.getName(),
                                    isDirectory,
                                    size,
                                    modified,
                                    "dropbox",
                                    finalRemotePath
                            );
                        });
            } catch (Exception e) {
                return Flux.error(new RuntimeException("Failed to list Dropbox files", e));
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Void> downloadFile(String remoteFile, Path localDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (client == null) {
                    throw new RuntimeException("Not connected to Dropbox");
                }

                FileOutputStream outputStream = new FileOutputStream(localDestination.toFile());
                client.files().downloadBuilder(remoteFile)
                        .download(outputStream);
                outputStream.close();

            } catch (Exception e) {
                throw new RuntimeException("Failed to download file from Dropbox", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> uploadFile(Path localFile, String remoteDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (client == null) {
                    throw new RuntimeException("Not connected to Dropbox");
                }

                FileInputStream inputStream = new FileInputStream(localFile.toFile());

                String destination = remoteDestination;
                if (!destination.startsWith("/")) {
                    destination = "/" + destination;
                }

                client.files().uploadBuilder(destination)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream);

                inputStream.close();

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file to Dropbox", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> deleteFile(String remoteFile) {
        return Mono.fromRunnable(() -> {
            try {
                if (client == null) {
                    throw new RuntimeException("Not connected to Dropbox");
                }
                client.files().deleteV2(remoteFile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete file from Dropbox", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> createDirectory(String remotePath) {
        return Mono.fromRunnable(() -> {
            try {
                if (client == null) {
                    throw new RuntimeException("Not connected to Dropbox");
                }
                String path = remotePath;
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }

                client.files().createFolderV2(path);

            } catch (Exception e) {
                throw new RuntimeException("Failed to create folder in Dropbox", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Long> getAvailableSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (client == null) {
                    return 0L;
                }
                SpaceUsage spaceUsage = client.users().getSpaceUsage();
                long allocated = spaceUsage.getAllocation().getIndividualValue().getAllocated();
                long used = spaceUsage.getUsed();

                return allocated - used;
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Long> getUsedSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (client == null) {
                    return 0L;
                }
                SpaceUsage spaceUsage = client.users().getSpaceUsage();
                return spaceUsage.getUsed();
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public String getServiceName() {
        return "Dropbox";
    }
}