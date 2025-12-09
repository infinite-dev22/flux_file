package io.nomard.flux_file.config;

import io.nomard.flux_file.infrastructure.service.FileWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationShutdownListener {

    private final FileWatchService fileWatchService;

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        log.info("Application shutting down, cleaning up file watchers...");
        fileWatchService.stopAllWatchers();
    }
}