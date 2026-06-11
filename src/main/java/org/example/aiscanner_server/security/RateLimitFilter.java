package org.example.aiscanner_server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aiscanner_server.config.RateLimitProperties;
import org.example.aiscanner_server.metrics.DetectionMetrics;
import org.example.aiscanner_server.service.RateLimitService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(2) //这里是Order2，Api过滤器时Order1.
public class RateLimitFilter extends OncePerRequestFilter {  //集成该接口并通过注释将其注入到Spring管理的容器中

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final DetectionMetrics metrics;

    public RateLimitFilter(RateLimitProperties properties, RateLimitService rateLimitService,
                           DetectionMetrics metrics) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.metrics = metrics;       //分别是配置，逻辑，观测（记录结果以供分析）
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/")) {   //监控系统本身不受业务限流影响
            chain.doFilter(request, response);
            return;
        }

        if (!properties.isEnabled()) { //全局限流开关
            chain.doFilter(request, response);
            return;
        }

        // 管理员Key不受业务限流影响
        Integer rateLimit = (Integer) request.getAttribute("rateLimit"); //从Http请求中获取rateLimit值，从内部ApiKeyFilter获取
        int limit = (rateLimit != null && rateLimit > 0) ? rateLimit : properties.getDefaultLimit(); //rateLimit不为 null且大于0就是用默认值
        if (rateLimit != null && rateLimit == 0) {  //若为0就是特殊权限，不被限制
            chain.doFilter(request, response);
            return;
        }

        String keyValue = (String) request.getAttribute("apiKeyValue"); //获取此前setAttribute的API，如没有就从请求头中获取
        if (keyValue == null) {
            keyValue = request.getHeader("X-API-Key");
        }

        RateLimitService.RateLimitResult result = rateLimitService.checkRate(
                keyValue != null ? keyValue : "unknown", limit, properties.getWindowSeconds());//检查是否触发限流

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTimeSeconds())); //告知客户端剩余请求数等信息

        if (!result.allowed()) {
    metrics.recordRateLimitExceeded();//观测层记录
            response.setStatus(429); //限流错误码
            response.setHeader("Retry-After", String.valueOf(Math.max(1, result.resetTimeSeconds() - System.currentTimeMillis() / 1000)));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
