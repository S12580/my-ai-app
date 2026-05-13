package com.ai.app.chat.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Stores raw bytes for chat attachments (pending at analyze time, then bound to a user message).
 */
@Service
public class ChatAttachmentStorage {

    private static final String BLOB_EXT = ".blob";

    @Value("${app.chat.attachments.dir:data/chat-attachments}")
    private String baseDirString;

    private Path baseDir;

    @PostConstruct
    void init() throws IOException {
        baseDir = Path.of(baseDirString).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
    }

    public Path pendingBlobPath(long sessionId, String attachmentId) {
        return baseDir.resolve("pending")
                .resolve(String.valueOf(sessionId))
                .resolve(attachmentId + BLOB_EXT);
    }

    public Path messageBlobPath(long sessionId, long messageId, String attachmentId) {
        return baseDir.resolve("messages")
                .resolve(String.valueOf(sessionId))
                .resolve(String.valueOf(messageId))
                .resolve(attachmentId + BLOB_EXT);
    }

    public void savePending(long sessionId, String attachmentId, byte[] data) throws IOException {
        Path path = pendingBlobPath(sessionId, attachmentId);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }

    public boolean pendingExists(long sessionId, String attachmentId) {
        return Files.isRegularFile(pendingBlobPath(sessionId, attachmentId));
    }

    public void bindAll(long sessionId, long messageId, List<String> attachmentIds) throws IOException {
        for (String id : attachmentIds) {
            bindOne(sessionId, messageId, id);
        }
    }

    private void bindOne(long sessionId, long messageId, String attachmentId) throws IOException {
        Path src = pendingBlobPath(sessionId, attachmentId);
        if (!Files.isRegularFile(src)) {
            throw new IOException("pending attachment missing: " + attachmentId);
        }
        Path dest = messageBlobPath(sessionId, messageId, attachmentId);
        Files.createDirectories(dest.getParent());
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    public Resource loadMessageBlob(long sessionId, long messageId, String attachmentId) {
        Path p = messageBlobPath(sessionId, messageId, attachmentId);
        if (!Files.isRegularFile(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "attachment file not found");
        }
        return new FileSystemResource(p);
    }

    public void deleteMessageFolder(long sessionId, long messageId) {
        Path dir = baseDir.resolve("messages")
                .resolve(String.valueOf(sessionId))
                .resolve(String.valueOf(messageId));
        if (Files.isDirectory(dir)) {
            try {
                FileSystemUtils.deleteRecursively(dir);
            } catch (IOException ignored) {
            }
        }
    }

    public void deleteSessionFolders(long sessionId) {
        Path pending = baseDir.resolve("pending").resolve(String.valueOf(sessionId));
        if (Files.isDirectory(pending)) {
            try {
                FileSystemUtils.deleteRecursively(pending);
            } catch (IOException ignored) {
            }
        }
        Path messages = baseDir.resolve("messages").resolve(String.valueOf(sessionId));
        if (Files.isDirectory(messages)) {
            try {
                FileSystemUtils.deleteRecursively(messages);
            } catch (IOException ignored) {
            }
        }
    }
}
