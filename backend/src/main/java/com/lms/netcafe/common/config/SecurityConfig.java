package com.lms.netcafe.common.config;

import com.lms.netcafe.common.security.BearerTokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;

    public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/auth/login", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/api/v1/statistics/dashboard")
                        .hasAuthority("dashboard:view")
                        .requestMatchers("/api/v1/statistics/**")
                        .hasAuthority("statistics:view")
                        .requestMatchers("/api/v1/portal/overview")
                        .hasAuthority("portal:home")
                        .requestMatchers("/api/v1/portal/account-flows")
                        .hasAuthority("portal:account")
                        .requestMatchers("/api/v1/portal/sessions")
                        .hasAuthority("portal:sessions")
                        .requestMatchers("/api/v1/portal/devices", "/api/v1/portal/billing-rules")
                        .hasAuthority("portal:devices")
                        .requestMatchers("/api/v1/portal/faults/**")
                        .hasAuthority("portal:support")
                        .requestMatchers("/api/v1/members/**")
                        .hasAuthority("member:manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/devices/**")
                        .hasAnyAuthority("device:view", "device:manage")
                        .requestMatchers("/api/v1/devices/**")
                        .hasAuthority("device:manage")
                        .requestMatchers("/api/v1/billing/**")
                        .hasAuthority("billing:manage")
                        .requestMatchers("/api/v1/sessions/**")
                        .hasAuthority("session:view")
                        .requestMatchers("/api/v1/faces/**")
                        .hasAuthority("face:manage")
                        .requestMatchers("/api/v1/system/**")
                        .hasAuthority("system:user")
                        .requestMatchers("/api/v1/maintenance/**")
                        .hasAuthority("maintenance:manage")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://127.0.0.1:5173", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
