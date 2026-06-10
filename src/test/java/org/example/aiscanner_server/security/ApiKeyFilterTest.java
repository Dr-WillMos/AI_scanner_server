package org.example.aiscanner_server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aiscanner_server.model.entity.ApiKey;
import org.example.aiscanner_server.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private ApiKeyService apiKeyService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        apiKeyService = mock(ApiKeyService.class);
        filter = new ApiKeyFilter(apiKeyService);
        var field = ApiKeyFilter.class.getDeclaredField("rootKey");
        field.setAccessible(true);
        field.set(filter, "test-api-key");

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("actuator 路径无需 API Key 直接放行")
    void actuatorPathBypassesAuth() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).getHeader(anyString());
    }

    @Test
    @DisplayName("无 X-API-Key 头 → 401")
    void missingApiKeyReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn(null);
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        assertTrue(stringWriter.toString().contains("401"));
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("错误的动态 Key → 401")
    void wrongDynamicKeyReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("unknown-key");
        when(apiKeyService.validateKey("unknown-key")).thenReturn(Optional.empty());
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("正确的 Root Key → 放行并授予全部角色")
    void rootKeyGrantsAllRoles() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("test-api-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("test-api-key", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DETECT")));
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HISTORY")));
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(request).setAttribute("rateLimit", 0);
    }

    @Test
    @DisplayName("有效的动态 Key → 放行并授予对应权限")
    void dynamicKeyGrantsDevicePermissions() throws Exception {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setKeyValue("dynamic-key");
        apiKey.setPermissions("DETECT,HISTORY");
        apiKey.setRateLimit(30);
        apiKey.setStatus("ACTIVE");
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("dynamic-key");
        when(apiKeyService.validateKey("dynamic-key")).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DETECT")));
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HISTORY")));
        assertFalse(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(request).setAttribute("rateLimit", 30);
    }

    @Test
    @DisplayName("空白 API Key → 401")
    void blankApiKeyReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("   ");
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verifyNoInteractions(chain);
    }
}
