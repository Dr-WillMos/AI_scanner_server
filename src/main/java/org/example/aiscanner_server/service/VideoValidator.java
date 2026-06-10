package org.example.aiscanner_server.service;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class VideoValidator {

    private static final byte[] MP4_SIGNATURE = {'f', 't', 'y', 'p'};

    public void validate(@NonNull MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("视频文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("video/mp4")) {
            throw new IllegalArgumentException("仅支持 MP4 视频格式");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp4")) {
            throw new IllegalArgumentException("仅支持 .mp4 后缀的视频文件");
        }

        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = in.read(header);
            if (bytesRead < 12) {
                throw new IllegalArgumentException("视频文件已损坏或格式不正确");
            }
            // MP4 files begin with a 4-byte box size followed by "ftyp"
            for (int i = 0; i < MP4_SIGNATURE.length; i++) {
                if (header[4 + i] != MP4_SIGNATURE[i]) {
                    throw new IllegalArgumentException("视频文件已损坏或格式不正确");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取视频文件", e);
        }
    }
}
