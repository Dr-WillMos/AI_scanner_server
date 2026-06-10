package org.example.aiscanner_server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int windowSeconds = 60;
    private int defaultLimit = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public int getDefaultLimit() { return defaultLimit; }
    public void setDefaultLimit(int defaultLimit) { this.defaultLimit = defaultLimit; }
}
