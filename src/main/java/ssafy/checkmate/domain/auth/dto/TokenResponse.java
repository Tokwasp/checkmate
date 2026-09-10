package ssafy.checkmate.domain.auth.dto;

/**
 * 로그인 성공 시 내려주는 토큰. 현재는 Access Token 하나만 사용한다 (Refresh Token 없음).
 * 클라이언트는 이 값을 secure storage에 저장하고 요청마다 "Authorization: Bearer {accessToken}"으로 보낸다.
 */
public record TokenResponse(String accessToken) {
}
