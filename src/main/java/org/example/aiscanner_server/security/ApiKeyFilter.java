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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(1)  //定义组件或切面的执行顺序，数值越小越靠前，详细查询AOP切面
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
        if (path.startsWith("/actuator/") || path.equals("/api/v1/keys/register")) {  //检测健康和注册接口直接放行
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-API-Key");  //获取并验证Key
        if (key == null || key.isBlank()) {
            sendUnauthorized(response, "Missing or invalid API key");
            return;
        }

        // 管理员Key，不收任何限制
        if (rootKey.equals(key)) {
            setAuthentication(key, List.of("DETECT", "HISTORY", "ADMIN"));
            request.setAttribute("rateLimit", 0); // 不受业务限流限制
            request.setAttribute("apiKeyId", 0L); //
            chain.doFilter(request, response);
            return;
        }

        // 动态密钥，需要和MySQL以及Redis联合认证
        Optional<ApiKey> optKey = apiKeyService.validateKey(key);  //该方法在API的Status为不可用时会返回空值
        if (optKey.isEmpty()) {
            sendUnauthorized(response, "Missing or invalid API key");
            return;
        }

        ApiKey apiKey = optKey.get();
        String[] perms = apiKey.getPermissions().split(",");  //返回权限字符串给Perms并使用逗号分割
        setAuthentication(key, List.of(perms)); //将perms转化为List<String>用于设置权限

        request.setAttribute("rateLimit", apiKey.getRateLimit());
        request.setAttribute("apiKeyId", apiKey.getId());
        request.setAttribute("apiKeyValue", key);  //设置该业务Key的必要管理参数

        chain.doFilter(request, response);

        // 异步地通过ID记录API最后使用时间
        if (apiKey.getId() != null) {
            try {
                apiKeyService.recordUsage(apiKey.getId());
            } catch (Exception ignored) {
                //即使无法获取最后使用时间，也不要返回异常
            }
        }
    }

    private void setAuthentication(String principal, List<String> permissions) {  //principal实则是API标识
        List<GrantedAuthority> authorities = new ArrayList<>(); //创建一个 ArrayList，用于存放 Spring Security 的 GrantedAuthority 对象
        for (String perm : permissions) {
            String trimmed = perm.trim().toUpperCase();  //将权限名称转换成大写传入Trimmed
            if (!trimmed.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed)); //新建SimpleGrantedAuthority转载进权限集合
            }
        }
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);  //生成一个已认证的令牌对象
        SecurityContextHolder.getContext().setAuthentication(auth); //将当前已认证用户的身份和权限信息绑定到当前线程
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String json = new ObjectMapper().writeValueAsString(Map.of("code", 401, "message", message));
        response.getWriter().write(json);
    }
}
