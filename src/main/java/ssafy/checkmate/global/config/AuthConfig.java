package ssafy.checkmate.global.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ssafy.checkmate.global.auth.JwtAuthenticationFilter;
import ssafy.checkmate.global.auth.JwtProperties;
import ssafy.checkmate.global.auth.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/dev-login",
            "/api/v1/auth/google",
            "/api/v1/auth/login/**"
    );

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtTokenProvider, objectMapper, WHITELIST);
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
