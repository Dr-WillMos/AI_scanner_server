package org.example.aiscanner_server.client;

import org.example.aiscanner_server.model.dto.AiResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AiClient {

    private final RestClient restClient;

    public AiClient(RestClient aiRestClient) {
        this.restClient = aiRestClient;
    }

    public AiResult analyze(MultipartFile video) {
        try {
            byte[] bytes = video.getBytes();
            String filename = video.getOriginalFilename() != null
                    ? video.getOriginalFilename()
                    : "video.mp4";

            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", resource);

            return restClient.post()
                    .uri("/v1/analyze/video")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(AiResult.class);

        } catch (Exception e) {
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }
}
