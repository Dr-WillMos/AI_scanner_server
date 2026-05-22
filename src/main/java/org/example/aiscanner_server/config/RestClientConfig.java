package org.example.aiscanner_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.timeout:30s}")
    private Duration timeout;

    @Bean
    public RestClient aiRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        return builder
                .requestFactory(factory)
                .baseUrl(aiServiceUrl)
                .build();
    }
}
