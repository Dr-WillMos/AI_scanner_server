package org.example.aiscanner_server.client;

import org.example.aiscanner_server.common.AiServiceException;
import org.example.aiscanner_server.model.dto.AiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final RestClient restClient;

    public AiClient(RestClient aiRestClient) {
        this.restClient = aiRestClient;
    }

    public AiResult analyze(MultipartFile video) {
        byte[] bytes = readBytes(video);
        String filename = getFilename(video);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/analyze/video")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(AiResult.class);
            } catch (HttpClientErrorException e) {
                // 4xx — client errors are not retryable
                throw new AiServiceException("AI 服务拒绝请求 (HTTP " + e.getStatusCode().value() + ")", e, attempt);
            } catch (ResourceAccessException e) {
                // Connection refused / DNS failure / timeout — retryable
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = INITIAL_BACKOFF_MS << (attempt - 1); // 1s, 2s, 4s
                    log.warn("AI service unreachable (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_RETRIES, delay, e.getMessage());
                    sleep(delay);
                }
            } catch (Exception e) {
                HttpStatusCode status = extractStatusCode(e);
                if (status != null && status.is5xxServerError()) {
                    // 5xx — retryable
                    lastException = e;
                    if (attempt < MAX_RETRIES) {
                        long delay = INITIAL_BACKOFF_MS << (attempt - 1);
                        log.warn("AI service returned 5xx (attempt {}/{}), retrying in {}ms: {}",
                                attempt, MAX_RETRIES, delay, e.getMessage());
                        sleep(delay);
                    }
                } else {
                    // Unknown or non-retryable
                    throw new AiServiceException("AI 服务调用失败: " + e.getMessage(), e, attempt);
                }
            }
        }

        // All retries exhausted
        Throwable cause = lastException;
        String rootMsg = cause != null ? cause.getMessage() : "unknown";
        throw new AiServiceException("AI 服务调用失败 (已重试 " + MAX_RETRIES + " 次): " + rootMsg,
                lastException, MAX_RETRIES);
    }

    private byte[] readBytes(MultipartFile video) {
        try {
            return video.getBytes();
        } catch (Exception e) {
            throw new AiServiceException("无法读取视频文件: " + e.getMessage(), e, 0);
        }
    }

    private String getFilename(MultipartFile video) {
        String name = video.getOriginalFilename();
        return name != null ? name : "video.mp4";
    }

    private HttpStatusCode extractStatusCode(Exception e) {
        try {
            if (e instanceof org.springframework.web.client.HttpServerErrorException se) {
                return se.getStatusCode();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
