package org.example.aiscanner_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class VideoStorageService {

    private static final Logger log = LoggerFactory.getLogger(VideoStorageService.class);

    private final Path storageDir;

    public VideoStorageService(@Value("${detect.video-storage-dir:${java.io.tmpdir}/aiscanner/videos}") String dir) {
        this.storageDir = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create video storage directory: " + storageDir, e);
        }
    }

    public Path save(String taskId, MultipartFile video) {
        try {
            String filename = taskId + ".mp4";
            Path filepath = storageDir.resolve(filename);
            video.transferTo(filepath.toFile());
            log.debug("Video saved: {}", filepath);
            return filepath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save video for task " + taskId, e);
        }
    }

    public byte[] read(String taskId) {
        Path filepath = storageDir.resolve(taskId + ".mp4");
        try {
            return Files.readAllBytes(filepath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read video for task " + taskId, e);
        }
    }

    public void delete(String taskId) {
        try {
            Path filepath = storageDir.resolve(taskId + ".mp4");
            Files.deleteIfExists(filepath);
        } catch (IOException e) {
            log.warn("Failed to delete video for task {}, ignoring", taskId, e);
        }
    }
}
