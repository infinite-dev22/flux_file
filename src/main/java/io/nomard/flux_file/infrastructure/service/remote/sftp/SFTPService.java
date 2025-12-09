package io.nomard.flux_file.infrastructure.service.remote.sftp;

import com.jcraft.jsch.*;
import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import io.nomard.flux_file.infrastructure.service.remote.RemoteFileSystemService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.Vector;

@Service
public class SFTPService implements RemoteFileSystemService {

    private final reactor.core.scheduler.Scheduler ioScheduler = Schedulers.boundedElastic();
    private Session session;
    private ChannelSftp sftpChannel;

    // Connection format: username:password@host:port or username@host:port (for key auth)
    @Override
    public Mono<Boolean> connect(String connectionString) {
        return Mono.fromCallable(() -> {
            try {
                String[] parts = connectionString.split("@");
                String userPart = parts[0];
                String hostPart = parts[1];

                String username;
                String password = null;

                if (userPart.contains(":")) {
                    String[] userPass = userPart.split(":");
                    username = userPass[0];
                    password = userPass[1];
                } else {
                    username = userPart;
                }

                String host;
                int port = 22;

                if (hostPart.contains(":")) {
                    String[] hostPort = hostPart.split(":");
                    host = hostPort[0];
                    port = Integer.parseInt(hostPort[1]);
                } else {
                    host = hostPart;
                }

                JSch jsch = new JSch();

                // Try to load SSH key if available
                String sshDir = System.getProperty("user.home") + "/.ssh";
                java.io.File privateKey = new java.io.File(sshDir + "/id_rsa");
                if (privateKey.exists() && password == null) {
                    jsch.addIdentity(privateKey.getAbsolutePath());
                }

                session = jsch.getSession(username, host, port);

                if (password != null) {
                    session.setPassword(password);
                }

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                session.connect();

                Channel channel = session.openChannel("sftp");
                channel.connect();
                sftpChannel = (ChannelSftp) channel;

                return true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to SFTP server", e);
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> disconnect() {
        return Mono.fromCallable(() -> {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            return true;
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Boolean> isConnected() {
        return Mono.just(sftpChannel != null && sftpChannel.isConnected());
    }

    @Override
    public Flux<RemoteFileItem> listFiles(String remotePath) {
        return Flux.defer(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    return Flux.error(new RuntimeException("Not connected to SFTP server"));
                }
                String path = remotePath;
                if (path == null) {
                    path = sftpChannel.getHome();
                }

                @SuppressWarnings("unchecked")
                Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(path);

                String finalRemotePath = path;
                return Flux.fromIterable(files)
                        .filter(entry -> !entry.getFilename().equals(".") && !entry.getFilename().equals(".."))
                        .map(entry -> {
                            SftpATTRS attrs = entry.getAttrs();
                            boolean isDirectory = attrs.isDir();
                            long size = attrs.getSize();
                            Instant modified = Instant.ofEpochSecond(attrs.getMTime());

                            String fullPath = finalRemotePath.endsWith("/")
                                    ? finalRemotePath + entry.getFilename()
                                    : finalRemotePath + "/" + entry.getFilename();

                            return new RemoteFileItem(
                                    fullPath,
                                    entry.getFilename(),
                                    isDirectory,
                                    size,
                                    modified,
                                    "sftp",
                                    finalRemotePath
                            );
                        });
            } catch (Exception e) {
                return Flux.error(new RuntimeException("Failed to list SFTP files", e));
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Void> downloadFile(String remoteFile, Path localDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    throw new RuntimeException("Not connected to SFTP server");
                }
                sftpChannel.get(remoteFile, localDestination.toString());
            } catch (Exception e) {
                throw new RuntimeException("Failed to download file from SFTP server", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> uploadFile(Path localFile, String remoteDestination) {
        return Mono.fromRunnable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    throw new RuntimeException("Not connected to SFTP server");
                }
                sftpChannel.put(localFile.toString(), remoteDestination);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload file to SFTP server", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> deleteFile(String remoteFile) {
        return Mono.fromRunnable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    throw new RuntimeException("Not connected to SFTP server");
                }

                SftpATTRS attrs = sftpChannel.stat(remoteFile);
                if (attrs.isDir()) {
                    sftpChannel.rmdir(remoteFile);
                } else {
                    sftpChannel.rm(remoteFile);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete file from SFTP server", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Void> createDirectory(String remotePath) {
        return Mono.fromRunnable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    throw new RuntimeException("Not connected to SFTP server");
                }
                sftpChannel.mkdir(remotePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create directory on SFTP server", e);
            }
        }).subscribeOn(ioScheduler).then();
    }

    @Override
    public Mono<Long> getAvailableSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    return 0L;
                }
                // SFTP doesn't provide a standard way to get disk space
                // Would need to execute df command via SSH
                return -1L; // Unknown
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public Mono<Long> getUsedSpace() {
        return Mono.fromCallable(() -> {
            try {
                if (sftpChannel == null || !sftpChannel.isConnected()) {
                    return 0L;
                }
                return -1L; // Unknown
            } catch (Exception e) {
                return 0L;
            }
        }).subscribeOn(ioScheduler);
    }

    @Override
    public String getServiceName() {
        return "SFTP/SSH";
    }

    public Mono<String> executeCommand(String command) {
        return Mono.fromCallable(() -> {
            try {
                Channel channel = session.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);

                channel.setInputStream(null);
                java.io.InputStream in = channel.getInputStream();
                channel.connect();

                StringBuilder result = new StringBuilder();
                byte[] tmp = new byte[1024];
                do {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, 1024);
                        if (i < 0) break;
                        result.append(new String(tmp, 0, i));
                    }
                } while (!channel.isClosed());
                channel.disconnect();

                return result.toString();
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute SSH command", e);
            }
        }).subscribeOn(ioScheduler);
    }
}