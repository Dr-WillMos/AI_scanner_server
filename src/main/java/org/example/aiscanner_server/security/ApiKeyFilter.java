package org.example.aiscanner_server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aiscanner_server.model.entity.ApiKey;
import org.example.aiscanner_server.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${api.key}")
    private String rootKey;

    private final ApiKeyService apiKeyService;

    public ApiKeyFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-API-Key");
        if (key == null || key.isBlank()) {
            sendUnauthorized(response, "Missing or invalid API key");
            return;
        }

        // Root key — full permissions, no rate limit
        if (rootKey.equals(key)) {
            setAuthentication(key, List.of("DETECT", "HISTORY", "ADMIN"));
            request.setAttribute("rateLimit", 0); // 0 = unlimited
            request.setAttribute("apiKeyId", 0L);
            chain.doFilter(request, response);
            return;
        }

        // Dynamic key — validate against MySQL/Redis
        Optional<ApiKey> optKey = apiKeyService.validateKey(key);
        if (optKey.isEmpty()) {
            sendUnauthorized(response, "Missing or invalid API key");
            return;
        }

        ApiKey apiKey = optKey.get();
        String[] perms = apiKey.getPermissions().split(",");
        setAuthentication(key, List.of(perms));

        request.setAttribute("rateLimit", apiKey.getRateLimit());
        request.setAttribute("apiKeyId", apiKey.getId());
        request.setAttribute("apiKeyValue", key);

        chain.doFilter(request, response);

        // Record usage asynchronously (after response, fire-and-forget)
        if (apiKey.getId() != null) {
            try {
                apiKeyService.recordUsage(apiKey.getId());
            } catch (Exception ignored) {
                // Don't fail the request if usage recording fails
            }
        }
    }

    private void setAuthentication(String principal, List<String> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String perm : permissions) {
            String trimmed = perm.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
            }
        }
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
