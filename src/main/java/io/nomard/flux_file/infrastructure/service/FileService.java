package io.nomard.flux_file.infrastructure.service;

import io.nomard.flux_file.core.domain.model.FileItem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();

    public Flux<FileItem> listFiles(Path directory) {
        return Flux.defer(() -> {
            try {
                Stream<Path> pathStream = Files.list(directory);
                return Flux.fromStream(pathStream)
                        .flatMap(this::createFileItem)
                        .onErrorResume(e -> Flux.empty());
            } catch (IOException e) {
                return Flux.error(e);
            }
        }).subscribeOn(ioScheduler);
    }

    public Mono<FileItem> createFileItem(Path path) {
        return Mono.fromCallable(() -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                String name = path.getFileName().toString();
                boolean isDirectory = attrs.isDirectory();
                long size = isDirectory ? 0 : attrs.size();
                Instant modified = attrs.lastModifiedTime().toInstant();

                return new FileItem(path, name, isDirectory, size, modified);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + path, e);
            }
        }).subscribeOn(ioScheduler);
    }

    public Flux<Path> searchFiles(Path root, String searchTerm) {
        return Flux.defer(() -> {
            try {
                return Flux.fromStream(Files.walk(root, 10))
                        .filter(p -> p.getFileName().toString().toLowerCase()
                                .contains(searchTerm.toLowerCase()));
            } catch (IOException e) {
                return Flux.error(e);
            }
        }).subscribeOn(ioScheduler);
    }

    public Mono<Void> openFile(Path path) {
        return Mono.fromRunnable(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(path.toFile());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to open file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> openWith(Path path, String application) {
        return Mono.fromRunnable(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(application, path.toString());
                pb.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to open with application", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> copyFile(Path source, Path target) {
        return Mono.fromRunnable(() -> {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> moveFile(Path source, Path target) {
        return Mono.fromRunnable(() -> {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to move file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> deleteFile(Path path) {
        return Mono.fromRunnable(() -> {
            try {
                if (Files.isDirectory(path)) {
                    deleteDirectoryRecursively(path);
                } else {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    public Mono<Boolean> createDirectory(Path path) {
        return Mono.fromCallable(() -> {
            try {
                Files.createDirectories(path);
                return true;
            } catch (IOException e) {
                return false;
            }
        }).subscribeOn(ioScheduler);
    }

    public Mono<Void> compressFiles(java.util.List<Path> files, Path zipFile) {
        return Mono.fromRunnable(() -> {
            try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                for (Path file : files) {
                    if (Files.isDirectory(file)) {
                        zipDirectory(file, file.getFileName().toString(), zos);
                    } else {
                        zipFile(file, file.getFileName().toString(), zos);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to compress files", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    private void zipFile(Path file, String fileName, ZipOutputStream zos) throws IOException {
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void zipDirectory(Path folder, String parentFolder, ZipOutputStream zos) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(path -> !path.equals(folder))
                    .forEach(path -> {
                        try {
                            String zipEntryName = parentFolder + "/" + folder.relativize(path).toString();
                            if (Files.isDirectory(path)) {
                                zipEntryName += "/";
                            }
                            ZipEntry zipEntry = new ZipEntry(zipEntryName);
                            zos.putNextEntry(zipEntry);
                            if (!Files.isDirectory(path)) {
                                Files.copy(path, zos);
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    public Mono<Void> renameFile(Path path, String newName) {
        return Mono.fromRunnable(() -> {
            try {
                Path target = path.getParent().resolve(newName);
                Files.move(path, target);
            } catch (IOException e) {
                throw new RuntimeException("Failed to rename file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }
}