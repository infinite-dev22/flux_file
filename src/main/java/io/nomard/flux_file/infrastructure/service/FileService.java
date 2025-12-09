package io.nomard.flux_file.infrastructure.service;

import io.nomard.flux_file.core.domain.model.FileItem;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();

    public Flux<FileItem> listFiles(Path directory) {
        return Flux.defer(() -> {
            try {
                Stream<Path> pathStream = Files.list(directory);
                return Flux.fromStream(pathStream)
                        .flatMap(this::createFileItem)
                        .onErrorResume(e -> {
                            log.error("Error listing files in: {}", directory, e);
                            return Flux.empty();
                        });
            } catch (IOException e) {
                log.error("Failed to list directory: {}", directory, e);
                return Flux.error(e);
            }
        }).subscribeOn(ioScheduler);
    }

    public Mono<FileItem> createFileItem(Path path) {
        return Mono.fromCallable(() -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

                // Safe way to get file name (handles root paths)
                String name = getFileName(path);
//                String name1 = path.getFileName().toString();
                boolean isDirectory = attrs.isDirectory();
                long size = isDirectory ? 0 : attrs.size();
                Instant modified = attrs.lastModifiedTime().toInstant();

                return new FileItem(path, name, isDirectory, size, modified);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + path, e);
            }
        }).subscribeOn(ioScheduler);
    }

    /**
     * Safely get the file name from a path.
     * Handles edge cases like root paths.
     *
     * @param path the path to extract name from
     * @return the file name, or a suitable default
     */
    private String getFileName(Path path) {
        if (path == null) {
            return "";
        }

        Path fileName = path.getFileName();

        // Handle root paths (e.g., "/" on Linux, "C:\" on Windows)
        if (fileName == null) {
            // Try to get the root
            Path root = path.getRoot();
            return Objects.requireNonNullElse(root, path).toString();
            // Last resort: use the entire path
        }

        return fileName.toString();
    }


    public Flux<FileItem> searchFiles(Path root, String searchTerm) {
        return Flux.defer(() -> {
            if (root == null || searchTerm == null) {
                return Flux.empty();
            }
            String trimmedTerm = searchTerm.trim();
            if (trimmedTerm.isEmpty()) {
                return Flux.empty();
            }

            String lowerSearchTerm = trimmedTerm.toLowerCase();

            return Flux.<FileItem>create(sink -> {
                try {
                    Files.walkFileTree(root, java.util.EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                            new SimpleFileVisitor<>() {
                                @Override
                                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                                    try {
                                        Path fileName = file.getFileName();
                                        if (fileName != null &&
                                                fileName.toString().toLowerCase().contains(lowerSearchTerm)) {

                                            if (!sink.isCancelled()) {
                                                sink.next(new FileItem(
                                                        file,
                                                        fileName.toString(),
                                                        false,
                                                        attrs.size(),
                                                        attrs.lastModifiedTime().toInstant()
                                                ));
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("Error processing file {}: {}", file, e.getMessage());
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public @NonNull FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc) {
                                    log.debug("Skipping inaccessible: {}", file);
                                    return FileVisitResult.CONTINUE;
                                }
                            }
                    );
                    sink.complete();
                } catch (IOException e) {
                    sink.error(e);
                }
            });
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
                throw new RuntimeException("Failed to copy file: " + source, e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> moveFile(Path source, Path target) {
        return Mono.fromRunnable(() -> {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to move file: " + source, e);
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
                throw new RuntimeException("Failed to delete: " + path, e);
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
                            throw new RuntimeException("Failed to delete: " + path, e);
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
                log.error("Failed to create directory: {}", path, e);
                return false;
            }
        }).subscribeOn(ioScheduler);
    }

    public Mono<Void> renameFile(Path path, String newName) {
        return Mono.fromRunnable(() -> {
            try {
                Path target = path.getParent().resolve(newName);
                Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to rename: " + path, e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<Void> compressFiles(java.util.List<Path> files, Path zipFile) {
        return Mono.fromRunnable(() -> {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(zipFile.toFile());
                 java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {

                for (Path file : files) {
                    if (Files.isDirectory(file)) {
                        zipDirectory(file, file.getFileName().toString(), zos);
                    } else {
                        zipFile(file, getFileName(file), zos);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to compress files", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    private void zipFile(Path file, String fileName, java.util.zip.ZipOutputStream zos) throws IOException {
        java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void zipDirectory(Path folder, String parentFolder, java.util.zip.ZipOutputStream zos) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(path -> !path.equals(folder))
                    .forEach(path -> {
                        try {
                            String zipEntryName = parentFolder + "/" + folder.relativize(path).toString();
                            if (Files.isDirectory(path)) {
                                zipEntryName += "/";
                            }
                            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(zipEntryName);
                            zos.putNextEntry(zipEntry);
                            if (!Files.isDirectory(path)) {
                                Files.copy(path, zos);
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip: " + path, e);
                        }
                    });
        }
    }
}