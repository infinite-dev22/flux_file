package io.nomard.flux_file.infrastructure.service.remote.onedrive;

import com.azure.identity.InteractiveBrowserCredential;
import com.azure.identity.InteractiveBrowserCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
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
import java.util.List;
import java.util.Objects;

@Service
public class OneDriveService implements RemoteFileSystemService {

    private static final List<String> SCOPES = List.of("Files.ReadWrite.All", "User.Read");
    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();
    private GraphServiceClient<okhttp3.Request> graphClient;

    @Override
    public Mono<Boolean> connect(String clientId) {
        return Mono.fromCallable(() -> {
            try {
                InteractiveBrowserCredential credential = new InteractiveBrowserCredentialBuilder()
                        .clientId(clientId)
                        .redirectUrl("http://localhost:8888")
                        .build();

                TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(SCOPES, credential);

                graphClient = GraphServiceClient.builder()
                        .authenticationProvider(authProvider)
                        .buildClient();

                return true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to OneDrive", e);
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> disconnect() {
        return Mono.fromCallable(() -> {
            graphClient = null;
            return true;
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> isConnected() {
        return Mono.just(graphClient != null);
    }

    @Override
    public Flux<RemoteFileItem> listFiles(String folderId) {
        return Flux.defer(() -> {
            try {
                if (graphClient == null) {
                    return Flux.error(new RuntimeException("Not connected to OneDrive"));
                }

                DriveItemCollectionPage itemsPage;
                if (folderId == null || folderId.equals("root")) {
                    itemsPage = graphClient.me().drive().root().children()
                            .buildRequest()
                            .get();
                } else {
                    itemsPage = graphClient.me().drive().items(folderId).children()
                            .buildRequest()
                            .get();
                }

                return Flux.fromIterable(Objects.requireNonNull(itemsPage).getCurrentPage())
                        .map(item -> {
                            boolean isFolder = item.folder != null;
                            long size = item.size != null ? item.size : 0;
                            Instant modified = item.lastModifiedDateTime != null
                                    ? item.lastModifiedDateTime.toInstant()
                                    : Instant.now();

                            return new RemoteFileItem(
                                    item.id,
                                    item.name,
                                    isFolder,
                                    size,
                                    modified,
                                    "onedrive",
                                    folderId != null ? folderId : "root"
                            );
                        });
            } catch (Exception e) {
                return Flux.error(new RuntimeException("Failed to list OneDrive files", e));
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Void> downloadFile(String fileId, Path localDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (graphClient == null) {
                    throw new RuntimeException("Not connected to OneDrive");
                }

                java.io.InputStream inputStream = graphClient.me().drive().items(fileId).content()
                        .buildRequest()
                        .get();

                FileOutputStream outputStream = new FileOutputStream(localDestination.toFile());
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = Objects.requireNonNull(inputStream).read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

            } catch (Exception e) {
                throw new RuntimeException("Failed to download file from OneDrive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> uploadFile(Path localFile, String parentFolderId) {
        return Mono.fromRunnable(() -> {
            try {
                if (graphClient == null) {
                    throw new RuntimeException("Not connected to OneDrive");
                }

                FileInputStream fileStream = new FileInputStream(localFile.toFile());

                if (parentFolderId == null || parentFolderId.equals("root")) {
                    graphClient.me().drive().root()
                            .itemWithPath(localFile.getFileName().toString())
                            .content()
                            .buildRequest()
                            .put(fileStream.readAllBytes());
                } else {
                    graphClient.me().drive().items(parentFolderId)
                            .itemWithPath(localFile.getFileName().toString())
                            .content()
                            .buildRequest()
                            .put(fileStream.readAllBytes());
                }

                fileStream.close();

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file to OneDrive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> deleteFile(String fileId) {
        return Mono.fromRunnable(() -> {
            try {
                if (graphClient == null) {
                    throw new RuntimeException("Not connected to OneDrive");
                }
                graphClient.me().drive().items(fileId)
                        .buildRequest()
                        .delete();
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete file from OneDrive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> createDirectory(String folderName) {
        return Mono.fromRunnable(() -> {
            try {
                if (graphClient == null) {
                    throw new RuntimeException("Not connected to OneDrive");
                }

                DriveItem folder = new DriveItem();
                folder.name = folderName;
                folder.folder = new com.microsoft.graph.models.Folder();

                graphClient.me().drive().root().children()
                        .buildRequest()
                        .post(folder);

            } catch (Exception e) {
                throw new RuntimeException("Failed to create folder in OneDrive", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Long> getAvailableSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (graphClient == null) {
                    return 0L;
                }
                com.microsoft.graph.models.Drive drive = graphClient.me().drive()
                        .buildRequest()
                        .get();

                Long total = Objects.requireNonNull(Objects.requireNonNull(drive).quota).total;
                Long used = drive.quota.used;

                return total != null && used != null ? total - used : 0L;
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Long> getUsedSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (graphClient == null) {
                    return 0L;
                }
                com.microsoft.graph.models.Drive drive = graphClient.me().drive()
                        .buildRequest()
                        .get();

                return Objects.requireNonNull(Objects.requireNonNull(drive).quota).used;
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public String getServiceName() {
        return "Microsoft OneDrive";
    }
}