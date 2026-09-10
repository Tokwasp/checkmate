package ssafy.checkmate.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.checkmate.domain.member.entity.Role;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtTokenProviderTest {
    private static final String SECRET = "test-secret-key-for-jwt-unit-and-integration-tests-0123456789abcdef";
    private static final String OTHER_SECRET = "another-secret-key-that-is-long-enough-for-hs256-0123456789";
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("발급한 엑세스 토큰을 파싱하면 memberId와 role이 그대로 나온다")
    void createAndParse() {
        String token = provider.createAccessToken(1L, Role.USER);

        Claims claims = provider.parseClaims(token);

        assertThat(provider.getMemberId(claims)).isEqualTo(1L);
        assertThat(provider.getRole(claims)).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException을 던진다")
    void expired() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofSeconds(-10)));
        String token = expiredProvider.createAccessToken(1L, Role.USER);

        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 SignatureException을 던진다")
    void wrongSignature() {
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(OTHER_SECRET, Duration.ofMinutes(30)));
        String token = other.createAccessToken(1L, Role.USER);

        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 문자열은 MalformedJwtException을 던진다")
    void malformed() {
        assertThatThrownBy(() -> provider.parseClaims("this.is.not-a-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }
}