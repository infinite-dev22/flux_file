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
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                return enumerateWindowsApplications();
            } else if (os.contains("mac")) {
                return enumerateMacApplications();
            } else {
                return enumerateLinuxApplications();
            }
        }).subscribeOn(ioScheduler);
    }

    // --------------------------
    // Application enumeration
    // --------------------------

    private List<String> enumerateMacApplications() {
        List<String> apps = new ArrayList<>();
        // Common application folders
        addMacAppsFromDir(apps, java.nio.file.Paths.get("/Applications"));
        addMacAppsFromDir(apps, java.nio.file.Paths.get(System.getProperty("user.home"), "Applications"));
        addMacAppsFromDir(apps, java.nio.file.Paths.get("/System/Applications"));

        // Ensure some well-known ones even if not found
        addIfAbsent(apps, "TextEdit");
        addIfAbsent(apps, "Preview");
        addIfAbsent(apps, "Safari");
        addIfAbsent(apps, "Visual Studio Code");
        addIfAbsent(apps, "Sublime Text");

        apps.sort(String::compareToIgnoreCase);
        return dedup(apps);
    }

    private void addMacAppsFromDir(List<String> out, java.nio.file.Path dir) {
        try {
            if (java.nio.file.Files.isDirectory(dir)) {
                try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files.newDirectoryStream(dir, "*.app")) {
                    for (java.nio.file.Path p : stream) {
                        String name = p.getFileName().toString();
                        if (name.toLowerCase().endsWith(".app")) {
                            name = name.substring(0, name.length() - 4);
                        }
                        addIfAbsent(out, name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private List<String> enumerateLinuxApplications() {
        List<String> apps = new ArrayList<>();
        // Scan .desktop files and extract first Exec token
        java.nio.file.Path sysApps = java.nio.file.Paths.get("/usr/share/applications");
        java.nio.file.Path userApps = java.nio.file.Paths.get(System.getProperty("user.home"), ".local/share/applications");
        addLinuxAppsFromDir(apps, sysApps);
        addLinuxAppsFromDir(apps, userApps);

        // Also include common editors present on PATH
        addIfCommandAvailable(apps, "xdg-open");
        addIfCommandAvailable(apps, "gedit");
        addIfCommandAvailable(apps, "nano");
        addIfCommandAvailable(apps, "vim");
        addIfCommandAvailable(apps, "code");
        addIfCommandAvailable(apps, "kate");
        addIfCommandAvailable(apps, "mousepad");
        addIfCommandAvailable(apps, "vlc");
        addIfCommandAvailable(apps, "eog");
        addIfCommandAvailable(apps, "eom");
        addIfCommandAvailable(apps, "xdg-email");

        List<String> deduped = dedup(apps);
        deduped.sort(String::compareToIgnoreCase);
        return deduped;
    }

    private void addLinuxAppsFromDir(List<String> out, java.nio.file.Path dir) {
        try {
            if (java.nio.file.Files.isDirectory(dir)) {
                try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files.newDirectoryStream(dir, "*.desktop")) {
                    for (java.nio.file.Path desktopFile : stream) {
                        String exec = extractExecFromDesktopFile(desktopFile);
                        if (exec != null && !exec.isBlank()) {
                            // First token is command
                            String cmd = exec.trim().split("\\s+")[0];
                            // Remove quotes around path if any
                            if ((cmd.startsWith("\"") && cmd.endsWith("\"")) || (cmd.startsWith("'") && cmd.endsWith("'"))) {
                                cmd = cmd.substring(1, cmd.length() - 1);
                            }
                            // If command is absolute path, prefer basename
                            java.nio.file.Path p = java.nio.file.Paths.get(cmd);
                            String candidateName = p.getFileName() != null ? p.getFileName().toString() : cmd;
                            boolean onPath = isCommandAvailable(candidateName);
                            boolean isAbsExec = java.nio.file.Files.isExecutable(p);
                            if (onPath) {
                                addIfAbsent(out, candidateName);
                            } else if (isAbsExec) {
                                // keep absolute path so we can execute directly
                                addIfAbsent(out, p.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extractExecFromDesktopFile(java.nio.file.Path desktopFile) {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(desktopFile);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Exec=")) {
                    String exec = trimmed.substring(5).trim();
                    // remove field codes like %f, %F, %U etc
                    exec = exec.replaceAll("%[fFuUdDnNickvm]", "").trim();
                    return exec;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<String> enumerateWindowsApplications() {
        List<String> apps = new ArrayList<>();

        // Always useful commands if on PATH
        addIfCommandAvailable(apps, "notepad");
        addIfCommandAvailable(apps, "code");
        addIfCommandAvailable(apps, "write");
        addIfCommandAvailable(apps, "mspaint");
        addIfCommandAvailable(apps, "wordpad");
        addIfCommandAvailable(apps, "wmplayer");

        // Common installed apps by default locations
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LocalAppData");

        addIfExists(apps, join(programFiles, "Notepad++", "notepad++.exe"));
        addIfExists(apps, join(programFilesX86, "Notepad++", "notepad++.exe"));

        addIfExists(apps, join(programFiles, "Microsoft VS Code", "Code.exe"));
        addIfExists(apps, join(programFilesX86, "Microsoft VS Code", "Code.exe"));
        addIfExists(apps, join(localAppData, "Programs", "Microsoft VS Code", "Code.exe"));

        addIfExists(apps, join(programFiles, "Sublime Text", "sublime_text.exe"));
        addIfExists(apps, join(programFilesX86, "Sublime Text", "sublime_text.exe"));

        addIfExists(apps, join(programFiles, "VideoLAN", "VLC", "vlc.exe"));
        addIfExists(apps, join(programFilesX86, "VideoLAN", "VLC", "vlc.exe"));

        addIfExists(apps, join(programFiles, "Google", "Chrome", "Application", "chrome.exe"));
        addIfExists(apps, join(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"));

        addIfExists(apps, join(programFiles, "Mozilla Firefox", "firefox.exe"));
        addIfExists(apps, join(programFilesX86, "Mozilla Firefox", "firefox.exe"));

        addIfExists(apps, join(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"));
        addIfExists(apps, join(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"));

        addIfExists(apps, join(programFiles, "7-Zip", "7zFM.exe"));
        addIfExists(apps, join(programFilesX86, "7-Zip", "7zFM.exe"));

        addIfExists(apps, join(programFiles, "WinRAR", "WinRAR.exe"));
        addIfExists(apps, join(programFilesX86, "WinRAR", "WinRAR.exe"));

        // Deduplicate and sort; prefer names on PATH first, then full paths
        List<String> deduped = dedup(apps);
        deduped.sort(String::compareToIgnoreCase);
        return deduped;
    }

    private String join(String base, String... parts) {
        if (base == null || base.isBlank()) return null;
        java.nio.file.Path p = java.nio.file.Paths.get(base, parts);
        return p.toString();
    }

    private void addIfExists(List<String> out, String pathStr) {
        if (pathStr == null) return;
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(pathStr);
            if (java.nio.file.Files.exists(p)) {
                addIfAbsent(out, p.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private void addIfCommandAvailable(List<String> out, String command) {
        try {
            if (isCommandAvailable(command)) {
                addIfAbsent(out, command);
            }
        } catch (Exception ignored) {
        }
    }

    private void addIfAbsent(List<String> out, String value) {
        if (value == null || value.isBlank()) return;
        if (!out.contains(value)) {
            out.add(value);
        }
    }

    private List<String> dedup(List<String> in) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(in);
        return new java.util.ArrayList<>(set);
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