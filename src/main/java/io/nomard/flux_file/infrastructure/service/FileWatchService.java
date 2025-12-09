package io.nomard.flux_file.infrastructure.service;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.nio.file.*;
import java.util.function.Consumer;

@Service
public class FileWatchService {

    private static @NonNull Thread getWatchThread(FluxSink<WatchEvent<?>> sink, WatchService watchService) {
        return Thread.ofVirtual().start(() -> {
            try {
                while (!sink.isCancelled()) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        sink.next(event);
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                sink.error(e);
            } finally {
                try {
                    watchService.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        });
    }

    public Flux<WatchEvent<?>> watchDirectory(Path directory) {
        return Flux.create((Consumer<FluxSink<WatchEvent<?>>>) sink -> {
                    try {
                        WatchService watchService = FileSystems.getDefault().newWatchService();
                        directory.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);

                        Thread watchThread = getWatchThread(sink, watchService);

                        sink.onDispose(() -> {
                            watchThread.interrupt();
                            try {
                                watchService.close();
                            } catch (Exception e) {
                                // Ignore
                            }
                        });

                    } catch (Exception e) {
                        sink.error(e);
                    }
                }, FluxSink.OverflowStrategy.BUFFER)
                .subscribeOn(Schedulers.boundedElastic());
    }
}