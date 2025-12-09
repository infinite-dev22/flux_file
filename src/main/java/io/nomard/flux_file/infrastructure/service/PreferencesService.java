package io.nomard.flux_file.infrastructure.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class PreferencesService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();
    private final Path preferencesFile;
    private final Properties preferences;

    public PreferencesService() {
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".filemanager");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            // Ignore
        }
        this.preferencesFile = configDir.resolve("preferences.properties");
        this.preferences = new Properties();
        loadPreferences();
    }

    private void loadPreferences() {
        if (Files.exists(preferencesFile)) {
            try (InputStream is = Files.newInputStream(preferencesFile)) {
                preferences.load(is);
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public Mono<Void> savePreferences() {
        return Mono.fromRunnable(() -> {
            try (OutputStream os = Files.newOutputStream(preferencesFile)) {
                preferences.store(os, "File Manager Preferences");
            } catch (IOException e) {
                throw new RuntimeException("Failed to save preferences", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public void setDefaultApplication(String extension, String application) {
        String keyExt = normalizeExt(extension);
        preferences.setProperty("default.app." + keyExt, application);
        savePreferences().subscribe();
    }

    public String getDefaultApplication(String extension) {
        String keyExt = normalizeExt(extension);
        return preferences.getProperty("default.app." + keyExt);
    }

    public Map<String, String> getAllDefaultApplications() {
        Map<String, String> defaults = new HashMap<>();
        preferences.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith("default.app.")) {
                String ext = keyStr.substring("default.app.".length());
                defaults.put(ext, value.toString());
            }
        });
        return defaults;
    }

    // -------------------------
    // Hidden files preference
    // -------------------------

    private static final String PREF_SHOW_HIDDEN = "ui.showHidden";

    public boolean isShowHidden() {
        String v = preferences.getProperty(PREF_SHOW_HIDDEN, "false");
        return Boolean.parseBoolean(v);
    }

    public void setShowHidden(boolean show) {
        preferences.setProperty(PREF_SHOW_HIDDEN, Boolean.toString(show));
        // Fire and forget persistence
        savePreferences().subscribe();
    }

    private String normalizeExt(String ext) {
        if (ext == null) return "";
        String e = ext.trim();
        if (e.startsWith(".")) e = e.substring(1);
        return e.toLowerCase();
    }
}