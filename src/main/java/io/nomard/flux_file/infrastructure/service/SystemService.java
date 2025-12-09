package io.nomard.flux_file.infrastructure.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();

    public Mono<List<String>> getAvailableTerminals() {
        return Mono.fromCallable(() -> {
            List<String> terminals = new ArrayList<>();
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows terminals
                if (isCommandAvailable("wt")) terminals.add("wt"); // Windows Terminal
                if (isCommandAvailable("powershell")) terminals.add("powershell");
                if (isCommandAvailable("cmd")) terminals.add("cmd");
            } else if (os.contains("mac")) {
                // macOS terminals
                terminals.add("open -a Terminal");
                if (isCommandAvailable("iterm")) terminals.add("open -a iTerm");
            } else {
                // Linux terminals
                if (isCommandAvailable("gnome-terminal")) terminals.add("gnome-terminal");
                if (isCommandAvailable("konsole")) terminals.add("konsole");
                if (isCommandAvailable("xterm")) terminals.add("xterm");
                if (isCommandAvailable("alacritty")) terminals.add("alacritty");
                if (isCommandAvailable("kitty")) terminals.add("kitty");
                if (isCommandAvailable("terminator")) terminals.add("terminator");
            }

            return terminals;
        }).subscribeOn(ioScheduler);
    }

    private boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("where", command);
            } else {
                pb = new ProcessBuilder("which", command);
            }

            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Mono<Void> openInTerminal(Path directory, String terminal) {
        return Mono.fromRunnable(() -> {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;

                if (os.contains("win")) {
                    if (terminal.equals("wt")) {
                        pb = new ProcessBuilder("wt", "-d", directory.toString());
                    } else if (terminal.equals("powershell")) {
                        pb = new ProcessBuilder("powershell", "-NoExit", "-Command",
                                "Set-Location '" + directory.toString() + "'");
                    } else {
                        pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k",
                                "cd /d " + directory.toString());
                    }
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", "-a", terminal.replace("open -a ", ""), directory.toString());
                } else {
                    pb = new ProcessBuilder(terminal, "--working-directory=" + directory.toString());
                }

                pb.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to open terminal", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    public Mono<List<String>> getInstalledApplications() {
        return Mono.fromCallable(() -> {
            List<String> apps = new ArrayList<>();
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                apps.add("notepad");
                apps.add("notepad++");
                apps.add("code"); // VS Code
                apps.add("explorer");
            } else if (os.contains("mac")) {
                apps.add("TextEdit");
                apps.add("Visual Studio Code");
                apps.add("Sublime Text");
            } else {
                apps.add("gedit");
                apps.add("nano");
                apps.add("vim");
                apps.add("code");
            }

            return apps;
        }).subscribeOn(ioScheduler);
    }

    public Mono<Void> shareFile(Path file) {
        return Mono.fromRunnable(() -> {
            try {
                String os = System.getProperty("os.name").toLowerCase();

                if (os.contains("win")) {
                    // Open Windows share dialog
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start",
                            "shell:::{f81e9010-6ea4-11ce-a7ff-00aa003ca9f6}");
                    pb.start();
                } else if (os.contains("mac")) {
                    // macOS sharing
                    ProcessBuilder pb = new ProcessBuilder("open", "-R", file.toString());
                    pb.start();
                } else {
                    // Linux - open file manager to location
                    ProcessBuilder pb = new ProcessBuilder("xdg-open", file.getParent().toString());
                    pb.start();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to share file", e);
            }
        }).subscribeOn(ioScheduler).then();
    }
}