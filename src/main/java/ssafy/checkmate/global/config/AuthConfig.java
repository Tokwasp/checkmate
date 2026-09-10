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

/**
 * JWT 인증 관련 빈 등록.
 * - JwtProperties 활성화 (yaml의 jwt.* 바인딩)
 * - JwtTokenProvider 빈 생성
 * - JwtAuthenticationFilter를 /api/* 경로에 등록
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    /**
     * 토큰 없이 접근 가능한 경로.
     * /api/v1/auth/login/** 은 소셜 로그인 담당 팀원이 쓸 자리를 미리 열어둔 것.
     */
    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/dev-login",
            "/api/v1/auth/login/**"
    );

    /**
     * JwtTokenProvider에 @Component를 붙이지 않고 여기서 만든다.
     * 덕분에 JwtTokenProvider는 Spring에 의존하지 않는 순수 클래스로 남고, 단위 테스트도 new로 만들 수 있다.
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    /**
     * 필터를 "어느 URL에, 어떤 순서로" 걸지 Tomcat에 알려주는 등록 객체.
     * /api/* 는 서블릿 URL 패턴으로 /api/ 아래 전부를 뜻한다. /h2-console 은 여기 안 걸려 필터를 타지 않는다.
     */
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
