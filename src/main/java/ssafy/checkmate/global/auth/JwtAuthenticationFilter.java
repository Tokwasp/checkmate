package ssafy.checkmate.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ssafy.checkmate.global.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Authorization 헤더의 JWT를 검증하는 서블릿 필터.
 *
 * 컨트롤러(DispatcherServlet)보다 앞에서 실행되므로, 토큰이 없거나 잘못된 요청은
 * 컨트롤러까지 가지 못하고 여기서 401로 끝난다.
 * 검증에 성공하면 토큰에서 꺼낸 회원 정보를 MemberPrincipal로 만들어 request attribute에 넣는다.
 *
 * 주의: 필터는 DispatcherServlet 바깥이라 @RestControllerAdvice가 여기서 난 예외를 잡지 못한다.
 * 그래서 401 응답 JSON을 이 클래스가 직접 쓴다.
 *
 * @Component를 붙이지 않는다. 붙이면 Boot가 모든 URL에 자동 등록해버리기 때문에,
 * URL 패턴과 순서를 제어하기 위해 AuthConfig에서 FilterRegistrationBean으로 등록한다.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    /** 토큰 없이 접근 가능한 경로 패턴 (로그인 등). AntPathMatcher 문법 사용 */
    private final List<String> whitelist;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   ObjectMapper objectMapper,
                                   List<String> whitelist) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.whitelist = whitelist;
    }

    /**
     * true를 반환하면 doFilterInternal을 건너뛰고 다음 필터/컨트롤러로 바로 넘어간다.
     * 로그인처럼 토큰이 없어야 정상인 경로를 여기서 걸러낸다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            sendUnauthorized(request, response, "인증이 필요합니다.");
            return;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            MemberPrincipal principal = new MemberPrincipal(
                    jwtTokenProvider.getMemberId(claims),
                    jwtTokenProvider.getRole(claims));
            // 검증 통과: 회원 정보를 요청에 붙여 컨트롤러까지 전달한다
            request.setAttribute(MemberPrincipal.ATTRIBUTE_KEY, principal);
        } catch (ExpiredJwtException e) {
            // ExpiredJwtException도 JwtException의 자식이므로 반드시 먼저 잡아야 한다.
            // 순서를 바꾸면 만료도 "유효하지 않은 토큰"으로 나간다.
            sendUnauthorized(request, response, "만료된 토큰입니다.");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException: 서명 불일치, 형식 오류 등 / IllegalArgumentException: 빈 토큰 문자열
            sendUnauthorized(request, response, "유효하지 않은 토큰입니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** "Authorization: Bearer xxx" 헤더에서 xxx만 꺼낸다. 헤더가 없거나 형식이 다르면 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /**
     * 401 응답을 직접 쓴다. 호출한 쪽은 반드시 return 해서 filterChain.doFilter를 타지 않아야 한다.
     * (안 그러면 컨트롤러까지 실행돼 응답이 두 번 써진다)
     */
    private void sendUnauthorized(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String message) throws IOException {
        log.warn("Unauthorized request: {} {} - {}", request.getMethod(), request.getRequestURI(), message);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(HttpStatus.UNAUTHORIZED, message);
        // Jackson 3(tools.jackson)의 writeValueAsString은 unchecked 예외라 try/catch 불필요
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
