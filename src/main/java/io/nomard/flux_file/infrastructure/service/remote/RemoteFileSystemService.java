package io.nomard.flux_file.infrastructure.service.remote;

import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface RemoteFileSystemService {
    
    Mono<Boolean> connect(String credentials);
    Mono<Boolean> disconnect();
    Mono<Boolean> isConnected();
    
    Flux<RemoteFileItem> listFiles(String remotePath);
    Mono<Void> downloadFile(String remoteFile, Path localDestination);
    Mono<Void> uploadFile(Path localFile, String remoteDestination);
    Mono<Void> deleteFile(String remoteFile);
    Mono<Void> createDirectory(String remotePath);
    
    Mono<Long> getAvailableSpace();
    Mono<Long> getUsedSpace();
    
    String getServiceName();
}