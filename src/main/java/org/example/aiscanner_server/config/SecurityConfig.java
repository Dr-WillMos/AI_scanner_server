package org.example.aiscanner_server.config;

import org.example.aiscanner_server.security.ApiKeyFilter;
import org.example.aiscanner_server.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiKeyFilter apiKeyFilter,
                                                   RateLimitFilter rateLimitFilter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/detect").hasRole("DETECT")
                .requestMatchers(HttpMethod.POST, "/api/v1/detect/async").hasRole("DETECT")
                .requestMatchers(HttpMethod.GET, "/api/v1/detect/*/status").hasRole("DETECT")
                .requestMatchers(HttpMethod.GET, "/api/v1/history").hasRole("HISTORY")
                .requestMatchers("/api/v1/blacklist/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/keys/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/stats").hasRole("ADMIN")
                .requestMatchers("/api/v1/dlq/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, ApiKeyFilter.class);
        return http.build();
    }
}
