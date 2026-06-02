package org.example.aiscanner_server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        filter = new ApiKeyFilter();
        // Inject apiKey via reflection (no Spring context in unit test)
        var field = ApiKeyFilter.class.getDeclaredField("apiKey");
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
        verify(response).setContentType("application/json;charset=UTF-8");
        assertTrue(stringWriter.toString().contains("401"));
        assertTrue(stringWriter.toString().contains("Missing or invalid API key"));
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("错误的 API Key → 401")
    void wrongApiKeyReturns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");
        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("正确的 API Key → 放行并设置 SecurityContext")
    void correctApiKeySetsAuthAndContinues() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/detect");
        when(request.getHeader("X-API-Key")).thenReturn("test-api-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("api-client", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_API_CLIENT")));
    }

    @Test
    @DisplayName("actuator 子路径也放行")
    void actuatorSubPathBypassesAuth() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/info");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
